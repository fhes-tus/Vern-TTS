package com.veritas.reader

import com.veritas.reader.tts.*
import org.junit.Assert.*
import org.junit.Test

class PcmLookAheadCacheTest {
    private fun key(index: Int, text: String = "$index", generation: Long = 0L, rate: Float = 1f, pitch: Float = 1f) =
        PcmCacheKey(index, text, rate, pitch, generation)
    private fun clip(index: Int, frames: Int = 100, sampleRate: Int = 1000, text: String = "$index") =
        CachedSpeechPcm(key(index, text), ShortArray(frames) { 100 }, sampleRate)
    private fun cache(bytes: Long = 1000L, duration: Long = 1000L) = PcmLookAheadCache(PcmCacheBudget(bytes, duration)).apply {
        setWindow(1, 10, 0L, 1f, 1f)
    }

    @Test fun byteLimitIsInclusiveAndIndependentOfDuration() {
        val cache = cache(bytes = 400L, duration = 10000L)
        assertTrue(cache.publish(clip(1)))
        assertTrue(cache.publish(clip(2)))
        assertEquals(400L, cache.snapshot().aheadBytes)
        assertFalse(cache.mayVisit(3))
        assertFalse(cache.publish(clip(3)))
        assertNull(cache.find(key(3)))
        assertEquals(2, cache.snapshot().aheadCount)
    }

    @Test fun durationLimitIsInclusiveAndIndependentOfBytes() {
        val cache = cache(bytes = 10000L, duration = 200L)
        assertTrue(cache.publish(clip(1)))
        assertTrue(cache.publish(clip(2)))
        assertEquals(200L, cache.snapshot().aheadDurationMs)
        assertFalse(cache.mayVisit(3))
        assertFalse(cache.publish(clip(3)))
    }

    @Test fun durationRoundsUpAndUsesEachClipsActualSampleRate() {
        val cache = cache(duration = 2L)
        assertTrue(cache.publish(clip(1, 1, 24000)))
        assertTrue(cache.publish(clip(2, 1, 22050)))
        assertEquals(2L, cache.snapshot().aheadDurationMs)
        assertFalse(cache.publish(clip(3, 1, 48000)))
    }

    @Test fun oversizedCurrentAudioIsProtectedEvenWithZeroFutureBudget() {
        val cache = cache(bytes = 0L, duration = 0L)
        val current = cache.selectCurrent(key(0))
        val audio = clip(0, 2000)
        assertTrue(cache.publish(audio))
        assertSame(audio, cache.find(key(0)))
        assertEquals(4000L, cache.snapshot().currentBytes)
        assertEquals(0L, cache.snapshot().aheadBytes)
        assertFalse(cache.mayVisit(1))
        assertFalse(cache.publish(clip(1)))
        cache.releaseCurrent(current)
        assertEquals(0L, cache.snapshot().currentBytes)
    }

    @Test fun promotionReusesTheSameArrayAndFreesFutureBudget() {
        val cache = cache(bytes = 200L)
        val audio = clip(1)
        assertTrue(cache.publish(audio))
        assertFalse(cache.mayVisit(2))
        cache.selectCurrent(key(1))
        assertSame(audio.pcm, cache.find(key(1))?.pcm)
        assertEquals(1000, cache.find(key(1))?.sampleRate)
        assertEquals(0L, cache.snapshot().aheadBytes)
        assertTrue(cache.mayVisit(2))
        assertTrue(cache.publish(clip(2)))
    }

    @Test fun oldCompletionCannotReleaseAnIdenticalReplacementRequest() {
        val cache = cache()
        val old = cache.selectCurrent(key(0))
        cache.publish(clip(0))
        val replacement = cache.selectCurrent(key(0))
        cache.releaseCurrent(old)
        assertNotNull(cache.find(key(0)))
        cache.releaseCurrent(replacement)
        assertNull(cache.find(key(0)))
    }

    @Test fun lateNearerAudioEvictsTheFarthestClipWhileProtectingCurrent() {
        val cache = cache(bytes = 400L)
        cache.selectCurrent(key(0))
        val current = clip(0, 900)
        cache.publish(current)
        cache.publish(clip(2)); cache.publish(clip(3))
        assertTrue(cache.publish(clip(1)))
        assertNotNull(cache.find(key(1)))
        assertNotNull(cache.find(key(2)))
        assertNull(cache.find(key(3)))
        assertSame(current, cache.find(key(0)))
        assertEquals(400L, cache.snapshot().aheadBytes)
    }

