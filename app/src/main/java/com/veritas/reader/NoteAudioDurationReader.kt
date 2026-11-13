package com.veritas.reader

import android.media.MediaMetadataRetriever
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Small, read-only metadata cache. File inspection and native calls stay off the UI thread. */
internal class NoteAudioDurationReader(
    private val readMetadata: (File) -> Long? = ::readAudioDurationMetadata,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val cacheLimit: Int = 64
) {
    init { require(cacheLimit > 0) }

    private data class Version(val path: String, val modified: Long, val bytes: Long, val revision: Long)
    // Serialize native reads, coalescing repeated requests through the cache after the first read.
    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<Version, Long>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Version, Long>): Boolean = size > cacheLimit
    }

    suspend fun read(path: String, revision: Long = 0): Long? = withContext(dispatcher) {
        if (path.isBlank()) return@withContext null
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            try {
                val file = File(path).canonicalFile
                fun version(): Version? = if (file.isFile) Version(file.path, file.lastModified(), file.length(), revision) else null
                val before = version() ?: return@withLock null
                cache[before]?.let { return@withLock it }
                val duration = readMetadata(file)
                // Native metadata reads are blocking: discard their result if cancelled or replaced.
                currentCoroutineContext().ensureActive()
                if (duration == null || duration < 0 || before != version()) return@withLock null
                cache[before] = duration
                duration
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null // Missing, unreadable or invalid media keeps the existing unavailable label.
            }
        }
    }
}

private fun readAudioDurationMetadata(file: File): Long? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.path)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
    } finally {
        retriever.release()
    }
}
