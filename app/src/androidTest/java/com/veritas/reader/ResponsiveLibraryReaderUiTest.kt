package com.veritas.reader

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.ReaderUiState
import com.veritas.reader.ui.screens.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class ResponsiveLibraryReaderUiTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var pxPerDp = 1f
    @Before fun isolated() { check(context.packageName.endsWith(".checks")) }

    @Composable private fun Frame(width: Dp, height: Dp, scale: Float = 1f, content: @Composable () -> Unit) {
        val base = LocalDensity.current
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val fit = minOf(1f, maxWidth / width, maxHeight / height)
            val density = Density(base.density * fit, scale)
            pxPerDp = density.density
            CompositionLocalProvider(LocalDensity provides density) {
                MaterialTheme { Box(Modifier.requiredSize(width, height).testTag("flow-fixture")) { content() } }
            }
        }
    }
    private fun rect(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
    private fun inside(inner: Rect, outer: Rect) {
        assertTrue("$inner must fit $outer", inner.left >= outer.left - 1 && inner.top >= outer.top - 1 && inner.right <= outer.right + 1 && inner.bottom <= outer.bottom + 1)
    }
    private fun capture(tag: String, name: String) {
        val file = File(context.getExternalFilesDir(null), "responsive-23/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun browserRestoresQueryAndSelectedMetadataAndImportsWithDoubleText() {
        val restore = StateRestorationTester(rule)
        var large by mutableStateOf(false)
        var imported = emptyList<VeritasBrowserFile>()
        val files = (1..10).map { VeritasBrowserFile(Uri.parse("content://responsive23/$it"), "Fixture $it", "application/pdf", 900L, 11L, "Test root", "Fixture $it.pdf", VeritasBrowserTab.PDF) }
        restore.setContent {
            Frame(320.dp, 568.dp, if (large) 2f else 1f) {
                FileBrowserDialog(emptyList(), files, null, false, false, null, true, false, "",
                    {}, {}, {}, {}, {}, {}, {}, {}, {}, { selected, _ -> imported = selected }, modifier = Modifier.width(320.dp).height(568.dp))
            }
        }
        rule.onNode(hasSetTextAction()).performTextInput("Fixture")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNodeWithTag("file-browser-files").performScrollToNode(hasText("Fixture 1"))
        rule.onNodeWithText("Fixture 1").performTouchInput { longClick() }
        restore.emulateSavedInstanceStateRestore()
        rule.onNode(hasSetTextAction()).assertTextContains("Fixture")
        rule.runOnIdle { large = true }
        rule.onNode(hasText(context.getString(R.string.ui_import)) and isEnabled()).performScrollTo().assertIsDisplayed().performClick()
        assertEquals(listOf(files.first()), imported)
        capture("file-browser-content", "browser-restored-large-text")
    }

    @Test fun narrowBrowserUsesItsOwnColumnsAndLastFileClearsBrowseButton() {
        val files = (1..9).map { VeritasBrowserFile(Uri.parse("content://responsive23/$it"), "Book ${it.toString().padStart(2, '0')}", "application/pdf", 20L, 0L, "", "", VeritasBrowserTab.PDF) }
        rule.setContent {
            // The window is logically wide while this dialog's content is only 320 dp.
            Frame(1000.dp, 800.dp) {
                FileBrowserDialog(emptyList(), files, null, false, false, null, true, false, "",
                    {}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, modifier = Modifier.width(320.dp).height(700.dp))
            }
        }
        rule.onNodeWithTag("file-browser-files").performScrollToNode(hasText("Book 01"))
        val first = rule.onNodeWithText("Book 01").fetchSemanticsNode().boundsInRoot
        if (context.resources.configuration.fontScale < 1.5f) {
            val third = rule.onNodeWithText("Book 03").fetchSemanticsNode().boundsInRoot
            assertTrue("A narrow content column needs two columns even in a wide window", third.top > first.bottom)
        } else {
            rule.onNodeWithText("Book 01").assertIsDisplayed()
        }
        rule.onNodeWithTag("file-browser-files").performScrollToNode(hasText("Book 09"))
        rule.onNodeWithTag("file-browser-files").performTouchInput { swipeUp() }
        rule.onNodeWithText("Book 09").assertIsDisplayed()
        assertTrue(rule.onNodeWithText("Book 09").fetchSemanticsNode().boundsInRoot.bottom < rect("file-browser-browse").top)
        capture("file-browser-content", "browser-local-columns-end")
    }

    @Test fun narrowNavigationKeepsFourTargetsAndWholeEnglishLabelsAtDoubleText() {
        var tab by mutableStateOf(VeritasHomeTab.HOME)
        rule.setContent { Frame(320.dp, 180.dp, 2f) { LibraryBottomNavBar(tab, true, { tab = it }) } }
        for (label in listOf("Home", "Library", "Study", "Notes")) {
            val item = rule.onNodeWithContentDescription(label).assertIsDisplayed()
            assertTrue(item.fetchSemanticsNode().boundsInRoot.width >= 48 * pxPerDp)
            item.performClick().assertIsSelected()
            rule.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { action ->
                val result = mutableListOf<androidx.compose.ui.text.TextLayoutResult>(); action(result)
                assertFalse("$label must not be ellipsized", result.single().isLineEllipsized(0))
                assertTrue(result.single().getLineRight(0) <= result.single().size.width + 1)
            }
        }
        capture("flow-fixture", "navigation-320-font2")
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun browserWindowChoosesReadableSystemBarIconsForBothThemes() {
        var dark by mutableStateOf(false)
        rule.setContent { MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
            FileBrowserDialog(emptyList(), emptyList(), null, false, false, null, true, false, "",
                {}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> })
        } }
        fun checkBars(light: Boolean) {
            rule.runOnIdle {
                fun find(view: android.view.View): android.view.Window? {
                    if (view is androidx.compose.ui.window.DialogWindowProvider) return view.window
                    if (view is android.view.ViewGroup) for (i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
                    return null
                }
                val window = android.view.inspector.WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::find)
                assertNotNull(window)
                val controller = androidx.core.view.WindowCompat.getInsetsController(window!!, window.decorView)
                assertEquals(light, controller.isAppearanceLightStatusBars)
                assertEquals(light, controller.isAppearanceLightNavigationBars)
            }
        }
        checkBars(true)
        rule.runOnIdle { dark = true }
        checkBars(false)
    }

    @Test fun landscapeLibraryKeepsFourProportionedCardsAndCollapsedPlayerInGutter() {
        val docs = (1..12).map { SavedDocument("responsive23-$it", "Fixture book $it", "missing-$it.txt", "TXT", 0, 0, 0, 12, 100, "Preview") }
        val previousId = PlaybackStateStore.activeDocumentId
        val prefs = context.getSharedPreferences("veritas_library_settings", 0)
        val oldMode = prefs.getString("library_view_mode", null)
        try {
            prefs.edit().putString("library_view_mode", LibraryViewMode.TILES.name).apply()
            PlaybackStateStore.activeDocumentId = docs.first().id
            rule.setContent { Frame(1000.dp, 850.dp) { LibraryFixture(docs) } }
            rule.onNodeWithContentDescription("Library").performClick()
            rule.onNodeWithTag("floater_collapse").performClick()
            val tiles = rule.onAllNodes(hasText("Fixture book", substring = true)).fetchSemanticsNodes().map { it.boundsInRoot }.filter { it.height > 0 }
            val top = tiles.minOf { it.top }
            assertEquals(4, tiles.count { kotlin.math.abs(it.top - top) < 2 })
            assertTrue(rect("library-content").width <= 760 * pxPerDp + 1)
            assertTrue("Collapsed player belongs outside the book column when there is room", rect("live_playback_floater").right <= rect("library-content").left)
            capture("flow-fixture", "library-four-columns-gutter")
        } finally {
            PlaybackStateStore.activeDocumentId = previousId
            if (oldMode == null) prefs.edit().remove("library_view_mode").apply() else prefs.edit().putString("library_view_mode", oldMode).apply()
        }
    }

    @Composable private fun LibraryFixture(docs: List<SavedDocument>) {
        LibraryScreen(uiState = ReaderUiState(documents = docs), onDraftTextChange = {}, onCreateFromDraft = {},
            onImportWebArticle = {}, onImportFile = {}, onImportImage = {}, onAdvancedPdfImport = {}, onOpenFileBrowser = {},
            onBrowseClassicArchive = { _, _, _ -> }, onOpenReadingLists = {}, onOpenReadingHistory = {}, onOpenDocument = {}, onOpenDocumentAt = { _, _ -> },
            onClearContinueDocument = {}, onDeleteDocument = {}, onToggleQueue = {}, onToggleFavorite = {}, onRenameDocument = {}, onSetCollection = {}, onShowDetails = {},
            isQueued = { false }, onPlayQueue = {}, onMoveQueueUp = {}, onMoveQueueDown = {}, onMoveQueueBy = { _, _ -> }, onRemoveFromQueue = {}, onClearQueue = {},
            onOpenSyncCenter = {}, onOpenSettingsHub = {}, onRefreshMainPage = {}, onBatchDeleteDocuments = {}, onBatchFavoriteDocuments = {}, onBatchQueueDocuments = {},
            onBatchSetCollectionDocuments = { _, _ -> }, onDeleteAnnotations = {}, onWriteGeneralNote = {}, onEditGeneralNote = {})
    }

    @Test fun collapsedReaderPlayerCentersAllControlsAtInfinixWidthAndTextScale() {
        var played = 0
        var samsung by mutableStateOf(false)
        rule.setContent { Frame(if (samsung) 411.dp else 360.dp, 640.dp, if (samsung) .8f else 1.15f) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
                PlayerPanel(false, false, "Ready", 1f, 1f, 18,
                    queueCount = 0, canGoPrevious = true, canGoNext = true,
                    onPrevious = {}, onPlayPause = { played++ }, onStop = {}, onNext = {},
                    onRateChange = {}, onPitchChange = {}, onFontSizeChange = {},
                    onOpenVoiceStudio = {}, onPlayQueue = {}, onOpenAudioMode = {},
                    voices = emptyList(), voiceSettings = VoiceSettings(), onVoiceSelected = {})
            }
        } }
        for (profile in listOf("infinix-360-font115", "samsung-411-font08")) {
            rule.runOnIdle { samsung = profile.startsWith("samsung") }
            val panel = rect("reader-player-panel")
            val groups = listOf("reader-player-leading", "reader-player-transport", "reader-player-trailing").map(::rect)
            groups.forEach { inside(it, panel) }
            assertEquals("Cover/speed and transport share a center line", groups[1].center.y, groups[0].center.y, 1f)
            assertEquals("Side actions and transport share a center line", groups[1].center.y, groups[2].center.y, 1f)
            assertEquals("Transport stays vertically centered in the capsule", panel.center.y, groups[1].center.y, 1f)
            assertTrue("All groups fit one row", groups.zipWithNext().all { (left, right) -> left.right <= right.left + 1 })
            capture("reader-player-panel", "reader-player-$profile")
        }
        rule.onNodeWithContentDescription("Play").performClick()
        assertEquals(1, played)
    }

    @Test fun readerModeTabsFillNormalPhoneWidthAndScrollOnlyWhenLabelsNeedIt() {
        var large by mutableStateOf(false)
        var selected by mutableStateOf(ReaderMode.TEXT)
        rule.setContent { Frame(if (large) 200.dp else 360.dp, 180.dp, if (large) 2f else 1.15f) {
            Box(Modifier.fillMaxWidth().padding(12.dp)) {
                ReaderModeToggle(selected, { selected = it }, hasCanvas = large,
                    modifier = Modifier.fillMaxWidth().testTag("mode-toggle"), showIcons = false)
            }
        } }
        val text = rect("reader-mode-TEXT")
        val listen = rect("reader-mode-LISTEN")
        assertEquals(text.width, listen.width, 1f)
        assertTrue("Normal phone tabs fill the selector", text.width > rect("mode-toggle").width * .45f)
        rule.onNodeWithTag("reader-mode-LISTEN").performClick()
        assertEquals(ReaderMode.LISTEN, selected)
        capture("mode-toggle", "reader-mode-normal-phone")
        rule.runOnIdle { large = true }
        rule.onNodeWithTag("reader-mode-ORIGINAL").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(ReaderMode.ORIGINAL, selected)
        capture("mode-toggle", "reader-mode-large-text")
    }

    @Test fun normalPhoneReaderPageKeepsItsOriginalBottomClearance() {
        var navigationInset = 0
        var samsung by mutableStateOf(false)
        val chunks = listOf("The page retains its original extent behind the floating player.")
        val document = ReaderDocument(null, "Page position", "TXT", chunks.single(), chunks, 1)
        rule.setContent { Frame(if (samsung) 411.dp else 360.dp, 800.dp, if (samsung) .8f else 1.15f) {
            navigationInset = WindowInsets.navigationBars.getBottom(LocalDensity.current)
            ReaderFixture(document, 0, {})
        } }
        for (profile in listOf("infinix", "samsung")) {
            rule.runOnIdle { samsung = profile == "samsung" }
            assertEquals("Normal phone paper keeps the original 64 dp clearance",
                rect("flow-fixture").bottom - navigationInset - 64 * pxPerDp, rect("reader-viewport").bottom, 2f)
            assertEquals("Normal phone header retains its original single row",
                rect("reader-header-document").center.y, rect("reader-header-search").center.y, 1f)
            capture("flow-fixture", "reader-restored-phone-$profile")
        }
    }

    @Test fun readerChromeMeasuresNarrowLargeTextAndRetainsItsSentenceAcrossResize() {
        var wide by mutableStateOf(false)
        var index by mutableIntStateOf(3)
        val chunks = (1..12).map { "Sentence $it stays in the reading document when its window changes." }
        val document = ReaderDocument(null, "A long responsive reader title", "TXT", chunks.joinToString("\n\n"), chunks, 1)
        rule.setContent { Frame(if (wide) 900.dp else 320.dp, if (wide) 500.dp else 568.dp, 2f) {
            ReaderFixture(document, index, { index++ })
        } }
        val frame = rect("flow-fixture")
        assertTrue("Toolbar must clear the prose", rect("reader-viewport").top >= rect("reader-top-controls").bottom)
        assertTrue(rect("reader-viewport").height > 70 * pxPerDp)
        inside(rect("reader-player-panel"), frame)
        rule.onNodeWithContentDescription(context.getString(R.string.ui_next)).performClick()
        assertEquals(4, index)
        capture("flow-fixture", "reader-320-font2")
        rule.runOnIdle { wide = true }
        assertEquals(4, index)
        inside(rect("reader-player-panel"), rect("flow-fixture"))
        assertTrue(rect("reader-viewport").top >= rect("reader-top-controls").bottom)
        capture("flow-fixture", "reader-wide-font2")
    }

    @Composable private fun ReaderFixture(document: ReaderDocument, index: Int, next: () -> Unit) {
        val settings = ReaderSettings(showGestureTips = false, collapsibleReaderBars = false)
        val state = ReaderScreenState(document, index, false, false, 1f, 1f, "", 0, false, emptyList(), 0,
            settings, VoiceSettings(), NarrationSettings(), AskAiSettings(), "", emptyList(), 0, emptyList(), false,
            0L, 0L, VeritasSleepTimerAction.PAUSE, 0, 0, emptyList(), ReaderMode.TEXT)
        ReaderScreen(state, rememberLazyListState(), false,
            onBackToLibrary = {}, onSentenceClick = {}, onSentenceDoubleTap = {}, onPlayPause = {}, onStop = {}, onPrevious = {}, onNext = next,
            onRateChange = {}, onPitchChange = {}, onFontSizeChange = {}, onReadingSettingsChange = {}, onToggleQueue = {}, onToggleBookmark = {},
            onEditNote = {}, onEditNotes = {}, onSearchQueryChange = {}, onNextSearchMatch = {}, onPreviousSearchMatch = {}, onOpenReaderSettings = {},
            onOpenPronunciationRules = {}, onOpenVoiceStudio = {}, onOpenNarrationStudio = {}, onOpenDocumentNotes = {}, onOpenCanvas = {},
            onOpenStudyTools = {}, onOpenTranslationTools = {}, onOpenSleepTimer = {}, onOpenReadingLists = {}, onOpenReadingHistory = {},
            onAskCurrentSection = {}, onSelectAskAiAssistant = { _, _ -> }, onOpenTextEditor = {}, onStartRecord = {}, onExportAudio = {},
            onCopySelection = {}, onShareSelection = {}, onGoogleSelection = {}, onTranslateSelection = {}, onAskAiSelection = {}, onEditSpeechSelection = {},
            onReadSelection = {}, onEditExtractedSelection = {}, onPlayQueue = {}, onVoiceSelected = {}, onReaderModeChange = {})
    }
}
