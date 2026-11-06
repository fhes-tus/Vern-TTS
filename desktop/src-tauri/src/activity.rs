//! Measured activity has one native clock. Heartbeats report state, never elapsed time.
use crate::storage::{error, now, Result, Storage};
use chrono::{Local, NaiveDate, TimeZone};
use rusqlite::{params, OptionalExtension};
use serde::{Deserialize, Serialize};
use std::{collections::BTreeMap, time::Instant};

const DAY: u64 = 86_400_000;
const LIMIT: usize = 20_000;
// Ten years of measured time per imported session also keeps 20,000-session
// aggregate milliseconds within JavaScript's exact-integer range.
pub(crate) const MAX_SESSION_MS: u64 = DAY * 3660;

#[derive(Clone, Debug, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum Mode {
    Reading,
    Listening,
}
#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DayPart {
    pub date: String,
    pub milliseconds: u64,
}
#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Session {
    pub id: String,
    pub book_id: Option<String>,
    pub title: String,
    pub mode: Mode,
    pub started_at: u64,
    pub updated_at: u64,
    pub start_position: Option<usize>,
    pub end_position: Option<usize>,
    pub measured_ms: u64,
    pub corrected_ms: Option<u64>,
    pub excluded: bool,
    pub manual: bool,
    pub days: Vec<DayPart>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub android_source: Option<crate::android_history::AndroidSessionSource>,
}
impl Session {
    pub fn active_ms(&self) -> u64 {
        if self.excluded {
            0
        } else {
            self.corrected_ms.unwrap_or(self.measured_ms)
        }
    }
    pub(crate) fn validate(&self) -> Result<()> {
        if uuid::Uuid::parse_str(&self.id).is_err()
            || self.title.trim().is_empty()
            || self.title.chars().count() > 1000
            || self.started_at == 0
            || self.started_at
                > if self.android_source.is_some() {
                    8_640_000_000_000_000
                } else {
                    now() + DAY
                }
            || (self.updated_at < self.started_at && self.android_source.is_none())
            || self.updated_at
                > if self.android_source.is_some() {
                    8_640_000_000_000_000
                } else {
                    now() + DAY
                }
            || self.measured_ms > MAX_SESSION_MS
            || self.corrected_ms.is_some_and(|ms| ms > DAY)
            || self.start_position.is_some_and(|p| p >= 100_000)
            || self.end_position.is_some_and(|p| p >= 100_000)
            || self.start_position.is_some() != self.end_position.is_some()
            || (self.measured_ms == 0) != self.days.is_empty()
            || self.days.len() > LIMIT
            || self.days.iter().any(|d| {
                d.date.len() != 10
                    || d.milliseconds == 0
                    || d.milliseconds > MAX_SESSION_MS
                    || NaiveDate::parse_from_str(&d.date, "%Y-%m-%d").is_err()
            })
            || self.days.iter().map(|d| d.milliseconds).sum::<u64>() != self.measured_ms
            || self
                .days
                .iter()
                .map(|d| &d.date)
                .collect::<std::collections::HashSet<_>>()
                .len()
                != self.days.len()
        {
            return Err("Invalid reading session or date allocation.".into());
        }
        if let Some(source) = &self.android_source {
            source.validate_against(self)?;
        }
        Ok(())
    }
}

