package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class VeritasWidgetLogicTest {

    @Test
    fun testWidgetProgressCalculation() {
        val book = SavedDocument("widget-book", "Fixture", "fixture.txt", "TXT", 1L, 1L, 0, 1, 10, "Fixture")
        assertEquals(0f, WidgetReadingState(book).progress, 0f)
        assertEquals(1f, WidgetReadingState(book, isFinished = true).progress, 0f)
        assertEquals(.24f, WidgetReadingState(book.copy(chunkCount = 100), index = 24).progress, .001f)
    }

    @Test
    fun testWidgetProgressFormatting() {
        val chunkCount = 50
        val currentIndex = 9
        val progressPercent = ((currentIndex + 1) * 100) / chunkCount
        val progressText = "Sentence ${currentIndex + 1} of $chunkCount • $progressPercent%"
        assertEquals("Sentence 10 of 50 • 20%", progressText)
    }

    @Test
    fun testFormatBadgeResolution() {
        fun getFormat(title: String): String {
            return when {
                title.endsWith(".pdf", ignoreCase = true) -> "PDF"
                title.endsWith(".docx", ignoreCase = true) || title.endsWith(".doc", ignoreCase = true) -> "DOCX"
                title.endsWith(".epub", ignoreCase = true) -> "EPUB"
                title.endsWith(".pptx", ignoreCase = true) || title.endsWith(".ppt", ignoreCase = true) -> "PPTX"
                title.endsWith(".txt", ignoreCase = true) -> "TXT"
                else -> "DOC"
            }
        }

        assertEquals("PDF", getFormat("Chapter1.PDF"))
        assertEquals("EPUB", getFormat("moby_dick.epub"))
        assertEquals("DOCX", getFormat("notes.docx"))
        assertEquals("PPTX", getFormat("lecture.pptx"))
        assertEquals("TXT", getFormat("summary.txt"))
        assertEquals("DOC", getFormat("random_article"))
    }

    @Test
    fun testChecklistTogglingLogic() {
        fun toggleItem(line: String): String {
            return when {
                line.startsWith("[x] ") -> "[ ] " + line.removePrefix("[x] ")
                line.startsWith("[ ] ") -> "[x] " + line.removePrefix("[ ] ")
                line.startsWith("[x]") -> "[ ]" + line.removePrefix("[x]")
                line.startsWith("[ ]") -> "[x]" + line.removePrefix("[ ]")
                else -> line
            }
        }

        assertEquals("[x] Read chapter 3", toggleItem("[ ] Read chapter 3"))
        assertEquals("[ ] Read chapter 3", toggleItem("[x] Read chapter 3"))
    }
}
