package com.veritas.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Html
import androidx.core.graphics.createBitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.yield
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor
import com.google.mlkit.vision.text.Text as MlText

data class PdfImportOptions(
    val startPage: Int? = null,
    val endPage: Int? = null,
    val cleanupRepeatedLines: Boolean = true,
    val removePageNumbers: Boolean = true,
    val repairHyphenation: Boolean = true,
    val includePageMarkers: Boolean = false,
    val forceOcr: Boolean = false,
    val preferOcrWhenLowText: Boolean = true,
    val extractionMode: String = "HTML with images",
    val removeTopPageNoise: Boolean = true,
    val removeBottomPageNoise: Boolean = true,
    val manualCropBeforeExtract: Boolean = false,
    val minWordGap: String = "0.1",
    val separateWordsOnFontChange: Boolean = true,
    val markPdfLinesForCanvas: Boolean = true,
    val forceFreshExtraction: Boolean = false,
    val cropRect: RectF? = null
) {
    fun normalized(pageCount: Int): PdfImportOptions {
        val safePageCount = pageCount.coerceAtLeast(1)
        val safeStart = (startPage ?: 1).coerceIn(1, safePageCount)
        val safeEnd = (endPage ?: safePageCount).coerceIn(safeStart, safePageCount)
        return copy(startPage = safeStart, endPage = safeEnd)
    }
}

object DocumentExtractor {
    const val DEFAULT_IMPORT_TIMEOUT_MS = 60_000L

    fun defaultPdfMemorySetting(context: Context): MemoryUsageSetting {
        val maxMemory = Runtime.getRuntime().maxMemory()
        // Allocate up to 25% of available JVM heap, clamped between 32MB and 128MB.
        // This ensures typical PDFs stay 100% in RAM with zero disk swapping,
        // while safely spilling giant or image-heavy PDFs to disk temp storage.
        val mainMemoryBytes = (maxMemory / 4).coerceIn(32L * 1024 * 1024, 128L * 1024 * 1024)
        return MemoryUsageSetting.setupMixed(mainMemoryBytes).apply {
            setTempDir(java.io.File(context.cacheDir, "pdfbox_temp").apply { mkdirs() })
        }
    }

