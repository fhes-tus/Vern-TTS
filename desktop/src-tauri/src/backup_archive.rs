//! Full backups keep original files outside JSON and stage untrusted payloads privately.
use crate::{
    storage::{error, Result, Storage},
    transfer::{Backup, LIMIT},
};
use std::{
    collections::HashSet,
    fs::{self, File, OpenOptions},
    io::{Read, Seek, SeekFrom, Write},
    path::{Path, PathBuf},
    sync::Arc,
};
use zip::{write::SimpleFileOptions, CompressionMethod, ZipArchive, ZipWriter};

const ARCHIVE_LIMIT: u64 = 2 * 1024 * 1024 * 1024;
const ENTRY_LIMIT: u64 = 32 * 1024 * 1024;
#[derive(Debug)]
pub(crate) struct StagedFile {
    path: PathBuf,
}
impl StagedFile {
    fn create_in(parent: &Path) -> Result<(Arc<Self>, File)> {
        let path = parent.join(format!(".vern-backup-{}.tmp", uuid::Uuid::new_v4()));
        let mut options = OpenOptions::new();
        options.read(true).write(true).create_new(true);
        #[cfg(unix)]
        {
            use std::os::unix::fs::OpenOptionsExt;
            options.mode(0o600);
        }
        let file = options.open(&path).map_err(error)?;
        Ok((Arc::new(Self { path }), file))
    }
    pub fn path(&self) -> &Path {
        &self.path
    }
    pub fn read(&self, limit: usize) -> Result<Vec<u8>> {
        let mut bytes = Vec::new();
        File::open(&self.path)
            .map_err(error)?
            .take(limit as u64 + 1)
            .read_to_end(&mut bytes)
            .map_err(error)?;
        if bytes.len() > limit {
            return Err("A backup attachment exceeds its size limit.".into());
        }
        Ok(bytes)
    }
    pub(crate) fn stage(reader: impl Read, limit: u64) -> Result<Arc<Self>> {
        let (lease, mut file) = Self::create_in(&std::env::temp_dir())?;
        let result = std::io::copy(&mut reader.take(limit + 1), &mut file).map_err(error);
        drop(file);
        if result? > limit {
            return Err("A backup attachment exceeds its size limit.".into());
        }
        Ok(lease)
    }
}
impl Drop for StagedFile {
    fn drop(&mut self) {
        let _ = fs::remove_file(&self.path);
    }
}
impl Storage {
    pub(crate) fn backup_database_snapshot(&self) -> Result<Arc<StagedFile>> {
        let (lease, file) = StagedFile::create_in(&std::env::temp_dir())?;
        drop(file);
        self.connection
            .execute(
                "VACUUM INTO ?1",
                [lease
                    .path()
                    .to_str()
                    .ok_or("The temporary folder path is unsupported.")?],
            )
            .map_err(error)?;
        Ok(lease)
    }
}

