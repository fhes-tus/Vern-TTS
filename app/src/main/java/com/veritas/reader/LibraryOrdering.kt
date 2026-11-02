package com.veritas.reader

import java.util.Locale

internal fun isLibraryDocumentCompleted(document: SavedDocument): Boolean =
    document.chunkCount > 0 && document.currentIndex >= document.chunkCount - 1

internal fun canReorderLibraryDocuments(document: SavedDocument, target: SavedDocument): Boolean =
    isLibraryDocumentCompleted(document) == isLibraryDocumentCompleted(target)

/** Completion is the first library sort key; each group keeps the chosen order. */
internal fun sortLibraryDocuments(
    documents: List<SavedDocument>,
    sortMode: String,
    queueOrder: List<String>? = null
): List<SavedDocument> {
    val ordered = if (queueOrder != null) {
        val positions = queueOrder.withIndex().associate { (index, id) -> id to index }
        documents.sortedBy { positions[it.id] ?: Int.MAX_VALUE }
    } else when (sortMode) {
        "Title" -> documents.sortedBy { it.title.lowercase(Locale.getDefault()) }
        "Progress" -> documents.sortedByDescending { progressFraction(it) }
        "Type" -> documents.sortedWith(compareBy<SavedDocument> { it.sourceLabel }.thenBy { it.title.lowercase(Locale.getDefault()) })
        "Newest" -> documents.sortedByDescending { it.createdAt }
        else -> documents
    }
    return ordered.sortedBy(::isLibraryDocumentCompleted)
}
