use crate::{
    storage::{error, Annotation, Book, Result, Settings, Storage},
    study::{CardInput, Flashcard},
};
use rusqlite::params;
use serde::{Deserialize, Serialize};
use std::{
    collections::HashSet,
    fs::{self, OpenOptions},
    io::{Read, Write},
    path::Path,
};

pub const LIMIT: usize = 64 * 1024 * 1024;
pub fn wav_bytes(audio: crate::speech::Audio) -> Result<Vec<u8>> {
    if !(8000..=96000).contains(&audio.sample_rate)
        || audio.samples.is_empty()
        || audio.samples.len() > (LIMIT - 44) / 2
        || audio.samples.iter().any(|s| !s.is_finite())
    {
        return Err("The voice returned unsupported audio for WAV export.".into());
    }
    let data_size = u32::try_from(audio.samples.len() * 2).map_err(error)?;
    let rate = u32::try_from(audio.sample_rate).map_err(error)?;
    let mut bytes = Vec::with_capacity(data_size as usize + 44);
    bytes.extend_from_slice(b"RIFF");
    bytes.extend_from_slice(&(data_size + 36).to_le_bytes());
    bytes.extend_from_slice(b"WAVEfmt ");
    bytes.extend_from_slice(&16_u32.to_le_bytes());
    bytes.extend_from_slice(&1_u16.to_le_bytes());
    bytes.extend_from_slice(&1_u16.to_le_bytes());
    bytes.extend_from_slice(&rate.to_le_bytes());
    bytes.extend_from_slice(&(rate * 2).to_le_bytes());
    bytes.extend_from_slice(&2_u16.to_le_bytes());
    bytes.extend_from_slice(&16_u16.to_le_bytes());
    bytes.extend_from_slice(b"data");
    bytes.extend_from_slice(&data_size.to_le_bytes());
    for sample in audio.samples {
        bytes
            .extend_from_slice(&((sample.clamp(-1.0, 1.0) * 32767.0).round() as i16).to_le_bytes());
    }
    Ok(bytes)
}
const SCHEMA: &str = "vern.desktop.backup.v1";

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Backup {
    schema: String,
    books: Vec<Book>,
    annotations: Vec<Annotation>,
    flashcards: Vec<Flashcard>,
    #[serde(default)]
    quizzes: Vec<crate::quizzes::Quiz>,
    #[serde(default)]
    notes: Vec<crate::notes::Note>,
    #[serde(default)]
    notebooks: Vec<crate::notes::Notebook>,
    settings: Settings,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BackupPreview {
    pub token: String,
    pub books: usize,
    pub annotations: usize,
    pub flashcards: usize,
    pub quizzes: usize,
    pub notes: usize,
    pub notebooks: usize,
    pub vocabulary: usize,
    pub pronunciation_rules: usize,
}

// Stop serialization at the same bound enforced on import, before writing any destination.
struct BoundedBytes(Vec<u8>);
impl Write for BoundedBytes {
    fn write(&mut self, bytes: &[u8]) -> std::io::Result<usize> {
        if self.0.len().saturating_add(bytes.len()) > LIMIT {
            return Err(std::io::Error::other("Backup exceeds the 64 MB limit."));
        }
        self.0.extend_from_slice(bytes);
        Ok(bytes.len())
    }
    fn flush(&mut self) -> std::io::Result<()> {
        Ok(())
    }
}

pub fn read_backup(path: &Path) -> Result<Backup> {
    let file = fs::File::open(path).map_err(error)?;
    if !file.metadata().map_err(error)?.is_file() {
        return Err("Select a regular backup file.".into());
    }
    let mut bytes = Vec::new();
    file.take((LIMIT + 1) as u64)
        .read_to_end(&mut bytes)
        .map_err(error)?;
    if bytes.len() > LIMIT {
        return Err("Backup exceeds the 64 MB limit.".into());
    }
    let value: serde_json::Value = serde_json::from_slice(&bytes).map_err(error)?;
    if value.get("schema").and_then(|v| v.as_str()) == Some("veritas.reader.backup.v1") {
        return Err("This is an Android backup. Android backup migration is not supported yet; choose a Vern desktop backup.".into());
    }
    let backup: Backup = serde_json::from_value(value).map_err(error)?;
    backup.validate()?;
    Ok(backup)
}

pub fn write_file(path: &Path, bytes: &[u8]) -> Result<()> {
    let parent = path.parent().ok_or("Choose a destination folder.")?;
    let staging = parent.join(format!(".vern-export-{}.tmp", uuid::Uuid::new_v4()));
    let result = (|| {
        let mut file = OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(&staging)
            .map_err(error)?;
        file.write_all(bytes).map_err(error)?;
        file.sync_all().map_err(error)?;
        drop(file);
        fs::rename(&staging, path).map_err(error)
    })();
    if result.is_err() {
        let _ = fs::remove_file(&staging);
    }
    result
}

impl Backup {
    pub fn preview(&self, token: String) -> BackupPreview {
        BackupPreview {
            token,
            books: self.books.len(),
            annotations: self.annotations.len(),
            flashcards: self.flashcards.len(),
            quizzes: self.quizzes.len(),
            notes: self.notes.len(),
            notebooks: self.notebooks.len(),
            vocabulary: self.settings.vocabulary.len(),
            pronunciation_rules: self.settings.pronunciation_rules.len(),
        }
    }
    fn validate(&self) -> Result<()> {
        if self.schema != SCHEMA {
            return Err("Unsupported desktop backup version.".into());
        }
        if [
            self.books.len(),
            self.annotations.len(),
            self.flashcards.len(),
            self.quizzes.len(),
            self.notes.len(),
            self.notebooks.len(),
        ]
        .iter()
        .any(|n| *n > 20_000)
        {
            return Err("Backup contains too many items (maximum 20,000 per section).".into());
        }
        // Validate with the same native rules as ordinary commands, in an isolated database.
        let mut scratch = Storage::open(Path::new(":memory:"))?;
        let mut ids = HashSet::new();
        for book in &self.books {
            if !ids.insert(book.id.clone()) {
                return Err("Backup contains duplicate books.".into());
            }
            let saved = scratch.save_book(book.clone())?;
            if saved.id != book.id {
                return Err("A backed-up book has an invalid content identifier.".into());
            }
            scratch.update_book(&book.id, &book.title, &book.author, &book.collection)?;
            scratch.progress(&book.id, book.position)?;
            if book.updated_at > 8_640_000_000_000_000 {
                return Err("Invalid book timestamp.".into());
            }
        }
        ids.clear();
        for item in &self.annotations {
            if !ids.insert(item.id.clone()) {
                return Err("Backup contains duplicate notes or bookmarks.".into());
            }
            scratch.annotate(item.clone())?;
        }
        ids.clear();
        for card in &self.flashcards {
            if !ids.insert(card.id.clone()) {
                return Err("Backup contains duplicate flashcards.".into());
            }
            scratch.restore_card(card)?;
        }
        ids.clear();
        for quiz in &self.quizzes {
            if !ids.insert(quiz.id.clone()) {
                return Err("Backup contains duplicate quizzes.".into());
            }
            scratch.restore_quiz(quiz)?;
        }
        ids.clear();
        for notebook in &self.notebooks {
            if !ids.insert(notebook.id.clone()) {
                return Err("Backup contains duplicate notebooks.".into());
            }
            scratch.restore_notebook(notebook)?;
        }
        ids.clear();
        for note in &self.notes {
            if !ids.insert(note.id.clone()) {
                return Err("Backup contains duplicate personal notes.".into());
            }
            scratch.restore_note(note)?;
        }
        if !self.settings.last_book.is_empty()
            && !self.books.iter().any(|b| b.id == self.settings.last_book)
        {
            return Err("The backup resume target does not belong to its library.".into());
        }
        scratch.save_settings(self.settings.clone())
    }
}

impl Storage {
    pub fn backup_bytes(&self) -> Result<Vec<u8>> {
        let mut backup = Backup {
            schema: SCHEMA.into(),
            books: self.books()?,
            annotations: self.all_annotations()?,
            flashcards: self.flashcards()?,
            quizzes: self.quizzes()?,
            notes: self.notes()?,
            notebooks: self.notebooks()?,
            settings: self.settings()?,
        };
        if !backup
            .books
            .iter()
            .any(|b| b.id == backup.settings.last_book)
        {
            backup.settings.last_book.clear();
        }
        if [
            backup.books.len(),
            backup.annotations.len(),
            backup.flashcards.len(),
            backup.quizzes.len(),
            backup.notes.len(),
            backup.notebooks.len(),
        ]
        .iter()
        .any(|n| *n > 20_000)
        {
            return Err("A backup supports up to 20,000 items in each section.".into());
        }
        let mut output = BoundedBytes(Vec::new());
        serde_json::to_writer(&mut output, &backup).map_err(error)?;
        Ok(output.0)
    }
    fn restore_card(&mut self, card: &Flashcard) -> Result<()> {
        if !["", "again", "hard", "good", "easy"].contains(&card.recall.as_str())
            || card.due_at > 8_640_000_000_000_000
            || card.interval_days > 100_000
            || card.repetitions > 1_000_000
            || !card.ease.is_finite()
            || !(1.3..=3.0).contains(&card.ease)
        {
            return Err("A flashcard has an invalid review schedule.".into());
        }
        self.save_flashcard(CardInput {
            id: Some(card.id.clone()),
            book_id: card.book_id.clone(),
            position: card.position,
            deck: card.deck.clone(),
            front: card.front.clone(),
            back: card.back.clone(),
        })?;
        self.connection.execute("UPDATE flashcards SET recall=?1,due_at=?2,interval_days=?3,repetitions=?4,ease=?5 WHERE id=?6",
            params![card.recall,i64::try_from(card.due_at).map_err(error)?,card.interval_days,card.repetitions,card.ease,card.id]).map_err(error)?;
        Ok(())
    }
    pub fn restore_backup(&mut self, backup: Backup, restore_settings: bool) -> Result<()> {
        self.connection
            .execute_batch("SAVEPOINT restore_backup")
            .map_err(error)?;
        let result = (|| {
            let current_settings = self.settings()?;
            let existing_books: HashSet<String> = self.books()?.into_iter().map(|b| b.id).collect();
            let existing_notes: HashSet<String> =
                self.all_annotations()?.into_iter().map(|a| a.id).collect();
            let existing_cards: HashSet<String> =
                self.flashcards()?.into_iter().map(|c| c.id).collect();
            for book in backup.books {
                if existing_books.contains(&book.id) {
                    continue;
                }
                self.save_book(book.clone())?;
                self.update_book(&book.id, &book.title, &book.author, &book.collection)?;
                self.progress(&book.id, book.position)?;
                self.connection
                    .execute(
                        "UPDATE books SET updated_at=?1 WHERE id=?2",
                        params![i64::try_from(book.updated_at).map_err(error)?, book.id],
                    )
                    .map_err(error)?;
            }
            for item in backup.annotations {
                if !existing_notes.contains(&item.id) {
                    self.annotate(item)?;
                }
            }
            for card in backup.flashcards {
                if !existing_cards.contains(&card.id) {
                    self.restore_card(&card)?;
                }
            }
            for quiz in backup.quizzes {
                if !self.quiz_exists(&quiz.id)? {
                    self.restore_quiz(&quiz)?;
                }
            }
            let notebooks: HashSet<String> = self.notebooks()?.into_iter().map(|n| n.id).collect();
            for notebook in backup.notebooks {
                if !notebooks.contains(&notebook.id) {
                    self.restore_notebook(&notebook)?;
                }
            }
            let notes: HashSet<String> = self.notes()?.into_iter().map(|n| n.id).collect();
            for note in backup.notes {
                if !notes.contains(&note.id) {
                    self.restore_note(&note)?;
                }
            }
            let mut settings = if restore_settings {
                backup.settings.clone()
            } else {
                current_settings.clone()
            };
            // These collections are library data, even when current reader preferences are kept.
            settings.queue = current_settings.queue;
            for id in backup.settings.queue {
                if !settings.queue.contains(&id) {
                    settings.queue.push(id);
                }
            }
            settings.vocabulary = current_settings.vocabulary;
            for entry in backup.settings.vocabulary {
                if !settings.vocabulary.iter().any(|v| v.id == entry.id) {
                    settings.vocabulary.push(entry);
                }
            }
            settings.pronunciation_rules = current_settings.pronunciation_rules;
            for rule in backup.settings.pronunciation_rules {
                if !settings
                    .pronunciation_rules
                    .iter()
                    .any(|r| r.find.trim().to_lowercase() == rule.find.trim().to_lowercase())
                {
                    settings.pronunciation_rules.push(rule);
                }
            }
            self.save_settings(settings)?;
            Ok(())
        })();
        match result {
            Ok(()) => self
                .connection
                .execute_batch("RELEASE restore_backup")
                .map_err(error),
            Err(e) => {
                self.connection
                    .execute_batch("ROLLBACK TO restore_backup; RELEASE restore_backup")
                    .map_err(error)?;
                Err(e)
            }
        }
    }
    pub fn export_book(&self, id: &str, kind: &str) -> Result<Vec<u8>> {
        let book = self.book(id)?;
        let content = match kind {
            "text" => format!(
                "{}\n{}\n\n{}\n",
                book.title,
                book.author,
                book.chunks.join("\n\n")
            ),
            "notes" => {
                let mut output = BoundedBytes(Vec::new());
                writeln!(output, "# Notes on {}\n", book.title).map_err(error)?;
                for a in self.annotations(id)? {
                    write!(
                        output,
                        "## {} · Passage {}\n\n> {}\n\n{}\n\n",
                        a.kind,
                        a.position + 1,
                        book.chunks
                            .get(a.position)
                            .ok_or("A note has an invalid source passage.")?
                            .replace('\n', "\n> "),
                        a.text
                    )
                    .map_err(error)?;
                }
                return Ok(output.0);
            }
            _ => return Err("Choose text or notes export.".into()),
        };
        if content.len() > LIMIT {
            return Err("Export exceeds the 64 MB limit.".into());
        }
        Ok(content.into_bytes())
    }
}
