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
}
