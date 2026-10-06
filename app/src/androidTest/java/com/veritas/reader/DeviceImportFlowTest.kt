package com.veritas.reader

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.text.Selection
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.veritas.reader.ui.ReaderViewModel
import com.veritas.reader.ui.importDocumentFromUri
import com.veritas.reader.ui.appendVocabularyWord
import com.veritas.reader.ui.removeVocabularyWord
import com.veritas.reader.ui.importMultipleDocuments
import com.veritas.reader.ui.DOCUMENT_BATCH_TAG
import com.veritas.reader.ui.withVisibility
import com.veritas.reader.ui.screens.updateReaderPresentationSpans
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Real WorkManager/extractor/UI lifecycle checks; only removes this test's own readings. */
@RunWith(AndroidJUnit4::class)
class DeviceImportFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val token = "Device check ${UUID.randomUUID()}"
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var viewModel: ReaderViewModel
    private lateinit var repository: DocumentRepository
    private lateinit var fixtures: File
    private val workIds = mutableSetOf<UUID>()

    @Before fun setUp() {
        repository = DocumentRepository(context)
        fixtures = File(context.cacheDir, "device_check_${UUID.randomUUID()}").apply { mkdirs() }
        // MainActivity consumes its incoming action/data. An action-free explicit
        // intent remains matchable by ActivityScenario after that consumption.
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        scenario.onActivity { viewModel = ViewModelProvider(it)[ReaderViewModel::class.java] }
    }

    @After fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        val workManager = WorkManager.getInstance(context)
        workIds.forEach { workManager.cancelWorkById(it).result.get(10, TimeUnit.SECONDS) }
        repository.loadDocuments().filter { it.title.startsWith(token) || it.id in workIds.map(UUID::toString) }
            .forEach { repository.deleteDocument(it.id) }
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        workIds.forEach { notifications.cancel((it.hashCode() and 0x3fffffff) * 2 + 1) }
        fixtures.deleteRecursively()
    }

    private fun await(description: String, timeoutMillis: Long = 90_000, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (!predicate()) {
            check(SystemClock.elapsedRealtime() < deadline) { "Timed out: $description; state=${viewModel.uiState.value.importInProgress}" }
            SystemClock.sleep(100)
        }
    }

    private fun import(file: File, options: PdfImportOptions = PdfImportOptions()) {
        scenario.onActivity {
            viewModel.importDocumentFromUri(Uri.fromFile(file), pdfOptions = options, customTitle = token)
            workIds.addAll(viewModel.pendingImportIds)
            assertTrue(viewModel.uiState.value.importInProgress)
            assertFalse("Importing must not show an opening dialog", viewModel.uiState.value.isOpeningDocument)
        }
        await("import completes") { !viewModel.uiState.value.importInProgress }
    }

    private fun pdf(pages: Int): File {
        PDFBoxResourceLoader.init(context)
        val file = File(fixtures, "fixture.pdf")
        PDDocument().use { document ->
            repeat(pages) { index ->
                val page = PDPage()
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 14f)
                    stream.newLineAtOffset(60f, 500f)
                    stream.showText("Source page ${index + 1} contains a unique readable sentence.")
                    stream.endText()
                }
            }
            document.save(file)
        }
        return file
    }

    @Test fun utf8ImportOpensExtractedTextAndConsumesStatus() {
        val text = "Café reading. Ελληνικά words. 日本語 text."
        import(File(fixtures, "fixture.txt").apply { writeText(text) })
        val saved = repository.loadDocuments().single { it.title == token }
        assertEquals(text, repository.readText(saved))
        await("reader opens imported text") { viewModel.uiState.value.activeDocument?.id == saved.id }
        assertTrue(viewModel.uiState.value.activeDocument!!.rawText.contains("Café"))
        await("completion status consumed") { viewModel.uiState.value.importMessage == null }
        scenario.onActivity { viewModel.returnToLibrary() }
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        instrumentation.waitForIdleSync()
        assertNull("Returning/resuming must not replay the consumed status", viewModel.uiState.value.importMessage)
    }

    @Test fun multiChunkPdfFinishesInOneReadingAndDisplaysLastPage() {
        import(pdf(26), PdfImportOptions(preferOcrWhenLowText = false, removeTopPageNoise = false,
            removeBottomPageNoise = false, cleanupRepeatedLines = false))
        val saved = repository.loadDocuments().single { it.title == token }
        val text = repository.readText(saved)
        assertFalse(saved.partial)
        assertEquals(26, saved.pageCount)
        assertTrue(text.contains("Source page 1 "))
        assertTrue(text.contains("Source page 26 "))
        assertEquals(TextChunker.chunk(text).size, saved.chunkCount)
        await("reader refresh includes appended page") {
            viewModel.uiState.value.activeDocument?.rawText?.contains("Source page 26 ") == true
        }
    }

    @Test fun sherlockReadyPagesAreUsableWhileRemainingPagesImport() {
        val file = sherlockPdf()
        val started = SystemClock.elapsedRealtime()
        scenario.onActivity {
            viewModel.importDocumentFromUri(Uri.fromFile(file), customTitle = token)
            workIds.addAll(viewModel.pendingImportIds)
        }
        await("first Sherlock pages open", 20_000) {
            viewModel.uiState.value.activeDocument?.title == token && !viewModel.uiState.value.isOpeningDocument
        }
        val readyMillis = SystemClock.elapsedRealtime() - started
        android.util.Log.i("ProgressiveImportRegression", "Reader ready after ${readyMillis}ms")
        assertTrue("Initial reading must not wait for the entire PDF ($readyMillis ms)", readyMillis < 15_000)
        assertTrue("Background extraction must still be running when reading opens", viewModel.uiState.value.importInProgress)
        assertTrue(WorkManager.getInstance(context).getWorkInfoById(workIds.single()).get().state == WorkInfo.State.RUNNING)
        val firstText = viewModel.uiState.value.activeDocument!!.rawText
        assertTrue(firstText.contains(ReaderTextIndex.pageMarker(5)))
        assertFalse(firstText.contains(ReaderTextIndex.pageMarker(50)))
        compose.onAllNodesWithText("Importing", substring = false).assertCountEquals(0)
        await("more pages reach the open reader") {
            viewModel.uiState.value.activeDocument?.rawText?.contains(ReaderTextIndex.pageMarker(30)) == true
        }
        assertTrue("Newly ready pages should be visible before full completion", viewModel.uiState.value.importInProgress)
        await("Sherlock finishes") { !viewModel.uiState.value.importInProgress }
        val saved = repository.loadDocuments().single { it.title == token }
        assertFalse(saved.partial)
        assertEquals(50, saved.pageCount)
        assertTrue(repository.readText(saved).contains(ReaderTextIndex.pageMarker(50)))
        assertEquals(saved.id, viewModel.uiState.value.activeDocument?.id)
        assertTrue(viewModel.uiState.value.activeDocument!!.rawText.startsWith(firstText))
    }

    private fun sherlockPdf(): File = File(fixtures, "sherlock.pdf").also { file ->
        instrumentation.context.assets.open("sherlock-progressive-import.pdf").use { input ->
            file.outputStream().use(input::copyTo)
        }
    }

    @Test fun leavingProgressiveImportKeepsLibraryUsableThroughCompletion() {
        val file = sherlockPdf()
        scenario.onActivity {
            viewModel.importDocumentFromUri(Uri.fromFile(file), customTitle = token)
            workIds.addAll(viewModel.pendingImportIds)
        }
        await("ready pages open") { viewModel.uiState.value.activeDocument?.title == token }
        assertTrue(viewModel.uiState.value.importInProgress)
        scenario.onActivity { viewModel.returnToLibrary() }
        assertNull(viewModel.uiState.value.activeDocument)
        assertFalse(viewModel.uiState.value.importAwaitingReadyPages)
        compose.onAllNodesWithText("Importing", substring = false).assertCountEquals(0)
        await("background import finishes in library") { !viewModel.uiState.value.importInProgress }
        assertNull("Completion must not reopen the reading the user left", viewModel.uiState.value.activeDocument)
        assertFalse(repository.loadDocuments().single { it.title == token }.partial)
    }

    @Test fun resumedPartialWorkerReplacesItsExistingReading() {
        val file = pdf(2)
        val request = OneTimeWorkRequestBuilder<DocumentImportWorker>().setInputData(workDataOf(
            "uri" to Uri.fromFile(file).toString(), "title" to token,
            "pdf_preferOcrWhenLowText" to false, "pdf_removeTopPageNoise" to false,
            "pdf_removeBottomPageNoise" to false, "pdf_cleanupRepeatedLines" to false)).build()
        workIds.add(request.id)
        repository.createDocumentWithResult(title = token, text = "Previously extracted page.",
            sourceLabel = "PDF", partial = true, documentId = request.id.toString())
        val manager = WorkManager.getInstance(context)
        manager.enqueue(request).result.get(10, TimeUnit.SECONDS)
        await("resumed worker completes") { manager.getWorkInfoById(request.id).get().state.isFinished }
        assertEquals(WorkInfo.State.SUCCEEDED, manager.getWorkInfoById(request.id).get().state)
        val restored = repository.loadDocuments().single { it.title == token }
        assertEquals(request.id.toString(), restored.id)
        assertFalse(restored.partial)
        val text = repository.readText(restored)
        assertTrue(text.contains("Source page 2 "))
        assertFalse(text.contains("Previously extracted"))
    }

    @Test fun unreadableImportClearsBusyStateWithoutCreatingReading() {
        import(File(fixtures, "empty.txt").apply { writeText("   \n") })
        assertTrue(repository.loadDocuments().none { it.title == token })
        val result = WorkManager.getInstance(context).getWorkInfoById(workIds.single()).get()
        assertEquals(WorkInfo.State.FAILED, result.state)
        assertFalse(viewModel.uiState.value.isOpeningDocument)
        await("failure status consumed") { viewModel.uiState.value.importMessage == null }
    }

    @Test fun pendingImportDoesNotCoverLibraryOrFileBrowser() {
        scenario.onActivity {
            viewModel.updateState { it.copy(importInProgress = true, importAwaitingReadyPages = true,
                activeDocument = null, importSourceName = "Pending PDF") }
        }
        compose.onAllNodesWithText("Importing", substring = false).assertCountEquals(0)
        scenario.onActivity { viewModel.updateState { it.withVisibility(VeritasScreen.FILE_BROWSER, true) } }
        compose.onAllNodesWithText("Importing", substring = false).assertCountEquals(0)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        await("Back dismisses file browser") { !viewModel.uiState.value.showFileBrowser }
        assertTrue("Back must not cancel an import", viewModel.uiState.value.importInProgress)
    }

    private fun startBatch(files: List<File>, queue: Boolean): List<UUID> {
        val manager = WorkManager.getInstance(context)
        val previous = manager.getWorkInfosByTag(DOCUMENT_BATCH_TAG).get().map { it.id }.toSet()
        scenario.onActivity { viewModel.importMultipleDocuments(files.map(Uri::fromFile), queue) }
        var requests = emptyList<WorkInfo>()
        await("all batch files registered") {
            requests = manager.getWorkInfosByTag(DOCUMENT_BATCH_TAG).get().filter { it.id !in previous }
            requests.size == files.distinct().size
        }
        return requests.map { it.id }.also { workIds.addAll(it) }
    }

    @Test fun batchContinuesAfterRejectedFileAndBackWithoutDamagingOtherFiles() {
        val first = File(fixtures, "$token first.pdf").also { sherlockPdf().copyTo(it) }
        val bad = File(fixtures, "$token empty.txt").apply { writeText(" \n ") }
        val finalText = "Café batch reading. Ελληνικά words. 日本語 text."
        val last = File(fixtures, "$token last.txt").apply { writeText(finalText) }
        scenario.onActivity { viewModel.updateState { it.withVisibility(VeritasScreen.FILE_BROWSER, true) } }
        val ids = startBatch(listOf(first, bad, last, last), queue = true)
        val manager = WorkManager.getInstance(context)
        await("first PDF publishes ready pages") {
            ids.any { manager.getWorkInfoById(it).get().progress.getString("firstChunkDocumentId") != null }
        }
        val work = ids.map { manager.getWorkInfoById(it).get() }
        assertEquals("Only one batch extractor may run", 1, work.count { it.state == WorkInfo.State.RUNNING })
        assertEquals("Later files wait instead of competing for PDF memory", 2, work.count { it.state == WorkInfo.State.BLOCKED })
        assertEquals(0, viewModel.uiState.value.batchImportCurrent)
        compose.onAllNodesWithText("Importing", substring = false).assertCountEquals(0)
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        await("Back leaves file browser") { !viewModel.uiState.value.showFileBrowser }
        assertTrue(viewModel.uiState.value.isBatchImporting)
        await("batch completes including rejected file") {
            ids.all { manager.getWorkInfoById(it).get().state.isFinished } && !viewModel.uiState.value.isBatchImporting
        }
        assertEquals(3, viewModel.uiState.value.batchImportTotal)
        assertEquals(3, viewModel.uiState.value.batchImportCurrent)
        assertEquals(1, viewModel.uiState.value.batchImportFailed)
        assertFalse(viewModel.uiState.value.importInProgress)
        assertNull("Batch must not reopen the browser or reader", viewModel.uiState.value.activeDocument)
        val readings = repository.loadDocuments().filter { it.id in ids.map(UUID::toString) }
        assertEquals("A duplicate selection must not create duplicate documents", 2, readings.size)
        val pdf = readings.single { it.sourceLabel == "PDF" }
        assertFalse(pdf.partial)
        assertEquals(50, pdf.pageCount)
        assertTrue(repository.readText(pdf).contains(ReaderTextIndex.pageMarker(50)))
        assertEquals(first.length(), repository.originalFile(pdf)!!.length())
        val text = readings.single { it.id != pdf.id }
        assertEquals(finalText, repository.readText(text))
        assertEquals(listOf(pdf.id, text.id), repository.loadQueueDocuments().filter { it.id in readings.map { doc -> doc.id } }.map { it.id })
        assertEquals(1, ids.count { manager.getWorkInfoById(it).get().outputData.getString("error") != null })
    }

    @Test fun batchReattachesAfterActivityAndViewModelAreDestroyed() {
        val first = File(fixtures, "$token restart.pdf").also { sherlockPdf().copyTo(it) }
        val last = File(fixtures, "$token after restart.txt").apply { writeText("This file must survive leaving the app.") }
        val ids = startBatch(listOf(first, last), queue = false)
        val manager = WorkManager.getInstance(context)
        await("first pages published before leaving app") {
            ids.any { manager.getWorkInfoById(it).get().progress.getString("firstChunkDocumentId") != null }
        }
        assertTrue(ids.any { manager.getWorkInfoById(it).get().state == WorkInfo.State.RUNNING })
        val previousViewModel = viewModel
        scenario.close()
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        scenario.onActivity { viewModel = ViewModelProvider(it)[ReaderViewModel::class.java] }
        assertNotSame(previousViewModel, viewModel)
        await("restored batch reaches completion") {
            ids.all { manager.getWorkInfoById(it).get().state.isFinished } &&
                viewModel.uiState.value.batchImportCurrent == 2 && !viewModel.uiState.value.isBatchImporting
        }
        assertEquals(0, viewModel.uiState.value.batchImportFailed)
        assertFalse(viewModel.uiState.value.importInProgress)
        val saved = repository.loadDocuments().filter { it.id in ids.map(UUID::toString) }
        assertEquals(2, saved.size)
        assertTrue(saved.none { it.partial })
        assertTrue(repository.readText(saved.single { it.pageCount == 50 }).contains(ReaderTextIndex.pageMarker(50)))
        assertTrue(saved.all { repository.originalFile(it)?.exists() == true })
        assertNull(viewModel.uiState.value.activeDocument)
    }

    @Test fun singleFileBatchRefreshesCompletionInLibraryAndOpenReader() {
        val file = File(fixtures, "$token single batch.pdf").also { sherlockPdf().copyTo(it) }
        val id = startBatch(listOf(file), queue = false).single()
        await("partial batch document appears in library") {
            viewModel.uiState.value.documents.any { it.id == id.toString() && it.partial }
        }
        val ready = repository.findDocument(id.toString())!!
        scenario.onActivity { viewModel.openSavedDocument(ready) }
        await("ready batch pages open") { viewModel.uiState.value.activeDocument?.id == ready.id }
        assertTrue("Reading can begin during batch import", viewModel.uiState.value.isBatchImporting)
        await("batch completion refreshes reader and library") {
            !viewModel.uiState.value.isBatchImporting &&
                viewModel.uiState.value.documents.any { it.id == ready.id && !it.partial } &&
                viewModel.uiState.value.activeDocument?.rawText?.contains(ReaderTextIndex.pageMarker(50)) == true
        }
        assertEquals(1, viewModel.uiState.value.batchImportCurrent)
        assertEquals(0, viewModel.uiState.value.batchImportFailed)
        assertFalse(viewModel.uiState.value.importInProgress)
        assertEquals(ready.id, viewModel.uiState.value.activeDocument?.id)
    }

    @Test fun androidSelectionSurvivesPresentationSpanRefresh() {
        instrumentation.runOnMainSync {
            val text = SpannableString("Selected reader sentence.")
            val oldStyle = ForegroundColorSpan(android.graphics.Color.BLACK)
            text.setSpan(oldStyle, 0, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            Selection.setSelection(text, 0, 8)
            val editorSpan = Any()
            text.setSpan(editorSpan, 0, 8, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            val next = SpannableString(text.toString())
            val newStyle = ForegroundColorSpan(android.graphics.Color.BLUE)
            next.setSpan(newStyle, 0, next.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            updateReaderPresentationSpans(text, listOf(oldStyle), next)
            assertEquals(0, Selection.getSelectionStart(text))
            assertEquals(8, Selection.getSelectionEnd(text))
            assertEquals(0, text.getSpanStart(editorSpan))
            assertEquals(-1, text.getSpanStart(oldStyle))
            assertEquals(0, text.getSpanStart(newStyle))
        }
    }

    @Test fun vocabularyUsesSelectedSentenceEvenWhenPlaybackIsElsewhere() {
        import(File(fixtures, "vocabulary.txt").apply {
            writeText("A curious visitor arrived. The patient visitor waited. Another sentence is being read.")
        })
        val saved = repository.loadDocuments().single { it.title == token }
        await("vocabulary reader opens") { viewModel.uiState.value.activeDocument?.id == saved.id }
        val sentences = viewModel.uiState.value.activeDocument!!.sentences
        val selected = sentences.indexOfFirst { it.contains("patient visitor") }
        assertTrue(selected >= 0)
        scenario.onActivity {
            PlaybackStateStore.currentIndex = sentences.lastIndex
            viewModel.appendVocabularyWord("visitor", "A person making a visit.", selectedSentenceIndex = selected)
            viewModel.appendVocabularyWord("patient", "Able to wait calmly.", selectedSentenceIndex = selected)
        }
        val title = "__vocab__${saved.id}"
        try {
            await("both asynchronous vocabulary additions persist") {
                repository.loadGeneralNotes().firstOrNull { it.title == title }?.let {
                    parseVocabularyNoteContent(it.content).size == 2
                } == true
            }
            val entries = parseVocabularyNoteContent(repository.loadGeneralNotes().single { it.title == title }.content)
            entries.forEach {
                assertEquals(selected, it.sentenceIndex)
                assertEquals(sentences[selected], it.contextSentence)
            }
            scenario.onActivity { viewModel.removeVocabularyWord(saved.id, "visitor") }
            await("vocabulary removal completes") {
                repository.loadGeneralNotes().firstOrNull { it.title == title }?.let {
                    parseVocabularyNoteContent(it.content).size == 1
                } == true
            }
            val remaining = parseVocabularyNoteContent(repository.loadGeneralNotes().single { it.title == title }.content).single()
            assertEquals("patient", remaining.word)
            assertEquals(sentences[selected], remaining.contextSentence)
        } finally {
            repository.mutateGeneralNotes { it.filterNot { note -> note.title == title } }
        }
    }
}
