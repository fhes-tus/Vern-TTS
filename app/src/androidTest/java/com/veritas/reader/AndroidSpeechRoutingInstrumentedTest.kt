package com.veritas.reader

import android.media.AudioManager
import android.os.Bundle
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AndroidSpeechRoutingInstrumentedTest {
    @Test fun actualAndroidVoiceCompletesAndCapturesSessionRoutingEvidence() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".checks"))
        val voice = offlineEnglishTestVoice(context)
        val manager = AudioExportManager(context)
        val tts = manager.createReadyTts(VoiceSettings(enginePackage = voice.enginePackage, voiceName = voice.name, localeTag = voice.localeTag))
        val session = (context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager).generateAudioSessionId()
        val effect = SpeechEqualizer.create(session)
        val started = CountDownLatch(1); val complete = CountDownLatch(1)
        var failed = false
        val text = "This speech test checks the selected Android voice and its requested audio session. " +
            "The reader preserves each word and completes the final sentence."
        try {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { started.countDown() }
                override fun onDone(id: String?) { complete.countDown() }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) { failed = true; started.countDown(); complete.countDown() }
            })
            assertEquals(TextToSpeech.SUCCESS, tts.speak(text, TextToSpeech.QUEUE_FLUSH,
                Bundle().apply { putInt(TextToSpeech.Engine.KEY_PARAM_SESSION_ID, session) }, "routing-fixture"))
            assertTrue(started.await(20, TimeUnit.SECONDS))
            val providerUid = context.packageManager.getApplicationInfo(tts.defaultEngine, 0).uid
            val trackPattern = Regex("(?m)^\\s*(?:[A-Z]\\s+)?\\d+\\s+yes\\s+\\d+/\\s*$providerUid\\s+$session\\s+.*$")
            var dump = ""
            var activeTrack = ""
            for (attempt in 0 until 8) {
                dump = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("dumpsys media.audio_flinger"))
                    .bufferedReader().use { it.readText() }
                activeTrack = trackPattern.find(dump)?.value.orEmpty()
                if (activeTrack.isNotEmpty()) break
                SystemClock.sleep(150)
            }
            val evidence = File(context.filesDir, "android-tts-session.txt").apply {
                writeText("Requested session=$session engine=${tts.defaultEngine} voice=${voice.name}\n$dump")
            }
            preserveSpeechTestArtifact(context, evidence)
            assertTrue("No active provider AudioTrack with requested session $session", activeTrack.isNotEmpty())
            assertTrue(complete.await(60, TimeUnit.SECONDS)); assertFalse(failed)
            val raw = File(context.cacheDir, "android-sound-fixture.wav")
            val converted = File(context.filesDir, "android-voice-converted.wav")
            try {
                withTimeout(60000) { manager.synthesizePart(tts, "Read these words clearly, then finish quietly.", raw, "routing-file-fixture") }
                PcmWavWriter(converted, 24000).use { WavAudioNormalizer.append(raw, it, 24000) }
                assertTrue(converted.length() > 44)
                preserveSpeechTestArtifact(context, converted)
            } finally { raw.delete() }
            val json = File(context.filesDir, "android-speech-route.json").apply {
                writeText(JSONObject().put("engine", tts.defaultEngine).put("voice", voice.name).put("requestedSession", session)
                    .put("providerUid", providerUid).put("activeProviderTrack", activeTrack)
                    .put("speakCompleted", true).put("fileConversionCompleted", true)
                    .put("routeEvidence", "Inspect track session column; an effect row alone does not prove routing.").toString(2))
            }
            preserveSpeechTestArtifact(context, json)
        } finally { effect.release(); tts.stop(); tts.shutdown() }
    }
}
