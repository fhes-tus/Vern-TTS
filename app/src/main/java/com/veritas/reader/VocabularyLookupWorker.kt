package com.veritas.reader

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Keep the selected word/source before leaving for the browser; resolve it durably. */
internal fun DocumentRepository.saveVocabularyLookup(
    context: Context, documentId: String, entry: VocabularyEntry
): List<GeneralNote> {
    val pending = VocabularyLookup.needsDefinition(entry.explanation)
    val lookupId = UUID.randomUUID().toString()
    val saved = entry.copy(explanation = if (pending) VocabularyLookup.PENDING else entry.explanation,
        lookupId = if (pending) lookupId else null)
    val now = System.currentTimeMillis()
    val timestamp = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", java.util.Locale.getDefault()).format(java.util.Date(now))
    val text = formatVocabularyEntry(saved, timestamp)
    val updated = mutateGeneralNotes { notes ->
        val title = "__vocab__$documentId"
        val existing = notes.firstOrNull { it.title == title }
        if (existing == null) listOf(GeneralNote(UUID.randomUUID().toString(), title, text, now)) + notes
        else notes.map { if (it.id == existing.id) it.copy(
            content = if (it.content.isBlank()) text else it.content + "\n\n" + text, updatedAt = now) else it }
    }
    if (pending) VocabularyLookupWorker.enqueue(context, documentId, lookupId)
    return updated
}

class VocabularyLookupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val documentId = inputData.getString("documentId") ?: return@withContext Result.failure()
        val lookupId = inputData.getString("lookupId") ?: return@withContext Result.failure()
        val repository = DocumentRepository(applicationContext)
        val title = "__vocab__$documentId"
        val note = repository.loadGeneralNotes().firstOrNull { it.title == title } ?: return@withContext Result.success()
        val entry = parseVocabularyNoteContent(note.content).firstOrNull { it.lookupId == lookupId }
            ?: return@withContext Result.success()
        val bookTitle = repository.loadDocuments().firstOrNull { it.id == documentId }?.title.orEmpty()
        val meaning = VocabularyLookup.resolve(applicationContext, entry.word, "", entry.contextSentence, bookTitle)
        if (meaning.definition == VocabularyLookup.UNAVAILABLE && runAttemptCount < 2) return@withContext Result.retry()
        if (meaning.definition == VocabularyLookup.UNAVAILABLE && !VocabularyLookup.needsDefinition(entry.explanation)) return@withContext Result.success()
        // A deleted entry/note must never be recreated by a late network response.
        repository.mutateGeneralNotes { notes -> notes.map { current ->
            if (current.id != note.id) current else {
                val content = updateVocabularyMeaning(current.content, lookupId, meaning)
                if (content == current.content) current else current.copy(content = content, updatedAt = System.currentTimeMillis())
            }
        } }
        Result.success()
    }

    companion object {
        const val TAG = "vocabulary_lookup"
        fun enqueue(context: Context, documentId: String, lookupId: String) {
            val request = OneTimeWorkRequestBuilder<VocabularyLookupWorker>()
                .setInputData(workDataOf("documentId" to documentId, "lookupId" to lookupId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(TAG).build()
            // Callers run on IO. Wait for WorkManager's durable enqueue before browser handoff.
            WorkManager.getInstance(context).enqueueUniqueWork("$TAG:$lookupId", ExistingWorkPolicy.KEEP, request).result.get()
        }
    }
}
