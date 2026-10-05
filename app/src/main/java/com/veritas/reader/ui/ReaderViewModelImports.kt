package com.veritas.reader.ui

import com.veritas.reader.VeritasScreen
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.veritas.reader.CoverExtractor
import com.veritas.reader.DocumentExtractor
import com.veritas.reader.DocumentImportWorker
import com.veritas.reader.PdfImportOptions
import com.veritas.reader.PlaybackStateStore
import com.veritas.reader.PptxImportOptions
import com.veritas.reader.SavedDocument
import com.veritas.reader.TextImportOptions
import com.veritas.reader.WebArticleExtractor
import com.veritas.reader.addToQueue
import com.veritas.reader.cleanDocumentTitle
import com.veritas.reader.buildReaderDocument
import com.veritas.reader.getDisplayName
import com.veritas.reader.loadAnnotations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

fun ReaderViewModel.createAndOpenDocument(
    title: String,
    text: String,
    sourceLabel: String,
    originalUri: Uri? = null,
    originalMimeType: String = "",
    pageCount: Int = 0,
    partial: Boolean = false,
    initialCoverBitmap: Bitmap? = null
) {
    if (text.isBlank()) return
    viewModelScope.launch(Dispatchers.IO) {
        val saved = repository.createDocument(
            title = title,
            text = text,
            sourceLabel = sourceLabel,
            originalUri = originalUri,
            originalDisplayName = title,
            originalMimeType = originalMimeType,
            pageCount = pageCount,
            partial = partial
        )
        if (initialCoverBitmap != null) {
            runCatching {
                CoverExtractor.saveCoverBitmap(getApplication(), saved.id, initialCoverBitmap)
            }
        }
        // Extract cover image in background for compatible documents
        if (originalUri != null) {
            runCatching {
                val mime = getApplication<Application>().contentResolver.getType(originalUri).orEmpty().lowercase()
                val ext = saved.title.substringAfterLast('.', "").lowercase()
                when {
                    mime.contains("pdf") || ext == "pdf" -> {
                        CoverExtractor.extractPdfCover(getApplication(), saved.id, originalUri)
                    }
                    mime.contains("epub") || ext == "epub" -> {
                        CoverExtractor.extractEpubCover(getApplication(), saved.id, originalUri)
                    }
                    mime.startsWith("image/") || ext in setOf("png", "jpg", "jpeg", "webp", "bmp", "gif") -> {
                        CoverExtractor.extractImageCover(getApplication(), saved.id, originalUri)
                    }
                }
            }
        }
        withContext(Dispatchers.Main) {
            refreshAll()
            openSavedDocument(saved)
            _uiState.update { it.copy(draftText = "", hasImportedOrOpenedDocument = true) }
        }
    }
}

fun ReaderViewModel.continuePdfExtractionInBackground(
    saved: SavedDocument,
    uri: Uri,
    title: String,
    pdfOptions: PdfImportOptions
) {
    viewModelScope.launch(Dispatchers.IO) {
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(importMessage = "Opened the ready pages. Vern is finishing the rest of this PDF in the background.") }
        }
        val full = runCatching {
            DocumentExtractor.extract(
                context = getApplication(),
                uri = uri,
                displayName = title,
                pdfOptions = pdfOptions,
                foregroundBudgetMillis = null
            )
        }.getOrElse { error ->
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(importMessage = "The readable pages are open. Background extraction could not finish: ${error.message ?: "unknown error"}") }
            }
            null
        }

        if (full != null && full.text.isNotBlank()) {
            repository.updateDocumentText(saved.id, full.text, partial = full.partial)?.let { updated ->
                withContext(Dispatchers.Main) {
                    refreshAll()
                    if (uiState.value.activeDocument?.id == updated.id) {
                        val previousIndex = currentReaderIndex
                        viewModelScope.launch(Dispatchers.IO) {
                            val readerDocument = loadReaderDocument(updated)
                            val annotations = repository.loadAnnotations(updated.id)
                            withContext(Dispatchers.Main) {
                                _uiState.update { it.copy(activeDocument = readerDocument, annotations = annotations) }
                                syncPlaybackStateForDocument(readerDocument, previousIndex)
                            }
                        }
                    }
                    _uiState.update { it.copy(importMessage = full.note ?: "Finished background extraction for ${updated.title}.") }
                }
            }
        }
    }
}

