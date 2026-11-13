package com.veritas.reader

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Gregorian dates, with the user's day-first convention for ambiguous numeric input. */
internal object SpeechDates {
    private const val MONTH = "(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\.?"
    private val numeric = Regex("""(?<![\p{L}\p{N}_/.-])([0-9]{1,4})[ \t]*([/.-])[ \t]*([0-9]{1,2})[ \t]*\2[ \t]*([0-9]{1,4})(?![\p{L}\p{N}_/-]|\.[0-9])""")
    private val dayFirst = Regex("""(?<![\p{L}\p{N}_])([0-9]{1,2})(?:st|nd|rd|th)?[ \t]*(?:of[ \t]+)?($MONTH)(?:[ \t,]*([0-9]{4}|[0-9]{2}))?(?![\p{L}\p{N}_])""", RegexOption.IGNORE_CASE)
    private val monthFirst = Regex("""(?<![\p{L}\p{N}_])($MONTH)[ \t]*([0-9]{1,2})(?:st|nd|rd|th)?(?:(?:[ \t]*,[ \t]*|[ \t]+)([0-9]{4}|[0-9]{2}))?(?![\p{L}\p{N}_])""", RegexOption.IGNORE_CASE)
    private val versionContext = Regex("""\b(?:version|build|release|v)[ \t]*$""", RegexOption.IGNORE_CASE)
    private val monthNumbers = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val ordinals = listOf("", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth",
        "eleventh", "twelfth", "thirteenth", "fourteenth", "fifteenth", "sixteenth", "seventeenth", "eighteenth", "nineteenth", "twentieth",
        "twenty-first", "twenty-second", "twenty-third", "twenty-fourth", "twenty-fifth", "twenty-sixth", "twenty-seventh", "twenty-eighth", "twenty-ninth", "thirtieth", "thirty-first")
    private data class Date(val value: LocalDate, val year: String?)

    /** No century is invented for a two-digit year, and no year is invented when absent. */
    fun expand(input: SpokenText, yearWords: ((Int) -> String)? = null): SpokenText {
        var output = input
        fun rewrite(pattern: Regex, parse: (MatchResult) -> Date?) {
            val source = output.text
            val code = ReadingTextCleanup.codeRanges(source)
            output = output.replace(pattern) { match ->
                val prefix = source.substring(maxOf(0, match.range.first - 32), match.range.first)
                if (code.any { match.range.first in it } || versionContext.containsMatchIn(prefix)) match.value
                else parse(match)?.let { render(it, yearWords) } ?: match.value
            }
        }
        rewrite(numeric) { match ->
            val first = match.groupValues[1]
            val middle = match.groupValues[3].toInt()
            val last = match.groupValues[4]
            when {
                first.length == 4 && last.length <= 2 -> date(last.toInt(), middle, first)
                first.length <= 2 && last.length in listOf(2, 4) -> {
                    // A second component above 12 makes month-first input unambiguous.
                    if (middle > 12) date(middle, first.toInt(), last)
                    else date(first.toInt(), middle, last)
                }
                else -> null
            }
        }
        rewrite(dayFirst) { date(it.groupValues[1].toInt(), month(it.groupValues[2]), it.groupValues[3].ifEmpty { null }) }
        rewrite(monthFirst) { date(it.groupValues[2].toInt(), month(it.groupValues[1]), it.groupValues[3].ifEmpty { null }) }
        return output
    }

    private fun month(name: String): Int = monthNumbers.indexOf(name.take(3).lowercase(Locale.ROOT)) + 1

    private fun date(day: Int, month: Int, year: String?): Date? {
        val parsedYear = year?.toInt() ?: 2000 // Validation only; never spoken as an absent year.
        if (year?.length == 4 && parsedYear == 0) return null
        // A year ending in 00 is not enough to establish whether 29 February existed.
        if (year == "00" && month == 2 && day == 29) return null
        return runCatching { Date(LocalDate.of(if (year?.length == 2) 2000 + parsedYear else parsedYear, month, day), year) }.getOrNull()
    }

    private fun render(date: Date, yearWords: ((Int) -> String)?): String {
        val month = date.value.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val year = date.year?.let {
            when {
                yearWords == null -> it
                it.length == 2 && it.startsWith('0') -> if (it == "00") "zero zero" else "oh ${listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")[it.toInt()]}"
                else -> yearWords(it.toInt())
            }
        }
        return if (yearWords == null) "$month ${date.value.dayOfMonth}" + if (year == null) "" else ", $year"
        else "${ordinals[date.value.dayOfMonth]} of $month" + if (year == null) "" else ", $year"
    }
}
