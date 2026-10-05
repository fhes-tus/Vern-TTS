package com.veritas.reader

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

fun DocumentRepository.buildBackupJson(): String {
    val root = JSONObject()
        .put("schema", "veritas.reader.backup.v1")
        .put("createdAt", System.currentTimeMillis())
        .put(
            "appVersion",
            runCatching {
                appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
            }.getOrNull() ?: "unknown"
        )
        .put("syncPeer", "android")

    val documentArray = JSONArray()
    loadDocuments().forEach { document ->
        documentArray.put(
            document.toJson()
                .put("text", readText(document))
        )
    }
    root.put("documents", documentArray)

    val queueArray = JSONArray()
    loadQueueEntries().forEach { queueArray.put(it.toJson()) }
    root.put("queue", queueArray)

    root.put("readingLists", loadReadingListCatalog().toJsonArray())

    val readingHistoryArray = JSONArray()
    loadReadingHistory().forEach { readingHistoryArray.put(it.toJson()) }
    root.put("readingHistory", readingHistoryArray)

    val annotationArray = JSONArray()
    loadAllAnnotations().forEach { annotationArray.put(it.toJson()) }
    root.put("annotations", annotationArray)

    val documentNotesArray = JSONArray()
    loadDocumentNotes().forEach { (documentId, note) ->
        documentNotesArray.put(
            JSONObject()
                .put("documentId", documentId)
                .put("note", note)
        )
    }
    root.put("documentNotes", documentNotesArray)

    val pronunciationArray = JSONArray()
    loadPronunciationRules().forEach { pronunciationArray.put(it.toJson()) }
    root.put("pronunciationRules", pronunciationArray)

    root.put("notesSettings", com.veritas.reader.ui.NotesSettingsStore.load(appContext).toJson())
    root.put("readerSettings", loadReaderSettings().toJson())
    root.put("voiceSettings", loadVoiceSettings().toJson())
    root.put("narrationSettings", loadNarrationSettings().toJson())
    root.put("askAiSettings", loadAskAiSettings().toJson())

    val aiTemplateArray = JSONArray()
    loadAiPromptTemplates().forEach { aiTemplateArray.put(it.toJson()) }
    root.put("aiPromptTemplates", aiTemplateArray)

    val aiHistoryArray = JSONArray()
    loadAiPromptHistory().forEach { aiHistoryArray.put(it.toJson()) }
    root.put("aiPromptHistory", aiHistoryArray)

    // General Notes: includes vocabulary automatically — vocab entries are
    // stored as hidden notes titled "__vocab__<documentId>".
    val generalNotesArray = JSONArray()
    (loadGeneralNotes() + loadTrashedGeneralNotes()).distinctBy { it.id }.forEach { generalNotesArray.put(it.toJson()) }
    root.put("generalNotes", generalNotesArray)
    val notebooksArray = JSONArray()
    loadNoteNotebooks().forEach { notebooksArray.put(it.toJson()) }
    root.put("noteNotebooks", notebooksArray)
    val revisionsArray = JSONArray()
    loadAllNoteRevisions().forEach { revision ->
        revisionsArray.put(JSONObject().put("noteId", revision.noteId).put("savedAt", revision.savedAt).put("snapshot", revision.snapshot.toJson()))
    }
    root.put("noteRevisions", revisionsArray)

    // Per-day reading data behind streaks, the heatmap, and weekly stats.
    val trackerDaysArray = JSONArray()
    loadTrackerDays().values.forEach { trackerDaysArray.put(it.toJson()) }
    root.put("trackerDays", trackerDaysArray)

    // Spaced-repetition deck including scheduling state.
    val flashcardsArray = JSONArray()
    loadAllFlashcards().forEach { flashcardsArray.put(it.toJson()) }
    root.put("flashcards", flashcardsArray)

    // Current month's per-document reading time (Time Allocation donut).
    val readingTimes = JSONObject()
    loadDocReadingTimes().forEach { (docId, millis) -> readingTimes.put(docId, millis) }
    root.put("docReadingTimesThisMonth", readingTimes)
    return root.toString(2)
}

