package com.veritas.reader.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import com.veritas.reader.ui.VeritasMotion
import com.veritas.reader.veritasGlassBackdrop
import androidx.compose.foundation.layout.statusBarsPadding
import com.veritas.reader.recordVeritasBackdrop
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.zIndex
import com.veritas.reader.ui.NotesSettings
import com.veritas.reader.AiResultParser
import com.veritas.reader.Flashcard
import com.veritas.reader.FlashcardProgress
import com.veritas.reader.FlashcardSet
import com.veritas.reader.GeneralNote
import com.veritas.reader.LibraryViewMode
import com.veritas.reader.PlaybackStateStore
import com.veritas.reader.QuizQuestion
import com.veritas.reader.QuizSet
import com.veritas.reader.ReaderAnnotation
import com.veritas.reader.ReaderSettings
import com.veritas.reader.ResolvedVeritasFeature
import com.veritas.reader.SavedDocument
import com.veritas.reader.VeritasFeatureContext
import com.veritas.reader.VeritasFeatureId
import com.veritas.reader.VeritasFeatureRegistry
import com.veritas.reader.VeritasFeatureSurface
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.parseVocabularyNoteContent
import com.veritas.reader.VocabularyEntry
import com.veritas.reader.ui.OnboardingController
import com.veritas.reader.ui.OnboardingStep
import com.veritas.reader.ui.ReaderUiState
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.abs

internal data class MarkedDocument(
    val document: SavedDocument,
    val annotations: List<ReaderAnnotation>,
    val documentNote: String,
    val updatedAt: Long
)

private data class LibraryTabTransition(val outgoing: Int, val incoming: Int)

internal val LibraryTabFade = tween<Float>(durationMillis = 155, easing = LinearOutSlowInEasing)

internal enum class ImportSheetMode {
    MENU,
    WEB,
    PASTE
}

internal enum class ImportOption {
    FILE,
    WEB,
    PASTE,
    SCAN,
    BROWSE,
    CLASSICS,
    WRITE_NOTE
}


