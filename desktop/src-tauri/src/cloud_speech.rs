use crate::{
    speech::{decode_audio, Audio},
    storage::{error, hex_digest, Result},
};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::time::Duration;

pub fn valid_id(id: &str) -> bool {
    id == "cloud" || id.starts_with("cloud_") || id.starts_with("cloud:")
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum CloudVoiceProvider {
    #[default]
    Elevenlabs,
    Cartesia,
    Minimax,
    Kitten,
    Azure,
    GoogleCloud,
    Custom,
}

impl CloudVoiceProvider {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Elevenlabs => "ELEVENLABS",
            Self::Cartesia => "CARTESIA",
            Self::Minimax => "MINIMAX",
            Self::Kitten => "KITTEN",
            Self::Azure => "AZURE",
            Self::GoogleCloud => "GOOGLE_CLOUD",
            Self::Custom => "CUSTOM",
        }
    }

    pub fn display_name(&self) -> &'static str {
        match self {
            Self::Elevenlabs => "ElevenLabs",
            Self::Cartesia => "Cartesia",
            Self::Minimax => "MiniMax",
            Self::Kitten => "KittenML",
            Self::Azure => "Microsoft Azure Speech",
            Self::GoogleCloud => "Google Cloud Text-to-Speech",
            Self::Custom => "OpenAI-compatible server",
        }
    }

    pub fn default_endpoint(&self) -> &'static str {
        match self {
            Self::Elevenlabs => "https://api.elevenlabs.io/v1/text-to-speech",
            Self::Cartesia => "https://api.cartesia.ai/tts/bytes",
            Self::Minimax => "https://api.minimax.io/v1/t2a_v2",
            Self::Kitten => "https://api.kittenml.com/v1/audio/speech",
            Self::Azure => "",
            Self::GoogleCloud => "https://texttospeech.googleapis.com/v1/text:synthesize",
            Self::Custom => "",
        }
    }

    pub fn default_model(&self) -> &'static str {
        match self {
            Self::Elevenlabs => "eleven_v4",
            Self::Cartesia => "sonic-3.6",
            Self::Minimax => "speech-2.8-hd",
            Self::Kitten => "kitten-tts-2-latest",
            Self::Azure => "neural",
            Self::GoogleCloud => "v1",
            Self::Custom => "tts-1",
        }
    }

    pub fn default_voice(&self) -> &'static str {
        match self {
            Self::Elevenlabs => "JBFqnCBsd6RMkjVDRZzb",
            Self::Cartesia => "db6b0ed5-d5d3-463d-ae85-518a07d3c2b4",
            Self::Minimax => "English_expressive_narrator",
            Self::Kitten => "07 — Saoirse — Joyful Irish Female",
            Self::Azure => "en-US-JennyNeural",
            Self::GoogleCloud => "en-US-Neural2-F",
            Self::Custom => "alloy",
        }
    }

    pub fn default_language(&self) -> &'static str {
        match self {
            Self::Azure | Self::GoogleCloud => "en-US",
            _ => "",
        }
    }

    pub fn website(&self) -> &'static str {
        match self {
            Self::Elevenlabs => "https://elevenlabs.io/docs/overview/models",
            Self::Cartesia => "https://docs.cartesia.ai/api-reference/tts/bytes",
            Self::Minimax => "https://platform.minimax.io/docs/api-reference/speech-t2a-http",
            Self::Kitten => "https://kittenml.com/",
            Self::Azure => "https://learn.microsoft.com/azure/ai-services/speech-service/get-started-text-to-speech",
            Self::GoogleCloud => "https://cloud.google.com/text-to-speech/docs/get-started",
            Self::Custom => "https://github.com/debpalash/VoiceStudio",
        }
    }

    pub fn has_voice_catalog(&self) -> bool {
        matches!(self, Self::Azure | Self::GoogleCloud)
    }

    pub fn credential_name(&self, endpoint: &str, region: &str) -> String {
        format!(
            "tts_{}{}",
            self.as_str(),
            match self {
                Self::Custom => format!("_{}", hex_digest(Sha256::digest(endpoint.as_bytes()))),
                Self::Azure => format!("_{}", hex_digest(Sha256::digest(region.as_bytes()))),
                _ => String::new(),
            }
        )
    }
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct CloudVoiceConfig {
    pub enabled: bool,
    pub provider: CloudVoiceProvider,
    pub endpoint: String,
    pub model: String,
    pub voice: String,
    pub language: String,
    pub wifi_only: bool,
    pub fallback_to_local: bool,
    pub cache_audio: bool,
    pub timeout_seconds: u32,
    pub retries: u32,
    pub price_per_million: f64,
    pub region: String,
}

