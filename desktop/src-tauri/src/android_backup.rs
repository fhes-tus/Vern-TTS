//! Read Android exports privately before conversion and the existing restore preview.
//! Archive names are lookup keys, never destinations on the user's filesystem.
use crate::{
    backup_archive::{open_validated_zip, StagedFile},
    storage::{error, hex_digest, Result},
    transfer::LIMIT,
};
use serde::Serialize;
use serde_json::Value;
use sha2::{Digest, Sha256};
use std::{
    collections::{HashMap, HashSet},
    fs::File,
    io::{Read, Seek},
    path::Path,
    sync::Arc,
};

const SECTIONS: &[&str] = &[
    "annotations",
    "queue",
    "readingLists",
    "readingHistory",
    "pronunciationRules",
    "documentNotes",
    "aiPromptTemplates",
    "aiPromptHistory",
    "generalNotes",
    "trackerDays",
    "flashcards",
    "noteMedia",
    "quizzes",
    "noteNotebooks",
    "noteRevisions",
    "readingSessions",
];

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AndroidTextInput {
    pub text: String,
    pub page_count: usize,
    pub sentence_index_version: u8,
    pub expected_chunks: usize,
}

#[derive(Debug)]
pub struct AndroidBackup {
    root: Value,
    assets: HashMap<String, Arc<StagedFile>>,
    full_archive: bool,
}

fn file_name(name: &str) -> bool {
    !name.trim().is_empty()
        && name.len() <= 1000
        && ![".", ".."].contains(&name)
        && !name
            .chars()
            .any(|c| c.is_control() || matches!(c, '/' | '\\' | ':'))
}
fn number(row: &Value, field: &str, default: u64, max: u64) -> Result<u64> {
    let value = row.get(field).map_or(Ok(default), |value| {
        value
            .as_u64()
            .ok_or_else(|| format!("Invalid Android {field}."))
    })?;
    if value > max {
        return Err(format!("Android {field} exceeds the desktop limit."));
    }
    Ok(value)
}
fn validate(root: &Value) -> Result<()> {
    let schema = root.get("schema").and_then(Value::as_str).unwrap_or("");
    if !root.is_object()
        || root.get("schema").is_some_and(|value| !value.is_string())
        || !["", "veritas.reader.backup.v1"].contains(&schema)
    {
        return Err("Unsupported Android backup version.".into());
    }
    let documents = root
        .get("documents")
        .and_then(Value::as_array)
        .ok_or("The Android backup has no documents section.")?;
    if documents.len() > 20_000 {
        return Err("The Android backup has too many readings.".into());
    }
    let mut ids = HashSet::new();
    for document in documents {
        let id = document
            .get("id")
            .and_then(Value::as_str)
            .ok_or("An Android reading has no identifier.")?;
        let text = document
            .get("text")
            .and_then(Value::as_str)
            .ok_or("An Android reading is missing its text.")?;
        if !file_name(id) || !ids.insert(id) || text.len() > 32 * 1024 * 1024 {
            return Err("Android readings need unique identifiers and text up to 32 MB.".into());
        }
        if document
            .get("title")
            .is_some_and(|title| title.as_str().is_none_or(|s| s.chars().count() > 1000))
        {
            return Err("An Android reading title is invalid or too long.".into());
        }
        if let Some(original) = document.get("originalFileName") {
            let name = original
                .as_str()
                .ok_or("Invalid Android original-file reference.")?;
            if !name.is_empty() && !name.starts_with("content://") && !file_name(name) {
                return Err("An Android original-file name is unsafe.".into());
            }
        }
        number(document, "pageCount", 0, 10_000)?;
        number(document, "chunkCount", 0, 100_000)?;
        number(document, "currentIndex", 0, 100_000)?;
        if ![1, 2].contains(&number(document, "sentenceIndexVersion", 1, 2)?) {
            return Err(
                "Unsupported Android sentence index; export a fresh backup from Android.".into(),
            );
        }
    }
    for field in SECTIONS {
        if let Some(value) = root.get(field) {
            let values = value
                .as_array()
                .ok_or_else(|| format!("Invalid Android {field} section."))?;
            if values.len() > 20_000 || values.iter().any(|row| !row.is_object()) {
                return Err(format!("Android {field} needs at most 20,000 records."));
            }
        }
    }
    for field in [
        "readerSettings",
        "voiceSettings",
        "narrationSettings",
        "askAiSettings",
        "notesSettings",
        "readingTrackingSettings",
        "docReadingTimesThisMonth",
    ] {
        if root.get(field).is_some_and(|value| !value.is_object()) {
            return Err(format!("Invalid Android {field}."));
        }
    }
    let mut names = HashSet::new();
    let mut paths = HashSet::new();
    if let Some(media) = root.get("noteMedia").and_then(Value::as_array) {
        for item in media {
            let name = item
                .get("fileName")
                .and_then(Value::as_str)
                .ok_or("An Android attachment has no name.")?;
            let reference = item
                .get("path")
                .and_then(Value::as_str)
                .ok_or("An Android attachment has no reference.")?;
            if !file_name(name)
                || !names.insert(name)
                || reference.is_empty()
                || reference.len() > 4096
                || reference.chars().any(char::is_control)
                || !paths.insert(reference)
            {
                return Err("An Android attachment has an unsafe or duplicate reference.".into());
            }
            number(item, "size", 0, crate::note_media::LIMIT as u64)?;
            if let Some(hash) = item.get("sha256") {
                if hash
                    .as_str()
                    .is_none_or(|s| s.len() != 64 || !s.bytes().all(|c| c.is_ascii_hexdigit()))
                {
                    return Err("An Android attachment checksum is invalid.".into());
                }
            }
        }
    }
    Ok(())
}

