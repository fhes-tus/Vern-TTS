package com.veritas.reader

import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.tts.NeuralSpeechText
import org.junit.Assert.*
import org.junit.Test

/** Native Unicode/source-map checks only; does not load models or change a library. */
class SpeechFrontendInstrumentedTest {
    private fun localSegment(source: String, locale: String = "en-US"): SpeechSegment {
        check(InstrumentationRegistry.getInstrumentation().targetContext.packageName.endsWith(".checks"))
        return SpeechPlan.segment("fixture", "revision", 0, source,
            VoiceSettings(enginePackage = VoiceManager.VERITAS_LITE, voiceName = "piper_fixture", localeTag = locale),
            NarrationSettings(), 1f, 1f, emptyList())
    }

    @Test fun finalNeuralTextAndUnicodeSpansAgreeOnAndroid() {
        val source = "Cafe\u0301\u00a0\tco\u00ADoperate 😀 next."
        val spoken = localSegment(source).spoken
        assertEquals("Café cooperate 😀 next.", spoken.text)
        assertEquals(spoken.text, NeuralSpeechText.prepare(spoken.text))
        assertEquals(0, spoken.sourceOffset(spoken.text.indexOf("Café")))
        assertEquals(5, spoken.sourceEndOffset(spoken.text.indexOf("Café") + 4))
        assertEquals(source.indexOf("next"), spoken.sourceOffset(spoken.text.indexOf("next")))
        assertEquals(source.length, spoken.sourceOffset(spoken.text.length))
    }

    @Test fun hangulCompositionDoesNotMoveTheNextWord() {
        val source = "\u1100\u1161 next"
        val spoken = localSegment(source, "ko-KR").spoken
        assertEquals("가 next", spoken.text)
        assertEquals(0, spoken.sourceOffset(0))
        assertEquals(2, spoken.sourceEndOffset(1))
        assertEquals(source.indexOf("next"), spoken.sourceOffset(spoken.text.indexOf("next")))
    }

    @Test fun tablesAndMarkersKeepTheirSourceSpans() {
        val source = "[[VERITAS_IMAGE:cover]] | Alpha | N/A | Beta |"
        val spoken = localSegment(source).spoken
        assertEquals("Alpha. N/A. Beta.", spoken.text)
        for (word in listOf("Alpha", "N/A", "Beta")) {
            val start = spoken.text.indexOf(word)
            assertEquals(source.indexOf(word), spoken.sourceOffset(start))
            assertEquals(source.indexOf(word) + word.length, spoken.sourceEndOffset(start + word.length))
        }
    }
}
