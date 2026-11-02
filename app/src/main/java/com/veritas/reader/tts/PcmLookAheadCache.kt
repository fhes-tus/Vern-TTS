package com.veritas.reader.tts

/** Retained future PCM only. Current output and a native call's temporary allocations are separate. */
data class PcmCacheBudget(val maxBytes: Long = 4L * 1024 * 1024, val maxDurationMs: Long = 30_000L,
    val maxAgeMs: Long = 60_000L) {
    init { require(maxBytes >= 0 && maxDurationMs >= 0 && maxAgeMs >= 0) }
}

internal data class PcmCacheKey(val index: Int, val text: String, val rate: Float, val pitch: Float, val generation: Long)

/** PCM16 mono; keep format metadata with the array through cache promotion and output. */
internal data class CachedSpeechPcm(val key: PcmCacheKey, val pcm: ShortArray, val sampleRate: Int) {
    init { require(pcm.isNotEmpty() && sampleRate > 0) }
    val bytes: Long get() = pcm.size.toLong() * 2L
    // Round up per clip so retained duration is never understated.
    val durationMs: Long get() = (pcm.size.toLong() * 1000L + sampleRate - 1L) / sampleRate
}

internal class PcmLookAheadCache(private val budget: PcmCacheBudget,
    private val clock: SpeechClock = SpeechClock.SYSTEM) {
    class Current internal constructor(val key: PcmCacheKey)
    data class Snapshot(val aheadCount: Int, val aheadBytes: Long, val aheadDurationMs: Long,
        val currentBytes: Long, val currentDurationMs: Long)

    private data class Window(val start: Int, val end: Int, val generation: Long, val rate: Float, val pitch: Float) {
        fun accepts(key: PcmCacheKey) = key.index in start until end && key.generation == generation &&
            key.rate == rate && key.pitch == pitch
    }

    private var window: Window? = null
    private var current: Current? = null
    private var currentAudio: CachedSpeechPcm? = null
    private val ahead = java.util.TreeMap<Int, CachedSpeechPcm>()
    private val publishedAt = HashMap<Int, Long>()
    private var blockedAt: Int? = null

    private fun pruneExpired() {
        val expired = ahead.keys.filter { clock.expired(publishedAt.getValue(it), budget.maxAgeMs) }
        expired.forEach { ahead.remove(it); publishedAt.remove(it) }
        if (expired.isNotEmpty()) blockedAt = null
    }
    @Synchronized fun expireAhead() { pruneExpired() }

    @Synchronized fun selectCurrent(key: PcmCacheKey): Current {
        val ready = find(key)
        ahead.remove(key.index)
        publishedAt.remove(key.index)
        currentAudio = ready
        blockedAt = null
        return Current(key).also { current = it }
    }

    @Synchronized fun releaseCurrent(owner: Current) {
        if (current === owner) { current = null; currentAudio = null }
    }

    @Synchronized fun setWindow(start: Int, end: Int, generation: Long, rate: Float, pitch: Float) {
        val next = Window(start, end, generation, rate, pitch)
        window = next
        ahead.entries.removeIf { !next.accepts(it.value.key) }
        publishedAt.keys.retainAll(ahead.keys)
        pruneExpired()
        blockedAt = null
    }

    @Synchronized fun clearAhead() { window = null; ahead.clear(); publishedAt.clear(); blockedAt = null }
    @Synchronized fun clear() { clearAhead(); current = null; currentAudio = null }

    @Synchronized fun find(key: PcmCacheKey): CachedSpeechPcm? {
        pruneExpired()
        return currentAudio?.takeIf { it.key == key } ?: ahead[key.index]?.takeIf { it.key == key }
    }

    @Synchronized fun isCurrent(key: PcmCacheKey): Boolean = current?.key == key

    /** Checked before visiting the next item; a budget stop does not spin or skip to farther text. */
    @Synchronized fun mayVisit(index: Int): Boolean {
        pruneExpired()
        if (budget.maxAgeMs == 0L) return false
        if (blockedAt?.let { index >= it } == true) return false
        if (ahead.containsKey(index)) return true
        val used = snapshot()
        return used.aheadBytes < budget.maxBytes && used.aheadDurationMs < budget.maxDurationMs
    }

    @Synchronized fun publish(audio: CachedSpeechPcm): Boolean {
        pruneExpired()
        if (current?.key == audio.key) { currentAudio = audio; return true }
        if (budget.maxAgeMs == 0L) return false
        if (current?.key?.index == audio.key.index) return false
        if (window?.accepts(audio.key) != true) return false

        // Preserve nearer sentences. A late nearer result may evict the farthest ones.
        val nearer = ahead.headMap(audio.key.index).values
        val bytesBefore = nearer.sumOf { it.bytes }
        val durationBefore = nearer.sumOf { it.durationMs }
        if (audio.bytes > budget.maxBytes - bytesBefore || audio.durationMs > budget.maxDurationMs - durationBefore) {
            blockedAt = minOf(blockedAt ?: audio.key.index, audio.key.index)
            return false
        }
        ahead[audio.key.index] = audio
        publishedAt[audio.key.index] = clock.nowMs()
        while (true) {
            val used = snapshot()
            if (used.aheadBytes <= budget.maxBytes && used.aheadDurationMs <= budget.maxDurationMs) break
            val removed = ahead.pollLastEntry() ?: break
            publishedAt.remove(removed.key)
        }
        return true
    }

    @Synchronized fun snapshot(): Snapshot {
        pruneExpired()
        return Snapshot(ahead.size, ahead.values.sumOf { it.bytes },
            ahead.values.sumOf { it.durationMs }, currentAudio?.bytes ?: 0L, currentAudio?.durationMs ?: 0L)
    }
}
