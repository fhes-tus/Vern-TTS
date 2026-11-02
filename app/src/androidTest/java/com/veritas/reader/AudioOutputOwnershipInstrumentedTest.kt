package com.veritas.reader

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.tts.PcmAudioOutput
import com.veritas.reader.tts.TtsEngine
import com.veritas.reader.tts.VeritasAudioBuffer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

class AudioOutputOwnershipInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext.also { check(it.packageName.endsWith(".checks")) }

    private class Engine(override val sampleRate: Int = 24000, private val frames: Int = sampleRate / 5) : TtsEngine {
        val releases = AtomicInteger()
        val calls = AtomicInteger()
        override fun isReady() = true
        override suspend fun synthesize(sentence: String, rate: Float, pitch: Float): ShortArray {
            calls.incrementAndGet()
            return ShortArray(frames) { 100 }
        }
        override fun shutdown() { releases.incrementAndGet() }
    }

    private fun play(buffer: VeritasAudioBuffer) {
        val finished = CountDownLatch(1)
        var success = false
        buffer.playSentencePcm(0, "Synthetic ownership fixture") { success = it; finished.countDown() }
        assertTrue("Synthetic PCM did not finish", finished.await(20, TimeUnit.SECONDS))
        assertTrue(success)
    }

    private fun awaitWriterRelease(output: PcmAudioOutput) {
        val deadline = SystemClock.elapsedRealtime() + 10000L
        while (output.snapshot().hasWriter && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(20)
        assertFalse("Output writer remained borrowed", output.snapshot().hasWriter)
    }

    @Test fun sameRateModelBuffersReuseOneRealTrack() {
        val output = PcmAudioOutput()
        val firstEngine = Engine()
        val first = VeritasAudioBuffer(context, firstEngine, output = output)
        var second: VeritasAudioBuffer? = null
        try {
            play(first)
            first.shutdown()
            assertEquals(1, firstEngine.releases.get())
            assertEquals(0, output.snapshot().releaseAttempts)
            second = VeritasAudioBuffer(context, Engine(), output = output)
            play(second)
            assertEquals(1, output.snapshot().tracksCreated)
            assertEquals(24000, output.snapshot().sampleRate)
            second.shutdown()
            assertFalse(output.snapshot().closed)
            output.close()
            assertEquals(1, output.snapshot().releaseAttempts)
        } finally { first.shutdown(); second?.shutdown(); output.close(); awaitWriterRelease(output) }
    }

    @Test fun differentRatesRebuildTheTrackBetweenModelBuffers() {
        val output = PcmAudioOutput()
        val first = VeritasAudioBuffer(context, Engine(), output = output)
        var second: VeritasAudioBuffer? = null
        try {
            play(first); first.shutdown()
            second = VeritasAudioBuffer(context, Engine(22050), output = output)
            play(second)
            assertEquals(2, output.snapshot().tracksCreated)
            assertEquals(1, output.snapshot().releaseAttempts)
            assertEquals(22050, output.snapshot().sampleRate)
            second.shutdown(); output.close()
            assertEquals(2, output.snapshot().releaseAttempts)
        } finally { first.shutdown(); second?.shutdown(); output.close(); awaitWriterRelease(output) }
    }

    @Test fun oldShutdownAndFlushCannotInterruptTheNewVoice() {
        val output = PcmAudioOutput()
        val first = VeritasAudioBuffer(context, Engine(frames = 24000), output = output)
        var second: VeritasAudioBuffer? = null
        val firstStarted = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val secondFinished = CountDownLatch(1)
        val oldCompletions = AtomicInteger()
        val oldProgress = AtomicInteger()
        var success = false
        try {
            first.playSentencePcm(0, "Old voice", onStarted = { firstStarted.countDown() },
                onProgress = { _, _, _ -> oldProgress.incrementAndGet() }) { oldCompletions.incrementAndGet() }
            assertTrue(firstStarted.await(10, TimeUnit.SECONDS))
            second = VeritasAudioBuffer(context, Engine(frames = 24000), output = output)
            second.playSentencePcm(0, "New voice", onStarted = { secondStarted.countDown() }) {
                success = it; secondFinished.countDown()
            }
            assertTrue(secondStarted.await(10, TimeUnit.SECONDS))
            val before = oldProgress.get()
            first.flush(); first.shutdown(); first.shutdown()
            assertTrue(secondFinished.await(10, TimeUnit.SECONDS))
            assertTrue(success)
            assertEquals(0, oldCompletions.get())
            assertEquals(before, oldProgress.get())
            assertEquals(1, output.snapshot().tracksCreated)
            assertEquals(0, output.snapshot().releaseAttempts)
        } finally { first.shutdown(); second?.shutdown(); output.close(); awaitWriterRelease(output) }
    }

    @Test fun serviceCleanupClosesItsOutputAndClearsModelReferences() {
        val output = PcmAudioOutput()
        val engine = Engine()
        val buffer = VeritasAudioBuffer(context, engine, output = output)
        play(buffer)
        instrumentation.runOnMainSync {
            val service = PlaybackService()
            try {
                service.veritasAudioBuffer = buffer
                service.retiringAudioBuffer = buffer
                service.veritasAudioOutput = output
                service.veritasAudioVoiceId = "synthetic"
                service.releaseLocalSpeech(); service.releaseLocalSpeech()
                assertNull(service.veritasAudioBuffer)
                assertNull(service.retiringAudioBuffer)
                assertNull(service.veritasAudioVoiceId)
                assertNull(service.veritasAudioOutput)
                assertTrue(output.snapshot().closed)
                assertEquals(1, output.snapshot().releaseAttempts)
                assertEquals(1, engine.releases.get())
            } finally { service.serviceJob.cancel(); buffer.shutdown(); output.close() }
        }
    }

    @Test fun closingBeforeQueuedOutputPreventsSynthesisAndCallbacks() {
        val tasks = ConcurrentLinkedQueue<Runnable>()
        val dispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }
        }
        val output = PcmAudioOutput()
        val engine = Engine()
        val buffer = VeritasAudioBuffer(context, engine, dispatcher, dispatcher, output = output)
        val callbacks = AtomicInteger()
        try {
            buffer.playSentencePcm(0, "Abandoned", onStarted = { callbacks.incrementAndGet() }) { callbacks.incrementAndGet() }
            output.close()
            while (true) (tasks.poll() ?: break).run()
            assertEquals(0, engine.calls.get())
            assertEquals(0, callbacks.get())
            assertEquals(0, output.snapshot().tracksCreated)
        } finally {
            buffer.shutdown(); output.close()
            while (true) (tasks.poll() ?: break).run()
        }
    }

    @Test fun closingDuringRealOutputDefersReleaseAndSuppressesLateCompletion() {
        val output = PcmAudioOutput()
        val buffer = VeritasAudioBuffer(context, Engine(frames = 72000), output = output)
        val started = CountDownLatch(1)
        val callbacks = AtomicInteger()
        try {
            buffer.playSentencePcm(0, "Close mid-output", onStarted = { started.countDown() }) { callbacks.incrementAndGet() }
            assertTrue(started.await(10, TimeUnit.SECONDS))
            assertTrue(output.snapshot().hasWriter)
            output.close(); output.close()
            awaitWriterRelease(output)
            assertEquals(1, output.snapshot().tracksCreated)
            assertEquals(1, output.snapshot().releaseAttempts)
            assertTrue(output.snapshot().closed)
            assertEquals(0, callbacks.get())
        } finally { buffer.shutdown(); output.close(); awaitWriterRelease(output) }
    }
}
