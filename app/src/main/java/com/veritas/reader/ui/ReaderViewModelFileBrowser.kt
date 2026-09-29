package com.veritas.reader.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.veritas.reader.VeritasBrowserFile
import com.veritas.reader.VeritasFileBrowserScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
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
            fileBrowserRoots = VeritasFileBrowserScanner.persistedRoots(context),
            fileBrowserLocation = null,
            fileBrowserBackStack = emptyList()
        )
    }
    refreshFileBrowser()
}

fun ReaderViewModel.refreshFileBrowserAccessState() {
    val context = getApplication<Application>()
    _uiState.update {
        it.copy(
            fileBrowserRoots = VeritasFileBrowserScanner.persistedRoots(context),
            fileBrowserAllFilesGranted = hasAllFilesAccess()
        )
    }
}

fun ReaderViewModel.clearFileBrowserAccess() {
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
            fileBrowserLocation = null,
            fileBrowserBackStack = emptyList()
        )
    }
}

fun ReaderViewModel.openFileBrowser() {
    _uiState.update { it.copy(showFileBrowser = true) }
    refreshFileBrowser()
}

fun ReaderViewModel.enterFileBrowserDirectory(entry: VeritasBrowserFile) {
    val next = entry.targetLocation ?: return
    val current = uiState.value.fileBrowserLocation
    _uiState.update {
        it.copy(
            fileBrowserLocation = next,
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
    scanJob?.cancel()
    _uiState.update { it.copy(fileBrowserScanning = true, fileBrowserMessage = null) }
    scanJob = viewModelScope.launch(Dispatchers.IO) {
        val context = getApplication<Application>()
        val roots = VeritasFileBrowserScanner.persistedRoots(context)
        val allFilesGranted = hasAllFilesAccess()
        withContext(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                    fileBrowserRoots = roots,
                    fileBrowserAllFilesGranted = allFilesGranted
                )
            }
        }
        val result = runCatching {
            VeritasFileBrowserScanner.scan(
                context = context,
                roots = roots,
                includeAllFilesAccess = allFilesGranted,
                location = uiState.value.fileBrowserLocation
            )
        }
        val scanResult = result.getOrElse { error ->
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(fileBrowserScanning = false, fileBrowserMessage = "Scan failed: ${error.message}") }
            }
            return@launch
        }
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(
                fileBrowserScanning = false,
                fileBrowserFiles = scanResult.files,
                fileBrowserLocation = scanResult.location,
                fileBrowserAllFilesGranted = hasAllFilesAccess(),
                fileBrowserMessage = scanResult.diagnostics.joinToString("\n").ifBlank {
                    if (scanResult.files.isEmpty()) "No files found" else null
                }
            )}
        }
    }
}

