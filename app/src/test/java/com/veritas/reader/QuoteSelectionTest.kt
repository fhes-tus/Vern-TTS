package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test

class QuoteSelectionTest {
    private val chunks = (0..6).map { "Sentence $it." }

    @Test fun selectionIsLimitedToFiveAndUsesBookOrder() {
        var selected = listOf(4)
        for (index in listOf(2, 0, 3, 1)) selected = QuoteSelection.toggle(chunks, selected, index)
        assertEquals(listOf(0, 1, 2, 3, 4), selected)
        assertEquals(chunks.take(5).joinToString("\n\n"), QuoteSelection.text(chunks, selected))
        assertThrows(IllegalArgumentException::class.java) { QuoteSelection.toggle(chunks, selected, 5) }
        assertEquals(listOf(0, 1, 3, 4), QuoteSelection.toggle(chunks, selected, 2))
    }

    @Test fun invalidEmptyAndOverlongQuotesAreRejectedWithoutTruncating() {
        assertThrows(IllegalArgumentException::class.java) { QuoteSelection.toggle(chunks, emptyList(), 99) }
        assertThrows(IllegalArgumentException::class.java) { QuoteSelection.toggle(listOf(" "), emptyList(), 0) }
        assertThrows(IllegalArgumentException::class.java) { QuoteSelection.toggle(listOf("a".repeat(2401)), emptyList(), 0) }
        assertEquals(emptyList<Int>(), QuoteSelection.toggle(chunks, listOf(0), 0))
    }
}
