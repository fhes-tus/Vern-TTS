package com.veritas.reader

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal const val BATCH_QUEUE_WORK_TAG = "vern_batch_queue"
internal const val IMPORT_ORDER_PREFIX = "vern_batch_order:"

/** Persist queue order after parallel extraction, including when the app has been restarted. */
class DocumentBatchQueueWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val group = inputData.getString("batchGroup") ?: return@withContext Result.failure()
        val work = WorkManager.getInstance(applicationContext).getWorkInfosByTag(group).get()
        val documents = work.mapNotNull { info ->
            val order = info.tags.firstOrNull { it.startsWith(IMPORT_ORDER_PREFIX) }
                ?.removePrefix(IMPORT_ORDER_PREFIX)?.toIntOrNull() ?: return@mapNotNull null
            if (info.state != WorkInfo.State.SUCCEEDED) return@mapNotNull null
            info.outputData.getString("documentId")?.let { order to it }
        }.sortedBy { it.first }.map { it.second }.distinct()
        val repository = DocumentRepository(applicationContext)
        var failures = 0
        for (document in documents) {
            ensureActive()
            try {
                repository.addToQueue(document)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failures++
            }
        }
        Result.success(workDataOf("queueFailures" to failures))
    }
}
