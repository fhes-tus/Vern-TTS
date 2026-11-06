package com.veritas.reader

import android.content.Context
import android.content.ContextWrapper
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.tts.*
import kotlinx.coroutines.*
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Only public test sentences and isolated cache models; never records the user's book or changes its voices. */
class VoiceAcceptanceInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val root = File(context.cacheDir,"voice-acceptance").apply { mkdirs() }
    private val fixture = object : ContextWrapper(context) {
        override fun getFilesDir() = root
        override fun getApplicationContext(): Context = this
    }
    private fun install(name: String, destination: File) {
        val path = "/data/local/tmp/vern-$name-acceptance.tar.bz2"
        val stream = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("cat $path"))
        TarArchiveInputStream(BZip2CompressorInputStream(stream.buffered(65536))).use { tar ->
            var total = 0L
            while(true) {
                val entry = tar.nextTarEntry ?: break
                require(!entry.isSymbolicLink && !entry.isLink && entry.size in 0..200_000_000)
                total += entry.size; require(total < 300_000_000)
                val relative = entry.name.substringAfter('/',"")
                if(relative.isEmpty()) continue
                val target = File(destination,relative).canonicalFile
                require(target.toPath().startsWith(destination.canonicalFile.toPath()))
                if(entry.isDirectory) target.mkdirs() else {
                    target.parentFile?.mkdirs(); target.outputStream().use { check(entry.size == tar.copyTo(it)) }
                }
            }
        }
    }
    @Test fun ryanAndKittenGenerateCompleteNormalSpeedPassages() = runBlocking {
        withContext(Dispatchers.Default) {
            val ryan = VoiceModelManager.getVoiceDirectory(fixture,OfflineEngineType.PIPER,"piper_en_us_ryan")
            install("ryan",ryan)
            val shared = VoiceModelManager.sharedEspeakDirectory(fixture)
            if(!shared.exists()) check(File(ryan,"espeak-ng-data").renameTo(shared))
            val kitten = VoiceModelManager.getVoiceDirectory(fixture,OfflineEngineType.KITTEN)
            install("kitten",kitten)
            assertTrue(VoiceModelManager.isVoiceInstalled(fixture,"piper_en_us_ryan"))
            assertTrue(VoiceModelManager.isVoiceInstalled(fixture,"kitten_0"))
            val sentences = listOf(
                "The reader paused beside the window, then turned to the next page.",
                "In 2026, Dr. Ryan paid 12.50 dollars for three books and a notebook.",
                "She asked, ‘Are you sure?’ He replied, ‘Yes—absolutely.’",
                "The co-operative's first report contained footnotes, abbreviations, and a reference to section 4.2.",
                "The path crossed the river; beyond it, a narrow road climbed through the trees, past the school, around the old church, and towards the village where the children had spent every summer of their lives.",
                "The next sentence should sound clear and ordinary again.")
            val report = JSONArray()
            for(voice in listOf("piper_en_us_ryan","kitten_0","kitten_7")) {
                val engine: TtsEngine = if(voice.startsWith("piper")) PiperEngine(fixture,voice) else KittenEngine(fixture,voice)
                try {
                    for((index,text) in sentences.withIndex()) {
                        val started = android.os.SystemClock.elapsedRealtime()
                        val audio = requireNotNull(engine.synthesizeFloat(text,1f,1f))
                        assertTrue(audio.samples.isNotEmpty()); assertTrue(audio.samples.all(Float::isFinite))
                        assertTrue(audio.samples.any { kotlin.math.abs(it) > .001f })
                        val file = File(root,"$voice-$index.wav")
                        PcmWavWriter(file,audio.sampleRate).use { it.append(audio.pcm16()) }
                        report.put(JSONObject().put("voice",voice).put("text",text).put("file",file.name)
                            .put("frames",audio.samples.size).put("sampleRate",audio.sampleRate)
                            .put("generationMs",android.os.SystemClock.elapsedRealtime()-started))
                        File(root,"report.json").writeText(report.toString(2))
                    }
                } finally { engine.shutdown(); engine.awaitShutdown() }
            }
        }
    }

    @Test fun ryanLongPassageAndFollowingSentenceRemainPlayable() = runBlocking {
        withContext(Dispatchers.Default) {
            val ryan = VoiceModelManager.getVoiceDirectory(fixture,OfflineEngineType.PIPER,"piper_en_us_ryan")
            install("ryan",ryan)
            val shared = VoiceModelManager.sharedEspeakDirectory(fixture)
            if(!shared.exists()) check(File(ryan,"espeak-ng-data").renameTo(shared))
            val text = "The narrow road crossed the river and climbed past the school, where a group of children were waiting beside the old stone wall, looking towards the village and listening for the sound of the approaching bus; ".repeat(3)+
                "at last they saw the driver wave, and everyone picked up their bags."
            assertTrue(text.length > PiperSpeechChunks.LIMIT)
            assertEquals(text,PiperSpeechChunks.split(text).joinToString(""))
            val engine = PiperEngine(fixture,"piper_en_us_ryan")
            try {
                for((name,source) in listOf("ryan-long-guard" to text,"ryan-after-long" to "The following sentence is clear and ordinary again.")) {
                    val audio = requireNotNull(engine.synthesizeFloat(source,1f,1f))
                    assertTrue(audio.samples.isNotEmpty() && audio.samples.all(Float::isFinite))
                    PcmWavWriter(File(root,"$name.wav"),audio.sampleRate).use { it.append(audio.pcm16()) }
                    File(root,"$name.txt").writeText(source)
                }
            } finally { engine.shutdown(); engine.awaitShutdown() }
        }
    }

    @Test fun actualWifiDownloadsVerifyAndInstallLiteStudioAndKittenInIsolation() = runBlocking {
        val network = context.getSystemService(android.net.ConnectivityManager::class.java)
        org.junit.Assume.assumeTrue("Model network acceptance requires Wi-Fi",
            network.getNetworkCapabilities(network.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true)
        org.junit.Assume.assumeFalse(VoiceModelManager.downloadState.value.values.any { it is DownloadState.Downloading })
        val downloadRoot = File(context.cacheDir,"voice-download-acceptance").apply { mkdirs() }
        val downloadContext = object : ContextWrapper(context) {
            override fun getFilesDir() = downloadRoot
            override fun getApplicationContext(): Context = this
        }
        withContext(Dispatchers.IO) {
            for (id in listOf("piper_en_us_ryan", "kokoro_af_heart", "kitten_0")) {
                val voice = VoiceModelManager.availableVoices.first { it.id == id }
                assertTrue("Public model download failed: $id",VoiceModelManager.downloadVoice(downloadContext,voice))
                assertTrue(VoiceModelManager.isVoiceInstalled(downloadContext,id))
            }
        }
    }

    @Test fun kittenRoutesThroughPlaybackAndPortableExport() = runBlocking {
        withContext(Dispatchers.Default) {
            val kitten = VoiceModelManager.getVoiceDirectory(fixture,OfflineEngineType.KITTEN)
            install("kitten",kitten)
            val voice = VoiceSettings(enginePackage = VoiceManager.KITTEN,voiceName = "kitten_0",localeTag = "en-US")
            val segment = SpeechPlan.segment("kitten-fixture","v1",0,"The downloaded Kitten voice can read this passage.",voice,
                NarrationSettings(),1f,1f,PronunciationRulesEngine.compile(emptyList()))
            assertEquals(SpeechEngineFamily.KITTEN,segment.family)
            val buffer = VeritasAudioBuffer(fixture,KittenEngine(fixture,"kitten_0",SpeechModelRole.LIVE))
            try {
                val done = CompletableDeferred<Boolean>()
                buffer.playSentencePcm(0,segment.spoken.text,onComplete = { done.complete(it) })
                assertTrue(withTimeout(30_000) { done.await() })
            } finally { buffer.shutdown(); buffer.awaitShutdown() }
            val exported = MixedAudioExporter(fixture).export("Kitten fixture",listOf(segment))
            try { assertTrue(exported.file.length() > 44) } finally { exported.file.delete() }
        }
    }

    @Test fun kittenSelectionRetainsItsEngineAndLabelAfterReload() {
        val owned = "voice_repository_acceptance"
        val isolated = object : ContextWrapper(fixture) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String,mode: Int) = context.getSharedPreferences(owned,mode)
        }
        try {
            val repository = DocumentRepository(isolated)
            val saved = repository.saveVoiceSettings(VoiceSettings(voiceName = "kitten_0"))
            assertEquals(VoiceManager.KITTEN,saved.enginePackage)
            assertEquals("Kitten",saved.engineLabel)
            assertEquals(saved,repository.loadVoiceSettings())
        } finally { context.getSharedPreferences(owned,0).edit().clear().commit() }
    }

    @Test fun compareRyanDurationNoiseOnPublicSentences() = runBlocking {
        withContext(Dispatchers.Default) {
            val dir = VoiceModelManager.getVoiceDirectory(fixture,OfflineEngineType.PIPER,"piper_en_us_ryan")
            install("ryan",dir)
            val shared = VoiceModelManager.sharedEspeakDirectory(fixture)
            if(!shared.exists()) check(File(dir,"espeak-ng-data").renameTo(shared))
            val text = "The narrow road crossed the river and climbed past the school, where a group of children were waiting beside the old stone wall, looking towards the village and listening for the sound of the approaching bus; ".repeat(3)+
                "at last they saw the driver wave, and everyone picked up their bags."
            for((name,noise) in listOf("default" to .8f,"stable" to 0f)) {
                val model = com.k2fsa.sherpa.onnx.OfflineTts(config = com.k2fsa.sherpa.onnx.OfflineTtsConfig(
                    model = com.k2fsa.sherpa.onnx.OfflineTtsModelConfig(vits = com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig(
                        model = requireNotNull(PiperModelFiles.preferred(dir,"en_US-ryan-medium")).path,
                        tokens = File(dir,"tokens.txt").path,dataDir = shared.path,noiseScaleW = noise),numThreads = 2)))
                try {
                    for((length,source) in listOf("short" to "The narrow road crossed the river and climbed past the school.","long" to text)) {
                        val audio = model.generate(source,0,1f)
                        assertTrue(audio.samples.isNotEmpty())
                        PcmWavWriter(File(root,"ryan-$name-$length.wav"),audio.sampleRate).use {
                            it.append(FloatPcmAudio(audio.samples,audio.sampleRate).pcm16())
                        }
                    }
                } finally { model.free() }
            }
        }
    }
}
