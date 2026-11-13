package com.veritas.reader

import com.veritas.reader.tts.NeuralSpeechText
import org.junit.Assert.*
import org.junit.Test

class SpeechDatesTest {
    private fun expand(source: String) = SpeechDates.expand(SpokenText.identity(source)).text

    @Test fun ambiguousNumericDatesUseTheRequestedDayFirstConvention() {
        for (source in listOf("03/04/2026", "3/4/2026", "03-04-2026", "03.04.2026", "3 / 4 / 2026")) {
            assertEquals(source, "April 3, 2026", expand(source))
        }
    }

    @Test fun yearFirstAndUnambiguousMonthFirstDatesAreAlsoRecognized() {
        for (source in listOf("2026-12-31", "2026/12/31", "2026.12.31", "31/12/2026", "12/31/2026", "12.31.2026")) {
            assertEquals(source, "December 31, 2026", expand(source))
        }
    }

    @Test fun namedMonthsAllowOrdinalsAbbreviationsAndEitherOrder() {
        for (source in listOf("3 April 2026", "3rd April, 2026", "3rd of Apr. 2026", "April 3, 2026", "Apr. 3rd,2026", "3rdApril2026", "April3,2026")) {
            assertEquals(source, "April 3, 2026", expand(source))
        }
        assertEquals("September 12, 2026", expand("12 Sept. 2026"))
    }

    @Test fun invalidDatesFractionsVersionsAndCodeAreNotInventedDates() {
        for (source in listOf("31/02/2026", "2026-13-35", "29/02/2025", "April 31, 2026", "1/2", "12.5%",
            "v1.2.26", "version 1.2.26", "192.168.1.2", "`03/04/2026`", "29/02/00")) {
            assertEquals(source, source, expand(source))
        }
        assertEquals("February 29, 2024", expand("29/02/2024"))
    }

    @Test fun absentYearsAndShortYearsNeverAcquireAnInventedCentury() {
        assertEquals("April 3", expand("3 April"))
        assertEquals("April 3, 26", expand("03/04/26"))
        assertEquals("April 3, 06", expand("3 Apr 06"))
        assertEquals("third of April, 26", NeuralSpeechText.prepare("03/04/26"))
        assertEquals("third of April, oh six", NeuralSpeechText.prepare("03/04/06"))
    }

    @Test fun neuralDatesHaveExplicitOrdinalDaysAndClearlySpokenYears() {
        assertEquals("On the third of April, twenty twenty-six.", NeuralSpeechText.prepare("On the 03/04/2026."))
        assertEquals("twenty-first of November, nineteen eighty-two", NeuralSpeechText.prepare("21st Nov 1982"))
        assertEquals("first of January, two thousand", NeuralSpeechText.prepare("2000-01-01"))
        assertEquals("twelfth of November", NeuralSpeechText.prepare("Nov 12"))
    }

    @Test fun dateExpansionRetainsItsSourceSpanAndFollowingWordOffsets() {
        val source = "On 03/04/2026, continue."
        val spoken = NeuralSpeechText.prepare(SpokenText.identity(source))
        val dateStart = spoken.text.indexOf("third")
        assertEquals(source.indexOf("03/04/2026"), spoken.sourceOffset(dateStart))
        val dateEnd = spoken.text.indexOf(", continue")
        assertEquals(source.indexOf("03/04/2026") + 10, spoken.sourceEndOffset(dateEnd))
        assertEquals(source.indexOf("continue"), spoken.sourceOffset(spoken.text.indexOf("continue")))
        assertEquals(spoken.text, NeuralSpeechText.prepare(spoken).text)
    }

    @Test fun sharedReadingsUseTheSameDateParsingAndKeepForeignInputUntouched() {
        assertEquals("On April 3, 2026.", ReadingSymbols.prepare("On 03/04/2026.").text)
        assertEquals("03/04/2026", ReadingSymbols.prepare("03/04/2026", english = false).text)
    }
}
