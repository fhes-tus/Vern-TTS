package com.veritas.reader.tts

import android.content.Context
import android.util.Log
import com.veritas.reader.SpeechDelivery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * One serialized synthesis owner with demand priority and byte/duration-bounded future PCM.
 * Current output is protected independently; budgets never crop a spoken clip.
 * Actual playback gaps depend on synthesis throughput and the output route.
 */
class VeritasAudioBuffer(
    private val context: Context,
    private val engine: TtsEngine,
    workDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val outputDispatcher: CoroutineDispatcher = Dispatchers.IO,
    cacheBudget: PcmCacheBudget = PcmCacheBudget(),
    output: PcmAudioOutput? = null,
    private val resourcePolicy: SpeechResourcePolicy = SpeechResourcePolicy(),
    private val clock: SpeechClock = SpeechClock { android.os.SystemClock.elapsedRealtime() }
) {
    data class LookAhead(val chunkCount: Int, val windowSize: Int = 4, val textAt: (Int) -> String)

    private val scope = CoroutineScope(workDispatcher + SupervisorJob())
    private val pcmCache = PcmLookAheadCache(cacheBudget.copy(maxAgeMs = minOf(cacheBudget.maxAgeMs, resourcePolicy.futurePcmMs)), clock)
    private val generation = PlaybackGeneration()
    private val playbackMutex = Mutex()
    private var playbackJob: Job? = null
    private val synthesisGate = SynthesisPriorityGate()
    private val activeDemand = AtomicReference<SynthesisPriorityGate.Demand?>(null)
    private val aheadProducer = LookAheadProducer(scope, ::synthesizeAhead,
        onFailure = { Log.w(TAG, "Ahead preparation failed", it) },
        shouldContinue = { window, index -> !clock.expired(window.queuedAtMs, resourcePolicy.aheadQueueMs) && pcmCache.mayVisit(index) })

    init {
        // Expire retained references even during a long current clip. Keep this
        // housekeeping independent of an injected synthesis/test dispatcher.
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(1_000)
                pcmCache.expireAhead()
                aheadProducer.current?.takeIf { clock.expired(it.queuedAtMs, resourcePolicy.aheadQueueMs) }
                    ?.let(aheadProducer::clearIf)
            }
        }
    }

    internal fun cacheSnapshot(): PcmLookAheadCache.Snapshot = pcmCache.snapshot()
    internal fun releaseModelPin() { (engine as? PlaybackModelPin)?.setPlaybackActive(false) }

    private val ownsOutput = output == null
    private val audioOutput = output ?: PcmAudioOutput()
    private val outputOwner = audioOutput.newOwner()

    private fun isCurrent(token: Long) = outputOwner.active && generation.isCurrent(token)

    // Read by the playback wait loop on Dispatchers.IO and written by flush() on the
    // service's main thread. Without @Volatile that read can be hoisted out of the loop
    // and the stop never observed, leaving a cancelled sentence spinning to its timeout.
    @Volatile
    private var isPlaying = false

    private val deliveryLock = Any()
    private var lastBufferRate: Float = 1.0f
    private var lastBufferPitch: Float = 1.0f

    fun prebufferAhead(chunks: List<String>, currentIndex: Int, windowSize: Int = 4, rate: Float = 1.0f, pitch: Float = 1.0f) {
        prebufferAhead(chunks.size, currentIndex, windowSize, rate, pitch) { index ->
            chunks.getOrNull(index).orEmpty()
        }
    }

    fun prebufferAhead(
        chunkCount: Int,
        currentIndex: Int,
        windowSize: Int = 4,
        rate: Float = 1.0f,
        pitch: Float = 1.0f,
        textAt: (Int) -> String
    ) {
        val safeRate = SpeechDelivery.rate(rate)
        val safePitch = SpeechDelivery.pitch(pitch)
        configureAhead(chunkCount, currentIndex, windowSize, safeRate, safePitch, textAt)
    }

    private fun configureDelivery(rate: Float, pitch: Float): Long = synchronized(deliveryLock) {
        if (rate != lastBufferRate || pitch != lastBufferPitch) {
            lastBufferRate = rate; lastBufferPitch = pitch
            generation.advance { pcmCache.clear() }
            aheadProducer.clear()
        }
        generation.current()
    }

    private fun configureAhead(chunkCount: Int, currentIndex: Int, windowSize: Int, rate: Float, pitch: Float,
        textAt: (Int) -> String): LookAheadProducer.Window {
        val token = configureDelivery(rate, pitch)
        val (start, end) = prebufferWindow(chunkCount, currentIndex, windowSize)
        pcmCache.setWindow(start, end, token, rate, pitch)
        return LookAheadProducer.Window(start, end, token, rate, pitch, clock.nowMs(), textAt).also(aheadProducer::update)
    }

    fun clearLookAhead() {
        aheadProducer.clear()
        pcmCache.clearAhead()
    }

    private suspend fun synthesizeAhead(window: LookAheadProducer.Window, index: Int) {
        if (!aheadProducer.isLatest(window) || !isCurrent(window.generation)) return
        val text = window.textAt(index).trim()
        if (text.isBlank() || cached(index, text, window.rate, window.pitch, window.generation) != null) return
        val pcm = synthesisGate.ahead({ aheadProducer.isLatest(window) && isCurrent(window.generation) &&
            !clock.expired(window.queuedAtMs, resourcePolicy.aheadQueueMs) },
            { resourcePolicy.aheadQueueMs - (clock.nowMs() - window.queuedAtMs) }) {
            synthesizeAndCacheLocked(index, text, window.rate, window.pitch, window.generation, window.queuedAtMs)
        }
        if (pcm != null) Log.d(TAG, "Pre-buffered sentence $index (${pcm.pcm.size} samples, rate=${window.rate}, pitch=${window.pitch})")
    }

    private fun cached(index: Int, text: String, rate: Float, pitch: Float, token: Long): CachedSpeechPcm? =
        pcmCache.find(PcmCacheKey(index, text, rate, pitch, token))

    fun playSentencePcm(
        index: Int,
        text: String,
        rate: Float = 1.0f,
        pitch: Float = 1.0f,
        leadingSilenceMs: Long = 0L,
        onStarted: () -> Unit = {},
        onProgress: (renderedFrames: Long, totalFrames: Int, sampleRate: Int) -> Unit = { _, _, _ -> },
        onAudioLevel: (Float) -> Unit = {},
        lookAhead: LookAhead? = null,
        onComplete: (success: Boolean) -> Unit
    ) {
        (engine as? PlaybackModelPin)?.setPlaybackActive(true)
        val safeRate = SpeechDelivery.rate(rate)
        val safePitch = SpeechDelivery.pitch(pitch)
        val request = PcmCacheKey(index, text.trim(), safeRate, safePitch, configureDelivery(safeRate, safePitch))
        // Reserve synchronously, before an ahead worker or output coroutine can enter synthesis.
        val ticket = synthesisGate.reserveDemand()
        activeDemand.getAndSet(ticket)?.cancel()
        // Promotion frees future capacity; demand must already have priority when it does.
        val current = pcmCache.selectCurrent(request)
        playbackJob?.cancel()
        val requestWindow = lookAhead?.let {
            configureAhead(it.chunkCount, index, it.windowSize, safeRate, safePitch, it.textAt)
        } ?: aheadProducer.current?.copy()?.also(aheadProducer::update)
        // Wake a cancelled blocking writer before the replacement waits for ownership.
        audioOutput.interruptWriter(outputOwner)
        val job = scope.launch(outputDispatcher) {
            playbackMutex.withLock {
            isPlaying = true
            val token = request.generation
            val audio = try {
                val ready = cached(index, request.text, safeRate, safePitch, token) ?: synthesisGate.demand(ticket) {
                    val result = if (request.text.isBlank()) null else synthesizeAndCacheLocked(index, request.text, safeRate, safePitch, token)
                    if (result == null) aheadProducer.clearIf(requestWindow)
                    result
                }
                ready
            } finally { ticket.cancel() }

            if (audio == null) {
                Log.w(TAG, "No audio generated for sentence $index")
                withContext(Dispatchers.Main) { if (isCurrent(token)) onComplete(false) }
                return@withLock
            }

            currentCoroutineContext().ensureActive()
            if (!isCurrent(token)) return@withLock
            if (leadingSilenceMs > 0L) delay(leadingSilenceMs.coerceAtMost(5_000L))
            if (!isCurrent(token)) return@withLock
            val played = audioOutput.play(outputOwner, audio.pcm, audio.sampleRate,
                { isPlaying && isCurrent(token) }, onStarted, onProgress, onAudioLevel)
            if (played) pcmCache.releaseCurrent(current)

            withContext(Dispatchers.Main) {
                if (isCurrent(token)) { onAudioLevel(0f); onComplete(played) }
            }
            }
        }
        playbackJob = job
        job.invokeOnCompletion {
            ticket.cancel() // Covers cancellation before the coroutine body starts, too.
            activeDemand.compareAndSet(ticket, null)
            pcmCache.releaseCurrent(current)
        }
    }

    /** Caller owns synthesisGate; capture the request generation before waiting, never after. */
    private suspend fun synthesizeAndCacheLocked(index: Int, text: String, rate: Float, pitch: Float, token: Long,
        aheadQueuedAtMs: Long? = null): CachedSpeechPcm? {
        if (!isCurrent(token)) return null
        val key = PcmCacheKey(index, text, rate, pitch, token)
        if (aheadQueuedAtMs != null && clock.expired(aheadQueuedAtMs, resourcePolicy.aheadQueueMs) && !pcmCache.isCurrent(key)) return null
        cached(index, text, rate, pitch, token)?.let { return it }
        currentCoroutineContext().ensureActive()
        if (!beginSynthesis()) return null
        val pcm = try {
            engine.synthesize(text, rate, pitch)?.takeIf { it.isNotEmpty() }?.let { samples ->
                // JNI metadata belongs to the same guarded engine lifetime as generation.
                val sampleRate = engine.sampleRate
                require(sampleRate > 0) { "Voice returned an invalid sample rate" }
                CachedSpeechPcm(PcmCacheKey(index, text, rate, pitch, token), samples, sampleRate)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Synthesis failed for sentence $index", error)
            null
        } finally {
            endSynthesis()
        }
        currentCoroutineContext().ensureActive()
        var retained = false
        return if (pcm != null && generation.publishIfCurrent(token) {
            if (outputOwner.active && (aheadQueuedAtMs == null ||
                !clock.expired(aheadQueuedAtMs, resourcePolicy.aheadQueueMs) || pcmCache.isCurrent(key))) retained = pcmCache.publish(pcm)
        } && retained) pcm else null
    }

    // engine.synthesize() is a blocking call into sherpa-onnx, and coroutine cancellation
    // cannot interrupt native code. Freeing the engine while a generate() is still reading
    // it is a use-after-free that lands as SIGSEGV inside OfflineTts_generateImpl, on the
    // Dispatchers.Default thread, below the level the app's crash reporter can observe.
    // So shutdown only marks intent; whichever side finishes last performs the free —
    // the same lifetime rule the separate output controller applies to track writers.
    private val engineLock = Any()
    private var synthesisInFlight = 0
    private var engineShutdownRequested = false
    private val engineDisposed = kotlinx.coroutines.CompletableDeferred<Unit>()

    suspend fun awaitShutdown() { engineDisposed.await(); engine.awaitShutdown() }

    private fun beginSynthesis(): Boolean = synchronized(engineLock) {
        if (engineShutdownRequested) return@synchronized false
        synthesisInFlight++
        true
    }

    private fun endSynthesis() = synchronized(engineLock) {
        synthesisInFlight--
        if (engineShutdownRequested && synthesisInFlight == 0) {
            runCatching { engine.shutdown() }
            engineDisposed.complete(Unit)
        }
    }

    private fun requestEngineShutdown() = synchronized(engineLock) {
        if (engineShutdownRequested) return@synchronized
        engineShutdownRequested = true
        if (synthesisInFlight == 0) {
            runCatching { engine.shutdown() }
            engineDisposed.complete(Unit)
        }
    }

    /**
     * Stops the current sentence and drops the look-ahead window, keeping the track alive
     * for the next one. Safe to call while a sentence is mid-write.
     */
    fun flush() {
        stopPlayback(release = false)
    }

    /**
     * Permanent model-buffer teardown. A supplied service output survives this buffer;
     * a standalone buffer closes its own output after its writer finishes.
     */
    fun shutdown() {
        stopPlayback(release = true)
        if (ownsOutput) audioOutput.close()
        aheadProducer.close()
        scope.cancel()
        requestEngineShutdown()
    }

    private fun stopPlayback(release: Boolean) {
        (engine as? PlaybackModelPin)?.setPlaybackActive(false)
        generation.advance { pcmCache.clear() }
        isPlaying = false
        aheadProducer.clear()
        activeDemand.getAndSet(null)?.cancel()
        playbackJob?.cancel()
        if (release) audioOutput.retire(outputOwner) else audioOutput.interrupt(outputOwner)
    }

    companion object {
        private const val TAG = "VeritasAudioBuffer"

        /**
         * Half-open range of sentence indices the look-ahead should hold. The current
         * sentence is excluded: it may be resumed from a character offset, so only
         * playSentencePcm knows the exact text to synthesize for it.
         */
        fun prebufferWindow(chunkCount: Int, currentIndex: Int, windowSize: Int): Pair<Int, Int> {
            val safeCount = chunkCount.coerceAtLeast(0)
            val start = (currentIndex + 1).coerceIn(0, safeCount)
            val end = (start + windowSize.coerceAtLeast(0)).coerceAtMost(safeCount)
            return start to end
        }
    }
}
