package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfSelectionRangeTest {
    @Test
    fun resolvesShortCrossSentenceSelectionsOnTheSourcePage() {
        val model = model(1 to "You can go.", 1 to "Come back.")
        assertEquals(PdfSelectionRange(PdfSelectionMatch(0, 8), 1),
            PdfSelectionLocator.findExactRange("go. Come", model, 1))
    }

    @Test
    fun repeatedTailDoesNotExtendTheRange() {
        val model = model(1 to "A passage starts here.", 1 to "It ends here.",
            1 to "Another thought.", 1 to "It ends here.")
        assertEquals(PdfSelectionRange(PdfSelectionMatch(0, 0), 1),
            PdfSelectionLocator.findExactRange("A passage starts here. It ends here.", model, 1))
    }

    @Test
    fun rejectsNonContiguousTextAndAmbiguousMatches() {
        val model = model(1 to "A passage starts here.", 1 to "Unselected text.",
            1 to "It ends here.", 1 to "It ends here.")
        assertNull(PdfSelectionLocator.findExactRange("A passage starts here. It ends here.", model, 1))
        assertNull(PdfSelectionLocator.findExactRange("It ends here.", model, 1))
    }

    @Test
    fun authoritativePageDoesNotMatchElsewhereButRangeCanCrossPages() {
        val model = model(1 to "Starting page.", 2 to "The next page.", 3 to "Other text.")
        assertEquals(PdfSelectionRange(PdfSelectionMatch(0, 0), 1),
            PdfSelectionLocator.findExactRange("Starting page. The next page.", model, 1, allowAdjacentPages = false))
        assertNull(PdfSelectionLocator.findExactRange("The next page.", model, 1, allowAdjacentPages = false))
        assertEquals(PdfSelectionRange(PdfSelectionMatch(1, 0), 1),
            PdfSelectionLocator.findExactRange("The next page.", model, 1))
    }

    @Test
    fun usesTheSelectedPageAndMapsFoldedTextToTheOriginalOffset() {
        val model = model(1 to "The final word.", 2 to "The ﬁnal word.")
        assertEquals(PdfSelectionRange(PdfSelectionMatch(1, 4), 1),
            PdfSelectionLocator.findExactRange("final", model, 2, allowAdjacentPages = false))
        assertNull(PdfSelectionLocator.findExactRange("he", model, 2))
    }

    @Test
    fun approximatePageRejectsTheSameSelectionOnANeighbor() {
        val model = model(1 to "Repeated phrase.", 2 to "Repeated phrase.")
        assertNull(PdfSelectionLocator.findExactRange("Repeated phrase.", model, 1))
    }

    private fun model(vararg pages: Pair<Int, String>) = ReaderTextModel(
        sentences = pages.mapIndexed { index, (page, text) -> ReaderSentence(index, text, page) },
        parts = emptyList(),
        pageCount = pages.maxOf { it.first },
        cleanText = pages.joinToString(" ") { it.second }
    )
}
