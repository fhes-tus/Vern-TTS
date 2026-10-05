package com.veritas.reader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext

class DocumentImportWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    private val notificationId = (id.hashCode() and 0x3fffffff) * 2

    override suspend fun doWork(): Result {
        val result = try {
            importDocument()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("DocumentImportWorker", "Could not start import", error)
            Result.failure(workDataOf("error" to (error.message ?: "Could not start import")))
        }
        if (!inputData.getBoolean("batchImport", false)) return result
        // A rejected file is a completed batch item, so later files still run.
        // Preserve its error/partial ID for truthful UI reporting.
        if (result is Result.Failure) {
            val data = result.outputData
            return Result.success(workDataOf("error" to (data.getString("error") ?: "Import failed"),
                "partialDocumentId" to data.getString("partialDocumentId")))
        }
        if (result is Result.Success && inputData.getBoolean("queueAfterImport", false)) {
            result.outputData.getString("documentId")?.let { documentId ->
                // Commit before the next worker starts, keeping the selected order.
                try {
                    DocumentRepository(applicationContext).addToQueue(documentId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.e("DocumentImportWorker", "Imported document could not be queued", error)
                    return Result.success(workDataOf("documentId" to documentId,
                        "queueError" to (error.message ?: "Could not add to reading queue")))
                }
            }
        }
        return result
    }

    private suspend fun importDocument(): Result {
        val importStarted = android.os.SystemClock.elapsedRealtime()
        val uriString = inputData.getString("uri") ?: return Result.failure()
        val title = inputData.getString("title") ?: "Imported document"
        val isPartial = inputData.getBoolean("isPartial", false)
        
        val uri = Uri.parse(uriString)
        val repository = DocumentRepository(applicationContext)
        val existingImport = repository.findDocument(id.toString())
        if (existingImport != null && !existingImport.partial) {
            return Result.success(workDataOf("documentId" to existingImport.id))
        }
        var publishedDocumentId: String? = existingImport?.id
        
        // Setup notification channel
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            "import_channel",
            "Document Imports",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)

        // Show foreground notification while running
        val notification = NotificationCompat.Builder(applicationContext, "import_channel")
            .setContentTitle("Importing $title")
            .setContentText("Vern is extracting text in the background...")
            .setSmallIcon(R.drawable.ic_stat_veritas)
            .setOngoing(true)
            .build()
            
        val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
        setForeground(foregroundInfo)

        return try {
            val pdfStartPage = inputData.getInt("pdf_startPage", -1).let { if (it == -1) null else it }
            val pdfEndPage = inputData.getInt("pdf_endPage", -1).let { if (it == -1) null else it }
            val cropLeft = inputData.getFloat("pdf_cropLeft", -1f)
            val cropTop = inputData.getFloat("pdf_cropTop", -1f)
            val cropRight = inputData.getFloat("pdf_cropRight", -1f)
            val cropBottom = inputData.getFloat("pdf_cropBottom", -1f)
            val cropRect = if (cropLeft != -1f && cropTop != -1f && cropRight != -1f && cropBottom != -1f) {
                android.graphics.RectF(cropLeft, cropTop, cropRight, cropBottom)
            } else null

            val pdfOptions = PdfImportOptions(
                startPage = pdfStartPage,
                endPage = pdfEndPage,
                cleanupRepeatedLines = inputData.getBoolean("pdf_cleanupRepeatedLines", true),
                removePageNumbers = inputData.getBoolean("pdf_removePageNumbers", true),
                repairHyphenation = inputData.getBoolean("pdf_repairHyphenation", true),
                includePageMarkers = inputData.getBoolean("pdf_includePageMarkers", false),
                forceOcr = inputData.getBoolean("pdf_forceOcr", false),
                preferOcrWhenLowText = inputData.getBoolean("pdf_preferOcrWhenLowText", true),
                extractionMode = inputData.getString("pdf_extractionMode") ?: "HTML with images",
                removeTopPageNoise = inputData.getBoolean("pdf_removeTopPageNoise", true),
                removeBottomPageNoise = inputData.getBoolean("pdf_removeBottomPageNoise", true),
                manualCropBeforeExtract = inputData.getBoolean("pdf_manualCropBeforeExtract", false),
                minWordGap = inputData.getString("pdf_minWordGap") ?: "0.1",
                separateWordsOnFontChange = inputData.getBoolean("pdf_separateWordsOnFontChange", true),
                markPdfLinesForCanvas = inputData.getBoolean("pdf_markPdfLinesForCanvas", true),
                forceFreshExtraction = inputData.getBoolean("pdf_forceFreshExtraction", false),
                cropRect = cropRect
            )
            val textOptions = TextImportOptions(
                encodingId = inputData.getString("text_encodingId") ?: TextImportEncodingCatalog.AUTO_DETECT_ID
            )
            val pptxOptions = PptxImportOptions(
                includeSpeakerNotes = inputData.getBoolean("pptx_includeSpeakerNotes", true),
                autoPunctuate = inputData.getBoolean("pptx_autoPunctuate", false),
                ocrSlideImages = inputData.getBoolean("pptx_ocrSlideImages", true)
            )

            val isPdf = applicationContext.contentResolver.getType(uri)?.contains("pdf") == true ||
                uri.path?.lowercase()?.endsWith(".pdf") == true ||
                title.lowercase().endsWith(".pdf")

            if (isPdf && existingImport == null) {
                try {
                    val managedPdf = DocumentExtractor.openPdfDocument(applicationContext, uri)
                    try {
                        val document = managedPdf.document
                        val totalPages = managedPdf.pageCount
                        if (totalPages > 0) {
                            // Apply crop rect once to all pages before chunking
                            if (pdfOptions.cropRect != null) {
                                DocumentExtractor.applyCropRect(document, pdfOptions.cropRect)
                            }

                            val normalizedOptions = pdfOptions.normalized(totalPages)
                            val start = normalizedOptions.startPage ?: 1
                            val end = normalizedOptions.endPage ?: totalPages
                            val chunkSize = 25
                            var currentStart = start
                            var documentId: String? = null
                            val repository = DocumentRepository(applicationContext)

                            while (currentStart <= end) {
                                currentCoroutineContext().ensureActive()
                                // Publish a small initial reading quickly; subsequent
                                // batches amortize cleanup and persistence work.
                                val batchSize = if (documentId == null) 5 else chunkSize
                                val currentEnd = minOf(currentStart + batchSize - 1, end)
                                val chunkOptions = normalizedOptions.copy(
                                    startPage = currentStart,
                                    endPage = currentEnd,
                                    cropRect = null, // already applied to document
                                    includePageMarkers = true // source-page mapping stays stable as pages arrive
                                )

                                val extracted = DocumentExtractor.extractPdfChunk(
                                    context = applicationContext,
                                    uri = uri,
                                    document = document,
                                    totalPageCount = totalPages,
                                    displayName = title,
                                    options = chunkOptions
                                )

                                currentCoroutineContext().ensureActive()
                                val isComplete = currentEnd >= end

                                if (documentId == null) {
                                    if (ReaderTextIndex.stripInternalMarkers(extracted.text).isBlank()) {
                                        if (!isComplete) {
                                            currentStart = currentEnd + 1
                                            continue
                                        }
                                        showCompletionNotification("Import Failed", "No readable text was found in $title.")
                                        return Result.failure(workDataOf("error" to "No readable text was found."))
                                    }

                                    val detectedLanguage = LanguageDetector.detectLanguage(extracted.text)
                                    val (newDocument, _) = repository.createDocumentWithResult(
                                        title = extracted.title,
                                        text = extracted.text,
                                        sourceLabel = extracted.sourceLabel,
                                        originalUri = uri,
                                        originalMimeType = applicationContext.contentResolver.getType(uri).orEmpty(),
                                        pageCount = totalPages,
                                        partial = !isComplete || isPartial,
                                        language = detectedLanguage,
                                        documentId = id.toString()
                                    )
                                    documentId = newDocument.id
                                    publishedDocumentId = documentId

                                    // Signal ViewModel to auto-open the document immediately
                                    setProgress(workDataOf("firstChunkDocumentId" to newDocument.id,
                                        "importedCharCount" to newDocument.charCount,
                                        "readyThroughPage" to currentEnd, "totalPages" to totalPages))
                                    Log.i("DocumentImportWorker", "Ready pages $start–$currentEnd after ${android.os.SystemClock.elapsedRealtime() - importStarted}ms; continuing in background")

                                     // Extract cover page synchronously from the copied original document
                                     runCatching {
                                         val originalFile = repository.originalFile(newDocument)
                                         if (originalFile != null && originalFile.exists()) {
                                             CoverExtractor.extractCoverFromFile(applicationContext, newDocument.id, originalFile)
                                         }
                                     }
                                } else {
                                    val updated = checkNotNull(repository.appendDocumentText(documentId, extracted.text, isComplete = isComplete && !isPartial)) {
                                        "The partial reading was removed while importing."
                                    }
                                    setProgress(workDataOf("firstChunkDocumentId" to documentId,
                                        "importedCharCount" to updated.charCount,
                                        "readyThroughPage" to currentEnd, "totalPages" to totalPages))
                                }
                                currentStart = currentEnd + 1
                            }
                            
                            showCompletionNotification("Import Complete", "$title has been fully added to your library.")
                            return Result.success(workDataOf("documentId" to documentId))
                        }
                    } finally {
                        managedPdf.close()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Once visible, the partial reading owns this job. A second
                    // extraction must not create a duplicate behind the user's back.
                    if (publishedDocumentId != null) throw e
                    Log.w("DocumentImportWorker", "PDF chunked extraction failed, falling back to generic extract", e)
                }
            }

            val extracted = DocumentExtractor.extract(
                context = applicationContext,
                uri = uri,
                displayName = title,
                pdfOptions = if (isPdf) pdfOptions.copy(includePageMarkers = true) else pdfOptions,
                textOptions = textOptions,
                pptxOptions = pptxOptions,
                foregroundBudgetMillis = null
            )

            currentCoroutineContext().ensureActive()
            if (ReaderTextIndex.stripInternalMarkers(extracted.text).isBlank()) {
                showCompletionNotification("Import Failed", "No readable text was found in $title.")
                Result.failure(workDataOf("error" to "No readable text was found."))
            } else {
                val repository = DocumentRepository(applicationContext)
                val detectedLanguage = LanguageDetector.detectLanguage(extracted.text)
                val newDocument = if (existingImport != null) {
                    checkNotNull(repository.updateDocumentText(existingImport.id, extracted.text,
                        partial = extracted.partial || isPartial)) { "The partial reading could not be resumed." }
                } else repository.createDocumentWithResult(
                    title = extracted.title,
                    text = extracted.text,
                    sourceLabel = extracted.sourceLabel,
                    originalUri = uri,
                    originalMimeType = applicationContext.contentResolver.getType(uri).orEmpty(),
                    pageCount = extracted.pageCount,
                    partial = extracted.partial || isPartial,
                    language = detectedLanguage,
                    documentId = id.toString()
                ).document
                
                runCatching {
                    val originalFile = repository.originalFile(newDocument)
                    if (originalFile != null && originalFile.exists()) {
                        CoverExtractor.extractCoverFromFile(applicationContext, newDocument.id, originalFile)
                    }
                }
                
                showCompletionNotification("Import Complete", "$title has been added to your library.")
                Result.success(workDataOf("documentId" to newDocument.id))
            }
        } catch (e: CancellationException) {
            Log.d("DocumentImportWorker", "Import cancelled")
            throw e
        } catch (e: Exception) {
            Log.e("DocumentImportWorker", "Failed to import document", e)
            showCompletionNotification("Import Failed", "Could not import $title: ${e.message}")
            Result.failure(workDataOf("error" to (e.message ?: "Extraction failed"), "partialDocumentId" to publishedDocumentId))
        }
    }

    private fun showCompletionNotification(title: String, message: String) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(applicationContext, "import_channel")
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_stat_veritas)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(notificationId + 1, notification)
    }

}
