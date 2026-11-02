package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test

class LibraryOrderingTest {
    private fun book(id: String, index: Int, count: Int = 10, created: Long = 0, type: String = "TXT") =
        SavedDocument(id, id, "$id.txt", type, created, created, index, count, 100, "")

    private val books = listOf(
        book("A finished", 9, created = 40, type = "PDF"),
        book("D unread", 0, created = 20),
        book("C reading", 4, created = 30, type = "EPUB"),
        book("B finished", 9, created = 10)
    )

    @Test fun completedBooksFollowUnfinishedBooksInEveryLibrarySort() {
        val expected = mapOf(
            "Title" to listOf("C reading", "D unread", "A finished", "B finished"),
            "Progress" to listOf("C reading", "D unread", "A finished", "B finished"),
            "Type" to listOf("C reading", "D unread", "A finished", "B finished"),
            "Newest" to listOf("C reading", "D unread", "A finished", "B finished"),
            "Custom" to listOf("D unread", "C reading", "A finished", "B finished")
        )
        expected.forEach { (mode, ids) -> assertEquals(mode, ids, sortLibraryDocuments(books, mode).map { it.id }) }
        assertEquals("A finished", books.first().id)
    }

    @Test fun queuedLibraryResultsGroupCompletionWithoutMutatingThePlaybackQueue() {
        val queue = listOf("B finished", "C reading", "A finished", "D unread")
        assertEquals(listOf("C reading", "D unread", "B finished", "A finished"),
            sortLibraryDocuments(books, "Title", queue).map { it.id })
        assertEquals("B finished", queue.first())
    }

    @Test fun emptyAndCompletedOnlyListsRetainTheirSelectedOrder() {
        assertTrue(sortLibraryDocuments(emptyList(), "Newest").isEmpty())
        val empty = book("Empty", 0, count = 0)
        val one = book("One sentence", 0, count = 1)
        assertEquals(listOf(empty, one), sortLibraryDocuments(listOf(one, empty), "Custom"))
        assertEquals(listOf("B finished", "A finished"),
            sortLibraryDocuments(books.filter { it.currentIndex == 9 }.reversed(), "Custom").map { it.id })
        val reopened = books[0].copy(currentIndex = 2)
        assertEquals(reopened, sortLibraryDocuments(listOf(books[3], reopened), "Custom").first())
    }

    @Test fun manualReorderingCannotMoveCompletedBooksAheadOfUnfinishedBooks() {
        assertTrue(canReorderLibraryDocuments(books[0], books[3]))
        assertTrue(canReorderLibraryDocuments(books[1], books[2]))
        assertFalse(canReorderLibraryDocuments(books[0], books[1]))
        assertFalse(canReorderLibraryDocuments(books[1], books[0]))
    }
}
