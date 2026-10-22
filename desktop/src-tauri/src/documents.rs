use crate::storage::{error, Book, Result, Storage};
use rusqlite::{params, OptionalExtension};
use serde::Serialize;

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SourceInfo {
    pub name: String,
    pub format: String,
    pub page_starts: Vec<usize>,
    pub page: usize,
    pub text_available: bool,
}
#[derive(Serialize)]
pub struct SourceFile {
    pub name: String,
    pub bytes: Vec<u8>,
}

impl Storage {
    pub fn import_source(
        &mut self,
        book: Book,
        name: String,
        bytes: Vec<u8>,
        page_starts: Vec<usize>,
        text_available: bool,
    ) -> Result<Book> {
        let extension = name.rsplit('.').next().unwrap_or("").to_uppercase();
        if bytes.is_empty()
            || bytes.len() > 32 * 1024 * 1024
            || name.len() > 1000
            || name.contains(['/', '\\', '\0'])
            || extension != book.format
        {
            return Err("The original file does not match this import.".into());
        }
        if page_starts.len() > 10_000
            || page_starts.windows(2).any(|p| p[0] > p[1])
            || page_starts.iter().any(|p| *p >= book.chunks.len())
            || page_starts.first().is_some_and(|p| *p != 0)
        {
            return Err("The document page index is invalid.".into());
        }
        if extension == "PDF" && !bytes.starts_with(b"%PDF-")
            || ["EPUB", "DOCX", "PPTX"].contains(&extension.as_str()) && !bytes.starts_with(b"PK")
        {
            return Err("The original document has an invalid file signature.".into());
        }
        self.connection
            .execute_batch("SAVEPOINT import_source")
            .map_err(error)?;
        let result = (|| {
            let saved = self.save_book(book)?;
            self.connection.execute("INSERT OR IGNORE INTO sources(book_id,name,format,bytes,page_starts,page,text_available) VALUES(?1,?2,?3,?4,?5,0,?6)", params![saved.id,name,extension,bytes,serde_json::to_string(&page_starts).map_err(error)?,text_available]).map_err(error)?;
            Ok(saved)
        })();
        match result {
            Ok(book) => {
                self.connection
                    .execute_batch("RELEASE import_source")
                    .map_err(error)?;
                Ok(book)
            }
            Err(e) => {
                self.connection
                    .execute_batch("ROLLBACK TO import_source; RELEASE import_source")
                    .map_err(error)?;
                Err(e)
            }
        }
    }
    pub fn source_info(&self, id: &str) -> Result<Option<SourceInfo>> {
        let row: Option<(String, String, String, i64, bool)> = self
            .connection
            .query_row(
                "SELECT name,format,page_starts,page,text_available FROM sources WHERE book_id=?1",
                [id],
                |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?, r.get(4)?)),
            )
            .optional()
            .map_err(error)?;
        row.map(|(name, format, pages, page, text_available)| {
            Ok(SourceInfo {
                name,
                format,
                page_starts: serde_json::from_str(&pages).map_err(error)?,
                page: usize::try_from(page).map_err(error)?,
                text_available,
            })
        })
        .transpose()
    }
    pub fn source_file(&self, id: &str) -> Result<Option<SourceFile>> {
        self.connection
            .query_row(
                "SELECT name,bytes FROM sources WHERE book_id=?1",
                [id],
                |r| {
                    Ok(SourceFile {
                        name: r.get(0)?,
                        bytes: r.get(1)?,
                    })
                },
            )
            .optional()
            .map_err(error)
    }
    pub fn source_page(&mut self, id: &str, page: usize) -> Result<()> {
        let source = self
            .source_info(id)?
            .ok_or("Reimport the original document to use this view.")?;
        if page >= source.page_starts.len().max(1) {
            return Err("This page is outside the document.".into());
        }
        self.connection
            .execute(
                "UPDATE sources SET page=?1 WHERE book_id=?2",
                params![i64::try_from(page).map_err(error)?, id],
            )
            .map_err(error)?;
        Ok(())
    }
    pub fn source_index(&mut self, id: &str, pages: Vec<usize>) -> Result<()> {
        let book = self.book(id)?;
        if pages.is_empty()
            || pages.len() > 10000
            || pages[0] != 0
            || pages.windows(2).any(|p| p[0] > p[1])
            || pages.iter().any(|p| *p >= book.chunks.len())
        {
            return Err("Invalid original-document page index.".into());
        }
        self.connection
            .execute(
                "UPDATE sources SET page_starts=?1,page=MIN(page,?2) WHERE book_id=?3",
                params![
                    serde_json::to_string(&pages).map_err(error)?,
                    i64::try_from(pages.len() - 1).map_err(error)?,
                    id
                ],
            )
            .map_err(error)?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn original_bytes_pages_and_notes_survive_reopen_and_delete_together() {
        let path =
            std::env::temp_dir().join(format!("vern-source-{}.sqlite", uuid::Uuid::new_v4()));
        let input = Book {
            id: String::new(),
            title: "Source fixture".into(),
            author: String::new(),
            format: "PDF".into(),
            chunks: vec!["First page.".into(), "Second page.".into()],
            position: 0,
            favorite: false,
            collection: String::new(),
            updated_at: 0,
            text_available: true,
        };
        let bytes = b"%PDF-1.4\nsource-persistence-fixture".to_vec();
        let mut db = Storage::open(&path).unwrap();
        assert!(db
            .import_source(
                input.clone(),
                "fixture.pdf".into(),
                bytes.clone(),
                vec![0, 4],
                true
            )
            .is_err());
        assert!(db.books().unwrap().is_empty());
        let book = db
            .import_source(
                input.clone(),
                "fixture.pdf".into(),
                bytes.clone(),
                vec![0, 1],
                true,
            )
            .unwrap();
        db.progress(&book.id, 1).unwrap();
        db.source_page(&book.id, 1).unwrap();
        assert!(db.source_page(&book.id, 2).is_err());
        db.annotate(crate::storage::Annotation {
            id: uuid::Uuid::new_v4().to_string(),
            book_id: book.id.clone(),
            position: 1,
            text: "Anchored on page two".into(),
            kind: "note".into(),
        })
        .unwrap();
        assert_eq!(
            db.import_source(input, "fixture.pdf".into(), bytes.clone(), vec![0, 1], true)
                .unwrap()
                .position,
            1
        );
        drop(db);
        let mut db = Storage::open(&path).unwrap();
        assert_eq!(db.source_file(&book.id).unwrap().unwrap().bytes, bytes);
        assert_eq!(db.source_info(&book.id).unwrap().unwrap().page, 1);
        assert_eq!(db.annotations(&book.id).unwrap().len(), 1);
        db.delete_book(&book.id).unwrap();
        assert!(db.source_file(&book.id).unwrap().is_none());
        assert!(db.annotations(&book.id).unwrap().is_empty());
        drop(db);
        std::fs::remove_file(path).unwrap();
    }
}
