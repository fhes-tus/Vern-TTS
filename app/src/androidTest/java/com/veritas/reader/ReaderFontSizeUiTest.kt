package com.veritas.reader

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.screens.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ReaderFontSizeUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun realReaderHostRoutesListenResizeToItsOwnSavedPreference() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val repository = DocumentRepository(context)
        val previous = repository.loadReaderSettings()
        var settings by mutableStateOf(previous.copy(fontSizeSp = 16, listenFontSizeSp = 18, showGestureTips = false))
        var mode by mutableStateOf(ReaderMode.LISTEN)
        val text = "A sentence to read and listen to."
        val document = ReaderDocument(null, "Separate reading sizes", "TXT", text, listOf(text), 1)
        try {
            rule.setContent { MaterialTheme {
                val state = ReaderScreenState(document, 0, false, false, 1f, 1f, "", 0, false, emptyList(), 0,
                    settings, VoiceSettings(), NarrationSettings(), AskAiSettings(), "", emptyList(), 0, emptyList(), false,
                    0L, 0L, VeritasSleepTimerAction.PAUSE, 0, 0, emptyList(), mode)
                ReaderScreen(state, rememberLazyListState(), false,
                    onBackToLibrary = {}, onSentenceClick = {}, onSentenceDoubleTap = {}, onPlayPause = {}, onStop = {},
                    onPrevious = {}, onNext = {}, onRateChange = {}, onPitchChange = {},
                    onFontSizeChange = { settings = settings.copy(fontSizeSp = it); repository.saveReaderSettings(settings) },
                    onReadingSettingsChange = { settings = it; repository.saveReaderSettings(it) },
                    onToggleQueue = {}, onToggleBookmark = {}, onEditNote = {}, onEditNotes = {}, onSearchQueryChange = {},
                    onNextSearchMatch = {}, onPreviousSearchMatch = {}, onOpenReaderSettings = {}, onOpenPronunciationRules = {},
                    onOpenVoiceStudio = {}, onOpenNarrationStudio = {}, onOpenDocumentNotes = {}, onOpenCanvas = {},
                    onOpenStudyTools = {}, onOpenTranslationTools = {}, onOpenSleepTimer = {}, onOpenReadingLists = {},
                    onOpenReadingHistory = {}, onAskCurrentSection = {}, onSelectAskAiAssistant = { _, _ -> },
                    onOpenTextEditor = {}, onStartRecord = {}, onExportAudio = {}, onCopySelection = {}, onShareSelection = {},
                    onGoogleSelection = {}, onTranslateSelection = {}, onAskAiSelection = {}, onEditSpeechSelection = {},
                    onReadSelection = {}, onEditExtractedSelection = {}, onPlayQueue = {}, onVoiceSelected = {},
                    onReaderModeChange = { mode = it })
            } }
            rule.onNodeWithTag("listen-text-size").performClick()
            rule.onNodeWithContentDescription("Reading text size").performSemanticsAction(SemanticsActions.SetProgress) { it(24f) }
            rule.onNodeWithText("24 sp").assertIsDisplayed()
            rule.onNodeWithText("Done").performClick()
            rule.runOnIdle {
                assertEquals(16, settings.fontSizeSp)
                assertEquals(24, repository.loadReaderSettings().listenFontSizeSp)
            }
            rule.onNodeWithContentDescription("Back").performClick()
            rule.runOnIdle {
                assertEquals(ReaderMode.TEXT, mode)
                settings = settings.copy(fontSizeSp = 20)
                repository.saveReaderSettings(settings)
            }
            rule.onNodeWithTag("listen-page").assertDoesNotExist()
            rule.runOnIdle { settings = repository.loadReaderSettings(); mode = ReaderMode.LISTEN }
            rule.onNodeWithTag("listen-text-size").performClick()
            rule.onNodeWithText("24 sp").assertIsDisplayed()
            rule.runOnIdle { assertEquals(20, settings.fontSizeSp) }
        } finally { repository.saveReaderSettings(previous) }
    }
}
