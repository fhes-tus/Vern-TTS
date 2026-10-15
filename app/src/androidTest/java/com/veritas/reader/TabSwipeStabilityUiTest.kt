package com.veritas.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.veritas.reader.ui.ReaderUiState
import com.veritas.reader.ui.screens.LibraryScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After

class TabSwipeStabilityUiTest {
    @get:Rule val rule = createComposeRule()
    private var previousAudioId: String? = null
    @Before fun rememberPlaybackIdentity() { previousAudioId = PlaybackStateStore.activeDocumentId }
    @After fun restorePlaybackIdentity() { PlaybackStateStore.activeDocumentId = previousAudioId }

    @Test fun headersPillsAndNavigationFollowTheDragTogetherAndInterruptedNavigationRecovers() {
        val docs = (1..60).map { SavedDocument("swipe-$it", "Reading $it", "missing-$it.txt", "TXT", 0, 0, 0, 40, 500, "Reading preview") }
        val notes = (1..40).map { GeneralNote("swipe-note-$it", "Note $it", "Thoughts from today's reading.", 0) }
        PlaybackStateStore.activeDocumentId = docs.first().id
        rule.setContent {
            MaterialTheme {
                LibraryScreen(
                    uiState = ReaderUiState(documents = docs, generalNotes = notes), onDraftTextChange = {}, onCreateFromDraft = {},
                    onImportWebArticle = {}, onImportFile = {}, onImportImage = {}, onAdvancedPdfImport = {}, onOpenFileBrowser = {},
                    onBrowseClassicArchive = { _, _, _ -> },
                    onOpenReadingLists = {}, onOpenReadingHistory = {}, onOpenDocument = {}, onOpenDocumentAt = { _, _ -> },
                    onClearContinueDocument = {}, onDeleteDocument = {}, onToggleQueue = {}, onToggleFavorite = {}, onRenameDocument = {},
                    onSetCollection = {}, onShowDetails = {}, isQueued = { false }, onPlayQueue = {}, onMoveQueueUp = {}, onMoveQueueDown = {},
                    onMoveQueueBy = { _, _ -> }, onRemoveFromQueue = {}, onClearQueue = {}, onOpenSyncCenter = {}, onOpenSettingsHub = {},
                    onRefreshMainPage = {}, onBatchDeleteDocuments = {}, onBatchFavoriteDocuments = {}, onBatchQueueDocuments = {},
                    onBatchSetCollectionDocuments = { _, _ -> }, onDeleteAnnotations = {}, onWriteGeneralNote = {}, onEditGeneralNote = {}
                )
            }
        }
        fun dragToNext(forward: Boolean, expected: String) {
            val pager = rule.onNodeWithTag("home_pager")
            // Stay below genre chips and above the horizontal book shelves.
            pager.performTouchInput {
                val y = (110f * androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density)
                    .coerceAtMost(height * .4f)
                val startX = if (forward) width * .88f else width * .12f
                val endX = if (forward) width * .12f else width * .88f
                down(androidx.compose.ui.geometry.Offset(startX, y))
                moveTo(androidx.compose.ui.geometry.Offset(endX, y), delayMillis = 400)
            }
            // Assert the new destination BEFORE lifting the finger: a settled-
            // page-only header can pass endpoint tests while visibly lagging.
            rule.onNodeWithContentDescription(expected).assertIsSelected()
            val header = when (expected) {
                "Library" -> "My Library"
                "Study" -> "Study Hub"
                "Notes" -> "Notes & Annotations"
                else -> "ern"
            }
            rule.onNodeWithText(header).assertIsDisplayed()
            if (expected == "Notes") {
                rule.onNodeWithContentDescription("Write note").assertExists()
                rule.onNodeWithTag("live_playback_floater").assertDoesNotExist()
            } else {
                rule.onNodeWithTag("live_playback_floater").assertIsDisplayed()
            }
            pager.performTouchInput { up() }
            rule.waitForIdle()
            rule.onNodeWithContentDescription(expected).assertIsSelected()
        }
        repeat(2) {
            listOf("Library", "Library", "Study", "Notes").forEach { dragToNext(true, it) }
            listOf("Study", "Library", "Library", "Home").forEach { dragToNext(false, it) }
        }
        val pager = rule.onNodeWithTag("home_pager")
        pager.performTouchInput {
            down(androidx.compose.ui.geometry.Offset(width * .85f, 12f))
            moveTo(androidx.compose.ui.geometry.Offset(width * .65f, 12f), delayMillis = 200)
            moveTo(androidx.compose.ui.geometry.Offset(width * .85f, 12f), delayMillis = 200)
            advanceEventTime(100)
            up()
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Home").assertIsSelected()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription("Notes").performClick()
        rule.mainClock.advanceTimeBy(80)
        rule.onNodeWithContentDescription("Library").performClick()
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Library").assertIsSelected()
        rule.onNodeWithText("Added").assertIsSelected()
    }
}
