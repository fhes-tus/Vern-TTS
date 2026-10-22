use crate::storage::{error, Result, Storage};
use rusqlite::{params, OptionalExtension};
use serde::{Deserialize, Serialize};
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CardInput {
    pub id: Option<String>,
    pub book_id: Option<String>,
    pub position: Option<usize>,
    pub deck: String,
    pub front: String,
    pub back: String,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Flashcard {
    pub id: String,
    pub book_id: Option<String>,
    pub position: Option<usize>,
    pub deck: String,
    pub front: String,
    pub back: String,
    pub recall: String,
    pub due_at: u64,
    pub interval_days: u32,
    pub repetitions: u32,
    pub ease: f32,
}

fn card(row: &rusqlite::Row<'_>) -> rusqlite::Result<Flashcard> {
    let position: Option<i64> = row.get(2)?;
    Ok(Flashcard {
        id: row.get(0)?,
        book_id: row.get(1)?,
        position: position
            .map(|p| usize::try_from(p).map_err(|_| rusqlite::Error::IntegralValueOutOfRange(2, p)))
            .transpose()?,
        deck: row.get(3)?,
        front: row.get(4)?,
        back: row.get(5)?,
        recall: row.get(6)?,
        due_at: row
            .get::<_, i64>(7)?
            .try_into()
            .map_err(|_| rusqlite::Error::InvalidQuery)?,
        interval_days: row.get(8)?,
        repetitions: row.get(9)?,
        ease: row.get(10)?,
    })
}
const CARD_COLUMNS: &str =
    "id,book_id,position,deck,front,back,recall,due_at,interval_days,repetitions,ease";

impl Storage {
    pub fn flashcards(&self) -> Result<Vec<Flashcard>> {
        let mut query = self
            .connection
            .prepare(&format!(
                "SELECT {CARD_COLUMNS} FROM flashcards ORDER BY due_at, rowid"
            ))
            .map_err(error)?;
        let rows = query.query_map([], card).map_err(error)?;
        rows.map(|r| r.map_err(error)).collect()
    }

    pub fn save_flashcard(&mut self, input: CardInput) -> Result<()> {
        if input.front.trim().is_empty()
            || input.back.trim().is_empty()
            || input.deck.trim().is_empty()
            || input.front.chars().count() > 10_000
            || input.back.chars().count() > 10_000
            || input.deck.chars().count() > 200
        {
            return Err(
                "A flashcard needs a deck (up to 200 characters), a question and an answer (up to 10,000 characters each)."
                    .into(),
            );
        }
        match (&input.book_id, input.position) {
            (Some(id), Some(position)) => {
                let count: i64 = self
                    .connection
                    .query_row(
                        "SELECT json_array_length(chunks) FROM books WHERE id=?1",
                        [id],
                        |r| r.get(0),
                    )
                    .map_err(error)?;
                if position >= usize::try_from(count).map_err(error)? {
                    return Err("The flashcard source is outside this book.".into());
                }
            }
            (None, None) => {}
            _ => return Err("Select both a book and a source passage, or neither.".into()),
        }
        let id = input.id.unwrap_or_else(|| uuid::Uuid::new_v4().to_string());
        uuid::Uuid::parse_str(&id).map_err(error)?;
        // Edits preserve the existing review schedule; scheduling is exclusively native.
        self.connection.execute("INSERT INTO flashcards(id,book_id,position,deck,front,back) VALUES (?1,?2,?3,?4,?5,?6) ON CONFLICT(id) DO UPDATE SET book_id=excluded.book_id,position=excluded.position,deck=excluded.deck,front=excluded.front,back=excluded.back",
            params![id,input.book_id,input.position.map(i64::try_from).transpose().map_err(error)?,input.deck.trim(),input.front.trim(),input.back.trim()]).map_err(error)?;
        Ok(())
    }

    pub fn delete_flashcard(&mut self, id: &str) -> Result<()> {
        self.connection
            .execute("DELETE FROM flashcards WHERE id=?1", [id])
            .map_err(error)?;
        Ok(())
    }

    pub fn review_flashcard(&mut self, id: &str, rating: &str) -> Result<()> {
        let existing = self
            .connection
            .query_row(
                &format!("SELECT {CARD_COLUMNS} FROM flashcards WHERE id=?1"),
                [id],
                card,
            )
            .optional()
            .map_err(error)?
            .ok_or("This flashcard no longer exists.")?;
        let ease = existing.ease.clamp(1.3, 3.0);
        let interval = existing.interval_days;
        let reps = existing.repetitions;
        // Port of Android StudyAssistant.kt SpacedRepetitionScheduler (SM-2 Lite).
        let (next_reps, next_interval, next_ease) = match rating {
            "again" => (0, 1, (ease - 0.2).max(1.3)),
            "hard" => (
                reps.saturating_add(1),
                if interval <= 1 {
                    1
                } else {
                    (interval as f32 * 1.2).round() as u32
                },
                (ease - 0.15).max(1.3),
            ),
            "good" => (
                reps.saturating_add(1),
                match reps {
                    0 => 1,
                    1 => 3,
                    _ => ((interval as f32 * ease).round() as u32).max(interval.saturating_add(1)),
                },
                ease,
            ),
            "easy" => (
                reps.saturating_add(1),
                match reps {
                    0 => 3,
                    1 => 7,
                    _ => ((interval as f32 * ease * 1.3).round() as u32)
                        .max(interval.saturating_add(2)),
                },
                (ease + 0.15).min(3.0),
            ),
            _ => return Err("Choose Again, Hard, Good or Easy.".into()),
        };
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map_err(error)?
            .as_millis();
        let due = i64::try_from(now + u128::from(next_interval) * 86_400_000).map_err(error)?;
        self.connection.execute("UPDATE flashcards SET recall=?1,due_at=?2,interval_days=?3,repetitions=?4,ease=?5 WHERE id=?6",
            params![rating,due,next_interval,next_reps,next_ease,id]).map_err(error)?;
        Ok(())
    }
}
