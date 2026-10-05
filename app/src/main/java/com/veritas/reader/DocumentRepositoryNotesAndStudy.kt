package com.veritas.reader

import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun DocumentRepository.loadAiPromptTemplates(): List<AiPromptTemplate> {
    val raw = prefs.getString(DocumentRepository.KEY_AI_TEMPLATES, "[]") ?: "[]"
    val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    val items = mutableListOf<AiPromptTemplate>()
    for (i in 0 until array.length()) {
        AiPromptTemplate.fromJson(array.optJSONObject(i) ?: continue)?.let { items.add(it) }
    }
    return items.sortedByDescending { it.createdAt }
}

fun DocumentRepository.saveAiPromptTemplates(templates: List<AiPromptTemplate>) {
    val array = JSONArray()
    templates.take(40).forEach { array.put(it.toJson()) }
    prefs.edit { putString(DocumentRepository.KEY_AI_TEMPLATES, array.toString()) }
}

fun DocumentRepository.addAiPromptTemplate(title: String, instruction: String): List<AiPromptTemplate> {
    val cleanInstruction = instruction.trim()
    if (cleanInstruction.isBlank()) return loadAiPromptTemplates()
    val template = AiPromptTemplate(
        id = UUID.randomUUID().toString(),
        title = title.trim().ifBlank { "Custom prompt" },
        instruction = cleanInstruction
    )
    saveAiPromptTemplates(listOf(template) + loadAiPromptTemplates())
    return loadAiPromptTemplates()
}

fun DocumentRepository.deleteAiPromptTemplate(id: String): List<AiPromptTemplate> {
    saveAiPromptTemplates(loadAiPromptTemplates().filterNot { it.id == id })
    return loadAiPromptTemplates()
}

fun DocumentRepository.loadAiPromptHistory(): List<AiPromptHistoryEntry> {
    val raw = prefs.getString(DocumentRepository.KEY_AI_HISTORY, "[]") ?: "[]"
    val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    val items = mutableListOf<AiPromptHistoryEntry>()
    for (i in 0 until array.length()) {
        AiPromptHistoryEntry.fromJson(array.optJSONObject(i) ?: continue)?.let { items.add(it) }
    }
    return items.sortedByDescending { it.createdAt }
}

internal fun DocumentRepository.saveAiPromptHistory(history: List<AiPromptHistoryEntry>) {
    val array = JSONArray()
    history.take(50).forEach { array.put(it.toJson()) }
    prefs.edit { putString(DocumentRepository.KEY_AI_HISTORY, array.toString()) }
}

fun DocumentRepository.addAiPromptHistory(documentTitle: String, promptType: String, scope: String, prompt: String): List<AiPromptHistoryEntry> {
    val preview = prompt.replace(Regex("\\s+"), " ").trim().take(420)
    if (preview.isBlank()) return loadAiPromptHistory()
    val entry = AiPromptHistoryEntry(
        id = UUID.randomUUID().toString(),
        documentTitle = documentTitle.ifBlank { "Untitled document" },
        promptType = promptType,
        scope = scope,
        promptPreview = preview
    )
    saveAiPromptHistory(listOf(entry) + loadAiPromptHistory())
    return loadAiPromptHistory()
}

fun DocumentRepository.clearAiPromptHistory(): List<AiPromptHistoryEntry> {
    saveAiPromptHistory(emptyList())
    return emptyList()
}

fun DocumentRepository.loadAnnotations(documentId: String): List<ReaderAnnotation> {
    return loadAllAnnotations()
        .filter { it.documentId == documentId }
        .sortedWith(compareBy<ReaderAnnotation> { it.chunkIndex }.thenBy { it.type.name })
}

fun DocumentRepository.loadAnnotationCount(): Int = loadAllAnnotations().size + loadDocumentNotes().size

fun DocumentRepository.loadAnnotationsForChunk(documentId: String, chunkIndex: Int): List<ReaderAnnotation> {
    return loadAnnotations(documentId).filter { it.chunkIndex == chunkIndex }
}

