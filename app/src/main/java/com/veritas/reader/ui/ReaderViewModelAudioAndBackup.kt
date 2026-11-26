package com.veritas.reader.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import com.veritas.reader.AudioExportFormat
import com.veritas.reader.AudioExportManager
import com.veritas.reader.AutomaticBackupEntry
import com.veritas.reader.AutomaticBackupFiles
import com.veritas.reader.BackupRestoreInspection
import com.veritas.reader.BackupRestoreSafety
import com.veritas.reader.BackupReviewChangedException
import com.veritas.reader.GeneralNote
import com.veritas.reader.PlaybackStateStore
import com.veritas.reader.ReaderTextIndex
import com.veritas.reader.SavedDocument
import com.veritas.reader.SharedBackupFolder
import com.veritas.reader.SharedBackupFolderEntry
import com.veritas.reader.StudyGuidePdfExporter
import com.veritas.reader.applyPronunciationRules
import com.veritas.reader.buildBackupJson
import com.veritas.reader.buildSharedSyncJson
import com.veritas.reader.buildDocumentNotesExport
import com.veritas.reader.estimateFullBackupBytes
import com.veritas.reader.inspectBackup
import com.veritas.reader.loadAiPromptHistory
import com.veritas.reader.loadAiPromptTemplates
import com.veritas.reader.loadAllAnnotations
import com.veritas.reader.loadAllDocumentNotes
import com.veritas.reader.loadAnnotationCount
import com.veritas.reader.loadAnnotations
import com.veritas.reader.loadDocumentNote
import com.veritas.reader.loadGeneralNotes
import com.veritas.reader.loadPronunciationRules
import com.veritas.reader.loadQueueDocuments
import com.veritas.reader.loadReaderTrackerSnapshot
import com.veritas.reader.loadReadingHistory
import com.veritas.reader.loadReadingListCatalog
import com.veritas.reader.restoreBackupAuto
import com.veritas.reader.saveRecordedVoiceMemo
import com.veritas.reader.stageBackupForReview
import com.veritas.reader.writeFullBackupZip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

fun ReaderViewModel.exportActiveDocumentToAudio(format: AudioExportFormat = AudioExportFormat.WAV) {
    if (uiState.value.exportInProgress || uiState.value.recordMode || uiState.value.recordAwaitingDecision) return
    val doc = uiState.value.activeDocument ?: return
    if (doc.chunks.isEmpty()) {
        _uiState.update { it.copy(exportMessage = "This document has no readable text to export.") }
        return
    }
    stopServicePlayback()
    exportJob?.cancel()
    _uiState.update { it.copy(exportInProgress = true, exportedAudioFile = null,
        exportMessage = "Playback stopped while creating the audio file.") }
    exportJob = viewModelScope.launch(Dispatchers.IO) {
        val result = runCatching {
            AudioExportManager(getApplication()).export(
                title = doc.title,
                chunks = doc.chunks,
                rate = PlaybackStateStore.rate,
                pitch = PlaybackStateStore.pitch,
                documentId = doc.id.orEmpty(),
                pageNumbers = if (doc.sourceLabel.uppercase().contains("PPT")) com.veritas.reader.ReaderTextModelCache.get(doc.id, doc.rawText, doc.pageCount).sentences.map { it.pageNumber }.toIntArray() else null,
                curatedLabels = doc.id?.let { repository.findDocument(it)?.catalogId?.substringBefore(':') in listOf("classic_pilgrims_progress") } == true,
                format = format
            )
        }
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(exportInProgress = false) }
            exportJob = null
            result.onSuccess { exported ->
                _uiState.update {
                    it.copy(
                        exportedAudioFile = exported.file,
                        exportMessage = "Audio export complete: ${exported.displayName} (${exported.synthesizedParts} part${if (exported.synthesizedParts == 1) "" else "s"})."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        exportMessage = if (error is CancellationException) "Audio export cancelled." else "Audio export failed: ${error.message ?: "unknown error"}"
                    )
                }
            }
        }
    }
}

