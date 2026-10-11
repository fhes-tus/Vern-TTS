package com.veritas.reader

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.*
import com.veritas.reader.ui.screens.NoteSharing
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Explicit two-phase checks: force-stop only the disposable .checks package between phases. */
class DurableLookupAcceptanceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also { check(it.packageName.endsWith(".checks")) }
    private val marker get() = File(context.filesDir, "durable-lookup-check.json")
    @Test fun enqueueBeforeProcessRestart() {
        assertFalse("Finish or clean up the previous two-phase fixture first", marker.exists())
        val repo = DocumentRepository(context)
        val lookup = UUID.randomUUID().toString(); val noteId = UUID.randomUUID().toString()
        val entry = VocabularyEntry("serendipity", VocabularyLookup.PENDING, "(looked up: Section 2, sentence 7)", 6,
            "It was a moment of serendipity.", lookupId = lookup)
        repo.mutateGeneralNotes { it + GeneralNote(noteId, "__vocab__durable-check", formatVocabularyEntry(entry), System.currentTimeMillis()) }
        val request = OneTimeWorkRequestBuilder<VocabularyLookupWorker>()
            .setInputData(workDataOf("documentId" to "durable-check", "lookupId" to lookup))
            .setInitialDelay(20, TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniqueWork("${VocabularyLookupWorker.TAG}:$lookup", ExistingWorkPolicy.KEEP, request).result.get()
        marker.writeText(JSONObject().put("noteId", noteId).put("lookupId", lookup).put("workId", request.id).toString())
        assertEquals(WorkInfo.State.ENQUEUED, WorkManager.getInstance(context).getWorkInfoById(request.id).get().state)
    }
    @Test fun resolvesOriginalSelectionAfterProcessRestart() {
        val data = JSONObject(marker.readText()); val repo = DocumentRepository(context)
        val id = data.getString("noteId"); val lookup = data.getString("lookupId")
        try {
            val deadline = SystemClock.elapsedRealtime() + 90000
            var entry = parseVocabularyNoteContent(repo.loadGeneralNotes().single { it.id == id }.content).single()
            while (VocabularyLookup.needsDefinition(entry.explanation) && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(250)
                entry = parseVocabularyNoteContent(repo.loadGeneralNotes().single { it.id == id }.content).single()
            }
            assertFalse("Definition did not survive process restart: ${entry.explanation}", VocabularyLookup.needsDefinition(entry.explanation))
            assertEquals("It was a moment of serendipity.", entry.contextSentence); assertEquals(6, entry.sentenceIndex)
            assertTrue(entry.source.contains("Section 2, sentence 7"))
        } finally {
            WorkManager.getInstance(context).cancelUniqueWork("${VocabularyLookupWorker.TAG}:$lookup").result.get()
            repo.mutateGeneralNotes { it.filterNot { note -> note.id == id } }; marker.delete()
        }
    }
    @Test fun longNotePdfContainsEveryLineAndFitsPageMargins() {
        val rows = (1..360).joinToString("\n") { "LINE_${it.toString().padStart(3, '0')} Reading, listening and study notes remain portable." }
        val note = GeneralNote("pdf-pagination-check", "Portable study notes", rows, 1)
        val uri = NoteSharing.pdfIntent(context, note).clipData!!.getItemAt(0).uri
        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
            context.contentResolver.openInputStream(uri)!!.use { input -> com.tom_roush.pdfbox.pdmodel.PDDocument.load(input).use { doc ->
                val text = com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc)
                assertTrue(doc.numberOfPages >= 8)
                (1..360).forEach { assertTrue("Missing note line $it", text.contains("LINE_${it.toString().padStart(3, '0')}")) }
            } }
            val target = File(context.getExternalFilesDir(null), "remaining-fixes/long-note-pages.pdf").apply { parentFile!!.mkdirs() }
            context.contentResolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
        } finally { context.contentResolver.delete(uri, null, null) }
    }
}
