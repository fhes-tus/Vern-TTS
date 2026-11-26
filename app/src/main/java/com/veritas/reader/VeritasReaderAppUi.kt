package com.veritas.reader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.veritas.reader.ui.cancelAiStudyGeneration
import com.veritas.reader.ui.openBookArchive
import com.veritas.reader.ui.currentReaderIndex
import com.veritas.reader.ui.isReaderPlaying


import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.veritas.reader.ui.OnboardingController
import com.veritas.reader.ui.OnboardingStep
import com.veritas.reader.ui.screens.NoteCollectionActions
import com.veritas.reader.ui.ReaderViewModel
import com.veritas.reader.ui.setDraftNoteLabels
import com.veritas.reader.ui.createNoteLabel
import com.veritas.reader.ui.setGeneralNoteLabels
import com.veritas.reader.ui.restoreGeneralNoteRevision
import com.veritas.reader.ui.noteRevisions
import com.veritas.reader.ui.permanentlyDeleteTrashedGeneralNote
import com.veritas.reader.ui.restoreTrashedGeneralNote
import com.veritas.reader.ui.deleteNoteNotebook
import com.veritas.reader.ui.renameNoteNotebook
import com.veritas.reader.ui.moveGeneralNoteToNotebook
import com.veritas.reader.ui.createNoteNotebook
import com.veritas.reader.ui.saveNotesSettings
import com.veritas.reader.ui.addBookmarkGroup
import com.veritas.reader.ui.addDocumentToReadingList
import com.veritas.reader.ui.appendVocabularyWord
import com.veritas.reader.ui.beginSentenceNote
import com.veritas.reader.ui.changeGeneralNoteColor
import com.veritas.reader.ui.clearQueue
import com.veritas.reader.ui.clearReadingHistory
import com.veritas.reader.ui.createAndOpenDocument
import com.veritas.reader.ui.createReadingList
import com.veritas.reader.ui.createWelcomeDocumentSilently
import com.veritas.reader.ui.deleteAnnotations
import com.veritas.reader.ui.deleteDocuments
import com.veritas.reader.ui.deleteFlashcard
import com.veritas.reader.ui.deleteFlashcardSet
import com.veritas.reader.ui.deleteGeneralNote
import com.veritas.reader.ui.deleteQuiz
import com.veritas.reader.ui.discardRecordedSoundFile
import com.veritas.reader.ui.dismissQuestChecklist
import com.veritas.reader.ui.downloadClassicBook
import com.veritas.reader.ui.exportActiveDocumentToAudio
import com.veritas.reader.ui.applySettingsProfile
import com.veritas.reader.ui.exportStudyGuidePdf
import com.veritas.reader.ui.favoriteDocuments
import com.veritas.reader.ui.withVisibility
import com.veritas.reader.ui.generateInAppQuiz
import com.veritas.reader.ui.importFlashcards
import com.veritas.reader.ui.importWebArticle
import com.veritas.reader.ui.moveQueueItem
import com.veritas.reader.ui.moveToNextSearchMatch
import com.veritas.reader.ui.moveToPreviousSearchMatch
import com.veritas.reader.ui.openCurrentPartTextEditor
import com.veritas.reader.ui.openDocumentNotes
import com.veritas.reader.ui.openFileBrowser
import com.veritas.reader.ui.openSelectionTextEditor
import com.veritas.reader.ui.prepareImport
import com.veritas.reader.ui.queueDocuments
import com.veritas.reader.ui.rateFlashcardRecall
import com.veritas.reader.ui.recordQuizScore
import com.veritas.reader.ui.removeDocumentFromReadingList
import com.veritas.reader.ui.removeReadingHistoryEntry
import com.veritas.reader.ui.removeVocabularyWord
import com.veritas.reader.ui.refreshVocabularyMeaning
import com.veritas.reader.ui.renameFlashcardSet
import com.veritas.reader.ui.reorderDocuments
import com.veritas.reader.ui.saveAskAiSettings
import com.veritas.reader.ui.saveQuiz
import com.veritas.reader.ui.saveReaderSettings
import com.veritas.reader.ui.saveRecordedSoundFile
import com.veritas.reader.ui.saveVoiceSettings
import com.veritas.reader.ui.screens.LibraryScreen
import com.veritas.reader.ui.screens.OnboardingQuestChecklist
import com.veritas.reader.ui.screens.FirstChapterPrompt
import com.veritas.reader.ui.screens.ReaderScreen
import com.veritas.reader.ui.screens.ReaderScreenState
import com.veritas.reader.ui.searchLibraryContent
import com.veritas.reader.ui.setCollectionForDocuments
import com.veritas.reader.ui.startRecordSoundFile
import com.veritas.reader.ui.stopRecordSoundFile
import com.veritas.reader.ui.toggleBookmark
import com.veritas.reader.ui.toggleFavorite
import com.veritas.reader.ui.toggleGeneralNotePin
import com.veritas.reader.ui.toggleQueue
import com.veritas.reader.ui.importSharedNote
import com.veritas.reader.ui.updateSearchQuery
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File


internal const val MAIN_ACTIVITY_TAG = "MainActivity"


