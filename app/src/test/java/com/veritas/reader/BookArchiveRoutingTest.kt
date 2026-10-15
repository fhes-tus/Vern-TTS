package com.veritas.reader

import com.veritas.reader.ui.ReaderUiState
import com.veritas.reader.ui.withBookArchive
import com.veritas.reader.ui.screens.FREE_BOOK_SITES
import com.veritas.reader.ui.screens.LibrarySection
import org.junit.Assert.*
import org.junit.Test

class BookArchiveRoutingTest {
    @Test fun everyArchiveOpensItsOwnBrowserAndRetainsTheSearchAndLibraryDestination() {
        val original = ReaderUiState(showOceanOfPdfBrowser = true, oceanOfPdfQuery = "old query",
            targetLibrarySection = LibrarySection.CLASSICS, readerPosition = 1432)
        for (site in FREE_BOOK_SITES) {
            val opened = original.withBookArchive(site.url, site.name, "  Alice & wonderland  ")
            assertTrue("${site.name} opens a browser", opened.showBookBrowser)
            assertEquals(site.url, opened.bookBrowserUrl)
            assertEquals(site.name, opened.bookBrowserTitle)
            assertEquals("Alice & wonderland", opened.bookBrowserQuery)
            assertFalse(opened.showOceanOfPdfBrowser)
            assertEquals("", opened.oceanOfPdfQuery)
            assertEquals(LibrarySection.CLASSICS, opened.targetLibrarySection)
            assertEquals(1432, opened.readerPosition)
        }
    }
}
