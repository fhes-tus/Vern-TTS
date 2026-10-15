package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test
import java.io.StringWriter

class ReadingLocationSummaryTest {
    private fun session(id: String, start: Long, locations: List<Int>, revision: String = "r", manual: Boolean = false) =
        ReadingSession(id, "book", "=Test, \"book\"", revision, ReadingSessionMode.READING, start, start + locations.size * 1000,
            locations.first(), locations.last(), locations.mapIndexed { i, location -> ReadingSessionSpan(start + i * 1000, 1000, "UTC", location) },
            closedReason = "test", manual = manual)
    @Test fun seeksDoNotVisitSkippedLocationsAndCheckpointContinuationsAreNotRevisits() {
        val first = session("a", 0, listOf(0, 0, 5, 0))
        val second = session("b", 10000, listOf(2, 5))
        val result = readingLocationSummaries(listOf(second, first, first))
        assertEquals(3000, result.getValue("a").firstVisitMillis); assertEquals(1000, result.getValue("a").revisitMillis)
        assertEquals(1000, result.getValue("b").firstVisitMillis); assertEquals(1000, result.getValue("b").revisitMillis)
    }
    @Test fun exclusionsRevisionsManualEntriesAndCorrectionsRemainHonest() {
        val excluded = session("old", 0, listOf(0)).copy(excluded = true)
        val read = session("a", 10000, listOf(0, 1, 0)).copy(correctedMillis = 6000)
        val revised = session("b", 20000, listOf(0), revision = "changed")
        val manual = session("c", 30000, listOf(0), manual = true)
        val result = readingLocationSummaries(listOf(excluded, read, revised, manual))
        assertEquals(0, result.getValue("old").firstVisitMillis)
        assertEquals(4000, result.getValue("a").firstVisitMillis); assertEquals(2000, result.getValue("a").revisitMillis)
        assertEquals(1000, result.getValue("b").firstVisitMillis); assertEquals(1000, result.getValue("c").unknownMillis)
    }
    @Test fun csvQuotesTextAndIncludesCorrectionAndExclusionState() {
        val output = StringWriter(); writeReadingSessionsCsv(listOf(session("a", 0, listOf(0)).copy(excluded = true)), output)
        val text = output.toString()
        assertTrue(text.contains("first_visit_ms,revisit_ms,unknown_ms")); assertTrue(text.contains("\"'=Test, \"\"book\"\"\""))
        assertTrue(text.contains("\"true\"")); assertTrue(text.endsWith("\r\n"))
    }
    @Test fun largeHistoryClassificationProcessesEveryMeasuredLocation() {
        val records = (0 until 10000).map { session("$it", it * 10000L, listOf(it % 100, (it + 1) % 100)) }
        val result = readingLocationSummaries(records)
        assertEquals(10000, result.size)
        assertEquals(20000000L, result.values.sumOf { it.firstVisitMillis + it.revisitMillis })
        assertEquals(100000L, result.values.sumOf { it.firstVisitMillis })
    }
}
