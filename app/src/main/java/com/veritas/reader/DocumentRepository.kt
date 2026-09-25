package com.veritas.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.edit
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.veritas.reader.ui.screens.cleanTocTitle
import java.io.File
import java.util.zip.ZipInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject
class DocumentRepository(context: Context) {
    internal val appContext = context.applicationContext
    internal val prefs = appContext.getSharedPreferences("veritas_reader_library", Context.MODE_PRIVATE)
    internal val docsDir: File = File(appContext.filesDir, "reader_documents").apply { mkdirs() }
    internal val originalsDir: File = File(appContext.filesDir, "original_documents").apply { mkdirs() }
    internal val dbHelper = VeritasDatabaseHelper.getInstance(appContext)

    init {
        dbHelper.ensureMigratedFromPrefs(
            loadDocsFromPrefs = { loadDocumentsFromPrefsRaw() },
            loadAnnotationsFromPrefs = { loadAllAnnotationsFromPrefsRaw() },
            loadFlashcardsFromPrefs = { loadAllFlashcardsFromPrefsRaw() }
        )
    }

    // applyPronunciationRules() runs once per spoken sentence. Re-reading prefs, re-parsing the
    // JSON, and recompiling every rule's Regex on each call was measurable TTS-start latency
    // once a user had several rules. Cache the compiled rules keyed by the raw stored string so
    // the expensive work happens only when the rules actually change.
    internal class CompiledPronunciationRule(val regex: Regex, val replaceWith: String)
    internal var pronunciationRulesRaw: String? = null
    internal var compiledPronunciationRules: List<CompiledPronunciationRule> = emptyList()

    fun saveDocumentTitle(documentId: String, title: String) {
        val raw = prefs.getString("document_titles", "{}") ?: "{}"
        val json = JSONObject(raw)
        json.put(documentId, title)
        prefs.edit().putString("document_titles", json.toString()).apply()
    }

    fun getDocumentTitle(documentId: String): String {
        val raw = prefs.getString("document_titles", "{}") ?: "{}"
        val json = JSONObject(raw)
        return json.optString(documentId, "Deleted Book")
    }

    fun loadAllDocumentTitles(): Map<String, String> {
        val raw = prefs.getString("document_titles", "{}") ?: "{}"
        val json = JSONObject(raw)
        val map = mutableMapOf<String, String>()
        
        // Auto-backfill active document titles
        runCatching {
            loadDocuments().forEach { doc ->
                map[doc.id] = doc.title
                if (!json.has(doc.id)) {
                    json.put(doc.id, doc.title)
                }
            }
        }
        
        json.keys().forEach { key ->
            map[key] = json.optString(key, "Deleted Book")
        }
        
        runCatching {
            prefs.edit().putString("document_titles", json.toString()).apply()
        }
        return map
    }

    // Sleep timer state is mirrored to prefs so a process kill mid-timer does not
    // silently lose it; the service restores and re-schedules on creation.
    fun saveSleepTimerState(durationMillis: Long, endsAtMillis: Long, actionName: String, stopAtEndOfSection: Boolean) {
        prefs.edit()
            .putLong("sleep_timer_duration", durationMillis)
            .putLong("sleep_timer_ends_at", endsAtMillis)
            .putString("sleep_timer_action", actionName)
            .putBoolean("sleep_timer_stop_at_section_end", stopAtEndOfSection)
            .apply()
    }

    fun clearSleepTimerState() {
        prefs.edit()
            .remove("sleep_timer_duration")
            .remove("sleep_timer_ends_at")
            .remove("sleep_timer_action")
            .remove("sleep_timer_stop_at_section_end")
            .apply()
    }

    fun savePersistedResumePoint(documentId: String, chunkIndex: Int, charOffset: Int, wordCount: Int) {
        prefs.edit()
            .putString("resume_document_id", documentId)
            .putInt("resume_chunk_index", chunkIndex)
            .putInt("resume_char_offset", charOffset)
            .putInt("resume_word_count", wordCount)
            .apply()
    }

    fun loadPersistedResumePoint(): PersistedResumePoint? {
        val docId = prefs.getString("resume_document_id", null) ?: return null
        val index = prefs.getInt("resume_chunk_index", -1)
        if (index < 0) return null
        val offset = prefs.getInt("resume_char_offset", 0)
        val words = prefs.getInt("resume_word_count", 0)
        return PersistedResumePoint(docId, index, offset, words)
    }

    fun clearPersistedResumePoint() {
        prefs.edit()
            .remove("resume_document_id")
            .remove("resume_chunk_index")
            .remove("resume_char_offset")
            .remove("resume_word_count")
            .apply()
    }

    fun loadPersistedSleepTimer(nowMillis: Long = System.currentTimeMillis()): VeritasSleepTimerSnapshot? {
        val duration = prefs.getLong("sleep_timer_duration", 0L)
        val endsAt = prefs.getLong("sleep_timer_ends_at", 0L)
        val stopAtEnd = prefs.getBoolean("sleep_timer_stop_at_section_end", false)
        if (!stopAtEnd && (duration <= 0L || endsAt <= nowMillis)) {
            clearSleepTimerState()
            return null
        }
        return VeritasSleepTimerSnapshot(
            durationMillis = duration,
            endsAtMillis = endsAt,
            action = VeritasSleepTimerAction.fromName(prefs.getString("sleep_timer_action", null)),
            stopAtEndOfSection = stopAtEnd
        )
    }

