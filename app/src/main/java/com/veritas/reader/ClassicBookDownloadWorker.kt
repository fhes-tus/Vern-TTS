package com.veritas.reader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.*
import java.io.IOException
import java.util.concurrent.TimeUnit

class ClassicBookDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val book = resolveClassicDownload(inputData.getString(BOOK_ID))
            ?: return@withContext Result.failure(workDataOf(ERROR to "This book is no longer in the catalog."))
        if (!book.hasDirectTextDownload) return@withContext Result.failure(workDataOf(ERROR to "This book requires access through its source website."))
        val repository = DocumentRepository(applicationContext)
        findCatalogDocument(book, repository.migrateClassicProvenance())?.let {
            return@withContext Result.success(workDataOf("documentId" to it.id))
        }
        try {
            setForeground(foreground(book.title))
            val cleaned = downloadClassicText(book.downloadUrl) { source ->
                setProgress(workDataOf(PHASE to "download"))
                fetchClassicText(source) { percent ->
                    setProgress(workDataOf(PHASE to "download", PERCENT to percent))
                }
            }
            ensureActive()
            setProgress(workDataOf(PHASE to "adding"))
            val job = currentCoroutineContext().job
            val document = repository.installClassic(book, cleaned) { job.ensureActive() }
            // Publication is already committed. A missing cover must never fail a completed book.
            BookCoverLoader.asset(applicationContext, book.id)?.let {
                runCatching { CoverExtractor.saveCoverBitmap(applicationContext, document.id, it) }
            }
            repository.completeFirstChapterMilestone(FirstChapterMilestone.IMPORT)
            Result.success(workDataOf("documentId" to document.id))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (error is IOException && runAttemptCount < 2 && error !is ClassicDownloadRejected) {
                setProgress(workDataOf(PHASE to "waiting"))
                Result.retry()
            } else {
                Result.failure(workDataOf(ERROR to (error.message ?: "Could not download this book.")))
            }
        }
    }

    private fun foreground(title: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel("classic_downloads", "Classic book downloads", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, "classic_downloads")
            .setSmallIcon(R.drawable.ic_stat_veritas).setContentTitle("Adding $title")
            .setContentText("Downloading to your Vern library").setOngoing(true)
            .addAction(0, "Cancel", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
        val notificationId = (id.hashCode() and 0x3fffffff) * 2 + 1
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(notificationId, notification)
    }

    companion object {
        const val TAG = "classic-download"
        const val BOOK_ID = "catalogId"
        const val PHASE = "phase"
        const val PERCENT = "percent"
        const val ERROR = "error"
        fun uniqueName(bookId: String) = "classic-download:$bookId"
        fun enqueue(context: Context, bookId: String) {
            require(resolveClassicDownload(bookId)?.hasDirectTextDownload == true)
            val request = OneTimeWorkRequestBuilder<ClassicBookDownloadWorker>()
                .setInputData(workDataOf(BOOK_ID to bookId))
                .addTag(TAG).addTag("book:$bookId").addTag("created:${System.currentTimeMillis()}")
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork(uniqueName(bookId), ExistingWorkPolicy.KEEP, request)
        }
    }
}

internal fun classicDownloadStates(work: List<WorkInfo>): Map<String, ClassicDownloadState> =
    work.groupBy { info -> info.tags.firstOrNull { it.startsWith("book:") }?.removePrefix("book:") }
        .mapNotNull { (bookId, history) ->
            if (bookId == null) return@mapNotNull null
            val info = history.maxBy { item -> item.tags.firstOrNull { it.startsWith("created:") }?.removePrefix("created:")?.toLongOrNull() ?: 0L }
            val phase = when (info.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ClassicDownloadPhase.QUEUED
                WorkInfo.State.RUNNING -> if (info.progress.getString(ClassicBookDownloadWorker.PHASE) == "adding") ClassicDownloadPhase.ADDING else ClassicDownloadPhase.DOWNLOADING
                WorkInfo.State.SUCCEEDED -> ClassicDownloadPhase.AVAILABLE
                WorkInfo.State.FAILED -> ClassicDownloadPhase.FAILED
                WorkInfo.State.CANCELLED -> ClassicDownloadPhase.CANCELLED
            }
            bookId to ClassicDownloadState(phase,
                info.progress.getInt(ClassicBookDownloadWorker.PERCENT, -1).takeIf { it >= 0 },
                info.outputData.getString(ClassicBookDownloadWorker.ERROR).orEmpty())
        }.toMap()
