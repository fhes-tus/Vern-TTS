pub mod activity;
pub mod android_archive;
pub mod android_backup;
pub mod android_history;
pub mod android_migration;
pub mod android_restore;
mod android_settings;
pub mod backup_archive;
pub mod classics;
pub mod covers;
pub mod documents;
mod handoff;
pub mod highlights;
mod legacy_activity;
pub mod note_media;
pub mod notes;
pub mod opened_books;
mod profile;
pub mod quizzes;
pub mod reading_lists;
pub mod reading_tools;
pub mod speech;
pub mod storage;
pub mod study;
pub mod transfer;
pub mod voices;
pub mod windows_speech;
use speech::{Audio, Speech};
use std::{
    fs,
    io::Read,
    sync::{Arc, Mutex},
};
use storage::{error, Annotation, Book, Result, Settings, Storage};
use study::{CardInput, Flashcard};
use tauri::{Manager, State};
use tauri_plugin_dialog::DialogExt;
use transfer::{Backup, BackupPreview};
use voices::{descriptor, Voice, Voices};

struct AppState {
    db: Mutex<Storage>,
    voices: Arc<Voices>,
    speech: Arc<Speech>,
    pending_backup: Mutex<Option<(String, Backup)>>,
    android_conversion: Mutex<Option<(String, android_restore::Conversion)>>,
    classics: Arc<classics::Downloads>,
    activity: Mutex<activity::Tracker>,
}
#[tauri::command]
async fn list_opened_books(
    state: State<'_, Arc<AppState>>,
) -> Result<Vec<opened_books::OpenedBook>> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.opened_books()).await
}
#[tauri::command]
async fn record_reading_open(state: State<'_, Arc<AppState>>, id: String) -> Result<()> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.record_reading_open(&id)).await
}
#[tauri::command]
async fn remove_opened_book(state: State<'_, Arc<AppState>>, id: String) -> Result<()> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.remove_opened_book(&id)).await
}
#[tauri::command]
async fn clear_opened_books(state: State<'_, Arc<AppState>>) -> Result<()> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.clear_opened_books()).await
}
#[tauri::command]
async fn list_highlights(
    state: State<'_, Arc<AppState>>,
    book_id: Option<String>,
) -> Result<Vec<highlights::ReadingHighlight>> {
    let state = state.inner().clone();
    work(move || {
        let db = state.db.lock().map_err(error)?;
        match book_id {
            Some(id) => db.book_highlights(&id),
            None => db.all_highlights(),
        }
    })
    .await
}
#[tauri::command]
async fn save_highlight(
    state: State<'_, Arc<AppState>>,
    input: highlights::HighlightInput,
) -> Result<highlights::ReadingHighlight> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.save_highlight(input)).await
}
#[tauri::command]
async fn delete_highlight(state: State<'_, Arc<AppState>>, id: String) -> Result<()> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.delete_highlight(&id)).await
}
#[tauri::command]
async fn list_note_media(
    state: State<'_, Arc<AppState>>,
    id: String,
) -> Result<Vec<note_media::NoteMedia>> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.note_media(&id)).await
}
#[tauri::command]
async fn get_note_media(state: State<'_, Arc<AppState>>, id: String) -> Result<String> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.note_media_url(&id)).await
}
#[tauri::command]
async fn delete_note_media(state: State<'_, Arc<AppState>>, id: String) -> Result<()> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.delete_note_media(&id)).await
}
#[tauri::command]
async fn attach_note_media(
    app: tauri::AppHandle,
    state: State<'_, Arc<AppState>>,
    id: String,
) -> Result<Option<note_media::NoteMedia>> {
    let state = state.inner().clone();
    work(move || {
        let Some(file) = app
            .dialog()
            .file()
            .add_filter(
                "Image or recording",
                &["jpg", "jpeg", "png", "wav", "mp3", "ogg"],
            )
            .blocking_pick_file()
        else {
            return Ok(None);
        };
        let path = file.into_path().map_err(error)?;
        let input = fs::File::open(&path).map_err(error)?;
        if !input.metadata().map_err(error)?.is_file() {
            return Err("Choose a regular attachment file.".into());
        }
        let mut bytes = Vec::new();
        input
            .take(note_media::LIMIT as u64 + 1)
            .read_to_end(&mut bytes)
            .map_err(error)?;
        let name = path
            .file_name()
            .and_then(|v| v.to_str())
            .ok_or("Choose a file with a supported name.")?
            .to_string();
        state
            .db
            .lock()
            .map_err(error)?
            .add_note_media(&id, name, bytes)
            .map(Some)
    })
    .await
}
#[tauri::command]
async fn list_note_revisions(
    state: State<'_, Arc<AppState>>,
    id: String,
) -> Result<Vec<notes::NoteRevision>> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.note_revisions(&id)).await
}
#[tauri::command]
async fn list_reading_lists(
    state: State<'_, Arc<AppState>>,
) -> Result<Vec<reading_lists::ReadingList>> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.reading_lists()).await
}
#[tauri::command]
async fn save_reading_list(
    state: State<'_, Arc<AppState>>,
    input: reading_lists::ListInput,
) -> Result<reading_lists::ReadingList> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.save_reading_list(input)).await
}
#[tauri::command]
async fn delete_reading_list(state: State<'_, Arc<AppState>>, id: String) -> Result<()> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.delete_reading_list(&id)).await
}
#[tauri::command]
async fn record_reading_activity(
    state: State<'_, Arc<AppState>>,
    event: activity::Event,
) -> Result<()> {
    let mono = std::time::Instant::now();
    let wall = storage::now();
    let state = state.inner().clone();
    work(move || {
        let mut tracker = state.activity.lock().map_err(error)?;
        let mut db = state.db.lock().map_err(error)?;
        tracker.event(&mut db, event, mono, wall)
    })
    .await
}
#[tauri::command]
async fn get_reading_activity(state: State<'_, Arc<AppState>>) -> Result<activity::Report> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.activity_report()).await
}
#[tauri::command]
async fn correct_reading_session(
    state: State<'_, Arc<AppState>>,
    id: String,
    corrected_ms: Option<u64>,
    excluded: bool,
) -> Result<()> {
    let state = state.inner().clone();
    work(move || {
        state
            .db
            .lock()
            .map_err(error)?
            .correct_session(&id, corrected_ms, excluded)
    })
    .await
}
#[tauri::command]
async fn add_reading_session(
    state: State<'_, Arc<AppState>>,
    target: activity::Target,
    mode: activity::Mode,
    started_at: u64,
    duration_ms: u64,
) -> Result<()> {
    let state = state.inner().clone();
    work(move || {
        state
            .db
            .lock()
            .map_err(error)?
            .manual_session(target, mode, started_at, duration_ms)
    })
    .await
}
#[tauri::command]
async fn export_reading_sessions(
    app: tauri::AppHandle,
    state: State<'_, Arc<AppState>>,
) -> Result<bool> {
    let state = state.inner().clone();
    work(move || {
        let bytes = state.db.lock().map_err(error)?.sessions_csv()?;
        save_export(&app, "Vern-reading-history.csv", "csv", &bytes)
    })
    .await
}
#[tauri::command]
async fn open_assistant(id: String) -> Result<()> {
    work(move || handoff::open(&id)).await
}
#[tauri::command]
async fn open_reference(site: String, text: String) -> Result<()> {
    work(move || handoff::open_reference(&site, &text)).await
}
#[tauri::command]
async fn open_app_link(id: String) -> Result<()> {
    work(move || handoff::open_about(&id)).await
}
#[tauri::command]
async fn open_voice_settings() -> Result<()> {
    work(handoff::open_voice_settings).await
}
#[tauri::command]
async fn list_classics(state: State<'_, Arc<AppState>>) -> Result<Vec<classics::Classic>> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.classics()).await
}
#[tauri::command]
async fn download_classic(
    state: State<'_, Arc<AppState>>,
    id: String,
) -> Result<documents::SourceFile> {
    let downloads = state.classics.clone();
    work(move || downloads.fetch(&id)).await
}
#[tauri::command]
fn cancel_classic_download(state: State<'_, Arc<AppState>>, id: String) -> Result<()> {
    state.classics.cancel(&id)
}
#[tauri::command]
async fn save_classic(
    state: State<'_, Arc<AppState>>,
    id: String,
    book: Book,
    file: ImportFile,
) -> Result<Book> {
    let state = state.inner().clone();
    work(move || {
        state
            .db
            .lock()
            .map_err(error)?
            .save_classic(&id, book, file.name, file.bytes)
    })
    .await
}
async fn work<T: Send + 'static>(f: impl FnOnce() -> Result<T> + Send + 'static) -> Result<T> {
    tauri::async_runtime::spawn_blocking(f)
        .await
        .map_err(error)?
}

