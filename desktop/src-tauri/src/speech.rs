use crate::{
    storage::{error, Result},
    voices::{descriptor, load_engine, Voices},
};
use serde::Serialize;
use sherpa_onnx::{GenerationConfig, OfflineTts};
use std::sync::{
    atomic::{AtomicU64, Ordering},
    Arc, Mutex,
};
use unicode_normalization::UnicodeNormalization;

pub struct Speech {
    pub generation: Arc<AtomicU64>,
    engine: Mutex<Option<(String, OfflineTts)>>,
}
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Audio {
    pub samples: Vec<f32>,
    pub sample_rate: i32,
}
impl Default for Speech {
    fn default() -> Self {
        Self {
            generation: Arc::new(AtomicU64::new(0)),
            engine: Mutex::new(None),
        }
    }
}
impl Speech {
    pub fn cancel(&self, generation: u64) {
        self.generation.fetch_max(generation, Ordering::SeqCst);
    }
    pub fn synthesize(
        &self,
        voices: &Voices,
        voice_id: &str,
        text: &str,
        rate: f32,
        generation: u64,
    ) -> Result<Audio> {
        if text.len() > 4096 || text.trim().is_empty() || !(0.5..=2.0).contains(&rate) {
            return Err("Invalid speech chunk or rate.".into());
        }
        let is_current = || self.generation.load(Ordering::SeqCst) == generation;
        if !is_current() {
            return Err("Speech request cancelled.".into());
        }
        if crate::windows_speech::valid_id(voice_id) {
            let mut cache = self.engine.lock().map_err(error)?;
            *cache = None;
            if !is_current() {
                return Err("Speech request cancelled.".into());
            }
            return crate::windows_speech::synthesize(voice_id, text, rate, is_current);
        }
        let d = descriptor(voice_id)?;
        if !voices.installed(d) {
            return Err("Download this voice in Voice Studio before listening.".into());
        }
        let mut cache = self.engine.lock().map_err(error)?;
        if !is_current() {
            return Err("Speech request cancelled.".into());
        }
        let engine_key = format!("{}:{}", d.id, d.sid >= 20);
        if cache.as_ref().is_none_or(|(id, _)| id != &engine_key) {
            // Drop previous graph first to bound memory use when switching families.
            *cache = None;
            let engine = voices
                .verify(d)
                .and_then(|()| load_engine(&voices.root.join(d.id), d))
                .inspect_err(|message| voices.invalidate(d, message.clone()))?;
            *cache = Some((engine_key, engine));
        }
        let clean: String = text
            .nfc()
            .filter(|c| {
                !c.is_control() && !['\u{00ad}', '\u{200b}', '\u{2060}', '\u{feff}'].contains(c)
            })
            .collect();
        let clean = clean.split_whitespace().collect::<Vec<_>>().join(" ");
        let epoch = self.generation.clone();
        let config = GenerationConfig {
            sid: d.sid,
            speed: rate,
            ..Default::default()
        };
        let audio = cache
            .as_ref()
            .unwrap()
            .1
            .generate_with_config(
                &clean,
                &config,
                Some(move |_: &[f32], _: f32| epoch.load(Ordering::SeqCst) == generation),
            )
            .ok_or("Voice could not synthesize this passage.")?;
        if !is_current() {
            return Err("Speech request cancelled.".into());
        }
        if audio.samples().is_empty() {
            return Err("Voice returned no audio.".into());
        }
        Ok(Audio {
            samples: audio.samples().iter().map(|s| s.clamp(-1.0, 1.0)).collect(),
            sample_rate: audio.sample_rate(),
        })
    }
}

