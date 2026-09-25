package com.veritas.reader


import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.content.ContentUris
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.KeyEvent
import android.view.Menu
import android.widget.Toast
import android.app.Activity
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.foundation.BorderStroke
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.veritas.reader.ui.*
import com.veritas.reader.ui.ReaderViewModel
import com.veritas.reader.ui.VeritasPendingImport
import com.veritas.reader.ui.screens.AskAiSettingsDialog
import com.veritas.reader.ui.screens.DocumentNotesDialog
import com.veritas.reader.ui.screens.FeatureDropdownMenuItem
import com.veritas.reader.ui.screens.LibraryScreen
import com.veritas.reader.ui.screens.GeneralNotesEditor
import com.veritas.reader.ui.screens.NarrationStudioDialog
import com.veritas.reader.ui.screens.PronunciationRulesDialog
import com.veritas.reader.ui.screens.ReaderScreen
import com.veritas.reader.ui.screens.ReaderScreenState
import com.veritas.reader.ui.screens.ReaderSettingsDialog
import com.veritas.reader.ui.screens.AccessibilitySettingsDialog
import com.veritas.reader.ui.screens.ReadingListsDialog
import com.veritas.reader.ui.screens.SettingsHubDialog
import com.veritas.reader.ui.screens.StorageDialog
import com.veritas.reader.ui.screens.formatVeritasBytes
import com.veritas.reader.ui.screens.UserManualDialog
import com.veritas.reader.ui.screens.AboutDialog
import com.veritas.reader.ui.screens.SleepTimerDialog
import com.veritas.reader.ui.screens.UpdateAvailableDialog
import com.veritas.reader.ui.screens.ReleaseNotesDialog
import com.veritas.reader.ui.screens.ClassicsCatalogDialog
import com.veritas.reader.ui.screens.OceanOfPdfBrowserDialog
import com.veritas.reader.ui.screens.BookCatalogBrowserDialog
import com.veritas.reader.ui.screens.VeritasHomeTab
import com.veritas.reader.ui.screens.VoiceStudioDialog
import com.veritas.reader.ReaderMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.veritas.reader.ui.screens.OnboardingQuestChecklist
import com.veritas.reader.ui.screens.OnboardingSpotlightOverlay
import com.veritas.reader.ui.screens.RevampedOnboardingFlow
import com.veritas.reader.ui.screens.ConfettiOverlay
import com.veritas.reader.ui.OnboardingStep
import com.veritas.reader.ui.OnboardingController
import androidx.compose.ui.layout.onGloballyPositioned
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt



internal const val MAIN_ACTIVITY_TAG = "MainActivity"


