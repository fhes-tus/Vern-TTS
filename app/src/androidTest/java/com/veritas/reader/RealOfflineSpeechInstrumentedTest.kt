package com.veritas.reader

import android.content.Context
import android.content.ContextWrapper
import android.os.Debug
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.tts.KokoroTtsEngine
import com.veritas.reader.tts.PiperEngine
import com.veritas.reader.tts.TtsEngine
import com.veritas.reader.tts.VoiceModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/** Explicit local fixture package; never reads the production app or downloads a model. */
class RealOfflineSpeechInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext.also { check(it.packageName.endsWith(".checks")) }

    internal fun fixtureContext(): Context {
        val root = File(context.filesDir, "native-speech-fixture").apply { mkdirs() }
        val fixtureTar = InstrumentationRegistry.getArguments().getString("fixtureTar") ?: "/data/local/tmp/vern-native-model-fixture.tar"
        require(fixtureTar.matches(Regex("/data/local/tmp/vern-native-model-fixture(-09)?\\.tar")))
        val stream = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("cat $fixtureTar"))
        TarArchiveInputStream(stream.buffered(65536)).use { input ->
            var total = 0L
            while (true) {
                val entry = input.nextTarEntry ?: break
                val name = entry.name.replace('\\', '/')
                val relative = when {
                    name.startsWith("kokoro_af_heart/") -> "tts/kokoro/" + name.removePrefix("kokoro_af_heart/")
                    name.startsWith("piper_en_us_lessac/espeak-ng-data/") -> "tts/espeak-ng-data/" + name.removePrefix("piper_en_us_lessac/espeak-ng-data/")
                    name.startsWith("piper_en_us_lessac/") || name.startsWith("piper_en_us_amy/") ||
                        name.startsWith("piper_en_gb_alan/") || name.startsWith("piper_en_us_lessac_fp32/") -> "tts/" + name
                    else -> error("Unexpected fixture entry: $name")
                }
                val target = File(root, relative).canonicalFile
                require(target.toPath().startsWith(root.canonicalFile.toPath()) && target != root.canonicalFile)
                require(!entry.isSymbolicLink && !entry.isLink && entry.size in 0..250_000_000L)
                total += entry.size
                require(total <= 512_000_000L)
                if (entry.isDirectory) target.mkdirs() else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { output -> assertEquals(entry.size, input.copyTo(output, 65536)) }
                }
            }
        }
        return object : ContextWrapper(context) {
            override fun getFilesDir(): File = root
            override fun getApplicationContext(): Context = this
        }
    }

    @Test fun actualPiperAndKokoroProduceMappedPatternReadingsAndPortableEvidence() = runBlocking {
        withContext(Dispatchers.Default) {
            val fixture = fixtureContext()
            val directory = File(context.filesDir, "real-speech-evidence").apply { mkdirs() }
            val report = JSONArray()
            val results = File(directory, "real-speech-results.json")
            results.writeText(report.toString(2))
            preserve(results)
            val source = "The result[12] is _clear_. Read every word."
            val rules = PronunciationRulesEngine.compile(listOf(
                PronunciationRule("references", "[n]", "", matchKind = SpeechRuleKind.PATTERN),
                PronunciationRule("markers", "_{text}_", "\$1", matchKind = SpeechRuleKind.PATTERN)))
            for (voice in listOf("piper_en_us_lessac", "kokoro_af_heart")) {
                assertTrue("Fixture package is incomplete: $voice", VoiceModelManager.isVoiceInstalled(fixture, voice))
                val started = SystemClock.elapsedRealtime()
                val engine: TtsEngine = if (voice.startsWith("piper_")) PiperEngine(fixture, voice) else KokoroTtsEngine(fixture, voice)
                val loadMs = SystemClock.elapsedRealtime() - started
                try {
                    assertTrue("Real engine did not initialize: $voice", engine.isReady())
                    for ((name, rate, pitch) in listOf(Triple("neutral", 1f, 1f), Triple("delivery", 1.2f, .95f))) {
                        val plan = SpeechPlan.segment("real-fixture", "v1", 0, source,
                            VoiceSettings(enginePackage = if (voice.startsWith("piper_")) VoiceManager.VERITAS_LITE else VoiceManager.VERITAS_STUDIO,
                                voiceName = voice, localeTag = "en-US"), NarrationSettings(), rate, pitch, rules)
                        assertEquals("The result is clear. Read every word.", plan.spoken.text)
                        val begin = SystemClock.elapsedRealtime()
                        val samples = requireNotNull(engine.synthesize(plan.spoken.text, plan.rate, plan.pitch))
                        val synthesisMs = SystemClock.elapsedRealtime() - begin
                        val sampleRate = engine.sampleRate
                        assertTrue(samples.isNotEmpty())
                        assertTrue(samples.any { it != 0.toShort() })
                        assertTrue(sampleRate in 8000..192000)
                        val file = File(directory, "$voice-$name.wav")
                        PcmWavWriter(file, sampleRate).use { it.append(samples) }
                        val peak = samples.maxOf { abs(it.toInt()) }
                        val rms = sqrt(samples.sumOf { it.toDouble() * it } / samples.size)
                        report.put(JSONObject().put("voice", voice).put("case", name).put("loadMs", loadMs)
                            .put("text", plan.spoken.text).put("rate", plan.rate).put("pitch", plan.pitch)
                            .put("synthesisMs", synthesisMs).put("frames", samples.size).put("sampleRate", sampleRate)
                            .put("audioDurationMs", samples.size * 1000L / sampleRate).put("samplePeak", peak)
                            .put("rms", rms).put("fullScaleSamples", samples.count { abs(it.toInt()) >= 32767 })
                            .put("javaUsedBytesAfter", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
                            .put("nativeAllocatedBytesAfter", Debug.getNativeHeapAllocatedSize()))
                        preserve(file)
                        results.writeText(report.toString(2))
                        preserve(results)
                    }
                } finally { engine.shutdown() }
            }
            val repository = DocumentRepository(fixture)
            val previousVoice = repository.loadVoiceSettings()
            val previousNarration = repository.loadNarrationSettings()
            val previousRules = repository.loadPronunciationRules()
            try {
                repository.saveVoiceSettings(VoiceSettings(enginePackage = VoiceManager.VERITAS_LITE,
                    voiceName = "piper_en_us_lessac", localeTag = "en-US"))
                repository.saveNarrationSettings(NarrationSettings())
                repository.savePronunciationRules(listOf(PronunciationRule("fixture-references", "[n]", "", matchKind = SpeechRuleKind.PATTERN)))
                val exported = AudioExportManager(fixture).exportToWav("Pattern export fixture", listOf("[12].", "Read this result[1]."), 1f, 1f)
                try {
                    assertEquals(1, exported.synthesizedParts)
                    assertTrue(exported.file.length() > 44)
                    val evidence = File(directory, "piper-pattern-export.wav")
                    exported.file.copyTo(evidence, overwrite = true)
                    preserve(evidence)
                } finally { exported.file.delete() }
            } finally {
                repository.saveVoiceSettings(previousVoice)
                repository.saveNarrationSettings(previousNarration)
                repository.savePronunciationRules(previousRules)
            }
        }
    }

    private fun preserve(file: File) {
        preserveSpeechTestArtifact(context, file)
    }
}
