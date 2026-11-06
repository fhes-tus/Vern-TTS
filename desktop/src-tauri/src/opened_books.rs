//! Recent books are navigation history, never a substitute for measured reading time.
use crate::storage::{error, now, Result, Storage};
use rusqlite::{params, OptionalExtension};
use serde::{Deserialize, Serialize};
use serde_json::Value;
pub const LIMIT: usize = 40;
#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct OpenedBook {
    pub id: String,
    pub book_id: Option<String>,
    pub title: String,
    pub format: String,
    pub opened_at: u64,
    pub position: Option<usize>,
    pub passages: usize,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub android_source: Option<Value>,
}
impl OpenedBook {
    pub(crate) fn validate(&self, db: &Storage) -> Result<()> {
        if self.id.len() != 64
            || !self
                .id
                .bytes()
                .all(|c| c.is_ascii_digit() || (b'a'..=b'f').contains(&c))
            || self.title.trim().is_empty()
            || self.title.chars().count() > 1000
            || self.format.trim().is_empty()
            || self.format.chars().count() > 200
            || self.opened_at > 8_640_000_000_000_000
            || self.passages > 100_000
            || self.position.is_some_and(|p| p >= self.passages)
            || self.android_source.as_ref().is_some_and(|source| {
                !source.is_object() || serde_json::to_vec(source).map_or(true, |b| b.len() > 8192)
            })
        {
            return Err("Invalid recent-book history.".into());
        }
        if let Some(id) = &self.book_id {
            let count: usize = db
                .connection
                .query_row(
                    "SELECT json_array_length(chunks) FROM books WHERE id=?1",
                    [id],
                    |r| crate::storage::read_unsigned(r, 0),
                )
                .map_err(error)?;
            if id != &self.id || self.position.is_some_and(|p| p >= count) {
                return Err("The history entry does not belong to this reading.".into());
            }
        }
        Ok(())
    }
}
impl Storage {
    pub fn opened_books(&self) -> Result<Vec<OpenedBook>> {
        let mut query=self.connection.prepare("SELECT h.value,h.book_id,b.title FROM reading_history h LEFT JOIN books b ON b.id=h.book_id ORDER BY json_extract(h.value,'$.openedAt') DESC,h.id LIMIT 40").map_err(error)?;
        let rows = query
            .query_map([], |row| {
                Ok((
                    row.get::<_, String>(0)?,
                    row.get::<_, Option<String>>(1)?,
                    row.get::<_, Option<String>>(2)?,
                ))
            })
            .map_err(error)?;
        rows.map(|row| {
            let (value, book_id, title) = row.map_err(error)?;
            let mut item: OpenedBook = serde_json::from_str(&value).map_err(error)?;
            item.book_id = book_id;
            if let Some(title) = title {
                item.title = title;
            }
            Ok(item)
        })
        .collect()
    }
    fn write_opened_book(&self, item: &OpenedBook, keep_current: bool) -> Result<()> {
        item.validate(self)?;
        self.connection
            .execute_batch("SAVEPOINT opened_book_update")
            .map_err(error)?;
        let result = (|| {
            self.connection.execute("INSERT INTO reading_history(id,book_id,value) VALUES(?1,?2,?3) ON CONFLICT(id) DO UPDATE SET book_id=excluded.book_id,value=excluded.value",params![item.id,item.book_id,serde_json::to_string(item).map_err(error)?]).map_err(error)?;
            if keep_current {
                self.connection.execute("DELETE FROM reading_history WHERE id<>?1 AND id NOT IN (SELECT id FROM reading_history WHERE id<>?1 ORDER BY json_extract(value,'$.openedAt') DESC,id LIMIT 39)",[&item.id]).map_err(error)?;
            } else {
                self.connection.execute("DELETE FROM reading_history WHERE id NOT IN (SELECT id FROM reading_history ORDER BY json_extract(value,'$.openedAt') DESC,id LIMIT 40)",[]).map_err(error)?;
            }
            Ok(())
        })();
        match result {
            Ok(()) => self
                .connection
                .execute_batch("RELEASE opened_book_update")
                .map_err(error),
            Err(e) => {
                self.connection
                    .execute_batch("ROLLBACK TO opened_book_update; RELEASE opened_book_update")
                    .map_err(error)?;
                Err(e)
            }
        }
    }
    pub fn record_reading_open(&mut self, id: &str) -> Result<()> {
        if id.len() != 64 || !id.bytes().all(|c| c.is_ascii_hexdigit()) {
            return Err("Choose a library book.".into());
        }
        let (title,format,position,passages,text):(String,String,usize,usize,bool)=self.connection.query_row("SELECT title,format,position,json_array_length(chunks),text_available FROM books WHERE id=?1",[id],|r|Ok((r.get(0)?,r.get(1)?,crate::storage::read_unsigned(r,2)?,crate::storage::read_unsigned(r,3)?,r.get(4)?))).map_err(error)?;
        self.write_opened_book(
            &OpenedBook {
                id: id.into(),
                book_id: Some(id.into()),
                title,
                format,
                opened_at: now(),
                position: text.then_some(position),
                passages: if text { passages } else { 0 },
                android_source: None,
            },
            true,
        )
    }
    pub(crate) fn restore_opened_book(&self, item: &OpenedBook) -> Result<()> {
        item.validate(self)?;
        let previous: Option<(String, Option<String>)> = self
            .connection
            .query_row(
                "SELECT value,book_id FROM reading_history WHERE id=?1",
                [&item.id],
                |row| Ok((row.get(0)?, row.get(1)?)),
            )
            .optional()
            .map_err(error)?;
        if let Some((value, book_id)) = previous {
            let mut old: OpenedBook = serde_json::from_str(&value).map_err(error)?;
            old.book_id = book_id;
            if old.opened_at >= item.opened_at {
                if old.book_id.is_none() && item.book_id.is_some() {
                    old.book_id.clone_from(&item.book_id);
                    return self.write_opened_book(&old, false);
                }
                return Ok(());
            }
        }
        self.write_opened_book(item, false)
    }
    pub fn remove_opened_book(&self, id: &str) -> Result<()> {
        if id.len() != 64 || !id.bytes().all(|c| c.is_ascii_hexdigit()) {
            return Err("Choose a recent-book entry.".into());
        }
        self.connection
            .execute("DELETE FROM reading_history WHERE id=?1", [id])
            .map_err(error)?;
        Ok(())
    }
    pub fn clear_opened_books(&self) -> Result<()> {
        self.connection
            .execute("DELETE FROM reading_history", [])
            .map_err(error)?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{backup_archive::StagedFile, storage::Book};
    use std::{io::Cursor, path::Path};
    fn book(db: &mut Storage, n: usize) -> Book {
        db.save_book(Book {
            id: String::new(),
            title: format!("Reading {n}"),
            author: String::new(),
            format: "TXT".into(),
            chunks: vec![format!("Opening passage {n}."), "A final passage.".into()],
            position: 0,
            favorite: false,
            collection: String::new(),
            text_available: true,
            updated_at: 0,
            cover: None,
        })
        .unwrap()
    }
    #[test]
    fn opens_deduplicate_preserve_progress_detach_on_delete_and_round_trip() {
        let lease = StagedFile::stage(Cursor::new([]), 0).unwrap();
        let mut db = Storage::open(lease.path()).unwrap();
        let reading = book(&mut db, 1);
        db.progress(&reading.id, 1).unwrap();
        db.record_reading_open(&reading.id).unwrap();
        let mut older = db.opened_books().unwrap().remove(0);
        older.opened_at = 0;
        older.position = Some(0);
        db.restore_opened_book(&older).unwrap();
        assert_eq!(db.opened_books().unwrap()[0].position, Some(1));
        assert_eq!(db.book(&reading.id).unwrap().position, 1);
        db.record_reading_open(&reading.id).unwrap();
        assert_eq!(db.opened_books().unwrap().len(), 1);
        drop(db);
        let mut db = Storage::open(lease.path()).unwrap();
        assert_eq!(db.opened_books().unwrap().len(), 1);
        db.delete_book(&reading.id).unwrap();
        let gone = db.opened_books().unwrap().remove(0);
        assert_eq!(gone.book_id, None);
        assert_eq!(gone.title, reading.title);
        let backup = StagedFile::stage(
            Cursor::new(db.backup_bytes().unwrap()),
            crate::transfer::LIMIT as u64,
        )
        .unwrap();
        let mut restored = Storage::open(Path::new(":memory:")).unwrap();
        restored
            .restore_backup(crate::transfer::read_backup(backup.path()).unwrap(), false)
            .unwrap();
        assert_eq!(restored.opened_books().unwrap()[0].book_id, None);
        let restored_book = book(&mut restored, 1);
        restored
            .restore_opened_book(&OpenedBook {
                book_id: Some(restored_book.id.clone()),
                ..gone
            })
            .unwrap();
        assert_eq!(
            restored.opened_books().unwrap()[0].book_id,
            Some(restored_book.id.clone())
        );
        restored.remove_opened_book(&restored_book.id).unwrap();
        assert!(restored.opened_books().unwrap().is_empty());
        assert_eq!(restored.books().unwrap().len(), 1);
    }
    #[test]
    fn history_is_bounded_clear_preserves_books_and_schema14_upgrades() {
        let lease = StagedFile::stage(Cursor::new([]), 0).unwrap();
        let mut db = Storage::open(lease.path()).unwrap();
        let mut last = String::new();
        for n in 0..41 {
            let reading = book(&mut db, n);
            db.record_reading_open(&reading.id).unwrap();
            last = reading.id;
        }
        let entries = db.opened_books().unwrap();
        assert_eq!(entries.len(), LIMIT);
        assert!(entries.iter().any(|r| r.book_id.as_ref() == Some(&last)));
        db.clear_opened_books().unwrap();
        assert!(db.opened_books().unwrap().is_empty());
        assert_eq!(db.books().unwrap().len(), 41);
        db.connection
            .execute_batch("DROP TABLE reading_history; DROP TABLE legacy_book_times; DROP TABLE legacy_activity_days; PRAGMA user_version=14;")
            .unwrap();
        drop(db);
        let db = Storage::open(lease.path()).unwrap();
        assert_eq!(db.books().unwrap().len(), 41);
        assert!(db.opened_books().unwrap().is_empty());
        assert_eq!(
            db.connection
                .pragma_query_value(None, "user_version", |r| r.get::<_, u32>(0))
                .unwrap(),
            16
        );
    }
}
