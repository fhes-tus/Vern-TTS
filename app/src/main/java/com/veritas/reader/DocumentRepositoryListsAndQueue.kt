package com.veritas.reader

import org.json.JSONArray

fun DocumentRepository.loadQueueEntries(): List<QueueEntry> {
    val existingIds = loadDocuments().map { it.id }.toSet()
    val raw = prefs.getString(DocumentRepository.KEY_QUEUE, "[]") ?: "[]"
    val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    val entries = mutableListOf<QueueEntry>()
    val seen = mutableSetOf<String>()
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        val entry = runCatching { QueueEntry.fromJson(item) }.getOrNull() ?: continue
        if (entry.documentId.isNotBlank() && entry.documentId in existingIds && seen.add(entry.documentId)) {
            entries.add(entry)
        }
    }
    if (entries.size != array.length()) saveQueueEntries(entries)
    PlaybackStateStore.queueCount = entries.size
    return entries
}

fun DocumentRepository.loadQueueDocuments(): List<SavedDocument> {
    val docsById = loadDocuments().associateBy { it.id }
    val queueDocs = loadQueueEntries().mapNotNull { docsById[it.documentId] }
    PlaybackStateStore.queueCount = queueDocs.size
    return queueDocs
}

fun DocumentRepository.loadReadingHistory(): List<ReadingHistoryEntry> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val existingDocs = loadDocuments().associateBy { it.id }
    val raw = prefs.getString(DocumentRepository.KEY_READING_HISTORY, "[]") ?: "[]"
    val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    val entries = mutableListOf<ReadingHistoryEntry>()
    val seen = mutableSetOf<String>()
    for (i in 0 until array.length()) {
        val entry = ReadingHistoryEntry.fromJson(array.optJSONObject(i) ?: continue) ?: continue
        val document = existingDocs[entry.documentId]
        if (seen.add(entry.documentId)) {
            if (document != null) {
                entries.add(
                    entry.copy(
                        title = document.title,
                        sourceLabel = document.sourceLabel,
                        currentIndex = entry.currentIndex.coerceIn(0, (document.chunkCount - 1).coerceAtLeast(0)),
                        chunkCount = document.chunkCount
                    )
                )
            } else {
                entries.add(entry)
            }
        }
    }
    val normalized = entries.sortedByDescending { it.openedAt }.take(DocumentRepository.MAX_READING_HISTORY)
    if (normalized.size != array.length()) saveReadingHistory(normalized)
    normalized
}

fun DocumentRepository.addReadingHistory(document: SavedDocument, currentIndex: Int = document.currentIndex): List<ReadingHistoryEntry> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val safeIndex = currentIndex.coerceIn(0, (document.chunkCount - 1).coerceAtLeast(0))
    val entry = ReadingHistoryEntry(
        documentId = document.id,
        title = document.title,
        sourceLabel = document.sourceLabel,
        currentIndex = safeIndex,
        chunkCount = document.chunkCount,
        openedAt = System.currentTimeMillis()
    )
    saveReadingHistory(
        (listOf(entry) + loadReadingHistory().filterNot { it.documentId == document.id })
            .take(DocumentRepository.MAX_READING_HISTORY)
    )
    loadReadingHistory()
}

fun DocumentRepository.clearReadingHistory(): List<ReadingHistoryEntry> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    saveReadingHistory(emptyList())
    emptyList()
}

fun DocumentRepository.removeReadingHistoryEntry(documentId: String): List<ReadingHistoryEntry> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val current = loadReadingHistory().filterNot { it.documentId == documentId }
    saveReadingHistory(current)
    loadReadingHistory()
}

fun DocumentRepository.isQueued(documentId: String): Boolean {
    return loadQueueEntries().any { it.documentId == documentId }
}

fun DocumentRepository.addToQueue(documentId: String): List<SavedDocument> {
    if (findDocument(documentId) == null) return loadQueueDocuments()
    val current = loadQueueEntries()
    if (current.any { it.documentId == documentId }) return loadQueueDocuments()
    saveQueueEntries(current + QueueEntry(documentId, System.currentTimeMillis()))
    return loadQueueDocuments()
}

