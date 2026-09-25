package com.veritas.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * On-demand image provider for reader pages.
 * Retrieves inline illustrations and diagrams corresponding to a specific page
 * from the original document storage (EPUB chapters, DOCX pages, PPTX slides)
 * and caches decoded bitmaps in memory.
 */
object DocumentPageImageLoader {

    private val loaderMutex = Mutex()
    private val maxCacheSize = (Runtime.getRuntime().maxMemory() / 32).toInt().coerceIn(2 * 1024 * 1024, 8 * 1024 * 1024)

    private val bitmapCache = object : LruCache<String, List<Bitmap>>(maxCacheSize) {
        override fun sizeOf(key: String, value: List<Bitmap>): Int {
            return value.sumOf { it.byteCount }
        }
    }

    private val epubBookCache = object : LruCache<String, EpubBook>(4) {}
    private val docxDocCache = object : LruCache<String, DocxDocument>(4) {}
    private val pptxDeckCache = object : LruCache<String, PptxDeck>(4) {}

    private data class CachedDocInfo(
        val savedDoc: SavedDocument,
        val originalFile: java.io.File?,
        val originalUri: android.net.Uri,
        val isPdf: Boolean,
        val isImage: Boolean,
        val isPresentation: Boolean,
        val isEpub: Boolean,
        val isDocx: Boolean
    )
    private val docInfoCache = object : LruCache<String, CachedDocInfo>(8) {}

    fun getCachedPageImages(documentId: String, pageNumber: Int): List<Bitmap>? {
        return bitmapCache.get(cacheKey(documentId, pageNumber))
    }

    suspend fun loadPageImages(
        context: Context,
        repository: DocumentRepository,
        documentId: String,
        pageNumber: Int
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        }
        val key = cacheKey(documentId, pageNumber)
        bitmapCache.get(key)?.let { return@withContext it }

