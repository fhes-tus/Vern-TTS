package com.veritas.reader

import com.veritas.reader.ui.screens.ClassicBookEntry
import com.veritas.reader.ui.screens.CURATED_CLASSICS

/** Provenance wins over mutable titles. Legacy adoption requires exactly one full match. */
fun findCatalogDocument(book: ClassicBookEntry, documents: List<SavedDocument>): SavedDocument? {
    documents.firstOrNull { it.catalogId == book.id }?.let { return it }
    if (book.legacyCatalogId.isNotBlank()) documents.firstOrNull { it.catalogId == book.legacyCatalogId }?.let { return it }
    if (book.editions.isNotEmpty()) documents.firstOrNull { doc ->
        book.editions.any { doc.catalogId == "${book.id}:${it.id}" }
    }?.let { return it }
    if (book.editions.isNotEmpty()) book.editions.firstNotNullOfOrNull {
        findCatalogDocument(classicEdition(book, it), documents)
    }?.let { return it }
    val candidates = documents.filter {
        it.catalogId.isBlank() && it.sourceLabel == "Classic Book" &&
            (it.title == "${book.title} - ${book.author}" || (book.legacyCatalogId.isNotBlank() &&
                it.title == "${book.title.substringBefore(" (")} - ${book.author}"))
    }
    return candidates.singleOrNull()
}

fun DocumentRepository.migrateClassicProvenance(): List<SavedDocument> =
    synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
        val documents = loadDocuments()
        val adopted = mutableMapOf<String, String>()
        val entries = CURATED_CLASSICS.flatMap { book ->
            if (book.editions.isEmpty()) listOf(book) else book.editions.map { classicEdition(book, it) }
        }
        entries.forEach { book ->
            val match = findCatalogDocument(book, documents)
            if (match != null && (match.catalogId.isBlank() || match.catalogId == book.legacyCatalogId) &&
                entries.count { findCatalogDocument(it, listOf(match))?.id == match.id } == 1) {
                dbHelper.setDocumentCatalogId(match.id, book.id)
                adopted[match.id] = book.id
            }
        }
        documents.map { doc -> adopted[doc.id]?.let { doc.copy(catalogId = it) } ?: doc }
    }

/** One atomic, idempotent publication shared by retries and independent repository instances. */
fun DocumentRepository.installClassic(book: ClassicBookEntry, text: String, checkActive: () -> Unit = {}): SavedDocument =
    synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
        val existing = findCatalogDocument(book, loadDocuments())
        if (existing != null) {
            if (existing.catalogId.isBlank() || existing.catalogId == book.legacyCatalogId) dbHelper.setDocumentCatalogId(existing.id, book.id)
            return@synchronized existing.copy(catalogId = book.id)
        }
        checkActive()
        require(text.isNotBlank()) { "The book contained no reading text." }
        val documentId = java.util.UUID.randomUUID().toString()
        try {
            createDocumentWithResult(
                title = "${book.title} - ${book.author}", text = text,
                sourceLabel = "Classic Book", documentId = documentId, catalogId = book.id
            ).document
        } catch (error: Exception) {
            if (findDocument(documentId) == null) java.io.File(docsDir, "$documentId.txt").delete()
            throw error
        }
    }
