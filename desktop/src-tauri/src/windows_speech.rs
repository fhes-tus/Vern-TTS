//! Installed Microsoft voices, explicitly selected separately from neural packages.
use crate::{speech::Audio, storage::Result, voices::Voice};

pub fn valid_id(id: &str) -> bool {
    id.strip_prefix("windows_").is_some_and(|hash| {
        hash.len() == 64
            && hash
                .bytes()
                .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b))
    })
}

#[cfg(not(target_os = "windows"))]
pub fn list() -> Result<Vec<Voice>> {
    Ok(Vec::new())
}
#[cfg(not(target_os = "windows"))]
pub fn synthesize(_: &str, _: &str, _: f32, _: impl Fn() -> bool) -> Result<Audio> {
    Err("Windows voices are available only on Windows. Choose a Studio or Lite voice on this computer.".into())
}

#[cfg(target_os = "windows")]
mod platform {
    use super::*;
    use crate::storage::{error, hex_digest};
    use sha2::{Digest, Sha256};
    use std::{
        sync::{
            atomic::{AtomicBool, Ordering},
            mpsc, Arc, OnceLock,
        },
        time::{Duration, Instant},
    };
    use windows::{
        core::HSTRING,
        Media::SpeechSynthesis::{SpeechSynthesizer, VoiceGender, VoiceInformation},
        Storage::Streams::DataReader,
        Win32::System::WinRT::{RoInitialize, RoUninitialize, RO_INIT_MULTITHREADED},
    };
    struct Apartment;
    impl Apartment {
        fn new() -> Result<Self> {
            // These functions are called only on native blocking workers, never the UI thread.
            unsafe { RoInitialize(RO_INIT_MULTITHREADED) }.map_err(error)?;
            Ok(Self)
        }
    }
    impl Drop for Apartment {
        fn drop(&mut self) {
            unsafe { RoUninitialize() };
        }
    }
    enum Request {
        List(mpsc::Sender<Result<Vec<Voice>>>),
        Speak {
            id: String,
            text: String,
            rate: f32,
            cancelled: Arc<AtomicBool>,
            reply: mpsc::Sender<Result<Audio>>,
        },
    }
    fn service() -> Result<mpsc::SyncSender<Request>> {
        static SERVICE: OnceLock<Result<mpsc::SyncSender<Request>>> = OnceLock::new();
        SERVICE
            .get_or_init(|| {
                let (sender, receiver) = mpsc::sync_channel::<Request>(8);
                std::thread::Builder::new()
                    .name("vern-windows-speech".into())
                    .spawn(move || {
                        // Keep WinRT and its cached activation factories in one live MTA.
                        // No COM object leaves this thread; callers receive plain metadata/PCM.
                        let apartment = Apartment::new();
                        for request in receiver {
                            match request {
                                Request::List(reply) => {
                                    let _ = reply.send(
                                        apartment
                                            .as_ref()
                                            .map_err(Clone::clone)
                                            .and_then(|_| list_inner()),
                                    );
                                }
                                Request::Speak {
                                    id,
                                    text,
                                    rate,
                                    cancelled,
                                    reply,
                                } => {
                                    let _ = reply.send(
                                        apartment.as_ref().map_err(Clone::clone).and_then(|_| {
                                            render(&id, &text, rate, || {
                                                !cancelled.load(Ordering::SeqCst)
                                            })
                                        }),
                                    );
                                }
                            }
                        }
                    })
                    .map_err(error)?;
                Ok(sender)
            })
            .clone()
    }
    pub fn list() -> Result<Vec<Voice>> {
        let (reply, result) = mpsc::channel();
        service()?
            .try_send(Request::List(reply))
            .map_err(|_| "Windows voice service is busy. Try again.".to_string())?;
        result
            .recv_timeout(Duration::from_secs(5))
            .map_err(|_| "Windows voice discovery timed out. Try again.".to_string())?
    }
    pub fn synthesize(
        id: &str,
        text: &str,
        rate: f32,
        current: impl Fn() -> bool,
    ) -> Result<Audio> {
        if !valid_id(id)
            || text.trim().is_empty()
            || text.len() > 4096
            || !(0.5..=2.0).contains(&rate)
        {
            return Err("Invalid Windows speech request.".into());
        }
        let (reply, result) = mpsc::channel();
        let cancelled = Arc::new(AtomicBool::new(false));
        service()?
            .try_send(Request::Speak {
                id: id.into(),
                text: text.into(),
                rate,
                cancelled: cancelled.clone(),
                reply,
            })
            .map_err(|_| "Windows voice service is busy. Try again.".to_string())?;
        let deadline = Instant::now() + Duration::from_secs(45);
        loop {
            if !current() {
                cancelled.store(true, Ordering::SeqCst);
                return Err("Speech request cancelled.".into());
            }
            if Instant::now() >= deadline {
                cancelled.store(true, Ordering::SeqCst);
                return Err("Windows speech timed out. Try another voice.".into());
            }
            match result.recv_timeout(Duration::from_millis(10)) {
                Ok(audio) => return audio,
                Err(mpsc::RecvTimeoutError::Timeout) => {}
                Err(mpsc::RecvTimeoutError::Disconnected) => {
                    return Err("Windows speech worker stopped.".into())
                }
            }
        }
    }
    fn trace(step: &str) {
        if cfg!(debug_assertions) && std::env::var_os("VERN_SPEECH_TRACE").is_some() {
            eprintln!("Windows speech: {step}");
        }
    }
    fn id(voice: &VoiceInformation) -> Result<String> {
        Ok(format!(
            "windows_{}",
            hex_digest(Sha256::digest(
                voice.Id().map_err(error)?.to_string().as_bytes()
            ))
        ))
    }
    fn list_inner() -> Result<Vec<Voice>> {
        let voices = SpeechSynthesizer::AllVoices().map_err(error)?;
        let mut result = Vec::new();
        for n in 0..voices.Size().map_err(error)?.min(128) {
            let voice = voices.GetAt(n).map_err(error)?;
            result.push(Voice {
                id: id(&voice)?,
                name: voice.DisplayName().map_err(error)?.to_string(),
                family: "Windows".into(),
                engine: "Microsoft".into(),
                size: "Installed on this PC".into(),
                language: voice.Language().map_err(error)?.to_string(),
                gender: if voice.Gender().map_err(error)? == VoiceGender::Female {
                    "Female"
                } else {
                    "Male"
                }
                .into(),
                state: "installed".into(),
                downloaded: 0,
                total: 0,
                error: None,
            });
        }
        result.sort_by(|a, b| {
            a.language
                .cmp(&b.language)
                .then_with(|| a.name.cmp(&b.name))
        });
        Ok(result)
    }
    fn render(voice_id: &str, text: &str, rate: f32, current: impl Fn() -> bool) -> Result<Audio> {
        if !current() {
            return Err("Speech request cancelled.".into());
        }
        trace("find voice");
        let voices = SpeechSynthesizer::AllVoices().map_err(error)?;
        let mut selected = None;
        for n in 0..voices.Size().map_err(error)?.min(128) {
            let voice = voices.GetAt(n).map_err(error)?;
            if id(&voice)? == voice_id {
                selected = Some(voice);
                break;
            }
        }
        let voice = selected.ok_or("This Windows voice is no longer installed. Refresh Voice Studio and choose another voice.")?;
        trace("create synthesizer");
        let synth = SpeechSynthesizer::new().map_err(error)?;
        trace("select voice and rate");
        synth.SetVoice(&voice).map_err(error)?;
        synth
            .Options()
            .and_then(|options| options.SetSpeakingRate(f64::from(rate)))
            .map_err(error)?;
        trace("synthesize");
        let operation = synth
            .SynthesizeTextToStreamAsync(&HSTRING::from(text))
            .map_err(error)?;
        let deadline = Instant::now() + Duration::from_secs(30);
        while operation.Status().map_err(error)?.0 == 0 {
            if !current() || Instant::now() >= deadline {
                let _ = operation.Cancel();
                let _ = synth.Close();
                return Err(if current() {
                    "Windows speech timed out. Try another voice."
                } else {
                    "Speech request cancelled."
                }
                .into());
            }
            std::thread::sleep(Duration::from_millis(10));
        }
        trace("get stream");
        let stream = operation.GetResults().map_err(error)?;
        let size = stream.Size().map_err(error)?;
        if size == 0 || size > 16_000_000 {
            return Err("Windows returned an empty or oversized speech stream.".into());
        }
        trace("read stream");
        let reader = DataReader::CreateDataReader(&stream.GetInputStreamAt(0).map_err(error)?)
            .map_err(error)?;
        let loading = reader.LoadAsync(size as u32).map_err(error)?;
        while loading.Status().map_err(error)?.0 == 0 {
            if !current() || Instant::now() >= deadline {
                let _ = loading.Cancel();
                let _ = synth.Close();
                return Err("Windows speech read was cancelled or timed out.".into());
            }
            std::thread::sleep(Duration::from_millis(10));
        }
        if loading.GetResults().map_err(error)? != size as u32 {
            return Err("Windows returned incomplete speech audio.".into());
        }
        let mut bytes = vec![0; size as usize];
        reader.ReadBytes(&mut bytes).map_err(error)?;
        trace("close stream");
        let _ = reader.Close();
        let _ = stream.Close();
        let _ = synth.Close();
        if !current() {
            return Err("Speech request cancelled.".into());
        }
        trace("decode PCM");
        decode_pcm(&bytes)
    }
}
#[cfg(target_os = "windows")]
pub use platform::{list, synthesize};

