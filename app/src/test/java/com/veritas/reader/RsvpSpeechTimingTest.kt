package com.veritas.reader

import com.veritas.reader.ui.screens.buildRsvpWordTokens
import com.veritas.reader.ui.screens.rsvpTokenAtSource
import org.junit.Assert.*
import org.junit.Test

class RsvpSpeechTimingTest {
    @Test fun sourceTokensRetainWhitespaceAndUtf16Ranges() {
        val source = "  One\t😀 two.\n"
        val tokens = buildRsvpWordTokens(listOf(source, "Next sentence."))
        assertEquals(listOf("One", "😀", "two.", "Next", "sentence."), tokens.map { it.word })
        tokens.take(3).forEach { assertEquals(it.word, source.substring(it.sourceStart, it.sourceEnd)) }
        assertEquals(2, tokens[0].sourceStart)
        assertEquals(2, tokens[1].sourceEnd - tokens[1].sourceStart)
        assertEquals(1, tokens[3].sentenceIndex)
        assertEquals(1, rsvpTokenAtSource(tokens, 0, tokens[1].sourceStart))
        assertEquals(3, rsvpTokenAtSource(tokens, 1, 0))
        assertEquals(-1, rsvpTokenAtSource(tokens, 3, 0))
    }
    @Test fun pcmEstimateDoesNotAdvanceWhileNoFramesHavePlayed() {
        val timing = SpeechTimingEstimate("One two three.")
        assertNull(timing.rangeAt(0, 24_000))
        assertNull(timing.rangeAt(100, 0))
        assertEquals(0..2, timing.rangeAt(1, 24_000))
        assertEquals(8..13, timing.rangeAt(24_000, 24_000))
        assertEquals(8..13, timing.rangeAt(48_000, 24_000))
    }
    @Test fun estimatedSpeechRangesMapBackThroughPronunciationExpansion() {
        val mapped = SpokenText.identity("Dr. Jane").replace(Regex("Dr\\.")) { "Doctor" }
        val timing = SpeechTimingEstimate(mapped.text)
        val start = timing.rangeAt(1, 24_000)!!.first
        assertEquals(0, mapped.sourceOffset(start))
        val end = timing.rangeAt(24_000, 24_000)!!.first
        assertEquals(4, mapped.sourceOffset(end))
    }
}
