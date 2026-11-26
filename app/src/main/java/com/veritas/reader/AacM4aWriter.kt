package com.veritas.reader

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.IOException
import java.nio.ByteOrder

/** Encodes mono PCM16 directly into an MPEG-4 container with a bounded codec wait. */
internal class AacM4aWriter(file: File, private val sampleRate: Int) : PcmAudioWriter {
    private val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
    private val muxer: MediaMuxer
    private val info = MediaCodec.BufferInfo()
    private var track = -1
    private var inputSamples = 0L
    private var outputSamples = false

    init {
        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, EXPORT_AAC_BIT_RATE)
                setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (error: Throwable) {
            runCatching { codec.release() }
            throw error
        }
    }

    override fun append(samples: ShortArray, checkActive: () -> Unit) {
        var offset = 0
        while (offset < samples.size) {
            val index = inputBuffer(checkActive)
            val input = requireNotNull(codec.getInputBuffer(index))
            input.clear()
            input.order(ByteOrder.nativeOrder())
            val count = minOf(samples.size - offset, input.remaining() / Short.SIZE_BYTES)
            check(count > 0) { "The AAC encoder returned an empty input buffer." }
            input.asShortBuffer().put(samples, offset, count)
            codec.queueInputBuffer(index, 0, count * Short.SIZE_BYTES, timestamp(), 0)
            inputSamples += count
            offset += count
            drain(checkActive, timeoutUs = 0)
        }
    }

    override fun finish(checkActive: () -> Unit) {
        val index = inputBuffer(checkActive)
        codec.queueInputBuffer(index, 0, 0, timestamp(), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        val started = System.nanoTime()
        while (!drain(checkActive, timeoutUs = BUFFER_WAIT_US)) {
            if (System.nanoTime() - started >= MAX_WAIT_NS) throw IOException("The AAC encoder did not finish.")
        }
        check(outputSamples) { "The AAC encoder produced no audio." }
        muxer.stop()
    }

    private fun inputBuffer(checkActive: () -> Unit): Int {
        val started = System.nanoTime()
        while (System.nanoTime() - started < MAX_WAIT_NS) {
            checkActive()
            val index = codec.dequeueInputBuffer(BUFFER_WAIT_US)
            if (index >= 0) return index
            drain(checkActive, timeoutUs = 0)
        }
        throw IOException("The AAC encoder stopped accepting audio.")
    }

    private fun drain(checkActive: () -> Unit, timeoutUs: Long): Boolean {
        var wait = timeoutUs
        while (true) {
            checkActive()
            when (val index = codec.dequeueOutputBuffer(info, wait)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> return false
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(track < 0) { "The AAC format changed after writing began." }
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                }
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                else -> {
                    check(index >= 0) { "Unexpected AAC encoder response." }
                    val end = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    try {
                        if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            check(track >= 0) { "The AAC encoder returned data without a track format." }
                            val samples = requireNotNull(codec.getOutputBuffer(index))
                            muxer.writeSampleData(track, samples, info)
                            outputSamples = true
                        }
                    } finally {
                        codec.releaseOutputBuffer(index, false)
                    }
                    if (end) return true
                }
            }
            wait = 0
        }
    }

    private fun timestamp(): Long = inputSamples * 1_000_000L / sampleRate

    override fun close() {
        try {
            muxer.release()
        } finally {
            codec.release()
        }
    }

    private companion object {
        const val BUFFER_WAIT_US = 10_000L
        const val MAX_WAIT_NS = 10_000_000_000L
    }
}
