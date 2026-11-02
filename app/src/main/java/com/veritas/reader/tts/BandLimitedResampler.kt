package com.veritas.reader.tts

import kotlin.math.*

/** Offline, delay-compensated windowed-sinc conversion. Absolute positions preserve phase across blocks. */
internal object BandLimitedResampler {
    private const val PHASES = 256
    private const val CACHE_BYTES = 4 * 1024 * 1024
    private val kernels = LinkedHashMap<Double, Kernel>(4, .75f, true)
    private var retainedBytes = 0

    fun frames(input: Long, step: Double): Long {
        require(input > 0 && step.isFinite() && step in (1.0 / 24)..24.0)
        val count = (input / step).roundToLong().coerceAtLeast(1)
        require(count > 0)
        return count
    }

    fun kernel(step: Double): Kernel = synchronized(kernels) {
        require(step.isFinite() && step in (1.0 / 24)..24.0)
        kernels[step] ?: Kernel(step).also { kernel ->
            while (kernels.isNotEmpty() && retainedBytes + kernel.bytes > CACHE_BYTES) {
                val first = kernels.entries.iterator(); retainedBytes -= first.next().value.bytes; first.remove()
            }
            if (kernel.bytes <= CACHE_BYTES) { kernels[step] = kernel; retainedBytes += kernel.bytes }
        }
    }

    class Kernel internal constructor(step: Double) {
        // Beta 8.6, 96 taps at unity; support grows for downsampling to retain rejection.
        private val bandwidth = min(1.0, 1.0 / step)
        private val radius = ceil(48 / bandwidth).toInt()
        private val taps = radius * 2
        private val table = Array(PHASES + 1) { phase ->
            val fraction = phase.toDouble() / PHASES
            val cutoff = .94 * bandwidth
            val weights = DoubleArray(taps) { i ->
                val distance = i - radius + 1 - fraction
                val x = distance / radius
                val window = if (abs(x) >= 1) 0.0 else bessel(8.6 * sqrt(1 - x * x)) / bessel(8.6)
                val angle = PI * cutoff * distance
                cutoff * (if (abs(angle) < 1e-12) 1.0 else sin(angle) / angle) * window
            }
            val sum = weights.sum()
            FloatArray(taps) { (weights[it] / sum).toFloat() }
        }
        internal val bytes get() = table.size * taps * 4

        fun at(position: Double, sample: (Long) -> Double): Double {
            val base = floor(position).toLong()
            val phase = ((position - base) * PHASES).coerceIn(0.0, PHASES.toDouble())
            val lo = phase.toInt().coerceAtMost(PHASES - 1)
            val fraction = phase - lo
            val a = table[lo]; val b = table[lo + 1]
            var value = 0.0
            for (i in 0 until taps) value += sample(base + i - radius + 1) * (a[i] + (b[i] - a[i]) * fraction)
            return value
        }
    }

    fun convert(input: FloatArray, step: Double): FloatArray {
        require(step.isFinite() && step in (1.0 / 24)..24.0)
        require(input.all(Float::isFinite)) { "Non-finite audio." }
        if (input.isEmpty() || abs(step - 1) < 1e-12) return input
        val count = frames(input.size.toLong(), step)
        require(count <= Int.MAX_VALUE) { "Audio is too large." }
        val filter = kernel(step)
        return FloatArray(count.toInt()) { i ->
            if (i % 1024 == 0 && Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException()
            filter.at(i * step) { input[it.coerceIn(0, input.lastIndex.toLong()).toInt()].toDouble() }.toFloat()
        }
    }

    private fun bessel(x: Double): Double {
        var sum = 1.0; var term = 1.0
        for (k in 1..40) { term *= x * x / (4.0 * k * k); sum += term; if (term < sum * 1e-16) break }
        return sum
    }
}
