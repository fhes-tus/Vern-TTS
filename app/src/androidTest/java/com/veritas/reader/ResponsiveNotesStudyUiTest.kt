package com.veritas.reader

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.ReaderUiState
import com.veritas.reader.ui.NotesSettings
import com.veritas.reader.ui.screens.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ResponsiveNotesStudyUiTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also { check(it.packageName.endsWith(".checks")) }
    @Composable private fun Frame(width: Dp, height: Dp, font: Float = 1f, content: @Composable () -> Unit) {
        val base = LocalDensity.current
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val fit = minOf(1f, maxWidth / width, maxHeight / height)
            CompositionLocalProvider(LocalDensity provides Density(base.density * fit, font)) {
                MaterialTheme { Box(Modifier.requiredSize(width, height).testTag("adaptive-frame")) { content() } }
            }
        }
    }
    private fun capture(name: String) {
        val file = File(context.getExternalFilesDir(null), "responsive-final/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { rule.onNodeWithTag("adaptive-frame").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private val notes = (0..11).map { GeneralNote("n$it", "Note $it", List(if (it == 0) 10 else 2) { "A useful line of note $it" }.joinToString("\n"), 1000L - it) }
    private val cards = (0..7).map { FlashcardProgress("c$it", front = "Question $it", back = "Answer $it", setId = "s$it", setName = "Deck $it") }

    private fun openFlashcardCollection() {
        val list = rule.onNode(hasTestTag("study-layout-1") or hasTestTag("study-layout-2") or hasTestTag("study-layout-3") or hasTestTag("study-layout-4"))
        val action = rule.onNodeWithTag("study-material-Flashcards").performScrollTo()
        // ScrollTo accounts for the list viewport; the floating navigation sits over it.
        val bottom = action.fetchSemanticsNode().boundsInRoot.bottom
        val navigationTop = rule.onNodeWithContentDescription("Study").fetchSemanticsNode().boundsInRoot.top
        val clearance = (bottom - navigationTop + 16f).coerceAtLeast(0f)
        if (clearance > 0f) list.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, clearance) }
        action.assertIsDisplayed().performClick()
        list.performScrollToNode(hasTestTag("study-deck-s0"))
    }

    @Test fun notesGainMasonryColumnsInLandscapeAndRetainTheirOpenAction() {
        var profile by mutableIntStateOf(0); var opened = ""
        val state = ReaderUiState(generalNotes = notes, notesSettings = NotesSettings(isGridView = true, previewLines = 8))
        rule.setContent { Frame(if (profile == 1) 891.dp else if (profile == 2) 320.dp else 411.dp,
            if (profile == 1) 411.dp else 800.dp, if (profile == 2) 2f else 1f) { LibraryFixture(state, { opened = it.id }) } }
        rule.onNodeWithContentDescription("Notes").performClick()
        rule.onNodeWithTag("notes-layout-2").assertExists()
        val first = rule.onNodeWithTag("note_card_n0").fetchSemanticsNode().boundsInRoot
        val second = rule.onNodeWithTag("note_card_n1").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 2f)
        assertTrue("Note cards keep independent heights", first.height > second.height)
        capture("notes-phone")
        rule.runOnIdle { profile = 1 }
        rule.onNodeWithTag("notes-layout-3").assertExists()
        rule.onNodeWithTag("notes-list").performScrollToNode(hasTestTag("note_card_n0"))
        val row = (0..2).map { rule.onNodeWithTag("note_card_n$it").fetchSemanticsNode().boundsInRoot }
        assertTrue(row.zipWithNext().all { (a, b) -> a.right <= b.left + 1 && kotlin.math.abs(a.top - b.top) < 2 })
        rule.onNodeWithTag("note_card_n0").performClick()
        assertEquals("n0", opened)
        capture("notes-landscape")
        rule.runOnIdle { profile = 2 }
        rule.onNodeWithTag("notes-layout-1").assertExists()
        capture("notes-narrow-font2")
    }

    @Test fun studyUsesWidePanelsThenReadableSingleColumnsWithLargeText() {
        var narrow by mutableStateOf(false); var selected = ""
        val sets = cards.map { FlashcardSet(it.setId, it.setName, listOf(it)) }
        rule.setContent { Frame(if (narrow) 320.dp else 1040.dp, if (narrow) 568.dp else 800.dp, if (narrow) 2f else 1f) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                StudyHubOverview(ReaderUiState(flashcards = cards), sets, 4, StudyReviewSummary(cards, 0), { selected = it }, {}, {}, {}, {}, {}, {})
            }
        } }
        val review = rule.onNodeWithTag("study-review-panel").fetchSemanticsNode().boundsInRoot
        val decks = rule.onNodeWithTag("study-decks-panel").fetchSemanticsNode().boundsInRoot
        assertTrue("Wide Study panels use the available horizontal space", review.right <= decks.left + 1 && kotlin.math.abs(review.top - decks.top) < 2)
        capture("study-expanded")
        rule.runOnIdle { narrow = true }
        rule.onNodeWithTag("study-material-Flashcards").performScrollTo().assertIsDisplayed().performClick()
        assertEquals("Flashcards", selected)
        val flashcards = rule.onNodeWithTag("study-material-Flashcards").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("study-material-Quizzes").performScrollTo()
        val quizzes = rule.onNodeWithTag("study-material-Quizzes").fetchSemanticsNode().boundsInRoot
        assertTrue("Large text does not squeeze material actions into narrow cells", quizzes.width >= flashcards.width - 2)
        capture("study-narrow-font2")
    }

    @Test fun studyDeckCollectionChangesColumnsWithoutLosingItsSelectedSection() {
        var wide by mutableStateOf(false)
        rule.setContent { Frame(if (wide) 1040.dp else 411.dp, 800.dp) { LibraryFixture(ReaderUiState(flashcards = cards), {}) } }
        rule.onNodeWithContentDescription("Study").performClick()
        openFlashcardCollection()
        rule.onNodeWithTag("study-deck-s0").assertExists()
        rule.runOnIdle { wide = true }
        rule.onNodeWithTag("study-layout-4").assertExists()
        val first = rule.onNodeWithTag("study-deck-s0").fetchSemanticsNode().boundsInRoot
        val fourth = rule.onNodeWithTag("study-deck-s3").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, fourth.top, 2f)
        assertTrue(first.right < fourth.left)
        capture("study-decks-expanded")
    }

    @Test fun widgetMenuLastActionRemainsReachableInShortLargeTextWindow() {
        var action: String? = null
        rule.setContent { Frame(320.dp, 260.dp, 2f) { WidgetMenuDialog({}, { action = it }) } }
        rule.onNodeWithText(context.getString(R.string.l10n_import_documents)).performScrollTo().assertIsDisplayed().performClick()
        assertEquals(MainActivity.ACTION_IMPORT_DOCUMENTS, action)
        capture("widget-menu-short")
    }

    @Test fun outlineHeaderScrollsToPageJumpWithoutRemovingTheOutlineViewport() {
        var page: Int? = null
        rule.setContent { Frame(320.dp, 360.dp, 2f) {
            ReaderOutlineScreen("Table of contents", "A long document title", listOf(SmartOutlineEntry(0, "Chapter one", "", true, pageNumber = 1)),
                0, 8, { target, _ -> page = target }, {}, reduceMotion = true)
        } }
        val label = context.getString(R.string.l10n_dynamic_145, "8")
        rule.onNodeWithText(label).performScrollTo().performTextInput("4")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.onNodeWithText(context.getString(R.string.ui_go)).performScrollTo().assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(4, page) }
    }

    @Test fun notesEditorKeepsSaveReachableWithTheActualKeyboardOpen() {
        var view: android.view.View? = null; var saved = ""
        rule.setContent {
            view = androidx.compose.ui.platform.LocalView.current
            val activity = androidx.compose.ui.platform.LocalContext.current.vernActivity()!!
            DisposableEffect(activity) {
                val previousMode = activity.window.attributes.softInputMode
                // Match MainActivity's manifest policy instead of the test activity's adjustPan default.
                activity.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                onDispose { activity.window.setSoftInputMode(previousMode) }
            }
            MaterialTheme {
            GeneralNotesEditor(GeneralNote("keyboard-fixture", "Keyboard note", "Keyboard fixture text", 123),
                onSave = { _, body, _, _, _, _, _, _, _, _, _ -> saved = body }, onDelete = {}, onDismiss = {})
        } }
        try {
            rule.onNodeWithText("Keyboard fixture text").performTouchInput { click() }
            rule.waitUntil(8_000) {
                var visible = false
                rule.runOnIdle { visible = view?.let { androidx.core.view.ViewCompat.getRootWindowInsets(it)?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) } == true }
                visible
            }
            rule.onNodeWithText("Keyboard fixture text").performTextReplacement("A draft entered with the keyboard open")
            val profile = InstrumentationRegistry.getArguments().getString("vernProfile", "phone")
            val file = File(context.getExternalFilesDir(null), "responsive-final/$profile-keyboard-open.png").apply { parentFile!!.mkdirs() }
            file.outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            rule.onNodeWithContentDescription("Save Note").assertIsDisplayed().performClick()
            rule.runOnIdle { assertTrue(saved.contains("keyboard open")) }
        } finally { androidx.test.espresso.Espresso.closeSoftKeyboard() }
    }

    @Test fun landscapeAddSheetShowsAllSixOptionsInTwoColumnsAndKeepsTheirActions() {
        var narrow by mutableStateOf(false)
        val selected = mutableListOf<ImportOption>()
        rule.setContent { Frame(if (narrow) 320.dp else 640.dp, if (narrow) 568.dp else 340.dp) {
            ImportSheetMenu({ selected += it }, {})
        } }
        rule.onNodeWithTag("add-options-2").assertExists()
        val titles = listOf(R.string.ui_file_browser, R.string.ui_paste_text_e6f8612, R.string.l10n_from_web,
            R.string.ui_scan_ocr, R.string.l10n_browse_phone_folders, R.string.ui_write_note_96dcbf1)
        titles.forEach { rule.onNodeWithText(context.getString(it)).assertIsDisplayed().performClick() }
        assertEquals(listOf(ImportOption.BROWSE, ImportOption.PASTE, ImportOption.WEB, ImportOption.SCAN, ImportOption.FILE, ImportOption.WRITE_NOTE), selected)
        capture("add-sheet-landscape-two-columns")
        rule.runOnIdle { narrow = true }
        rule.onNodeWithTag("add-options-1").assertExists()
        rule.onNodeWithText(context.getString(R.string.ui_write_note_96dcbf1)).performScrollTo().assertIsDisplayed()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun actualWindowKeepsNotesStudyAndAddActionsUsable() {
        val profile = InstrumentationRegistry.getArguments().getString("vernProfile", "phone")
        val expectedColumns = when (profile) { "landscape" -> 3; "expanded" -> 4; "short-font2" -> 1; else -> 2 }
        val previousTheme = VeritasThemeState.themeId
        val previousPack = VeritasThemeState.themePackId
        val previousMotion = VeritasThemeState.reduceMotion
        var showAdd by mutableStateOf(false)
        var noteOpened = ""
        try {
            VeritasThemeState.themeId = "midnight_dark"
            VeritasThemeState.themePackId = "liquid_glass"
            VeritasThemeState.reduceMotion = true
            rule.setContent { VeritasTheme {
                Box(Modifier.fillMaxSize().testTag("live-window")) {
                    LibraryFixture(ReaderUiState(generalNotes = notes, flashcards = cards,
                        notesSettings = NotesSettings(isGridView = true, previewLines = 8)), { noteOpened = it.id })
                }
                if (showAdd) ModalBottomSheet(onDismissRequest = { showAdd = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    shape = VeritasPackStyle.sheetShape(), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    ImportSheetMenu({}, { showAdd = false }, Modifier.testTag("actual-add-sheet"))
                }
            } }
            fun captureWindow(tag: String, name: String) {
                val file = File(context.getExternalFilesDir(null), "responsive-final/$profile-$name.png").apply { parentFile!!.mkdirs() }
                file.outputStream().use { rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            rule.onNodeWithContentDescription("Notes").performClick()
            rule.onNodeWithTag("notes-layout-$expectedColumns").assertExists()
            rule.onNodeWithTag("notes-list").performScrollToNode(hasTestTag("note_card_n0"))
            rule.onNodeWithTag("note_card_n0").performClick()
            assertEquals("n0", noteOpened)
            captureWindow("live-window", "notes")
            rule.onNodeWithContentDescription("Study").performClick()
            val title = rule.onNodeWithTag("study-title").fetchSemanticsNode().boundsInRoot
            val streak = rule.onNodeWithTag("study-streak").fetchSemanticsNode().boundsInRoot
            assertTrue("Study title and streak remain separate", title.right <= streak.left + 1 || title.bottom <= streak.top + 1)
            rule.onNodeWithTag("study-review-panel").assertExists()
            captureWindow("live-window", "study")
            openFlashcardCollection()
            rule.onNodeWithTag("study-deck-s0").assertExists()
            captureWindow("live-window", "study-decks")
            rule.runOnIdle { showAdd = true }
            for (title in listOf(R.string.ui_file_browser, R.string.ui_paste_text_e6f8612, R.string.l10n_from_web,
                R.string.ui_scan_ocr, R.string.l10n_browse_phone_folders, R.string.ui_write_note_96dcbf1)) {
                val node = rule.onNodeWithText(context.getString(title))
                if (profile == "landscape" || profile == "expanded") {
                    node.assertIsDisplayed()
                    val label = node.getUnclippedBoundsInRoot()
                    val viewport = rule.onNodeWithTag("add-options-2").getUnclippedBoundsInRoot()
                    assertTrue("Every Add title fits fully in the visible two-column menu", label.top >= viewport.top && label.bottom <= viewport.bottom)
                } else node.performScrollTo().assertIsDisplayed()
            }
            captureWindow("actual-add-sheet", "add-sheet")
        } finally {
            rule.runOnIdle { showAdd = false; VeritasThemeState.themeId = previousTheme; VeritasThemeState.themePackId = previousPack; VeritasThemeState.reduceMotion = previousMotion }
        }
    }

    @Composable private fun LibraryFixture(state: ReaderUiState, edit: (GeneralNote) -> Unit) {
        LibraryScreen(uiState = state, onDraftTextChange = {}, onCreateFromDraft = {}, onImportWebArticle = {}, onImportFile = {},
            onImportImage = {}, onAdvancedPdfImport = {}, onOpenFileBrowser = {}, onBrowseClassicArchive = { _, _, _ -> },
            onOpenReadingLists = {}, onOpenReadingHistory = {}, onOpenDocument = {}, onOpenDocumentAt = { _, _ -> }, onClearContinueDocument = {},
            onDeleteDocument = {}, onToggleQueue = {}, onToggleFavorite = {}, onRenameDocument = {}, onSetCollection = {}, onShowDetails = {},
            isQueued = { false }, onPlayQueue = {}, onMoveQueueUp = {}, onMoveQueueDown = {}, onMoveQueueBy = { _, _ -> }, onRemoveFromQueue = {}, onClearQueue = {},
            onOpenSyncCenter = {}, onOpenSettingsHub = {}, onRefreshMainPage = {}, onBatchDeleteDocuments = {}, onBatchFavoriteDocuments = {}, onBatchQueueDocuments = {},
            onBatchSetCollectionDocuments = { _, _ -> }, onDeleteAnnotations = {}, onWriteGeneralNote = {}, onEditGeneralNote = edit)
    }
}