#[derive(Clone, Debug, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "camelCase")]
pub struct Target {
    pub book_id: String,
    pub position: usize,
}
#[derive(Default, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Event {
    pub reader: Option<Target>,
    pub audio: Option<Target>,
    pub interacting: bool,
    pub playback_requested: bool,
}
#[derive(Default)]
pub struct Tracker {
    current: Option<Session>,
    last: Option<(Instant, u64)>,
    interaction: Option<Instant>,
    reader: Option<String>,
}
impl Tracker {
    pub fn event(
        &mut self,
        db: &mut Storage,
        event: Event,
        mono: Instant,
        wall: u64,
    ) -> Result<()> {
        let idle = db.settings()?.reading_idle_seconds as u64 * 1000;
        // Validate before changing the clock. A forged target must never create activity.
        for target in [&event.reader, &event.audio].into_iter().flatten() {
            db.validate_activity_target(target)?;
        }
        if let Some((last, last_wall)) = self.last {
            let elapsed = mono.saturating_duration_since(last).as_millis() as u64;
            // A suspended process or stopped heartbeat earns no catch-up time.
            let allowed = if elapsed > 15_000 {
                0
            } else {
                match self.current.as_ref().map(|s| &s.mode) {
                    Some(Mode::Reading) => self.interaction.map_or(0, |i| {
                        idle.saturating_sub(last.saturating_duration_since(i).as_millis() as u64).min(elapsed)
                    }),
                    Some(Mode::Listening) => elapsed,
                    None => 0,
                }
            };
            if let Some(session) = &mut self.current {
                let allowed = allowed.min(DAY - session.measured_ms);
                if allowed > 0 {
                    add_days(&mut session.days, last_wall, allowed)?;
                    session.measured_ms += allowed;
                    session.updated_at = wall.max(session.started_at);
                    db.write_measured_session(session)?;
                }
            }
        }
        let reader_changed = event
            .reader
            .as_ref()
            .is_some_and(|target| self.reader.as_ref() != Some(&target.book_id));
        self.reader = event.reader.as_ref().map(|target| target.book_id.clone());
        if event.interacting || reader_changed {
            self.interaction = Some(mono);
        }
        let reader_eligible = self
            .interaction
            .is_some_and(|i| mono.saturating_duration_since(i).as_millis() < idle as u128);
        let chosen = event.audio.map(|t| (t, Mode::Listening)).or_else(|| {
            if !event.playback_requested && reader_eligible {
                event.reader.map(|t| (t, Mode::Reading))
            } else {
                None
            }
        });
        match chosen {
            Some((target, mode)) => {
                let same = self.current.as_ref().is_some_and(|s| {
                    s.book_id.as_ref() == Some(&target.book_id)
                        && s.mode == mode
                        && s.measured_ms < DAY
                });
                if !same {
                    let book = db.book(&target.book_id)?;
                    self.current = Some(Session {
                        id: uuid::Uuid::new_v4().to_string(),
                        book_id: Some(book.id),
                        title: book.title,
                        mode,
                        started_at: wall,
                        updated_at: wall,
                        start_position: Some(target.position),
                        end_position: Some(target.position),
                        measured_ms: 0,
                        corrected_ms: None,
                        excluded: false,
                        manual: false,
                        days: Vec::new(),
                        android_source: None,
                    });
                } else if let Some(s) = &mut self.current {
                    s.end_position = Some(target.position);
                }
            }
            None => self.current = None,
        }
        self.last = Some((mono, wall));
        Ok(())
    }
}

// Resolve local dates at recording time. Walk minute-sized slices to handle DST and
// offset changes, splitting precisely at midnight without assuming 24-hour days.
fn add_days(days: &mut Vec<DayPart>, start: u64, duration: u64) -> Result<()> {
    let mut cursor = start;
    let end = start
        .checked_add(duration)
        .ok_or("Session timestamp is too large.")?;
    while cursor < end {
        let local = Local
            .timestamp_millis_opt(i64::try_from(cursor).map_err(error)?)
            .single()
            .ok_or("Invalid session date.")?;
        let ms_into_day = local.time().num_seconds_from_midnight() as u64 * 1000 + cursor % 1000;
        let step = (end - cursor).min(60_000).min(DAY - ms_into_day);
        let date = local.date_naive().to_string();
        if let Some(day) = days.iter_mut().find(|d| d.date == date) {
            day.milliseconds += step;
        } else {
            days.push(DayPart {
                date,
                milliseconds: step,
            });
        }
        cursor += step;
    }
    Ok(())
}
use chrono::Timelike;

