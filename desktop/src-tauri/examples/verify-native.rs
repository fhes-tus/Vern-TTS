//! Developer-only native integration check; not an installed application binary.
use std::{path::PathBuf, sync::atomic::Ordering};
use vern_desktop_lib::{
    speech::Speech,
    storage::{Book, Storage},
    voices::{Voices, CATALOGUE},
};
fn main() -> Result<(), String> {
    let mut args = std::env::args().skip(1);
    let root = PathBuf::from(
        args.next()
            .ok_or("Usage: verify-native <scratch-dir> [--download]")?,
    );
    let download = args.any(|arg| arg == "--download");
    std::fs::create_dir_all(&root).map_err(|e| e.to_string())?;
    let mut db = Storage::open(&root.join("verification.sqlite3"))?;
    let book = db.save_book(Book {
        id: String::new(),
        title: "Native persistence check".into(),
        author: String::new(),
        format: "TXT".into(),
        chunks: vec![
            "A real local book.".into(),
            "The next passage is saved.".into(),
        ],
        position: 0,
        favorite: false,
        collection: String::new(),
        text_available: true,
        updated_at: 0,
    })?;
    db.progress(&book.id, 1)?;
    drop(db);
    let reopened = Storage::open(&root.join("verification.sqlite3"))?;
    assert_eq!(reopened.books()?[0].position, 1);
    println!("PASS SQLite survives close/reopen");
    let voices = Voices::new(root.join("voices"))?;
    for d in CATALOGUE {
        if download && !voices.installed(d) {
            voices.begin(d)?;
            voices.install(d)?;
        }
        if !voices.installed(d) {
            return Err(format!(
                "{} missing. Run with --download while online, then re-run without it offline.",
                d.family
            ));
        }
        voices.verify(d)?;
        let speech = Speech::default();
        speech.generation.store(1, Ordering::SeqCst);
        let audio = speech.synthesize(
            &voices,
            d.id,
            "Welcome to Vern. Your next chapter is waiting.",
            1.0,
            1,
        )?;
        assert!(audio.samples.len() > audio.sample_rate as usize / 2);
        assert!(audio.samples.iter().all(|s| s.is_finite()));
        assert!(audio.samples.iter().any(|s| s.abs() > 0.001));
        let mut wav = Vec::new();
        let len = (audio.samples.len() * 2) as u32;
        wav.extend(b"RIFF");
        wav.extend((36 + len).to_le_bytes());
        wav.extend(b"WAVEfmt ");
        wav.extend(16_u32.to_le_bytes());
        wav.extend(1_u16.to_le_bytes());
        wav.extend(1_u16.to_le_bytes());
        wav.extend((audio.sample_rate as u32).to_le_bytes());
        wav.extend((audio.sample_rate as u32 * 2).to_le_bytes());
        wav.extend(2_u16.to_le_bytes());
        wav.extend(16_u16.to_le_bytes());
        wav.extend(b"data");
        wav.extend(len.to_le_bytes());
        for sample in &audio.samples {
            wav.extend(((sample.clamp(-1.0, 1.0) * 32767.0).round() as i16).to_le_bytes());
        }
        std::fs::write(root.join(format!("{}.wav", d.id)), wav).map_err(|e| e.to_string())?;
        speech.cancel(2);
        assert!(speech
            .synthesize(&voices, d.id, "Obsolete.", 1.0, 1)
            .is_err());
        println!(
            "PASS {} / {}: {} samples at {} Hz; cancellation rejected obsolete request",
            d.family,
            d.engine,
            audio.samples.len(),
            audio.sample_rate
        );
    }
    Ok(())
}
