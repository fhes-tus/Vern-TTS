package com.veritas.reader

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.tts.DownloadState
import com.veritas.reader.tts.OfflineEngineType
import com.veritas.reader.tts.VoiceModelManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OptionalDownloadsInstrumentedTest {
    @Test fun baseMlClientsRemainAvailableWithOrWithoutTranslationFeature() {
        com.google.mlkit.nl.languageid.LanguageIdentification.getClient().close()
        com.google.mlkit.vision.text.TextRecognition.getClient(
            com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS).close()
    }

    @Test fun installedFeatureLoadsItsClientWithoutDownloadingALanguageModel() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(DeviceMenuTranslation.installed(context))
        DeviceMenuTranslation.client("it").use {
            withTimeout(8_000) { DeviceMenuTranslation.isDownloaded("it") }
        }
        Unit
    }

    /** Every fixture lives outside real downloaded voices and real preferences. */
    private fun isolated(block: suspend (Context) -> Unit) = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val root = File(base.cacheDir, "optional-download-test-$id").apply { mkdirs() }
        val context = object : ContextWrapper(base) {
            override fun getFilesDir() = root
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("$id-$name", mode)
        }
        try { block(context) } finally {
            context.getSharedPreferences("veritas_offline_tts_prefs", 0).edit().clear().commit()
            root.deleteRecursively()
        }
    }

    @Test fun piperDeletionPreservesOtherVoiceAndRemovesLastSharedData() = isolated { context ->
        val voices = VoiceModelManager.availableVoices.filter { it.engineType == OfflineEngineType.PIPER }.take(2)
        val shared = VoiceModelManager.sharedEspeakDirectory(context).apply { mkdirs() }
        File(shared, "phontab").writeText("fixture")
        voices.forEach { voice ->
            val dir = VoiceModelManager.getVoiceDirectory(context, voice.engineType, voice.id).apply { mkdirs() }
            File(dir, "${voice.modelBaseName}.onnx").writeText("fixture")
            File(dir, "tokens.txt").writeText("fixture")
            assertTrue(VoiceModelManager.isVoiceInstalled(context, voice.id))
        }
        assertTrue(VoiceModelManager.deleteVoice(context, voices[0].id))
        assertFalse(VoiceModelManager.isVoiceInstalled(context, voices[0].id))
        assertTrue(VoiceModelManager.isVoiceInstalled(context, voices[1].id))
        assertTrue(shared.exists())
        assertTrue(VoiceModelManager.deleteVoice(context, voices[1].id))
        assertFalse(shared.exists())
    }

    @Test fun kittenDeletionRemovesSharedPackageForEverySpeaker() = isolated { context ->
        val voices = VoiceModelManager.availableVoices.filter { it.engineType == OfflineEngineType.KITTEN }
        val dir = VoiceModelManager.getVoiceDirectory(context, OfflineEngineType.KITTEN).apply { mkdirs() }
        listOf("model.fp32.onnx", "tokens.txt", "espeak-ng-data/phontab").forEach {
            File(dir, it).apply { parentFile!!.mkdirs(); writeText("fixture") }
        }
        RandomAccessFile(File(dir, "voices.bin"), "rw").use { it.setLength(3_276_800) }
        assertTrue(voices.all { VoiceModelManager.isVoiceInstalled(context, it.id) })
        assertTrue(VoiceModelManager.deleteVoice(context, voices.last().id))
        assertFalse(dir.exists())
        assertTrue(voices.none { VoiceModelManager.isVoiceInstalled(context, it.id) })
        assertTrue(voices.all { VoiceModelManager.stateFor(it.id) is DownloadState.Idle })
    }

    @Test fun cancelAfterFailureStillRemovesResumableBytes() = isolated { context ->
        val voice = VoiceModelManager.availableVoices.first { it.engineType == OfflineEngineType.PIPER }
        val partial = File(VoiceModelManager.getTtsDirectory(context), "downloads/${voice.id}.part")
        partial.parentFile!!.mkdirs()
        partial.writeText("interrupted archive")
        val validator = File(partial.path + ".validator").apply { writeText("fixture") }
        VoiceModelManager.cancelDownload(context, voice.id)
        assertFalse(partial.exists())
        assertFalse(validator.exists())
    }

    @Test fun languageEligibilityWorksWithoutLoadingOptionalEngine() {
        assertEquals("it", DeviceMenuTranslation.target(Locale.ITALY, ""))
        assertNull(DeviceMenuTranslation.target(Locale.ENGLISH, ""))
        assertNull(DeviceMenuTranslation.target(Locale.ITALY, "fr"))
        assertNull(DeviceMenuTranslation.target(Locale.TRADITIONAL_CHINESE, ""))
    }
}
