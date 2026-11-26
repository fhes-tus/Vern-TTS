package com.veritas.reader.ui

import com.veritas.reader.VeritasScreen
import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.veritas.reader.VeritasBrowserFile
import com.veritas.reader.VeritasFileBrowserScanner
import com.veritas.reader.VeritasFileBrowserScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun ReaderViewModel.approveFileBrowserFolder(uri: Uri?) {
    if (uri == null) return
    val context = getApplication<Application>()
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
    runCatching {
        context.contentResolver.takePersistableUriPermission(uri, flags)
    }
    _uiState.update {
        it.copy(
            fileBrowserLocation = null,
            fileBrowserFiles = emptyList(),
            fileBrowserBackStack = emptyList()
        )
    }
    refreshFileBrowser()
}

fun ReaderViewModel.clearFileBrowserAccess() {
    scanJob?.cancel()
    scanJob = null
    fileBrowserCache.evictAll()
    val context = getApplication<Application>()
    context.contentResolver.persistedUriPermissions.forEach { permission ->
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                permission.uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }
    _uiState.update {
        it.copy(
            fileBrowserRoots = emptyList(),
            fileBrowserFiles = emptyList(),
            fileBrowserScanning = false,
            fileBrowserMessage = null,
            fileBrowserLocation = null,
            fileBrowserBackStack = emptyList()
        )
    }
}

fun ReaderViewModel.openFileBrowser() {
    if (uiState.value.showFileBrowser) return
    _uiState.update { it.withVisibility(VeritasScreen.FILE_BROWSER, true) }
    refreshFileBrowser()
}

fun ReaderViewModel.closeFileBrowser() {
    scanJob?.cancel()
    scanJob = null
    _uiState.update { it.withVisibility(VeritasScreen.FILE_BROWSER, false).copy(fileBrowserScanning = false) }
}

fun ReaderViewModel.enterFileBrowserDirectory(entry: VeritasBrowserFile) {
    val next = entry.targetLocation ?: return
    val current = uiState.value.fileBrowserLocation
    _uiState.update {
        it.copy(
            fileBrowserLocation = next,
            fileBrowserFiles = fileBrowserCache.get(next)?.files.orEmpty(),
            fileBrowserBackStack = if (current == null) it.fileBrowserBackStack else it.fileBrowserBackStack + current
        )
    }
    refreshFileBrowser()
}

fun ReaderViewModel.goUpFileBrowserDirectory() {
    val stack = uiState.value.fileBrowserBackStack
    val previous = stack.lastOrNull() ?: return
    _uiState.update {
        it.copy(
            fileBrowserLocation = previous,
            fileBrowserFiles = fileBrowserCache.get(previous)?.files.orEmpty(),
            fileBrowserBackStack = stack.dropLast(1)
        )
    }
    refreshFileBrowser()
}

/**
 * Removes files from the device, then re-lists so they stop appearing.
 *
 * Two kinds of entry reach here. The recursive walk yields file:// URIs, which All Files
 * access can unlink directly. The MediaStore index yields content:// URIs, which are
 * deleted through the resolver so the index is updated too - unlinking those by path
 * alone would leave a stale row pointing at nothing.
 */
fun ReaderViewModel.deleteBrowserFiles(files: List<VeritasBrowserFile>) {
    if (files.isEmpty()) return
    viewModelScope.launch {
        val context = getApplication<Application>()
        var deleted = 0
        val failures = mutableListOf<String>()
        withContext(Dispatchers.IO) {
            files.forEach { entry ->
                val ok = runCatching {
                    if (entry.uri.scheme == "file") {
                        val target = entry.uri.path?.let { File(it) }
                        target != null && target.exists() && target.delete()
                    } else {
                        context.contentResolver.delete(entry.uri, null, null) > 0
                    }
                }.getOrDefault(false)
                if (ok) deleted++ else failures.add(entry.name)
            }
        }
        val summary = when {
            failures.isEmpty() && deleted == 1 -> "Deleted 1 file."
            failures.isEmpty() -> "Deleted $deleted files."
            deleted == 0 -> "Could not delete ${failures.size} file(s). Android may be protecting them."
            else -> "Deleted $deleted, could not delete ${failures.size}."
        }
        _uiState.update { it.copy(importMessage = summary) }
        refreshFileBrowser()
    }
}

fun ReaderViewModel.refreshFileBrowser() {
    val previous = uiState.value
    if (!previous.showFileBrowser) return
    scanJob?.cancel()
    _uiState.update { it.copy(fileBrowserScanning = true, fileBrowserMessage = null) }
    val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
        val owner = coroutineContext.job
        val scanResult = try {
            withContext(Dispatchers.IO) {
                val context = getApplication<Application>()
                val roots = VeritasFileBrowserScanner.persistedRoots(context, previous.fileBrowserRoots) {
                    owner.ensureActive()
                }
                val allFilesGranted = hasAllFilesAccess()
                val previousLocation = previous.fileBrowserLocation
                val location = previousLocation?.takeIf { current ->
                    if (current.filePath != null) allFilesGranted else roots.any { it.uri == current.rootUri }
                }
                val keepPreviousFiles = location != null && previous.fileBrowserFiles.isNotEmpty() &&
                    fileBrowserCache.get(location)?.files === previous.fileBrowserFiles
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            fileBrowserRoots = roots,
                            fileBrowserAllFilesGranted = allFilesGranted,
                            fileBrowserLocation = location,
                            fileBrowserFiles = if (location == previousLocation) it.fileBrowserFiles else emptyList(),
                            fileBrowserBackStack = if (location == previousLocation) it.fileBrowserBackStack else emptyList()
                        )
                    }
                }
                val publishProgress: ((VeritasFileBrowserScanResult) -> Unit)? =
                    if (keepPreviousFiles) null else { partial ->
                        viewModelScope.launch(Dispatchers.Main) {
                            if (scanJob === owner && owner.isActive && uiState.value.showFileBrowser) {
                                _uiState.update {
                                    it.copy(fileBrowserFiles = partial.files, fileBrowserLocation = partial.location)
                                }
                            }
                        }
                    }
                VeritasFileBrowserScanner.scan(
                    context = context,
                    roots = roots,
                    includeAllFilesAccess = allFilesGranted,
                    location = location,
                    checkCancelled = { owner.ensureActive() },
                    onProgress = publishProgress
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (scanJob === owner) {
                scanJob = null
                _uiState.update {
                    it.copy(fileBrowserScanning = false, fileBrowserMessage = "Scan failed: ${error.message}")
                }
            }
            return@launch
        }
        if (scanJob !== owner) return@launch
        scanJob = null
        scanResult.location?.let { fileBrowserCache.put(it, scanResult) }
        _uiState.update {
            it.copy(
                fileBrowserScanning = false,
                fileBrowserFiles = scanResult.files,
                fileBrowserLocation = scanResult.location,
                fileBrowserMessage = scanResult.diagnostics.joinToString("\n").ifBlank {
                    if (scanResult.files.isEmpty()) "No files found" else null
                }
            )
        }
    }
    scanJob = job
    job.start()
}
