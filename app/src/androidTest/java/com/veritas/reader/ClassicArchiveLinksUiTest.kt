package com.veritas.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.veritas.reader.ui.ReaderUiState
import com.veritas.reader.ui.withBookArchive
import com.veritas.reader.ui.screens.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ClassicArchiveLinksUiTest {
    @get:Rule val rule = createComposeRule()
    @Test fun allArchiveSearchButtonsOpenTheirSelectedDestination() {
        var routed = ReaderUiState()
        rule.setContent {
            val controls = rememberClassicsHeaderState()
            controls.query = "xyz987 absent book title"
            MaterialTheme {
                ClassicsCatalogContent(emptyList(), emptyMap(), {}, {}, {},
                    onBrowse = { url, title, query -> routed = routed.withBookArchive(url, title, query) }, headerState = controls)
            }
        }
        for (site in FREE_BOOK_SITES) {
            rule.onNodeWithTag("classics_catalog").performScrollToNode(hasTestTag("classic_archive_${site.name}"))
            rule.onNodeWithTag("classic_archive_${site.name}").performClick()
            rule.runOnIdle {
                assertTrue(routed.showBookBrowser)
                assertEquals(site.url, routed.bookBrowserUrl)
                assertEquals(site.name, routed.bookBrowserTitle)
                assertEquals("xyz987 absent book title", routed.bookBrowserQuery)
            }
        }
    }
}
