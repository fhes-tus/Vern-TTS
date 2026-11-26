package com.veritas.reader

import org.json.JSONArray

fun DocumentRepository.loadQueueEntries(): List<QueueEntry> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
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
    entries
}

fun DocumentRepository.loadQueueDocuments(): List<SavedDocument> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val docsById = loadDocuments().associateBy { it.id }
    val queueDocs = loadQueueEntries().mapNotNull { docsById[it.documentId] }
    PlaybackStateStore.queueCount = queueDocs.size
    queueDocs
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

fun DocumentRepository.addToQueue(documentId: String): List<SavedDocument> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    if (findDocument(documentId) == null) return@synchronized loadQueueDocuments()
    val current = loadQueueEntries()
    if (current.any { it.documentId == documentId }) return@synchronized loadQueueDocuments()
    saveQueueEntries(current + QueueEntry(documentId, System.currentTimeMillis()))
    loadQueueDocuments()
}

fun DocumentRepository.removeFromQueue(documentId: String): List<SavedDocument> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    saveQueueEntries(loadQueueEntries().filterNot { it.documentId == documentId })
    loadQueueDocuments()
}

fun DocumentRepository.moveQueueItem(documentId: String, offset: Int): List<SavedDocument> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val queue = loadQueueEntries().toMutableList()
    val oldIndex = queue.indexOfFirst { it.documentId == documentId }
    if (oldIndex == -1) return@synchronized loadQueueDocuments()
    val newIndex = (oldIndex + offset).coerceIn(0, queue.lastIndex)
    if (oldIndex == newIndex) return@synchronized loadQueueDocuments()
    val item = queue.removeAt(oldIndex)
    queue.add(newIndex, item)
    saveQueueEntries(queue)
    loadQueueDocuments()
}

fun DocumentRepository.clearQueue(): List<SavedDocument> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    saveQueueEntries(emptyList())
    emptyList()
}

internal fun DocumentRepository.advanceQueue(currentDocumentId: String?): SavedDocument? = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val queue = loadQueueEntries()
    val remaining = queue.filterNot { it.documentId == currentDocumentId }
    saveQueueEntries(remaining)
    PlaybackStateStore.queueCount = remaining.size
    remaining.firstOrNull()?.let { findDocument(it.documentId) }
}

fun DocumentRepository.loadReadingListCatalog(): VeritasReadingListCatalog = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val raw = prefs.getString(DocumentRepository.KEY_READING_LISTS, "[]") ?: "[]"
    val source = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    val catalog = VeritasReadingListCatalog.fromJsonArray(source)
    val legacy = loadDocuments().filter { it.collection.isNotBlank() }
    var updated = catalog
    legacy.groupBy { it.collection.trim() }.forEach { (title, files) ->
        updated = updated.addFilesToCollection(title, files.map { it.id })
    }
    val normalized = normalizeReadingListCatalog(updated)
    if (legacy.isEmpty()) {
        if (normalized != catalog) saveReadingListCatalog(normalized)
    } else {
        // Publish the migrated memberships before clearing the old document labels. A retry
        // merges the same memberships if the database update was interrupted.
        val durable = prefs.edit().putString(DocumentRepository.KEY_READING_LISTS, normalized.toJsonArray().toString()).commit()
        if (durable) {
            legacy.forEach { doc -> dbHelper.setDocumentCollection(doc.id, "", doc.updatedAt) }
        }
    }
    normalized
}

fun DocumentRepository.createReadingList(
    title: String,
    description: String = "",
    documentId: String? = null
): VeritasReadingListCatalog = updateReadingListCatalog { catalog ->
    val id = java.util.UUID.randomUUID().toString()
    val created = catalog.createList(title = title, description = description, id = id)
    if (documentId == null) created else created.addDocument(id, documentId)
}

fun DocumentRepository.renameReadingList(listId: String, title: String, description: String? = null): VeritasReadingListCatalog =
    updateReadingListCatalog { it.renameList(listId, title, description) }

fun DocumentRepository.archiveReadingList(listId: String): VeritasReadingListCatalog =
    updateReadingListCatalog { it.archiveList(listId) }

fun DocumentRepository.deleteReadingList(listId: String): VeritasReadingListCatalog =
    updateReadingListCatalog { it.deleteList(listId) }

fun DocumentRepository.addDocumentToReadingList(listId: String, documentId: String): VeritasReadingListCatalog =
    updateReadingListCatalog { it.addDocument(listId, documentId) }

fun DocumentRepository.removeDocumentFromReadingList(listId: String, documentId: String): VeritasReadingListCatalog =
    updateReadingListCatalog { it.removeDocument(listId, documentId) }

fun DocumentRepository.moveReadingListDocument(listId: String, documentId: String, offset: Int): VeritasReadingListCatalog =
    updateReadingListCatalog { it.moveDocument(listId, documentId, offset) }

fun DocumentRepository.setReadingListSortMode(listId: String, sortMode: VeritasReadingListSortMode): VeritasReadingListCatalog =
    updateReadingListCatalog { it.setSortMode(listId, sortMode) }

fun DocumentRepository.transferCollectionDocument(from: String, to: String, documentId: String): VeritasReadingListCatalog =
    updateReadingListCatalog { catalog ->
        if (from == to || catalog.list(from)?.contains(documentId) != true || catalog.list(to)?.archived != false) {
            catalog
        } else {
            catalog.addDocument(to, documentId).removeDocument(from, documentId)
        }
    }

fun DocumentRepository.queueFromCollection(id: String): List<SavedDocument> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val documents = loadReadingListCatalog().resolveDocuments(id, loadDocuments())
    if (documents.isEmpty()) return@synchronized loadQueueDocuments()
    val now = System.currentTimeMillis()
    saveQueueEntries(documents.map { QueueEntry(it.id, now) })
    loadQueueDocuments()
}

fun DocumentRepository.addFilesToCollection(title: String, ids: Set<String>): VeritasReadingListCatalog =
    updateReadingListCatalog { it.addFilesToCollection(title, ids) }

private fun VeritasReadingListCatalog.addFilesToCollection(
    title: String,
    ids: Collection<String>
): VeritasReadingListCatalog {
    val cleanTitle = title.trim()
    if (cleanTitle.isBlank() || ids.isEmpty()) return this
    val existing = activeLists.firstOrNull { it.title.equals(cleanTitle, ignoreCase = true) }
    val id = existing?.id ?: java.util.UUID.randomUUID().toString()
    val target = if (existing == null) createList(title = cleanTitle, id = id) else this
    return ids.fold(target) { catalog, documentId -> catalog.addDocument(id, documentId) }
}

private fun DocumentRepository.updateReadingListCatalog(
    transform: (VeritasReadingListCatalog) -> VeritasReadingListCatalog
): VeritasReadingListCatalog = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val existing = loadReadingListCatalog()
    val updated = transform(existing)
    if (updated == existing) existing else saveReadingListCatalog(updated)
}
