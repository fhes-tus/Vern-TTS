package com.veritas.reader.ui.screens

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

private val firstMonday = LocalDate.of(1970, 1, 5)
private const val WEEKLY_CLASSIC_SEED = 0x5645524EL

/** The same catalog has the same Monday-to-Sunday pick on every installation. */
internal fun weeklyClassicId(sortedIds: List<String>, date: LocalDate): String {
    val size = sortedIds.size.toLong()
    val week = Math.floorDiv(ChronoUnit.DAYS.between(firstMonday, date), 7L)
    val index = Math.floorMod(week, size).toInt()
    if (size <= 2) return sortedIds.shuffled(Random(WEEKLY_CLASSIC_SEED))[index]

    val cycle = Math.floorDiv(week, size)
    fun shuffled(number: Long) = sortedIds.shuffled(Random(WEEKLY_CLASSIC_SEED xor number))
    val picks = shuffled(cycle)
    // Swap only the first two entries so the last entry remains predictable at the next boundary.
    return if (index < 2 && picks.first() == shuffled(cycle - 1).last()) {
        picks[1 - index]
    } else {
        picks[index]
    }
}
