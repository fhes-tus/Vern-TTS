//! Older Android totals are aggregates, not fabricated timed sessions.
use crate::storage::{error, Result, Storage};
use chrono::NaiveDate;
use rusqlite::{params, OptionalExtension};
use serde::{Deserialize, Serialize};
use std::collections::BTreeSet;

pub(crate) const LIMIT: usize = 20_000;
pub(crate) const MAX_MS: u64 = 86_400_000 * 3660;
fn identity(id: &str) -> bool {
    id.len() == 64
        && id
            .bytes()
            .all(|c| c.is_ascii_digit() || (b'a'..=b'f').contains(&c))
}
#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LegacyDay {
    pub date: String,
    pub app_open_count: u32,
    pub usage_ms: u64,
    pub reading_ms: u64,
    pub read_book_ids: Vec<String>,
}
impl LegacyDay {
    pub(crate) fn validate(&self) -> Result<()> {
        if self.date.len() != 10
            || NaiveDate::parse_from_str(&self.date, "%Y-%m-%d").is_err()
            || self.app_open_count > i32::MAX as u32
            || self.usage_ms > MAX_MS
            || self.reading_ms > MAX_MS
            || self.read_book_ids.len() > LIMIT
            || self.read_book_ids.iter().any(|id| !identity(id))
            || self.read_book_ids.iter().collect::<BTreeSet<_>>().len() != self.read_book_ids.len()
        {
            return Err("Invalid legacy daily activity.".into());
        }
        Ok(())
    }
    pub(crate) fn summary(&self) -> LegacyDaySummary {
        LegacyDaySummary {
            date: self.date.clone(),
            app_open_count: self.app_open_count,
            usage_ms: self.usage_ms,
            reading_ms: self.reading_ms,
            recorded_books: self.read_book_ids.len(),
        }
    }
}
#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LegacyDaySummary {
    pub date: String,
    pub app_open_count: u32,
    pub usage_ms: u64,
    pub reading_ms: u64,
    pub recorded_books: usize,
}
#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LegacyBookTime {
    pub id: String,
    pub book_id: Option<String>,
    pub title: String,
    pub milliseconds: u64,
    pub snapshot_at: u64,
}
impl LegacyBookTime {
    pub(crate) fn validate(&self, db: &Storage) -> Result<()> {
        if !identity(&self.id)
            || self.title.trim().is_empty()
            || self.title.chars().count() > 1000
            || self.milliseconds > MAX_MS
            || self.snapshot_at > 8_640_000_000_000_000
        {
            return Err("Invalid legacy book time.".into());
        }
        if let Some(id) = &self.book_id {
            let exists: bool = db
                .connection
                .query_row(
                    "SELECT EXISTS(SELECT 1 FROM books WHERE id=?1)",
                    [id],
                    |r| r.get(0),
                )
                .map_err(error)?;
            if !exists {
                return Err("The legacy time does not belong to this reading.".into());
            }
        }
        Ok(())
    }
}
impl Storage {
    pub(crate) fn legacy_days(&self) -> Result<Vec<LegacyDay>> {
        let mut query = self
            .connection
            .prepare("SELECT value FROM legacy_activity_days ORDER BY date DESC")
            .map_err(error)?;
        let rows = query
            .query_map([], |r| r.get::<_, String>(0))
            .map_err(error)?;
        rows.map(|row| serde_json::from_str(&row.map_err(error)?).map_err(error))
            .collect()
    }
    pub(crate) fn restore_legacy_day(&self, day: &LegacyDay) -> Result<()> {
        day.validate()?;
        let old: Option<String> = self
            .connection
            .query_row(
                "SELECT value FROM legacy_activity_days WHERE date=?1",
                [&day.date],
                |r| r.get(0),
            )
            .optional()
            .map_err(error)?;
        let mut merged = day.clone();
        if let Some(value) = old {
            let old: LegacyDay = serde_json::from_str(&value).map_err(error)?;
            merged.app_open_count = merged.app_open_count.max(old.app_open_count);
            merged.usage_ms = merged.usage_ms.max(old.usage_ms);
            merged.reading_ms = merged.reading_ms.max(old.reading_ms);
            merged.read_book_ids = merged
                .read_book_ids
                .into_iter()
                .chain(old.read_book_ids)
                .collect::<BTreeSet<_>>()
                .into_iter()
                .collect();
        } else {
            let count: usize = self
                .connection
                .query_row("SELECT count(*) FROM legacy_activity_days", [], |r| {
                    crate::storage::read_unsigned(r, 0)
                })
                .map_err(error)?;
            if count >= LIMIT {
                return Err("Legacy activity supports up to 20,000 dates.".into());
            }
        }
        merged.validate()?;
        self.connection.execute("INSERT INTO legacy_activity_days(date,value) VALUES(?1,?2) ON CONFLICT(date) DO UPDATE SET value=excluded.value",params![merged.date,serde_json::to_string(&merged).map_err(error)?]).map_err(error)?;
        Ok(())
    }
    pub(crate) fn legacy_book_times(&self) -> Result<Vec<LegacyBookTime>> {
        let mut query=self.connection.prepare("SELECT l.value,l.book_id,b.title FROM legacy_book_times l LEFT JOIN books b ON b.id=l.book_id ORDER BY json_extract(l.value,'$.milliseconds') DESC,l.id").map_err(error)?;
        let rows = query
            .query_map([], |r| {
                Ok((
                    r.get::<_, String>(0)?,
                    r.get::<_, Option<String>>(1)?,
                    r.get::<_, Option<String>>(2)?,
                ))
            })
            .map_err(error)?;
        rows.map(|row| {
            let (value, book_id, title) = row.map_err(error)?;
            let mut item: LegacyBookTime = serde_json::from_str(&value).map_err(error)?;
            item.book_id = book_id;
            if let Some(title) = title {
                item.title = title;
            }
            Ok(item)
        })
        .collect()
    }
    pub(crate) fn restore_legacy_book_time(&self, item: &LegacyBookTime) -> Result<()> {
        item.validate(self)?;
        let old: Option<(String, Option<String>)> = self
            .connection
            .query_row(
                "SELECT value,book_id FROM legacy_book_times WHERE id=?1",
                [&item.id],
                |r| Ok((r.get(0)?, r.get(1)?)),
            )
            .optional()
            .map_err(error)?;
        let mut merged = item.clone();
        if let Some((value, book_id)) = old {
            let old: LegacyBookTime = serde_json::from_str(&value).map_err(error)?;
            if old.milliseconds > merged.milliseconds
                || (old.milliseconds == merged.milliseconds && old.snapshot_at > merged.snapshot_at)
            {
                merged = LegacyBookTime {
                    book_id: book_id.or_else(|| item.book_id.clone()),
                    ..old
                };
            } else if merged.book_id.is_none() {
                merged.book_id = book_id;
            }
            if item.book_id.is_some() {
                merged.book_id.clone_from(&item.book_id);
            }
        } else {
            let count: usize = self
                .connection
                .query_row("SELECT count(*) FROM legacy_book_times", [], |r| {
                    crate::storage::read_unsigned(r, 0)
                })
                .map_err(error)?;
            if count >= LIMIT {
                return Err("Legacy activity supports up to 20,000 book totals.".into());
            }
        }
        merged.validate(self)?;
        self.connection.execute("INSERT INTO legacy_book_times(id,book_id,value) VALUES(?1,?2,?3) ON CONFLICT(id) DO UPDATE SET book_id=excluded.book_id,value=excluded.value",params![merged.id,merged.book_id,serde_json::to_string(&merged).map_err(error)?]).map_err(error)?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{
        backup_archive::StagedFile,
        storage::{now, Book},
    };
    use std::{io::Cursor, path::Path};
    fn book(db: &mut Storage) -> Book {
        db.save_book(Book {
            id: String::new(),
            title: "Legacy reading".into(),
            author: String::new(),
            format: "TXT".into(),
            chunks: vec!["First passage.".into(), "Next passage.".into()],
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
    fn merges_use_maximums_and_union_and_survive_reopen_backup_and_book_deletion() {
        let lease = StagedFile::stage(Cursor::new([]), 0).unwrap();
        let mut db = Storage::open(lease.path()).unwrap();
        let book = book(&mut db);
        db.progress(&book.id, 1).unwrap();
        let day = LegacyDay {
            date: "2024-02-29".into(),
            app_open_count: 1,
            usage_ms: 3000,
            reading_ms: 1000,
            read_book_ids: vec![book.id.clone()],
        };
        db.restore_legacy_day(&day).unwrap();
        let incoming = LegacyDay {
            app_open_count: 2,
            usage_ms: 2000,
            reading_ms: 2000,
            read_book_ids: vec!["b".repeat(64)],
            ..day.clone()
        };
        db.restore_legacy_day(&incoming).unwrap();
        db.restore_legacy_day(&incoming).unwrap();
        let saved = db.legacy_days().unwrap().remove(0);
        assert_eq!(
            (
                saved.app_open_count,
                saved.usage_ms,
                saved.reading_ms,
                saved.read_book_ids.len()
            ),
            (2, 3000, 2000, 2)
        );
        let total = LegacyBookTime {
            id: book.id.clone(),
            book_id: Some(book.id.clone()),
            title: book.title.clone(),
            milliseconds: 10000,
            snapshot_at: 1000,
        };
        db.restore_legacy_book_time(&total).unwrap();
        db.restore_legacy_book_time(&LegacyBookTime {
            milliseconds: 2000,
            snapshot_at: 2000,
            ..total.clone()
        })
        .unwrap();
        assert_eq!(db.legacy_book_times().unwrap()[0].milliseconds, 10000);
        assert_eq!(db.book(&book.id).unwrap().position, 1);
        drop(db);
        let mut db = Storage::open(lease.path()).unwrap();
        let json = StagedFile::stage(
            Cursor::new(db.backup_bytes().unwrap()),
            crate::transfer::LIMIT as u64,
        )
        .unwrap();
        let mut restored = Storage::open(Path::new(":memory:")).unwrap();
        restored
            .restore_backup(crate::transfer::read_backup(json.path()).unwrap(), false)
            .unwrap();
        assert_eq!(restored.legacy_days().unwrap()[0].read_book_ids.len(), 2);
        assert_eq!(restored.legacy_book_times().unwrap()[0].milliseconds, 10000);
        db.delete_book(&book.id).unwrap();
        assert_eq!(db.legacy_book_times().unwrap()[0].book_id, None);
        assert_eq!(db.legacy_days().unwrap()[0].read_book_ids.len(), 2);
        let zip = StagedFile::stage(Cursor::new([]), 0).unwrap();
        crate::backup_archive::write(&db, zip.path()).unwrap();
        let mut final_db = Storage::open(Path::new(":memory:")).unwrap();
        final_db
            .restore_backup(crate::transfer::read_backup(zip.path()).unwrap(), true)
            .unwrap();
        assert_eq!(final_db.legacy_book_times().unwrap()[0].book_id, None);
        assert_eq!(final_db.legacy_book_times().unwrap()[0].snapshot_at, 1000);
        assert_eq!(final_db.legacy_days().unwrap()[0].reading_ms, 2000);
    }
    #[test]
    fn unknown_duration_markers_keep_streaks_and_app_usage_and_book_totals_add_no_minutes() {
        let mut db = Storage::open(Path::new(":memory:")).unwrap();
        let today = chrono::Local::now().date_naive();
        let yesterday = today.pred_opt().unwrap();
        let older = yesterday.pred_opt().unwrap();
        let entry = LegacyDay {
            date: today.to_string(),
            app_open_count: 1,
            usage_ms: 600000,
            reading_ms: 0,
            read_book_ids: vec![],
        };
        db.restore_legacy_day(&entry).unwrap();
        db.restore_legacy_day(&LegacyDay {
            date: yesterday.to_string(),
            read_book_ids: vec!["a".repeat(64)],
            ..entry.clone()
        })
        .unwrap();
        db.restore_legacy_day(&LegacyDay {
            date: older.to_string(),
            reading_ms: 60000,
            ..entry
        })
        .unwrap();
        db.restore_legacy_book_time(&LegacyBookTime {
            id: "a".repeat(64),
            book_id: None,
            title: "Removed reading".into(),
            milliseconds: 900000,
            snapshot_at: 0,
        })
        .unwrap();
        let report = db.activity_report().unwrap();
        assert_eq!(
            (
                report.current_streak,
                report.best_streak,
                report.total_sessions
            ),
            (2, 2, 0)
        );
        assert!(report.books.is_empty());
        let day = report
            .days
            .iter()
            .find(|d| d.date == today.to_string())
            .unwrap();
        assert_eq!(
            (
                day.reading_ms,
                day.listening_ms,
                day.legacy_ms,
                day.app_usage_ms
            ),
            (0, 0, 0, 600000)
        );
        let session = crate::activity::Session {
            id: uuid::Uuid::new_v4().to_string(),
            book_id: None,
            title: "Measured reading".into(),
            mode: crate::activity::Mode::Reading,
            started_at: now() - 60000,
            updated_at: now(),
            start_position: None,
            end_position: None,
            measured_ms: 30000,
            corrected_ms: None,
            excluded: false,
            manual: false,
            days: vec![crate::activity::DayPart {
                date: older.to_string(),
                milliseconds: 30000,
            }],
            android_source: None,
        };
        db.restore_session(&session).unwrap();
        let report = db.activity_report().unwrap();
        let day = report
            .days
            .iter()
            .find(|d| d.date == older.to_string())
            .unwrap();
        assert_eq!((day.reading_ms, day.legacy_ms), (30000, 60000));
        assert_eq!(
            (report.total_sessions, report.books[0].reading_ms),
            (1, 30000)
        );
        db.correct_session(&session.id, None, true).unwrap();
        let report = db.activity_report().unwrap();
        assert_eq!(
            report
                .days
                .iter()
                .find(|d| d.date == older.to_string())
                .unwrap()
                .legacy_ms,
            60000
        );
        assert_eq!(report.current_streak, 2);
    }
    #[test]
    fn malformed_data_is_rejected_before_restore_and_schema15_migrates_without_losing_books() {
        let lease = StagedFile::stage(Cursor::new([]), 0).unwrap();
        let mut db = Storage::open(lease.path()).unwrap();
        let reading = book(&mut db);
        let good = LegacyDay {
            date: "2025-01-01".into(),
            app_open_count: 1,
            usage_ms: 0,
            reading_ms: 60000,
            read_book_ids: vec![reading.id.clone()],
        };
        for bad in [
            LegacyDay {
                date: "2025-02-30".into(),
                ..good.clone()
            },
            LegacyDay {
                app_open_count: u32::MAX,
                ..good.clone()
            },
            LegacyDay {
                reading_ms: MAX_MS + 1,
                ..good.clone()
            },
            LegacyDay {
                read_book_ids: vec![reading.id.clone(), reading.id.clone()],
                ..good.clone()
            },
        ] {
            assert!(db.restore_legacy_day(&bad).is_err());
        }
        assert!(db.legacy_days().unwrap().is_empty());
        let mut snapshot: serde_json::Value =
            serde_json::from_slice(&db.backup_bytes().unwrap()).unwrap();
        snapshot["legacyDays"] = serde_json::json!([good,{"date":"bad","appOpenCount":0,"usageMs":0,"readingMs":0,"readBookIds":[]}]);
        let bad = StagedFile::stage(
            Cursor::new(serde_json::to_vec(&snapshot).unwrap()),
            crate::transfer::LIMIT as u64,
        )
        .unwrap();
        assert!(crate::transfer::read_backup(bad.path()).is_err());
        assert_eq!(db.books().unwrap().len(), 1);
        db.connection.execute_batch("DROP TABLE legacy_book_times; DROP TABLE legacy_activity_days; PRAGMA user_version=15;").unwrap();
        drop(db);
        let db = Storage::open(lease.path()).unwrap();
        assert_eq!(db.book(&reading.id).unwrap().title, reading.title);
        assert!(db.legacy_days().unwrap().is_empty());
        assert_eq!(
            db.connection
                .pragma_query_value(None, "user_version", |r| r.get::<_, u32>(0))
                .unwrap(),
            16
        );
    }
}
