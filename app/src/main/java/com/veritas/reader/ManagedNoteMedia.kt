package com.veritas.reader

import com.veritas.reader.ui.screens.NoteSharing
import org.json.JSONArray
import java.io.File

internal object ManagedMediaLeases {
    private val counts = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()
    fun acquire(path: String) { counts.compute(path) { _, count -> (count ?: java.util.concurrent.atomic.AtomicInteger()).apply { incrementAndGet() } } }
    fun release(path: String) { counts.computeIfPresent(path) { _, count -> if (count.decrementAndGet() <= 0) null else count } }
    fun paths(): Set<String> = counts.keys.toSet()
}

internal data class UnusedNoteMedia(val file: File, val size: Long, val modifiedAt: Long)

internal fun DocumentRepository.noteMediaReferences(): Set<String> {
    val versions = loadGeneralNotes() + loadTrashedGeneralNotes() + loadAllNoteRevisions().map { it.snapshot }
    val paths = versions.flatMap { NoteSharing.blocks(it).mapNotNull(NoteSharing::path) }.toMutableSet()
    loadAllAnnotations().mapNotNullTo(paths) { it.audioPath }
    loadAllQuizzes().flatMap { it.questions }.flatMap { it.attachments }.mapNotNullTo(paths) { it.resolve(appContext)?.path }
    // Recovery snapshots are also owners. If they cannot be read, refuse cleanup.
    prefs.getString("general_notes__bak", null)?.let { raw ->
        val array = JSONArray(raw)
        for (i in 0 until array.length()) {
            val note = GeneralNote.fromJson(array.getJSONObject(i))
            paths.addAll(NoteSharing.blocks(note).mapNotNull(NoteSharing::path))
        }
    }
    VoiceNoteRecorder.activeAudioPath.value?.let(paths::add)
    paths.addAll(ManagedMediaLeases.paths())
    return paths.mapNotNull { path -> runCatching {
        val file = if (path.startsWith("file://")) File(android.net.Uri.parse(path).path.orEmpty()) else File(path)
        file.canonicalPath
    }.getOrNull() }.toSet()
}

internal fun DocumentRepository.findUnusedNoteMedia(now: Long = System.currentTimeMillis()): List<UnusedNoteMedia> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val references = noteMediaReferences()
    listOf("notes_media", "voice_notes").flatMap { name ->
        val root = File(appContext.filesDir, name).canonicalFile
        root.listFiles().orEmpty().mapNotNull { file ->
            // Recent/unpublished writes and files outside the managed roots are never candidates.
            if (!file.isFile || file.name.startsWith(".") || file.canonicalFile.parentFile != root ||
                file.canonicalPath in references || now - file.lastModified() < 86_400_000L) null
            else UnusedNoteMedia(file, file.length(), file.lastModified())
        }
    }
}

internal fun DocumentRepository.removeUnusedNoteMedia(preview: List<UnusedNoteMedia>): Long = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val eligible = findUnusedNoteMedia().associateBy { it.file.canonicalPath }
    preview.sumOf { candidate ->
        val live = eligible[candidate.file.canonicalPath]
        if (live != null && live.size == candidate.size && live.modifiedAt == candidate.modifiedAt && live.file.delete()) live.size else 0L
    }
}

internal fun mediaSha256(file: File): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal fun DocumentRepository.managedAttachmentRecords(): List<ManagedAttachment> =
    (loadGeneralNotes() + loadTrashedGeneralNotes() + loadAllNoteRevisions().map { it.snapshot.withManagedAttachments(appContext) })
        .flatMap { it.attachments } + loadAllAnnotations().mapNotNull { it.audioAttachment } + loadAllQuizzes().flatMap { it.questions }.flatMap { it.attachments }