    fun getPdfPageCount(context: Context, uri: Uri): Int {
        return runCatching {
            PDFBoxResourceLoader.init(context.applicationContext)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                PDDocument.load(stream, defaultPdfMemorySetting(context)).use { document ->
                    document.numberOfPages
                }
            } ?: 0
        }.getOrDefault(0)
    }

    data class ManagedPdfDocument(
        val document: PDDocument,
        val pageCount: Int,
        private val tempFile: java.io.File? = null
    ) : java.io.Closeable {
        override fun close() {
            try {
                document.close()
            } finally {
                runCatching { tempFile?.delete() }
            }
        }
    }

    /**
     * Opens a PDF document and returns the ManagedPdfDocument (which holds the PDDocument,
     * total page count, and backing temporary file).
     * Caller MUST close the result via managedPdf.close() in a finally block.
     */
    fun openPdfDocument(context: Context, uri: Uri): ManagedPdfDocument {
        PDFBoxResourceLoader.init(context.applicationContext)
        val tempFile = java.io.File(context.cacheDir, "temp_pdf_load_${System.currentTimeMillis()}.pdf")
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                tempFile.outputStream().use { out ->
                    stream.copyTo(out)
                }
            } ?: throw IllegalStateException("Cannot open PDF URI")

            val document = try {
                PDDocument.load(tempFile, defaultPdfMemorySetting(context))
            } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
                throw IllegalArgumentException("This PDF is password-protected. Please remove password protection before importing.", e)
            } catch (e: java.io.IOException) {
                if (e.message?.contains("password", ignoreCase = true) == true || e.message?.contains("encrypted", ignoreCase = true) == true) {
                    throw IllegalArgumentException("This PDF is password-protected or encrypted. Please remove password protection before importing.", e)
                }
                throw e
            }
            return ManagedPdfDocument(document, document.numberOfPages, tempFile)
        } catch (e: Exception) {
            runCatching { tempFile.delete() }
            throw e
        }
    }

    /**
     * Applies a crop rect to all pages of an already-open PDDocument.
     * Call once before chunked extraction begins.
     */
    fun applyCropRect(document: PDDocument, cropRect: RectF) {
        for (page in document.pages) {
            val mediaBox = page.mediaBox
            val cropX = mediaBox.lowerLeftX + cropRect.left * mediaBox.width
            val cropY = mediaBox.lowerLeftY + (1f - cropRect.bottom) * mediaBox.height
            val cropW = (cropRect.right - cropRect.left) * mediaBox.width
            val cropH = (cropRect.bottom - cropRect.top) * mediaBox.height
            page.cropBox = com.tom_roush.pdfbox.pdmodel.common.PDRectangle(cropX, cropY, cropW, cropH)
        }
    }

    /**
     * Extracts text from a chunk of pages in an already-open PDDocument.
     * Does NOT open or close the document.
     */
    suspend fun extractPdfChunk(
        context: Context,
        uri: Uri,
        document: PDDocument,
        totalPageCount: Int,
        displayName: String,
        options: PdfImportOptions
    ): ExtractedImport {
        coroutineContext.ensureActive()
        val normalizedOptions = options.normalized(totalPageCount)
        val startPage = normalizedOptions.startPage ?: 1
        val endPage = normalizedOptions.endPage ?: totalPageCount
        val selectedPageCount = (endPage - startPage + 1).coerceAtLeast(1)
        val diagnostics = mutableListOf<String>()

        val internalPdfTitle = runCatching { document.documentInformation?.title }.getOrNull()?.trim()
        val resolvedTitle = if (displayName.isNotBlank() && displayName != "Imported document" && displayName != "Imported Book" && !displayName.startsWith("Fetching Resource", ignoreCase = true)) {
            displayName
        } else if (!internalPdfTitle.isNullOrBlank() && !internalPdfTitle.equals("untitled", ignoreCase = true) && !internalPdfTitle.startsWith("Fetching Resource", ignoreCase = true)) {
            cleanDocumentTitle(internalPdfTitle)
        } else {
            displayName.ifBlank { "Imported document" }
        }

        if (normalizedOptions.forceOcr) {
            diagnostics.add("Forced OCR mode was used for this PDF import.")
            val ocr = extractPdfOcr(context, uri, normalizedOptions, null)
            val text = ocr.text.normalizeExtractedText().smartFormatPdfContent().let { MathText.beautify(it) }
            return ExtractedImport(
                title = resolvedTitle,
                text = text,
                sourceLabel = "PDF",
                note = (diagnostics + ocr.diagnostics).joinToString("\n").ifBlank { null },
                pageCount = selectedPageCount,
                partial = ocr.partial
            )
        }

        val pageNumbers = mutableListOf<Int>()
        val pageTexts = mutableListOf<String>()

        for (pageNumber in startPage..endPage) {
            coroutineContext.ensureActive()
            pageNumbers.add(pageNumber)
            val pageText = PdfPageTextExtractor.extractPage(document, pageNumber)
            pageTexts.add(pageText)
            yield()
        }

        yield()
        val cleaned = PdfTextCleaner.cleanPages(pageTexts, pageNumbers, normalizedOptions)

        if (normalizedOptions.cleanupRepeatedLines && cleaned.removedRepeatedLineCount > 0) {
            diagnostics.add("Removed ${cleaned.removedRepeatedLineCount} repeated header/footer line${if (cleaned.removedRepeatedLineCount == 1) "" else "s"}.")
        }
        if (normalizedOptions.removePageNumbers && cleaned.removedPageNumberCount > 0) {
            diagnostics.add("Removed ${cleaned.removedPageNumberCount} standalone page number${if (cleaned.removedPageNumberCount == 1) "" else "s"}.")
        }
        if (normalizedOptions.repairHyphenation && cleaned.joinedHyphenationCount > 0) {
            diagnostics.add("Joined ${cleaned.joinedHyphenationCount} hyphenated line break${if (cleaned.joinedHyphenationCount == 1) "" else "s"}.")
        }

        val extractedPageCount = pageTexts.size.coerceAtLeast(1)
        val averageCharsPerPage = cleaned.text.length / extractedPageCount
        if (normalizedOptions.preferOcrWhenLowText && (cleaned.text.isBlank() || averageCharsPerPage < 80)) {
            diagnostics.add("Very little extractable PDF text was found, so OCR was attempted.")
            val ocr = extractPdfOcr(context, uri, normalizedOptions, null)
            if (ocr.text.isNotBlank()) {
                val text = ocr.text.normalizeExtractedText().smartFormatPdfContent().let { MathText.beautify(it) }
                return ExtractedImport(
                    title = resolvedTitle,
                    text = text,
                    sourceLabel = "PDF",
                    note = (diagnostics + ocr.diagnostics).joinToString("\n").ifBlank { null },
                    pageCount = selectedPageCount,
                    partial = ocr.partial
                )
            }
            diagnostics.add("OCR did not find readable text.")
        }

        val text = cleaned.text.normalizeExtractedText().smartFormatPdfContent().let { MathText.beautify(it) }
        return ExtractedImport(
            title = resolvedTitle,
            text = text,
            sourceLabel = "PDF",
            note = diagnostics.joinToString("\n").ifBlank { null },
            pageCount = selectedPageCount
        )
    }

    suspend fun extract(
        context: Context,
        uri: Uri,
        displayName: String,
        pdfOptions: PdfImportOptions = PdfImportOptions(),
        textOptions: TextImportOptions = TextImportOptions(),
        pptxOptions: PptxImportOptions = PptxImportOptions(),
        foregroundBudgetMillis: Long? = DEFAULT_IMPORT_TIMEOUT_MS
    ): ExtractedImport {
        coroutineContext.ensureActive()
        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.getDefault())
        val mimeType = context.contentResolver.getType(uri).orEmpty().lowercase(Locale.getDefault())

        val sourceLabel = when {
            mimeType.contains("pdf") || extension == "pdf" || uri.path?.lowercase(Locale.getDefault())?.endsWith(".pdf") == true -> "PDF"
            mimeType.contains("wordprocessingml") || extension == "docx" || uri.path?.lowercase(Locale.getDefault())?.endsWith(".docx") == true -> "DOCX"
            mimeType.contains("presentationml") || extension == "pptx" || uri.path?.lowercase(Locale.getDefault())?.endsWith(".pptx") == true -> "PPTX"
            extension == "ppt" || mimeType.contains("ms-powerpoint") -> "PPT"
            mimeType.contains("epub") || extension == "epub" || uri.path?.lowercase(Locale.getDefault())?.endsWith(".epub") == true -> "EPUB"
            mimeType.startsWith("image/") || extension in imageExtensions || imageExtensions.any { uri.path?.lowercase(Locale.getDefault())?.endsWith(".$it") == true } -> "OCR"
            else -> "TXT"
        }

        val extracted = when (sourceLabel) {
            "OCR" -> extractImageOcr(context, uri)
            "PDF" -> extractPdf(context, uri, pdfOptions, foregroundBudgetMillis)
            "DOCX" -> extractDocx(readAllBytes(context, uri))
            "PPTX" -> extractPptx(readAllBytes(context, uri), pptxOptions, foregroundBudgetMillis)
            "PPT" -> PptLegacyExtractor.extract(readAllBytes(context, uri))
            "EPUB" -> extractEpub(readAllBytes(context, uri))
            else -> extractPlainText(context, uri, textOptions, isHtmlish(displayName) || mimeType.contains("html"))
        }

        val text = extracted.text.normalizeExtractedText()
            .let { if (sourceLabel == "PDF") it.smartFormatPdfContent() else it }
            // Prettify equations once, at extraction, so every downstream consumer
            // (reader display, selection offsets, TTS) sees the same Unicode math.
            .let { MathText.beautify(it) }

        val baseNote = when (sourceLabel) {
            "PDF" -> "PDF text was extracted with the current import options. If very little text was found, OCR may have been attempted depending on your settings."
            "DOCX" -> "DOCX body text was extracted. Images, footnotes, comments, and advanced layout are not fully modeled yet."
            "PPTX" -> buildString {
                append("PowerPoint slide text was extracted (titles, bullets, tables")
                if (pptxOptions.includeSpeakerNotes) append(", speaker notes")
                append("). Charts, SmartArt, and slide design are not included")
                if (pptxOptions.ocrSlideImages) append("; text found inside slide images was read with OCR")
                append(". Use Open original for the visual deck.")
            }
            "EPUB" -> "EPUB spine text was extracted. DRM-protected books are not supported."
            "OCR" -> "OCR extracted text from this image-based file."
            else -> null
        }

        val note = buildList {
            baseNote?.let { add(it) }
            addAll(extracted.diagnostics)
        }.joinToString("\n").ifBlank { null }

        return ExtractedImport(
            title = displayName.ifBlank { "Imported document" },
            text = text,
            sourceLabel = sourceLabel,
            note = note,
            pageCount = extracted.pageCount,
            partial = extracted.partial
        )
    }

    private suspend fun extractImageOcr(context: Context, uri: Uri): ExtractionBody {
        val image = InputImage.fromFilePath(context.applicationContext, uri)
        val recognized = recognizeText(image)
        val text = formatRecognizedText(recognized)
        val diagnostics = buildList {
            add("OCR completed using on-device Latin-script text recognition.")
            add("Detected ${recognized.textBlocks.size} text block${if (recognized.textBlocks.size == 1) "" else "s"}.")
            if (text.length < 40) {
                add("Only a small amount of text was detected. Try a clearer, straighter, higher-resolution image if the result looks incomplete.")
            }
        }
        return ExtractionBody(text, diagnostics, pageCount = 1)
    }

    private suspend fun extractPdfOcr(
        context: Context,
        uri: Uri,
        options: PdfImportOptions = PdfImportOptions(),
        deadlineMillis: Long? = null
    ): ExtractionBody = kotlinx.coroutines.coroutineScope {
        val diagnostics = mutableListOf<String>()
        val output = StringBuilder()
        var partial = false
        var requestedPagesForResult = 0
        val descriptor = if (uri.scheme == "file") {
            try {
                val filePath = uri.path
                    ?: return@coroutineScope ExtractionBody("", listOf("Could not open PDF pages for OCR rendering."))
                android.os.ParcelFileDescriptor.open(java.io.File(filePath), android.os.ParcelFileDescriptor.MODE_READ_ONLY)
            } catch (e: Exception) {
                context.contentResolver.openFileDescriptor(uri, "r")
            }
        } else {
            context.contentResolver.openFileDescriptor(uri, "r")
        } ?: return@coroutineScope ExtractionBody("", listOf("Could not open PDF pages for OCR rendering."))

        descriptor.use { pfd ->
            val renderer = try {
                PdfRenderer(pfd)
            } catch (e: SecurityException) {
                return@coroutineScope ExtractionBody("", listOf("This PDF is password-protected or encrypted. Please remove password protection before running OCR."))
            } catch (e: Exception) {
                return@coroutineScope ExtractionBody("", listOf("Could not initialize PDF renderer for OCR: ${e.localizedMessage ?: "Unknown error"}"))
            }
            try {
                val normalizedOptions = options.normalized(renderer.pageCount)
                val startPage = normalizedOptions.startPage ?: 1
                val endPage = normalizedOptions.endPage ?: renderer.pageCount
                val requestedPages = (endPage - startPage + 1).coerceAtLeast(1)
                requestedPagesForResult = requestedPages
                val pagesToProcess = requestedPages.coerceAtMost(MAX_PDF_OCR_PAGES)
                if (requestedPages > MAX_PDF_OCR_PAGES) {
                    diagnostics.add("OCR was limited to $MAX_PDF_OCR_PAGES page${if (MAX_PDF_OCR_PAGES == 1) "" else "s"} from the selected range to protect memory and keep the app responsive.")
                    partial = true
                }

                val cacheKey = "ocr_" + uri.toString().hashCode().toString()
                val cacheDir = java.io.File(context.cacheDir, cacheKey)
                cacheDir.mkdirs()

                val ocrSemaphore = kotlinx.coroutines.sync.Semaphore(3)

                val deferredResults = (0 until pagesToProcess).map { offset ->
                    coroutineContext.ensureActive()
                    val pageIndex = startPage - 1 + offset
                    val cacheFile = java.io.File(cacheDir, "page_${pageIndex}.txt")
                    
                    if (cacheFile.exists()) {
                        kotlinx.coroutines.CompletableDeferred(pageIndex to cacheFile.readText(Charsets.UTF_8))
                    } else {
                        val page = renderer.openPage(pageIndex)
                        val bitmap = try {
                            renderPdfPage(page)
                        } finally {
                            page.close()
                        }
                        
                        val croppedBitmap = if (options.cropRect != null) {
                            val r = options.cropRect
                            val left = (r.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
                            val top = (r.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
                            val right = (r.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
                            val bottom = (r.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
                            Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top).also {
                                bitmap.recycle()
                            }
                        } else {
                            bitmap
                        }

                        this@coroutineScope.async(kotlinx.coroutines.Dispatchers.Default) {
                            ocrSemaphore.withPermit {
                                try {
                                    coroutineContext.ensureActive()
                                    if (!hasImportTimeRemaining(deadlineMillis)) {
                                        return@withPermit pageIndex to ""
                                    }
                                    val recognized = recognizeText(InputImage.fromBitmap(croppedBitmap, 0))
                                    val formatted = formatRecognizedText(recognized)
                                    runCatching { cacheFile.writeText(formatted, Charsets.UTF_8) }
                                    pageIndex to formatted
                                } finally {
                                    croppedBitmap.recycle()
                                }
                            }
                        }
                    }
                }

                val results = deferredResults.awaitAll()
                var droppedPageCount = 0
                results.forEach { (pageIndex, pageText) ->
                    if (pageText.isNotBlank()) {
                        if (output.isNotBlank()) output.append("\n\n")
                        output.append("Page ${pageIndex + 1}\n")
                        output.append(pageText)
                    } else {
                        val cacheFile = java.io.File(cacheDir, "page_${pageIndex}.txt")
                        if (!cacheFile.exists()) {
                            partial = true
                            droppedPageCount++
                        }
                    }
                }

                if (partial && !hasImportTimeRemaining(deadlineMillis)) {
                    diagnostics.add(
                        "$droppedPageCount page${if (droppedPageCount == 1) "" else "s"} ran out of time during OCR and will finish in the background, or can be re-imported with a smaller page range."
                    )
                }

                if (!partial) {
                    runCatching { cacheDir.deleteRecursively() }
                }
            } finally {
                renderer.close()
            }
        }

        if (output.isNotBlank()) {
            diagnostics.add("OCR extracted text from rendered PDF pages.")
        }
        ExtractionBody(output.toString(), diagnostics, pageCount = requestedPagesForResult, partial = partial)
    }

    private fun hasImportTimeRemaining(deadlineMillis: Long?): Boolean =
        deadlineMillis == null || System.currentTimeMillis() <= deadlineMillis

    private fun renderPdfPage(page: PdfRenderer.Page): Bitmap {
        val scale = (OCR_RENDER_TARGET_WIDTH.toFloat() / page.width.toFloat()).coerceIn(1.0f, 3.0f)
        val width = (page.width * scale).roundToInt().coerceAtLeast(1)
        val height = (page.height * scale).roundToInt().coerceAtLeast(1)
        val bitmap = createBitmap(width, height)
        Canvas(bitmap).drawColor(AndroidColor.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    private suspend fun recognizeText(image: InputImage): MlText {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            recognizer.process(image).awaitResult()
        } finally {
            recognizer.close()
        }
    }

    private fun formatRecognizedText(result: MlText): String {
        return result.textBlocks
            .sortedWith(compareBy<MlText.TextBlock> { it.boundingBox?.top ?: 0 }.thenBy { it.boundingBox?.left ?: 0 })
            .joinToString("\n\n") { block ->
                block.lines
                    .sortedWith(compareBy<MlText.Line> { it.boundingBox?.top ?: 0 }.thenBy { it.boundingBox?.left ?: 0 })
                    .joinToString("\n") { it.text }
            }
            .normalizeExtractedText()
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) continuation.resume(result)
        }
        addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
        addOnCanceledListener {
            if (continuation.isActive) continuation.resumeWithException(CancellationException("ML Kit OCR task was cancelled."))
        }
    }

    private val PARAGRAPH_SPLIT_REGEX = Regex("""\n\s*\n+""")
    private val NUMBERED_LIST_ITEM_REGEX = Regex("""^\d+\.""")

    internal fun normalizePlainTextParagraphs(raw: String): String {
        val clean = raw.replace("\r\n", "\n").replace('\r', '\n')
        val blocks = clean.split(PARAGRAPH_SPLIT_REGEX)
        return blocks.mapNotNull { block ->
            val rawLines = block.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (rawLines.isEmpty()) return@mapNotNull null
            val lines = rawLines

            val isTable = lines.all { it.startsWith("|") && it.endsWith("|") }
            val isList = lines.all { it.startsWith("•") || it.startsWith("-") || it.startsWith("*") || NUMBERED_LIST_ITEM_REGEX.containsMatchIn(it) }

            if (isTable || isList) {
                lines.joinToString("\n")
            } else {
                val sb = StringBuilder()
                lines.forEachIndexed { idx, line ->
                    if (idx == 0) {
                        sb.append(line)
                    } else if (sb.endsWith("-") && line.firstOrNull()?.isLowerCase() == true) {
                        sb.deleteCharAt(sb.length - 1)
                        sb.append(line)
                    } else {
                        sb.append(" ").append(line)
                    }
                }
                sb.toString()
            }
        }.joinToString("\n\n").trim()
    }

    private fun extractPlainText(
        context: Context,
        uri: Uri,
        options: TextImportOptions,
        htmlish: Boolean
    ): ExtractionBody {
        val bytes = readAllBytes(context, uri)
        if (bytes.isEmpty()) return ExtractionBody("")
        val decoded = TextImportDecoder.decode(bytes, options)
        val text = if (htmlish) htmlishToText(decoded.text) else normalizePlainTextParagraphs(decoded.text)
        return ExtractionBody(text, decoded.diagnostics)
    }

    private suspend fun extractPdf(
        context: Context,
        uri: Uri,
        options: PdfImportOptions = PdfImportOptions(),
        foregroundBudgetMillis: Long? = DEFAULT_IMPORT_TIMEOUT_MS
    ): ExtractionBody {
        PDFBoxResourceLoader.init(context.applicationContext)
        context.contentResolver.openInputStream(uri).use { stream ->
            if (stream == null) return ExtractionBody("")
            val document = try {
                PDDocument.load(stream, defaultPdfMemorySetting(context))
            } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
                throw IllegalArgumentException("This PDF is password-protected. Please remove password protection before importing.", e)
            } catch (e: java.io.IOException) {
                if (e.message?.contains("password", ignoreCase = true) == true || e.message?.contains("encrypted", ignoreCase = true) == true) {
                    throw IllegalArgumentException("This PDF is password-protected or encrypted. Please remove password protection before importing.", e)
                }
                throw e
            }
            return try {
                val pageCount = document.numberOfPages.coerceAtLeast(1)
                val normalizedOptions = options.normalized(pageCount)
                val startPage = normalizedOptions.startPage ?: 1
                val endPage = normalizedOptions.endPage ?: pageCount
                val selectedPageCount = (endPage - startPage + 1).coerceAtLeast(1)
                val diagnostics = mutableListOf<String>()

                if (startPage != 1 || endPage != pageCount) {
                    diagnostics.add("Imported selected PDF page range: $startPage–$endPage of $pageCount pages.")
                }
                if (normalizedOptions.extractionMode.isNotBlank()) {
                    diagnostics.add("Import mode: ${normalizedOptions.extractionMode}.")
                }
                if (normalizedOptions.markPdfLinesForCanvas) {
                    diagnostics.add("Canvas line marks are enabled for comparing extracted text with the original PDF view.")
                }
                if (normalizedOptions.cropRect != null) {
                    diagnostics.add("Manual crop applied to the extraction area.")
                    val r = normalizedOptions.cropRect
                    for (page in document.pages) {
                        val mediaBox = page.mediaBox
                        val cropX = mediaBox.lowerLeftX + r.left * mediaBox.width
                        val cropY = mediaBox.lowerLeftY + (1f - r.bottom) * mediaBox.height
                        val cropW = (r.right - r.left) * mediaBox.width
                        val cropH = (r.bottom - r.top) * mediaBox.height
                        page.cropBox = com.tom_roush.pdfbox.pdmodel.common.PDRectangle(cropX, cropY, cropW, cropH)
                    }
                }
                if (normalizedOptions.forceFreshExtraction) {
                    diagnostics.add("Fresh extraction was requested for this import.")
                }

                val deadlineMillis = foregroundBudgetMillis?.let { System.currentTimeMillis() + it }

                if (normalizedOptions.forceOcr) {
                    diagnostics.add("Forced OCR mode was used for this PDF import.")
                    val ocr = extractPdfOcr(context, uri, normalizedOptions, deadlineMillis)
                    return ExtractionBody(
                        text = ocr.text,
                        diagnostics = diagnostics + ocr.diagnostics,
                        pageCount = selectedPageCount,
                        partial = ocr.partial
                    )
                }

                val pageNumbers = mutableListOf<Int>()
                val pageTexts = mutableListOf<String>()
                var partial = false

                for (pageNumber in startPage..endPage) {
                    coroutineContext.ensureActive()
                    if (deadlineMillis != null && pageTexts.isNotEmpty() && System.currentTimeMillis() >= deadlineMillis) {
                        partial = true
                        break
                    }
                    pageNumbers.add(pageNumber)
                    val pageText = PdfPageTextExtractor.extractPage(document, pageNumber)
                    pageTexts.add(pageText)
                    yield()
                }

                yield()
                val cleaned = PdfTextCleaner.cleanPages(pageTexts, pageNumbers, normalizedOptions)
                if (partial) {
                    diagnostics.add("Opened ${pageTexts.size} of $selectedPageCount selected PDF pages after the foreground import window. Vern will continue extracting the rest in the background.")
                }
                if (normalizedOptions.cleanupRepeatedLines && cleaned.removedRepeatedLineCount > 0) {
                    diagnostics.add("Removed ${cleaned.removedRepeatedLineCount} repeated header/footer line${if (cleaned.removedRepeatedLineCount == 1) "" else "s"}.")
                }
                if (normalizedOptions.removePageNumbers && cleaned.removedPageNumberCount > 0) {
                    diagnostics.add("Removed ${cleaned.removedPageNumberCount} standalone page number${if (cleaned.removedPageNumberCount == 1) "" else "s"}.")
                }
                if (normalizedOptions.repairHyphenation && cleaned.joinedHyphenationCount > 0) {
                    diagnostics.add("Joined ${cleaned.joinedHyphenationCount} hyphenated line break${if (cleaned.joinedHyphenationCount == 1) "" else "s"}.")
                }
                if (normalizedOptions.includePageMarkers) {
                    diagnostics.add("Inserted page markers into the imported text.")
                }

                val extractedPageCount = pageTexts.size.coerceAtLeast(1)
                val averageCharsPerPage = cleaned.text.length / extractedPageCount
                if (!partial && normalizedOptions.preferOcrWhenLowText && (cleaned.text.isBlank() || averageCharsPerPage < 80)) {
                    diagnostics.add("Very little extractable PDF text was found in the selected range, so OCR was attempted on rendered PDF pages.")
                    val ocr = extractPdfOcr(context, uri, normalizedOptions, deadlineMillis)
                    if (ocr.text.isNotBlank()) {
                        return ExtractionBody(
                            text = ocr.text,
                            diagnostics = diagnostics + ocr.diagnostics,
                            pageCount = selectedPageCount,
                            partial = ocr.partial
                        )
                    }
                    diagnostics.add("OCR did not find readable text. The PDF may be low-resolution, handwritten, encrypted, or image quality may be too poor.")
                }
                ExtractionBody(cleaned.text, diagnostics, pageCount = selectedPageCount, partial = partial)
            } finally {
                document.close()
            }
        }
    }

    /**
     * DOCX text comes from [DocxDocumentParser] rather than a second XML walk of its own.
     * The original-document view already renders that parser's pages, so deriving the
     * reading text from the same parse is what makes a page marker mean the same page in
     * both views. Word stores no page boundaries in document.xml — the parser synthesises
     * them by block weight — so these numbers are arbitrary but, crucially, identical on
     * both sides.
     */
    internal fun extractDocx(bytes: ByteArray): ExtractionBody {
        val parsed = DocxDocumentParser.parse(bytes, "", includeImages = false)
        val output = StringBuilder()
        parsed.pages.forEach { page ->
            var imageCounter = 0
            val rendered = page.blocks.mapNotNull { block ->
                docxBlockToText(block) { imageCounter++ }
            }.joinToString("\n\n").trim()
            if (rendered.isNotBlank()) {
                if (output.isNotBlank()) output.append("\n\n")
                output.append(ReaderTextIndex.pageMarker(page.pageNumber)).append('\n')
                output.append(rendered)
            }
        }
        return ExtractionBody(output.toString(), pageCount = parsed.totalPages)
    }

    private fun docxBlockToText(block: DocxBlock, nextImageIndex: () -> Int = { 0 }): String? = when (block) {
        is DocxBlock.Heading -> {
            val prefix = "#".repeat(block.level.coerceIn(1, 4))
            "$prefix ${block.text}".takeIf { block.text.isNotBlank() }
        }
        is DocxBlock.Paragraph -> block.text.takeIf { it.isNotBlank() }
        is DocxBlock.Bullet -> "• ${block.text}".takeIf { block.text.isNotBlank() }
        // Cells are joined with a comma rather than run together: the previous XML walk
        // emitted them with no separator at all, which the speech engine read as one
        // long compound word.
        is DocxBlock.Table -> {
            val validRows = block.rows.filter { row -> row.any { it.isNotBlank() } }
            if (validRows.isEmpty()) null
            else {
                val colCount = validRows.maxOf { it.size }
                val header = "| " + validRows.first().joinToString(" | ") { it.trim() } + " |"
                val separator = "| " + List(colCount) { "---" }.joinToString(" | ") + " |"
                val body = validRows.drop(1).map { row ->
                    val padded = if (row.size < colCount) row + List(colCount - row.size) { "" } else row
                    "| " + padded.joinToString(" | ") { it.trim() } + " |"
                }
                (listOf(header, separator) + body).joinToString("\n").takeIf { it.isNotBlank() }
            }
        }
        is DocxBlock.Image -> "[[VERITAS_IMAGE:${nextImageIndex()}]]"
    }

    private suspend fun extractPptx(
        bytes: ByteArray,
        options: PptxImportOptions,
        foregroundBudgetMillis: Long?
    ): ExtractionBody {
        val deck = PptxExtractor.parseDeck(bytes, includeSpeakerNotes = options.includeSpeakerNotes)
        if (deck.slides.isEmpty()) {
            return ExtractionBody("", listOf("No slides were found in this presentation."))
        }
        val diagnostics = mutableListOf<String>()
        var partial = false

        val ocrLinesBySlide = if (options.ocrSlideImages) {
            val deadlineMillis = foregroundBudgetMillis?.let { System.currentTimeMillis() + it }
            // One second pass over the zip pulls only the images slides reference.
            val wantedPaths = deck.slides.flatMap { it.mediaPaths }
                .distinct()
                .take(MAX_PPTX_OCR_IMAGES)
                .toSet()
            val mediaBytes = mutableMapOf<String, ByteArray>()
            if (wantedPaths.isNotEmpty()) {
                ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name.trimStart('/')
                        if (!entry.isDirectory && name in wantedPaths) {
                            mediaBytes[name] = zip.readBytes()
                            if (mediaBytes.size == wantedPaths.size) break
                        }
                    }
                }
            }
            val ocrByPath = mutableMapOf<String, List<String>>()
            for ((path, imageBytes) in mediaBytes) {
                coroutineContext.ensureActive()
                if (!hasImportTimeRemaining(deadlineMillis)) {
                    partial = true
                    diagnostics.add("Some slide images ran out of time during OCR; re-import to retry.")
                    break
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
                // Decoration filter: logos, icons, and dividers aren't worth reading.
                if (minOf(bounds.outWidth, bounds.outHeight) < MIN_PPTX_OCR_IMAGE_DIMENSION) continue
                val sampled = BitmapFactory.Options().apply {
                    inSampleSize = (bounds.outWidth / OCR_RENDER_TARGET_WIDTH).coerceAtLeast(1)
                }
                val bitmap = runCatching {
                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, sampled)
                }.getOrNull() ?: continue
                try {
                    val recognized = runCatching {
                        formatRecognizedText(recognizeText(InputImage.fromBitmap(bitmap, 0)))
                    }.getOrDefault("")
                    val lines = recognized.lines().map { it.trim() }.filter { it.isNotBlank() }
                    if (lines.isNotEmpty()) ocrByPath[path] = lines
                } finally {
                    bitmap.recycle()
                }
            }
            if (ocrByPath.isNotEmpty()) {
                diagnostics.add("OCR read text from ${ocrByPath.size} slide image${if (ocrByPath.size == 1) "" else "s"}.")
            }
            deck.slides.associate { slide ->
                slide.number to slide.mediaPaths.flatMap { ocrByPath[it].orEmpty() }
            }
        } else {
            emptyMap()
        }

        val text = PptxExtractor.renderDeckText(
            deck = deck,
            ocrLinesBySlide = ocrLinesBySlide,
            autoPunctuate = options.autoPunctuate
        )
        return ExtractionBody(
            text = text,
            diagnostics = diagnostics,
            pageCount = deck.slideCount,
            partial = partial
        )
    }

    /**
     * EPUB text comes from [EpubDocumentParser] rather than a second spine walk of its own,
     * so a `[[VERITAS_PAGE:n]]` marker refers to the same chapter the original-document view
     * is showing. The parser numbers chapters contiguously from 1 and only counts chapters
     * that actually produced paragraphs, so marker numbers and chapter indices cannot drift.
     */
    internal fun extractEpub(bytes: ByteArray): ExtractionBody {
        val book = EpubDocumentParser.parse(bytes, "", includeImages = false)
        val output = StringBuilder()
        book.chapters.forEach { chapter ->
            // extractChapterContent already drops the title from the paragraph list, so
            // leading with it here restores the heading exactly once.
            val body = buildList {
                if (chapter.title.isNotBlank()) {
                    val heading = if (chapter.title.startsWith("#")) chapter.title else "# ${chapter.title}"
                    add(heading)
                }
                addAll(chapter.paragraphs)
            }.joinToString("\n\n").trim()
            if (body.isNotBlank()) {
                if (output.isNotBlank()) output.append("\n\n")
                output.append(ReaderTextIndex.pageMarker(chapter.number)).append('\n')
                output.append(body)
            }
        }
        return ExtractionBody(output.toString(), pageCount = book.totalChapters)
    }

    private fun isHtmlish(path: String): Boolean {
        val lower = path.lowercase(Locale.getDefault())
        return lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")
    }

    private fun convertHtmlTablesToMarkdown(html: String): String {
        val tableRegex = Regex("(?is)<table\\b[^>]*>(.*?)</table>")
        return tableRegex.replace(html) { tableMatch ->
            val tableContent = tableMatch.groupValues[1]
            val trRegex = Regex("(?is)<tr\\b[^>]*>(.*?)</tr>")
            val rows = mutableListOf<List<String>>()
            trRegex.findAll(tableContent).forEach { trMatch ->
                val trContent = trMatch.groupValues[1]
                val cellRegex = Regex("(?is)<(td|th)\\b[^>]*>(.*?)</\\1>")
                val cells = cellRegex.findAll(trContent).map { cellMatch ->
                    val rawCell = cellMatch.groupValues[2]
                        .replace(Regex("<[^>]+>"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    decodeHtmlEntities(rawCell)
                }.toList()
                if (cells.isNotEmpty() && cells.any { it.isNotBlank() }) {
                    rows.add(cells)
                }
            }
            if (rows.isEmpty()) ""
            else {
                val colCount = rows.maxOf { it.size }
                val header = "| " + rows.first().joinToString(" | ") + " |"
                val separator = "| " + List(colCount) { "---" }.joinToString(" | ") + " |"
                val body = rows.drop(1).map { row ->
                    val padded = if (row.size < colCount) row + List(colCount - row.size) { "" } else row
                    "| " + padded.joinToString(" | ") + " |"
                }
                "\n\n" + (listOf(header, separator) + body).joinToString("\n") + "\n\n"
            }
        }
    }

    private fun htmlishToText(html: String): String {
        val withTables = convertHtmlTablesToMarkdown(html)
        val withoutNoise = withTables
            .replace(Regex("(?is)<(script|style|svg|math)[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</(p|div|section|article|blockquote|li|h[1-6]|tr)>"), "\n\n")
            .replace(Regex("<[^>]+>"), " ")
        return decodeHtmlEntities(withoutNoise)
    }

    private fun decodeHtmlEntities(text: String): String {
        return runCatching {
            Html.fromHtml(text.replace("\n", "___NEWLINE___"), Html.FROM_HTML_MODE_LEGACY).toString()
                .replace("___NEWLINE___", "\n")
        }.getOrDefault(text)
    }

    private fun readAllBytes(context: Context, uri: Uri): ByteArray {
        context.contentResolver.openInputStream(uri).use { stream ->
            return stream?.readBytes() ?: ByteArray(0)
        }
    }

    private const val OCR_RENDER_TARGET_WIDTH = 1600
    private const val MAX_PDF_OCR_PAGES = 150 // Increased to support longer scanned documents
    private const val MAX_PPTX_OCR_IMAGES = 80
    private const val MIN_PPTX_OCR_IMAGE_DIMENSION = 200 // px; skips logos/icons/dividers
    private val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp", "tif", "tiff")
}

data class ExtractionBody(
    val text: String,
    val diagnostics: List<String> = emptyList(),
    val pageCount: Int = 0,
    val partial: Boolean = false
)

private val MULTI_SPACE_3_REGEX = Regex(" {3,}")
private val MULTI_NEWLINE_4_REGEX = Regex("\n{4,}")
private val MULTI_NEWLINE_3_REGEX = Regex("\n{3,}")
private val PDF_CHAPTER_OR_SECTION_REGEX = Regex("""^(CHAPTER|Chapter|PROLOGUE|Prologue|EPILOGUE|Epilogue|INTRODUCTION|Introduction|PREFACE|Preface|PART|Part|BOOK|Book|SECTION|Section|ACT|Act|SCENE|Scene)\b.*""", RegexOption.IGNORE_CASE)
private val PDF_NUMBERED_HEADING_REGEX = Regex("""^\d+(\.\d+)*\s+[A-Z0-9].*""")
private val EPOCH_PREFIX_REGEX = Regex("""^\d{6,}[_\-\s.]+""")
private val OCEAN_PDF_WRAPPED_REGEX = Regex("""(?i)[/_(\[]?OceanofPDF(\.com)?[/_\])]?""")
private val OCEAN_PDF_WORD_REGEX = Regex("""(?i)\bOceanofPDF(\.com)?\b""")
private val PDF_EPUB_BRACKET_REGEX = Regex("""(?i)\[(PDF|EPUB)\]""")
private val PDF_EPUB_PAREN_REGEX = Regex("""(?i)\((PDF|EPUB)\)""")
private val DOWNLOAD_WORD_REGEX = Regex("""(?i)\bDownload\b""")
private val COPY_COUNTER_REGEX = Regex("""\s*[_\-]?\(\d+\)$""")
private val COPY_DASH_REGEX = Regex("""\s*-\s*Copy$""", RegexOption.IGNORE_CASE)
private val MULTI_SPACE_2_REGEX = Regex("""\s{2,}""")

fun String.normalizeExtractedText(): String {
    return replace('\u00A0', ' ')
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')
        .joinToString("\n") { line ->
            val expanded = line.replace("\t", "    ")
            val leadingSpaces = expanded.takeWhile { it == ' ' }.length.coerceAtMost(16)
            val leadingIndent = " ".repeat(leadingSpaces)
            val body = expanded
                .drop(leadingSpaces)
                .replace(MULTI_SPACE_3_REGEX, "  ")
                .trimEnd()
            if (body.isBlank()) {
                ""
            } else {
                "$leadingIndent${body.trimStart()}"
            }
        }
        .replace(MULTI_NEWLINE_4_REGEX, "\n\n\n")
        .trim()
}

fun String.smartFormatPdfContent(): String {
    val lines = this.split('\n')
    val formatted = mutableListOf<String>()
    
    lines.forEachIndexed { index, line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            if (formatted.isNotEmpty() && formatted.last().isNotBlank()) {
                formatted.add("")
            }
            return@forEachIndexed
        }
        
        if (trimmed.startsWith("[[VERITAS_") || trimmed.contains("VERITAS_PAGE", ignoreCase = true) || trimmed.contains("veritas page", ignoreCase = true)) {
            formatted.add(trimmed)
            return@forEachIndexed
        }
        
        val isExplicitHeader = trimmed.startsWith("#")
        val prevLine = lines.getOrNull(index - 1)?.trim().orEmpty()
        val nextLine = lines.getOrNull(index + 1)?.trim().orEmpty()

        val prevEndsTerminal = prevLine.isEmpty() ||
            prevLine.startsWith("#") ||
            PDF_CHAPTER_OR_SECTION_REGEX.matches(prevLine) ||
            prevLine.lastOrNull() in listOf('.', '!', '?', ':', '"', '”', '’', '\'')
        val nextStartsLower = nextLine.firstOrNull()?.isLowerCase() == true

        val isMiddleOfSentence = !prevEndsTerminal || nextStartsLower

        val isExplicitChapterOrSection = PDF_CHAPTER_OR_SECTION_REGEX.matches(trimmed) ||
            PDF_NUMBERED_HEADING_REGEX.matches(trimmed)

        // Only convert to heading if it is an explicit chapter/section label,
        // or an isolated all-caps header surrounded by blank lines. Never convert normal lines based on title-casing words.
        val isIsolatedHeader = prevLine.isEmpty() && nextLine.isEmpty() && trimmed.length in 4..45 &&
            trimmed.filter { it.isLetter() }.length >= 4 && trimmed.filter { it.isLetter() }.all { it.isUpperCase() } &&
            !trimmed.endsWith(".") && !trimmed.endsWith(",") && !trimmed.endsWith(";")

        val isChapterOrTopic = !isMiddleOfSentence && (isExplicitChapterOrSection || isIsolatedHeader)
            
        if (isChapterOrTopic && !isExplicitHeader) {
            if (formatted.isNotEmpty() && formatted.last().isNotBlank()) {
                formatted.add("")
            }
            formatted.add("## $trimmed")
            formatted.add("")
        } else {
            formatted.add(line.trimEnd())
        }
    }
    
    return formatted.joinToString("\n")
        .replace(MULTI_NEWLINE_3_REGEX, "\n\n")
        .trim()
}

