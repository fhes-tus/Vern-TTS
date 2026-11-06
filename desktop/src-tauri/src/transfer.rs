use crate::{
    storage::{error, Annotation, Book, Result, Settings, Storage},
    study::{CardInput, Flashcard},
};
use rusqlite::params;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{
    collections::HashSet,
    fs::{self, OpenOptions},
    io::{Read, Seek, Write},
    path::Path,
    sync::Arc,
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
const LEGACY_FULL_SCHEMA: &str = "vern.desktop.backup.v2";
pub(crate) const FULL_SCHEMA: &str = "vern.desktop.backup.v6";
pub(crate) fn supported_schema(schema: &str) -> bool {
    [
        SCHEMA,
        LEGACY_FULL_SCHEMA,
        "vern.desktop.backup.v3",
        "vern.desktop.backup.v4",
        "vern.desktop.backup.v5",
        FULL_SCHEMA,
    ]
    .contains(&schema)
}

#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct SourceBackup {
    pub book_id: String,
    pub info: crate::documents::SourceInfo,
    pub size: u64,
    pub sha256: String,
    #[serde(skip)]
    pub staged: Option<Arc<crate::backup_archive::StagedFile>>,
}
#[derive(Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct MediaBackup {
    pub info: crate::note_media::NoteMedia,
    #[serde(skip)]
    pub staged: Option<Arc<crate::backup_archive::StagedFile>>,
}
impl MediaBackup {
    pub fn bytes(&self) -> Result<Vec<u8>> {
        let bytes = self
            .staged
            .as_ref()
            .ok_or("Choose the full backup archive; its note attachments are missing.")?
            .read(crate::note_media::LIMIT)?;
        self.info.validate(&bytes)?;
        Ok(bytes)
    }
}
impl SourceBackup {
    pub fn archive_name(&self) -> Result<String> {
        if self.book_id.len() != 64
            || !self
                .book_id
                .bytes()
                .all(|b| b.is_ascii_digit() || (b'a'..=b'f').contains(&b))
        {
            return Err("An original document has an invalid library identifier.".into());
        }
        Ok(format!("originals/{}.bin", self.book_id))
    }
    pub fn bytes(&self) -> Result<Vec<u8>> {
        let bytes = self
            .staged
            .as_ref()
            .ok_or("Choose the full backup archive; its original files are missing.")?
            .read(32 * 1024 * 1024)?;
        if bytes.len() as u64 != self.size
            || crate::storage::hex_digest(Sha256::digest(&bytes)) != self.sha256
        {
            return Err(
                "A backed-up original file failed its integrity check. Choose the backup again."
                    .into(),
            );
        }
        Ok(bytes)
    }
}

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Backup {
    schema: String,
    books: Vec<Book>,
    annotations: Vec<Annotation>,
    #[serde(default)]
    highlights: Vec<crate::highlights::ReadingHighlight>,
    flashcards: Vec<Flashcard>,
    #[serde(default)]
    quizzes: Vec<crate::quizzes::Quiz>,
    #[serde(default)]
    notes: Vec<crate::notes::Note>,
    #[serde(default)]
    note_revisions: Vec<crate::notes::NoteRevision>,
    #[serde(default)]
    notebooks: Vec<crate::notes::Notebook>,
    #[serde(default)]
    sessions: Vec<crate::activity::Session>,
    #[serde(default)]
    opened_books: Vec<crate::opened_books::OpenedBook>,
    #[serde(default)]
    legacy_days: Vec<crate::legacy_activity::LegacyDay>,
    #[serde(default)]
    legacy_book_times: Vec<crate::legacy_activity::LegacyBookTime>,
    settings: Settings,
    #[serde(default)]
    reading_lists: Vec<crate::reading_lists::ReadingList>,
    #[serde(default)]
    classic_links: Vec<crate::classics::ClassicLink>,
    #[serde(default)]
    pub(crate) sources: Vec<SourceBackup>,
    #[serde(default)]
    pub(crate) note_media: Vec<MediaBackup>,
    #[serde(default)]
    pub(crate) android_imports: Vec<crate::android_archive::AndroidImport>,
    #[serde(skip)]
    pub(crate) origin: String,
    #[serde(skip)]
    pub(crate) warnings: Vec<String>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BackupPreview {
    pub token: String,
    pub books: usize,
    pub annotations: usize,
    pub highlights: usize,
    pub flashcards: usize,
    pub quizzes: usize,
    pub notes: usize,
    pub notebooks: usize,
    pub sessions: usize,
    pub opened_books: usize,
    pub legacy_days: usize,
    pub legacy_book_times: usize,
    pub vocabulary: usize,
    pub pronunciation_rules: usize,
    pub source_files: usize,
    pub source: String,
    pub warnings: Vec<String>,
    pub reading_lists: usize,
    pub note_revisions: usize,
    pub note_media: usize,
    pub retained_android_backups: usize,
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
    let mut file = fs::File::open(path).map_err(error)?;
    if !file.metadata().map_err(error)?.is_file() {
        return Err("Select a regular backup file.".into());
    }
    let mut magic = [0_u8; 4];
    let count = file.read(&mut magic).map_err(error)?;
    if count >= 2 && magic.starts_with(b"PK") {
        return crate::backup_archive::read(path);
    }
    file.rewind().map_err(error)?;
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
    pub(crate) fn json_bytes(&self) -> Result<Vec<u8>> {
        let mut output = BoundedBytes(Vec::new());
        serde_json::to_writer(&mut output, self).map_err(error)?;
        Ok(output.0)
    }
    pub fn preview(&self, token: String) -> BackupPreview {
        BackupPreview {
            token,
            books: self.books.len(),
            annotations: self.annotations.len(),
            highlights: self.highlights.len(),
            flashcards: self.flashcards.len(),
            quizzes: self.quizzes.len(),
            notes: self.notes.len(),
            notebooks: self.notebooks.len(),
            sessions: self.sessions.len(),
            opened_books: self.opened_books.len(),
            legacy_days: self.legacy_days.len(),
            legacy_book_times: self.legacy_book_times.len(),
            vocabulary: self.settings.vocabulary.len(),
            pronunciation_rules: self.settings.pronunciation_rules.len(),
            source_files: self.sources.len(),
            source: if self.origin.is_empty() {
                "Desktop".into()
            } else {
                self.origin.clone()
            },
            warnings: self.warnings.clone(),
            reading_lists: self.reading_lists.len(),
            note_revisions: self.note_revisions.len(),
            note_media: self.note_media.len(),
            retained_android_backups: self.android_imports.len(),
        }
    }
    pub(crate) fn validate(&self) -> Result<()> {
        if !supported_schema(&self.schema) {
            return Err("Unsupported desktop backup version.".into());
        }
        if [
            self.books.len(),
            self.annotations.len(),
            self.highlights.len(),
            self.flashcards.len(),
            self.quizzes.len(),
            self.notes.len(),
            self.notebooks.len(),
            self.sessions.len(),
            self.sources.len(),
            self.reading_lists.len(),
            self.classic_links.len(),
            self.note_revisions.len(),
            self.note_media.len(),
        ]
        .iter()
        .any(|n| *n > 20_000)
        {
            return Err("Backup contains too many items (maximum 20,000 per section).".into());
        }
        // Validate with the same native rules as ordinary commands, in an isolated database.
        let mut scratch = Storage::open(Path::new(":memory:"))?;
        if self.legacy_days.len() > crate::legacy_activity::LIMIT
            || self.legacy_book_times.len() > crate::legacy_activity::LIMIT
        {
            return Err("Legacy activity exceeds 20,000 records per section.".into());
        }
        if self.opened_books.len() > crate::opened_books::LIMIT {
            return Err("A backup supports up to 40 recent books.".into());
        }
        if self.android_imports.len() > 100 {
            return Err("A backup supports up to 100 retained Android imports.".into());
        }
        let mut import_ids = HashSet::new();
        for item in &self.android_imports {
            if !import_ids.insert(&item.id) {
                return Err("Duplicate retained Android import.".into());
            }
            item.validate()?;
        }
        let mut ids = HashSet::new();
        for book in &self.books {
            if !ids.insert(book.id.clone()) {
                return Err("Backup contains duplicate books.".into());
            }
            let digest = self
                .sources
                .iter()
                .find(|s| s.book_id == book.id)
                .map(|s| s.sha256.as_str());
            let saved = scratch.restore_book_exact(book.clone(), digest)?;
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
        for source in &self.sources {
            if !ids.insert(source.book_id.clone()) {
                return Err("Backup contains duplicate original files.".into());
            }
            source.archive_name()?;
            let mut book = scratch.book(&source.book_id)?;
            book.format.clone_from(&source.info.format);
            let bytes = source.bytes()?;
            crate::documents::validate_source(
                &book,
                &source.info.name,
                &bytes,
                &source.info.page_starts,
            )?;
            if source.info.page >= source.info.page_starts.len().max(1) {
                return Err("A backed-up original page is outside its document.".into());
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
        for item in &self.highlights {
            if !ids.insert(item.id.clone()) {
                return Err("Backup contains duplicate reading highlights.".into());
            }
            scratch.restore_highlight(item)?;
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
        ids.clear();
        for revision in &self.note_revisions {
            if !ids.insert(revision.id.clone()) {
                return Err("Backup contains duplicate note revisions.".into());
            }
            scratch.restore_note_revision(revision)?;
        }
        ids.clear();
        let note_ids: HashSet<_> = self.notes.iter().map(|note| note.id.as_str()).collect();
        let mut media_counts = std::collections::HashMap::<&str, (usize, usize)>::new();
        for media in &self.note_media {
            if !ids.insert(media.info.id.clone()) || !note_ids.contains(media.info.note_id.as_str())
            {
                return Err("A note attachment is duplicated or has no parent note.".into());
            }
            let bytes = media.bytes()?;
            let totals = media_counts.entry(&media.info.note_id).or_default();
            totals.0 += 1;
            totals.1 = totals
                .1
                .checked_add(bytes.len())
                .ok_or("Attachment size overflow.")?;
            if totals.0 > 20 || totals.1 > 64 * 1024 * 1024 {
                return Err("A note exceeds its attachment limit.".into());
            }
        }
        ids.clear();
        for item in &self.opened_books {
            if !ids.insert(item.id.clone()) {
                return Err("Duplicate recent-book history.".into());
            }
            scratch.restore_opened_book(item)?;
        }
        ids.clear();
        for session in &self.sessions {
            if !ids.insert(session.id.clone()) {
                return Err("Backup contains duplicate reading sessions.".into());
            }
            scratch.restore_session(session)?;
        }
        ids.clear();
        for day in &self.legacy_days {
            if !ids.insert(day.date.clone()) {
                return Err("Duplicate legacy activity date.".into());
            }
            scratch.restore_legacy_day(day)?;
        }
        ids.clear();
        for item in &self.legacy_book_times {
            if !ids.insert(item.id.clone()) {
                return Err("Duplicate legacy book time.".into());
            }
            scratch.restore_legacy_book_time(item)?;
        }
        ids.clear();
        for list in &self.reading_lists {
            if !ids.insert(list.id.clone()) {
                return Err("Backup contains duplicate reading lists.".into());
            }
            scratch.restore_reading_list(list)?;
        }
        ids.clear();
        for link in &self.classic_links {
            if !ids.insert(link.catalog_id.clone()) {
                return Err("Backup contains duplicate catalogue links.".into());
            }
            scratch.restore_classic_link(link)?;
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
        let backup = self.backup_snapshot(false)?;
        backup.json_bytes()
    }
    pub(crate) fn backup_snapshot(&self, include_sources: bool) -> Result<Backup> {
        let mut backup = Backup {
            schema: FULL_SCHEMA.into(),
            books: self.books()?,
            annotations: self.all_annotations()?,
            highlights: self.all_highlights()?,
            flashcards: self.flashcards()?,
            quizzes: self.quizzes()?,
            notes: self.notes()?,
            note_revisions: self.all_note_revisions()?,
            notebooks: self.notebooks()?,
            sessions: self.sessions()?,
            opened_books: self.opened_books()?,
            legacy_days: self.legacy_days()?,
            legacy_book_times: self.legacy_book_times()?,
            settings: self.settings()?,
            reading_lists: self.reading_lists()?,
            classic_links: self.classic_links()?,
            sources: Vec::new(),
            note_media: Vec::new(),
            android_imports: self.android_imports()?,
            origin: String::new(),
            warnings: Vec::new(),
        };
        if !include_sources
            && backup.books.iter().any(|book| {
                serde_json::to_string(&book.chunks)
                    .map(|s| crate::storage::hex_digest(Sha256::digest(s.as_bytes())) != book.id)
                    .unwrap_or(true)
            })
        {
            return Err("Save a full backup to preserve scanned original files.".into());
        }
        if include_sources {
            backup.note_media = self
                .all_note_media()?
                .into_iter()
                .map(|info| MediaBackup { info, staged: None })
                .collect();
            for book in &backup.books {
                if let (Some(info), Some(file)) =
                    (self.source_info(&book.id)?, self.source_file(&book.id)?)
                {
                    backup.sources.push(SourceBackup {
                        book_id: book.id.clone(),
                        info,
                        size: file.bytes.len() as u64,
                        sha256: crate::storage::hex_digest(Sha256::digest(&file.bytes)),
                        staged: None,
                    });
                }
            }
        }
        if !include_sources && !self.all_note_media()?.is_empty() {
            return Err("Save a full backup to include note attachments.".into());
        }
        if !include_sources
            && backup
                .android_imports
                .iter()
                .any(|item| !item.assets.is_empty())
        {
            return Err("Save a full backup to include retained Android attachments.".into());
        }
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
            backup.highlights.len(),
            backup.flashcards.len(),
            backup.quizzes.len(),
            backup.notes.len(),
            backup.notebooks.len(),
            backup.sessions.len(),
            backup.reading_lists.len(),
            backup.classic_links.len(),
            backup.note_revisions.len(),
            backup.note_media.len(),
        ]
        .iter()
        .any(|n| *n > 20_000)
        {
            return Err("A backup supports up to 20,000 items in each section.".into());
        }
        Ok(backup)
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
        backup.validate()?;
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
                let digest = backup
                    .sources
                    .iter()
                    .find(|s| s.book_id == book.id)
                    .map(|s| s.sha256.as_str());
                self.restore_book_exact(book.clone(), digest)?;
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
            let highlights: HashSet<String> = self
                .all_highlights()?
                .into_iter()
                .map(|item| item.id)
                .collect();
            for item in backup.highlights {
                if !highlights.contains(&item.id) {
                    self.restore_highlight(&item)?;
                }
            }
            for source in backup.sources {
                let bytes = source.bytes()?;
                self.restore_source(&source.book_id, &source.info, bytes)?;
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
            for revision in backup.note_revisions.into_iter().rev() {
                self.restore_note_revision(&revision)?;
            }
            for media in backup.note_media {
                self.restore_note_media(&media.info, &media.bytes()?)?;
            }
            for session in backup.sessions {
                self.restore_session(&session)?;
            }
            for item in backup.opened_books {
                self.restore_opened_book(&item)?;
            }
            for day in backup.legacy_days {
                self.restore_legacy_day(&day)?;
            }
            for item in backup.legacy_book_times {
                self.restore_legacy_book_time(&item)?;
            }
            for item in backup.android_imports {
                self.restore_android_import(&item)?;
            }
            let lists: HashSet<String> = self.reading_lists()?.into_iter().map(|l| l.id).collect();
            for list in backup.reading_lists {
                if !lists.contains(&list.id) {
                    self.restore_reading_list(&list)?;
                }
            }
            for link in backup.classic_links {
                self.restore_classic_link(&link)?;
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