fun DocumentRepository.upsertAnnotation(
    documentId: String,
    chunkIndex: Int,
    type: AnnotationType,
    note: String = "",
    highlightColor: String? = null,
    selectionGroupId: String? = null,
    audioPath: String? = null,
    audioDurationSeconds: Int = 0,
    replaceAudio: Boolean = false
): List<ReaderAnnotation> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val now = System.currentTimeMillis()
    val existing = loadAllAnnotations().toMutableList()
    val index = existing.indexOfFirst { it.documentId == documentId && it.chunkIndex == chunkIndex && it.type == type }
    if (index >= 0) {
        val old = existing[index]
        existing[index] = old.copy(
            note = note,
            updatedAt = now,
            highlightColor = highlightColor ?: old.highlightColor,
            selectionGroupId = selectionGroupId ?: old.selectionGroupId,
            audioPath = if (replaceAudio) audioPath else audioPath ?: old.audioPath,
            audioDurationSeconds = if (replaceAudio) audioDurationSeconds else if (audioDurationSeconds > 0) audioDurationSeconds else old.audioDurationSeconds
        )
    } else {
        existing.add(
            ReaderAnnotation(
                documentId = documentId,
                chunkIndex = chunkIndex,
                type = type,
                note = note,
                createdAt = now,
                updatedAt = now,
                highlightColor = highlightColor,
                selectionGroupId = selectionGroupId,
                audioPath = audioPath,
                audioDurationSeconds = audioDurationSeconds
            )
        )
    }
    saveAllAnnotations(existing)
    return@synchronized loadAnnotations(documentId)
}

fun DocumentRepository.removeAnnotation(documentId: String, chunkIndex: Int, type: AnnotationType): List<ReaderAnnotation> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    saveAllAnnotations(loadAllAnnotations().filterNot {
        it.documentId == documentId && it.chunkIndex == chunkIndex && it.type == type
    })
    return@synchronized loadAnnotations(documentId)
}

fun DocumentRepository.removeAnnotations(stableKeys: Set<String>): List<ReaderAnnotation> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    if (stableKeys.isEmpty()) return@synchronized loadAllAnnotations()
    saveAllAnnotations(loadAllAnnotations().filterNot { it.stableKey in stableKeys })
    return@synchronized loadAllAnnotations()
}

fun DocumentRepository.toggleAnnotation(documentId: String, chunkIndex: Int, type: AnnotationType): List<ReaderAnnotation> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val exists = loadAllAnnotations().any { it.documentId == documentId && it.chunkIndex == chunkIndex && it.type == type }
    return@synchronized if (exists) removeAnnotation(documentId, chunkIndex, type) else upsertAnnotation(documentId, chunkIndex, type)
}

fun DocumentRepository.deleteAnnotationsForDocument(documentId: String): Unit = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    saveAllAnnotations(loadAllAnnotations().filterNot { it.documentId == documentId })
}

fun DocumentRepository.loadDocumentNote(documentId: String): String {
    return loadDocumentNotes()[documentId].orEmpty()
}

fun DocumentRepository.loadAllDocumentNotes(): Map<String, String> = loadDocumentNotes()

fun DocumentRepository.saveDocumentNote(documentId: String, note: String): String = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    if (findDocument(documentId) == null) return@synchronized ""
    val updated = loadDocumentNotes().toMutableMap()
    val cleanNote = note.trim()
    if (cleanNote.isBlank()) {
        updated.remove(documentId)
    } else {
        updated[documentId] = cleanNote
    }
    saveDocumentNotes(updated)
    return@synchronized loadDocumentNote(documentId)
}

fun DocumentRepository.deleteDocumentNote(documentId: String): Unit = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    saveDocumentNotes(loadDocumentNotes().filterKeys { it != documentId })
}

fun DocumentRepository.deleteDocumentNotes(documentIds: Set<String>): Map<String, String> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    if (documentIds.isEmpty()) return@synchronized loadDocumentNotes()
    saveDocumentNotes(loadDocumentNotes().filterKeys { it !in documentIds })
    return@synchronized loadDocumentNotes()
}

fun DocumentRepository.loadDocumentNoteAudio(documentId: String): List<Pair<String, Int>> {
    if (documentId.isBlank()) return emptyList()
    val raw = prefs.getString("doc_note_audio_$documentId", null) ?: return emptyList()
    return raw.split("||").mapNotNull { part ->
        val pieces = part.split("::")
        if (pieces.isNotEmpty()) {
            val path = pieces[0].trim()
            val dur = pieces.getOrNull(1)?.toIntOrNull() ?: 0
            if (path.isNotEmpty()) Pair(path, dur) else null
        } else null
    }
}

