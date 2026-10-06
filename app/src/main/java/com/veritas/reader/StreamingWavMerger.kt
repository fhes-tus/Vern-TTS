package com.veritas.reader

import java.io.File
import java.io.RandomAccessFile

/** RIFF metadata stays small; PCM is copied with a fixed 64 KiB buffer. */
internal object StreamingWavMerger {
    private data class Part(val file: File, val format: ByteArray, val offset: Long, val length: Long)
    private fun inspect(file: File): Part = RandomAccessFile(file, "r").use { input ->
        fun tag(): String = ByteArray(4).also(input::readFully).toString(Charsets.US_ASCII)
        fun size(): Long = Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
        require(input.length() >= 44 && tag() == "RIFF") { "Invalid WAV output." }
        val riffEnd = size() + 8L
        require(riffEnd <= input.length() && tag() == "WAVE") { "Truncated WAV output." }
        var format: ByteArray? = null
        var offset = -1L
        var length = 0L
        while (input.filePointer + 8 <= riffEnd) {
            val id = tag()
            val bytes = size()
            val start = input.filePointer
            require(bytes <= riffEnd - start) { "Truncated WAV chunk." }
            when (id) {
                "fmt " -> {
                    require(bytes in 16L..4096L) { "Unsupported WAV format." }
                    format = ByteArray(bytes.toInt()).also(input::readFully)
                }
                "data" -> { require(offset == -1L) { "Multiple WAV data chunks are unsupported." }; offset = start; length = bytes }
            }
            input.seek(start + bytes + bytes % 2)
        }
        val fmt = requireNotNull(format) { "Missing WAV format." }
        require(offset >= 0 && length > 0) { "Missing WAV samples." }
        val encoding = (fmt[0].toInt() and 255) or ((fmt[1].toInt() and 255) shl 8)
        require(encoding == 1 || encoding == 3) { "Unsupported WAV encoding." }
        val blockSize = (fmt[12].toInt() and 255) or ((fmt[13].toInt() and 255) shl 8)
        require(blockSize > 0 && length % blockSize == 0L) { "Incomplete WAV frame." }
        Part(file, fmt, offset, length)
    }

    fun merge(parts: List<File>, destination: File, checkActive: () -> Unit = {}) {
        require(parts.isNotEmpty()) { "No audio parts were produced." }
        val metadata = parts.map(::inspect)
        val format = metadata.first().format
        require(metadata.all { it.format.contentEquals(format) }) { "The voice produced incompatible WAV parts." }
        val dataSize = metadata.sumOf { it.length }
        val riffSize = 4L + 8 + format.size + format.size % 2 + 8 + dataSize + dataSize % 2
        require(riffSize <= 0xffffffffL) { "The WAV export exceeds the 4 GiB RIFF limit. Export a smaller range." }
        try {
            RandomAccessFile(destination, "rw").use { output ->
                output.setLength(0)
                fun tag(text: String) = output.write(text.toByteArray(Charsets.US_ASCII))
                fun size(bytes: Long) = output.writeInt(Integer.reverseBytes(bytes.toInt()))
                tag("RIFF"); size(riffSize); tag("WAVE"); tag("fmt "); size(format.size.toLong()); output.write(format)
                if (format.size % 2 == 1) output.write(0)
                tag("data"); size(dataSize)
                val buffer = ByteArray(64 * 1024)
                metadata.forEach { part ->
                    RandomAccessFile(part.file, "r").use { input ->
                        input.seek(part.offset)
                        var remaining = part.length
                        while (remaining > 0) {
                            checkActive()
                            val count = minOf(buffer.size.toLong(), remaining).toInt()
                            input.readFully(buffer, 0, count)
                            output.write(buffer, 0, count)
                            remaining -= count
                        }
                    }
                }
                if (dataSize % 2 == 1L) output.write(0)
                output.fd.sync()
            }
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }
}
