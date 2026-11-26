package com.veritas.reader

import com.veritas.reader.ui.screens.CURATED_CLASSICS
import com.veritas.reader.ui.screens.getBookOfTheWeek
import com.veritas.reader.ui.screens.weeklyClassicId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDate

class WeeklyClassicSelectionTest {
    private val firstMonday = LocalDate.of(1970, 1, 5)

    @Test
    fun pickChangesOnMondayAndRemainsTheSameAcrossNewYear() {
        val monday = LocalDate.of(2026, 12, 28)
        val pick = getBookOfTheWeek(monday).id
        for (day in 1L..6L) {
            assertEquals(pick, getBookOfTheWeek(monday.plusDays(day)).id)
        }
        assertNotEquals(pick, getBookOfTheWeek(monday.plusWeeks(1)).id)
    }

    @Test
    fun realCatalogIsShuffledAndEachCycleContainsEveryBookExactlyOnce() {
        val catalog = CURATED_CLASSICS.map { it.id }
        val cycles = (-1L..2L).map { cycle ->
            val start = firstMonday.plusWeeks(cycle * catalog.size)
            catalog.indices.map { getBookOfTheWeek(start.plusWeeks(it.toLong())).id }
        }
        cycles.forEach { assertEquals(catalog.sorted(), it.sorted()) }
        assertFalse(cycles.any { picks -> catalog.indices.any { catalog.drop(it) + catalog.take(it) == picks } })
        assertEquals(cycles.size, cycles.distinct().size)
        cycles.zipWithNext().forEach { (previous, next) -> assertNotEquals(previous.last(), next.first()) }
    }

    @Test
    fun smallCatalogsKeepTheirAvailablePicksWithoutAnArtificialSkip() {
        assertEquals("a", weeklyClassicId(listOf("a"), firstMonday.minusWeeks(1)))
        val picks = (0L..3L).map { weeklyClassicId(listOf("a", "b"), firstMonday.plusWeeks(it)) }
        assertNotEquals(picks[0], picks[1])
        assertEquals(picks.take(2), picks.drop(2))
    }
}