@Composable
internal fun VeritasReaderApp(
    initialSharedText: String,
    initialSharedUri: Uri?,
    isShareToNotes: Boolean = false,
    openVoiceStudioOnStart: Boolean,
    widgetAction: String? = null,
    noteId: String? = null
) {
    val context = LocalContext.current
    val viewModel: ReaderViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var showStorageTools by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showUnrestrictedBatteryDialog by remember { mutableStateOf(false) }
    var pendingShareChooser by remember { mutableStateOf<Pair<String, Uri?>?>(null) }

    val batteryPrefs = remember { context.getSharedPreferences("veritas_reader_library", Context.MODE_PRIVATE) }
    LaunchedEffect(Unit) {
        if (!isBatteryOptimizationIgnored(context) && !batteryPrefs.getBoolean("battery_unrestricted_never_ask", false)) {
            showUnrestrictedBatteryDialog = true
        }
    }

    fun openInNotes(text: String, uri: Uri?) {
        val noteId = java.util.UUID.randomUUID().toString()
        val noteTitle = if (text.isNotBlank()) {
            text.lineSequence().firstOrNull { it.isNotBlank() }?.take(50)?.trim() ?: ""
        } else ""
        val savedImagePath = if (uri != null) {
            runCatching {
                val notesMediaDir = File(context.filesDir, "notes_media").apply { if (!exists()) mkdirs() }
                val destFile = File(notesMediaDir, "img_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                destFile.absolutePath
            }.getOrNull() ?: uri.toString()
        } else null

        viewModel.updateState {
            it.copy(
                showGeneralNotesEditor = true,
                generalNoteEditorTarget = GeneralNote(
                    id = noteId,
                    title = noteTitle,
                    content = text,
                    updatedAt = System.currentTimeMillis(),
                    imageUrl = savedImagePath
                )
            )
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
        activity?.onIncomingShare = { text, uri, toNotes ->
            if (toNotes) {
                openInNotes(text, uri)
            } else {
                pendingShareChooser = Pair(text, uri)
            }
        }
        onDispose {
            activity?.onIncomingShare = null
        }
    }
    val documentRepository = remember(context) { DocumentRepository(context.applicationContext) }
    val importFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.prepareImport(uri)
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
    LaunchedEffect(
        uiState.readerSettings.themeId,
        uiState.readerSettings.themePackId,
        uiState.readerSettings.adaptiveCover,
        uiState.readerSettings.amoledMode,
        activeDocId
    ) {
        VeritasThemeState.themeId = uiState.readerSettings.themeId
        VeritasThemeState.themePackId = uiState.readerSettings.themePackId
        VeritasThemeState.adaptiveCover = uiState.readerSettings.adaptiveCover
        VeritasThemeState.uiFontId = uiState.readerSettings.uiFontId
        VeritasThemeState.reduceMotion = uiState.readerSettings.reduceMotion
        VeritasThemeState.amoledMode = uiState.readerSettings.amoledMode
        VeritasThemeState.activeDocumentId = activeDocId
    }




    MainIntentAndRoutingEffects(
        context = context,
        viewModel = viewModel,
        uiState = uiState,
        initialSharedText = initialSharedText,
        initialSharedUri = initialSharedUri,
        isShareToNotes = isShareToNotes,
        openVoiceStudioOnStart = openVoiceStudioOnStart,
        widgetAction = widgetAction,
        noteId = noteId,
        onOpenInNotes = ::openInNotes,
        onPendingShareChooserChange = { pendingShareChooser = it }
    )


    VeritasTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            @OptIn(ExperimentalSharedTransitionApi::class)
            SharedTransitionLayout {
                AnimatedContent(
                    targetState = uiState.activeDocument,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                    },
                    contentKey = { it?.id },
                    label = "reader_transition"
                ) { activeDoc ->
                    if (activeDoc == null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LibraryScreen(
                                uiState = uiState,
                                sharedTransitionScope = this@SharedTransitionLayout,
                                animatedVisibilityScope = this@AnimatedContent,
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
                    onImportFile = { importFileLauncher.launch(readableImportMimeTypes()) },
                    onImportImage = { importFileLauncher.launch(arrayOf("image/*")) },
                    onAdvancedPdfImport = { viewModel.updateState { it.copy(showPdfImportTools = true) } },
                    onOpenFileBrowser = { viewModel.openFileBrowser() },
                    onOpenClassicsCatalog = { viewModel.updateState { it.copy(showClassicsCatalog = true) } },
                    onDownloadClassicBook = { viewModel.downloadClassicBook(it) },
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
                    onClearReadingHistory = { viewModel.clearReadingHistory() },
                    onRemoveReadingHistoryEntry = viewModel::removeReadingHistoryEntry,
                    onToggleGeneralNotePin = viewModel::toggleGeneralNotePin,
                    onChangeGeneralNoteColor = viewModel::changeGeneralNoteColor,
                    onDeleteGeneralNote = viewModel::deleteGeneralNote,
                    onRateFlashcardRecall = viewModel::rateFlashcardRecall,
                    onDeleteFlashcard = viewModel::deleteFlashcard,
                    onImportFlashcards = { name, cards -> viewModel.importFlashcards("pasted", name, cards) },
                    onRenameFlashcardSet = viewModel::renameFlashcardSet,
                    onDeleteFlashcardSet = viewModel::deleteFlashcardSet,
                    onSaveReaderSettings = { viewModel.saveReaderSettings(it) },
                    onSearchLibraryContent = viewModel::searchLibraryContent,
                    onSaveQuiz = viewModel::saveQuiz,
                    onDeleteQuiz = viewModel::deleteQuiz,
                    onRecordQuizScore = viewModel::recordQuizScore,
                    onGenerateInAppFlashcards = { doc, prompt -> viewModel.generateInAppFlashcards(doc, prompt) },
                    onGenerateInAppQuiz = { doc, prompt -> viewModel.generateInAppQuiz(doc, prompt) },
                    onOpenAiStudyTools = { viewModel.updateState { it.copy(showAiStudyTools = true) } }
                )
                val areQuestsIncomplete = !uiState.questTourDone || !uiState.questImportDone || !uiState.questSpeedDone || !uiState.questBookmarkDone
                if (areQuestsIncomplete && !uiState.questChecklistDismissed) {
                    OnboardingQuestChecklist(
                        questTourDone = uiState.questTourDone,
                        questImportDone = uiState.questImportDone,
                        questSpeedDone = uiState.questSpeedDone,
                        questBookmarkDone = uiState.questBookmarkDone,
                        onStartTour = {
                            viewModel.createWelcomeDocumentSilently()
                            OnboardingController.activeStep = OnboardingStep.WELCOME
                        },
                        onDismissQuests = {
                            viewModel.dismissQuestChecklist()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            // Keep the card clear of the Add FAB anchored at the bottom end.
                            .fillMaxWidth(0.65f)
                            .padding(bottom = 90.dp)
                            .onGloballyPositioned { OnboardingController.updateBounds("quest_checklist", it) }
                    )
                }
            }
        } else {
            val activeDocument = activeDoc
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
                        .getOrNull(PlaybackStateStore.currentIndex)
                    var pageSettleJob by remember { mutableStateOf<Job?>(null) }
                    ActualDocumentView(
                        document = activeMetadata,
                        repository = documentRepository,
                        activeSentencePage = activeSentence?.pageNumber ?: 0,
                        activeSentenceText = activeSentence?.text.orEmpty(),
                        isPlaying = PlaybackStateStore.isPlaying,
                        statusMessage = PlaybackStateStore.statusMessage,
                        queueCount = PlaybackStateStore.queueCount,
                        rate = PlaybackStateStore.rate,
                        pitch = PlaybackStateStore.pitch,
                        fontSizeSp = uiState.readerSettings.fontSizeSp,
                        canGoPrevious = PlaybackStateStore.currentIndex > 0,
                        canGoNext = PlaybackStateStore.currentIndex < activeDocument.chunks.lastIndex || PlaybackStateStore.queueCount > 0,
                        onPrevious = {
                            viewModel.moveTo(
                                (PlaybackStateStore.currentIndex - 1).coerceAtLeast(0),
                                false
                            )
                        },
                        onPlayPause = { viewModel.playOrPause() },
                        onNext = {
                            viewModel.moveTo(
                                (PlaybackStateStore.currentIndex + 1).coerceAtMost(
                                    activeDocument.chunks.size - 1
                                ),
                                false
                            )
                        },
                        onPageChanged = { pageIndex, _ ->
                            // Eyes don't move the voice: while TTS is playing, browsing pages
                            // in Original view must never jump playback (moveTo notifies the
                            // service mid-utterance and it re-reads from the viewed page).
                            if (!PlaybackStateStore.isPlaying) {
                                pageSettleJob?.cancel()
                                pageSettleJob = coroutineScope.launch {
                                    delay(1200L)
                                    val targetPageNumber = pageIndex + 1
                                    val exactIndex = readerModel.sentences
                                        .indexOfFirst { it.pageNumber == targetPageNumber }
                                    val targetIndex = if (exactIndex >= 0) {
                                        exactIndex
                                    } else {
                                        readerModel.sentences.indices.minByOrNull {
                                            kotlin.math.abs(readerModel.sentences[it].pageNumber - targetPageNumber)
                                        } ?: -1
                                    }
                                    if (targetIndex >= 0) {
                                        viewModel.moveTo(targetIndex, autoPlay = false)
                                    }
                                }
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
                                    preferredSentenceIndex = PlaybackStateStore.currentIndex
                                )
                                val targetIndex = match?.chunkIndex
                                    ?: readerModel.sentences
                                        .indexOfFirst { it.pageNumber == selectionPage }
                                        .takeIf { it >= 0 }
                                if (targetIndex != null) {
                                    viewModel.moveTo(targetIndex, autoPlay = true, forcePlaybackStart = true)
                                }
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
                            currentIndex = PlaybackStateStore.currentIndex,
                            isPlaying = PlaybackStateStore.isPlaying,
                            isBackgroundActive = PlaybackStateStore.isForegroundActive,
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
                        sharedTransitionScope = this@SharedTransitionLayout,
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
                                (PlaybackStateStore.currentIndex - 1).coerceAtLeast(
                                    0
                                ), false
                            )
                        },
                        onNext = {
                            viewModel.moveTo(
                                (PlaybackStateStore.currentIndex + 1).coerceAtMost(
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
                        onOpenCanvas = {
                            val metadata = activeMetadata
                            val file = metadata?.let { documentRepository.originalFile(it) }
                            val isPdfCanvas = metadata != null && (
                                    metadata.originalMimeType.contains("pdf", ignoreCase = true) ||
                                            metadata.originalFileName.endsWith(".pdf", ignoreCase = true) ||
                                            (file != null && file.exists() && runCatching {
                                                file.inputStream().use { input ->
                                                    val bytes = ByteArray(4)
                                                    val read = input.read(bytes)
                                                    read == 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()
                                                }
                                            }.getOrDefault(false))
                                    )
                            if (metadata != null && isPdfCanvas) {
                                context.startActivity(
                                    VeritasPdfViewerActivity.intent(
                                        context,
                                        metadata.id
                                    )
                                )
                            } else {
                                viewModel.updateState { it.copy(showCanvasView = true) }
                            }
                        },
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
                        onExportAudio = { viewModel.exportActiveDocumentToAudio() },
                        onCopySelection = { copyTextToClipboard(context, "Vern selection", it) },
                        onShareSelection = { sharePlainText(context, "Vern selection", it) },
                        onGoogleSelection = {
                            viewModel.appendVocabularyWord(it, "Looked up definition / web references.")
                            openGoogleSearch(context, it)
                        },
                        onTranslateSelection = {
                            viewModel.appendVocabularyWord(it, "Looked up translation.")
                            openGoogleTranslate(context, it)
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
                                    newRuleReplaceWith = ""
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
                        onReaderModeChange = { PlaybackStateStore.readerMode = it },
                        onPaperToneModeChange = { newTone ->
                            viewModel.saveReaderSettings(
                                uiState.readerSettings.copy(
                                    paperToneMode = newTone.name.lowercase()
                                )
                            )
                        },
                        onAddGeneralNote = {
                            viewModel.updateState { it.copy(showGeneralNotesEditor = true, generalNoteEditorTarget = null) }
                        }
                    )
                }
            }
        }
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

        if (uiState.importInProgress) {
            ImportProgressOverlay(
                title = uiState.importSourceName.ifBlank { "document" }
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
            onOpenFilePicker = { importFileLauncher.launch(readableImportMimeTypes()) }
        )

        MainOnboardingAndTourHost(
            viewModel = viewModel,
            uiState = uiState
        )
    }
}

