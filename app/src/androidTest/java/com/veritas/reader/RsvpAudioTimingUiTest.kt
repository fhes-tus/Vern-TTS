package com.veritas.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.screens.RsvpSpeedReaderContent
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RsvpAudioTimingUiTest {
    @get:Rule val rule = createComposeRule()
    private class Transport : RsvpTransport {
        var plays = 0
        var offset = -1
        var closed = false
        override fun enter() { PlaybackStateStore.activeDocumentId = "rsvp-fixture"; PlaybackStateStore.isPlaying = false }
        override fun play(sentence: Int, offset: Int, rate: Float) {
            plays++; this.offset = offset; PlaybackStateStore.isPlaying = true
        }
        override fun accepts(position: SpeechPosition) = position.documentId == "rsvp-fixture"
        override fun pause() { PlaybackStateStore.isPlaying = false }
        override fun close() { closed = true; pause() }
    }
    @Test fun audioWordsFollowSourceEventsInsteadOfTheWpmTimer() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        DocumentRepository(context).prefs.edit().putBoolean("rsvp_audio_mode", false).commit()
        val transport = Transport()
        val document = ReaderDocument("rsvp-fixture", "Timing fixture", "TXT", "One two three four.", listOf("One two three four."))
        rule.mainClock.autoAdvance = false
        rule.setContent { MaterialTheme { RsvpSpeedReaderContent(document, 0, {}, transport) } }
        rule.onNode(isToggleable()).performClick()
        rule.onNodeWithContentDescription("Play").performClick()
        rule.mainClock.advanceTimeBy(3000)
        rule.onNodeWithText("One").assertIsDisplayed()
        assertEquals(1, transport.plays)
        rule.runOnIdle { PlaybackStateStore.speechPosition = SpeechPosition(document.id!!, 0, 8, 13, SpeechPositionTiming.WORD_RANGE, "first") }
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithText("three").assertIsDisplayed()
        rule.onNodeWithText("Audio · word timing").assertIsDisplayed()
        rule.mainClock.advanceTimeBy(2000)
        rule.onNodeWithText("three").assertIsDisplayed()
        rule.runOnIdle { PlaybackStateStore.speechPosition = SpeechPosition("different-document", 0, 0, 3, SpeechPositionTiming.WORD_RANGE, "obsolete") }
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithText("three").assertIsDisplayed()
        rule.runOnIdle { PlaybackStateStore.speechPosition = SpeechPosition(document.id!!, 0, 14, 19, SpeechPositionTiming.ESTIMATED_PCM, "first") }
        rule.mainClock.advanceTimeBy(64)
        rule.onNodeWithText("four.").assertIsDisplayed()
        rule.onNodeWithText("Audio · estimated word timing").assertIsDisplayed()
        rule.onNodeWithContentDescription("Pause").performClick()
        rule.mainClock.advanceTimeBy(2000)
        rule.onNodeWithContentDescription("Play").assertIsDisplayed()
        rule.runOnIdle { PlaybackStateStore.reset() }
    }
}