#[tauri::command]
async fn list_books(state: State<'_, Arc<AppState>>) -> Result<Vec<Book>> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.books()).await
}
#[tauri::command]
async fn list_personal_notes(state: State<'_, Arc<AppState>>) -> Result<Vec<notes::Note>> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.notes()).await
}
#[tauri::command]
async fn list_notebooks(state: State<'_, Arc<AppState>>) -> Result<Vec<notes::Notebook>> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.notebooks()).await
}
#[tauri::command]
async fn save_personal_note(
    state: State<'_, Arc<AppState>>,
    input: notes::NoteInput,
) -> Result<notes::Note> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.save_note(input)).await
}
#[tauri::command]
async fn save_notebook(
    state: State<'_, Arc<AppState>>,
    id: Option<String>,
    name: String,
) -> Result<()> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.save_notebook(id, name)).await
}
#[tauri::command]
async fn trash_personal_note(
    state: State<'_, Arc<AppState>>,
    id: String,
    trashed: bool,
) -> Result<()> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.trash_note(&id, trashed)).await
}
#[tauri::command]
async fn delete_personal_note(state: State<'_, Arc<AppState>>, id: String) -> Result<()> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.delete_note(&id)).await
}
#[tauri::command]
async fn delete_notebook(state: State<'_, Arc<AppState>>, id: String) -> Result<()> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.delete_notebook(&id)).await
}
#[tauri::command]
async fn save_book(state: State<'_, Arc<AppState>>, book: Book) -> Result<Book> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.save_book(book)).await
}
#[tauri::command]
async fn save_progress(state: State<'_, Arc<AppState>>, id: String, position: usize) -> Result<()> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.progress(&id, position)).await
}
#[tauri::command]
async fn toggle_favorite(state: State<'_, Arc<AppState>>, id: String) -> Result<()> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.favorite(&id)).await
}
#[tauri::command]
async fn update_book(
    state: State<'_, Arc<AppState>>,
    id: String,
    title: String,
    author: String,
    collection: String,
) -> Result<()> {
    let state = state.inner().clone();
    work(move || {
        state
            .db
            .lock()
            .map_err(error)?
            .update_book(&id, &title, &author, &collection)
    })
    .await
}
#[tauri::command]
async fn delete_book(state: State<'_, Arc<AppState>>, id: String) -> Result<()> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.delete_book(&id)).await
}
#[tauri::command]
async fn list_all_annotations(state: State<'_, Arc<AppState>>) -> Result<Vec<Annotation>> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.all_annotations()).await
}
#[tauri::command]
async fn delete_annotation(state: State<'_, Arc<AppState>>, id: String) -> Result<()> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.delete_annotation(&id)).await
}
#[tauri::command]
async fn list_flashcards(state: State<'_, Arc<AppState>>) -> Result<Vec<Flashcard>> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.flashcards()).await
}
#[tauri::command]
async fn list_quizzes(state: State<'_, Arc<AppState>>) -> Result<Vec<quizzes::Quiz>> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.quizzes()).await
}
#[tauri::command]
async fn save_quiz(
    state: State<'_, Arc<AppState>>,
    input: quizzes::QuizInput,
) -> Result<quizzes::Quiz> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.save_quiz(input)).await
}
#[tauri::command]
async fn submit_quiz(
    state: State<'_, Arc<AppState>>,
    id: String,
    answers: Vec<usize>,
) -> Result<quizzes::Quiz> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.submit_quiz(&id, answers)).await
}
#[tauri::command]
async fn delete_quiz(state: State<'_, Arc<AppState>>, id: String) -> Result<()> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.delete_quiz(&id)).await
}
#[tauri::command]
async fn import_flashcards(
    state: State<'_, Arc<AppState>>,
    cards: Vec<CardInput>,
) -> Result<usize> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.import_flashcards(cards)).await
}
#[tauri::command]
async fn save_flashcard(state: State<'_, Arc<AppState>>, input: CardInput) -> Result<()> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.save_flashcard(input)).await
}
#[tauri::command]
async fn review_flashcard(
    state: State<'_, Arc<AppState>>,
    id: String,
    rating: String,
) -> Result<()> {
    let state = state.inner().clone();
    work(move || {
        state
            .db
            .lock()
            .map_err(error)?
            .review_flashcard(&id, &rating)
    })
    .await
}
#[tauri::command]
async fn delete_flashcard(state: State<'_, Arc<AppState>>, id: String) -> Result<()> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.delete_flashcard(&id)).await
}
#[tauri::command]
async fn list_annotations(
    state: State<'_, Arc<AppState>>,
    book_id: String,
) -> Result<Vec<Annotation>> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.annotations(&book_id)).await
}
#[tauri::command]
async fn save_annotation(state: State<'_, Arc<AppState>>, annotation: Annotation) -> Result<()> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.annotate(annotation)).await
}
#[tauri::command]
async fn get_settings(state: State<'_, Arc<AppState>>) -> Result<Settings> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.settings()).await
}
#[tauri::command]
async fn save_settings(state: State<'_, Arc<AppState>>, settings: Settings) -> Result<()> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.save_settings(settings)).await
}

