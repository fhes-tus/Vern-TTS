package com.veritas.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.core.graphics.createBitmap
import android.graphics.Color as AndroidColor

/**
 * Encapsulates rendered page output from PDF rasterization.
 */
internal data class RenderedPage(
    val pageCount: Int,
    val bitmap: Bitmap
)

/**
 * Detects whether the given document format is PDF.
 */
internal fun detectIsPdf(document: SavedDocument, repository: DocumentRepository, context: Context): Boolean {
    if (document.originalMimeType.contains("pdf", ignoreCase = true) ||
        document.originalFileName.endsWith(".pdf", ignoreCase = true) ||
        document.title.lowercase().contains(".pdf")
    ) {
        return true
    }
    val file = repository.originalFile(document)
    if (file != null && file.exists()) {
        return runCatching {
            file.inputStream().use { input ->
                val bytes = ByteArray(4)
                val read = input.read(bytes)
                read == 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()
            }
        }.getOrDefault(false)
    } else if (document.originalFileName.startsWith("content://")) {
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(document.originalFileName))?.use { input ->
                val bytes = ByteArray(4)
                val read = input.read(bytes)
                read == 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()
            } ?: false
        }.getOrDefault(false)
    }
    return false
}

/**
 * Detects whether the document is a standalone image format.
 */
internal fun detectIsImage(document: SavedDocument, repository: DocumentRepository, context: Context, isPdf: Boolean): Boolean {
    if (isPdf) return false
    if (document.originalMimeType.startsWith("image/") ||
        listOf(".jpg", ".jpeg", ".png", ".webp", ".bmp").any {
            document.originalFileName.endsWith(it, ignoreCase = true)
        }
    ) {
        return true
    }
    val file = repository.originalFile(document)
    if (file != null && file.exists()) {
        return runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.outWidth > 0 && options.outHeight > 0
        }.getOrDefault(false)
    } else if (document.originalFileName.startsWith("content://")) {
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(document.originalFileName))?.use { input ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, options)
                options.outWidth > 0 && options.outHeight > 0
            } ?: false
        }.getOrDefault(false)
    }
    return false
}

/**
 * Detects whether the document is a PowerPoint presentation.
 */
internal fun detectIsPresentation(document: SavedDocument, isPdf: Boolean, isImage: Boolean): Boolean {
    if (isPdf || isImage) return false
    return document.sourceLabel == "PPTX" || document.sourceLabel == "PPT" ||
            document.originalFileName.endsWith(".pptx", ignoreCase = true) ||
            document.originalFileName.endsWith(".ppt", ignoreCase = true) ||
            document.originalMimeType.contains("presentationml") ||
            document.originalMimeType.contains("powerpoint")
}

/**
 * Detects whether the document is an EPUB book.
 */
internal fun detectIsEpub(document: SavedDocument, isPdf: Boolean, isImage: Boolean, isPresentation: Boolean): Boolean {
    if (isPdf || isImage || isPresentation) return false
    return document.sourceLabel == "EPUB" ||
            document.originalFileName.endsWith(".epub", ignoreCase = true) ||
            document.originalMimeType.contains("epub")
}

/**
 * Detects whether the document is a Word DOCX document.
 */
internal fun detectIsDocx(document: SavedDocument, isPdf: Boolean, isImage: Boolean, isPresentation: Boolean, isEpub: Boolean): Boolean {
    if (isPdf || isImage || isPresentation || isEpub) return false
    return document.sourceLabel == "DOCX" ||
            document.originalFileName.endsWith(".docx", ignoreCase = true) ||
            document.originalMimeType.contains("wordprocessingml")
}

/**
 * Loads and parses a PowerPoint presentation deck from original storage.
 */
internal fun loadPresentationDeck(context: Context, original: Uri, document: SavedDocument): PptxDeck {
    val source = DocumentRepository(context).originalFile(document) ?: stageOriginalArchive(context, original)
    val header = ByteArray(8).also { bytes -> source.inputStream().use { java.io.DataInputStream(it).readFully(bytes) } }
    return if (PptLegacyExtractor.isPptFile(header)) {
        val bytes = source.inputStream().use { OriginalArchiveEntries.readBounded(it, 64 * 1024 * 1024) }
        val body = PptLegacyExtractor.extract(bytes)
        val lines = body.text.lines()
        val slides = mutableListOf<PptxSlideContent>()
        var currentSlideNum = 1
        var currentLines = mutableListOf<String>()
        for (line in lines) {
            if (line.startsWith("[[VERITAS_PAGE:")) {
                if (currentLines.isNotEmpty()) {
                    val title = currentLines.firstOrNull().orEmpty()
                    val content = currentLines.drop(1)
                    slides.add(PptxSlideContent(currentSlideNum, listOf(title), content, emptyList(), emptyList()))
                    currentSlideNum++
                    currentLines = mutableListOf()
                }
            } else if (line.isNotBlank()) {
                currentLines.add(line)
            }
        }
        if (currentLines.isNotEmpty()) {
            val title = currentLines.firstOrNull().orEmpty()
            val content = currentLines.drop(1)
            slides.add(PptxSlideContent(currentSlideNum, listOf(title), content, emptyList(), emptyList()))
        }
        val finalSlides = if (slides.isNotEmpty()) slides else listOf(PptxSlideContent(1, listOf(document.title), lines.filter { it.isNotBlank() }, emptyList(), emptyList()))
        PptxDeck(slides = finalSlides, slideCount = finalSlides.size)
    } else {
        PptxExtractor.parseDeck(source, includeSpeakerNotes = true)
    }
}