impl Default for CloudVoiceConfig {
    fn default() -> Self {
        let p = CloudVoiceProvider::Elevenlabs;
        Self {
            enabled: false,
            provider: p,
            endpoint: p.default_endpoint().into(),
            model: p.default_model().into(),
            voice: p.default_voice().into(),
            language: p.default_language().into(),
            wifi_only: true,
            fallback_to_local: false,
            cache_audio: false,
            timeout_seconds: 30,
            retries: 0,
            price_per_million: 0.0,
            region: String::new(),
        }
    }
}

impl CloudVoiceConfig {
    pub fn for_provider(provider: CloudVoiceProvider) -> Self {
        Self {
            enabled: false,
            provider,
            endpoint: provider.default_endpoint().into(),
            model: provider.default_model().into(),
            voice: provider.default_voice().into(),
            language: provider.default_language().into(),
            wifi_only: true,
            fallback_to_local: false,
            cache_audio: false,
            timeout_seconds: 30,
            retries: 0,
            price_per_million: 0.0,
            region: if provider == CloudVoiceProvider::Azure {
                "eastus".into()
            } else {
                String::new()
            },
        }
    }

    pub fn speech_endpoint(&self) -> String {
        if self.provider == CloudVoiceProvider::Azure {
            format!(
                "https://{}.tts.speech.microsoft.com/cognitiveservices/v1",
                self.region.trim().to_lowercase()
            )
        } else {
            self.endpoint.clone()
        }
    }

