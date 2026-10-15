package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test

class ReadingSymbolsTest {
    @Test fun mathematicalSymbolsAreSpokenAndFollowingOffsetsStayAligned() {
        val source = "If α ≤ 3, x² + β = 9. Continue reading."
        val spoken = ReadingSymbols.prepare(source)
        assertTrue(spoken.text.contains("alpha"))
        assertTrue(spoken.text.contains("less than or equal to"))
        assertTrue(spoken.text.contains("squared"))
        assertTrue(spoken.text.contains("equals"))
        assertEquals(source.indexOf('≤'), spoken.sourceOffset(spoken.text.indexOf("less than")))
        assertEquals(source.indexOf("Continue"), spoken.sourceOffset(spoken.text.indexOf("Continue")))
        assertEquals(source.length, spoken.sourceOffset(spoken.text.length))
    }
    @Test fun romanNumeralsRequireContextAndCanonicalSyntax() {
        assertEquals("I mix CIVIL words. Chapter 14, Part 4, Henry the 8th.", ReadingSymbols.prepare("I mix CIVIL words. Chapter XIV, Part IV, Henry VIII.").text)
        assertEquals("Chapter IIII", ReadingSymbols.prepare("Chapter IIII").text)
        assertNull(ReadingSymbols.romanValue("IC"))
    }
    @Test fun datesRangesAndArithmeticAreDistinguished() {
        assertTrue(ReadingSymbols.prepare("On 2026-10-01.").text.contains("October 1, 2026"))
        assertEquals("Pages 10 to 12.", ReadingSymbols.prepare("Pages 10-12.").text)
        assertEquals("1930 to 1940", ReadingSymbols.prepare("1930-1940").text)
        assertEquals("5 minus 2", ReadingSymbols.prepare("5 - 2").text)
        assertEquals("Temperature minus 4", ReadingSymbols.prepare("Temperature -4").text)
        assertEquals("A well-known name on 01/02/2026.", ReadingSymbols.prepare("A well-known name on 01/02/2026.").text)
        assertEquals("2026-13-35", ReadingSymbols.prepare("2026-13-35").text) // invalid ISO dates stay intact
    }
    @Test fun chemistryRequiresAFormulaOrExplicitChemicalContext() {
        assertEquals("He met us. I am IN the USA.", ReadingSymbols.prepare("He met us. I am IN the USA.").text)
        assertEquals("hydrogen 2 oxygen and sodium chlorine", ReadingSymbols.prepare("H₂O and NaCl").text)
        assertEquals("The element iron is present.", ReadingSymbols.prepare("The element Fe is present.").text)
        assertEquals("carbon oxygen 2", ReadingSymbols.prepare("CO2").text)
    }
    @Test fun foreignProseIsNotExpandedIntoEnglishNames() {
        assertEquals("α ≤ 3", ReadingSymbols.prepare("α ≤ 3", english = false).text)
        assertEquals("Acta non verba.", ReadingSymbols.prepare("Acta non verba.").text)
        assertEquals("for example birds, that is animals, Smith and others", ReadingSymbols.prepare("e.g. birds, i.e. animals, Smith et al.").text)
    }
    @Test fun composedPronunciationReplacementsMapToOriginalText() {
        val raw = "Read Dr. β next."
        val mapped = SpokenText.identity(raw).replace(Regex("Dr\\.")) { "Doctor" }
        val spoken = ReadingSymbols.expand(mapped).sanitize()
        assertEquals(raw.indexOf("Dr."), spoken.sourceOffset(spoken.text.indexOf("Doctor")))
        assertEquals(raw.indexOf('β'), spoken.sourceOffset(spoken.text.indexOf("beta")))
        assertEquals(raw.indexOf("next"), spoken.sourceOffset(spoken.text.indexOf("next")))
    }
}