fun DocumentRepository.restoreBackupJson(rawJson: String, replaceExisting: Boolean = false): BackupRestoreResult =
    restoreBackupJsonWithMap(rawJson, replaceExisting).first

/** Restores either a plain JSON backup or a full .zip backup (sniffed by PK magic). */
fun DocumentRepository.restoreBackupAuto(input: InputStream, replaceExisting: Boolean = false): BackupRestoreResult {
    val buffered = BufferedInputStream(input)
    buffered.mark(4)
    val magic = ByteArray(4)
    val read = buffered.read(magic)
    buffered.reset()
    val isZip = read >= 2 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()
    return if (isZip) {
        restoreFullBackupZip(buffered, replaceExisting)
    } else {
        val bytes = ByteArrayOutputStream()
        BackupRestoreSafety.copyBounded(buffered, bytes, BackupRestoreSafety.MAX_JSON_BYTES)
        restoreBackupJson(bytes.toString(Charsets.UTF_8.name()), replaceExisting)
    }
}

/** Rough size of a full backup so the UI can warn before writing a large zip. */
fun DocumentRepository.estimateFullBackupBytes(): Long {
    var total = 0L
    loadDocuments().forEach { doc ->
        total += File(docsDir, doc.fileName).length()
        originalFile(doc)?.let { total += it.length() }
        CoverExtractor.coverFile(appContext, doc.id)?.let { total += it.length() }
    }
    total += backedUpNoteMedia().sumOf { it.second.length() }
    return total
}

/** Only referenced files owned by the notes store are included, never arbitrary note paths. */
private fun DocumentRepository.backedUpNoteMedia(): List<Pair<String, File>> {
    val directory = File(appContext.filesDir, "notes_media").canonicalFile
    val noteVersions = (loadGeneralNotes() + loadTrashedGeneralNotes()).distinctBy { it.id } + loadAllNoteRevisions().map { it.snapshot }
    return noteVersions.flatMap { it.allImageUrls + it.allAudioUrls + it.allVideoUrls }.distinct()
        .mapNotNull { path ->
            val file = runCatching { File(path).canonicalFile }.getOrNull() ?: return@mapNotNull null
            if (file.isFile && file.parentFile == directory) path to file else null
        }
}

/**
 * Full backup: backup.json plus every stored original document and cover, so a
 * restore brings back Original View and covers — which the JSON-only backup
 * cannot. Can be large; callers should surface [estimateFullBackupBytes] first.
 */
