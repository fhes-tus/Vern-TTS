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

class DocumentImportWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val uriString = inputData.getString("uri") ?: return Result.failure()
        val title = inputData.getString("title") ?: "Imported document"
        val isPartial = inputData.getBoolean("isPartial", false)
        
        val uri = Uri.parse(uriString)
        
        // Setup notification channel
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "import_channel",
                "Document Imports",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

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

            if (isPdf) {
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

                            val start = pdfStartPage ?: 1
                            val end = pdfEndPage ?: totalPages
                            val chunkSize = 25
                            var currentStart = start
                            var documentId: String? = null
                            val repository = DocumentRepository(applicationContext)

                            while (currentStart <= end) {
                                val currentEnd = minOf(currentStart + chunkSize - 1, end)
                                val chunkOptions = pdfOptions.copy(
                                    startPage = currentStart,
                                    endPage = currentEnd,
                                    cropRect = null // already applied to document
                                )

                                val extracted = DocumentExtractor.extractPdfChunk(
                                    context = applicationContext,
                                    uri = uri,
                                    document = document,
                                    totalPageCount = totalPages,
                                    displayName = title,
                                    options = chunkOptions
                                )

                                val isComplete = currentEnd >= end

                                if (documentId == null) {
                                    if (extracted.text.isBlank() && isComplete) {
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
                                        language = detectedLanguage
                                    )
                                    documentId = newDocument.id

                                    // Signal ViewModel to auto-open the document immediately
                                    setProgress(workDataOf("firstChunkDocumentId" to newDocument.id))

                                     // Extract cover page synchronously from the copied original document
                                     runCatching {
                                         val originalFile = repository.originalFile(newDocument)
                                         if (originalFile != null && originalFile.exists()) {
                                             CoverExtractor.extractCoverFromFile(applicationContext, newDocument.id, originalFile)
                                         }
                                     }
                                } else {
                                    repository.appendDocumentText(documentId, extracted.text, isComplete = isComplete)
                                }
                                currentStart = currentEnd + 1
                            }
                            
                            showCompletionNotification("Import Complete", "$title has been fully added to your library.")
                            return Result.success(workDataOf("documentId" to documentId))
                        }
                    } finally {
                        managedPdf.close()
                    }
                } catch (e: Exception) {
                    Log.w("DocumentImportWorker", "PDF chunked extraction failed, falling back to generic extract", e)
                }
            }

            val extracted = DocumentExtractor.extract(
                context = applicationContext,
                uri = uri,
                displayName = title,
                pdfOptions = pdfOptions,
                textOptions = textOptions,
                pptxOptions = pptxOptions,
                foregroundBudgetMillis = null
            )

            if (extracted.text.isBlank()) {
                showCompletionNotification("Import Failed", "No readable text was found in $title.")
                Result.failure(workDataOf("error" to "No readable text was found."))
            } else {
                val repository = DocumentRepository(applicationContext)
                val detectedLanguage = LanguageDetector.detectLanguage(extracted.text)
                val (newDocument, _) = repository.createDocumentWithResult(
                    title = extracted.title,
                    text = extracted.text,
                    sourceLabel = extracted.sourceLabel,
                    originalUri = uri,
                    originalMimeType = applicationContext.contentResolver.getType(uri).orEmpty(),
                    pageCount = extracted.pageCount,
                    partial = extracted.partial || isPartial,
                    language = detectedLanguage
                )
                
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
            Result.failure()
        } catch (e: Exception) {
            Log.e("DocumentImportWorker", "Failed to import document", e)
            showCompletionNotification("Import Failed", "Could not import $title: ${e.message}")
            Result.failure(workDataOf("error" to e.message))
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

    companion object {
        private const val notificationId = 1001
    }
}
