package com.veritas.reader

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer

/** Supplied PDF stays outside the APK/repository. Run explicitly with the local fixture present. */
class GoodVibesExtractionInstrumentedTest {
    private fun words(text: String) = Regex("[\\p{L}\\p{N}]+").findAll(Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase())
        .map { it.value }.groupingBy { it }.eachCount()
    private fun page(text: String, number: Int): String {
        val marker = ReaderTextIndex.pageMarker(number)
        val start = text.indexOf(marker)
        if (start < 0) return ""
        return text.substring(start + marker.length).substringBefore("[[VERITAS_PAGE:").trim()
    }

    @Test fun suppliedFirstFivePagesKeepEveryNativeWordAndOcrTheImageCover() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName == "com.veritas.reader.checks")
        val original = File(context.getExternalFilesDir(null), "good-vibes-original.pdf")
        assumeTrue("Supply the user's local PDF before running this diagnostic", original.isFile)
        val uri = Uri.fromFile(original)
        val folder = File(context.getExternalFilesDir(null), "GoodVibesAudit").apply { mkdirs() }
        DocumentExtractor.openPdfDocument(context, uri).use { managed ->
            val extracted = DocumentExtractor.extractPdfChunk(context, uri, managed.document, managed.pageCount,
                "Good Vibes, Good Life (Vex King)", PdfImportOptions(startPage = 1, endPage = 5))
            File(folder, "first-five-fixed.txt").writeText(extracted.text)
            assertFalse(extracted.partial)
            val coverLetters = page(extracted.text, 1).filter { it.isLetter() }.lowercase()
            assertTrue("OCR recovers the letter-spaced cover title", coverLetters.contains("vibes"))
            assertTrue("OCR recovers the cover author", coverLetters.contains("vexking"))
            assertTrue(extracted.note.orEmpty().contains("OCR read image-only PDF page 1"))
            for (number in 2..5) {
                val native = PdfLayoutProbeStripper.extract(managed.document, number).plainText
                val expected = words(native)
                val actual = words(page(extracted.text, number))
                val lost = expected.filter { (word, count) -> (actual[word] ?: 0) < count }
                assertTrue("Page $number lost native word counts: $lost", lost.isEmpty())
            }
            assertEquals(3, Regex("Introduction").findAll(page(extracted.text, 4)).count())
            assertEquals(3, Regex("Introduction").findAll(page(extracted.text, 5)).count())

            val repairId = InstrumentationRegistry.getArguments().getString("repairDocumentId") ?: return@use
            val repository = DocumentRepository(context)
            val saved = requireNotNull(repository.findDocument(repairId))
            require(saved.title.contains("Good Vibes", true))
            val storedOriginal = requireNotNull(repository.originalFile(saved))
            fun sha(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            require(sha(original).contentEquals(sha(storedOriginal))) { "Refuse to repair a different edition" }
            val oldText = repository.readText(saved)
            val suffixStart = oldText.indexOf(ReaderTextIndex.pageMarker(6))
            require(suffixStart > 0) { "Expected the saved book's original page boundaries" }
            val annotations = repository.loadAnnotations(saved.id)
            File(folder, "backup-${saved.id}.txt").let { if (!it.exists()) it.writeText(oldText) }
            File(folder, "backup-${saved.id}-metadata.json").let { if (!it.exists()) it.writeText(saved.toJson().toString(2)) }
            val annotationBackup = JSONArray().apply {
                annotations.forEach { put(it.toJson()) }
            }.toString(2)
            File(folder, "backup-${saved.id}-annotations.json").let { if (!it.exists()) it.writeText(annotationBackup) }
            val oldAnchor = ReaderTextIndex.build(oldText, saved.pageCount).sentences.getOrNull(saved.currentIndex)
            val repaired = extracted.text.trim() + "\n\n" + oldText.substring(suffixStart)
            val newSentences = ReaderTextIndex.build(repaired, saved.pageCount).sentences
            val samePage = newSentences.indices.filter { newSentences[it].pageNumber == oldAnchor?.pageNumber }
            val exact = samePage.filter { newSentences[it].text == oldAnchor?.text }
            val candidates = exact.ifEmpty { samePage }
            val anchorWords = words(oldAnchor?.text.orEmpty()).keys
            val newIndex = candidates.maxWithOrNull(compareBy<Int> {
                words(newSentences[it].text).keys.intersect(anchorWords).size
            }.thenBy { -kotlin.math.abs(it - saved.currentIndex) }) ?: saved.currentIndex.coerceAtMost(newSentences.lastIndex)
            requireNotNull(repository.updateDocumentText(saved.id, repaired, partial = saved.partial))
            repository.saveProgress(saved.id, newIndex)
            oldAnchor?.let { assertEquals(it.pageNumber, newSentences[newIndex].pageNumber) }
            assertEquals(annotations.size, repository.loadAnnotations(saved.id).size)
            assertEquals(oldText.substring(suffixStart), repository.readText(requireNotNull(repository.findDocument(saved.id)))
                .substringAfter(ReaderTextIndex.pageMarker(6)).let { ReaderTextIndex.pageMarker(6) + it })
            File(folder, "repair-result.txt").writeText("Repaired ${saved.id}; first five pages only; position ${saved.currentIndex} -> $newIndex; annotations ${annotations.size} retained.")
        }
    }
}
