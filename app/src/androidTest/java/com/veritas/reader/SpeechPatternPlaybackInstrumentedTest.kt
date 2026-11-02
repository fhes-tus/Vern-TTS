package com.veritas.reader

import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.tts.TtsEngine
import com.veritas.reader.tts.VeritasAudioBuffer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.content.Intent
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario

class SpeechPatternPlaybackInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also { check(it.packageName.endsWith(".checks")) }

    private fun withRules(block: (DocumentRepository) -> Unit) {
        val repository = DocumentRepository(context)
        val previous = repository.loadPronunciationRules()
        repository.savePronunciationRules(emptyList())
        try { block(repository) } finally { repository.savePronunciationRules(previous) }
    }

    @Test fun savedPatternsReachTheRealBufferWithMappedInput() = withRules { repository ->
        repository.addPronunciationRule("[n]", "", SpeechRuleKind.PATTERN)
        repository.addPronunciationRule("_{text}_", "\$1", SpeechRuleKind.PATTERN)
        val source = "Before[12] _terro_ after."
        val segment = SpeechPlan.segment("fixture", "revision", 0, source,
            VoiceSettings(enginePackage = VoiceManager.VERITAS_LITE, voiceName = "piper_fixture", localeTag = "en-US"),
            NarrationSettings(), 1f, 1f, DocumentRepository(context).compiledPronunciationRules())
        assertEquals("Before terro after.", segment.spoken.text)
        val start = segment.spoken.text.indexOf("after")
        assertEquals(source.indexOf("after"), segment.spoken.sourceOffset(start))
        var received = ""
        val buffer = VeritasAudioBuffer(context, object : TtsEngine {
            override val sampleRate = 24000
            override fun isReady() = true
            override suspend fun synthesize(sentence: String, rate: Float, pitch: Float): ShortArray {
                received = sentence; return ShortArray(4800) { 100 }
            }
            override fun shutdown() = Unit
        })
        val done = CountDownLatch(1)
        var success = false
        try {
            buffer.playSentencePcm(0, segment.spoken.text) { success = it; done.countDown() }
            assertTrue(done.await(20, TimeUnit.SECONDS))
            assertTrue(success)
            assertEquals("Before terro after.", received)
        } finally { buffer.shutdown() }
    }

    @Test fun savedPatternsRoundTripThroughActualBackupWithoutEditingTheBook() = withRules { repository ->
        val document = repository.createDocument("Speech pattern fixture", "Keep [12] and _terro_ in the displayed book.", "TXT")
        try {
            repository.addPronunciationRule("[n]", "reference \$1", SpeechRuleKind.PATTERN, caseSensitive = true)
            val before = repository.loadPronunciationRules().single()
            val backup = repository.buildBackupJson()
            repository.savePronunciationRules(emptyList())
            repository.restoreBackupJson(backup, replaceExisting = false)
            assertEquals(before, DocumentRepository(context).loadPronunciationRules().single())
            // Restore atomically publishes a new file. Reload its current metadata by identity.
            val restored = requireNotNull(repository.findDocument(document.id))
            assertNotEquals(document.fileName, restored.fileName)
            assertEquals("Keep [12] and _terro_ in the displayed book.", repository.readText(restored))
            assertEquals("Keep reference 12 and _terro_ in the displayed book.", repository.applyPronunciationRules(repository.readText(restored)))
        } finally { repository.deleteDocument(document.id) }
    }

    @Test fun editingAndTogglingInvalidateCompiledPatterns() = withRules { repository ->
        val saved = repository.addPronunciationRule("[n]", "", SpeechRuleKind.PATTERN).single()
        assertEquals("A B", repository.applyPronunciationRules("A[12]B"))
        repository.togglePronunciationRule(saved.id)
        assertEquals("A[12]B", repository.applyPronunciationRules("A[12]B"))
        repository.addPronunciationRule("_{text}_", "\$1", SpeechRuleKind.PATTERN, ruleId = saved.id)
        assertEquals(1, repository.loadPronunciationRules().size)
        assertEquals("A[12]B terro", repository.applyPronunciationRules("A[12]B _terro_"))
        repository.removePronunciationRule(saved.id)
        assertEquals("_terro_", repository.applyPronunciationRules("_terro_"))
    }

    @Test fun actualServiceSkipsReferenceOnlySentencesAndFinishesAnExcludedSelection() = withRules { repository ->
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val previousVoice = repository.loadVoiceSettings()
        val previousNarration = repository.loadNarrationSettings()
        val previousReader = repository.loadReaderSettings()
        repository.markOnboardingComplete("Speech checks")
        val voice = runBlocking { offlineEnglishTestVoice(context) }
        repository.saveVoiceSettings(VoiceSettings(enginePackage = voice.enginePackage, voiceName = voice.name, localeTag = voice.localeTag))
        repository.saveNarrationSettings(NarrationSettings())
        repository.saveReaderSettings(previousReader.copy(autoPlayQueue = false))
        repository.addPronunciationRule("[n]", "", SpeechRuleKind.PATTERN)
        val document = repository.createDocument("Skipped speech fixture", "[1]. [2]. [3]. Finally speak these words.", "TXT")
        fun awaitStatus(expected: String) {
            val deadline = SystemClock.elapsedRealtime() + 40000L
            while (PlaybackStateStore.statusMessage != expected && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
            assertEquals(expected, PlaybackStateStore.statusMessage)
        }
        fun command(action: String, extras: Intent.() -> Unit = {}) = instrumentation.runOnMainSync {
            context.startService(PlaybackCommandSecurity.authorize(context, Intent(context, PlaybackService::class.java).setAction(action).apply(extras)))
        }
        val activity = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try {
            command(PlaybackActions.ACTION_PLAY) {
                putExtra(PlaybackActions.EXTRA_DOCUMENT_ID, document.id)
                putExtra(PlaybackActions.EXTRA_START_INDEX, 0)
                putExtra(PlaybackActions.EXTRA_RATE, 2f)
            }
            awaitStatus("Finished reading.")
            val sentences = ReaderTextIndex.build(repository.readText(document)).sentences
            assertEquals(sentences.lastIndex, PlaybackStateStore.currentIndex)
            val log = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "logcat -d --pid=${android.os.Process.myPid()} -s VeritasSystemTts")).bufferedReader().use { it.readText() }
            assertFalse(log.contains("chars=0 "))
            command(PlaybackActions.ACTION_SPEAK_SELECTION) { putExtra(PlaybackActions.EXTRA_SELECTION_TEXT, "[12].") }
            awaitStatus("Selected text finished.")
            assertFalse(PlaybackStateStore.isPreparing)
            assertEquals(sentences.lastIndex, PlaybackStateStore.currentIndex)
        } finally {
            command(PlaybackActions.ACTION_STOP)
            context.stopService(Intent(context, PlaybackService::class.java))
            instrumentation.waitForIdleSync()
            activity.close()
            repository.saveVoiceSettings(previousVoice)
            repository.saveNarrationSettings(previousNarration)
            repository.saveReaderSettings(previousReader)
            repository.deleteDocument(document.id)
        }
    }
}