fun ReaderViewModel.prepareImport(uri: Uri, sourceNameHint: String? = null) {
    documentOpenJob?.cancel()
    autoOpenImportId = null
    val app = getApplication<Application>()
    val name = getDisplayName(app, uri).ifBlank { "Imported document" }
    val mimeType = app.contentResolver.getType(uri).orEmpty().lowercase()
    val extension = name.substringAfterLast('.', "").lowercase()
    val isPdf = mimeType.contains("pdf") || extension == "pdf" || uri.path?.lowercase()?.endsWith(".pdf") == true
    
    val sizeBytes = if (uri.scheme == "file") {
        uri.path?.let { File(it).length() } ?: 0L
    } else {
        try {
            app.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                fd.statSize
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    _uiState.update { it.copy(isOpeningDocument = true, importSourceName = name, importAwaitingReadyPages = false) }

    documentOpenJob = viewModelScope.launch(Dispatchers.IO) {
        try {
            val pageCount = if (isPdf) {
                DocumentExtractor.getPdfPageCount(app, uri)
            } else 0
            // Same name AND same byte size as a stored original = the same file:
            // open the existing reading instead of importing a second copy.
            val baseName = name.substringBeforeLast('.').trim()
            val cleanedName = cleanDocumentTitle(name)
            val duplicate = repository.loadDocuments().firstOrNull { doc ->
                val titleMatches = doc.title.trim().equals(baseName, ignoreCase = true) ||
                    doc.title.trim().equals(name.trim(), ignoreCase = true) ||
                    doc.title.trim().equals(cleanedName, ignoreCase = true)
                val originalMatches = sizeBytes > 0L && repository.originalFile(doc)?.length() == sizeBytes
                val textExists = File(repository.docsDir, doc.fileName).let { it.exists() && it.length() > 0L }
                titleMatches && originalMatches && textExists && !doc.partial
            }
            if (duplicate != null) {
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.withVisibility(VeritasScreen.FILE_BROWSER, false).copy(
                            isOpeningDocument = false,
                            importMessage = "Already in your library - opening \"${duplicate.title}\"."
                        )
                    }
                    openSavedDocument(duplicate)
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                val isPptx = mimeType.contains("presentationml", ignoreCase = true) ||
                    name.endsWith(".pptx", ignoreCase = true)
                val pending = VeritasPendingImport(
                    uri = uri,
                    name = name,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes,
                    isPdf = isPdf,
                    pageCount = pageCount,
                    pdfOptions = PdfImportOptions(
                        startPage = 1,
                        endPage = if (pageCount > 0) pageCount else null
                    ),
                    textOptions = TextImportOptions(),
                    isPptx = isPptx,
                    pptxOptions = PptxImportOptions(),
                    sourceNameHint = sourceNameHint
                )
                _uiState.update {
                    it.copy(
                        pendingImport = pending,
                        isOpeningDocument = false
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(isOpeningDocument = false,
                    importMessage = "Could not prepare $name: ${error.message ?: "unknown error"}") }
            }
        }
    }
}

fun ReaderViewModel.cancelPendingImport() {
    _uiState.update { it.copy(pendingImport = null) }
}

fun ReaderViewModel.executePendingImport(
    title: String,
    pdfOptions: PdfImportOptions,
    textOptions: TextImportOptions,
    pptxOptions: PptxImportOptions = PptxImportOptions()
) {
    val pending = uiState.value.pendingImport ?: return
    _uiState.update { it.copy(pendingImport = null) }
    importDocumentFromUri(
        uri = pending.uri,
        pdfOptions = pdfOptions,
        textOptions = textOptions,
        pptxOptions = pptxOptions,
        sourceNameHint = pending.sourceNameHint,
        customTitle = title
    )
}

fun ReaderViewModel.importDocumentFromUri(
    uri: Uri,
    pdfOptions: PdfImportOptions = PdfImportOptions(),
    textOptions: TextImportOptions = TextImportOptions(),
    pptxOptions: PptxImportOptions = PptxImportOptions(),
    sourceNameHint: String? = null,
    customTitle: String? = null,
    queueAfterImport: Boolean = false,
    openAfterImport: Boolean = true
) {
    val app = getApplication<Application>()
    val title = customTitle?.ifBlank { null }
        ?: cleanDocumentTitle(getDisplayName(app, uri)).ifBlank { "Imported document" }
    _uiState.update {
        if (openAfterImport) {
            it.withVisibility(VeritasScreen.FILE_BROWSER, false).copy(
                importMessage = "Importing $title in background...",
                importInProgress = true,
                importAwaitingReadyPages = true,
                importSourceName = title
            )
        } else {
            it.copy(importMessage = "Importing $title in background...", importInProgress = true)
        }
    }
    
    // Ensure we have permission
    if (uri.scheme == "content") {
        try {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            android.util.Log.w("ReaderViewModel", "Could not take persistable permission for $uri", e)
        }
    }
    
    val inputData = androidx.work.workDataOf(
        "uri" to uri.toString(),
        "title" to title,
        "pdf_startPage" to (pdfOptions.startPage ?: -1),
        "pdf_endPage" to (pdfOptions.endPage ?: -1),
        "pdf_cleanupRepeatedLines" to pdfOptions.cleanupRepeatedLines,
        "pdf_removePageNumbers" to pdfOptions.removePageNumbers,
        "pdf_repairHyphenation" to pdfOptions.repairHyphenation,
        "pdf_includePageMarkers" to pdfOptions.includePageMarkers,
        "pdf_forceOcr" to pdfOptions.forceOcr,
        "pdf_preferOcrWhenLowText" to pdfOptions.preferOcrWhenLowText,
        "pdf_extractionMode" to pdfOptions.extractionMode,
        "pdf_removeTopPageNoise" to pdfOptions.removeTopPageNoise,
        "pdf_removeBottomPageNoise" to pdfOptions.removeBottomPageNoise,
        "pdf_manualCropBeforeExtract" to pdfOptions.manualCropBeforeExtract,
        "pdf_minWordGap" to pdfOptions.minWordGap,
        "pdf_separateWordsOnFontChange" to pdfOptions.separateWordsOnFontChange,
        "pdf_markPdfLinesForCanvas" to pdfOptions.markPdfLinesForCanvas,
        "pdf_forceFreshExtraction" to pdfOptions.forceFreshExtraction,
        "pdf_cropLeft" to (pdfOptions.cropRect?.left ?: -1f),
        "pdf_cropTop" to (pdfOptions.cropRect?.top ?: -1f),
        "pdf_cropRight" to (pdfOptions.cropRect?.right ?: -1f),
        "pdf_cropBottom" to (pdfOptions.cropRect?.bottom ?: -1f),
        "text_encodingId" to textOptions.encodingId,
        "pptx_includeSpeakerNotes" to pptxOptions.includeSpeakerNotes,
        "pptx_autoPunctuate" to pptxOptions.autoPunctuate,
        "pptx_ocrSlideImages" to pptxOptions.ocrSlideImages
    )
    
    val request = androidx.work.OneTimeWorkRequestBuilder<DocumentImportWorker>()
        .setInputData(inputData)
        .build()
        
    val workManager = androidx.work.WorkManager.getInstance(app)
    if (openAfterImport) autoOpenImportId = request.id
    pendingImportIds.add(request.id)
    workManager.enqueue(request)

    var firstChunkOpened = false
    var lastImportedChars = 0
    var partialRefreshJob: Job? = null
    viewModelScope.launch(Dispatchers.Main) {
        try {
            workManager.getWorkInfoByIdFlow(request.id).first { workInfo ->
                if (workInfo != null) {
                    if (workInfo.state.isFinished && autoOpenImportId == request.id) {
                        _uiState.update { it.copy(importAwaitingReadyPages = false) }
                    }
                    // Auto-open the document as soon as the first chunk is ready
                    if (!firstChunkOpened && workInfo.state == androidx.work.WorkInfo.State.RUNNING) {
                        val firstChunkId = workInfo.progress.getString("firstChunkDocumentId")
                        if (firstChunkId != null) {
                            firstChunkOpened = true
                            refreshAll()
                            if (openAfterImport && autoOpenImportId == request.id) {
                                val saved = withContext(Dispatchers.IO) { repository.findDocument(firstChunkId) }
                                if (saved != null && autoOpenImportId == request.id) {
                                    _uiState.update { it.copy(importAwaitingReadyPages = false) }
                                    openSavedDocument(saved)
                                }
                            }
                            _uiState.update { it.copy(importMessage = "Importing remaining pages of $title...") }
                        }
                    }
                    if (workInfo.state == androidx.work.WorkInfo.State.RUNNING) {
                        val docId = workInfo.progress.getString("firstChunkDocumentId")
                        val chars = workInfo.progress.getInt("importedCharCount", 0)
                        if (docId != null && chars > lastImportedChars) {
                            lastImportedChars = chars
                            if (_uiState.value.activeDocument?.id == docId) {
                                partialRefreshJob?.cancel()
                                partialRefreshJob = viewModelScope.launch(Dispatchers.IO) {
                                    try { refreshImportedReader(docId) }
                                    catch (cancelled: CancellationException) { throw cancelled }
                                    catch (error: Exception) { android.util.Log.w("ReaderViewModel", "Could not refresh ready PDF pages", error) }
                                }
                            }
                        }
                    }
                    when (workInfo.state) {
                        androidx.work.WorkInfo.State.SUCCEEDED -> {
                            val docId = workInfo.outputData.getString("documentId")
                            if (docId != null) {
                                refreshAll()
                                completeQuestImport()
                                if (queueAfterImport) {
                                    viewModelScope.launch(Dispatchers.IO) {
                                        val queuedDocs = repository.addToQueue(docId)
                                        withContext(Dispatchers.Main) {
                                            _uiState.update { it.copy(queuedDocuments = queuedDocs) }
                                            PlaybackStateStore.queueCount = queuedDocs.size
                                        }
                                    }
                                }
                                partialRefreshJob?.cancel()
                                val saved = withContext(Dispatchers.IO) { repository.findDocument(docId) }
                                if (saved != null) {
                                    if (_uiState.value.activeDocument?.id == docId) refreshImportedReader(docId)
                                    else if (openAfterImport && autoOpenImportId == request.id) openSavedDocument(saved)
                                }
                            }
                            pendingImportIds.remove(request.id)
                            _uiState.update { it.copy(importMessage = "Successfully imported $title.", importInProgress = pendingImportIds.isNotEmpty()) }
                        }
                        androidx.work.WorkInfo.State.FAILED -> {
                            val error = workInfo.outputData.getString("error") ?: "Unknown error"
                            val partialId = workInfo.outputData.getString("partialDocumentId")
                            pendingImportIds.remove(request.id)
                            refreshAll()
                            _uiState.update { it.copy(importMessage = if (partialId != null)
                                "Import stopped: $error. The ready pages remain in your library."
                                else "Import failed: $error", importInProgress = pendingImportIds.isNotEmpty()) }
                        }
                        androidx.work.WorkInfo.State.CANCELLED -> {
                            pendingImportIds.remove(request.id)
                            _uiState.update { it.copy(importMessage = "Import cancelled", importInProgress = pendingImportIds.isNotEmpty()) }
                        }
                        else -> {
                            // Still enqueued or running
                        }
                    }
                }
                workInfo?.state?.isFinished == true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("ReaderViewModel", "Error observing work info", e)
            pendingImportIds.remove(request.id)
            _uiState.update { it.copy(importInProgress = pendingImportIds.isNotEmpty(),
                importAwaitingReadyPages = if (autoOpenImportId == request.id) false else it.importAwaitingReadyPages,
                importMessage = "Could not track this import: ${e.message}") }
        } finally {
            partialRefreshJob?.cancel()
        }
    }
}

/** Append ready pages without reopening the reader or resetting selection/navigation. */
internal suspend fun ReaderViewModel.refreshImportedReader(documentId: String) = withContext(Dispatchers.IO) {
    val saved = repository.findDocument(documentId) ?: return@withContext
    val reader = buildReaderDocument(saved, repository.readText(saved))
    coroutineContext.ensureActive()
    withContext(Dispatchers.Main) {
        _uiState.update { state ->
            val active = state.activeDocument
            if (active?.id == documentId && reader.rawText.length >= active.rawText.length) state.copy(activeDocument = reader)
            else state
        }
    }
}

fun ReaderViewModel.importWebArticle(url: String) {
    importJob?.cancel()
    importJob = viewModelScope.launch(Dispatchers.IO) {
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(importInProgress = true, importSourceName = "web article") }
        }
        val article = runCatching {
            WebArticleExtractor.extract(url)
        }.getOrElse { error ->
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(importMessage = "Could not import this web article: ${error.message ?: "unknown error"}") }
            }
            null
        }
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(importInProgress = false, importSourceName = "") }
            importJob = null
            if (article != null) {
                createAndOpenDocument(
                    title = article.title,
                    text = "${article.text}\n\nSource: ${article.url}",
                    sourceLabel = "Web"
                )
                _uiState.update { it.copy(importMessage = "Web article imported into Vern.") }
            }
        }
    }
}

fun ReaderViewModel.importDownloadedBook(file: File, customTitle: String) {
    val cleanTitle = cleanDocumentTitle(customTitle)
    val uri = Uri.fromFile(file)
    importDocumentFromUri(
        uri = uri,
        sourceNameHint = cleanTitle,
        customTitle = cleanTitle,
        openAfterImport = true
    )
    _uiState.update {
        it.copy(
            showOceanOfPdfBrowser = false,
            showClassicsCatalog = false,
            importMessage = "Imported $cleanTitle into your library!"
        )
    }
}