fun ReaderViewModel.cancelAudioExport() {
    exportJob?.cancel()
    _uiState.update {
        it.copy(
            exportInProgress = false,
            exportMessage = "Audio export cancelled."
        )
    }
}

fun ReaderViewModel.startRecordSoundFile() {
    if (uiState.value.recordMode || uiState.value.recordAwaitingDecision) return
    val doc = uiState.value.activeDocument ?: run {
        _uiState.update { it.copy(exportMessage = "Open a reading before recording a sound file.") }
        return
    }
    if (doc.chunks.isEmpty()) {
        _uiState.update { it.copy(exportMessage = "This document has no readable text to record.") }
        return
    }
    stopServicePlayback()
    exportJob?.cancel()
    val startedAt = System.currentTimeMillis()
    _uiState.update {
        it.copy(
            recordMode = true,
            recordAwaitingDecision = false,
            recordStartedAt = startedAt,
            recordElapsedSeconds = 0L,
            exportInProgress = true,
            exportedAudioFile = null,
            exportMessage = "Recording sound file from ${doc.title}..."
        )
    }
    exportJob = viewModelScope.launch(Dispatchers.IO) {
        val result = runCatching {
            AudioExportManager(getApplication()).exportToWav(
                title = doc.title,
                chunks = doc.chunks,
                rate = PlaybackStateStore.rate,
                pitch = PlaybackStateStore.pitch,
                documentId = doc.id.orEmpty(),
                pageNumbers = if (doc.sourceLabel.uppercase().contains("PPT")) com.veritas.reader.ReaderTextModelCache.get(doc.id, doc.rawText, doc.pageCount).sentences.map { it.pageNumber }.toIntArray() else null,
                curatedLabels = doc.id?.let { repository.findDocument(it)?.catalogId?.substringBefore(':') in listOf("classic_pilgrims_progress") } == true
            )
        }
        val elapsedSeconds = ((System.currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0L)
        withContext(Dispatchers.Main) {
            exportJob = null
            result.onSuccess { exported ->
                _uiState.update {
                    it.copy(
                        exportInProgress = false,
                        exportedAudioFile = exported.file,
                        recordAwaitingDecision = true,
                        recordElapsedSeconds = elapsedSeconds,
                        exportMessage = "Sound file ready: ${exported.displayName}"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        exportInProgress = false,
                        exportedAudioFile = null,
                        recordAwaitingDecision = true,
                        recordElapsedSeconds = elapsedSeconds,
                        exportMessage = if (error is CancellationException) {
                            "Sound file recording stopped before audio was created."
                        } else {
                            "Sound file recording failed: ${error.message ?: "unknown error"}"
                        }
                    )
                }
            }
        }
    }
}

fun ReaderViewModel.stopRecordSoundFile() {
    exportJob?.cancel()
    val elapsedSeconds = uiState.value.recordStartedAt.let { started ->
        if (started > 0L) ((System.currentTimeMillis() - started) / 1000L).coerceAtLeast(0L) else 0L
    }
    _uiState.update {
        it.copy(
            exportInProgress = false,
            recordAwaitingDecision = true,
            recordElapsedSeconds = elapsedSeconds,
            exportMessage = if (it.exportedAudioFile == null) "Stopping sound file recording..." else it.exportMessage
        )
    }
}

fun ReaderViewModel.saveRecordedSoundFile() {
    val state = uiState.value
    if (!state.recordAwaitingDecision || state.exportInProgress) return
    val file = state.exportedAudioFile ?: return
    val now = System.currentTimeMillis()
    val docTitle = state.activeDocument?.title.orEmpty().ifBlank { "Recorded Audio" }
    val memo = GeneralNote(
        id = UUID.randomUUID().toString(),
        title = "Voice Memo: $docTitle",
        content = "Sound recording from $docTitle\nRecorded ${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(now))}",
        updatedAt = now
    )
    _uiState.update { it.copy(exportInProgress = true) }
    viewModelScope.launch {
        try {
            val notes = withContext(Dispatchers.IO) { repository.saveRecordedVoiceMemo(memo, file) }
            _uiState.update {
                it.copy(generalNotes = notes, recordMode = false, recordAwaitingDecision = false,
                    exportInProgress = false, exportMessage = "Sound file saved to Voice Memos: ${file.name}")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            _uiState.update {
                it.copy(exportInProgress = false, exportMessage = "Could not save the sound file. Try saving it again.")
            }
        }
    }
}

fun ReaderViewModel.exportAudioToDevice(file: File, destinationUri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
        val success = runCatching {
            val destination = getApplication<Application>().contentResolver.openOutputStream(destinationUri)
                ?: throw IOException("Could not open the audio destination.")
            destination.use { output ->
                file.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
        }.isSuccess
        withContext(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                    exportMessage = if (success) "Audio file exported to device storage successfully." else "Failed to export audio file to device."
                )
            }
        }
    }
}

