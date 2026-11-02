package com.veritas.reader

import com.veritas.reader.tts.NeuralSpeechText
import org.junit.Assert.*
import org.junit.Test

class NeuralSpeechTextTest {
    @Test fun copiedWordsKeepTheirPronunciationAndAccents() {
        assertEquals("cooperate café", NeuralSpeechText.prepare("co\u00ADoperate cafe\u0301"))
        assertEquals("don't well-being", NeuralSpeechText.prepare("don't well-being"))
    }
    @Test fun invisibleCharactersAndCopySpacingDoNotBecomeSpokenTokens() {
        assertEquals("Hello, world!", NeuralSpeechText.prepare("\uFEFFHello,\u00A0\n\tworld!\u0000"))
        assertEquals("reader", NeuralSpeechText.prepare("rea\u200Bder"))
    }
    @Test fun punctuationAndNumbersRetainMeaning() {
        assertEquals("Does Dr. Smith want 12.5%, or 13%?", NeuralSpeechText.prepare("Does Dr. Smith want 12.5%, or 13%?"))
        assertEquals("Ready? Yes!", NeuralSpeechText.prepare("Ready？ Yes！"))
    }
    @Test fun metadataAndDecorationAreNotNarrated() {
        assertEquals("Before. After.", NeuralSpeechText.prepare("Before. [[VERITAS_IMAGE:test]] After."))
        assertEquals("", NeuralSpeechText.prepare("| --- | --- |"))
    }

    @Test fun finalPreparationMapsComposedAccentsAndDeletedCharacters() {
        val source = "  Cafe\u0301\u00a0\tco\u00ADoperate 😀 next.  "
        val spoken = NeuralSpeechText.prepare(SpokenText.identity(source))
        assertEquals("Café cooperate 😀 next.", spoken.text)
        assertEquals(2, spoken.sourceOffset(0))
        assertEquals(7, spoken.sourceEndOffset(4))
        for (word in listOf("co", "operate", "😀", "next")) {
            assertEquals(word, source.indexOf(word), spoken.sourceOffset(spoken.text.indexOf(word)))
        }
        assertEquals(source.length, spoken.sourceOffset(spoken.text.length))
        assertEquals(source.indexOf("next.") + 5, spoken.sourceEndOffset(spoken.text.length))
        val again = NeuralSpeechText.prepare(spoken)
        assertEquals(spoken.text, again.text)
        for (index in spoken.text.indices) {
            assertEquals(spoken.sourceOffset(index), again.sourceOffset(index))
            assertEquals(spoken.sourceEndOffset(index + 1), again.sourceEndOffset(index + 1))
        }
    }

    @Test fun normalizationRetainsHangulAndReorderedCombiningSequences() {
        for (source in listOf("\u1100\u1161 next", "a\u0315\u0300 next", "\u212B next")) {
            val spoken = NeuralSpeechText.prepare(SpokenText.identity(source))
            assertEquals(java.text.Normalizer.normalize(source, java.text.Normalizer.Form.NFC), spoken.text)
            assertEquals(source.indexOf("next"), spoken.sourceOffset(spoken.text.indexOf("next")))
            assertEquals(source.indexOf(' '), spoken.sourceEndOffset(spoken.text.indexOf(' ')))
        }
    }
}
