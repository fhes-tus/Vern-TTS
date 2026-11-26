package com.veritas.reader

import com.veritas.reader.tts.NeuralSpeechText
import org.junit.Assert.*
import org.junit.Test

class NeuralSpeechTextTest {
    @Test fun kittenTailStartsAtFullGainAndEndsAtSilence() {
        val samples = FloatArray(2400) { .5f }
        com.veritas.reader.tts.fadeKittenTail(samples, 24000)
        assertEquals(.5f, samples[2040], 0f)
        assertTrue(samples[2220] in 0f..0.25f)
        assertEquals(0f, samples.last(), 0f)
        assertTrue(samples.take(2040).all { it == .5f })
    }
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

    @Test fun kittenLiteAndStudioReceiveNaturalSentencePunctuation() {
        val voices = mapOf(
            SpeechEngineFamily.KITTEN to "kitten_0",
            SpeechEngineFamily.PIPER to "piper_en_us_ryan",
            SpeechEngineFamily.KOKORO to "kokoro_af_heart",
        )
        val cases = mapOf(
            "Kofi is a boy!." to "Kofi is a boy!",
            "She is the girl who said \"I am going to school.\"" to "She is the girl who said I am going to school.",
            "She asked, “Ready?!”." to "She asked, Ready?",
            "Wait...?! Then he ran !!" to "Wait? Then he ran!",
            "\"No,\" she said." to "No, she said.",
            "‘Don't stop!’" to "Don't stop!",
            "She whispered (ready?)." to "She whispered (ready)?",
            "She asked, “Are you sure?!”; I said, ‘Yes.’" to "She asked, Are you sure? I said, Yes.",
            "Listen: “Take a breath,” she said; “then begin.”" to "Listen: Take a breath, she said; then begin.",
            "First,, breathe; ; then wait…" to "First, breathe; then wait.",
            "Wait . . . for me." to "Wait. for me.",
            "!?." to "",
            " [ … ] — " to "",
        )

        for ((family, voiceName) in voices) {
            for ((source, expected) in cases) {
                val segment = SpeechPlan.segment(
                    "book", "v1", 0, source, VoiceSettings(voiceName = voiceName, localeTag = "en-US"),
                    NarrationSettings(), 1f, 1f, emptyList(),
                )
                assertEquals(voiceName, family, segment.family)
                assertEquals("$voiceName / $source", expected, segment.spoken.text)
                assertEquals("$voiceName / $source at synthesis", expected, NeuralSpeechText.prepare(segment.spoken.text))
            }
        }
    }

    @Test fun quotesAndRedundantStopsLeaveSourceWordOffsetsIntact() {
        val source = "She said, \"Go to school!.\""
        val spoken = NeuralSpeechText.prepare(SpokenText.identity(source))
        assertEquals("She said, Go to school!", spoken.text)
        for (word in listOf("She", "said", "Go", "to", "school")) {
            val index = spoken.text.indexOf(word)
            assertEquals(source.indexOf(word), spoken.sourceOffset(index))
            assertEquals(source.indexOf(word) + word.length, spoken.sourceEndOffset(index + word.length))
        }
    }

    @Test fun proseCleanupPreservesApostrophesMeasurementsAndLiteralPunctuation() {
        for (source in listOf(
            "'Twas the teachers' plan. Don't change it.",
            "Use a 6\" screen and a 5'11\" shelf.",
            "In the ’90s, the writers’ group met at 12:30.",
            "Try https://example.com/?q=a..b",
            "Use std::string and a 3.14 threshold.",
        )) {
            assertEquals(source, NeuralSpeechText.prepare(source))
        }
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