    @Test fun anOversizedFutureClipIsNotTruncatedOrRetainedAndStopsFartherWork() {
        val cache = cache(duration = 150L)
        assertFalse(cache.publish(clip(1, 151)))
        assertNull(cache.find(key(1)))
        assertFalse(cache.mayVisit(2))
        assertEquals(0L, cache.snapshot().aheadBytes)
    }

    @Test fun rejectingALaterClipKeepsTheNearestUsefulAudio() {
        val cache = cache(duration = 150L)
        val near = clip(1)
        cache.publish(near)
        assertFalse(cache.publish(clip(2)))
        assertSame(near, cache.find(key(1)))
        assertFalse(cache.mayVisit(3))
    }

    @Test fun advancingTheWindowCanResumeAfterABudgetStop() {
        val cache = cache(duration = 150L)
        cache.publish(clip(1)); cache.publish(clip(2))
        assertFalse(cache.mayVisit(3))
        cache.selectCurrent(key(1))
        cache.setWindow(2, 5, 0L, 1f, 1f)
        assertTrue(cache.mayVisit(2))
        assertTrue(cache.publish(clip(2)))
        assertNotNull(cache.find(key(1)))
    }

    @Test fun windowReplacementPrunesObsoleteAudioAndRejectsLateOldResults() {
        val cache = cache()
        cache.publish(clip(1)); cache.publish(clip(2)); cache.publish(clip(3))
        cache.setWindow(3, 5, 0L, 1f, 1f)
        assertNull(cache.find(key(1)))
        assertNotNull(cache.find(key(3)))
        assertFalse(cache.publish(clip(2)))
        cache.setWindow(3, 5, 1L, 1.2f, .9f)
        assertEquals(0L, cache.snapshot().aheadBytes)
        assertFalse(cache.publish(clip(3)))
    }

    @Test fun textDeliveryAndGenerationMustMatchForReuse() {
        val cache = cache()
        cache.publish(clip(1))
        assertNull(cache.find(key(1, "changed")))
        assertNull(cache.find(key(1, rate = 1.2f)))
        assertNull(cache.find(key(1, pitch = .8f)))
        assertNull(cache.find(key(1, generation = 1L)))
        cache.selectCurrent(key(1, "changed"))
        assertFalse(cache.publish(clip(1))) // Old ahead window still includes this index.
        assertNull(cache.find(key(1, "changed")))
    }

    @Test fun clearAheadPreservesCurrentButClearInvalidatesEverything() {
        val cache = cache()
        val owner = cache.selectCurrent(key(0))
        cache.publish(clip(0)); cache.publish(clip(1))
        cache.clearAhead()
        assertNotNull(cache.find(key(0)))
        assertEquals(0L, cache.snapshot().aheadBytes)
        assertFalse(cache.publish(clip(2)))
        cache.clear()
        assertNull(cache.find(key(0)))
        cache.selectCurrent(key(0))
        cache.publish(clip(0))
        cache.releaseCurrent(owner)
        assertNotNull(cache.find(key(0)))
    }

    @Test fun replacingAnEntryDoesNotAccumulateItsOldCost() {
        val cache = cache(bytes = 400L)
        repeat(20) { assertTrue(cache.publish(clip(1, 100 + it))) }
        assertEquals(238L, cache.snapshot().aheadBytes)
        assertEquals(119L, cache.snapshot().aheadDurationMs)
        assertEquals(1, cache.snapshot().aheadCount)
    }

    @Test fun wideBudgetArithmeticAndInvalidInputsAreHandled() {
        val cache = cache(bytes = Long.MAX_VALUE, duration = Long.MAX_VALUE)
        assertTrue(cache.publish(clip(1)))
        assertTrue(cache.publish(clip(2)))
        assertTrue(runCatching { PcmCacheBudget(-1L, 100L) }.isFailure)
        assertTrue(runCatching { PcmCacheBudget(100L, -1L) }.isFailure)
        assertTrue(runCatching { clip(1, sampleRate = 0) }.isFailure)
        assertTrue(runCatching { clip(1, frames = 0) }.isFailure)
    }
}
