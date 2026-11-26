package com.veritas.reader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AiStudyServiceLogicTest {
    @org.junit.Test
    fun explicitEmptyScopeCannotFallbackToTheBook() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            studyRequestText("   ") { error("Explicit scope must not read the book") }
        }
        assertEquals("selected", studyRequestText("selected") { error("Explicit scope has its own text") })
        assertEquals("whole book", studyRequestText(null) { "whole book" })
    }

    @org.junit.Test
    fun malformedPageRangeRemainsUnselected() {
        org.junit.Assert.assertNull(explicitStudyPageRange("", "3", 10))
        org.junit.Assert.assertNull(explicitStudyPageRange("5", "3", 10))
        org.junit.Assert.assertNull(explicitStudyPageRange("0", "3", 10))
        org.junit.Assert.assertNull(explicitStudyPageRange("5", "11", 10))
        assertEquals(3..5, explicitStudyPageRange("3", "5", 10))
    }

    @org.junit.Test
    fun providerProfileKeepsItsCredentialAndDestinationTogether() {
        val standard = AiStudyProfile.create(AiProvider.OPENAI, "", "https://other.example/completions", "first-key")
        val custom = AiStudyProfile.create(AiProvider.CUSTOM, "local-model", "https://other.example/completions", "second-key")
        assertEquals(AiProvider.OPENAI.defaultEndpoint, standard.endpoint)
        assertEquals("first-key", standard.apiKey)
        assertEquals("second-key", custom.apiKey)
        assertEquals("https://other.example/completions", custom.endpoint)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            AiStudyProfile.create(AiProvider.CUSTOM, "local-model", "http://other.example/completions", "second-key")
        }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            AiStudyProfile.create(AiProvider.CUSTOM, "local-model", "https://user@other.example/completions", "second-key")
        }
    }

    @org.junit.Test
    fun boundedExcerptRequiresTextAndPreservesCompleteCodePoints() {
        assertEquals("abc", boundedStudySource("abc😀def", 4))
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) { boundedStudySource("    text", 2) }
    }

    @Test
    fun verifyAiProviderDefaults() {
        assertEquals("gemini-4-hydrogen", AiProvider.GEMINI.defaultModel)
        assertEquals("gpt-6-luna", AiProvider.OPENAI.defaultModel)
        assertEquals("claude-haiku-5-5", AiProvider.ANTHROPIC.defaultModel)
        assertEquals("openai/gpt-oss-120b", AiProvider.GROQ.defaultModel)
        assertEquals("openai/gpt-6-luna", AiProvider.OPENROUTER.defaultModel)
    }

    @Test
    fun upgradesRetiredModelsOnlyForTheirOwnProvider() {
        assertEquals("gemini-4-hydrogen", AiStudyProfile.create(AiProvider.GEMINI, "models/gemini-2.0-flash", "", "").model)
        assertEquals("gpt-6-luna", AiStudyProfile.create(AiProvider.OPENAI, "gpt-4.5-preview", "", "").model)
        assertEquals("claude-sonnet-5-5", AiStudyProfile.create(AiProvider.ANTHROPIC, "claude-3-7-sonnet-latest", "", "").model)
        assertEquals("gpt-4o-mini", AiStudyProfile.create(AiProvider.OPENAI, "gpt-4o-mini", "", "").model)
        assertEquals("gpt-4.5-preview", AiStudyProfile.create(AiProvider.CUSTOM, "gpt-4.5-preview", "https://example.org/chat", "").model)
    }

    @Test
    fun verifyGeminiSseEventParsing() {
        val sseChunk = """{"candidates":[{"content":{"parts":[{"text":"Calculus is the mathematical study of continuous change."}]}}]}"""
        val json = JSONObject(sseChunk)
        val textPart = json.optJSONArray("candidates")
            ?.optJSONObject(0)
            ?.optJSONObject("content")
            ?.optJSONArray("parts")
            ?.optJSONObject(0)
            ?.optString("text", "")
        assertEquals("Calculus is the mathematical study of continuous change.", textPart)
    }

    @Test
    fun verifyOpenAiSseEventParsing() {
        val sseChunk = """{"choices":[{"delta":{"content":"Photosynthesis converts light into energy."}}]}"""
        val json = JSONObject(sseChunk)
        val delta = json.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("delta")
            ?.optString("content", "")
        assertEquals("Photosynthesis converts light into energy.", delta)
    }

    @Test
    fun verifyAnthropicSseEventParsing() {
        val sseChunk = """{"type":"content_block_delta","delta":{"text":"Newton's laws of motion."}}"""
        val json = JSONObject(sseChunk)
        assertEquals("content_block_delta", json.optString("type"))
        val delta = json.optJSONObject("delta")?.optString("text", "")
        assertEquals("Newton's laws of motion.", delta)
    }
}
