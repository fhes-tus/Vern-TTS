package com.veritas.reader

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.tts.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.DataOutputStream
import kotlin.math.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual native fixtures plus reproducible raw/processed pairs. No subjective quality score. */
class SoundPipelineInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also { check(it.packageName.endsWith(".checks")) }

    @Test fun phoneConverterRejectsAliasingAndRecordsProcessingCost() {
        val source = FloatArray(48000) { (.5 * sin(2 * PI * 18000 * it / 48000)).toFloat() }
        val started = SystemClock.elapsedRealtimeNanos()
        val result = BandLimitedResampler.convert(source, 2.0)
        val elapsed = (SystemClock.elapsedRealtimeNanos() - started) / 1e6
        val rms = sqrt((1024 until result.size - 1024).sumOf { result[it].toDouble().pow(2) } / (result.size - 2048))
        assertTrue("Alias $rms", rms < .000035)
        val evidence = File(context.filesDir, "sound-converter-results.json").apply {
            writeText(JSONObject().put("inputRate", 48000).put("outputRate", 24000).put("frames", result.size)
                .put("elapsedMsIncludingKernel", elapsed).put("aliasRms", rms).toString(2))
        }
        preserveSpeechTestArtifact(context, evidence)
    }

    @Test fun nativeCorpusKeepsOneCompatibleGraphAndProducesMatchedComparisonFiles() = runBlocking {
        withContext(Dispatchers.Default) {
            val fixture = RealOfflineSpeechInstrumentedTest().fixtureContext()
            val pool = KokoroGraphPool(fixture)
            val directory = File(context.filesDir, "sound-corpus").apply { mkdirs() }
            val rows = JSONArray()
            fun persist() {
                val file = File(directory, "sound-corpus-results.json").apply { writeText(rows.toString(2)) }
                preserveSpeechTestArtifact(context, file)
            }
            var retired: TtsEngine? = null
            try {
                for ((index, voice) in listOf("kokoro_af_heart", "kokoro_af_bella", "kokoro_bm_george").withIndex()) {
                    val engine = pool.voice(voice, false)
                    val loadStart = SystemClock.elapsedRealtime()
                    assertTrue("Cannot load $voice", engine.isReady())
                    val readyMs = SystemClock.elapsedRealtime() - loadStart
                    assertEquals(if (index < 2) 1 else 2, pool.modelCreations)
                    retired?.let { assertFalse(it.isReady()); assertNull(it.synthesizeFloat("Obsolete voice.", 1f, 1f)) }
                    val text = when (index) {
                        0 -> "She softly said, finish this sentence, and preserve its quiet ending."
                        1 -> "Is the price twelve dollars, or thirteen? Choose carefully."
                        else -> "The weather was clear. He paused, then read the final words."
                    }
                    val begin = SystemClock.elapsedRealtime()
                    val raw = requireNotNull(engine.synthesizeFloat(text, 1f, 1f))
                    val synthesisMs = SystemClock.elapsedRealtime() - begin
                    assertTrue(raw.samples.isNotEmpty())
                    val processingStart = SystemClock.elapsedRealtimeNanos()
                    val shaped = KokoroVoiceProcessor.render(raw.samples, raw.sampleRate, 1f, true)
                    val processingMs = (SystemClock.elapsedRealtimeNanos() - processingStart) / 1e6
                    val rawFile = File(directory, "sound-$voice-raw-float.wav")
                    writeFloatWav(rawFile, raw)
                    preserveSpeechTestArtifact(context, rawFile)
                    // Reduce both versions to the same RMS, with enough common headroom to avoid limiter bias.
                    fun rms(a: FloatArray) = sqrt(a.sumOf { it.toDouble().pow(2) } / a.size)
                    val rawRms = rms(raw.samples); val shapedRms = rms(shaped.samples)
                    val target = minOf(rawRms, shapedRms, rawRms * .5 / raw.samples.maxOf { abs(it) },
                        shapedRms * .5 / shaped.samples.maxOf { abs(it) })
                    val a = FloatArray(raw.samples.size) { (raw.samples[it] * target / rawRms).toFloat() }
                    val b = FloatArray(shaped.samples.size) { (shaped.samples[it] * target / shapedRms).toFloat() }
                    assertEquals(rms(a), rms(b), .000001)
                    for ((name, samples) in listOf("original" to a, "shaped" to b)) {
                        val file = File(directory, "sound-$voice-$name.wav")
                        PcmWavWriter(file, raw.sampleRate).use { it.append(FloatPcmAudio(samples, raw.sampleRate).pcm16()) }
                        preserveSpeechTestArtifact(context, file)
                    }
                    val limited = PcmPeakLimiter(raw.sampleRate)
                    val final = limited.process(shaped.samples) + limited.finish()
                    assertEquals(raw.samples.size, final.size)
                    assertTrue(final.all { abs(it.toInt()) <= 32113 })
                    rows.put(JSONObject().put("voice", voice).put("text", text).put("sampleRate", raw.sampleRate)
                        .put("frames", raw.samples.size).put("readyMs", readyMs).put("synthesisMs", synthesisMs)
                        .put("processingMs", processingMs).put("graphsCreated", pool.modelCreations)
                        .put("rawPeak", raw.samples.maxOf { abs(it) }.toDouble()).put("rawRms", rawRms)
                        .put("shapedPeakBeforeLimiter", shaped.samples.maxOf { abs(it) }.toDouble())
                        .put("shapedRmsBeforeLimiter", shapedRms).put("limitedFrames", limited.limitedFrames)
                        .put("comparisonRms", target).put("comparisonMethod", "same-generation RMS matched; not LUFS matched"))
                    persist()
                    engine.shutdown(); retired = engine
                }
                pool.close()
                assertFalse(pool.voice("kokoro_af_heart", true).isReady())
                assertNull(pool.voice("kokoro_af_heart", true).synthesizeFloat("Closed model.", 1f, 1f))
            } finally { retired?.shutdown(); pool.close() }
        }
    }

    @Test fun shapingPreferencePersistsAndDisablesTheActualServiceEffect() {
        val repository = DocumentRepository(context)
        val previous = repository.loadVoiceSettings()
        // Persistence and backup are real; effect lifecycle is also covered by SpeechEqualizerInstrumentedTest.
        try {
            val original = previous.copy(voiceShaping = false)
            repository.saveVoiceSettings(original)
            assertFalse(DocumentRepository(context).loadVoiceSettings().voiceShaping)
            val backup = JSONObject(repository.buildBackupJson())
            assertFalse(backup.getJSONObject("voiceSettings").getBoolean("voiceShaping"))
            val audio = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            val session = audio.generateAudioSessionId()
            val effect = SpeechEqualizer.create(session)
            try {
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    val owner = PlaybackService()
                    owner.repository = repository
                    owner.ttsEqualizer = effect; owner.ttsSessionId = session
                    owner.attachVoiceShaping()
                    assertNull(owner.ttsEqualizer)
                    assertEquals(android.media.AudioManager.ERROR, owner.ttsSessionId)
                    owner.serviceJob.cancel()
                }
                assertTrue(runCatching { effect.enabled }.isFailure)
            } finally { runCatching { effect.release() } }
        } finally { repository.saveVoiceSettings(previous) }
    }

    @Test fun closingAPoolWaitsForNativeAccessAndDiscardsTheRetiredResult() = runBlocking {
        val fixture = withContext(Dispatchers.Default) { RealOfflineSpeechInstrumentedTest().fixtureContext() }
        val pool = KokoroGraphPool(fixture)
        val entered = CountDownLatch(1); val proceed = CountDownLatch(1)
        val engine = pool.voice("kokoro_af_heart", false)
        pool.onNativeStart = { entered.countDown(); check(proceed.await(30, TimeUnit.SECONDS)) }
        try {
            val result = async(Dispatchers.Default) { engine.synthesizeFloat("Finish the current native request safely.", 1f, 1f) }
            assertTrue(entered.await(30, TimeUnit.SECONDS))
            val closing = pool.closeAsync()
            assertFalse(closing.isCompleted)
            proceed.countDown()
            closing.join()
            assertNull(result.await())
            assertFalse(engine.isReady())
        } finally { proceed.countDown(); engine.shutdown(); withContext(Dispatchers.Default) { pool.close() } }
    }

    private fun writeFloatWav(file: File, audio: FloatPcmAudio) = DataOutputStream(file.outputStream().buffered(65536)).use { out ->
        fun int(v: Int) = out.writeInt(Integer.reverseBytes(v))
        fun short(v: Int) = out.writeShort(java.lang.Short.reverseBytes(v.toShort()).toInt())
        fun tag(v: String) = out.write(v.toByteArray(Charsets.US_ASCII))
        tag("RIFF"); int(36 + audio.samples.size * 4); tag("WAVE"); tag("fmt "); int(16)
        short(3); short(1); int(audio.sampleRate); int(audio.sampleRate * 4); short(4); short(32)
        tag("data"); int(audio.samples.size * 4); audio.samples.forEach { int(it.toRawBits()) }
    }
}
