package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StreakReminderWorkerTest {
    private val today = LocalDate.parse("2026-11-20")
    private val previousStreak = ReaderTrackerSnapshot(activeDateKeys = setOf("2026-11-18", "2026-11-19"))

    @Test fun appUsageTodayDoesNotPreventReminderForYesterdaysStreak() {
        val snapshot = previousStreak.copy(
            openedDateKeys = setOf(today.toString()),
            weeklyUsageByDay = List(7) { 3_600_000L }
        )
        assertEquals(StreakReminder(2, 20L), streakReminderFor(today, snapshot, 20))
    }

    @Test fun qualifyingTodayOrDisabledGoalSuppressesReminder() {
        val snapshot = previousStreak.copy(
            activeDateKeys = previousStreak.activeDateKeys + today.toString(),
            readingMillisByDate = mapOf(today.toString() to 60_000L),
            listeningMillisByDate = mapOf(today.toString() to 60_000L)
        )
        assertNull(streakReminderFor(today, snapshot, 20))
        assertNull(streakReminderFor(today, previousStreak, 0))
    }

    @Test fun qualifyingReadingHasToBeConsecutiveThroughYesterday() {
        val snapshot = previousStreak.copy(activeDateKeys = setOf("2026-11-15", "2026-11-18", "2026-11-19"))
        assertEquals(StreakReminder(2, 20L), streakReminderFor(today, snapshot, 20))
        assertNull(streakReminderFor(today, previousStreak.copy(activeDateKeys = setOf("2026-11-17", "2026-11-18")), 20))
    }
}
