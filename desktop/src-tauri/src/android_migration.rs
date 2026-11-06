//! Validate the canonical Android worker's sentence-to-passage map in Rust.
use crate::{
    android_backup::AndroidBackup,
    backup_archive::StagedFile,
    documents::{validate_source, SourceInfo},
    storage::{error, hex_digest, Book, Result, Storage},
};
use serde::Deserialize;
use sha2::{Digest, Sha256};
use std::sync::Arc;

#[derive(Clone, Debug, Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
pub struct ConvertedAndroidText {
    pub chunks: Vec<String>,
    pub source_to_chunk: Vec<usize>,
    pub source_pages: Vec<usize>,
    pub page_starts: Vec<usize>,
    pub source_count: usize,
    pub warnings: Vec<String>,
    #[serde(default)]
    pub cover: Option<String>,
}
#[derive(Debug)]
pub struct ConvertedReading {
    pub android_id: String,
    pub book: Book,
    pub source: Option<SourceInfo>,
    pub warnings: Vec<String>,
    source_to_chunk: Vec<usize>,
    pub(crate) original: Option<Arc<StagedFile>>,
}
impl ConvertedReading {
    pub fn anchor_range(&self, position: usize) -> Result<(usize, usize)> {
        let start = self.source_to_chunk.get(position).copied().ok_or("An Android note or study item refers to a missing sentence; its position was not guessed.")?;
        let end = self
            .source_to_chunk
            .get(position + 1)
            .copied()
            .unwrap_or(self.book.chunks.len())
            - 1;
        Ok((start, end))
    }
    pub fn original_bytes(&self) -> Result<Option<Vec<u8>>> {
        self.original
            .as_ref()
            .map(|file| file.read(32 * 1024 * 1024))
            .transpose()
    }
}
fn integer(row: &serde_json::Value, key: &str, default: u64) -> Result<u64> {
    row.get(key).map_or(Ok(default), |v| {
        v.as_u64().ok_or_else(|| format!("Invalid Android {key}."))
    })
}
fn text<'a>(row: &'a serde_json::Value, key: &str) -> &'a str {
    row.get(key)
        .and_then(serde_json::Value::as_str)
        .unwrap_or("")
}
impl AndroidBackup {
    pub fn convert_reading(
        &self,
        index: usize,
        converted: ConvertedAndroidText,
    ) -> Result<ConvertedReading> {
        let input = self.text_input(index)?;
        let document = &self.metadata()["documents"][index];
        let count = converted.source_count;
        if count > 100_000
            || converted.chunks.len() > 100_000
            || converted.source_to_chunk.len() != count
            || converted.source_pages.len() != count
            || (input.expected_chunks != 0 && input.expected_chunks != count)
            || converted.warnings.len() > 4
            || converted.warnings.iter().any(|s| s.chars().count() > 512)
            || converted
                .chunks
                .iter()
                .any(|s| s.trim().is_empty() || s.len() > 4096)
            || converted.chunks.iter().map(String::len).sum::<usize>() > 32 * 1024 * 1024
            || converted
                .source_to_chunk
                .windows(2)
                .any(|pair| pair[0] >= pair[1])
            || converted.source_to_chunk.first().is_some_and(|n| *n != 0)
            || converted
                .source_to_chunk
                .iter()
                .any(|n| *n >= converted.chunks.len())
            || converted
                .source_pages
                .iter()
                .any(|p| !(1..=10_000).contains(p))
            || converted
                .source_pages
                .windows(2)
                .any(|pair| pair[0] > pair[1])
            || (count == 0) != converted.chunks.is_empty()
        {
            return Err(
                "The converted Android sentence map is invalid; no restore was performed.".into(),
            );
        }
        let pages = input
            .page_count
            .max(1)
            .max(converted.source_pages.last().copied().unwrap_or(1));
        let mut cursor = 0;
        let expected_pages: Vec<usize> = (1..=pages)
            .map(|page| {
                while cursor < count && converted.source_pages[cursor] < page {
                    cursor += 1;
                }
                converted
                    .source_to_chunk
                    .get(cursor)
                    .copied()
                    .unwrap_or(converted.chunks.len().saturating_sub(1))
            })
            .collect();
        if converted.page_starts != expected_pages {
            return Err(
                "The converted Android page map does not match its sentence anchors.".into(),
            );
        }
        let saved = usize::try_from(integer(document, "currentIndex", 0)?).map_err(error)?;
        if saved > count {
            return Err(
                "The saved Android position exceeds its sentence index; it was not guessed.".into(),
            );
        }
        let original_name = text(document, "originalFileName");
        let original = self.staged_asset(&format!("originals/{original_name}"));
        let original_bytes = original
            .as_ref()
            .map(|file| file.read(32 * 1024 * 1024))
            .transpose()?;
        let text_available = !converted.chunks.is_empty();
        if !text_available && original.is_none() {
            return Err("An Android reading has no extracted text or original file. Export a full ZIP backup from Android.".into());
        }
        let supported = ["TXT", "MD", "LOG", "PDF", "EPUB", "DOCX", "PPTX"];
        let extension = original_name
            .rsplit('.')
            .next()
            .unwrap_or("")
            .to_uppercase();
        let format = if original.is_some() {
            if !supported.contains(&extension.as_str()) {
                return Err("An Android original uses a format not supported on desktop yet; keep the original backup.".into());
            }
            extension
        } else {
            let label = text(document, "sourceLabel").to_uppercase();
            if supported.contains(&label.as_str()) {
                label
            } else {
                "TXT".into()
            }
        };
        let title = text(document, "title").trim();
        let chunks = if text_available {
            converted.chunks
        } else {
            vec!["No extracted text.".into()]
        };
        let original_digest = original_bytes
            .as_ref()
            .map(|bytes| hex_digest(Sha256::digest(bytes)));
        let id = if text_available {
            hex_digest(Sha256::digest(serde_json::to_vec(&chunks).map_err(error)?))
        } else {
            hex_digest(Sha256::digest(format!(
                "vern:original:{}",
                original_digest
                    .as_deref()
                    .ok_or("Missing original digest.")?
            )))
        };
        let current_source = saved.min(count.saturating_sub(1));
        let position = converted
            .source_to_chunk
            .get(current_source)
            .copied()
            .unwrap_or(0);
        let updated_at = integer(document, "updatedAt", 0)?;
        if updated_at > 8_640_000_000_000_000 {
            return Err("Invalid Android reading timestamp.".into());
        }
        let book = Book {
            id,
            title: if title.is_empty() {
                "Untitled reading"
            } else {
                title
            }
            .into(),
            author: text(document, "author").into(),
            format,
            chunks,
            position,
            favorite: document
                .get("favorite")
                .and_then(serde_json::Value::as_bool)
                .unwrap_or(false),
            collection: text(document, "collection").into(),
            text_available,
            updated_at,
            cover: converted.cover,
        };
        let source = if let Some(bytes) = &original_bytes {
            validate_source(&book, original_name, bytes, &converted.page_starts)?;
            Some(SourceInfo {
                name: original_name.into(),
                format: book.format.clone(),
                page_starts: converted.page_starts,
                page: converted
                    .source_pages
                    .get(current_source)
                    .copied()
                    .unwrap_or(1)
                    - 1,
                text_available,
            })
        } else {
            None
        };
        // Reuse normal validators in private memory before any real library merge.
        let mut scratch = Storage::open(std::path::Path::new(":memory:"))?;
        scratch.restore_book_exact(book.clone(), original_digest.as_deref())?;
        scratch.update_book(&book.id, &book.title, &book.author, &book.collection)?;
        scratch.progress(&book.id, book.position)?;
        let mut warnings = converted.warnings;
        if original.is_none() && !original_name.is_empty() {
            warnings.push(format!(
                "{}: the backup has text but no original file.",
                book.title
            ));
        }
        Ok(ConvertedReading {
            android_id: text(document, "id").into(),
            book,
            source,
            warnings,
            source_to_chunk: converted.source_to_chunk,
            original,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::{
        fs::File,
        io::{Cursor, Write},
    };
    use zip::{write::SimpleFileOptions, ZipWriter};
    fn input(root: serde_json::Value, original: Option<(&str, &[u8])>) -> Arc<StagedFile> {
        let bytes = serde_json::to_vec(&root).unwrap();
        if let Some((name, data)) = original {
            let lease = StagedFile::stage(Cursor::new([]), 0).unwrap();
            let mut zip = ZipWriter::new(File::create(lease.path()).unwrap());
            zip.start_file("backup.json", SimpleFileOptions::default())
                .unwrap();
            zip.write_all(&bytes).unwrap();
            zip.start_file(format!("originals/{name}"), SimpleFileOptions::default())
                .unwrap();
            zip.write_all(data).unwrap();
            zip.finish().unwrap();
            lease
        } else {
            StagedFile::stage(Cursor::new(bytes), 64 * 1024 * 1024).unwrap()
        }
    }
    fn map() -> ConvertedAndroidText {
        ConvertedAndroidText {
            chunks: vec!["語".repeat(1365), "語".repeat(135) + ".", "Tail.".into()],
            source_to_chunk: vec![0, 2],
            source_pages: vec![1, 2],
            page_starts: vec![0, 2],
            source_count: 2,
            warnings: vec![],
            cover: None,
        }
    }
    #[test]
    fn split_utf8_passages_keep_sentence_anchors_resume_page_and_original_bytes() {
        let original = format!("{}. Tail.", "語".repeat(1500));
        let root = serde_json::json!({"schema":"veritas.reader.backup.v1","documents":[{"id":"android-reading","title":"A reading","text":original,"chunkCount":2,"currentIndex":1,"originalFileName":"original.txt","pageCount":2}]});
        let lease = input(root, Some(("original.txt", original.as_bytes())));
        let backup = AndroidBackup::read(lease.path()).unwrap();
        let reading = backup.convert_reading(0, map()).unwrap();
        assert_eq!(reading.book.position, 2);
        assert_eq!(reading.source.as_ref().unwrap().page, 1);
        assert_eq!(reading.anchor_range(0).unwrap(), (0, 1));
        assert_eq!(reading.anchor_range(1).unwrap(), (2, 2));
        assert!(reading.anchor_range(2).is_err());
        assert_eq!(
            reading.original_bytes().unwrap().unwrap(),
            original.as_bytes()
        );
        let mut bad = map();
        bad.source_to_chunk = vec![2, 0];
        assert!(backup.convert_reading(0, bad).is_err());
        let mut bad = map();
        bad.page_starts = vec![0, 1];
        assert!(backup.convert_reading(0, bad).is_err());
        let mut bad = map();
        bad.source_count = 3;
        assert!(backup.convert_reading(0, bad).is_err());
    }
    #[test]
    fn scans_require_their_original_and_use_original_identity_not_shared_placeholder_text() {
        let root = serde_json::json!({"schema":"veritas.reader.backup.v1","documents":[{"id":"scan","text":"","originalFileName":"scan.pdf","pageCount":2}]});
        let map = || ConvertedAndroidText {
            chunks: vec![],
            source_to_chunk: vec![],
            source_pages: vec![],
            page_starts: vec![0, 0],
            source_count: 0,
            warnings: vec![],
            cover: None,
        };
        let missing = input(root.clone(), None);
        assert!(AndroidBackup::read(missing.path())
            .unwrap()
            .convert_reading(0, map())
            .is_err());
        let a = input(root.clone(), Some(("scan.pdf", b"%PDF-1.7\nscan A")));
        let b = input(root, Some(("scan.pdf", b"%PDF-1.7\nscan B")));
        let a = AndroidBackup::read(a.path())
            .unwrap()
            .convert_reading(0, map())
            .unwrap();
        let b = AndroidBackup::read(b.path())
            .unwrap()
            .convert_reading(0, map())
            .unwrap();
        assert!(!a.book.text_available);
        assert_eq!(a.book.position, 0);
        assert_ne!(a.book.id, b.book.id);
    }
}