// Inspect the small central-directory envelope before the ZIP reader allocates metadata.
fn envelope(file: &mut File) -> Result<()> {
    let len = file.metadata().map_err(error)?.len();
    if !(22..=ARCHIVE_LIMIT).contains(&len) {
        return Err("Full backups support archives up to 2 GB.".into());
    }
    let mut tail = Vec::new();
    file.seek(SeekFrom::End(-(len.min(65_557) as i64)))
        .map_err(error)?;
    file.read_to_end(&mut tail).map_err(error)?;
    let start = tail
        .windows(4)
        .enumerate()
        .rev()
        .find_map(|(i, b)| {
            (b == b"PK\x05\x06"
                && i + 22 <= tail.len()
                && i + 22 + u16::from_le_bytes([tail[i + 20], tail[i + 21]]) as usize == tail.len())
            .then_some(i)
        })
        .ok_or("This is not a complete backup ZIP archive.")?;
    let header = &tail[start..];
    let entries = u16::from_le_bytes([header[10], header[11]]);
    if header[4..8] != [0, 0, 0, 0]
        || header[8..10] != header[10..12]
        || entries > 20_000
        || u32::from_le_bytes(header[12..16].try_into().map_err(error)?) > 16 * 1024 * 1024
    {
        return Err("The archive has too many entries, excessive metadata, or unsupported split/ZIP64 records.".into());
    }
    file.rewind().map_err(error)
}
fn safe_entry(name: &str, directory: bool) -> bool {
    if name.len() > 1024
        || name
            .chars()
            .any(|c| c.is_control() || matches!(c, '\\' | ':'))
    {
        return false;
    }
    if name == "backup.json" {
        return !directory;
    }
    if directory {
        return ["originals/", "covers/", "note_media/"].contains(&name);
    }
    if let Some(path) = name.strip_prefix("android_imports/") {
        let mut parts = path.split('/');
        let digest = |s: &str| {
            s.len() == 64
                && s.bytes()
                    .all(|c| c.is_ascii_digit() || (b'a'..=b'f').contains(&c))
        };
        return parts.next().is_some_and(digest)
            && parts
                .next()
                .and_then(|file| file.strip_suffix(".bin"))
                .is_some_and(digest)
            && parts.next().is_none()
            && !directory;
    }
    let mut parts = name.split('/');
    matches!(parts.next(), Some("originals" | "covers" | "note_media"))
        && parts
            .next()
            .is_some_and(|n| !n.is_empty() && n != "." && n != "..")
        && parts.next().is_none()
}
pub(crate) fn open_validated_zip(path: &Path) -> Result<ZipArchive<File>> {
    let mut file = File::open(path).map_err(error)?;
    envelope(&mut file)?;
    let mut zip = ZipArchive::new(file).map_err(error)?;
    let mut seen = HashSet::new();
    let mut total = 0_u64;
    for index in 0..zip.len() {
        let entry = zip.by_index(index).map_err(error)?;
        let name = entry.name().map_err(error)?;
        if !safe_entry(&name, entry.is_dir())
            || !seen.insert(name.to_string())
            || entry
                .unix_mode()
                .is_some_and(|mode| mode & 0o170000 == 0o120000)
            || !matches!(
                entry.compression(),
                CompressionMethod::Stored | CompressionMethod::Deflated
            )
        {
            return Err(
                "The backup contains unsafe, duplicated or unsupported archive entries.".into(),
            );
        }
        let limit = if name.as_ref() == "backup.json" {
            LIMIT as u64
        } else {
            ENTRY_LIMIT
        };
        if entry.size() > limit {
            return Err("A backup entry exceeds its size limit.".into());
        }
        total = total
            .checked_add(entry.size())
            .ok_or("Backup size overflow.")?;
        if total > ARCHIVE_LIMIT {
            return Err("Expanded backup data exceeds 2 GB.".into());
        }
    }
    Ok(zip)
}
pub fn read(path: &Path) -> Result<Backup> {
    let mut zip = open_validated_zip(path)?;
    let mut json = Vec::new();
    zip.by_name("backup.json")
        .map_err(error)?
        .take(LIMIT as u64 + 1)
        .read_to_end(&mut json)
        .map_err(error)?;
    if json.len() > LIMIT {
        return Err("Backup metadata exceeds 64 MB.".into());
    }
    let value: serde_json::Value = serde_json::from_slice(&json).map_err(error)?;
    if value.get("schema").and_then(|v| v.as_str()) == Some("veritas.reader.backup.v1") {
        return Err(
            "Android archive migration is being completed; keep this original backup.".into(),
        );
    }
    if !value
        .get("schema")
        .and_then(|schema| schema.as_str())
        .is_some_and(crate::transfer::supported_schema)
    {
        return Err("Unsupported desktop backup version.".into());
    }
    let mut backup: Backup = serde_json::from_value(value).map_err(error)?;
    if backup.sources.len() > 20_000 {
        return Err("The backup contains too many original files.".into());
    }
    let mut source_ids = HashSet::new();
    for source in &mut backup.sources {
        let name = source.archive_name()?;
        if !source_ids.insert(source.book_id.clone()) || !(1..=ENTRY_LIMIT).contains(&source.size) {
            return Err("The backup has duplicate or oversized original files.".into());
        }
        let entry = zip.by_name(&name).map_err(error)?;
        if entry.size() != source.size {
            return Err("A backed-up original file has an unexpected size.".into());
        }
        source.staged = Some(StagedFile::stage(entry, ENTRY_LIMIT)?);
    }
    if backup.note_media.len() > 20_000 {
        return Err("The backup contains too many note attachments.".into());
    }
    let mut media_ids = HashSet::new();
    for media in &mut backup.note_media {
        if !media_ids.insert(media.info.id.clone())
            || !(1..=crate::note_media::LIMIT).contains(&media.info.size)
        {
            return Err("The backup contains duplicate or oversized note attachments.".into());
        }
        let entry = zip.by_name(&media.info.archive_name()?).map_err(error)?;
        if entry.size() != media.info.size as u64 {
            return Err("A note attachment has an unexpected size.".into());
        }
        media.staged = Some(StagedFile::stage(entry, crate::note_media::LIMIT as u64)?);
    }
    let mut import_ids = HashSet::new();
    let mut retained_entries = 0usize;
    if backup.android_imports.len() > 100 {
        return Err("Too many retained Android backups.".into());
    }
    for item in &backup.android_imports {
        item.validate_manifest()?;
        if !import_ids.insert(&item.id) {
            return Err("Duplicate retained Android backup.".into());
        }
        retained_entries = retained_entries
            .checked_add(item.assets.len())
            .ok_or("Too many Android attachments.")?;
    }
    if retained_entries + backup.sources.len() + backup.note_media.len() + 1 > 20_000 {
        return Err("Too many backup attachments.".into());
    }
    for item in &mut backup.android_imports {
        for asset in &mut item.assets {
            let entry = zip.by_name(&asset.archive_name(&item.id)?).map_err(error)?;
            if asset.size > ENTRY_LIMIT as usize || entry.size() != asset.size as u64 {
                return Err("Invalid retained Android attachment size.".into());
            }
            asset.staged = Some(StagedFile::stage(entry, ENTRY_LIMIT)?);
        }
    }
    backup.validate()?;
    Ok(backup)
}

