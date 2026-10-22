use crate::storage::{error, now, Result, Storage};
use rusqlite::{params, OptionalExtension};
use serde::{Deserialize, Serialize};
use std::collections::HashSet;

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Notebook {
    pub id: String,
    pub name: String,
    pub created_at: u64,
}
#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Note {
    pub id: String,
    pub title: String,
    pub content: String,
    pub color: String,
    pub pinned: bool,
    pub is_checklist: bool,
    pub label_ids: Vec<String>,
    pub created_at: u64,
    pub updated_at: u64,
    pub deleted_at: Option<u64>,
}
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NoteInput {
    pub id: Option<String>,
    pub title: String,
    pub content: String,
    pub color: String,
    pub pinned: bool,
    pub is_checklist: bool,
    pub label_ids: Vec<String>,
}
impl Note {
    fn validate(&self) -> Result<()> {
        uuid::Uuid::parse_str(&self.id).map_err(error)?;
        if (self.title.trim().is_empty() && self.content.trim().is_empty())
            || self.title.chars().count() > 500
            || self.content.chars().count() > 200_000
            || !["paper", "sage", "rose", "blue", "amber"].contains(&self.color.as_str())
            || self.label_ids.len() > 20
            || self.label_ids.iter().collect::<HashSet<_>>().len() != self.label_ids.len()
            || self.created_at > self.updated_at
            || self.updated_at > 8_640_000_000_000_000
            || self.deleted_at.is_some_and(|t| t > 8_640_000_000_000_000)
        {
            return Err("Notes need text, valid labels and a supported colour (title up to 500 characters; body up to 200,000).".into());
        }
        Ok(())
    }
}
impl Storage {
    pub fn notebooks(&self) -> Result<Vec<Notebook>> {
        let mut q = self
            .connection
            .prepare("SELECT id,name,created_at FROM notebooks ORDER BY name COLLATE NOCASE")
            .map_err(error)?;
        let rows = q
            .query_map([], |r| {
                Ok(Notebook {
                    id: r.get(0)?,
                    name: r.get(1)?,
                    created_at: r
                        .get::<_, i64>(2)?
                        .try_into()
                        .map_err(|_| rusqlite::Error::InvalidQuery)?,
                })
            })
            .map_err(error)?;
        rows.map(|r| r.map_err(error)).collect()
    }
    pub fn save_notebook(&mut self, id: Option<String>, name: String) -> Result<()> {
        let id = id.unwrap_or_else(|| uuid::Uuid::new_v4().to_string());
        uuid::Uuid::parse_str(&id).map_err(error)?;
        if name.trim().is_empty() || name.chars().count() > 200 {
            return Err("Notebook names need 1–200 characters.".into());
        }
        self.connection.execute("INSERT INTO notebooks(id,name,created_at) VALUES(?1,?2,?3) ON CONFLICT(id) DO UPDATE SET name=excluded.name",params![id,name.trim(),i64::try_from(now()).map_err(error)?]).map_err(error)?;
        Ok(())
    }
    pub fn restore_notebook(&mut self, item: &Notebook) -> Result<()> {
        if item.created_at > 8_640_000_000_000_000 {
            return Err("Invalid notebook date.".into());
        }
        self.save_notebook(Some(item.id.clone()), item.name.clone())?;
        self.connection
            .execute(
                "UPDATE notebooks SET created_at=?1 WHERE id=?2",
                params![i64::try_from(item.created_at).map_err(error)?, item.id],
            )
            .map_err(error)?;
        Ok(())
    }
    pub fn notes(&self) -> Result<Vec<Note>> {
        let mut q = self
            .connection
            .prepare("SELECT value FROM personal_notes ORDER BY rowid DESC")
            .map_err(error)?;
        let rows = q.query_map([], |r| r.get::<_, String>(0)).map_err(error)?;
        rows.map(|r| serde_json::from_str(&r.map_err(error)?).map_err(error))
            .collect()
    }
    fn personal_note(&self, id: &str) -> Result<Note> {
        let value: String = self
            .connection
            .query_row("SELECT value FROM personal_notes WHERE id=?1", [id], |r| {
                r.get(0)
            })
            .map_err(error)?;
        serde_json::from_str(&value).map_err(error)
    }
    fn write_note(&mut self, note: &Note) -> Result<()> {
        note.validate()?;
        for id in &note.label_ids {
            if self
                .connection
                .query_row("SELECT 1 FROM notebooks WHERE id=?1", [id], |_| Ok(()))
                .optional()
                .map_err(error)?
                .is_none()
            {
                return Err("One of these notebooks no longer exists.".into());
            }
        }
        self.connection.execute("INSERT INTO personal_notes(id,value) VALUES(?1,?2) ON CONFLICT(id) DO UPDATE SET value=excluded.value",params![note.id,serde_json::to_string(note).map_err(error)?]).map_err(error)?;
        Ok(())
    }
    pub fn restore_note(&mut self, note: &Note) -> Result<()> {
        self.write_note(note)
    }
    pub fn save_note(&mut self, input: NoteInput) -> Result<Note> {
        let old = if let Some(id) = &input.id {
            Some(self.personal_note(id)?)
        } else {
            None
        };
        if old.as_ref().is_some_and(|n| n.deleted_at.is_some()) {
            return Err("Restore this note from Trash before editing it.".into());
        }
        let note = Note {
            id: input.id.unwrap_or_else(|| uuid::Uuid::new_v4().to_string()),
            title: input.title.trim().into(),
            content: input.content,
            color: input.color,
            pinned: input.pinned,
            is_checklist: input.is_checklist,
            label_ids: input.label_ids,
            created_at: old.as_ref().map_or_else(now, |n| n.created_at),
            updated_at: now(),
            deleted_at: None,
        };
        self.write_note(&note)?;
        Ok(note)
    }
    pub fn trash_note(&mut self, id: &str, trashed: bool) -> Result<()> {
        let mut note = self.personal_note(id)?;
        note.deleted_at = if trashed { Some(now()) } else { None };
        note.updated_at = now();
        self.write_note(&note)
    }
    pub fn delete_note(&mut self, id: &str) -> Result<()> {
        if self.personal_note(id)?.deleted_at.is_none() {
            return Err("Move a note to Trash before deleting it permanently.".into());
        }
        self.connection
            .execute("DELETE FROM personal_notes WHERE id=?1", [id])
            .map_err(error)?;
        Ok(())
    }
    pub fn delete_notebook(&mut self, id: &str) -> Result<()> {
        self.connection
            .execute_batch("SAVEPOINT remove_notebook")
            .map_err(error)?;
        let result = (|| {
            for mut note in self.notes()? {
                if note.label_ids.iter().any(|label| label == id) {
                    note.label_ids.retain(|label| label != id);
                    self.write_note(&note)?;
                }
            }
            self.connection
                .execute("DELETE FROM notebooks WHERE id=?1", [id])
                .map_err(error)?;
            Ok(())
        })();
        match result {
            Ok(()) => self
                .connection
                .execute_batch("RELEASE remove_notebook")
                .map_err(error),
            Err(e) => {
                self.connection
                    .execute_batch("ROLLBACK TO remove_notebook; RELEASE remove_notebook")
                    .map_err(error)?;
                Err(e)
            }
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn notebooks_checklists_trash_and_backup_survive_reopen() {
        let root = std::env::temp_dir().join(format!("vern-notes-{}", uuid::Uuid::new_v4()));
        std::fs::create_dir(&root).unwrap();
        let path = root.join("library.db");
        let mut db = Storage::open(&path).unwrap();
        db.save_notebook(None, "Reading".into()).unwrap();
        let label = db.notebooks().unwrap()[0].clone();
        let input = || NoteInput {
            id: None,
            title: "Remember".into(),
            content: "- [ ] Read\n- [x] Think".into(),
            color: "sage".into(),
            pinned: true,
            is_checklist: true,
            label_ids: vec![label.id.clone()],
        };
        let note = db.save_note(input()).unwrap();
        assert!(db.delete_note(&note.id).is_err());
        let mut bad = input();
        bad.label_ids.push("missing".into());
        assert!(db.save_note(bad).is_err());
        db.trash_note(&note.id, true).unwrap();
        drop(db);
        let mut db = Storage::open(&path).unwrap();
        assert!(db.notes().unwrap()[0].deleted_at.is_some());
        db.trash_note(&note.id, false).unwrap();
        assert!(db.notes().unwrap()[0].pinned);
        let backup_path = root.join("backup.json");
        std::fs::write(&backup_path, db.backup_bytes().unwrap()).unwrap();
        let backup = crate::transfer::read_backup(&backup_path).unwrap();
        let mut restored = Storage::open(std::path::Path::new(":memory:")).unwrap();
        restored.restore_backup(backup, false).unwrap();
        assert_eq!(
            restored.notes().unwrap()[0].label_ids,
            vec![label.id.clone()]
        );
        db.delete_notebook(&label.id).unwrap();
        assert!(db.notes().unwrap()[0].label_ids.is_empty());
        db.trash_note(&note.id, true).unwrap();
        db.delete_note(&note.id).unwrap();
        assert!(db.notes().unwrap().is_empty());
        drop(db);
        std::fs::remove_dir_all(root).unwrap();
    }
}
