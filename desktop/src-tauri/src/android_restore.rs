//! Android conversion is transient until the existing single-use restore preview is accepted.
use crate::{
    android_backup::{AndroidBackup, AndroidTextInput},
    android_migration::{ConvertedAndroidText, ConvertedReading},
    storage::{error, hex_digest, Result, Settings},
    transfer::{Backup, MediaBackup, SourceBackup, FULL_SCHEMA},
};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::{
    collections::{HashMap, HashSet},
    fs::File,
    io::Read,
    path::Path,
};

pub struct Conversion {
    backup: AndroidBackup,
    readings: Vec<ConvertedReading>,
}
fn text<'a>(row: &'a Value, key: &str) -> &'a str {
    row.get(key).and_then(Value::as_str).unwrap_or("")
}
fn uint(row: &Value, key: &str, default: u64) -> Result<u64> {
    row.get(key).map_or(Ok(default), |v| {
        v.as_u64().ok_or_else(|| format!("Invalid Android {key}."))
    })
}
fn flag(row: &Value, key: &str) -> bool {
    row.get(key).and_then(Value::as_bool).unwrap_or(false)
}
fn rows<'a>(root: &'a Value, key: &str) -> &'a [Value] {
    root.get(key)
        .and_then(Value::as_array)
        .map_or(&[], Vec::as_slice)
}
pub(crate) fn stable_id(kind: &str, source: &str) -> String {
    // Preserve source UUIDs; deterministic namespaced UUIDs cover older Android IDs.
    if let Ok(id) = uuid::Uuid::parse_str(source) {
        return id.to_string();
    }
    let hash = Sha256::digest(format!("vern:android:{kind}:{source}"));
    let mut bytes = [0_u8; 16];
    bytes.copy_from_slice(&hash[..16]);
    bytes[6] = (bytes[6] & 15) | 128;
    bytes[8] = (bytes[8] & 63) | 128;
    uuid::Uuid::from_bytes(bytes).to_string()
}
pub fn is_android(path: &Path) -> Result<bool> {
    let mut file = File::open(path).map_err(error)?;
    let mut magic = [0_u8; 2];
    file.read_exact(&mut magic).map_err(error)?;
    let mut bytes = Vec::new();
    if &magic == b"PK" {
        let mut zip = crate::backup_archive::open_validated_zip(path)?;
        zip.by_name("backup.json")
            .map_err(error)?
            .take(crate::transfer::LIMIT as u64 + 1)
            .read_to_end(&mut bytes)
            .map_err(error)?;
    } else {
        file = File::open(path).map_err(error)?;
        file.take(crate::transfer::LIMIT as u64 + 1)
            .read_to_end(&mut bytes)
            .map_err(error)?;
    }
    if bytes.len() > crate::transfer::LIMIT {
        return Err("Backup metadata exceeds 64 MB.".into());
    }
    let value: Value = serde_json::from_slice(&bytes).map_err(error)?;
    Ok(value["schema"].as_str() == Some("veritas.reader.backup.v1")
        || (value.get("schema").is_none() && value.get("documents").is_some()))
}
impl Conversion {
    pub fn read(path: &Path) -> Result<Self> {
        Ok(Self {
            backup: AndroidBackup::read(path)?,
            readings: Vec::new(),
        })
    }
    pub fn document_count(&self) -> usize {
        self.backup.document_count()
    }
    pub fn next_text(&self) -> Result<Option<AndroidTextInput>> {
        if self.readings.len() == self.document_count() {
            return Ok(None);
        }
        self.backup.text_input(self.readings.len()).map(Some)
    }
    pub fn accept(&mut self, index: usize, converted: ConvertedAndroidText) -> Result<()> {
        if index != self.readings.len() {
            return Err("Android conversion is out of order. Choose the backup again.".into());
        }
        self.readings
            .push(self.backup.convert_reading(index, converted)?);
        Ok(())
    }
    pub fn finish(self, mut settings: Settings) -> Result<Backup> {
        if self.readings.len() != self.document_count() {
            return Err("Android conversion is incomplete.".into());
        }
        let root = self.backup.metadata();
        settings.vocabulary.clear();
        let map: HashMap<_, _> = self
            .readings
            .iter()
            .map(|r| (r.android_id.as_str(), r))
            .collect();
        let reading = |id: &str| -> Result<&ConvertedReading> {
            map.get(id)
                .copied()
                .ok_or_else(|| format!("Android data refers to a missing reading: {id}"))
        };
        let mut books = Vec::new();
        let mut sources = Vec::new();
        let mut ids = HashSet::new();
        let mut warnings = Vec::new();
        for r in &self.readings {
            warnings.extend(r.warnings.iter().cloned());
            if !ids.insert(&r.book.id) {
                warnings.push("Identical Android reading text was combined; all original records remain in the retained backup.".into());
                continue;
            }
            books.push(r.book.clone());
            if let (Some(info), Some(original)) = (&r.source, &r.original) {
                let bytes = r.original_bytes()?.ok_or("Missing original.")?;
                sources.push(SourceBackup {
                    book_id: r.book.id.clone(),
                    info: info.clone(),
                    size: bytes.len() as u64,
                    sha256: hex_digest(Sha256::digest(bytes)),
                    staged: Some(original.clone()),
                });
            }
        }
        let mut annotations = Vec::new();
        let mut highlights = Vec::new();
        for row in rows(root, "annotations") {
            let r = reading(text(row, "documentId"))?;
            let position = usize::try_from(uint(row, "chunkIndex", 0)?).map_err(error)?;
            let (start, end) = r.anchor_range(position)?;
            let kind = text(row, "type");
            let id = stable_id("annotation", &format!("{}:{position}:{kind}", r.android_id));
            if kind == "HIGHLIGHT" {
                let color = match text(row, "highlightColor") {
                    "" => "yellow",
                    value => value,
                };
                let created = uint(row, "createdAt", 0)?;
                highlights.push(json!({"id":id,"bookId":r.book.id,"position":start,"endPosition":end,"color":color,"note":text(row,"note"),"createdAt":created,"updatedAt":uint(row,"updatedAt",created)?}));
            } else if ["BOOKMARK", "NOTE"].contains(&kind) {
                let note = text(row, "note");
                let body = if note.trim().is_empty() && kind == "BOOKMARK" {
                    r.book.chunks[start..=end].join(" ")
                } else {
                    note.into()
                };
                annotations.push(json!({"id":id,"bookId":r.book.id,"position":start,"text":body,"kind":if kind=="BOOKMARK" {"bookmark"} else {"note"}}));
            } else {
                return Err("Unsupported Android annotation kind.".into());
            }
            if !text(row, "audioPath").is_empty() {
                warnings.push("Annotation recordings are retained in the original Android backup; playback from book notes is still pending.".into());
            }
        }
        for row in rows(root, "documentNotes") {
            let r = reading(text(row, "documentId"))?;
            annotations.push(json!({"id":stable_id("document-note",&r.android_id),"bookId":r.book.id,"position":0,"text":text(row,"note"),"kind":"note"}));
        }
        let mut flashcards = Vec::new();
        for row in rows(root, "flashcards") {
            let id = text(row, "documentId");
            let book = if id.is_empty() {
                None
            } else {
                Some(reading(id)?.book.id.clone())
            };
            flashcards.push(json!({"id":stable_id("card",text(row,"id")),"bookId":book,"position":null,"deck":if text(row,"setName").is_empty() {"Android cards"} else {text(row,"setName")},"front":text(row,"front"),"back":text(row,"back"),"recall":text(row,"recall"),"dueAt":uint(row,"nextReviewDueTimestamp",0)?,"intervalDays":uint(row,"intervalDays",0)?,"repetitions":uint(row,"repetitionCount",0)?,"ease":row.get("easeFactor").cloned().unwrap_or(json!(2.5))}));
        }
        let mut quizzes = Vec::new();
        for row in rows(root, "quizzes") {
            let document = text(row, "documentId");
            let book = if document.is_empty() {
                None
            } else {
                Some(reading(document)?.book.id.clone())
            };
            let questions = rows(row, "questions");
            let score = row
                .get("bestScore")
                .map_or(Ok(-1), |v| v.as_i64().ok_or("Invalid Android quiz score."))?;
            let score = if score < 0 {
                None
            } else if score as usize > questions.len() {
                Some(((score.min(100) as f64 / 100.0) * questions.len() as f64).round() as usize)
            } else {
                Some(score as usize)
            };
            let quiz:crate::quizzes::Quiz=serde_json::from_value(json!({"id":stable_id("quiz",text(row,"id")),"title":text(row,"title"),"bookId":book,"questions":questions,"bestScore":score,"lastScore":null,"attempts":0,"createdAt":uint(row,"createdAtTimestamp",0)?})).map_err(error)?;
            if quiz.validate().is_ok() {
                quizzes.push(quiz);
            } else {
                warnings.push("Some Android quizzes need unsupported question formats; the originals remain in the retained backup.".into());
            }
            if questions.iter().any(|q| !rows(q, "attachments").is_empty()) {
                warnings.push("Quiz attachments are retained in the original backup; inline desktop quiz media is pending.".into());
            }
        }
        let mut notebooks = Vec::new();
        for row in rows(root, "noteNotebooks") {
            notebooks.push(json!({"id":stable_id("notebook",text(row,"id")),"name":text(row,"name"),"createdAt":uint(row,"createdAt",0)?}));
        }
        let mut notes = Vec::new();
        let mut revisions = Vec::new();
        let mut media = Vec::new();
        let sentence_reference = regex::Regex::new(r"(?i)sentence\s+(\d+)").map_err(error)?;
        for row in rows(root, "generalNotes") {
            let id = text(row, "id");
            if id.is_empty() {
                return Err("An Android note is missing its identifier.".into());
            }
            // Vocabulary remains visible as its original note as well as indexed entries.
            if let Some(document) = text(row, "title").strip_prefix("__vocab__") {
                let r = reading(document)?;
                for (index, block) in text(row, "content").split("\n\n").enumerate() {
                    let lines: Vec<_> = block
                        .lines()
                        .map(str::trim)
                        .filter(|s| !s.is_empty())
                        .collect();
                    if lines.len() < 2 {
                        continue;
                    }
                    let sentence = lines
                        .get(2)
                        .and_then(|s| {
                            sentence_reference
                                .captures(s)?
                                .get(1)?
                                .as_str()
                                .parse::<usize>()
                                .ok()
                        })
                        .and_then(|n| n.checked_sub(1));
                    let anchor = sentence
                        .map(|n| r.anchor_range(n))
                        .transpose()?
                        .map(|range| range.0);
                    settings.vocabulary.push(crate::reading_tools::Vocabulary {
                        id: stable_id("vocabulary", &format!("{id}:{index}")),
                        word: lines[0].into(),
                        definition: lines[1].into(),
                        book_id: anchor.map(|_| r.book.id.clone()),
                        position: anchor,
                    });
                }
            }
            notes.push(convert_note(row)?);
            if uint(row, "reminderAt", 0)? > 0 {
                warnings.push("Note reminder times are retained; desktop reminder notifications are still pending.".into());
            }
            // Resolve only manifest references. Never open an Android filesystem path.
            let references = note_references(row);
            for manifest in rows(root, "noteMedia") {
                let reference = text(manifest, "path");
                if !references.iter().any(|p| p == reference) {
                    continue;
                }
                let name = text(manifest, "fileName");
                let Some(staged) = self.backup.staged_asset(&format!("note_media/{name}")) else {
                    warnings.push(
                        "Some note attachments are absent; export a full ZIP from Android.".into(),
                    );
                    continue;
                };
                let bytes = staged.read(crate::note_media::LIMIT)?;
                match crate::note_media::media_type(&bytes) {
                    Ok(mime) => media.push(MediaBackup { info:crate::note_media::NoteMedia { id:stable_id("note-media",&format!("{id}:{reference}")),note_id:stable_id("note",id),name:name.into(),mime:mime.into(),size:bytes.len(),sha256:hex_digest(Sha256::digest(&bytes)),created_at:uint(row,"createdAt",uint(row,"updatedAt",0)?)? },staged:Some(staged) }),
                    Err(_) => warnings.push("Some Android media formats are retained for export but cannot be previewed on desktop yet.".into())
                }
            }
        }
        for row in rows(root, "noteRevisions") {
            let id = text(row, "noteId");
            revisions.push(json!({"id":stable_id("revision",&format!("{id}:{}",uint(row,"savedAt",0)?)),"noteId":stable_id("note",id),"note":convert_note(&row["snapshot"])?}));
        }
        let mut lists = Vec::new();
        for row in rows(root, "readingLists") {
            let mut items = rows(row, "items").to_vec();
            items.sort_by_key(|item| item["position"].as_u64().unwrap_or(u64::MAX));
            let items = items.iter().map(|item| Ok(json!({"bookId":reading(text(item,"documentId"))?.book.id,"addedAt":uint(item,"addedAt",0)?}))).collect::<Result<Vec<_>>>()?;
            let sort = match text(row, "sortMode") {
                "" | "MANUAL" => "manual",
                "TITLE_ASCENDING" => "title",
                "TITLE_DESCENDING" => "title-desc",
                "NEWEST_ADDED" => "newest",
                "OLDEST_ADDED" => "oldest",
                _ => return Err("Unknown Android reading-list order.".into()),
            };
            lists.push(json!({"id":stable_id("list",text(row,"id")),"title":text(row,"title"),"description":text(row,"description"),"createdAt":uint(row,"createdAt",0)?,"updatedAt":uint(row,"updatedAt",0)?,"archived":flag(row,"archived"),"sortMode":sort,"items":items}));
        }
        settings.queue.clear();
        settings.pronunciation_rules.clear();
        // Existing vocabulary is merged by restore_backup, not copied into the incoming backup.
        for row in rows(root, "queue") {
            let id = reading(text(row, "documentId"))?.book.id.clone();
            if !settings.queue.contains(&id) {
                settings.queue.push(id);
            }
        }
        for row in rows(root, "pronunciationRules") {
            if !["", "LITERAL"].contains(&text(row, "matchKind")) || flag(row, "caseSensitive") {
                warnings.push("Pattern and case-sensitive speech rules are retained; desktop speech-rule support is pending.".into());
                continue;
            }
            settings
                .pronunciation_rules
                .push(crate::reading_tools::PronunciationRule {
                    id: stable_id("speech-rule", text(row, "id")),
                    find: text(row, "find").into(),
                    replace_with: text(row, "replaceWith").into(),
                    enabled: row["enabled"].as_bool().unwrap_or(true),
                });
        }
        settings.last_book.clear();
        crate::android_settings::apply(root, &mut settings, &mut warnings)?;
        settings.reading_idle_seconds = u32::try_from(uint(
            &root["readingTrackingSettings"],
            "idleSeconds",
            settings.reading_idle_seconds as u64,
        )?)
        .map_err(error)?;
        // Android's system/neural voice IDs are not portable. Store an explicitly unavailable
        // preference, so restoring preferences can never silently select a different engine.
        if let Some(voice) = root.get("voiceSettings") {
            settings.voice = format!(
                "android_{}",
                hex_digest(Sha256::digest(serde_json::to_vec(voice).map_err(error)?))
            );
            settings.rate = voice
                .get("preferredRate")
                .and_then(Value::as_f64)
                .unwrap_or(1.0) as f32;
            warnings.push("The Android voice preference is unavailable here. Select a desktop voice after restoring preferences.".into());
        }
        let mut sessions = Vec::new();
        for record in rows(root, "readingSessions") {
            let book_id = map
                .get(text(record, "documentId"))
                .map(|reading| reading.book.id.clone());
            sessions.push(crate::android_history::AndroidSessionSource::convert(
                record.clone(),
                book_id,
            )?);
        }
        if !sessions.is_empty() {
            warnings.push("Imported sessions keep their recorded dates and durations. Historical sentence links are unavailable; sessions open the book at its current position.".into());
        }
        let mut opened_books =
            std::collections::HashMap::<String, crate::opened_books::OpenedBook>::new();
        for row in rows(root, "readingHistory") {
            let source_id = text(row, "documentId");
            if source_id.is_empty() || source_id.len() > 1000 {
                return Err("Invalid Android reading-history identifier.".into());
            }
            let book_id = map.get(source_id).map(|reading| reading.book.id.clone());
            let id = book_id.clone().unwrap_or_else(|| {
                hex_digest(Sha256::digest(format!("vern:android-history:{source_id}")))
            });
            let entry = crate::opened_books::OpenedBook {
                id: id.clone(),
                book_id,
                title: if text(row, "title").trim().is_empty() {
                    "Untitled reading"
                } else {
                    text(row, "title")
                }
                .into(),
                format: if text(row, "sourceLabel").is_empty() {
                    "Text"
                } else {
                    text(row, "sourceLabel")
                }
                .into(),
                opened_at: uint(row, "openedAt", 0)?,
                position: None,
                passages: 0,
                android_source: Some(row.clone()),
            };
            if opened_books
                .get(&id)
                .is_none_or(|old| old.opened_at < entry.opened_at)
            {
                opened_books.insert(id, entry);
            }
        }
        let mut opened_books: Vec<_> = opened_books.into_values().collect();
        opened_books.sort_by(|a, b| b.opened_at.cmp(&a.opened_at).then(a.id.cmp(&b.id)));
        if opened_books.len() > crate::opened_books::LIMIT {
            opened_books.truncate(crate::opened_books::LIMIT);
            warnings.push("Recent books keep the 40 newest entries; the complete original history remains in the retained Android backup.".into());
        }
        let legacy_id = |source: &str| {
            // Keep source identity stable when a missing reading is restored later.
            hex_digest(Sha256::digest(format!("vern:android-activity:{source}")))
        };
        let mut legacy_days =
            std::collections::BTreeMap::<String, crate::legacy_activity::LegacyDay>::new();
        for row in rows(root, "trackerDays") {
            let mut read_book_ids = std::collections::BTreeSet::new();
            if let Some(values) = row.get("readDocumentIds") {
                let values = values
                    .as_array()
                    .ok_or("Invalid Android recorded-book identifiers.")?;
                if values.len() > crate::legacy_activity::LIMIT {
                    return Err("Too many recorded books in an Android day.".into());
                }
                for value in values {
                    let id = value
                        .as_str()
                        .ok_or("Invalid Android recorded-book identifier.")?
                        .trim();
                    if id.is_empty() || id.len() > 1000 {
                        return Err("Invalid Android recorded-book identifier.".into());
                    }
                    read_book_ids.insert(legacy_id(id));
                }
            }
            let mut day = crate::legacy_activity::LegacyDay {
                date: text(row, "dateKey").into(),
                app_open_count: u32::try_from(uint(row, "appOpenCount", 0)?).map_err(error)?,
                usage_ms: uint(row, "usageMillis", 0)?,
                reading_ms: uint(row, "readingMillis", 0)?,
                read_book_ids: read_book_ids.into_iter().collect(),
            };
            day.validate()?;
            if let Some(old) = legacy_days.get(&day.date) {
                day.app_open_count = day.app_open_count.max(old.app_open_count);
                day.usage_ms = day.usage_ms.max(old.usage_ms);
                day.reading_ms = day.reading_ms.max(old.reading_ms);
                day.read_book_ids = day
                    .read_book_ids
                    .into_iter()
                    .chain(old.read_book_ids.iter().cloned())
                    .collect::<std::collections::BTreeSet<_>>()
                    .into_iter()
                    .collect();
            }
            legacy_days.insert(day.date.clone(), day);
        }
        let legacy_days: Vec<_> = legacy_days.into_values().collect();
        let mut legacy_book_times =
            HashMap::<String, crate::legacy_activity::LegacyBookTime>::new();
        if let Some(totals) = root.get("docReadingTimesThisMonth") {
            let totals = totals
                .as_object()
                .ok_or("Invalid Android book-time totals.")?;
            if totals.len() > crate::legacy_activity::LIMIT {
                return Err("Too many Android legacy book totals.".into());
            }
            for (source, value) in totals {
                if source.trim().is_empty() || source.len() > 1000 {
                    return Err("Invalid Android book-time identifier.".into());
                }
                let milliseconds = value
                    .as_u64()
                    .ok_or("Invalid Android legacy book duration.")?;
                if milliseconds > crate::legacy_activity::MAX_MS {
                    return Err("Android legacy book time exceeds the desktop limit.".into());
                }
                let id = legacy_id(source);
                let reading = map.get(source.as_str());
                let item = crate::legacy_activity::LegacyBookTime {
                    id: id.clone(),
                    book_id: reading.map(|r| r.book.id.clone()),
                    title: reading
                        .map_or("Removed reading", |r| r.book.title.as_str())
                        .into(),
                    milliseconds,
                    snapshot_at: uint(root, "createdAt", 0)?,
                };
                if legacy_book_times
                    .get(&id)
                    .is_none_or(|old| old.milliseconds < item.milliseconds)
                {
                    legacy_book_times.insert(id, item);
                }
            }
        }
        let mut legacy_book_times: Vec<_> = legacy_book_times.into_values().collect();
        legacy_book_times.sort_by(|a, b| a.id.cmp(&b.id));
        if !legacy_days.is_empty() {
            warnings.push("Legacy day totals are separate from measured sessions. Recorded reading days without a duration keep their streak marker; app use alone adds no reading time.".into());
        }
        if !legacy_book_times.is_empty() {
            warnings.push("Older Android per-book totals do not record their month. They remain legacy totals outside dated activity and session totals.".into());
        }
        for (key, label) in [
            ("aiPromptTemplates", "AI templates"),
            ("aiPromptHistory", "AI history"),
        ] {
            let count = rows(root, key).len();
            if count > 0 {
                warnings.push(format!("{label}: {count} records retained in the original Android backup; desktop conversion is pending."));
            }
        }
        if root.get("readerSettings").is_some()
            || root.get("notesSettings").is_some()
            || root.get("narrationSettings").is_some()
        {
            warnings.push("Android theme packs, decorative/phone controls, note rich-preview/citation options and narration preferences remain in the retained backup; their desktop conversion is incomplete.".into());
        }
        warnings.sort();
        warnings.dedup();
        if warnings.len() > 40 {
            warnings.truncate(39);
            warnings
                .push("Additional source details remain in the retained Android backup.".into());
        }
        let mut backup: Backup = serde_json::from_value(json!({"schema":FULL_SCHEMA,"books":books,"annotations":annotations,"highlights":highlights,"flashcards":flashcards,"quizzes":quizzes,"notes":notes,"notebooks":notebooks,"noteRevisions":revisions,"settings":settings,"readingLists":lists,"sessions":sessions,"openedBooks":opened_books,"legacyDays":legacy_days,"legacyBookTimes":legacy_book_times})).map_err(error)?;
        backup.sources = sources;
        backup.note_media = media;
        backup.android_imports.push(self.backup.retained_import()?);
        backup.origin = "Android".into();
        backup.warnings = warnings;
        backup.validate()?;
        Ok(backup)
    }
}
fn convert_note(row: &Value) -> Result<Value> {
    let id = text(row, "id");
    if id.is_empty() {
        return Err("An Android note is missing its identifier.".into());
    }
    let mut labels = rows_strings(row, "labelIds");
    let legacy = text(row, "notebookId");
    if !legacy.is_empty() && !labels.iter().any(|id| id == legacy) {
        labels.push(legacy.into());
    }
    let updated = uint(row, "updatedAt", 0)?;
    let deleted = uint(row, "deletedAt", 0)?;
    let color = match text(row, "color") {
        "" => "paper",
        "green" => "sage",
        "pink" => "rose",
        "yellow" => "amber",
        value if ["paper", "sage", "rose", "blue", "amber"].contains(&value) => value,
        _ => "paper",
    };
    Ok(
        json!({"id":stable_id("note",id),"title":text(row,"title"),"content":text(row,"content"),"color":color,"pinned":flag(row,"pinned"),"isChecklist":flag(row,"isChecklist"),"labelIds":labels.iter().map(|id|stable_id("notebook",id)).collect::<Vec<_>>(),"createdAt":uint(row,"createdAt",updated)?,"updatedAt":updated,"deletedAt":if deleted==0 {None} else {Some(deleted)}}),
    )
}
fn rows_strings(row: &Value, key: &str) -> Vec<String> {
    row.get(key)
        .and_then(Value::as_array)
        .map(|items| {
            items
                .iter()
                .filter_map(Value::as_str)
                .map(str::to_string)
                .collect()
        })
        .unwrap_or_default()
}
fn note_references(row: &Value) -> Vec<String> {
    let mut refs = rows_strings(row, "audioUrls");
    for field in ["audioUrl", "imageUrl"] {
        let value = text(row, field);
        if !value.is_empty() {
            refs.push(value.into());
        }
    }
    // Inline Android blocks are plain data. Extract exact paths, never dereference them.
    if let Ok(pattern) = regex::Regex::new(
        r"(?i)!?\[(?:[^\]]*)\]\(([^)\r\n]+)\)|\[(?:image|photo|audio|video|file):([^\]\r\n]+)\]",
    ) {
        refs.extend(pattern.captures_iter(text(row, "content")).filter_map(|c| {
            c.get(1)
                .or_else(|| c.get(2))
                .map(|s| s.as_str().trim().to_string())
        }));
    }
    refs
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{backup_archive::StagedFile, storage::Storage};
    use std::io::{Cursor, Write};
    fn fixture() -> (std::sync::Arc<StagedFile>, Value, Vec<u8>) {
        let image = include_bytes!("../../public/covers/classic_alice_wonderland.jpg").to_vec();
        let root = json!({"schema":"veritas.reader.backup.v1","createdAt":1000,"documents":[{"id":"android-book","title":"Android reading","text":"First. Second.","currentIndex":1,"chunkCount":2,"sentenceIndexVersion":2,"originalFileName":"reading.txt"}],
            "annotations":[{"documentId":"android-book","chunkIndex":1,"type":"BOOKMARK","note":""},{"documentId":"android-book","chunkIndex":0,"type":"HIGHLIGHT","highlightColor":"#ffe082","createdAt":10,"updatedAt":11}],
            "queue":[{"documentId":"android-book"}],"documentNotes":[{"documentId":"android-book","note":"Whole book note"}],
            "readingHistory":[{"documentId":"android-book","title":"Earlier title","sourceLabel":"TXT","currentIndex":1,"chunkCount":2,"openedAt":100},{"documentId":"deleted-android","title":"Removed reading","sourceLabel":"EPUB","currentIndex":4,"chunkCount":10,"openedAt":50}],
            "generalNotes":[{"id":"note-a","title":"Picture","content":"A note","createdAt":10,"updatedAt":20,"imageUrl":"/android/picture.jpg","labelIds":["notebook-a"],"reminderAt":30}],
            "noteNotebooks":[{"id":"notebook-a","name":"Ideas","createdAt":10}],
            "noteRevisions":[{"noteId":"note-a","savedAt":15,"snapshot":{"id":"note-a","title":"Earlier","content":"Earlier body","createdAt":10,"updatedAt":15}}],
            "noteMedia":[{"path":"/android/picture.jpg","fileName":"0_picture.jpg","size":image.len(),"sha256":hex_digest(Sha256::digest(&image))}],
            "readingLists":[{"id":"list-a","title":"Read next","createdAt":10,"updatedAt":20,"sortMode":"MANUAL","items":[{"documentId":"android-book","position":0,"addedAt":12}]}],
            "flashcards":[{"id":"card-a","documentId":"android-book","front":"Front","back":"Back","setName":"Deck","recall":"good","nextReviewDueTimestamp":12345,"intervalDays":7,"repetitionCount":3,"easeFactor":2.4}],
            "quizzes":[{"id":"quiz-a","title":"Quiz","documentId":"android-book","createdAtTimestamp":10,"bestScore":1,"questions":[{"question":"One?","answer":"Yes","options":["Yes","No"]}]}],
            "voiceSettings":{"voiceName":"android-only","preferredRate":1.1},
            "readerSettings":{"fontSizeSp":24,"themeId":"dracula_system","uiFontId":"gazette","paperToneMode":"warm_sepia","readingLayout":"scroll","reduceMotion":true},
            "notesSettings":{"paperTemplate":"DOTS","newNoteIsChecklist":true,"defaultSortOrder":"title"},"askAiSettings":{"assistantId":"claude","promptTemplate":"Keep this source template"},
            "trackerDays":[{"dateKey":"2023-11-14","appOpenCount":2,"usageMillis":90000,"readingMillis":60000,"readDocumentIds":["android-book"]},{"dateKey":"2023-11-13","appOpenCount":1,"usageMillis":5000,"readingMillis":0,"readDocumentIds":["deleted-android"]}],
            "docReadingTimesThisMonth":{"android-book":300000,"deleted-android":120000},
            "readingSessions":[{"id":"retained-session","documentId":"android-book","title":"Android reading","mode":"READING","startedAt":1700000000000_u64,"checkpointAt":1700000000007_u64,"startLocation":0,"endLocation":1,"revision":"cache.txt:1700000000000:14:2","spans":[{"startedAt":1700000000000_u64,"zone":"America/New_York","durationMillis":7,"location":0}]}],"aiPromptTemplates":[{"id":"template","text":"Preserve exactly"}]});
        let lease = StagedFile::stage(Cursor::new([]), 0).unwrap();
        let mut zip = zip::ZipWriter::new(File::create(lease.path()).unwrap());
        let options = zip::write::SimpleFileOptions::default();
        for (name, bytes) in [
            ("backup.json", serde_json::to_vec(&root).unwrap()),
            ("originals/reading.txt", b"First. Second.".to_vec()),
            ("note_media/0_picture.jpg", image.clone()),
        ] {
            zip.start_file(name, options).unwrap();
            zip.write_all(&bytes).unwrap();
        }
        zip.finish().unwrap();
        (lease, root, image)
    }
    fn convert(path: &Path) -> Backup {
        let mut conversion = Conversion::read(path).unwrap();
        assert_eq!(
            conversion.next_text().unwrap().unwrap().text,
            "First. Second."
        );
        conversion
            .accept(
                0,
                ConvertedAndroidText {
                    chunks: vec!["First.".into(), "Second.".into()],
                    source_to_chunk: vec![0, 1],
                    source_pages: vec![1, 1],
                    page_starts: vec![0],
                    source_count: 2,
                    warnings: vec![],
                    cover: None,
                },
            )
            .unwrap();
        assert!(conversion.next_text().unwrap().is_none());
        conversion.finish(Settings::default()).unwrap()
    }
    #[test]
    fn android_merge_reopen_and_full_archive_preserve_data_and_original_export() {
        let (input, metadata, image) = fixture();
        assert!(is_android(input.path()).unwrap());
        let backup = convert(input.path());
        let preview = backup.preview("test".into());
        assert_eq!(
            (
                preview.books,
                preview.annotations,
                preview.highlights,
                preview.flashcards,
                preview.quizzes,
                preview.notes,
                preview.note_media,
                preview.retained_android_backups
            ),
            (1, 2, 1, 1, 1, 1, 1, 1)
        );
        assert!(preview
            .warnings
            .iter()
            .any(|s| s.contains("Historical sentence links")));
        assert_eq!(preview.sessions, 1);
        assert_eq!(preview.opened_books, 2);
        assert_eq!((preview.legacy_days, preview.legacy_book_times), (2, 2));
        let database = StagedFile::stage(Cursor::new([]), 0).unwrap();
        let mut db = Storage::open(database.path()).unwrap();
        db.restore_backup(backup, true).unwrap();
        let imported = db.settings().unwrap();
        assert_eq!(
            (imported.font_size, &*imported.color_theme, &*imported.theme),
            (24, "dracula", "system")
        );
        assert_eq!(
            (
                &*imported.reader_font,
                &*imported.reader_layout,
                &*imported.paper_tone
            ),
            ("Bitter", "scroll", "sepia")
        );
        assert_eq!(
            (
                &*imported.notes.paper,
                &*imported.notes.sort,
                &*imported.ai_assistant
            ),
            ("dots", "title", "claude")
        );
        assert!(imported.reduce_motion && imported.notes.new_checklist);
        let book = db.books().unwrap().remove(0);
        assert_eq!(book.position, 1);
        assert_eq!(db.flashcards().unwrap()[0].due_at, 12345);
        let quiz = db.quizzes().unwrap().remove(0);
        assert_eq!(
            (quiz.best_score, quiz.last_score, quiz.attempts),
            (Some(1), None, 0)
        );
        let voice = db.settings().unwrap().voice;
        assert!(voice.starts_with("android_"));
        assert!(crate::voices::descriptor(&voice).is_err());
        let note_id = stable_id("note", "note-a");
        assert_eq!(
            db.note_media_bytes(&db.note_media(&note_id).unwrap()[0].id)
                .unwrap(),
            image
        );
        assert_eq!(
            db.note_revisions(&note_id).unwrap()[0].note.content,
            "Earlier body"
        );
        db.progress(&book.id, 0).unwrap();
        let mut local = db.settings().unwrap();
        local.font_size = 27;
        local.notes.paper = "grid".into();
        local.ai_assistant = "copilot".into();
        db.save_settings(local).unwrap();
        db.restore_backup(convert(input.path()), false).unwrap();
        let kept = db.settings().unwrap();
        assert_eq!(
            (kept.font_size, &*kept.notes.paper, &*kept.ai_assistant),
            (27, "grid", "copilot")
        );
        assert_eq!(db.books().unwrap().len(), 1);
        assert_eq!(db.book(&book.id).unwrap().position, 0);
        assert_eq!(db.android_imports().unwrap().len(), 1);
        drop(db);
        let db = Storage::open(database.path()).unwrap();
        let archive = StagedFile::stage(Cursor::new([]), 0).unwrap();
        crate::backup_archive::write(&db, archive.path()).unwrap();
        let incoming = crate::transfer::read_backup(archive.path()).unwrap();
        let mut destination = Storage::open(Path::new(":memory:")).unwrap();
        destination.restore_backup(incoming, true).unwrap();
        assert_eq!(destination.android_imports().unwrap()[0].metadata, metadata);
        let original = StagedFile::stage(Cursor::new([]), 0).unwrap();
        destination
            .export_android_import(
                &destination.android_imports().unwrap()[0].id,
                original.path(),
            )
            .unwrap();
        let restored = AndroidBackup::read(original.path()).unwrap();
        assert_eq!(restored.metadata(), &metadata);
        assert_eq!(
            restored
                .asset_bytes("note_media/0_picture.jpg", crate::note_media::LIMIT)
                .unwrap()
                .unwrap(),
            image
        );
        assert_eq!(
            restored
                .asset_bytes("originals/reading.txt", 32 * 1024 * 1024)
                .unwrap()
                .unwrap(),
            b"First. Second."
        );
        let retained_id = destination.android_import_summaries().unwrap()[0]
            .id
            .clone();
        destination.delete_android_import(&retained_id).unwrap();
        assert!(destination.android_import_summaries().unwrap().is_empty());
        let legacy = destination.legacy_days().unwrap();
        assert_eq!(legacy.len(), 2);
        assert_eq!(legacy.iter().map(|d| d.reading_ms).sum::<u64>(), 60000);
        let legacy_books = destination.legacy_book_times().unwrap();
        assert_eq!(legacy_books.len(), 2);
        assert!(legacy_books.iter().any(|b| b.book_id.is_none()));
        assert!(legacy_books.iter().all(|b| b.snapshot_at == 1000));
        let report = destination.activity_report().unwrap();
        let day = report.days.iter().find(|d| d.date == "2023-11-14").unwrap();
        assert_eq!(
            (day.reading_ms, day.legacy_ms, day.app_usage_ms),
            (7, 60000, 90000)
        );
        assert_eq!(destination.books().unwrap().len(), 1);
        let recent = destination.opened_books().unwrap();
        assert_eq!(recent.len(), 2);
        assert!(recent.iter().any(|r| r.book_id.is_none()));
        assert!(recent
            .iter()
            .all(|r| r.position.is_none() && r.android_source.is_some()));
        let history = destination.sessions().unwrap().remove(0);
        assert_eq!(history.measured_ms, 7);
        assert_eq!(history.end_position, None);
        assert_eq!(
            history.android_source.unwrap().record["revision"],
            "cache.txt:1700000000000:14:2"
        );
        assert_eq!(destination.note_media(&note_id).unwrap().len(), 1);
        assert_eq!(
            destination.source_file(&book.id).unwrap().unwrap().bytes,
            b"First. Second."
        );
    }
    #[test]
    fn legacy_missing_reading_reconnects_without_duplicate_markers_or_totals() {
        fn convert_root(root: &Value, has_book: bool) -> Backup {
            let file = StagedFile::stage(
                Cursor::new(serde_json::to_vec(root).unwrap()),
                crate::transfer::LIMIT as u64,
            )
            .unwrap();
            let mut conversion = Conversion::read(file.path()).unwrap();
            if has_book {
                conversion
                    .accept(
                        0,
                        ConvertedAndroidText {
                            chunks: vec!["A sentence.".into()],
                            source_to_chunk: vec![0],
                            source_pages: vec![1],
                            page_starts: vec![0],
                            source_count: 1,
                            warnings: vec![],
                            cover: None,
                        },
                    )
                    .unwrap();
            }
            conversion.finish(Settings::default()).unwrap()
        }
        let mut root = json!({"schema":"veritas.reader.backup.v1","createdAt":1000,"documents":[],"trackerDays":[{"dateKey":"2025-01-01","readDocumentIds":["later"]}],"docReadingTimesThisMonth":{"later":10000}});
        let mut db = Storage::open(Path::new(":memory:")).unwrap();
        db.restore_backup(convert_root(&root, false), false)
            .unwrap();
        assert_eq!(db.legacy_book_times().unwrap()[0].book_id, None);
        root["documents"] = json!([{"id":"later","title":"Restored reading","text":"A sentence.","chunkCount":1,"currentIndex":0,"sentenceIndexVersion":2}]);
        root["createdAt"] = json!(2000);
        root["docReadingTimesThisMonth"]["later"] = json!(5000);
        db.restore_backup(convert_root(&root, true), false).unwrap();
        assert_eq!(db.legacy_days().unwrap()[0].read_book_ids.len(), 1);
        let totals = db.legacy_book_times().unwrap();
        assert_eq!(totals.len(), 1);
        assert_eq!(totals[0].milliseconds, 10000);
        assert_eq!(totals[0].snapshot_at, 1000);
        assert_eq!(totals[0].book_id, Some(db.books().unwrap()[0].id.clone()));
    }
    #[test]
    fn valid_android_small_text_preferences_do_not_prevent_library_restore() {
        let root = json!({"schema":"veritas.reader.backup.v1","documents":[{"id":"book","title":"Reading","text":"A sentence.","chunkCount":1,"currentIndex":0,"sentenceIndexVersion":2}],"readerSettings":{"fontSizeSp":10}});
        let input = StagedFile::stage(
            Cursor::new(serde_json::to_vec(&root).unwrap()),
            crate::transfer::LIMIT as u64,
        )
        .unwrap();
        let mut conversion = Conversion::read(input.path()).unwrap();
        conversion
            .accept(
                0,
                ConvertedAndroidText {
                    chunks: vec!["A sentence.".into()],
                    source_to_chunk: vec![0],
                    source_pages: vec![1],
                    page_starts: vec![0],
                    source_count: 1,
                    warnings: vec![],
                    cover: None,
                },
            )
            .unwrap();
        let settings = Settings {
            font_size: 23,
            ..Settings::default()
        };
        let backup = conversion.finish(settings).unwrap();
        assert!(backup
            .preview("test".into())
            .warnings
            .iter()
            .any(|s| s.contains("outside the desktop range")));
        let mut db = Storage::open(Path::new(":memory:")).unwrap();
        db.restore_backup(backup, true).unwrap();
        assert_eq!(db.books().unwrap().len(), 1);
        assert_eq!(db.settings().unwrap().font_size, 23);
        assert_eq!(
            db.android_imports().unwrap()[0].metadata["readerSettings"]["fontSizeSp"],
            10
        );
    }
    #[test]
    fn incomplete_out_of_order_and_tampered_conversions_do_not_merge() {
        let (input, _, _) = fixture();
        assert!(Conversion::read(input.path())
            .unwrap()
            .finish(Settings::default())
            .is_err());
        let mut ordered = Conversion::read(input.path()).unwrap();
        assert!(ordered
            .accept(
                1,
                ConvertedAndroidText {
                    chunks: vec![],
                    source_to_chunk: vec![],
                    source_pages: vec![],
                    page_starts: vec![],
                    source_count: 0,
                    warnings: vec![],
                    cover: None
                }
            )
            .is_err());
        assert!(ordered.next_text().unwrap().is_some());
        let mut converted = convert(input.path());
        let asset = &converted.android_imports[0].assets[0];
        std::fs::write(asset.staged.as_ref().unwrap().path(), b"tampered").unwrap();
        assert!(converted.validate().is_err());
        converted.android_imports.clear(); // source has its own retained staged Arc, still validates integrity independently.
        assert_eq!(
            stable_id("note", "legacy-id"),
            stable_id("note", "legacy-id")
        );
    }
}
