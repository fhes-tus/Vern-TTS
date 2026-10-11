package com.veritas.reader

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StudyHubLogicTest {
    @Test fun fullAnswerIsNotConfusedWithItsFirstLetter() {
        val parsed = AiResultParser.parseQuiz("Q: Which element?\nA) Carbon\nB) Oxygen\nC) Iron\nAnswer: Carbon")
        assertEquals("Carbon", parsed.single().answer)
    }

    @Test fun fullAnswerUsesCanonicalOptionText() {
        val parsed = AiResultParser.parseQuiz("Q: Which element?\nA) Carbon\nB) Oxygen\nAnswer: carbon")
        assertEquals("Carbon", parsed.single().answer)
        assertTrue(parsed.single().isPlayable)
    }

    @Test fun unresolvedAnswersCannotProduceAnUnwinnableQuiz() {
        listOf("E", "Unlisted answer", "A) Oxygen").forEach { answer ->
            assertTrue(AiResultParser.parseQuiz("Q: Which element?\nA) Carbon\nB) Oxygen\nAnswer: $answer").isEmpty())
        }
    }

    @Test fun explicitLabelAndAnswerTextMustAgree() {
        val parsed = AiResultParser.parseQuiz("Q: Which element?\nA) Carbon\nB) Oxygen\nAnswer: A) Carbon")
        assertEquals("Carbon", parsed.single().answer)
    }

    @Test fun numericChoicesRetainTheirLabelsAndAreDetectedAsQuiz() {
        val text = "Q: Which element?\n1) Carbon\n2) Oxygen\nAnswer: 2"
        assertEquals("Oxygen", AiResultParser.parseQuiz(text).single().answer)
        assertTrue(AiResultParser.isLikelyQuiz(text))
        assertTrue(AiResultParser.parseFlashcards(text).isEmpty())
    }

    @Test fun jsonObjectChoicesResolveTheirActualLabels() {
        val parsed = AiResultParser.parseQuiz("""{"questions":[{"question":"Element?","options":{"B":"Oxygen","D":"Carbon"},"answer":"D"}]}""")
        assertEquals("Carbon", parsed.single().answer)
    }

    @Test fun numberedQuestionsStartANewQuestionAfterThePreviousAnswer() {
        val parsed = AiResultParser.parseQuiz("1. Element?\nA) Carbon\nB) Oxygen\nAnswer: A\n\n2. Gas?\nA) Carbon\nB) Oxygen\nAnswer: B")
        assertEquals(listOf("Element?", "Gas?"), parsed.map { it.question })
        assertEquals(listOf("Carbon", "Oxygen"), parsed.map { it.answer })
    }

    @Test fun jsonChoicesAreTrimmedBeforeScoring() {
        val parsed = AiResultParser.parseQuiz("""[{"question":"Element?","options":[" Carbon "," Oxygen "],"answer":"carbon"}]""")
        assertEquals(listOf("Carbon", "Oxygen"), parsed.single().options)
        assertTrue(parsed.single().isPlayable)
    }

    @Test fun badJsonQuestionDoesNotHideOtherValidQuestions() {
        val parsed = AiResultParser.parseQuiz("""[
            {"question":"Bad?","options":["Carbon","Oxygen"],"answer":"E"},
            {"question":"Good?","options":["Carbon","Oxygen"],"answerIndex":1}
        ]""")
        assertEquals("Good?", parsed.single().question)
        assertEquals("Oxygen", parsed.single().answer)
    }

    @Test fun duplicateOrBlankOptionsAreRejected() {
        listOf("[\"Carbon\",\"Carbon\"]", "[\"Carbon\",\" \" ]").forEach { options ->
            assertTrue(AiResultParser.parseQuiz("""[{"question":"Element?","options":$options,"answer":"Carbon"}]""").isEmpty())
        }
    }

    private val question = QuizQuestion("Element?", "Carbon", listOf("Carbon", "Oxygen"))

    @Test fun questionCountAlwaysFollowsActualQuestionsIncludingCopies() {
        val quiz = QuizSet.fromJson(QuizSet(title = "Quiz", questions = listOf(question)).toJson().put("totalQuestions", -9))
        assertEquals(1, quiz.totalQuestions)
        assertEquals(2, quiz.copy(questions = listOf(question, question)).totalQuestions)
        assertEquals(1, quiz.toJson().getInt("totalQuestions"))
    }

    @Test fun legacyPercentageIsConvertedOnceAndSurvivesRoundTrip() {
        val quiz = QuizSet(title = "Quiz", questions = List(4) { question }, bestScore = 75)
        assertEquals(3, quiz.calibratedBestScore)
        val restored = QuizSet.fromJson(quiz.toJson())
        assertEquals(3, restored.bestScore)
        assertEquals(3, restored.calibratedBestScore)
        assertEquals(-1, quiz.copy(bestScore = -1).calibratedBestScore)
    }

    @Test fun invalidStoredQuizIsKeptButCannotBeScored() {
        val quiz = QuizSet.fromJson(QuizSet(title = "Old import", questions = listOf(question.copy(answer = "E"))).toJson())
        assertEquals("Old import", quiz.title)
        assertFalse(quiz.isPlayable)
        assertFalse(quiz.copy(questions = emptyList(), bestScore = 0).isPlayable)
    }

    @Test fun pastedLegacyFlashcardDoesNotRequireABookId() {
        val card = FlashcardProgress.fromJson(JSONObject("""{"id":"card","front":"Term","back":"Meaning"}"""))
        assertEquals("", card.documentId)
        assertTrue(card.isDue(1))
    }

    @Test fun dueQueueUsesScheduleEvenWhenPreviousRecallWasGood() {
        val cards = listOf(
            FlashcardProgress("new", front = "Q", back = "A"),
            FlashcardProgress("overdue", front = "Q", back = "A", recall = "good", nextReviewDueTimestamp = 100),
            FlashcardProgress("scheduled", front = "Q", back = "A", recall = "hard", nextReviewDueTimestamp = 300)
        )
        val review = StudyReviewSummary.from(cards, 200)
        assertEquals(listOf("new", "overdue"), review.dueCards.map { it.id })
        assertEquals(33, review.notDuePercent)
        assertEquals(0, StudyReviewSummary.from(cards, 300).notDuePercent)
        assertEquals(0, StudyReviewSummary.from(emptyList(), 300).notDuePercent)
    }
}
