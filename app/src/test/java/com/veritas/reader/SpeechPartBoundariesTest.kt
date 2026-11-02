package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test

class SpeechPartBoundariesTest {
    private fun segment(source: String, spoken: SpokenText = SpokenText.identity(source)) = SpeechSegment(
        "doc", "revision", 7, 0, source.length, source, spoken,
        BookCharacter("narrator", "Narrator"), VoiceSettings(), SpeechEngineFamily.ANDROID, 1.2f, .9f, 350L)

    @Test fun exactLimitAndOneOverKeepAllTextAndOriginalSpans() {
        for (source in listOf("abcdefgh", "abcdefghi")) {
            val parts = SpeechPlan.split(segment(source), 8)
            assertEquals(source, parts.joinToString("") { it.spoken.text })
            assertTrue(parts.all { it.spoken.text.length <= 8 })
            parts.forEach { part ->
                val offset = part.spoken.sourceOffset(0)
                assertEquals(part.spoken.text, source.substring(offset, part.spoken.sourceEndOffset(part.spoken.text.length)))
                assertEquals(7, part.sentenceIndex)
                assertEquals(1.2f, part.rate)
                assertEquals(.9f, part.pitch)
            }
            assertEquals(350L, parts.first().leadingPauseMs)
            assertTrue(parts.drop(1).all { it.leadingPauseMs == 0L })
        }
    }

    @Test fun expandedInputIsBoundedAfterTransformationAndKeepsTheFullReplacementSpan() {
        val source = "SQL next."
        val mapped = SpokenText.identity(source).replace(Regex("SQL")) { "database ".repeat(25).trim() }
        val parts = SpeechPlan.split(segment(source, mapped), 17)
        assertEquals(mapped.text, parts.joinToString("") { it.spoken.text })
        assertTrue(parts.all { it.spoken.text.length <= 17 })
        for (part in parts.dropLast(1)) {
            assertEquals(0, part.spoken.sourceOffset(0))
            assertEquals(3, part.spoken.sourceEndOffset(part.spoken.text.length))
        }
        val last = parts.last().spoken
        assertEquals(source.indexOf("next"), last.sourceOffset(last.text.indexOf("next")))
    }

    @Test fun supplementaryCharactersNeverSplitIntoUnpairedSurrogates() {
        val source = "a😀b😀c😀d"
        val parts = SpeechPlan.split(segment(source), 2)
        assertEquals(source, parts.joinToString("") { it.spoken.text })
        assertTrue(parts.none { Character.isHighSurrogate(it.spoken.text.last()) || Character.isLowSurrogate(it.spoken.text.first()) })
    }

    @Test fun combiningSequencesMoveWholeToTheNextPart() {
        val source = "A a\u0301\u0323 B"
        val parts = SpeechPlan.split(segment(source), 4)
        assertEquals(source, parts.joinToString("") { it.spoken.text })
        assertEquals("A ", parts.first().spoken.text)
        assertTrue(parts.any { it.spoken.text.contains("a\u0301\u0323") })
    }

    @Test fun anUnsplittableGraphemeFailsExplicitlyInsteadOfBeingDamaged() {
        val source = "a" + "\u0301".repeat(8)
        assertThrows(IllegalArgumentException::class.java) { SpeechPlan.split(segment(source), 4) }
    }

    @Test fun skippedWhitespaceDoesNotLoseTheFirstAudiblePartsLeadingPause() {
        val source = " ".repeat(30) + "Hello world"
        val parts = SpeechPlan.split(segment(source), 8)
        assertTrue(parts.none { it.spoken.text.isBlank() })
        assertEquals(350L, parts.first().leadingPauseMs)
        assertEquals(source.indexOf("Hello"), parts.first().spoken.sourceOffset(parts.first().spoken.text.indexOf("Hello")))
    }

    @Test fun longSelectionsRetainTheirEndingAndDoNotReclassifyLaterParts() {
        val source = "word ".repeat(350) + "FINAL WORDS."
        val original = segment(source).copy(sentenceIndex = -1)
        val parts = SpeechPlan.split(original, 80)
        assertEquals(source, parts.joinToString("") { it.spoken.text })
        assertTrue(parts.joinToString("") { it.spoken.text }.endsWith("FINAL WORDS."))
        assertEquals(source.length, parts.last().spoken.sourceEndOffset(parts.last().spoken.text.length))
        assertTrue(parts.all { it.sentenceIndex == -1 && it.role == original.role && it.voice == original.voice })
    }
}
