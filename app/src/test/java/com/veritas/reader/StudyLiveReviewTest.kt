package com.veritas.reader

import java.io.EOFException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class StudyLiveReviewTest {
    @Test
    fun againSchedulesOnceAndKeepsRetriesInSessionOrder() {
        val initial = FlashcardReviewQueue()
        assertTrue(initial.shouldSchedule("a"))
        val failed = initial.rate("a", "again", false).rate("b", "again", false)
        assertFalse(failed.shouldSchedule("a"))
        assertEquals(listOf("a", "b"), failed.retryIds)
        val triedAgain = failed.rate("a", "again", true)
        assertEquals(listOf("b", "a"), triedAgain.retryIds)
        val passed = triedAgain.rate("a", "easy", true)
        assertFalse(passed.shouldSchedule("a"))
        assertEquals(listOf("b"), passed.retryIds)
        assertTrue(passed.remove("b").retryIds.isEmpty())
    }

    @Test
    fun openAiStyleReadsMultilineDataAndIgnoresRoleAndUsage() {
        val body = """
            : status
            data: {"choices":[{"delta":{"role":"assistant"},"finish_reason":null}]}

            data: {"choices":[{"delta":
            data: {"content":"Hello"}}]}

            data: {"choices":[{"delta":{"content":" world"},"finish_reason":"stop"}]}

            data: {"choices":[],"usage":{"total_tokens":5}}
        """.trimIndent()
        for (provider in listOf(AiProvider.OPENAI, AiProvider.GROQ, AiProvider.OPENROUTER)) {
            assertEquals(listOf("Hello", " world"), text(provider, body))
        }
    }

    @Test
    fun geminiReadsVisiblePartsAndAnthropicReadsTextDeltas() {
        val gemini = """
            data: {"candidates":[{"content":{"parts":[{"thought":true,"text":"reasoning"},{"text":"First "},{"text":"second"}]}}]}

            data: {"candidates":[{"finishReason":"STOP"}]}
        """.trimIndent()
        val anthropic = """
            event: content_block_delta
            data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"Claude"}}

            event: message_stop
            data: {"type":"message_stop"}
        """.trimIndent()
        assertEquals(listOf("First ", "second"), text(AiProvider.GEMINI, gemini))
        assertEquals(listOf("Claude"), text(AiProvider.ANTHROPIC, anthropic))
    }

    @Test
    fun answerWithoutTerminalEventFailsEvenAfterText() {
        val body = """data: {"choices":[{"delta":{"content":"unfinished"}}]}"""
        assertThrows(EOFException::class.java) { text(AiProvider.OPENAI, body) }
    }

    @Test
    fun providerErrorsAndLengthStopsCannotBecomeSuccessfulAnswers() {
        val error = """data: {"error":{"message":"private configuration detail"}}"""
        val cutoff = """data: {"choices":[{"delta":{"content":"part"},"finish_reason":"length"}]}"""
        val thrown = assertThrows(IllegalStateException::class.java) { text(AiProvider.OPENROUTER, error) }
        assertFalse(thrown.message.orEmpty().contains("private configuration detail"))
        assertThrows(IllegalStateException::class.java) { text(AiProvider.OPENAI, cutoff) }
    }

    private fun text(provider: AiProvider, body: String) = runBlocking {
        buildList { readStudyStream(body.reader().buffered(), provider) { add(it) } }
    }
}
