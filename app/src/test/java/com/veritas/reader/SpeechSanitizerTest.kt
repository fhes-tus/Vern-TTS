package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechSanitizerTest {

    @Test
    fun `bullet glyphs become spaces and length is preserved`() {
        val input = "• I am a boy"
        val output = SpeechSanitizer.forSpeech(input)
        assertEquals(input.length, output.length)
        assertEquals("  I am a boy", output)
    }

    @Test
    fun `arrows checkmarks and box drawing are silenced`() {
        val input = "→ done ✓ next ► ─── end"
        val output = SpeechSanitizer.forSpeech(input)
        assertEquals(input.length, output.length)
        assertFalse(output.any { it == '→' || it == '✓' || it == '►' || it == '─' })
        assertTrue(output.contains("done"))
        assertTrue(output.contains("next"))
        assertTrue(output.contains("end"))
    }

    @Test
    fun `dot leaders collapse to one pause with length preserved`() {
        val input = "Chapter 1......Page 9"
        val output = SpeechSanitizer.forSpeech(input)
        assertEquals(input.length, output.length)
        assertEquals("Chapter 1.     Page 9", output)
    }

    @Test
    fun `normal punctuation and sentences pass through untouched`() {
        val input = "Dr. Smith said: \"It works!\" (12.5% up, e.g. now)."
        assertEquals(input, SpeechSanitizer.forSpeech(input))
    }

    @Test
    fun `ellipsis character becomes a single period`() {
        val output = SpeechSanitizer.forSpeech("Wait… what")
        assertEquals("Wait. what", output)
    }

    @Test
    fun `pure decoration chunks are not speakable`() {
        assertFalse(SpeechSanitizer.isSpeakable("•••"))
        assertFalse(SpeechSanitizer.isSpeakable("───────"))
        assertTrue(SpeechSanitizer.isSpeakable("• real text"))
        assertTrue(SpeechSanitizer.isSpeakable("plain sentence."))
    }

    @Test
    fun `two dots are left alone - only runs of three or more collapse`() {
        assertEquals("a..b", SpeechSanitizer.forSpeech("a..b"))
    }

    @Test
    fun `table rows are formatted with column periods and empty cell handling`() {
        val row = "| Quarter | Revenue | Profit | Notes |"
        val speech = SpeechSanitizer.forSpeech(row)
        assertEquals("Quarter. Revenue. Profit. Notes.", speech)
    }

    @Test
    fun `ambiguous table values retain their literal meaning and avoid double periods`() {
        val row = "| Product A | - | N/A | Done. |"
        val speech = SpeechSanitizer.forSpeech(row)
        assertEquals("Product A. -. N/A. Done.", speech)
        assertEquals("Product A. Done.", SpeechSanitizer.forSpeech("| Product A | | Done. |"))
        assertEquals("NA. None.", SpeechSanitizer.forSpeech("| NA | None |"))
    }

    @Test
    fun `table separator lines are completely muted and not speakable`() {
        val separator = "| --- | :---: | ---: |"
        assertEquals("", SpeechSanitizer.forSpeech(separator))
        assertFalse(SpeechSanitizer.isSpeakable(separator))
    }

    @Test
    fun `numeric and percentage lists are retained without structural chart evidence`() {
        val numericTicks = "0 10 20 30 40 50 60 70 80 90 100"
        assertEquals(numericTicks, SpeechSanitizer.forSpeech(numericTicks))
        assertTrue(SpeechSanitizer.isSpeakable(numericTicks))

        val percentageTicks = "0% 25% 50% 75% 100%"
        assertEquals(percentageTicks, SpeechSanitizer.forSpeech(percentageTicks))
        assertTrue(SpeechSanitizer.isSpeakable(percentageTicks))
    }

    @Test
    fun `month and quarter lists are retained without structural chart evidence`() {
        val monthTicks = "Jan Feb Mar Apr May Jun Jul Aug Sep Oct Nov Dec"
        assertEquals(monthTicks, SpeechSanitizer.forSpeech(monthTicks))
        assertTrue(SpeechSanitizer.isSpeakable(monthTicks))

        val quarterTicks = "Q1 Q2 Q3 Q4"
        assertEquals(quarterTicks, SpeechSanitizer.forSpeech(quarterTicks))
        assertTrue(SpeechSanitizer.isSpeakable(quarterTicks))
    }

    @Test
    fun `figure titles and captions are preserved and speakable`() {
        val caption = "Figure 1: Quarterly trends from 2021 to 2023."
        val speech = SpeechSanitizer.forSpeech(caption)
        assertEquals(caption, speech)
        assertTrue(SpeechSanitizer.isSpeakable(caption))
    }

    @Test
    fun `tableColumnIndexAt accurately maps character offsets to columns`() {
        val row = "| Name | Price | Stock |"
        // Playback has already mapped the callback to the original row.
        assertEquals(0, SpeechSanitizer.tableColumnIndexAt(row, 0))
        assertEquals(0, SpeechSanitizer.tableColumnIndexAt(row, 4))
        assertEquals(1, SpeechSanitizer.tableColumnIndexAt(row, row.indexOf("Price")))
        assertEquals(1, SpeechSanitizer.tableColumnIndexAt(row, 10))
        assertEquals(2, SpeechSanitizer.tableColumnIndexAt(row, row.indexOf("Stock")))
    }
}
