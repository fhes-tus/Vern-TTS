use crate::storage::{error, hex_digest, Result};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use sherpa_onnx::{
    OfflineTts, OfflineTtsConfig, OfflineTtsKokoroModelConfig, OfflineTtsModelConfig,
    OfflineTtsVitsModelConfig,
};
use std::{
    collections::HashMap,
    fs::{self, File},
    io::{Read, Write},
    path::{Component, Path, PathBuf},
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc, Mutex,
    },
    time::Duration,
};

#[derive(Clone, Copy)]
pub struct Descriptor {
    pub id: &'static str,
    pub name: &'static str,
    pub family: &'static str,
    pub engine: &'static str,
    pub size: &'static str,
    pub archive: &'static str,
    pub model: &'static str,
    pub sha256: &'static str,
    pub sid: i32,
}
pub const CATALOGUE: [Descriptor; 2] = [
    Descriptor {
        id: "piper_en_us_lessac",
        name: "Lessac",
        family: "Lite",
        engine: "Piper",
        size: "~20 MB",
        archive: "vits-piper-en_US-lessac-medium-int8",
        model: "en_US-lessac-medium.onnx",
        sha256: "f1c6d0295cf16087b05f80fdca5b44daca5cd78e2c425d419a42ba34929805f9",
        sid: 0,
    },
    Descriptor {
        id: "kokoro_af_heart",
        name: "Heart",
        family: "Studio",
        engine: "Kokoro",
        size: "~126 MB",
        archive: "kokoro-int8-multi-lang-v1_0",
        model: "model.int8.onnx",
        sha256: "4c3052abaa60943a341f193888cf6abd68787dae6ab8ae5c925a706caa247e4e",
        sid: 3,
    },
];
pub fn descriptor(id: &str) -> Result<Descriptor> {
    CATALOGUE
        .iter()
        .find(|v| v.id == id)
        .copied()
        .ok_or("Unknown voice.".into())
}