        loaderMutex.withLock {
            bitmapCache.get(key)?.let { return@withLock it }

            val docInfo = docInfoCache.get(documentId) ?: run {
                val savedDoc = repository.findDocument(documentId) ?: return@withLock emptyList()
                val originalFile = repository.originalFile(savedDoc)
                val originalUri = repository.originalUri(savedDoc) ?: originalFile?.let { android.net.Uri.fromFile(it) } ?: return@withLock emptyList()

                val isPdf = detectIsPdf(savedDoc, repository, context)
                val isImage = detectIsImage(savedDoc, repository, context, isPdf)
                val isPresentation = detectIsPresentation(savedDoc, isPdf, isImage)
                val isEpub = detectIsEpub(savedDoc, isPdf, isImage, isPresentation)
                val isDocx = detectIsDocx(savedDoc, isPdf, isImage, isPresentation, isEpub)

                CachedDocInfo(savedDoc, originalFile, originalUri, isPdf, isImage, isPresentation, isEpub, isDocx).also {
                    docInfoCache.put(documentId, it)
                }
            }

            val savedDoc = docInfo.savedDoc
            val originalFile = docInfo.originalFile
            val originalUri = docInfo.originalUri
            val isPdf = docInfo.isPdf
            val isImage = docInfo.isImage
            val isPresentation = docInfo.isPresentation
            val isEpub = docInfo.isEpub
            val isDocx = docInfo.isDocx

            val bitmaps = mutableListOf<Bitmap>()

        try {
            when {
                isEpub -> {
                    val book = epubBookCache.get(documentId) ?: runCatching {
                        loadEpubBook(context, originalUri, savedDoc.title).also {
                            epubBookCache.put(documentId, it)
                        }
                    }.getOrNull()

                    val chapter = book?.chapters?.getOrNull(pageNumber - 1)
                    chapter?.images?.forEach { bytes ->
                        decodeScaledBitmap(bytes, 720, 720)?.let { bitmaps.add(it) }
                    }
                }
                isDocx -> {
                    val doc = docxDocCache.get(documentId) ?: runCatching {
                        loadDocxDocument(context, originalUri, savedDoc.title).also {
                            docxDocCache.put(documentId, it)
                        }
                    }.getOrNull()

                    val page = doc?.pages?.getOrNull(pageNumber - 1)
                    page?.blocks?.filterIsInstance<DocxBlock.Image>()?.forEach { block ->
                        decodeScaledBitmap(block.imageBytes, 720, 720)?.let { bitmaps.add(it) }
                    }
                }
                isPdf -> {
                    if (!com.tom_roush.pdfbox.android.PDFBoxResourceLoader.isReady()) {
                        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
                    }
                    val memorySetting = com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(10L * 1024 * 1024).apply {
                        setTempDir(java.io.File(context.cacheDir, "pdfbox_temp").apply { mkdirs() })
                    }
                    val doc = if (originalFile != null && originalFile.exists()) {
                        com.tom_roush.pdfbox.pdmodel.PDDocument.load(originalFile, memorySetting)
                    } else {
                        context.contentResolver.openInputStream(originalUri)?.let { stream ->
                            com.tom_roush.pdfbox.pdmodel.PDDocument.load(stream, memorySetting)
                        }
                    }
                    doc?.use { pdDoc ->
                        val pageIdx = (pageNumber - 1).coerceIn(0, pdDoc.numberOfPages - 1)
                        val pdPage = pdDoc.getPage(pageIdx)
                        val resources = pdPage.resources
                        if (resources != null) {
                            extractPdfImages(resources, bitmaps, maxImages = 3)
                        }
                    }
                }
                isPresentation -> {
                    val deck = pptxDeckCache.get(documentId) ?: runCatching {
                        loadPresentationDeck(context, originalUri, savedDoc).also {
                            pptxDeckCache.put(documentId, it)
                        }
                    }.getOrNull()

                    val slide = deck?.slides?.getOrNull(pageNumber - 1)
                    if (slide != null && slide.mediaPaths.isNotEmpty()) {
                        context.contentResolver.openInputStream(originalUri)?.use { stream ->
                            val zip = java.util.zip.ZipInputStream(stream)
                            val wanted = slide.mediaPaths.toSet()
                            while (true) {
                                val entry = zip.nextEntry ?: break
                                if (entry.name in wanted || entry.name.trimStart('/') in wanted) {
                                    val bytes = zip.readBytes()
                                    decodeScaledBitmap(bytes, 720, 720)?.let { bitmaps.add(it) }
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            // Gracefully ignore corrupt or missing media entries
        }

        if (bitmaps.isNotEmpty()) {
            bitmapCache.put(key, bitmaps)
        }
        bitmaps
        }
    }

    private fun decodeScaledBitmap(bytes: ByteArray, maxWidth: Int, maxHeight: Int): Bitmap? {
        if (bytes.isEmpty()) return null
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
        val origW = boundsOptions.outWidth
        val origH = boundsOptions.outHeight
        if (origW <= 0 || origH <= 0) return null

        var sampleSize = 1
        while (origW / (sampleSize * 2) >= maxWidth && origH / (sampleSize * 2) >= maxHeight) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        }.getOrNull()
    }

    private fun extractPdfImages(
        resources: com.tom_roush.pdfbox.pdmodel.PDResources,
        dest: MutableList<Bitmap>,
        maxImages: Int
    ) {
        if (dest.size >= maxImages) return
        for (name in resources.xObjectNames) {
            if (dest.size >= maxImages) break
            val xObject = runCatching { resources.getXObject(name) }.getOrNull() ?: continue
            if (xObject is com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject) {
                val w = runCatching { xObject.width }.getOrDefault(0)
                val h = runCatching { xObject.height }.getOrDefault(0)
                if (w < 80 || h < 80 || w > 8000 || h > 8000) continue

                var scaledBm: Bitmap? = null

                // Try fast, low-memory decode directly from raw stream first
                val rawBytes = runCatching { xObject.createInputStream()?.use { it.readBytes() } }.getOrNull()
                if (rawBytes != null && rawBytes.isNotEmpty()) {
                    scaledBm = decodeScaledBitmap(rawBytes, 720, 720)
                }

                // Fallback to PDFBox image parser if custom color space/filter
                if (scaledBm == null) {
                    val rawBm = runCatching { xObject.image }.getOrNull()
                    if (rawBm != null) {
                        if (rawBm.width > 720 || rawBm.height > 720) {
                            val ratio = minOf(720f / rawBm.width, 720f / rawBm.height)
                            val targetW = (rawBm.width * ratio).toInt().coerceAtLeast(1)
                            val targetH = (rawBm.height * ratio).toInt().coerceAtLeast(1)
                            scaledBm = Bitmap.createScaledBitmap(rawBm, targetW, targetH, true)
                            if (scaledBm !== rawBm) {
                                rawBm.recycle()
                            }
                        } else {
                            scaledBm = rawBm
                        }
                    }
                }

                if (scaledBm != null && scaledBm.width >= 80 && scaledBm.height >= 80) {
                    dest.add(scaledBm)
                }
            } else if (xObject is com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject) {
                val nestedRes = xObject.resources
                if (nestedRes != null) {
                    extractPdfImages(nestedRes, dest, maxImages)
                }
            }
        }
    }

    private fun cacheKey(documentId: String, pageNumber: Int) = "${documentId}_p_$pageNumber"

    fun clear() {
        bitmapCache.evictAll()
        epubBookCache.evictAll()
        docxDocCache.evictAll()
        pptxDeckCache.evictAll()
        docInfoCache.evictAll()
    }
}
