package com.veritas.reader

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Publishes weekly and explicitly requested data-only copies through the same writer. */
class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val workContext = coroutineContext
        serialWriter.withLock {
            val manual = inputData.getBoolean(MANUAL_INPUT, false)
            try {
                val repository = DocumentRepository(applicationContext)
                if (!manual && !repository.loadReaderSettings().autoBackupWeekly) return@withLock Result.success()
                val json = repository.buildBackupJson()
                val date = LocalDate.now()
                val internal = requireNotNull(AutomaticBackupFiles.directory(applicationContext, AutomaticBackupLocation.APP_STORAGE))
                AutomaticBackupFiles.publish(internal, AutomaticBackupLocation.APP_STORAGE, date, json, workContext::ensureActive)
                val externalSaved = try {
                    val external = AutomaticBackupFiles.directory(applicationContext, AutomaticBackupLocation.EXTERNAL_COPY)
                    if (external != null) {
                        AutomaticBackupFiles.publish(external, AutomaticBackupLocation.EXTERNAL_COPY, date, json, workContext::ensureActive)
                    }
                    external != null
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Log.w(TAG, "Could not publish the optional external backup copy.", error)
                    false
                }
                saveStatus(if (externalSaved) AutomaticBackupStatus.SAVED else AutomaticBackupStatus.LOCAL_ONLY)
                Result.success()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Could not publish the internal automatic backup.", error)
                val retry = error is IOException && runAttemptCount < 2
                saveStatus(if (retry) AutomaticBackupStatus.RETRYING else AutomaticBackupStatus.FAILED)
                if (retry) Result.retry() else Result.failure()
            }
        }
    }

    private fun saveStatus(status: AutomaticBackupStatus) {
        applicationContext.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE).edit()
            .putString(STATUS_KEY, status.name).apply()
    }

    companion object {
        private const val TAG = "AutoBackupWorker"
        internal const val WORK_NAME = "vern_auto_backup"
        internal const val MANUAL_WORK_NAME = "vern_auto_backup_now"
        private const val MANUAL_INPUT = "user_requested"
        private const val STATUS_PREFS = "vern_auto_backup_status"
        private const val STATUS_KEY = "last_result"
        private val serialWriter = Mutex()

        internal fun configure(context: Context, enabled: Boolean) {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork("veritas_auto_backup")
            if (enabled) {
                val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS).build()
                workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            } else {
                workManager.cancelUniqueWork(WORK_NAME)
            }
        }

        internal fun backUpNow(context: Context): androidx.work.Operation = WorkManager.getInstance(context).enqueueUniqueWork(
            MANUAL_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<AutoBackupWorker>().setInputData(workDataOf(MANUAL_INPUT to true)).build()
        )

        internal fun lastStatus(context: Context): AutomaticBackupStatus? {
            val saved = context.getSharedPreferences(STATUS_PREFS, Context.MODE_PRIVATE).getString(STATUS_KEY, null)
            return AutomaticBackupStatus.entries.firstOrNull { it.name == saved }
        }
    }
}

internal enum class AutomaticBackupStatus { SAVED, LOCAL_ONLY, RETRYING, FAILED }
