package com.veritas.reader.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

enum class VeritasHomeTab {
    HOME,
    LIBRARY,
    NOTES,
    STUDY
}

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
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null
) {

    val documents = uiState.documents
    val configuration = LocalConfiguration.current
    val columnCount = when {
        configuration.screenWidthDp >= 840 -> 4
        configuration.screenWidthDp >= 600 -> 3
        else -> 2
    }
    val queuedDocuments = uiState.queuedDocuments
    val draftText = uiState.draftText
    var libraryQuery by rememberSaveable { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("All") }
    var sourceFilter by remember { mutableStateOf("All") }
    var collectionFilter by remember { mutableStateOf("All") }
    var readingListFilter by remember { mutableStateOf("All") }
    var manageListsDocument by remember { mutableStateOf<SavedDocument?>(null) }
    var sortMode by remember { mutableStateOf("Updated") }
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
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var renameSetTarget by remember { mutableStateOf<FlashcardSet?>(null) }
    var showContentSearchResults by remember { mutableStateOf(false) }


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
    var showBatchMenu by remember { mutableStateOf(false) }
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
    var isHomeGridView by remember { mutableStateOf(libraryPrefs.getBoolean("is_home_grid_view", true)) }
    var libraryViewMode by remember {
        mutableStateOf(
            runCatching {
                LibraryViewMode.valueOf(
                    libraryPrefs.getString("library_view_mode", LibraryViewMode.TILES.name) ?: LibraryViewMode.TILES.name
                )
            }.getOrDefault(LibraryViewMode.TILES)
        )
    }
    var showLibraryViewMenu by remember { mutableStateOf(false) }
    val initialTab = remember(widgetAction, uiState.targetHomeTab) {
        uiState.targetHomeTab ?: when (widgetAction) {
            "show_study_dashboard",
            "show_flashcards" -> VeritasHomeTab.STUDY
            "show_notes",
            "new_note",
            "new_checklist_note",
            "new_reminder_note" -> VeritasHomeTab.NOTES
            "open_library" -> VeritasHomeTab.LIBRARY
            else -> VeritasHomeTab.HOME
        }
    }
    var selectedHomeTab by remember(initialTab) { mutableStateOf(initialTab) }
    LaunchedEffect(selectedHomeTab) {
        if (selectedHomeTab == VeritasHomeTab.STUDY) {
            val clip = clipboardManager.getText()?.text?.toString()?.trim().orEmpty()
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
    val isTourActive = OnboardingController.activeStep != null
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
    var expandedVocabDocIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var confirmDeleteVocabDocId by remember { mutableStateOf<String?>(null) }
    var selectedGeneralNoteTag by remember { mutableStateOf("All") }
    val libraryListState = rememberLazyListState()
    val notesListState = rememberLazyListState()
    var lastMainPageRefreshAt by remember { mutableLongStateOf(0L) }
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

    val completedCount by remember(documents) { derivedStateOf { documents.count { it.chunkCount > 0 && it.currentIndex >= it.chunkCount - 1 } } }
    val readingCount by remember(documents) { derivedStateOf { documents.count { it.chunkCount > 1 && it.currentIndex in 1 until it.chunkCount - 1 } } }
    val favoriteCount by remember(documents) { derivedStateOf { documents.count { it.favorite } } }
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
    val longestStreak = uiState.readerTrackerSnapshot.longestStreak

    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = initialTab.ordinal) { VeritasHomeTab.entries.size }
    val homeListState = rememberLazyListState()
    val studyListState = rememberLazyListState()

    val navigateToTab: (VeritasHomeTab) -> Unit = remember(pagerState, coroutineScope) {
        { tab: VeritasHomeTab ->
            selectedHomeTab = tab
            coroutineScope.launch { pagerState.animateScrollToPage(tab.ordinal) }
        }
    }

    val isNotHomeTab = pagerState.currentPage != VeritasHomeTab.HOME.ordinal
    BackHandler(enabled = selectionMode || showHomeSidebar || showImportSheet || isNotHomeTab) {
        when {
            selectionMode -> selectedDocumentIds = emptySet()
            showHomeSidebar -> showHomeSidebar = false
            showImportSheet -> showImportSheet = false
            isNotHomeTab -> navigateToTab(VeritasHomeTab.HOME)
        }
    }

    // Handle targetHomeTab navigation requests directly:
    LaunchedEffect(uiState.targetHomeTab) {
        uiState.targetHomeTab?.let { target ->
            pagerState.scrollToPage(target.ordinal)
            selectedHomeTab = target
            onClearTargetHomeTab()
        }
    }

    // Handle widget actions:
    LaunchedEffect(widgetAction) {
        when (widgetAction) {
            "show_study_dashboard",
            "show_flashcards" -> {
                pagerState.animateScrollToPage(VeritasHomeTab.STUDY.ordinal)
                selectedHomeTab = VeritasHomeTab.STUDY
            }
            "show_notes",
            "new_note",
            "new_checklist_note",
            "new_reminder_note" -> {
                pagerState.animateScrollToPage(VeritasHomeTab.NOTES.ordinal)
                selectedHomeTab = VeritasHomeTab.NOTES
            }
            "open_library" -> {
                pagerState.animateScrollToPage(VeritasHomeTab.LIBRARY.ordinal)
                selectedHomeTab = VeritasHomeTab.LIBRARY
            }
        }
    }

    // Handle onboarding tour steps:
    LaunchedEffect(OnboardingController.activeStep) {
        when (OnboardingController.activeStep) {
            OnboardingStep.CLASSICS_SPOTLIGHT,
            OnboardingStep.INSIGHTS_SPOTLIGHT,
            OnboardingStep.INSIGHTS_PAGE_SPOTLIGHT -> {
                pagerState.animateScrollToPage(VeritasHomeTab.HOME.ordinal)
                selectedHomeTab = VeritasHomeTab.HOME
            }
            OnboardingStep.NOTES_TAB_SPOTLIGHT -> {
                pagerState.animateScrollToPage(VeritasHomeTab.NOTES.ordinal)
                selectedHomeTab = VeritasHomeTab.NOTES
            }
            OnboardingStep.STUDY_TAB_SPOTLIGHT -> {
                pagerState.animateScrollToPage(VeritasHomeTab.STUDY.ordinal)
                selectedHomeTab = VeritasHomeTab.STUDY
            }
            null -> {}
            else -> {
                pagerState.animateScrollToPage(VeritasHomeTab.LIBRARY.ordinal)
                selectedHomeTab = VeritasHomeTab.LIBRARY
            }
        }
    }

    // Active navigation tab follows targetPage during swipe, settles cleanly:
    val activeNavTab = remember(pagerState.currentPage, pagerState.targetPage, pagerState.isScrollInProgress, selectedHomeTab) {
        if (pagerState.isScrollInProgress) {
            VeritasHomeTab.entries.getOrNull(pagerState.targetPage) ?: selectedHomeTab
        } else {
            VeritasHomeTab.entries.getOrNull(pagerState.currentPage) ?: selectedHomeTab
        }
    }

    // Keep selectedHomeTab in sync with settled page:
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val targetTab = VeritasHomeTab.entries.getOrNull(page) ?: VeritasHomeTab.HOME
            if (selectedHomeTab != targetTab) {
                selectedHomeTab = targetTab
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

    Box(modifier = Modifier.fillMaxSize().background(VeritasPackStyle.backgroundBrush(MaterialTheme.colorScheme))) {
        Scaffold(
            containerColor = Color.Transparent,
            // The container is transparent (we draw our own background brush), so set the
            // content colour explicitly to the theme's onSurface. Otherwise uncoloured Text
            // (section headers, empty states) falls back to the default black LocalContentColor
            // and is unreadable in dark mode on the notes/bookmarks/history/vocabulary tabs.
            contentColor = MaterialTheme.colorScheme.onSurface,
            floatingActionButton = {
                // FAB pops in/out with the Library tab and its + rotates 45° with a
                // spring while the import sheet is open (micro-morph, no layout risk).
                AnimatedVisibility(
                    visible = activeNavTab == VeritasHomeTab.LIBRARY || activeNavTab == VeritasHomeTab.NOTES,
                    enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(150)),
                    exit = scaleOut(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) + fadeOut(tween(150))
                ) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = activeNavTab,
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
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)?.let { Modifier.border(it, VeritasPackStyle.chipShape()) } ?: Modifier
                            )
                        } else {
                            val fabPlusRotation by animateFloatAsState(
                                targetValue = if (showImportSheet) 45f else 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
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
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .onGloballyPositioned { OnboardingController.updateBounds("add_fab", it) }
                                    .then(VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)?.let { Modifier.border(it, VeritasPackStyle.chipShape()) } ?: Modifier)
                            )
                        }
                    }
                }
            },

            topBar = {
                LibraryTopAndFilterBar(
                    activeNavTab = activeNavTab,
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
                    onOpenSettingsHub = onOpenSettingsHub
                )
            },
            bottomBar = {
                LibraryBottomNavBar(
                    activeNavTab = activeNavTab,
                    showNavLabels = uiState.readerSettings.showNavLabels,
                    onNavigateToTab = navigateToTab
                )
            }
        ) { homePadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = homePadding.calculateTopPadding(),
                        bottom = homePadding.calculateBottomPadding()
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 760.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedVisibility(
                        visible = uiState.isBatchImporting,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(12.dp)
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
                                        text = "${uiState.batchImportCurrent} of ${uiState.batchImportTotal} processed",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (VeritasHomeTab.entries[page]) {
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
                                LibraryBooksTab(
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
                            VeritasHomeTab.NOTES -> {
                                LibraryNotesTab(
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
                                    onImportFile = onImportFile
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

                val context = LocalContext.current
                LaunchedEffect(uiState.importMessage) {
                    val msg = uiState.importMessage
                    if (!msg.isNullOrBlank()) {
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
