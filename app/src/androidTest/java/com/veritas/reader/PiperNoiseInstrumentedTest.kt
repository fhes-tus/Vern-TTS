package com.veritas.reader

import android.os.Debug
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.tts.*
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.*

class PiperNoiseInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also { check(it.packageName.endsWith(".checks")) }
    private val text = "She softly finished the sentence. Six fresh leaves drift past the quiet shore."
    @Test fun captureNativePitchPreviewPlaybackAndExport() = runBlocking {
        withContext(Dispatchers.Default) {
            val fixture = RealOfflineSpeechInstrumentedTest().fixtureContext()
            val directory = File(fixture.filesDir, "piper-captures").apply { mkdirs(); File(this, "enabled").writeText("isolated test") }
            val rows = JSONArray()
            for (voice in listOf("piper_en_us_amy", "piper_en_us_lessac", "piper_en_gb_alan")) {
                val engine = PiperEngine(fixture, voice)
                try {
                    for ((rate, pitch) in listOf(1f to 1f, 1.2f to .95f)) {
                        val audio = requireNotNull(engine.synthesizeFloat(text, rate, pitch))
                        val pcm = audio.pcm16()
                        assertEquals(audio.samples.size, pcm.size)
                        assertTrue(audio.samples.all(Float::isFinite))
                        assertTrue(audio.samples.any { abs(it) > .001f })
                        assertTrue(pcm.all { abs(it.toInt()) <= 32113 })
                        val wav = File(directory, "$voice-final-r$rate-p$pitch.wav")
                        PcmWavWriter(wav, audio.sampleRate).use { it.append(pcm) }
                        rows.put(JSONObject().put("voice", voice).put("rate", rate).put("pitch", pitch)
                            .put("sampleRate", audio.sampleRate).put("frames", pcm.size)
                            .put("rms", sqrt(audio.samples.sumOf { it.toDouble().pow(2) } / pcm.size))
                            .put("nativeHeap", Debug.getNativeHeapAllocatedSize()).put("pssKb", Debug.getPss())
                            .put("residentModels", NativeModelResources.get(fixture).admission.snapshot().resident)
                            .put("retiringModels", NativeModelResources.get(fixture).admission.snapshot().retiring))
                    }
                } finally { engine.shutdown(); engine.awaitShutdown() }
                val buffer = VeritasAudioBuffer(context, PiperEngine(fixture, voice, SpeechModelRole.LIVE))
                try {
                    val done = CompletableDeferred<Boolean>()
                    buffer.playSentencePcm(0, text, onComplete = { done.complete(it) })
                    assertTrue(withTimeout(60_000) { done.await() })
                } finally { buffer.shutdown(); buffer.awaitShutdown() }
                val finished = CountDownLatch(1)
                var error: String? = null
                VoiceManager.previewVoice(fixture, VoiceManager.VERITAS_LITE, voice, text, 1f, 1f,
                    onFinished = { error = it; finished.countDown() })
                assertTrue(finished.await(60, TimeUnit.SECONDS))
                assertNull(error)
                VoiceManager.releasePreviewEngine()
                val segment = SpeechPlan.segment("piper-noise", "v1", 0, text,
                    VoiceSettings(enginePackage = VoiceManager.VERITAS_LITE, voiceName = voice),
                    NarrationSettings(), 1f, 1f, PronunciationRulesEngine.compile(emptyList()))
                val export = MixedAudioExporter(fixture).export("Piper noise", listOf(segment))
                export.file.copyTo(File(directory, "$voice-export.wav"), overwrite = true)
                export.file.delete()
                withTimeout(30_000) {
                    while (NativeModelResources.get(fixture).admission.snapshot().resident != 0) delay(25)
                }
            }
            val lessac = File(fixture.filesDir, "tts/piper_en_us_lessac")
            for ((quality, modelName) in listOf("int8" to "en_US-lessac-medium.onnx",
                    "fp32" to "en_US-lessac-medium.fp32.onnx")) {
                val engine = OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(model = File(lessac, modelName).path,
                        tokens = File(lessac, "tokens.txt").path,
                        dataDir = VoiceModelManager.sharedEspeakDirectory(fixture).path), numThreads = 2)))
                try {
                    repeat(3) { trial ->
                        val audio = engine.generate(text, sid = 0, speed = 1f)
                        val neutral = PcmPitchShift.apply(audio.samples, audio.sampleRate, 1f)
                        val selected = PcmPitchShift.apply(audio.samples, audio.sampleRate, .95f)
                        assertEquals(audio.samples.size, neutral.size)
                        assertTrue("quiet consonants must remain present", audio.samples.count { abs(it) > .0001f } > 100)
                        PiperCapture.record(fixture, "piper_en_us_lessac-$quality", 1f, 1f,
                            audio.samples, neutral, audio.sampleRate, quality)
                        PiperCapture.record(fixture, "piper_en_us_lessac-$quality", 1f, .95f,
                            audio.samples, selected, audio.sampleRate, quality)
                        rows.put(JSONObject().put("voice", "piper_en_us_lessac").put("quality", quality)
                            .put("trial", trial).put("sampleRate", audio.sampleRate).put("frames", audio.samples.size)
                            .put("rms", sqrt(audio.samples.sumOf { it.toDouble().pow(2) } / audio.samples.size))
                            .put("quietFrameRmsP10", quietFrameRmsP10(audio.samples, audio.sampleRate)))
                    }
                } finally { engine.free() }
            }
            File(directory, "piper-results.json").writeText(rows.toString(2))
            directory.listFiles().orEmpty().filter { it.name != "enabled" }.forEach { preserveSpeechTestArtifact(context, it) }
        }
    }

    /** Lower-decile 20 ms frame RMS is a repeatable quiet-section noise proxy, not an SNR claim. */
    private fun quietFrameRmsP10(samples: FloatArray, sampleRate: Int): Double {
        val frame = (sampleRate / 50).coerceAtLeast(1)
        val levels = samples.asList().chunked(frame).filter { it.isNotEmpty() }.map { window ->
            sqrt(window.sumOf { it.toDouble().pow(2) } / window.size)
        }.sorted()
        val quiet = levels.take((levels.size / 10).coerceAtLeast(1))
        return quiet.average()
    }
}
