// Read-only diagnostic for the native catalogue connection; does not change a library.
fn main() {
    let _ = rustls::crypto::ring::default_provider().install_default();
    let client = reqwest::blocking::Client::builder()
        .connect_timeout(std::time::Duration::from_secs(15))
        .timeout(std::time::Duration::from_secs(60))
        .user_agent("Vern-Desktop/0.1 (user-initiated book download)")
        .build()
        .expect("HTTP client");
    match client
        .get("https://www.gutenberg.org/cache/epub/11/pg11.txt")
        .send()
        .and_then(|r| r.error_for_status())
    {
        Ok(response) => println!(
            "Native catalogue response: {} ({} bytes)",
            response.status(),
            response.content_length().unwrap_or(0)
        ),
        Err(error) => {
            eprintln!("Native catalogue connection: {error:?}");
            std::process::exit(1);
        }
    }
}