/**
 * Turns a raw file name into something worth showing as a title.
 *
 * Downloads and shares arrive as things like
 * "1741927936_Good_Vibes,_Good_Life_(Vex_King)_(1).pdf", and that string was
 * being used verbatim as the reading's title everywhere in the app.
 *
 * Drops the extension, a leading epoch/id prefix, a trailing copy counter, and
 * separator underscores/dots. Deliberately conservative: anything it cannot
 * confidently improve is returned unchanged, and it never returns blank.
 */
fun cleanDocumentTitle(fileName: String): String {
    val withoutExtension = fileName.trim().let { name ->
        val dot = name.lastIndexOf('.')
        // Only strip a plausible extension, not the dot in "Vol. 2".
        if (dot > 0 && name.length - dot in 2..6 && name.drop(dot + 1).all { it.isLetterOrDigit() }) {
            name.take(dot)
        } else {
            name
        }
    }

    var title = withoutExtension
        // "1741927936_Good_Vibes" / "20260802-notes" — a long digit run up front is
        // a timestamp or export id, never part of the title.
        .replace(EPOCH_PREFIX_REGEX, "")
        // OceanofPDF download prefixes/suffixes/tags with any wrapping slashes, brackets, or delimiters
        .replace(OCEAN_PDF_WRAPPED_REGEX, "")
        .replace(OCEAN_PDF_WORD_REGEX, "")
        .replace(PDF_EPUB_BRACKET_REGEX, "")
        .replace(PDF_EPUB_PAREN_REGEX, "")
        .replace(DOWNLOAD_WORD_REGEX, "")
        // "report (1)" / "report(2)" / "report - Copy" — download de-duplication.
        .replace(COPY_COUNTER_REGEX, "")
        .replace(COPY_DASH_REGEX, "")

    // Underscores are separators in file names but never in prose.
    if (title.contains('_')) title = title.replace('_', ' ')
    // Same for dot-separated names, but only when there are no spaces already.
    if (!title.contains(' ') && title.count { it == '.' } >= 2) title = title.replace('.', ' ')

    title = title.replace(MULTI_SPACE_2_REGEX, " ")
        .trim()
        .trim('-', '_', '.', ' ', '/', '\\', ':', '|', '•')

    if (title.equals("Fetching Resource", ignoreCase = true) ||
        title.equals("Fetching Resource...", ignoreCase = true) ||
        title.isBlank()) {
        return "Imported document"
    }

    return title
}

fun getDisplayName(context: Context, uri: Uri): String {
    val fallback = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported text"
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                // Cursor.getString is a platform type and IS null for some share
                // providers (crash report 2026-07-05: NPE in prepareImport when a
                // file was shared into Veritas). Never let that null escape.
                if (index >= 0) cursor.getString(index) ?: fallback else fallback
            } else {
                fallback
            }
        }
    }.getOrDefault(fallback)
}