fn save_export(app: &tauri::AppHandle, name: &str, extension: &str, bytes: &[u8]) -> Result<bool> {
    let Some(file) = app
        .dialog()
        .file()
        .add_filter("Export", &[extension])
        .set_file_name(name)
        .blocking_save_file()
    else {
        return Ok(false);
    };
    let path = file.into_path().map_err(error)?;
    if !path
        .extension()
        .is_some_and(|ext| ext.to_string_lossy().eq_ignore_ascii_case(extension))
    {
        return Err(format!("Choose a .{extension} filename."));
    }
    transfer::write_file(&path, bytes)?;
    Ok(true)
}
#[tauri::command]
async fn export_backup(app: tauri::AppHandle, state: State<'_, Arc<AppState>>) -> Result<bool> {
    let state = state.inner().clone();
    work(move || {
        let Some(file) = app
            .dialog()
            .file()
            .add_filter("Vern full backup", &["vernbackup", "zip"])
            .set_file_name("Vern-library.vernbackup")
            .blocking_save_file()
        else {
            return Ok(false);
        };
        let path = file.into_path().map_err(error)?;
        if !path.extension().is_some_and(|ext| {
            ["vernbackup", "zip"]
                .iter()
                .any(|allowed| ext.to_string_lossy().eq_ignore_ascii_case(allowed))
        }) {
            return Err("Choose a .vernbackup or .zip filename.".into());
        }
        let snapshot = state.db.lock().map_err(error)?.backup_database_snapshot()?;
        let snapshot_db = Storage::open(snapshot.path())?;
        backup_archive::write(&snapshot_db, &path)?;
        Ok(true)
    })
    .await
}
#[tauri::command]
async fn inspect_backup(
    app: tauri::AppHandle,
    state: State<'_, Arc<AppState>>,
) -> Result<Option<BackupInspection>> {
    let state = state.inner().clone();
    work(move || {
        *state.pending_backup.lock().map_err(error)? = None;
        *state.android_conversion.lock().map_err(error)? = None;
        let Some(file) = app
            .dialog()
            .file()
            .add_filter("Vern backup", &["vernbackup", "zip", "json"])
            .blocking_pick_file()
        else {
            return Ok(None);
        };
        let path = file.into_path().map_err(error)?;
        let token = uuid::Uuid::new_v4().to_string();
        if android_restore::is_android(&path)? {
            let conversion = android_restore::Conversion::read(&path)?;
            let documents = conversion.document_count();
            *state.android_conversion.lock().map_err(error)? = Some((token.clone(), conversion));
            return Ok(Some(BackupInspection::Android { token, documents }));
        }
        let backup = transfer::read_backup(&path)?;
        let preview = backup.preview(token.clone());
        *state.pending_backup.lock().map_err(error)? = Some((token, backup));
        Ok(Some(BackupInspection::Desktop { preview }))
    })
    .await
}
#[derive(serde::Serialize)]
#[serde(tag = "kind", rename_all = "lowercase")]
enum BackupInspection {
    Desktop { preview: BackupPreview },
    Android { token: String, documents: usize },
}
#[tauri::command]
async fn android_backup_text(
    state: State<'_, Arc<AppState>>,
    token: String,
) -> Result<Option<android_backup::AndroidTextInput>> {
    let state = state.inner().clone();
    work(move || {
        let pending = state.android_conversion.lock().map_err(error)?;
        let (id, conversion) = pending.as_ref().ok_or("Choose the Android backup again.")?;
        if id != &token {
            return Err("Choose the Android backup again.".into());
        }
        conversion.next_text()
    })
    .await
}
#[tauri::command]
async fn accept_android_text(
    state: State<'_, Arc<AppState>>,
    token: String,
    index: usize,
    converted: android_migration::ConvertedAndroidText,
) -> Result<()> {
    let state = state.inner().clone();
    work(move || {
        let mut pending = state.android_conversion.lock().map_err(error)?;
        let (id, conversion) = pending.as_mut().ok_or("Choose the Android backup again.")?;
        if id != &token {
            return Err("Choose the Android backup again.".into());
        }
        conversion.accept(index, converted)
    })
    .await
}
#[tauri::command]
async fn finish_android_backup(
    state: State<'_, Arc<AppState>>,
    token: String,
) -> Result<BackupPreview> {
    let state = state.inner().clone();
    work(move || {
        let conversion = {
            let mut pending = state.android_conversion.lock().map_err(error)?;
            if !pending.as_ref().is_some_and(|(id, _)| id == &token) {
                return Err("Choose the Android backup again.".into());
            }
            pending.take().ok_or("Choose the Android backup again.")?.1
        };
        let settings = state.db.lock().map_err(error)?.settings()?;
        let backup = conversion.finish(settings)?;
        let preview = backup.preview(token.clone());
        *state.pending_backup.lock().map_err(error)? = Some((token, backup));
        Ok(preview)
    })
    .await
}
#[tauri::command]
async fn list_android_imports(
    state: State<'_, Arc<AppState>>,
) -> Result<Vec<android_archive::ImportSummary>> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.android_import_summaries()).await
}
#[tauri::command]
async fn export_android_import(
    app: tauri::AppHandle,
    state: State<'_, Arc<AppState>>,
    id: String,
) -> Result<bool> {
    let state = state.inner().clone();
    work(move || {
        let Some(file) = app
            .dialog()
            .file()
            .add_filter("Original Android backup", &["zip"])
            .set_file_name("Vern-Android-backup.zip")
            .blocking_save_file()
        else {
            return Ok(false);
        };
        let snapshot = state.db.lock().map_err(error)?.backup_database_snapshot()?;
        Storage::open(snapshot.path())?
            .export_android_import(&id, &file.into_path().map_err(error)?)?;
        Ok(true)
    })
    .await
}
#[tauri::command]
async fn delete_android_import(state: State<'_, Arc<AppState>>, id: String) -> Result<()> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.delete_android_import(&id)).await
}
#[tauri::command]
async fn restore_backup(
    state: State<'_, Arc<AppState>>,
    token: String,
    restore_settings: bool,
) -> Result<()> {
    let state = state.inner().clone();
    work(move || {
        let backup = {
            let mut pending = state.pending_backup.lock().map_err(error)?;
            if !pending.as_ref().is_some_and(|(id, _)| id == &token) {
                return Err("Choose and preview the backup again.".into());
            }
            pending.take().ok_or("Choose a backup first.")?.1
        };
        state
            .db
            .lock()
            .map_err(error)?
            .restore_backup(backup, restore_settings)
    })
    .await
}
#[tauri::command]
fn discard_backup(state: State<'_, Arc<AppState>>, token: String) -> Result<()> {
    let mut conversion = state.android_conversion.lock().map_err(error)?;
    if conversion.as_ref().is_some_and(|(id, _)| id == &token) {
        *conversion = None;
    }
    drop(conversion);
    let mut pending = state.pending_backup.lock().map_err(error)?;
    if pending.as_ref().is_some_and(|(id, _)| id == &token) {
        *pending = None;
    }
    Ok(())
}
#[tauri::command]
async fn export_book(
    app: tauri::AppHandle,
    state: State<'_, Arc<AppState>>,
    id: String,
    kind: String,
) -> Result<bool> {
    let state = state.inner().clone();
    work(move || {
        let bytes = state.db.lock().map_err(error)?.export_book(&id, &kind)?;
        let (name, ext) = if kind == "notes" {
            ("Vern-notes.md", "md")
        } else {
            ("Vern-book.txt", "txt")
        };
        save_export(&app, name, ext, &bytes)
    })
    .await
}
#[tauri::command]
async fn export_passage_audio(
    app: tauri::AppHandle,
    state: State<'_, Arc<AppState>>,
    id: String,
    position: usize,
    voice_id: String,
    rate: f32,
) -> Result<bool> {
    let state = state.inner().clone();
    work(move || {
        let (text, rules) = {
            let db = state.db.lock().map_err(error)?;
            let book = db.book(&id)?;
            if !book.text_available {
                return Err("This document needs OCR before audio export is available.".into());
            }
            (
                book.chunks
                    .get(position)
                    .ok_or("Choose a passage in this book.")?
                    .clone(),
                db.pronunciation_rules()?,
            )
        };
        let text = reading_tools::apply_pronunciation(&text, &rules)?;
        if !voices::valid_id(&voice_id) {
            return Err("Unknown voice.".into());
        }
        if !(0.5..=2.0).contains(&rate) {
            return Err("Choose a narration speed between 0.5× and 2×.".into());
        }
        let generation = state
            .speech
            .generation
            .load(std::sync::atomic::Ordering::SeqCst);
        let audio = if text.trim().is_empty() {
            Audio {
                samples: vec![0.0; 240],
                sample_rate: 24000,
            }
        } else {
            state
                .speech
                .synthesize(&state.voices, &voice_id, &text, rate, generation)?
        };
        let bytes = transfer::wav_bytes(audio)?;
        save_export(&app, "Vern-passage.wav", "wav", &bytes)
    })
    .await
}

