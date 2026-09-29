package com.veritas.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import androidx.core.graphics.createBitmap
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLDecoder
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

/**
 * Extracts cover images (first page thumbnails) from PDF documents
 * and stores them as JPEG files in a dedicated covers directory.
 */
object CoverExtractor {
    private const val TAG = "CoverExtractor"
    private const val COVER_WIDTH = 600
    private const val COVER_QUALITY = 85

    /**
     * Returns the covers directory, creating it if needed.
     */
    fun coversDir(context: Context): File =
        File(context.filesDir, "document_covers").apply { mkdirs() }

    /**
     * Extracts a cover image from a PDF URI and saves it as a JPEG.
     * Returns the filename of the saved cover, or null on failure.
     */
    /**
     * Extracts a cover image from a PDF URI and saves it as a WEBP.
     * Evaluates the first few pages with strong preference for the genuine title/cover on page 0.
     * Returns the filename of the saved cover, or null on failure.
     */
    fun extractPdfCover(context: Context, documentId: String, uri: Uri): String? {
        return try {
            val descriptor = if (uri.scheme == "file") {
                try {
                    val filePath = uri.path ?: return null
                    android.os.ParcelFileDescriptor.open(
                        File(filePath),
                        android.os.ParcelFileDescriptor.MODE_READ_ONLY
                    )
                } catch (e: Exception) {
                    context.contentResolver.openFileDescriptor(uri, "r")
                }
            } else {
                context.contentResolver.openFileDescriptor(uri, "r")
            } ?: return null

            descriptor.use { pfd ->
                val renderer = PdfRenderer(pfd)
                try {
                    val pageCount = renderer.pageCount
                    if (pageCount == 0) return null

                    var bestPageIndex = 0
                    var bestScore = -1f
                    var bestBitmap: Bitmap? = null

                    val pagesToExplore = minOf(5, pageCount)
                    for (i in 0 until pagesToExplore) {
                        runCatching {
                            val page = renderer.openPage(i)
                            try {
                                val bitmap = renderCoverPage(page)
                                try {
                                    var score = evaluatePageScore(bitmap)
                                    // Give page 0 a substantial prior bonus because page 0 is almost always
                                    // the designed book/document cover unless it's a completely blank sheet.
                                    if (i == 0 && score > 2.0f) {
                                        score *= 1.6f
                                    }
                                    Log.d(TAG, "PDF Page $i score: $score")
                                    if (score > bestScore) {
                                        bestScore = score
                                        bestBitmap?.recycle()
                                        bestBitmap = bitmap
                                        bestPageIndex = i
                                    } else {
                                        bitmap.recycle()
                                    }
                                } catch (e: Exception) {
                                    bitmap.recycle()
                                    throw e
                                }
                            } finally {
                                page.close()
                            }
                        }.onFailure { e ->
                            Log.w(TAG, "Failed to render page $i for cover extraction", e)
                        }
                    }

                    if (bestBitmap != null) {
                        try {
                            val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                Bitmap.CompressFormat.WEBP_LOSSY
                            } else {
                                @Suppress("DEPRECATION")
                                Bitmap.CompressFormat.WEBP
                            }
                            val fileName = "$documentId.cover.webp"
                            val coverFile = File(coversDir(context), fileName)
                            coverFile.outputStream().use { out ->
                                bestBitmap.compress(format, COVER_QUALITY, out)
                            }
                            Log.i(TAG, "Saved cover from page $bestPageIndex for $documentId (score: $bestScore)")
                            fileName
                        } finally {
                            bestBitmap.recycle()
                        }
                    } else {
                        null
                    }
                } finally {
                    renderer.close()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract cover for $documentId", e)
            null
        }
    }

    private fun evaluatePageScore(bitmap: Bitmap): Float {
        val w = bitmap.width
        val h = bitmap.height
        val stepX = (w / 40).coerceAtLeast(1)
        val stepY = (h / 40).coerceAtLeast(1)

        var nonWhitePixels = 0
        var totalSamples = 0
        var totalLuminance = 0L
        val pixelLuminances = ArrayList<Int>(1650)

        for (y in 0 until h step stepY) {
            for (x in 0 until w step stepX) {
                val color = bitmap.getPixel(x, y)
                val r = (color shr 16) and 0xff
                val g = (color shr 8) and 0xff
                val b = color and 0xff

                val lum = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
                pixelLuminances.add(lum)
                totalLuminance += lum
                totalSamples++

                if (r < 245 || g < 245 || b < 245) {
                    nonWhitePixels++
                }
            }
        }

        if (totalSamples == 0) return 0f

        val meanLuminance = totalLuminance.toFloat() / totalSamples
        var sumSquares = 0f
        for (i in 0 until pixelLuminances.size) {
            val diff = pixelLuminances[i] - meanLuminance
            sumSquares += diff * diff
        }
        val variance = sumSquares / totalSamples
        val stdDev = kotlin.math.sqrt(variance)

        val nonWhiteRatio = nonWhitePixels.toFloat() / totalSamples

        return stdDev * (1f + nonWhiteRatio * 2f)
    }

    /**
     * Extracts a cover from the original file stored on disk.
     */
    fun extractCoverFromFile(context: Context, documentId: String, originalFile: File): String? {
        return try {
            val uri = Uri.fromFile(originalFile)
            val isPdf = runCatching {
                originalFile.inputStream().use { input ->
                    val bytes = ByteArray(4)
                    val read = input.read(bytes)
                    read == 4 && bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()
                }
            }.getOrDefault(false)

            val isZip = !isPdf && runCatching {
                originalFile.inputStream().use { input ->
                    val bytes = ByteArray(4)
                    val read = input.read(bytes)
                    read == 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
                }
            }.getOrDefault(false)

            val isImage = !isPdf && !isZip && runCatching {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(originalFile.absolutePath, options)
                options.outWidth > 0 && options.outHeight > 0
            }.getOrDefault(false)

            var isPptx = false
            var isDocx = false
            var isEpub = false

            if (isZip) {
                runCatching {
                    java.util.zip.ZipInputStream(originalFile.inputStream()).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            val name = entry.name.trimStart('/').lowercase(Locale.getDefault())
                            if (PptxExtractor.isPptxEntryName(name)) {
                                isPptx = true
                                break
                            } else if (name.startsWith("word/")) {
                                isDocx = true
                                break
                            } else if (name == "meta-inf/container.xml" || name.endsWith(".opf")) {
                                isEpub = true
                                break
                            }
                        }
                    }
                }
            }

            when {
                isPdf -> extractPdfCover(context, documentId, uri)
                isPptx -> extractPptxCover(context, documentId, originalFile)
                isDocx -> extractDocxCover(context, documentId, originalFile)
                isEpub -> extractEpubCover(context, documentId, uri)
                isImage -> extractImageCover(context, documentId, uri)
                else -> null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract cover from file for $documentId", e)
            null
        }
    }