fun DocumentRepository.saveDocumentNoteAudio(documentId: String, memos: List<Pair<String, Int>>) {
    if (documentId.isBlank()) return
    if (memos.isEmpty()) {
        prefs.edit().remove("doc_note_audio_$documentId").apply()
    } else {
        val serialized = memos.joinToString("||") { "${it.first}::${it.second}" }
        prefs.edit().putString("doc_note_audio_$documentId", serialized).apply()
    }
}

fun DocumentRepository.loadPronunciationRules(): List<PronunciationRule> {
    val raw = prefs.getString(DocumentRepository.KEY_PRONUNCIATION_RULES, "[]") ?: "[]"
    val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    val rules = mutableListOf<PronunciationRule>()
    val seen = mutableSetOf<String>()
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val rule = PronunciationRule.fromJson(obj) ?: continue
        if (rule.id.isNotBlank() && seen.add(rule.id)) rules.add(rule)
    }
    if (rules.size != array.length()) savePronunciationRules(rules)
    return rules.sortedByDescending { it.createdAt }
}

fun DocumentRepository.addPronunciationRule(find: String, replaceWith: String): List<PronunciationRule> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val cleanFind = find.trim().take(120)
    if (cleanFind.isBlank()) return@synchronized loadPronunciationRules()
    val current = loadPronunciationRules()
    val existing = current.firstOrNull { it.find.equals(cleanFind, ignoreCase = true) }
    val rule = existing?.copy(replaceWith = replaceWith.trim().take(120), enabled = true)
        ?: PronunciationRule(UUID.randomUUID().toString(), cleanFind, replaceWith.trim().take(120), true, System.currentTimeMillis())
    savePronunciationRules(listOf(rule) + current.filterNot { it.find.equals(cleanFind, ignoreCase = true) })
    loadPronunciationRules()
}

fun DocumentRepository.removePronunciationRule(ruleId: String): List<PronunciationRule> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    savePronunciationRules(loadPronunciationRules().filterNot { it.id == ruleId })
    loadPronunciationRules()
}

fun DocumentRepository.togglePronunciationRule(ruleId: String): List<PronunciationRule> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    savePronunciationRules(loadPronunciationRules().map { if (it.id == ruleId) it.copy(enabled = !it.enabled) else it })
    loadPronunciationRules()
}

fun DocumentRepository.applyPronunciationRules(text: String): String =
    PronunciationRulesEngine.applyCompiled(text, compiledPronunciationRules())

internal fun DocumentRepository.compiledPronunciationRules(): List<DocumentRepository.CompiledPronunciationRule> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val raw = prefs.getString(DocumentRepository.KEY_PRONUNCIATION_RULES, "[]") ?: "[]"
    if (raw == pronunciationRulesRaw) return@synchronized compiledPronunciationRules
    val compiled = PronunciationRulesEngine.compile(loadPronunciationRules())
    pronunciationRulesRaw = raw
    compiledPronunciationRules = compiled
    compiled
}

internal fun DocumentRepository.savePronunciationRules(rules: List<PronunciationRule>) {
    val array = JSONArray()
    rules.forEach { array.put(it.toJson()) }
    prefs.edit { putString(DocumentRepository.KEY_PRONUNCIATION_RULES, array.toString()) }
}

fun DocumentRepository.loadGeneralNotes(): List<GeneralNote> {
    return loadStoredGeneralNotes().filter { it.deletedAt == null }.sortedByDescending { it.updatedAt }
}

private fun DocumentRepository.loadStoredGeneralNotes(): List<GeneralNote> {
    val array = readResilientJsonArray("general_notes")
    val notes = mutableListOf<GeneralNote>()
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        val note = runCatching { GeneralNote.fromJson(item) }.getOrNull() ?: continue
        notes.add(note)
    }
    return notes
}

fun DocumentRepository.loadTrashedGeneralNotes(): List<GeneralNote> =
    loadStoredGeneralNotes().filter { it.deletedAt != null }.sortedByDescending { it.deletedAt }

fun DocumentRepository.saveGeneralNotes(notes: List<GeneralNote>) = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val array = JSONArray()
    val preservedTrash = loadStoredGeneralNotes().filter { it.deletedAt != null }
    (notes.filter { it.deletedAt == null } + notes.filter { it.deletedAt != null } + preservedTrash)
        .distinctBy { it.id }.forEach { array.put(it.toJson()) }
    val previous = prefs.getString("general_notes", null)
    val editor = prefs.edit().putString("general_notes", array.toString())
    if (previous != null) editor.putString("general_notes__bak", previous)
    check(editor.commit()) { "Unable to persist notes" }
    updateVeritasWidgets(appContext)
}

