package com.veritas.reader

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AiStudyServiceLogicTest {

    @Test
    fun verifyAiProviderDefaults() {
        assertEquals("gemini-2.5-flash", AiProvider.GEMINI.defaultModel)
        assertEquals("gpt-4o-mini", AiProvider.OPENAI.defaultModel)
        assertEquals("claude-3-7-sonnet-latest", AiProvider.ANTHROPIC.defaultModel)
        assertEquals("llama-3.3-70b-versatile", AiProvider.GROQ.defaultModel)
        assertEquals("google/gemini-2.5-flash", AiProvider.OPENROUTER.defaultModel)
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
