use crate::storage::{error, now, read_unsigned, Result, Storage};
use rusqlite::{params, OptionalExtension};
use serde::{Deserialize, Serialize};

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct ReadingHighlight {
    pub id: String,
    pub book_id: String,
    pub position: usize,
    pub end_position: usize,
    pub color: String,
    pub note: String,
    pub created_at: u64,
    pub updated_at: u64,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HighlightInput {
    pub id: Option<String>,
    pub book_id: String,
    pub position: usize,
    pub end_position: Option<usize>,
    pub color: String,
    #[serde(default)]
    pub note: String,
}

fn supported_color(color: &str) -> bool {
    ["yellow", "sage", "blue", "rose"].contains(&color)
        || color.strip_prefix('#').is_some_and(|value| {
            value.len() == 6 && value.bytes().all(|byte| byte.is_ascii_hexdigit())
        })
}

impl Storage {
    pub fn all_highlights(&self) -> Result<Vec<ReadingHighlight>> {
        let mut query = self
            .connection
            .prepare("SELECT value FROM reader_highlights ORDER BY rowid")
            .map_err(error)?;
        let rows = query
            .query_map([], |row| row.get::<_, String>(0))
            .map_err(error)?;
        rows.map(|row| serde_json::from_str(&row.map_err(error)?).map_err(error))
            .collect()
    }

    pub fn book_highlights(&self, book_id: &str) -> Result<Vec<ReadingHighlight>> {
        let mut query = self
            .connection
            .prepare("SELECT value FROM reader_highlights WHERE book_id=?1 ORDER BY json_extract(value,'$.position'),rowid")
            .map_err(error)?;
        let rows = query
            .query_map([book_id], |row| row.get::<_, String>(0))
            .map_err(error)?;
        rows.map(|row| serde_json::from_str(&row.map_err(error)?).map_err(error))
            .collect()
    }

    pub fn highlight(&self, id: &str) -> Result<ReadingHighlight> {
        let value: String = self
            .connection
            .query_row(
                "SELECT value FROM reader_highlights WHERE id=?1",
                [id],
                |row| row.get(0),
            )
            .map_err(error)?;
        serde_json::from_str(&value).map_err(error)
    }

    fn write_highlight(&self, item: &ReadingHighlight) -> Result<()> {
        uuid::Uuid::parse_str(&item.id).map_err(error)?;
        let (count, text_available): (usize, bool) = self
            .connection
            .query_row(
                "SELECT json_array_length(chunks),text_available FROM books WHERE id=?1",
                [&item.book_id],
                |row| Ok((read_unsigned(row, 0)?, row.get(1)?)),
            )
            .map_err(|_| "The highlighted reading is missing from the library.".to_string())?;
        if !text_available
            || item.position > item.end_position
            || item.end_position >= count
            || item.end_position - item.position >= 10_000
            || !supported_color(&item.color)
            || item.note.chars().count() > 10_000
            || item.created_at > item.updated_at
            || item.updated_at > 8_640_000_000_000_000
        {
            return Err("Choose text in this reading, a supported highlight colour and a note of up to 10,000 characters.".into());
        }
        let previous: Option<String> = self
            .connection
            .query_row(
                "SELECT book_id FROM reader_highlights WHERE id=?1",
                [&item.id],
                |row| row.get(0),
            )
            .optional()
            .map_err(error)?;
        if previous.as_ref().is_some_and(|id| id != &item.book_id) {
            return Err("A highlight cannot be moved to another reading.".into());
        }
        if previous.is_none() {
            let total: usize = self
                .connection
                .query_row("SELECT count(*) FROM reader_highlights", [], |row| {
                    read_unsigned(row, 0)
                })
                .map_err(error)?;
            if total >= 20_000 {
                return Err("This library has reached its 20,000-highlight limit.".into());
            }
        }
        self.connection
            .execute(
                "INSERT INTO reader_highlights(id,book_id,value) VALUES(?1,?2,?3) ON CONFLICT(id) DO UPDATE SET value=excluded.value",
                params![item.id, item.book_id, serde_json::to_string(item).map_err(error)?],
            )
            .map_err(error)?;
        Ok(())
    }

    pub fn save_highlight(&mut self, input: HighlightInput) -> Result<ReadingHighlight> {
        let previous = input
            .id
            .as_deref()
            .map(|id| self.highlight(id))
            .transpose()?;
        let created_at = previous.as_ref().map_or_else(now, |item| item.created_at);
        let item = ReadingHighlight {
            id: input.id.unwrap_or_else(|| uuid::Uuid::new_v4().to_string()),
            book_id: input.book_id,
            position: input.position,
            end_position: input.end_position.unwrap_or(input.position),
            color: input.color.to_ascii_lowercase(),
            note: input.note.trim().into(),
            created_at,
            updated_at: now().max(created_at),
        };
        self.write_highlight(&item)?;
        Ok(item)
    }

    pub(crate) fn restore_highlight(&mut self, item: &ReadingHighlight) -> Result<()> {
        self.write_highlight(item)
    }

    pub fn delete_highlight(&mut self, id: &str) -> Result<()> {
        self.connection
            .execute("DELETE FROM reader_highlights WHERE id=?1", [id])
            .map_err(error)?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::storage::Book;

    fn book(db: &mut Storage, text_available: bool) -> Book {
        db.save_book(Book {
            id: String::new(),
            title: "A marked reading".into(),
            author: String::new(),
            format: "TXT".into(),
            chunks: if text_available {
                vec!["A passage with a 🦊.".into(), "Another passage.".into()]
            } else {
                vec!["An image-only placeholder.".into()]
            },
            position: 0,
            favorite: false,
            collection: String::new(),
            text_available,
            cover: None,
            updated_at: 0,
        })
        .unwrap()
    }

    fn draft(book_id: &str) -> HighlightInput {
        HighlightInput {
            id: None,
            book_id: book_id.into(),
            position: 0,
            end_position: Some(1),
            color: "yellow".into(),
            note: "Return to this idea.".into(),
        }
    }

    #[test]
    fn marks_survive_reopen_backup_and_merge_without_resetting_the_reading() {
        let root = std::env::temp_dir().join(format!("vern-highlights-{}", uuid::Uuid::new_v4()));
        std::fs::create_dir(&root).unwrap();
        let path = root.join("library.sqlite3");
        let mut db = Storage::open(&path).unwrap();
        let reading = book(&mut db, true);
        db.progress(&reading.id, 1).unwrap();
        let saved = db.save_highlight(draft(&reading.id)).unwrap();
        drop(db);
        let db = Storage::open(&path).unwrap();
        assert_eq!(
            db.book_highlights(&reading.id).unwrap(),
            vec![saved.clone()]
        );
        assert_eq!(db.book(&reading.id).unwrap().position, 1);
        let backup = serde_json::from_slice(&db.backup_bytes().unwrap()).unwrap();
        let mut restored = Storage::open(std::path::Path::new(":memory:")).unwrap();
        restored.restore_backup(backup, false).unwrap();
        assert_eq!(restored.highlight(&saved.id).unwrap(), saved);
        let mut changed = draft(&reading.id);
        changed.id = Some(saved.id.clone());
        changed.color = "blue".into();
        changed.note = "A newer thought.".into();
        let changed = restored.save_highlight(changed).unwrap();
        let backup = serde_json::from_slice(&db.backup_bytes().unwrap()).unwrap();
        restored.restore_backup(backup, false).unwrap();
        assert_eq!(restored.highlight(&saved.id).unwrap(), changed);
        let mut legacy = serde_json::to_value(
            serde_json::from_slice::<crate::transfer::Backup>(&db.backup_bytes().unwrap()).unwrap(),
        )
        .unwrap();
        legacy["schema"] = "vern.desktop.backup.v2".into();
        legacy.as_object_mut().unwrap().remove("highlights");
        let mut older = Storage::open(std::path::Path::new(":memory:")).unwrap();
        older
            .restore_backup(serde_json::from_value(legacy).unwrap(), false)
            .unwrap();
        assert!(older.all_highlights().unwrap().is_empty());
        restored.delete_highlight(&saved.id).unwrap();
        assert_eq!(restored.book(&reading.id).unwrap().position, 1);
        restored.save_highlight(draft(&reading.id)).unwrap();
        restored.delete_book(&reading.id).unwrap();
        assert!(restored.all_highlights().unwrap().is_empty());
        drop(db);
        let canonical = root.canonicalize().unwrap();
        let temp = std::env::temp_dir().canonicalize().unwrap();
        assert_eq!(canonical.parent(), Some(temp.as_path()));
        assert!(canonical
            .file_name()
            .unwrap()
            .to_string_lossy()
            .starts_with("vern-highlights-"));
        std::fs::remove_dir_all(canonical).unwrap();
    }

    #[test]
    fn invalid_and_cross_reading_marks_never_partially_change_saved_data() {
        let mut db = Storage::open(std::path::Path::new(":memory:")).unwrap();
        let reading = book(&mut db, true);
        let saved = db.save_highlight(draft(&reading.id)).unwrap();
        let mut outside = draft(&reading.id);
        outside.end_position = Some(2);
        assert!(db.save_highlight(outside).is_err());
        let mut reversed = draft(&reading.id);
        reversed.position = 1;
        reversed.end_position = Some(0);
        assert!(db.save_highlight(reversed).is_err());
        let mut invalid = draft(&reading.id);
        invalid.color = "url(untrusted)".into();
        assert!(db.save_highlight(invalid).is_err());
        let mut invalid = draft(&reading.id);
        invalid.color = "#12345G".into();
        assert!(db.save_highlight(invalid).is_err());
        assert!(db.save_highlight(draft("missing")).is_err());
        let mut other = reading.clone();
        other.id.clear();
        other.chunks.push("A different reading.".into());
        let other = db.save_book(other).unwrap();
        let mut moved = draft(&other.id);
        moved.id = Some(saved.id.clone());
        assert!(db.save_highlight(moved).is_err());
        let scanned = book(&mut db, false);
        let mut empty_text = draft(&scanned.id);
        empty_text.end_position = None;
        assert!(db.save_highlight(empty_text).is_err());
        assert_eq!(db.all_highlights().unwrap(), vec![saved]);
    }
}