pub fn decode_pcm(bytes: &[u8]) -> Result<Audio> {
    if bytes.len() < 12
        || bytes.len() > 16_000_000
        || &bytes[..4] != b"RIFF"
        || &bytes[8..12] != b"WAVE"
    {
        return Err("Audio stream is not a valid RIFF WAVE container.".into());
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
            .ok_or("Truncated speech audio.")?;
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
        format.ok_or("Speech audio has no format chunk.")?;
    let data = data.ok_or("Speech audio has no data chunk.")?;
    if encoding != 1
        || !(1..=2).contains(&channels)
        || bits != 16
        || align != channels * 2
        || !(8000..=48000).contains(&sample_rate)
        || data.is_empty()
        || !data.len().is_multiple_of(usize::from(align))
        || data.len() / usize::from(align) > sample_rate as usize * 120
    {
        return Err("Speech audio must be bounded 16-bit PCM.".into());
    }
    let samples = data
        .chunks_exact(usize::from(align))
        .map(|frame| {
            let ch_samples = frame
                .as_chunks::<2>()
                .0
                .iter()
                .map(|s| f32::from(i16::from_le_bytes([s[0], s[1]])) / 32768.0)
                .sum::<f32>();
            (ch_samples / f32::from(channels)).clamp(-1.0, 1.0)
        })
        .collect();
    Ok(Audio {
        samples,
        sample_rate: sample_rate as i32,
    })
}

pub fn decode_mp3(bytes: &[u8]) -> Result<Audio> {
    use minimp3::{Decoder, Error, Frame};
    use std::io::Cursor;

    let mut decoder = Decoder::new(Cursor::new(bytes));
    let mut all_samples = Vec::new();
    let mut sample_rate = 0;

    loop {
        match decoder.next_frame() {
            Ok(Frame {
                data,
                sample_rate: sr,
                channels: ch,
                ..
            }) => {
                if sample_rate == 0 {
                    sample_rate = sr;
                }
                if ch == 1 {
                    all_samples.extend(
                        data.into_iter()
                            .map(|s| (s as f32 / 32768.0).clamp(-1.0, 1.0)),
                    );
                } else if ch >= 2 {
                    for pair in data.chunks_exact(ch) {
                        let avg = (pair[0] as f32 + pair[1] as f32) / (2.0 * 32768.0);
                        all_samples.push(avg.clamp(-1.0, 1.0));
                    }
                }
            }
            Err(Error::Eof) => break,
            Err(e) => return Err(format!("MP3 decoding failed: {e}")),
        }
    }

    if all_samples.is_empty() || sample_rate == 0 {
        return Err("Decoded MP3 contained no audio samples.".into());
    }

    Ok(Audio {
        samples: all_samples,
        sample_rate,
    })
}

pub fn decode_audio(bytes: &[u8]) -> Result<Audio> {
    if bytes.len() >= 12 && &bytes[0..4] == b"RIFF" && &bytes[8..12] == b"WAVE" {
        decode_pcm(bytes)
    } else {
        decode_mp3(bytes)
    }
}

pub fn resample(samples: &[f32], source_rate: i32, target_rate: i32) -> Vec<f32> {
    if source_rate == target_rate || samples.is_empty() || source_rate <= 0 || target_rate <= 0 {
        return samples.to_vec();
    }
    let ratio = source_rate as f64 / target_rate as f64;
    let target_len = (samples.len() as f64 / ratio).round() as usize;
    let mut out = Vec::with_capacity(target_len);
    for i in 0..target_len {
        let src_idx = i as f64 * ratio;
        let idx0 = src_idx.floor() as usize;
        let frac = (src_idx - idx0 as f64) as f32;
        let s0 = samples.get(idx0).copied().unwrap_or(0.0);
        let s1 = samples.get(idx0 + 1).copied().unwrap_or(s0);
        out.push(s0 + frac * (s1 - s0));
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn resample_preserves_length_when_rates_match() {
        let input = vec![0.1, 0.2, 0.3, 0.4];
        let out = resample(&input, 24000, 24000);
        assert_eq!(out, input);
    }

    #[test]
    fn resample_interpolates_smoothly() {
        let input = vec![0.0, 1.0];
        let out = resample(&input, 1000, 2000);
        assert_eq!(out.len(), 4);
        assert!((out[0] - 0.0).abs() < 1e-4);
        assert!((out[1] - 0.5).abs() < 1e-4);
    }
}
