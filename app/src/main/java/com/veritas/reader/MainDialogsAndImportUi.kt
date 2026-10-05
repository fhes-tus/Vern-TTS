package com.veritas.reader

import com.veritas.reader.ui.currentReaderIndex

import com.veritas.reader.ui.withVisibility

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.veritas.reader.ui.ReaderUiState
import com.veritas.reader.ui.ReaderViewModel
import com.veritas.reader.ui.approveFileBrowserFolder
import com.veritas.reader.ui.beginSentenceNote
import com.veritas.reader.ui.clearFileBrowserAccess
import com.veritas.reader.ui.clearReadingHistory
import com.veritas.reader.ui.deleteBrowserFiles
import com.veritas.reader.ui.deleteDocument
import com.veritas.reader.ui.deleteSentenceNote
import com.veritas.reader.ui.dismissSentenceNote
import com.veritas.reader.ui.dismissTextEditor
import com.veritas.reader.ui.enterFileBrowserDirectory
import com.veritas.reader.ui.estimateFullBackupBytes
import com.veritas.reader.ui.exportFullBackup
import com.veritas.reader.ui.exportLibraryBackup
import com.veritas.reader.ui.exportStudyGuidePdf
import com.veritas.reader.ui.goUpFileBrowserDirectory
import com.veritas.reader.ui.importLibraryBackup
import com.veritas.reader.ui.importMultipleDocuments
import com.veritas.reader.ui.openFileBrowser
import com.veritas.reader.ui.prepareImport
import com.veritas.reader.ui.refreshFileBrowser
import com.veritas.reader.ui.refreshFileBrowserAccessState
import com.veritas.reader.ui.renameDocument
import com.veritas.reader.ui.saveDocumentNoteDraft
import com.veritas.reader.ui.saveReaderSettings
import com.veritas.reader.ui.saveSentenceNote
import com.veritas.reader.ui.saveTextEditorChanges
import com.veritas.reader.ui.screens.CURATED_CLASSICS
import com.veritas.reader.ui.screens.ClassicBookCover
import com.veritas.reader.ui.screens.DocumentNotesDialog
import com.veritas.reader.ui.setDocumentCollection
import com.veritas.reader.ui.shareActiveDocumentNotes
import com.veritas.reader.ui.shareLibrarySyncPack
import java.util.Locale


