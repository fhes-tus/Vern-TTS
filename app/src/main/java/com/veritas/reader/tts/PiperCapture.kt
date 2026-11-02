package com.veritas.reader.tts

import android.content.Context
import com.veritas.reader.PcmWavWriter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong

/** Explicitly opted-in isolated-package diagnostics; production speech is never recorded. */
internal object PiperCapture {
    private val sequence = AtomicLong()
    fun record(context: Context, voice: String, rate: Float, pitch: Float, raw: FloatArray, processed: FloatArray, sampleRate: Int,
        quality: String = "native") {
        if (!context.packageName.endsWith(".checks")) return
        val directory = File(context.filesDir, "piper-captures")
        if (!File(directory, "enabled").isFile) return
        val prefix = "${android.os.SystemClock.elapsedRealtime()}-${sequence.incrementAndGet()}-$voice-$quality-r$rate-p$pitch"
        fun floats(name: String, samples: FloatArray) {
            File(directory, "$prefix-$name.f32").outputStream().buffered().use { output ->
                val block = ByteBuffer.allocate(16384).order(ByteOrder.LITTLE_ENDIAN)
                for (sample in samples) {
                    if (block.remaining() < 4) { output.write(block.array(), 0, block.position()); block.clear() }
                    block.putFloat(sample)
                }
                output.write(block.array(), 0, block.position())
            }
            PcmWavWriter(File(directory, "$prefix-$name.wav"), sampleRate).use { it.append(FloatPcmAudio(samples, sampleRate).pcm16()) }
        }
        floats("raw", raw); floats("pitch", processed)
        File(directory, "$prefix-format.txt").writeText("sampleRate=$sampleRate\nframes=${raw.size}\nquality=$quality\n")
    }
}
