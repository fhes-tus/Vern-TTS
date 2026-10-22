use crate::storage::{error, Result, Storage};
use rusqlite::{params, OptionalExtension};
use serde::{Deserialize, Serialize};
use std::collections::HashSet;

#[derive(Clone, Debug, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Question {
    pub question: String,
    pub options: Vec<String>,
    pub answer: String,
    #[serde(default)]
    pub explanation: String,
}
#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Quiz {
    pub id: String,
    pub title: String,
    pub book_id: Option<String>,
    pub questions: Vec<Question>,
    pub best_score: Option<usize>,
    pub last_score: Option<usize>,
    pub attempts: u32,
    pub created_at: u64,
}
#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct QuizInput {
    pub id: Option<String>,
    pub title: String,
    pub book_id: Option<String>,
    pub questions: Vec<Question>,
}

impl Quiz {
    pub fn validate(&self) -> Result<()> {
        uuid::Uuid::parse_str(&self.id).map_err(error)?;
        if self.title.trim().is_empty()
            || self.title.chars().count() > 200
            || self.questions.is_empty()
            || self.questions.len() > 200
            || self.created_at > 8_640_000_000_000_000
            || self.best_score.is_some_and(|s| s > self.questions.len())
            || self.last_score.is_some_and(|s| s > self.questions.len())
            || (self.attempts == 0) != self.best_score.is_none()
            || self.best_score.is_none() != self.last_score.is_none()
            || self.best_score < self.last_score
        {
            return Err(
                "A quiz needs a title and 1–200 valid questions with valid results.".into(),
            );
        }
        for q in &self.questions {
            let unique: HashSet<_> = q.options.iter().map(|s| s.trim().to_lowercase()).collect();
            if q.question.trim().is_empty()
                || q.question.chars().count() > 10_000
                || !(2..=8).contains(&q.options.len())
                || unique.len() != q.options.len()
                || q.options
                    .iter()
                    .any(|s| s.trim().is_empty() || s.chars().count() > 2_000)
                || !q.options.contains(&q.answer)
                || q.explanation.chars().count() > 10_000
            {
                return Err(
                    "Each question needs 2–8 different choices and an answer matching one choice."
                        .into(),
                );
            }
        }
        Ok(())
    }
}
impl Storage {
    pub fn quizzes(&self) -> Result<Vec<Quiz>> {
        let mut query = self
            .connection
            .prepare("SELECT value FROM quizzes ORDER BY rowid DESC")
            .map_err(error)?;
        let rows = query
            .query_map([], |r| r.get::<_, String>(0))
            .map_err(error)?;
        rows.map(|r| serde_json::from_str(&r.map_err(error)?).map_err(error))
            .collect()
    }
    fn quiz(&self, id: &str) -> Result<Quiz> {
        let value: String = self
            .connection
            .query_row("SELECT value FROM quizzes WHERE id=?1", [id], |r| r.get(0))
            .map_err(error)?;
        serde_json::from_str(&value).map_err(error)
    }
    pub fn restore_quiz(&mut self, quiz: &Quiz) -> Result<()> {
        quiz.validate()?;
        if let Some(id) = &quiz.book_id {
            self.book(id)?;
        }
        self.connection
            .execute(
                "INSERT INTO quizzes(id,book_id,value) VALUES(?1,?2,?3)",
                params![
                    quiz.id,
                    quiz.book_id,
                    serde_json::to_string(quiz).map_err(error)?
                ],
            )
            .map_err(error)?;
        Ok(())
    }
    pub fn save_quiz(&mut self, input: QuizInput) -> Result<Quiz> {
        let old = if let Some(id) = &input.id {
            Some(self.quiz(id)?)
        } else {
            None
        };
        let mut quiz = Quiz {
            id: input.id.unwrap_or_else(|| uuid::Uuid::new_v4().to_string()),
            title: input.title.trim().into(),
            book_id: input.book_id,
            questions: input.questions,
            best_score: None,
            last_score: None,
            attempts: 0,
            created_at: old
                .as_ref()
                .map_or_else(crate::storage::now, |q| q.created_at),
        };
        if let Some(old) = old {
            if old.questions == quiz.questions {
                quiz.best_score = old.best_score;
                quiz.last_score = old.last_score;
                quiz.attempts = old.attempts;
            }
        }
        quiz.validate()?;
        if let Some(id) = &quiz.book_id {
            self.book(id)?;
        }
        self.connection.execute("INSERT INTO quizzes(id,book_id,value) VALUES(?1,?2,?3) ON CONFLICT(id) DO UPDATE SET book_id=excluded.book_id,value=excluded.value", params![quiz.id,quiz.book_id,serde_json::to_string(&quiz).map_err(error)?]).map_err(error)?;
        Ok(quiz)
    }
    pub fn submit_quiz(&mut self, id: &str, answers: Vec<usize>) -> Result<Quiz> {
        let mut quiz = self.quiz(id)?;
        if answers.len() != quiz.questions.len()
            || answers
                .iter()
                .zip(&quiz.questions)
                .any(|(a, q)| *a >= q.options.len())
        {
            return Err("Answer every question before submitting this quiz.".into());
        }
        let score = answers
            .iter()
            .zip(&quiz.questions)
            .filter(|(a, q)| q.options[**a] == q.answer)
            .count();
        quiz.best_score = Some(quiz.best_score.unwrap_or(0).max(score));
        quiz.last_score = Some(score);
        quiz.attempts = quiz.attempts.saturating_add(1);
        self.connection
            .execute(
                "UPDATE quizzes SET value=?1 WHERE id=?2",
                params![serde_json::to_string(&quiz).map_err(error)?, id],
            )
            .map_err(error)?;
        Ok(quiz)
    }
    pub fn delete_quiz(&mut self, id: &str) -> Result<()> {
        self.connection
            .execute("DELETE FROM quizzes WHERE id=?1", [id])
            .map_err(error)?;
        Ok(())
    }
    pub fn import_flashcards(&mut self, cards: Vec<crate::study::CardInput>) -> Result<usize> {
        if cards.is_empty() || cards.len() > 200 || cards.iter().any(|c| c.id.is_some()) {
            return Err("Import between 1 and 200 new cards at a time.".into());
        }
        let count = cards.len();
        self.connection
            .execute_batch("SAVEPOINT import_cards")
            .map_err(error)?;
        let result = cards.into_iter().try_for_each(|c| self.save_flashcard(c));
        match result {
            Ok(()) => self
                .connection
                .execute_batch("RELEASE import_cards")
                .map_err(error)?,
            Err(e) => {
                self.connection
                    .execute_batch("ROLLBACK TO import_cards; RELEASE import_cards")
                    .map_err(error)?;
                return Err(e);
            }
        }
        Ok(count)
    }
    pub fn quiz_exists(&self, id: &str) -> Result<bool> {
        Ok(self
            .connection
            .query_row("SELECT 1 FROM quizzes WHERE id=?1", [id], |_| Ok(()))
            .optional()
            .map_err(error)?
            .is_some())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn scoring_validation_reopen_and_atomic_card_import() {
        let root = std::env::temp_dir().join(format!("vern-quiz-{}", uuid::Uuid::new_v4()));
        std::fs::create_dir(&root).unwrap();
        let path = root.join("library.db");
        let mut db = Storage::open(&path).unwrap();
        let input = || QuizInput {
            id: None,
            title: "Atoms".into(),
            book_id: None,
            questions: vec![Question {
                question: "Element?".into(),
                options: vec!["Carbon".into(), "Water".into()],
                answer: "Carbon".into(),
                explanation: "Carbon is an element.".into(),
            }],
        };
        let saved = db.save_quiz(input()).unwrap();
        assert!(db.submit_quiz(&saved.id, vec![8]).is_err());
        assert_eq!(
            db.submit_quiz(&saved.id, vec![0]).unwrap().best_score,
            Some(1)
        );
        assert_eq!(
            db.submit_quiz(&saved.id, vec![1]).unwrap().last_score,
            Some(0)
        );
        drop(db);
        let mut db = Storage::open(&path).unwrap();
        assert_eq!(db.quizzes().unwrap()[0].attempts, 2);
        let mut edited = input();
        edited.id = Some(saved.id.clone());
        edited.questions[0].question = "Changed?".into();
        assert_eq!(db.save_quiz(edited).unwrap().attempts, 0);
        let mut bad = input();
        bad.questions[0].options[1] = " carbon ".into();
        assert!(db.save_quiz(bad).is_err());
        let card = |back: &str| crate::study::CardInput {
            id: None,
            book_id: None,
            position: None,
            deck: "Atoms".into(),
            front: "Element?".into(),
            back: back.into(),
        };
        assert!(db
            .import_flashcards(vec![card("Carbon"), card("")])
            .is_err());
        assert!(db.flashcards().unwrap().is_empty());
        assert_eq!(db.import_flashcards(vec![card("Carbon")]).unwrap(), 1);
        let bytes = db.backup_bytes().unwrap();
        let backup_path = root.join("backup.json");
        std::fs::write(&backup_path, bytes).unwrap();
        let backup = crate::transfer::read_backup(&backup_path).unwrap();
        let mut restored = Storage::open(std::path::Path::new(":memory:")).unwrap();
        restored.restore_backup(backup, false).unwrap();
        assert_eq!(restored.quizzes().unwrap().len(), 1);
        db.delete_quiz(&saved.id).unwrap();
        assert!(db.quizzes().unwrap().is_empty());
        drop(db);
        std::fs::remove_dir_all(root).unwrap();
    }
}