@Composable
internal fun MainDialogsHost(
    viewModel: ReaderViewModel,
    uiState: ReaderUiState,
    onOpenFilePicker: () -> Unit
) {
    val context = LocalContext.current

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        viewModel.approveFileBrowserFolder(uri)
    }
    val textDownloadLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        val pending = viewModel.uiState.value.pendingTextDownload
        if (uri == null || pending == null) {
            viewModel.updateState { it.copy(pendingTextDownload = null) }
        } else {
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(pending.second.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("Could not open the selected save location.")
            }
            viewModel.updateState {
                it.copy(
                    pendingTextDownload = null,
                    exportMessage = result.fold(
                        onSuccess = { "Edited text saved to phone." },
                        onFailure = { error -> "Could not save edited text: ${error.message ?: "unknown error"}" }
                    )
                )
            }
        }
    }
    val backupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) {
            viewModel.updateState { it.copy(backupMessage = "Backup export cancelled.") }
        } else {
            viewModel.exportLibraryBackup(uri)
        }
    }
    val fullBackupExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) {
            viewModel.updateState { it.copy(backupMessage = "Backup export cancelled.") }
        } else {
            viewModel.exportFullBackup(uri)
        }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            viewModel.updateState { it.copy(backupMessage = "Backup import cancelled.") }
        } else {
            viewModel.importLibraryBackup(uri)
        }
    }



        // --- Extracted dialogs ---
        if (uiState.showPdfImportTools) {
            PdfImportOptionsDialog(
                options = uiState.advancedPdfOptions,
                textOptions = uiState.textImportOptions,
                onOptionsChange = { opt -> viewModel.updateState { it.copy(advancedPdfOptions = opt) } },
                onTextOptionsChange = { opt -> viewModel.updateState { it.copy(textImportOptions = opt) } },
                onPickPdf = {
                    viewModel.updateState { it.withVisibility(VeritasScreen.PDF_IMPORT_TOOLS, false) }
                    viewModel.openFileBrowser()
                },
                onDismiss = { viewModel.updateState { it.withVisibility(VeritasScreen.PDF_IMPORT_TOOLS, false) } }
            )
        }

        if (uiState.showFileBrowser && uiState.pendingImport == null) {
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        viewModel.refreshFileBrowser()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            FileBrowserDialog(
                roots = uiState.fileBrowserRoots,
                entries = uiState.fileBrowserFiles,
                location = uiState.fileBrowserLocation,
                canGoUp = uiState.fileBrowserBackStack.isNotEmpty(),
                scanning = uiState.fileBrowserScanning,
                message = uiState.fileBrowserMessage,
                allFilesAccessGranted = uiState.fileBrowserAllFilesGranted,
                importing = uiState.importInProgress || uiState.isBatchImporting,
                importingName = uiState.importSourceName,
                onDismiss = { viewModel.updateState { it.withVisibility(VeritasScreen.FILE_BROWSER, false) } },
                onPickFolder = { folderPickerLauncher.launch(null) },
                onRequestAllFilesAccess = {
                    openAllFilesAccessSettings(context)
                    viewModel.refreshFileBrowserAccessState()
                },
                onOpenFilePicker = onOpenFilePicker,
                onRefresh = { viewModel.refreshFileBrowser() },
                onGoUp = { viewModel.goUpFileBrowserDirectory() },
                onEnterDirectory = { viewModel.enterFileBrowserDirectory(it) },
                onRemoveAllAccess = { viewModel.clearFileBrowserAccess() },
                onImportFile = { file ->
                    if (file.isSupported && !file.isDirectory) {
                        viewModel.prepareImport(uri = file.uri, sourceNameHint = file.name)
                    }
                },
                onImportMultipleFiles = { files, queue ->
                    viewModel.importMultipleDocuments(files.map { it.uri }, queue)
                },
                onDeleteFiles = { files -> viewModel.deleteBrowserFiles(files) }
            )
        }

        if (uiState.showReadingHistory) {
            ReadingHistoryDialog(
                history = uiState.readingHistory,
                documents = uiState.documents,
                onDismiss = { viewModel.updateState { it.withVisibility(VeritasScreen.READING_HISTORY, false) } },
                onOpenDocument = { doc ->
                    viewModel.updateState { it.withVisibility(VeritasScreen.READING_HISTORY, false) }; viewModel.openSavedDocument(
                    doc
                )
                },
                onClearHistory = { viewModel.clearReadingHistory() }
            )
        }

        if (uiState.showDocumentNotes) {
            uiState.activeDocument?.let { document ->
                DocumentNotesDialog(
                    document = document,
                    annotations = uiState.annotations,
                    documentNote = uiState.documentNoteDraft,
                    currentIndex = viewModel.currentReaderIndex.coerceIn(
                        0,
                        document.chunks.lastIndex.coerceAtLeast(0)
                    ),
                    onDocumentNoteChange = { draft ->
                        viewModel.updateState {
                            it.copy(
                                documentNoteDraft = draft
                            )
                        }
                    },
                    onSaveDocumentNote = { viewModel.saveDocumentNoteDraft() },
                    onAddCurrentNote = { viewModel.beginSentenceNote(listOf(viewModel.currentReaderIndex)) },
                    onJumpToSection = { index ->
                        viewModel.moveTo(index, false)
                        viewModel.updateState { it.withVisibility(VeritasScreen.DOCUMENT_NOTES, false) }
                    },
                    onExportNotes = {
                        viewModel.saveDocumentNoteDraft()
                        viewModel.shareActiveDocumentNotes()
                    },
                    onExportPdf = {
                        viewModel.saveDocumentNoteDraft()
                        viewModel.exportStudyGuidePdf()
                    },
                    onDismiss = { viewModel.updateState { it.withVisibility(VeritasScreen.DOCUMENT_NOTES, false) } }
                )
            }
        }

        val noteIndexes = uiState.noteTargetIndexes.ifEmpty {
            uiState.noteTargetIndex?.let(::listOf).orEmpty()
        }
        if (noteIndexes.isNotEmpty()) {
            uiState.activeDocument?.let { document ->
                SentenceNoteDialog(
                    document = document,
                    sentenceIndexes = noteIndexes,
                    noteDraft = uiState.noteDraft,
                    audioPath = uiState.noteAudioPath,
                    audioDuration = uiState.noteAudioDuration,
                    onNoteChange = { draft ->
                        viewModel.updateState {
                            it.copy(
                                noteDraft = capWords(
                                    draft,
                                    300
                                )
                            )
                        }
                    },
                    onAudioChange = { path, duration ->
                        viewModel.updateState {
                            it.copy(
                                noteAudioPath = path,
                                noteAudioDuration = duration
                            )
                        }
                    },
                    onSave = { viewModel.saveSentenceNote() },
                    onDelete = { viewModel.deleteSentenceNote() },
                    onDismiss = { viewModel.dismissSentenceNote() }
                )
            }
        }

        if (uiState.showTextEditor) {
            val document = uiState.activeDocument
            val target = uiState.editorTarget
            if (document != null && target != null) {
                TextEditorDialog(
                    document = document,
                    currentIndex = viewModel.currentReaderIndex,
                    text = uiState.editorText,
                    target = target,
                    onTextChange = { text -> viewModel.updateState { it.copy(editorText = text) } },
                    onSave = { partIdx, text -> viewModel.saveTextEditorChanges(partIdx, text) },
                    onDownloadToPhone = {
                        val fileName = textEditorDownloadName(document, target)
                        viewModel.updateState { it.copy(pendingTextDownload = fileName to uiState.editorText) }
                        textDownloadLauncher.launch(fileName)
                    },
                    onDismiss = { viewModel.dismissTextEditor() }
                )
            }
        }

        uiState.deleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { viewModel.updateState { it.copy(deleteTarget = null) } },
                title = { Text("Delete reading?") },
                text = { Text("This removes ${target.title} from the local library, queue, and reading lists. Saved notes, bookmarks, and reading history remain available.") },
                confirmButton = {
                    Button(onClick = { viewModel.deleteDocument(target) }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.updateState { it.copy(deleteTarget = null) } }) {
                        Text(
                            "Cancel"
                        )
                    }
                }
            )
        }

        uiState.renameTarget?.let { target ->
            AlertDialog(
                onDismissRequest = {
                    viewModel.updateState {
                        it.copy(
                            renameTarget = null,
                            renameDraft = ""
                        )
                    }
                },
                title = { Text("Rename reading") },
                text = {
                    OutlinedTextField(
                        value = uiState.renameDraft,
                        onValueChange = { value -> viewModel.updateState { it.copy(renameDraft = value) } },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.renameDocument(target, uiState.renameDraft) },
                        enabled = uiState.renameDraft.trim().isNotBlank()
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.updateState {
                            it.copy(
                                renameTarget = null,
                                renameDraft = ""
                            )
                        }
                    }) { Text("Cancel") }
                }
            )
        }

        uiState.collectionTarget?.let { target ->
            AlertDialog(
                onDismissRequest = {
                    viewModel.updateState {
                        it.copy(
                            collectionTarget = null,
                            collectionDraft = ""
                        )
                    }
                },
                title = { Text("Move to collection") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(target.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        OutlinedTextField(
                            value = uiState.collectionDraft,
                            onValueChange = { value ->
                                viewModel.updateState {
                                    it.copy(
                                        collectionDraft = value
                                    )
                                }
                            },
                            label = { Text("Collection") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.setDocumentCollection(
                            target,
                            uiState.collectionDraft
                        )
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.updateState {
                            it.copy(
                                collectionTarget = null,
                                collectionDraft = ""
                            )
                        }
                    }) { Text("Cancel") }
                }
            )
        }

        @OptIn(ExperimentalMaterial3Api::class)
        uiState.detailsTarget?.let { target ->
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            val coverFile = remember(target.id) { CoverExtractor.coverFile(context, target.id) }
            val coverState = produceState<android.graphics.Bitmap?>(null, coverFile) {
                value = withContext(Dispatchers.IO) {
                    coverFile?.takeIf { it.isFile }?.let { file ->
                        runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
                    }
                }
            }
            val coverBitmap = coverState.value
            val progress = progressFraction(target)
            val percent = (progress * 100).toInt().coerceIn(0, 100)
            val bookmarksCount = remember(target.id, uiState.allAnnotations) {
                uiState.allAnnotations.count { it.documentId == target.id && it.type == AnnotationType.BOOKMARK }
            }
            val notesCount = remember(target.id, uiState.allAnnotations, uiState.documentNotes) {
                uiState.allAnnotations.count { it.documentId == target.id && it.type != AnnotationType.BOOKMARK } +
                    (if (uiState.documentNotes.containsKey(target.id)) 1 else 0)
            }
            val matchingClassic = remember(target) {
                CURATED_CLASSICS.firstOrNull { classic ->
                    target.title.contains(classic.title, ignoreCase = true) ||
                        (target.originalFileName.isNotBlank() && target.originalFileName.contains(classic.id, ignoreCase = true))
                }
            }

            var singleParagraphSummary by remember(target.id) { mutableStateOf("Preparing document overview…") }
            var overviewSource by remember(target.id) { mutableStateOf("") }
            var overviewSample by remember(target.id) { mutableStateOf("") }
            var overviewApiKey by remember(target.id) { mutableStateOf("") }
            var overviewBusy by remember(target.id) { mutableStateOf(false) }
            var overviewError by remember(target.id) { mutableStateOf<String?>(null) }
            val overviewScope = rememberCoroutineScope()
            val overviewKey = "document_overview_${target.id}_${target.charCount}_${target.preview.hashCode()}"
            LaunchedEffect(target.id, target.charCount, target.preview, matchingClassic) {
                val result = withContext(Dispatchers.IO) {
                    val prefs = context.getSharedPreferences("document_overviews", android.content.Context.MODE_PRIVATE)
                    val stored = prefs.getString(overviewKey, "").orEmpty()
                    val raw = runCatching { DocumentOverview.sample(java.io.File(viewModel.repository.docsDir, target.fileName)) }.getOrDefault("")
                    val text = matchingClassic?.description?.takeIf { it.isNotBlank() }
                        ?: stored.takeIf { it.isNotBlank() } ?: DocumentOverview.summarize(raw)
                    val source = when {
                        matchingClassic?.description?.isNotBlank() == true -> "Book description"
                        stored.isNotBlank() -> "AI overview"
                        else -> "Overview from the text"
                    }
                    listOf(text, source, DocumentOverview.sample(raw), GeminiStudyService.getApiKey(context))
                }
                singleParagraphSummary = result[0]; overviewSource = result[1]
                overviewSample = result[2]; overviewApiKey = result[3]
            }

            val displayAuthor = remember(target, matchingClassic) {
                matchingClassic?.author ?: run {
                    val t = target.title
                    when {
                        t.contains(" by ", ignoreCase = true) -> t.substringAfterLast(" by ", "").trim()
                        t.contains(" - ") -> {
                            val candidate = t.substringAfterLast(" - ").trim()
                            if (candidate.length in 2..35 && !candidate.contains('.')) candidate else null
                        }
                        target.collection.isNotBlank() -> target.collection
                        else -> null
                    }
                }
            }

            val fileExtension = remember(target) {
                viewModel.repository.detectExtensionFromNameOrType(
                    displayName = target.title,
                    sourceLabel = target.sourceLabel,
                    mimeType = target.originalMimeType,
                    fileName = target.originalFileName
                ).uppercase()
            }

            val docFile = remember(target) {
                viewModel.repository.originalFile(target) ?: java.io.File(viewModel.repository.docsDir, target.fileName).takeIf { it.exists() }
            }

            val fileSizeText = remember(docFile) {
                docFile?.length()?.let { len ->
                    if (len <= 0L) null
                    else {
                        val kb = len / 1024.0
                        val mb = kb / 1024.0
                        when {
                            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
                            kb >= 1.0 -> String.format(Locale.US, "%.0f KB", kb)
                            else -> "$len B"
                        }
                    }
                }
            }

            val highlightQuote = remember(matchingClassic, uiState.allAnnotations, target.id) {
                matchingClassic?.quote?.takeIf { it.isNotBlank() }
                    ?: uiState.allAnnotations.firstOrNull { it.documentId == target.id && it.note.isNotBlank() }?.note?.take(160)
            }

            ModalBottomSheet(
        shape = com.veritas.reader.VeritasPackStyle.sheetShape(),
                onDismissRequest = { viewModel.updateState { it.copy(detailsTarget = null) } },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 36.dp)
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Centered Book Cover
                    if (matchingClassic != null) {
                        ClassicBookCover(
                            book = matchingClassic,
                            width = 110.dp,
                            height = 160.dp,
                            large = true
                        )
                    } else {
                        Surface(
                            modifier = Modifier
                                .size(110.dp, 160.dp)
                                .shadow(8.dp, RoundedCornerShape(12.dp)),
                            shape = com.veritas.reader.VeritasPackStyle.compactShape(),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        ) {
                            if (coverBitmap != null) {
                                Image(
                                    bitmap = coverBitmap.asImageBitmap(),
                                    contentDescription = target.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primaryContainer,
                                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                                )
                                            )
                                        )
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = when (fileExtension.lowercase()) {
                                                "pdf" -> Icons.Outlined.PictureAsPdf
                                                "epub" -> Icons.Outlined.Book
                                                else -> Icons.Outlined.Description
                                            },
                                            contentDescription = null,
                                            modifier = Modifier.size(36.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = fileExtension.ifBlank { target.sourceLabel.take(4).uppercase() },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Title & Author (Centered)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = target.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!displayAuthor.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "by $displayAuthor",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Compact Horizontal Pills Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = fileExtension.ifBlank { target.sourceLabel.ifBlank { "Document" } },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        if (!fileSizeText.isNullOrBlank()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = fileSizeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            val lengthLabel = if (target.pageCount > 0) "${target.pageCount} pgs" else "${target.chunkCount} sents"
                            Text(
                                text = lengthLabel,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "⏱️ ${formatEstimatedReadTime(target)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        if (bookmarksCount > 0) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "🔖 $bookmarksCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (notesCount > 0) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = "📝 $notesCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // Highlight Quote Card
                    if (!highlightQuote.isNullOrBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = com.veritas.reader.VeritasPackStyle.compactShape(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "“$highlightQuote”",
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(overviewSource, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (overviewApiKey.isNotBlank() && overviewSample.isNotBlank()) TextButton(enabled = !overviewBusy, onClick = {
                            overviewBusy = true; overviewError = null
                            overviewScope.launch {
                                try {
                                    GeminiStudyService.generateDocumentOverview(overviewApiKey, target.title, overviewSample)
                                        .onSuccess { result ->
                                            withContext(Dispatchers.IO) {
                                                check(context.getSharedPreferences("document_overviews", android.content.Context.MODE_PRIVATE).edit().putString(overviewKey, result).commit()) { "Could not save the overview." }
                                            }
                                            singleParagraphSummary = result; overviewSource = "AI overview"
                                        }.onFailure { overviewError = it.message ?: "Could not generate the overview." }
                                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                catch (error: Exception) { overviewError = error.message ?: "Could not save the overview." }
                                finally { overviewBusy = false }
                            }
                        }) { Text(if (overviewBusy) "Summarizing…" else "Summarize with AI") }
                    }
                    overviewError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    Text(
                        text = DocumentOverview.limitSentences(singleParagraphSummary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Start,
                        lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Reading Progress Indicator (if started)
                    if (progress > 0f) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Reading Progress",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$percent%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Bottom Action Bar: Full-Width Stretched Read/Resume Button + Native Share Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                viewModel.updateState { it.copy(detailsTarget = null) }
                                viewModel.openSavedDocument(target)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = com.veritas.reader.VeritasPackStyle.compactShape()
                        ) {
                            Icon(
                                imageVector = if (target.currentIndex > 0) Icons.Filled.PlayArrow else Icons.Outlined.Book,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (target.currentIndex > 0) "Resume" else "Read",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }

                        FilledTonalIconButton(
                            onClick = {
                                val uri = viewModel.repository.originalUri(target)
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    if (uri != null) {
                                        val mime = target.originalMimeType.ifBlank {
                                            when (fileExtension.lowercase()) {
                                                "pdf" -> "application/pdf"
                                                "epub" -> "application/epub+zip"
                                                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                                "txt" -> "text/plain"
                                                else -> "application/octet-stream"
                                            }
                                        }
                                        type = mime
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        putExtra(Intent.EXTRA_SUBJECT, target.title)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    } else {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, target.title)
                                        putExtra(Intent.EXTRA_TEXT, "${target.title}\n\n${target.preview}")
                                    }
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Document"))
                            },
                            modifier = Modifier.size(50.dp),
                            shape = com.veritas.reader.VeritasPackStyle.compactShape()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share Document",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        if (uiState.showBackupTools) {
            var fullBackupEstimate by remember { mutableLongStateOf(0L) }
            LaunchedEffect(Unit) {
                fullBackupEstimate = viewModel.estimateFullBackupBytes()
            }
            BackupRestoreDialog(
                documentCount = uiState.documents.size,
                annotationCount = uiState.annotationCount,
                queueCount = uiState.queuedDocuments.size,
                inProgress = uiState.backupInProgress,
                message = uiState.backupMessage,
                fullBackupEstimateBytes = fullBackupEstimate,
                autoBackupEnabled = uiState.readerSettings.autoBackupWeekly,
                onToggleAutoBackup = {
                    viewModel.saveReaderSettings(
                        uiState.readerSettings.copy(autoBackupWeekly = !uiState.readerSettings.autoBackupWeekly)
                    )
                },
                onExport = {
                    backupExportLauncher.launch(veritasBackupFileName("vern_backup"))
                },
                onExportFull = {
                    fullBackupExportLauncher.launch(veritasBackupZipFileName("vern_full_backup"))
                },
                onImport = {
                    backupImportLauncher.launch(veritasBackupMimeTypes())
                },
                onDismiss = { viewModel.updateState { it.withVisibility(VeritasScreen.BACKUP_TOOLS, false) } }
            )
        }

        if (uiState.showSyncCenter) {
            var fullBackupEstimate by remember { mutableLongStateOf(0L) }
            LaunchedEffect(Unit) {
                fullBackupEstimate = viewModel.estimateFullBackupBytes()
            }
            SyncCenterDialog(
                documentCount = uiState.documents.size,
                annotationCount = uiState.annotationCount,
                queueCount = uiState.queuedDocuments.size,
                pronunciationRuleCount = uiState.pronunciationRules.size,
                inProgress = uiState.backupInProgress,
                message = uiState.backupMessage,
                fullBackupEstimateBytes = fullBackupEstimate,
                autoBackupEnabled = uiState.readerSettings.autoBackupWeekly,
                onToggleAutoBackup = {
                    viewModel.saveReaderSettings(
                        uiState.readerSettings.copy(autoBackupWeekly = !uiState.readerSettings.autoBackupWeekly)
                    )
                },
                onExportSyncPack = {
                    backupExportLauncher.launch(veritasBackupFileName("vern_sync_pack"))
                },
                onExportFull = {
                    fullBackupExportLauncher.launch(veritasBackupZipFileName("vern_full_backup"))
                },
                onShareSyncPack = { viewModel.updateState { it.withVisibility(VeritasScreen.SYNC_CENTER, false) }; viewModel.shareLibrarySyncPack() },
                onImportSyncPack = {
                    backupImportLauncher.launch(veritasBackupMimeTypes())
                },
                onDismiss = { viewModel.updateState { it.withVisibility(VeritasScreen.SYNC_CENTER, false) } }
            )
        }

        if (uiState.showAppHealth) {
            AppHealthDialog(
                documentCount = uiState.documents.size,
                queueCount = uiState.queuedDocuments.size,
                themePackName = VeritasThemePackCatalog.displayName(uiState.readerSettings.themePackId),
                themeName = VeritasThemeCatalog.displayName(uiState.readerSettings.themeId),
                onDismiss = { viewModel.updateState { it.withVisibility(VeritasScreen.APP_HEALTH, false) } }
            )
        }


}
