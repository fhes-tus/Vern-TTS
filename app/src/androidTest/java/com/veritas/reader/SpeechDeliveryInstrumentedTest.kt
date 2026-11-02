package com.veritas.reader

import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.tts.TtsEngine
import com.veritas.reader.tts.VeritasAudioBuffer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class SpeechDeliveryInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext.also { check(it.packageName.endsWith(".checks")) }

    @Test fun finitePreferenceBoundariesSurviveTheActualAndroidStore() {
        val repository = DocumentRepository(context)
        val previousVoice = repository.loadVoiceSettings()
        val previousNarration = repository.loadNarrationSettings()
        val previousMemory = repository.prefs.getString("doc_voice_memory", null)
        val id = "delivery-${UUID.randomUUID()}"
        try {
            val valid = repository.saveVoiceSettings(VoiceSettings(preferredRate = 1.73f, preferredPitch = .97f))
            assertEquals(valid, repository.loadVoiceSettings())
            val repaired = repository.saveVoiceSettings(valid.copy(preferredRate = Float.NaN, preferredPitch = Float.POSITIVE_INFINITY))
            assertEquals(1f, repaired.preferredRate, 0f)
            assertEquals(1f, repaired.preferredPitch, 0f)
            assertEquals(repaired, repository.loadVoiceSettings())

            val narration = NarrationSettings(narratorRateMultiplier = .75f, narratorPitchMultiplier = .8f,
                dialogueRateMultiplier = 1.25f, dialoguePitchMultiplier = 1.25f,
                characterProfiles = listOf(BookCharacter("narrator", "Narrator", rateMultiplier = .5f, pitchMultiplier = 1.4f)))
            assertEquals(narration, repository.saveNarrationSettings(narration))
            assertEquals(narration, repository.loadNarrationSettings())
            val fixed = repository.saveNarrationSettings(narration.copy(punctuationExpressionStrength = Float.NaN,
                characterProfiles = listOf(BookCharacter("alice", "Alice", rateMultiplier = Float.NaN, pitchMultiplier = Float.NEGATIVE_INFINITY))))
            assertEquals(fixed, repository.loadNarrationSettings())
            assertEquals(.5f, fixed.punctuationExpressionStrength, 0f)
            assertEquals(1f, fixed.characterProfiles.single().pitchMultiplier, 0f)

            repository.saveDocVoiceMemory(id, 1.73f, .97f)
            assertEquals(1.73f to .97f, repository.loadDocVoiceMemory(id))
            repository.saveDocVoiceMemory(id, Float.NaN, Float.NEGATIVE_INFINITY)
            assertEquals(1f to 1f, repository.loadDocVoiceMemory(id))
        } finally {
            repository.saveVoiceSettings(previousVoice)
            repository.saveNarrationSettings(previousNarration)
            repository.prefs.edit().apply {
                if (previousMemory == null) remove("doc_voice_memory") else putString("doc_voice_memory", previousMemory)
            }.commit()
        }
    }

    @Test fun resolvedPlansKeepEngineExpressionPolicyAndExportRejectsNonFinitePropagation() = runBlocking {
        val system = offlineEnglishTestVoice(context)
        val narration = NarrationSettings(enabled = true, characterProfiles = listOf(
            BookCharacter("narrator", "Narrator", rateMultiplier = .9f, pitchMultiplier = 1.1f)))
        val voice = VoiceSettings(enginePackage = system.enginePackage, voiceName = system.name, localeTag = "en-US")
        val android = SpeechPlan.segment("fixture", "revision", 0, "Ready?", voice, narration, Float.NaN, Float.POSITIVE_INFINITY, emptyList())
        val local = SpeechPlan.segment("fixture", "revision", 1, "Ready?",
            VoiceSettings(enginePackage = VoiceManager.VERITAS_LITE, voiceName = "piper_fixture", localeTag = "en-US"),
            narration, Float.NaN, Float.POSITIVE_INFINITY, emptyList())
        assertEquals(.864f, android.rate, .0001f)
        assertEquals(.9f, local.rate, 0f)
        assertEquals(1.1f, android.pitch, 0f)
        assertEquals(android.pitch, local.pitch, 0f)

        val calls = mutableListOf<Pair<Float, Float>>()
        val exporter = MixedAudioExporter(context, createNeural = { object : TtsEngine {
            override val sampleRate = 24000
            override fun isReady() = true
            override suspend fun synthesize(sentence: String, rate: Float, pitch: Float): ShortArray {
                calls.add(rate to pitch)
                return ShortArray(2400) { 100 }
            }
            override fun shutdown() = Unit
        } })
        val before = DocumentRepository(context).loadVoiceSettings()
        val result = exporter.export("Delivery fixture", listOf(
            android.copy(rate = Float.NaN, pitch = Float.POSITIVE_INFINITY),
            local.copy(rate = Float.NEGATIVE_INFINITY, pitch = Float.NaN)))
        try {
            assertEquals(listOf(1f to 1f), calls)
            assertEquals(2, result.synthesizedParts)
            assertTrue(result.file.length() > 44 + 4800)
            assertEquals(before, DocumentRepository(context).loadVoiceSettings())
        } finally { result.file.delete() }
    }

    @Test fun aheadAndDemandBufferRequestsReceiveFiniteResolvedParameters() {
        val calls = Collections.synchronizedList(mutableListOf<Pair<Float, Float>>())
        val ahead = CountDownLatch(1)
        val buffer = VeritasAudioBuffer(context, object : TtsEngine {
            override val sampleRate = 24000
            override fun isReady() = true
            override suspend fun synthesize(sentence: String, rate: Float, pitch: Float): ShortArray {
                calls.add(rate to pitch)
                if (sentence == "ahead") ahead.countDown()
                return ShortArray(2400) { 100 }
            }
            override fun shutdown() = Unit
        })
        try {
            buffer.prebufferAhead(1, -1, 1, Float.NaN, Float.POSITIVE_INFINITY) { "ahead" }
            assertTrue(ahead.await(20, TimeUnit.SECONDS))
            buffer.flush()
            for ((rate, pitch) in listOf(Float.NaN to Float.POSITIVE_INFINITY, Float.MAX_VALUE to Float.MAX_VALUE, 1.23f to .97f)) {
                val done = CountDownLatch(1)
                var succeeded = false
                buffer.playSentencePcm(-1, "demand", rate, pitch) { succeeded = it; done.countDown() }
                assertTrue(done.await(20, TimeUnit.SECONDS))
                assertTrue("Short PCM delivery failed", succeeded)
            }
            assertEquals(listOf(1f to 1f, 1f to 1f, 2f to 1.5f, 1.23f to .97f), calls.toList())
        } finally { buffer.shutdown() }
    }

    @Test fun actualAndroidVoicePreviewsAcceptValidAndRepairedDelivery() = runBlocking {
        val voice = offlineEnglishTestVoice(context)
        try {
            for ((rate, pitch) in listOf(1.23f to .97f, Float.NaN to Float.POSITIVE_INFINITY)) {
                val done = CountDownLatch(1)
                var failure: String? = "Preview did not complete"
                instrumentation.runOnMainSync {
                    VoiceManager.previewVoice(context, voice.enginePackage, voice.name, "The reading continues.", rate, pitch) {
                        failure = it; done.countDown()
                    }
                }
                assertTrue("Actual Android preview timed out", done.await(30, TimeUnit.SECONDS))
                assertNull(failure)
            }
        } finally { instrumentation.runOnMainSync { VoiceManager.releasePreviewEngine() } }
    }

    @Test fun invalidControlComponentsRetainTheCurrentValidServiceValues() {
        val repository = DocumentRepository(context)
        repository.markOnboardingComplete("Delivery checks")
        context.stopService(Intent(context, PlaybackService::class.java))
        instrumentation.waitForIdleSync()
        val activity = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        fun update(rate: Float, pitch: Float) {
            instrumentation.runOnMainSync {
                context.startService(PlaybackCommandSecurity.authorize(context, Intent(context, PlaybackService::class.java)
                    .setAction(PlaybackActions.ACTION_UPDATE_PLAYBACK_SETTINGS)
                    .putExtra(PlaybackActions.EXTRA_RATE, rate).putExtra(PlaybackActions.EXTRA_PITCH, pitch)))
            }
        }
        fun await(check: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (!check()) { assertTrue("Control update timed out", SystemClock.elapsedRealtime() < deadline); SystemClock.sleep(50) }
        }
        try {
            instrumentation.runOnMainSync { PlaybackStateStore.rate = 1.3f; PlaybackStateStore.pitch = .9f }
            update(2f, Float.NaN)
            await { PlaybackStateStore.rate == 2f }
            assertEquals(.9f, PlaybackStateStore.pitch, 0f)
            update(Float.POSITIVE_INFINITY, 1.1f)
            await { PlaybackStateStore.pitch == 1.1f }
            assertEquals(2f, PlaybackStateStore.rate, 0f)
        } finally {
            context.stopService(Intent(context, PlaybackService::class.java))
            instrumentation.waitForIdleSync()
            activity.close()
        }
    }
}
