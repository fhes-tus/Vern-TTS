package com.veritas.reader.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.veritas.reader.DocumentImportWorker
import com.veritas.reader.DocumentBatchQueueWorker
import com.veritas.reader.BATCH_QUEUE_WORK_TAG
import com.veritas.reader.IMPORT_ORDER_PREFIX
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

internal const val DOCUMENT_BATCH_TAG = "vern_document_batch"
private const val GROUP_PREFIX = "vern_batch_group:"

fun ReaderViewModel.importMultipleDocuments(uris: List<Uri>, queue: Boolean) {
    val sources = uris.distinct()
    if (sources.isEmpty()) return
    if (batchImportJob?.isActive == true || uiState.value.isBatchImporting) {
        _uiState.update { it.copy(importMessage = "The current batch is still importing.") }
        return
    }
    autoOpenImportId = null
    _uiState.update { it.copy(isBatchImporting = true, batchImportTotal = sources.size,
        batchImportCurrent = 0, batchImportFailed = 0, importInProgress = true,
        importAwaitingReadyPages = false, importSourceName = "${sources.size} files",
        importMessage = "Importing ${sources.size} files in the background.") }
    batchImportJob = viewModelScope.launch {
        val manager = WorkManager.getInstance(getApplication<Application>())
        val group = "$GROUP_PREFIX${System.currentTimeMillis()}:${UUID.randomUUID()}"
        try {
            val requests = withContext(Dispatchers.IO) {
                val app = getApplication<Application>()
                sources.mapIndexed { index, uri ->
                    if (uri.scheme == "content") {
                        runCatching { app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    }
                    OneTimeWorkRequestBuilder<DocumentImportWorker>()
                        .setInputData(workDataOf("uri" to uri.toString(), "batchImport" to true))
                        .addTag(DOCUMENT_BATCH_TAG).addTag(group).addTag("$IMPORT_ORDER_PREFIX$index").build()
                }
            }
            // WorkManager owns the entire selection before observing begins.
            // Back, rotation or process death cannot abandon the remaining files.
            var chain = manager.beginUniqueWork("vern_document_batches", ExistingWorkPolicy.APPEND_OR_REPLACE, requests)
            if (queue) {
                chain = chain.then(OneTimeWorkRequestBuilder<DocumentBatchQueueWorker>()
                    .setInputData(workDataOf("batchGroup" to group))
                    .addTag(DOCUMENT_BATCH_TAG).addTag(group).addTag(BATCH_QUEUE_WORK_TAG).build())
            }
            withContext(Dispatchers.IO) { chain.enqueue().result.get() }
            observeBatchImport(manager, group)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _uiState.update { it.copy(isBatchImporting = false, importInProgress = pendingImportIds.isNotEmpty(),
                importMessage = "Could not track the batch import: ${error.message ?: "unknown error"}") }
        }
    }
}

/** Reattach to unfinished durable batches; do not replay old completion messages. */
internal fun ReaderViewModel.resumeBatchImports() {
    viewModelScope.launch {
        try {
            val manager = WorkManager.getInstance(getApplication<Application>())
            val activeGroup = withContext(Dispatchers.IO) {
                manager.getWorkInfosByTagFlow(DOCUMENT_BATCH_TAG).first()
                    .filterNot { it.state.isFinished }
                    .flatMap { it.tags }.filter { it.startsWith(GROUP_PREFIX) }.minOrNull()
            } ?: return@launch
            if (batchImportJob?.isActive == true) return@launch
            batchImportJob = viewModelScope.launch {
                try {
                    observeBatchImport(manager, activeGroup)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    android.util.Log.w("ReaderViewModel", "Could not track restored batch", error)
                    _uiState.update { it.copy(isBatchImporting = false,
                        importMessage = "Batch files are still importing in the background, but status could not be refreshed.") }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("ReaderViewModel", "Could not restore batch import status", error)
        }
    }
}

private suspend fun ReaderViewModel.observeBatchImport(manager: WorkManager, group: String) {
    var knownLibraryItems = emptySet<UUID>()
    var knownFinished = -1
    var lastActiveChars = -1
    manager.getWorkInfosByTagFlow(group).first { allWork ->
        val work = allWork.filterNot { BATCH_QUEUE_WORK_TAG in it.tags }
        if (work.isEmpty()) return@first false
        val finished = work.count { it.state.isFinished }
        val failed = work.count { it.state.isFinished && (it.state != WorkInfo.State.SUCCEEDED ||
            it.outputData.getString("error") != null || it.outputData.getString("documentId") == null) }
        work.forEach { if (it.state.isFinished) pendingImportIds.remove(it.id) else pendingImportIds.add(it.id) }
        val visibleItems = work.filter { it.state.isFinished || it.progress.getString("firstChunkDocumentId") != null }.map { it.id }.toSet()
        if (visibleItems != knownLibraryItems || finished != knownFinished) {
            knownLibraryItems = visibleItems
            knownFinished = finished
            refreshAll()
        }
        val activeId = uiState.value.activeDocument?.id
        val activeWork = work.firstOrNull { it.id.toString() == activeId }
        val chars = activeWork?.progress?.getInt("importedCharCount", 0) ?: 0
        if (activeId != null && activeWork != null && (chars != lastActiveChars || activeWork.state.isFinished)) {
            lastActiveChars = chars
            refreshImportedReader(activeId)
        }
        val complete = allWork.all { it.state.isFinished }
        if (finished > failed && !uiState.value.questImportDone) completeQuestImport()
        val queueFailures = work.count { it.outputData.getString("queueError") != null } +
            allWork.filter { BATCH_QUEUE_WORK_TAG in it.tags }.sumOf { it.outputData.getInt("queueFailures", 0) +
                if (it.state == WorkInfo.State.FAILED) 1 else 0 }
        val duplicates = work.count { it.outputData.getBoolean("duplicate", false) }
        val missingOriginals = work.count { it.outputData.getString("originalCopyWarning") != null }
        if (complete && allWork.size != work.size) refreshAll()
        _uiState.update { it.copy(isBatchImporting = !complete, batchImportTotal = work.size,
            batchImportCurrent = finished, batchImportFailed = failed,
            importInProgress = pendingImportIds.isNotEmpty(), importAwaitingReadyPages = false,
            importSourceName = if (complete) "" else "${work.size} files",
            importMessage = if (complete) "Batch finished: ${finished - failed - duplicates} imported, $failed failed." +
                (if (duplicates > 0) " $duplicates already in your library." else "") +
                (if (queueFailures > 0) " $queueFailures could not be added to the reading queue." else "") +
                (if (missingOriginals > 0) " Original View is unavailable for $missingOriginals." else "") else it.importMessage) }
        complete
    }
}