fun ReaderViewModel.discardRecordedSoundFile() {
    if (uiState.value.exportInProgress && uiState.value.exportedAudioFile != null) return
    exportJob?.cancel()
    uiState.value.exportedAudioFile?.let { file ->
        runCatching { file.delete() }
    }
    _uiState.update {
        it.copy(
            recordMode = false,
            recordAwaitingDecision = false,
            recordStartedAt = 0L,
            recordElapsedSeconds = 0L,
            exportInProgress = false,
            exportedAudioFile = null,
            exportMessage = "Sound file discarded."
        )
    }
}

fun ReaderViewModel.shareExportedAudio(file: File) {
    val uri = com.veritas.reader.SafeFileSharing.uri(getApplication(), "${getApplication<Application>().packageName}.fileprovider", file)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = AudioExportFormat.forFileName(file.name).mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = android.content.ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    getApplication<Application>().startActivity(Intent.createChooser(shareIntent, "Share exported audio").apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
}

fun ReaderViewModel.shareActiveDocumentNotes() {
    val doc = uiState.value.activeDocument ?: return
    val notesText = buildDocumentNotesExport(doc, uiState.value.annotations, uiState.value.documentNoteDraft)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Notes - ${doc.title}")
        putExtra(Intent.EXTRA_TEXT, notesText)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    getApplication<Application>().startActivity(Intent.createChooser(shareIntent, "Export notes to notes app").apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
}

fun ReaderViewModel.exportStudyGuidePdf(document: SavedDocument? = null) {
    val active = uiState.value.activeDocument
    val documentId = document?.id ?: active?.id ?: return
    val title = document?.title ?: active?.title ?: return
    val context = getApplication<Application>()
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val rawText = document?.let { repository.readText(it) } ?: active?.rawText.orEmpty()
            val sentences = ReaderTextIndex.build(rawText, document?.pageCount ?: active?.pageCount ?: 0).sentences
            val generalNote = repository.loadDocumentNote(documentId)
            val file = StudyGuidePdfExporter.generateStudyGuidePdf(
                context = context,
                documentTitle = title,
                documentId = documentId,
                annotations = repository.loadAnnotations(documentId),
                generalNote = generalNote,
                sentences = sentences
            )
            if (file != null && file.exists()) {
                val uri = com.veritas.reader.SafeFileSharing.uri(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_SUBJECT, "Study Guide - $title")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                withContext(Dispatchers.Main) {
                    context.startActivity(
                        Intent.createChooser(shareIntent, "Share Study Guide PDF").apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                }
            }
            else error("The study guide could not be created.")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _uiState.update { it.copy(importMessage = "Could not export study guide: ${error.message ?: "Please try again."}") }
        }
    }
}

fun ReaderViewModel.exportLibraryBackup(uri: Uri) {
    if (uiState.value.backupInProgress) return
    backupJob?.cancel()
    _uiState.update { it.copy(backupInProgress = true, backupMessage = null) }
    backupJob = viewModelScope.launch(Dispatchers.IO) {
        val result = runCatching {
            val json = repository.buildBackupJson()
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("Could not open the selected backup location.")
        }
        withContext(Dispatchers.Main) {
            _uiState.update { state ->
                state.copy(
                    backupInProgress = false,
                    backupMessage = result.fold(
                        onSuccess = { "Vern backup/sync file exported successfully." },
                        onFailure = { if (it is CancellationException) "Backup export cancelled." else "Backup export failed: ${it.message ?: "unknown error"}" }
                    )
                )
            }
            backupJob = null
        }
    }
}

suspend fun ReaderViewModel.estimateFullBackupBytes(): Long =
    withContext(Dispatchers.IO) { runCatching { repository.estimateFullBackupBytes() }.getOrDefault(0L) }

fun ReaderViewModel.exportFullBackup(uri: Uri) {
    if (uiState.value.backupInProgress) return
    backupJob?.cancel()
    _uiState.update { it.copy(backupInProgress = true, backupMessage = null) }
    backupJob = viewModelScope.launch(Dispatchers.IO) {
        val result = runCatching {
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
                repository.writeFullBackupZip(output)
            } ?: throw IllegalStateException("Could not open the selected backup location.")
        }
        withContext(Dispatchers.Main) {
            _uiState.update { state ->
                state.copy(
                    backupInProgress = false,
                    backupMessage = result.fold(
                        onSuccess = { "Full backup exported - includes originals and covers." },
                        onFailure = { if (it is CancellationException) "Backup export cancelled." else "Full backup failed: ${it.message ?: "unknown error"}" }
                    )
                )
            }
            backupJob = null
        }
    }
}

