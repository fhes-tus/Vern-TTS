package com.veritas.reader

import com.veritas.reader.ui.saveNotesSettings
import com.veritas.reader.ui.currentReaderIndex

import com.veritas.reader.ui.withVisibility

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.veritas.reader.ui.ReaderUiState
import com.veritas.reader.ui.ReaderViewModel
import com.veritas.reader.ui.addDocumentToReadingList
import com.veritas.reader.ui.archiveReadingList
import com.veritas.reader.ui.cancelAiStudyGeneration
import com.veritas.reader.ui.cancelAudioExport
import com.veritas.reader.ui.cancelPendingImport
import com.veritas.reader.ui.cancelSleepTimer
import com.veritas.reader.ui.cancelUpdateDownload
import com.veritas.reader.ui.clearAiPromptHistory
import com.veritas.reader.ui.createReadingList
import com.veritas.reader.ui.deleteAiPromptTemplate
import com.veritas.reader.ui.deleteGeneralNote
import com.veritas.reader.ui.deleteReadingList
import com.veritas.reader.ui.dismissReleaseNotes
import com.veritas.reader.ui.downloadClassicBook
import com.veritas.reader.ui.duplicateGeneralNote
import com.veritas.reader.ui.executePendingImport
import com.veritas.reader.ui.generateInAppExplanation
import com.veritas.reader.ui.generateInAppFlashcards
import com.veritas.reader.ui.generateInAppQuiz
import com.veritas.reader.ui.generateInAppStudyGuide
import com.veritas.reader.ui.generateInAppStudySummary
import com.veritas.reader.ui.importDownloadedBook
import com.veritas.reader.ui.importFlashcards
import com.veritas.reader.ui.isInstalledFromGooglePlay
import com.veritas.reader.ui.moveReadingListDocument
import com.veritas.reader.ui.openGooglePlayStore
import com.veritas.reader.ui.rateFlashcardRecall
import com.veritas.reader.ui.recordAiPrompt
import com.veritas.reader.ui.recordQuizScore
import com.veritas.reader.ui.removeDocumentFromReadingList
import com.veritas.reader.ui.saveAiPromptTemplate
import com.veritas.reader.ui.saveAskAiSettings
import com.veritas.reader.ui.createNoteLabel
import com.veritas.reader.ui.saveGeneralNote
import com.veritas.reader.ui.saveNarrationSettings
import com.veritas.reader.ui.saveQuiz
import com.veritas.reader.ui.saveSentenceNote
import com.veritas.reader.ui.screens.AskAiSettingsDialog
import com.veritas.reader.ui.screens.BookCatalogBrowserDialog
import com.veritas.reader.ui.screens.ClassicsCatalogDialog
import com.veritas.reader.ui.screens.GeneralNotesEditor
import com.veritas.reader.ui.screens.NarrationStudioDialog
import com.veritas.reader.ui.screens.ReadingListsDialog
import com.veritas.reader.ui.screens.ReleaseNotesDialog
import com.veritas.reader.ui.screens.SleepTimerDialog
import com.veritas.reader.ui.screens.UpdateAvailableDialog
import com.veritas.reader.ui.screens.VeritasHomeTab
import com.veritas.reader.ui.setReadingListSortMode
import com.veritas.reader.ui.setSleepTimer
import com.veritas.reader.ui.shareExportedAudio
import com.veritas.reader.ui.startUpdateDownload


