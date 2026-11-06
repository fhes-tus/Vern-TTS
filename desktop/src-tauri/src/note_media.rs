use crate::storage::{error, hex_digest, now, Result, Storage};
use base64::Engine;
use rusqlite::{params, OptionalExtension};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
pub const LIMIT: usize = 8 * 1024 * 1024;
#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NoteMedia {
    pub id: String,
    pub note_id: String,
    pub name: String,
    pub mime: String,
    pub size: usize,
    pub sha256: String,
    pub created_at: u64,
}
fn dimensions(width: u32, height: u32) -> Result<()> {
    if width == 0
        || height == 0
        || width > 8192
        || height > 8192
        || u64::from(width) * u64::from(height) > 16_777_216
    {
        return Err("Use an image up to 8,192 pixels per side and 16 megapixels.".into());
    }
    Ok(())
}
pub fn media_type(bytes: &[u8]) -> Result<&'static str> {
    if bytes.is_empty() || bytes.len() > LIMIT {
        return Err("Choose an attachment up to 8 MB.".into());
    }
    if bytes.starts_with(b"\x89PNG\r\n\x1a\n") && bytes.len() >= 24 && &bytes[12..16] == b"IHDR" {
        dimensions(
            u32::from_be_bytes(bytes[16..20].try_into().map_err(error)?),
            u32::from_be_bytes(bytes[20..24].try_into().map_err(error)?),
        )?;
        return Ok("image/png");
    }
    if bytes.starts_with(&[255, 216]) {
        let mut at = 2;
        while at + 9 < bytes.len() {
            if bytes[at] != 255 {
                break;
            }
            let marker = bytes[at + 1];
            if marker == 255 {
                at += 1;
                continue;
            }
            let size = usize::from(u16::from_be_bytes([bytes[at + 2], bytes[at + 3]]));
            if size < 2 || at + 2 + size > bytes.len() {
                break;
            }
            if [
                192, 193, 194, 195, 197, 198, 199, 201, 202, 203, 205, 206, 207,
            ]
            .contains(&marker)
            {
                dimensions(
                    u32::from(u16::from_be_bytes([bytes[at + 7], bytes[at + 8]])),
                    u32::from(u16::from_be_bytes([bytes[at + 5], bytes[at + 6]])),
                )?;
                return Ok("image/jpeg");
            }
            at += size + 2;
        }
        return Err("This JPEG has invalid dimensions.".into());
    }
    if bytes.len() >= 44 && bytes.starts_with(b"RIFF") && &bytes[8..12] == b"WAVE" {
        return Ok("audio/wav");
    }
    if bytes.len() >= 16
        && (bytes.starts_with(b"ID3") || (bytes[0] == 255 && bytes[1] & 0xe0 == 0xe0))
    {
        return Ok("audio/mpeg");
    }
    if bytes.len() >= 32 && bytes.starts_with(b"OggS") {
        return Ok("audio/ogg");
    }
    Err("Choose a JPEG/PNG image or WAV/MP3/OGG recording.".into())
}
impl NoteMedia {
    pub fn archive_name(&self) -> Result<String> {
        uuid::Uuid::parse_str(&self.id).map_err(error)?;
        Ok(format!("note_media/{}.bin", self.id))
    }
    pub fn validate(&self, bytes: &[u8]) -> Result<()> {
        uuid::Uuid::parse_str(&self.id).map_err(error)?;
        uuid::Uuid::parse_str(&self.note_id).map_err(error)?;
        if self.name.trim().is_empty()
            || self.name.chars().count() > 255
            || self
                .name
                .chars()
                .any(|c| c.is_control() || matches!(c, '/' | '\\' | ':'))
            || self.created_at > 8_640_000_000_000_000
            || bytes.len() != self.size
            || hex_digest(Sha256::digest(bytes)) != self.sha256
            || media_type(bytes)? != self.mime
        {
            return Err("The attachment metadata or integrity check is invalid.".into());
        }
        Ok(())
    }
}
impl Storage {
    pub fn all_note_media(&self) -> Result<Vec<NoteMedia>> {
        let mut q = self
            .connection
            .prepare("SELECT value FROM note_media ORDER BY rowid")
            .map_err(error)?;
        let rows = q.query_map([], |r| r.get::<_, String>(0)).map_err(error)?;
        rows.map(|r| serde_json::from_str(&r.map_err(error)?).map_err(error))
            .collect()
    }
    pub fn note_media(&self, note_id: &str) -> Result<Vec<NoteMedia>> {
        let mut q = self
            .connection
            .prepare("SELECT value FROM note_media WHERE note_id=?1 ORDER BY rowid")
            .map_err(error)?;
        let rows = q
            .query_map([note_id], |r| r.get::<_, String>(0))
            .map_err(error)?;
        rows.map(|r| serde_json::from_str(&r.map_err(error)?).map_err(error))
            .collect()
    }
    pub fn note_media_bytes(&self, id: &str) -> Result<Vec<u8>> {
        self.connection
            .query_row("SELECT bytes FROM note_media WHERE id=?1", [id], |r| {
                r.get(0)
            })
            .map_err(error)
    }
    pub fn note_media_url(&self, id: &str) -> Result<String> {
        let value: String = self
            .connection
            .query_row("SELECT value FROM note_media WHERE id=?1", [id], |r| {
                r.get(0)
            })
            .map_err(error)?;
        let item: NoteMedia = serde_json::from_str(&value).map_err(error)?;
        let bytes = self.note_media_bytes(id)?;
        item.validate(&bytes)?;
        Ok(format!(
            "data:{};base64,{}",
            item.mime,
            base64::engine::general_purpose::STANDARD.encode(bytes)
        ))
    }
    pub fn restore_note_media(&self, item: &NoteMedia, bytes: &[u8]) -> Result<()> {
        item.validate(bytes)?;
        if self
            .connection
            .query_row("SELECT 1 FROM note_media WHERE id=?1", [&item.id], |_| {
                Ok(())
            })
            .optional()
            .map_err(error)?
            .is_some()
        {
            return Ok(());
        }
        let count: i64 = self
            .connection
            .query_row(
                "SELECT COUNT(*) FROM note_media WHERE note_id=?1",
                [&item.note_id],
                |r| r.get(0),
            )
            .map_err(error)?;
        if count >= 20 {
            return Err("A note supports up to 20 attachments.".into());
        }
        let size: i64 = self
            .connection
            .query_row(
                "SELECT COALESCE(SUM(length(bytes)),0) FROM note_media WHERE note_id=?1",
                [&item.note_id],
                |r| r.get(0),
            )
            .map_err(error)?;
        if size.saturating_add(bytes.len() as i64) > 64 * 1024 * 1024 {
            return Err("A note supports up to 64 MB of attachments.".into());
        }
        self.connection
            .execute(
                "INSERT OR IGNORE INTO note_media(id,note_id,value,bytes) VALUES(?1,?2,?3,?4)",
                params![
                    item.id,
                    item.note_id,
                    serde_json::to_string(item).map_err(error)?,
                    bytes
                ],
            )
            .map_err(error)?;
        Ok(())
    }
    pub fn add_note_media(&self, note_id: &str, name: String, bytes: Vec<u8>) -> Result<NoteMedia> {
        let value: Option<String> = self
            .connection
            .query_row(
                "SELECT value FROM personal_notes WHERE id=?1",
                [note_id],
                |r| r.get(0),
            )
            .optional()
            .map_err(error)?;
        let note: crate::notes::Note =
            serde_json::from_str(&value.ok_or("Save the note before adding an attachment.")?)
                .map_err(error)?;
        if note.deleted_at.is_some() {
            return Err("Restore the note before adding attachments.".into());
        }
        let count: i64 = self
            .connection
            .query_row(
                "SELECT COUNT(*) FROM note_media WHERE note_id=?1",
                [note_id],
                |r| r.get(0),
            )
            .map_err(error)?;
        if count >= 20 {
            return Err("A note supports up to 20 attachments.".into());
        }
        let item = NoteMedia {
            id: uuid::Uuid::new_v4().to_string(),
            note_id: note_id.into(),
            name,
            mime: media_type(&bytes)?.into(),
            size: bytes.len(),
            sha256: hex_digest(Sha256::digest(&bytes)),
            created_at: now(),
        };
        self.restore_note_media(&item, &bytes)?;
        Ok(item)
    }
    pub fn delete_note_media(&self, id: &str) -> Result<()> {
        self.connection
            .execute("DELETE FROM note_media WHERE id=?1", [id])
            .map_err(error)?;
        Ok(())
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn attachments_validate_survive_trash_and_delete_with_the_note() {
        let mut db = Storage::open(std::path::Path::new(":memory:")).unwrap();
        let note = db
            .save_note(crate::notes::NoteInput {
                id: None,
                title: "Picture".into(),
                content: "A saved thought".into(),
                color: "paper".into(),
                pinned: false,
                is_checklist: false,
                label_ids: vec![],
            })
            .unwrap();
        let bytes = include_bytes!("../../public/covers/classic_alice_wonderland.jpg").to_vec();
        let item = db
            .add_note_media(&note.id, "cover.jpg".into(), bytes.clone())
            .unwrap();
        assert_eq!(db.note_media_bytes(&item.id).unwrap(), bytes);
        assert!(db
            .note_media_url(&item.id)
            .unwrap()
            .starts_with("data:image/jpeg;base64,"));
        assert!(db
            .add_note_media(&note.id, "../unsafe.jpg".into(), bytes.clone())
            .is_err());
        assert!(db
            .add_note_media(&note.id, "script.svg".into(), b"<svg/>".to_vec())
            .is_err());
        db.trash_note(&note.id, true).unwrap();
        assert_eq!(db.note_media(&note.id).unwrap().len(), 1);
        assert!(db
            .add_note_media(&note.id, "cover.jpg".into(), bytes)
            .is_err());
        db.delete_note(&note.id).unwrap();
        assert!(db.all_note_media().unwrap().is_empty());
        let mut oversized = b"\x89PNG\r\n\x1a\n\0\0\0\rIHDR".to_vec();
        oversized.extend_from_slice(&u32::MAX.to_be_bytes());
        oversized.extend_from_slice(&u32::MAX.to_be_bytes());
        assert!(media_type(&oversized).is_err());
    }
}
