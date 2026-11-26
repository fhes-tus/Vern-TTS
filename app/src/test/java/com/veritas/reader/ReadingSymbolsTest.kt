package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test

class ReadingSymbolsTest {
    @Test fun moneyUsesCurrencyAndFractionalUnitNames() {
        val expected = mapOf(
            "\$59" to "59 dollars", "\$1" to "1 dollar", "\$1.25" to "1 dollar and 25 cents",
            "£1,234.01" to "1234 pounds and 1 penny", "£0.50" to "50 pence",
            "1.234,56 €" to "1234 euros and 56 cents", "€1" to "1 euro",
            "₵12.05" to "12 cedis and 5 pesewas", "GH₵1" to "1 cedi",
            "GHS 60" to "60 cedis", "1 500 F CFA" to "1500 CFA francs",
            "XOF 1" to "1 CFA franc", "250 XAF" to "250 CFA francs"
        )
        for ((source, speech) in expected) assertEquals(source, speech, ReadingSymbols.prepare(source).text)
    }

    @Test fun narrationPlansReadMoneyAmpersandsAndPercentFromBookText() {
        val imported = MathText.beautify("R&D paid \$59 or £7 & saved 12.5%.")
        val sentence = ReaderTextIndex.build(imported, 1).sentences.single()
        for (voice in listOf(
            VoiceSettings(localeTag = "en-GB"),
            VoiceSettings(enginePackage = VoiceManager.KITTEN, voiceName = "kitten_0", localeTag = "en-US")
        )) {
            val speech = SpeechPlan.segment("book", "1", 0, sentence.text, voice,
                NarrationSettings(), 1f, 1f, emptyList()).spoken
            assertEquals("R and D paid 59 dollars or 7 pounds and saved 12.5 percent.", speech.text)
            assertEquals(imported.indexOf("saved"), speech.sourceOffset(speech.text.indexOf("saved")))
        }
    }

    @org.junit.Test fun bibleRangesAllowSpacesAroundTheDash() {
        for (range in listOf("2-3", "2 -3", "2- 3", "2 - 3", "2–3", "2−3", "2‑3", "2\u202f—\u200b3")) {
            org.junit.Assert.assertEquals("John 4 verse 2 to 3", ReadingSymbols.prepare("John 4:$range").text)
        }
        org.junit.Assert.assertEquals("verse 2 to 3", ReadingSymbols.prepare("verse 2 - 3").text)
        org.junit.Assert.assertEquals("John 4 verse 2 to 3", ReadingSymbols.prepare("<u>John 4:2-3</u>").text)
        org.junit.Assert.assertEquals("2 minus 3", ReadingSymbols.prepare("2 - 3").text)
        assertEquals("Matthew 3 verse 4 to 5", ReadingSymbols.prepare("Matthew 3 : 4 − 5").text)
        assertEquals("First Corinthians 2 verse 3 to 5", ReadingSymbols.prepare("1\u00A0Cor. 2:3-5").text)
        assertEquals("Song of Solomon 2 verse 3 to 5", ReadingSymbols.prepare("Song of Solomon 2:3-5").text)
    }

    @Test fun readerCitationReachesKittenAndAndroidAsAVerseRange() {
        for ((engine, voiceName, tag) in listOf(
            Triple(VoiceManager.KITTEN, "kitten_0", "fr-FR"),
            Triple("", "", "en-US")
        )) {
            val voice = VoiceSettings(enginePackage = engine, voiceName = voiceName, localeTag = tag)
            for (citation in listOf("Matthew 3:4-5", "Matthew 3:4 -5", "Matthew 3:4- 5", "Matthew 3:4 - 5",
                "Matthew 3 : 4\u202f—\u200b5", "Matthew 3 verse 4-5")) {
                val savedText = "Read $citation. Then calculate 4 - 5."
                val spoken = ReaderTextIndex.build(savedText, 1).sentences.map { sentence ->
                    SpeechPlan.segment("test", "1", sentence.index, sentence.text, voice,
                        NarrationSettings(), 1f, 1f, emptyList()).spoken.text
                }.joinToString(" ")
                assertTrue("Unexpected speech for $citation: $spoken", spoken.contains("Matthew 3 verse 4 to 5"))
                assertTrue("Subtraction must still be spoken as subtraction: $spoken", spoken.contains("4 minus 5"))
            }
        }
    }
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
        assertEquals("A well-known name on February 1, 2026.", ReadingSymbols.prepare("A well-known name on 01/02/2026.").text)
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
