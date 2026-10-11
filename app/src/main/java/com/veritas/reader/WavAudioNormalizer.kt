package com.veritas.reader

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.roundToInt

/** Bounded PCM/float WAV decoding, channel downmix and linear sample-rate conversion. */
internal object WavAudioNormalizer {
    fun append(file: File, writer: PcmWavWriter, outputRate: Int, checkActive: () -> Unit = {}) {
        RandomAccessFile(file, "r").use { input ->
            fun tag() = ByteArray(4).also(input::readFully).toString(Charsets.US_ASCII)
            fun uint() = Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
            require(input.length() >= 44 && tag() == "RIFF") { "Invalid voice WAV." }
            val end = uint() + 8
            require(end <= input.length() && tag() == "WAVE") { "Truncated voice WAV." }
            var fmt: ByteArray? = null; var data = -1L; var length = 0L
            while (input.filePointer + 8 <= end) {
                val id = tag(); val size = uint(); val start = input.filePointer
                require(size <= end - start) { "Truncated audio chunk." }
                if (id == "fmt ") { require(size in 16..4096); fmt = ByteArray(size.toInt()).also(input::readFully) }
                if (id == "data") { require(data == -1L); data = start; length = size }
                input.seek(start + size + size % 2)
            }
            val format = requireNotNull(fmt) { "Missing WAV format." }
            fun u16(at: Int) = (format[at].toInt() and 255) or ((format[at + 1].toInt() and 255) shl 8)
            val encoding = u16(0); val channels = u16(2); val block = u16(12); val bits = u16(14)
            val rate = (4..7).fold(0) { value, at -> value or ((format[at].toInt() and 255) shl ((at - 4) * 8)) }
            require(channels in 1..8 && rate in 8000..192000 && data >= 0 && length > 0 && length % block.coerceAtLeast(1) == 0L)
            require((encoding == 1 && bits in listOf(8, 16, 24, 32)) || (encoding == 3 && bits == 32)) { "Unsupported voice WAV encoding." }
            require(block == channels * bits / 8) { "Invalid WAV frame size." }
            val frames = length / block
            val cache = ByteArray(64 * 1024 / block * block)
            var cacheStart = -1L; var cacheSize = 0
            fun sample(frame: Long): Double {
                val safe = frame.coerceIn(0, frames - 1); val offset = safe * block
                if (offset < cacheStart || offset + block > cacheStart + cacheSize) {
                    checkActive(); cacheStart = offset; cacheSize = minOf(cache.size.toLong(), length - offset).toInt()
                    input.seek(data + offset); input.readFully(cache, 0, cacheSize)
                }
                val start = (offset - cacheStart).toInt()
                var sum = 0.0
                for (channel in 0 until channels) {
                    val at = start + channel * bits / 8
                    var value = 0
                    for (byte in 0 until bits / 8) value = value or ((cache[at + byte].toInt() and 255) shl (byte * 8))
                    sum += when {
                        encoding == 3 -> Float.fromBits(value).let { require(it.isFinite()) { "Invalid audio sample." }; it.coerceIn(-1f, 1f).toDouble() * 32767 }
                        bits == 8 -> (value - 128) * 256.0
                        bits == 16 -> value.toShort().toDouble()
                        bits == 24 -> (value shl 8 shr 8) / 256.0
                        else -> value / 65536.0
                    }
                }
                return sum / channels
            }
            val count = (frames.toDouble() * outputRate / rate).toLong().coerceAtLeast(1)
            val buffer = ShortArray(8192); var written = 0L
            while (written < count) {
                checkActive(); val n = minOf(buffer.size.toLong(), count - written).toInt()
                for (i in 0 until n) {
                    val at = (written + i).toDouble() * rate / outputRate
                    val frame = at.toLong(); val fraction = at - frame
                    val a = sample(frame); val b = sample(frame + 1)
                    buffer[i] = (a + (b - a) * fraction).roundToInt().coerceIn(-32768, 32767).toShort()
                }
                writer.append(if (n == buffer.size) buffer else buffer.copyOf(n), checkActive); written += n
            }
        }
    }
    fun resample(samples: ShortArray, from: Int, to: Int): ShortArray {
        require(from in 8000..192000 && to in 8000..192000 && samples.isNotEmpty())
        if (from == to) return samples
        return ShortArray((samples.size.toDouble() * to / from).roundToInt().coerceAtLeast(1)) { i ->
            val at = i.toDouble() * from / to; val index = at.toInt().coerceAtMost(samples.lastIndex)
            (samples[index] + (samples[minOf(index + 1, samples.lastIndex)] - samples[index]) * (at - index))
                .roundToInt().coerceIn(-32768, 32767).toShort()
        }
    }
}
