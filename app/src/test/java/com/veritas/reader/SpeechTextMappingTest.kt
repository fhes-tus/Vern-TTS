package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test

class SpeechTextMappingTest {
    @Test fun tableWordsMapToTheirActualSourceAfterInsertedPauses() {
        val source = "| Alpha | Beta | Gamma |"
        val spoken = SpokenText.identity(source).sanitize()
        assertEquals("Alpha. Beta. Gamma.", spoken.text)
        for (word in listOf("Alpha", "Beta", "Gamma")) {
            assertEquals(word, source.indexOf(word), spoken.sourceOffset(spoken.text.indexOf(word)))
        }
        assertEquals(source.length, spoken.sourceOffset(spoken.text.length))
    }

    @Test fun deletedMarkersDoNotMoveFollowingSourceAssociations() {
        val source = "[[VERITAS_IMAGE:cover]] Alpha [[VERITAS_IMAGE:figure]] Beta."
        val spoken = SpokenText.identity(source).sanitize()
        assertFalse(spoken.text.contains("VERITAS"))
        assertEquals(source.indexOf("Alpha"), spoken.sourceOffset(spoken.text.indexOf("Alpha")))
        assertEquals(source.indexOf("Beta"), spoken.sourceOffset(spoken.text.indexOf("Beta")))
        assertEquals(source.length, spoken.sourceOffset(spoken.text.length))
    }

    @Test fun tableMappingComposesWithPronunciationAndSupplementaryCharacters() {
        val source = "| 😀 SQL | Cafe\u0301 | Next |"
        val rules = PronunciationRulesEngine.compile(listOf(PronunciationRule("sql", "SQL", "sequel database")))
        val spoken = SpokenText.identity(source).applyRules(rules).sanitize()
        assertEquals(source.indexOf("SQL"), spoken.sourceOffset(spoken.text.indexOf("sequel")))
        assertEquals(source.indexOf("SQL"), spoken.sourceOffset(spoken.text.indexOf("database")))
        assertEquals(source.indexOf("Cafe"), spoken.sourceOffset(spoken.text.indexOf("Cafe")))
        assertEquals(source.indexOf("Next"), spoken.sourceOffset(spoken.text.indexOf("Next")))
    }

    @Test fun tableColumnLookupUsesOriginalSourceOffsets() {
        val source = " | A | B | C | "
        assertEquals(0, SpeechSanitizer.tableColumnIndexAt(source, source.indexOf("A")))
        assertEquals(1, SpeechSanitizer.tableColumnIndexAt(source, source.indexOf("B")))
        assertEquals(2, SpeechSanitizer.tableColumnIndexAt(source, source.indexOf("C")))
        val sparse = "| A | | C |"
        assertEquals(2, SpeechSanitizer.tableColumnIndexAt(sparse, sparse.indexOf("C")))
        assertEquals(-1, SpeechSanitizer.tableColumnIndexAt("plain text", 0))
    }

    @Test fun chemistryContextDoesNotTurnOrdinaryProseIntoElements() {
        val result = ReadingSymbols.prepare("He said the chemical formula is H2O. As we discussed, it is water.").text
        assertTrue(result.startsWith("He said"))
        assertTrue(result.contains("As we discussed"))
        assertTrue(result.contains("hydrogen 2 oxygen"))
        assertEquals("The element iron is present.", ReadingSymbols.prepare("The element Fe is present.").text)
    }

    @Test fun languageLettersArePreservedWhilePresentationLigaturesStillExpand() {
        val result = ReadingSymbols.prepare("læse et cœur; the ﬁnal ﬂower", english = false).text
        assertEquals("læse et cœur; the final flower", result)
    }

    @Test fun proseFootnoteMarkersAreNotInventedExponents() {
        val prose = ReadingSymbols.prepare("The result¹ was useful. See the note.²").text
        assertFalse(prose.contains("power"))
        assertFalse(prose.contains("squared"))
        assertTrue(prose.contains("result¹"))
        assertTrue(ReadingSymbols.prepare("x² and 10³; E=mc²").text.contains("squared"))
        assertTrue(ReadingSymbols.prepare("x² and 10³; E=mc²").text.contains("cubed"))
    }

    @Test fun numericListsAndAmbiguousTableValuesKeepTheirMeaning() {
        assertEquals("2024 2025 2026", SpeechSanitizer.forSpeech("2024 2025 2026"))
        assertEquals("Jan Feb Mar", SpeechSanitizer.forSpeech("Jan Feb Mar"))
        val result = SpeechSanitizer.forSpeech("| Item | N/A | None | - | |")
        assertTrue(result.contains("N/A"))
        assertTrue(result.contains("None."))
        assertEquals(1, Regex("None").findAll(result).count())
    }

    @Test fun expandedWordsRetainTheEntireOriginalSpanEvenWhenSliced() {
        val source = "SQL next."
        val spoken = SpokenText.identity(source).replace(Regex("SQL")) { "sequel database" }
        assertEquals(0, spoken.sourceOffset(0))
        assertEquals(3, spoken.sourceEndOffset("sequel".length))
        val databaseStart = spoken.text.indexOf("database")
        assertEquals(0, spoken.sourceOffset(databaseStart))
        assertEquals(3, spoken.sourceEndOffset(databaseStart + "database".length))
        val part = spoken.slice(0, "sequel".length)
        assertEquals(3, part.sourceEndOffset(part.text.length))
        assertEquals(3, part.sourceOffset(part.text.length))
        assertEquals(source.indexOf("next"), spoken.sourceOffset(spoken.text.indexOf("next")))
    }

    @Test fun finalWordEndExcludesRemovedTrailingMetadata() {
        val source = "Word [[VERITAS_IMAGE:cover]]"
        val spoken = SpokenText.identity(source).sanitize()
        assertEquals("Word", spoken.text)
        assertEquals(4, spoken.sourceEndOffset(spoken.text.length))
        assertEquals(source.length, spoken.sourceOffset(spoken.text.length))
    }

    @Test fun tableWordsHaveExactEndsAndInsertedPausesHaveEmptySpans() {
        val source = " | Alpha | Beta | "
        val spoken = SpokenText.identity(source).sanitize()
        for (word in listOf("Alpha", "Beta")) {
            val end = spoken.text.indexOf(word) + word.length
            assertEquals(source.indexOf(word) + word.length, spoken.sourceEndOffset(end))
            assertEquals(spoken.sourceOffset(end), spoken.sourceEndOffset(end + 1))
        }
    }

    @Test fun emptyAndZeroWidthEditsHaveDefinedBoundaries() {
        val spoken = SpokenText.identity("ABC").replace(Regex("ABC")) { "" }
        assertEquals("", spoken.text)
        assertEquals(3, spoken.sourceOffset(-1))
        assertEquals(3, spoken.sourceEndOffset(100))
        val inserted = SpokenText.identity("A").replace(Regex("$")) { "." }
        assertEquals("A.", inserted.text)
        assertEquals(1, inserted.sourceOffset(1))
        assertEquals(1, inserted.sourceEndOffset(2))
    }
}
