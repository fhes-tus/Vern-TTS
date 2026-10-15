package com.veritas.reader

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import com.veritas.reader.tts.TtsEngine
import com.veritas.reader.ui.ReaderViewModel
import com.veritas.reader.ui.saveVoiceSettings

class TechnicalDebtInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also { check(it.packageName.endsWith(".checks")) }

    @Test fun playRestoresBookPitchAndHonorsPendingOrExplicitControls() {
        val repository = DocumentRepository(context)
        val first = repository.createDocument("Remembered pitch", "This is a long test sentence for remembered voice parameters and normal playback initialization.", "TXT")
        val second = repository.createDocument("Manual pitch", "This is another long test sentence for immediate manual voice changes before persistence finishes.", "TXT")
        val previous = repository.loadVoiceSettings()
        fun awaitPitch(documentId: String, expected: Float) {
            val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
            while (android.os.SystemClock.elapsedRealtime() < deadline &&
                (PlaybackStateStore.activeDocumentId != documentId || kotlin.math.abs(PlaybackStateStore.pitch - expected) > .001f)) Thread.sleep(25)
            assertEquals(documentId, PlaybackStateStore.activeDocumentId)
            assertEquals(expected, PlaybackStateStore.pitch, .001f)
        }
        try {
            repository.saveVoiceSettings(VoiceSettings())
            repository.saveDocVoiceMemory(first.id, .85f, .75f)
            repository.saveDocVoiceMemory(second.id, .8f, .7f)
            PlaybackStateStore.pendingVoiceSettings = false
            PlaybackStateStore.pitch = 1f
            sendPlaybackIntent(context, PlaybackActions.ACTION_PLAY, first.id, 0)
            awaitPitch(first.id, .75f)
            assertEquals(.85f, PlaybackStateStore.rate, .001f)
            PlaybackStateStore.pitch = 1.25f
            PlaybackStateStore.pendingVoiceSettings = true
            sendPlaybackIntent(context, PlaybackActions.ACTION_PLAY, second.id, 0)
            awaitPitch(second.id, 1.25f)
            PlaybackStateStore.pendingVoiceSettings = false
            sendPlaybackIntent(context, PlaybackActions.ACTION_PLAY, first.id, 0, rate = 1.2f, pitch = 1.3f)
            awaitPitch(first.id, 1.3f)
            assertEquals(1.2f, PlaybackStateStore.rate, .001f)
        } finally {
            PlaybackStateStore.pendingVoiceSettings = false
            sendPlaybackIntent(context, PlaybackActions.ACTION_STOP)
            repository.saveVoiceSettings(previous)
            repository.deleteDocument(first.id); repository.deleteDocument(second.id)
        }
    }

    @Test fun rapidPitchGesturesKeepTheLatestValueThroughStartupAndPersistence() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val repository = DocumentRepository(context)
        val original = repository.loadVoiceSettings()
        val store = androidx.lifecycle.ViewModelStore()
        lateinit var model: ReaderViewModel
        try {
            instrumentation.runOnMainSync {
                model = androidx.lifecycle.ViewModelProvider(store,
                    androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as android.app.Application))[ReaderViewModel::class.java]
                repeat(20) { model.saveVoiceSettings(original.copy(preferredPitch = .7f + it * .03f)) }
                model.saveVoiceSettings(original.copy(preferredPitch = 1.35f))
            }
            val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
            while (android.os.SystemClock.elapsedRealtime() < deadline && kotlin.math.abs(repository.loadVoiceSettings().preferredPitch - 1.35f) > .001f) Thread.sleep(25)
            instrumentation.waitForIdleSync()
            assertEquals(1.35f, repository.loadVoiceSettings().preferredPitch, .001f)
            assertEquals(1.35f, model.uiState.value.voiceSettings.preferredPitch, .001f)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            repository.saveVoiceSettings(original)
        }
    }

    @Test fun apiCredentialMigrationRemovesPlaintextAndRoundTrips() {
        val previous = ApiCredentialStore.get(context)
        val library = context.getSharedPreferences("veritas_reader_library", 0)
        try {
            ApiCredentialStore.save(context, "")
            assertTrue(library.edit().putString("gemini_api_key", "dummy-regression-credential").commit())
            assertEquals("dummy-regression-credential", ApiCredentialStore.get(context))
            assertFalse(library.contains("gemini_api_key")); assertFalse(library.contains("ai_api_key"))
            val sealed = context.getSharedPreferences("veritas_reader_secrets", 0).getString("credential", "")!!
            assertFalse(sealed.contains("dummy-regression-credential"))
            ApiCredentialStore.save(context, "changed-dummy-value")
            assertEquals("changed-dummy-value", ApiCredentialStore.get(context))
        } finally { ApiCredentialStore.save(context, previous) }
    }

    @Test fun removingSentenceNoteAudioPersistsTheRemoval() {
        val repository = DocumentRepository(context)
        val document = repository.createDocument("Note audio removal", "Test sentence.", "TXT")
        try {
            repository.upsertAnnotation(document.id, 0, AnnotationType.NOTE, "Note", audioPath = "old.m4a", audioDurationSeconds = 4)
            repository.upsertAnnotation(document.id, 0, AnnotationType.NOTE, "Updated", replaceAudio = true)
            val note = DocumentRepository(context).loadAnnotations(document.id).single()
            assertNull(note.audioPath); assertEquals(0, note.audioDurationSeconds); assertEquals("Updated", note.note)
        } finally { repository.deleteAnnotationsForDocument(document.id); repository.deleteDocument(document.id) }
    }

    @Test fun concurrentLibraryEditsAndExtractionKeepIndependentMetadata() = runBlocking {
        val repository = DocumentRepository(context)
        val document = repository.createDocument("Library mutation check", "One sentence. Another sentence.", "TXT")
        try {
            coroutineScope {
                launch(Dispatchers.IO) { repeat(20) { repository.renameDocument(document.id, "Renamed") } }
                launch(Dispatchers.IO) { repeat(20) { repository.toggleFavorite(document.id) } }
                launch(Dispatchers.IO) { repeat(20) { repository.setCollection(document.id, "Study") } }
                launch(Dispatchers.IO) { repeat(20) { repository.saveProgress(document.id, 1) } }
            }
            val result = repository.findDocument(document.id)!!
            assertEquals("Renamed", result.title); assertEquals("Study", result.collection)
            assertFalse(result.favorite); assertEquals(1, result.currentIndex)
            repository.clearProgress(document.id)
            assertEquals("Renamed", repository.findDocument(document.id)!!.title)
            assertEquals(0, repository.findDocument(document.id)!!.currentIndex)
        } finally { repository.deleteDocument(document.id) }
    }

    @Test fun neuralWavStreamsCorrectPcmAndCleansUpCancelledOutput() = runBlocking {
        var releases = 0
        val calls = mutableListOf<Pair<Float, Float>>()
        val exporter = NeuralAudioExporter(context) { object : TtsEngine {
            override val sampleRate = 24000
            override fun isReady() = true
            override suspend fun synthesize(sentence: String, rate: Float, pitch: Float): ShortArray {
                calls.add(rate to pitch); return ShortArray(24000) { 1234 }
            }
            override fun shutdown() { releases++ }
        } }
        val voice = VoiceSettings(voiceName = "piper_test", enginePackage = VoiceManager.VERITAS_LITE)
        val result = exporter.export("Neural regression", listOf("Sentence one.", "Sentence two."), voice, NarrationSettings(enabled = false), 1.2f, 0.85f)
        try {
            assertEquals(2, result.synthesizedParts)
            assertEquals(44L + 96000, result.file.length())
            assertEquals(listOf(1.2f to 0.85f, 1.2f to 0.85f), calls)
            assertEquals(1, releases)
        } finally { result.file.delete() }
        val directory = java.io.File(context.cacheDir, "VernExports")
        val before = directory.listFiles().orEmpty().map { it.name }.toSet()
        val cancelled = NeuralAudioExporter(context) { object : TtsEngine {
            override val sampleRate = 24000
            override fun isReady() = true
            override suspend fun synthesize(sentence: String, rate: Float, pitch: Float): ShortArray? { delay(2000); return null }
            override fun shutdown() { releases++ }
        } }
        try { withTimeout(100) { cancelled.export("Cancelled regression", listOf("Text."), voice, NarrationSettings(), 1f, 1f) }; fail("Expected cancellation") }
        catch (_: TimeoutCancellationException) { }
        assertEquals(before, directory.listFiles().orEmpty().map { it.name }.toSet())
        assertEquals(2, releases)
    }
}
