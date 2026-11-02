package com.veritas.reader

import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.tts.TtsEngine
import com.veritas.reader.tts.VeritasAudioBuffer
import com.veritas.reader.tts.PcmCacheBudget
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger
import java.util.Collections
import kotlin.coroutines.CoroutineContext

/** Controlled scheduling; the fake native call cannot be interrupted by coroutine cancellation. */
class SynthesisSchedulingInstrumentedTest {
    private class ManualDispatcher : CoroutineDispatcher() {
        private val tasks = ConcurrentLinkedQueue<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }
        fun runCurrent() {
            var count = 0
            while (true) {
                val task = tasks.poll() ?: return
                check(++count < 10000) { "Scheduling did not settle" }
                task.run()
            }
        }
    }

    private class NativeProbe(private val frames: Int = 2400) : TtsEngine {
        override val sampleRate: Int get() { check(releases == 0); sampleRateReads++; return 24000 }
        val calls = mutableListOf<String>()
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var active = 0
        var maximumActive = 0
        var releases = 0
        var shutdownAttempts = 0
        var sampleRateReads = 0
        override fun isReady() = true
        override suspend fun synthesize(sentence: String, rate: Float, pitch: Float): ShortArray {
            calls.add(sentence)
            active++
            maximumActive = maxOf(active, maximumActive)
            try {
                if (calls.size == 1) withContext(NonCancellable) {
                    firstEntered.complete(Unit)
                    releaseFirst.await()
                }
                return ShortArray(frames) { 100 }
            } finally { active-- }
        }
        override fun shutdown() { shutdownAttempts++; check(active == 0); releases++ }
    }

    private fun assertOversizedLookAheadStops(sampleRate: Int, frames: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val dispatcher = ManualDispatcher()
        val calls = mutableListOf<String>()
        val buffer = VeritasAudioBuffer(context, object : TtsEngine {
            override val sampleRate = sampleRate
            override fun isReady() = true
            override suspend fun synthesize(sentence: String, rate: Float, pitch: Float): ShortArray {
                calls.add(sentence)
                return ShortArray(frames) { 100 }
            }
            override fun shutdown() = Unit
        }, dispatcher, dispatcher)
        try {
            buffer.prebufferAhead(listOf("oversized", "farther"), -1, 2)
            dispatcher.runCurrent()
            assertEquals("Stop speculative work after a clip exceeds the cache budget", listOf("oversized"), calls)
            assertEquals(0L, buffer.cacheSnapshot().aheadBytes)
            assertEquals(0L, buffer.cacheSnapshot().aheadDurationMs)
        } finally { buffer.shutdown(); dispatcher.runCurrent() }
    }

    @Test fun durationOverflowStopsSpeculatingOnFartherSentences() {
        assertOversizedLookAheadStops(24000, 24000 * 31)
    }

    @Test fun byteOverflowStopsSpeculatingEvenWhenDurationIsShort() {
        assertOversizedLookAheadStops(192000, 192000 * 12)
    }

    @Test fun advancingPromotesCachedAudioAndRefillsTheBudgetWithoutRegeneratingCurrent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val dispatcher = ManualDispatcher()
        val engine = NativeProbe()
        val buffer = VeritasAudioBuffer(context, engine, dispatcher, dispatcher,
            PcmCacheBudget(maxBytes = 100000L, maxDurationMs = 150L))
        val chunks = listOf("current", "one", "two", "three", "four")
        fun ahead() = VeritasAudioBuffer.LookAhead(chunks.size, 4) { chunks[it] }
        try {
            buffer.playSentencePcm(0, chunks[0], lookAhead = ahead()) { }
            dispatcher.runCurrent()
            engine.releaseFirst.complete(Unit)
            dispatcher.runCurrent()
            assertEquals(listOf("current", "one", "two"), engine.calls)
            assertEquals(1, buffer.cacheSnapshot().aheadCount)
            assertEquals(100L, buffer.cacheSnapshot().aheadDurationMs)
            buffer.playSentencePcm(1, chunks[1], lookAhead = ahead()) { }
            dispatcher.runCurrent()
            assertEquals(listOf("current", "one", "two", "two", "three"), engine.calls)
            assertEquals(1, engine.calls.count { it == "one" })
            assertEquals(1, buffer.cacheSnapshot().aheadCount)
            assertEquals(100L, buffer.cacheSnapshot().aheadDurationMs)
            assertEquals(1, engine.maximumActive)
        } finally { engine.releaseFirst.complete(Unit); buffer.shutdown(); dispatcher.runCurrent() }
        assertEquals(0L, buffer.cacheSnapshot().aheadBytes)
        assertEquals(0L, buffer.cacheSnapshot().currentBytes)
    }

    @Test fun currentAudioCompletesUntrimmedWithAZeroFutureBudget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val frames = 12000
        val buffer = VeritasAudioBuffer(context, object : TtsEngine {
            override val sampleRate = 24000
            override fun isReady() = true
            override suspend fun synthesize(sentence: String, rate: Float, pitch: Float): ShortArray {
                calls.add(sentence); return ShortArray(frames) { 100 }
            }
            override fun shutdown() = Unit
        }, cacheBudget = PcmCacheBudget(0L, 0L))
        val done = CountDownLatch(1)
        val currentBytes = AtomicLong()
        val observedFrames = AtomicInteger()
        var success = false
        try {
            buffer.playSentencePcm(0, "current", onStarted = {
                currentBytes.set(buffer.cacheSnapshot().currentBytes)
            }, onProgress = { _, total, _ -> observedFrames.set(total) },
                lookAhead = VeritasAudioBuffer.LookAhead(3) { "must not generate" }) {
                success = it; done.countDown()
            }
            assertTrue("Complete protected PCM did not reach the output head", done.await(20, TimeUnit.SECONDS))
            assertTrue(success)
            assertEquals(frames, observedFrames.get())
            assertEquals(frames.toLong() * 2L, currentBytes.get())
            assertEquals(listOf("current"), calls)
            assertEquals(0L, buffer.cacheSnapshot().aheadBytes)
            assertEquals(0L, buffer.cacheSnapshot().currentBytes)
        } finally { buffer.shutdown() }
    }

    @Test fun flushDropsTheCacheAndRejectsAnInFlightFutureClip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val dispatcher = ManualDispatcher()
        val engine = NativeProbe()
        val buffer = VeritasAudioBuffer(context, engine, dispatcher, dispatcher)
        try {
            buffer.prebufferAhead(listOf("unused", "old", "farther"), 0, 2)
            dispatcher.runCurrent()
            assertTrue(engine.firstEntered.isCompleted)
            buffer.flush()
            engine.releaseFirst.complete(Unit)
            dispatcher.runCurrent()
            assertEquals(listOf("old"), engine.calls)
            assertEquals(0L, buffer.cacheSnapshot().aheadBytes)
            assertEquals(0L, buffer.cacheSnapshot().currentBytes)
            buffer.prebufferAhead(listOf("unused", "new"), 0, 1)
            dispatcher.runCurrent()
            assertEquals(listOf("old", "new"), engine.calls)
            assertEquals(4800L, buffer.cacheSnapshot().aheadBytes)
        } finally { engine.releaseFirst.complete(Unit); buffer.shutdown(); dispatcher.runCurrent() }
    }

    @Test fun oversizedInFlightAudioCanBecomeProtectedDemandWithoutRegeneration() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val worker = ManualDispatcher()
        val output = ManualDispatcher()
        val engine = NativeProbe(frames = 4800)
        val buffer = VeritasAudioBuffer(context, engine, worker, output,
            PcmCacheBudget(maxBytes = 100000L, maxDurationMs = 150L))
        try {
            buffer.prebufferAhead(listOf("unused", "wanted"), 0, 1)
            worker.runCurrent()
            assertTrue(engine.firstEntered.isCompleted)
            buffer.playSentencePcm(1, "wanted") { }
            engine.releaseFirst.complete(Unit)
            worker.runCurrent()
            assertEquals(9600L, buffer.cacheSnapshot().currentBytes)
            assertEquals(200L, buffer.cacheSnapshot().currentDurationMs)
            assertEquals(0L, buffer.cacheSnapshot().aheadBytes)
            output.runCurrent()
            worker.runCurrent()
            assertEquals(listOf("wanted"), engine.calls)
            assertEquals(1, engine.maximumActive)
        } finally {
            engine.releaseFirst.complete(Unit); buffer.shutdown(); worker.runCurrent(); output.runCurrent()
        }
    }

    @Test fun cacheMissServesDemandThenContinuesTheExistingAheadWindow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val dispatcher = ManualDispatcher()
        val engine = NativeProbe()
        val buffer = VeritasAudioBuffer(context, engine, dispatcher, dispatcher)
        try {
            buffer.prebufferAhead(listOf("current", "ahead-one", "ahead-two"), 0, 2)
            dispatcher.runCurrent()
            assertTrue(engine.firstEntered.isCompleted)
            buffer.playSentencePcm(0, "current") { }
            dispatcher.runCurrent()
            engine.releaseFirst.complete(Unit)
            dispatcher.runCurrent()
            assertEquals(listOf("ahead-one", "current", "ahead-two"), engine.calls)
            assertEquals(1, engine.maximumActive)
            assertEquals(3, engine.sampleRateReads)
        } finally {
            engine.releaseFirst.complete(Unit)
            buffer.shutdown()
            dispatcher.runCurrent()
        }
        assertEquals(1, engine.releases)
    }

    @Test fun demandAdoptsAMatchingInFlightAheadResultInsteadOfGeneratingItTwice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val dispatcher = ManualDispatcher()
        val engine = NativeProbe()
        val buffer = VeritasAudioBuffer(context, engine, dispatcher, dispatcher)
        try {
            buffer.prebufferAhead(listOf("previous", "wanted"), 0, 1)
            dispatcher.runCurrent()
            assertTrue(engine.firstEntered.isCompleted)
            buffer.playSentencePcm(1, "wanted") { }
            dispatcher.runCurrent()
            engine.releaseFirst.complete(Unit)
            dispatcher.runCurrent()
            assertEquals(listOf("wanted"), engine.calls)
            assertEquals(1, engine.maximumActive)
            assertEquals(1, engine.sampleRateReads)
        } finally {
            engine.releaseFirst.complete(Unit)
            buffer.shutdown()
            dispatcher.runCurrent()
        }
        assertEquals(1, engine.releases)
    }

    @Test fun combinedColdRequestReservesCurrentSynthesisBeforeStartingLookAhead() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val dispatcher = ManualDispatcher()
        val engine = NativeProbe()
        val buffer = VeritasAudioBuffer(context, engine, dispatcher, dispatcher)
        val chunks = listOf("current", "ahead-one", "ahead-two")
        try {
            buffer.playSentencePcm(0, chunks[0], lookAhead = VeritasAudioBuffer.LookAhead(chunks.size, 2) { chunks[it] }) { }
            dispatcher.runCurrent()
            assertEquals(listOf("current"), engine.calls)
            engine.releaseFirst.complete(Unit)
            dispatcher.runCurrent()
            assertEquals(listOf("current", "ahead-one", "ahead-two"), engine.calls)
            assertEquals(1, engine.maximumActive)
        } finally {
            engine.releaseFirst.complete(Unit); buffer.shutdown(); dispatcher.runCurrent()
        }
    }

    @Test fun flushBeforeTheOutputJobStartsDoesNotLeakADemandReservation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val dispatcher = ManualDispatcher()
        val engine = NativeProbe()
        val buffer = VeritasAudioBuffer(context, engine, dispatcher, dispatcher)
        var abandonedCallbacks = 0
        try {
            buffer.playSentencePcm(0, "abandoned") { abandonedCallbacks++ }
            buffer.flush()
            engine.releaseFirst.complete(Unit)
            buffer.prebufferAhead(listOf("unused", "new-ahead"), 0, 1)
            dispatcher.runCurrent()
            assertEquals(listOf("new-ahead"), engine.calls)
            assertEquals(0, abandonedCallbacks)
        } finally {
            engine.releaseFirst.complete(Unit); buffer.shutdown(); dispatcher.runCurrent()
        }
    }

    @Test fun aLatePreFlushResultCannotSatisfyAnIdenticalNewRequest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val dispatcher = ManualDispatcher()
        val engine = NativeProbe()
        val buffer = VeritasAudioBuffer(context, engine, dispatcher, dispatcher)
        try {
            buffer.prebufferAhead(listOf("unused", "wanted"), 0, 1)
            dispatcher.runCurrent()
            assertTrue(engine.firstEntered.isCompleted)
            buffer.flush()
            buffer.playSentencePcm(1, "wanted") { }
            dispatcher.runCurrent()
            engine.releaseFirst.complete(Unit)
            dispatcher.runCurrent()
            assertEquals(listOf("wanted", "wanted"), engine.calls)
            assertEquals(1, engine.maximumActive)
        } finally {
            engine.releaseFirst.complete(Unit); buffer.shutdown(); dispatcher.runCurrent()
        }
    }

    @Test fun shutdownWaitsForTheRunningCallAndReleasesTheEngineOnlyOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val dispatcher = ManualDispatcher()
        val engine = NativeProbe()
        val buffer = VeritasAudioBuffer(context, engine, dispatcher, dispatcher)
        try {
            buffer.prebufferAhead(listOf("unused", "running"), 0, 1)
            dispatcher.runCurrent()
            assertEquals(1, engine.active)
            buffer.shutdown()
            dispatcher.runCurrent()
            assertEquals(0, engine.shutdownAttempts)
            buffer.playSentencePcm(0, "after close") { fail("Closed buffer published playback") }
            dispatcher.runCurrent()
            engine.releaseFirst.complete(Unit)
            dispatcher.runCurrent()
            buffer.shutdown()
            dispatcher.runCurrent()
            assertEquals(listOf("running"), engine.calls)
            assertEquals(1, engine.shutdownAttempts)
            assertEquals(1, engine.releases)
        } finally {
            engine.releaseFirst.complete(Unit); buffer.shutdown(); dispatcher.runCurrent()
        }
    }

    @Test fun failedDemandStopsItsAheadWindowBeforeReleasingPriority() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val dispatcher = ManualDispatcher()
        val calls = mutableListOf<String>()
        val buffer = VeritasAudioBuffer(context, object : TtsEngine {
            override val sampleRate = 24000
            override fun isReady() = true
            override suspend fun synthesize(sentence: String, rate: Float, pitch: Float): ShortArray? {
                calls.add(sentence); return null
            }
            override fun shutdown() = Unit
        }, dispatcher, dispatcher)
        val finished = CountDownLatch(1)
        var successful = true
        try {
            buffer.playSentencePcm(0, "current", lookAhead = VeritasAudioBuffer.LookAhead(2) { "must not run" }) {
                successful = it; finished.countDown()
            }
            dispatcher.runCurrent()
            assertTrue(finished.await(20, TimeUnit.SECONDS))
            dispatcher.runCurrent()
            assertFalse(successful)
            assertEquals(listOf("current"), calls)
        } finally { buffer.shutdown(); dispatcher.runCurrent() }
    }
}
