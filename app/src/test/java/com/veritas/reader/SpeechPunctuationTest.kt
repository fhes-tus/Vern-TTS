package com.veritas.reader

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SpeechPunctuationTest {
    @Test fun quotedAndBracketedQuestionsKeepTheirMeaning() {
        listOf("Are you ready?", "“Are you ready?”", "'Really?!'", "(Ready？)", "هل أنت مستعد؟").forEach {
            assertEquals(it, SpeechPunctuation.Ending.QUESTION, SpeechPunctuation.ending(it))
        }
    }

    @Test fun normalExclamationAndHesitationHaveDifferentDelivery() {
        assertEquals(SpeechPunctuation.Ending.STATEMENT, SpeechPunctuation.ending("Ready."))
        assertEquals(SpeechPunctuation.Ending.EXCLAMATION, SpeechPunctuation.ending("“Watch out！”"))
        assertEquals(SpeechPunctuation.Ending.HESITATION, SpeechPunctuation.ending("Wait…"))
        assertEquals(SpeechPunctuation.Ending.HESITATION, SpeechPunctuation.ending("‘Wait...’"))
        assertEquals(SpeechPunctuation.Ending.STATEMENT, SpeechPunctuation.ending("No punctuation"))
    }

    @Test fun unicodePunctuationReachesSpeechWithoutShiftingOffsets() {
        val original = "Ready？ Yes！ Wait，listen：one；two。 هل تسمعني؟"
        val speech = SpeechSanitizer.forSpeech(original)
        assertEquals(original.length, speech.length)
        assertEquals("Ready? Yes! Wait,listen:one;two. هل تسمعني?", speech)
        assertEquals(original.indexOf("listen"), speech.indexOf("listen"))
    }

    @Test fun asciiQuestionsCommasAndDecimalsArePreserved() {
        val text = "Does Dr. Smith want 12.5%, or 13%?"
        assertEquals(text, SpeechSanitizer.forSpeech(text))
        assertEquals("| Ready? |", "| ${SpeechSanitizer.formatTableCellForSpeech("Ready？")} |")
        assertEquals("“Ready?”", SpeechSanitizer.formatTableCellForSpeech("“Ready？”"))
    }

    @Test fun questionCuesWorkWithoutCharacterNarration() {
        val settings = NarrationSettings(enabled = false)
        assertEquals(0.96f, NarrationAnalyzer.effectiveRate(1f, settings, "Ready?"), 0.0001f)
        assertEquals(1f, NarrationAnalyzer.effectivePitch(1f, settings, "“Ready?”"), 0.0001f)
        assertEquals(1f, NarrationAnalyzer.effectiveRate(1f, settings, "Ready."), 0f)
        assertEquals(1f, NarrationAnalyzer.effectivePitch(1f, settings, "Ready."), 0f)
    }

    @Test fun expressionCanBeDisabledOrReducedToZero() {
        listOf(NarrationSettings(punctuationExpressionEnabled = false),
            NarrationSettings(punctuationExpressionStrength = 0f)).forEach {
            assertEquals(1.2f, NarrationAnalyzer.effectiveRate(1.2f, it, "Ready?"), 0f)
            assertEquals(0.9f, NarrationAnalyzer.effectivePitch(0.9f, it, "Ready?"), 0f)
        }
    }

    @Test fun engineOptOutRetainsCharacterSettingsWithoutPunctuationCues() {
        val settings = NarrationSettings(enabled = true, characterProfiles = listOf(
            BookCharacter("narrator", "Narrator", rateMultiplier = 0.9f, pitchMultiplier = 1.1f)))
        assertEquals(0.9f, NarrationAnalyzer.effectiveRate(1f, settings, "Ready?", false), 0.0001f)
        assertEquals(1.1f, NarrationAnalyzer.effectivePitch(1f, settings, "Ready?", false), 0.0001f)
        assertEquals(0.864f, NarrationAnalyzer.effectiveRate(1f, settings, "Ready?"), 0.0001f)
    }

    @Test fun settingsRoundTripAndLegacyDefaultsPreserveControl() {
        val settings = NarrationSettings(punctuationExpressionEnabled = false, punctuationExpressionStrength = 0.8f)
        val restored = NarrationSettings.fromJson(settings.toJson())
        assertFalse(restored.punctuationExpressionEnabled)
        assertEquals(0.8f, restored.punctuationExpressionStrength, 0.0001f)
        assertTrue(NarrationSettings.fromJson(JSONObject()).punctuationExpressionEnabled)
        assertEquals(1f, NarrationSettings.fromJson(JSONObject().put("punctuationExpressionStrength", 5)).punctuationExpressionStrength, 0f)
    }

    @Test fun mixedParagraphIsNotGivenQuestionPitchBecauseOfAnEarlierQuestion() {
        val text = "Are you ready? The reader continues normally."
        assertEquals(SpeechPunctuation.Ending.STATEMENT, SpeechPunctuation.ending(text))
        assertEquals(1f, NarrationAnalyzer.effectivePitch(1f, NarrationSettings(), text), 0f)
    }
}
