package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiResultParserTest {

    @Test
    fun `parses Q A pairs with preamble chatter`() {
        val cards = AiResultParser.parseFlashcards(
            """
            Sure! Here are your flashcards:

            Q: What is photosynthesis?
            A: The process plants use to convert light into chemical energy.

            Q: Where does it occur?
            A: In the chloroplasts.
            """.trimIndent()
        )
        assertEquals(2, cards.size)
        assertEquals("What is photosynthesis?", cards[0].front)
        assertTrue(cards[1].back.contains("chloroplasts"))
    }

    @Test
    fun `parses Front Back and markdown bold variants`() {
        val cards = AiResultParser.parseFlashcards(
            """
            **Q:** What is osmosis?
            **A:** Movement of water across a membrane.

            Front: Define diffusion
            Back: Movement of particles from high to low concentration.
            """.trimIndent()
        )
        assertEquals(2, cards.size)
        assertEquals("What is osmosis?", cards[0].front)
        assertEquals("Define diffusion", cards[1].front)
    }

    @Test
    fun `multi-line answers are joined`() {
        val cards = AiResultParser.parseFlashcards(
            "Q: List the states of matter\nA: Solid, liquid,\ngas, and plasma."
        )
        assertEquals(1, cards.size)
        assertTrue(cards[0].back.contains("plasma"))
    }

    @Test
    fun `garbage input produces no cards`() {
        assertTrue(AiResultParser.parseFlashcards("just some prose with no structure").isEmpty())
        assertTrue(AiResultParser.parseFlashcards("").isEmpty())
    }

    @Test
    fun `parses quiz with letter answer resolution`() {
        val quiz = AiResultParser.parseQuiz(
            """
            Q: Which organelle produces ATP?
            A) Nucleus
            B) Mitochondria
            C) Ribosome
            D) Golgi body
            Answer: B
            Explanation: Mitochondria are the powerhouse of the cell.
            """.trimIndent()
        )
        assertEquals(1, quiz.size)
        assertEquals("Mitochondria", quiz[0].answer)
        assertEquals(4, quiz[0].options.size)
        assertTrue(quiz[0].explanation.contains("powerhouse"))
    }

    @Test
    fun `parses multiple quiz questions separated by blank lines`() {
        val quiz = AiResultParser.parseQuiz(
            """
            Q: First question?
            A) one
            B) two
            Answer: A

            Q: Second question?
            A) alpha
            B) beta
            C) gamma
            Answer: C
            Explanation: Because gamma.
            """.trimIndent()
        )
        assertEquals(2, quiz.size)
        assertEquals("one", quiz[0].answer)
        assertEquals("gamma", quiz[1].answer)
    }

    @Test
    fun `quiz without enough options is dropped`() {
        val quiz = AiResultParser.parseQuiz("Q: Broken?\nA) only one option\nAnswer: A")
        assertTrue(quiz.isEmpty())
    }

    @Test
    fun `parses flashcards from markdown table`() {
        val markdownTable = """
            Here is your table:
            | Term | Definition |
            | --- | --- |
            | Velocity | Speed in a given direction |
            | Acceleration | Rate of change of velocity |
        """.trimIndent()
        val cards = AiResultParser.parseFlashcards(markdownTable)
        assertEquals(2, cards.size)
        assertEquals("Velocity", cards[0].front)
        assertEquals("Speed in a given direction", cards[0].back)
        assertEquals("Acceleration", cards[1].front)
    }

    @Test
    fun `parses flashcards from numbered bold definitions`() {
        val text = """
            Key concepts:
            1. **Entropy**: A measure of disorder in a closed system.
            2. **Enthalpy**: Total heat content of a system.
        """.trimIndent()
        val cards = AiResultParser.parseFlashcards(text)
        assertEquals(2, cards.size)
        assertEquals("Entropy", cards[0].front)
        assertEquals("A measure of disorder in a closed system.", cards[0].back)
    }

    @Test
    fun `parses flashcards from raw JSON array`() {
        val json = """
            [
              {"front": "Mitochondria", "back": "Powerhouse of the cell"},
              {"front": "Ribosome", "back": "Protein synthesizer"}
            ]
        """.trimIndent()
        val cards = AiResultParser.parseFlashcards(json)
        assertEquals(2, cards.size)
        assertEquals("Mitochondria", cards[0].front)
        assertEquals("Protein synthesizer", cards[1].back)
    }

    @Test
    fun `parses quiz from JSON format`() {
        val json = """
            {
              "quiz": [
                {
                  "question": "What is the capital of France?",
                  "options": ["Berlin", "Madrid", "Paris", "Rome"],
                  "answer": "Paris",
                  "explanation": "Paris is the capital and largest city of France."
                }
              ]
            }
        """.trimIndent()
        val quiz = AiResultParser.parseQuiz(json)
        assertEquals(1, quiz.size)
        assertEquals("What is the capital of France?", quiz[0].question)
        assertEquals("Paris", quiz[0].answer)
        assertEquals(4, quiz[0].options.size)
        assertEquals("Paris is the capital and largest city of France.", quiz[0].explanation)
    }

    @Test
    fun `SM-2 spaced repetition scheduler computes intervals accurately`() {
        val card = FlashcardProgress(
            id = "c1",
            front = "Front",
            back = "Back",
            recall = "",
            repetitionCount = 0,
            intervalDays = 1,
            easeFactor = 2.5f
        )

        val againCard = SpacedRepetitionScheduler.rateCard(card, SpacedRepetitionScheduler.BUCKET_AGAIN)
        assertEquals(0, againCard.repetitionCount)
        assertEquals(1, againCard.intervalDays)
        assertEquals("1d", SpacedRepetitionScheduler.previewNextInterval(card, "again"))

        val goodCard = SpacedRepetitionScheduler.rateCard(card, SpacedRepetitionScheduler.BUCKET_GOOD)
        assertEquals(1, goodCard.repetitionCount)
        assertEquals(1, goodCard.intervalDays)

        val goodCard2 = SpacedRepetitionScheduler.rateCard(goodCard, SpacedRepetitionScheduler.BUCKET_GOOD)
        assertEquals(2, goodCard2.repetitionCount)
        assertEquals(3, goodCard2.intervalDays)

        val easyCard = SpacedRepetitionScheduler.rateCard(card, SpacedRepetitionScheduler.BUCKET_EASY)
        assertEquals(1, easyCard.repetitionCount)
        assertEquals(3, easyCard.intervalDays)
    }
}
