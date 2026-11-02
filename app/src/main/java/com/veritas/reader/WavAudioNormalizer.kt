package com.veritas.reader

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.roundToInt
import com.veritas.reader.tts.BandLimitedResampler
import com.veritas.reader.tts.FloatPcmAudio
import com.veritas.reader.tts.PcmPeakLimiter

/** Bounded PCM/float WAV decoding, channel downmix and band-limited sample-rate conversion. */
internal object WavAudioNormalizer {
    fun append(file: File, writer: PcmWavWriter, outputRate: Int, checkActive: () -> Unit = {}) {
        require(outputRate in 8000..192000)
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
                        encoding == 3 -> Float.fromBits(value).let { require(it.isFinite()) { "Invalid audio sample." }; it.toDouble() * 32768 }
                        bits == 8 -> (value - 128) * 256.0
                        bits == 16 -> value.toShort().toDouble()
                        bits == 24 -> (value shl 8 shr 8) / 256.0
                        else -> value / 65536.0
                    }
                }
                return sum / channels
            }
            val step = rate.toDouble() / outputRate
            val count = BandLimitedResampler.frames(frames, step)
            val filter = if (rate == outputRate) null else BandLimitedResampler.kernel(step)
            val buffer = FloatArray(8192); var written = 0L
            val limiter = PcmPeakLimiter(outputRate)
            while (written < count) {
                checkActive(); val n = minOf(buffer.size.toLong(), count - written).toInt()
                for (i in 0 until n) {
                    val at = (written + i) * step
                    val value = filter?.at(at, ::sample) ?: sample(written + i)
                    buffer[i] = (value / 32768.0).toFloat()
                }
                writer.append(limiter.process(if (n == buffer.size) buffer else buffer.copyOf(n)), checkActive); written += n
            }
            writer.append(limiter.finish(), checkActive)
        }
    }
    fun resample(samples: ShortArray, from: Int, to: Int): ShortArray {
        require(from in 8000..192000 && to in 8000..192000 && samples.isNotEmpty())
        if (from == to) return samples
        val floating = FloatArray(samples.size) { samples[it] / 32768f }
        return FloatPcmAudio(BandLimitedResampler.convert(floating, from.toDouble() / to), to).pcm16()
    }
}