@Composable
internal fun MainCatalogAndToolsDialogsHost(
    viewModel: ReaderViewModel,
    uiState: ReaderUiState,
    pendingShareChooser: Pair<String, Uri?>?,
    onDismissShareChooser: () -> Unit,
    onOpenInNotes: (String, Uri?) -> Unit,
    onImportToReader: (String, Uri?) -> Unit
) {
    val context = LocalContext.current

    if (uiState.showNarrationStudio) {
        NarrationStudioDialog(
            settings = uiState.narrationSettings,
            sampleText = uiState.activeDocument?.chunks?.getOrNull(viewModel.currentReaderIndex)
                .orEmpty(),
            availableVoices = uiState.ttsVoices,
            voiceSettings = uiState.voiceSettings,
            pronunciationRules = uiState.pronunciationRules,
            onSettingsChange = { settings -> viewModel.saveNarrationSettings(settings) },
            onDismiss = { viewModel.updateState { it.withVisibility(VeritasScreen.NARRATION_STUDIO, false) } }
        )
    }

    if (uiState.showAskAiSettings) {
        AskAiSettingsDialog(
            settings = uiState.askAiSettings,
            onSettingsChange = { settings -> viewModel.saveAskAiSettings(settings) },
            onInstallAssistant = { packageName ->
                openPlayStoreForPackage(
                    context,
                    packageName
                )
            },
            onDismiss = { viewModel.updateState { it.withVisibility(VeritasScreen.ASK_AI_SETTINGS, false) } }
        )
    }

    if (uiState.showAiCenter) {
        AiCenterDialog(
            askAiSettings = uiState.askAiSettings,
            installedAiCount = installedAiOptions(context).size,
            documentCount = uiState.documents.size,
            onOpenAskAiSettings = {
                viewModel.updateState {
                    it.withVisibility(VeritasScreen.AI_CENTER, false).withVisibility(VeritasScreen.ASK_AI_SETTINGS, true)
                }
            },
            onOpenStudyTools = {
                viewModel.updateState {
                    it.withVisibility(VeritasScreen.AI_CENTER, false).withVisibility(VeritasScreen.AI_STUDY_TOOLS, true)
                }
            },
            onDismiss = { viewModel.updateState { it.withVisibility(VeritasScreen.AI_CENTER, false) } }
        )
    }

    if (uiState.showAiStudyTools && uiState.activeDocument == null) {
        AlertDialog(
            onDismissRequest = { viewModel.updateState { it.withVisibility(VeritasScreen.AI_STUDY_TOOLS, false) } },
            title = { Text("AI Study Tools") },
            text = { Text("Open a reading first, then AI Study Tools can prepare the current part or whole document.") },
            confirmButton = {
                TextButton(onClick = { viewModel.updateState { it.withVisibility(VeritasScreen.AI_STUDY_TOOLS, false) } }) {
                    Text(
                        "OK"
                    )
                }
            }
        )
    }

    uiState.activeDocument?.let { document ->
        if (uiState.showAiStudyTools) {
            AiAppStudyDialog(
                document = document,
                currentIndex = viewModel.currentReaderIndex,
                templates = uiState.aiPromptTemplates,
                history = uiState.aiPromptHistory,
                askAiSettings = uiState.askAiSettings,
                onUpdateAskAiSettings = { viewModel.saveAskAiSettings(it) },
                onDismiss = { viewModel.updateState { it.withVisibility(VeritasScreen.AI_STUDY_TOOLS, false) } },
                onSendToAiApp = { type, customInstruction, scope, range ->
                    val prompt = AiPromptLauncher.buildPrompt(
                        title = document.title,
                        chunks = document.chunks,
                        currentIndex = viewModel.currentReaderIndex,
                        type = type,
                        customInstruction = customInstruction,
                        scope = scope
                    )
                    AiPromptLauncher.launch(
                        context = context,
                        document = document,
                        currentIndex = viewModel.currentReaderIndex,
                        type = type,
                        customInstruction = customInstruction,
                        scope = scope,
                        customPageRange = range,
                        settings = uiState.askAiSettings,
                        // false: the study-tool prompt must be copied to the clipboard
                        // and prepended to the share body — noPrompt=true silently
                        // dropped the user's edited instructions entirely.
                        noPrompt = false
                    )
                    viewModel.recordAiPrompt(document.title, type.label, scope.label, prompt)
                },
                onSaveTemplate = { title, instruction ->
                    viewModel.saveAiPromptTemplate(
                        title,
                        instruction
                    )
                },
                onDeleteTemplate = { id -> viewModel.deleteAiPromptTemplate(id) },
                onClearHistory = { viewModel.clearAiPromptHistory() },
                onCopyText = { label, text -> copyTextToClipboard(context, label, text) },
                onSaveAiResultAsNote = { result ->
                    viewModel.updateState {
                        it.copy(
                            noteDraft = result,
                            noteTargetIndexes = listOf(viewModel.currentReaderIndex)
                        )
                    }
                    viewModel.saveSentenceNote()
                },
                onImportFlashcards = { name, cards ->
                    viewModel.importFlashcards(document.id ?: "pasted", name, cards)
                },
                onCancelGeneration = { viewModel.cancelAiStudyGeneration() },
                onGenerateInAppFlashcards = { scopeText, count, onComplete ->
                    viewModel.generateInAppFlashcards(
                        document = document,
                        count = count,
                        scopeText = scopeText,
                        setName = "${document.title} Flashcards",
                        onComplete = onComplete
                    )
                },
                onGenerateInAppQuiz = { scopeText, count, onComplete ->
                    viewModel.generateInAppQuiz(
                        document = document,
                        count = count,
                        scopeText = scopeText,
                        quizTitle = "${document.title} Quiz",
                        onComplete = onComplete
                    )
                },
                onGenerateInAppSummary = { scopeText, onComplete ->
                    viewModel.generateInAppStudySummary(
                        document = document,
                        scopeText = scopeText,
                        onComplete = onComplete
                    )
                },
                onGenerateInAppExplanation = { scopeText, targetPassage, onComplete ->
                    viewModel.generateInAppExplanation(
                        document = document,
                        scopeText = scopeText,
                        targetPassage = targetPassage,
                        onComplete = onComplete
                    )
                },
                onGenerateInAppStudyGuide = { scopeText, onComplete ->
                    viewModel.generateInAppStudyGuide(
                        document = document,
                        scopeText = scopeText,
                        onComplete = onComplete
                    )
                },
                onSaveQuiz = { quiz ->
                    viewModel.saveQuiz(quiz)
                },
                onRecordQuizScore = { quizId, score ->
                    viewModel.recordQuizScore(quizId, score)
                },
                onRateFlashcard = { cardId, recall ->
                    viewModel.rateFlashcardRecall(cardId, recall)
                },
                onOpenStudyHub = {
                    viewModel.updateState { it.withVisibility(VeritasScreen.AI_STUDY_TOOLS, false).withVisibility(VeritasScreen.AI_CENTER, false) }
                    viewModel.navigateToHomeTab(VeritasHomeTab.STUDY)
                }
            )
        }
    }

    if ((uiState.exportInProgress || uiState.exportMessage != null || uiState.exportedAudioFile != null) && !uiState.recordMode && !uiState.recordAwaitingDecision) {
        ExportAudioStatusDialog(
            inProgress = uiState.exportInProgress,
            message = uiState.exportMessage,
            file = uiState.exportedAudioFile,
            onShare = { file -> viewModel.shareExportedAudio(file) },
            onCancel = { viewModel.cancelAudioExport() },
            onDismiss = {
                viewModel.updateState {
                    it.copy(
                        exportMessage = null,
                        exportedAudioFile = null
                    )
                }
            }
        )
    }

    pendingShareChooser?.let { (text, uri) ->
        ShareTargetChooserDialog(
            sharedText = text,
            sharedUri = uri,
            onImportToReader = {
                onDismissShareChooser()
                onImportToReader(text, uri)
            },
            onAddToNotes = {
                onDismissShareChooser()
                onOpenInNotes(text, uri)
            },
            onDismiss = {
                onDismissShareChooser()
            }
        )
    }

    if (uiState.showSleepTimerDialog) {
        SleepTimerDialog(
            activeTimer = PlaybackStateStore.activeSleepTimerSnapshot(),
            onSetTimer = viewModel::setSleepTimer,
            onCancelTimer = viewModel::cancelSleepTimer,
            onDismiss = { viewModel.updateState { it.withVisibility(VeritasScreen.SLEEP_TIMER, false) } }
        )
    }

    if (uiState.showUpdateDialog) {
        val context = LocalContext.current
        val isPlayStore = remember(context) { isInstalledFromGooglePlay(context) }
        UpdateAvailableDialog(
            versionName = uiState.updateVersionName,
            changelog = uiState.updateChangelog,
            isDownloading = uiState.isDownloadingUpdate,
            downloadProgress = uiState.updateDownloadProgress,
            downloadError = uiState.updateDownloadError,
            isPlayStoreInstall = isPlayStore,
            onUpdate = {
                if (uiState.updateApkUrl.isNotEmpty()) {
                    viewModel.startUpdateDownload(uiState.updateApkUrl)
                } else {
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uiState.updateUrl))
                        context.startActivity(intent)
                    }
                    viewModel.updateState { it.copy(showUpdateDialog = false) }
                }
            },
            onOpenPlayStore = {
                openGooglePlayStore(context)
            },
            onCancelDownload = {
                viewModel.cancelUpdateDownload()
            },
            onDismiss = {
                viewModel.cancelUpdateDownload()
                viewModel.updateState { it.copy(showUpdateDialog = false) }
            }
        )
    }

    if (uiState.showReleaseNotesDialog) {
        ReleaseNotesDialog(
            versionName = uiState.releaseNotesVersionName,
            changelog = uiState.releaseNotesChangelog,
            onDismiss = {
                viewModel.dismissReleaseNotes()
            }
        )
    }

    if (uiState.showBookBrowser || uiState.showOceanOfPdfBrowser) {
        val url = uiState.bookBrowserUrl.ifBlank { "https://oceanofpdf.com/" }
        val name = uiState.bookBrowserTitle.ifBlank { "Free Books" }
        val query = uiState.bookBrowserQuery.ifBlank { uiState.oceanOfPdfQuery }
        BookCatalogBrowserDialog(
            initialUrl = url,
            siteName = name,
            initialQuery = query,
            onImportDownloadedFile = { file, title ->
                viewModel.importDownloadedBook(file, title)
            },
            onDismiss = {
                viewModel.updateState {
                    it.copy(
                        showBookBrowser = false,
                        showOceanOfPdfBrowser = false,
                        bookBrowserUrl = "",
                        bookBrowserTitle = "",
                        bookBrowserQuery = "",
                        oceanOfPdfQuery = ""
                    )
                }
            }
        )
    }

    val pending = uiState.pendingImport
    if (pending != null) {
        VeritasImportPreviewDialog(
            pendingImport = pending,
            onConfirm = viewModel::executePendingImport,
            onCancel = viewModel::cancelPendingImport
        )
    }

    if (uiState.showReadingLists) {
        ReadingListsDialog(
            catalog = uiState.readingListCatalog,
            documents = uiState.documents,
            activeDocumentId = uiState.activeDocument?.id,
            onDismiss = { viewModel.updateState { it.withVisibility(VeritasScreen.READING_LISTS, false) } },
            onCreateList = { title -> viewModel.createReadingList(title) },
            onAddDocument = viewModel::addDocumentToReadingList,
            onRemoveDocument = viewModel::removeDocumentFromReadingList,
            onOpenDocument = { doc ->
                viewModel.updateState { it.withVisibility(VeritasScreen.READING_LISTS, false) }
                viewModel.openSavedDocument(doc)
            },
            onMoveDocument = viewModel::moveReadingListDocument,
            onSetSortMode = viewModel::setReadingListSortMode,
            onArchiveList = viewModel::archiveReadingList,
            onDeleteList = viewModel::deleteReadingList
        )
    }

    if (uiState.showGeneralNotesEditor) {
        GeneralNotesEditor(
            notebooks = uiState.noteNotebooks,
            onCreateNotebook = { viewModel.createNoteLabel(it) },
            notesSettings = uiState.notesSettings,
            onSaveNotesSettings = { viewModel.saveNotesSettings(it) },
            note = uiState.generalNoteEditorTarget,
            saveStatus = uiState.generalNoteSaveStatus,
            onSave = { title, content, color, pinned, isChecklist, imageUrl, audioUrl, reminderAt, closeEditor, audioUrls, notebookIds ->
                viewModel.saveGeneralNote(title, content, color, pinned, isChecklist, imageUrl, audioUrl, reminderAt, closeEditor, audioUrls, notebookIds)
            },
            onDelete = { noteId -> viewModel.deleteGeneralNote(noteId) },
            onCopyDraft = { title, content, color, pinned, checklist, image, audios, reminder, notebookIds ->
                val draft = GeneralNote(id = uiState.generalNoteEditorTarget?.id.orEmpty(), title = title, content = content,
                    updatedAt = System.currentTimeMillis(), color = color, pinned = pinned, isChecklist = checklist,
                    imageUrl = image, audioUrl = audios.firstOrNull(), audioUrls = audios, reminderAt = reminder).withLabels(notebookIds)
                viewModel.duplicateGeneralNote(draft)
            },
            onDismiss = { viewModel.updateState { it.withVisibility(VeritasScreen.GENERAL_NOTES_EDITOR, false).copy(
                generalNoteEditorTarget = null
            ) } }
        )
    }

    uiState.activeDocument?.let { document ->
        if (uiState.showTranslationTools) {
            TranslationToolsDialog(
                document = document,
                currentIndex = viewModel.currentReaderIndex,
                onDismiss = { viewModel.updateState { it.withVisibility(VeritasScreen.TRANSLATION_TOOLS, false) } },
                onSend = { targetLanguage, mode ->
                    TranslationLauncher.launch(
                        context = context,
                        title = document.title,
                        chunks = document.chunks,
                        currentIndex = viewModel.currentReaderIndex,
                        targetLanguage = targetLanguage,
                        mode = mode
                    )
                    viewModel.updateState { it.withVisibility(VeritasScreen.TRANSLATION_TOOLS, false) }
                }
            )
        }
    }


}
