package com.veritas.reader.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Continuous 4-Sentence Pre-Buffering Pipeline (Producer-Consumer Sliding Window).
 * Pre-synthesizes up to 4 sentences ahead in background RAM for 0ms gapless narration.
 */
class VeritasAudioBuffer(
    private val context: Context,
    private val engine: TtsEngine
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val pcmCache = ConcurrentHashMap<Int, ShortArray>()
    // Sherpa's native TTS object is not safe to generate audio from multiple coroutines.
    private val synthesisMutex = Mutex()

    private var audioTrack: AudioTrack? = null

    // Read by the playback wait loop on Dispatchers.IO and written by flush() on the
    // service's main thread. Without @Volatile that read can be hoisted out of the loop
    // and the stop never observed, leaving a cancelled sentence spinning to its timeout.
    @Volatile
    private var isPlaying = false

    // AudioTrack teardown races the writer. flush() and shutdown() arrive on the main
    // thread (pause, stop, seek, voice change, onDestroy) while playSentencePcm may be
    // parked inside a blocking write(). Releasing the track out from under that write is
    // an IllegalStateException at best and a native abort at worst, and cancelling the
    // coroutine cannot help: write() is a blocking call, not a suspension point.
    //
    // So teardown never releases a track a writer still holds. pause() + flush() are safe
    // to call concurrently with write() and make it return promptly; the writer then does
    // the release itself on its way out. trackLock is only held for this bookkeeping,
    // never across a write, so the main thread cannot stall behind playback.
    private val trackLock = Any()
    private var writersInFlight = 0
    private var releaseRequested = false

    // Only one look-ahead pass may be in flight. speakChunk calls prebufferAhead for
    // every sentence, which used to stack a fresh coroutine each time: four jobs were
    // observed walking the same window, all queued on synthesisMutex ahead of the
    // sentence the user was actually waiting to hear.
    private var prebufferJob: Job? = null
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
        if (rate != lastBufferRate || pitch != lastBufferPitch) {
            lastBufferRate = rate
            lastBufferPitch = pitch
            pcmCache.clear()
        }
        prebufferJob?.cancel()
        prebufferJob = scope.launch {
            val (startIndex, targetEnd) = prebufferWindow(chunkCount, currentIndex, windowSize)
            // This is a sliding window. Seeking could otherwise leave PCM from every
            // abandoned position retained in memory for the rest of the service lifetime.
            pcmCache.keys.removeIf { it !in startIndex until targetEnd }
            for (i in startIndex until targetEnd) {
                if (!pcmCache.containsKey(i)) {
                    val rawText = textAt(i).trim()
                    if (rawText.isNotBlank()) {
                        val pcm = synthesizeAndCache(i, rawText, rate, pitch)
                        if (pcm != null && pcm.isNotEmpty()) {
                            Log.d(TAG, "Pre-buffered sentence $i (${pcm.size} samples, rate=$rate, pitch=$pitch)")
                        }
                    }
                }
            }
        }
    }

    fun playSentencePcm(
        index: Int,
        text: String,
        rate: Float = 1.0f,
        pitch: Float = 1.0f,
        onComplete: (success: Boolean) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            isPlaying = true
            if (rate != lastBufferRate || pitch != lastBufferPitch) {
                lastBufferRate = rate
                lastBufferPitch = pitch
                pcmCache.clear()
            }
            var pcm = pcmCache[index]
            if (pcm == null) {
                // kotlinx Mutex is FIFO-fair, so the sentence being waited on would
                // otherwise sit behind every look-ahead item already queued — measured
                // at 14s on a sentence whose own synthesis took 11s. Stand the queue
                // down first; the window is rebuilt as soon as this one is playing.
                prebufferJob?.cancelAndJoin()
                val rawText = text.trim()
                if (rawText.isNotBlank()) {
                    pcm = synthesizeAndCache(index, rawText, rate, pitch)
                }
            }

            if (pcm == null || pcm.isEmpty()) {
                Log.w(TAG, "No audio generated for sentence $index")
                withContext(Dispatchers.Main) { onComplete(false) }
                return@launch
            }

            val sampleRate = engine.sampleRate
            val track = acquireTrack(sampleRate)
            if (track == null) {
                // shutdown() ran while this sentence was still being synthesized.
                withContext(Dispatchers.Main) { onComplete(false) }
                return@launch
            }

            val played = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    runCatching {
                        val params = android.media.PlaybackParams().apply {
                            setPitch(pitch.coerceIn(0.5f, 2.0f))
                        }
                        track.playbackParams = params
                    }
                }
                if (track.playState != AudioTrack.PLAYSTATE_PLAYING) {
                    track.play()
                }
                // write() is blocking in MODE_STREAM: it returns once the data is
                // queued, not once it has been heard. Sleeping for the clip's full
                // duration on top of that added a gap at every sentence boundary, so
                // wait on the head position actually reaching the end instead.
                val startHead = track.playbackHeadPosition
                track.write(pcm, 0, pcm.size)
                pcmCache.remove(index)

                val target = startHead.toLong() + pcm.size
                val timeoutAt = System.currentTimeMillis() +
                    (pcm.size.toDouble() / sampleRate * 1000).toLong() + 1_000L
                while (isPlaying &&
                    track.playbackHeadPosition.toLong() < target &&
                    System.currentTimeMillis() < timeoutAt
                ) {
                    delay(15)
                }
                true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                // The track can still die between acquire and write on paths this
                // bookkeeping does not cover (engine death, audio focus loss).
                Log.w(TAG, "Playback of sentence $index failed", t)
                false
            } finally {
                releaseWriter()
            }

            withContext(Dispatchers.Main) { onComplete(played) }
        }
    }

    private suspend fun synthesizeAndCache(index: Int, text: String, rate: Float = 1.0f, pitch: Float = 1.0f): ShortArray? = synthesisMutex.withLock {
        pcmCache[index]?.let { return@withLock it }
        if (!beginSynthesis()) return@withLock null
        val pcm = try {
            engine.synthesize(text, rate, pitch)?.takeIf { it.isNotEmpty() }
        } finally {
            endSynthesis()
        }
        if (pcm != null) {
            pcmCache[index] = pcm
        }
        pcm
    }

    // engine.synthesize() is a blocking call into sherpa-onnx, and coroutine cancellation
    // cannot interrupt native code. Freeing the engine while a generate() is still reading
    // it is a use-after-free that lands as SIGSEGV inside OfflineTts_generateImpl, on the
    // Dispatchers.Default thread, below the level the app's crash reporter can observe.
    // So shutdown only marks intent; whichever side finishes last performs the free —
    // the same bargain acquireTrack/releaseWriter strike for the AudioTrack.
    private val engineLock = Any()
    private var synthesisInFlight = 0
    private var engineShutdownRequested = false

    private fun beginSynthesis(): Boolean = synchronized(engineLock) {
        if (engineShutdownRequested) return@synchronized false
        synthesisInFlight++
        true
    }

    private fun endSynthesis() = synchronized(engineLock) {
        synthesisInFlight--
        if (engineShutdownRequested && synthesisInFlight == 0) {
            runCatching { engine.shutdown() }
        }
    }

    private fun requestEngineShutdown() = synchronized(engineLock) {
        engineShutdownRequested = true
        if (synthesisInFlight == 0) {
            runCatching { engine.shutdown() }
        }
    }

    /** Sample rate the live [audioTrack] was built for; -1 when there is no track. */
    private var trackSampleRate: Int = -1

    /**
     * Hands out the shared track and registers the caller as a writer so teardown knows
     * not to release underneath it. Returns null once [shutdown] has run.
     */
    private fun acquireTrack(sampleRate: Int): AudioTrack? = synchronized(trackLock) {
        if (releaseRequested) return@synchronized null

        // The track is long-lived, but its sample rate is fixed at construction.
        // Switching voice (Kokoro 24kHz <-> Piper 22.05kHz) without rebuilding
        // it played every sample at the wrong rate — audible as a gritty,
        // slightly-off-pitch rasp rather than an obvious failure.
        if (audioTrack != null && trackSampleRate != sampleRate && writersInFlight == 0) {
            Log.i(TAG, "Sample rate changed $trackSampleRate -> $sampleRate; rebuilding AudioTrack")
            disposeTrackLocked()
        }

        if (audioTrack == null) {
            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            trackSampleRate = sampleRate
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                // Sized from the sample rate (about half a second) rather than
                // from the first sentence's length, which is arbitrary.
                .setBufferSizeInBytes(minBufferSize.coerceAtLeast(sampleRate))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }

        writersInFlight++
        audioTrack
    }

    private fun releaseWriter() = synchronized(trackLock) {
        writersInFlight--
        if (releaseRequested && writersInFlight == 0) {
            disposeTrackLocked()
        }
    }

    private fun disposeTrackLocked() {
        audioTrack?.let { track ->
            runCatching { track.stop() }
            runCatching { track.release() }
        }
        audioTrack = null
        trackSampleRate = -1
    }

    /**
     * Stops the current sentence and drops the look-ahead window, keeping the track alive
     * for the next one. Safe to call while a sentence is mid-write.
     */
    fun flush() {
        stopPlayback(release = false)
    }

    /**
     * Permanent teardown: releases the track once no writer is using it, then shuts the
     * native engine down. The scope is cancelled here rather than only having its children
     * cancelled — a rebuilt buffer (voice change) or a destroyed service otherwise leaves
     * its SupervisorJob alive for the rest of the process.
     */
    fun shutdown() {
        stopPlayback(release = true)
        scope.cancel()
        requestEngineShutdown()
    }

    private fun stopPlayback(release: Boolean) {
        isPlaying = false
        prebufferJob?.cancel()
        prebufferJob = null
        scope.coroutineContext.cancelChildren()
        pcmCache.clear()
        synchronized(trackLock) {
            // pause() + flush() are the documented way to make a blocked write() return.
            // They are safe on a track another thread is writing to; release() is not.
            audioTrack?.let { track ->
                runCatching { track.pause() }
                runCatching { track.flush() }
            }
            if (release) {
                releaseRequested = true
            }
            if (releaseRequested && writersInFlight == 0) {
                disposeTrackLocked()
            }
        }
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