impl AndroidBackup {
    pub(crate) fn retained_import(&self) -> Result<crate::android_archive::AndroidImport> {
        crate::android_archive::AndroidImport::from_parts(self.root.clone(), &self.assets)
    }
    pub fn read(path: &Path) -> Result<Self> {
        let mut file = File::open(path).map_err(error)?;
        if !file.metadata().map_err(error)?.is_file() {
            return Err("Choose a regular backup file.".into());
        }
        let mut magic = [0_u8; 4];
        let count = file.read(&mut magic).map_err(error)?;
        let full_archive = count >= 2 && magic.starts_with(b"PK");
        let mut assets = HashMap::new();
        let mut bytes = Vec::new();
        if full_archive {
            let mut zip = open_validated_zip(path)?;
            zip.by_name("backup.json")
                .map_err(error)?
                .take(LIMIT as u64 + 1)
                .read_to_end(&mut bytes)
                .map_err(error)?;
            if bytes.len() > LIMIT {
                return Err("Android backup metadata exceeds 64 MB.".into());
            }
            let root: Value = serde_json::from_slice(&bytes).map_err(error)?;
            validate(&root)?;
            for index in 0..zip.len() {
                let entry = zip.by_index(index).map_err(error)?;
                if entry.is_dir() {
                    continue;
                }
                let name = entry.name().map_err(error)?.to_string();
                if name == "backup.json" {
                    continue;
                }
                let limit = if name.starts_with("originals/") {
                    32 * 1024 * 1024
                } else {
                    crate::note_media::LIMIT
                };
                if entry.size() > limit as u64 {
                    return Err("An Android attachment exceeds the desktop size limit.".into());
                }
                assets.insert(name, StagedFile::stage(entry, limit as u64)?);
            }
            let result = Self {
                root,
                assets,
                full_archive,
            };
            result.validate_media()?;
            Ok(result)
        } else {
            file.rewind().map_err(error)?;
            file.take(LIMIT as u64 + 1)
                .read_to_end(&mut bytes)
                .map_err(error)?;
            if bytes.len() > LIMIT {
                return Err("Android backup metadata exceeds 64 MB.".into());
            }
            let root: Value = serde_json::from_slice(&bytes).map_err(error)?;
            validate(&root)?;
            Ok(Self {
                root,
                assets,
                full_archive,
            })
        }
    }
    pub fn metadata(&self) -> &Value {
        &self.root
    }
    pub fn is_full_archive(&self) -> bool {
        self.full_archive
    }
    pub fn document_count(&self) -> usize {
        self.root["documents"].as_array().map_or(0, Vec::len)
    }
    pub fn text_input(&self, index: usize) -> Result<AndroidTextInput> {
        let document = self.root["documents"]
            .get(index)
            .ok_or("The Android reading index is outside this backup.")?;
        Ok(AndroidTextInput {
            text: document["text"]
                .as_str()
                .ok_or("Missing Android text.")?
                .into(),
            page_count: number(document, "pageCount", 0, 10_000)? as usize,
            sentence_index_version: number(document, "sentenceIndexVersion", 1, 2)? as u8,
            expected_chunks: number(document, "chunkCount", 0, 100_000)? as usize,
        })
    }
    pub fn asset_bytes(&self, name: &str, limit: usize) -> Result<Option<Vec<u8>>> {
        if limit > 32 * 1024 * 1024 {
            return Err("An Android original file exceeds 32 MB.".into());
        }
        self.assets
            .get(name)
            .map(|asset| asset.read(limit))
            .transpose()
    }
    pub(crate) fn staged_asset(&self, name: &str) -> Option<Arc<StagedFile>> {
        self.assets.get(name).cloned()
    }
    fn validate_media(&self) -> Result<()> {
        if let Some(media) = self.root.get("noteMedia").and_then(Value::as_array) {
            for item in media {
                let name = format!(
                    "note_media/{}",
                    item["fileName"]
                        .as_str()
                        .ok_or("Missing attachment name.")?
                );
                let bytes = self
                    .asset_bytes(&name, crate::note_media::LIMIT)?
                    .ok_or("An Android note attachment is missing from the archive.")?;
                if bytes.is_empty()
                    || item
                        .get("size")
                        .is_some_and(|size| size.as_u64() != Some(bytes.len() as u64))
                    || item
                        .get("sha256")
                        .and_then(Value::as_str)
                        .is_some_and(|hash| {
                            !hex_digest(Sha256::digest(&bytes)).eq_ignore_ascii_case(hash)
                        })
                {
                    return Err("An Android attachment failed its size or checksum check.".into());
                }
            }
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::{Cursor, Write};
    use zip::{write::SimpleFileOptions, ZipWriter};
    fn json() -> Value {
        serde_json::json!({"schema":"veritas.reader.backup.v1","documents":[{"id":"reading-1","title":"A reading","text":"First. Second.","chunkCount":2,"currentIndex":1}],"queue":[{"documentId":"reading-1"}]})
    }
    fn archive(root: &Value, name: &str, bytes: &[u8]) -> Arc<StagedFile> {
        let input = StagedFile::stage(Cursor::new([]), 0).unwrap();
        let mut zip = ZipWriter::new(File::create(input.path()).unwrap());
        zip.start_file("backup.json", SimpleFileOptions::default())
            .unwrap();
        zip.write_all(&serde_json::to_vec(root).unwrap()).unwrap();
        zip.start_file(name, SimpleFileOptions::default()).unwrap();
        zip.write_all(bytes).unwrap();
        zip.finish().unwrap();
        input
    }
    #[test]
    fn json_retains_sections_and_supplies_the_exact_worker_metadata() {
        let input = StagedFile::stage(
            Cursor::new(serde_json::to_vec(&json()).unwrap()),
            LIMIT as u64,
        )
        .unwrap();
        let parsed = AndroidBackup::read(input.path()).unwrap();
        assert!(!parsed.is_full_archive());
        assert_eq!(parsed.document_count(), 1);
        assert_eq!(parsed.text_input(0).unwrap().sentence_index_version, 1);
        assert_eq!(parsed.text_input(0).unwrap().expected_chunks, 2);
        assert_eq!(parsed.metadata()["queue"][0]["documentId"], "reading-1");
        assert!(parsed.text_input(1).is_err());
        assert!(parsed.asset_bytes("../../other", 8).unwrap().is_none());
    }
    #[test]
    fn unsafe_and_unsupported_metadata_is_rejected_before_conversion() {
        let mut value = json();
        value["documents"][0]["id"] = "../outside".into();
        assert!(validate(&value).is_err());
        value = json();
        let duplicate = value["documents"][0].clone();
        value["documents"].as_array_mut().unwrap().push(duplicate);
        assert!(validate(&value).is_err());
        value = json();
        value["documents"][0]["sentenceIndexVersion"] = 3.into();
        assert!(validate(&value).is_err());
        value = json();
        value["readingSessions"] = serde_json::json!({});
        assert!(validate(&value).is_err());
        value = json();
        value["schema"] = "veritas.reader.backup.v2".into();
        assert!(validate(&value).is_err());
    }
    #[test]
    fn archive_payloads_stay_private_and_are_removed_after_the_backup_is_dropped() {
        let data = b"owned recording fixture";
        let mut root = json();
        root["noteMedia"] = serde_json::json!([{"path":"/data/user/0/app/notes_media/clip.wav","fileName":"0_clip.wav","size":data.len(),"sha256":hex_digest(Sha256::digest(data))}]);
        let input = archive(&root, "note_media/0_clip.wav", data);
        let parsed = AndroidBackup::read(input.path()).unwrap();
        assert!(parsed.is_full_archive());
        assert_eq!(
            parsed
                .asset_bytes("note_media/0_clip.wav", 1024)
                .unwrap()
                .unwrap(),
            data
        );
        let staged = parsed.assets["note_media/0_clip.wav"].path().to_path_buf();
        assert_ne!(staged.file_name().unwrap(), "0_clip.wav");
        drop(parsed);
        assert!(!staged.exists());
        root["noteMedia"][0]["sha256"] = "0".repeat(64).into();
        assert!(AndroidBackup::read(archive(&root, "note_media/0_clip.wav", data).path()).is_err());
        assert!(
            AndroidBackup::read(archive(&root, "originals/../../outside", data).path()).is_err()
        );
    }
}
