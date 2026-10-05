package com.veritas.reader.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import com.veritas.reader.ui.VeritasMotion
import com.veritas.reader.veritasGlassBackdrop
import androidx.compose.foundation.layout.statusBarsPadding
import com.veritas.reader.recordVeritasBackdrop
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.ui.NotesSettings
import com.veritas.reader.AiResultParser
import com.veritas.reader.DocumentRepository
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
import com.veritas.reader.TextChunker
import com.veritas.reader.VeritasFeatureContext
import com.veritas.reader.VeritasFeatureId
import com.veritas.reader.VeritasFeatureRegistry
import com.veritas.reader.VeritasFeatureSurface
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.parseVocabularyNoteContent
import com.veritas.reader.ui.OnboardingController
import com.veritas.reader.ui.OnboardingStep
import com.veritas.reader.ui.ReaderUiState
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

internal data class MarkedDocument(
    val document: SavedDocument,
    val annotations: List<ReaderAnnotation>,
    val documentNote: String,
    val updatedAt: Long
)

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
    onImportWebArticle: (String) -> Unit,
    onImportFile: () -> Unit,
    onImportImage: () -> Unit,
    onAdvancedPdfImport: () -> Unit,
    onOpenFileBrowser: () -> Unit,
    onOpenClassicsCatalog: () -> Unit = {},
    onDownloadClassicBook: (ClassicBookEntry) -> Unit = {},
    onCancelClassicBook: (ClassicBookEntry) -> Unit = {},
    onBrowseClassicArchive: (String, String, String) -> Unit = { _, _, _ -> },
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
    onClearReadingHistory: () -> Unit = {},
    onRemoveReadingHistoryEntry: (String) -> Unit = {},
    onToggleGeneralNotePin: (String) -> Unit = {},
    onChangeGeneralNoteColor: (String, String?) -> Unit = { _, _ -> },
    onDeleteGeneralNote: (String) -> Unit = {},
    onRateFlashcardRecall: (String, String) -> Unit = { _, _ -> },
    onDeleteFlashcard: (String) -> Unit = {},
    onImportFlashcards: (String, List<Flashcard>) -> Unit = { _, _ -> },
    onRenameFlashcardSet: (String, String) -> Unit = { _, _ -> },
    onDeleteFlashcardSet: (String) -> Unit = {},
    onSaveReaderSettings: (ReaderSettings) -> Unit = {},
    onSaveQuiz: (QuizSet) -> Unit = {},
    onDeleteQuiz: (String) -> Unit = {},
    onRecordQuizScore: (String, Int) -> Unit = { _, _ -> },
    onGenerateInAppFlashcards: (SavedDocument, String?) -> Unit = { _, _ -> },
    onGenerateInAppQuiz: (SavedDocument, String?) -> Unit = { _, _ -> },
    onOpenAiStudyTools: () -> Unit = {},
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
    val windowWidthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val columnCount = when {
        windowWidthDp >= 840.dp -> 4
        windowWidthDp >= 600.dp -> 3
        else -> 2
    }
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
    val repository = remember(context) { DocumentRepository(context) }
    var loadedDocSentences by remember { mutableStateOf(emptyMap<String, List<String>>()) }
    val docIdsWithAnnotations = remember(uiState.allAnnotations) {
        uiState.allAnnotations.map { it.documentId }.toSet()
    }
    LaunchedEffect(docIdsWithAnnotations, uiState.documents) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val neededIds = docIdsWithAnnotations.filter { it !in loadedDocSentences }
            if (neededIds.isNotEmpty()) {
                val newMap = neededIds.associateWith { docId ->
                    val docMetadata = uiState.documents.firstOrNull { it.id == docId }
                    if (docMetadata != null) {
                        val text = repository.readText(docMetadata)
                        TextChunker.chunk(text)
                    } else {
                        emptyList()
                    }
                }
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    loadedDocSentences = loadedDocSentences + newMap
                }
            }
        }
    }
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
                val quiz = AiResultParser.parseQuiz(clip)
                if (quiz.isNotEmpty()) {
                    detectedClipboardQuiz = quiz
                    detectedClipboardFlashcards = emptyList()
                } else {
                    val cards = AiResultParser.parseFlashcards(clip)
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
    var showReadingStatsHome by remember(widgetAction) {
        mutableStateOf(widgetAction == "show_reader_tracker")
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
    var annotationFilter by remember { mutableStateOf("Bookmarks") }
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
    val pagerMotion = VeritasMotion.spatialSlow<Float>()
    val chromeEffects = VeritasMotion.effectsFast<Float>()
    var navigationJob by remember { mutableStateOf<Job?>(null) }
    var navigationRequest by remember { mutableStateOf(0) }
    var requestedNavTab by remember { mutableStateOf<VeritasHomeTab?>(null) }
    val navigateToPage: (VeritasHomeTab, LibrarySection) -> Unit = { tab, section ->
            val request = ++navigationRequest
            navigationJob?.cancel()
            if (tab == VeritasHomeTab.LIBRARY) librarySection = section
            selectedHomeTab = tab
            requestedNavTab = tab
            navigationJob = coroutineScope.launch {
                try {
                    val page = libraryPagerIndex(tab, section)
                    if (reduceMotion) pagerState.scrollToPage(page)
                    else pagerState.animateScrollToPage(page, animationSpec = pagerMotion)
                } finally {
                    if (navigationRequest == request) requestedNavTab = null
                }
            }
    }
    val navigateToTab: (VeritasHomeTab) -> Unit = { tab ->
        navigateToPage(tab, if (tab == VeritasHomeTab.LIBRARY) LibrarySection.MY_LIBRARY else librarySection)
    }

    val isNotHomeTab = (requestedNavTab ?: libraryPagerPages[pagerState.currentPage].tab) != VeritasHomeTab.HOME
    BackHandler(enabled = selectionMode || showHomeSidebar || showImportSheet || isNotHomeTab) {
        when {
            selectionMode -> selectedDocumentIds = emptySet()
            showHomeSidebar -> showHomeSidebar = false
            showImportSheet -> showImportSheet = false
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
            navigationRequest++
            navigationJob?.cancel()
            requestedNavTab = null
            pagerState.scrollToPage(libraryPagerIndex(target, uiState.targetLibrarySection ?: librarySection))
            selectedHomeTab = target
        }
        uiState.targetLibrarySection?.let { librarySection = it }
        if (explicitTarget != null) onClearTargetHomeTab()
    }

    // Handle onboarding tour steps:
    LaunchedEffect(OnboardingController.activeStep) {
        when (OnboardingController.activeStep) {
            OnboardingStep.CLASSICS_SPOTLIGHT -> {
                librarySection = LibrarySection.CLASSICS
                pagerState.animateScrollToPage(libraryPagerIndex(VeritasHomeTab.LIBRARY, librarySection), animationSpec = pagerMotion)
                selectedHomeTab = VeritasHomeTab.LIBRARY
            }
            OnboardingStep.INSIGHTS_SPOTLIGHT,
            OnboardingStep.INSIGHTS_PAGE_SPOTLIGHT -> {
                pagerState.animateScrollToPage(libraryPagerIndex(VeritasHomeTab.HOME, librarySection), animationSpec = pagerMotion)
                selectedHomeTab = VeritasHomeTab.HOME
            }
            OnboardingStep.NOTES_TAB_SPOTLIGHT -> {
                pagerState.animateScrollToPage(libraryPagerIndex(VeritasHomeTab.NOTES, librarySection), animationSpec = pagerMotion)
                selectedHomeTab = VeritasHomeTab.NOTES
            }
            OnboardingStep.STUDY_TAB_SPOTLIGHT -> {
                pagerState.animateScrollToPage(libraryPagerIndex(VeritasHomeTab.STUDY, librarySection), animationSpec = pagerMotion)
                selectedHomeTab = VeritasHomeTab.STUDY
            }
            null -> {}
            else -> {
                pagerState.animateScrollToPage(libraryPagerIndex(VeritasHomeTab.LIBRARY, librarySection), animationSpec = pagerMotion)
                selectedHomeTab = VeritasHomeTab.LIBRARY
            }
        }
    }

    // Follow the nearest page during a swipe; settled destinations are persisted below.
    val activeNavTab = requestedNavTab ?: libraryPagerPages[pagerState.currentPage].tab
    BackHandler(enabled = (activeNavTab == VeritasHomeTab.LIBRARY || activeNavTab == VeritasHomeTab.NOTES) && searchExpanded) {
        searchExpanded = false
        headerFocus.clearFocus()
    }
    LaunchedEffect(activeNavTab) { searchExpanded = false; headerFocus.clearFocus() }

    // Keep selectedHomeTab in sync with settled page:
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val destination = libraryPagerPages[page]
            if (requestedNavTab == null) {
                destination.section?.let { librarySection = it }
                val targetTab = destination.tab
                if (selectedHomeTab != targetTab) selectedHomeTab = targetTab
            }
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

    val glassEnabled = VeritasPackStyle.glassChromeEnabled() && com.veritas.reader.isDeviceGlassCapable()
    val glassBackdrop = if (glassEnabled) com.veritas.reader.rememberVeritasLayerBackdrop() else null
    com.veritas.reader.VeritasBackdropProvider(glassBackdrop) {
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
                // FAB pops in/out with the Library tab and its + rotates 45° with a
                // spring while the import sheet is open (micro-morph, no layout risk).
                AnimatedVisibility(
                    visible = (activeNavTab == VeritasHomeTab.LIBRARY && librarySection == LibrarySection.MY_LIBRARY) || activeNavTab == VeritasHomeTab.NOTES,
                    enter = scaleIn(VeritasMotion.spatialFast(), initialScale = .92f) + fadeIn(chromeEffects),
                    exit = scaleOut(VeritasMotion.spatialFast(), targetScale = .96f) + fadeOut(chromeEffects)
                ) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = activeNavTab,
                        transitionSpec = { (fadeIn(chromeEffects) togetherWith fadeOut(chromeEffects)).using(null) },
                        modifier = Modifier.offset(y = if (activeNavTab != VeritasHomeTab.NOTES &&
                            documents.any { it.id == PlaybackStateStore.activeDocumentId }) 88.dp * (1f - playerExpansion) else 0.dp),
                        label = "fabTabContentTransition"
                    ) { currentTab ->
                        if (currentTab == VeritasHomeTab.NOTES) {
                            ExtendedFloatingActionButton(
                                text = { Text("Note") },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.EditNote,
                                        contentDescription = "Write note",
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                onClick = { onWriteGeneralNote() },
                                shape = VeritasPackStyle.chipShape(),
                                containerColor = if (glassEnabled) Color.Transparent else MaterialTheme.colorScheme.primary,
                                contentColor = if (glassEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.veritasGlassBackdrop(VeritasPackStyle.chipShape(), glassEnabled, surfaceOpacity = .72f)
                                    .border(VeritasPackStyle.cardBorder(MaterialTheme.colorScheme), VeritasPackStyle.chipShape())
                            )
                        } else {
                            val fabPlusRotation by animateFloatAsState(
                                targetValue = if (showImportSheet) 45f else 0f,
                                animationSpec = VeritasMotion.spatialFast(),
                                label = "fabPlusRotation"
                            )
                            ExtendedFloatingActionButton(
                                text = { Text("Add") },
                                icon = {
                                    Text(
                                        "+",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.graphicsLayer { rotationZ = fabPlusRotation }
                                    )
                                },
                                onClick = { showImportSheet = true },
                                shape = VeritasPackStyle.chipShape(),
                                containerColor = if (glassEnabled) Color.Transparent else MaterialTheme.colorScheme.primary,
                                contentColor = if (glassEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .veritasGlassBackdrop(VeritasPackStyle.chipShape(), glassEnabled, surfaceOpacity = .72f)
                                    .onGloballyPositioned { OnboardingController.updateBounds("add_fab", it) }
                                    .border(VeritasPackStyle.cardBorder(MaterialTheme.colorScheme), VeritasPackStyle.chipShape())
                            )
                        }
                    }
                }
            },

            bottomBar = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (activeNavTab != VeritasHomeTab.NOTES) documents.firstOrNull { it.id == com.veritas.reader.PlaybackStateStore.activeDocumentId }?.let { playingDocument ->
                        LivePlaybackFloater(playingDocument,
                            onOpen = { onOpenDocumentAt(playingDocument, com.veritas.reader.PlaybackStateStore.currentIndex) },
                            onPlayPause = { onPlayPauseContinue(playingDocument) },
                            collapsed = playerCollapsed, expansion = playerExpansion,
                            onToggleCollapsed = { playerCollapsed = !playerCollapsed })
                    }
                LibraryBottomNavBar(
                    activeNavTab = activeNavTab,
                    showNavLabels = uiState.readerSettings.showNavLabels,
                    onNavigateToTab = navigateToTab
                )
                }
            }
        ) { homePadding ->
            CompositionLocalProvider(LocalHomeBottomPadding provides homePadding.calculateBottomPadding()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .recordVeritasBackdrop(glassBackdrop, glassEnabled)
                    .background(VeritasPackStyle.backgroundBrush(MaterialTheme.colorScheme))
                    .padding(
                        top = homePadding.calculateTopPadding()
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .widthIn(max = 760.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedVisibility(
                        visible = uiState.isBatchImporting,
                        enter = expandVertically(VeritasMotion.spatial()) + fadeIn(chromeEffects),
                        exit = shrinkVertically(VeritasMotion.spatial()) + fadeOut(chromeEffects)
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = com.veritas.reader.VeritasPackStyle.compactShape()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Importing files...",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "${uiState.batchImportCurrent} of ${uiState.batchImportTotal} completed" +
                                            if (uiState.batchImportFailed > 0) " · ${uiState.batchImportFailed} failed" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }

                            LibraryTopAndFilterBar(
                                activeNavTab = activeNavTab,
                                librarySection = librarySection,
                                librarySelectedSection = libraryPagerPages[pagerState.currentPage].section ?: librarySection,
                                libraryTabProgress = { (pagerState.currentPage + pagerState.currentPageOffsetFraction - 1f).coerceIn(0f, 1f) },
                                isActiveHeader = true,
                                onLibrarySectionChange = { section ->
                                    librarySection = section; selectedDocumentIds = emptySet()
                                    navigateToPage(VeritasHomeTab.LIBRARY, section)
                                },
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
                                    when (activeNavTab) {
                                        VeritasHomeTab.LIBRARY -> LibraryHeaderActions(librarySection, libraryViewMode,
                                            { libraryViewMode = it; libraryPrefs.edit().putString("library_view_mode", it.name).apply() },
                                            classicsHeaderState, searchExpanded,
                                            { searchExpanded = !searchExpanded; if (!searchExpanded) headerFocus.clearFocus() },
                                            onOpenReadingLists, onOpenReadingHistory)
                                        VeritasHomeTab.NOTES -> NotesHeaderActions(notesSettings, onSaveNotesSettings,
                                            { localShowNotesSettings = true; onOpenNotesSettings() }, searchExpanded,
                                            { searchExpanded = !searchExpanded; if (!searchExpanded) headerFocus.clearFocus() },
                                            showNotesTrash, { showNotesTrash = it }, uiState.noteNotebooks,
                                            selectedNotesNotebookId, { selectedNotesNotebookId = it }, noteCollectionActions != null,
                                            { showCreateNotesNotebook = true }, { showManageNotesNotebooks = true })
                                        else -> Unit
                                    }
                                },
                                notesSearch = {
                                    AnimatedVisibility(searchExpanded, enter = expandVertically(VeritasMotion.spatial()) + fadeIn(chromeEffects), exit = shrinkVertically(VeritasMotion.spatial()) + fadeOut(chromeEffects)) {
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

                    AnimatedVisibility(activeNavTab == VeritasHomeTab.LIBRARY && searchExpanded,
                        enter = expandVertically(VeritasMotion.spatial()) + fadeIn(chromeEffects), exit = shrinkVertically(VeritasMotion.spatial()) + fadeOut(chromeEffects)) {
                        LibrarySearchField(
                            if (librarySection == LibrarySection.CLASSICS) classicsHeaderState.query else libraryQuery,
                            { if (librarySection == LibrarySection.CLASSICS) classicsHeaderState.query = it else libraryQuery = it },
                            if (librarySection == LibrarySection.CLASSICS) "Search books or authors" else "Search library...",
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
                                .testTag(if (librarySection == LibrarySection.CLASSICS) "classics_search" else "library_search"),
                            onDone = { searchExpanded = false; headerFocus.clearFocus() })
                    }

                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !selectionMode,
                        beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxSize().testTag("home_pager").nestedScroll(object : NestedScrollConnection {
                            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                                if (source == NestedScrollSource.UserInput && available.y < -8f && searchExpanded) {
                                    searchExpanded = false; headerFocus.clearFocus()
                                }
                                return Offset.Zero
                            }
                        })
                    ) { page ->
                        val destination = libraryPagerPages[page]
                        Column(Modifier.fillMaxSize().then(if (page == pagerState.currentPage) Modifier else Modifier.clearAndSetSemantics {})) {

                        when (destination.tab) {
                            VeritasHomeTab.HOME -> {
                                LibraryHomeTab(
                                    documents = documents,
                                    uiState = uiState,
                                    currentStreak = currentStreak,
                                    continueDocument = continueDocument,
                                    homeListState = homeListState,
                                    isHomeGridView = isHomeGridView,
                                    onToggleHomeGridView = {
                                        isHomeGridView = !isHomeGridView
                                        libraryPrefs.edit().putBoolean("is_home_grid_view", isHomeGridView).apply()
                                    },
                                    onRefreshMainPage = onRefreshMainPage,
                                    onOpenDocument = onOpenDocument,
                                    onPlayPauseContinue = onPlayPauseContinue,
                                    onClearContinueDocument = onClearContinueDocument,
                                    onShowImportSheet = { showImportSheet = true },
                                    onOpenClassicsCatalog = onOpenClassicsCatalog,
                                    onDownloadClassicBook = onDownloadClassicBook,
                                    onCancelClassicBook = onCancelClassicBook,
                                    onNavigateToTab = navigateToTab,
                                    onSetSourceFilter = {
                                        sourceFilter = it
                                        navigateToTab(VeritasHomeTab.LIBRARY)
                                    },
                                    onShowReadingStatsHome = { showReadingStatsHome = true },
                                    isQueued = isQueued,
                                    onToggleFavorite = onToggleFavorite,
                                    onToggleQueue = onToggleQueue,
                                    onMoveQueueUp = onMoveQueueUp,
                                    onMoveQueueDown = onMoveQueueDown,
                                    onSetCollection = onSetCollection,
                                    onManageLists = { manageListsDocument = it },
                                    onRenameDocument = onRenameDocument,
                                    onShowDetails = onShowDetails,
                                    onDeleteDocument = onDeleteDocument
                                )
                            }
                            VeritasHomeTab.LIBRARY -> {
                                sectionStateHolder.SaveableStateProvider(destination.section!!.name) {
                                if (destination.section == LibrarySection.CLASSICS) {
                                    ClassicsCatalogContent(documents, uiState.classicDownloads, onDownloadClassicBook,
                                        onCancelClassicBook, onOpenDocument, onBrowseClassicArchive, headerState = classicsHeaderState)
                                } else LibraryBooksTab(
                                    filters = {
                                        LibraryDocumentFilters(documents, queuedDocuments, uiState,
                                            statusFilter, { statusFilter = it }, sourceFilter, { sourceFilter = it },
                                            collectionFilter, { collectionFilter = it }, readingListFilter, { readingListFilter = it })
                                        LibrarySortControl(sortMode) { sortMode = it }
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
                                    onGenerateInAppFlashcards = onGenerateInAppFlashcards,
                                    onImportFlashcards = onImportFlashcards,
                                    onSaveQuiz = onSaveQuiz,
                                    onDeleteAnnotations = onDeleteAnnotations,
                                    onRemoveVocabularyWord = onRemoveVocabularyWord,
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
                                    onImportFile = onImportFile
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
}
}
