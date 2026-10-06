package com.veritas.reader

import com.veritas.reader.ui.screens.ClassicBookEntry
import com.veritas.reader.ui.screens.BookEditionOption
import com.veritas.reader.ui.screens.CURATED_CLASSICS

fun classicEdition(book: ClassicBookEntry, edition: BookEditionOption): ClassicBookEntry {
    require(book.editions.any { it == edition })
    return book.copy(id = "${book.id}:${edition.id}", title = "${book.title} (${edition.name})",
        downloadUrl = edition.downloadUrl, editions = emptyList(),
        legacyCatalogId = if (edition.id == "original") book.id else "")
}

fun resolveClassicDownload(id: String?): ClassicBookEntry? {
    val book = CURATED_CLASSICS.firstOrNull { it.id == id?.substringBefore(':') } ?: return null
    if (book.editions.isEmpty()) return book.takeIf { it.id == id }
    val editionId = id?.substringAfter(':', "original")
    val edition = book.editions.firstOrNull { it.id == editionId } ?: return null
    return classicEdition(book, edition)
}

/** Surface an edition job on its parent shelf while keeping independent worker identities. */
fun catalogDownloadState(book: ClassicBookEntry, downloads: Map<String, ClassicDownloadState>): ClassicDownloadState {
    val states = (listOf(book.id) + book.editions.map { "${book.id}:${it.id}" }).mapNotNull { downloads[it] }
    return states.firstOrNull { it.busy } ?: states.firstOrNull { it.phase != ClassicDownloadPhase.AVAILABLE } ?: ClassicDownloadState()
}
