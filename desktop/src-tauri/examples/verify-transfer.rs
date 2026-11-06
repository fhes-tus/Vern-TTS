//! Verify a checkpointed database snapshot without opening or changing a live library.
use serde::Serialize;
use sha2::{Digest, Sha256};
use std::{fs, path::PathBuf};
use vern_desktop_lib::{
    backup_archive,
    storage::{hex_digest, Storage},
    transfer,
};

fn sorted<T: Serialize>(values: Vec<T>) -> serde_json::Value {
    let mut values: Vec<serde_json::Value> = values
        .into_iter()
        .map(|v| serde_json::to_value(v).unwrap())
        .collect();
    values.sort_by(|a, b| a["id"].as_str().cmp(&b["id"].as_str()));
    serde_json::Value::Array(values)
}
fn main() -> Result<(), Box<dyn std::error::Error>> {
    let mut args = std::env::args().skip(1);
    let input = PathBuf::from(
        args.next()
            .ok_or("Supply a checkpointed SQLite snapshot and an artifact directory.")?,
    );
    let destination = PathBuf::from(args.next().ok_or("Supply an artifact directory.")?);
    if args.next().is_some() {
        return Err("Expected exactly two arguments.".into());
    }
    if input
        .with_file_name(format!(
            "{}-wal",
            input
                .file_name()
                .ok_or("Snapshot needs a filename.")?
                .to_string_lossy()
        ))
        .exists()
    {
        return Err("Use a checkpointed snapshot, not a live SQLite/WAL library.".into());
    }
    let root = destination.join(format!("transfer-{}", uuid::Uuid::new_v4()));
    fs::create_dir_all(&root)?;
    let copied = root.join("source.sqlite3");
    fs::copy(&input, &copied)?;
    let source = Storage::open(&copied)?;
    let original_books = source.books()?;
    let archive = root.join("library.vernbackup");
    backup_archive::write(&source, &archive)?;
    let pending = transfer::read_backup(&archive)?;
    let preview = pending.preview("verification-only".into());
    let mut restored = Storage::open(&root.join("restored.sqlite3"))?;
    restored.restore_backup(pending, true)?;
    if sorted(source.books()?) != sorted(restored.books()?)
        || sorted(source.all_highlights()?) != sorted(restored.all_highlights()?)
        || sorted(source.all_annotations()?) != sorted(restored.all_annotations()?)
        || sorted(source.notes()?) != sorted(restored.notes()?)
        || sorted(source.all_note_revisions()?) != sorted(restored.all_note_revisions()?)
        || sorted(source.all_note_media()?) != sorted(restored.all_note_media()?)
        || sorted(source.notebooks()?) != sorted(restored.notebooks()?)
        || sorted(source.flashcards()?) != sorted(restored.flashcards()?)
        || sorted(source.quizzes()?) != sorted(restored.quizzes()?)
        || sorted(source.sessions()?) != sorted(restored.sessions()?)
        || sorted(source.reading_lists()?) != sorted(restored.reading_lists()?)
        || serde_json::to_value(source.classic_links()?)?
            != serde_json::to_value(restored.classic_links()?)?
        || serde_json::to_value(source.settings()?)? != serde_json::to_value(restored.settings()?)?
    {
        return Err("A full backup did not preserve library data/preferences.".into());
    }
    let mut originals = 0;
    for item in source.all_note_media()? {
        if source.note_media_bytes(&item.id)? != restored.note_media_bytes(&item.id)? {
            return Err("Note attachment bytes changed.".into());
        }
    }
    for book in &original_books {
        if serde_json::to_value(source.source_info(&book.id)?)?
            != serde_json::to_value(restored.source_info(&book.id)?)?
        {
            return Err("Original document metadata/page positions changed.".into());
        }
        if let Some(file) = source.source_file(&book.id)? {
            if restored
                .source_file(&book.id)?
                .ok_or("An original file was not restored.")?
                .bytes
                != file.bytes
            {
                return Err("Original document bytes changed.".into());
            }
            originals += 1;
        }
    }
    let report = serde_json::json!({"books":original_books.len(),"originalsVerified":originals,
        "annotations":preview.annotations,"notes":preview.notes,"sessions":preview.sessions,
        "archive":archive,"sha256":hex_digest(Sha256::digest(fs::read(&archive)?)),
        "allDataAndPreferencesPreserved":true,"liveLibraryOpened":false});
    fs::write(
        root.join("verification.json"),
        serde_json::to_vec_pretty(&report)?,
    )?;
    println!("{}", serde_json::to_string_pretty(&report)?);
    Ok(())
}
