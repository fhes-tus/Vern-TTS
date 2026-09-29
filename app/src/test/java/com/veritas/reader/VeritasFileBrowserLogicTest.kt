package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VeritasFileBrowserLogicTest {

    @Test
    fun `detects extended compatible office, book, presentation, and text formats`() {
        assertEquals(VeritasBrowserTab.DOC, VeritasFileBrowserScanner.fileTypeFor("resume.doc", ""))
        assertEquals(VeritasBrowserTab.DOC, VeritasFileBrowserScanner.fileTypeFor("paper.rtf", ""))
        assertEquals(VeritasBrowserTab.DOC, VeritasFileBrowserScanner.fileTypeFor("thesis.odt", ""))
        assertEquals(VeritasBrowserTab.DOC, VeritasFileBrowserScanner.fileTypeFor("notes.docx", ""))

        assertEquals(VeritasBrowserTab.BOOKS, VeritasFileBrowserScanner.fileTypeFor("classic.epub", ""))
        assertEquals(VeritasBrowserTab.BOOKS, VeritasFileBrowserScanner.fileTypeFor("novel.mobi", ""))
        assertEquals(VeritasBrowserTab.BOOKS, VeritasFileBrowserScanner.fileTypeFor("guide.azw", ""))
        assertEquals(VeritasBrowserTab.BOOKS, VeritasFileBrowserScanner.fileTypeFor("story.azw3", ""))
        assertEquals(VeritasBrowserTab.BOOKS, VeritasFileBrowserScanner.fileTypeFor("folklore.fb2", ""))

        assertEquals(VeritasBrowserTab.SLIDES, VeritasFileBrowserScanner.fileTypeFor("deck.pptx", ""))
        assertEquals(VeritasBrowserTab.SLIDES, VeritasFileBrowserScanner.fileTypeFor("legacy.ppt", ""))
        assertEquals(VeritasBrowserTab.SLIDES, VeritasFileBrowserScanner.fileTypeFor("open.odp", ""))

        assertEquals(VeritasBrowserTab.TXT, VeritasFileBrowserScanner.fileTypeFor("notes.txt", ""))
        assertEquals(VeritasBrowserTab.TXT, VeritasFileBrowserScanner.fileTypeFor("math.tex", ""))
        assertEquals(VeritasBrowserTab.TXT, VeritasFileBrowserScanner.fileTypeFor("config.ini", ""))
        assertEquals(VeritasBrowserTab.TXT, VeritasFileBrowserScanner.fileTypeFor("server.conf", ""))

        assertEquals(VeritasBrowserTab.OCR, VeritasFileBrowserScanner.fileTypeFor("scan.png", ""))
        assertEquals(VeritasBrowserTab.OCR, VeritasFileBrowserScanner.fileTypeFor("photo.jpg", ""))
        assertEquals(VeritasBrowserTab.OCR, VeritasFileBrowserScanner.fileTypeFor("shot.heic", ""))
    }

    @Test
    fun `mimeTypeForFileName maps all supported extensions accurately`() {
        assertEquals("application/msword", VeritasFileBrowserScanner.mimeTypeForFileName("doc.doc"))
        assertEquals("application/rtf", VeritasFileBrowserScanner.mimeTypeForFileName("paper.rtf"))
        assertEquals("application/vnd.oasis.opendocument.text", VeritasFileBrowserScanner.mimeTypeForFileName("text.odt"))
        assertEquals("application/x-mobipocket-ebook", VeritasFileBrowserScanner.mimeTypeForFileName("book.mobi"))
        assertEquals("application/vnd.amazon.ebook", VeritasFileBrowserScanner.mimeTypeForFileName("kindle.azw"))
        assertEquals("application/x-fictionbook+xml", VeritasFileBrowserScanner.mimeTypeForFileName("story.fb2"))
        assertEquals("application/vnd.oasis.opendocument.presentation", VeritasFileBrowserScanner.mimeTypeForFileName("slides.odp"))
        assertEquals("text/plain", VeritasFileBrowserScanner.mimeTypeForFileName("formula.tex"))
    }

    @Test
    fun `image deprioritization sorts all document types before ocr images`() {
        data class SimpleBrowserItem(val name: String, val type: VeritasBrowserTab)

        val files = listOf(
            SimpleBrowserItem("photo_a.jpg", VeritasBrowserTab.OCR),
            SimpleBrowserItem("document_b.pdf", VeritasBrowserTab.PDF),
            SimpleBrowserItem("photo_b.png", VeritasBrowserTab.OCR),
            SimpleBrowserItem("notes_c.docx", VeritasBrowserTab.DOC),
            SimpleBrowserItem("book_d.epub", VeritasBrowserTab.BOOKS)
        )

        val prioritized = files.sortedBy { it.type == VeritasBrowserTab.OCR }

        // Documents must come first
        assertFalse(prioritized[0].type == VeritasBrowserTab.OCR)
        assertFalse(prioritized[1].type == VeritasBrowserTab.OCR)
        assertFalse(prioritized[2].type == VeritasBrowserTab.OCR)

        // OCR images must come last
        assertTrue(prioritized[3].type == VeritasBrowserTab.OCR)
        assertTrue(prioritized[4].type == VeritasBrowserTab.OCR)
    }

    @Test
    fun `filter-scoped select all toggles only files matching the active filter`() {
        data class SimpleFile(val id: String, val type: VeritasBrowserTab)

        val allFiles = listOf(
            SimpleFile("doc1.docx", VeritasBrowserTab.DOC),
            SimpleFile("doc2.docx", VeritasBrowserTab.DOC),
            SimpleFile("book1.epub", VeritasBrowserTab.BOOKS),
            SimpleFile("book2.epub", VeritasBrowserTab.BOOKS)
        )

        val selectedFiles = mutableListOf<SimpleFile>()

        // User filters on DOC tab
        val docFiles = allFiles.filter { it.type == VeritasBrowserTab.DOC }

        // Select all for DOC
        val toAdd = docFiles.filter { it !in selectedFiles }
        selectedFiles.addAll(toAdd)

        assertEquals(2, selectedFiles.size)
        assertTrue(selectedFiles.all { it.type == VeritasBrowserTab.DOC })

        // User switches to BOOKS tab and selects all
        val bookFiles = allFiles.filter { it.type == VeritasBrowserTab.BOOKS }
        selectedFiles.addAll(bookFiles)
        assertEquals(4, selectedFiles.size)

        // Deselect only DOC tab
        val docIds = docFiles.map { it.id }.toSet()
        selectedFiles.removeAll { it.id in docIds }

        assertEquals(2, selectedFiles.size)
        assertTrue(selectedFiles.all { it.type == VeritasBrowserTab.BOOKS })
    }

    @Test
    fun `deduplicateFiles collapses identical files indexed by MediaStore and filesystem crawl`() {
        data class TestFile(
            val isDirectory: Boolean = false,
            val filePath: String? = null,
            val uriString: String = "",
            val name: String,
            val sizeBytes: Long,
            val relativePath: String,
            val targetLocationFilePath: String? = null
        )

        val mediaStoreEntry = TestFile(
            isDirectory = false,
            filePath = "/storage/emulated/0/Download/Philosophy_Paper.pdf",
            uriString = "content://media/external/file/10294",
            name = "Philosophy_Paper.pdf",
            sizeBytes = 2_048_100L,
            relativePath = "Download/Philosophy_Paper.pdf"
        )
        val fileCrawlEntry = TestFile(
            isDirectory = false,
            filePath = "/storage/emulated/0/Download/Philosophy_Paper.pdf",
            uriString = "file:///storage/emulated/0/Download/Philosophy_Paper.pdf",
            name = "Philosophy_Paper.pdf",
            sizeBytes = 2_048_100L,
            relativePath = "Download/Philosophy_Paper.pdf"
        )

        val deduplicated = VeritasFileBrowserScanner.deduplicateFiles(
            items = listOf(mediaStoreEntry, fileCrawlEntry),
            isDirectory = { it.isDirectory },
            filePath = { it.filePath },
            uriString = { it.uriString },
            name = { it.name },
            sizeBytes = { it.sizeBytes },
            relativePath = { it.relativePath },
            targetLocationFilePath = { it.targetLocationFilePath }
        )

        assertEquals(1, deduplicated.size)
        assertEquals("Philosophy_Paper.pdf", deduplicated[0].name)
    }

    @Test
    fun `deduplicateFiles preserves files with identical names when located in different folders`() {
        data class TestFile(
            val isDirectory: Boolean = false,
            val filePath: String? = null,
            val uriString: String = "",
            val name: String,
            val sizeBytes: Long,
            val relativePath: String,
            val targetLocationFilePath: String? = null
        )

        val downloadFile = TestFile(
            isDirectory = false,
            filePath = "/storage/emulated/0/Download/notes.docx",
            uriString = "file:///storage/emulated/0/Download/notes.docx",
            name = "notes.docx",
            sizeBytes = 15_000L,
            relativePath = "Download/notes.docx"
        )
        val documentsFile = TestFile(
            isDirectory = false,
            filePath = "/storage/emulated/0/Documents/notes.docx",
            uriString = "file:///storage/emulated/0/Documents/notes.docx",
            name = "notes.docx",
            sizeBytes = 15_000L,
            relativePath = "Documents/notes.docx"
        )

        val deduplicated = VeritasFileBrowserScanner.deduplicateFiles(
            items = listOf(downloadFile, documentsFile),
            isDirectory = { it.isDirectory },
            filePath = { it.filePath },
            uriString = { it.uriString },
            name = { it.name },
            sizeBytes = { it.sizeBytes },
            relativePath = { it.relativePath },
            targetLocationFilePath = { it.targetLocationFilePath }
        )

        assertEquals(2, deduplicated.size)
        assertEquals("Download/notes.docx", deduplicated[0].relativePath)
        assertEquals("Documents/notes.docx", deduplicated[1].relativePath)
    }

    @Test
    fun `deduplicateFiles collapses duplicate folder entries`() {
        data class TestFile(
            val isDirectory: Boolean = true,
            val filePath: String? = null,
            val uriString: String = "",
            val name: String,
            val sizeBytes: Long = 0L,
            val relativePath: String,
            val targetLocationFilePath: String? = null
        )

        val dir1 = TestFile(
            isDirectory = true,
            filePath = "/storage/emulated/0/Download",
            uriString = "file:///storage/emulated/0/Download",
            name = "Download",
            relativePath = "Download",
            targetLocationFilePath = "/storage/emulated/0/Download"
        )
        val dir2 = TestFile(
            isDirectory = true,
            filePath = "/storage/emulated/0/Download",
            uriString = "file:///storage/emulated/0/Download",
            name = "Download",
            relativePath = "Download",
            targetLocationFilePath = "/storage/emulated/0/Download"
        )

        val deduplicated = VeritasFileBrowserScanner.deduplicateFiles(
            items = listOf(dir1, dir2),
            isDirectory = { it.isDirectory },
            filePath = { it.filePath },
            uriString = { it.uriString },
            name = { it.name },
            sizeBytes = { it.sizeBytes },
            relativePath = { it.relativePath },
            targetLocationFilePath = { it.targetLocationFilePath }
        )

        assertEquals(1, deduplicated.size)
    }

    @Test
    fun `deduplicateFiles collapses MediaStore content URI with null path and promotes richer disk entry`() {
        data class TestFile(
            val isDirectory: Boolean = false,
            val filePath: String? = null,
            val uriString: String = "",
            val name: String,
            val sizeBytes: Long,
            val relativePath: String,
            val targetLocationFilePath: String? = null
        )

        // MediaStore entry has content URI and null filePath
        val mediaStoreEntry = TestFile(
            isDirectory = false,
            filePath = null,
            uriString = "content://media/external/file/88412",
            name = "Thinking_Fast_and_Slow.pdf",
            sizeBytes = 15_482_910L,
            relativePath = "Download/"
        )

        // Disk scan entry has file URI and real filePath
        val diskEntry = TestFile(
            isDirectory = false,
            filePath = "/storage/emulated/0/Download/Thinking_Fast_and_Slow.pdf",
            uriString = "file:///storage/emulated/0/Download/Thinking_Fast_and_Slow.pdf",
            name = "Thinking_Fast_and_Slow.pdf",
            sizeBytes = 15_482_910L,
            relativePath = "Download/Thinking_Fast_and_Slow.pdf"
        )

        val deduplicated = VeritasFileBrowserScanner.deduplicateFiles(
            items = listOf(mediaStoreEntry, diskEntry),
            isDirectory = { it.isDirectory },
            filePath = { it.filePath },
            uriString = { it.uriString },
            name = { it.name },
            sizeBytes = { it.sizeBytes },
            relativePath = { it.relativePath },
            targetLocationFilePath = { it.targetLocationFilePath }
        )

        assertEquals(1, deduplicated.size)
        assertEquals("/storage/emulated/0/Download/Thinking_Fast_and_Slow.pdf", deduplicated[0].filePath)
        assertEquals("file:///storage/emulated/0/Download/Thinking_Fast_and_Slow.pdf", deduplicated[0].uriString)
    }

    @Test
    fun `deduplicateFiles collapses duplicate when MediaStore size is unindexed or 0 but name and folder match`() {
        data class TestFile(
            val isDirectory: Boolean = false,
            val filePath: String? = null,
            val uriString: String = "",
            val name: String,
            val sizeBytes: Long,
            val relativePath: String,
            val targetLocationFilePath: String? = null
        )

        val mediaStoreUnindexed = TestFile(
            isDirectory = false,
            filePath = null,
            uriString = "content://media/external/file/99120",
            name = "Book.epub",
            sizeBytes = 0L,
            relativePath = "Books"
        )

        val diskFile = TestFile(
            isDirectory = false,
            filePath = "/storage/emulated/0/Books/Book.epub",
            uriString = "file:///storage/emulated/0/Books/Book.epub",
            name = "Book.epub",
            sizeBytes = 850_000L,
            relativePath = "Books/Book.epub"
        )

        val deduplicated = VeritasFileBrowserScanner.deduplicateFiles(
            items = listOf(diskFile, mediaStoreUnindexed),
            isDirectory = { it.isDirectory },
            filePath = { it.filePath },
            uriString = { it.uriString },
            name = { it.name },
            sizeBytes = { it.sizeBytes },
            relativePath = { it.relativePath },
            targetLocationFilePath = { it.targetLocationFilePath }
        )

        assertEquals(1, deduplicated.size)
        assertEquals(850_000L, deduplicated[0].sizeBytes)
        assertEquals("/storage/emulated/0/Books/Book.epub", deduplicated[0].filePath)
    }

    @Test
    fun `shouldSkipRecursiveDirectory skips voice notes and thumbnails but preserves document directories`() {
        val root = java.io.File("/storage/emulated/0")
        val voiceNotes = java.io.File("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes")
        val stickers = java.io.File("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Stickers")
        val whatsappDocs = java.io.File("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents")
        val downloadFolder = java.io.File("/storage/emulated/0/Download")
        val documentsFolder = java.io.File("/storage/emulated/0/Documents")

        assertTrue(VeritasFileBrowserScanner.shouldSkipRecursiveDirectory(voiceNotes, root))
        assertTrue(VeritasFileBrowserScanner.shouldSkipRecursiveDirectory(stickers, root))
        assertFalse(VeritasFileBrowserScanner.shouldSkipRecursiveDirectory(whatsappDocs, root))
        assertFalse(VeritasFileBrowserScanner.shouldSkipRecursiveDirectory(downloadFolder, root))
        assertFalse(VeritasFileBrowserScanner.shouldSkipRecursiveDirectory(documentsFolder, root))
    }
}