@Composable
internal fun VeritasReaderApp(
    initialShare: IncomingShare?,
    openVoiceStudioOnStart: Boolean,
    widgetAction: String? = null,
    noteId: String? = null
) {
    val context = LocalContext.current
    val viewModel: ReaderViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAudioExportOptions by rememberSaveable(uiState.activeDocument?.id, uiState.activeDocument?.title) {
        mutableStateOf(false)
    }
    val coroutineScope = rememberCoroutineScope()
    var showStorageTools by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showUnrestrictedBatteryDialog by remember { mutableStateOf(false) }
    var pendingSettingsUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    TrackReaderOverlay(if (PlaybackStateStore.readerMode == ReaderMode.LISTEN && !uiState.showCanvasView) "listen" else "main",
        showAboutDialog || showUnrestrictedBatteryDialog || showAudioExportOptions || pendingSettingsUri != null)
    var pendingShareChooser by remember { mutableStateOf<IncomingShare?>(null) }

    val batteryPrefs = remember { context.getSharedPreferences("veritas_reader_library", Context.MODE_PRIVATE) }
    LaunchedEffect(Unit) {
        if (!isBatteryOptimizationIgnored(context) && !batteryPrefs.getBoolean("battery_unrestricted_never_ask", false)) {
            showUnrestrictedBatteryDialog = true
        }
    }

    fun openInNotes(share: IncomingShare) {
        viewModel.importSharedNote(share)
    }

    fun routeIncomingShare(share: IncomingShare) {
        if (share.toNotes) {
            openInNotes(share)
            return
        }
        coroutineScope.launch {
            val settingsUri = share.settingsFile(context.applicationContext)
            if (settingsUri != null) {
                pendingShareChooser = null
                pendingSettingsUri = settingsUri
            } else {
                pendingShareChooser = share
            }
        }
    }

    fun importToReader(text: String, uri: Uri?) {
        if (uri != null) {
            viewModel.prepareImport(uri)
        } else if (text.isNotBlank()) {
            if (WebArticleExtractor.looksLikeUrl(text)) {
                viewModel.importWebArticle(text.trim())
            } else {
                viewModel.updateState { it.copy(draftText = text) }
            }
        }
    }

    DisposableEffect(context) {
        val activity = context as? MainActivity
        activity?.onIncomingShare = ::routeIncomingShare
        onDispose {
            activity?.onIncomingShare = null
        }
    }
    val documentRepository = remember(context) { DocumentRepository(context.applicationContext) }
    val originalPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val documentId = result.data?.getStringExtra(VeritasPdfViewerActivity.EXTRA_DOCUMENT_ID)
            val index = result.data?.getIntExtra(VeritasPdfViewerActivity.EXTRA_READER_INDEX, -1) ?: -1
            if (documentId != null && documentId == viewModel.uiState.value.activeDocument?.id &&
                index >= 0 && !viewModel.isReaderPlaying && index != viewModel.currentReaderIndex) {
                viewModel.moveTo(index, autoPlay = false)
            }
        }
    }
    val importFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.prepareImport(uri)
        }
    }
    fun openOriginalView(metadata: SavedDocument) {
        val isPdf = metadata.originalMimeType.contains("pdf", ignoreCase = true) ||
            metadata.originalFileName.endsWith(".pdf", ignoreCase = true) ||
            documentRepository.originalFile(metadata)?.takeIf { it.isFile }?.let { file ->
                runCatching {
                    java.io.DataInputStream(file.inputStream()).use { input ->
                        ByteArray(4).also(input::readFully).decodeToString() == "%PDF"
                    }
                }.getOrDefault(false)
            } == true
        if (isPdf) {
            originalPdfLauncher.launch(VeritasPdfViewerActivity.intent(context, metadata.id, viewModel.currentReaderIndex))
        } else {
            viewModel.updateState { it.copy(showCanvasView = true) }
        }
    }

    LaunchedEffect(uiState.recordMode, uiState.recordAwaitingDecision, uiState.recordStartedAt) {
        while (uiState.recordMode && !uiState.recordAwaitingDecision && uiState.recordStartedAt > 0L) {
            val elapsed =
                ((System.currentTimeMillis() - uiState.recordStartedAt) / 1000L).coerceAtLeast(0L)
            viewModel.updateState { it.copy(recordElapsedSeconds = elapsed) }
            delay(1_000L)
        }
    }

    val activeDocId = uiState.activeDocument?.id
    val launcherSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    LaunchedEffect(
        launcherSystemDark,
        uiState.readerSettings.themeId,
        uiState.readerSettings.themePackId,
        uiState.readerSettings.customAccentColor,
        uiState.readerSettings.adaptiveCover,
        uiState.readerSettings.amoledMode,
        uiState.readerSettings.reduceMotion,
        uiState.readerSettings.reduceTransparency,
        uiState.readerSettings.glassFloatingControls,
        uiState.readerSettings.uiFontId,
        uiState.readerSettings.launcherPalette,
        uiState.readerSettings.splashAnimation,
        uiState.readerSettings.scatteredCards,
        activeDocId
    ) {
        VeritasThemeState.themeId = uiState.readerSettings.themeId
        VeritasThemeState.themePackId = uiState.readerSettings.themePackId
        VeritasThemeState.customAccentColor = uiState.readerSettings.customAccentColor
        VeritasThemeState.adaptiveCover = uiState.readerSettings.adaptiveCover
        VeritasThemeState.uiFontId = uiState.readerSettings.uiFontId
        VeritasThemeState.reduceMotion = uiState.readerSettings.reduceMotion
        VeritasThemeState.reduceTransparency = uiState.readerSettings.reduceTransparency
        VeritasThemeState.glassFloatingControls = uiState.readerSettings.glassFloatingControls
        VeritasThemeState.amoledMode = uiState.readerSettings.amoledMode
        VeritasThemeState.launcherPalette = uiState.readerSettings.launcherPalette
        VeritasThemeState.splashAnimation = uiState.readerSettings.splashAnimation
        VeritasThemeState.scatteredCards = uiState.readerSettings.scatteredCards
        VeritasThemeState.activeDocumentId = activeDocId
        VernCustomization.queueLauncherPalette(uiState.readerSettings)
    }




    MainIntentAndRoutingEffects(
        context = context,
        viewModel = viewModel,
        uiState = uiState,
        initialShare = initialShare,
        openVoiceStudioOnStart = openVoiceStudioOnStart,
        widgetAction = widgetAction,
        noteId = noteId,
        onOpenInNotes = ::openInNotes,
        onPendingShareChooserChange = { share -> if (share != null) routeIncomingShare(share) else pendingShareChooser = null }
    )


    val showSetup = uiState.showTutorial && !uiState.hasCompletedOnboarding && OnboardingController.activeStep == null
    VeritasTheme(systemBarColor = com.veritas.reader.ui.screens.OnboardingPaper.takeIf { showSetup }) {
        val scanImport = rememberScanImportAction(
            onImageSelected = { viewModel.prepareImport(it) },
            onError = { message -> viewModel.updateState { it.copy(importMessage = message) } }
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = uiState.activeDocument,
                transitionSpec = {
                    // Keep the outgoing page behind the arriving one so the canvas never flashes through.
                    (fadeIn(animationSpec = tween(190, easing = LinearOutSlowInEasing)) togetherWith
                        fadeOut(animationSpec = tween(65, delayMillis = 145))).using(null)
                },
                contentKey = { it?.id },
                label = "reader_transition"
            ) { activeDoc ->
                if (activeDoc == null) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        var chapterCardBounds by remember { mutableStateOf<Rect?>(null) }
                        var chapterSheetOpen by rememberSaveable { mutableStateOf(false) }
                        val promptTarget = chapterCardBounds?.takeIf {
                            it.width > 0 && it.height > 0 && uiState.showFirstChapterPrompt &&
                                !uiState.showTutorial && uiState.activeDocument == null &&
                                OnboardingController.activeStep == null && !chapterSheetOpen
                        }
                        val dismissChapterPrompt = {
                            viewModel.updateState { it.copy(showFirstChapterPrompt = false) }
                        }
                        Box(
                            Modifier.fillMaxSize().then(
                                if (promptTarget != null) Modifier.clearAndSetSemantics {} else Modifier
                            )
                        ) {
                        LibraryScreen(
                            uiState = uiState,
                            onRefreshInsights = viewModel::refreshReadingInsights,
                            sharedTransitionScope = null,
                            animatedVisibilityScope = this@AnimatedContent,
                                onDismissOpeningDocument = { viewModel.dismissOpeningDocument() },
                    onDraftTextChange = { text -> viewModel.updateState { it.copy(draftText = text) } },
                    widgetAction = uiState.pendingWidgetAction ?: widgetAction,
                    onCreateFromDraft = {
                        if (WebArticleExtractor.looksLikeUrl(uiState.draftText)) viewModel.importWebArticle(
                            uiState.draftText
                        )
                        else viewModel.createAndOpenDocument(
                            "Pasted text",
                            uiState.draftText,
                            "Pasted"
                        )
                    },
                    onImportWebArticle = { viewModel.importWebArticle(it) },
                    onExportStudyGuidePdf = { document -> viewModel.exportStudyGuidePdf(document) },
                    onImportFile = { importFileLauncher.launch(readableImportMimeTypes()) },
                    onImportImage = scanImport,
                    onAdvancedPdfImport = { viewModel.updateState { it.copy(showPdfImportTools = true) } },
                    onOpenFileBrowser = { viewModel.openFileBrowser() },
                    onOpenClassicsCatalog = { viewModel.updateState { it.copy(showClassicsCatalog = true) } },
                    onDownloadClassicBook = { viewModel.downloadClassicBook(it) },
                    onBrowseClassicArchive = viewModel::openBookArchive,
                    onOpenReadingLists = { viewModel.updateState { it.copy(showReadingLists = true) } },
                    onCreateReadingList = { title, docId -> viewModel.createReadingList(title, docId) },
                    onAddDocumentToReadingList = viewModel::addDocumentToReadingList,
                    onRemoveDocumentFromReadingList = viewModel::removeDocumentFromReadingList,
                    onOpenReadingHistory = { viewModel.updateState { it.copy(showReadingHistory = true) } },
                    onOpenDocument = { viewModel.openSavedDocument(it) },
                    onOpenDocumentAt = { document, sentenceIndex ->
                        viewModel.openSavedDocument(
                            document,
                            sentenceIndex
                        )
                    },
                    onClearContinueDocument = { viewModel.clearContinueReading(it) },
                    onPlayPauseContinue = { viewModel.playOrPauseSavedDocument(it) },
                    onDeleteDocument = { doc -> viewModel.updateState { it.copy(deleteTarget = doc) } },
                    onToggleQueue = { viewModel.toggleQueue(it) },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onRenameDocument = { doc ->
                        viewModel.updateState {
                            it.copy(
                                renameTarget = doc,
                                renameDraft = doc.title
                            )
                        }
                    },
                    onSetCollection = { doc ->
                        viewModel.updateState {
                            it.copy(
                                collectionTarget = doc,
                                collectionDraft = doc.collection
                            )
                        }
                    },
                    onShowDetails = { doc -> viewModel.updateState { it.copy(detailsTarget = doc) } },
                    isQueued = { doc -> uiState.queuedDocuments.any { it.id == doc.id } },
                    onPlayQueue = { viewModel.playQueue() },
                    onMoveQueueUp = { viewModel.moveQueueItem(it, -1) },
                    onMoveQueueDown = { viewModel.moveQueueItem(it, 1) },
                    onMoveQueueBy = { document, offset -> viewModel.moveQueueItem(document, offset) },
                    onRemoveFromQueue = { viewModel.toggleQueue(it) },
                    onClearQueue = { viewModel.clearQueue() },
                    onReorderDocuments = { viewModel.reorderDocuments(it) },
                    onOpenSyncCenter = { viewModel.updateState { it.copy(showSyncCenter = true) } },
                    onOpenSettingsHub = { viewModel.updateState { it.copy(showSettingsHub = true) } },
                    onRefreshMainPage = { viewModel.refreshAll() },
                    onBatchDeleteDocuments = { viewModel.deleteDocuments(it) },
                    onBatchFavoriteDocuments = { viewModel.favoriteDocuments(it) },
                    onBatchQueueDocuments = { viewModel.queueDocuments(it) },
                    onBatchSetCollectionDocuments = { ids, collection ->
                        viewModel.setCollectionForDocuments(
                            ids,
                            collection
                        )
                    },
                    onDeleteAnnotations = { keys -> viewModel.deleteAnnotations(keys) },
                    onClearTargetHomeTab = { viewModel.clearTargetHomeTab() },
                    onWriteGeneralNote = { viewModel.updateState { it.copy(showGeneralNotesEditor = true, generalNoteEditorTarget = null) } },
                    onEditGeneralNote = { note -> viewModel.updateState { it.copy(showGeneralNotesEditor = true, generalNoteEditorTarget = note) } },
                    onRemoveVocabularyWord = { docId, word -> viewModel.removeVocabularyWord(docId, word) },
                    onRefreshVocabularyMeaning = { docId, entry -> viewModel.refreshVocabularyMeaning(docId, entry) },
                    onClearReadingHistory = { viewModel.clearReadingHistory() },
                    onRemoveReadingHistoryEntry = viewModel::removeReadingHistoryEntry,
                    onToggleGeneralNotePin = viewModel::toggleGeneralNotePin,
                    onChangeGeneralNoteColor = viewModel::changeGeneralNoteColor,
                    onDeleteGeneralNote = viewModel::deleteGeneralNote,
                    onRateFlashcardRecall = { id, recall, complete -> viewModel.rateFlashcardRecall(id, recall, complete) },
                    onDeleteFlashcard = viewModel::deleteFlashcard,
                    onImportFlashcards = { name, cards -> viewModel.importFlashcards("pasted", name, cards) },
                    onRenameFlashcardSet = viewModel::renameFlashcardSet,
                    onDeleteFlashcardSet = viewModel::deleteFlashcardSet,
                    onSaveReaderSettings = { viewModel.saveReaderSettings(it) },
                    notesSettings = uiState.notesSettings,
                    onSaveNotesSettings = viewModel::saveNotesSettings,
                    noteCollectionActions = NoteCollectionActions(
                        createNotebook = viewModel::createNoteNotebook,
                        moveNote = viewModel::moveGeneralNoteToNotebook,
                        setLabels = viewModel::setGeneralNoteLabels,
                        createLabel = viewModel::createNoteLabel,
                        setDraftLabels = viewModel::setDraftNoteLabels,
                        renameNotebook = viewModel::renameNoteNotebook,
                        deleteNotebook = viewModel::deleteNoteNotebook,
                        restoreNote = viewModel::restoreTrashedGeneralNote,
                        permanentlyDeleteNote = viewModel::permanentlyDeleteTrashedGeneralNote,
                        revisionsFor = viewModel::noteRevisions,
                        restoreRevision = viewModel::restoreGeneralNoteRevision
                    ),
                    onSearchLibraryContent = viewModel::searchLibraryContent,
                    onSaveQuiz = viewModel::saveQuiz,
                    onDeleteQuiz = viewModel::deleteQuiz,
                    onRecordQuizScore = viewModel::recordQuizScore,
                    onGenerateInAppQuiz = { doc, prompt -> viewModel.generateInAppQuiz(doc, prompt) },
                    onOpenAiStudyTools = { viewModel.updateState { it.withVisibility(VeritasScreen.AI_STUDY_TOOLS, true) } },
                    onCancelAiStudyGeneration = { viewModel.cancelAiStudyGeneration() },
                    firstChapter = if (uiState.hasCompletedOnboarding && !uiState.questChecklistDismissed ||
                        OnboardingController.activeStep == OnboardingStep.CHECKLIST_SPOTLIGHT
                    ) {
                        { openImport ->
                            val reading = uiState.documents.firstOrNull {
                                it.sourceLabel == "System" && it.title == "Vern Welcome Guide"
                            } ?: uiState.documents.firstOrNull { it.chunkCount > 0 }
                            OnboardingQuestChecklist(
                                state = uiState,
                                hasReading = reading != null,
                                onAddReading = openImport,
                                onOpenReading = {
                                    if (reading != null) viewModel.openSavedDocument(reading)
                                    else openImport()
                                },
                                onWriteNote = {
                                    viewModel.updateState { it.copy(showGeneralNotesEditor = true, generalNoteEditorTarget = null) }
                                },
                                onStartTour = {
                                    viewModel.createWelcomeDocumentSilently()
                                    OnboardingController.activeStep = OnboardingStep.WELCOME
                                },
                                onDismissQuests = viewModel::dismissQuestChecklist,
                                showSheet = chapterSheetOpen,
                                onShowSheetChange = { shown ->
                                    if (shown && uiState.showFirstChapterPrompt) dismissChapterPrompt()
                                    chapterSheetOpen = shown
                                },
                                onCardPositioned = { chapterCardBounds = it }
                            )
                        }
                    } else null
                )
                        }
                        FirstChapterPrompt(
                            target = promptTarget,
                            onOpen = {
                                dismissChapterPrompt()
                                chapterSheetOpen = true
                            },
                            onDismiss = dismissChapterPrompt,
                            modifier = Modifier.matchParentSize()
                        )
            }
        } else {
            val activeDocument = activeDoc
            val readerIndex = viewModel.currentReaderIndex
            val readerPlaying = viewModel.isReaderPlaying
            val readerOwnsAudio = PlaybackStateStore.activeDocumentId == activeDocument.id
                val activeMetadata = activeDocument.id?.let { activeId ->
                    uiState.documents.firstOrNull { it.id == activeId }
                }
                if (uiState.showCanvasView && activeMetadata != null) {
                    // Same cache key ReaderScreen uses, so both views read one parse of the
                    // document rather than each deriving pages their own way.
                    val readerModel = remember(
                        activeDocument.rawText,
                        activeDocument.pageCount,
                        activeDocument.chunks.size
                    ) {
                        ReaderTextModelCache.get(
                            activeDocument.id,
                            activeDocument.rawText,
                            activeDocument.pageCount
                        )
                    }
                    val activeSentence = readerModel.sentences
                        .getOrNull(readerIndex)
                    ActualDocumentView(
                        document = activeMetadata,
                        repository = documentRepository,
                        readerDocument = activeDoc,
                        animatedVisibilityScope = this@AnimatedContent,
                        activeSentencePage = activeSentence?.pageNumber ?: 0,
                        activeSentenceText = activeSentence?.text.orEmpty(),
                        isPlaying = readerPlaying,
                        statusMessage = PlaybackStateStore.statusMessage,
                        queueCount = PlaybackStateStore.queueCount,
                        rate = PlaybackStateStore.rate,
                        pitch = PlaybackStateStore.pitch,
                        fontSizeSp = uiState.readerSettings.fontSizeSp,
                        canGoPrevious = readerIndex > 0,
                        canGoNext = readerIndex < activeDocument.chunks.lastIndex || PlaybackStateStore.queueCount > 0,
                        onPrevious = {
                            viewModel.moveTo(
                                (readerIndex - 1).coerceAtLeast(0),
                                false
                            )
                        },
                        onPlayPause = { viewModel.playOrPause() },
                        onNext = {
                            viewModel.moveTo(
                                (readerIndex + 1).coerceAtMost(
                                    activeDocument.chunks.size - 1
                                ),
                                false
                            )
                        },
                        onPageChanged = { pageIndex, _ ->
                            // Eyes don't move the voice: while TTS is playing, browsing pages
                            // in Original view must never jump playback (moveTo notifies the
                            // service mid-utterance and it re-reads from the viewed page).
                            val targetPageNumber = pageIndex + 1
                            if (!readerPlaying && activeSentence?.pageNumber != targetPageNumber) {
                                // Original View emits settled page changes. Update before a quick switch to Text.
                                val targetIndex = readerModel.sentences.indices.minByOrNull {
                                    kotlin.math.abs(readerModel.sentences[it].pageNumber - targetPageNumber)
                                }
                                if (targetIndex != null && targetIndex != readerIndex) viewModel.moveTo(targetIndex, autoPlay = false)
                            }
                        },
                        onOpenExternal = {
                            openOriginalDocument(
                                context,
                                documentRepository,
                                activeMetadata
                            )
                        },
                        onRateChange = {
                            viewModel.saveVoiceSettings(
                                uiState.voiceSettings.copy(
                                    preferredRate = it
                                )
                            )
                        },
                        onPitchChange = {
                            viewModel.saveVoiceSettings(
                                uiState.voiceSettings.copy(
                                    preferredPitch = it
                                )
                            )
                        },
                        onFontSizeChange = {
                            viewModel.saveReaderSettings(
                                uiState.readerSettings.copy(
                                    fontSizeSp = it
                                )
                            )
                        },
                        onSectionSpacingChange = {
                            viewModel.saveReaderSettings(
                                uiState.readerSettings.copy(
                                    sectionSpacingDp = it
                                )
                            )
                        },
                        onOpenVoiceStudio = { viewModel.updateState { it.copy(showVoiceStudio = true) } },
                        voices = uiState.ttsVoices,
                        voiceSettings = uiState.voiceSettings,
                        onVoiceSelected = { voice ->
                            val detectedEngine = VoiceManager.engineForVoice(voice.name)
                            val targetEngine = if (detectedEngine != null) {
                                detectedEngine
                            } else if (VoiceManager.isVeritasEngine(uiState.voiceSettings.enginePackage)) {
                                ""
                            } else {
                                uiState.voiceSettings.enginePackage
                            }
                            val targetEngineLabel = when (targetEngine) {
                                VoiceManager.VERITAS_LITE -> "Vern Lite"
                                VoiceManager.VERITAS_STUDIO -> "Vern Studio"
                                VoiceManager.KITTEN -> "Kitten"
                                "" -> "System default"
                                else -> uiState.voiceSettings.engineLabel
                            }
                            viewModel.saveVoiceSettings(
                                uiState.voiceSettings.copy(
                                    voiceName = voice.name,
                                    voiceLabel = voice.label.ifBlank { voice.name },
                                    localeTag = voice.localeTag,
                                    enginePackage = targetEngine,
                                    engineLabel = targetEngineLabel
                                )
                            )
                        },
                        onSelectionAction = { action, text, page ->
                            viewModel.handleOriginalSelection(context, OriginalSelectionRequest(activeMetadata.id, action, text, page))
                        },
                        onReadFromSentence = { selectedText, selectionPage ->
                            val cleanText = selectedText.trim()
                            if (cleanText.isNotBlank()) {
                                // This used to score every chunk in the document by word overlap
                                // with no notion of which page the tap came from, so a phrase that
                                // recurs anywhere earlier won, and a miss fell back to sentence 0 —
                                // "continue from here" would start the book over. PdfSelectionLocator
                                // searches the tapped page first, widens to its neighbours, and only
                                // then considers the whole document.
                                val match = PdfSelectionLocator.findMatch(
                                    selectedText = cleanText,
                                    model = readerModel,
                                    currentPage = selectionPage,
                                    preferredSentenceIndex = readerIndex
                                )
                                if (match != null) {
                                    viewModel.moveTo(match.chunkIndex, autoPlay = true, forcePlaybackStart = true, charOffset = match.charOffset)
                                } else android.widget.Toast.makeText(context, "This selection could not be located in the extracted text.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        initialPaperToneMode = PaperToneMode.fromString(uiState.readerSettings.paperToneMode),
                        onPaperToneModeChange = { newMode ->
                            viewModel.saveReaderSettings(
                                uiState.readerSettings.copy(paperToneMode = newMode.name.lowercase())
                            )
                        },
                        onClose = { viewModel.updateState { it.copy(showCanvasView = false) } }
                    )
                } else {
                    ReaderScreen(
                        state = ReaderScreenState(
                            document = activeDocument,
                            currentIndex = readerIndex,
                            isPlaying = readerPlaying,
                            isBackgroundActive = readerOwnsAudio && PlaybackStateStore.isForegroundActive,
                            rate = PlaybackStateStore.rate,
                            pitch = PlaybackStateStore.pitch,
                            statusMessage = PlaybackStateStore.statusMessage,
                            queueCount = PlaybackStateStore.queueCount,
                            isQueued = uiState.queuedDocuments.any { it.id == activeDocument.id },
                            annotations = uiState.annotations,
                            pronunciationRuleCount = uiState.pronunciationRules.size,
                            readerSettings = uiState.readerSettings,
                            voiceSettings = uiState.voiceSettings,
                            narrationSettings = uiState.narrationSettings,
                            askAiSettings = uiState.askAiSettings,
                            searchQuery = uiState.searchQuery,
                            searchMatches = uiState.searchMatches,
                            searchCursor = uiState.searchCursor,
                            outlineEntries = uiState.documentOutline,
                            hasCanvas = activeMetadata?.originalFileName?.isNotBlank() == true || activeDocument.chunks.any {
                                it.trim().startsWith("[CANVAS")
                            },
                            sleepTimerDurationMillis = PlaybackStateStore.sleepTimerDurationMillis,
                            sleepTimerEndsAtMillis = PlaybackStateStore.sleepTimerEndsAtMillis,
                            sleepTimerAction = PlaybackStateStore.sleepTimerAction,
                            readingListCount = uiState.readingListCatalog.activeLists.size,
                            activeDocumentReadingListCount = activeDocument.id?.let { activeId ->
                                uiState.readingListCatalog.listsContaining(activeId)
                                    .count { !it.archived }
                            } ?: 0,
                            voices = uiState.ttsVoices,
                            readerMode = PlaybackStateStore.readerMode
                        ),
                        sharedTransitionScope = null,
                        animatedVisibilityScope = this@AnimatedContent,
                        listState = rememberLazyListState(),
                        hasCanvas = activeMetadata?.originalFileName?.isNotBlank() == true || activeDocument.chunks.any {
                            it.trim().startsWith("[CANVAS")
                        },
                        onBackToLibrary = { viewModel.returnToLibrary() },
                        onSentenceClick = { viewModel.moveTo(it, false) },
                        onSentenceDoubleTap = {
                            viewModel.moveTo(
                                it,
                                autoPlay = true,
                                forcePlaybackStart = true
                            )
                        },
                        onPlayPause = { viewModel.playOrPause() },
                        onStop = { viewModel.stopAndForgetPlayback() },
                        onPrevious = {
                            viewModel.moveTo(
                                (readerIndex - 1).coerceAtLeast(
                                    0
                                ), false
                            )
                        },
                        onNext = {
                            viewModel.moveTo(
                                (readerIndex + 1).coerceAtMost(
                                    activeDocument.chunks.size - 1
                                ), false
                            )
                        },
                        onRateChange = {
                            viewModel.saveVoiceSettings(
                                uiState.voiceSettings.copy(
                                    preferredRate = it
                                )
                            )
                        },
                        onPitchChange = {
                            viewModel.saveVoiceSettings(
                                uiState.voiceSettings.copy(
                                    preferredPitch = it
                                )
                            )
                        },
                        onFontSizeChange = {
                            viewModel.saveReaderSettings(
                                uiState.readerSettings.copy(
                                    fontSizeSp = it
                                )
                            )
                        },
                        onSectionSpacingChange = {
                            viewModel.saveReaderSettings(
                                uiState.readerSettings.copy(
                                    sectionSpacingDp = it
                                )
                            )
                        },
                        onToggleQueue = {
                            uiState.documents.firstOrNull { it.id == uiState.activeDocument?.id }
                                ?.let(viewModel::toggleQueue)
                        },
                        onToggleBookmark = viewModel::toggleBookmark,
                        onEditNote = { idx -> viewModel.beginSentenceNote(listOf(idx)) },
                        onEditNotes = { idxs -> viewModel.beginSentenceNote(idxs) },
                        onSearchQueryChange = { query -> viewModel.updateSearchQuery(query) },
                        onNextSearchMatch = { viewModel.moveToNextSearchMatch() },
                        onPreviousSearchMatch = { viewModel.moveToPreviousSearchMatch() },
                        onReadingSettingsChange = { viewModel.saveReaderSettings(it) },
                        onOpenReaderSettings = { viewModel.updateState { it.copy(showReaderSettings = true) } },
                        onOpenPronunciationRules = {
                            viewModel.updateState {
                                it.copy(
                                    showPronunciationRules = true
                                )
                            }
                        },
                        onOpenVoiceStudio = { viewModel.updateState { it.copy(showVoiceStudio = true) } },
                        onOpenNarrationStudio = {
                            viewModel.updateState {
                                it.copy(
                                    showNarrationStudio = true
                                )
                            }
                        },
                        onOpenDocumentNotes = { viewModel.openDocumentNotes() },
                        onExportStudyGuidePdf = { viewModel.exportStudyGuidePdf() },
                        onOpenCanvas = { activeMetadata?.let(::openOriginalView) },
                        onOpenStudyTools = { viewModel.updateState { it.copy(showAiStudyTools = true) } },
                        onOpenTranslationTools = {
                            viewModel.updateState {
                                it.copy(
                                    showTranslationTools = true
                                )
                            }
                        },
                        onOpenSleepTimer = { viewModel.updateState { it.copy(showSleepTimerDialog = true) } },
                        onOpenReadingLists = { viewModel.updateState { it.copy(showReadingLists = true) } },
                        onOpenReadingHistory = { viewModel.updateState { it.copy(showReadingHistory = true) } },
                        onAskCurrentSection = {
                            uiState.activeDocument?.let { document ->
                                shareToAi(
                                    context = context,
                                    document = document,
                                    scope = ShareScope.CURRENT_SECTION,
                                    selection = null,
                                    customPageRange = null,
                                    settings = uiState.askAiSettings
                                )
                            }
                        },
                        onSelectAskAiAssistant = { option, installedPackage ->
                            viewModel.saveAskAiSettings(
                                uiState.askAiSettings.copy(
                                    assistantId = option.id,
                                    assistantLabel = option.label,
                                    packageName = installedPackage
                                )
                            )
                        },
                        onOpenTextEditor = { viewModel.openCurrentPartTextEditor() },
                        onStartRecord = { viewModel.startRecordSoundFile() },
                        onExportAudio = { showAudioExportOptions = true },
                        onCopySelection = { copyTextToClipboard(context, "Vern selection", it) },
                        onShareSelection = { sharePlainText(context, "Vern selection", it) },
                        onDefineSelection = { selection ->
                            showDictionaryLookup(context, selection.text, DictionaryLookupAnchor(
                                uiState.activeDocument?.id, selection.firstSentenceIndex,
                                uiState.activeDocument?.chunks?.getOrNull(selection.firstSentenceIndex)))
                        },
                        onTranslateSelection = {
                            viewModel.appendVocabularyWord(it.text, "Looked up translation.", selectedSentenceIndex = it.firstSentenceIndex)
                            openGoogleTranslate(context, it.text)
                        },
                        onAskAiSelection = {
                            viewModel.appendVocabularyWord(it, "Asked AI for explanation.")
                            askAiWithSelection(
                                context,
                                uiState.askAiSettings,
                                it
                            )
                        },
                        onEditSpeechSelection = { selection ->
                            viewModel.updateState {
                                it.copy(
                                    showPronunciationRules = true,
                                    newRuleFind = selection.replace(Regex("\\s+"), " ").trim()
                                        .take(120),
                                    newRuleReplaceWith = "", editingRuleId = null,
                                    newRuleKind = SpeechRuleKind.LITERAL,
                                    newRuleCaseSensitive = false, newRuleSkip = false, newRuleError = null
                                )
                            }
                        },
                        onReadSelection = { sendSelectionSpeechIntent(context, it) },
                        onEditExtractedSelection = { selection ->
                            viewModel.openSelectionTextEditor(
                                selection.sentenceIndexes
                            )
                        },
                        onPlayQueue = { viewModel.playQueue() },
                        onVoiceSelected = { voice ->
                            val detectedEngine = VoiceManager.engineForVoice(voice.name)
                            val targetEngine = if (detectedEngine != null) {
                                detectedEngine
                            } else if (VoiceManager.isVeritasEngine(uiState.voiceSettings.enginePackage)) {
                                ""
                            } else {
                                uiState.voiceSettings.enginePackage
                            }
                            val targetEngineLabel = when (targetEngine) {
                                VoiceManager.VERITAS_LITE -> "Vern Lite"
                                VoiceManager.VERITAS_STUDIO -> "Vern Studio"
                                VoiceManager.KITTEN -> "Kitten"
                                "" -> "System default"
                                else -> uiState.voiceSettings.engineLabel
                            }
                            viewModel.saveVoiceSettings(
                                uiState.voiceSettings.copy(
                                    voiceName = voice.name,
                                    voiceLabel = voice.label.ifBlank { voice.name },
                                    localeTag = voice.localeTag,
                                    enginePackage = targetEngine,
                                    engineLabel = targetEngineLabel
                                )
                            )
                        },
                        onAddBookmarkGroup = { indexes, colorHex ->
                            viewModel.addBookmarkGroup(indexes, colorHex)
                        },
                        onShareToAi = { scope, selection, range, noPrompt ->
                            uiState.activeDocument?.let { doc ->
                                val mappedSelection = selection?.let {
                                    ReaderTextSelection(
                                        partIndex = it.partIndex,
                                        start = it.start,
                                        endExclusive = it.endExclusive,
                                        text = it.text,
                                        sentenceIndexes = it.sentenceIndexes
                                    )
                                }
                                shareToAi(context, doc, scope, mappedSelection, range, uiState.askAiSettings, noPrompt)
                            }
                        },
                        showShareToAi = false,
                        onDismissShareToAi = {
                            viewModel.updateState { it.copy(showAiStudyTools = false) }
                        },
                        onReaderModeChange = { mode ->
                            if (mode == ReaderMode.ORIGINAL) activeMetadata?.let(::openOriginalView)
                            else PlaybackStateStore.readerMode = mode
                        },
                        onPaperToneModeChange = { newTone ->
                            viewModel.saveReaderSettings(
                                uiState.readerSettings.copy(
                                    paperToneMode = newTone.name.lowercase()
                                )
                            )
                        },
                        onAddGeneralNote = {
                            viewModel.updateState { it.copy(showGeneralNotesEditor = true, generalNoteEditorTarget = null) }
                        },
                        onDismissGestureTipsPermanently = {
                            viewModel.saveReaderSettings(uiState.readerSettings.copy(showGestureTips = false))
                        }
                    )
                }
            }
        }
    }

        if (showAudioExportOptions) {
            uiState.activeDocument?.let { document ->
                AudioExportOptions(
                    chunks = document.chunks,
                    rate = PlaybackStateStore.rate,
                    onExport = { format ->
                        showAudioExportOptions = false
                        viewModel.exportActiveDocumentToAudio(format)
                    },
                    onDismiss = { showAudioExportOptions = false }
                )
            }
        }

        if (uiState.recordMode || uiState.recordAwaitingDecision) {
            FloatingRecordOverlay(
                inProgress = uiState.exportInProgress,
                fileReady = uiState.exportedAudioFile != null,
                awaitingDecision = uiState.recordAwaitingDecision,
                elapsedSeconds = uiState.recordElapsedSeconds,
                onStopRecording = { viewModel.stopRecordSoundFile() },
                onSave = { viewModel.saveRecordedSoundFile() },
                onDiscard = { viewModel.discardRecordedSoundFile() }
            )
        }






        // ── Extracted Dialog & Tool Hosts ────────────────────────────────────
        MainSettingsDialogsHost(
            viewModel = viewModel,
            uiState = uiState,
            showUnrestrictedBatteryDialog = showUnrestrictedBatteryDialog,
            onDismissUnrestrictedBatteryDialog = { showUnrestrictedBatteryDialog = false }
        )

        MainCatalogAndToolsDialogsHost(
            viewModel = viewModel,
            uiState = uiState,
            pendingShareChooser = pendingShareChooser,
            onDismissShareChooser = { pendingShareChooser = null },
            onOpenInNotes = ::openInNotes,
            onImportToReader = ::importToReader
        )

        MainDialogsHost(
            viewModel = viewModel,
            uiState = uiState,
            onReviewSettings = { pendingSettingsUri = it },
            onOpenFilePicker = { importFileLauncher.launch(readableImportMimeTypes()) }
        )

        MainOnboardingAndTourHost(
            viewModel = viewModel,
            uiState = uiState
        )

        pendingSettingsUri?.let { uri ->
            androidx.compose.runtime.key(uri) {
                VernSettingsReviewDialog(
                    uri = uri,
                    current = uiState.readerSettings,
                    busy = uiState.backupInProgress,
                    onApply = { profile ->
                        pendingSettingsUri = null
                        viewModel.applySettingsProfile(profile)
                    },
                    onDismiss = { pendingSettingsUri = null }
                )
            }
        }

    }
}
