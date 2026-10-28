package com.veritas.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.veritas.reader.ui.ReaderUiState
import com.veritas.reader.ui.screens.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReaderOrganizationUiTest {
    @get:Rule val rule = createComposeRule()

    private fun capture(name: String) {
        rule.waitForIdle()
        android.os.SystemClock.sleep(250)
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "ReaderOrganization/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try { file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    @Test fun navigationAndSwipeChoicesLiveInReaderSettingsAndPersist() {
        var settings by mutableStateOf(ReaderSettings())
        rule.setContent {
            MaterialTheme {
                ReaderSettingsDialog(settings, onDismiss = {}, onFontSizeChange = {}, onSpacingChange = {},
                    onThemeChange = {}, onThemePackChange = {}, onToggleVibrantHero = {}, onToggleAutoPlayQueue = {},
                    onReadingSettingsChange = { settings = it })
            }
        }
        rule.onNodeWithText("Display & theme").assertIsDisplayed()
        rule.onNodeWithText("Text sizing & spacing").assertDoesNotExist()
        rule.onNodeWithText("Document paper tone").assertDoesNotExist()
        rule.onNodeWithTag("reader-layout-scroll").performScrollTo().performClick()
        rule.onNodeWithTag("reader-turn-flip").performScrollTo().performClick()
        rule.onNodeWithTag("reader-pdf-swipe").performScrollTo().performClick()
        rule.runOnIdle {
            val saved = ReaderSettings.fromJson(settings.toJson())
            assertEquals("scroll", saved.readingLayout)
            assertEquals("flip", saved.pageTurnStyle)
            assertEquals("swipe", saved.pdfNavigationMode)
        }
        rule.onNodeWithTag("reader-layout-pages").performScrollTo().performClick()
        rule.runOnIdle { assertEquals("pages", settings.readingLayout) }
        capture("reader-navigation")
    }

    @Test fun readerToolsOverflowChoicesUpdateTheSamePreferences() {
        var settings by mutableStateOf(ReaderSettings())
        rule.setContent { MaterialTheme { Column { ReaderNavigationOverflowItems(settings) { settings = it } } } }
        rule.onNodeWithText("Reading layout: Page swipe").performClick()
        rule.onNodeWithText("Vertical scroll").performClick()
        rule.onNodeWithText("Reading layout: Vertical scroll").assertExists()
        rule.onNodeWithText("Swipe effect: Slide").performClick()
        rule.onNodeWithText("Page curl").performClick()
        rule.onNodeWithText("Swipe effect: Page curl").assertExists()
        rule.runOnIdle {
            val restored = ReaderSettings.fromJson(settings.toJson())
            assertEquals("scroll", restored.readingLayout)
            assertEquals("flip", restored.pageTurnStyle)
        }
    }

    @Test fun funkyCardsReachAddedTilesAndStudyMaterialsAndRespectSelectionAndContrast() {
        val originalFunky = VeritasThemeState.scatteredCards
        val originalTheme = VeritasThemeState.themeId
        val originalMotion = VeritasThemeState.reduceMotion
        var selectionMode by mutableStateOf(false)
        val book = SavedDocument("funky-grid-regression", "A reading for the grid", "fixture.txt", "TXT", 1, 1, 0, 1, 40, "")
        try {
            VeritasThemeState.scatteredCards = false
            VeritasThemeState.themeId = "light"
            VeritasThemeState.reduceMotion = true
            rule.setContent { MaterialTheme {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DocumentTileCard(book, false, selectionMode, false, {}, {}, {}, {}, {}, onToggleFavorite = {},
                        onRename = {}, onSetCollection = {}, onShowDetails = {}, modifier = Modifier.width(180.dp).testTag("added-funky-card"))
                    StudyHubOverview(ReaderUiState(), emptyList(), 0, StudyReviewSummary.from(emptyList(), 100),
                        {}, {}, {}, {}, {}, {}, {})
                }
            } }
            val straightTile = rule.onAllNodesWithText(book.title).onLast().fetchSemanticsNode().boundsInRoot.size
            rule.runOnIdle { VeritasThemeState.scatteredCards = true }
            val funkyTile = rule.onAllNodesWithText(book.title).onLast().fetchSemanticsNode().boundsInRoot.size
            assertNotEquals("Added grid must respond to Funky cards", straightTile, funkyTile)
            capture("funky-added")
            rule.runOnIdle { selectionMode = true }
            assertEquals(straightTile, rule.onAllNodesWithText(book.title).onLast().fetchSemanticsNode().boundsInRoot.size)
            rule.onNodeWithTag("study-material-Flashcards").performScrollTo()
            val funkyStudy = rule.onNodeWithTag("study-material-Flashcards").fetchSemanticsNode().boundsInRoot.size
            capture("funky-study")
            rule.runOnIdle { VeritasThemeState.scatteredCards = false }
            val straightStudy = rule.onNodeWithTag("study-material-Flashcards").fetchSemanticsNode().boundsInRoot.size
            assertNotEquals("Study material buttons must respond to Funky cards", straightStudy, funkyStudy)
            rule.runOnIdle { VeritasThemeState.scatteredCards = true; VeritasThemeState.themeId = "white_high_contrast" }
            assertEquals(straightStudy, rule.onNodeWithTag("study-material-Flashcards").fetchSemanticsNode().boundsInRoot.size)
        } finally {
            VeritasThemeState.scatteredCards = originalFunky
            VeritasThemeState.themeId = originalTheme
            VeritasThemeState.reduceMotion = originalMotion
        }
    }
}
