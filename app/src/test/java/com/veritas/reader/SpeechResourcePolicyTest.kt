package com.veritas.reader

import com.veritas.reader.tts.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechResourcePolicyTest {
    @Test fun modelFileDeletionRetiresIdleGraphsBeforeRemovingTheirFiles() = runTest {
        val resources = NativeModelResources(scope = backgroundScope)
        var freed = 0
        val model = NativeModel(resources, SpeechModelRole.PREVIEW, { Any() }, { freed++ })
        model.use { }
        var removed = false
        assertTrue(resources.mutateModelFiles {
            assertEquals(1, freed)
            assertEquals(0, resources.admission.snapshot().resident)
            removed = true
            true
        })
        assertTrue(removed)
    }

    @Test fun modelFileDeletionCannotRemoveAPinnedPlaybackModel() = runTest {
        val resources = NativeModelResources(scope = backgroundScope)
        val model = NativeModel(resources, SpeechModelRole.LIVE, { Any() }, {})
        model.use { }
        model.setPlaybackActive(true)
        var removed = false
        assertFalse(resources.mutateModelFiles { removed = true; true })
        assertFalse(removed)
        model.setPlaybackActive(false)
        assertTrue(resources.mutateModelFiles { true })
    }
    private class Clock(var time: Long = 0) : SpeechClock { override fun nowMs() = time }
    @Test fun futurePcmExpiresAtBoundaryButPromotedCurrentDoesNot() {
        val clock = Clock()
        val cache = PcmLookAheadCache(PcmCacheBudget(maxAgeMs = 60), clock)
        fun key(i: Int) = PcmCacheKey(i, "$i", 1f, 1f, 0)
        fun clip(i: Int) = CachedSpeechPcm(key(i), ShortArray(100), 1000)
        cache.setWindow(1, 4, 0, 1f, 1f)
        val promoted = clip(1)
        cache.publish(promoted); cache.publish(clip(2))
        clock.time = 59
        cache.selectCurrent(key(1))
        clock.time = 60
        assertSame(promoted, cache.find(key(1)))
        assertNull(cache.find(key(2)))
        assertEquals(0, cache.snapshot().aheadCount)
        assertEquals(200L, cache.snapshot().currentBytes)
    }
    @Test fun expiredAheadWaiterReleasesWithoutEnteringNativeWork() = runTest {
        val gate = SynthesisPriorityGate()
        val demand = gate.reserveDemand()
        var called = false
        val ahead = async { gate.ahead({ true }, { 30 }) { called = true } }
        advanceTimeBy(30); runCurrent()
        assertNull(ahead.await()); assertFalse(called)
        demand.cancel()
    }
    @Test fun copyingAWindowDoesNotRefreshItsAge() {
        val window = LookAheadProducer.Window(1, 5, 0, 1f, 1f, 12) { "$it" }
        assertEquals(12L, window.copy().queuedAtMs)
    }
    @Test fun retirementStaysChargedAndCannotEvictPinnedOrBusyModels() {
        val clock = Clock()
        var freed = 0
        val admission = ModelAdmission(SpeechResourcePolicy(maxResidentModels = 1), clock)
        val live = ModelAdmission.Owner(true) { freed++ }
        val preview = ModelAdmission.Owner(false) { freed++ }
        assertTrue(admission.tryAcquire(live))
        admission.pin(live, true); admission.release(live)
        clock.time = 100_000
        admission.trim(true)
        assertFalse(admission.tryAcquire(preview)); assertEquals(0, freed)
        admission.pin(live, false)
        assertFalse(admission.tryAcquire(preview)); assertEquals(1, freed)
        assertEquals(1, admission.snapshot().retiring)
        assertFalse(admission.tryAcquire(preview))
        admission.retired(live)
        assertTrue(admission.tryAcquire(preview))
        admission.close(preview)
        assertEquals(1, freed) // An active native lease cannot be freed.
        admission.release(preview); assertEquals(2, freed)
        admission.close(preview); assertEquals(2, freed)
    }
    @Test fun liveWaitersTakePriorityAndSecondaryMemoryGateIsRespected() {
        val admission = ModelAdmission(SpeechResourcePolicy(), Clock(), mayAddSecond = { false })
        val live = ModelAdmission.Owner(true) {}
        val preview = ModelAdmission.Owner(false) {}
        admission.waiting(live, true)
        assertFalse(admission.tryAcquire(preview))
        assertTrue(admission.tryAcquire(live))
        admission.waiting(live, false)
        assertFalse(admission.tryAcquire(preview))
    }
    @Test fun idleTimeoutStartsAfterPlaybackUnpinsAndOnlyOneWarmGraphSurvives() {
        val clock = Clock()
        var retired = 0
        val admission = ModelAdmission(SpeechResourcePolicy(modelIdleMs = 60), clock)
        val a = ModelAdmission.Owner(true) { retired++ }
        val b = ModelAdmission.Owner(false) { retired++ }
        admission.tryAcquire(a); admission.pin(a, true); admission.release(a)
        clock.time = 100
        admission.pin(a, false)
        admission.trim(false); assertEquals(0, retired)
        clock.time = 159
        admission.trim(false); assertEquals(0, retired)
        clock.time = 160
        admission.trim(false); assertEquals(1, retired)
        admission.retired(a)
        admission.tryAcquire(a); admission.release(a)
        admission.tryAcquire(b); admission.release(b)
        assertEquals(2, retired)
    }
    @Test fun nativeSlotReloadsAfterIdleRetirementAndShutdownFreesExactlyOnce() = runTest {
        val clock = Clock()
        val resources = NativeModelResources(SpeechResourcePolicy(modelIdleMs = 10), clock, scope = backgroundScope)
        var created = 0; var freed = 0
        val model = NativeModel(resources, SpeechModelRole.LIVE, { ++created }, { freed++ })
        assertEquals(1, model.use { it })
        clock.time = 10
        resources.admission.trim(false); runCurrent()
        assertEquals(1, freed)
        assertEquals(2, model.use { it })
        model.close(); model.close(); runCurrent()
        model.awaitClosed()
        assertEquals(2, freed)
        assertEquals(0, resources.admission.snapshot().resident)
    }
    @Test fun canceledAndExpiredAdmissionNeverLeaksALiveReservation() = runTest {
        val clock = SpeechClock { testScheduler.currentTime }
        val resources = NativeModelResources(SpeechResourcePolicy(maxResidentModels = 1, admissionWaitMs = 50), clock, scope = backgroundScope)
        val live = ModelAdmission.Owner(true) {}
        resources.admission.tryAcquire(live)
        val preview = NativeModel(resources, SpeechModelRole.PREVIEW, { 1 }, {})
        val result = async { runCatching { preview.use { it } }.exceptionOrNull() }
        advanceTimeBy(50); runCurrent()
        assertTrue(result.await() is SpeechModelBusyException)
        val waiting = launch { resources.acquire(ModelAdmission.Owner(true) {}) }
        runCurrent(); waiting.cancel(); runCurrent()
        resources.admission.release(live)
        // Closing an owner that never loaded is also immediately awaitable.
        preview.close(); preview.awaitClosed()
    }
}
