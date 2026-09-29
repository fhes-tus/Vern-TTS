package com.veritas.reader


import android.content.Context
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.veritas.reader.ui.ReaderUiState
import com.veritas.reader.ui.ReaderViewModel
import com.veritas.reader.ui.dismissSentenceNote
import com.veritas.reader.ui.dismissTextEditor
import com.veritas.reader.ui.goUpFileBrowserDirectory
import com.veritas.reader.ui.openFileBrowser
import com.veritas.reader.ui.screens.VeritasHomeTab


@Composable
internal fun MainIntentAndRoutingEffects(
    context: Context,
    viewModel: ReaderViewModel,
    uiState: ReaderUiState,
    initialSharedText: String,
    initialSharedUri: Uri?,
    isShareToNotes: Boolean,
    openVoiceStudioOnStart: Boolean,
    widgetAction: String?,
    noteId: String?,
    onOpenInNotes: (String, Uri?) -> Unit,
    onPendingShareChooserChange: (Pair<String, Uri?>?) -> Unit
) {
    val documentRepository = remember(context) { DocumentRepository(context.applicationContext) }
    LaunchedEffect(Unit) {
        if (!uiState.handledInitialShare) {
            if (isShareToNotes) {
                if (initialSharedText.isNotBlank() || initialSharedUri != null) {
                    onOpenInNotes(initialSharedText, initialSharedUri)
                }
            } else if (initialSharedText.isNotBlank() || initialSharedUri != null) {
                onPendingShareChooserChange(Pair(initialSharedText, initialSharedUri))
            }
            if (openVoiceStudioOnStart) {
                viewModel.updateState { it.copy(showVoiceStudio = true) }
            }
            viewModel.updateState { it.copy(handledInitialShare = true) }
        }
    }

    LaunchedEffect(uiState.pendingWidgetAction) {
        val action = uiState.pendingWidgetAction
        val docId = uiState.pendingWidgetDocId
        val noteId = uiState.pendingWidgetNoteId

        if (action != null) {
            when (action) {
                MainActivity.ACTION_NEW_NOTE -> {
                    viewModel.updateState { it.copy(showGeneralNotesEditor = true, generalNoteEditorTarget = null) }
                }
                MainActivity.ACTION_SHOW_NOTES -> {
                    viewModel.navigateToHomeTab(VeritasHomeTab.NOTES)
                }
                MainActivity.ACTION_NEW_CHECKLIST_NOTE -> {
                    viewModel.updateState { it.copy(showGeneralNotesEditor = true, generalNoteEditorTarget = null, noteEditorChecklistOnStart = true) }
                }
                MainActivity.ACTION_NEW_REMINDER_NOTE -> {
                    viewModel.updateState { it.copy(showGeneralNotesEditor = true, generalNoteEditorTarget = null, noteEditorReminderOnStart = true) }
                }
                MainActivity.ACTION_NEW_IMAGE_NOTE -> {
                    viewModel.updateState { it.copy(showGeneralNotesEditor = true, generalNoteEditorTarget = null, noteEditorImageOnStart = true) }
                }
                MainActivity.ACTION_OPEN_LIBRARY -> {
                    viewModel.navigateToHomeTab(VeritasHomeTab.LIBRARY)
                }
                MainActivity.ACTION_SHOW_STUDY_DASHBOARD,
                MainActivity.ACTION_SHOW_FLASHCARDS -> {
                    viewModel.navigateToHomeTab(VeritasHomeTab.STUDY)
                }
                MainActivity.ACTION_IMPORT_DOCUMENTS -> {
                    viewModel.returnToLibrary()
                    viewModel.openFileBrowser()
                }
                MainActivity.ACTION_ACTIVE_READING,
                MainActivity.ACTION_CONTINUE_READING -> {
                    val activeDocId = uiState.activeDocument?.id
                    val docToOpen: SavedDocument? = if (activeDocId != null) {
                        uiState.documents.firstOrNull { it.id == activeDocId }
                    } else {
                        val lastHistory = uiState.readingHistory.maxByOrNull { it.openedAt }
                        val found: SavedDocument? = if (lastHistory != null) {
                            uiState.documents.firstOrNull { it.id == lastHistory.documentId }
                        } else {
                            uiState.documents.maxByOrNull { it.updatedAt }
                        }
                        found
                    }
                    if (docToOpen != null) {
                        PlaybackStateStore.readerMode = ReaderMode.TEXT
                        viewModel.openSavedDocument(docToOpen)
                    }
                }
                MainActivity.ACTION_NEW_READING_NOTE -> {
                    if (uiState.activeDocument != null) {
                        viewModel.updateState { it.copy(showDocumentNotes = true) }
                    } else {
                        viewModel.updateState { it.copy(showGeneralNotesEditor = true, generalNoteEditorTarget = null) }
                    }
                }
                MainActivity.ACTION_NEW_STUDY_NOTE -> {
                    viewModel.updateState { it.copy(showGeneralNotesEditor = true, generalNoteEditorTarget = null) }
                }
                MainActivity.ACTION_VOICE_NOTE -> {
                    viewModel.updateState { it.copy(showVoiceStudio = true) }
                }
                MainActivity.ACTION_EDIT_NOTE -> {
                    noteId?.let { nid ->
                        val note = documentRepository.loadGeneralNotes().firstOrNull { it.id == nid }
                        if (note != null) {
                            viewModel.updateState { it.copy(showGeneralNotesEditor = true, generalNoteEditorTarget = note) }
                        }
                    }
                }
            }
            viewModel.updateState { it.copy(pendingWidgetAction = null, pendingWidgetDocId = null, pendingWidgetNoteId = null, pendingImportOnStart = false) }
        }
    }

    val pendingFixWord = PlaybackStateStore.pendingPronunciationFixWord
    LaunchedEffect(pendingFixWord) {
        if (pendingFixWord != null) {
            viewModel.updateState {
                it.copy(
                    showPronunciationRules = true,
                    newRuleFind = pendingFixWord,
                    newRuleReplaceWith = ""
                )
            }
            PlaybackStateStore.pendingPronunciationFixWord = null
        }
    }

    LaunchedEffect(PlaybackStateStore.readerMode, uiState.activeDocument) {
        val activeDoc = uiState.activeDocument
        if (PlaybackStateStore.readerMode == ReaderMode.ORIGINAL && activeDoc != null) {
            val activeMetadata = uiState.documents.firstOrNull { it.id == activeDoc.id }
            if (activeMetadata != null) {
                val file = documentRepository.originalFile(activeMetadata)
                val isPdf = activeMetadata.originalMimeType.contains("pdf", ignoreCase = true) ||
                        activeMetadata.originalFileName.endsWith(".pdf", ignoreCase = true) ||
                        (file != null && file.exists() && runCatching {
                            file.inputStream().use { input ->
                                val bytes = ByteArray(4)
                                val read = input.read(bytes)
                                read == 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()
                            }
                        }.getOrDefault(false))
                if (isPdf) {
                    context.startActivity(
                        VeritasPdfViewerActivity.intent(
                            context,
                            activeMetadata.id
                        )
                    )
                    PlaybackStateStore.readerMode = ReaderMode.TEXT
                } else {
                    viewModel.updateState { it.copy(showCanvasView = true) }
                    PlaybackStateStore.readerMode = ReaderMode.TEXT
                }
            }
        }
    }

    BackHandler(enabled = true) {
        when {
            uiState.showExitConfirmationDialog -> viewModel.updateState { it.copy(showExitConfirmationDialog = false) }
            uiState.detailsTarget != null -> viewModel.updateState { it.copy(detailsTarget = null) }
            uiState.renameTarget != null -> viewModel.updateState { it.copy(renameTarget = null) }
            uiState.collectionTarget != null -> viewModel.updateState { it.copy(collectionTarget = null) }
            uiState.showClassicsCatalog -> viewModel.updateState { it.copy(showClassicsCatalog = false) }
            uiState.showOceanOfPdfBrowser -> viewModel.updateState { it.copy(showOceanOfPdfBrowser = false) }
            uiState.showBookBrowser -> viewModel.updateState { it.copy(showBookBrowser = false) }
            uiState.showUserManual -> viewModel.updateState { it.copy(showUserManual = false) }
            uiState.showAccessibilitySettings -> viewModel.updateState { it.copy(showAccessibilitySettings = false) }
            uiState.showTextEditor -> viewModel.dismissTextEditor()
            uiState.showReadingHistory -> viewModel.updateState { it.copy(showReadingHistory = false) }
            uiState.showDocumentNotes -> viewModel.updateState { it.copy(showDocumentNotes = false) }
            uiState.showAiStudyTools -> viewModel.updateState { it.copy(showAiStudyTools = false) }
            uiState.showReadingLists -> viewModel.updateState { it.copy(showReadingLists = false) }
            uiState.showReaderSettings -> viewModel.updateState { it.copy(showReaderSettings = false) }
            uiState.showVoiceStudio -> viewModel.updateState { it.copy(showVoiceStudio = false) }
            uiState.showNarrationStudio -> viewModel.updateState { it.copy(showNarrationStudio = false) }
            uiState.showPronunciationRules -> viewModel.updateState { it.copy(showPronunciationRules = false) }
            uiState.showSleepTimerDialog -> viewModel.updateState { it.copy(showSleepTimerDialog = false) }
            uiState.showGeneralNotesEditor -> viewModel.updateState { it.copy(showGeneralNotesEditor = false, generalNoteEditorTarget = null) }
            uiState.noteTargetIndexes.isNotEmpty() || uiState.noteTargetIndex != null -> viewModel.dismissSentenceNote()
            uiState.showCanvasView -> viewModel.updateState { it.copy(showCanvasView = false) }
            uiState.showFileBrowser && uiState.fileBrowserBackStack.isNotEmpty() -> viewModel.goUpFileBrowserDirectory()
            uiState.showFileBrowser -> viewModel.updateState { it.copy(showFileBrowser = false) }
            uiState.navStack.isNotEmpty() -> viewModel.navigateBack()
            uiState.activeDocument != null -> viewModel.returnToLibrary()
            else -> viewModel.updateState { it.copy(showExitConfirmationDialog = true) }
        }
    }

    if (uiState.showExitConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.updateState { it.copy(showExitConfirmationDialog = false) } },
            title = { Text("Exit Vern?") },
            text = { Text("Are you sure you want to close Vern?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updateState { it.copy(showExitConfirmationDialog = false) }
                        (context as? ComponentActivity)?.finish()
                    }
                ) {
                    Text("Exit")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.updateState { it.copy(showExitConfirmationDialog = false) } }
                ) {
                    Text("Cancel")
                }
            }
        )
    }


}
