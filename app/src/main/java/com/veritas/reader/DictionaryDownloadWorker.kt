package com.veritas.reader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

class DictionaryDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        OfflineDictionaryStore.clearInterrupted(applicationContext)
        if (OfflineDictionaryStore.info(applicationContext) != null) return@withContext Result.success()
        val cache = File(applicationContext.cacheDir, "offline-dictionary").apply { mkdirs() }
        val archive = File(cache, "download-${UUID.randomUUID()}.zip")
        try {
            setForeground(notification())
            setProgress(workDataOf("phase" to "download", "percent" to 0))
            val job = currentCoroutineContext().job
            var lastPercent = -1
            SecureDownloads.download(EnglishDictionaryPack.URL, archive, 16L * 1024 * 1024,
                checkActive = { job.ensureActive() }, onProgress = { done, _ ->
                    val percent = ((done * 100) / EnglishDictionaryPack.DOWNLOAD_BYTES).toInt().coerceIn(0, 100)
                    if (percent != lastPercent) { lastPercent = percent; setProgressAsync(workDataOf("phase" to "download", "percent" to percent)) }
                })
            setProgress(workDataOf("phase" to "install", "percent" to 0))
            OfflineDictionaryStore.install(applicationContext, archive, { job.ensureActive() }) {
                setProgressAsync(workDataOf("phase" to "install", "percent" to it))
            }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("OfflineDictionary", "Dictionary installation failed", error)
            Result.failure(workDataOf("error" to "Could not install the English dictionary. Check your connection and free storage, then try again."))
        } finally { archive.delete() }
    }

    private fun notification(): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(NotificationChannel(TAG, "Dictionary downloads", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, TAG)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("English dictionary")
            .setContentText("Downloading and preparing offline definitions")
            .setOngoing(true).setProgress(0,0,true)
            .addAction(0, "Cancel", WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(49127, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(49127, notification)
    }

    companion object {
        const val TAG = "offline_dictionary_download"
        const val NAME = "offline-dictionary-english-2025"
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<DictionaryDownloadWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .addTag(TAG).build())
        }
    }
}
