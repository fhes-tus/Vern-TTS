package com.veritas.reader

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.ReaderUiState
import com.veritas.reader.ui.screens.LibraryScreen
import org.junit.Rule
import org.junit.Test

class LibraryBackHierarchyUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun backReturnsToStudyAndNotesRootsBeforeHome() {
        lateinit var activity: ComponentActivity
        rule.setContent {
            activity = LocalContext.current as ComponentActivity
            MaterialTheme {
                LibraryScreen(
                    uiState = ReaderUiState(), onDraftTextChange = {}, onCreateFromDraft = {},
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
        fun back() {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }
            rule.waitForIdle()
        }
        rule.onNodeWithContentDescription("Study").performClick()
        // Only the section chip is visible before scrolling: the Overview card is lower.
        listOf("Flashcards", "Quizzes", "Bookmarks", "Booknotes", "History").forEach { section ->
            rule.onAllNodesWithText(section).onFirst().performScrollTo().performClick()
            back()
            rule.onNodeWithText("Your study material").assertExists()
            rule.onNodeWithContentDescription("Study").assertIsSelected()
        }
        back()
        rule.onNodeWithContentDescription("Home").assertIsSelected()
        rule.onNodeWithContentDescription("Notes").performClick()
        rule.onNodeWithContentDescription("Notes options").performClick()
        rule.onNodeWithText("Show trash").performClick()
        rule.onNodeWithText("Trash is empty").assertExists()
        back()
        rule.onNodeWithText("Trash is empty").assertDoesNotExist()
        rule.onNodeWithContentDescription("Notes").assertIsSelected()
        back()
        rule.onNodeWithContentDescription("Home").assertIsSelected()
    }
}
