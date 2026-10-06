package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class OriginalArchiveEntriesTest {
    @Test fun epubAndWordFileParsingLoadImagesOnlyWhenRequested() {
        val directory = Files.createTempDirectory("vern_original_archive").toFile()
        try {
            val epub = java.io.File(directory, "book.epub")
            ZipOutputStream(epub.outputStream()).use { zip ->
                fun put(name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
                put("chapter.xhtml", "<html><title>Test chapter</title><body><p>A complete paragraph for original view.</p><img src='picture.png'/><p>Following paragraph.</p></body></html>".toByteArray())
                put("picture.png", ByteArray(17 * 1024 * 1024))
            }
            val book = EpubDocumentParser.parse(epub, "Book")
            assertEquals(1, book.chapters.single().images.size)
            assertTrue(book.chapters.single().paragraphs.any { it.contains("[[VERITAS_IMAGE:0]]") })
            assertThrows(IllegalArgumentException::class.java) { book.chapters.single().images[0] }
            val docx = java.io.File(directory, "document.docx")
            ZipOutputStream(docx.outputStream()).use { zip ->
                fun put(name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
                put("word/document.xml", "<w:document xmlns:w='word' xmlns:r='rels' xmlns:a='draw'><w:body><w:p><w:r><w:t>One &amp; two</w:t></w:r></w:p><w:p><w:r><a:blip r:embed='image1'/></w:r></w:p><w:p><w:r><w:t>Following text.</w:t></w:r></w:p></w:body></w:document>".toByteArray())
                put("word/_rels/document.xml.rels", "<Relationships><Relationship Id=\"image1\" Target=\"media/picture.png\"/></Relationships>".toByteArray())
                put("word/media/picture.png", ByteArray(17 * 1024 * 1024))
            }
            val document = DocxDocumentParser.parse(docx, "Document")
            val blocks = document.pages.flatMap { it.blocks }
            assertTrue(blocks.filterIsInstance<DocxBlock.Paragraph>().any { it.text == "One & two" })
            // Oversized/unsupported media doesn't stop readable text from opening.
            assertThrows(IllegalArgumentException::class.java) { blocks.filterIsInstance<DocxBlock.Image>().single().imageBytes }
        } finally { directory.deleteRecursively() }
    }
}
