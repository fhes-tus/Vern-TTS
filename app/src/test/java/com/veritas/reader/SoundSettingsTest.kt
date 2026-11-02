package com.veritas.reader

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SoundSettingsTest {
    @Test fun oldSettingsKeepShapingAndTheOriginalToneChoiceRoundTrips() {
        assertTrue(VoiceSettings.fromJson(JSONObject()).voiceShaping)
        val original = VoiceSettings(voiceShaping = false)
        assertEquals(original, VoiceSettings.fromJson(original.toJson()))
    }
    @Test fun castVoiceResolutionRetainsTheChosenProcessingMode() {
        val base = VoiceSettings(enginePackage = VoiceManager.VERITAS_STUDIO, voiceName = "kokoro_af_heart", voiceShaping = false)
        assertFalse(SpeechPlan.voiceFor("A sentence.", base, NarrationSettings()).voiceShaping)
    }
}
