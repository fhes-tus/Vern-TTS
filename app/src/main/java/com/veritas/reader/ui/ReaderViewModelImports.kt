package com.veritas.reader.ui

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
import com.veritas.reader.getDisplayName
import com.veritas.reader.loadAnnotations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
            repository.updateDocumentText(saved.id, full.text)?.let { updated ->
                withContext(Dispatchers.Main) {
                    refreshAll()
                    if (uiState.value.activeDocument?.id == updated.id) {
                        val previousIndex = PlaybackStateStore.currentIndex
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

    _uiState.update { it.copy(isOpeningDocument = true) }

    viewModelScope.launch(Dispatchers.IO) {
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
                    it.copy(
                        showFileBrowser = false,
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
            it.copy(
                showFileBrowser = false,
                importMessage = "Importing $title in background...",
                isOpeningDocument = true,
                importSourceName = title
            )
        } else {
            it.copy(importMessage = "Importing $title in background...")
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
    workManager.enqueue(request)

    var firstChunkOpened = false
    viewModelScope.launch(Dispatchers.Main) {
        try {
            workManager.getWorkInfoByIdFlow(request.id).collect { workInfo ->
                if (workInfo != null) {
                    // Auto-open the document as soon as the first chunk is ready
                    if (!firstChunkOpened && workInfo.state == androidx.work.WorkInfo.State.RUNNING) {
                        val firstChunkId = workInfo.progress.getString("firstChunkDocumentId")
                        if (firstChunkId != null) {
                            firstChunkOpened = true
                            refreshAll()
                            if (openAfterImport) {
                                val saved = repository.findDocument(firstChunkId)
                                if (saved != null) {
                                    openSavedDocument(saved)
                                }
                            }
                            _uiState.update { it.copy(importMessage = "Importing remaining pages of $title...") }
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
                                val saved = repository.findDocument(docId)
                                if (saved != null) {
                                    if (openAfterImport || _uiState.value.activeDocument?.id == docId) {
                                        val currentIdx = if (_uiState.value.activeDocument?.id == docId) PlaybackStateStore.currentIndex else null
                                        openSavedDocument(saved, startIndex = currentIdx)
                                    } else {
                                        _uiState.update { it.copy(isOpeningDocument = false) }
                                    }
                                } else {
                                    _uiState.update { it.copy(isOpeningDocument = false) }
                                }
                            }
                            _uiState.update { it.copy(importMessage = "Successfully imported $title.", isOpeningDocument = false) }
                        }
                        androidx.work.WorkInfo.State.FAILED -> {
                            val error = workInfo.outputData.getString("error") ?: "Unknown error"
                            _uiState.update { it.copy(importMessage = "Import failed: $error", isOpeningDocument = false) }
                        }
                        androidx.work.WorkInfo.State.CANCELLED -> {
                            _uiState.update { it.copy(importMessage = "Import cancelled", isOpeningDocument = false) }
                        }
                        else -> {
                            // Still enqueued or running
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ReaderViewModel", "Error observing work info", e)
        }
    }
}

fun ReaderViewModel.importMultipleDocuments(uris: List<Uri>, queue: Boolean) {
    if (uris.isEmpty()) return
    val count = uris.size
    _uiState.update {
        it.copy(
            isBatchImporting = true,
            batchImportTotal = count,
            batchImportCurrent = 0,
            importMessage = "Importing 1 of $count files…"
        )
    }
    viewModelScope.launch(Dispatchers.IO) {
        uris.forEachIndexed { index, uri ->
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        batchImportCurrent = index + 1,
                        importMessage = "Importing ${index + 1} of $count files…"
                    )
                }
            }
            importDocumentFromUri(
                uri = uri,
                queueAfterImport = queue,
                openAfterImport = false
            )
        }
        withContext(Dispatchers.Main) {
            kotlinx.coroutines.delay(1200)
            _uiState.update {
                it.copy(
                    isBatchImporting = false,
                    batchImportTotal = 0,
                    batchImportCurrent = 0,
                    importMessage = "Successfully queued $count files for import into Vern."
                )
            }
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

fun ReaderViewModel.downloadClassicBook(book: com.veritas.reader.ui.screens.ClassicBookEntry) {
    viewModelScope.launch(Dispatchers.IO) {
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(importInProgress = true, importSourceName = book.title) }
        }
        val text = runCatching {
            val connection = URL(book.downloadUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            try {
                if (connection.responseCode in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    throw java.io.IOException("Server returned HTTP ${connection.responseCode}")
                }
            } finally {
                connection.disconnect()
            }
        }.getOrElse { error ->
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(importMessage = "Could not download ${book.title}: ${error.message}") }
            }
            null
        }
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(importInProgress = false, importSourceName = "") }
            if (text != null && text.isNotBlank()) {
                val cleaned = cleanAndUnwrapClassicBookText(text)
                val docTitle = "${book.title} - ${book.author}"
                val coverBmp = runCatching {
                    getApplication<Application>().assets.open("covers/${book.id}.jpg").use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)
                    }
                }.getOrNull()
                createAndOpenDocument(
                    title = docTitle,
                    text = cleaned,
                    sourceLabel = "Classic Book",
                    initialCoverBitmap = coverBmp
                )
                _uiState.update {
                    it.copy(
                        importMessage = "Added ${book.title} to your library!",
                        showClassicsCatalog = false
                    )
                }
            }
        }
    }
}

