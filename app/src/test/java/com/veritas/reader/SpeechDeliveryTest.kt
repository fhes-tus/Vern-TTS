package com.veritas.reader

import com.veritas.reader.tts.PcmPitchShift
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SpeechDeliveryTest {
    @Test fun neutralDeliveryStillBoundsEngineInputs() {
        val settings = NarrationSettings(punctuationExpressionEnabled = false)
        assertEquals(2f, NarrationAnalyzer.effectiveRate(8f, settings, "Ordinary prose."), 0f)
        assertEquals(.5f, NarrationAnalyzer.effectiveRate(.1f, settings, "Ordinary prose."), 0f)
        assertEquals(1.5f, NarrationAnalyzer.effectivePitch(8f, settings, "Ordinary prose."), 0f)
        assertEquals(.6f, NarrationAnalyzer.effectivePitch(.1f, settings, "Ordinary prose."), 0f)
    }

    @Test fun nonFiniteComponentsUseNeutralDefaultsAndKeepValidRoleTuning() {
        val settings = NarrationSettings(enabled = true, punctuationExpressionEnabled = false,
            characterProfiles = listOf(BookCharacter("narrator", "Narrator", rateMultiplier = .9f, pitchMultiplier = 1.1f)))
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(.9f, NarrationAnalyzer.effectiveRate(bad, settings, "Prose."), 0f)
            assertEquals(1.1f, NarrationAnalyzer.effectivePitch(bad, settings, "Prose."), 0f)
            val brokenRole = settings.copy(characterProfiles = listOf(BookCharacter("narrator", "Narrator", rateMultiplier = bad, pitchMultiplier = bad)))
            assertEquals(1.2f, NarrationAnalyzer.effectiveRate(1.2f, brokenRole, "Prose."), 0f)
            assertEquals(.9f, NarrationAnalyzer.effectivePitch(.9f, brokenRole, "Prose."), 0f)
        }
    }

    @Test fun invalidPunctuationStrengthUsesTheExistingDefaultStrength() {
        val expected = SpeechPunctuation.rateMultiplier("Ready?", .5f)
        assertEquals(expected, SpeechPunctuation.rateMultiplier("Ready?", Float.NaN), 0f)
    }

    @Test fun narrationValuesAcceptedByTheStoreSurviveJsonReload() {
        val settings = NarrationSettings(narratorRateMultiplier = .75f, narratorPitchMultiplier = .8f,
            dialogueRateMultiplier = 1.25f, dialoguePitchMultiplier = 1.25f)
        val restored = NarrationSettings.fromJson(settings.toJson())
        assertEquals(settings.narratorRateMultiplier, restored.narratorRateMultiplier, 0f)
        assertEquals(settings.narratorPitchMultiplier, restored.narratorPitchMultiplier, 0f)
        assertEquals(settings.dialogueRateMultiplier, restored.dialogueRateMultiplier, 0f)
        assertEquals(settings.dialoguePitchMultiplier, restored.dialoguePitchMultiplier, 0f)
    }

    @Test fun malformedJsonNumbersCannotReachSlidersOrDelivery() {
        val voice = VoiceSettings.fromJson(JSONObject().put("preferredRate", "NaN").put("preferredPitch", "Infinity"))
        assertEquals(1f, voice.preferredRate, 0f)
        assertEquals(1f, voice.preferredPitch, 0f)
        val role = BookCharacter.fromJson(JSONObject().put("pitchMultiplier", "NaN").put("rateMultiplier", "-Infinity"))
        assertEquals(1f, role.pitchMultiplier, 0f)
        assertEquals(1f, role.rateMultiplier, 0f)
    }

    @Test fun savingNonFiniteComponentsProducesValidJsonWithoutLosingOtherSettings() {
        val voice = VoiceSettings(voiceName = "test-voice", localeTag = "fr-FR", preferredRate = Float.NaN, preferredPitch = Float.POSITIVE_INFINITY)
        val restored = VoiceSettings.fromJson(voice.toJson())
        assertEquals(voice.voiceName, restored.voiceName)
        assertEquals(voice.localeTag, restored.localeTag)
        assertEquals(1f, restored.preferredRate, 0f)
        assertEquals(1f, restored.preferredPitch, 0f)
        val settings = NarrationSettings(enabled = true, punctuationExpressionStrength = Float.NaN,
            characterProfiles = listOf(BookCharacter("alice", "Alice", rateMultiplier = Float.NaN, pitchMultiplier = Float.NaN)))
        val narration = NarrationSettings.fromJson(settings.toJson())
        assertTrue(narration.enabled)
        assertEquals(.5f, narration.punctuationExpressionStrength, 0f)
        assertEquals("Alice", narration.characterProfiles.single().name)
        assertEquals(1f, narration.characterProfiles.single().rateMultiplier, 0f)
    }

    @Test fun invalidPcmPitchIsAnExplicitNeutralBypass() {
        val samples = shortArrayOf(100, -100, 50, -50)
        for (bad in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertSame(samples, PcmPitchShift.apply(samples, 24000, bad))
        }
    }

    @Test fun validSavedPreferencesAndNearNeutralPitchRemainExact() {
        val voice = VoiceSettings(preferredRate = 1.73f, preferredPitch = .97f)
        assertEquals(voice, VoiceSettings.fromJson(voice.toJson()))
        val settings = NarrationSettings(punctuationExpressionEnabled = false)
        assertEquals(1.0002f, NarrationAnalyzer.effectivePitch(1.0002f, settings, "Prose."), 0f)
        assertEquals(.65f, NarrationAnalyzer.effectivePitch(.65f, settings, "Prose."), 0f)
    }

    @Test fun normalizedDeliveryIsFiniteAndIdempotentForCacheKeys() {
        for (input in listOf(Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, -1f, 0f,
            Float.MIN_VALUE, .65f, .97f, 1.0002f, 1.4f, 2f, Float.MAX_VALUE)) {
            val rate = SpeechDelivery.rate(input)
            val pitch = SpeechDelivery.pitch(input)
            assertTrue(rate.isFinite() && rate in .5f..2f)
            assertTrue(pitch.isFinite() && pitch in .6f..1.5f)
            assertEquals(rate, SpeechDelivery.rate(rate), 0f)
            assertEquals(pitch, SpeechDelivery.pitch(pitch), 0f)
        }
        assertEquals(1.7f, SpeechDelivery.userRate(Float.NaN, 1.7f), 0f)
        assertEquals(.9f, SpeechDelivery.userPitch(Float.NaN, .9f), 0f)
    }

    @Test fun settersReceiveResolvedValuesAndFailureIsNotReportedAsSuccess() {
        val calls = mutableListOf<String>()
        assertTrue(SpeechDelivery.applyResolved(Float.NaN, Float.POSITIVE_INFINITY,
            { calls.add("rate=$it"); true }, { calls.add("pitch=$it"); true }))
        assertEquals(listOf("rate=1.0", "pitch=1.0"), calls)
        calls.clear()
        assertFalse(SpeechDelivery.applyResolved(1.2f, .9f,
            { calls.add("rate"); false }, { calls.add("pitch"); true }))
        assertEquals(listOf("rate"), calls)
        assertFalse(SpeechDelivery.applyResolved(1.2f, .9f, { true }, { false }))
    }

    @Test fun tutorialKeepsItsExistingWiderAndroidRange() {
        assertEquals(2.5f, SpeechDelivery.tutorialRate(2.5f), 0f)
        assertEquals(2f, SpeechDelivery.tutorialPitch(2f), 0f)
        assertEquals(1f, SpeechDelivery.tutorialRate(Float.NaN), 0f)
        assertEquals(1f, SpeechDelivery.tutorialPitch(Float.NEGATIVE_INFINITY), 0f)
    }
}
