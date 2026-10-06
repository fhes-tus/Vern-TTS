package com.veritas.reader

import java.io.File
import java.io.RandomAccessFile

/** Streams mono PCM16 with a fixed buffer, finalizing the RIFF header on close. */
internal class PcmWavWriter(file: File, private val sampleRate: Int) : java.io.Closeable {
    private val output = RandomAccessFile(file, "rw")
    private var bytes = 0L
    init {
        require(sampleRate in 8000..192000)
        output.setLength(0)
        output.write(ByteArray(44))
    }
    fun append(samples: ShortArray, checkActive: () -> Unit = {}) {
        require(bytes + samples.size * 2L <= 0xffffffffL - 36) { "The WAV export exceeds the 4 GiB RIFF limit. Export a smaller range." }
        val buffer = ByteArray(64 * 1024)
        var offset = 0
        while (offset < samples.size) {
            checkActive()
            val count = minOf(buffer.size / 2, samples.size - offset)
            for (i in 0 until count) {
                val value = samples[offset + i].toInt()
                buffer[2 * i] = value.toByte(); buffer[2 * i + 1] = (value shr 8).toByte()
            }
            output.write(buffer, 0, count * 2)
            bytes += count * 2L
            offset += count
        }
    }
    override fun close() {
        try {
            output.seek(0)
            fun tag(value: String) = output.write(value.toByteArray(Charsets.US_ASCII))
            fun int(value: Long) = output.writeInt(Integer.reverseBytes(value.toInt()))
            fun short(value: Int) = output.writeShort(java.lang.Short.reverseBytes(value.toShort()).toInt())
            tag("RIFF"); int(bytes + 36); tag("WAVE"); tag("fmt "); int(16)
            short(1); short(1); int(sampleRate.toLong()); int(sampleRate * 2L); short(2); short(16)
            tag("data"); int(bytes)
            output.fd.sync()
        } finally { output.close() }
    }
}