pub(crate) struct BoundedFile {
    pub(crate) inner: File,
    pub(crate) written: u64,
}
impl Write for BoundedFile {
    fn write(&mut self, bytes: &[u8]) -> std::io::Result<usize> {
        if self.written.saturating_add(bytes.len() as u64) > ARCHIVE_LIMIT {
            return Err(std::io::Error::other("The full backup exceeds 2 GB."));
        }
        let count = self.inner.write(bytes)?;
        self.written += count as u64;
        Ok(count)
    }
    fn flush(&mut self) -> std::io::Result<()> {
        self.inner.flush()
    }
}
impl Seek for BoundedFile {
    fn seek(&mut self, position: SeekFrom) -> std::io::Result<u64> {
        self.inner.seek(position)
    }
}
pub fn write(db: &Storage, path: &Path) -> Result<()> {
    let backup = db.backup_snapshot(true)?;
    if backup.sources.len()
        + backup.note_media.len()
        + backup
            .android_imports
            .iter()
            .map(|item| item.assets.len())
            .sum::<usize>()
        + 1
        > 20_000
    {
        return Err("A full backup supports up to 19,999 original files and attachments.".into());
    }
    let json = backup.json_bytes()?;
    let (lease, file) = StagedFile::create_in(path.parent().ok_or("Choose a backup folder.")?)?;
    let mut zip = ZipWriter::new(BoundedFile {
        inner: file,
        written: 0,
    });
    let options = SimpleFileOptions::default()
        .compression_method(CompressionMethod::Deflated)
        .compression_level(Some(3));
    zip.start_file("backup.json", options).map_err(error)?;
    zip.write_all(&json).map_err(error)?;
    let mut total = json.len() as u64;
    for source in &backup.sources {
        total = total
            .checked_add(source.size)
            .ok_or("Backup size overflow.")?;
        if total > ARCHIVE_LIMIT {
            return Err("Original files and metadata exceed the 2 GB backup limit.".into());
        }
        let file = db
            .source_file(&source.book_id)?
            .ok_or("An original file disappeared during backup.")?;
        let method = if ["PDF", "EPUB", "DOCX", "PPTX"].contains(&source.info.format.as_str()) {
            CompressionMethod::Stored
        } else {
            CompressionMethod::Deflated
        };
        zip.start_file(
            source.archive_name()?,
            options.compression_method(method).compression_level(
                if method == CompressionMethod::Stored {
                    None
                } else {
                    Some(3)
                },
            ),
        )
        .map_err(error)?;
        zip.write_all(&file.bytes).map_err(error)?;
    }
    for media in &backup.note_media {
        total = total
            .checked_add(media.info.size as u64)
            .ok_or("Backup size overflow.")?;
        if total > ARCHIVE_LIMIT {
            return Err("The full backup exceeds 2 GB.".into());
        }
        let bytes = db.note_media_bytes(&media.info.id)?;
        media.info.validate(&bytes)?;
        zip.start_file(
            media.info.archive_name()?,
            options
                .compression_method(CompressionMethod::Stored)
                .compression_level(None),
        )
        .map_err(error)?;
        zip.write_all(&bytes).map_err(error)?;
    }
    for item in &backup.android_imports {
        for asset in &item.assets {
            total = total
                .checked_add(asset.size as u64)
                .ok_or("Backup size overflow.")?;
            if total > ARCHIVE_LIMIT {
                return Err("The full backup exceeds 2 GB.".into());
            }
            let bytes = db.android_asset_bytes(&item.id, asset)?;
            zip.start_file(
                asset.archive_name(&item.id)?,
                options
                    .compression_method(CompressionMethod::Stored)
                    .compression_level(None),
            )
            .map_err(error)?;
            zip.write_all(&bytes).map_err(error)?;
        }
    }
    let output = zip.finish().map_err(error)?;
    output.inner.sync_all().map_err(error)?;
    drop(output);
    fs::rename(lease.path(), path).map_err(error)
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::storage::Book;
    fn target() -> Arc<StagedFile> {
        let (lease, file) = StagedFile::create_in(&std::env::temp_dir()).unwrap();
        drop(file);
        lease
    }
    fn fixture() -> (Storage, Book, Vec<u8>) {
        let mut db = Storage::open(Path::new(":memory:")).unwrap();
        let bytes = b"%PDF-1.7\noriginal-page-fixture".to_vec();
        let book = db
            .import_source(
                Book {
                    id: String::new(),
                    title: "Archive fixture".into(),
                    author: "Author".into(),
                    format: "PDF".into(),
                    chunks: vec!["A first passage.".into(), "A second passage.".into()],
                    position: 0,
                    favorite: true,
                    collection: String::new(),
                    text_available: true,
                    updated_at: 0,
                    cover: None,
                },
                "fixture.pdf".into(),
                bytes.clone(),
                vec![0, 1],
                true,
            )
            .unwrap();
        db.progress(&book.id, 1).unwrap();
        db.source_page(&book.id, 1).unwrap();
        (db, book, bytes)
    }
    #[test]
    fn note_attachments_and_revisions_round_trip_without_json_data_loss() {
        let (mut db, _, _) = fixture();
        let input = |id, content: &str| crate::notes::NoteInput {
            id,
            title: "A picture".into(),
            content: content.into(),
            color: "paper".into(),
            pinned: false,
            is_checklist: false,
            label_ids: vec![],
        };
        let note = db.save_note(input(None, "First version")).unwrap();
        db.save_note(input(Some(note.id.clone()), "Second version"))
            .unwrap();
        let bytes = include_bytes!("../../public/covers/classic_alice_wonderland.jpg").to_vec();
        let item = db
            .add_note_media(&note.id, "Alice.jpg".into(), bytes.clone())
            .unwrap();
        assert!(db.backup_bytes().err().unwrap().contains("attachments"));
        let archive = target();
        write(&db, archive.path()).unwrap();
        let incoming = read(archive.path()).unwrap();
        assert_eq!(incoming.preview("test".into()).note_media, 1);
        let mut restored = Storage::open(Path::new(":memory:")).unwrap();
        restored.restore_backup(incoming, false).unwrap();
        assert_eq!(restored.note_media_bytes(&item.id).unwrap(), bytes);
        assert_eq!(
            restored.note_revisions(&note.id).unwrap()[0].note.content,
            "First version"
        );
        restored
            .restore_backup(read(archive.path()).unwrap(), false)
            .unwrap();
        assert_eq!(restored.note_media(&note.id).unwrap().len(), 1);
        let mut corrupt = read(archive.path()).unwrap();
        fs::write(
            corrupt.note_media[0].staged.as_ref().unwrap().path(),
            b"tampered",
        )
        .unwrap();
        assert!(corrupt.validate().is_err());
        corrupt.note_media.clear();
    }
    #[test]
    fn full_backup_restores_sources_and_pages_without_overwriting_existing_progress() {
        let (source, book, bytes) = fixture();
        let snapshot = source.backup_database_snapshot().unwrap();
        let snapshot_db = Storage::open(snapshot.path()).unwrap();
        let archive = target();
        write(&snapshot_db, archive.path()).unwrap();
        let incoming = read(archive.path()).unwrap();
        assert_eq!(incoming.preview("test".into()).source_files, 1);
        let mut restored = Storage::open(Path::new(":memory:")).unwrap();
        restored.restore_backup(incoming, false).unwrap();
        assert_eq!(restored.book(&book.id).unwrap().position, 1);
        assert_eq!(
            restored.source_file(&book.id).unwrap().unwrap().bytes,
            bytes
        );
        assert_eq!(restored.source_info(&book.id).unwrap().unwrap().page, 1);

        let mut current = Storage::open(Path::new(":memory:")).unwrap();
        let mut existing = book.clone();
        existing.title = "Current title".into();
        let current_bytes = b"%PDF-1.7\ncurrent-original".to_vec();
        current
            .import_source(
                existing,
                "current.pdf".into(),
                current_bytes.clone(),
                vec![0, 1],
                true,
            )
            .unwrap();
        current
            .restore_backup(read(archive.path()).unwrap(), false)
            .unwrap();
        assert_eq!(current.book(&book.id).unwrap().title, "Current title");
        assert_eq!(current.book(&book.id).unwrap().position, 0);
        assert_eq!(
            current.source_file(&book.id).unwrap().unwrap().bytes,
            current_bytes
        );
        assert_eq!(current.source_info(&book.id).unwrap().unwrap().page, 0);
    }
    #[test]
    fn source_tampering_is_rejected_before_restore() {
        let (db, _, mut bytes) = fixture();
        let backup = db.backup_snapshot(true).unwrap();
        let archive = target();
        let mut zip = ZipWriter::new(File::create(archive.path()).unwrap());
        let options = SimpleFileOptions::default().compression_method(CompressionMethod::Stored);
        zip.start_file("backup.json", options).unwrap();
        zip.write_all(&backup.json_bytes().unwrap()).unwrap();
        *bytes.last_mut().unwrap() ^= 1;
        zip.start_file(backup.sources[0].archive_name().unwrap(), options)
            .unwrap();
        zip.write_all(&bytes).unwrap();
        drop(zip.finish().unwrap());
        assert!(read(archive.path()).err().unwrap().contains("integrity"));
    }
    #[test]
    fn traversal_and_excessive_directory_metadata_are_rejected() {
        for name in [
            "android_imports/../escape.bin",
            "android_imports/abc/def.bin",
            "android_imports/a/b/c.bin",
            "android_imports/C:/outside.bin",
        ] {
            assert!(!safe_entry(name, false));
        }
        let bad = target();
        let mut zip = ZipWriter::new(File::create(bad.path()).unwrap());
        zip.start_file("originals/../../escape", SimpleFileOptions::default())
            .unwrap();
        zip.write_all(b"untrusted").unwrap();
        drop(zip.finish().unwrap());
        assert!(read(bad.path()).is_err());

        let good = target();
        let db = Storage::open(Path::new(":memory:")).unwrap();
        write(&db, good.path()).unwrap();
        assert!(read(good.path()).is_ok());
        let mut bytes = fs::read(good.path()).unwrap();
        let end = bytes.windows(4).rposition(|p| p == b"PK\x05\x06").unwrap();
        bytes[end + 12..end + 16].copy_from_slice(&(17_u32 * 1024 * 1024).to_le_bytes());
        fs::write(good.path(), bytes).unwrap();
        assert!(read(good.path()).err().unwrap().contains("metadata"));
    }
    #[test]
    fn earlier_json_backups_remain_readable() {
        let (db, book, _) = fixture();
        let json = target();
        let mut legacy: serde_json::Value =
            serde_json::from_slice(&db.backup_bytes().unwrap()).unwrap();
        legacy["schema"] = "vern.desktop.backup.v1".into();
        for field in ["sources", "readingLists", "classicLinks"] {
            legacy.as_object_mut().unwrap().remove(field);
        }
        fs::write(json.path(), serde_json::to_vec(&legacy).unwrap()).unwrap();
        let backup = crate::transfer::read_backup(json.path()).unwrap();
        assert_eq!(backup.preview("old".into()).source_files, 0);
        let mut restored = Storage::open(Path::new(":memory:")).unwrap();
        restored.restore_backup(backup, false).unwrap();
        assert_eq!(restored.book(&book.id).unwrap().position, 1);
    }
    #[test]
    fn different_scans_with_identical_placeholders_keep_distinct_identities_and_backups() {
        let (_, mut template, _) = fixture();
        template.text_available = false;
        template.chunks = vec![
            "Page one has no extracted text.".into(),
            "Page two has no extracted text.".into(),
        ];
        let mut db = Storage::open(Path::new(":memory:")).unwrap();
        let first_bytes = b"%PDF-1.7\nscanned-image-one".to_vec();
        let second_bytes = b"%PDF-1.7\nscanned-image-two".to_vec();
        let first = db
            .import_source(
                template.clone(),
                "one.pdf".into(),
                first_bytes.clone(),
                vec![0, 1],
                false,
            )
            .unwrap();
        let second = db
            .import_source(
                template.clone(),
                "two.pdf".into(),
                second_bytes.clone(),
                vec![0, 1],
                false,
            )
            .unwrap();
        assert_ne!(first.id, second.id);
        assert_eq!(db.books().unwrap().len(), 2);
        db.progress(&first.id, 1).unwrap();
        assert_eq!(
            db.import_source(
                template,
                "renamed.pdf".into(),
                first_bytes.clone(),
                vec![0, 1],
                false
            )
            .unwrap()
            .position,
            1
        );
        assert_eq!(db.books().unwrap().len(), 2);
        assert!(db.backup_bytes().is_err());
        let archive = target();
        write(&db, archive.path()).unwrap();
        let mut restored = Storage::open(Path::new(":memory:")).unwrap();
        restored
            .restore_backup(read(archive.path()).unwrap(), false)
            .unwrap();
        assert_eq!(restored.books().unwrap().len(), 2);
        assert_eq!(restored.book(&first.id).unwrap().position, 1);
        assert_eq!(
            restored.source_file(&first.id).unwrap().unwrap().bytes,
            first_bytes
        );
        assert_eq!(
            restored.source_file(&second.id).unwrap().unwrap().bytes,
            second_bytes
        );
    }
    #[test]
    fn legacy_scanned_identity_is_preserved_when_the_same_original_is_reimported() {
        let (_, mut template, _) = fixture();
        template.text_available = false;
        let mut db = Storage::open(Path::new(":memory:")).unwrap();
        let legacy = db.save_book(template.clone()).unwrap();
        let bytes = b"%PDF-1.7\nlegacy-scanned-original".to_vec();
        db.connection.execute("INSERT INTO sources(book_id,name,format,bytes,page_starts,page,text_available) VALUES(?1,'legacy.pdf','PDF',?2,'[0,1]',1,0)",rusqlite::params![legacy.id,bytes]).unwrap();
        db.progress(&legacy.id, 1).unwrap();
        let saved = db
            .import_source(template, "legacy.pdf".into(), bytes, vec![0, 1], false)
            .unwrap();
        assert_eq!(saved.id, legacy.id);
        assert_eq!(saved.position, 1);
        assert_eq!(db.books().unwrap().len(), 1);
        assert_eq!(db.source_info(&saved.id).unwrap().unwrap().page, 1);
    }
}