#[derive(serde::Serialize, serde::Deserialize)]
struct ImportFile {
    name: String,
    bytes: Vec<u8>,
}
#[tauri::command]
async fn save_book_with_source(
    state: State<'_, Arc<AppState>>,
    book: Book,
    file: ImportFile,
    page_starts: Vec<usize>,
    text_available: bool,
) -> Result<Book> {
    let state = state.inner().clone();
    work(move || {
        state.db.lock().map_err(error)?.import_source(
            book,
            file.name,
            file.bytes,
            page_starts,
            text_available,
        )
    })
    .await
}
#[tauri::command]
async fn get_source_info(
    state: State<'_, Arc<AppState>>,
    id: String,
) -> Result<Option<documents::SourceInfo>> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.source_info(&id)).await
}
#[tauri::command]
async fn save_book_cover(state: State<'_, Arc<AppState>>, id: String, cover: String) -> Result<()> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.save_cover(&id, &cover)).await
}
#[tauri::command]
async fn get_source_file(
    state: State<'_, Arc<AppState>>,
    id: String,
) -> Result<Option<documents::SourceFile>> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.source_file(&id)).await
}
#[tauri::command]
async fn save_source_page(state: State<'_, Arc<AppState>>, id: String, page: usize) -> Result<()> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.source_page(&id, page)).await
}
#[tauri::command]
async fn save_source_index(
    state: State<'_, Arc<AppState>>,
    id: String,
    pages: Vec<usize>,
) -> Result<()> {
    let state = state.inner().clone();
    work(move || state.db.lock().map_err(error)?.source_index(&id, pages)).await
}
#[tauri::command]
async fn pick_book(app: tauri::AppHandle) -> Result<Option<ImportFile>> {
    work(move || {
        let selected = app
            .dialog()
            .file()
            .add_filter(
                "Books",
                &["txt", "md", "log", "epub", "pdf", "docx", "pptx"],
            )
            .blocking_pick_file();
        let Some(selected) = selected else {
            return Ok(None);
        };
        let path = selected.into_path().map_err(error)?;
        let file = fs::File::open(&path).map_err(error)?;
        if !file.metadata().map_err(error)?.is_file() {
            return Err("Select a regular book file.".into());
        }
        let mut bytes = Vec::new();
        file.take(32 * 1024 * 1024 + 1)
            .read_to_end(&mut bytes)
            .map_err(error)?;
        if bytes.len() > 32 * 1024 * 1024 {
            return Err("Choose a book smaller than 32 MB.".into());
        }
        Ok(Some(ImportFile {
            name: path
                .file_name()
                .ok_or("Invalid filename.")?
                .to_string_lossy()
                .into_owned(),
            bytes,
        }))
    })
    .await
}
#[tauri::command]
async fn list_voices(state: State<'_, Arc<AppState>>) -> Result<Vec<Voice>> {
    let voices = state.voices.clone();
    work(move || voices.list()).await
}
#[tauri::command]
async fn download_voice(state: State<'_, Arc<AppState>>, voice_id: String) -> Result<()> {
    let d = descriptor(&voice_id)?;
    let voices = state.voices.clone();
    if !voices.begin(d)? {
        return Ok(());
    }
    tauri::async_runtime::spawn(async move {
        if let Ok(_permit) = voices.slots.clone().acquire_owned().await {
            let report = voices.clone();
            if let Err(join_error) =
                tauri::async_runtime::spawn_blocking(move || voices.install(d)).await
            {
                report.invalidate(
                    d,
                    format!("Voice download worker stopped: {join_error}. Retry the download."),
                );
            }
        }
    });
    Ok(())
}
#[tauri::command]
fn cancel_download(state: State<'_, Arc<AppState>>, voice_id: String) -> Result<()> {
    state.voices.cancel(&voice_id)
}
#[tauri::command]
fn cancel_speech(state: State<'_, Arc<AppState>>, generation: u64) {
    state.speech.cancel(generation);
}
#[tauri::command]
async fn synthesize(
    state: State<'_, Arc<AppState>>,
    voice_id: String,
    text: String,
    rate: f32,
    generation: u64,
) -> Result<Audio> {
    let state = state.inner().clone();
    work(move || {
        if text.len() > 4096 || text.trim().is_empty() || !(0.5..=2.0).contains(&rate) {
            return Err("Invalid speech chunk.".into());
        }
        if !voices::valid_id(&voice_id) {
            return Err("Unknown voice.".into());
        }
        let rules = state.db.lock().map_err(error)?.pronunciation_rules()?;
        let text = reading_tools::apply_pronunciation(&text, &rules)?;
        if state
            .speech
            .generation
            .load(std::sync::atomic::Ordering::SeqCst)
            != generation
        {
            return Err("Speech request cancelled.".into());
        }
        if text.trim().is_empty() {
            return Ok(Audio {
                samples: vec![0.0; 240],
                sample_rate: 24000,
            });
        }
        state
            .speech
            .synthesize(&state.voices, &voice_id, &text, rate, generation)
    })
    .await
}

