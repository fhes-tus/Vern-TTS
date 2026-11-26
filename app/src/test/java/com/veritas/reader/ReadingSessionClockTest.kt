package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ReadingSessionClockTest {
    private val target = ReadingSessionTarget("book", "A book", "revision1", ReadingSessionMode.READING, 2)
    @Test fun pausesBufferingAndSeekingNeverAccrueOrMarkSkippedLocations() {
        val saved = linkedMapOf<String, ReadingSession>()
        var id = 0
        val clock = ReadingSessionClock { (++id).toString() }
        clock.transition(target, true, 100_000, 1_000, "UTC")
        clock.transition(target, false, 110_000, 11_000, "UTC")
        clock.checkpoint(170_000, 71_000, "UTC")
        clock.transition(target, true, 180_000, 81_000, "UTC")
        clock.close("seek", 190_000, 91_000, "UTC")
        clock.transition(target.copy(location = 40), true, 200_000, 101_000, "UTC")
        clock.close("paused", 210_000, 111_000, "UTC")
        clock.flush { saved[it.id] = it }
        assertEquals(30_000L, saved.values.sumOf { it.activeMillis })
        assertEquals(listOf(2, 2), saved.getValue("1").spans.map { it.location })
        assertEquals(listOf(40), saved.getValue("2").spans.map { it.location })
    }
    @Test fun modeHandoffUsesOneOwnerAndDuplicateCheckpointsAreIdempotent() {
        val saved = linkedMapOf<String, ReadingSession>(); var id = 0
        val clock = ReadingSessionClock { (++id).toString() }
        clock.transition(target, true, 100_000, 1000, "UTC")
        clock.transition(target.copy(mode = ReadingSessionMode.LISTENING), true, 105_000, 6000, "UTC")
        clock.checkpoint(110_000, 11000, "UTC")
        clock.checkpoint(110_000, 11000, "UTC")
        clock.close("end", 110_000, 11000, "UTC")
        clock.flush { saved[it.id] = it }
        assertEquals(5000L, saved.getValue("1").activeMillis)
        assertEquals(5000L, saved.getValue("2").activeMillis)
        assertEquals(10_000L, saved.values.sumOf { it.activeMillis })
    }
    @Test fun inactivityCapAndWallClockChangesUseMonotonicTime() {
        var result: ReadingSession? = null
        val clock = ReadingSessionClock()
        clock.transition(target, true, 100_000, 1000, "UTC")
        clock.transition(null, false, 900_000, 131_000, "UTC", "idle", capMono = 121_000)
        clock.flush { result = it }
        assertEquals(120_000L, result!!.activeMillis)
        assertEquals(100_000L, result!!.spans.single().startedAt)
        val reversed = ReadingSessionClock()
        reversed.transition(target, true, 100_000, 1000, "UTC")
        reversed.close("end", 50_000, 6000, "UTC")
        reversed.flush { result = it }
        assertEquals(5000L, result!!.activeMillis)
    }

    @Test fun failedCheckpointDoesNotCountTheIntervalAgainAndCloseWritesOnce() {
        val clock = ReadingSessionClock { "session" }
        val saved = mutableListOf<ReadingSession>()
        clock.transition(target, true, 100_000, 1_000, "UTC")
        clock.flush { saved += it }
        clock.checkpoint(105_000, 6_000, "UTC")
        assertThrows(IllegalStateException::class.java) {
            clock.flush { error("Temporary storage failure") }
        }
        clock.transition(null, false, 110_000, 11_000, "UTC", "hidden")
        clock.flush { saved += it }
        clock.flush { saved += it }
        assertEquals(listOf(0L, 10_000L), saved.map { it.measuredMillis })
        assertEquals("hidden", saved.last().closedReason)
        assertNull(clock.current)
    }

    @Test fun failedHandoffKeepsTheNewSessionRunningAndRetriesOnlyUncommittedRecords() {
        var id = 0
        val clock = ReadingSessionClock { (++id).toString() }
        val saved = mutableListOf<ReadingSession>()
        val audio = target.copy(mode = ReadingSessionMode.LISTENING, location = 16, page = 3)
        clock.transition(target, true, 100_000, 1_000, "UTC")
        clock.transition(audio, true, 105_000, 6_000, "UTC")
        assertThrows(IllegalStateException::class.java) {
            clock.flush {
                if (it.id == "2") error("Temporary storage failure")
                saved += it
            }
        }
        clock.close("seek", 110_000, 11_000, "UTC")
        clock.flush { saved += it }
        assertEquals(listOf("1", "2"), saved.map { it.id })
        assertEquals(listOf(5_000L, 5_000L), saved.map { it.measuredMillis })
        assertEquals(16, saved.last().spans.single().location)
        assertEquals(3, saved.last().endPage)
    }
    @Test fun midnightAndTimezoneSlicesRetainOwnershipAndCorrectionPreservesMeasurement() {
        val midnight = Instant.parse("2026-10-10T23:59:30Z").toEpochMilli()
        val session = ReadingSession("1", "book", "Title", "rev", ReadingSessionMode.LISTENING,
            midnight, midnight + 60_000, 1, 1, listOf(ReadingSessionSpan(midnight, 60_000, "UTC", 1)), "end")
        assertEquals(mapOf("2026-10-10" to 30_000L, "2026-10-11" to 30_000L), session.millisByDate())
        val corrected = session.copy(correctedMillis = 120_000)
        assertEquals(60_000L, corrected.measuredMillis)
        assertEquals(mapOf("2026-10-10" to 60_000L, "2026-10-11" to 60_000L), corrected.millisByDate())
        assertTrue(session.copy(excluded = true).millisByDate().isEmpty())
        assertEquals(session, ReadingSession.fromJson(session.toJson()))
        assertEquals(mapOf("2026-10-11" to 60_000L), session.copy(spans = listOf(ReadingSessionSpan(midnight, 60_000, "Europe/Berlin", 1))).millisByDate())
    }
    @Test fun dstDayUsesActualMidnightBoundary() {
        val start = Instant.parse("2026-03-29T00:30:00Z").toEpochMilli()
        val session = ReadingSession("1", "book", "Title", "rev", ReadingSessionMode.READING, start, start + 86_400_000, 0, 0,
            listOf(ReadingSessionSpan(start, 86_400_000, "Europe/Berlin", 0)), "end")
        assertEquals(21 * 3_600_000L + 30 * 60_000L, session.millisByDate()["2026-03-29"])
        assertEquals(2 * 3_600_000L + 30 * 60_000L, session.millisByDate()["2026-03-30"])
    }

    @Test fun dateOnlyManualSessionsKeepTheEntireDurationOnTheChosenDstDay() {
        val date = LocalDate.parse("2026-03-29")
        val noon = Instant.parse("2026-03-29T10:00:00Z").toEpochMilli()
        val duration = 900 * 60_000L
        val session = ReadingSession("manual", "book", "Title", "manual", ReadingSessionMode.READING,
            noon, noon + duration, 1, 1, listOf(ReadingSessionSpan(noon, duration, "Europe/Berlin", 1)),
            "manual", manual = true, manualDate = date)

        assertEquals(mapOf("2026-03-29" to duration), session.millisByDate())
        assertEquals(session, ReadingSession.fromJson(session.toJson()))
        assertEquals(mapOf("2026-03-29" to 60_000L), session.copy(correctedMillis = 60_000L).millisByDate())
        assertTrue(session.copy(excluded = true).millisByDate().isEmpty())

        val legacy = ReadingSession.fromJson(session.copy(manualDate = null).toJson())!!
        assertEquals(mapOf("2026-03-29" to 720 * 60_000L, "2026-03-30" to 180 * 60_000L), legacy.millisByDate())
    }

    @Test fun timedUsageKeepsTheMinutesOnBothSidesOfMidnight() {
        val start = Instant.parse("2026-11-20T23:50:00Z").toEpochMilli()
        val usage = mutableMapOf("2026-11-20" to 60_000L)
        usage.addDurationByDate(start, 25 * 60_000L, ZoneId.of("UTC"))
        assertEquals(mapOf("2026-11-20" to 11 * 60_000L, "2026-11-21" to 15 * 60_000L), usage)
    }
}
