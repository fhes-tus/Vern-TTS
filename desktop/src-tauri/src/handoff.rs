use crate::storage::{error, Result};

pub fn open_reference(site: &str, text: &str) -> Result<()> {
    launch(reference_url(site, text)?.as_str())
}

fn reference_url(site: &str, text: &str) -> Result<reqwest::Url> {
    // Accept tool IDs, never a caller-supplied destination or scheme.
    let (base, parameter) = match site {
        "wikipedia" => ("https://en.wikipedia.org/wiki/Special:Search", "search"),
        "wiktionary" => ("https://en.wiktionary.org/wiki/Special:Search", "search"),
        "translate" => ("https://translate.google.com/", "text"),
        _ => return Err("Unknown reference site.".into()),
    };
    if text.len() > 14_000
        || text.chars().count() > 3500
        || text.chars().any(|c| c.is_control() && !c.is_whitespace())
    {
        return Err("Use up to 3,500 characters of plain text for this lookup.".into());
    }
    let clean = text.split_whitespace().collect::<Vec<_>>().join(" ");
    if clean.is_empty() {
        return Err("Enter some text to look up or translate.".into());
    }
    let mut url = reqwest::Url::parse(base).map_err(error)?;
    url.query_pairs_mut().append_pair(parameter, &clean);
    if site == "translate" {
        url.query_pairs_mut()
            .append_pair("sl", "auto")
            .append_pair("op", "translate");
    }
    // Bound the OS command line after percent encoding, including non-ASCII text.
    if url.as_str().len() > 16_000 {
        return Err("This text makes a link that is too long. Use a shorter passage.".into());
    }
    Ok(url)
}
pub fn open(id: &str) -> Result<()> {
    let url = match id {
        "chatgpt" => "https://chatgpt.com/",
        "gemini" => "https://gemini.google.com/",
        "claude" => "https://claude.ai/",
        "copilot" => "https://copilot.microsoft.com/",
        "perplexity" => "https://www.perplexity.ai/",
        _ => return Err("Unknown assistant.".into()),
    };
    launch(url)
}
pub fn open_about(id: &str) -> Result<()> {
    launch(match id {
        "website" => "https://fhes-tus.github.io/Vern-TTS/",
        "github" => "https://github.com/fhes-tus/Vern-TTS",
        "issues" => "https://github.com/fhes-tus/Vern-TTS/issues",
        "telegram" => "https://t.me/myreader_veritas",
        "contact" => "mailto:myreader.veritas@gmail.com?subject=Vern%20Feedback%20and%20Support",
        "support" => "mailto:myreader.veritas@gmail.com?subject=Support%20Vern",
        _ => return Err("Unknown app link.".into()),
    })
}
pub fn open_voice_settings() -> Result<()> {
    #[cfg(target_os = "windows")]
    {
        launch("ms-settings:speech")
    }
    #[cfg(not(target_os = "windows"))]
    {
        Err("Manage installed Microsoft voices in Windows Speech settings.".into())
    }
}
fn launch(url: &str) -> Result<()> {
    #[cfg(target_os = "windows")]
    let mut command = {
        let mut c = std::process::Command::new("rundll32.exe");
        c.arg("url.dll,FileProtocolHandler");
        c
    };
    #[cfg(target_os = "macos")]
    let mut command = std::process::Command::new("open");
    #[cfg(target_os = "linux")]
    let mut command = std::process::Command::new("xdg-open");
    command.arg(url).spawn().map_err(error)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reference_text_cannot_change_destination_or_inject_query_parameters() {
        let text = "  café &sl=es#fragment\n\"https://elsewhere.example/\"  ";
        for (site, host, parameter) in [
            ("wikipedia", "en.wikipedia.org", "search"),
            ("wiktionary", "en.wiktionary.org", "search"),
            ("translate", "translate.google.com", "text"),
        ] {
            let url = reference_url(site, text).unwrap();
            assert_eq!(url.scheme(), "https");
            assert_eq!(url.host_str(), Some(host));
            assert_eq!(url.fragment(), None);
            let pairs = url
                .query_pairs()
                .collect::<std::collections::HashMap<_, _>>();
            assert_eq!(
                pairs[parameter],
                "café &sl=es#fragment \"https://elsewhere.example/\""
            );
            assert_eq!(pairs.len(), if site == "translate" { 3 } else { 1 });
            if site == "translate" {
                assert_eq!(pairs["sl"], "auto");
                assert_eq!(pairs["op"], "translate");
            }
        }
    }

    #[test]
    fn reference_requests_are_bounded_before_opening_any_process() {
        for site in ["https://example.com", "javascript:alert(1)", "", "other"] {
            assert!(reference_url(site, "word").is_err());
        }
        for text in ["", "\n\t ", "word\0word", "word\u{1b}word"] {
            assert!(reference_url("wikipedia", text).is_err());
        }
        assert!(reference_url("translate", &"x".repeat(3500)).is_ok());
        assert!(reference_url("translate", &"x".repeat(3501)).is_err());
        assert!(reference_url("translate", &"😀".repeat(3500)).is_err());
        assert!(reference_url("translate", "日本語 العربية français").is_ok());
    }
}