/** Read and publish together so simultaneous dictionary responses retain both entries. */
fun DocumentRepository.mutateGeneralNotes(transform: (List<GeneralNote>) -> List<GeneralNote>): List<GeneralNote> =
    synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
        val preservedTrash = loadStoredGeneralNotes().filter { it.deletedAt != null }
        saveGeneralNotes(transform(loadGeneralNotes()) + preservedTrash)
        loadGeneralNotes()
    }

private fun DocumentRepository.mutateStoredGeneralNotes(transform: (List<GeneralNote>) -> List<GeneralNote>) = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val all = loadStoredGeneralNotes()
    val next = transform(all)
    val array = JSONArray().also { out -> next.distinctBy { it.id }.forEach { out.put(it.toJson()) } }
    val previous = prefs.getString("general_notes", null)
    val editor = prefs.edit().putString("general_notes", array.toString())
    if (previous != null) editor.putString("general_notes__bak", previous)
    check(editor.commit()) { "Unable to persist notes" }
    updateVeritasWidgets(appContext)
}

/** Restore owns the complete active/trash snapshot; normal saves preserve Trash. */
internal fun DocumentRepository.replaceStoredGeneralNotes(notes: List<GeneralNote>) =
    mutateStoredGeneralNotes { notes }

fun DocumentRepository.moveGeneralNoteToTrash(noteId: String): List<GeneralNote> {
    val now = System.currentTimeMillis()
    mutateStoredGeneralNotes { all -> all.map { if (it.id == noteId && it.deletedAt == null) it.copy(deletedAt = now) else it } }
    return loadGeneralNotes()
}

fun DocumentRepository.restoreGeneralNote(noteId: String): List<GeneralNote> {
    mutateStoredGeneralNotes { all -> all.map { if (it.id == noteId) it.copy(deletedAt = null, updatedAt = System.currentTimeMillis()) else it } }
    return loadGeneralNotes()
}

fun DocumentRepository.permanentlyDeleteTrashedNote(noteId: String) = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    if (loadStoredGeneralNotes().none { it.id == noteId && it.deletedAt != null }) return@synchronized
    mutateStoredGeneralNotes { all -> all.filterNot { it.id == noteId && it.deletedAt != null } }
    deleteNoteRevisions(noteId)
}

fun DocumentRepository.loadNoteNotebooks(): List<NoteNotebook> {
    val array = readResilientJsonArray("general_note_notebooks")
    return (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { runCatching { NoteNotebook.fromJson(it) }.getOrNull() } }
        .filter { it.id.isNotBlank() && it.name.isNotBlank() }.sortedBy { it.name.lowercase() }
}

fun DocumentRepository.saveNoteNotebooks(notebooks: List<NoteNotebook>) = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val array = JSONArray().also { out -> notebooks.distinctBy { it.id }.forEach { out.put(it.toJson()) } }
    check(prefs.edit().putString("general_note_notebooks", array.toString()).commit()) { "Unable to persist labels" }
}

fun DocumentRepository.renameNoteNotebook(notebookId: String, newName: String): List<NoteNotebook> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val clean = newName.trim().take(60)
    require(clean.isNotBlank()) { "Enter a label name." }
    val now = System.currentTimeMillis()
    val current = loadNoteNotebooks()
    require(current.none { it.id != notebookId && it.name.equals(clean, ignoreCase = true) }) { "A label with this name already exists." }
    val renamed = current.map { if (it.id == notebookId) it.copy(name = clean, updatedAt = now) else it }
    saveNoteNotebooks(renamed)
    renamed
}

fun DocumentRepository.createNoteNotebook(name: String): List<NoteNotebook> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val clean = name.trim().take(60)
    require(clean.isNotBlank()) { "Enter a label name." }
    val current = loadNoteNotebooks()
    require(current.none { it.name.equals(clean, ignoreCase = true) }) { "A label with this name already exists." }
    saveNoteNotebooks(current + NoteNotebook(java.util.UUID.randomUUID().toString(), clean, System.currentTimeMillis()))
    loadNoteNotebooks()
}

