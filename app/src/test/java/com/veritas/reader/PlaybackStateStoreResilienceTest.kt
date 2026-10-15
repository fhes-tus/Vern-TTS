package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackStateStoreResilienceTest {

    @Before
    fun setUp() {
        PlaybackStateStore.reset()
    }

    @Test
    fun testInitialResetState() {
        assertNull(PlaybackStateStore.activeDocumentId)
        assertEquals("", PlaybackStateStore.documentTitle)
        assertEquals(0, PlaybackStateStore.currentIndex)
        assertFalse(PlaybackStateStore.isPlaying)
        assertEquals(0L, PlaybackStateStore.sleepTimerDurationMillis)
    }

    @Test
    fun preparationIsScopedToTheRequestedBookUntilOutputStarts() {
        PlaybackStateStore.activeDocumentId = "old-book"
        PlaybackStateStore.beginPreparing("new-book")
        assertTrue(PlaybackStateStore.isPreparingFor("new-book"))
        assertFalse(PlaybackStateStore.isPreparingFor("old-book"))
        PlaybackStateStore.isPlaying = true // intent accepted, not audio output
        assertTrue(PlaybackStateStore.isPreparing)
        PlaybackStateStore.finishPreparing()
        assertFalse(PlaybackStateStore.isPreparing)
        assertTrue(PlaybackStateStore.isPlaying)
    }

    @Test
    fun pauseOrFailureClearsPreparationEvenBeforePlayingWasSet() {
        PlaybackStateStore.beginPreparing("book")
        PlaybackStateStore.isPlaying = false
        assertFalse(PlaybackStateStore.isPreparing)
        PlaybackStateStore.beginPreparing("book")
        PlaybackStateStore.isPlaying = true
        PlaybackStateStore.isPlaying = false
        assertFalse(PlaybackStateStore.isPreparing)
    }

    @Test
    fun preparationIsTransientAndDoesNotSurviveReset() {
        PlaybackStateStore.beginPreparing("book")
        PlaybackStateStore.reset()
        assertFalse(PlaybackStateStore.isPreparingFor("book"))
        assertFalse(PlaybackStateStore.isPreparing)
    }

    @Test
    fun testSleepTimerManagement() {
        val now = 1_000_000L
        val request = VeritasSleepTimerRequest(
            durationMillis = 60_000L,
            action = VeritasSleepTimerAction.PAUSE,
            stopAtEndOfSection = false
        )
        PlaybackStateStore.setSleepTimer(request, now)

        assertEquals(60_000L, PlaybackStateStore.sleepTimerDurationMillis)
        assertEquals(now + 60_000L, PlaybackStateStore.sleepTimerEndsAtMillis)
        assertEquals(VeritasSleepTimerAction.PAUSE, PlaybackStateStore.sleepTimerAction)

        val snapshot = PlaybackStateStore.activeSleepTimerSnapshot(now)
        assertTrue(snapshot != null && snapshot.isActive(now))

        PlaybackStateStore.clearSleepTimer()
        assertEquals(0L, PlaybackStateStore.sleepTimerDurationMillis)
        assertNull(PlaybackStateStore.activeSleepTimerSnapshot(now))
    }

    @Test
    fun testStatePreservationDuringPlayback() {
        PlaybackStateStore.activeDocumentId = "doc_123"
        PlaybackStateStore.documentTitle = "Moby Dick"
        PlaybackStateStore.currentIndex = 42
        PlaybackStateStore.isPlaying = true

        assertEquals("doc_123", PlaybackStateStore.activeDocumentId)
        assertEquals("Moby Dick", PlaybackStateStore.documentTitle)
        assertEquals(42, PlaybackStateStore.currentIndex)
        assertTrue(PlaybackStateStore.isPlaying)

        PlaybackStateStore.reset()
        assertNull(PlaybackStateStore.activeDocumentId)
        assertEquals("", PlaybackStateStore.documentTitle)
        assertEquals(0, PlaybackStateStore.currentIndex)
        assertFalse(PlaybackStateStore.isPlaying)
    }
}
