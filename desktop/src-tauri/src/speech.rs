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
        let d = descriptor(voice_id)?;
        if !voices.installed(d) {
            return Err("Download this voice in Voice Studio before listening.".into());
        }
        let mut cache = self.engine.lock().map_err(error)?;
        if !is_current() {
            return Err("Speech request cancelled.".into());
        }
        if cache.as_ref().is_none_or(|(id, _)| id != voice_id) {
            // Drop previous graph first to bound memory use when switching families.
            *cache = None;
            let engine = voices
                .verify(d)
                .and_then(|()| load_engine(&voices.root.join(d.id), d))
                .inspect_err(|message| voices.invalidate(d, message.clone()))?;
            *cache = Some((voice_id.into(), engine));
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
