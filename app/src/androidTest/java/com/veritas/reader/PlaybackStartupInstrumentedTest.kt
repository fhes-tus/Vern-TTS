package com.veritas.reader

import android.os.Looper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.tts.DeferredTtsEngine
import com.veritas.reader.tts.TtsEngine
import com.veritas.reader.tts.VeritasAudioBuffer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PlaybackStartupInstrumentedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun preparingControlCanCancelInBothMotionModes() {
        val previous = VeritasThemeState.reduceMotion
        var taps = 0
        try {
            rule.setContent { VeritasTheme {
                Box(Modifier.clickable { taps++; PlaybackStateStore.isPlaying = false }) {
                    PlaybackControlIcon(PlaybackStateStore.isPlaying, PlaybackStateStore.isPreparingFor("fixture"))
                }
            } }
            listOf(false, true).forEach { reduced ->
                rule.runOnIdle {
                    VeritasThemeState.reduceMotion = reduced
                    PlaybackStateStore.beginPreparing("fixture")
                    PlaybackStateStore.isPlaying = true
                }
                rule.onNodeWithContentDescription("Cancel starting playback").assertExists()
                rule.onNodeWithContentDescription("Cancel starting playback").performClick()
                rule.onNodeWithContentDescription("Play").assertExists()
                rule.onNodeWithContentDescription("Cancel starting playback").assertDoesNotExist()
            }
            assertEquals(2, taps)
        } finally { rule.runOnIdle { PlaybackStateStore.reset(); VeritasThemeState.reduceMotion = previous } }
    }

    @Test fun systemVoiceClearsPreparationAtSpeechStartAndOnStop() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val repository = DocumentRepository(context)
        val previousVoice = repository.loadVoiceSettings()
        val document = repository.createDocument("Playback startup fixture",
            "This reading checks that playback feedback ends when the voice actually starts speaking, and that stopping playback clears the loading state.", "TXT")
        try {
            rule.setContent { Box {} }
            repository.saveVoiceSettings(previousVoice.copy(enginePackage = "", voiceName = "", localeTag = "en-US"))
            rule.runOnIdle {
                sendPlaybackIntent(context, PlaybackActions.ACTION_PLAY, document.id, 0)
                assertTrue(PlaybackStateStore.isPreparingFor(document.id))
            }
            rule.waitUntil(20_000) { PlaybackStateStore.isPlaying && !PlaybackStateStore.isPreparing }
            rule.runOnIdle { sendPlaybackIntent(context, PlaybackActions.ACTION_STOP) }
            rule.waitUntil(5_000) { !PlaybackStateStore.isPlaying && !PlaybackStateStore.isPreparing }
        } finally {
            rule.runOnIdle { context.stopService(android.content.Intent(context, PlaybackService::class.java)); PlaybackStateStore.reset() }
            repository.saveVoiceSettings(previousVoice)
            repository.deleteDocument(document.id)
        }
    }

    @Test fun neuralStartFollowsOutputAndPrecedesSentenceCompletion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val synthesized = CountDownLatch(1)
        val release = CompletableDeferred<Unit>()
        val started = CountDownLatch(1)
        val completed = CountDownLatch(1)
        val starts = AtomicInteger()
        val finished = AtomicInteger()
        var initializedOnMain = true
        var callbackOnMain = false
        val buffer = VeritasAudioBuffer(context, DeferredTtsEngine {
            initializedOnMain = Looper.myLooper() == Looper.getMainLooper()
            object : TtsEngine {
                override val sampleRate = 24000
                override fun isReady() = true
                override fun shutdown() {}
                override suspend fun synthesize(sentence: String, rate: Float, pitch: Float): ShortArray {
                    synthesized.countDown()
                    release.await()
                    return ShortArray(sampleRate * 2)
                }
            }
        })
        try {
            buffer.playSentencePcm(0, "Delayed synthetic fixture", onStarted = {
                callbackOnMain = Looper.myLooper() == Looper.getMainLooper()
                starts.incrementAndGet(); started.countDown()
            }) { success -> if (success) finished.incrementAndGet(); completed.countDown() }
            assertTrue(synthesized.await(5, TimeUnit.SECONDS))
            assertEquals(1L, started.count) // still waiting for PCM, not reading yet
            release.complete(Unit)
            assertTrue("Actual output start was not signalled", started.await(5, TimeUnit.SECONDS))
            assertEquals("Start must precede complete drainage", 1L, completed.count)
            assertTrue(completed.await(8, TimeUnit.SECONDS))
            assertEquals(1, starts.get())
            assertEquals(1, finished.get())
            assertFalse("Model construction blocked the main thread", initializedOnMain)
            assertTrue(callbackOnMain)
        } finally { release.complete(Unit); buffer.shutdown() }
    }

    @Test fun cancelledNativeSynthesisCannotPublishAStart() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entered = CountDownLatch(1)
        val left = CountDownLatch(1)
        val release = CompletableDeferred<Unit>()
        val callbacks = AtomicInteger()
        val buffer = VeritasAudioBuffer(context, object : TtsEngine {
            override val sampleRate = 24000
            override fun isReady() = true
            override fun shutdown() {}
            override suspend fun synthesize(sentence: String, rate: Float, pitch: Float): ShortArray =
                withContext(NonCancellable) {
                    entered.countDown(); release.await(); left.countDown(); ShortArray(sampleRate)
                }
        })
        try {
            buffer.playSentencePcm(0, "Cancelled fixture", onStarted = { callbacks.incrementAndGet() }) { callbacks.incrementAndGet() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            buffer.flush()
            release.complete(Unit)
            assertTrue(left.await(5, TimeUnit.SECONDS))
            // Allow the cancelled coroutine to discard its late native return.
            android.os.SystemClock.sleep(150)
            assertEquals(0, callbacks.get())
        } finally { release.complete(Unit); buffer.shutdown() }
    }

    @Test fun failedSynthesisNeverClaimsOutputStarted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val completed = CountDownLatch(1)
        val starts = AtomicInteger()
        var success = true
        val buffer = VeritasAudioBuffer(context, object : TtsEngine {
            override val sampleRate = 24000
            override fun isReady() = false
            override fun shutdown() {}
            override suspend fun synthesize(sentence: String, rate: Float, pitch: Float): ShortArray? = null
        })
        try {
            buffer.playSentencePcm(0, "Failed fixture", onStarted = { starts.incrementAndGet() }) {
                success = it; completed.countDown()
            }
            assertTrue(completed.await(5, TimeUnit.SECONDS))
            assertFalse(success)
            assertEquals(0, starts.get())
        } finally { buffer.shutdown() }
    }
    @Test fun rsvpTemporaryPaceIsNotSavedAndClosingRestoresIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val repository = DocumentRepository(context)
        val previous = repository.loadVoiceSettings()
        val document = repository.createDocument("RSVP temporary pace fixture",
            "The reader is checking a long sentence with enough spoken words to pause before the sentence ends and to check the book's saved pace remains unchanged throughout audio RSVP playback.", "TXT")
        repository.saveDocVoiceMemory(document.id, 1.1f, .9f)
        lateinit var session: RsvpPlaybackSession
        try {
            rule.setContent { Box {} }
            repository.saveVoiceSettings(VoiceSettings())
            rule.runOnIdle {
                context.stopService(android.content.Intent(context, PlaybackService::class.java))
                PlaybackStateStore.reset()
                session = RsvpPlaybackSession(context, document.id)
                session.enter()
                session.play(0, 4, 1.75f)
            }
            rule.waitUntil(20_000) { PlaybackStateStore.speechPosition?.documentId == document.id }
            assertTrue(PlaybackStateStore.speechPosition!!.sourceStart >= 4)
            assertEquals(1.1f, repository.loadDocVoiceMemory(document.id)!!.first, .001f)
            rule.runOnIdle { session.close() }
            rule.waitUntil(5_000) { !PlaybackStateStore.isPlaying && !PlaybackStateStore.isPreparing && PlaybackStateStore.rate == 1.1f }
            assertEquals(.9f, PlaybackStateStore.pitch, .001f)
            assertEquals(1.1f, repository.loadDocVoiceMemory(document.id)!!.first, .001f)
        } finally {
            rule.runOnIdle { context.stopService(android.content.Intent(context, PlaybackService::class.java)); PlaybackStateStore.reset() }
            repository.saveVoiceSettings(previous)
            repository.deleteDocument(document.id)
        }
    }

}
