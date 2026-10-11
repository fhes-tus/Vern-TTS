package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test

class BookExportTest {
    @Test fun catalogTextUsesTxtAndOriginalFormatsKeepTheirMimeType() {
        assertEquals("Pride and Prejudice.txt", bookExportName("Pride and Prejudice", "txt").filename)
        assertEquals("text/plain", bookExportName("Book", "txt").mimeType)
        assertEquals("application/epub+zip", bookExportName("Book", "epub").mimeType)
        assertEquals("application/pdf", bookExportName("Book", "pdf").mimeType)
        assertEquals("Book.pdf", bookExportName("Book.pdf", "pdf").filename)
    }
    @Test fun namesCannotEscapeTheirChosenDestination() {
        val name = bookExportName("../Books: A\nStory?", "txt").filename
        assertFalse(name.contains('/'))
        assertFalse(name.contains(':'))
        assertFalse(name.contains('\n'))
        assertEquals("Vern book.txt", bookExportName("...", "bad/ext").filename)
    }
}
