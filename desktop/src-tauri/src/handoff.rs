use crate::storage::{error, Result};
pub fn open(id: &str) -> Result<()> {
    let url = match id {
        "chatgpt" => "https://chatgpt.com/",
        "gemini" => "https://gemini.google.com/",
        "claude" => "https://claude.ai/",
        "copilot" => "https://copilot.microsoft.com/",
        "perplexity" => "https://www.perplexity.ai/",
        _ => return Err("Unknown assistant.".into()),
    };
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
