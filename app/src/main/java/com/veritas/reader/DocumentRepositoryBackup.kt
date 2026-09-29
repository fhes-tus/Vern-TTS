package com.veritas.reader

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
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
    loadGeneralNotes().forEach { generalNotesArray.put(it.toJson()) }
    root.put("generalNotes", generalNotesArray)

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
        restoreBackupJson(buffered.readBytes().toString(Charsets.UTF_8), replaceExisting)
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
    return total
}

/**
 * Full backup: backup.json plus every stored original document and cover, so a
 * restore brings back Original View and covers — which the JSON-only backup
 * cannot. Can be large; callers should surface [estimateFullBackupBytes] first.
 */
fun DocumentRepository.writeFullBackupZip(output: OutputStream) {
    ZipOutputStream(BufferedOutputStream(output)).use { zip ->
        zip.putNextEntry(ZipEntry("backup.json"))
        zip.write(buildBackupJson().toByteArray(Charsets.UTF_8))
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
    }
}

internal fun DocumentRepository.restoreFullBackupZip(input: InputStream, replaceExisting: Boolean): BackupRestoreResult {
    var backupJson: String? = null
    val stagedOriginals = mutableListOf<Pair<String, File>>()
    val stagedCovers = mutableListOf<Pair<String, File>>()
    val stagingDir = File(appContext.cacheDir, "restore_staging_${System.currentTimeMillis()}").apply { mkdirs() }
    try {
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name.trimStart('/')
                when {
                    name == "backup.json" -> backupJson = zip.readBytes().toString(Charsets.UTF_8)
                    name.startsWith("originals/") -> {
                        // File(...).name strips any path segments — zip-slip guard.
                        val fileName = File(name).name
                        if (fileName.isNotBlank()) {
                            val temp = File(stagingDir, "orig_${stagedOriginals.size}")
                            temp.outputStream().use { zip.copyTo(it) }
                            stagedOriginals.add(fileName to temp)
                        }
                    }
                    name.startsWith("covers/") -> {
                        val fileName = File(name).name
                        if (fileName.isNotBlank()) {
                            val temp = File(stagingDir, "cover_${stagedCovers.size}")
                            temp.outputStream().use { zip.copyTo(it) }
                            stagedCovers.add(fileName to temp)
                        }
                    }
                }
            }
        }
        val json = backupJson
            ?: throw IllegalArgumentException("This zip does not contain a Vern backup.json.")
        val (result, idMap) = restoreBackupJsonWithMap(json, replaceExisting)
        // Originals are keyed by originalFileName (carried inside the restored
        // documents), so they drop straight into place.
        stagedOriginals.forEach { (fileName, temp) ->
            val target = File(originalsDir, fileName)
            if (!target.exists()) runCatching { temp.copyTo(target, overwrite = false) }
        }
        // Covers are keyed by document id — rename through the restore id map.
        stagedCovers.forEach { (fileName, temp) ->
            val oldId = fileName.substringBefore(".cover.")
            val suffix = fileName.removePrefix(oldId)
            val newName = "${idMap[oldId] ?: oldId}$suffix"
            val target = File(CoverExtractor.coversDir(appContext), newName)
            if (!target.exists()) runCatching { temp.copyTo(target, overwrite = false) }
        }
        return result
    } finally {
        runCatching { stagingDir.deleteRecursively() }
    }
}

internal fun DocumentRepository.restoreBackupJsonWithMap(rawJson: String, replaceExisting: Boolean = false): Pair<BackupRestoreResult, Map<String, String>> {
    val root = runCatching { JSONObject(rawJson) }
        .getOrElse { throw IllegalArgumentException("This is not a valid Vern backup file.") }

    val documentArray = root.optJSONArray("documents")
        ?: throw IllegalArgumentException("The backup does not contain a documents section.")

    if (replaceExisting) {
        loadDocuments().forEach { document ->
            runCatching { File(docsDir, document.fileName).delete() }
            runCatching { originalFile(document)?.delete() }
            runCatching { CoverExtractor.deleteCover(appContext, document.id) }
        }
    }

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

        val fileName = existingMatch?.fileName ?: "$newId.txt"
        File(docsDir, fileName).writeText(text, Charsets.UTF_8)
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
            val existing = if (replaceExisting) emptyList() else loadGeneralNotes()
            // Existing notes win on id collision (they may be newer edits).
            saveGeneralNotes((existing + imported).distinctBy { it.id })
            importedGeneralNotes = imported.size
        }
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