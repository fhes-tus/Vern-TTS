package com.veritas.reader

import com.veritas.reader.ui.screens.VeritasHomeTab
import com.veritas.reader.ui.screens.libraryPagerNavPosition
import com.veritas.reader.ui.screens.libraryHeaderMoves
import com.veritas.reader.ui.screens.libraryPinnedHeaderProgress
import org.junit.Assert.*
import org.junit.Test

class HomeDestinationRoutingTest {
    @Test fun headersFollowThePageOnlyAcrossTheOuterLibraryTransitions() {
        for ((page, moves) in listOf(0 to true, 1 to false, 2 to true, 3 to false)) {
            assertEquals(moves, libraryHeaderMoves(page, page + 1))
            assertEquals(moves, libraryHeaderMoves(page + 1, page))
            if (moves) {
                assertNull(libraryPinnedHeaderProgress(page, page + .25f))
            } else {
                assertEquals(.25f, requireNotNull(libraryPinnedHeaderProgress(page, page + .25f)), 0f)
                assertEquals(.25f, requireNotNull(libraryPinnedHeaderProgress(page + 1, page + .25f)), 0f)
            }
        }
        assertFalse(libraryHeaderMoves(0, -1))
        assertFalse(libraryHeaderMoves(4, 5))
        assertNull(libraryPinnedHeaderProgress(3, 3f))
    }

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

    @Test fun navigationPillTracksPartialSwipesAndStaysOnLibraryAcrossItsSections() {
        val navPosition = ::libraryPagerNavPosition
        assertEquals(.25f, navPosition(.25f), 0f)
        assertEquals(1f, navPosition(1.5f), 0f)
        assertEquals(1.25f, navPosition(2.25f), 0f)
        assertEquals(2.75f, navPosition(3.75f), 0f)
        assertEquals(2.5f, navPosition(3.5f), 0f)
        assertEquals(0f, navPosition(-.5f), 0f)
        assertEquals(3f, navPosition(4.5f), 0f)
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