    pub fn checked(&self) -> Result<()> {
        if self.provider == CloudVoiceProvider::Azure {
            let region = self.region.trim().to_lowercase();
            if region.is_empty()
                || region.len() > 40
                || !region
                    .chars()
                    .all(|c| c.is_ascii_alphanumeric() || c == '-')
            {
                return Err("Azure Speech requires a valid region name (e.g. 'eastus').".into());
            }
        }
        let endpoint = self.speech_endpoint();
        if endpoint.is_empty() || endpoint.len() > 2048 {
            return Err("Speech endpoint URL must be between 1 and 2048 characters.".into());
        }
        if !endpoint.starts_with("https://")
            && !(self.provider == CloudVoiceProvider::Custom && endpoint.starts_with("http://"))
        {
            return Err("Speech endpoint must use HTTPS (or HTTP for local servers).".into());
        }
        if self.model.trim().is_empty() || self.model.len() > 200 {
            return Err("Model identifier must be between 1 and 200 characters.".into());
        }
        if self.voice.trim().is_empty() || self.voice.len() > 200 {
            return Err("Voice identifier must be between 1 and 200 characters.".into());
        }
        if !(5..=120).contains(&self.timeout_seconds) {
            return Err("Timeout must be between 5 and 120 seconds.".into());
        }
        if self.retries > 2 {
            return Err("Retries must be between 0 and 2.".into());
        }
        if self.price_per_million < 0.0 || !self.price_per_million.is_finite() {
            return Err("Price per million characters must be 0 or greater.".into());
        }
        Ok(())
    }
}

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CloudVoiceUsage {
    pub characters: u64,
    pub requests: u64,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CatalogVoice {
    pub id: String,
    pub language: String,
    pub label: String,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CloudVoiceStatus {
    pub config: CloudVoiceConfig,
    pub has_key: bool,
    pub usage: CloudVoiceUsage,
}

pub struct CloudSpeechManager {
    client: reqwest::blocking::Client,
}

impl Default for CloudSpeechManager {
    fn default() -> Self {
        let client = reqwest::blocking::Client::builder()
            .timeout(Duration::from_secs(60))
            .build()
            .unwrap_or_else(|_| reqwest::blocking::Client::new());
        Self { client }
    }
}

fn escape_xml(text: &str) -> String {
    let mut out = String::with_capacity(text.len());
    for c in text.chars() {
        match c {
            '&' => out.push_str("&amp;"),
            '<' => out.push_str("&lt;"),
            '>' => out.push_str("&gt;"),
            '"' => out.push_str("&quot;"),
            '\'' => out.push_str("&apos;"),
            _ => out.push(c),
        }
    }
    out
}

fn url_encode(input: &str) -> String {
    let mut out = String::new();
    for b in input.bytes() {
        if b.is_ascii_alphanumeric() || b == b'-' || b == b'_' || b == b'.' || b == b'~' {
            out.push(b as char);
        } else {
            use std::fmt::Write;
            let _ = write!(out, "%{:02X}", b);
        }
    }
    out
}

impl CloudSpeechManager {
    pub fn synthesize(
        &self,
        config: &CloudVoiceConfig,
        key: &str,
        text: &str,
        is_current: impl Fn() -> bool,
    ) -> Result<Audio> {
        config.checked()?;
        if text.trim().is_empty() || text.len() > 4096 {
            return Err("Text passage must be between 1 and 4096 characters.".into());
        }
        if !is_current() {
            return Err("Speech request cancelled.".into());
        }

        let timeout = Duration::from_secs(u64::from(config.timeout_seconds.clamp(5, 120)));
        let mut attempts = 0;
        let max_attempts = 1 + config.retries.min(2);

        while attempts < max_attempts {
            attempts += 1;
            if !is_current() {
                return Err("Speech request cancelled.".into());
            }

            let mut req = match config.provider {
                CloudVoiceProvider::Elevenlabs => {
                    let url = if config.model == "eleven_v4" {
                        "https://api.elevenlabs.io/v1/text-to-dialogue/stream?output_format=mp3_44100_128".to_string()
                    } else {
                        format!(
                            "{}/{}/stream?output_format=mp3_44100_128",
                            config.endpoint.trim_end_matches('/'),
                            url_encode(&config.voice)
                        )
                    };
                    let body = if config.model == "eleven_v4" {
                        json!({
                            "inputs": [{ "text": text, "voice_id": config.voice }],
                            "model_id": config.model
                        })
                    } else {
                        json!({
                            "text": text,
                            "model_id": config.model
                        })
                    };
                    let mut b = self
                        .client
                        .post(&url)
                        .header("Content-Type", "application/json")
                        .header("Accept", "audio/*, application/octet-stream")
                        .body(serde_json::to_string(&body).map_err(error)?);
                    if !key.is_empty() {
                        b = b.header("xi-api-key", key);
                    }
                    b
                }
                CloudVoiceProvider::Cartesia => {
                    let body = json!({
                        "transcript": text,
                        "model_id": config.model,
                        "voice": config.voice,
                        "output_format": {
                            "container": "wav",
                            "encoding": "pcm_s16le",
                            "sample_rate": 44100
                        }
                    });
                    let mut b = self
                        .client
                        .post(&config.endpoint)
                        .header("Content-Type", "application/json")
                        .header("Cartesia-Version", "2026-08-14")
                        .body(serde_json::to_string(&body).map_err(error)?);
                    if !key.is_empty() {
                        b = b.header("Authorization", format!("Bearer {key}"));
                    }
                    b
                }
                CloudVoiceProvider::Minimax => {
                    let body = json!({
                        "text": text,
                        "model": config.model,
                        "stream": false,
                        "output_format": "hex",
                        "language_boost": "auto",
                        "voice_setting": {
                            "voice_id": config.voice,
                            "speed": 1,
                            "vol": 1,
                            "pitch": 0
                        },
                        "audio_setting": {
                            "format": "mp3",
                            "sample_rate": 32000,
                            "bitrate": 128000,
                            "channel": 1
                        }
                    });
                    let mut b = self
                        .client
                        .post(&config.endpoint)
                        .header("Content-Type", "application/json")
                        .body(serde_json::to_string(&body).map_err(error)?);
                    if !key.is_empty() {
                        b = b.header("Authorization", format!("Bearer {key}"));
                    }
                    b
                }
                CloudVoiceProvider::Kitten => {
                    let voice_val = if config.voice.starts_with("voice_") {
                        json!({ "id": config.voice })
                    } else {
                        json!(config.voice)
                    };
                    let body = json!({
                        "input": text,
                        "model": config.model,
                        "voice": voice_val,
                        "response_format": "mp3",
                        "speed": 1
                    });
                    let mut b = self
                        .client
                        .post(&config.endpoint)
                        .header("Content-Type", "application/json")
                        .body(serde_json::to_string(&body).map_err(error)?);
                    if !key.is_empty() {
                        b = b.header("Authorization", format!("Bearer {key}"));
                    }
                    b
                }
                CloudVoiceProvider::Azure => {
                    let lang = if config.language.is_empty() {
                        "en-US"
                    } else {
                        &config.language
                    };
                    let ssml = format!(
                        r#"<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="{}"><voice name="{}">{}</voice></speak>"#,
                        lang,
                        config.voice,
                        escape_xml(text)
                    );
                    let mut b = self
                        .client
                        .post(config.speech_endpoint())
                        .header("Content-Type", "application/ssml+xml")
                        .header("X-Microsoft-OutputFormat", "riff-24khz-16bit-mono-pcm")
                        .body(ssml);
                    if !key.is_empty() {
                        b = b.header("Ocp-Apim-Subscription-Key", key);
                    }
                    b
                }
                CloudVoiceProvider::GoogleCloud => {
                    let lang = if config.language.is_empty() {
                        "en-US"
                    } else {
                        &config.language
                    };
                    let body = json!({
                        "input": { "text": text },
                        "voice": {
                            "languageCode": lang,
                            "name": config.voice
                        },
                        "audioConfig": {
                            "audioEncoding": "LINEAR16",
                            "sampleRateHertz": 24000
                        }
                    });
                    let mut b = self
                        .client
                        .post(&config.endpoint)
                        .header("Content-Type", "application/json")
                        .body(serde_json::to_string(&body).map_err(error)?);
                    if !key.is_empty() {
                        b = b.header("X-Goog-Api-Key", key);
                    }
                    b
                }
                CloudVoiceProvider::Custom => {
                    let body = json!({
                        "input": text,
                        "model": config.model,
                        "voice": config.voice,
                        "response_format": "wav",
                        "speed": 1
                    });
                    let mut b = self
                        .client
                        .post(&config.endpoint)
                        .header("Content-Type", "application/json")
                        .body(serde_json::to_string(&body).map_err(error)?);
                    if !key.is_empty() {
                        b = b.header("Authorization", format!("Bearer {key}"));
                    }
                    b
                }
            };

            req = req.timeout(timeout);

            let res = match req.send() {
                Ok(r) => r,
                Err(_e) if attempts < max_attempts => {
                    std::thread::sleep(Duration::from_millis(1000 * attempts as u64));
                    continue;
                }
                Err(e) => return Err(format!("Network connection failed: {e}")),
            };

            let status = res.status();
            if status == reqwest::StatusCode::TOO_MANY_REQUESTS && attempts < max_attempts {
                std::thread::sleep(Duration::from_millis(1500 * attempts as u64));
                continue;
            }

            if !status.is_success() {
                let code = status.as_u16();
                return Err(match code {
                    401 | 403 => "Invalid or unauthorized API key for this voice provider.".into(),
                    402 | 429 => {
                        "Usage quota exceeded or rate limit reached for this voice provider.".into()
                    }
                    400 => {
                        "Provider rejected request parameters. Check model, voice ID, and language."
                            .into()
                    }
                    500..=599 => "Voice provider server error. Please try again later.".into(),
                    _ => format!("Voice request failed with HTTP status {status}."),
                });
            }

            if !is_current() {
                return Err("Speech request cancelled.".into());
            }

            let raw_bytes = res.bytes().map_err(error)?;
            if raw_bytes.is_empty() {
                return Err("Provider returned an empty audio response.".into());
            }

            // Extract audio bytes based on provider format
            let audio_bytes = if config.provider == CloudVoiceProvider::Minimax {
                let val: Value = serde_json::from_slice(&raw_bytes)
                    .map_err(|e| format!("Invalid MiniMax JSON response: {e}"))?;
                let status_code = val["base_resp"]["status_code"].as_i64().unwrap_or(-1);
                if status_code != 0 {
                    let msg = val["base_resp"]["status_msg"]
                        .as_str()
                        .unwrap_or("unknown error");
                    return Err(format!("MiniMax error ({status_code}): {msg}"));
                }
                let hex_str = val["data"]["audio"]
                    .as_str()
                    .ok_or("MiniMax response missing audio hex payload.")?;
                if hex_str.len() % 2 != 0 {
                    return Err("Invalid hex audio payload length.".into());
                }
                let mut decoded = Vec::with_capacity(hex_str.len() / 2);
                for i in (0..hex_str.len()).step_by(2) {
                    let byte = u8::from_str_radix(&hex_str[i..i + 2], 16)
                        .map_err(|_| "Invalid hex in audio payload.")?;
                    decoded.push(byte);
                }
                decoded
            } else if config.provider == CloudVoiceProvider::GoogleCloud {
                let val: Value = serde_json::from_slice(&raw_bytes)
                    .map_err(|e| format!("Invalid Google Cloud TTS JSON response: {e}"))?;
                let b64 = val["audioContent"]
                    .as_str()
                    .ok_or("Google Cloud TTS response missing audioContent.")?;
                use base64::Engine;
                base64::engine::general_purpose::STANDARD
                    .decode(b64)
                    .map_err(|e| format!("Base64 decode error: {e}"))?
            } else {
                raw_bytes.to_vec()
            };

            if !is_current() {
                return Err("Speech request cancelled.".into());
            }

            return decode_audio(&audio_bytes);
        }

        Err("Voice request timed out or exceeded retry limit.".into())
    }

    pub fn list_catalog(&self, config: &CloudVoiceConfig, key: &str) -> Result<Vec<CatalogVoice>> {
        config.checked()?;
        match config.provider {
            CloudVoiceProvider::Azure => {
                let url = format!(
                    "https://{}.tts.speech.microsoft.com/cognitiveservices/voices/list",
                    config.region.trim().to_lowercase()
                );
                let mut req = self.client.get(&url).header("Accept", "application/json");
                if !key.is_empty() {
                    req = req.header("Ocp-Apim-Subscription-Key", key);
                }
                let res = req.send().map_err(error)?;
                if !res.status().is_success() {
                    return Err(format!("Azure catalog request failed: {}", res.status()));
                }
                let bytes = res.bytes().map_err(error)?;
                let val: Value = serde_json::from_slice(&bytes).map_err(error)?;
                let items = val
                    .as_array()
                    .ok_or("Invalid Azure voices response format.")?;
                let mut list = Vec::new();
                for item in items {
                    let id = item["ShortName"].as_str().unwrap_or("").to_string();
                    let lang = item["Locale"].as_str().unwrap_or("").to_string();
                    let label = item["DisplayName"].as_str().unwrap_or(&id).to_string();
                    if !id.is_empty() && !lang.is_empty() {
                        list.push(CatalogVoice {
                            id,
                            language: lang,
                            label,
                        });
                    }
                }
                list.sort_by(|a, b| {
                    a.language
                        .cmp(&b.language)
                        .then_with(|| a.label.cmp(&b.label))
                });
                Ok(list)
            }
            CloudVoiceProvider::GoogleCloud => {
                let url = "https://texttospeech.googleapis.com/v1/voices";
                let mut req = self.client.get(url).header("Accept", "application/json");
                if !key.is_empty() {
                    req = req.header("X-Goog-Api-Key", key);
                }
                let res = req.send().map_err(error)?;
                if !res.status().is_success() {
                    return Err(format!(
                        "Google Cloud catalog request failed: {}",
                        res.status()
                    ));
                }
                let bytes = res.bytes().map_err(error)?;
                let val: Value = serde_json::from_slice(&bytes).map_err(error)?;
                let items = val["voices"]
                    .as_array()
                    .ok_or("Invalid Google Cloud voices format.")?;
                let mut list = Vec::new();
                for item in items {
                    let id = item["name"].as_str().unwrap_or("").to_string();
                    let locales = item["languageCodes"].as_array();
                    let lang = locales
                        .and_then(|arr| arr.first())
                        .and_then(|v| v.as_str())
                        .unwrap_or("")
                        .to_string();
                    if !id.is_empty() && !lang.is_empty() {
                        list.push(CatalogVoice {
                            id: id.clone(),
                            language: lang,
                            label: id,
                        });
                    }
                }
                list.sort_by(|a, b| {
                    a.language
                        .cmp(&b.language)
                        .then_with(|| a.label.cmp(&b.label))
                });
                Ok(list)
            }
            _ => Err(
                "Voice catalog discovery is only available for Azure and Google Cloud TTS.".into(),
            ),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn provider_defaults_and_validation_match_parity() {
        assert!(valid_id("cloud"));
        assert!(valid_id("cloud_elevenlabs"));
        assert!(!valid_id("piper_lessac"));

        let p = CloudVoiceProvider::Elevenlabs;
        assert_eq!(p.as_str(), "ELEVENLABS");
        assert_eq!(p.display_name(), "ElevenLabs");
        assert_eq!(p.default_model(), "eleven_v4");

        let mut config = CloudVoiceConfig::for_provider(CloudVoiceProvider::Cartesia);
        assert!(config.checked().is_ok());

        config.timeout_seconds = 200; // invalid (> 120)
        assert!(config.checked().is_err());
        config.timeout_seconds = 30;

        config.endpoint = "ftp://invalid".into();
        assert!(config.checked().is_err());

        let azure_cfg = CloudVoiceConfig::for_provider(CloudVoiceProvider::Azure);
        assert_eq!(
            azure_cfg.speech_endpoint(),
            "https://eastus.tts.speech.microsoft.com/cognitiveservices/v1"
        );
        assert!(azure_cfg.checked().is_ok());
    }

    #[test]
    fn credential_names_match_rules() {
        let p = CloudVoiceProvider::Elevenlabs;
        assert_eq!(p.credential_name("", ""), "tts_ELEVENLABS");

        let custom = CloudVoiceProvider::Custom;
        assert!(custom
            .credential_name("https://my-server.com", "")
            .starts_with("tts_CUSTOM_"));

        let azure = CloudVoiceProvider::Azure;
        assert!(azure
            .credential_name("", "eastus")
            .starts_with("tts_AZURE_"));
    }
}
