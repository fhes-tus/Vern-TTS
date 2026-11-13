package com.veritas.reader.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veritas.reader.AnnotationType
import com.veritas.reader.DocumentRepository
import com.veritas.reader.Flashcard
import com.veritas.reader.FlashcardProgress
import com.veritas.reader.FlashcardSet
import com.veritas.reader.GeminiStudyService
import com.veritas.reader.QuizQuestion
import com.veritas.reader.QuizSet
import com.veritas.reader.SavedDocument
import com.veritas.reader.StudyGuidePdfExporter
import com.veritas.reader.StudyReviewSummary
import com.veritas.reader.TextChunker
import com.veritas.reader.VocabularyEntry
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.copyTextToClipboard
import com.veritas.reader.documentNoteStableKey
import com.veritas.reader.parseVocabularyNoteContent
import com.veritas.reader.ui.ReaderUiState

@Composable
internal fun LibraryStudyTab(
    documents: List<SavedDocument>,
    uiState: ReaderUiState,
    flashcardSets: List<FlashcardSet>,
    studyListState: LazyListState,
    onRefreshMainPage: () -> Unit,
    annotationFilter: String,
    onAnnotationFilterChange: (String) -> Unit,
    selectedAnnotationKeys: Set<String>,
    onSelectedAnnotationKeysChange: (Set<String>) -> Unit,
    onOpenDocument: (SavedDocument) -> Unit,
    onOpenDocumentAt: (SavedDocument, Int) -> Unit,
    onOpenAiStudyTools: () -> Unit,
    onShowGeminiApiKeyDialog: () -> Unit,
    onShowQuizLabMetrics: () -> Unit,
    onShowPasteQuiz: () -> Unit,
    onShowPasteFlashcards: () -> Unit,
    onPlayQuiz: (QuizSet) -> Unit,
    onDeleteQuiz: (QuizSet) -> Unit,
    onGenerateInAppFlashcards: (SavedDocument, String?) -> Unit,
    onCancelAiStudyGeneration: () -> Unit,
    onImportFlashcards: (String, List<Flashcard>) -> Unit,
    onSaveQuiz: (QuizSet) -> Unit,
    onDeleteAnnotations: (Set<String>) -> Unit,
    onRemoveVocabularyWord: (String, String) -> Unit,
    onRefreshVocabularyMeaning: (String, VocabularyEntry) -> Unit,
    onConfirmDeleteVocabDocId: (String) -> Unit,
    onClearReadingHistory: () -> Unit,
    onRemoveReadingHistoryEntry: (String) -> Unit,
    onDeleteFlashcardSet: (String) -> Unit,
    onOpenFlashcardViewer: (String, List<FlashcardProgress>) -> Unit,
    onRenameFlashcardSet: (FlashcardSet) -> Unit,
    onNavigateToTab: (VeritasHomeTab) -> Unit,
    onImportFile: () -> Unit,
    onExportStudyGuidePdf: (SavedDocument) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val floatingBottomPadding = LocalHomeBottomPadding.current
    var reviewNowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            reviewNowMillis = System.currentTimeMillis()
            kotlinx.coroutines.delay(60_000)
        }
    }

    var lastMainPageRefreshAt by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var dismissedClipboardSnippet by rememberSaveable { mutableStateOf<String?>(null) }
    var detectedClipboardFlashcards by remember { mutableStateOf<List<Flashcard>>(emptyList()) }
    var detectedClipboardQuiz by remember { mutableStateOf<List<QuizQuestion>>(emptyList()) }
    var expandedVocabDocIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val annotationSelectionMode = selectedAnnotationKeys.isNotEmpty()

    var showAiBookPicker by remember { mutableStateOf(false) }
    var showStudyGuideBookPicker by remember { mutableStateOf(false) }
    if (showStudyGuideBookPicker) {
        StudyBookPickerDialog(
            documents = documents,
            onSelect = { document -> showStudyGuideBookPicker = false; onExportStudyGuidePdf(document) },
            onGoToLibrary = { showStudyGuideBookPicker = false; onNavigateToTab(VeritasHomeTab.LIBRARY) },
            onDismiss = { showStudyGuideBookPicker = false },
            title = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_choose_a_book_for_your_study_guide),
            emptyDescription = "Add a book to your library, then export its booknotes and highlights as a study guide."
        )
    }
    LaunchedEffect(annotationFilter) { studyListState.requestScrollToItem(0) }
    if (showAiBookPicker) {
        StudyBookPickerDialog(
            documents = documents,
            onSelect = { document -> showAiBookPicker = false; onGenerateInAppFlashcards(document, null) },
            onGoToLibrary = { showAiBookPicker = false; onNavigateToTab(VeritasHomeTab.LIBRARY) },
            onDismiss = { showAiBookPicker = false }
        )
    }

    val repository = remember(context) { DocumentRepository(context) }
    var loadedDocSentences by remember { mutableStateOf(emptyMap<String, Pair<String, List<String>>>()) }
    val docIdsWithAnnotations = remember(uiState.allAnnotations) {
        uiState.allAnnotations.map { it.documentId }.toSet()
    }
    LaunchedEffect(docIdsWithAnnotations, uiState.documents) {
        val previous = loadedDocSentences
        val metadata = uiState.documents.associateBy { it.id }
        loadedDocSentences = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            docIdsWithAnnotations.associateWith { docId ->
                val doc = metadata[docId]
                if (doc == null) "missing" to emptyList()
                else {
                    val file = java.io.File(context.filesDir, "reader_documents/${doc.fileName}")
                    val revision = "${doc.fileName}:${file.lastModified()}:${file.length()}:${doc.sentenceIndexVersion}"
                    previous[docId]?.takeIf { it.first == revision }
                        ?: (revision to TextChunker.chunk(repository.readText(doc)))
                }
            }
        }
    }

    val vocabDocs = remember(uiState.generalNotes, uiState.documents, uiState.documentTitles) {
        uiState.generalNotes
            .filter { it.title.startsWith("__vocab__") }
            .mapNotNull { note ->
                val docId = note.title.removePrefix("__vocab__")
                val doc = uiState.documents.firstOrNull { it.id == docId } ?: SavedDocument(
                    id = docId,
                    title = if (docId == com.veritas.reader.DICTIONARY_VOCAB_ID) "Dictionary" else uiState.documentTitles[docId] ?: "Deleted Book",
                    fileName = "",
                    sourceLabel = if (docId == com.veritas.reader.DICTIONARY_VOCAB_ID) "Dictionary" else "Deleted",
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


    val annotatedDocuments = remember(documents, uiState.allAnnotations, uiState.documentNotes, uiState.documentTitles) {
        val annotationsByDocument = uiState.allAnnotations
            .filter { it.type == AnnotationType.BOOKMARK || it.type == AnnotationType.NOTE }
            .groupBy { it.documentId }
        val markedDocumentIds = annotationsByDocument.keys + uiState.documentNotes.keys
        markedDocumentIds.mapNotNull { documentId ->
            val document = documents.firstOrNull { it.id == documentId } ?: SavedDocument(
                id = documentId,
                title = uiState.documentTitles[documentId] ?: "Deleted Book",
                fileName = "",
                sourceLabel = "Deleted",
                createdAt = 0,
                updatedAt = 0,
                currentIndex = 0,
                chunkCount = 0,
                charCount = 0,
                preview = ""
            )
            val annotations = annotationsByDocument[documentId].orEmpty().sortedBy { annotation -> annotation.chunkIndex }
            val documentNote = uiState.documentNotes[documentId].orEmpty()
            if (annotations.isEmpty() && documentNote.isBlank()) {
                null
            } else {
                MarkedDocument(
                    document = document,
                    annotations = annotations,
                    documentNote = documentNote,
                    updatedAt = maxOf(
                        annotations.maxOfOrNull { it.updatedAt } ?: 0L,
                        if (documentNote.isBlank()) 0L else document.updatedAt
                    )
                )
            }
        }.sortedByDescending { it.updatedAt }
    }

    val filteredAnnotatedDocuments = remember(annotatedDocuments, annotationFilter) {
        annotatedDocuments.map { markedDoc ->
            val bookmarks = markedDoc.annotations.filter { it.type == AnnotationType.BOOKMARK }
            val notes = markedDoc.annotations.filter { it.type == AnnotationType.NOTE }
            markedDoc.copy(
                annotations = when (annotationFilter) {
                    "Bookmarks" -> bookmarks
                    "Booknotes" -> notes
                    else -> markedDoc.annotations
                },
                documentNote = if (annotationFilter == "Bookmarks") "" else markedDoc.documentNote
            )
        }.filter {
            (annotationFilter == "All") || it.annotations.isNotEmpty() || it.documentNote.isNotBlank()
        }
    }

    val bookmarksOnly = remember(filteredAnnotatedDocuments) {
        filteredAnnotatedDocuments.filter { markedDoc -> markedDoc.annotations.any { it.type == AnnotationType.BOOKMARK } }
    }
    val notesOnly = remember(filteredAnnotatedDocuments) {
        filteredAnnotatedDocuments.filter { markedDoc -> markedDoc.annotations.any { it.type == AnnotationType.NOTE } || markedDoc.documentNote.isNotBlank() }
    }

                                BoxWithConstraints(modifier.fillMaxSize()) {
                                val columns = vernCollectionColumns(maxWidth.value - 36f,
                                    if (maxWidth < 600.dp) 168f else 236f, LocalDensity.current.fontScale, gapDp = 12f)
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("study-layout-$columns")
                                        .pointerInput(studyListState) {
                                            var pullDistance = 0f
                                            detectVerticalDragGestures(
                                                onDragStart = { pullDistance = 0f },
                                                onVerticalDrag = { _, dragAmount ->
                                                    val atTop = studyListState.firstVisibleItemIndex == 0 && studyListState.firstVisibleItemScrollOffset == 0
                                                    if (atTop && dragAmount > 0f) pullDistance += dragAmount
                                                },
                                                onDragEnd = {
                                                    val now = System.currentTimeMillis()
                                                    if (pullDistance > 120f && now - lastMainPageRefreshAt > 1500L) {
                                                        lastMainPageRefreshAt = now
                                                        onRefreshMainPage()
                                                    }
                                                    pullDistance = 0f
                                                },
                                                onDragCancel = { pullDistance = 0f }
                                            )
                                        }
                                        .padding(horizontal = 18.dp),
                                    state = studyListState,
                                    contentPadding = PaddingValues(top = 10.dp, bottom = floatingBottomPadding + 22.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    if (uiState.isGeneratingAiStudy) {
                                        item(key = "study-generating-banner") {
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = com.veritas.reader.VeritasPackStyle.compactShape(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                                ),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                                                    Text(
                                                        text = uiState.aiStudyStatusMessage ?: com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_ai_is_preparing_study_materials),
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    androidx.compose.material3.TextButton(onClick = onCancelAiStudyGeneration) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_cancel)) }
                                                }
                                            }
                                        }
                                    }

                                    if (detectedClipboardFlashcards.isNotEmpty() || detectedClipboardQuiz.isNotEmpty()) {
                                        item(key = "study-clipboard-banner") {
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = com.veritas.reader.VeritasPackStyle.compactShape(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                                                ),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(14.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.AutoAwesome,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(26.dp)
                                                    )
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_study_content_on_clipboard),
                                                            style = MaterialTheme.typography.titleSmall)
                                                        Text(
                                                            text = if (detectedClipboardFlashcards.isNotEmpty())
                                                                com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_119, com.veritas.reader.ui.screens.localizedUiValue(detectedClipboardFlashcards.size))
                                                            else
                                                                com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_120, com.veritas.reader.ui.screens.localizedUiValue(detectedClipboardQuiz.size)),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                                        )
                                                    }
                                                    Button(
                                                        onClick = {
                                                            if (detectedClipboardFlashcards.isNotEmpty()) {
                                                                onImportFlashcards("Imported Deck", detectedClipboardFlashcards)
                                                            } else if (detectedClipboardQuiz.isNotEmpty()) {
                                                                onSaveQuiz(QuizSet(title = "Imported Quiz", questions = detectedClipboardQuiz))
                                                            }
                                                            dismissedClipboardSnippet = clipboardManager.getText()?.text?.toString()?.take(60)
                                                            detectedClipboardFlashcards = emptyList()
                                                            detectedClipboardQuiz = emptyList()
                                                        },
                                                        shape = com.veritas.reader.VeritasPackStyle.chipShape(),
                                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                                                    ) {
                                                        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_1_tap_import), style = MaterialTheme.typography.labelSmall)
                                                    }
                                                    IconButton(
                                                        onClick = {
                                                            dismissedClipboardSnippet = clipboardManager.getText()?.text?.toString()?.take(60)
                                                            detectedClipboardFlashcards = emptyList()
                                                            detectedClipboardQuiz = emptyList()
                                                        },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(Icons.Filled.Close, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_dismiss), modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    item(key = "study-category-filter-chips") {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val filterOptions = listOf("Overview", "Bookmarks", "Booknotes", "Vocab", "Dictionary", "Flashcards", "Quizzes", "History")
                                            filterOptions.forEach { option ->
                                                FilterChip(
                                                    selected = annotationFilter == option,
                                                    onClick = { onAnnotationFilterChange(option) },
                                                    label = { Text(localizedCatalogLabel(option)) },
                                                    shape = VeritasPackStyle.chipShape()
                                                )
                                            }
                                        }
                                    }

                                    if (annotationFilter == "Overview") {
                                        item(key = "study-overview") {
                                            val review = remember(uiState.flashcards, reviewNowMillis) {
                                                StudyReviewSummary.from(uiState.flashcards, reviewNowMillis)
                                            }
                                            StudyHubOverview(
                                                state = uiState,
                                                sets = flashcardSets,
                                                vocabularyCount = vocabDocs.sumOf { it.third.size },
                                                review = review,
                                                onSelectSection = onAnnotationFilterChange,
                                                onStartReview = {
                                                    val due = StudyReviewSummary.from(uiState.flashcards, System.currentTimeMillis()).dueCards
                                                    if (due.isNotEmpty()) onOpenFlashcardViewer("Due flashcards", due)
                                                    else onAnnotationFilterChange("Flashcards")
                                                },
                                                onOpenDeck = { onOpenFlashcardViewer(it.name, it.cards) },
                                                onImportFlashcards = onShowPasteFlashcards,
                                                onImportQuiz = onShowPasteQuiz,
                                                onCreateWithAi = {
                                                    if (GeminiStudyService.hasApiKey(context)) showAiBookPicker = true
                                                    else onShowGeminiApiKeyDialog()
                                                },
                                                onSetupAi = onShowGeminiApiKeyDialog,
                                                onExportStudyGuide = { showStudyGuideBookPicker = true }
                                            )
                                        }
                                    }

                                    when (annotationFilter) {
                "Dictionary" -> item(key = "study-dictionary") {
                    OfflineDictionaryPanel(onSaved = { onRefreshMainPage() })
                }
                "Bookmarks" -> {
                    if (bookmarksOnly.isEmpty()) {
                        item {
                            StudyEmptyState(
                                icon = Icons.Outlined.Bookmark,
                                title = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_no_bookmarks_yet),
                                description = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_bookmark_key_passages_while_reading_your_bookmarked_sentences_wil),
                                onGoToLibrary = { onNavigateToTab(VeritasHomeTab.LIBRARY) },
                                onImportFile = onImportFile
                            )
                        }
                    } else {
                        bookmarksOnly.forEach { markedDocument ->
                            val doc = markedDocument.document
                            val docBookmarks = markedDocument.annotations.filter { it.type == AnnotationType.BOOKMARK }
                            val groups = groupBookmarks(doc, docBookmarks)
                            if (groups.isNotEmpty()) {
                                item(key = "marks-bookmarks-${doc.id}") {
                                    BookmarkDocumentCard(
                                        document = doc,
                                        groups = groups,
                                        sentenceTextLookup = { chunkIndex ->
                                            loadedDocSentences[doc.id]?.second?.getOrNull(chunkIndex)
                                        },
                                        onOpenAt = { index -> onOpenDocumentAt(doc, index) },
                                        onDeleteAnnotations = onDeleteAnnotations
                                    )
                                }
                            }
                        }
                    }
                }
                "Booknotes" -> {
                    if (notesOnly.isEmpty()) {
                        item {
                            StudyEmptyState(
                                icon = Icons.Outlined.EditNote,
                                title = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_no_booknotes_yet),
                                description = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_add_notes_to_sentences_or_write_general_document_notes_while_read),
                                onGoToLibrary = { onNavigateToTab(VeritasHomeTab.LIBRARY) },
                                onImportFile = onImportFile
                            )
                        }
                    } else {
                        notesOnly.forEach { markedDocument ->
                            val doc = markedDocument.document
                            item(key = "marks-notes-${doc.id}") {
                                AnnotationDocumentCard(
                                    document = doc,
                                    annotations = markedDocument.annotations.filter { it.type == AnnotationType.NOTE },
                                    documentNote = markedDocument.documentNote,
                                    selectedKeys = selectedAnnotationKeys,
                                    selectionMode = annotationSelectionMode,
                                    onToggleDocumentNoteSelected = {
                                        val key = documentNoteStableKey(doc.id)
                                        onSelectedAnnotationKeysChange(if (key in selectedAnnotationKeys) selectedAnnotationKeys - key else selectedAnnotationKeys + key)
                                    },
                                    onLongPressDocumentNote = {
                                        onSelectedAnnotationKeysChange(selectedAnnotationKeys + documentNoteStableKey(doc.id))
                                    },
                                    onToggleSelected = { annotation ->
                                        onSelectedAnnotationKeysChange(if (annotation.stableKey in selectedAnnotationKeys) selectedAnnotationKeys - annotation.stableKey else selectedAnnotationKeys + annotation.stableKey)
                                    },
                                    onLongPressAnnotation = { annotation -> onSelectedAnnotationKeysChange(selectedAnnotationKeys + annotation.stableKey) },
                                    onOpenDocumentNote = { onOpenDocumentAt(doc, doc.currentIndex.coerceAtLeast(0)) },
                                    onOpenAt = { index -> onOpenDocumentAt(doc, index) },
                                    sentenceTextLookup = { chunkIndex ->
                                        loadedDocSentences[doc.id]?.second?.getOrNull(chunkIndex)
                                    },
                                    onDeleteAnnotations = onDeleteAnnotations
                                )
                            }
                        }
                    }
                }
                "Vocab" -> {
                    if (vocabDocs.isEmpty()) {
                        item {
                            StudyEmptyState(
                                icon = Icons.Outlined.Book,
                                title = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_no_vocabulary_words_yet),
                                description = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_select_words_in_the_reader_and_click_ask_ai_google_search_or_tran),
                                onGoToLibrary = { onNavigateToTab(VeritasHomeTab.LIBRARY) },
                                onImportFile = onImportFile
                            )
                        }
                    } else {
                        vocabDocs.forEach { (doc, _, entries) ->
                            val isExpanded = doc.id in expandedVocabDocIds
                            item(key = "vocab-doc-${doc.id}") {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        shape = VeritasPackStyle.compactShape(),
                                        colors = CardDefaults.cardColors(containerColor = com.veritas.reader.VeritasPackStyle.panelColor(MaterialTheme.colorScheme)),
                                        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .clickable {
                                                            expandedVocabDocIds = if (isExpanded) {
                                                                expandedVocabDocIds - doc.id
                                                            } else {
                                                                expandedVocabDocIds + doc.id
                                                            }
                                                        },
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Book,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(doc.title, style = MaterialTheme.typography.bodyMedium)
                                                        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_213, com.veritas.reader.ui.screens.localizedUiValue(entries.size)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }

                                                var showBatchMenu by remember { mutableStateOf(false) }
                                                Box {
                                                    IconButton(onClick = { showBatchMenu = true }) {
                                                        Icon(
                                                            imageVector = Icons.Filled.MoreVert,
                                                            contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_batch_actions),
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    DropdownMenu(
                                                        expanded = showBatchMenu,
                                                        onDismissRequest = { showBatchMenu = false }
                                                    ) {
                                                        DropdownMenuItem(
                                                            text = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_share_as_pdf)) },
                                                            onClick = {
                                                                showBatchMenu = false
                                                                val pdf = StudyGuidePdfExporter.generateVocabularyBatchPdf(context, doc.title, entries)
                                                                if (pdf != null) {
                                                                    StudyGuidePdfExporter.sharePdfFile(context, pdf, "Share Vocabulary PDF")
                                                                }
                                                            },
                                                            leadingIcon = { Icon(Icons.Outlined.Book, contentDescription = null) }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_copy_all)) },
                                                            onClick = {
                                                                showBatchMenu = false
                                                                val allText = entries.joinToString("\n\n") { e ->
                                                                    buildString {
                                                                        append(e.word)
                                                                        if (!e.pronunciation.isNullOrBlank()) append(" (${e.pronunciation})")
                                                                        append("\n").append(e.explanation)
                                                                        if (!e.contextSentence.isNullOrBlank()) append("\n\"${e.contextSentence}\"")
                                                                    }
                                                                }
                                                                copyTextToClipboard(context, "Vocabulary Batch", allText)
                                                            },
                                                            leadingIcon = { Icon(Icons.Filled.ContentPaste, contentDescription = null) }
                                                        )
                                                        DropdownMenuItem(
                                                            text = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_delete_all_0817058), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                                            onClick = {
                                                                showBatchMenu = false
                                                                onConfirmDeleteVocabDocId(doc.id)
                                                            },
                                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                                        )
                                                    }
                                                }

                                                IconButton(onClick = {
                                                    expandedVocabDocIds = if (isExpanded) {
                                                        expandedVocabDocIds - doc.id
                                                    } else {
                                                        expandedVocabDocIds + doc.id
                                                    }
                                                }) {
                                                    Icon(
                                                        imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                                        contentDescription = if (isExpanded) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_collapse) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_expand),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }

                                            if (isExpanded) {
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 14.dp))
                                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    entries.forEach { entry ->
                                                        VocabularyEntryRow(
                                                            entry = entry,
                                                            document = doc,
                                                            onOpenDocumentAt = onOpenDocumentAt,
                                                            onRemoveVocabularyWord = onRemoveVocabularyWord,
                                                            onRefreshVocabularyMeaning = onRefreshVocabularyMeaning
                                                        )
                                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                "Quizzes" -> {
                    studyQuizSection(
                        quizzes = uiState.quizzes,
                        onOpenAiStudyTools = onOpenAiStudyTools,
                        onShowPasteQuiz = { onShowPasteQuiz() },
                        onShowQuizLabMetrics = { onShowQuizLabMetrics() },
                        onPlayQuiz = { onPlayQuiz(it) },
                        onDeleteQuiz = { onDeleteQuiz(it) },
                        onGoToLibrary = { onNavigateToTab(VeritasHomeTab.LIBRARY) }
                    )
                }
                "History" -> {
                    studyHistorySection(
                        readingHistory = uiState.readingHistory,
                        documents = documents,
                        onClearReadingHistory = onClearReadingHistory,
                        onRemoveReadingHistoryEntry = onRemoveReadingHistoryEntry,
                        onOpenDocumentAt = onOpenDocumentAt,
                        onGoToLibrary = { onNavigateToTab(VeritasHomeTab.LIBRARY) },
                        onImportFile = onImportFile
                    )
                }
                "Flashcards" -> {
                    if (flashcardSets.isEmpty()) {
                        item {
                            StudyEmptyState(
                                icon = Icons.Outlined.Book,
                                title = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_no_flashcards_yet),
                                description = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_open_a_document_study_tools_create_flashcards_to_send_a_prompt_to),
                                onGoToLibrary = { onNavigateToTab(VeritasHomeTab.LIBRARY) },
                                primaryActionLabel = "Open AI Hub",
                                onPrimaryAction = onOpenAiStudyTools
                            )
                        }
                        item {
                            OutlinedButton(
                                onClick = { onShowPasteFlashcards() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = com.veritas.reader.VeritasPackStyle.chipShape()
                            ) {
                                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_paste_ai_reply_add_cards))
                            }
                        }
                    } else {
                        item {
                            Button(
                                onClick = { onShowPasteFlashcards() },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                shape = VeritasPackStyle.chipShape()
                            ) {
                                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_paste_ai_reply_add_cards))
                            }
                        }
                        // Tile width follows the available pane and text size.
                        items(flashcardSets.chunked(columns), key = { row -> row.first().setId }) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                row.forEach { set ->
                                    FlashcardSetTile(
                                        set = set,
                                        modifier = Modifier.weight(1f).testTag("study-deck-${set.setId}"),
                                        onOpen = {
                                            onOpenFlashcardViewer(set.name, set.cards)
                                        },
                                        onViewBucket = { bucket ->
                                            onOpenFlashcardViewer("${set.name} · ${bucket.replaceFirstChar { it.uppercase() }}", set.cards.filter { it.recall == bucket })
                                        },
                                        onRename = { onRenameFlashcardSet(set) },
                                        onDelete = { onDeleteFlashcardSet(set.setId) }
                                    )
                                }
                                repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }

}
}