    /**
     * Returns the cover file for a given document, or null if it doesn't exist.
     */
    fun coverFile(context: Context, documentId: String): File? {
        val webpFile = File(coversDir(context), "$documentId.cover.webp")
        if (webpFile.exists()) return webpFile
        val jpgFile = File(coversDir(context), "$documentId.cover.jpg")
        return jpgFile.takeIf { it.exists() }
    }

    /**
     * Saves a pre-existing Bitmap as the document's cover image.
     */
    fun saveCoverBitmap(context: Context, documentId: String, bitmap: Bitmap): File? {
        return runCatching {
            val file = File(coversDir(context), "$documentId.cover.webp")
            file.outputStream().use { out ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, out)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 85, out)
                }
            }
            file
        }.getOrNull()
    }

    /**
     * Deletes the cover file for a document.
     */
    fun deleteCover(context: Context, documentId: String) {
        runCatching { File(coversDir(context), "$documentId.cover.webp").delete() }
        runCatching { File(coversDir(context), "$documentId.cover.jpg").delete() }
    }

    private fun renderCoverPage(page: PdfRenderer.Page): Bitmap {
        val scale = (COVER_WIDTH.toFloat() / page.width.toFloat()).coerceIn(0.5f, 3.0f)
        val width = (page.width * scale).roundToInt().coerceAtLeast(1)
        val height = (page.height * scale).roundToInt().coerceAtLeast(1)
        val bitmap = createBitmap(width, height)
        Canvas(bitmap).drawColor(AndroidColor.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    /**
     * Extracts a cover image from an EPUB URI and saves it as a WEBP.
     * Evaluates explicit metadata, cover-related items, XHTML image tags, and manifest fallbacks.
     */
    fun extractEpubCover(context: Context, documentId: String, uri: Uri): String? {
        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: return null
            val bytes = stream.use { it.readBytes() }
            if (bytes.isEmpty()) return null

            val zipEntries = mutableMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory) {
                        val name = normalizeZipPath(entry.name.trimStart('/'))
                        val lower = name.lowercase(Locale.getDefault())
                        val shouldRead = lower == "meta-inf/container.xml" ||
                            lower.endsWith(".opf") ||
                            lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm") ||
                            lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                            lower.endsWith(".png") || lower.endsWith(".webp")
                        if (shouldRead) {
                            zipEntries[name] = zip.readBytes()
                        }
                    }
                }
            }

            val containerXmlBytes = zipEntries.entries.firstOrNull {
                it.key.equals("META-INF/container.xml", ignoreCase = true)
            }?.value ?: return null
            val containerXml = containerXmlBytes.toString(Charsets.UTF_8)
            val opfPath = findContainerRootFile(containerXml)?.let { normalizeZipPath(it) } ?: return null
            val opfBytes = zipEntries[opfPath] ?: zipEntries.entries.firstOrNull {
                it.key.equals(opfPath, ignoreCase = true)
            }?.value ?: return null
            val opfXml = opfBytes.toString(Charsets.UTF_8)

            val baseDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "")

            var coverHref: String? = null

            // 1. Check properties="cover-image"
            val propertiesMatch = Regex("""<item\b[^>]*(properties\s*=\s*["'][^"']*cover-image[^"']*["'])[^>]*>""", RegexOption.IGNORE_CASE).find(opfXml)
            if (propertiesMatch != null) {
                val attrs = parseAttributes(propertiesMatch.value)
                coverHref = attrs["href"]
            }

            // 2. Check meta name="cover"
            if (coverHref == null) {
                val metaMatch = Regex("""<meta\b[^>]*name\s*=\s*["']cover["'][^>]*>""", RegexOption.IGNORE_CASE).find(opfXml)
                if (metaMatch != null) {
                    val metaAttrs = parseAttributes(metaMatch.value)
                    val coverId = metaAttrs["content"]
                    if (!coverId.isNullOrBlank()) {
                        val itemMatch = Regex("""<item\b[^>]*id\s*=\s*["']${Regex.escape(coverId)}["'][^>]*>""", RegexOption.IGNORE_CASE).find(opfXml)
                        if (itemMatch != null) {
                            coverHref = parseAttributes(itemMatch.value)["href"]
                        }
                    }
                }
            }

            // 3. Look for items with "cover", "jacket", "front", "title", "poster" in id or href
            if (coverHref == null) {
                val candidates = mutableListOf<String>()
                Regex("""<item\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(opfXml).forEach { item ->
                    val attrs = parseAttributes(item.value)
                    val id = attrs["id"].orEmpty().lowercase(Locale.getDefault())
                    val href = attrs["href"].orEmpty().lowercase(Locale.getDefault())
                    val mediaType = attrs["media-type"].orEmpty().lowercase(Locale.getDefault())
                    if (mediaType.startsWith("image/")) {
                        if (id.contains("cover") || href.contains("cover") ||
                            id.contains("jacket") || href.contains("jacket") ||
                            id.contains("front") || href.contains("front") ||
                            id.contains("title") || href.contains("title") ||
                            id.contains("poster") || href.contains("poster")) {
                            attrs["href"]?.let { candidates.add(it) }
                        }
                    }
                }
                coverHref = candidates.firstOrNull()
            }

            // 4. Check the first XHTML spine document for embedded cover <img> or <image>
            if (coverHref == null) {
                val spineMatch = Regex("""<itemref\b[^>]*idref\s*=\s*["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE).find(opfXml)
                if (spineMatch != null) {
                    val idref = spineMatch.groupValues[1]
                    val itemMatch = Regex("""<item\b[^>]*id\s*=\s*["']${Regex.escape(idref)}["'][^>]*>""", RegexOption.IGNORE_CASE).find(opfXml)
                    if (itemMatch != null) {
                        val firstPageHref = parseAttributes(itemMatch.value)["href"]
                        if (!firstPageHref.isNullOrBlank()) {
                            val resolvedPage = resolveZipPath(baseDir, firstPageHref)
                            val pageBytes = zipEntries[resolvedPage] ?: zipEntries.entries.firstOrNull { it.key.equals(resolvedPage, ignoreCase = true) }?.value
                            if (pageBytes != null) {
                                val pageHtml = pageBytes.toString(Charsets.UTF_8)
                                val imgMatch = Regex("""<(?:img|image)\b[^>]*(?:src|href|xlink:href)\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(pageHtml)
                                if (imgMatch != null) {
                                    val imgSrc = imgMatch.groupValues[1]
                                    val pageBaseDir = resolvedPage.substringBeforeLast('/', missingDelimiterValue = "")
                                    coverHref = resolveZipPath(pageBaseDir, imgSrc)
                                }
                            }
                        }
                    }
                }
            }

            // 5. Fallback: pick the first or largest image entry from zip
            val resolvedPath = if (coverHref != null) resolveZipPath(baseDir, coverHref) else null
            val imageBytes = if (resolvedPath != null) {
                zipEntries[resolvedPath] ?: zipEntries.entries.firstOrNull { it.key.equals(resolvedPath, ignoreCase = true) }?.value
            } else {
                zipEntries.entries.filter { (k, v) ->
                    val l = k.lowercase(Locale.getDefault())
                    (l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png") || l.endsWith(".webp")) && v.size > 2048
                }.maxByOrNull { it.value.size }?.value
            }

            if (imageBytes != null && imageBytes.isNotEmpty()) {
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                if (bitmap != null) {
                    val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        Bitmap.CompressFormat.WEBP_LOSSY
                    } else {
                        @Suppress("DEPRECATION")
                        Bitmap.CompressFormat.WEBP
                    }
                    val fileName = "$documentId.cover.webp"
                    val coverFile = File(coversDir(context), fileName)
                    coverFile.outputStream().use { out ->
                        bitmap.compress(format, COVER_QUALITY, out)
                    }
                    bitmap.recycle()
                    Log.i(TAG, "Successfully extracted EPUB cover for $documentId")
                    return fileName
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract EPUB cover for $documentId", e)
            null
        }
    }

    /**
     * Word (.docx) documents can contain embedded thumbnails at docProps/thumbnail.*
     * or the first document image at word/media/image1.*.
     */
    private fun extractDocxCover(context: Context, documentId: String, originalFile: File): String? {
        return try {
            var imageBytes: ByteArray? = null
            java.util.zip.ZipInputStream(originalFile.inputStream()).use { zip ->
                val entries = mutableMapOf<String, ByteArray>()
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = normalizeZipPath(entry.name.trimStart('/')).lowercase(Locale.getDefault())
                    if (!entry.isDirectory && (name.startsWith("docprops/thumbnail") || name.startsWith("word/media/image1"))) {
                        entries[name] = zip.readBytes()
                    }
                }
                imageBytes = entries.entries.firstOrNull { it.key.startsWith("docprops/thumbnail") }?.value
                    ?: entries.entries.firstOrNull { it.key.startsWith("word/media/image1") }?.value
            }
            val bytes = imageBytes ?: return null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            val fileName = "$documentId.cover.webp"
            val coverFile = File(coversDir(context), fileName)
            coverFile.outputStream().use { out ->
                bitmap.compress(format, COVER_QUALITY, out)
            }
            bitmap.recycle()
            Log.i(TAG, "Successfully extracted DOCX cover for $documentId")
            fileName
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract DOCX cover for $documentId", e)
            null
        }
    }

    /**
     * PowerPoint decks embed a thumbnail of the first slide at docProps/thumbnail.*;
     * it becomes the document cover so decks get real covers (and adaptive theming).
     */
    private fun extractPptxCover(context: Context, documentId: String, originalFile: File): String? {
        return try {
            var imageBytes: ByteArray? = null
            java.util.zip.ZipInputStream(originalFile.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = normalizeZipPath(entry.name.trimStart('/')).lowercase(Locale.getDefault())
                    if (!entry.isDirectory && name.startsWith("docprops/thumbnail")) {
                        imageBytes = zip.readBytes()
                        break
                    }
                }
            }
            val bytes = imageBytes ?: return null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            val fileName = "$documentId.cover.webp"
            val coverFile = File(coversDir(context), fileName)
            coverFile.outputStream().use { out ->
                bitmap.compress(format, COVER_QUALITY, out)
            }
            bitmap.recycle()
            Log.i(TAG, "Successfully extracted PPTX cover for $documentId")
            fileName
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract PPTX cover for $documentId", e)
            null
        }
    }

    /**
     * Extracts a cover image from an image document URI and saves it as a WEBP.
     * Returns the filename of the saved cover, or null on failure.
     */
    fun extractImageCover(context: Context, documentId: String, uri: Uri): String? {
        return try {
            val stream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(stream) ?: return null
            val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            val fileName = "$documentId.cover.webp"
            val coverFile = File(coversDir(context), fileName)
            coverFile.outputStream().use { out ->
                bitmap.compress(format, COVER_QUALITY, out)
            }
            bitmap.recycle()
            Log.i(TAG, "Successfully extracted image cover for $documentId")
            fileName
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract image cover for $documentId", e)
            null
        }
    }

    private fun findContainerRootFile(containerXml: String): String? {
        val match = Regex("""full-path\s*=\s*[\"']([^\"']+)[\"']""", RegexOption.IGNORE_CASE).find(containerXml)
        return match?.groupValues?.getOrNull(1)?.trimStart('/')
    }

    private fun parseAttributes(tag: String): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        Regex("""([A-Za-z_:][-A-Za-z0-9_:.]*)\s*=\s*[\"']([^\"']*)[\"']""").findAll(tag).forEach { match ->
            attrs[match.groupValues[1].lowercase(Locale.getDefault())] = match.groupValues[2]
        }
        return attrs
    }

    private fun normalizeZipPath(path: String): String {
        val segments = path.replace('\\', '/').split('/')
        val stack = mutableListOf<String>()
        for (seg in segments) {
            if (seg == ".." && stack.isNotEmpty() && stack.last() != "..") {
                stack.removeAt(stack.size - 1)
            } else if (seg.isNotEmpty() && seg != ".") {
                stack.add(seg)
            }
        }
        return stack.joinToString("/")
    }

    private fun resolveZipPath(baseDir: String, href: String): String {
        val decoded = runCatching { URLDecoder.decode(href, "UTF-8") }.getOrDefault(href)
            .substringBefore('#')
            .trimStart('/')
        val combined = if (baseDir.isBlank()) decoded else "$baseDir/$decoded"
        return normalizeZipPath(combined)
    }
}