fun DocumentRepository.removeFromQueue(documentId: String): List<SavedDocument> {
    saveQueueEntries(loadQueueEntries().filterNot { it.documentId == documentId })
    return loadQueueDocuments()
}

fun DocumentRepository.moveQueueItem(documentId: String, offset: Int): List<SavedDocument> {
    val queue = loadQueueEntries().toMutableList()
    val oldIndex = queue.indexOfFirst { it.documentId == documentId }
    if (oldIndex == -1) return loadQueueDocuments()
    val newIndex = (oldIndex + offset).coerceIn(0, queue.lastIndex)
    if (oldIndex == newIndex) return loadQueueDocuments()
    val item = queue.removeAt(oldIndex)
    queue.add(newIndex, item)
    saveQueueEntries(queue)
    return loadQueueDocuments()
}

fun DocumentRepository.clearQueue(): List<SavedDocument> {
    saveQueueEntries(emptyList())
    return emptyList()
}

fun DocumentRepository.completeCurrentAndGetNextQueued(completedDocumentId: String?): SavedDocument? {
    val queue = loadQueueEntries()
    val docsById = loadDocuments().associateBy { it.id }
    completedDocumentId?.let { id ->
        docsById[id]?.let { completed -> recordDocumentCompletion(completed.id, completed.title, System.currentTimeMillis()) }
    }
    val remaining = if (completedDocumentId == null) {
        queue
    } else {
        queue.filterNot { it.documentId == completedDocumentId }
    }
    saveQueueEntries(remaining)
    val next = remaining.firstOrNull()?.let { docsById[it.documentId] }
    PlaybackStateStore.queueCount = remaining.size
    return next
}

fun DocumentRepository.loadReadingListCatalog(): VeritasReadingListCatalog {
    val raw = prefs.getString(DocumentRepository.KEY_READING_LISTS, "[]") ?: "[]"
    val source = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    val catalog = VeritasReadingListCatalog.fromJsonArray(source)
    val normalized = normalizeReadingListCatalog(catalog)
    if (normalized != catalog) saveReadingListCatalog(normalized)
    return normalized
}

fun DocumentRepository.createReadingList(title: String, description: String = ""): VeritasReadingListCatalog =
    saveReadingListCatalog(loadReadingListCatalog().createList(title = title, description = description))

fun DocumentRepository.renameReadingList(listId: String, title: String, description: String? = null): VeritasReadingListCatalog =
    saveReadingListCatalog(loadReadingListCatalog().renameList(listId, title, description))

fun DocumentRepository.archiveReadingList(listId: String): VeritasReadingListCatalog =
    saveReadingListCatalog(loadReadingListCatalog().archiveList(listId))

fun DocumentRepository.deleteReadingList(listId: String): VeritasReadingListCatalog =
    saveReadingListCatalog(loadReadingListCatalog().deleteList(listId))

fun DocumentRepository.addDocumentToReadingList(listId: String, documentId: String): VeritasReadingListCatalog {
    if (findDocument(documentId) == null) return loadReadingListCatalog()
    return saveReadingListCatalog(loadReadingListCatalog().addDocument(listId, documentId))
}

fun DocumentRepository.removeDocumentFromReadingList(listId: String, documentId: String): VeritasReadingListCatalog =
    saveReadingListCatalog(loadReadingListCatalog().removeDocument(listId, documentId))

fun DocumentRepository.moveReadingListDocument(listId: String, documentId: String, offset: Int): VeritasReadingListCatalog =
    saveReadingListCatalog(loadReadingListCatalog().moveDocument(listId, documentId, offset))

fun DocumentRepository.setReadingListSortMode(listId: String, sortMode: VeritasReadingListSortMode): VeritasReadingListCatalog =
    saveReadingListCatalog(loadReadingListCatalog().setSortMode(listId, sortMode))
