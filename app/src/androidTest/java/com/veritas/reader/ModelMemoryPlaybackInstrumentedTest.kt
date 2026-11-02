package com.veritas.reader

import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.tts.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*

class ModelMemoryPlaybackInstrumentedTest {
    @Test fun pressureRetiresModelsWithoutInterruptingOwnedAudioTrack() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val resources = NativeModelResources(scope = CoroutineScope(SupervisorJob() + Dispatchers.Default))
        val creations = AtomicInteger(); val frees = AtomicInteger()
        val model = NativeModel(resources, SpeechModelRole.LIVE, { creations.incrementAndGet() }, { frees.incrementAndGet() })
        val engine = object : TtsEngine, PlaybackModelPin {
            override val sampleRate = 24000
            override fun isReady() = true
            override suspend fun synthesize(sentence: String, rate: Float, pitch: Float) = model.use {
                ShortArray(12000) { n -> (1000 * sin(2 * PI * 220 * n / sampleRate)).toInt().toShort() }
            }
            override fun setPlaybackActive(active: Boolean) = model.setPlaybackActive(active)
            override fun shutdown() = model.close()
            override suspend fun awaitShutdown() = model.awaitClosed()
        }
        val output = PcmAudioOutput()
        val buffer = VeritasAudioBuffer(context, engine, output = output)
        try {
            val done = CompletableDeferred<Boolean>()
            var started = false
            buffer.playSentencePcm(0, "Protected PCM.", onStarted = {
                started = true
                resources.admission.trim(true)
                assertEquals(1, resources.admission.snapshot().pinned)
                assertEquals(0, frees.get())
            }, onComplete = { done.complete(it) })
            assertTrue(withTimeout(10_000) { done.await() }); assertTrue(started)
            buffer.releaseModelPin()
            resources.admission.trim(true)
            withTimeout(5_000) { while (frees.get() != 1) delay(10) }
            assertEquals(1, creations.get())
            val next = CompletableDeferred<Boolean>()
            buffer.playSentencePcm(1, "Reloaded model.", onComplete = { next.complete(it) })
            assertTrue(withTimeout(10_000) { next.await() })
            assertEquals(2, creations.get())
        } finally {
            buffer.shutdown(); buffer.awaitShutdown(); output.close(); resources.scope.cancel()
        }
        assertEquals(2, frees.get())
    }
}