fun DocumentRepository.writeFullBackupZip(output: OutputStream) {
    val noteMedia = backedUpNoteMedia()
    val root = JSONObject(buildBackupJson())
    val manifest = JSONArray()
    noteMedia.forEachIndexed { index, (path, file) -> manifest.put(JSONObject().put("path", path).put("fileName", "${index}_${file.name}")) }
    root.put("noteMedia", manifest)
    ZipOutputStream(BufferedOutputStream(output)).use { zip ->
        zip.putNextEntry(ZipEntry("backup.json"))
        zip.write(root.toString().toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        val seenOriginals = mutableSetOf<String>()
        loadDocuments().forEach { doc ->
            originalFile(doc)?.takeIf { it.exists() }?.let { file ->
                if (seenOriginals.add(file.name)) {
                    zip.putNextEntry(ZipEntry("originals/${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            CoverExtractor.coverFile(appContext, doc.id)?.let { cover ->
                zip.putNextEntry(ZipEntry("covers/${cover.name}"))
                cover.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        noteMedia.forEachIndexed { index, (_, file) ->
            zip.putNextEntry(ZipEntry("note_media/${index}_${file.name}"))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }

    }
}

internal fun DocumentRepository.restoreFullBackupZip(input: InputStream, replaceExisting: Boolean): BackupRestoreResult {
    var backupJson: String? = null
    val stagedOriginals = mutableListOf<Pair<String, File>>()
    val stagedCovers = mutableListOf<Pair<String, File>>()
    val stagedNoteMedia = mutableMapOf<String, File>()
    val stagingDir = File(appContext.cacheDir, "restore_staging_${System.currentTimeMillis()}").apply { mkdirs() }
    try {
        var entryCount = 0
        var expandedBytes = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entryCount <= BackupRestoreSafety.MAX_ENTRIES) { "The backup contains too many archive entries." }
                if (entry.isDirectory) {
                    expandedBytes += BackupRestoreSafety.copyBounded(zip, object : OutputStream() {
                        override fun write(value: Int) = Unit
                        override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
                    }, BackupRestoreSafety.MAX_ARCHIVE_BYTES - expandedBytes)
                    continue
                }
                val name = entry.name.trimStart('/')
                when {
                    name == "backup.json" -> {
                        require(backupJson == null) { "Duplicate backup.json in archive." }
                        val bytes = ByteArrayOutputStream()
                        expandedBytes += BackupRestoreSafety.copyBounded(zip, bytes,
                            minOf(BackupRestoreSafety.MAX_JSON_BYTES, BackupRestoreSafety.MAX_ARCHIVE_BYTES - expandedBytes))
                        backupJson = bytes.toString(Charsets.UTF_8.name())
                    }
                    name.startsWith("originals/") -> {
                        // File(...).name strips any path segments — zip-slip guard.
                        val fileName = name.removePrefix("originals/")
                        BackupRestoreSafety.requireFileName(fileName)
                        require(stagedOriginals.none { it.first == fileName }) { "Duplicate original in archive." }
                        if (fileName.isNotBlank()) {
                            val temp = File(stagingDir, "orig_${stagedOriginals.size}")
                            temp.outputStream().use { expandedBytes += BackupRestoreSafety.copyBounded(zip, it, BackupRestoreSafety.MAX_ARCHIVE_BYTES - expandedBytes) }
                            stagedOriginals.add(fileName to temp)
                        }
                    }
                    name.startsWith("note_media/") -> {
                        val fileName = name.removePrefix("note_media/")
                        BackupRestoreSafety.requireFileName(fileName)
                        require(fileName !in stagedNoteMedia) { "Duplicate note media in archive." }
                        val temp = File(stagingDir, "note_${stagedNoteMedia.size}")
                        temp.outputStream().use { expandedBytes += BackupRestoreSafety.copyBounded(zip, it, BackupRestoreSafety.MAX_ARCHIVE_BYTES - expandedBytes) }
                        stagedNoteMedia[fileName] = temp
                    }
                    name.startsWith("covers/") -> {
                        val fileName = name.removePrefix("covers/")
                        BackupRestoreSafety.requireFileName(fileName)
                        require(stagedCovers.none { it.first == fileName }) { "Duplicate cover in archive." }
                        if (fileName.isNotBlank()) {
                            val temp = File(stagingDir, "cover_${stagedCovers.size}")
                            temp.outputStream().use { expandedBytes += BackupRestoreSafety.copyBounded(zip, it, BackupRestoreSafety.MAX_ARCHIVE_BYTES - expandedBytes) }
                            stagedCovers.add(fileName to temp)
                        }
                    }
                    else -> expandedBytes += BackupRestoreSafety.copyBounded(zip, object : OutputStream() {
                        override fun write(value: Int) = Unit
                        override fun write(bytes: ByteArray, offset: Int, length: Int) = Unit
                    }, BackupRestoreSafety.MAX_ARCHIVE_BYTES - expandedBytes)
                }
            }
        }
        val json = backupJson
            ?: throw IllegalArgumentException("This zip does not contain a Vern backup.json.")
        val root = BackupRestoreSafety.parseAndValidate(json)
        val mediaTargets = mutableMapOf<String, File>()
        val pathMap = mutableMapOf<String, String>()
        root.optJSONArray("noteMedia")?.let { manifest ->
            for (i in 0 until manifest.length()) {
                val media = manifest.getJSONObject(i)
                val fileName = media.getString("fileName")
                require(fileName in stagedNoteMedia) { "A backed-up note attachment is missing." }
                val extension = fileName.substringAfterLast('.', "bin").takeIf { it.matches(Regex("[a-zA-Z0-9]{1,10}")) } ?: "bin"
                val target = File(appContext.filesDir, "notes_media/${UUID.randomUUID()}.$extension")
                mediaTargets[fileName] = target
                pathMap[media.getString("path")] = target.absolutePath
            }
        }
        root.optJSONArray("generalNotes")?.let { notes ->
            for (i in 0 until notes.length()) {
                val note = notes.getJSONObject(i)
                listOf("imageUrl", "audioUrl").forEach { key -> pathMap[note.optString(key)]?.let { note.put(key, it) } }
                var content = note.optString("content")
                pathMap.entries.sortedByDescending { it.key.length }.forEach { (old, target) -> content = content.replace(old, target) }
                note.put("content", content)
                note.optJSONArray("audioUrls")?.let { audios ->
                    for (j in 0 until audios.length()) pathMap[audios.optString(j)]?.let { audios.put(j, it) }
                }
            }
        }
        root.optJSONArray("noteRevisions")?.let { revisions ->
            for (i in 0 until revisions.length()) {
                val snapshot = revisions.optJSONObject(i)?.optJSONObject("snapshot") ?: continue
                listOf("imageUrl", "audioUrl").forEach { key -> pathMap[snapshot.optString(key)]?.let { snapshot.put(key, it) } }
                var content = snapshot.optString("content")
                pathMap.entries.sortedByDescending { it.key.length }.forEach { (old, target) -> content = content.replace(old, target) }
                snapshot.put("content", content)
                snapshot.optJSONArray("audioUrls")?.let { audios ->
                    for (j in 0 until audios.length()) pathMap[audios.optString(j)]?.let { audios.put(j, it) }
                }
            }
        }
        val (result, _) = restoreBackupJsonWithMap(root.toString(), replaceExisting) { files, idMap ->
        mediaTargets.forEach { (fileName, target) ->
            val temp = stagedNoteMedia.getValue(fileName)
            files.stage(target) { out -> temp.inputStream().use { it.copyTo(out) } }
        }
        // Originals are keyed by originalFileName (carried inside the restored
        // documents), so they drop straight into place.
        stagedOriginals.forEach { (fileName, temp) ->
            val target = File(originalsDir, fileName)
            if (replaceExisting || !target.exists()) files.stage(target) { out -> temp.inputStream().use { it.copyTo(out) } }
        }
        // Covers are keyed by document id — rename through the restore id map.
        stagedCovers.forEach { (fileName, temp) ->
            val oldId = fileName.substringBefore(".cover.")
            val suffix = fileName.removePrefix(oldId)
            val newName = "${idMap[oldId] ?: oldId}$suffix"
            val target = File(CoverExtractor.coversDir(appContext), newName)
            if (replaceExisting || !target.exists()) files.stage(target) { out -> temp.inputStream().use { it.copyTo(out) } }
        }
        }
        return result
    } finally {
        runCatching { stagingDir.deleteRecursively() }
    }
}

internal fun DocumentRepository.restoreBackupJsonWithMap(
    rawJson: String,
    replaceExisting: Boolean = false,
    stageAssets: (RestoreFileTransaction, Map<String, String>) -> Unit = { _, _ -> }
): Pair<BackupRestoreResult, Map<String, String>> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val root = BackupRestoreSafety.parseAndValidate(rawJson)
    val previousPreferences = prefs.all.toMap()
    val previousDocuments = loadDocuments()
    val database = dbHelper.writableDatabase
    RestoreFileTransaction(appContext.filesDir).use { files ->
        RestoreRecoveryJournal.prepare(files, previousPreferences, database)
        var metadataCommitted = false
        database.beginTransaction()
        try {
            val result = applyBackupJsonWithMap(root, replaceExisting, files, stageAssets)
            RestoreRecoveryJournal.markCommitted(files, prefs, database)
            database.setTransactionSuccessful()
            database.endTransaction()
            metadataCommitted = true
            files.commit()
            // Old assets remain available throughout restore. Only obsolete text is
            // cleaned up after a successful commit; originals may still be referenced.
            val retainedFiles = loadDocuments().map { it.fileName }.toSet()
            previousDocuments.filter { it.fileName !in retainedFiles }.forEach {
                runCatching { File(docsDir, it.fileName).delete() }
            }
            result
        } catch (error: Throwable) {
            if (metadataCommitted) throw error
            if (database.inTransaction()) database.endTransaction()
            val editor = prefs.edit().clear()
            previousPreferences.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            check(editor.commit()) { "Could not recover preferences after restore failure." }
            throw error
        }
    }
}

private fun DocumentRepository.applyBackupJsonWithMap(
    root: JSONObject,
    replaceExisting: Boolean,
    files: RestoreFileTransaction,
    stageAssets: (RestoreFileTransaction, Map<String, String>) -> Unit
): Pair<BackupRestoreResult, Map<String, String>> {

    val documentArray = root.optJSONArray("documents")
        ?: throw IllegalArgumentException("The backup does not contain a documents section.")

    val existingDocuments = if (replaceExisting) emptyList() else loadDocuments()
    val existingById = existingDocuments.associateBy { it.id }
    val usedIds = existingDocuments.map { it.id }.toMutableSet()
    val idMap = mutableMapOf<String, String>()
    val importedDocuments = mutableListOf<SavedDocument>()

    for (i in 0 until documentArray.length()) {
        val obj = documentArray.optJSONObject(i) ?: continue
        val originalId = obj.optString("id").trim()
        val incomingId = originalId.ifBlank { UUID.randomUUID().toString() }
        val text = obj.optString("text")
        if (text.isBlank()) continue

        val existingMatch = existingById[incomingId]
        var newId = when {
            existingMatch != null -> existingMatch.id
            incomingId !in usedIds -> incomingId
            else -> UUID.randomUUID().toString()
        }
        while (newId in usedIds && existingMatch?.id != newId) newId = UUID.randomUUID().toString()
        if (existingMatch?.id != newId) usedIds.add(newId)
        idMap[incomingId] = newId
        if (originalId.isNotBlank()) idMap[originalId] = newId

        // A new file preserves an existing document's text even when IDs collide.
        val fileName = "${UUID.randomUUID()}.txt"
        files.stage(File(docsDir, fileName)) { it.write(text.toByteArray(Charsets.UTF_8)) }
        val chunks = TextChunker.chunk(text)
        val base = runCatching { SavedDocument.fromJson(obj) }.getOrNull() ?: existingMatch
        val now = System.currentTimeMillis()
        val document = (base ?: SavedDocument(
            id = newId,
            title = obj.optString("title", "Restored reading"),
            fileName = fileName,
            sourceLabel = obj.optString("sourceLabel", "Backup"),
            createdAt = obj.optLong("createdAt", now),
            updatedAt = now,
            currentIndex = 0,
            chunkCount = chunks.size,
            charCount = text.length,
            preview = previewText(text)
        )).copy(
            id = newId,
            fileName = fileName,
            currentIndex = obj.optInt("currentIndex", 0).coerceIn(0, (chunks.size - 1).coerceAtLeast(0)),
            chunkCount = chunks.size,
            charCount = text.length,
            preview = previewText(text),
            updatedAt = now
        )
        importedDocuments.add(document)
    }

    val finalDocuments = (importedDocuments + existingDocuments).distinctBy { it.id }
    stageAssets(files, idMap)
    files.publish()
    saveDocuments(finalDocuments)

    val importedAnnotations = mutableListOf<ReaderAnnotation>()
    root.optJSONArray("annotations")?.let { array ->
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val annotation = ReaderAnnotation.fromJson(obj) ?: continue
            val mappedId = idMap[annotation.documentId] ?: continue
            importedAnnotations.add(annotation.copy(documentId = mappedId))
        }
    }
    val finalAnnotations = if (replaceExisting) {
        importedAnnotations
    } else {
        loadAllAnnotations() + importedAnnotations
    }.distinctBy { it.stableKey }
    saveAllAnnotations(finalAnnotations)

    val importedDocumentNotes = mutableMapOf<String, String>()
    root.optJSONArray("documentNotes")?.let { array ->
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val mappedId = idMap[obj.optString("documentId")] ?: continue
            val note = obj.optString("note").trim()
            if (note.isNotBlank()) importedDocumentNotes[mappedId] = note
        }
    }
    val finalDocumentNotes = if (replaceExisting) {
        importedDocumentNotes
    } else {
        loadDocumentNotes() + importedDocumentNotes
    }
    saveDocumentNotes(finalDocumentNotes)

    val importedQueue = mutableListOf<QueueEntry>()
    root.optJSONArray("queue")?.let { array ->
        for (i in 0 until array.length()) {
            val entry = QueueEntry.fromJson(array.optJSONObject(i) ?: continue)
            val mappedId = idMap[entry.documentId] ?: continue
            importedQueue.add(entry.copy(documentId = mappedId))
        }
    }
    val finalQueue = if (replaceExisting) importedQueue else loadQueueEntries() + importedQueue
    saveQueueEntries(finalQueue.distinctBy { it.documentId })

    val importedReadingLists = importReadingLists(root.optJSONArray("readingLists"), idMap, finalDocuments, replaceExisting)

    val importedHistory = mutableListOf<ReadingHistoryEntry>()
    root.optJSONArray("readingHistory")?.let { array ->
        for (i in 0 until array.length()) {
            val entry = ReadingHistoryEntry.fromJson(array.optJSONObject(i) ?: continue) ?: continue
            val mappedId = idMap[entry.documentId] ?: continue
            val mappedDocument = finalDocuments.firstOrNull { it.id == mappedId } ?: continue
            importedHistory.add(
                entry.copy(
                    documentId = mappedId,
                    title = mappedDocument.title,
                    sourceLabel = mappedDocument.sourceLabel,
                    currentIndex = entry.currentIndex.coerceIn(0, (mappedDocument.chunkCount - 1).coerceAtLeast(0)),
                    chunkCount = mappedDocument.chunkCount
                )
            )
        }
    }
    if (importedHistory.isNotEmpty() || replaceExisting) {
        val finalHistory = if (replaceExisting) importedHistory else importedHistory + loadReadingHistory()
        saveReadingHistory(finalHistory.sortedByDescending { it.openedAt }.distinctBy { it.documentId })
    }

    val importedRules = mutableListOf<PronunciationRule>()
    root.optJSONArray("pronunciationRules")?.let { array ->
        for (i in 0 until array.length()) {
            val rule = PronunciationRule.fromJson(array.optJSONObject(i) ?: continue) ?: continue
            importedRules.add(rule)
        }
    }
    if (importedRules.isNotEmpty()) {
        val finalRules = if (replaceExisting) importedRules else importedRules + loadPronunciationRules()
        savePronunciationRules(finalRules.distinctBy { it.id })
    }

    root.optJSONObject("notesSettings")?.let {
        com.veritas.reader.ui.NotesSettingsStore.save(appContext, com.veritas.reader.ui.NotesSettings.fromJson(it))
    }
    val restoredReaderSettings = root.optJSONObject("readerSettings")?.let {
        saveReaderSettings(ReaderSettings.fromJson(it)); true
    } ?: false
    val restoredVoiceSettings = root.optJSONObject("voiceSettings")?.let {
        saveVoiceSettings(VoiceSettings.fromJson(it)); true
    } ?: false
    root.optJSONObject("narrationSettings")?.let {
        saveNarrationSettings(NarrationSettings.fromJson(it))
    }
    root.optJSONObject("askAiSettings")?.let {
        saveAskAiSettings(AskAiSettings.fromJson(it))
    }

    root.optJSONArray("aiPromptTemplates")?.let { array ->
        val imported = mutableListOf<AiPromptTemplate>()
        for (i in 0 until array.length()) {
            AiPromptTemplate.fromJson(array.optJSONObject(i) ?: continue)?.let { imported.add(it) }
        }
        if (imported.isNotEmpty()) {
            saveAiPromptTemplates((loadAiPromptTemplates() + imported).distinctBy { it.id })
        }
    }

    root.optJSONArray("aiPromptHistory")?.let { array ->
        val imported = mutableListOf<AiPromptHistoryEntry>()
        for (i in 0 until array.length()) {
            AiPromptHistoryEntry.fromJson(array.optJSONObject(i) ?: continue)?.let { imported.add(it) }
        }
        if (imported.isNotEmpty()) {
            saveAiPromptHistory((loadAiPromptHistory() + imported).distinctBy { it.id }.sortedByDescending { it.createdAt }.take(50))
        }
    }

    // General Notes (includes hidden "__vocab__<docId>" vocabulary notes —
    // their titles are rewritten through the document id map so restored
    // vocab reattaches to the right documents even when ids were remapped).
    var importedGeneralNotes = 0
    root.optJSONArray("generalNotes")?.let { array ->
        val imported = mutableListOf<GeneralNote>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val note = runCatching { GeneralNote.fromJson(obj) }.getOrNull() ?: continue
            if (note.id.isBlank()) continue
            val remapped = if (note.title.startsWith("__vocab__")) {
                val oldDocId = note.title.removePrefix("__vocab__")
                note.copy(title = "__vocab__${idMap[oldDocId] ?: oldDocId}")
            } else note
            imported.add(remapped)
        }
        if (imported.isNotEmpty() || replaceExisting) {
            val existing = if (replaceExisting) emptyList() else loadGeneralNotes() + loadTrashedGeneralNotes()
            // Existing notes win on id collision (they may be newer edits).
            replaceStoredGeneralNotes((existing + imported).distinctBy { it.id })
            importedGeneralNotes = imported.size
        }
    }
    root.optJSONArray("noteNotebooks")?.let { array ->
        val imported = (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { runCatching { NoteNotebook.fromJson(it) }.getOrNull() } }
        val existing = if (replaceExisting) emptyList() else loadNoteNotebooks()
        saveNoteNotebooks((existing + imported).distinctBy { it.id })
    }
    root.optJSONArray("noteRevisions")?.let { array ->
        val imported = (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optString("noteId")
            val snapshot = item.optJSONObject("snapshot")?.let { runCatching { GeneralNote.fromJson(it) }.getOrNull() } ?: return@mapNotNull null
            if (id.isBlank() || snapshot.id != id) null else {
                val remapped = if (snapshot.title.startsWith("__vocab__")) {
                    val oldDocId = snapshot.title.removePrefix("__vocab__")
                    snapshot.copy(title = "__vocab__${idMap[oldDocId] ?: oldDocId}")
                } else snapshot
                NoteRevision(id, item.optLong("savedAt"), remapped)
            }
        }
        val existing = if (replaceExisting) emptyList() else loadAllNoteRevisions()
        saveNoteRevisions((existing + imported).distinctBy { "${it.noteId}:${it.savedAt}" })
    }

    // Tracker days: merge by date, keeping the richer record per day so a
    // restore never lowers an existing streak. Read-document ids are remapped.
    var importedTrackerDays = 0
    root.optJSONArray("trackerDays")?.let { array ->
        val imported = mutableListOf<ReaderTrackerDay>()
        for (i in 0 until array.length()) {
            val day = array.optJSONObject(i)?.let(ReaderTrackerDay::fromJson) ?: continue
            if (day.dateKey.isBlank()) continue
            imported.add(day.copy(readDocumentIds = day.readDocumentIds.map { idMap[it] ?: it }.toSet()))
        }
        if (imported.isNotEmpty()) {
            val merged = loadTrackerDays().toMutableMap()
            imported.forEach { day ->
                val existing = merged[day.dateKey]
                merged[day.dateKey] = if (existing == null) day else ReaderTrackerDay(
                    dateKey = day.dateKey,
                    appOpenCount = maxOf(existing.appOpenCount, day.appOpenCount),
                    usageMillis = maxOf(existing.usageMillis, day.usageMillis),
                    readDocumentIds = existing.readDocumentIds + day.readDocumentIds
                )
            }
            saveTrackerDays(merged.values)
            importedTrackerDays = imported.size
        }
    }

    // Flashcards: existing cards win on id collision (local scheduling state
    // is the live one); document references are remapped like annotations.
    var importedFlashcards = 0
    root.optJSONArray("flashcards")?.let { array ->
        val imported = mutableListOf<FlashcardProgress>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val card = runCatching { FlashcardProgress.fromJson(obj) }.getOrNull() ?: continue
            imported.add(card.copy(documentId = idMap[card.documentId] ?: card.documentId))
        }
        if (imported.isNotEmpty() || replaceExisting) {
            val existing = if (replaceExisting) emptyList() else loadAllFlashcards()
            saveAllFlashcards((existing + imported).distinctBy { it.id })
            importedFlashcards = imported.size
        }
    }

    // Monthly reading time: per-document max (not sum) so re-restoring the
    // same backup on the same device never double-counts.
    root.optJSONObject("docReadingTimesThisMonth")?.let { obj ->
        val current = loadDocReadingTimes().toMutableMap()
        obj.keys().forEach { docId ->
            val mapped = idMap[docId] ?: docId
            val incoming = obj.optLong(docId, 0L)
            if (incoming > (current[mapped] ?: 0L)) {
                recordDocReadingTime(mapped, incoming - (current[mapped] ?: 0L))
                current[mapped] = incoming
            }
        }
    }

    return BackupRestoreResult(
        documentCount = importedDocuments.size,
        annotationCount = importedAnnotations.size,
        queueCount = importedQueue.size,
        readingListCount = importedReadingLists,
        pronunciationRuleCount = importedRules.size,
        restoredReaderSettings = restoredReaderSettings,
        restoredVoiceSettings = restoredVoiceSettings,
        generalNoteCount = importedGeneralNotes,
        trackerDayCount = importedTrackerDays,
        flashcardCount = importedFlashcards
    ) to idMap
}

