package com.veritas.reader

import com.veritas.reader.ui.screens.*
import org.junit.Assert.*
import org.junit.Test

class OutlineRegressionTest {
    @Test fun ordinaryListsAreExcludedButNumberedSectionsWithBodySurvive() {
        listOf("• First Item", "- First Item", "* First Item", "+ First Item", "[x] First Item").forEach { assertFalse(it, looksLikeOutlineHeading(it)) }
        assertTrue(buildSmartOutline(listOf("1. First Item", "2. Second Item", "3. Third Item")).none { it.isHeading })
        assertTrue(buildSmartOutline(listOf("1. First Item\n2. Second Item\n3. Third Item")).none { it.isHeading })
        val sections = buildSmartOutline(listOf("1. Introduction", "Here is the discussion.", "2. Methods", "We explain the methods."))
        assertEquals(listOf(0, 2), sections.filter { it.isHeading }.map { it.index })
        assertTrue(looksLikeOutlineHeading("## First Item"))
        assertTrue(looksLikeOutlineHeading("1.2 Experimental Methods"))
    }
    @Test fun ordinarySentencesAreNotHeadingsOrTruncatedIntoHeadings() {
        listOf("Notes were left on the table.", "Summary of what he told me.", "1. We walked down the street.", "The Wind Was Strong.", "I could see the house.").forEach { assertFalse(it, looksLikeOutlineHeading(it)) }
        val outline = buildSmartOutline(listOf("Notes were left on the table. " + "He then said more. ".repeat(30)))
        assertTrue(outline.all { !it.isHeading && it.title.startsWith("Sentence ") })
        assertTrue(looksLikeOutlineHeading("CHAPTER I."))
        assertTrue(looksLikeOutlineHeading("## A Short Heading"))
    }
    @Test fun repeatedChaptersInCollectionsAreRetained() {
        val outline = buildSmartOutline(listOf("CHAPTER I", "Ordinary prose.", "CHAPTER II", "More prose.", "CHAPTER I", "A different story.", "CHAPTER II"))
        assertEquals(listOf(0, 2, 4, 6), outline.map { it.index })
    }
    @Test fun headingTargetsBeatEarlierMentionsAndSupportUnicode() {
        val chunks = listOf("We discuss Étude α in the next chapter.", "Étude α", "The discussion begins.")
        assertEquals(1, locateOutlineTarget(chunks, "Étude α"))
        assertEquals("étude α", normalizeOutlineNeedle("Étude α"))
    }
    @Test fun lateChaptersAreDetectedAcrossALongDocument() {
        val chunks = MutableList(3000) { "The detective asked another question." }
        chunks[2990] = "CHAPTER XL"
        assertEquals(2990, buildSmartOutline(chunks).single { it.isHeading }.index)
    }
}