fun DocumentRepository.setGeneralNoteLabels(noteId: String, labelIds: List<String>): List<GeneralNote> =
    synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
        val available = loadNoteNotebooks().map { it.id }.toSet()
        require(labelIds.all { it in available }) { "A selected label no longer exists." }
        mutateGeneralNotes { notes -> notes.map { if (it.id == noteId) it.withLabels(labelIds) else it } }
    }

// Compatibility for callers restoring an older single-notebook assignment.
fun DocumentRepository.moveGeneralNoteToNotebook(noteId: String, notebookId: String?): List<GeneralNote> =
    setGeneralNoteLabels(noteId, listOfNotNull(notebookId))

fun DocumentRepository.deleteNoteNotebook(notebookId: String) = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    // One commit removes the notebook and its assignments, including notes currently in Trash.
    val notebooks = JSONArray().also { out -> loadNoteNotebooks().filterNot { it.id == notebookId }.forEach { out.put(it.toJson()) } }
    val notes = JSONArray().also { out -> loadStoredGeneralNotes().forEach {
        out.put((if (notebookId in it.allLabelIds) it.withLabels(it.allLabelIds - notebookId) else it).toJson())
    } }
    val editor = prefs.edit().putString("general_note_notebooks", notebooks.toString()).putString("general_notes", notes.toString())
    prefs.getString("general_notes", null)?.let { editor.putString("general_notes__bak", it) }
    check(editor.commit()) { "Unable to delete label" }
    updateVeritasWidgets(appContext)
}

fun DocumentRepository.loadNoteRevisions(noteId: String): List<NoteRevision> {
    val array = readResilientJsonArray("general_note_revisions")
    return (0 until array.length()).mapNotNull { i ->
        val item = array.optJSONObject(i) ?: return@mapNotNull null
        if (item.optString("noteId") != noteId) return@mapNotNull null
        val snapshot = item.optJSONObject("snapshot")?.let { runCatching { GeneralNote.fromJson(it) }.getOrNull() } ?: return@mapNotNull null
        NoteRevision(noteId, item.optLong("savedAt"), snapshot)
    }.sortedByDescending { it.savedAt }
}

fun DocumentRepository.loadAllNoteRevisions(): List<NoteRevision> {
    val array = readResilientJsonArray("general_note_revisions")
    return (0 until array.length()).mapNotNull { i ->
        val item = array.optJSONObject(i) ?: return@mapNotNull null
        val id = item.optString("noteId")
        val snapshot = item.optJSONObject("snapshot")?.let { runCatching { GeneralNote.fromJson(it) }.getOrNull() } ?: return@mapNotNull null
        if (id.isBlank()) null else NoteRevision(id, item.optLong("savedAt"), snapshot)
    }.sortedByDescending { it.savedAt }
}

fun DocumentRepository.saveNoteRevisions(revisions: List<NoteRevision>) = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val array = JSONArray()
    revisions.filter { it.noteId.isNotBlank() && it.snapshot.id == it.noteId }
        .sortedByDescending { it.savedAt }.groupBy { it.noteId }.values.flatMap { it.take(30) }
        .sortedByDescending { it.savedAt }.take(200).forEach { revision ->
        array.put(JSONObject().put("noteId", revision.noteId).put("savedAt", revision.savedAt).put("snapshot", revision.snapshot.toJson()))
    }
    check(prefs.edit().putString("general_note_revisions", array.toString()).commit()) { "Unable to persist note revisions" }
}

fun DocumentRepository.deleteNoteRevisions(noteId: String) = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val retained = loadAllNoteRevisions().filterNot { it.noteId == noteId }
    saveNoteRevisions(retained)
}

fun DocumentRepository.archiveGeneralNoteRevision(note: GeneralNote) = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val existing = loadAllNoteRevisions()
    val latest = existing.firstOrNull { it.noteId == note.id }
    if (latest?.snapshot == note) return@synchronized
    // Always retain newest first; reversing an already reversed array discarded recent versions.
    saveNoteRevisions(listOf(NoteRevision(note.id, System.currentTimeMillis(), note)) + existing)
}

fun DocumentRepository.restoreNoteRevision(revision: NoteRevision): List<GeneralNote> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val current = loadGeneralNotes().firstOrNull { it.id == revision.noteId }
        ?: error("This note is no longer available. Restore it from Trash first.")
    require(revision.snapshot.id == revision.noteId) { "This revision does not belong to the note." }
    archiveGeneralNoteRevision(current)
    mutateGeneralNotes { notes ->
        notes.map { if (it.id == revision.noteId) revision.snapshot.copy(
            id = it.id, createdAt = it.createdAt, updatedAt = System.currentTimeMillis(),
            notebookId = null, labelIds = it.allLabelIds, pinned = it.pinned, deletedAt = null
        ) else it }
    }
}

