package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VeritasSleepTimerTest {
    @Test
    fun expiredStopTimerStaysArmedUntilTheServiceClearsIt() {
        val request = VeritasSleepTimerRequest(60_000L, VeritasSleepTimerAction.STOP)
        try {
            PlaybackStateStore.setSleepTimer(request, nowMillis = 1_000L)
            org.junit.Assert.assertNull(PlaybackStateStore.activeSleepTimerSnapshot(nowMillis = 61_000L))
            val armed = requireNotNull(PlaybackStateStore.armedSleepTimerSnapshot())
            assertEquals(VeritasSleepTimerAction.STOP, armed.action)
            assertEquals(0L, armed.remainingMillis(nowMillis = 61_000L))
        } finally {
            PlaybackStateStore.clearSleepTimer()
        }
        org.junit.Assert.assertNull(PlaybackStateStore.armedSleepTimerSnapshot())
    }

    @Test
    fun requestRejectsDurationsOutsideSupportedRange() {
        assertFails { VeritasSleepTimerRequest(59_999L, VeritasSleepTimerAction.PAUSE) }
        assertFails { VeritasSleepTimerRequest(12L * 60L * 60L * 1000L + 1L, VeritasSleepTimerAction.STOP) }
    }

    @Test
    fun snapshotReportsRemainingTimeAndExpiry() {
        val snapshot = VeritasSleepTimerSnapshot(
            durationMillis = 15L * 60L * 1000L,
            endsAtMillis = 1_000L,
            action = VeritasSleepTimerAction.PAUSE
        )

        assertEquals(500L, snapshot.remainingMillis(nowMillis = 500L))
        assertTrue(snapshot.isActive(nowMillis = 999L))
        assertFalse(snapshot.isActive(nowMillis = 1_000L))
    }

    @Test
    fun playbackStateStoreCreatesAndClearsSnapshot() {
        val request = VeritasSleepTimerRequest(
            durationMillis = 10L * 60L * 1000L,
            action = VeritasSleepTimerAction.STOP
        )

        PlaybackStateStore.setSleepTimer(request, nowMillis = 1_000L)
        val snapshot = PlaybackStateStore.activeSleepTimerSnapshot(nowMillis = 2_000L)

        assertEquals(VeritasSleepTimerAction.STOP, snapshot?.action)
        assertEquals(10L * 60L * 1000L - 1_000L, snapshot?.remainingMillis(nowMillis = 2_000L))

        PlaybackStateStore.clearSleepTimer()

        assertNull(PlaybackStateStore.activeSleepTimerSnapshot(nowMillis = 2_000L))
    }

    @Test
    fun formatterProducesCompactDurations() {
        assertEquals("5 min", VeritasSleepTimerFormatter.formatDuration(5L * 60L * 1000L))
        assertEquals("1h 15m", VeritasSleepTimerFormatter.formatDuration(75L * 60L * 1000L))
        assertEquals("1:05", VeritasSleepTimerFormatter.formatRemaining(65_000L))
    }

    private fun assertFails(block: () -> Unit) {
        var failed = false
        try {
            block()
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }
}
