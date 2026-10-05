package com.veritas.reader.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import com.veritas.reader.AudioExportManager
import com.veritas.reader.GeneralNote
import com.veritas.reader.PlaybackStateStore
import com.veritas.reader.ReaderTextIndex
import com.veritas.reader.StudyGuidePdfExporter
import com.veritas.reader.applyPronunciationRules
import com.veritas.reader.buildBackupJson
import com.veritas.reader.buildDocumentNotesExport
import com.veritas.reader.estimateFullBackupBytes
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
import com.veritas.reader.loadReadingHistory
import com.veritas.reader.loadReadingListCatalog
import com.veritas.reader.restoreBackupAuto
import com.veritas.reader.saveGeneralNotes
import com.veritas.reader.writeFullBackupZip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

fun ReaderViewModel.exportActiveDocumentToAudio() {
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
            AudioExportManager(getApplication()).exportToWav(
                title = doc.title,
                chunks = doc.chunks,
                rate = PlaybackStateStore.rate,
                pitch = PlaybackStateStore.pitch,
                transformText = { repository.applyPronunciationRules(it) }
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
                transformText = { repository.applyPronunciationRules(it) }
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
    val file = uiState.value.exportedAudioFile
    val doc = uiState.value.activeDocument
    if (file != null && file.exists()) {
        val now = System.currentTimeMillis()
        val docTitle = doc?.title?.ifBlank { "Recorded Audio" } ?: "Recorded Audio"
        val newMemo = GeneralNote(
            id = UUID.randomUUID().toString(),
            title = "Voice Memo: $docTitle",
            content = "Sound recording from $docTitle\nRecorded ${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(now))}",
            updatedAt = now,
            audioUrl = file.absolutePath,
            audioUrls = listOf(file.absolutePath)
        )
        viewModelScope.launch(Dispatchers.IO) {
            val existingNotes = repository.loadGeneralNotes()
            repository.saveGeneralNotes(listOf(newMemo) + existingNotes)
        }
    }
    _uiState.update {
        it.copy(
            recordMode = false,
            recordAwaitingDecision = false,
            exportInProgress = false,
            exportMessage = file?.let { saved -> "Sound file saved to Voice Memos: ${saved.name}" } ?: "No sound file was created."
        )
    }
}

fun ReaderViewModel.exportAudioToDevice(file: File, destinationUri: Uri) {
    viewModelScope.launch(Dispatchers.IO) {
        val success = runCatching {
            getApplication<Application>().contentResolver.openOutputStream(destinationUri)?.use { output ->
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
    val uri = FileProvider.getUriForFile(getApplication(), "${getApplication<Application>().packageName}.fileprovider", file)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/wav"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
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

fun ReaderViewModel.exportStudyGuidePdf() {
    val doc = uiState.value.activeDocument ?: return
    val context = getApplication<Application>()
    viewModelScope.launch(Dispatchers.IO) {
        val sentences = ReaderTextIndex.build(doc.rawText, doc.pageCount).sentences
        val generalNote = repository.loadDocumentNote(doc.id.orEmpty())
        val file = StudyGuidePdfExporter.generateStudyGuidePdf(
            context = context,
            documentTitle = doc.title,
            documentId = doc.id.orEmpty(),
            annotations = uiState.value.annotations,
            generalNote = generalNote,
            sentences = sentences
        )
        if (file != null && file.exists()) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_SUBJECT, "Study Guide - ${doc.title}")
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
    }
}

fun ReaderViewModel.exportLibraryBackup(uri: Uri) {
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
    backupJob?.cancel()
    _uiState.update { it.copy(backupInProgress = true, backupMessage = null) }
    backupJob = viewModelScope.launch(Dispatchers.IO) {
        val result = runCatching {
            val json = repository.buildBackupJson()
            val syncDir = File(getApplication<Application>().cacheDir, "vern_sync").apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(syncDir, "vern_sync_pack_$timestamp.json")
            file.writeText(json, Charsets.UTF_8)
            file
        }
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(backupInProgress = false) }
            backupJob = null
            result.onSuccess { file ->
                val uri = FileProvider.getUriForFile(getApplication(), "${getApplication<Application>().packageName}.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_SUBJECT, "Vern sync pack")
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "Vern sync pack. Import this file on another device to merge safely.")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                getApplication<Application>().startActivity(Intent.createChooser(shareIntent, "Share sync pack").apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
                _uiState.update { it.copy(backupMessage = "Sync pack ready. Choose Google Drive, WhatsApp, Files, Nearby Share, or any compatible app.") }
            }.onFailure { error ->
                _uiState.update { it.copy(backupMessage = if (error is CancellationException) "Sync share cancelled." else "Could not create sync pack: ${error.message ?: "unknown error"}") }
            }
        }
    }
}

fun ReaderViewModel.importLibraryBackup(uri: Uri) {
    backupJob?.cancel()
    _uiState.update { it.copy(backupInProgress = true, backupMessage = null) }
    backupJob = viewModelScope.launch(Dispatchers.IO) {
        val result = runCatching {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { stream ->
                // Handles both plain JSON backups and full .zip backups.
                repository.restoreBackupAuto(stream, replaceExisting = false)
            } ?: throw IllegalStateException("Could not open the selected backup file.")
        }
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(backupInProgress = false) }
            backupJob = null
            result.onSuccess { restored ->
                refreshAll()
                viewModelScope.launch(Dispatchers.IO) {
                    val documents = repository.loadDocuments()
                    val queuedDocuments = repository.loadQueueDocuments()
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
                    val annotationCount = repository.loadAnnotationCount()
                    val activeDocId = uiState.value.activeDocument?.id
                    val annotations = if (activeDocId != null) repository.loadAnnotations(activeDocId) else emptyList()
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                documents = documents,
                                queuedDocuments = queuedDocuments,
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
                                annotationCount = annotationCount,
                                annotations = annotations,
                                backupMessage = "Backup merged: ${restored.documentCount} reading${if (restored.documentCount == 1) "" else "s"}, ${restored.annotationCount} bookmark/note${if (restored.annotationCount == 1) "" else "s"}, ${restored.generalNoteCount} note${if (restored.generalNoteCount == 1) "" else "s"}, ${restored.flashcardCount} flashcard${if (restored.flashcardCount == 1) "" else "s"}, ${restored.trackerDayCount} tracked day${if (restored.trackerDayCount == 1) "" else "s"}, ${restored.readingListCount} list${if (restored.readingListCount == 1) "" else "s"}."
                            )
                        }
                        PlaybackStateStore.queueCount = queuedDocuments.size
                    }
                }
            }.onFailure { error ->
                _uiState.update { it.copy(backupMessage = if (error is CancellationException) "Backup import cancelled." else "Backup import failed: ${error.message ?: "unknown error"}") }
            }
        }
    }
}
