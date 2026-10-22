pub mod classics;
pub mod documents;
mod handoff;
pub mod notes;
pub mod quizzes;
pub mod reading_tools;
pub mod speech;
pub mod storage;
pub mod study;
pub mod transfer;
pub mod voices;
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
    classics: Arc<classics::Downloads>,
}
#[tauri::command]
async fn open_assistant(id: String) -> Result<()> {
    work(move || handoff::open(&id)).await
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
        let bytes = state.db.lock().map_err(error)?.backup_bytes()?;
        save_export(&app, "Vern-desktop-backup.json", "json", &bytes)
    })
    .await
}
#[tauri::command]
async fn inspect_backup(
    app: tauri::AppHandle,
    state: State<'_, Arc<AppState>>,
) -> Result<Option<BackupPreview>> {
    let state = state.inner().clone();
    work(move || {
        *state.pending_backup.lock().map_err(error)? = None;
        let Some(file) = app
            .dialog()
            .file()
            .add_filter("Vern desktop backup", &["json"])
            .blocking_pick_file()
        else {
            return Ok(None);
        };
        let backup = transfer::read_backup(&file.into_path().map_err(error)?)?;
        let token = uuid::Uuid::new_v4().to_string();
        let preview = backup.preview(token.clone());
        *state.pending_backup.lock().map_err(error)? = Some((token, backup));
        Ok(Some(preview))
    })
    .await
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
        descriptor(&voice_id)?;
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
        descriptor(&voice_id)?;
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
                classics: Arc::new(classics::Downloads::default()),
            };
            app.manage(Arc::new(state));
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            list_books,
            list_personal_notes,
            list_notebooks,
            save_personal_note,
            save_notebook,
            trash_personal_note,
            delete_personal_note,
            delete_notebook,
            list_classics,
            open_assistant,
            download_classic,
            cancel_classic_download,
            save_classic,
            save_book,
            save_book_with_source,
            get_source_info,
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
        .run(tauri::generate_context!())
        .expect("Could not run Vern");
}
