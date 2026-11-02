package com.veritas.reader.tts

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.veritas.reader.SpeechAmplitude
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Service-scoped local PCM output. Same-rate model switches reuse a track; rate changes rebuild it. */
class PcmAudioOutput {
    @Volatile internal var lastUnderruns: Int = 0; private set
    private val writers = Mutex()
    private val ownership = PcmOutputOwnership<AudioTrack>(::createTrack, { track ->
        runCatching { track.pause() }
        runCatching { track.flush() }
    }, { track ->
        runCatching { track.stop() }
        runCatching { track.release() }
    })

    internal fun newOwner() = ownership.newOwner()
    internal fun interrupt(owner: PcmOutputOwnership.Owner) = ownership.interrupt(owner)
    internal fun interruptWriter(owner: PcmOutputOwnership.Owner) = ownership.interrupt(owner, writingOnly = true)
    internal fun retire(owner: PcmOutputOwnership.Owner) = ownership.retire(owner)
    internal fun snapshot() = ownership.snapshot()
    fun close() = ownership.close()

    internal suspend fun play(
        owner: PcmOutputOwnership.Owner,
        pcm: ShortArray,
        sampleRate: Int,
        isCurrent: () -> Boolean,
        onStarted: () -> Unit,
        onProgress: (Long, Int, Int) -> Unit,
        onAudioLevel: (Float) -> Unit
    ): Boolean = writers.withLock {
        currentCoroutineContext().ensureActive()
        if (!owner.active || !isCurrent()) return@withLock false
        val writer = try { ownership.acquire(owner, sampleRate) } catch (error: Exception) {
            Log.w(TAG, "Could not initialize audio output", error)
            null
        } ?: return@withLock false
        val track = writer.track
        val initialUnderruns = runCatching { track.underrunCount }.getOrDefault(0)
        try {
            coroutineScope {
                var monitor: Job? = null
                try {
                    runCatching {
                        track.playbackParams = PlaybackParams().apply { allowDefaults(); setSpeed(1f); setPitch(1f) }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        track.setStartThresholdInFrames(minOf(pcm.size, track.bufferCapacityInFrames))
                    } else {
                        track.setBufferSizeInFrames(minOf(pcm.size, track.bufferCapacityInFrames))
                    }
                    if (track.playState != AudioTrack.PLAYSTATE_PLAYING) track.play()
                    val initialFrame = track.playbackHeadPosition.toLong() and 0xffffffffL
                    val totalFrames = pcm.size
                    monitor = launch {
                        var started = false
                        while (currentCoroutineContext().isActive && owner.active && isCurrent()) {
                            val frame = runCatching { track.playbackHeadPosition.toLong() and 0xffffffffL }.getOrNull() ?: break
                            val rendered = ((frame - initialFrame) and 0xffffffffL).coerceAtMost(totalFrames.toLong())
                            if (rendered > 0L) withContext(Dispatchers.Main) {
                                if (owner.active && isCurrent()) {
                                    if (!started) { started = true; onStarted() }
                                    onProgress(rendered, totalFrames, sampleRate)
                                    onAudioLevel(SpeechAmplitude.pcmLevel(pcm, rendered.toInt(), sampleRate))
                                }
                            }
                            delay(50)
                        }
                    }
                    val playbackContext = currentCoroutineContext()
                    PcmCompletion.play(pcm, sampleRate, object : PcmOutput {
                        override fun write(samples: ShortArray, offset: Int, count: Int) = track.write(samples, offset, count)
                        override fun renderedFrames() = track.playbackHeadPosition.toLong() and 0xffffffffL
                    }, { playbackContext.isActive && owner.active && isCurrent() }, SystemClock::elapsedRealtime) { delay(15) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "PCM output failed", error)
                    false
                } finally { monitor?.cancel() }
            }
        } finally {
            lastUnderruns = runCatching { (track.underrunCount - initialUnderruns).coerceAtLeast(0) }.getOrDefault(0)
            Log.d(TAG, "PCM frames=${pcm.size}, sampleRate=$sampleRate, underruns=$lastUnderruns")
            ownership.release(writer)
        }
    }

    private fun createTrack(sampleRate: Int): AudioTrack {
        val minimum = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(minimum.coerceAtLeast(sampleRate))
            .setTransferMode(AudioTrack.MODE_STREAM).build()
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            runCatching { track.release() }
            error("AudioTrack is not initialized")
        }
        return track
    }

    private companion object { const val TAG = "PcmAudioOutput" }
}