    fun recordDocReadingTime(documentId: String, durationMillis: Long, nowMillis: Long = System.currentTimeMillis()) {
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(nowMillis))
        val prefKey = "monthly_reading_time_$monthKey"
        // The reading-time counter is read-modify-written from both the UI (foreground session
        // timer) and the PlaybackService (background listening). Without a process-wide lock the
        // two could interleave and lose an update. Serialise the whole RMW.
        synchronized(LIBRARY_WRITE_LOCK) {
            val raw = prefs.getString(prefKey, "{}") ?: "{}"
            val json = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
            val currentVal = json.optLong(documentId, 0L)
            json.put(documentId, currentVal + durationMillis)
            // apply() (not commit()) so this never blocks the caller's thread on disk I/O —
            // this runs on the main thread from the service's onDone. The lock guarantees the
            // in-memory read-modify-write is consistent across the UI and service.
            prefs.edit().putString(prefKey, json.toString()).apply()
        }
    }

    fun loadDocReadingTimes(nowMillis: Long = System.currentTimeMillis()): Map<String, Long> {
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(nowMillis))
        val prefKey = "monthly_reading_time_$monthKey"
        val raw = prefs.getString(prefKey, "{}") ?: "{}"
        val json = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        val map = mutableMapOf<String, Long>()
        json.keys().forEach { key ->
            map[key] = json.optLong(key, 0L)
        }
        return map
    }

    fun computeStorageBreakdown(): StorageBreakdown {
        var text = 0L
        var originals = 0L
        var covers = 0L
        val docs = loadDocuments()
        docs.forEach { doc ->
            text += File(docsDir, doc.fileName).length()
            originalFile(doc)?.let { originals += it.length() }
            CoverExtractor.coverFile(appContext, doc.id)?.let { covers += it.length() }
        }

        fun dirSize(file: File?): Long {
            if (file == null || !file.exists()) return 0L
            if (file.isFile) return file.length()
            var total = 0L
            file.listFiles()?.forEach { child -> total += dirSize(child) }
            return total
        }

        val cache = dirSize(appContext.cacheDir) + dirSize(appContext.externalCacheDir)
        val dbFile = appContext.getDatabasePath("veritas_reader_db")
        val database = dirSize(dbFile.parentFile)

        return StorageBreakdown(
            textBytes = text,
            originalsBytes = originals,
            coversBytes = covers,
            cacheBytes = cache,
            databaseBytes = database,
            documentCount = docs.size
        )
    }

    /**
     * Clears temporary application cache from internal and external cache directories.
     * Returns the total number of bytes freed.
     */
    fun clearAppCache(): Long {
        var freed = 0L
        fun deleteContents(dir: File?) {
            if (dir == null || !dir.exists()) return
            dir.listFiles()?.forEach { child ->
                if (child.isDirectory) {
                    deleteContents(child)
                    runCatching { child.delete() }
                } else {
                    val size = child.length()
                    if (runCatching { child.delete() }.getOrDefault(false)) {
                        freed += size
                    }
                }
            }
        }
        deleteContents(appContext.cacheDir)
        deleteContents(appContext.externalCacheDir)
        return freed
    }


    /**
     * Smart-cleanup candidates: documents whose ORIGINAL file (the heavy part) can
     * be safely dropped — fully read or untouched for 90+ days, and not favorited.
     * Extracted text, progress, notes, and highlights are never touched; losing the
     * original only costs Original View until a re-import.
     */
    fun findCleanupCandidates(nowMillis: Long = System.currentTimeMillis()): List<Pair<SavedDocument, Long>> {
        val cutoff = nowMillis - 90L * 24 * 60 * 60 * 1000
        return loadDocuments().mapNotNull { doc ->
            if (doc.favorite) return@mapNotNull null
            val original = originalFile(doc) ?: return@mapNotNull null
            val fullyRead = doc.chunkCount > 0 && doc.currentIndex >= doc.chunkCount - 1
            val stale = doc.updatedAt < cutoff
            if (fullyRead || stale) doc to original.length() else null
        }.sortedByDescending { it.second }
    }

    /**
     * Deletes the stored original files for the given documents.
     *
     * A cover can only ever be extracted from the original, so removing it without
     * one first makes that document's cover unrecoverable — the backfill in
     * ReaderViewModel needs the file that is about to be deleted. A cover is ~40KB
     * against an original of several megabytes, so it is always worth rendering
     * before the file goes.
     */
    fun removeOriginals(documentIds: Set<String>): Long {
        var freed = 0L
        loadDocuments().filter { it.id in documentIds }.forEach { doc ->
            originalFile(doc)?.let { file ->
                if (CoverExtractor.coverFile(appContext, doc.id) == null) {
                    runCatching { CoverExtractor.extractCoverFromFile(appContext, doc.id, file) }
                }
                freed += file.length()
                runCatching { file.delete() }
            }
        }
        return freed
    }

    /**
     * Efficiently checks whether [file] contains [needle] using a 64KB streaming window
     * with an overlap buffer. Avoids reading multi-megabyte document texts into heap memory
     * when scanning through large libraries.
     */
    private fun fileContainsNeedle(file: File, needle: String): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        val bufferSize = 65536
        val overlap = (needle.length - 1).coerceAtLeast(0)
        val charBuffer = CharArray(bufferSize)
        return runCatching {
            file.bufferedReader(Charsets.UTF_8).use { reader ->
                val carryOver = StringBuilder(overlap)
                var charsRead: Int
                while (reader.read(charBuffer, 0, bufferSize).also { charsRead = it } != -1) {
                    val chunk = buildString(carryOver.length + charsRead) {
                        append(carryOver)
                        append(charBuffer, 0, charsRead)
                    }
                    if (chunk.contains(needle, ignoreCase = true)) {
                        return@use true
                    }
                    carryOver.clear()
                    if (chunk.length >= overlap) {
                        carryOver.append(chunk.substring(chunk.length - overlap))
                    } else {
                        carryOver.append(chunk)
                    }
                }
                false
            }
        }.getOrDefault(false)
    }

    /**
     * Full-text search across every document's extracted text. Returns sentence-level
     * hits so a tap can jump straight to the match. Caps per-document and total hits
     * to keep the scan bounded on large libraries. Uses streaming candidate filtering
     * to prevent OutOfMemoryError on large libraries.
     */
    fun searchLibraryContent(query: String, maxPerDocument: Int = 3, maxTotal: Int = 60): List<LibrarySearchHit> {
        val needle = query.trim()
        if (needle.length < 3) return emptyList()
        val hits = mutableListOf<LibrarySearchHit>()
        for (document in loadDocuments()) {
            if (hits.size >= maxTotal) break
            val file = File(docsDir, document.fileName)
            if (!fileContainsNeedle(file, needle)) continue
            val text = runCatching { readText(document) }.getOrDefault("")
            if (text.isBlank() || !text.contains(needle, ignoreCase = true)) continue
            val sentences = TextChunker.chunk(text)
            var found = 0
            for ((index, sentence) in sentences.withIndex()) {
                if (found >= maxPerDocument || hits.size >= maxTotal) break
                val at = sentence.indexOf(needle, ignoreCase = true)
                if (at < 0) continue
                val start = (at - 60).coerceAtLeast(0)
                val snippet = buildString {
                    if (start > 0) append("…")
                    append(sentence.substring(start, (at + needle.length + 90).coerceAtMost(sentence.length)).trim())
                    if (at + needle.length + 90 < sentence.length) append("…")
                }
                hits.add(LibrarySearchHit(document, index, snippet))
                found++
            }
        }
        return hits
    }

    // Per-document narration memory: the rate/pitch last used while reading a
    // document follow it (novel fast, textbook slow) instead of one global dial.
    // Voice selection deliberately stays global — per-doc voices proved confusing.
    fun saveDocVoiceMemory(documentId: String, rate: Float, pitch: Float) {
        if (documentId.isBlank()) return
        synchronized(LIBRARY_WRITE_LOCK) {
            val raw = prefs.getString("doc_voice_memory", "{}") ?: "{}"
            val json = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
            json.put(documentId, JSONObject().put("rate", rate.toDouble()).put("pitch", pitch.toDouble()))
            prefs.edit().putString("doc_voice_memory", json.toString()).apply()
        }
    }

    fun loadDocVoiceMemory(documentId: String): Pair<Float, Float>? {
        val raw = prefs.getString("doc_voice_memory", "{}") ?: "{}"
        val json = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        val entry = json.optJSONObject(documentId) ?: return null
        return entry.optDouble("rate", 1.0).toFloat() to entry.optDouble("pitch", 1.0).toFloat()
    }

    fun loadDocuments(): List<SavedDocument> {
        val docs = dbHelper.getAllDocuments(docsDir)
        if (docs.isEmpty()) {
            val fallback = loadDocumentsFromPrefsRaw()
            if (fallback.isNotEmpty()) {
                dbHelper.upsertDocuments(fallback)
                return fallback
            }
        }
        return docs
    }

    private fun loadDocumentsFromPrefsRaw(): List<SavedDocument> {
        val array = readResilientJsonArray(KEY_DOCUMENTS)
        val docs = mutableListOf<SavedDocument>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val doc = runCatching { SavedDocument.fromJson(item) }.getOrNull() ?: continue
            if (doc.id.isNotBlank() && doc.fileName.isNotBlank() && File(docsDir, doc.fileName).exists()) {
                docs.add(doc)
            }
        }
        return docs.sortedByDescending { it.updatedAt }
    }

    fun findDocument(documentId: String): SavedDocument? {
        if (documentId.isBlank()) return null
        return dbHelper.getDocumentById(documentId, docsDir) ?: loadDocuments().firstOrNull { it.id == documentId }
    }

    fun createDocument(
        title: String,
        text: String,
        sourceLabel: String,
        originalUri: Uri? = null,
        originalDisplayName: String = title,
        originalMimeType: String = "",
        pageCount: Int = 0,
        partial: Boolean = false,
        language: String = ""
    ): SavedDocument = createDocumentWithResult(
        title = title,
        text = text,
        sourceLabel = sourceLabel,
        originalUri = originalUri,
        originalDisplayName = originalDisplayName,
        originalMimeType = originalMimeType,
        pageCount = pageCount,
        partial = partial,
        language = language
    ).document

    fun createDocumentWithResult(
        title: String,
        text: String,
        sourceLabel: String,
        originalUri: Uri? = null,
        originalDisplayName: String = title,
        originalMimeType: String = "",
        pageCount: Int = 0,
        partial: Boolean = false,
        language: String = ""
    ): DocumentCreateResult {
        val normalizedTitle = title.trim().ifBlank { "Untitled reading" }
        val id = UUID.randomUUID().toString()
        val fileName = "$id.txt"
        File(docsDir, fileName).writeText(text, Charsets.UTF_8)
        saveDocumentTitle(id, normalizedTitle)
        val originalFileResult = originalUri?.let {
            saveOriginalFileReference(id, it, originalDisplayName, sourceLabel, originalMimeType)
        }
        val originalFileName = originalFileResult?.getOrNull().orEmpty()
        val fileErrorNote = originalFileResult?.exceptionOrNull()?.let {
            "Note: Original file could not be saved (${it.message}). PDF/image viewer may not be available."
        }

        val chunks = TextChunker.chunk(text)
        val now = System.currentTimeMillis()
        val doc = SavedDocument(
            id = id,
            title = normalizedTitle,
            fileName = fileName,
            sourceLabel = sourceLabel.ifBlank { "Text" },
            createdAt = now,
            updatedAt = now,
            currentIndex = 0,
            chunkCount = chunks.size,
            charCount = text.length,
            preview = previewText(text),
            originalFileName = originalFileName,
            originalMimeType = originalMimeType,
            pageCount = pageCount.coerceAtLeast(0),
            partial = partial,
            language = language
        )

        saveDocuments(listOf(doc) + loadDocuments().filterNot { it.id == id })

        // Log file copy errors
        if (fileErrorNote != null) {
            Log.w(TAG, "Original file save error for $id: ${originalFileResult.exceptionOrNull()?.message}")
        }

        return DocumentCreateResult(doc, fileErrorNote)
    }

    fun readText(document: SavedDocument): String {
        return runCatching { File(docsDir, document.fileName).readText(Charsets.UTF_8) }.getOrDefault("")
    }

    fun updateDocumentText(documentId: String, text: String): SavedDocument? {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return null
        val documents = loadDocuments()
        val target = documents.firstOrNull { it.id == documentId } ?: return null
        val oldChunks = runCatching {
            File(docsDir, target.fileName).takeIf { it.exists() }
                ?.readText(Charsets.UTF_8)
                ?.let { TextChunker.chunk(it) }
        }.getOrNull().orEmpty()
        ReaderTextModelCache.invalidate(documentId)
        File(docsDir, target.fileName).writeText(cleanText, Charsets.UTF_8)
        val chunks = TextChunker.chunk(cleanText)
        remapAnnotationsAfterEdit(documentId, oldChunks, chunks)
        val now = System.currentTimeMillis()
        val updatedTarget = target.copy(
            updatedAt = now,
            currentIndex = target.currentIndex.coerceIn(0, (chunks.size - 1).coerceAtLeast(0)),
            chunkCount = chunks.size,
            charCount = cleanText.length,
            preview = previewText(cleanText),
            pageCount = target.pageCount.takeIf { it > 0 } ?: ReaderTextIndex.build(cleanText).pageCount,
            partial = false
        )
        saveDocuments(documents.map { if (it.id == documentId) updatedTarget else it })
        return updatedTarget
    }

    // Annotations are anchored by chunk index; an edit can renumber chunks and leave
    // bookmarks/notes pointing at the wrong sentence. Re-anchor each one by matching
    // its original sentence text in the new chunk list.
    private fun remapAnnotationsAfterEdit(documentId: String, oldChunks: List<String>, newChunks: List<String>) {
        if (oldChunks.isEmpty() || newChunks.isEmpty()) return
        val all = loadAllAnnotations()
        if (all.none { it.documentId == documentId }) return
        val newIndexByText = HashMap<String, MutableList<Int>>()
        newChunks.forEachIndexed { idx, chunk ->
            newIndexByText.getOrPut(normalizeChunkForRemap(chunk)) { mutableListOf() }.add(idx)
        }
        var changed = false
        val remapped = all.map { ann ->
            if (ann.documentId != documentId) return@map ann
            val oldText = oldChunks.getOrNull(ann.chunkIndex) ?: return@map ann
            val candidates = newIndexByText[normalizeChunkForRemap(oldText)]
            val newIndex = if (candidates.isNullOrEmpty()) {
                // Sentence no longer exists verbatim; keep the position but stay in bounds.
                ann.chunkIndex.coerceIn(0, newChunks.lastIndex)
            } else {
                candidates.minByOrNull { kotlin.math.abs(it - ann.chunkIndex) } ?: ann.chunkIndex
            }
            if (newIndex != ann.chunkIndex) {
                changed = true
                ann.copy(chunkIndex = newIndex)
            } else {
                ann
            }
        }
        if (changed) saveAllAnnotations(remapped.distinctBy { it.stableKey })
    }

    private fun normalizeChunkForRemap(chunk: String): String =
        chunk.trim().replace(Regex("\\s+"), " ").lowercase(Locale.US)

    fun appendDocumentText(documentId: String, text: String, isComplete: Boolean = false): SavedDocument? {
        val cleanText = text.trim()
        if (cleanText.isBlank() && !isComplete) return null
        
        val documents = loadDocuments()
        val target = documents.firstOrNull { it.id == documentId } ?: return null
        ReaderTextModelCache.invalidate(documentId)
        
        val file = File(docsDir, target.fileName)
        if (cleanText.isNotBlank()) {
            file.appendText("\n\n" + cleanText, Charsets.UTF_8)
        }
        
        val newChunks = TextChunker.chunk(cleanText)
        val now = System.currentTimeMillis()
        
        val updatedTarget = target.copy(
            updatedAt = now,
            chunkCount = target.chunkCount + newChunks.size,
            charCount = target.charCount + cleanText.length + 2, // +2 for the \n\n
            partial = !isComplete
        )
        saveDocuments(listOf(updatedTarget) + documents.filterNot { it.id == documentId })
        return updatedTarget
    }

    fun originalFile(document: SavedDocument): File? {
        if (document.originalFileName.isBlank() || document.originalFileName.startsWith("content://")) return null
        return File(originalsDir, document.originalFileName).takeIf { it.exists() }
    }

    fun originalUri(document: SavedDocument): Uri? {
        val name = document.originalFileName
        if (name.isBlank()) return null
        if (name.startsWith("content://")) {
            return Uri.parse(name)
        }
        val file = File(originalsDir, name)
        if (!file.exists()) return null
        return runCatching {
            FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        }.getOrNull()
    }

    fun detectExtensionFromNameOrType(displayName: String, sourceLabel: String = "", mimeType: String = "", fileName: String = ""): String {
        val rawExt = displayName.substringAfterLast('.', "").lowercase().takeIf { it.length in 1..8 && it !in setOf("bin", "tmp") }
            ?: fileName.substringAfterLast('.', "").lowercase().takeIf { it.length in 1..8 && it !in setOf("bin", "tmp") }
        if (rawExt != null && rawExt in setOf("pdf", "epub", "docx", "pptx", "txt", "md", "html", "rtf", "png", "jpg", "jpeg")) {
            return rawExt
        }

        val mime = mimeType.lowercase()
        when {
            mime.contains("pdf") -> return "pdf"
            mime.contains("epub") -> return "epub"
            mime.contains("wordprocessingml") || mime.contains("msword") -> return "docx"
            mime.contains("presentationml") || mime.contains("powerpoint") -> return "pptx"
            mime.contains("plain") || mime.contains("text") -> return "txt"
            mime.contains("html") -> return "html"
        }

        val label = sourceLabel.trim().lowercase()
        when {
            label == "pdf" -> return "pdf"
            label == "epub" -> return "epub"
            label == "docx" || label == "word" -> return "docx"
            label == "pptx" || label == "powerpoint" -> return "pptx"
            label == "txt" || label == "text" || label == "plain" -> return "txt"
            label == "md" || label == "markdown" -> return "md"
        }

        return "txt"
    }

    fun detectExtension(file: File, sourceLabel: String = "", mimeType: String = ""): String {
        val labelExt = detectExtensionFromNameOrType(file.name, sourceLabel, mimeType)
        if (labelExt != "txt" || sourceLabel.isNotBlank() || mimeType.isNotBlank()) {
            val labelLower = sourceLabel.lowercase()
            if (labelLower in setOf("pdf", "epub", "docx", "pptx", "txt")) return labelLower
        }

        val nameExt = file.name.substringAfterLast('.', "").lowercase()
        if (nameExt in setOf("pdf", "epub", "docx", "pptx", "txt", "png", "jpg", "jpeg", "webp")) {
            return nameExt
        }

        return runCatching {
            file.inputStream().use { input ->
                val bytes = ByteArray(4)
                val read = input.read(bytes)
                if (read >= 4) {
                    if (bytes[0] == '%'.code.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'D'.code.toByte() && bytes[3] == 'F'.code.toByte()) {
                        return "pdf"
                    }
                    if (bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() && bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()) {
                        file.inputStream().use { zipStream ->
                            val zip = ZipInputStream(zipStream)
                            var count = 0
                            while (count < 20) {
                                val entry = zip.nextEntry ?: break
                                val name = entry.name.lowercase()
                                if (name.contains("word/")) return "docx"
                                if (name.contains("ppt/")) return "pptx"
                                if (name.contains("epub") || name.contains("oebps") || name.contains("container.xml")) return "epub"
                                count++
                            }
                        }
                        return "epub"
                    }
                }
            }
            if (sourceLabel.isNotBlank()) detectExtensionFromNameOrType("", sourceLabel, mimeType) else "pdf"
        }.getOrDefault(if (sourceLabel.isNotBlank()) detectExtensionFromNameOrType("", sourceLabel, mimeType) else "pdf")
    }

    fun getShareableUri(document: SavedDocument): Uri? {
        val shareDir = File(appContext.cacheDir, "shared_documents").apply { mkdirs() }
        runCatching {
            shareDir.listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 3600_000L }?.forEach { it.delete() }
        }
        val sanitizedTitle = document.title
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .ifBlank { "Vern_Document" }
            .take(64)

        val original = originalFile(document)
        if (original != null && original.exists()) {
            val ext = detectExtension(original, document.sourceLabel, document.originalMimeType)
            val shareFile = File(shareDir, "$sanitizedTitle.$ext")
            return runCatching {
                original.copyTo(shareFile, overwrite = true)
                FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", shareFile)
            }.getOrNull() ?: originalUri(document)
        }

        // If the original file does not exist on disk, synthesize an export file with its original format extension
        val ext = detectExtensionFromNameOrType("", document.sourceLabel, document.originalMimeType, document.originalFileName)
        val shareFile = File(shareDir, "$sanitizedTitle.$ext")
        return runCatching {
            val docFile = File(docsDir, document.fileName)
            val textContent = if (docFile.exists()) docFile.readText(Charsets.UTF_8) else document.title
            shareFile.writeText(textContent, Charsets.UTF_8)
            FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", shareFile)
        }.getOrNull() ?: originalUri(document)
    }

    fun getShareMimeType(document: SavedDocument): String {
        val original = originalFile(document)
        val ext = if (original != null) detectExtension(original, document.sourceLabel, document.originalMimeType)
                  else detectExtensionFromNameOrType(document.originalFileName, document.sourceLabel, document.originalMimeType)
        return when (ext) {
            "pdf" -> "application/pdf"
            "epub" -> "application/epub+zip"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt", "md" -> "text/plain"
            "html" -> "text/html"
            else -> document.originalMimeType.ifBlank {
                when (document.sourceLabel.uppercase()) {
                    "PDF" -> "application/pdf"
                    "EPUB" -> "application/epub+zip"
                    "DOCX" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                    "PPTX" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                    else -> "text/plain"
                }
            }
        }
    }

    private fun saveOriginalFileReference(
        documentId: String,
        uri: Uri,
        displayName: String,
        sourceLabel: String = "",
        mimeType: String = ""
    ): Result<String> {
        // Always copy the original file to the app sandbox to guarantee persistent access 
        // across app sessions, background workers, and different UI components.
        val detectedExt = detectExtensionFromNameOrType(displayName, sourceLabel, mimeType)
        val initialSafeExt = detectedExt.ifBlank { "bin" }
        val fileName = "$documentId.$initialSafeExt"
        val target = File(originalsDir, fileName)
        return try {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return Result.failure(IllegalStateException("Could not open original file for reading."))

            // If extension was defaulted to bin or txt, sniff magic bytes and rename to genuine format
            val finalFileName = if (initialSafeExt == "bin" || initialSafeExt == "txt") {
                val detected = detectExtension(target, sourceLabel, mimeType)
                if (detected != "bin" && detected != initialSafeExt) {
                    val renamed = File(originalsDir, "$documentId.$detected")
                    if (target.renameTo(renamed)) "$documentId.$detected" else fileName
                } else fileName
            } else fileName

            Result.success(finalFileName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy original file $displayName to $fileName", e)
            runCatching { target.delete() }
            Result.failure(e)
        }
    }

    fun hasSeenOnboardingTutorial(): Boolean {
        return prefs.getBoolean(KEY_ONBOARDING_TUTORIAL_SEEN, false)
    }

    fun markOnboardingTutorialSeen() {
        prefs.edit { putBoolean(KEY_ONBOARDING_TUTORIAL_SEEN, true) }
    }

    fun resetOnboardingState() {
        prefs.edit {
            putBoolean(KEY_ONBOARDING_TUTORIAL_SEEN, false)
            putBoolean(KEY_QUEST_CELEBRATED, false)
            putBoolean(KEY_QUEST_CHECKLIST_DISMISSED, false)
        }
    }

    fun loadUserName(): String {
        return prefs.getString(KEY_USER_NAME, "")?.trim().orEmpty()
    }

    fun saveUserName(name: String) {
        prefs.edit { putString(KEY_USER_NAME, name.trim().take(48)) }
    }

    fun loadReadingInterest(): String {
        return prefs.getString(KEY_READING_INTEREST, "Books & Novels") ?: "Books & Novels"
    }

    fun saveReadingInterest(interest: String) {
        prefs.edit { putString(KEY_READING_INTEREST, interest) }
    }

    fun markOnboardingComplete(name: String) {
        prefs.edit {
            putBoolean(KEY_ONBOARDING_TUTORIAL_SEEN, true)
            putString(KEY_USER_NAME, name.trim().take(48))
        }
    }

    fun loadQuestProgress(): QuestProgress {
        return QuestProgress(
            tourDone = prefs.getBoolean(KEY_QUEST_TOUR_DONE, false),
            importDone = prefs.getBoolean(KEY_QUEST_IMPORT_DONE, false),
            speedDone = prefs.getBoolean(KEY_QUEST_SPEED_DONE, false),
            bookmarkDone = prefs.getBoolean(KEY_QUEST_BOOKMARK_DONE, false)
        )
    }

    fun saveQuestProgress(tour: Boolean, import: Boolean, speed: Boolean, bookmark: Boolean) {
        prefs.edit {
            putBoolean(KEY_QUEST_TOUR_DONE, tour)
            putBoolean(KEY_QUEST_IMPORT_DONE, import)
            putBoolean(KEY_QUEST_SPEED_DONE, speed)
            putBoolean(KEY_QUEST_BOOKMARK_DONE, bookmark)
        }
    }

    fun isQuestChecklistDismissed(): Boolean {
        return prefs.getBoolean(KEY_QUEST_CHECKLIST_DISMISSED, false)
    }

    fun setQuestChecklistDismissed(dismissed: Boolean) {
        prefs.edit { putBoolean(KEY_QUEST_CHECKLIST_DISMISSED, dismissed) }
    }

    fun hasCelebratedQuests(): Boolean {
        return prefs.getBoolean(KEY_QUEST_CELEBRATED, false)
    }

    fun markQuestsCelebrated() {
        prefs.edit { putBoolean(KEY_QUEST_CELEBRATED, true) }
    }

    fun hasImportedOrOpenedDocument(): Boolean {
        return prefs.getBoolean(KEY_HAS_IMPORTED_OR_OPENED_DOCUMENT, loadDocuments().isNotEmpty())
    }

    fun markImportedOrOpenedDocument() {
        prefs.edit { putBoolean(KEY_HAS_IMPORTED_OR_OPENED_DOCUMENT, true) }
    }

    fun loadDocumentOutline(document: SavedDocument, chunks: List<String>): List<VeritasDocumentOutlineEntry> {
        val original = originalFile(document) ?: return emptyList()
        // Sniff the header rather than trusting the name or the mime type. Originals
        // are stored as "<uuid>.bin" and originalMimeType is empty on every document,
        // so all three of the old checks failed for every file — which meant the
        // embedded table of contents was never read for any PDF, and the reader always
        // fell back to guessing headings out of the prose.
        val isPdf = runCatching {
            original.inputStream().use { input ->
                val header = ByteArray(5)
                input.read(header) == 5 && header.decodeToString() == "%PDF-"
            }
        }.getOrDefault(false)
        if (!isPdf) return emptyList()
        return runCatching {
            PDFBoxResourceLoader.init(appContext)
            val memorySetting = com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(10L * 1024 * 1024).apply {
                setTempDir(File(appContext.cacheDir, "pdfbox_temp").apply { mkdirs() })
            }
            PDDocument.load(original, memorySetting).use { pdf ->
                val outline = pdf.documentCatalog.documentOutline ?: return@use emptyList()
                val textModel = runCatching {
                    ReaderTextModelCache.get(document.id, readText(document), pdf.numberOfPages)
                }.getOrNull()
                val entries = mutableListOf<VeritasDocumentOutlineEntry>()
                var child = outline.firstChild
                while (child != null) {
                    collectPdfOutlineEntries(pdf, child, chunks, 0, entries, textModel)
                    child = child.nextSibling
                }
                entries
            }
        }.getOrElse { error ->
            Log.w(TAG, "Could not read PDF outline for ${document.id}: ${error.message}")
            emptyList()
        }
    }

    private fun collectPdfOutlineEntries(
        pdf: PDDocument,
        item: PDOutlineItem,
        chunks: List<String>,
        level: Int,
        entries: MutableList<VeritasDocumentOutlineEntry>,
        textModel: ReaderTextModel? = null
    ) {
        val title = cleanTocTitle(item.title.orEmpty())
        val pageIndex = pdfOutlinePageIndex(item, pdf)
        if (title.isNotBlank() && !title.all { it == '.' || it.isWhitespace() || it == '•' || it == '·' }) {
            entries.add(
                VeritasDocumentOutlineEntry(
                    title = title.take(120),
                    targetIndex = outlineTargetIndex(pageIndex, pdf.numberOfPages, chunks, textModel),
                    pageNumber = pageIndex?.plus(1),
                    level = level.coerceIn(0, 6),
                    source = "PDF table of contents"
                )
            )
        }
        var child = item.firstChild
        while (child != null) {
            collectPdfOutlineEntries(pdf, child, chunks, level + 1, entries, textModel)
            child = child.nextSibling
        }
    }

    private fun pdfOutlinePageIndex(item: PDOutlineItem, pdDoc: PDDocument): Int? {
        return runCatching {
            var dest = item.destination
            if (dest == null && item.action is PDActionGoTo) {
                dest = (item.action as PDActionGoTo).destination
            }
            if (dest is PDPageDestination) {
                val p = dest.page
                if (p != null) {
                    val idx = pdDoc.pages.indexOf(p)
                    if (idx >= 0) return idx
                }
                val pageNumber = dest.pageNumber
                if (pageNumber >= 0) return pageNumber
            } else if (dest is PDNamedDestination) {
                val pageDest = pdDoc.documentCatalog.findNamedDestinationPage(dest)
                if (pageDest is PDPageDestination) {
                    val p = pageDest.page
                    if (p != null) {
                        val idx = pdDoc.pages.indexOf(p)
                        if (idx >= 0) return idx
                    }
                }
            }
            null
        }.getOrNull()
    }

    private fun outlineTargetIndex(
        pageIndex: Int?,
        pageCount: Int,
        chunks: List<String>,
        textModel: ReaderTextModel? = null
    ): Int {
        if (chunks.isEmpty()) return 0
        if (pageIndex != null && textModel != null) {
            val targetPage = pageIndex + 1
            val matchingSentence = textModel.sentences.firstOrNull { it.pageNumber == targetPage }
                ?: textModel.sentences.firstOrNull { it.pageNumber > targetPage }
            if (matchingSentence != null && matchingSentence.index in chunks.indices) {
                return matchingSentence.index
            }
        }
        val page = pageIndex ?: 0
        val denominator = (pageCount - 1).coerceAtLeast(1)
        return ((page.toFloat() / denominator.toFloat()) * chunks.lastIndex.toFloat())
            .roundToInt()
            .coerceIn(0, chunks.lastIndex)
    }

    fun updateProgress(documentId: String, currentIndex: Int, chunkCount: Int): List<SavedDocument> {
        val now = System.currentTimeMillis()
        val updated = loadDocuments().map { doc ->
            if (doc.id == documentId) {
                val safeIndex = if (chunkCount <= 0) 0 else currentIndex.coerceIn(0, chunkCount - 1)
                doc.copy(currentIndex = safeIndex, chunkCount = chunkCount, updatedAt = now)
            } else {
                doc
            }
        }
        saveDocuments(updated)
        return loadDocuments()
    }

    fun clearProgress(documentId: String): List<SavedDocument> {
        val updated = loadDocuments().map { doc ->
            if (doc.id == documentId) doc.copy(currentIndex = 0) else doc
        }
        saveDocuments(updated)
        return loadDocuments()
    }

    fun deleteDocument(documentId: String): List<SavedDocument> {
        ReaderTextModelCache.invalidate(documentId)
        val docs = loadDocuments()
        docs.firstOrNull { it.id == documentId }?.let { doc ->
            runCatching { File(docsDir, doc.fileName).delete() }
            originalFile(doc)?.let { runCatching { it.delete() } }
            CoverExtractor.deleteCover(appContext, documentId)
        }
        val updated = docs.filterNot { it.id == documentId }
        saveDocuments(updated)
        runCatching { dbHelper.deleteDocumentById(documentId) }
        removeFromQueue(documentId)
        // Keep annotations, document notes, and reading history intact so they can still be viewed in Study tab
        saveReadingListCatalog(loadReadingListCatalog().removeDocumentEverywhere(documentId))
        return updated
    }

    fun renameDocument(documentId: String, newTitle: String): List<SavedDocument> {
        val cleanTitle = newTitle.trim().ifBlank { "Untitled reading" }
        val now = System.currentTimeMillis()
        val updated = loadDocuments().map { doc ->
            if (doc.id == documentId) doc.copy(title = cleanTitle, updatedAt = now) else doc
        }
        saveDocuments(updated)
        return loadDocuments()
    }

    fun toggleFavorite(documentId: String): List<SavedDocument> {
        val now = System.currentTimeMillis()
        val updated = loadDocuments().map { doc ->
            if (doc.id == documentId) doc.copy(favorite = !doc.favorite, updatedAt = now) else doc
        }
        saveDocuments(updated)
        return loadDocuments()
    }

    fun setCollection(documentId: String, collectionName: String): List<SavedDocument> {
        val cleanCollection = collectionName.trim()
        val now = System.currentTimeMillis()
        val updated = loadDocuments().map { doc ->
            if (doc.id == documentId) doc.copy(collection = cleanCollection, updatedAt = now) else doc
        }
        saveDocuments(updated)
        return loadDocuments()
    }

    fun loadReaderSettings(): ReaderSettings {
        val raw = prefs.getString(KEY_READER_SETTINGS, null) ?: return ReaderSettings()
        return runCatching { ReaderSettings.fromJson(JSONObject(raw)) }.getOrDefault(ReaderSettings())
    }

    fun saveReaderSettings(settings: ReaderSettings): ReaderSettings {
        val normalized = settings.copy(
            fontSizeSp = settings.fontSizeSp.coerceIn(10, 28),
            sectionSpacingDp = settings.sectionSpacingDp.coerceIn(6, 24),
            themeId = VeritasThemeCatalog.normalizeThemeId(settings.themeId),
            themePackId = VeritasThemePackCatalog.normalizePackId(settings.themePackId)
        )
        prefs.edit { putString(KEY_READER_SETTINGS, normalized.toJson().toString()) }
        PlaybackStateStore.autoPlayQueue = normalized.autoPlayQueue
        updateVeritasWidgets(appContext)
        return normalized
    }

    fun loadVoiceSettings(): VoiceSettings {
        val raw = prefs.getString(KEY_VOICE_SETTINGS, null) ?: return VoiceSettings()
        val loaded = runCatching { VoiceSettings.fromJson(JSONObject(raw)) }.getOrDefault(VoiceSettings())
        val detectedEngine = VoiceManager.engineForVoice(loaded.voiceName)
        return if (detectedEngine != null && !VoiceManager.isVeritasEngine(loaded.enginePackage)) {
            loaded.copy(
                enginePackage = detectedEngine,
                engineLabel = if (detectedEngine == VoiceManager.VERITAS_LITE) "Vern Lite" else "Vern Studio"
            )
        } else {
            loaded
        }
    }

    fun saveVoiceSettings(settings: VoiceSettings): VoiceSettings {
        val detectedEngine = VoiceManager.engineForVoice(settings.voiceName)
        val resolvedEnginePackage = if (detectedEngine != null && !VoiceManager.isVeritasEngine(settings.enginePackage)) {
            detectedEngine
        } else {
            settings.enginePackage
        }
        val resolvedEngineLabel = if (detectedEngine != null && !VoiceManager.isVeritasEngine(settings.enginePackage)) {
            if (detectedEngine == VoiceManager.VERITAS_LITE) "Vern Lite" else "Vern Studio"
        } else {
            settings.engineLabel.ifBlank { "System default" }
        }
        val normalized = settings.copy(
            enginePackage = resolvedEnginePackage,
            engineLabel = resolvedEngineLabel,
            voiceLabel = settings.voiceLabel.ifBlank { "System default voice" },
            profileName = settings.profileName.ifBlank { "Balanced" },
            preferredRate = settings.preferredRate.coerceIn(0.5f, 2.0f),
            preferredPitch = settings.preferredPitch.coerceIn(0.7f, 1.4f)
        )
        prefs.edit { putString(KEY_VOICE_SETTINGS, normalized.toJson().toString()) }
        return normalized
    }

    fun loadNarrationSettings(): NarrationSettings {
        val raw = prefs.getString(KEY_NARRATION_SETTINGS, null) ?: return NarrationSettings()
        return runCatching { NarrationSettings.fromJson(JSONObject(raw)) }.getOrDefault(NarrationSettings())
    }

    fun saveNarrationSettings(settings: NarrationSettings): NarrationSettings {
        val normalized = settings.copy(
            narratorRateMultiplier = settings.narratorRateMultiplier.coerceIn(0.75f, 1.25f),
            narratorPitchMultiplier = settings.narratorPitchMultiplier.coerceIn(0.80f, 1.25f),
            dialogueRateMultiplier = settings.dialogueRateMultiplier.coerceIn(0.75f, 1.25f),
            dialoguePitchMultiplier = settings.dialoguePitchMultiplier.coerceIn(0.80f, 1.25f)
        )
        prefs.edit { putString(KEY_NARRATION_SETTINGS, normalized.toJson().toString()) }
        return normalized
    }

    fun loadAskAiSettings(): AskAiSettings {
        val raw = prefs.getString(KEY_ASK_AI_SETTINGS, null) ?: return AskAiSettings()
        return runCatching { AskAiSettings.fromJson(JSONObject(raw)) }.getOrDefault(AskAiSettings())
    }

    fun saveAskAiSettings(settings: AskAiSettings): AskAiSettings {
        val normalized = settings.copy(
            assistantId = settings.assistantId.ifBlank { "chooser" },
            assistantLabel = settings.assistantLabel.ifBlank { "Choose each time" },
            promptTemplate = settings.promptTemplate.ifBlank { "Answer clearly using this selected Vern text:\n\n{selection}" }
        )
        prefs.edit { putString(KEY_ASK_AI_SETTINGS, normalized.toJson().toString()) }
        return normalized
    }

    /**
     * Writes a JSON payload to [key] while preserving the previous value as a "last known
     * good" backup. If a later write is ever corrupt/partial, [readResilientJsonArray] can
     * recover from the backup instead of the data silently vanishing.
     */
    internal fun commitResilientJson(key: String, value: String) {
        val previous = prefs.getString(key, null)
        prefs.edit {
            if (!previous.isNullOrEmpty()) putString("${key}__bak", previous)
            putString(key, value)
        }
    }

    /**
     * Reads a JSON array from [key], verifying it parses. If the primary value is corrupt,
     * falls back to the last-known-good backup rather than returning empty — which would look
     * to the user like their library/notes had been wiped.
     */
    internal fun readResilientJsonArray(key: String): JSONArray {
        return ResilientJson.chooseArray(
            primary = prefs.getString(key, null),
            backup = prefs.getString("${key}__bak", null)
        )
    }

    internal fun saveDocuments(documents: List<SavedDocument>) {
        runCatching { dbHelper.replaceAllDocuments(documents) }
        val array = JSONArray()
        documents.forEach { array.put(it.toJson()) }
        commitResilientJson(KEY_DOCUMENTS, array.toString())
        updateVeritasWidgets(appContext)
    }

    internal fun saveQueueEntries(entries: List<QueueEntry>) {
        val array = JSONArray()
        entries.forEach { array.put(it.toJson()) }
        prefs.edit { putString(KEY_QUEUE, array.toString()) }
        PlaybackStateStore.queueCount = entries.size
    }

    internal fun saveReadingHistory(history: List<ReadingHistoryEntry>) {
        val array = JSONArray()
        history.take(MAX_READING_HISTORY).forEach { array.put(it.toJson()) }
        prefs.edit { putString(KEY_READING_HISTORY, array.toString()) }
        updateVeritasWidgets(appContext)
    }

    internal fun saveReadingListCatalog(catalog: VeritasReadingListCatalog): VeritasReadingListCatalog {
        val normalized = normalizeReadingListCatalog(catalog)
        prefs.edit { putString(KEY_READING_LISTS, normalized.toJsonArray().toString()) }
        return normalized
    }

    internal fun normalizeReadingListCatalog(catalog: VeritasReadingListCatalog): VeritasReadingListCatalog {
        val existingIds = loadDocuments().map { it.id }.toSet()
        val now = System.currentTimeMillis()
        return VeritasReadingListCatalog(
            lists = catalog.lists.map { list ->
                val filteredItems = VeritasReadingList.normalizeItems(
                    list.items.filter { item -> item.documentId in existingIds }
                )
                if (filteredItems == list.items) list else list.copy(items = filteredItems, updatedAt = now)
            }
        )
    }

    companion object {
        internal const val TAG = "DocumentRepository"
        internal const val MAX_READING_HISTORY = 40
        // Process-wide lock for read-modify-write of shared prefs counters that are touched
        // from multiple components/threads (UI + PlaybackService).
        internal val LIBRARY_WRITE_LOCK = Any()
        internal const val KEY_DOCUMENTS = "documents"
        internal const val KEY_QUEUE = "reading_queue"
        internal const val KEY_READING_LISTS = "reading_lists"
        internal const val KEY_READING_HISTORY = "reading_history"
        internal const val KEY_ANNOTATIONS = "reader_annotations"
        internal const val KEY_DOCUMENT_NOTES = "document_notes"
        internal const val KEY_PRONUNCIATION_RULES = "pronunciation_rules"
        internal const val KEY_READER_SETTINGS = "reader_settings"
        internal const val KEY_VOICE_SETTINGS = "voice_settings"
        internal const val KEY_NARRATION_SETTINGS = "narration_settings"
        internal const val KEY_ASK_AI_SETTINGS = "ask_ai_settings"
        internal const val KEY_AI_TEMPLATES = "ai_prompt_templates"
        internal const val KEY_AI_HISTORY = "ai_prompt_history"
        internal const val KEY_ONBOARDING_TUTORIAL_SEEN = "onboarding_tutorial_seen"
        internal const val KEY_USER_NAME = "user_name"
        internal const val KEY_READING_INTEREST = "reading_interest"
        internal const val KEY_HAS_IMPORTED_OR_OPENED_DOCUMENT = "has_imported_or_opened_document"
        internal const val KEY_QUEST_TOUR_DONE = "quest_tour_done"
        internal const val KEY_QUEST_IMPORT_DONE = "quest_import_done"
        internal const val KEY_QUEST_SPEED_DONE = "quest_speed_done"
        internal const val KEY_QUEST_BOOKMARK_DONE = "quest_bookmark_done"
        internal const val KEY_QUEST_CHECKLIST_DISMISSED = "quest_checklist_dismissed"
        internal const val KEY_QUEST_CELEBRATED = "quest_celebrated"
        internal const val KEY_TRACKER_DAYS = "reader_tracker_days"
        internal const val KEY_TRACKER_COMPLETIONS = "reader_tracker_completions"
        internal const val MAX_TRACKER_DAYS = 370
        internal const val MAX_TRACKER_COMPLETIONS = 500
        internal const val MAX_TRACKER_SESSION_MILLIS = 12L * 60L * 60L * 1000L
    }
}
