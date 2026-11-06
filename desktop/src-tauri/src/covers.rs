use crate::storage::{error, Result, Storage};
use base64::Engine;
pub fn validate_cover(cover: &str) -> Result<()> {
    if cover.len() > 350_000 {
        return Err("Cover exceeds the thumbnail limit.".into());
    }
    let data = base64::engine::general_purpose::STANDARD
        .decode(
            cover
                .strip_prefix("data:image/jpeg;base64,")
                .ok_or("Unsupported cover image.")?,
        )
        .map_err(error)?;
    if data.len() < 12 || data.len() > 256 * 1024 || !data.starts_with(&[255, 216]) {
        return Err("Invalid cover thumbnail.".into());
    }
    let mut at = 2;
    while at + 9 < data.len() {
        if data[at] != 255 {
            break;
        }
        let marker = data[at + 1];
        let size = usize::from(u16::from_be_bytes([data[at + 2], data[at + 3]]));
        if size < 2 || at + 2 + size > data.len() {
            break;
        }
        if [
            192, 193, 194, 195, 197, 198, 199, 201, 202, 203, 205, 206, 207,
        ]
        .contains(&marker)
        {
            let height = u16::from_be_bytes([data[at + 5], data[at + 6]]);
            let width = u16::from_be_bytes([data[at + 7], data[at + 8]]);
            if width > 0 && height > 0 && width <= 400 && height <= 600 {
                return Ok(());
            }
            break;
        }
        at += size + 2;
    }
    Err("Cover dimensions are invalid or exceed 400 by 600 pixels.".into())
}
impl Storage {
    pub fn save_cover(&mut self, id: &str, cover: &str) -> Result<()> {
        validate_cover(cover)?;
        if self
            .connection
            .execute(
                "UPDATE books SET cover=?1 WHERE id=?2",
                rusqlite::params![cover, id],
            )
            .map_err(error)?
            != 1
        {
            return Err("Book no longer exists.".into());
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::storage::Book;
    #[test]
    fn thumbnails_validate_survive_reopen_and_do_not_reset_progress() {
        let cover = format!(
            "data:image/jpeg;base64,{}",
            base64::engine::general_purpose::STANDARD.encode(include_bytes!(
                "../../public/covers/classic_alice_wonderland.jpg"
            ))
        );
        validate_cover(&cover).unwrap();
        assert!(validate_cover("data:text/html;base64,PGgxPmhpPC9oMT4=").is_err());
        assert!(
            validate_cover(&format!("data:image/jpeg;base64,{}", "A".repeat(350_000))).is_err()
        );
        let root = std::env::temp_dir().join(format!("vern-cover-{}.sqlite", uuid::Uuid::new_v4()));
        let mut db = Storage::open(&root).unwrap();
        let book = db
            .save_book(Book {
                id: String::new(),
                title: "A real cover".into(),
                author: String::new(),
                format: "EPUB".into(),
                chunks: vec!["Opening.".into(), "Next page.".into()],
                position: 0,
                favorite: false,
                collection: String::new(),
                text_available: true,
                updated_at: 0,
                cover: None,
            })
            .unwrap();
        db.progress(&book.id, 1).unwrap();
        db.save_cover(&book.id, &cover).unwrap();
        drop(db);
        let mut db = Storage::open(&root).unwrap();
        let saved = db.book(&book.id).unwrap();
        assert_eq!(saved.cover.as_deref(), Some(cover.as_str()));
        assert_eq!(saved.position, 1);
        let duplicate = db.save_book(saved).unwrap();
        assert_eq!(duplicate.position, 1);
        assert_eq!(duplicate.cover.as_deref(), Some(cover.as_str()));
        assert!(db.save_cover("missing", &cover).is_err());
        drop(db);
        std::fs::remove_file(root).unwrap();
    }
}
