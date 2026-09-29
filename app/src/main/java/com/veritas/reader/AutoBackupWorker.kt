package com.veritas.reader

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.veritas.reader.AutoBackupWorker.Companion.KEEP_COUNT
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Weekly safety net: writes a data-only backup (same JSON as manual export, no
 * originals) into app storage and keeps the last [KEEP_COUNT]. Costs a few KB–MB,
 * so users who never export manually still have something recent to restore or
 * copy out of Android/data. Controlled by ReaderSettings.autoBackupWeekly.
 */
class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val repository = DocumentRepository(applicationContext)
        if (!repository.loadReaderSettings().autoBackupWeekly) return Result.success()
        return runCatching {
            val json = repository.buildBackupJson()
            val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            val name = "vern_auto_backup_$dateStr.json"

            // 1. Internal app files safety net
            val dir = File(applicationContext.filesDir, "auto_backups").apply { mkdirs() }
            File(dir, name).writeText(json, Charsets.UTF_8)
            dir.listFiles()
                ?.filter { it.name.startsWith("vern_auto_backup_") || it.name.startsWith("veritas_auto_backup_") }
                ?.sortedByDescending { it.name }
                ?.drop(KEEP_COUNT)
                ?.forEach { runCatching { it.delete() } }

            // 2. External app storage safety net (survives app cache clear, accessible to user)
            runCatching {
                val externalDir = applicationContext.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
                if (externalDir != null) {
                    val backupDir = File(externalDir, "VernBackups").apply { mkdirs() }
                    File(backupDir, name).writeText(json, Charsets.UTF_8)
                    backupDir.listFiles()
                        ?.filter { it.name.startsWith("vern_auto_backup_") || it.name.startsWith("veritas_auto_backup_") }
                        ?.sortedByDescending { it.name }
                        ?.drop(KEEP_COUNT)
                        ?.forEach { runCatching { it.delete() } }
                }
            }
        }.fold({ Result.success() }, { Result.retry() })
    }

    companion object {
        private const val KEEP_COUNT = 4
        private const val WORK_NAME = "vern_auto_backup"

        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork("veritas_auto_backup")
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS).build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
