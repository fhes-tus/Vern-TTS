//! Android time comes from recorded spans, never from page counts or this PC's timezone.
use crate::{
    activity::{DayPart, Mode, Session, MAX_SESSION_MS},
    android_restore::stable_id,
    storage::{error, Result},
};
use chrono::{FixedOffset, NaiveDate, TimeZone};
use chrono_tz::{GapInfo, Tz};
use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::collections::HashMap;

const DAY: u64 = 86_400_000;
#[derive(Default)]
struct Dates {
    parts: Vec<DayPart>,
    indices: HashMap<String, usize>,
}
impl Dates {
    fn add(&mut self, date: NaiveDate, milliseconds: u64) -> Result<()> {
        let key = date.to_string();
        if let Some(index) = self.indices.get(&key) {
            self.parts[*index].milliseconds += milliseconds;
        } else {
            if self.parts.len() >= 20_000 {
                return Err("An Android session contains too many recorded dates.".into());
            }
            self.indices.insert(key.clone(), self.parts.len());
            self.parts.push(DayPart {
                date: key,
                milliseconds,
            });
        }
        Ok(())
    }
}
#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct AndroidSessionSource {
    pub record: Value,
}
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct Span {
    started_at: u64,
    duration_millis: u64,
    zone: String,
}
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct Recorded {
    #[serde(default = "version_one")]
    version: u8,
    id: String,
    document_id: String,
    title: String,
    mode: String,
    started_at: u64,
    #[serde(default)]
    checkpoint_at: Option<u64>,
    #[serde(default)]
    modified_at: Option<u64>,
    #[serde(default)]
    spans: Vec<Span>,
    #[serde(default)]
    corrected_millis: Option<u64>,
    #[serde(default)]
    excluded: bool,
    #[serde(default)]
    manual: bool,
}
fn version_one() -> u8 {
    1
}
fn parse(record: &Value) -> Result<Recorded> {
    if serde_json::to_vec(record).map_err(error)?.len() > 8 * 1024 * 1024 {
        return Err("An Android reading session exceeds 8 MB.".into());
    }
    let source: Recorded = serde_json::from_value(record.clone())
        .map_err(|e| format!("Invalid Android reading history: {e}"))?;
    if source.version != 1
        || source.id.is_empty()
        || source.id.len() > 1000
        || source.document_id.is_empty()
        || source.document_id.len() > 1000
        || source.spans.len() > 20_000
    {
        return Err("Unsupported or oversized Android reading history.".into());
    }
    Ok(source)
}
fn mode(source: &Recorded) -> Result<Mode> {
    match source.mode.as_str() {
        "READING" => Ok(Mode::Reading),
        "LISTENING" => Ok(Mode::Listening),
        _ => Err("Unsupported Android session mode.".into()),
    }
}
fn add_fixed(days: &mut Dates, span: &Span, tz: FixedOffset) -> Result<()> {
    let mut cursor = span.started_at;
    let end = cursor
        .checked_add(span.duration_millis)
        .ok_or("Android session time overflow.")?;
    while cursor < end {
        let local = tz
            .timestamp_millis_opt(i64::try_from(cursor).map_err(error)?)
            .single()
            .ok_or("Invalid Android session timestamp.")?;
        let date = local.date_naive();
        let next = date
            .succ_opt()
            .and_then(|day| day.and_hms_opt(0, 0, 0))
            .ok_or("Invalid Android session date.")?;
        let boundary = tz
            .from_local_datetime(&next)
            .single()
            .ok_or("Invalid fixed-offset session boundary.")?
            .timestamp_millis();
        let stop = end.min(u64::try_from(boundary).map_err(error)?);
        if stop <= cursor {
            return Err("Invalid Android midnight boundary.".into());
        }
        days.add(date, stop - cursor)?;
        cursor = stop;
    }
    Ok(())
}
fn add_named(days: &mut Dates, span: &Span, tz: Tz) -> Result<()> {
    let mut cursor = span.started_at;
    let end = cursor
        .checked_add(span.duration_millis)
        .ok_or("Android session time overflow.")?;
    while cursor < end {
        let local = tz
            .timestamp_millis_opt(i64::try_from(cursor).map_err(error)?)
            .single()
            .ok_or("Invalid Android session timestamp.")?;
        let date = local.date_naive();
        let next = date
            .succ_opt()
            .and_then(|day| day.and_hms_opt(0, 0, 0))
            .ok_or("Invalid Android session date.")?;
        // Java LocalDate.atStartOfDay uses the earlier overlap offset, or the
        // first valid instant following a gap (including a skipped calendar day).
        let boundary = tz
            .from_local_datetime(&next)
            .earliest()
            .or_else(|| GapInfo::new(&next, &tz).and_then(|gap| gap.end))
            .ok_or("The Android timezone has no resolvable midnight boundary.")?
            .timestamp_millis();
        let stop = end.min(u64::try_from(boundary).map_err(error)?);
        if stop <= cursor {
            return Err("Invalid Android timezone boundary.".into());
        }
        days.add(date, stop - cursor)?;
        cursor = stop;
    }
    Ok(())
}
fn allocation(source: &Recorded) -> Result<(u64, Vec<DayPart>)> {
    let mut days = Dates::default();
    let mut measured = 0_u64;
    for span in &source.spans {
        if span.duration_millis > DAY
            || span.zone.is_empty()
            || span.zone.len() > 128
            || span.started_at == 0
            || span.started_at > 8_640_000_000_000_000
        {
            return Err("Invalid Android history span or timezone.".into());
        }
        measured = measured
            .checked_add(span.duration_millis)
            .ok_or("Android reading duration overflow.")?;
        if measured > MAX_SESSION_MS {
            return Err("An Android session exceeds the supported measured-time limit.".into());
        }
        if let Ok(zone) = span.zone.parse::<Tz>() {
            add_named(&mut days, span, zone)?;
        } else {
            let offset = span
                .zone
                .strip_prefix("UTC")
                .or_else(|| span.zone.strip_prefix("GMT"))
                .or_else(|| span.zone.strip_prefix("UT"))
                .unwrap_or(&span.zone);
            let offset = if offset == "Z" || offset.is_empty() {
                FixedOffset::east_opt(0).ok_or("Invalid UTC offset.")?
            } else {
                offset.parse::<FixedOffset>().map_err(|_| {
                    format!("Unsupported timezone in Android history: {}.", span.zone)
                })?
            };
            add_fixed(&mut days, span, offset)?;
        }
    }
    Ok((measured, days.parts))
}
impl AndroidSessionSource {
    pub fn convert(record: Value, book_id: Option<String>) -> Result<Session> {
        let source = parse(&record)?;
        let (measured_ms, days) = allocation(&source)?;
        let mode = mode(&source)?;
        let session = Session {
            id: stable_id("session", &source.id),
            book_id,
            title: source.title,
            mode,
            started_at: source.started_at,
            updated_at: source
                .modified_at
                .or(source.checkpoint_at)
                .unwrap_or(source.started_at),
            // Android revisions contain private cached-file names/mtime/size. An
            // exported document cannot prove those historical sentence anchors.
            start_position: None,
            end_position: None,
            measured_ms,
            corrected_ms: source.corrected_millis,
            excluded: source.excluded,
            manual: source.manual,
            days,
            android_source: Some(Self { record }),
        };
        session.validate()?;
        Ok(session)
    }
    pub(crate) fn validate_against(&self, session: &Session) -> Result<()> {
        let source = parse(&self.record)?;
        let (measured, days) = allocation(&source)?;
        if stable_id("session", &source.id) != session.id
            || mode(&source)? != session.mode
            || source.started_at != session.started_at
            || measured != session.measured_ms
            || session.start_position.is_some()
            || session.end_position.is_some()
            || days.len() != session.days.len()
            || days
                .iter()
                .zip(&session.days)
                .any(|(a, b)| a.date != b.date || a.milliseconds != b.milliseconds)
        {
            return Err("Imported Android history no longer matches its recorded spans.".into());
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{backup_archive::StagedFile, storage::Storage};
    use serde_json::json;
    use std::{io::Cursor, path::Path};
    fn time(zone: Tz, year: i32, month: u32, day: u32, hour: u32, minute: u32) -> u64 {
        zone.with_ymd_and_hms(year, month, day, hour, minute, 0)
            .single()
            .unwrap()
            .timestamp_millis() as u64
    }
    fn record(start: u64, zone: &str, duration: u64) -> Value {
        json!({"version":1,"id":"android-history","documentId":"source-book","title":"A recorded reading","revision":"cached.txt:1710000000000:123:2","mode":"LISTENING","startedAt":start,"checkpointAt":start+duration,"modifiedAt":start+duration,"startLocation":700,"endLocation":900,"startPage":2,"endPage":4,"spans":[{"startedAt":start,"durationMillis":duration,"zone":zone,"location":700}],"closedReason":"closed","manual":false,"excluded":false})
    }
    fn parts(session: &Session) -> Vec<(&str, u64)> {
        session
            .days
            .iter()
            .map(|d| (d.date.as_str(), d.milliseconds))
            .collect()
    }
    #[test]
    fn recorded_timezones_split_spring_fall_midnight_gaps_and_skipped_dates() {
        let ny = chrono_tz::America::New_York;
        let spring = AndroidSessionSource::convert(
            record(time(ny, 2024, 3, 9, 23, 30), "America/New_York", DAY),
            None,
        )
        .unwrap();
        assert_eq!(
            parts(&spring),
            vec![
                ("2024-03-09", 1_800_000),
                ("2024-03-10", 82_800_000),
                ("2024-03-11", 1_800_000)
            ]
        );
        let start = time(ny, 2024, 11, 3, 0, 0);
        let mut fall = record(start, "America/New_York", DAY);
        fall["spans"].as_array_mut().unwrap().push(json!({"startedAt":start+DAY,"durationMillis":3_600_000,"zone":"America/New_York","location":800}));
        fall["checkpointAt"] = json!(start + DAY + 3_600_000);
        fall["modifiedAt"] = fall["checkpointAt"].clone();
        let fall = AndroidSessionSource::convert(fall, None).unwrap();
        assert_eq!(parts(&fall), vec![("2024-11-03", 90_000_000)]);
        let santiago = chrono_tz::America::Santiago;
        assert!(santiago
            .with_ymd_and_hms(2024, 9, 8, 0, 0, 0)
            .single()
            .is_none());
        let gap = AndroidSessionSource::convert(
            record(
                time(santiago, 2024, 9, 7, 23, 30),
                "America/Santiago",
                7_200_000,
            ),
            None,
        )
        .unwrap();
        assert_eq!(
            parts(&gap),
            vec![("2024-09-07", 1_800_000), ("2024-09-08", 5_400_000)]
        );
        let apia = chrono_tz::Pacific::Apia;
        let skip = AndroidSessionSource::convert(
            record(time(apia, 2011, 12, 29, 23, 30), "Pacific/Apia", 7_200_000),
            None,
        )
        .unwrap();
        assert_eq!(
            parts(&skip),
            vec![("2011-12-29", 1_800_000), ("2011-12-31", 5_400_000)]
        );
    }
    #[test]
    fn fixed_offsets_corrections_exclusions_zero_time_and_reopen_keep_recorded_facts() {
        let offset = FixedOffset::east_opt(5 * 3600 + 45 * 60).unwrap();
        let start = offset
            .with_ymd_and_hms(2024, 6, 1, 23, 59, 0)
            .single()
            .unwrap()
            .timestamp_millis() as u64;
        let mut raw = record(start, "UTC+05:45", 120_000);
        raw["correctedMillis"] = json!(3);
        let corrected = AndroidSessionSource::convert(raw.clone(), None).unwrap();
        assert_eq!(
            parts(&corrected),
            vec![("2024-06-01", 60_000), ("2024-06-02", 60_000)]
        );
        assert_eq!(
            (corrected.start_position, corrected.end_position),
            (None, None)
        );
        let lease = StagedFile::stage(Cursor::new([]), 0).unwrap();
        let mut db = Storage::open(lease.path()).unwrap();
        db.restore_session(&corrected).unwrap();
        let report = db.activity_report().unwrap();
        assert_eq!(report.days.iter().map(|d| d.listening_ms).sum::<u64>(), 3);
        assert_eq!(
            report
                .days
                .iter()
                .find(|d| d.date == "2024-06-02")
                .unwrap()
                .listening_ms,
            2
        );
        let csv = String::from_utf8(db.sessions_csv().unwrap()).unwrap();
        assert!(csv.lines().last().unwrap().ends_with(",true,false,,"));
        db.correct_session(&corrected.id, Some(0), true).unwrap();
        assert!(db.activity_report().unwrap().days.is_empty());
        drop(db);
        let db = Storage::open(lease.path()).unwrap();
        let stored = db.sessions().unwrap().remove(0);
        assert!(stored.excluded);
        assert_eq!(stored.android_source.unwrap().record, raw);
        let file = StagedFile::stage(
            Cursor::new(db.backup_bytes().unwrap()),
            crate::transfer::LIMIT as u64,
        )
        .unwrap();
        let mut restored = Storage::open(Path::new(":memory:")).unwrap();
        restored
            .restore_backup(crate::transfer::read_backup(file.path()).unwrap(), false)
            .unwrap();
        assert_eq!(
            restored.sessions().unwrap()[0]
                .android_source
                .as_ref()
                .unwrap()
                .record,
            raw
        );
        let mut zero = record(start, "UTC", 0);
        zero["id"] = json!("zero-time");
        zero["spans"] = json!([]);
        let zero = AndroidSessionSource::convert(zero, None).unwrap();
        restored.restore_session(&zero).unwrap();
        assert_eq!(restored.sessions().unwrap().len(), 2);
        assert_eq!(zero.measured_ms, 0);
        assert!(zero.days.is_empty());
    }
    #[test]
    fn first_seen_day_order_survives_timezone_changes_and_invalid_history_is_rejected() {
        let start = time(chrono_tz::UTC, 2024, 6, 2, 1, 0);
        let mut raw = record(start, "Pacific/Kiritimati", 1);
        raw["spans"].as_array_mut().unwrap().push(json!({"startedAt":start+1,"durationMillis":3,"zone":"Pacific/Honolulu","location":900}));
        raw["checkpointAt"] = json!(start + 4);
        raw["modifiedAt"] = raw["checkpointAt"].clone();
        let mut session = AndroidSessionSource::convert(raw.clone(), None).unwrap();
        assert_eq!(parts(&session), vec![("2024-06-02", 1), ("2024-06-01", 3)]);
        session.days[0].date = "2024-05-31".into();
        assert!(session.validate().is_err());
        raw["spans"][0]["zone"] = json!("Unknown/Timezone");
        assert!(AndroidSessionSource::convert(raw.clone(), None).is_err());
        raw["spans"][0]["zone"] = json!("UTC");
        raw["spans"][0]["durationMillis"] = json!(DAY + 1);
        assert!(AndroidSessionSource::convert(raw, None).is_err());
    }
}
