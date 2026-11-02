package com.veritas.reader.tts

/** Monotonic elapsed time; injectable so policy tests never need wall-clock sleeps. */
fun interface SpeechClock {
    fun nowMs(): Long
    companion object { val SYSTEM = SpeechClock { System.nanoTime() / 1_000_000L } }
}

data class SpeechResourcePolicy(
    val maxResidentModels: Int = 2,
    val maxIdleModels: Int = 1,
    val modelIdleMs: Long = 60_000L,
    val admissionWaitMs: Long = 30_000L,
    val aheadQueueMs: Long = 30_000L,
    val futurePcmMs: Long = 60_000L,
    val secondModelFreeBytes: Long = 512L * 1024 * 1024
) {
    init {
        require(maxResidentModels > 0 && maxIdleModels in 0..maxResidentModels)
        require(modelIdleMs >= 0 && admissionWaitMs > 0 && aheadQueueMs >= 0 && futurePcmMs >= 0)
        require(secondModelFreeBytes >= 0)
    }
}

internal fun SpeechClock.expired(since: Long, limitMs: Long) = nowMs() - since >= limitMs
