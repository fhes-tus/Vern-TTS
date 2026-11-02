package com.veritas.reader

import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.speech.tts.TextToSpeech
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

/** Real service/Android speech checks in a disposable package; local-model tests use explicit synthetic PCM. */
class BoundedSpeechPlaybackInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext.also { check(it.packageName.endsWith(".checks")) }
    private data class Submission(val id: String, val selection: Boolean, val sentence: Int, val chars: Int, val limit: Int, val end: Int)

    private fun submissions(): List<Submission> {
        val output = instrumentation.uiAutomation.executeShellCommand("logcat -d --pid=${android.os.Process.myPid()} -s VeritasSystemTts")
        val text = ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().use { it.readText() }
        val pattern = Regex("Submit speech part request=\\S+ id=(\\S+) selection=(true|false) sentence=(-?\\d+) chars=(\\d+) limit=(\\d+) source=\\d+\\.\\.(\\d+)")
        return pattern.findAll(text).map { match -> Submission(match.groupValues[1], match.groupValues[2].toBoolean(),
            match.groupValues[3].toInt(), match.groupValues[4].toInt(), match.groupValues[5].toInt(), match.groupValues[6].toInt()) }
            .distinctBy { it.id }.toList()
    }

    private fun await(label: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 40_000L
        while (!predicate()) {
            check(SystemClock.elapsedRealtime() < deadline) { "$label: ${PlaybackStateStore.statusMessage}" }
            SystemClock.sleep(100)
        }
    }

    private fun command(action: String, configure: Intent.() -> Unit = {}) {
        instrumentation.runOnMainSync {
            context.startService(PlaybackCommandSecurity.authorize(context,
                Intent(context, PlaybackService::class.java).setAction(action).apply(configure)))
        }
    }

    private fun withService(block: (DocumentRepository) -> Unit) {
        val repository = DocumentRepository(context)
        val previousVoice = repository.loadVoiceSettings()
        val previousNarration = repository.loadNarrationSettings()
        val previousReader = repository.loadReaderSettings()
        val previousRules = repository.loadPronunciationRules()
        context.stopService(Intent(context, PlaybackService::class.java))
        instrumentation.waitForIdleSync()
        repository.markOnboardingComplete("Speech checks")
        val voice = runBlocking { offlineEnglishTestVoice(context) }
        repository.saveVoiceSettings(VoiceSettings(enginePackage = voice.enginePackage, voiceName = voice.name, localeTag = voice.localeTag))
        repository.saveNarrationSettings(NarrationSettings())
        repository.saveReaderSettings(previousReader.copy(autoPlayQueue = false))
        repository.savePronunciationRules(emptyList())
        instrumentation.runOnMainSync { PlaybackStateStore.reset(); PlaybackStateStore.rate = 2f }
        val activity = ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java))
        try { block(repository) }
        finally {
            command(PlaybackActions.ACTION_STOP)
            context.stopService(Intent(context, PlaybackService::class.java))
            instrumentation.waitForIdleSync()
            activity.close()
            repository.saveVoiceSettings(previousVoice)
            repository.saveNarrationSettings(previousNarration)
            repository.saveReaderSettings(previousReader)
            repository.savePronunciationRules(previousRules)
        }
    }

    @Test fun longSelectionKeepsItsEndingAndUsesBoundedRealServiceRequests() = withService { repository ->
        val beforeDocuments = repository.loadDocuments().map { it.id }
        val before = submissions().map { it.id }.toSet()
        val source = "Begin " + "#".repeat(1300) + " ".repeat(TextToSpeech.getMaxSpeechInputLength()) + "END"
        val initialIndex = PlaybackStateStore.currentIndex
        command(PlaybackActions.ACTION_SPEAK_SELECTION) { putExtra(PlaybackActions.EXTRA_SELECTION_TEXT, source) }
        await("Complete long selection") { PlaybackStateStore.statusMessage == "Selected text finished." }
        val sent = submissions().filter { it.id !in before && it.selection }
        assertTrue("Expected multiple native requests", sent.size >= 2)
        assertTrue(sent.all { it.chars in 1..it.limit && it.limit == TextToSpeech.getMaxSpeechInputLength() })
        assertEquals("The ending beyond the old selection cap must be submitted", source.length, sent.last().end)
        assertEquals(initialIndex, PlaybackStateStore.currentIndex)
        assertEquals(beforeDocuments, repository.loadDocuments().map { it.id })

        // The reader action normalizes whitespace before the intent. Its text still
        // exceeds the former 1,200-character cap and must retain the final word.
        val beforeReaderAction = submissions().map { it.id }.toSet()
        val cleaned = source.replace(Regex("\\s+"), " ").trim()
        assertTrue(cleaned.length > 1200)
        instrumentation.runOnMainSync {
            PlaybackStateStore.statusMessage = "Preparing selection check."
            sendSelectionSpeechIntent(context, source)
        }
        await("Reader action selection completes") { PlaybackStateStore.statusMessage == "Selected text finished." }
        val readerSent = submissions().filter { it.id !in beforeReaderAction && it.selection }
        assertTrue(readerSent.isNotEmpty())
        assertEquals(cleaned.length, readerSent.last().end)
        assertEquals(initialIndex, PlaybackStateStore.currentIndex)
    }

    @Test fun pronunciationExpansionFinishesBeforeAdvancingToTheNextSentence() = withService { repository ->
        val token = "Longfixturetoken"
        repository.savePronunciationRules(listOf(PronunciationRule("bounded", token,
            "Begin " + "#".repeat(TextToSpeech.getMaxSpeechInputLength() + 50) + " TAIL")))
        val saved = repository.createDocument("Speech expansion ${UUID.randomUUID()}", "$token finishes now. Next sentence ends.", "TXT")
        val before = submissions().map { it.id }.toSet()
        try {
            command(PlaybackActions.ACTION_PLAY) {
                putExtra(PlaybackActions.EXTRA_DOCUMENT_ID, saved.id)
                putExtra(PlaybackActions.EXTRA_START_INDEX, 0)
                putExtra(PlaybackActions.EXTRA_RATE, 2f)
            }
            await("Complete expanded reading") { PlaybackStateStore.statusMessage == "Finished reading." }
            val sent = submissions().filter { it.id !in before && !it.selection }
            val nextSentence = sent.indexOfFirst { it.sentence == 1 }
            assertTrue("Every expanded part precedes the next sentence", nextSentence >= 2)
            assertTrue(sent.take(nextSentence).all { it.sentence == 0 })
            assertTrue(sent.all { it.chars in 1..it.limit })
            assertEquals(1, PlaybackStateStore.currentIndex)
        } finally { repository.deleteDocument(saved.id) }
    }

    @Test fun pausingASelectionPreventsItsRemainingPartsFromBeingSubmitted() = withService {
        val before = submissions().map { it.id }.toSet()
        val source = "Please listen carefully while this sentence is being read and then pause the selection. " +
            "#".repeat(TextToSpeech.getMaxSpeechInputLength() + 100) + " Do not continue."
        command(PlaybackActions.ACTION_SPEAK_SELECTION) { putExtra(PlaybackActions.EXTRA_SELECTION_TEXT, source) }
        await("First selection part submitted") { submissions().any { it.id !in before && it.selection } }
        command(PlaybackActions.ACTION_PAUSE)
        await("Selection paused") { PlaybackStateStore.statusMessage == "Paused." }
        val afterPause = submissions().map { it.id }.toSet()
        SystemClock.sleep(1000)
        assertTrue(submissions().all { it.id in afterPause })
        assertEquals("Paused.", PlaybackStateStore.statusMessage)
    }

    @Test fun offlinePartsUseTheRealBufferWithBoundedSyntheticEngineCalls() {
        val source = "word ".repeat(350) + "FINAL."
        val segment = SpeechPlan.segment("fixture", "revision", 3, source,
            VoiceSettings(enginePackage = VoiceManager.VERITAS_LITE, voiceName = "piper_fixture", localeTag = "en-US"),
            NarrationSettings(), 1f, 1f, emptyList())
        val parts = SpeechPlan.split(segment, SpeechInputBudget.OFFLINE_TEXT)
        val sequence = SpeechPartSequence(parts, false, "fixture:")
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val engine = object : TtsEngine {
            override val sampleRate = 24000
            override fun isReady() = true
            override suspend fun synthesize(sentence: String, rate: Float, pitch: Float): ShortArray {
                require(sentence.length <= SpeechInputBudget.OFFLINE_TEXT)
                calls.add(sentence)
                return ShortArray(2400) { 100 }
            }
            override fun shutdown() = Unit
        }
        val buffer = VeritasAudioBuffer(context, engine)
        val done = CountDownLatch(1)
        var successful = true
        fun playNext() {
            val part = requireNotNull(sequence.current)
            val id = sequence.utteranceId
            buffer.playSentencePcm(part.sentenceIndex, part.spoken.text) { success ->
                if (!success) { successful = false; done.countDown() }
                else if (sequence.complete(id) == SpeechPartSequence.Completion.NEXT) playNext()
                else done.countDown()
            }
        }
        try {
            playNext()
            assertTrue("Synthetic offline parts did not complete", done.await(20, TimeUnit.SECONDS))
            assertTrue("Short PCM parts must reach the playback head", successful)
            assertEquals(parts.size, calls.size)
            assertEquals(source.trim(), calls.joinToString(" ").replace(Regex("\\s+"), " "))
        } finally { buffer.shutdown() }
    }

    @Test fun serviceCancellationInvalidatesReadingAndSelectionTokens() {
        check(context.packageName.endsWith(".checks"))
        instrumentation.runOnMainSync {
            for (selection in listOf(false, true)) {
                val owner = PlaybackService()
                val segment = SpeechPlan.segment("fixture", "revision", 0, "One two three.", VoiceSettings(),
                    NarrationSettings(), 1f, 1f, emptyList())
                val sequence = SpeechPartSequence(SpeechPlan.split(segment, 5), selection, "fixture:")
                val id = sequence.utteranceId
                owner.speechParts = sequence
                if (!selection) owner.activeChunkUtteranceId = id
                owner.queuedChunkUtteranceId = "queued-fixture"
                owner.cancelSpeechParts()
                assertNull(owner.speechParts)
                assertNull(owner.activeChunkUtteranceId)
                assertNull(owner.queuedChunkUtteranceId)
                assertEquals(SpeechPartSequence.Completion.IGNORED, sequence.complete(id))
                // Normal completion also calls this cleanup before consulting retired IDs.
                assertFalse(owner.retiredUtteranceIds.contains(id))
                owner.serviceJob.cancel()
            }
        }
    }
}
