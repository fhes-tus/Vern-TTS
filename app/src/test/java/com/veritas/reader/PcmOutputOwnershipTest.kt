package com.veritas.reader

import com.veritas.reader.tts.PcmOutputOwnership
import org.junit.Assert.*
import org.junit.Test

class PcmOutputOwnershipTest {
    private class Track(val rate: Int) { var interruptions = 0; var released = 0 }
    private fun output() = PcmOutputOwnership({ rate -> Track(rate) }, { it.interruptions++ }, { it.released++ })

    @Test fun constructionAndOwnerChangesDoNotAllocateATrack() {
        val output = output()
        repeat(10) { output.newOwner() }
        assertEquals(0, output.snapshot().tracksCreated)
        output.close()
        assertEquals(0, output.snapshot().releaseAttempts)
    }

    @Test fun sameRateModelChangesReuseTheSamePhysicalTrack() {
        val output = output()
        val first = output.newOwner()
        val original = requireNotNull(output.acquire(first, 24000))
        output.release(original)
        output.retire(first)
        val next = output.newOwner()
        val replacement = requireNotNull(output.acquire(next, 24000))
        assertSame(original.track, replacement.track)
        assertEquals(1, output.snapshot().tracksCreated)
        assertEquals(0, output.snapshot().releaseAttempts)
        output.release(replacement); output.close()
        assertEquals(1, original.track.released)
    }

    @Test fun aRateChangeRebuildsOnlyAfterThePreviousWriterReturns() {
        val output = output()
        val old = output.newOwner()
        val writer = requireNotNull(output.acquire(old, 24000))
        val next = output.newOwner()
        assertFalse(old.active)
        assertNull(output.acquire(next, 22050))
        assertEquals(0, writer.track.released)
        output.release(writer)
        val second = requireNotNull(output.acquire(next, 22050))
        assertNotSame(writer.track, second.track)
        assertEquals(1, writer.track.released)
        assertEquals(22050, second.track.rate)
        assertEquals(2, output.snapshot().tracksCreated)
        output.release(second); output.close()
    }

    @Test fun concurrentBorrowingIsRejectedUntilTheWriterReturns() {
        val output = output()
        val owner = output.newOwner()
        val writer = requireNotNull(output.acquire(owner, 24000))
        assertNull(output.acquire(owner, 24000))
        output.release(writer)
        val next = requireNotNull(output.acquire(owner, 24000))
        assertSame(writer.track, next.track)
        output.release(next); output.close()
    }

    @Test fun closeDefersNativeReleaseUntilTheBorrowedWriterReturns() {
        val output = output()
        val owner = output.newOwner()
        val writer = requireNotNull(output.acquire(owner, 24000))
        output.close(); output.close()
        assertFalse(owner.active)
        assertEquals(0, writer.track.released)
        assertNull(output.acquire(output.newOwner(), 24000))
        output.release(writer); output.release(writer)
        assertEquals(1, writer.track.released)
        assertEquals(1, output.snapshot().releaseAttempts)
        assertFalse(output.snapshot().hasWriter)
    }

    @Test fun staleRetirementAndInterruptCannotStopANewerOwner() {
        val output = output()
        val old = output.newOwner()
        val first = requireNotNull(output.acquire(old, 24000))
        output.release(first)
        val next = output.newOwner()
        val second = requireNotNull(output.acquire(next, 24000))
        val before = second.track.interruptions
        output.retire(old); output.interrupt(old); output.retire(old)
        assertTrue(next.active)
        assertEquals(before, second.track.interruptions)
        assertEquals(0, second.track.released)
        output.release(second); output.close()
    }

    @Test fun doubleReleaseOfAnOldLeaseCannotReleaseANewerWriter() {
        val output = output()
        val owner = output.newOwner()
        val old = requireNotNull(output.acquire(owner, 24000))
        output.release(old)
        val next = requireNotNull(output.acquire(owner, 24000))
        output.release(old)
        assertTrue(output.snapshot().hasWriter)
        output.close()
        assertEquals(0, next.track.released)
        output.release(next)
        assertEquals(1, next.track.released)
    }

    @Test fun closingAnUnusedControllerPreventsLaterAllocation() {
        val output = output()
        val old = output.newOwner()
        output.close()
        val next = output.newOwner()
        assertFalse(old.active); assertFalse(next.active)
        assertNull(output.acquire(next, 24000))
        assertEquals(0, output.snapshot().tracksCreated)
    }

    @Test fun aFailedFactoryDoesNotPublishATrackAndCanBeRetried() {
        var fail = true
        val output = PcmOutputOwnership({ rate: Int -> if (fail) error("fixture"); Track(rate) }, {}, {})
        val owner = output.newOwner()
        assertTrue(runCatching { output.acquire(owner, 24000) }.isFailure)
        assertNull(output.snapshot().sampleRate)
        assertFalse(output.snapshot().hasWriter)
        assertEquals(0, output.snapshot().tracksCreated)
        fail = false
        val ready = requireNotNull(output.acquire(owner, 24000))
        assertEquals(1, output.snapshot().tracksCreated)
        output.release(ready); output.close()
    }

    @Test fun invalidSampleRatesNeverCreateAResource() {
        val output = output()
        val owner = output.newOwner()
        assertTrue(runCatching { output.acquire(owner, 0) }.isFailure)
        assertTrue(runCatching { output.acquire(owner, -1) }.isFailure)
        assertEquals(0, output.snapshot().tracksCreated)
    }

    @Test fun retiringAPendingModelCannotAcquireOrReleaseOutput() {
        val output = output()
        val owner = output.newOwner()
        output.retire(owner)
        assertNull(output.acquire(owner, 24000))
        assertEquals(0, output.snapshot().releaseAttempts)
        output.close()
    }

    @Test fun onlyTheLatestOfRapidModelChangesCanAcquireTheOutput() {
        val output = output()
        val owners = List(100) { output.newOwner() }
        owners.dropLast(1).forEach { assertNull(output.acquire(it, 24000)) }
        val writer = requireNotNull(output.acquire(owners.last(), 24000))
        assertEquals(1, output.snapshot().tracksCreated)
        output.release(writer); output.close()
    }

    @Test fun interruptKeepsTheOwnerUsableAndTheTrackAllocated() {
        val output = output()
        val owner = output.newOwner()
        val writer = requireNotNull(output.acquire(owner, 24000))
        output.interrupt(owner)
        assertTrue(owner.active)
        assertEquals(1, writer.track.interruptions)
        output.release(writer)
        val next = requireNotNull(output.acquire(owner, 24000))
        assertSame(writer.track, next.track)
        output.release(next); output.close()
    }

    @Test fun theNextSentenceDoesNotPauseAnIdleContinuousTrack() {
        val output = output()
        val owner = output.newOwner()
        val writer = requireNotNull(output.acquire(owner, 24000))
        output.release(writer)
        output.interrupt(owner, writingOnly = true)
        assertEquals(0, writer.track.interruptions)
        val next = requireNotNull(output.acquire(owner, 24000))
        output.interrupt(owner, writingOnly = true)
        assertEquals(1, next.track.interruptions)
        output.release(next); output.close()
    }
}
