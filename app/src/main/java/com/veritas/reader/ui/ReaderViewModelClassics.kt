package com.veritas.reader.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager

import com.veritas.reader.*
import com.veritas.reader.ui.screens.ClassicBookEntry
import com.veritas.reader.ui.screens.LibrarySection
import com.veritas.reader.ui.screens.VeritasHomeTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

fun ReaderViewModel.downloadClassicBook(book: ClassicBookEntry) {
    if (!book.hasDirectTextDownload) {
        _uiState.update { it.copy(showBookBrowser = true, bookBrowserUrl = book.downloadUrl,
            bookBrowserTitle = book.title, bookBrowserQuery = "") }
        return
    }
    ClassicBookDownloadWorker.enqueue(getApplication(), book.id)
}

fun ReaderViewModel.cancelClassicDownload(book: ClassicBookEntry) {
    val manager = WorkManager.getInstance(getApplication<Application>())
    (listOf(book.id) + book.editions.map { "${book.id}:${it.id}" }).forEach {
        manager.cancelUniqueWork(ClassicBookDownloadWorker.uniqueName(it))
    }
}

internal fun ReaderViewModel.observeClassicDownloads() {
    viewModelScope.launch(Dispatchers.IO) {
        var finishedIds: Set<java.util.UUID>? = null
        WorkManager.getInstance(getApplication<Application>()).getWorkInfosByTagFlow(ClassicBookDownloadWorker.TAG).collect { work ->
            val nowFinished = work.filter { it.state.isFinished }.map { it.id }.toSet()
            val refresh = nowFinished != finishedIds
            finishedIds = nowFinished
            val documents = if (refresh) repository.migrateClassicProvenance() else null
            _uiState.update { state -> state.copy(
                classicDownloads = classicDownloadStates(work),
                documents = documents ?: state.documents
            ) }
        }
    }
}

fun ReaderViewModel.openLibrarySection(section: LibrarySection) {
    if (uiState.value.activeDocument != null) returnToLibrary()
    _uiState.update { it.copy(targetHomeTab = VeritasHomeTab.LIBRARY, targetLibrarySection = section, showClassicsCatalog = false) }
}
