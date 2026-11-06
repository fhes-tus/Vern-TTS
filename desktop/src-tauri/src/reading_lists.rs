use crate::storage::{error, now, Result, Storage};
use rusqlite::{params, OptionalExtension};
use serde::{Deserialize, Serialize};
use std::collections::HashSet;

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ListItem {
    pub book_id: String,
    pub added_at: u64,
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::storage::Book;
    use std::path::Path;
    fn book(db: &mut Storage, text: &str) -> Book {
        db.save_book(Book {
            id: String::new(),
            title: text.into(),
            author: String::new(),
            format: "TXT".into(),
            chunks: vec![text.into()],
            position: 0,
            favorite: false,
            collection: String::new(),
            text_available: true,
            cover: None,
            updated_at: 0,
        })
        .unwrap()
    }
    fn draft(ids: Vec<String>) -> ListInput {
        ListInput {
            id: None,
            title: "Next reads".into(),
            description: "A deliberate order".into(),
            archived: false,
            sort_mode: "manual".into(),
            book_ids: ids,
        }
    }
    #[test]
    fn order_archive_backup_and_book_deletion_preserve_the_library() {
        let mut db = Storage::open(Path::new(":memory:")).unwrap();
        let a = book(&mut db, "Alpha");
        let b = book(&mut db, "Beta");
        let saved = db
            .save_reading_list(draft(vec![b.id.clone(), a.id.clone()]))
            .unwrap();
        let catalogue = crate::classics::catalogue().unwrap();
        db.restore_classic_link(&crate::classics::ClassicLink {
            catalog_id: catalogue[0].id.clone(),
            book_id: a.id.clone(),
        })
        .unwrap();
        let mut changed = draft(vec![a.id.clone(), b.id.clone()]);
        changed.id = Some(saved.id.clone());
        changed.archived = true;
        let changed = db.save_reading_list(changed).unwrap();
        assert!(changed.archived);
        assert_eq!(changed.items[0].book_id, a.id);
        assert_eq!(changed.items[1].added_at, saved.items[0].added_at);
        let backup = serde_json::from_slice(&db.backup_bytes().unwrap()).unwrap();
        let mut restored = Storage::open(Path::new(":memory:")).unwrap();
        restored.restore_backup(backup, false).unwrap();
        assert_eq!(
            restored.reading_list(&saved.id).unwrap().items[1].book_id,
            b.id
        );
        assert_eq!(
            restored.classic_links().unwrap()[0].catalog_id,
            catalogue[0].id
        );
        restored.delete_book(&a.id).unwrap();
        assert_eq!(restored.reading_list(&saved.id).unwrap().items.len(), 1);
        assert!(restored.classic_links().unwrap().is_empty());
        restored.delete_reading_list(&saved.id).unwrap();
        assert_eq!(restored.books().unwrap().len(), 1);
        assert_eq!(restored.books().unwrap()[0].id, b.id);
    }
    #[test]
    fn invalid_lists_are_rejected_without_partial_changes() {
        let mut db = Storage::open(Path::new(":memory:")).unwrap();
        let a = book(&mut db, "Alpha");
        assert!(db
            .save_reading_list(draft(vec![a.id.clone(), a.id.clone()]))
            .is_err());
        assert!(db.save_reading_list(draft(vec!["missing".into()])).is_err());
        let mut input = draft(vec![a.id]);
        input.sort_mode = "untrusted".into();
        assert!(db.save_reading_list(input).is_err());
        assert!(db.reading_lists().unwrap().is_empty());
    }
}
#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ReadingList {
    pub id: String,
    pub title: String,
    pub description: String,
    pub created_at: u64,
    pub updated_at: u64,
    pub archived: bool,
    pub sort_mode: String,
    pub items: Vec<ListItem>,
}
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ListInput {
    pub id: Option<String>,
    pub title: String,
    pub description: String,
    pub archived: bool,
    pub sort_mode: String,
    pub book_ids: Vec<String>,
}
impl Storage {
    pub fn reading_lists(&self) -> Result<Vec<ReadingList>> {
        let mut query = self
            .connection
            .prepare("SELECT value FROM reading_lists ORDER BY rowid DESC")
            .map_err(error)?;
        let rows = query
            .query_map([], |r| r.get::<_, String>(0))
            .map_err(error)?;
        rows.map(|r| serde_json::from_str(&r.map_err(error)?).map_err(error))
            .collect()
    }
    pub fn reading_list(&self, id: &str) -> Result<ReadingList> {
        let value: String = self
            .connection
            .query_row("SELECT value FROM reading_lists WHERE id=?1", [id], |r| {
                r.get(0)
            })
            .map_err(error)?;
        serde_json::from_str(&value).map_err(error)
    }
    fn write_reading_list(&self, list: &ReadingList) -> Result<()> {
        uuid::Uuid::parse_str(&list.id).map_err(error)?;
        if list.title.trim().is_empty()
            || list.title.chars().count() > 500
            || list.description.chars().count() > 10000
            || list.items.len() > 10000
            || list.created_at > list.updated_at
            || list.updated_at > 8_640_000_000_000_000
            || !["manual", "title", "title-desc", "newest", "oldest"]
                .contains(&list.sort_mode.as_str())
            || list
                .items
                .iter()
                .map(|i| &i.book_id)
                .collect::<HashSet<_>>()
                .len()
                != list.items.len()
        {
            return Err("Reading lists need a title, a supported order and unique books.".into());
        }
        for item in &list.items {
            if item.added_at > 8_640_000_000_000_000
                || self
                    .connection
                    .query_row("SELECT 1 FROM books WHERE id=?1", [&item.book_id], |_| {
                        Ok(())
                    })
                    .optional()
                    .map_err(error)?
                    .is_none()
            {
                return Err(
                    "A reading-list item is missing from the library or has an invalid date."
                        .into(),
                );
            }
        }
        self.connection.execute("INSERT INTO reading_lists(id,value) VALUES(?1,?2) ON CONFLICT(id) DO UPDATE SET value=excluded.value",params![list.id,serde_json::to_string(list).map_err(error)?]).map_err(error)?;
        Ok(())
    }
    pub fn save_reading_list(&mut self, input: ListInput) -> Result<ReadingList> {
        let previous = input
            .id
            .as_deref()
            .map(|id| self.reading_list(id))
            .transpose()?;
        let stamp = now();
        let list = ReadingList {
            id: input.id.unwrap_or_else(|| uuid::Uuid::new_v4().to_string()),
            title: input.title.trim().into(),
            description: input.description.trim().into(),
            created_at: previous.as_ref().map_or(stamp, |l| l.created_at),
            updated_at: stamp,
            archived: input.archived,
            sort_mode: input.sort_mode,
            items: input
                .book_ids
                .into_iter()
                .map(|book_id| {
                    let added_at = previous
                        .as_ref()
                        .and_then(|l| l.items.iter().find(|i| i.book_id == book_id))
                        .map_or(stamp, |i| i.added_at);
                    ListItem { book_id, added_at }
                })
                .collect(),
        };
        self.write_reading_list(&list)?;
        Ok(list)
    }
    pub fn restore_reading_list(&mut self, list: &ReadingList) -> Result<()> {
        self.write_reading_list(list)
    }
    pub fn delete_reading_list(&mut self, id: &str) -> Result<()> {
        self.connection
            .execute("DELETE FROM reading_lists WHERE id=?1", [id])
            .map_err(error)?;
        Ok(())
    }
    pub(crate) fn lists_without_book(&self, id: &str) -> Result<Vec<ReadingList>> {
        let mut lists = self.reading_lists()?;
        lists.retain(|l| l.items.iter().any(|i| i.book_id == id));
        for list in &mut lists {
            list.items.retain(|i| i.book_id != id);
            list.updated_at = now();
        }
        Ok(lists)
    }
}