pub fn run() {
    let mut context = tauri::generate_context!();
    if let Some(path) = std::env::var_os("VERN_PROFILE_DIR") {
        let root = profile::validate(path.into()).expect("Invalid isolated Vern profile");
        context.config_mut().app.app_directories_override =
            Some(tauri::utils::config::AppDirectoriesOverride::Root(root));
    }
    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .setup(|app| {
            let root = app.path().app_data_dir()?;
            #[cfg(debug_assertions)]
            let root = match std::env::var_os("VERN_DEV_DATA_DIR") {
                Some(path) => {
                    let path = std::path::PathBuf::from(path);
                    if !path.is_absolute() {
                        return Err(std::io::Error::other(
                            "VERN_DEV_DATA_DIR must be an absolute path.",
                        )
                        .into());
                    }
                    path
                }
                None => root,
            };
            fs::create_dir_all(&root)?;
            let state = AppState {
                db: Mutex::new(Storage::open(&root.join("vern.sqlite3"))?),
                voices: Arc::new(Voices::new(root.join("voices"))?),
                speech: Arc::new(Speech::default()),
                pending_backup: Mutex::new(None),
                android_conversion: Mutex::new(None),
                classics: Arc::new(classics::Downloads::default()),
                activity: Mutex::new(activity::Tracker::default()),
            };
            app.manage(Arc::new(state));
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            list_opened_books,
            record_reading_open,
            remove_opened_book,
            clear_opened_books,
            list_highlights,
            save_highlight,
            delete_highlight,
            list_reading_lists,
            save_reading_list,
            delete_reading_list,
            record_reading_activity,
            get_reading_activity,
            correct_reading_session,
            add_reading_session,
            export_reading_sessions,
            list_books,
            list_personal_notes,
            list_note_media,
            get_note_media,
            attach_note_media,
            delete_note_media,
            list_note_revisions,
            list_notebooks,
            save_personal_note,
            save_notebook,
            trash_personal_note,
            delete_personal_note,
            delete_notebook,
            list_classics,
            open_assistant,
            open_reference,
            open_app_link,
            open_voice_settings,
            download_classic,
            cancel_classic_download,
            save_classic,
            save_book,
            save_book_with_source,
            get_source_info,
            save_book_cover,
            get_source_file,
            save_source_page,
            save_source_index,
            save_progress,
            toggle_favorite,
            update_book,
            delete_book,
            list_all_annotations,
            delete_annotation,
            list_flashcards,
            save_flashcard,
            list_quizzes,
            save_quiz,
            submit_quiz,
            delete_quiz,
            import_flashcards,
            review_flashcard,
            delete_flashcard,
            list_annotations,
            save_annotation,
            get_settings,
            save_settings,
            export_backup,
            inspect_backup,
            android_backup_text,
            accept_android_text,
            finish_android_backup,
            list_android_imports,
            export_android_import,
            delete_android_import,
            restore_backup,
            discard_backup,
            export_book,
            export_passage_audio,
            pick_book,
            list_voices,
            download_voice,
            cancel_download,
            cancel_speech,
            synthesize
        ])
        .run(context)
        .expect("Could not run Vern");
}
