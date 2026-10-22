use crate::documents::SourceFile;
use crate::storage::{error, Book, Result, Storage};
use rusqlite::params;
use serde::{Deserialize, Serialize};
use std::{
    collections::HashMap,
    io::Read,
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc, Mutex,
    },
    time::Duration,
};

#[derive(Clone, Deserialize, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Classic {
    pub id: String,
    pub title: String,
    pub author: String,
    pub category: String,
    pub description: String,
    pub url: String,
    pub minutes: u32,
    #[serde(default)]
    pub book_id: Option<String>,
}
pub fn catalogue() -> Result<Vec<Classic>> {
    serde_json::from_str(include_str!("../../src/data/classics.json")).map_err(error)
}
pub fn entry(id: &str) -> Result<Classic> {
    catalogue()?
        .into_iter()
        .find(|book| book.id == id)
        .ok_or("This classic is not in the catalogue.".into())
}
#[derive(Default)]
pub struct Downloads {
    active: Mutex<HashMap<String, Arc<AtomicBool>>>,
}
impl Downloads {
    pub fn cancel(&self, id: &str) -> Result<()> {
        if let Some(cancelled) = self.active.lock().map_err(error)?.get(id) {
            cancelled.store(true, Ordering::SeqCst);
        }
        Ok(())
    }
    pub fn fetch(&self, id: &str) -> Result<SourceFile> {
        let book = entry(id)?;
        let cancelled = Arc::new(AtomicBool::new(false));
        {
            let mut active = self.active.lock().map_err(error)?;
            if active.len() >= 2 || active.contains_key(id) {
                return Err(
                    "A classic download is already running. Wait or cancel it first.".into(),
                );
            }
            active.insert(id.to_string(), cancelled.clone());
        }
        let result = (|| {
            let _ = rustls::crypto::ring::default_provider().install_default();
            let client = reqwest::blocking::Client::builder()
                .connect_timeout(Duration::from_secs(15))
                .timeout(Duration::from_secs(60))
                .user_agent("Vern-Desktop/0.1 (user-initiated book download)")
                .redirect(reqwest::redirect::Policy::custom(|attempt| {
                    if attempt.previous().len() >= 5 {
                        attempt.error("Too many redirects")
                    } else if attempt.url().scheme() == "https"
                        && matches!(
                            attempt.url().host_str(),
                            Some("www.gutenberg.org" | "gutenberg.org")
                        )
                    {
                        attempt.follow()
                    } else {
                        attempt.error("The book server redirected outside Project Gutenberg")
                    }
                }))
                .build()
                .map_err(error)?;
            let mut response = client
                .get(&book.url)
                .send()
                .and_then(|r| r.error_for_status())
                .map_err(error)?;
            const LIMIT: usize = 16 * 1024 * 1024;
            if response
                .content_length()
                .is_some_and(|length| length > LIMIT as u64)
            {
                return Err("This classic exceeds the 16 MB limit.".into());
            }
            let mut bytes = Vec::new();
            let mut buffer = [0u8; 16384];
            loop {
                if cancelled.load(Ordering::SeqCst) {
                    return Err("Classic download cancelled.".into());
                }
                let length = response.read(&mut buffer).map_err(error)?;
                if length == 0 {
                    break;
                }
                if bytes.len() + length > LIMIT {
                    return Err("This classic exceeds the 16 MB limit.".into());
                }
                bytes.extend_from_slice(&buffer[..length]);
            }
            let text = std::str::from_utf8(&bytes)
                .map_err(|_| "The book server returned unsupported text encoding.")?;
            if text.trim().is_empty()
                || text
                    .trim_start_matches('\u{feff}')
                    .trim_start()
                    .starts_with('<')
                || text.contains('\0')
            {
                return Err("The book server returned an empty or invalid text file.".into());
            }
            Ok(SourceFile {
                name: format!("{id}.txt"),
                bytes,
            })
        })();
        self.active.lock().map_err(error)?.remove(id);
        result
    }
}
impl Storage {
    pub fn classics(&self) -> Result<Vec<Classic>> {
        let mut entries = catalogue()?;
        let mut statement = self
            .connection
            .prepare("SELECT catalog_id,book_id FROM classic_books")
            .map_err(error)?;
        let saved: HashMap<String, String> = statement
            .query_map([], |row| Ok((row.get(0)?, row.get(1)?)))
            .map_err(error)?
            .collect::<std::result::Result<_, _>>()
            .map_err(error)?;
        for book in &mut entries {
            book.book_id = saved.get(&book.id).cloned();
        }
        Ok(entries)
    }
    pub fn save_classic(
        &mut self,
        id: &str,
        mut book: Book,
        name: String,
        bytes: Vec<u8>,
    ) -> Result<Book> {
        let classic = entry(id)?;
        if name != format!("{id}.txt") || bytes.len() > 16 * 1024 * 1024 {
            return Err("Invalid classic source file.".into());
        }
        book.title = classic.title;
        book.author = classic.author;
        book.format = "TXT".into();
        book.text_available = true;
        self.connection
            .execute_batch("SAVEPOINT classic_install")
            .map_err(error)?;
        let result = (|| {
            let saved = self.import_source(book, name, bytes, vec![0], true)?;
            self.connection.execute("INSERT INTO classic_books(catalog_id,book_id) VALUES(?1,?2) ON CONFLICT(catalog_id) DO UPDATE SET book_id=excluded.book_id", params![id, saved.id]).map_err(error)?;
            Ok(saved)
        })();
        if result.is_ok() {
            self.connection
                .execute_batch("RELEASE classic_install")
                .map_err(error)?;
        } else {
            self.connection
                .execute_batch("ROLLBACK TO classic_install; RELEASE classic_install")
                .map_err(error)?;
        }
        result
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn catalogue_has_unique_fixed_sources_and_rejects_unknown_ids() {
        let entries = catalogue().unwrap();
        assert_eq!(entries.len(), 36);
        let ids: std::collections::HashSet<_> = entries.iter().map(|b| &b.id).collect();
        assert_eq!(ids.len(), entries.len());
        for book in entries {
            assert!(book
                .url
                .starts_with("https://www.gutenberg.org/cache/epub/"));
            assert!(book.url.ends_with(".txt"));
        }
        assert!(entry("https://example.com/arbitrary.txt").is_err());
    }
    #[test]
    fn installing_classic_is_deduplicated_and_link_is_deleted_with_book() {
        let path =
            std::env::temp_dir().join(format!("vern-classic-{}.sqlite", uuid::Uuid::new_v4()));
        let mut db = Storage::open(&path).unwrap();
        let input = Book {
            id: String::new(),
            title: "User input".into(),
            author: String::new(),
            format: "TXT".into(),
            chunks: vec!["Alice went down the rabbit hole.".into()],
            position: 0,
            favorite: false,
            collection: String::new(),
            text_available: true,
            updated_at: 0,
        };
        let id = "classic_alice_wonderland";
        let file = format!("{id}.txt");
        let bytes = b"Alice went down the rabbit hole.".to_vec();
        let saved = db
            .save_classic(id, input.clone(), file.clone(), bytes.clone())
            .unwrap();
        assert_eq!(saved.title, "Alice in Wonderland");
        assert_eq!(
            db.save_classic(id, input, file, bytes).unwrap().id,
            saved.id
        );
        assert_eq!(db.books().unwrap().len(), 1);
        drop(db);
        let mut db = Storage::open(&path).unwrap();
        assert_eq!(
            db.classics()
                .unwrap()
                .into_iter()
                .find(|b| b.id == id)
                .unwrap()
                .book_id,
            Some(saved.id.clone())
        );
        db.delete_book(&saved.id).unwrap();
        assert!(db
            .classics()
            .unwrap()
            .into_iter()
            .find(|b| b.id == id)
            .unwrap()
            .book_id
            .is_none());
        drop(db);
        std::fs::remove_file(path).unwrap();
    }
}