#[derive(Serialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct DailyTotal {
    pub date: String,
    pub reading_ms: u64,
    pub listening_ms: u64,
    pub legacy_ms: u64,
    pub app_usage_ms: u64,
    pub app_open_count: u32,
    pub legacy_book_count: usize,
}
#[derive(Serialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct BookTotal {
    pub book_id: Option<String>,
    pub title: String,
    pub reading_ms: u64,
    pub listening_ms: u64,
    pub sessions: usize,
}
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Report {
    pub today: String,
    pub days: Vec<DailyTotal>,
    pub books: Vec<BookTotal>,
    pub sessions: Vec<Session>,
    pub total_sessions: usize,
    pub current_streak: usize,
    pub best_streak: usize,
    pub legacy_days: Vec<crate::legacy_activity::LegacyDaySummary>,
    pub legacy_book_times: Vec<crate::legacy_activity::LegacyBookTime>,
}
impl Storage {
    fn validate_activity_target(&self, target: &Target) -> Result<()> {
        let count: i64 = self
            .connection
            .query_row(
                "SELECT json_array_length(chunks) FROM books WHERE id=?1",
                [&target.book_id],
                |r| r.get(0),
            )
            .map_err(error)?;
        if target.position >= usize::try_from(count).map_err(error)? {
            return Err("Choose an existing passage for this session.".into());
        }
        Ok(())
    }
    pub fn sessions(&self) -> Result<Vec<Session>> {
        let mut q = self.connection.prepare("SELECT value,book_id FROM reading_sessions ORDER BY json_extract(value,'$.startedAt') DESC,id").map_err(error)?;
        let rows = q
            .query_map([], |r| {
                Ok((r.get::<_, String>(0)?, r.get::<_, Option<String>>(1)?))
            })
            .map_err(error)?;
        rows.map(|r| {
            let (value, book_id) = r.map_err(error)?;
            let mut s: Session = serde_json::from_str(&value).map_err(error)?;
            s.book_id = book_id;
            Ok(s)
        })
        .collect()
    }
    pub fn restore_session(&mut self, session: &Session) -> Result<()> {
        session.validate()?;
        let exists: bool = self
            .connection
            .query_row(
                "SELECT EXISTS(SELECT 1 FROM reading_sessions WHERE id=?1)",
                [&session.id],
                |r| r.get(0),
            )
            .map_err(error)?;
        if exists {
            return Ok(());
        }
        if let Some(book_id) = &session.book_id {
            self.book(book_id)?;
            for position in [session.start_position, session.end_position]
                .into_iter()
                .flatten()
            {
                self.validate_activity_target(&Target {
                    book_id: book_id.clone(),
                    position,
                })?;
            }
        }
        let count: i64 = self
            .connection
            .query_row("SELECT count(*) FROM reading_sessions", [], |r| r.get(0))
            .map_err(error)?;
        if count >= LIMIT as i64 {
            return Err("Reading history supports up to 20,000 sessions. Export your history before adding more.".into());
        }
        self.connection
            .execute(
                "INSERT OR IGNORE INTO reading_sessions(id,book_id,value) VALUES(?1,?2,?3)",
                params![
                    session.id,
                    session.book_id,
                    serde_json::to_string(session).map_err(error)?
                ],
            )
            .map_err(error)?;
        Ok(())
    }
    fn write_measured_session(&mut self, session: &Session) -> Result<()> {
        let existing: Option<String> = self
            .connection
            .query_row(
                "SELECT value FROM reading_sessions WHERE id=?1",
                [&session.id],
                |r| r.get(0),
            )
            .optional()
            .map_err(error)?;
        if let Some(value) = existing {
            let old: Session = serde_json::from_str(&value).map_err(error)?;
            let mut updated = session.clone();
            updated.corrected_ms = old.corrected_ms;
            updated.excluded = old.excluded;
            self.connection
                .execute(
                    "UPDATE reading_sessions SET value=?1 WHERE id=?2",
                    params![serde_json::to_string(&updated).map_err(error)?, session.id],
                )
                .map_err(error)?;
            Ok(())
        } else {
            self.restore_session(session)
        }
    }
    pub fn correct_session(
        &mut self,
        id: &str,
        corrected_ms: Option<u64>,
        excluded: bool,
    ) -> Result<()> {
        if corrected_ms.is_some_and(|ms| ms > DAY) {
            return Err("Use a duration between 0 and 24 hours.".into());
        }
        let value: String = self
            .connection
            .query_row(
                "SELECT value FROM reading_sessions WHERE id=?1",
                [id],
                |r| r.get(0),
            )
            .map_err(error)?;
        let mut s: Session = serde_json::from_str(&value).map_err(error)?;
        s.corrected_ms = corrected_ms;
        s.excluded = excluded;
        self.connection
            .execute(
                "UPDATE reading_sessions SET value=?1 WHERE id=?2",
                params![serde_json::to_string(&s).map_err(error)?, id],
            )
            .map_err(error)?;
        Ok(())
    }
    pub fn manual_session(
        &mut self,
        target: Target,
        mode: Mode,
        started_at: u64,
        duration_ms: u64,
    ) -> Result<()> {
        self.validate_activity_target(&target)?;
        if duration_ms == 0
            || duration_ms > DAY
            || started_at == 0
            || started_at > now()
            || started_at
                .checked_add(duration_ms)
                .is_none_or(|end| end > now())
        {
            return Err("Choose a past session of up to 24 hours that has already ended.".into());
        }
        let mut days = Vec::new();
        add_days(&mut days, started_at, duration_ms)?;
        let book = self.book(&target.book_id)?;
        self.restore_session(&Session {
            id: uuid::Uuid::new_v4().to_string(),
            book_id: Some(book.id),
            title: book.title,
            mode,
            started_at,
            updated_at: now(),
            start_position: Some(target.position),
            end_position: Some(target.position),
            measured_ms: duration_ms,
            corrected_ms: None,
            excluded: false,
            manual: true,
            days,
            android_source: None,
        })
    }
    pub fn activity_report(&self) -> Result<Report> {
        let sessions = self.sessions()?;
        let legacy = self.legacy_days()?;
        let mut days = BTreeMap::<String, DailyTotal>::new();
        let mut books = BTreeMap::<String, BookTotal>::new();
        for session in &sessions {
            let active = session.active_ms();
            if active == 0 {
                continue;
            }
            let book = books
                .entry(
                    session
                        .book_id
                        .clone()
                        .unwrap_or_else(|| format!("removed:{}", session.title)),
                )
                .or_insert_with(|| BookTotal {
                    book_id: session.book_id.clone(),
                    title: session.title.clone(),
                    ..Default::default()
                });
            book.sessions += 1;
            match session.mode {
                Mode::Reading => book.reading_ms += active,
                Mode::Listening => book.listening_ms += active,
            }
            let mut allocated = 0;
            for (index, part) in session.days.iter().enumerate() {
                let ms = if index + 1 == session.days.len() {
                    active - allocated
                } else {
                    u64::try_from(
                        u128::from(part.milliseconds) * u128::from(active)
                            / u128::from(session.measured_ms),
                    )
                    .map_err(error)?
                };
                allocated += ms;
                if ms == 0 {
                    continue;
                }
                let day = days.entry(part.date.clone()).or_insert_with(|| DailyTotal {
                    date: part.date.clone(),
                    ..Default::default()
                });
                match session.mode {
                    Mode::Reading => day.reading_ms += ms,
                    Mode::Listening => day.listening_ms += ms,
                }
            }
        }
        for entry in &legacy {
            let day = days
                .entry(entry.date.clone())
                .or_insert_with(|| DailyTotal {
                    date: entry.date.clone(),
                    ..Default::default()
                });
            day.legacy_ms = entry.reading_ms;
            day.app_usage_ms = entry.usage_ms;
            day.app_open_count = entry.app_open_count;
            day.legacy_book_count = entry.read_book_ids.len();
        }
        let reading_dates: std::collections::BTreeSet<_> = days
            .values()
            .filter(|d| {
                d.reading_ms > 0 || d.listening_ms > 0 || d.legacy_ms > 0 || d.legacy_book_count > 0
            })
            .map(|d| d.date.clone())
            .collect();
        let today = Local::now().date_naive();
        let mut best = 0;
        let mut run = 0;
        let mut previous: Option<NaiveDate> = None;
        for date in reading_dates
            .iter()
            .filter_map(|s| NaiveDate::parse_from_str(s, "%Y-%m-%d").ok())
        {
            run = if previous.and_then(|d| d.succ_opt()) == Some(date) {
                run + 1
            } else {
                1
            };
            best = best.max(run);
            previous = Some(date);
        }
        let mut cursor = if reading_dates.contains(&today.to_string()) {
            today
        } else {
            today.pred_opt().ok_or("Invalid local date.")?
        };
        let mut current = 0;
        while reading_dates.contains(&cursor.to_string()) {
            current += 1;
            let Some(prev) = cursor.pred_opt() else {
                break;
            };
            cursor = prev;
        }
        let mut books: Vec<_> = books.into_values().collect();
        books.sort_by_key(|b| std::cmp::Reverse(b.reading_ms + b.listening_ms));
        Ok(Report {
            today: today.to_string(),
            days: days.into_values().rev().take(366).collect(),
            books,
            total_sessions: sessions.len(),
            sessions: sessions.into_iter().take(200).collect(),
            current_streak: current,
            best_streak: best,
            legacy_days: legacy.iter().map(|d| d.summary()).collect(),
            legacy_book_times: self.legacy_book_times()?,
        })
    }
    pub fn sessions_csv(&self) -> Result<Vec<u8>> {
        let mut csv = String::from("Title,Mode,Started (UTC),Measured seconds,Corrected seconds,Included,Manual,Start passage,End passage\r\n");
        for session in self.sessions()? {
            // Quoting alone does not neutralize spreadsheet formula injection.
            let safe_title = if session.title.trim_start().starts_with(['=', '+', '-', '@'])
                || session.title.starts_with(['\t', '\r', '\n'])
            {
                format!("'{}", session.title)
            } else {
                session.title
            };
            let title = safe_title.replace('"', "\"\"");
            let date = chrono::DateTime::from_timestamp_millis(session.started_at as i64)
                .ok_or("Invalid session date.")?
                .to_rfc3339();
            csv.push_str(&format!(
                "\"{}\",{},{},{},{},{},{},{},{}\r\n",
                title,
                if session.mode == Mode::Reading {
                    "Reading"
                } else {
                    "Listening"
                },
                date,
                session.measured_ms / 1000,
                session
                    .corrected_ms
                    .map(|ms| (ms / 1000).to_string())
                    .unwrap_or_default(),
                !session.excluded,
                session.manual,
                session
                    .start_position
                    .map(|p| (p + 1).to_string())
                    .unwrap_or_default(),
                session
                    .end_position
                    .map(|p| (p + 1).to_string())
                    .unwrap_or_default()
            ));
        }
        Ok(csv.into_bytes())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::storage::Book;
    use std::time::Duration;

    fn book(db: &mut Storage) -> Target {
        let book = db
            .save_book(Book {
                id: String::new(),
                title: "A reading life".into(),
                author: "Vern".into(),
                format: "TXT".into(),
                chunks: vec!["A quiet page.".into(), "Another page.".into()],
                position: 0,
                favorite: false,
                collection: String::new(),
                text_available: true,
                cover: None,
                updated_at: 0,
            })
            .unwrap();
        Target {
            book_id: book.id,
            position: 0,
        }
    }
    fn event(
        reader: Option<Target>,
        audio: Option<Target>,
        interacting: bool,
        playback_requested: bool,
    ) -> Event {
        Event {
            reader,
            audio,
            interacting,
            playback_requested,
        }
    }
    #[test]
    fn clock_counts_one_mode_stops_at_idle_and_skips_suspend_and_buffering() {
        let mut db = Storage::open(std::path::Path::new(":memory:")).unwrap();
        let target = book(&mut db);
        let mut clock = Tracker::default();
        let start = Instant::now();
        let wall = now();
        for seconds in (0..=150).step_by(5) {
            clock
                .event(
                    &mut db,
                    event(Some(target.clone()), None, false, false),
                    start + Duration::from_secs(seconds),
                    wall + seconds * 1000,
                )
                .unwrap();
        }
        assert_eq!(
            db.sessions()
                .unwrap()
                .iter()
                .map(Session::active_ms)
                .sum::<u64>(),
            120_000
        );
        // Idle does not restart itself; an explicit interaction resumes it.
        clock
            .event(
                &mut db,
                event(Some(target.clone()), None, true, false),
                start + Duration::from_secs(155),
                wall + 155_000,
            )
            .unwrap();
        clock
            .event(
                &mut db,
                event(Some(target.clone()), None, false, true),
                start + Duration::from_secs(160),
                wall + 160_000,
            )
            .unwrap();
        // Preparation earns no reading or listening time.
        clock
            .event(
                &mut db,
                event(Some(target.clone()), Some(target.clone()), false, true),
                start + Duration::from_secs(170),
                wall + 170_000,
            )
            .unwrap();
        clock
            .event(
                &mut db,
                event(Some(target.clone()), None, false, true),
                start + Duration::from_secs(175),
                wall + 175_000,
            )
            .unwrap();
        // Hidden/background reading is represented by no reader; a large heartbeat gap is not credited.
        clock
            .event(
                &mut db,
                event(Some(target.clone()), None, true, false),
                start + Duration::from_secs(180),
                wall + 180_000,
            )
            .unwrap();
        clock
            .event(
                &mut db,
                event(None, None, false, false),
                start + Duration::from_secs(3600),
                wall + 3_600_000,
            )
            .unwrap();
        let sessions = db.sessions().unwrap();
        assert_eq!(
            sessions
                .iter()
                .filter(|s| s.mode == Mode::Reading)
                .map(Session::active_ms)
                .sum::<u64>(),
            125_000
        );
        assert_eq!(
            sessions
                .iter()
                .filter(|s| s.mode == Mode::Listening)
                .map(Session::active_ms)
                .sum::<u64>(),
            5_000
        );
        assert!(clock
            .event(
                &mut db,
                event(
                    Some(Target {
                        position: 2,
                        ..target
                    }),
                    None,
                    false,
                    false
                ),
                Instant::now(),
                now()
            )
            .is_err());
    }
    #[test]
    fn sessions_corrections_detachment_and_backup_survive_reopen() {
        let root = std::env::temp_dir().join(format!("vern-activity-{}", uuid::Uuid::new_v4()));
        std::fs::create_dir(&root).unwrap();
        let path = root.join("library.db");
        let mut db = Storage::open(&path).unwrap();
        let target = book(&mut db);
        assert!(db
            .manual_session(target.clone(), Mode::Reading, now(), 60_000)
            .is_err());
        db.manual_session(target.clone(), Mode::Reading, now() - 120_000, 60_000)
            .unwrap();
        let s = db.sessions().unwrap().remove(0);
        db.correct_session(&s.id, Some(30_000), false).unwrap();
        let mut measured = s.clone();
        measured.measured_ms += 10_000;
        measured.days[0].milliseconds += 10_000;
        db.write_measured_session(&measured).unwrap();
        assert_eq!(db.sessions().unwrap()[0].active_ms(), 30_000);
        drop(db);
        let mut db = Storage::open(&path).unwrap();
        assert_eq!(db.activity_report().unwrap().books[0].reading_ms, 30_000);
        let backup_file = root.join("backup.json");
        std::fs::write(&backup_file, db.backup_bytes().unwrap()).unwrap();
        let mut restored = Storage::open(std::path::Path::new(":memory:")).unwrap();
        for _ in 0..2 {
            restored
                .restore_backup(crate::transfer::read_backup(&backup_file).unwrap(), false)
                .unwrap();
        }
        assert_eq!(restored.sessions().unwrap().len(), 1);
        assert_eq!(restored.sessions().unwrap()[0].active_ms(), 30_000);
        db.correct_session(&s.id, None, true).unwrap();
        assert!(db.activity_report().unwrap().days.is_empty());
        db.delete_book(&target.book_id).unwrap();
        assert!(db.sessions().unwrap()[0].book_id.is_none());
        assert_eq!(db.sessions().unwrap()[0].title, "A reading life");
        drop(db);
        std::fs::remove_dir_all(root).unwrap();
    }
    #[test]
    fn local_midnight_allocation_and_csv_are_bounded_and_safe() {
        let mut db = Storage::open(std::path::Path::new(":memory:")).unwrap();
        let target = book(&mut db);
        let midnight = Local::now()
            .date_naive()
            .and_hms_opt(0, 0, 0)
            .unwrap()
            .and_local_timezone(Local)
            .earliest()
            .unwrap()
            .timestamp_millis() as u64;
        let mut days = Vec::new();
        add_days(&mut days, midnight - 30_000, 60_000).unwrap();
        assert_eq!(days.len(), 2);
        assert_eq!(days[0].milliseconds, 30_000);
        assert_eq!(days[1].milliseconds, 30_000);
        db.manual_session(target, Mode::Listening, now() - 120_000, 60_000)
            .unwrap();
        let mut session = db.sessions().unwrap().remove(0);
        session.title = " =SUM(1,2)\"\nBook".into();
        session.id = uuid::Uuid::new_v4().to_string();
        db.restore_session(&session).unwrap();
        assert!(String::from_utf8(db.sessions_csv().unwrap())
            .unwrap()
            .contains("\"' =SUM(1,2)\"\"\nBook\""));
        session.id = uuid::Uuid::new_v4().to_string();
        session.days[0].milliseconds = DAY + 1;
        assert!(db.restore_session(&session).is_err());
    }
}