/**
 * Parses an EPUB book from original storage.
 */
internal fun loadEpubBook(context: Context, original: Uri, title: String, storedOriginal: java.io.File? = null): EpubBook {
    return EpubDocumentParser.parse(storedOriginal ?: stageOriginalArchive(context, original), title)
}

/** Parses Word text once, loading embedded media on demand. */
internal fun loadDocxDocument(context: Context, original: Uri, title: String, storedOriginal: java.io.File? = null): DocxDocument {
    return DocxDocumentParser.parse(storedOriginal ?: stageOriginalArchive(context, original), title, preserveSourceFormatting = true)
}

private fun stageOriginalArchive(context: Context, original: Uri): java.io.File {
    val directory = java.io.File(context.cacheDir, "original_view_archives").apply { mkdirs() }
    val staged = java.io.File(directory, "${java.util.UUID.randomUUID()}.zip")
    try {
        context.contentResolver.openInputStream(original)?.use { input ->
            staged.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024); var total = 0L
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    total += count; require(total <= 512L * 1024 * 1024) { "The original document is too large to preview." }
                    output.write(buffer, 0, count)
                }
            }
        } ?: error("Could not read the original document.")
        // Stored originals normally bypass this fallback. Clean expired cache files.
        directory.listFiles()?.filter { it != staged && System.currentTimeMillis() - it.lastModified() > 24L * 60 * 60 * 1000 }?.forEach { it.delete() }
        return staged
    } catch (error: Throwable) { staged.delete(); throw error }
}

/**
 * Renders a specified PDF page to an in-memory Bitmap.
 */
internal fun renderPdfPage(context: Context, original: Uri, pageIndex: Int): RenderedPage {
    return context.contentResolver.openFileDescriptor(original, "r")?.use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            val count = renderer.pageCount.coerceAtLeast(1)
            val safePage = pageIndex.coerceIn(0, count - 1)
            renderer.openPage(safePage).use { page ->
                val targetWidth = 1500
                val scale = (targetWidth.toFloat() / page.width.toFloat()).coerceIn(1f, 4f)
                val width = (page.width * scale).toInt().coerceAtLeast(1)
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val output = createBitmap(width, height)
                Canvas(output).drawColor(AndroidColor.WHITE)
                page.render(output, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                RenderedPage(count, output)
            }
        }
    } ?: throw IllegalStateException("Could not open PDF descriptor")
}

/**
 * Decodes an image file or stream to an in-memory Bitmap.
 */
internal fun decodeImageBitmap(context: Context, original: Uri): Bitmap {
    return context.contentResolver.openInputStream(original)?.use { input ->
        BitmapFactory.decodeStream(input)
    } ?: throw IllegalStateException("Could not open image stream")
}

/**
 * Extracts and decodes images belonging to a specific presentation slide.
 */
internal fun loadSlideImageMap(context: Context, original: Uri, slide: PptxSlideContent, storedOriginal: java.io.File? = null): Map<String, Bitmap> {
    if (slide.mediaPaths.isEmpty()) return emptyMap()
    val source = storedOriginal ?: stageOriginalArchive(context, original)
    val archive = OriginalArchiveEntries(source)
    val media = archive.imageMap { it in slide.mediaPaths }
    var remainingPixels = 4_000_000L
    return slide.mediaPaths.distinct().take(100).mapNotNull { path ->
        runCatching {
            media[path]?.let { bytes ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || remainingPixels < 1024) return@let null
                while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600 ||
                    (bounds.outWidth / sample).toLong() * (bounds.outHeight / sample) > remainingPixels) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.let {
                    remainingPixels -= it.width.toLong() * it.height
                    path to it
                }
            }
        }.getOrNull()
    }.toMap()
}

internal fun loadSlideImages(context: Context, original: Uri, slide: PptxSlideContent): List<Bitmap> = loadSlideImageMap(context, original, slide).values.toList()
