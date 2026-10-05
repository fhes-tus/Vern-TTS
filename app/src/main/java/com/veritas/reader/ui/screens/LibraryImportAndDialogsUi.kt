package com.veritas.reader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.veritas.reader.Flashcard
import com.veritas.reader.FlashcardProgress
import com.veritas.reader.FlashcardSet
import com.veritas.reader.GeneralNote
import com.veritas.reader.PasteFlashcardsDialog
import com.veritas.reader.PasteQuizDialog
import com.veritas.reader.QuizPlayerDialog
import com.veritas.reader.QuizSet
import com.veritas.reader.ReaderSettings
import com.veritas.reader.ReaderTrackerSnapshot
import com.veritas.reader.SavedDocument
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.VeritasReadingListCatalog
import com.veritas.reader.VocabularyEntry
import com.veritas.reader.ui.ReaderUiState


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryDialogsAndSheetsHost(
    activePlayingQuiz: QuizSet?,
    onDismissActivePlayingQuiz: () -> Unit,
    onRecordQuizScore: (String, Int) -> Unit,
    showGeminiApiKeyDialog: Boolean,
    onDismissGeminiApiKeyDialog: () -> Unit,
    showQuizLabMetrics: Boolean,
    onDismissQuizLabMetrics: () -> Unit,
    onPlayQuiz: (QuizSet) -> Unit,
    onShowPasteQuiz: () -> Unit,
    quizToDelete: QuizSet?,
    onDismissQuizToDelete: () -> Unit,
    onDeleteQuiz: (String) -> Unit,
    showPasteQuiz: Boolean,
    onDismissPasteQuiz: () -> Unit,
    onSaveQuiz: (QuizSet) -> Unit,
    showContentSearchResults: Boolean,
    onDismissContentSearchResults: () -> Unit,
    showPasteFlashcards: Boolean,
    onDismissPasteFlashcards: () -> Unit,
    onImportFlashcards: (String, List<Flashcard>) -> Unit,
    onOpenDocumentAt: (SavedDocument, Int) -> Unit,
    // Bottom dialogs & sheets
    showImportSheet: Boolean,
    onDismissImportSheet: () -> Unit,
    importSheetMode: ImportSheetMode,
    onImportSheetModeChange: (ImportSheetMode) -> Unit,
    draftText: String,
    onDraftTextChange: (String) -> Unit,
    onCreateFromDraft: () -> Unit,
    onImportWebArticle: (String) -> Unit,
    onImportFile: () -> Unit,
    onImportImage: () -> Unit,
    onOpenFileBrowser: () -> Unit,
    onOpenClassicsCatalog: () -> Unit,
    onWriteGeneralNote: () -> Unit,
    manageListsDocument: SavedDocument?,
    onDismissManageLists: () -> Unit,
    readingListCatalog: VeritasReadingListCatalog,
    onCreateReadingList: (String, String?) -> Unit,
    onAddDocumentToReadingList: (String, String) -> Unit,
    onRemoveDocumentFromReadingList: (String, String) -> Unit,
    showQueue: Boolean,
    onDismissQueue: () -> Unit,
    queuedDocuments: List<SavedDocument>,
    onMoveQueueBy: (SavedDocument, Int) -> Unit,
    onRemoveFromQueue: (SavedDocument) -> Unit,
    onClearQueue: () -> Unit,
    onPlayQueue: () -> Unit,
    confirmBatchDelete: Boolean,
    onDismissConfirmBatchDelete: () -> Unit,
    selectedDocumentIds: Set<String>,
    onClearSelectedDocuments: () -> Unit,
    onBatchDeleteDocuments: (Set<String>) -> Unit,
    confirmAnnotationDelete: Boolean,
    onDismissConfirmAnnotationDelete: () -> Unit,
    selectedAnnotationKeys: Set<String>,
    onClearSelectedAnnotations: () -> Unit,
    onDeleteAnnotations: (Set<String>) -> Unit,
    showHomeSidebar: Boolean,
    onDismissHomeSidebar: () -> Unit,
    welcomeName: String,
    readerTrackerSnapshot: ReaderTrackerSnapshot,
    onNavigateToTab: (VeritasHomeTab) -> Unit,
    showReadingStatsHome: Boolean,
    onDismissReadingStatsHome: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettingsHub: () -> Unit,
    onOpenReadingLists: () -> Unit,
    onOpenReadingHistory: () -> Unit,
    documents: List<SavedDocument>,
    documentReadingTimes: Map<String, Long>,
    readerSettings: ReaderSettings,
    onSaveReaderSettings: (ReaderSettings) -> Unit,
    viewerCards: List<FlashcardProgress>?,
    viewerSetName: String,
    onDismissViewerCards: () -> Unit,
    onRateFlashcardRecall: (String, String) -> Unit,
    onDeleteFlashcard: (String) -> Unit,
    renameSetTarget: FlashcardSet?,
    onDismissRenameSetTarget: () -> Unit,
    onRenameFlashcardSet: (String, String) -> Unit,
    showBatchCollectionDialog: Boolean,
    onDismissBatchCollectionDialog: () -> Unit,
    batchCollectionDraft: String,
    onBatchCollectionDraftChange: (String) -> Unit,
    onBatchSetCollectionDocuments: (Set<String>, String) -> Unit,
    confirmDeleteVocabDocId: String?,
    onDismissConfirmDeleteVocabDocId: () -> Unit,
    vocabDocs: List<Triple<SavedDocument, GeneralNote, List<VocabularyEntry>>>,
    onRemoveVocabularyWord: (String, String) -> Unit,
    isOpeningDocument: Boolean,
    onDismissOpeningDocument: () -> Unit = {},
    uiState: ReaderUiState
) {
    LocalContext.current

    val playingQuiz = activePlayingQuiz
    if (playingQuiz != null) {
        QuizPlayerDialog(
            questions = playingQuiz.questions,
            quizTitle = playingQuiz.title,
            onSaveScore = { score -> onRecordQuizScore(playingQuiz.id, score) },
            onDismiss = { onDismissActivePlayingQuiz() }
        )
    }
    if (showGeminiApiKeyDialog) {
        GeminiApiKeyDialog(onDismiss = { onDismissGeminiApiKeyDialog() })
    }
    if (showQuizLabMetrics) {
        QuizLabMetricsDialog(
            quizzes = uiState.quizzes,
            documents = uiState.documents,
            onPlayQuiz = { onPlayQuiz(it) },
            onNewQuiz = { onShowPasteQuiz() },
            onDismiss = { onDismissQuizLabMetrics() }
        )
    }
    val deletingQuiz = quizToDelete
    if (deletingQuiz != null) {
        AlertDialog(
            onDismissRequest = { onDismissQuizToDelete() },
            title = { Text("Delete Quiz?") },
            text = { Text("Are you sure you want to delete \"${deletingQuiz.title}\"? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteQuiz(deletingQuiz.id)
                        onDismissQuizToDelete()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismissQuizToDelete() }) { Text("Cancel") }
            }
        )
    }

    if (showPasteQuiz) {
        PasteQuizDialog(onSaveQuiz = onSaveQuiz, onDismiss = { onDismissPasteQuiz() })
    }
    if (showContentSearchResults) {
        AlertDialog(
            onDismissRequest = { onDismissContentSearchResults() },
            confirmButton = {
                TextButton(onClick = { onDismissContentSearchResults() }) { Text("Close") }
            },
            title = { Text("In your documents") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    when {
                        uiState.librarySearchInProgress -> {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text("Searching every document…", style = MaterialTheme.typography.bodySmall)
                        }
                        uiState.librarySearchHits.isEmpty() ->
                            Text("No matches inside any document.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> uiState.librarySearchHits.forEach { hit ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDismissContentSearchResults()
                                        onOpenDocumentAt(hit.document, hit.sentenceIndex)
                                    },
                                shape = VeritasPackStyle.compactShape(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(hit.document.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(hit.snippet, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    Text("Sentence ${hit.sentenceIndex + 1} — tap to open", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        )
    }
    if (showPasteFlashcards) {
        PasteFlashcardsDialog(
            onImport = { name, parsed -> onImportFlashcards(name, parsed); onDismissPasteFlashcards() },
            onDismiss = { onDismissPasteFlashcards() }
        )
    }


    if (showImportSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
        shape = com.veritas.reader.VeritasPackStyle.sheetShape(),
            onDismissRequest = {
                onDismissImportSheet()
                onImportSheetModeChange(ImportSheetMode.MENU)
            },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            when (importSheetMode) {
                ImportSheetMode.MENU -> {
                    ImportSheetMenu(
                        onSelectOption = { option ->
                            when (option) {
                                ImportOption.FILE -> {
                                    onDismissImportSheet()
                                    onImportFile()
                                }
                                ImportOption.WEB -> {
                                    onImportSheetModeChange(ImportSheetMode.WEB)
                                }
                                ImportOption.PASTE -> {
                                    onImportSheetModeChange(ImportSheetMode.PASTE)
                                }
                                ImportOption.SCAN -> {
                                    onDismissImportSheet()
                                    onImportImage()
                                }
                                ImportOption.BROWSE -> {
                                    onDismissImportSheet()
                                    onOpenFileBrowser()
                                }
                                ImportOption.CLASSICS -> {
                                    onDismissImportSheet()
                                    onOpenClassicsCatalog()
                                }
                                ImportOption.WRITE_NOTE -> {
                                    onDismissImportSheet()
                                    onWriteGeneralNote()
                                }
                            }
                        },
                        onDismiss = { onDismissImportSheet() }
                    )
                }
                ImportSheetMode.WEB -> {
                    ImportSheetWeb(
                        urlText = draftText,
                        onUrlChange = onDraftTextChange,
                        onImport = { url ->
                            onImportWebArticle(url)
                            onDismissImportSheet()
                            onImportSheetModeChange(ImportSheetMode.MENU)
                        },
                        onBack = { onImportSheetModeChange(ImportSheetMode.MENU) }
                    )
                }
                ImportSheetMode.PASTE -> {
                    ImportSheetPaste(
                        pastedText = draftText,
                        onTextChange = onDraftTextChange,
                        onSave = {
                            onCreateFromDraft()
                            onDismissImportSheet()
                            onImportSheetModeChange(ImportSheetMode.MENU)
                        },
                        onBack = { onImportSheetModeChange(ImportSheetMode.MENU) }
                    )
            }
        }
    }
    }

    // Filter dialog removed — filters are handled inline via chip row and
    // the LibraryControlsCard. No modal needed.

    manageListsDocument?.let { doc ->
        ManageDocumentListsDialog(
            document = doc,
            catalog = readingListCatalog,
            onDismiss = { onDismissManageLists() },
            onCreateReadingList = { title ->
                onCreateReadingList(title, doc.id)
            },
            onAddDocumentToReadingList = onAddDocumentToReadingList,
            onRemoveDocumentFromReadingList = onRemoveDocumentFromReadingList
        )
    }

    if (showQueue) {
        VeritasQueueSheet(
            queue = queuedDocuments,
            onMove = onMoveQueueBy,
            onRemove = onRemoveFromQueue,
            onClear = { onClearQueue(); onDismissQueue() },
            onPlay = { onPlayQueue() },
            onDismiss = { onDismissQueue() }
        )
    }

    if (confirmBatchDelete) {
        AlertDialog(
            onDismissRequest = { onDismissConfirmBatchDelete() },
            confirmButton = {
                TextButton(
                    onClick = {
                        onBatchDeleteDocuments(selectedDocumentIds)
                        onClearSelectedDocuments()
                        onDismissConfirmBatchDelete()
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { onDismissConfirmBatchDelete() }) { Text("Cancel") }
            },
            title = { Text("Delete selected readings?") },
            text = { Text("This will remove ${selectedDocumentIds.size} reading${if (selectedDocumentIds.size == 1) "" else "s"} from this device.") }
        )
    }

    if (confirmAnnotationDelete) {
        AlertDialog(
            onDismissRequest = { onDismissConfirmAnnotationDelete() },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAnnotations(selectedAnnotationKeys)
                        onClearSelectedAnnotations()
                        onDismissConfirmAnnotationDelete()
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { onDismissConfirmAnnotationDelete() }) { Text("Cancel") }
            },
            title = { Text("Delete selected marks?") },
            text = { Text("This removes ${selectedAnnotationKeys.size} bookmark/note item${if (selectedAnnotationKeys.size == 1) "" else "s"} and clears bookmark highlights from the reader.") }
        )
    }

    if (showHomeSidebar) {
        HomeSidebarDialog(
            name = welcomeName,
            snapshot = readerTrackerSnapshot,
            onDismiss = { onDismissHomeSidebar() },
            onOpenLibrary = {
                onNavigateToTab(VeritasHomeTab.LIBRARY)
                onDismissHomeSidebar()
            },
            onOpenStats = {
                onOpenStats()
                onDismissHomeSidebar()
            },
            onOpenSettings = {
                onDismissHomeSidebar()
                onOpenSettingsHub()
            },
            onOpenReadingLists = {
                onDismissHomeSidebar()
                onOpenReadingLists()
            },
            onOpenReadingHistory = {
                onDismissHomeSidebar()
                onOpenReadingHistory()
            }
        )
    }

    if (showReadingStatsHome) {
        ReadingStatsDashboardDialog(
            snapshot = readerTrackerSnapshot,
            documents = documents,
            documentReadingTimes = documentReadingTimes,
            readerSettings = readerSettings,
            onGoalMinutesChange = { minutes ->
                onSaveReaderSettings(readerSettings.copy(dailyGoalMinutes = minutes))
            },
            onDismiss = { onDismissReadingStatsHome() }
        )
    }

    viewerCards?.let { deck ->
        FlashcardViewerDialog(
            setName = viewerSetName,
            cards = deck,
            onRate = onRateFlashcardRecall,
            onDeleteCard = onDeleteFlashcard,
            onDismiss = { onDismissViewerCards() }
        )
    }

    renameSetTarget?.let { target ->
        var draft by remember(target.setId) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { onDismissRenameSetTarget() },
            title = { Text("Rename set") },
            confirmButton = {
                Button(onClick = { onRenameFlashcardSet(target.setId, draft); onDismissRenameSetTarget() }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { onDismissRenameSetTarget() }) { Text("Cancel") } },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    label = { Text("Set name") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }

    if (showBatchCollectionDialog) {
        AlertDialog(
            onDismissRequest = { onDismissBatchCollectionDialog() },
            confirmButton = {
                TextButton(
                    onClick = {
                        onBatchSetCollectionDocuments(selectedDocumentIds, batchCollectionDraft)
                        onClearSelectedDocuments()
                        onBatchCollectionDraftChange("")
                        onDismissBatchCollectionDialog()
                    }
                ) { Text("Move") }
            },
            dismissButton = {
                TextButton(onClick = { onDismissBatchCollectionDialog() }) { Text("Cancel") }
            },
            title = { Text("Move selected readings") },
            text = {
                OutlinedTextField(
                    value = batchCollectionDraft,
                    onValueChange = { onBatchCollectionDraftChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Collection") },
                    placeholder = { Text("Leave blank for Unfiled") },
                    singleLine = true
                )
            }
        )
    }

    confirmDeleteVocabDocId?.let { delDocId ->
        AlertDialog(
            onDismissRequest = { onDismissConfirmDeleteVocabDocId() },
            title = { Text("Delete all vocabulary?") },
            text = { Text("This will permanently remove all vocabulary words saved for this book.") },
            confirmButton = {
                Button(
                    onClick = {
                        vocabDocs.firstOrNull { it.first.id == delDocId }?.third?.forEach { entry ->
                            onRemoveVocabularyWord(delDocId, entry.word)
                        }
                        onDismissConfirmDeleteVocabDocId()
                    }
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { onDismissConfirmDeleteVocabDocId() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (isOpeningDocument) {
        Dialog(
            onDismissRequest = onDismissOpeningDocument,
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
        ) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    val bookTitle = uiState.importSourceName.takeIf { it.isNotBlank() }
                    Text(
                        text = if (!bookTitle.isNullOrBlank()) "Opening \"$bookTitle\"..." else "Opening document...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

}
