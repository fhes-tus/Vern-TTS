package com.veritas.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * On-demand image provider for reader pages.
 * Retrieves inline illustrations and diagrams corresponding to a specific page
 * from the original document storage (EPUB chapters, DOCX pages, PPTX slides)
 * and caches decoded bitmaps in memory.
 */
data class DocumentPageMedia(val bitmaps: List<Bitmap?>, val followingTexts: List<String?> = emptyList())

object DocumentPageImageLoader {

    private val loaderMutex = Mutex()
    private val clearParsedPdf = java.util.concurrent.atomic.AtomicBoolean(false)
    // One parsed source avoids reopening a large PDF for each new reader page.
    // All access and eviction run under loaderMutex, including closing PDFBox.
    private val parsedPdfCache = object : LruCache<String, com.tom_roush.pdfbox.pdmodel.PDDocument>(1) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: com.tom_roush.pdfbox.pdmodel.PDDocument, newValue: com.tom_roush.pdfbox.pdmodel.PDDocument?) {
            if (oldValue !== newValue) runCatching { oldValue.close() }
        }
    }
    private val maxCacheSize = (Runtime.getRuntime().maxMemory() / 32).toInt().coerceIn(2 * 1024 * 1024, 8 * 1024 * 1024)

    private val bitmapCache = object : LruCache<String, DocumentPageMedia>(maxCacheSize) {
        override fun sizeOf(key: String, value: DocumentPageMedia): Int {
            // Empty pages also consume cache space. Keep geometry and pixels under
            // one eviction policy so cached images never lose their anchors.
            return 256 + value.bitmaps.filterNotNull().sumOf { it.byteCount } +
                value.followingTexts.sumOf { (it?.length ?: 0) * 2 }
        }
    }

    fun getPageImageAnchors(documentId: String, pageNumber: Int): List<String?> =
        bitmapCache.get(cacheKey(documentId, pageNumber))?.followingTexts.orEmpty()

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

    fun getCachedPageImages(documentId: String, pageNumber: Int): List<Bitmap?>? {
        return getCachedPageMedia(documentId, pageNumber)?.bitmaps
    }

    fun getCachedPageMedia(documentId: String, pageNumber: Int): DocumentPageMedia? =
        bitmapCache.get(cacheKey(documentId, pageNumber))

    suspend fun loadPageImages(
        context: Context, repository: DocumentRepository, documentId: String, pageNumber: Int
    ): List<Bitmap?> = loadPageMedia(context, repository, documentId, pageNumber).bitmaps

    suspend fun loadPageMedia(
        context: Context,
        repository: DocumentRepository,
        documentId: String,
        pageNumber: Int
    ): DocumentPageMedia = withContext(Dispatchers.IO) {
        runCatching {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        }
        val key = cacheKey(documentId, pageNumber)
        bitmapCache.get(key)?.let { return@withContext it }

        loaderMutex.withLock {
            if (clearParsedPdf.getAndSet(false)) parsedPdfCache.evictAll()
            bitmapCache.get(key)?.let { return@withLock it }

            val docInfo = docInfoCache.get(documentId) ?: run {
                val savedDoc = repository.findDocument(documentId) ?: return@withLock DocumentPageMedia(emptyList())
                val originalFile = repository.originalFile(savedDoc)
                val originalUri = repository.originalUri(savedDoc) ?: originalFile?.let { android.net.Uri.fromFile(it) } ?: return@withLock DocumentPageMedia(emptyList())

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
            docInfo.isImage
            val isPresentation = docInfo.isPresentation
            val isEpub = docInfo.isEpub
            val isDocx = docInfo.isDocx

            val bitmaps = mutableListOf<Bitmap?>()
            var followingTexts: List<String?> = emptyList()

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
                        bitmaps.add(decodeScaledBitmap(bytes, 720, 720))
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
                        bitmaps.add(decodeScaledBitmap(block.imageBytes, 720, 720))
                    }
                }
                isPdf -> {
                    if (!com.tom_roush.pdfbox.android.PDFBoxResourceLoader.isReady()) {
                        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
                    }
                    val memorySetting = com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(10L * 1024 * 1024).apply {
                        setTempDir(java.io.File(context.cacheDir, "pdfbox_temp").apply { mkdirs() })
                    }
                    val sourceKey = "$documentId:${originalFile?.length()}:${originalFile?.lastModified()}:$originalUri"
                    val doc = parsedPdfCache.get(sourceKey) ?: run {
                        val loaded = if (originalFile != null && originalFile.exists()) {
                            com.tom_roush.pdfbox.pdmodel.PDDocument.load(originalFile, memorySetting)
                        } else {
                            context.contentResolver.openInputStream(originalUri)?.use { stream ->
                                com.tom_roush.pdfbox.pdmodel.PDDocument.load(stream, memorySetting)
                            }
                        }
                        loaded?.also { parsedPdfCache.put(sourceKey, it) }
                    }
                    doc?.let { pdDoc ->
                        val pageIdx = (pageNumber - 1).coerceIn(0, pdDoc.numberOfPages - 1)
                        val illustrations = PdfIllustrationExtractor.extract(pdDoc, pageIdx + 1)
                        bitmaps.addAll(illustrations.map { it.bitmap })
                        followingTexts = illustrations.map { it.followingText }
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
                                    bitmaps.add(decodeScaledBitmap(bytes, 720, 720))
                                }
                            }
                        }
                    }
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Gracefully ignore corrupt or missing media entries
        }

        DocumentPageMedia(bitmaps.toList(), followingTexts).also { bitmapCache.put(key, it) }
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

    private fun cacheKey(documentId: String, pageNumber: Int) = "${documentId}_p_$pageNumber"

    fun clear() {
        if (loaderMutex.tryLock()) {
            try { parsedPdfCache.evictAll() } finally { loaderMutex.unlock() }
        } else clearParsedPdf.set(true)
        bitmapCache.evictAll()
        epubBookCache.evictAll()
        docxDocCache.evictAll()
        pptxDeckCache.evictAll()
        docInfoCache.evictAll()
    }
}
