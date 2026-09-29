package com.veritas.reader

import com.veritas.reader.tts.VeritasAudioBuffer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers the two pieces of playback sequencing that decide what gets synthesized and
 * how a deck is paced. Both used to live inline in coroutine and service bodies where
 * an off-by-one could only be found by ear.
 */
class PlaybackPipelineLogicTest {

    // --- Look-ahead window -------------------------------------------------------

    @Test
    fun windowStartsAfterTheCurrentSentence() {
        // The current sentence is resumable from a character offset, so the look-ahead
        // must never cache it — playSentencePcm owns that text.
        assertEquals(3 to 7, VeritasAudioBuffer.prebufferWindow(chunkCount = 40, currentIndex = 2, windowSize = 4))
    }

    @Test
    fun windowClampsToTheEndOfTheDocument() {
        // Reading the last few sentences must not walk past the end of the chunk list.
        assertEquals(38 to 40, VeritasAudioBuffer.prebufferWindow(chunkCount = 40, currentIndex = 37, windowSize = 4))
        assertEquals(40 to 40, VeritasAudioBuffer.prebufferWindow(chunkCount = 40, currentIndex = 39, windowSize = 4))
        assertEquals(40 to 40, VeritasAudioBuffer.prebufferWindow(chunkCount = 40, currentIndex = 99, windowSize = 4))
    }

    @Test
    fun windowIsEmptyForAnEmptyDocument() {
        assertEquals(0 to 0, VeritasAudioBuffer.prebufferWindow(chunkCount = 0, currentIndex = 0, windowSize = 4))
    }

    @Test
    fun selectionReadingDoesNotProduceNegativeIndices() {
        // speakSelectionText plays with index -1, which has no place in the document's
        // chunk list. The window has to start at 0 rather than at -1 + 1 of something.
        assertEquals(0 to 4, VeritasAudioBuffer.prebufferWindow(chunkCount = 40, currentIndex = -1, windowSize = 4))
        assertEquals(0 to 4, VeritasAudioBuffer.prebufferWindow(chunkCount = 40, currentIndex = -9, windowSize = 4))
    }

    @Test
    fun zeroWindowDisablesLookAhead() {
        assertEquals(3 to 3, VeritasAudioBuffer.prebufferWindow(chunkCount = 40, currentIndex = 2, windowSize = 0))
    }

    // --- Slide cadence -----------------------------------------------------------

    @Test
    fun nonDeckDocumentsGetNoLeadingSilence() {
        assertEquals(0L, PlaybackService.leadingSilenceMs(null, 0))
    }

    @Test
    fun eachSlideOpensWithATransitionBeatThenATitleBeat() {
        // Two sentences on slide 1, three on slide 2.
        val pages = intArrayOf(1, 1, 2, 2, 2)

        assertEquals(300L, PlaybackService.leadingSilenceMs(pages, 0)) // slide 1 opens
        assertEquals(250L, PlaybackService.leadingSilenceMs(pages, 1)) // after its title
        assertEquals(300L, PlaybackService.leadingSilenceMs(pages, 2)) // slide 2 opens
        assertEquals(250L, PlaybackService.leadingSilenceMs(pages, 3)) // after its title
        assertEquals(0L, PlaybackService.leadingSilenceMs(pages, 4))   // body text runs on
    }

    @Test
    fun singleSentenceSlidesStillOpenWithATransitionBeat() {
        val pages = intArrayOf(1, 2, 3)
        assertEquals(300L, PlaybackService.leadingSilenceMs(pages, 0))
        assertEquals(300L, PlaybackService.leadingSilenceMs(pages, 1))
        assertEquals(300L, PlaybackService.leadingSilenceMs(pages, 2))
    }

    @Test
    fun outOfRangeIndicesAreSilent() {
        val pages = intArrayOf(1, 1, 2)
        assertEquals(0L, PlaybackService.leadingSilenceMs(pages, -1))
        assertEquals(0L, PlaybackService.leadingSilenceMs(pages, 3))
        assertEquals(0L, PlaybackService.leadingSilenceMs(intArrayOf(), 0))
    }

    @Test
    fun tableRowGetsBreathingSilence() {
        val row = "| Quarter | Revenue | Profit |"
        assertEquals(350L, PlaybackService.leadingSilenceMs(null, 0, row))
    }
}
