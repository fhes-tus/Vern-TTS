package com.veritas.reader

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.*
import com.veritas.reader.ui.screens.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Calendar

class MobileReadingRefinementUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun readingLayoutMigratesAndPersistsWithoutChangingPageTurns() {
        assertEquals("pages", ReaderSettings.fromJson(JSONObject()).readingLayout)
        assertEquals("pages", ReaderSettings.fromJson(JSONObject().put("readingLayout", "unknown")).readingLayout)
        val settings = ReaderSettings(readingLayout = "scroll", pageTurnStyle = "flip", fontSizeSp = 24)
        assertEquals(settings, ReaderSettings.fromJson(settings.toJson()))
    }

    @Test fun decksPrecedeCompactNavigationAndActionsStillWork() {
        val cards = listOf(FlashcardProgress("refine", front = "Question", back = "Answer"))
        var selected = ""
        rule.setContent { MaterialTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                StudyHubOverview(ReaderUiState(flashcards = cards), listOf(FlashcardSet("refine", "Reading ideas", cards)), 7,
                    StudyReviewSummary(cards, 0), { selected = it }, {}, {}, {}, {}, {}, {})
            }
        } }
        val decks = rule.onNodeWithText("Your decks").fetchSemanticsNode().boundsInRoot
        val material = rule.onNodeWithText("Your study material").fetchSemanticsNode().boundsInRoot
        assertTrue(decks.top < material.top)
        rule.onNodeWithText("Vocabulary").performScrollTo().performClick()
        assertEquals("Vocab", selected)
        capture("study-refined")
    }

    @Test fun listenResizeFullscreenAndNarrowLargeTextKeepControlsReachable() {
        var font by mutableIntStateOf(18)
        var narrow by mutableStateOf(false)
        var bookmarks = 0
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, if (narrow) 1.5f else 1f),
                LocalVeritasMotion provides VeritasMotionScheme(true)) {
                MaterialTheme {
                    Box(Modifier.width(if (narrow) 320.dp else 412.dp).fillMaxHeight()) {
                        AudioModeScreen("Reading and listening together", 1, 3, "Second sentence.", false,
                            onPlayPause = {}, onNext = {}, onPrevious = {}, onDismiss = {},
                            onToggleBookmark = { bookmarks++ }, documentChunks = listOf("First sentence.", "Second sentence.", "Third sentence."),
                            fontSizeSp = font, onFontSizeChange = { font = it })
                    }
                }
            }
        }
        rule.onNodeWithContentDescription("Add bookmark").performClick()
        assertEquals(1, bookmarks)
        rule.onNodeWithTag("listen-text-size").performClick()
        rule.onNodeWithContentDescription("Reading text size")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(24f) }
        rule.onNodeWithText("Done").performClick()
        assertEquals(24, font)
        rule.onNodeWithText("Read", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("listen-collapse-controls").assertDoesNotExist()
        rule.onNodeWithTag("listen-fullscreen").performClick()
        rule.onNodeWithTag("listen-exit-fullscreen").assertIsDisplayed().performClick()
        capture("listen-refined")
        rule.runOnIdle { narrow = true }
        assertEquals(rule.onNodeWithTag("listen-fullscreen").fetchSemanticsNode().boundsInRoot.top,
            rule.onNodeWithTag("listen-text-size").fetchSemanticsNode().boundsInRoot.top, 1f)
        rule.onNodeWithTag("listen-fullscreen").assertIsDisplayed().performClick()
        rule.onNodeWithTag("listen-exit-fullscreen").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Sleep timer").assertIsDisplayed()
        capture("listen-refined-large")
    }

    @Test fun calendarSelectionControlsSummaryPeriodAndWeekRemainsSeparate() {
        var keys: List<String> = emptyList()
        var label = ""
        rule.setContent { MaterialTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                CalendarHeatMap(emptySet(), summary = { selected, period ->
                    keys = selected; label = period
                    Text("Weekly summary stays current")
                    Text("Selected: $period")
                })
            }
        } }
        rule.waitForIdle()
        val initialLabel = label
        assertTrue(keys.isNotEmpty())
        rule.onNodeWithContentDescription("Previous Month").performScrollTo().performClick()
        rule.runOnIdle {
            assertNotEquals(initialLabel, label)
            assertEquals(1, keys.map { it.take(7) }.distinct().size)
        }
        rule.onNodeWithText("Year", useUnmergedTree = true).performScrollTo().performClick()
        rule.runOnIdle { assertEquals(Calendar.getInstance().get(Calendar.YEAR).toString(), label); assertTrue(keys.size > 28) }
        rule.onNodeWithText("Weekly summary stays current").assertExists()
    }

    @Test fun pdfSupportsAllTurnStylesAndDisablesTurnsWhileZoomed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "refine-pager.pdf")
        val pdf = PdfDocument()
        try {
            repeat(3) { index ->
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(300, 400, index + 1).create())
                page.canvas.drawText("Page ${index + 1}", 30f, 80f, android.graphics.Paint().apply { textSize = 24f })
                pdf.finishPage(page)
            }
            file.outputStream().use(pdf::writeTo)
        } finally { pdf.close() }
        var page by mutableIntStateOf(0)
        var style by mutableStateOf("slide")
        var zoomed by mutableStateOf(false)
        rule.setContent { MaterialTheme {
            Box(Modifier.width(320.dp).height(480.dp)) {
                OriginalPdfPager(Uri.fromFile(file), page, 3, null, ReaderSettings(pageTurnStyle = style), zoomed, null) { page = it }
            }
        } }
        try {
            for (turn in listOf("slide", "flip", "hinge", "none")) {
                rule.runOnIdle { style = turn; page = 0 }
                rule.waitForIdle()
                rule.onNodeWithTag("original-pdf-pager").performTouchInput { swipeLeft() }
                rule.waitUntil(10_000) { page == 1 }
                rule.onNodeWithTag("original-pdf-pager").performTouchInput { swipeRight() }
                rule.waitUntil(10_000) { page == 0 }
            }
            rule.runOnIdle { zoomed = true }
            rule.onNodeWithTag("original-pdf-pager").performTouchInput { swipeLeft() }
            rule.runOnIdle { assertEquals(0, page) }
            capture("original-pdf-refined")
        } finally { file.delete() }
    }


    @Test fun continuousReaderPreservesTablesSelectionSizingAndListenIsolation() {
        val raw = (1..3).joinToString("\n\n") { page ->
            if (page == 2) "[[VERITAS_PAGE:2]]\n\n" else
            "[[VERITAS_PAGE:$page]]\n" + (1..8).joinToString("\n\n") { "A thoughtful sentence on page $page, paragraph $it, with enough words to wrap naturally and retain native text selection." } +
                "\n\n| Topic | Value |\n| --- | --- |\n| Reading | 12 |\n| Listening | 8 |"
        }
        val model = ReaderTextIndex.build(raw, 3)
        val doc = ReaderDocument("scroll-fixture", "A thoughtful book", "TXT", raw, model.sentences.map { it.text }, 3)
        var index by mutableIntStateOf(0)
        var mode by mutableStateOf(ReaderMode.TEXT)
        var font by mutableIntStateOf(18)
        rule.setContent {
            CompositionLocalProvider(LocalVeritasMotion provides VeritasMotionScheme(true)) {
                MaterialTheme {
                    val state = ReaderScreenState(doc, index, false, false, 1f, 1f, "", 0, false, emptyList(), 0,
                        ReaderSettings(readingLayout = "scroll", fontSizeSp = font, reduceMotion = true, showGestureTips = false),
                        VoiceSettings(), NarrationSettings(), AskAiSettings(), "", emptyList(), 0, emptyList(), false,
                        0L, 0L, VeritasSleepTimerAction.PAUSE, 0, 0, emptyList(), mode)
                    ReaderScreen(state, androidx.compose.foundation.lazy.rememberLazyListState(), false,
                    onBackToLibrary = {},
                    onSentenceClick = { index = it },
                    onSentenceDoubleTap = { index = it },
                    onPlayPause = {},
                    onStop = {},
                    onPrevious = {},
                    onNext = {},
                    onRateChange = {},
                    onPitchChange = {},
                    onFontSizeChange = { font = it },
                    onToggleQueue = {},
                    onToggleBookmark = {},
                    onEditNote = {},
                    onEditNotes = {},
                    onSearchQueryChange = {},
                    onNextSearchMatch = {},
                    onPreviousSearchMatch = {},
                    onOpenReaderSettings = {},
                    onOpenPronunciationRules = {},
                    onOpenVoiceStudio = {},
                    onOpenNarrationStudio = {},
                    onOpenDocumentNotes = {},
                    onOpenCanvas = {},
                    onOpenStudyTools = {},
                    onOpenTranslationTools = {},
                    onOpenSleepTimer = {},
                    onOpenReadingLists = {},
                    onOpenReadingHistory = {},
                    onAskCurrentSection = {},
                    onSelectAskAiAssistant = { _, _ -> },
                    onOpenTextEditor = {},
                    onStartRecord = {},
                    onExportAudio = {},
                    onCopySelection = {},
                    onShareSelection = {},
                    onGoogleSelection = {},
                    onTranslateSelection = {},
                    onAskAiSelection = {},
                    onEditSpeechSelection = {},
                    onReadSelection = {},
                    onEditExtractedSelection = {},
                    onPlayQueue = {},
                    onVoiceSelected = {},
                    onReaderModeChange = { mode = it },
                    )
                }
            }
        }
        rule.onNodeWithTag("reader-continuous-scroll").assertIsDisplayed()
        rule.onNodeWithTag("reader-prose-1-0").assertExists()
        var native: android.widget.TextView? = null
        rule.runOnIdle {
            fun find(view: android.view.View): android.widget.TextView? {
                if (view is android.widget.TextView && view.text.contains("thoughtful sentence")) return view
                if (view is android.view.ViewGroup) for (child in 0 until view.childCount) find(view.getChildAt(child))?.let { return it }
                return null
            }
            native = android.view.inspector.WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::find)
            assertNotNull("Scrolling prose retains a native selectable text view", native)
            val view = native!!
            val start = view.text.indexOf("thoughtful")
            view.requestFocus()
            android.text.Selection.setSelection(view.text as android.text.Spannable, start, start + 10)
            font = 24
        }
        rule.runOnIdle {
            assertTrue(native!!.hasSelection())
            assertEquals("thoughtful", native!!.text.subSequence(native!!.selectionStart, native!!.selectionEnd).toString())
            android.text.Selection.removeSelection(native!!.text as android.text.Spannable)
        }
        rule.onNodeWithTag("reader-continuous-scroll").performTouchInput { swipeUp() }
        rule.onNodeWithTag("reader-continuous-scroll").assertIsDisplayed()
        val pageThree = model.sentences.indexOfFirst { it.pageNumber == 3 }
        rule.runOnIdle { index = pageThree }
        rule.waitForIdle()
        rule.onNodeWithTag("reader-prose-3-0").assertExists()
        rule.onNodeWithContentDescription("Page position").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,
            androidx.compose.ui.semantics.ProgressBarRangeInfo(3f, 1f..3f, 1)))
        rule.onNodeWithContentDescription("Page position")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(2f) }
        rule.onNodeWithText("No readable text was extracted for this page.").assertIsDisplayed()
        capture("continuous-extracted-refined")
        rule.runOnIdle { mode = ReaderMode.LISTEN }
        rule.onNodeWithTag("listen-page").assertIsDisplayed()
        rule.onNodeWithTag("reader-continuous-scroll").assertDoesNotExist()
        rule.onNodeWithContentDescription("More options").performClick()
        rule.onNodeWithText("Full screen reading").assertExists()
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "mobile-refinement/$name.png").apply { parentFile!!.mkdirs() }
            .outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
