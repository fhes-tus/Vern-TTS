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

    @Test fun everyDirectCatalogEditionHasAnIndependentMirrorOfItsOwnEbook() {
        val editions = CURATED_CLASSICS.flatMap { book ->
            if (book.editions.isEmpty()) listOf(book) else book.editions.map { classicEdition(book, it) }
        }
        editions.filter { it.hasDirectTextDownload }.forEach { book ->
            val id = book.downloadUrl.substringBeforeLast('/').substringAfterLast('/')
            val mirror = classicTextSources(book.downloadUrl)[1]
            assertTrue(mirror.url.startsWith("https://mirror.csclub.uwaterloo.ca/gutenberg/"))
            assertTrue(mirror.url.substringBeforeLast('/').endsWith("/$id"))
            assertTrue(mirror.url.substringAfterLast('/').startsWith(id))
        }
        val modern = classicTextSources("https://www.gutenberg.org/cache/epub/39452/pg39452.txt").last()
        assertTrue(modern.url.endsWith("/3/9/4/5/39452/39452-8.txt"))
        assertEquals(Charsets.ISO_8859_1, modern.charset)
        val archive = "https://archive.org/details/goodmorningholys00hinn"
        assertEquals(listOf(ClassicTextSource(archive)), classicTextSources(archive))
    }

    @Test fun outageAndHtmlResponseBothTryTheMirrorBeforeFailing() = kotlinx.coroutines.runBlocking {
        val url = book.downloadUrl
        for (networkError in listOf(false, true)) {
            val attempts = mutableListOf<String>()
            val result = downloadClassicText(url) { source ->
                attempts += source.url
                when {
                    source.url != url -> "Readable mirror text."
                    networkError -> throw java.io.IOException("HTTP 503")
                    else -> "<html>Service unavailable</html>"
                }
            }
            assertEquals(classicTextSources(url).map { it.url }, attempts)
            assertEquals("Readable mirror text.", result)
        }
    }

    @Test fun cancellationNeverStartsAnotherDownload() = kotlinx.coroutines.runBlocking {
        val attempts = mutableListOf<String>()
        val cancelled = runCatching {
            downloadClassicText(book.downloadUrl) { source ->
                attempts += source.url
                throw kotlinx.coroutines.CancellationException("Download cancelled")
            }
        }.exceptionOrNull()
        assertTrue(cancelled is kotlinx.coroutines.CancellationException)
        assertEquals(listOf(book.downloadUrl), attempts)
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
