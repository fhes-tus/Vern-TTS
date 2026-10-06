package com.veritas.reader

import com.veritas.reader.ui.screens.VeritasHomeTab
import org.junit.Assert.*
import org.junit.Test

class HomeDestinationRoutingTest {
    @Test fun librarySectionsSwipeBeforeStudyWithoutChangingMainTabIdentities() {
        val pages = com.veritas.reader.ui.screens.libraryPagerPages
        assertEquals(listOf("HOME", "LIBRARY", "LIBRARY", "STUDY", "NOTES"), pages.map { it.tab.name })
        assertEquals(com.veritas.reader.ui.screens.LibrarySection.MY_LIBRARY, pages[1].section)
        assertEquals(com.veritas.reader.ui.screens.LibrarySection.CLASSICS, pages[2].section)
        assertEquals(3, com.veritas.reader.ui.screens.libraryPagerIndex(VeritasHomeTab.STUDY))
        assertEquals(4, com.veritas.reader.ui.screens.libraryPagerIndex(VeritasHomeTab.NOTES))
    }
    @Test fun swipeOrderMatchesApprovedNavigationWithoutChangingIdentities() {
        assertEquals(listOf("HOME", "LIBRARY", "STUDY", "NOTES"),
            VeritasHomeTab.orderedDestinations.map { it.name })
        assertEquals(2, VeritasHomeTab.STUDY.pagerIndex)
        assertEquals(3, VeritasHomeTab.NOTES.pagerIndex)
        VeritasHomeTab.entries.forEach {
            assertEquals(it, VeritasHomeTab.fromPagerIndex(it.pagerIndex))
            assertEquals(it, VeritasHomeTab.valueOf(it.name))
        }
        assertNull(VeritasHomeTab.fromPagerIndex(-1))
        assertNull(VeritasHomeTab.fromPagerIndex(4))
    }

    @Test fun noteAndStudyWidgetsKeepTheirSemanticDestinations() {
        listOf("show_notes", "new_note", "new_checklist_note", "new_reminder_note").forEach {
            assertEquals(VeritasHomeTab.NOTES, VeritasHomeTab.fromWidgetAction(it))
        }
        listOf("show_study_dashboard", "show_flashcards").forEach {
            assertEquals(VeritasHomeTab.STUDY, VeritasHomeTab.fromWidgetAction(it))
        }
        assertEquals(VeritasHomeTab.LIBRARY, VeritasHomeTab.fromWidgetAction("open_library"))
        assertNull(VeritasHomeTab.fromWidgetAction(null))
        assertNull(VeritasHomeTab.fromWidgetAction("unrecognized"))
    }

    @Test fun explicitSourceLinkWinsOverWidgetAndClearingItDoesNotReplayWidget() {
        assertEquals(VeritasHomeTab.STUDY,
            VeritasHomeTab.requestedDestination(VeritasHomeTab.STUDY, "show_notes", null))
        assertNull(VeritasHomeTab.requestedDestination(null, "show_notes", "show_notes"))
        assertEquals(VeritasHomeTab.LIBRARY,
            VeritasHomeTab.requestedDestination(null, "open_library", "show_notes"))
        assertEquals(VeritasHomeTab.NOTES,
            VeritasHomeTab.requestedDestination(VeritasHomeTab.NOTES, "show_notes", "show_notes"))
    }
}