internal fun DocumentRepository.importReadingLists(
    array: JSONArray?,
    documentIdMap: Map<String, String>,
    finalDocuments: List<SavedDocument>,
    replaceExisting: Boolean
): Int {
    if (array == null) {
        if (replaceExisting) saveReadingListCatalog(VeritasReadingListCatalog())
        return 0
    }
    val finalDocumentIds = finalDocuments.map { it.id }.toSet()
    val existingCatalog = if (replaceExisting) VeritasReadingListCatalog() else loadReadingListCatalog()
    val usedListIds = existingCatalog.lists.map { it.id }.toMutableSet()
    val importedLists = mutableListOf<VeritasReadingList>()
    val sourceCatalog = VeritasReadingListCatalog.fromJsonArray(array)
    sourceCatalog.lists.forEach { list ->
        val targetId = uniqueReadingListId(list.id, usedListIds)
        usedListIds.add(targetId)
        val mappedItems = list.items.mapNotNull { item ->
            val mappedDocumentId = documentIdMap[item.documentId] ?: return@mapNotNull null
            if (mappedDocumentId !in finalDocumentIds) return@mapNotNull null
            item.copy(documentId = mappedDocumentId)
        }
        importedLists.add(
            list.copy(
                id = targetId,
                items = VeritasReadingList.normalizeItems(mappedItems),
                updatedAt = System.currentTimeMillis()
            )
        )
    }
    saveReadingListCatalog(VeritasReadingListCatalog(existingCatalog.lists + importedLists))
    return importedLists.size
}

internal fun uniqueReadingListId(preferredId: String, usedIds: Set<String>): String {
    var candidate = preferredId.trim().ifBlank { UUID.randomUUID().toString() }
    while (candidate in usedIds) candidate = UUID.randomUUID().toString()
    return candidate
}