// Keep the OS output on Vern's PCM playback path: pause, selection, queue, sleep
// timer, highlights and WAV export all use the same controls and cancellation.
#[cfg(any(target_os = "windows", test))]
fn decode_pcm(bytes: &[u8]) -> Result<Audio> {
    if bytes.len() < 12
        || bytes.len() > 16_000_000
        || &bytes[..4] != b"RIFF"
        || &bytes[8..12] != b"WAVE"
    {
        return Err("Windows returned an unsupported speech stream.".into());
    }
    let mut format = None;
    let mut data = None;
    let mut at = 12;
    while at + 8 <= bytes.len() {
        let length = u32::from_le_bytes(bytes[at + 4..at + 8].try_into().unwrap()) as usize;
        let start = at + 8;
        let end = start
            .checked_add(length)
            .filter(|end| *end <= bytes.len())
            .ok_or("Truncated Windows speech audio.")?;
        if &bytes[at..at + 4] == b"fmt " && length >= 16 {
            let read16 =
                |n| u16::from_le_bytes(bytes[start + n..start + n + 2].try_into().unwrap());
            format = Some((
                read16(0),
                read16(2),
                u32::from_le_bytes(bytes[start + 4..start + 8].try_into().unwrap()),
                read16(12),
                read16(14),
            ));
        } else if &bytes[at..at + 4] == b"data" {
            data = Some(&bytes[start..end]);
        }
        at = end + (length & 1);
    }
    let (encoding, channels, sample_rate, align, bits) =
        format.ok_or("Windows speech has no audio format.")?;
    let data = data.ok_or("Windows speech has no audio samples.")?;
    if encoding != 1
        || !(1..=2).contains(&channels)
        || bits != 16
        || align != channels * 2
        || !(8000..=48000).contains(&sample_rate)
        || data.is_empty()
        || !data.len().is_multiple_of(usize::from(align))
        || data.len() / usize::from(align) > sample_rate as usize * 120
    {
        return Err("Windows speech must be bounded 16-bit PCM audio.".into());
    }
    let samples = data
        .chunks_exact(usize::from(align))
        .map(|frame| {
            frame
                .as_chunks::<2>()
                .0
                .iter()
                .map(|sample| f32::from(i16::from_le_bytes(*sample)) / 32768.0)
                .sum::<f32>()
                / f32::from(channels)
        })
        .collect();
    Ok(Audio {
        samples,
        sample_rate: sample_rate as i32,
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn ids_and_pcm_are_bounded_and_validated() {
        assert!(valid_id(&format!("windows_{}", "a".repeat(64))));
        assert!(!valid_id("windows_../../file"));
        let mut bytes = crate::transfer::wav_bytes(Audio {
            samples: vec![-0.5, 0.25, 0.75],
            sample_rate: 24000,
        })
        .unwrap();
        let audio = decode_pcm(&bytes).unwrap();
        assert_eq!(audio.sample_rate, 24000);
        assert!((audio.samples[2] - 0.75).abs() < 0.001);
        bytes[40..44].copy_from_slice(&u32::MAX.to_le_bytes());
        assert!(decode_pcm(&bytes).is_err());
        assert!(decode_pcm(b"not audio").is_err());
    }
}
