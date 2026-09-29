package com.veritas.reader.ui

import androidx.lifecycle.viewModelScope
import com.veritas.reader.PlaybackStateStore
import com.veritas.reader.SavedDocument
import com.veritas.reader.StorageBreakdown
import com.veritas.reader.addToQueue
import com.veritas.reader.clearQueue
import com.veritas.reader.clearReadingHistory
import com.veritas.reader.isQueued
import com.veritas.reader.loadQueueDocuments
import com.veritas.reader.moveQueueItem
import com.veritas.reader.removeFromQueue
import com.veritas.reader.removeReadingHistoryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun ReaderViewModel.deleteDocument(document: SavedDocument) {
    viewModelScope.launch(Dispatchers.IO) {
        val docs = repository.deleteDocument(document.id)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(documents = docs, deleteTarget = null) }
            stopPlaybackIfDocumentsRemoved(setOf(document.id))
        }
    }
}

fun ReaderViewModel.deleteDocuments(ids: Set<String>) {
    if (ids.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
        ids.forEach { repository.deleteDocument(it) }
        val docs = repository.loadDocuments()
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(documents = docs) }
            stopPlaybackIfDocumentsRemoved(ids)
        }
    }
}

fun ReaderViewModel.favoriteDocuments(ids: Set<String>) {
    if (ids.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
        ids.forEach { repository.toggleFavorite(it) }
        val docs = repository.loadDocuments()
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(documents = docs) }
        }
    }
}

fun ReaderViewModel.toggleFavorite(document: SavedDocument) {
    viewModelScope.launch(Dispatchers.IO) {
        val docs = repository.toggleFavorite(document.id)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(documents = docs) }
        }
    }
}

fun ReaderViewModel.queueDocuments(ids: Set<String>) {
    if (ids.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
        ids.forEach { repository.addToQueue(it) }
        val queue = repository.loadQueueDocuments()
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(queuedDocuments = queue) }
            PlaybackStateStore.queueCount = queue.size
        }
    }
}

fun ReaderViewModel.setCollectionForDocuments(ids: Set<String>, collection: String) {
    if (ids.isEmpty()) return
    val cleanCollection = collection.trim()
    viewModelScope.launch(Dispatchers.IO) {
        ids.forEach { repository.setCollection(it, cleanCollection) }
        val docs = repository.loadDocuments()
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(documents = docs) }
        }
    }
}

fun ReaderViewModel.renameDocument(document: SavedDocument, newTitle: String) {
    val cleanTitle = newTitle.trim()
    if (cleanTitle.isBlank()) return
    viewModelScope.launch(Dispatchers.IO) {
        val docs = repository.renameDocument(document.id, cleanTitle)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(documents = docs, renameTarget = null, renameDraft = "") }
        }
    }
}

fun ReaderViewModel.setDocumentCollection(document: SavedDocument, collection: String) {
    val cleanCollection = collection.trim()
    viewModelScope.launch(Dispatchers.IO) {
        val docs = repository.setCollection(document.id, cleanCollection)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(documents = docs, collectionTarget = null, collectionDraft = "") }
        }
    }
}

fun ReaderViewModel.moveQueueItem(document: SavedDocument, offset: Int) {
    viewModelScope.launch(Dispatchers.IO) {
        val queue = repository.moveQueueItem(document.id, offset)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(queuedDocuments = queue) }
        }
    }
}

fun ReaderViewModel.clearQueue() {
    viewModelScope.launch(Dispatchers.IO) {
        val queue = repository.clearQueue()
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(queuedDocuments = queue) }
            PlaybackStateStore.queueCount = 0
        }
    }
}

fun ReaderViewModel.clearReadingHistory() {
    viewModelScope.launch(Dispatchers.IO) {
        val history = repository.clearReadingHistory()
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(readingHistory = history) }
        }
    }
}

fun ReaderViewModel.removeReadingHistoryEntry(documentId: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val history = repository.removeReadingHistoryEntry(documentId)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(readingHistory = history) }
        }
    }
}

suspend fun ReaderViewModel.computeStorage(): Pair<StorageBreakdown, List<Pair<SavedDocument, Long>>> =
    withContext(Dispatchers.IO) {
        repository.computeStorageBreakdown() to repository.findCleanupCandidates()
    }

suspend fun ReaderViewModel.cleanupOriginals(ids: Set<String>): Long =
    withContext(Dispatchers.IO) { repository.removeOriginals(ids) }.also { refreshAll() }

suspend fun ReaderViewModel.clearAppCache(): Long =
    withContext(Dispatchers.IO) { repository.clearAppCache() }.also { refreshAll() }


fun ReaderViewModel.searchLibraryContent(query: String) {
    _uiState.update { it.copy(librarySearchInProgress = true, librarySearchHits = emptyList()) }
    viewModelScope.launch(Dispatchers.IO) {
        val hits = runCatching { repository.searchLibraryContent(query) }.getOrDefault(emptyList())
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(librarySearchInProgress = false, librarySearchHits = hits) }
        }
    }
}

fun ReaderViewModel.toggleQueue(document: SavedDocument) {
    viewModelScope.launch(Dispatchers.IO) {
        val queuedDocs = if (repository.isQueued(document.id)) {
            repository.removeFromQueue(document.id)
        } else {
            repository.addToQueue(document.id)
        }
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(queuedDocuments = queuedDocs) }
            PlaybackStateStore.queueCount = queuedDocs.size
        }
    }
}

fun ReaderViewModel.reorderDocuments(reorderedSubset: List<SavedDocument>) {
    val currentDocs = _uiState.value.documents
    val finalDocs = if (reorderedSubset.size == currentDocs.size) {
        reorderedSubset
    } else {
        val reorderedIds = reorderedSubset.map { it.id }.toSet()
        val queue = ArrayDeque(reorderedSubset)
        currentDocs.map { doc ->
            if (doc.id in reorderedIds && queue.isNotEmpty()) {
                queue.removeFirst()
            } else {
                doc
            }
        }
    }
    _uiState.update { it.copy(documents = finalDocs) }
    viewModelScope.launch(Dispatchers.IO) {
        repository.saveDocuments(finalDocs)
    }
}