private val GUTENBERG_START_REGEX = Regex("""\*\*\*\s*START OF (THE|THIS) PROJECT GUTENBERG[^\n]*\*\*\*""", RegexOption.IGNORE_CASE)
private val GUTENBERG_END_REGEX = Regex("""\*\*\*\s*END OF (THE|THIS) PROJECT GUTENBERG[^\n]*\*\*\*""", RegexOption.IGNORE_CASE)
private val DOUBLE_NEWLINE_SPLIT_REGEX = Regex("""\n\s*\n+""")
private val LIST_NUMBERED_REGEX = Regex("""^\d+[.)]""")
private val CLASSIC_HEADING_REGEX = Regex("""^(CHAPTER|Chapter|PROLOGUE|Prologue|EPILOGUE|Epilogue|INTRODUCTION|Introduction|PREFACE|Preface|PART|Part|BOOK|Book|ACT|Act|SCENE|Scene)\b.*""", RegexOption.IGNORE_CASE)

private fun cleanAndUnwrapClassicBookText(rawText: String): String {
    val normalized = rawText.replace("\r\n", "\n").replace('\r', '\n')
    var body = normalized
    val startMarker = GUTENBERG_START_REGEX.find(body)
    if (startMarker != null) {
        body = body.substring(startMarker.range.last + 1).trimStart()
    }
    val endMarker = GUTENBERG_END_REGEX.find(body)
    if (endMarker != null) {
        body = body.substring(0, endMarker.range.first).trimEnd()
    }

    val paragraphs = body.split(DOUBLE_NEWLINE_SPLIT_REGEX)
    val result = StringBuilder()

    paragraphs.forEach { paragraph ->
        val trimmed = paragraph.trim()
        if (trimmed.isBlank()) return@forEach

        val lines = trimmed.split('\n').map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return@forEach

        if (result.isNotEmpty()) {
            result.append("\n\n")
        }

        val isList = lines.all { it.startsWith("-") || it.startsWith("*") || it.startsWith("•") || LIST_NUMBERED_REGEX.containsMatchIn(it) }
        val isShortLinesPoetry = lines.size >= 3 && lines.all { it.length < 45 }
        val isExplicitHeading = lines.size == 1 && (
            lines[0].startsWith("#") ||
            CLASSIC_HEADING_REGEX.matches(lines[0]) ||
            (lines[0].length in 3..60 && lines[0].filter { it.isLetter() }.all { it.isUpperCase() })
        )

        if (isList || isShortLinesPoetry || isExplicitHeading) {
            result.append(lines.joinToString("\n"))
        } else {
            val unwrappedPara = StringBuilder()
            lines.forEach { line ->
                if (unwrappedPara.isEmpty()) {
                    unwrappedPara.append(line)
                } else {
                    if (unwrappedPara.endsWith("-") && line.firstOrNull()?.isLowerCase() == true) {
                        unwrappedPara.deleteCharAt(unwrappedPara.length - 1)
                        unwrappedPara.append(line)
                    } else {
                        unwrappedPara.append(' ').append(line)
                    }
                }
            }
            result.append(unwrappedPara.toString())
        }
    }

    return result.toString()
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