fun ReaderViewModel.shareLibrarySyncPack() {
    if (uiState.value.backupInProgress) return
    backupJob?.cancel()
    _uiState.update { it.copy(backupInProgress = true, backupMessage = null) }
    backupJob = viewModelScope.launch(Dispatchers.IO) {
        val result = runCatching {
            val json = repository.buildBackupJson()
            val syncDir = File(getApplication<Application>().cacheDir, "shared_exports").apply { check(isDirectory || mkdirs()) }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(syncDir, "vern_sync_pack_$timestamp.json")
            file.writeText(json, Charsets.UTF_8)
            file
        }
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(backupInProgress = false) }
            backupJob = null
            result.onSuccess { file ->
                val uri = com.veritas.reader.SafeFileSharing.uri(getApplication(), "${getApplication<Application>().packageName}.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_SUBJECT, "Vern sync pack")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "Vern sync pack. Import this file on another device to merge safely.")
                    clipData = android.content.ClipData.newRawUri(file.name, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                getApplication<Application>().startActivity(Intent.createChooser(shareIntent, "Share sync pack").apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                _uiState.update { it.copy(backupMessage = "Sync pack ready. Choose Google Drive, WhatsApp, Files, Nearby Share, or any compatible app.") }
            }.onFailure { error ->
                _uiState.update { it.copy(backupMessage = if (error is CancellationException) "Sync share cancelled." else "Could not create sync pack: ${error.message ?: "unknown error"}") }
            }
        }
    }
}

fun ReaderViewModel.importLibraryBackup(uri: Uri) = prepareBackupReview({
    val resolver = getApplication<Application>().contentResolver
    runCatching {
        resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull().orEmpty()
}) {
    getApplication<Application>().contentResolver.openInputStream(uri) ?: throw IOException("Could not open the selected backup.")
}

internal fun ReaderViewModel.restoreAutomaticBackup(entry: AutomaticBackupEntry) = prepareBackupReview({ entry.name }) {
    AutomaticBackupFiles.open(getApplication(), entry)
}

internal fun ReaderViewModel.reviewSharedBackup(entry: SharedBackupFolderEntry) = prepareBackupReview({ entry.name }, entry.expectedSha256) {
    SharedBackupFolder.open(getApplication(), entry)
}

internal fun ReaderViewModel.sendSharedBackup(folder: Uri) {
    if (uiState.value.backupInProgress || pendingBackupReview.value != null) return
    _uiState.update { it.copy(backupInProgress = true, backupMessage = null) }
    backupJob = viewModelScope.launch(Dispatchers.IO) {
        val app = getApplication<Application>()
        try {
            SharedBackupFolder.send(app, folder, repository.buildSharedSyncJson())
            _uiState.update { it.copy(backupMessage = app.getString(com.veritas.reader.R.string.shared_sync_send_complete)) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _uiState.update { it.copy(backupMessage = app.getString(com.veritas.reader.R.string.shared_sync_send_failed)) }
        } finally {
            backupJob = null
            _uiState.update { it.copy(backupInProgress = false) }
        }
    }
}

private fun ReaderViewModel.prepareBackupReview(
    sourceName: () -> String,
    expectedSha256: String? = null,
    openStream: () -> InputStream
) {
    if (uiState.value.backupInProgress || pendingBackupReview.value != null) return
    _uiState.update { it.copy(backupInProgress = true, backupMessage = null) }
    backupJob = viewModelScope.launch(Dispatchers.IO) {
        val app = getApplication<Application>()
        val operationContext = coroutineContext
        var staged: File? = null
        try {
            val name = sourceName().filterNot(Char::isISOControl).take(160)
                .ifBlank { app.getString(com.veritas.reader.R.string.backup_review_selected_file) }
            val snapshot = stageBackupForReview(
                File(app.cacheDir, "backup_review"), openStream, expectedSha256, operationContext::ensureActive
            )
            staged = snapshot
            val review = repository.inspectBackup(snapshot, name, operationContext::ensureActive, allowSettings = expectedSha256 == null)
            operationContext.ensureActive()
            pendingBackupReview.value = review
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val detail = error.message ?: app.getString(com.veritas.reader.R.string.backup_review_selected_file)
            val message = if (expectedSha256 == null) {
                app.getString(com.veritas.reader.R.string.backup_review_failed, detail)
            } else {
                app.getString(com.veritas.reader.R.string.shared_sync_review_invalid)
            }
            _uiState.update { it.copy(backupMessage = message) }
        } finally {
            if (pendingBackupReview.value?.snapshot != staged) staged?.delete()
            backupJob = null
            _uiState.update { it.copy(backupInProgress = false) }
        }
    }
}

internal fun ReaderViewModel.dismissBackupReview() {
    val review = pendingBackupReview.value ?: return
    pendingBackupReview.value = null
    viewModelScope.launch(Dispatchers.IO) { review.snapshot.delete() }
}

internal fun ReaderViewModel.confirmBackupReview() {
    val review = pendingBackupReview.value ?: return
    if (uiState.value.backupInProgress) return
    pendingBackupReview.value = null
    mergeLibraryBackup(review)
}

internal fun ReaderViewModel.saveAutomaticBackupCopy(entry: AutomaticBackupEntry, uri: Uri) {
    if (uiState.value.backupInProgress) return
    _uiState.update { it.copy(backupInProgress = true, backupMessage = null) }
    backupJob = viewModelScope.launch(Dispatchers.IO) {
        val message = try {
            val app = getApplication<Application>()
            AutomaticBackupFiles.open(app, entry).use { input ->
                val output = app.contentResolver.openOutputStream(uri)
                    ?: throw IOException("Could not open the selected backup location.")
                output.use {
                    if (BackupRestoreSafety.copyBounded(input, it, entry.size) != entry.size) {
                        throw IOException("The saved backup was incomplete.")
                    }
                }
            }
            app.getString(com.veritas.reader.R.string.auto_backup_copy_saved)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            getApplication<Application>().getString(com.veritas.reader.R.string.auto_backup_copy_failed)
        }
        withContext(Dispatchers.Main) {
            backupJob = null
            _uiState.update { it.copy(backupInProgress = false, backupMessage = message) }
        }
    }
}

private fun ReaderViewModel.mergeLibraryBackup(review: BackupRestoreInspection) {
    if (uiState.value.backupInProgress) return
    _uiState.update { it.copy(backupInProgress = true, backupMessage = null) }
    backupJob = viewModelScope.launch(Dispatchers.IO) {
        var merged = false
        try {
            val restored = review.snapshot.inputStream().use { stream ->
                repository.restoreBackupAuto(stream, replaceExisting = false, expected = review)
            }
            merged = true
            val documents = repository.loadDocuments()
            val queuedDocuments = repository.loadQueueDocuments()
            val tracker = repository.loadReaderTrackerSnapshot()
            val generalNotes = repository.loadGeneralNotes()
            val pronunciationRules = repository.loadPronunciationRules()
            val notesSettings = NotesSettingsStore.load(getApplication())
            val readerSettings = repository.loadReaderSettings()
            val voiceSettings = repository.loadVoiceSettings()
            val narrationSettings = repository.loadNarrationSettings()
            val askAiSettings = repository.loadAskAiSettings()
            val aiPromptTemplates = repository.loadAiPromptTemplates()
            val aiPromptHistory = repository.loadAiPromptHistory()
            val readingListCatalog = repository.loadReadingListCatalog()
            val readingHistory = repository.loadReadingHistory()
            val allAnnotations = repository.loadAllAnnotations()
            val documentNotes = repository.loadAllDocumentNotes()
            val documentTitles = repository.loadAllDocumentTitles()
            val annotationCount = repository.loadAnnotationCount()
            val activeDocId = uiState.value.activeDocument?.id
            val annotations = if (activeDocId != null) repository.loadAnnotations(activeDocId) else emptyList()
            val conflicts = restored.documentConflictCount
            val conflictMessage = when (conflicts) {
                0 -> ""
                1 -> " Kept your local copy of 1 reading that differs from the backup. Its backup version and linked records were skipped."
                else -> " Kept your local copies of $conflicts readings that differ from the backup. Their backup versions and linked records were skipped."
            }
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        documents = documents,
                        queuedDocuments = queuedDocuments,
                        readerTrackerSnapshot = tracker,
                        generalNotes = generalNotes,
                        pronunciationRules = pronunciationRules,
                        notesSettings = notesSettings,
                        readerSettings = readerSettings,
                        voiceSettings = voiceSettings,
                        narrationSettings = narrationSettings,
                        askAiSettings = askAiSettings,
                        aiPromptTemplates = aiPromptTemplates,
                        aiPromptHistory = aiPromptHistory,
                        readingListCatalog = readingListCatalog,
                        readingHistory = readingHistory,
                        allAnnotations = allAnnotations,
                        documentNotes = documentNotes,
                        documentTitles = documentTitles,
                        annotationCount = annotationCount,
                        annotations = annotations,
                        backupMessage = "Backup merged: ${restored.documentCount} new reading${if (restored.documentCount == 1) "" else "s"}, ${restored.annotationCount} bookmark/note${if (restored.annotationCount == 1) "" else "s"}, ${restored.generalNoteCount} note${if (restored.generalNoteCount == 1) "" else "s"}, ${restored.flashcardCount} flashcard${if (restored.flashcardCount == 1) "" else "s"}, ${restored.trackerDayCount} tracked day${if (restored.trackerDayCount == 1) "" else "s"}, ${restored.readingListCount} list${if (restored.readingListCount == 1) "" else "s"}.$conflictMessage"
                    )
                }
                PlaybackStateStore.queueCount = queuedDocuments.size
            }
        } catch (_: BackupReviewChangedException) {
            val app = getApplication<Application>()
            val operationContext = coroutineContext
            try {
                pendingBackupReview.value = repository.inspectBackup(
                    review.snapshot, review.sourceName, operationContext::ensureActive, allowSettings = review.allowSettings
                )
                _uiState.update { it.copy(backupMessage = app.getString(com.veritas.reader.R.string.backup_review_changed)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(backupMessage = app.getString(com.veritas.reader.R.string.backup_review_failed, error.message.orEmpty())) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val message = if (merged) {
                "Backup merged. Reopen Vern to refresh your library."
            } else {
                "Backup import failed: ${error.message ?: "unknown error"}"
            }
            _uiState.update { it.copy(backupMessage = message) }
        } finally {
            if (pendingBackupReview.value?.snapshot != review.snapshot) review.snapshot.delete()
            backupJob = null
            _uiState.update { it.copy(backupInProgress = false) }
        }
    }
}