fun DocumentRepository.loadAllAnnotations(): List<ReaderAnnotation> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val existingIds = loadDocuments().map { it.id }.toSet()
    val dbList = runCatching { dbHelper.getAllAnnotations() }.getOrDefault(emptyList())
    val sourceList = if (dbList.isEmpty()) {
        val fallback = loadAllAnnotationsFromPrefsRaw()
        if (fallback.isNotEmpty()) {
            runCatching { dbHelper.replaceAllAnnotations(fallback) }
            fallback
        } else {
            emptyList()
        }
    } else {
        dbList
    }

    val annotations = mutableListOf<ReaderAnnotation>()
    val seen = mutableSetOf<String>()
    var changed = false
    for (source in sourceList) {
        val annotation = if (source.type == AnnotationType.HIGHLIGHT) {
            changed = true
            source.copy(type = AnnotationType.BOOKMARK)
        } else {
            source
        }
        if (annotation.documentId in existingIds && annotation.chunkIndex >= 0 && seen.add(annotation.stableKey)) {
            annotations.add(annotation)
        }
    }
    if (changed || annotations.size != sourceList.size) saveAllAnnotations(annotations)
    return@synchronized annotations
}

internal fun DocumentRepository.loadAllAnnotationsFromPrefsRaw(): List<ReaderAnnotation> {
    val raw = prefs.getString(DocumentRepository.KEY_ANNOTATIONS, "[]") ?: "[]"
    val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    val annotations = mutableListOf<ReaderAnnotation>()
    val seen = mutableSetOf<String>()
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        val source = ReaderAnnotation.fromJson(obj) ?: continue
        val annotation = if (source.type == AnnotationType.HIGHLIGHT) {
            source.copy(type = AnnotationType.BOOKMARK)
        } else {
            source
        }
        if (annotation.chunkIndex >= 0 && seen.add(annotation.stableKey)) {
            annotations.add(annotation)
        }
    }
    return annotations
}

fun DocumentRepository.saveAllAnnotations(annotations: List<ReaderAnnotation>): Unit = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    dbHelper.replaceAllAnnotations(annotations)
    val array = JSONArray()
    annotations.sortedWith(compareBy<ReaderAnnotation> { it.documentId }.thenBy { it.chunkIndex }.thenBy { it.type.name })
        .forEach { array.put(it.toJson()) }
    prefs.edit { putString(DocumentRepository.KEY_ANNOTATIONS, array.toString()) }
}

fun DocumentRepository.loadAllFlashcards(): List<FlashcardProgress> {
    val dbList = runCatching { dbHelper.getAllFlashcards() }.getOrDefault(emptyList())
    val list = if (dbList.isEmpty()) {
        val fallback = loadAllFlashcardsFromPrefsRaw()
        if (fallback.isNotEmpty()) {
            runCatching { dbHelper.replaceAllFlashcards(fallback) }
            fallback
        } else {
            emptyList()
        }
    } else {
        dbList
    }
    // Migrate legacy cards (no setId) into a per-document set so the old flat
    // deck becomes reviewable under the new set-based UI.
    if (list.any { it.setId.isBlank() }) {
        val migrated = list.map { card ->
            if (card.setId.isNotBlank()) card
            else card.copy(
                setId = "legacy-${card.documentId.ifBlank { "pasted" }}",
                setName = "Imported cards"
            )
        }
        saveAllFlashcards(migrated)
        return migrated
    }
    return list
}

internal fun DocumentRepository.loadAllFlashcardsFromPrefsRaw(): List<FlashcardProgress> {
    val raw = prefs.getString("study_flashcards", "[]") ?: "[]"
    val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    val list = mutableListOf<FlashcardProgress>()
    for (i in 0 until array.length()) {
        val obj = array.optJSONObject(i) ?: continue
        list.add(FlashcardProgress.fromJson(obj))
    }
    return list
}

fun DocumentRepository.saveAllFlashcards(list: List<FlashcardProgress>) {
    dbHelper.replaceAllFlashcards(list)
    val array = JSONArray()
    list.forEach { array.put(it.toJson()) }
    prefs.edit { putString("study_flashcards", array.toString()) }
}