#[derive(Clone, Serialize)]
pub struct Voice {
    pub id: String,
    pub name: String,
    pub family: String,
    pub engine: String,
    pub size: String,
    pub state: String,
    pub downloaded: u64,
    pub total: u64,
    pub error: Option<String>,
}
struct Job {
    voice: Voice,
    cancelled: Arc<AtomicBool>,
}
#[derive(Serialize, Deserialize)]
struct Manifest {
    id: String,
    archive_sha256: String,
    files: HashMap<String, String>,
}
pub struct Voices {
    pub root: PathBuf,
    jobs: Mutex<HashMap<String, Job>>,
    pub slots: Arc<tokio::sync::Semaphore>,
}
impl Voices {
    pub fn new(root: PathBuf) -> Result<Self> {
        fs::create_dir_all(&root).map_err(error)?;
        // Only our fixed staging names are removed. Interrupted downloads restart safely.
        for voice in CATALOGUE {
            let stage = root.join(format!("{}.staging", voice.id));
            if stage.is_dir() {
                fs::remove_dir_all(stage).map_err(error)?;
            }
        }
        Ok(Self {
            root,
            jobs: Mutex::new(HashMap::new()),
            slots: Arc::new(tokio::sync::Semaphore::new(2)),
        })
    }
    pub fn list(&self) -> Result<Vec<Voice>> {
        let jobs = self.jobs.lock().map_err(error)?;
        Ok(CATALOGUE
            .iter()
            .map(|d| {
                if let Some(job) = jobs.get(d.id) {
                    if job.voice.state != "installed" {
                        return job.voice.clone();
                    }
                }
                Voice {
                    id: d.id.into(),
                    name: d.name.into(),
                    family: d.family.into(),
                    engine: d.engine.into(),
                    size: d.size.into(),
                    state: if self.installed(*d) {
                        "installed"
                    } else {
                        "idle"
                    }
                    .into(),
                    downloaded: 0,
                    total: 0,
                    error: None,
                }
            })
            .collect())
    }
    pub fn installed(&self, d: Descriptor) -> bool {
        let dir = self.root.join(d.id);
        let manifest = fs::read(dir.join("installed.json"))
            .ok()
            .and_then(|b| serde_json::from_slice::<Manifest>(&b).ok());
        manifest.is_some_and(|m| m.id == d.id && m.archive_sha256 == d.sha256)
            && validate_shape(&dir, d).is_ok()
    }
    pub fn begin(&self, d: Descriptor) -> Result<bool> {
        let mut jobs = self.jobs.lock().map_err(error)?;
        if jobs.get(d.id).is_some_and(|j| {
            ["queued", "downloading", "validating", "cancelling"].contains(&j.voice.state.as_str())
        }) || (self.installed(d) && !jobs.get(d.id).is_some_and(|j| j.voice.state == "failed"))
        {
            return Ok(false);
        }
        jobs.insert(
            d.id.into(),
            Job {
                voice: Voice {
                    id: d.id.into(),
                    name: d.name.into(),
                    family: d.family.into(),
                    engine: d.engine.into(),
                    size: d.size.into(),
                    state: "queued".into(),
                    downloaded: 0,
                    total: 0,
                    error: None,
                },
                cancelled: Arc::new(AtomicBool::new(false)),
            },
        );
        Ok(true)
    }
    pub fn cancel(&self, id: &str) -> Result<()> {
        descriptor(id)?;
        if let Some(job) = self.jobs.lock().map_err(error)?.get_mut(id) {
            if ["queued", "downloading", "validating", "cancelling"]
                .contains(&job.voice.state.as_str())
            {
                job.cancelled.store(true, Ordering::SeqCst);
                job.voice.state = "cancelling".into();
            }
        }
        Ok(())
    }
    pub fn invalidate(&self, d: Descriptor, message: String) {
        if let Ok(mut jobs) = self.jobs.lock() {
            jobs.insert(
                d.id.into(),
                Job {
                    voice: Voice {
                        id: d.id.into(),
                        name: d.name.into(),
                        family: d.family.into(),
                        engine: d.engine.into(),
                        size: d.size.into(),
                        state: "failed".into(),
                        downloaded: 0,
                        total: 0,
                        error: Some(message),
                    },
                    cancelled: Arc::new(AtomicBool::new(false)),
                },
            );
        }
    }
    fn state(&self, id: &str, state: &str, downloaded: u64, total: u64, message: Option<String>) {
        if let Ok(mut jobs) = self.jobs.lock() {
            if let Some(job) = jobs.get_mut(id) {
                if job.cancelled.load(Ordering::SeqCst)
                    && ["downloading", "validating"].contains(&state)
                {
                    return;
                }
                job.voice.state = state.into();
                job.voice.downloaded = downloaded;
                job.voice.total = total;
                job.voice.error = message;
            }
        }
    }
    pub fn install(&self, d: Descriptor) -> Result<()> {
        let flag = self
            .jobs
            .lock()
            .map_err(error)?
            .get(d.id)
            .ok_or("Download was not queued.")?
            .cancelled
            .clone();
        let stage = self.root.join(format!("{}.staging", d.id));
        let result: Result<()> = (|| {
            check_cancel(&flag)?;
            fs::create_dir_all(&stage).map_err(error)?;
            let url = format!(
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/{}.tar.bz2",
                d.archive
            );
            let client = download_client()?;
            self.state(d.id, "downloading", 0, 0, None);
            let mut response = client
                .get(url)
                .send()
                .map_err(|e| format!("Could not reach voice server: {e}"))?
                .error_for_status()
                .map_err(|e| format!("Voice package download failed: {e}"))?;
            let total = response.content_length().unwrap_or(0);
            if total > 250 * 1024 * 1024 {
                return Err("Voice archive exceeds the supported size.".into());
            }
            let archive_path = stage.join("package.tar.bz2");
            let mut archive = File::create(&archive_path).map_err(error)?;
            let mut digest = Sha256::new();
            let mut downloaded = 0_u64;
            let mut buffer = [0_u8; 65536];
            self.state(d.id, "downloading", 0, total, None);
            loop {
                check_cancel(&flag)?;
                let n = response
                    .read(&mut buffer)
                    .map_err(|e| format!("Voice download interrupted: {e}"))?;
                if n == 0 {
                    break;
                }
                downloaded += n as u64;
                if downloaded > 250 * 1024 * 1024 {
                    return Err("Voice download exceeds 250 MB.".into());
                }
                archive.write_all(&buffer[..n]).map_err(error)?;
                digest.update(&buffer[..n]);
                self.state(d.id, "downloading", downloaded, total, None);
            }
            if total != 0 && downloaded != total {
                return Err("Voice download was incomplete. Please retry.".into());
            }
            archive.sync_all().map_err(error)?;
            drop(archive);
            let archive_sha256 = hex_digest(digest.finalize());
            if archive_sha256 != d.sha256 {
                return Err("Voice archive checksum does not match the verified catalogue. Retry or update Vern.".into());
            }
            self.state(d.id, "validating", downloaded, total, None);
            let unpack = stage.join("unpacked");
            fs::create_dir_all(&unpack).map_err(error)?;
            extract_archive(File::open(&archive_path).map_err(error)?, &unpack, &flag)?;
            let model_dir = unpack.join(d.archive);
            validate_shape(&model_dir, d)?;
            check_cancel(&flag)?;
            // Load the actual native graph before marking installed, including the exact speaker table.
            let engine = load_engine(&model_dir, d)?;
            drop(engine);
            check_cancel(&flag)?;
            let mut files = HashMap::new();
            hash_tree(&model_dir, &model_dir, &mut files, &flag)?;
            let manifest = Manifest {
                id: d.id.into(),
                archive_sha256,
                files,
            };
            let mut marker = File::create(model_dir.join("installed.json")).map_err(error)?;
            marker
                .write_all(&serde_json::to_vec_pretty(&manifest).map_err(error)?)
                .map_err(error)?;
            marker.sync_all().map_err(error)?;
            drop(marker);
            check_cancel(&flag)?;
            let target = self.root.join(d.id);
            // Existing invalid packages are replaced only after a complete staged validation.
            if target.exists() {
                fs::remove_dir_all(&target).map_err(error)?;
            }
            fs::rename(&model_dir, &target).map_err(error)?;
            Ok(())
        })();
        if stage.exists() {
            let _ = fs::remove_dir_all(&stage);
        }
        match &result {
            Ok(()) => self.state(d.id, "installed", 0, 0, None),
            Err(e) => self.state(
                d.id,
                if flag.load(Ordering::SeqCst) {
                    "idle"
                } else {
                    "failed"
                },
                0,
                0,
                if flag.load(Ordering::SeqCst) {
                    None
                } else {
                    Some(e.clone())
                },
            ),
        }
        result
    }
    pub fn verify(&self, d: Descriptor) -> Result<()> {
        let dir = self.root.join(d.id);
        let manifest: Manifest =
            serde_json::from_slice(&fs::read(dir.join("installed.json")).map_err(error)?)
                .map_err(error)?;
        let mut actual = HashMap::new();
        hash_tree(&dir, &dir, &mut actual, &AtomicBool::new(false))?;
        if manifest.id != d.id || manifest.archive_sha256 != d.sha256 || actual != manifest.files {
            return Err(
                "Voice files have changed or are incomplete. Re-download the voice.".into(),
            );
        }
        validate_shape(&dir, d)
    }
}
fn download_client() -> Result<reqwest::blocking::Client> {
    // reqwest's rustls-no-provider feature requires explicit initialization.
    // Another caller may already have installed the process-wide provider.
    let _ = rustls::crypto::ring::default_provider().install_default();
    reqwest::blocking::Client::builder()
        .connect_timeout(Duration::from_secs(20))
        .timeout(Duration::from_secs(20))
        .user_agent("Vern-Desktop/0.1")
        .build()
        .map_err(error)
}
fn check_cancel(flag: &AtomicBool) -> Result<()> {
    if flag.load(Ordering::SeqCst) {
        Err("Download cancelled.".into())
    } else {
        Ok(())
    }
}
fn validate_shape(dir: &Path, d: Descriptor) -> Result<()> {
    for (name, min) in [
        (d.model, 1_000_000),
        ("tokens.txt", 10),
        ("espeak-ng-data/phontab", 100),
        ("espeak-ng-data/phondata", 100),
        ("espeak-ng-data/phonindex", 100),
    ] {
        if !dir
            .join(name)
            .metadata()
            .is_ok_and(|m| m.is_file() && m.len() >= min)
        {
            return Err(format!(
                "Voice package is missing or has an incomplete {name}."
            ));
        }
    }
    if d.engine == "Kokoro" {
        let size = dir.join("voices.bin").metadata().map_err(error)?.len();
        // Each v1.0 speaker has 510 style vectors of 256 f32 values.
        if ![53 * 510 * 256 * 4, 54 * 510 * 256 * 4].contains(&size) {
            return Err(format!("Kokoro voice table has unexpected size {size}; expected the v1.0 53/54-speaker package."));
        }
        if !dir.join("lexicon-us-en.txt").is_file() {
            return Err("Kokoro English lexicon is missing.".into());
        }
    }
    if !dir.join("espeak-ng-data/lang/gmw/en-US").is_file() {
        return Err("Voice package has no en-US eSpeak language data.".into());
    }
    Ok(())
}
pub fn load_engine(dir: &Path, d: Descriptor) -> Result<OfflineTts> {
    validate_shape(dir, d)?;
    let path = |name: &str| Some(dir.join(name).to_string_lossy().into_owned());
    let mut model = OfflineTtsModelConfig {
        num_threads: 2,
        provider: Some("cpu".into()),
        ..Default::default()
    };
    if d.engine == "Piper" {
        model.vits = OfflineTtsVitsModelConfig {
            model: path(d.model),
            tokens: path("tokens.txt"),
            data_dir: path("espeak-ng-data"),
            ..Default::default()
        };
    } else {
        model.kokoro = OfflineTtsKokoroModelConfig {
            model: path(d.model),
            tokens: path("tokens.txt"),
            voices: path("voices.bin"),
            data_dir: path("espeak-ng-data"),
            lexicon: path("lexicon-us-en.txt"),
            lang: Some("en-US".into()),
            ..Default::default()
        };
    }
    let config = OfflineTtsConfig {
        model,
        max_num_sentences: 1,
        silence_scale: 1.0,
        ..Default::default()
    };
    let engine = OfflineTts::create(&config)
        .ok_or("Native voice initialization failed. Check the package and available memory.")?;
    if d.engine == "Kokoro" && ![53, 54].contains(&engine.num_speakers()) {
        return Err("Kokoro speaker count does not match Vern's v1.0 catalogue.".into());
    }
    Ok(engine)
}
fn hash_tree(
    root: &Path,
    dir: &Path,
    files: &mut HashMap<String, String>,
    flag: &AtomicBool,
) -> Result<()> {
    for entry in fs::read_dir(dir).map_err(error)? {
        check_cancel(flag)?;
        let path = entry.map_err(error)?.path();
        if path.is_dir() {
            hash_tree(root, &path, files, flag)?;
        } else if path.file_name().is_some_and(|n| n != "installed.json") {
            let mut file = File::open(&path).map_err(error)?;
            let mut hash = Sha256::new();
            let mut buf = [0_u8; 65536];
            loop {
                check_cancel(flag)?;
                let n = file.read(&mut buf).map_err(error)?;
                if n == 0 {
                    break;
                }
                hash.update(&buf[..n]);
            }
            files.insert(
                path.strip_prefix(root)
                    .map_err(error)?
                    .to_string_lossy()
                    .replace('\\', "/"),
                hex_digest(hash.finalize()),
            );
        }
    }
    Ok(())
}
pub fn extract_archive(input: impl Read, dest: &Path, flag: &AtomicBool) -> Result<()> {
    let mut archive = tar::Archive::new(bzip2::read::BzDecoder::new(input));
    let mut size = 0_u64;
    for (i, entry) in archive.entries().map_err(error)?.enumerate() {
        check_cancel(flag)?;
        if i > 20_000 {
            return Err("Voice archive has too many entries.".into());
        }
        let mut entry = entry.map_err(error)?;
        let path = entry.path().map_err(error)?.into_owned();
        if path
            .components()
            .any(|c| !matches!(c, Component::Normal(_) | Component::CurDir))
            || path.to_string_lossy().contains(':')
            || path.to_string_lossy().contains('\\')
        {
            return Err("Unsafe voice archive path.".into());
        }
        let kind = entry.header().entry_type();
        if !kind.is_file() && !kind.is_dir() {
            return Err("Voice archive contains a link or unsupported entry.".into());
        }
        size = size
            .checked_add(entry.size())
            .ok_or("Archive size overflow.")?;
        if size > 1024 * 1024 * 1024 {
            return Err("Voice archive expands beyond 1 GB.".into());
        }
        if !entry.unpack_in(dest).map_err(error)? {
            return Err("Voice archive escapes its staging directory.".into());
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn blocking_download_client_has_crypto_provider() {
        download_client().expect("HTTPS client must initialize without a runtime panic");
    }
    #[test]
    fn rejects_archive_links() {
        let mut compressed = bzip2::write::BzEncoder::new(Vec::new(), bzip2::Compression::fast());
        {
            let mut tar = tar::Builder::new(&mut compressed);
            let mut header = tar::Header::new_gnu();
            header.set_entry_type(tar::EntryType::Symlink);
            header.set_size(0);
            header.set_mode(0o777);
            header.set_link_name("../../outside").unwrap();
            header.set_cksum();
            tar.append_data(&mut header, "bad-link", std::io::empty())
                .unwrap();
            tar.finish().unwrap();
        }
        let bytes = compressed.finish().unwrap();
        assert!(extract_archive(
            bytes.as_slice(),
            &std::env::temp_dir(),
            &AtomicBool::new(false)
        )
        .unwrap_err()
        .contains("link"));
    }
    #[test]
    fn unknown_voice_cannot_choose_url_or_path() {
        assert!(descriptor("../../outside").is_err());
    }
}
