use rusqlite::{params, Connection};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::path::Path;
use std::time::{SystemTime, UNIX_EPOCH};

pub type Result<T> = std::result::Result<T, String>;
pub fn error(e: impl std::fmt::Display) -> String {
    e.to_string()
}

fn read_unsigned<T: TryFrom<i64>>(row: &rusqlite::Row<'_>, index: usize) -> rusqlite::Result<T> {
    let value: i64 = row.get(index)?;
    T::try_from(value).map_err(|_| rusqlite::Error::IntegralValueOutOfRange(index, value))
}

pub fn hex_digest(bytes: impl AsRef<[u8]>) -> String {
    const DIGITS: &[u8; 16] = b"0123456789abcdef";
    let bytes = bytes.as_ref();
    let mut output = String::with_capacity(bytes.len() * 2);
    for byte in bytes {
        output.push(DIGITS[(byte >> 4) as usize] as char);
        output.push(DIGITS[(byte & 0x0f) as usize] as char);
    }
    output
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Book {
    #[serde(default)]
    pub id: String,
    pub title: String,
    pub author: String,
    pub format: String,
    pub chunks: Vec<String>,
    pub position: usize,
    pub favorite: bool,
    #[serde(default)]
    pub collection: String,
    #[serde(default = "default_text_available")]
    pub text_available: bool,
    #[serde(default)]
    pub updated_at: u64,
}
fn default_text_available() -> bool {
    true
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Annotation {
    pub id: String,
    pub book_id: String,
    pub position: usize,
    pub text: String,
    pub kind: String,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Settings {
    pub theme: String,
    #[serde(default = "default_color_theme")]
    pub color_theme: String,
    #[serde(default)]
    pub accent_color: String,
    #[serde(default)]
    pub reduce_motion: bool,
    pub font_size: u32,
    pub reader_font: String,
    pub voice: String,
    pub rate: f32,
    pub last_book: String,
    #[serde(default)]
    pub queue: Vec<String>,
    #[serde(default)]
    pub pronunciation_rules: Vec<crate::reading_tools::PronunciationRule>,
    #[serde(default)]
    pub vocabulary: Vec<crate::reading_tools::Vocabulary>,
    #[serde(default = "default_reader_layout")]
    pub reader_layout: String,
    #[serde(default = "default_reader_view")]
    pub reader_view: String,
}
fn default_reader_layout() -> String {
    "single".into()
}
fn default_color_theme() -> String {
    "sage".into()
}
fn default_reader_view() -> String {
    "flow".into()
}
impl Default for Settings {
    fn default() -> Self {
        Self {
            theme: "light".into(),
            color_theme: default_color_theme(),
            accent_color: String::new(),
            reduce_motion: false,
            font_size: 21,
            reader_font: "Bitter".into(),
            voice: "piper_en_us_lessac".into(),
            rate: 1.0,
            last_book: String::new(),
            queue: Vec::new(),
            pronunciation_rules: Vec::new(),
            vocabulary: Vec::new(),
            reader_layout: default_reader_layout(),
            reader_view: default_reader_view(),
        }
    }
}

pub struct Storage {
    pub(crate) connection: Connection,
}
impl Storage {
    pub fn open(path: &Path) -> Result<Self> {
        let connection = Connection::open(path).map_err(error)?;
        connection
            .busy_timeout(std::time::Duration::from_secs(5))
            .map_err(error)?;
        connection
            .execute_batch(
                "PRAGMA foreign_keys=ON; PRAGMA journal_mode=WAL; PRAGMA synchronous=FULL;",
            )
            .map_err(error)?;
        let version: u32 = connection
            .pragma_query_value(None, "user_version", |row| row.get(0))
            .map_err(error)?;
        if version > 6 {
            return Err("This library was created by a newer Vern version.".into());
        }
        connection.execute_batch("BEGIN;
            CREATE TABLE IF NOT EXISTS books (id TEXT PRIMARY KEY, title TEXT NOT NULL, author TEXT NOT NULL, format TEXT NOT NULL, chunks TEXT NOT NULL, position INTEGER NOT NULL DEFAULT 0, favorite INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS annotations (id TEXT PRIMARY KEY, book_id TEXT NOT NULL REFERENCES books(id) ON DELETE CASCADE, position INTEGER NOT NULL, text TEXT NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('note','bookmark')));
            CREATE INDEX IF NOT EXISTS annotation_book ON annotations(book_id, position);
            CREATE TABLE IF NOT EXISTS settings (id INTEGER PRIMARY KEY CHECK(id=1), value TEXT NOT NULL);
            COMMIT;").map_err(error)?;
        if version < 2 {
            connection
                .execute_batch(
                    "BEGIN;
                ALTER TABLE books ADD COLUMN collection TEXT NOT NULL DEFAULT '';
                CREATE TABLE flashcards (
                    id TEXT PRIMARY KEY,
                    book_id TEXT REFERENCES books(id) ON DELETE CASCADE,
                    position INTEGER,
                    deck TEXT NOT NULL,
                    front TEXT NOT NULL,
                    back TEXT NOT NULL,
                    recall TEXT NOT NULL DEFAULT '',
                    due_at INTEGER NOT NULL DEFAULT 0,
                    interval_days INTEGER NOT NULL DEFAULT 0,
                    repetitions INTEGER NOT NULL DEFAULT 0,
                    ease REAL NOT NULL DEFAULT 2.5
                );
                CREATE INDEX flashcard_due ON flashcards(due_at);
                CREATE INDEX flashcard_book ON flashcards(book_id);
                PRAGMA user_version=2; COMMIT;",
                )
                .map_err(error)?;
        }
        if version < 3 {
            connection.execute_batch("BEGIN; ALTER TABLE books ADD COLUMN text_available INTEGER NOT NULL DEFAULT 1; CREATE TABLE sources(book_id TEXT PRIMARY KEY REFERENCES books(id) ON DELETE CASCADE,name TEXT NOT NULL,format TEXT NOT NULL,bytes BLOB NOT NULL,page_starts TEXT NOT NULL,page INTEGER NOT NULL DEFAULT 0,text_available INTEGER NOT NULL DEFAULT 1); PRAGMA user_version=3; COMMIT;").map_err(error)?;
        }
        if version < 4 {
            connection.execute_batch("BEGIN; CREATE TABLE classic_books(catalog_id TEXT PRIMARY KEY,book_id TEXT NOT NULL REFERENCES books(id) ON DELETE CASCADE); PRAGMA user_version=4; COMMIT;").map_err(error)?;
        }
        if version < 5 {
            connection.execute_batch("BEGIN; CREATE TABLE quizzes(id TEXT PRIMARY KEY,book_id TEXT REFERENCES books(id) ON DELETE CASCADE,value TEXT NOT NULL); PRAGMA user_version=5; COMMIT;").map_err(error)?;
        }
        if version < 6 {
            connection.execute_batch("BEGIN; CREATE TABLE notebooks(id TEXT PRIMARY KEY,name TEXT NOT NULL,created_at INTEGER NOT NULL); CREATE TABLE personal_notes(id TEXT PRIMARY KEY,value TEXT NOT NULL); PRAGMA user_version=6; COMMIT;").map_err(error)?;
        }
        Ok(Self { connection })
    }
    pub fn books(&self) -> Result<Vec<Book>> {
        let mut query = self.connection.prepare("SELECT id,title,author,format,chunks,position,favorite,updated_at,collection,text_available FROM books ORDER BY updated_at DESC").map_err(error)?;
        let rows = query
            .query_map([], |row| {
                Ok((
                    row.get::<_, String>(0)?,
                    row.get::<_, String>(1)?,
                    row.get::<_, String>(2)?,
                    row.get::<_, String>(3)?,
                    row.get::<_, String>(4)?,
                    read_unsigned::<usize>(row, 5)?,
                    row.get::<_, bool>(6)?,
                    read_unsigned::<u64>(row, 7)?,
                    row.get::<_, String>(8)?,
                    row.get::<_, bool>(9)?,
                ))
            })
            .map_err(error)?;
        rows.map(|row| {
            let (
                id,
                title,
                author,
                format,
                chunks,
                position,
                favorite,
                updated_at,
                collection,
                text_available,
            ) = row.map_err(error)?;
            Ok(Book {
                id,
                title,
                author,
                format,
                chunks: serde_json::from_str(&chunks).map_err(error)?,
                position,
                favorite,
                collection,
                text_available,
                updated_at,
            })
        })
        .collect()
    }
    pub fn save_book(&mut self, mut book: Book) -> Result<Book> {
        if book.title.trim().is_empty()
            || book.title.chars().count() > 1000
            || book.author.chars().count() > 1000
            || !["TXT", "MD", "LOG", "EPUB", "PDF", "DOCX", "PPTX"].contains(&book.format.as_str())
            || book.chunks.is_empty()
            || book.chunks.len() > 100_000
            || book
                .chunks
                .iter()
                .any(|s| s.len() > 4096 || s.trim().is_empty())
            || book.chunks.iter().map(|s| s.len()).sum::<usize>() > 32 * 1024 * 1024
        {
            return Err("Book is empty, too large, or has invalid metadata/chunks.".into());
        }
        let content = serde_json::to_string(&book.chunks).map_err(error)?;
        book.id = hex_digest(Sha256::digest(content.as_bytes()));
        book.position = 0;
        book.collection.clear();
        book.updated_at = now();
        self.connection.execute("INSERT OR IGNORE INTO books(id,title,author,format,chunks,position,favorite,updated_at,text_available) VALUES (?1,?2,?3,?4,?5,0,?6,?7,?8)", params![book.id,book.title,book.author,book.format,content,book.favorite,i64::try_from(book.updated_at).map_err(error)?,book.text_available]).map_err(error)?;
        self.book(&book.id)
    }
    pub fn book(&self, id: &str) -> Result<Book> {
        self.connection.query_row("SELECT id,title,author,format,chunks,position,favorite,updated_at,collection,text_available FROM books WHERE id=?1", [id], |row| {
            let content: String = row.get(4)?;
            let chunks = serde_json::from_str(&content).map_err(|e| rusqlite::Error::FromSqlConversionFailure(4, rusqlite::types::Type::Text, Box::new(e)))?;
            Ok(Book { id: row.get(0)?, title: row.get(1)?, author: row.get(2)?, format: row.get(3)?, chunks, position: read_unsigned(row,5)?, favorite: row.get(6)?, updated_at: read_unsigned(row,7)?, collection: row.get(8)?, text_available: row.get(9)? })
        }).map_err(error)
    }
    pub fn pronunciation_rules(&self) -> Result<Vec<crate::reading_tools::PronunciationRule>> {
        use rusqlite::OptionalExtension;
        let value: Option<Option<String>> = self
            .connection
            .query_row(
                "SELECT json_extract(value,'$.pronunciationRules') FROM settings WHERE id=1",
                [],
                |row| row.get(0),
            )
            .optional()
            .map_err(error)?;
        value
            .flatten()
            .map(|v| serde_json::from_str(&v).map_err(error))
            .unwrap_or_else(|| Ok(Vec::new()))
    }
    pub fn progress(&mut self, id: &str, position: usize) -> Result<()> {
        let count: usize = self
            .connection
            .query_row(
                "SELECT json_array_length(chunks) FROM books WHERE id=?1",
                [id],
                |r| read_unsigned(r, 0),
            )
            .map_err(error)?;
        if position >= count {
            return Err("Reading position is outside this book.".into());
        }
        self.connection
            .execute(
                "UPDATE books SET position=?1,updated_at=?2 WHERE id=?3",
                params![
                    i64::try_from(position).map_err(error)?,
                    i64::try_from(now()).map_err(error)?,
                    id
                ],
            )
            .map_err(error)?;
        Ok(())
    }
    pub fn favorite(&mut self, id: &str) -> Result<()> {
        self.connection
            .execute("UPDATE books SET favorite=NOT favorite WHERE id=?1", [id])
            .map_err(error)?;
        Ok(())
    }
    pub fn update_book(
        &mut self,
        id: &str,
        title: &str,
        author: &str,
        collection: &str,
    ) -> Result<()> {
        if title.trim().is_empty()
            || title.chars().count() > 1000
            || author.chars().count() > 1000
            || collection.chars().count() > 200
        {
            return Err(
                "Use a title and author of up to 1,000 characters and a collection of up to 200 characters.".into(),
            );
        }
        let changed = self
            .connection
            .execute(
                "UPDATE books SET title=?1,author=?2,collection=?3 WHERE id=?4",
                params![title.trim(), author.trim(), collection.trim(), id],
            )
            .map_err(error)?;
        if changed != 1 {
            return Err("This book no longer exists.".into());
        }
        Ok(())
    }
    pub fn delete_book(&mut self, id: &str) -> Result<()> {
        let mut settings = self.settings()?;
        let transaction = self.connection.transaction().map_err(error)?;
        transaction
            .execute("DELETE FROM books WHERE id=?1", [id])
            .map_err(error)?;
        // Clear the resume target in the same transaction as the cascaded notes and cards.
        let was_resume = settings.last_book == id;
        let was_queued = settings.queue.iter().any(|book| book == id);
        if was_resume {
            settings.last_book.clear();
        }
        settings.queue.retain(|book| book != id);
        let was_vocab_source = settings
            .vocabulary
            .iter()
            .any(|entry| entry.book_id.as_deref() == Some(id));
        for entry in &mut settings.vocabulary {
            if entry.book_id.as_deref() == Some(id) {
                entry.book_id = None;
                entry.position = None;
            }
        }
        if was_resume || was_queued || was_vocab_source {
            transaction
                .execute(
                    "UPDATE settings SET value=?1 WHERE id=1",
                    [serde_json::to_string(&settings).map_err(error)?],
                )
                .map_err(error)?;
        }
        transaction.commit().map_err(error)
    }
    pub fn all_annotations(&self) -> Result<Vec<Annotation>> {
        let mut q = self
            .connection
            .prepare("SELECT id,book_id,position,text,kind FROM annotations ORDER BY rowid DESC")
            .map_err(error)?;
        let rows = q
            .query_map([], |r| {
                Ok(Annotation {
                    id: r.get(0)?,
                    book_id: r.get(1)?,
                    position: read_unsigned(r, 2)?,
                    text: r.get(3)?,
                    kind: r.get(4)?,
                })
            })
            .map_err(error)?;
        rows.map(|r| r.map_err(error)).collect()
    }
    pub fn delete_annotation(&mut self, id: &str) -> Result<()> {
        self.connection
            .execute("DELETE FROM annotations WHERE id=?1", [id])
            .map_err(error)?;
        Ok(())
    }
    pub fn annotations(&self, id: &str) -> Result<Vec<Annotation>> {
        let mut q = self.connection.prepare("SELECT id,book_id,position,text,kind FROM annotations WHERE book_id=?1 ORDER BY position,rowid").map_err(error)?;
        let rows = q
            .query_map([id], |r| {
                Ok(Annotation {
                    id: r.get(0)?,
                    book_id: r.get(1)?,
                    position: read_unsigned(r, 2)?,
                    text: r.get(3)?,
                    kind: r.get(4)?,
                })
            })
            .map_err(error)?;
        rows.map(|r| r.map_err(error)).collect()
    }
    pub fn annotate(&mut self, a: Annotation) -> Result<()> {
        let count: usize = self
            .connection
            .query_row(
                "SELECT json_array_length(chunks) FROM books WHERE id=?1",
                [&a.book_id],
                |r| read_unsigned(r, 0),
            )
            .map_err(error)?;
        if a.position >= count
            || a.text.len() > 50_000
            || a.text.trim().is_empty()
            || !["note", "bookmark"].contains(&a.kind.as_str())
            || uuid::Uuid::parse_str(&a.id).is_err()
        {
            return Err("Invalid note or bookmark.".into());
        }
        self.connection.execute("INSERT INTO annotations(id,book_id,position,text,kind) VALUES (?1,?2,?3,?4,?5) ON CONFLICT(id) DO UPDATE SET text=excluded.text WHERE annotations.book_id=excluded.book_id", params![a.id,a.book_id,i64::try_from(a.position).map_err(error)?,a.text,a.kind]).map_err(error)?;
        Ok(())
    }
    pub fn settings(&self) -> Result<Settings> {
        use rusqlite::OptionalExtension;
        let value: Option<String> = self
            .connection
            .query_row("SELECT value FROM settings WHERE id=1", [], |r| r.get(0))
            .optional()
            .map_err(error)?;
        value
            .map(|v| serde_json::from_str(&v).map_err(error))
            .unwrap_or_else(|| Ok(Settings::default()))
    }
    pub fn save_settings(&mut self, settings: Settings) -> Result<()> {
        if !(14..=32).contains(&settings.font_size)
            || !(0.5..=2.0).contains(&settings.rate)
            || !["light", "dark", "system"].contains(&settings.theme.as_str())
            || ![
                "sage",
                "midnight",
                "dracula",
                "github",
                "bw_gradient",
                "neon",
                "amoled",
                "dark_high_contrast",
                "white_high_contrast",
                "solarized_dark",
                "tomorrow_night_blue",
            ]
            .contains(&settings.color_theme.as_str())
            || !(settings.accent_color.is_empty()
                || (settings.accent_color.len() == 7
                    && settings.accent_color.starts_with('#')
                    && settings.accent_color.as_bytes()[1..]
                        .iter()
                        .all(u8::is_ascii_hexdigit)))
            || !["Bitter", "Atkinson", "Georgia"].contains(&settings.reader_font.as_str())
            || !["piper_en_us_lessac", "kokoro_af_heart"].contains(&settings.voice.as_str())
            || !["single", "double", "scroll"].contains(&settings.reader_layout.as_str())
            || !["flow", "original"].contains(&settings.reader_view.as_str())
        {
            return Err("Unsupported reader settings.".into());
        }
        if settings.queue.len() > 1000
            || settings
                .queue
                .iter()
                .collect::<std::collections::HashSet<_>>()
                .len()
                != settings.queue.len()
        {
            return Err("The listening queue must have at most 1,000 unique books.".into());
        }
        for id in &settings.queue {
            let exists: bool = self
                .connection
                .query_row(
                    "SELECT EXISTS(SELECT 1 FROM books WHERE id=?1)",
                    [id],
                    |row| row.get(0),
                )
                .map_err(error)?;
            if !exists {
                return Err("A queued book no longer belongs to the library.".into());
            }
        }
        if settings.pronunciation_rules.len() > 100 || settings.vocabulary.len() > 1000 {
            return Err("Keep up to 100 pronunciation rules and 1,000 vocabulary entries.".into());
        }
        let mut ids = std::collections::HashSet::new();
        let mut phrases = std::collections::HashSet::new();
        for rule in &settings.pronunciation_rules {
            if uuid::Uuid::parse_str(&rule.id).is_err()
                || !ids.insert(&rule.id)
                || rule.find.trim().is_empty()
                || rule.find.chars().count() > 100
                || rule.replace_with.chars().count() > 200
                || !phrases.insert(rule.find.trim().to_lowercase())
            {
                return Err("Use unique pronunciation phrases (up to 100 characters) and replacements (up to 200 characters).".into());
            }
        }
        ids.clear();
        for entry in &settings.vocabulary {
            if uuid::Uuid::parse_str(&entry.id).is_err()
                || !ids.insert(&entry.id)
                || entry.word.trim().is_empty()
                || entry.word.chars().count() > 100
                || entry.definition.trim().is_empty()
                || entry.definition.chars().count() > 2000
            {
                return Err("A vocabulary entry needs a word (up to 100 characters) and definition (up to 2,000 characters).".into());
            }
            match (&entry.book_id, entry.position) {
                (None, None) => {}
                (Some(id), Some(position)) => {
                    let count: i64 = self
                        .connection
                        .query_row(
                            "SELECT json_array_length(chunks) FROM books WHERE id=?1",
                            [id],
                            |row| row.get(0),
                        )
                        .map_err(error)?;
                    if position >= usize::try_from(count).map_err(error)? {
                        return Err("A vocabulary source passage is outside its book.".into());
                    }
                }
                _ => {
                    return Err(
                        "A vocabulary entry needs both a source book and passage, or neither."
                            .into(),
                    )
                }
            }
        }
        self.connection.execute("INSERT INTO settings(id,value) VALUES(1,?1) ON CONFLICT(id) DO UPDATE SET value=excluded.value", [serde_json::to_string(&settings).map_err(error)?]).map_err(error)?;
        Ok(())
    }
}
pub(crate) fn now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn theme_settings_validate_persist_and_read_older_defaults() {
        let path = std::env::temp_dir().join(format!("vern-theme-{}.sqlite", uuid::Uuid::new_v4()));
        let mut db = Storage::open(&path).unwrap();
        let mut settings = Settings {
            theme: "system".into(),
            color_theme: "dracula".into(),
            accent_color: "#38A169".into(),
            reduce_motion: true,
            ..Settings::default()
        };
        db.save_settings(settings.clone()).unwrap();
        settings.accent_color = "red;display:none".into();
        assert!(db.save_settings(settings.clone()).is_err());
        settings.accent_color.clear();
        settings.color_theme = "unrecognised".into();
        assert!(db.save_settings(settings).is_err());
        drop(db);
        let db = Storage::open(&path).unwrap();
        let saved = db.settings().unwrap();
        assert_eq!(saved.color_theme, "dracula");
        assert_eq!(saved.accent_color, "#38A169");
        assert!(saved.reduce_motion);
        let old = r#"{"theme":"dark","fontSize":21,"readerFont":"Bitter","voice":"piper_en_us_lessac","rate":1,"lastBook":""}"#;
        let restored: Settings = serde_json::from_str(old).unwrap();
        assert_eq!(restored.color_theme, "sage");
        assert!(restored.accent_color.is_empty());
        assert!(!restored.reduce_motion);
        drop(db);
        std::fs::remove_file(path).unwrap();
    }
    #[test]
    fn sha256_uses_stable_lowercase_hex() {
        assert_eq!(
            hex_digest(Sha256::digest(b"abc")),
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        );
    }
    #[test]
    fn persists_and_deduplicates_without_resetting_progress() {
        let path = std::env::temp_dir().join(format!("vern-test-{}.sqlite", uuid::Uuid::new_v4()));
        let mut db = Storage::open(&path).unwrap();
        let input = Book {
            id: String::new(),
            title: "Alice".into(),
            author: "Carroll".into(),
            format: "TXT".into(),
            chunks: vec!["First.".into(), "Second.".into()],
            position: 0,
            favorite: false,
            collection: String::new(),
            text_available: true,
            updated_at: 0,
        };
        let book = db.save_book(input.clone()).unwrap();
        db.progress(&book.id, 1).unwrap();
        assert!(db.progress(&book.id, 2).is_err());
        assert!(db.progress(&book.id, usize::MAX).is_err());
        assert_eq!(db.save_book(input).unwrap().position, 1);
        db.annotate(Annotation {
            id: uuid::Uuid::new_v4().to_string(),
            book_id: book.id,
            position: 1,
            text: "A note".into(),
            kind: "note".into(),
        })
        .unwrap();
        drop(db);
        let db = Storage::open(&path).unwrap();
        let books = db.books().unwrap();
        assert_eq!(books.len(), 1);
        assert_eq!(books[0].position, 1);
        assert_eq!(db.annotations(&books[0].id).unwrap()[0].text, "A note");
        drop(db);
        std::fs::remove_file(path).unwrap();
    }
}
