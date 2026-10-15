package com.veritas.reader

import com.veritas.reader.ui.screens.CURATED_CLASSICS
import org.junit.Assert.*
import org.junit.Test

class ClassicBookLibraryTest {
    private val book = CURATED_CLASSICS.first()
    private fun document(id: String = "fixture", title: String = "${book.title} - ${book.author}", source: String = "Classic Book", catalogId: String = "") =
        SavedDocument(id, title, "$id.txt", source, 1, 1, 4, 20, 100, "preview", catalogId = catalogId)

    @Test fun editionsResolveToSeparateAllowlistedTextsAndKeepLegacyOriginal() {
        val parent = CURATED_CLASSICS.single { it.id == "classic_pilgrims_progress" }
        val modern = resolveClassicDownload("${parent.id}:modern")!!
        val original = resolveClassicDownload("${parent.id}:original")!!
        assertNotEquals(modern.id, original.id)
        assertTrue(modern.downloadUrl.endsWith("pg39452.txt"))
        assertTrue(original.downloadUrl.endsWith("pg131.txt"))
        assertEquals(original, resolveClassicDownload(parent.id))
        assertNull(resolveClassicDownload("${parent.id}:unlisted"))
        val legacy = document(catalogId = parent.id)
        assertEquals(legacy, findCatalogDocument(original, listOf(legacy)))
        assertNull(findCatalogDocument(modern, listOf(legacy)))
        val renamed = document(title = "My modern edition", catalogId = modern.id)
        assertEquals(renamed, findCatalogDocument(parent, listOf(renamed)))
        val oldModern = document(title = "${modern.title} - ${modern.author}")
        assertEquals(oldModern, findCatalogDocument(parent, listOf(oldModern)))
        assertNull(findCatalogDocument(original, listOf(oldModern)))
        assertFalse(CURATED_CLASSICS.single { it.id == "classic_good_morning_holy_spirit" }.hasDirectTextDownload)
    }

    @Test fun catalogEntriesHaveUniqueNonBlankIdentities() {
        assertTrue(CURATED_CLASSICS.all { it.id.isNotBlank() })
        assertEquals(CURATED_CLASSICS.size, CURATED_CLASSICS.map { it.id }.distinct().size)
    }

    @Test fun provenanceSurvivesRenameAndOverridesSimilarTitles() {
        val renamed = document(title = "My renamed favourite", catalogId = book.id)
        assertEquals(renamed, findCatalogDocument(book, listOf(document("other"), renamed)))
    }
    @Test fun exactLegacyTitleAndSourceCanBeAdopted() {
        val legacy = document()
        assertEquals(legacy, findCatalogDocument(book, listOf(legacy)))
    }
    @Test fun partialTitleOrFilenameNeverIdentifiesACatalogBook() {
        assertNull(findCatalogDocument(book, listOf(document(title = "Notes about ${book.title}").copy(originalFileName = "${book.id}.pdf"))))
        assertNull(findCatalogDocument(book, listOf(document(title = book.title))))
        assertNull(findCatalogDocument(book, listOf(document(source = "PDF"))))
    }
    @Test fun ambiguousLegacyDuplicatesAreNotAdopted() {
        assertNull(findCatalogDocument(book, listOf(document("one"), document("two"))))
    }
    @Test fun differentProvenanceCannotBeAdoptedByTitle() {
        assertNull(findCatalogDocument(book, listOf(document(catalogId = "another-book"))))
    }
    @Test fun deletionMakesTheBookAvailableToDownloadAgain() {
        assertNull(findCatalogDocument(book, emptyList()))
    }
    @Test fun gutenbergHeadersAreRemovedAndProseIsUnwrapped() {
        val cleaned = cleanAndUnwrapClassicBookText("License\r\n*** START OF THE PROJECT GUTENBERG EBOOK ALICE ***\r\n\r\nCHAPTER I\r\n\r\nThis is a long line about a curious reading adventure with a hyphen-\r\nated word that continues in the following prose line.\r\n\r\n*** END OF THE PROJECT GUTENBERG EBOOK ALICE ***\r\nLicense")
        assertTrue(cleaned.startsWith("CHAPTER I\n\n"))
        assertTrue(cleaned.contains("hyphenated word"))
        assertFalse(cleaned.contains("License"))
        assertFalse(cleaned.contains("GUTENBERG"))
    }
    @Test fun poetryAndListsKeepTheirLineBreaks() {
        val raw = "The moon is bright\nThe night is long\nI sing my song\n\n1. First item\n2. Second item"
        assertEquals(raw, cleanAndUnwrapClassicBookText(raw))
    }
    @Test fun plainTextWithoutMarkersRemainsReadable() {
        assertEquals("A sentence.", cleanAndUnwrapClassicBookText("\r\nA sentence.\r\n"))
    }
}