/** Cards grouped into their named sets, newest set first. */
fun DocumentRepository.loadFlashcardSets(): List<FlashcardSet> {
    return loadAllFlashcards()
        .groupBy { it.setId }
        .map { (setId, cards) ->
            FlashcardSet(
                setId = setId,
                name = cards.firstOrNull { it.setName.isNotBlank() }?.setName ?: "Untitled set",
                cards = cards
            )
        }
}

fun DocumentRepository.renameFlashcardSet(setId: String, newName: String): List<FlashcardProgress> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val clean = newName.trim().ifBlank { "Untitled set" }
    val updated = loadAllFlashcards().map {
        if (it.setId == setId) it.copy(setName = clean) else it
    }
    saveAllFlashcards(updated)
    updated
}

fun DocumentRepository.deleteFlashcardSet(setId: String): List<FlashcardProgress> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val remaining = loadAllFlashcards().filterNot { it.setId == setId }
    saveAllFlashcards(remaining)
    remaining
}

fun DocumentRepository.loadAllQuizzes(): List<QuizSet> {
    val raw = prefs.getString("study_quizzes", "[]") ?: "[]"
    return runCatching {
        val array = JSONArray(raw)
        val list = mutableListOf<QuizSet>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            list.add(QuizSet.fromJson(obj))
        }
        list
    }.getOrDefault(emptyList())
}

fun DocumentRepository.saveAllQuizzes(list: List<QuizSet>) {
    val array = JSONArray()
    list.forEach { array.put(it.toJson()) }
    prefs.edit { putString("study_quizzes", array.toString()) }
}

fun DocumentRepository.saveQuiz(quiz: QuizSet): List<QuizSet> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val all = loadAllQuizzes()
    val previous = all.firstOrNull { it.id == quiz.id }
    val current = all.filterNot { it.id == quiz.id }.toMutableList()
    val saved = if (previous?.questions == quiz.questions) quiz.copy(bestScore = maxOf(previous.bestScore, quiz.bestScore)) else quiz
    current.add(0, saved)
    saveAllQuizzes(current)
    current
}

fun DocumentRepository.deleteQuiz(quizId: String): List<QuizSet> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    val remaining = loadAllQuizzes().filterNot { it.id == quizId }
    saveAllQuizzes(remaining)
    remaining
}

internal fun DocumentRepository.loadDocumentNotes(): Map<String, String> {
    val existingIds = loadDocuments().map { it.id }.toSet()
    val raw = prefs.getString(DocumentRepository.KEY_DOCUMENT_NOTES, "{}") ?: "{}"
    val obj = runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    val notes = linkedMapOf<String, String>()
    val keys = obj.keys()
    while (keys.hasNext()) {
        val documentId = keys.next()
        val note = obj.optString(documentId).trim()
        if (documentId in existingIds && note.isNotBlank()) {
            notes[documentId] = note
        }
    }
    if (notes.size != obj.length()) saveDocumentNotes(notes)
    return notes
}

internal fun DocumentRepository.saveDocumentNotes(notes: Map<String, String>) {
    val existingIds = loadDocuments().map { it.id }.toSet()
    val obj = JSONObject()
    notes.toSortedMap().forEach { (documentId, note) ->
        val cleanNote = note.trim()
        if (documentId in existingIds && cleanNote.isNotBlank()) {
            obj.put(documentId, cleanNote)
        }
    }
    prefs.edit { putString(DocumentRepository.KEY_DOCUMENT_NOTES, obj.toString()) }
}

fun DocumentRepository.mutateAnnotations(transform: (List<ReaderAnnotation>) -> List<ReaderAnnotation>): List<ReaderAnnotation> = synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
    saveAllAnnotations(transform(loadAllAnnotations()))
    loadAllAnnotations()
}

internal fun DocumentRepository.mutateStudyFlashcards(transform: (List<FlashcardProgress>) -> List<FlashcardProgress>): List<FlashcardProgress> =
    synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) { transform(loadAllFlashcards()).also(::saveAllFlashcards) }

internal fun DocumentRepository.mutateStudyQuizzes(transform: (List<QuizSet>) -> List<QuizSet>): List<QuizSet> =
    synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) { transform(loadAllQuizzes()).also(::saveAllQuizzes) }