@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UNUSED_VALUE", "ASSIGNED_VALUE_IS_NEVER_READ")
@Composable
fun LibraryScreen(
    uiState: ReaderUiState,
    onDraftTextChange: (String) -> Unit,
    onCreateFromDraft: () -> Unit,
    widgetAction: String? = null,
    onRefreshInsights: () -> Unit = {},
    onImportWebArticle: (String) -> Unit,
    onImportFile: () -> Unit,
    onImportImage: () -> Unit,
    onAdvancedPdfImport: () -> Unit,
    onOpenFileBrowser: () -> Unit,
    onOpenClassicsCatalog: () -> Unit = {},
    onDownloadClassicBook: (ClassicBookEntry) -> Unit = {},
    onCancelClassicBook: (ClassicBookEntry) -> Unit = {},
    onBrowseClassicArchive: (String, String, String) -> Unit,
    onOpenReadingLists: () -> Unit,
    onOpenReadingHistory: () -> Unit,
    onOpenDocument: (SavedDocument) -> Unit,
    onOpenDocumentAt: (SavedDocument, Int) -> Unit,
    onSearchLibraryContent: (String) -> Unit = {},
    onClearContinueDocument: (SavedDocument) -> Unit,
    onPlayPauseContinue: (SavedDocument) -> Unit = {},
    onDeleteDocument: (SavedDocument) -> Unit,
    onToggleQueue: (SavedDocument) -> Unit,
    onToggleFavorite: (SavedDocument) -> Unit,
    onRenameDocument: (SavedDocument) -> Unit,
    onSetCollection: (SavedDocument) -> Unit,
    onShowDetails: (SavedDocument) -> Unit,
    isQueued: (SavedDocument) -> Boolean,
    onPlayQueue: () -> Unit,
    onMoveQueueUp: (SavedDocument) -> Unit,
    onMoveQueueDown: (SavedDocument) -> Unit,
    /** Moves a queued reading by a relative number of places (drag-to-reorder). */
    onMoveQueueBy: (SavedDocument, Int) -> Unit,
    onRemoveFromQueue: (SavedDocument) -> Unit,
    onClearQueue: () -> Unit,
    onReorderDocuments: (List<SavedDocument>) -> Unit = {},
    onOpenSyncCenter: () -> Unit,
    onOpenSettingsHub: () -> Unit,
    onRefreshMainPage: () -> Unit,
    onBatchDeleteDocuments: (Set<String>) -> Unit,
    onBatchFavoriteDocuments: (Set<String>) -> Unit,
    onBatchQueueDocuments: (Set<String>) -> Unit,
    onBatchSetCollectionDocuments: (Set<String>, String) -> Unit,
    onDeleteAnnotations: (Set<String>) -> Unit,
    onClearTargetHomeTab: () -> Unit = {},
    onWriteGeneralNote: () -> Unit,
    onEditGeneralNote: (GeneralNote) -> Unit,
    onCreateReadingList: (String, String?) -> Unit = { _, _ -> },
    onAddDocumentToReadingList: (String, String) -> Unit = { _, _ -> },
    onRemoveDocumentFromReadingList: (String, String) -> Unit = { _, _ -> },
    onRemoveVocabularyWord: (String, String) -> Unit = { _, _ -> },
    onRefreshVocabularyMeaning: (String, VocabularyEntry) -> Unit = { _, _ -> },
    onClearReadingHistory: () -> Unit = {},
    onRemoveReadingHistoryEntry: (String) -> Unit = {},
    onToggleGeneralNotePin: (String) -> Unit = {},
    onChangeGeneralNoteColor: (String, String?) -> Unit = { _, _ -> },
    onDeleteGeneralNote: (String) -> Unit = {},
    onRateFlashcardRecall: (String, String, (Boolean) -> Unit) -> Unit = { _, _, finish -> finish(false) },
    onDeleteFlashcard: (String) -> Unit = {},
    onImportFlashcards: (String, List<Flashcard>) -> Unit = { _, _ -> },
    onRenameFlashcardSet: (String, String) -> Unit = { _, _ -> },
    onDeleteFlashcardSet: (String) -> Unit = {},
    onSaveReaderSettings: (ReaderSettings) -> Unit = {},
    onSaveQuiz: (QuizSet) -> Unit = {},
    onDeleteQuiz: (String) -> Unit = {},
    onRecordQuizScore: (String, Int) -> Unit = { _, _ -> },
    onGenerateInAppQuiz: (SavedDocument, String?) -> Unit = { _, _ -> },
    onOpenAiStudyTools: () -> Unit = {},
    onExportStudyGuidePdf: (SavedDocument) -> Unit = {},
    onCancelAiStudyGeneration: () -> Unit = {},
    firstChapter: (@Composable (openImport: () -> Unit) -> Unit)? = null,
    onDismissOpeningDocument: () -> Unit = {},
    onOpenNotesSettings: () -> Unit = {},
    showNotesSettings: Boolean = false,
    onDismissNotesSettings: () -> Unit = {},
    notesSettings: NotesSettings = uiState.notesSettings,
    noteCollectionActions: NoteCollectionActions? = null,
    onSaveNotesSettings: (NotesSettings) -> Unit = {},
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null
) {

    val documents = uiState.documents
    val queuedDocuments = uiState.queuedDocuments
    val draftText = uiState.draftText
    var librarySection by rememberSaveable { mutableStateOf(LibrarySection.MY_LIBRARY) }
    val sectionStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var libraryQuery by rememberSaveable { mutableStateOf("") }
    var showNotesTrash by rememberSaveable { mutableStateOf(false) }
    var selectedNotesNotebookId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCreateNotesNotebook by remember { mutableStateOf(false) }
    var showManageNotesNotebooks by remember { mutableStateOf(false) }
    var noteSearchQuery by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var playerCollapsed by rememberSaveable { mutableStateOf(false) }
    val playerExpansion by animateFloatAsState(if (playerCollapsed) 0f else 1f,
        animationSpec = com.veritas.reader.ui.VeritasMotion.spatial(), label = "playerExpansion")
    val classicsHeaderState = rememberClassicsHeaderState()
    val headerFocus = androidx.compose.ui.platform.LocalFocusManager.current
    var statusFilter by rememberSaveable { mutableStateOf("All") }
    var sourceFilter by rememberSaveable { mutableStateOf("All") }
    var collectionFilter by rememberSaveable { mutableStateOf("All") }
    var readingListFilter by rememberSaveable { mutableStateOf("All") }
    LaunchedEffect(uiState.readingListCatalog, readingListFilter) {
        if (readingListFilter != "All" && uiState.readingListCatalog.list(readingListFilter)?.archived != false) {
            readingListFilter = "All"
        }
    }
    var manageListsDocument by remember { mutableStateOf<SavedDocument?>(null) }
    var sortMode by rememberSaveable { mutableStateOf("Updated") }
    var showQueue by remember { mutableStateOf(false) }
    var viewerCards by remember { mutableStateOf<List<FlashcardProgress>?>(null) }
    var viewerSetName by remember { mutableStateOf("") }
    var showPasteFlashcards by remember { mutableStateOf(false) }
    var showPasteQuiz by remember { mutableStateOf(false) }
    var showQuizLabMetrics by remember { mutableStateOf(false) }
    var quizToDelete by remember { mutableStateOf<QuizSet?>(null) }

    var activePlayingQuiz by remember { mutableStateOf<QuizSet?>(null) }
    var showGeminiApiKeyDialog by remember { mutableStateOf(false) }
    var detectedClipboardFlashcards by remember { mutableStateOf<List<Flashcard>>(emptyList()) }
    var detectedClipboardQuiz by remember { mutableStateOf<List<QuizQuestion>>(emptyList()) }
    var dismissedClipboardSnippet by remember { mutableStateOf<String?>(null) }
    val clipboard = androidx.compose.ui.platform.LocalClipboard.current
    var renameSetTarget by remember { mutableStateOf<FlashcardSet?>(null) }
    var showContentSearchResults by remember { mutableStateOf(false) }
    var localShowNotesSettings by remember { mutableStateOf(false) }


    var selectedDocumentIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val cards = uiState.flashcards
    val flashcardSets = remember(cards) {
        cards.groupBy { it.setId }.map { (setId, setCards) ->
            FlashcardSet(
                setId = setId,
                name = setCards.firstOrNull { it.setName.isNotBlank() }?.setName ?: "Untitled set",
                cards = setCards
            )
        }
    }
    var confirmBatchDelete by remember { mutableStateOf(false) }
    var showBatchCollectionDialog by remember { mutableStateOf(false) }
    var batchCollectionDraft by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val libraryPrefs = remember { context.getSharedPreferences("veritas_library_settings", Context.MODE_PRIVATE) }
    var isHomeGridView by rememberSaveable { mutableStateOf(libraryPrefs.getBoolean("is_home_grid_view", true)) }
    var libraryViewMode by rememberSaveable {
        mutableStateOf(
            runCatching {
                LibraryViewMode.valueOf(
                    libraryPrefs.getString("library_view_mode", LibraryViewMode.TILES.name) ?: LibraryViewMode.TILES.name
                )
            }.getOrDefault(LibraryViewMode.TILES)
        )
    }
    val initialTab = uiState.targetHomeTab
        ?: VeritasHomeTab.fromWidgetAction(widgetAction)
        ?: VeritasHomeTab.HOME
    var selectedHomeTab by rememberSaveable { mutableStateOf(initialTab) }
    var handledWidgetAction by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(selectedHomeTab) {
        if (selectedHomeTab == VeritasHomeTab.STUDY) {
            val clip = clipboard.getClipEntry()?.clipData?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
            if (clip.isNotBlank() && clip.length in 15..30000 && clip.take(60) != dismissedClipboardSnippet) {
                val (quiz, cards) = withContext(Dispatchers.Default) {
                    val parsedQuiz = AiResultParser.parseQuiz(clip)
                    parsedQuiz to if (parsedQuiz.isEmpty()) AiResultParser.parseFlashcards(clip) else emptyList()
                }
                if (quiz.isNotEmpty()) {
                    detectedClipboardQuiz = quiz
                    detectedClipboardFlashcards = emptyList()
                } else {
                    if (cards.size >= 2) {
                        detectedClipboardFlashcards = cards
                        detectedClipboardQuiz = emptyList()
                    }
                }
            }
        }
    }
    // the hamburger lives on the HOME tab, the FAB and document cards on LIBRARY.
    OnboardingController.activeStep != null
    var showHomeSidebar by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    var importSheetMode by remember { mutableStateOf(ImportSheetMode.MENU) }
    var showReadingStatsHome by remember { mutableStateOf(false) }
    LaunchedEffect(showReadingStatsHome) {
        if (showReadingStatsHome) onRefreshInsights()
    }
    // During the insights onboarding step the dashboard opens itself so the tour can
    // describe it in place; it closes again when the tour moves on.
    val activeOnboardingStep = OnboardingController.activeStep
    LaunchedEffect(activeOnboardingStep) {
        if (activeOnboardingStep == OnboardingStep.INSIGHTS_PAGE_SPOTLIGHT) {
            showReadingStatsHome = true
        } else if (activeOnboardingStep != null) {
            showReadingStatsHome = false
        }
    }
    var selectedAnnotationKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmAnnotationDelete by remember { mutableStateOf(false) }
    var annotationFilter by rememberSaveable { mutableStateOf("Overview") }
    var confirmDeleteVocabDocId by remember { mutableStateOf<String?>(null) }
    var selectedGeneralNoteTag by remember { mutableStateOf("All") }
    val libraryListState = rememberLazyListState()
    val notesListState = rememberLazyListState()
    val libraryFeatures = remember(documents.size, queuedDocuments.size) {
        VeritasFeatureRegistry.resolve(
            VeritasFeatureSurface.LIBRARY_OVERFLOW,
            VeritasFeatureContext(
                hasSavedDocument = documents.isNotEmpty(),
                queueCount = queuedDocuments.size
            )
        ).associateBy { it.definition.id }
    }

    fun libraryFeature(id: VeritasFeatureId): ResolvedVeritasFeature =
        libraryFeatures.requireResolvedFeature(id)

    val continueDocument by remember(
        documents,
        uiState.dismissedHeroDocId,
        uiState.dismissedHeroDocIds,
        uiState.isHeroContinueDismissed
    ) {
        derivedStateOf {
            if (uiState.isHeroContinueDismissed) {
                null
            } else {
                documents
                    .filter { doc ->
                        doc.chunkCount > 1 &&
                        doc.currentIndex in 1 until doc.chunkCount &&
                        doc.id != uiState.dismissedHeroDocId &&
                        doc.id !in uiState.dismissedHeroDocIds
                    }
                    .maxByOrNull { it.updatedAt }
            }
        }
    }
    val selectionMode = selectedDocumentIds.isNotEmpty()
    val currentStreak = uiState.readerTrackerSnapshot.currentStreak
    uiState.readerTrackerSnapshot.longestStreak

    val coroutineScope = rememberCoroutineScope()
    // Old saved four-page offsets must not override the restored semantic destination.
    val pagerState = key("library_sections_pager") {
        rememberPagerState(initialPage = libraryPagerIndex(selectedHomeTab, librarySection)) { libraryPagerPages.size }
    }
    val homeListState = rememberLazyListState()
    val studyListState = rememberLazyListState()

    val reduceMotion = VeritasMotion.scheme.reduceMotion
    val chromeEffects = VeritasMotion.effectsFast<Float>()
    val tabFade = remember { mutableStateOf<LibraryTabTransition?>(null) }
    val tabArrival = remember { mutableFloatStateOf(1f) }
    val navigationJob = remember { mutableStateOf<Job?>(null) }
    var retainedPageRadius by remember { mutableIntStateOf(1) }
    LaunchedEffect(pagerState) {
        for (radius in 2..libraryPagerPages.lastIndex) {
            delay(120)
            snapshotFlow { !pagerState.isScrollInProgress && tabFade.value == null }.first { it }
            retainedPageRadius = radius
        }
    }
    LaunchedEffect(pagerState) {
        pagerState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                navigationJob.value?.cancel()
                tabFade.value = null
            }
        }
    }
    val navigateToPage: (VeritasHomeTab, LibrarySection, Boolean) -> Unit = remember(pagerState, coroutineScope, reduceMotion) {
        { tab, section, fade ->
            val page = libraryPagerIndex(tab, section)
            val previous = pagerState.currentPage
            val changingPage = previous != page
            if (changingPage || pagerState.isScrollInProgress || pagerState.currentPageOffsetFraction != 0f) {
                navigationJob.value?.cancel()
                val transition = if (changingPage && fade && !reduceMotion &&
                    !pagerState.isScrollInProgress
                ) LibraryTabTransition(previous, page) else null
                tabFade.value = transition
                val startAlpha = .12f
                tabArrival.floatValue = startAlpha
                pagerState.requestScrollToPage(page)
                navigationJob.value = if (transition != null) coroutineScope.launch {
                    animate(startAlpha, 1f, animationSpec = LibraryTabFade) { alpha, _ ->
                        tabArrival.floatValue = alpha
                    }
                    tabFade.value = null
                } else null
            }
        }
    }
    val navigateToTab: (VeritasHomeTab) -> Unit = remember(navigateToPage) {
        { tab -> navigateToPage(tab, LibrarySection.MY_LIBRARY, true) }
    }
    val currentDestination = libraryPagerPages[pagerState.currentPage]
    val backNavTab = currentDestination.tab
    val isNotHomeTab = backNavTab != VeritasHomeTab.HOME
    BackHandler(enabled = selectionMode || selectedAnnotationKeys.isNotEmpty() || showHomeSidebar || showImportSheet || isNotHomeTab) {
        when {
            selectionMode -> selectedDocumentIds = emptySet()
            selectedAnnotationKeys.isNotEmpty() -> selectedAnnotationKeys = emptySet()
            showHomeSidebar -> showHomeSidebar = false
            showImportSheet -> showImportSheet = false
            backNavTab == VeritasHomeTab.STUDY && annotationFilter != "Overview" -> {
                annotationFilter = "Overview"
                studyListState.requestScrollToItem(0)
            }
            backNavTab == VeritasHomeTab.NOTES && (showNotesTrash || selectedNotesNotebookId != null ||
                selectedGeneralNoteTag != "All" || noteSearchQuery.isNotBlank()) -> {
                showNotesTrash = false
                selectedNotesNotebookId = null
                selectedGeneralNoteTag = "All"
                noteSearchQuery = ""
                notesListState.requestScrollToItem(0)
            }
            isNotHomeTab -> navigateToTab(VeritasHomeTab.HOME)
        }
    }

    // Explicit destinations take priority; an old widget intent must not replay on return.
    LaunchedEffect(uiState.targetHomeTab, uiState.targetLibrarySection, widgetAction) {
        val explicitTarget = uiState.targetHomeTab
        val target = VeritasHomeTab.requestedDestination(
            explicitTarget, widgetAction, handledWidgetAction
        )
        handledWidgetAction = widgetAction
        if (target != null) {
            navigateToPage(target, uiState.targetLibrarySection ?: librarySection, false)
        }
        if (explicitTarget != null) onClearTargetHomeTab()
    }

    // Handle onboarding tour steps:
    LaunchedEffect(activeOnboardingStep) {
        when (activeOnboardingStep) {
            OnboardingStep.CLASSICS_SPOTLIGHT -> navigateToPage(VeritasHomeTab.LIBRARY, LibrarySection.CLASSICS, true)
            OnboardingStep.CHECKLIST_SPOTLIGHT,
            OnboardingStep.INSIGHTS_SPOTLIGHT,
            OnboardingStep.INSIGHTS_PAGE_SPOTLIGHT -> navigateToTab(VeritasHomeTab.HOME)
            OnboardingStep.NOTES_TAB_SPOTLIGHT -> navigateToTab(VeritasHomeTab.NOTES)
            OnboardingStep.STUDY_TAB_SPOTLIGHT -> navigateToTab(VeritasHomeTab.STUDY)
            null -> Unit
            else -> navigateToPage(VeritasHomeTab.LIBRARY, librarySection, true)
        }
    }

    val activeNavTab = currentDestination.tab
    val activeLibrarySection = currentDestination.section ?: librarySection
    // Card entrances belong to the initial screen, not every crossing of a page midpoint.
    val entrancePage = remember(pagerState) { pagerState.settledPage }
    val cardEntrance = remember(pagerState) { android.os.SystemClock.uptimeMillis() }
    BackHandler(enabled = (activeNavTab == VeritasHomeTab.LIBRARY || activeNavTab == VeritasHomeTab.NOTES) && searchExpanded) {
        searchExpanded = false
        headerFocus.clearFocus()
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val destination = libraryPagerPages[page]
            destination.section?.let { librarySection = it }
            if (selectedHomeTab != destination.tab) {
                searchExpanded = false
                headerFocus.clearFocus()
                selectedHomeTab = destination.tab
            }
            if (destination.tab == VeritasHomeTab.HOME) onRefreshInsights()
        }
    }

    // Keep document progress fresh: the PlaybackService writes currentIndex straight to the
    // repository while speaking, so the uiState document list goes stale during playback.
    // Refresh on entering this screen (initial false) and every time playback pauses/stops,
    // so the hero card and library rows show real progress without reopening the app.
    LaunchedEffect(PlaybackStateStore.isPlaying) {
        if (!PlaybackStateStore.isPlaying) {
            onRefreshMainPage()
        }
    }



    val vocabDocs = remember(uiState.generalNotes, uiState.documents, uiState.documentTitles) {
        uiState.generalNotes
            .filter { it.title.startsWith("__vocab__") }
            .mapNotNull { note ->
                val docId = note.title.removePrefix("__vocab__")
                val doc = uiState.documents.firstOrNull { it.id == docId } ?: SavedDocument(
                    id = docId,
                    title = uiState.documentTitles[docId] ?: "Deleted Book",
                    fileName = "",
                    sourceLabel = "Deleted",
                    createdAt = 0,
                    updatedAt = 0,
                    currentIndex = 0,
                    chunkCount = 0,
                    charCount = 0,
                    preview = ""
                )
                val entries = parseVocabularyNoteContent(note.content)
                if (entries.isNotEmpty()) {
                    Triple(doc, note, entries)
                } else {
                    null
                }
            }
    }

    val welcomeName = uiState.userName.trim().ifBlank { "Reader" }

    LibraryDialogsAndSheetsHost(
        activePlayingQuiz = activePlayingQuiz,
        onDismissActivePlayingQuiz = { activePlayingQuiz = null },
        onRecordQuizScore = { quizId, score -> onRecordQuizScore(quizId, score) },
        showGeminiApiKeyDialog = showGeminiApiKeyDialog,
        onDismissGeminiApiKeyDialog = { showGeminiApiKeyDialog = false },
        showQuizLabMetrics = showQuizLabMetrics,
        onDismissQuizLabMetrics = { showQuizLabMetrics = false },
        onPlayQuiz = { activePlayingQuiz = it },
        onShowPasteQuiz = { showPasteQuiz = true },
        quizToDelete = quizToDelete,
        onDismissQuizToDelete = { quizToDelete = null },
        onDeleteQuiz = { onDeleteQuiz(it) },
        showPasteQuiz = showPasteQuiz,
        onDismissPasteQuiz = { showPasteQuiz = false },
        onSaveQuiz = onSaveQuiz,
        showContentSearchResults = showContentSearchResults,
        onDismissContentSearchResults = { showContentSearchResults = false },
        showPasteFlashcards = showPasteFlashcards,
        onDismissPasteFlashcards = { showPasteFlashcards = false },
        onImportFlashcards = onImportFlashcards,
        onOpenDocumentAt = onOpenDocumentAt,
        showImportSheet = showImportSheet,
        onDismissImportSheet = { showImportSheet = false; importSheetMode = ImportSheetMode.MENU },
        importSheetMode = importSheetMode,
        onImportSheetModeChange = { importSheetMode = it },
        draftText = draftText,
        onDraftTextChange = onDraftTextChange,
        onCreateFromDraft = onCreateFromDraft,
        onImportWebArticle = onImportWebArticle,
        onImportFile = onImportFile,
        onImportImage = onImportImage,
        onOpenFileBrowser = onOpenFileBrowser,
        onOpenClassicsCatalog = onOpenClassicsCatalog,
        onWriteGeneralNote = onWriteGeneralNote,
        manageListsDocument = manageListsDocument,
        onDismissManageLists = { manageListsDocument = null },
        readingListCatalog = uiState.readingListCatalog,
        onCreateReadingList = onCreateReadingList,
        onAddDocumentToReadingList = onAddDocumentToReadingList,
        onRemoveDocumentFromReadingList = onRemoveDocumentFromReadingList,
        showQueue = showQueue,
        onDismissQueue = { showQueue = false },
        queuedDocuments = queuedDocuments,
        onMoveQueueBy = onMoveQueueBy,
        onRemoveFromQueue = onRemoveFromQueue,
        onClearQueue = onClearQueue,
        onPlayQueue = onPlayQueue,
        confirmBatchDelete = confirmBatchDelete,
        onDismissConfirmBatchDelete = { confirmBatchDelete = false },
        selectedDocumentIds = selectedDocumentIds,
        onClearSelectedDocuments = { selectedDocumentIds = emptySet() },
        onBatchDeleteDocuments = onBatchDeleteDocuments,
        confirmAnnotationDelete = confirmAnnotationDelete,
        onDismissConfirmAnnotationDelete = { confirmAnnotationDelete = false },
        selectedAnnotationKeys = selectedAnnotationKeys,
        onClearSelectedAnnotations = { selectedAnnotationKeys = emptySet() },
        onDeleteAnnotations = onDeleteAnnotations,
        showHomeSidebar = showHomeSidebar,
        onDismissHomeSidebar = { showHomeSidebar = false },
        welcomeName = welcomeName,
        readerTrackerSnapshot = uiState.readerTrackerSnapshot,
        onNavigateToTab = navigateToTab,
        showReadingStatsHome = showReadingStatsHome,
        onDismissReadingStatsHome = { showReadingStatsHome = false },
        onOpenStats = { showReadingStatsHome = true },
        onOpenSettingsHub = onOpenSettingsHub,
        onOpenReadingLists = onOpenReadingLists,
        onOpenReadingHistory = onOpenReadingHistory,
        onOpenSyncCenter = onOpenSyncCenter,
        documents = documents,
        documentReadingTimes = uiState.documentReadingTimes,
        readerSettings = uiState.readerSettings,
        onSaveReaderSettings = onSaveReaderSettings,
        viewerCards = viewerCards,
        viewerSetName = viewerSetName,
        onDismissViewerCards = { viewerCards = null },
        onRateFlashcardRecall = onRateFlashcardRecall,
        onDeleteFlashcard = onDeleteFlashcard,
        renameSetTarget = renameSetTarget,
        onDismissRenameSetTarget = { renameSetTarget = null },
        onRenameFlashcardSet = onRenameFlashcardSet,
        showBatchCollectionDialog = showBatchCollectionDialog,
        onDismissBatchCollectionDialog = { showBatchCollectionDialog = false },
        batchCollectionDraft = batchCollectionDraft,
        onBatchCollectionDraftChange = { batchCollectionDraft = it },
        onBatchSetCollectionDocuments = onBatchSetCollectionDocuments,
        confirmDeleteVocabDocId = confirmDeleteVocabDocId,
        onDismissConfirmDeleteVocabDocId = { confirmDeleteVocabDocId = null },
        vocabDocs = vocabDocs,
        onRemoveVocabularyWord = onRemoveVocabularyWord,
        isOpeningDocument = uiState.isOpeningDocument,
        onDismissOpeningDocument = onDismissOpeningDocument,
        uiState = uiState
    )

    if (showNotesSettings || localShowNotesSettings) {
        NotesSettingsSheet(
            notesSettings = notesSettings,
            onSaveNotesSettings = onSaveNotesSettings,
            onDismiss = {
                localShowNotesSettings = false
                onDismissNotesSettings()
            }
        )
    }

    val isTransitionSettled = animatedVisibilityScope?.transition?.let {
        it.currentState == EnterExitState.Visible && it.targetState == EnterExitState.Visible
    } ?: true
    val glassEnabled = VeritasPackStyle.glassChromeEnabled() && com.veritas.reader.isDeviceGlassCapable()
    val glassBackdrop = if (glassEnabled) com.veritas.reader.rememberVeritasLayerBackdrop() else null
    val effectiveBackdrop = if (isTransitionSettled) glassBackdrop else null
    val playbackId = PlaybackStateStore.activeDocumentId
    val playingDocument = remember(documents, playbackId) { documents.firstOrNull { it.id == playbackId } }
    val density = LocalDensity.current
    val fallbackPlayerHeight = VeritasPackStyle.libraryPlayerHeight + 8.dp
    var measuredPlayerHeight by remember(density, fallbackPlayerHeight) { mutableStateOf(fallbackPlayerHeight) }
    val playerClearance = if (playingDocument != null) measuredPlayerHeight else 0.dp
    val pagerPosition = remember(pagerState) {
        { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    }
    val navPosition = remember(pagerPosition) { { libraryPagerNavPosition(pagerPosition()) } }
    val addedPage = libraryPagerIndex(VeritasHomeTab.LIBRARY)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    com.veritas.reader.VeritasBackdropProvider(effectiveBackdrop) {
    Box(modifier = Modifier.fillMaxSize().background(VeritasPackStyle.backgroundBrush(MaterialTheme.colorScheme))) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            // The container is transparent (we draw our own background brush), so set the
            // content colour explicitly to the theme's onSurface. Otherwise uncoloured Text
            // (section headers, empty states) falls back to the default black LocalContentColor
            // and is unreadable in dark mode on the notes/bookmarks/history/vocabulary tabs.
            contentColor = MaterialTheme.colorScheme.onSurface,
            floatingActionButton = {
                val actionTab = when {
                    activeNavTab == VeritasHomeTab.NOTES -> VeritasHomeTab.NOTES
                    activeNavTab == VeritasHomeTab.LIBRARY && activeLibrarySection == LibrarySection.MY_LIBRARY -> VeritasHomeTab.LIBRARY
                    else -> null
                }
                androidx.compose.animation.AnimatedContent(
                    targetState = actionTab,
                    transitionSpec = { (fadeIn(LibraryTabFade) togetherWith fadeOut(LibraryTabFade)).using(null) },
                    contentAlignment = Alignment.BottomEnd,
                    label = "fabTabContentTransition"
                ) { currentTab ->
                    val actionSemantics = if (currentTab == actionTab) Modifier else Modifier.clearAndSetSemantics {}
                    if (currentTab == VeritasHomeTab.NOTES) {
                        ExtendedFloatingActionButton(
                            text = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_note)) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Outlined.EditNote,
                                    contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_write_note_96dcbf1),
                                    modifier = Modifier.size(22.dp)
                                )
                            },
                            onClick = { if (actionTab == currentTab) onWriteGeneralNote() },
                            shape = VeritasPackStyle.chipShape(),
                            elevation = if (glassEnabled) FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp) else FloatingActionButtonDefaults.elevation(),
                            containerColor = if (glassEnabled) Color.Transparent else MaterialTheme.colorScheme.primary,
                            contentColor = if (glassEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                            modifier = actionSemantics.veritasGlassBackdrop(VeritasPackStyle.chipShape(), glassEnabled, surfaceOpacity = .36f)
                                .then(if (glassEnabled) Modifier else Modifier.border(VeritasPackStyle.cardBorder(MaterialTheme.colorScheme), VeritasPackStyle.chipShape()))
                        )
                    } else if (currentTab == VeritasHomeTab.LIBRARY) {
                        val fabPlusRotation by animateFloatAsState(
                            targetValue = if (showImportSheet) 45f else 0f,
                            animationSpec = VeritasMotion.spatialFast(),
                            label = "fabPlusRotation"
                        )
                        ExtendedFloatingActionButton(
                            text = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_add)) },
                            icon = {
                                Text(
                                    "+",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.graphicsLayer { rotationZ = fabPlusRotation }
                                )
                            },
                            onClick = { if (actionTab == currentTab) showImportSheet = true },
                            shape = VeritasPackStyle.chipShape(),
                            elevation = if (glassEnabled) FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp) else FloatingActionButtonDefaults.elevation(),
                            containerColor = if (glassEnabled) Color.Transparent else MaterialTheme.colorScheme.primary,
                            contentColor = if (glassEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                            modifier = actionSemantics
                                .padding(bottom = playerClearance * playerExpansion)
                                .veritasGlassBackdrop(VeritasPackStyle.chipShape(), glassEnabled, surfaceOpacity = .36f)
                                .onGloballyPositioned { OnboardingController.updateBounds("add_fab", it) }
                                .then(if (glassEnabled) Modifier else Modifier.border(VeritasPackStyle.cardBorder(MaterialTheme.colorScheme), VeritasPackStyle.chipShape()))
                        )
                    }
                }
            },

            bottomBar = {
                LibraryBottomNavBar(
                    activeNavTab = activeNavTab,
                    showNavLabels = uiState.readerSettings.showNavLabels,
                    onNavigateToTab = navigateToTab,
                    pagerNavPosition = navPosition
                )
            }
        ) { homePadding ->
            val navigationClearance = homePadding.calculateBottomPadding()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = homePadding.calculateTopPadding()
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                // The floating player samples this backdrop. It must be a sibling of the capture.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .recordVeritasBackdrop(effectiveBackdrop, isTransitionSettled && glassEnabled)
                        .background(VeritasPackStyle.backgroundBrush(MaterialTheme.colorScheme)),
                    contentAlignment = Alignment.TopCenter
                ) {
                val contentWidth = 760.dp
                BoxWithConstraints(Modifier.widthIn(max = contentWidth).fillMaxSize().statusBarsPadding().testTag("library-content")) {
                // Use the capped content column, including after a live window resize.
                // Add columns when 160 dp tiles and 12 dp gaps fit within the 36 dp list gutters.
                val columnCount = ((maxWidth - 24.dp) / 172.dp).toInt().coerceIn(2, 4)
                Column(
                    modifier = Modifier
                        .fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.fillMaxSize()) {
                    val searchScroll = remember(headerFocus) { object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            if (source == NestedScrollSource.UserInput && available.y < -8f && searchExpanded) {
                                searchExpanded = false
                                headerFocus.clearFocus()
                            }
                            return Offset.Zero
                        }
                    } }
                    val tapTransition = tabFade.value
                    val tabBackground = VeritasPackStyle.backgroundBrush(MaterialTheme.colorScheme)
                    val transitionPageRadius = tapTransition?.let { abs(it.incoming - it.outgoing) } ?: 0
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !selectionMode,
                        beyondViewportPageCount = maxOf(retainedPageRadius, transitionPageRadius),
                        modifier = Modifier.fillMaxSize().testTag("home_pager").nestedScroll(searchScroll)
                    ) { page ->
                        val destination = libraryPagerPages[page]
                        val pageVisible by remember(pagerState, page) { derivedStateOf {
                            pagerState.settledPage == page
                        } }
                        CompositionLocalProvider(
                            LocalLibraryPageVisible provides pageVisible,
                            LocalHomeBottomPadding provides navigationClearance + (if (destination.tab == VeritasHomeTab.NOTES) 0.dp else playerClearance),
                            com.veritas.reader.LocalVernCardEntrance provides if (page == entrancePage) cardEntrance else -1L
                        ) {
                        val isCurrentPage = page == pagerState.currentPage
                        val pageLibrarySection = destination.section ?: librarySection
                        val showPageSearch = searchExpanded && destination.tab == selectedHomeTab
                        val outgoing = tapTransition?.outgoing == page
                        val arriving = tapTransition?.incoming == page
                        Column(
                            Modifier.fillMaxSize().zIndex(if (outgoing) -1f else page.toFloat())
                                .graphicsLayer {
                                    translationX = if (outgoing) (pagerPosition() - page) * size.width *
                                        if (rtl) -1f else 1f else 0f
                                    alpha = if (arriving) tabArrival.floatValue else 1f
                                }
                                .background(tabBackground)
                                .then(if (isCurrentPage) Modifier else Modifier.clearAndSetSemantics {})
                        ) {
                            Column(
                                Modifier.fillMaxWidth()
                                    .libraryHeaderSwipeGate(page, rtl)
                                    .graphicsLayer {
                                        val position = pagerPosition()
                                        val pinned = libraryPinnedHeaderProgress(page, position)
                                        translationX = if (pinned == null) 0f else
                                            (position - page) * size.width * if (rtl) -1f else 1f
                                        // Blend the arriving header over the stationary one.
                                        alpha = if (pinned == null || page == position.toInt()) 1f else pinned
                                    }
                            ) {

                    LibraryTopAndFilterBar(
                                activeNavTab = destination.tab,
                                isActiveHeader = isCurrentPage,
                                includeStatusBar = false,
                                documents = documents,
                                queuedDocuments = queuedDocuments,
                                uiState = uiState,
                                currentStreak = currentStreak,
                                statusFilter = statusFilter,
                                onStatusFilterChange = { statusFilter = it },
                                sourceFilter = sourceFilter,
                                onSourceFilterChange = { sourceFilter = it },
                                collectionFilter = collectionFilter,
                                onCollectionFilterChange = { collectionFilter = it },
                                readingListFilter = readingListFilter,
                                onReadingListFilterChange = { readingListFilter = it },
                                selectedGeneralNoteTag = selectedGeneralNoteTag,
                                onSelectedGeneralNoteTagChange = { selectedGeneralNoteTag = it },
                                onOpenHomeSidebar = { showHomeSidebar = true },
                                onOpenSettingsHub = onOpenSettingsHub,
                                headerActions = {
                                    when (destination.tab) {
                                        VeritasHomeTab.LIBRARY -> LibraryHeaderActions(pageLibrarySection, libraryViewMode,
                                            { libraryViewMode = it; libraryPrefs.edit().putString("library_view_mode", it.name).apply() },
                                            classicsHeaderState, showPageSearch,
                                            { searchExpanded = !searchExpanded; if (!searchExpanded) headerFocus.clearFocus() },
                                            onOpenReadingLists, onOpenReadingHistory)
                                        VeritasHomeTab.NOTES -> NotesHeaderActions(notesSettings, onSaveNotesSettings,
                                            { localShowNotesSettings = true; onOpenNotesSettings() }, showPageSearch,
                                            { searchExpanded = !searchExpanded; if (!searchExpanded) headerFocus.clearFocus() },
                                            showNotesTrash, { showNotesTrash = it }, uiState.noteNotebooks,
                                            selectedNotesNotebookId, { selectedNotesNotebookId = it }, noteCollectionActions != null,
                                            { showCreateNotesNotebook = true }, { showManageNotesNotebooks = true },
                                            onAppSettings = onOpenSettingsHub)
                                        else -> Unit
                                    }
                                },
                                notesSearch = {
                                    AnimatedVisibility(showPageSearch, enter = expandVertically(VeritasMotion.spatial()) + fadeIn(chromeEffects), exit = shrinkVertically(VeritasMotion.spatial()) + fadeOut(chromeEffects)) {
                                        LibrarySearchField(noteSearchQuery, { noteSearchQuery = it }, "Search notes...",
                                            Modifier.fillMaxWidth().padding(horizontal = 6.dp).testTag("notes_search"),
                                            onDone = { searchExpanded = false; headerFocus.clearFocus() })
                                    }
                                },
                                onOpenNotesSettings = {
                                    localShowNotesSettings = true
                                    onOpenNotesSettings()
                                }
                            )

                            if (destination.section != null) {
                                LibrarySectionPicker(
                                    selected = activeLibrarySection,
                                    progress = { pagerPosition() - addedPage },
                                    onSelect = { section ->
                                        selectedDocumentIds = emptySet()
                                        navigateToPage(VeritasHomeTab.LIBRARY, section, true)
                                    }
                                )
                            }

                    AnimatedVisibility(destination.section != null && showPageSearch,
                        enter = expandVertically(VeritasMotion.spatial()) + fadeIn(chromeEffects), exit = shrinkVertically(VeritasMotion.spatial()) + fadeOut(chromeEffects)) {
                        LibrarySearchField(
                            if (pageLibrarySection == LibrarySection.CLASSICS) classicsHeaderState.query else libraryQuery,
                            { if (pageLibrarySection == LibrarySection.CLASSICS) classicsHeaderState.query = it else libraryQuery = it },
                            menuString(if (pageLibrarySection == LibrarySection.CLASSICS) com.veritas.reader.R.string.filter_search_classics
                                else com.veritas.reader.R.string.ui_search_library),
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
                                .testTag(if (pageLibrarySection == LibrarySection.CLASSICS) "classics_search" else "library_search"),
                            onDone = { searchExpanded = false; headerFocus.clearFocus() })
                    }

                            }

                        when (destination.tab) {
                            VeritasHomeTab.HOME -> {
                                LibraryHomeTab(
                                    documents = documents,
                                    uiState = uiState,
                                    continueDocument = continueDocument,
                                    homeListState = homeListState,
                                    isHomeGridView = isHomeGridView,
                                    onToggleHomeGridView = {
                                        isHomeGridView = !isHomeGridView
                                        libraryPrefs.edit().putBoolean("is_home_grid_view", isHomeGridView).apply()
                                    },
                                    onRefreshMainPage = onRefreshMainPage,
                                    onOpenDocument = onOpenDocument,
                                    onClearContinueDocument = onClearContinueDocument,
                                    onShowImportSheet = { showImportSheet = true },
                                    onOpenClassicsCatalog = onOpenClassicsCatalog,
                                    onDownloadClassicBook = onDownloadClassicBook,
                                    onBrowseClassicArchive = onBrowseClassicArchive,
                                    onCancelClassicBook = onCancelClassicBook,
                                    onNavigateToTab = navigateToTab,
                                    onShowReadingStatsHome = { showReadingStatsHome = true },
                                    onGoalMinutesChange = { minutes -> onSaveReaderSettings(uiState.readerSettings.copy(dailyGoalMinutes = minutes)) },
                                    isQueued = isQueued,
                                    onToggleFavorite = onToggleFavorite,
                                    onToggleQueue = onToggleQueue,
                                    onMoveQueueUp = onMoveQueueUp,
                                    onMoveQueueDown = onMoveQueueDown,
                                    onSetCollection = onSetCollection,
                                    onManageLists = { manageListsDocument = it },
                                    onRenameDocument = onRenameDocument,
                                    onShowDetails = onShowDetails,
                                    onDeleteDocument = onDeleteDocument,
                                    firstChapter = firstChapter?.let { chapter ->
                                        { chapter { showImportSheet = true } }
                                    }
                                )
                            }
                            VeritasHomeTab.LIBRARY -> {
                                sectionStateHolder.SaveableStateProvider(destination.section!!.name) {
                                if (destination.section == LibrarySection.CLASSICS) {
                                    ClassicsCatalogContent(documents, uiState.classicDownloads, onDownloadClassicBook,
                                        onCancelClassicBook, onOpenDocument, onBrowseClassicArchive,
                                        headerState = classicsHeaderState,
                                        discoveryTipEnabled = uiState.hasCompletedOnboarding && !uiState.showTutorial &&
                                            !uiState.showFirstChapterPrompt && !uiState.showBookBrowser)
                                } else LibraryBooksTab(
                                    filters = {
                                        LibraryDocumentFilters(documents, queuedDocuments, uiState,
                                            statusFilter, { statusFilter = it }, sourceFilter, { sourceFilter = it },
                                            collectionFilter, { collectionFilter = it }, readingListFilter, { readingListFilter = it })
                                    },
                                    documents = documents,
                                    queuedDocuments = queuedDocuments,
                                    uiState = uiState,
                                    libraryListState = libraryListState,
                                    libraryQuery = libraryQuery,
                                    onLibraryQueryChange = { libraryQuery = it },
                                    statusFilter = statusFilter,
                                    onStatusFilterChange = { statusFilter = it },
                                    sourceFilter = sourceFilter,
                                    onSourceFilterChange = { sourceFilter = it },
                                    collectionFilter = collectionFilter,
                                    onCollectionFilterChange = { collectionFilter = it },
                                    readingListFilter = readingListFilter,
                                    onReadingListFilterChange = { readingListFilter = it },
                                    sortMode = sortMode,
                                    onSortModeChange = { sortMode = it },
                                    columnCount = columnCount,
                                    libraryViewMode = libraryViewMode,
                                    onLibraryViewModeChange = {
                                        libraryViewMode = it
                                        libraryPrefs.edit().putString("library_view_mode", it.name).apply()
                                    },
                                    selectedDocumentIds = selectedDocumentIds,
                                    onSelectedDocumentIdsChange = { selectedDocumentIds = it },
                                    onBatchFavoriteDocuments = onBatchFavoriteDocuments,
                                    onBatchQueueDocuments = onBatchQueueDocuments,
                                    onShowBatchCollectionDialog = {
                                        batchCollectionDraft = ""
                                        showBatchCollectionDialog = true
                                    },
                                    onConfirmBatchDelete = { confirmBatchDelete = true },
                                    onOpenDocument = onOpenDocument,
                                    onDeleteDocument = onDeleteDocument,
                                    onToggleQueue = onToggleQueue,
                                    onMoveQueueUp = onMoveQueueUp,
                                    onMoveQueueDown = onMoveQueueDown,
                                    onToggleFavorite = onToggleFavorite,
                                    onRenameDocument = onRenameDocument,
                                    onSetCollection = onSetCollection,
                                    onShowDetails = onShowDetails,
                                    onManageLists = { manageListsDocument = it },
                                    onImportFile = onImportFile,
                                    onOpenReadingLists = onOpenReadingLists,
                                    onOpenReadingHistory = onOpenReadingHistory,
                                    onSearchLibraryContent = {
                                        onSearchLibraryContent(it)
                                        showContentSearchResults = true
                                    },
                                    onRefreshMainPage = onRefreshMainPage,
                                    isQueued = isQueued,
                                    onReorderDocuments = onReorderDocuments,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                                }
                            }
                            VeritasHomeTab.NOTES -> {
                                LibraryNotesTab(
                                    noteSearchQuery = noteSearchQuery,
                                    documents = documents,
                                    uiState = uiState,
                                    notesListState = notesListState,
                                    annotationFilter = annotationFilter,
                                    selectedGeneralNoteTag = selectedGeneralNoteTag,
                                    onSelectedGeneralNoteTagChange = { selectedGeneralNoteTag = it },
                                    selectedAnnotationKeys = selectedAnnotationKeys,
                                    onSelectedAnnotationKeysChange = { selectedAnnotationKeys = it },
                                    onConfirmAnnotationDelete = { confirmAnnotationDelete = true },
                                    onOpenDocument = onOpenDocument,
                                    onOpenDocumentAt = onOpenDocumentAt,
                                    onEditGeneralNote = onEditGeneralNote,
                                    onDeleteGeneralNote = onDeleteGeneralNote,
                                    onToggleGeneralNotePin = onToggleGeneralNotePin,
                                    onChangeGeneralNoteColor = onChangeGeneralNoteColor,
                                    onDeleteAnnotations = onDeleteAnnotations,
                                    onWriteGeneralNote = onWriteGeneralNote,
                                    onNavigateToTab = navigateToTab,
                                    onImportFile = onImportFile,
                                    notesSettings = notesSettings,
                                    collectionActions = noteCollectionActions,
                                    showTrash = showNotesTrash,
                                    selectedNotebookId = selectedNotesNotebookId,
                                    onSelectedNotebookChange = { selectedNotesNotebookId = it },
                                    createNotebookDialog = showCreateNotesNotebook,
                                    onCreateNotebookDialogChange = { showCreateNotesNotebook = it },
                                    manageNotebooksDialog = showManageNotesNotebooks,
                                    onManageNotebooksDialogChange = { showManageNotesNotebooks = it }
                                )
                            }
                            VeritasHomeTab.STUDY -> {
                                LibraryStudyTab(
                                    documents = documents,
                                    uiState = uiState,
                                    flashcardSets = flashcardSets,
                                    studyListState = studyListState,
                                    onRefreshMainPage = onRefreshMainPage,
                                    annotationFilter = annotationFilter,
                                    onAnnotationFilterChange = { annotationFilter = it },
                                    selectedAnnotationKeys = selectedAnnotationKeys,
                                    onSelectedAnnotationKeysChange = { selectedAnnotationKeys = it },
                                    onOpenDocument = onOpenDocument,
                                    onOpenDocumentAt = onOpenDocumentAt,
                                    onOpenAiStudyTools = onOpenAiStudyTools,
                                    onShowGeminiApiKeyDialog = { showGeminiApiKeyDialog = true },
                                    onShowQuizLabMetrics = { showQuizLabMetrics = true },
                                    onShowPasteQuiz = { showPasteQuiz = true },
                                    onShowPasteFlashcards = { showPasteFlashcards = true },
                                    onPlayQuiz = { activePlayingQuiz = it },
                                    onDeleteQuiz = { quizToDelete = it },
                                    onCancelAiStudyGeneration = onCancelAiStudyGeneration,
                                    onImportFlashcards = onImportFlashcards,
                                    onSaveQuiz = onSaveQuiz,
                                    onDeleteAnnotations = onDeleteAnnotations,
                                    onRemoveVocabularyWord = onRemoveVocabularyWord,
                                    onRefreshVocabularyMeaning = onRefreshVocabularyMeaning,
                                    onConfirmDeleteVocabDocId = { confirmDeleteVocabDocId = it },
                                    onClearReadingHistory = onClearReadingHistory,
                                    onRemoveReadingHistoryEntry = onRemoveReadingHistoryEntry,
                                    onDeleteFlashcardSet = onDeleteFlashcardSet,
                                    onOpenFlashcardViewer = { name, cardsList ->
                                        viewerSetName = name
                                        viewerCards = cardsList
                                    },
                                    onRenameFlashcardSet = { renameSetTarget = it },
                                    onNavigateToTab = navigateToTab,
                                    onImportFile = onImportFile,
                                    onExportStudyGuidePdf = onExportStudyGuidePdf
                                )
                            }
                        }
                                        }
                        }
                    }
                    }
                }

                    }
                }
                BatchImportFloater(
                    visible = uiState.isBatchImporting,
                    current = uiState.batchImportCurrent,
                    total = uiState.batchImportTotal,
                    failed = uiState.batchImportFailed,
                    modifier = Modifier
                        .padding(bottom = navigationClearance + if (activeNavTab == VeritasHomeTab.NOTES) 0.dp else playerClearance)
                        .consumeWindowInsets(homePadding)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                )
                if (playingDocument != null && activeNavTab != VeritasHomeTab.NOTES) {
                    Box(
                        Modifier.align(Alignment.BottomCenter)
                            .padding(bottom = navigationClearance)
                            .onSizeChanged { measuredPlayerHeight = with(density) { it.height.toDp() } }
                    ) {
                        LivePlaybackFloater(
                            playingDocument,
                            onOpen = { onOpenDocumentAt(playingDocument, PlaybackStateStore.currentIndex) },
                            onPlayPause = { onPlayPauseContinue(playingDocument) },
                            collapsed = playerCollapsed,
                            expansion = playerExpansion,
                            onToggleCollapsed = { playerCollapsed = !playerCollapsed }
                        )
                    }
                }
            }
        }
    }
}
}

private fun Modifier.libraryHeaderSwipeGate(page: Int, rtl: Boolean): Modifier =
    pointerInput(page, rtl) {
        awaitEachGesture {
            val pointer = awaitFirstDown(requireUnconsumed = false).id
            var displacement = Offset.Zero
            var blockSwipe = false
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == pointer }
                    ?: break
                if (!change.pressed) break
                if (!blockSwipe) {
                    displacement += change.position - change.previousPosition
                    val horizontal = abs(displacement.x)
                    val vertical = abs(displacement.y)
                    if (vertical > viewConfiguration.touchSlop && vertical > horizontal) break
                    if (horizontal <= viewConfiguration.touchSlop) continue
                    val forward = if (rtl) displacement.x > 0f else displacement.x < 0f
                    if (libraryHeaderMoves(page, page + if (forward) 1 else -1)) break
                    blockSwipe = true
                }
                // Consume before the pager handles motion, while still giving taps to toolbar actions.
                change.consume()
            }
        }
    }
