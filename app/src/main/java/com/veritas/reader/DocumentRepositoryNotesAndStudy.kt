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
    audioDurationSeconds: Int = 0
): List<ReaderAnnotation> {
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
            audioPath = audioPath ?: old.audioPath,
            audioDurationSeconds = if (audioDurationSeconds > 0) audioDurationSeconds else old.audioDurationSeconds
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
    return loadAnnotations(documentId)
}

fun DocumentRepository.removeAnnotation(documentId: String, chunkIndex: Int, type: AnnotationType): List<ReaderAnnotation> {
    saveAllAnnotations(loadAllAnnotations().filterNot {
        it.documentId == documentId && it.chunkIndex == chunkIndex && it.type == type
    })
    return loadAnnotations(documentId)
}

fun DocumentRepository.removeAnnotations(stableKeys: Set<String>): List<ReaderAnnotation> {
    if (stableKeys.isEmpty()) return loadAllAnnotations()
    saveAllAnnotations(loadAllAnnotations().filterNot { it.stableKey in stableKeys })
    return loadAllAnnotations()
}

fun DocumentRepository.toggleAnnotation(documentId: String, chunkIndex: Int, type: AnnotationType): List<ReaderAnnotation> {
    val exists = loadAllAnnotations().any { it.documentId == documentId && it.chunkIndex == chunkIndex && it.type == type }
    return if (exists) removeAnnotation(documentId, chunkIndex, type) else upsertAnnotation(documentId, chunkIndex, type)
}

fun DocumentRepository.deleteAnnotationsForDocument(documentId: String) {
    saveAllAnnotations(loadAllAnnotations().filterNot { it.documentId == documentId })
}

fun DocumentRepository.loadDocumentNote(documentId: String): String {
    return loadDocumentNotes()[documentId].orEmpty()
}

fun DocumentRepository.loadAllDocumentNotes(): Map<String, String> = loadDocumentNotes()

fun DocumentRepository.saveDocumentNote(documentId: String, note: String): String {
    if (findDocument(documentId) == null) return ""
    val updated = loadDocumentNotes().toMutableMap()
    val cleanNote = note.trim()
    if (cleanNote.isBlank()) {
        updated.remove(documentId)
    } else {
        updated[documentId] = cleanNote
    }
    saveDocumentNotes(updated)
    return loadDocumentNote(documentId)
}

fun DocumentRepository.deleteDocumentNote(documentId: String) {
    saveDocumentNotes(loadDocumentNotes().filterKeys { it != documentId })
}

fun DocumentRepository.deleteDocumentNotes(documentIds: Set<String>): Map<String, String> {
    if (documentIds.isEmpty()) return loadDocumentNotes()
    saveDocumentNotes(loadDocumentNotes().filterKeys { it !in documentIds })
    return loadDocumentNotes()
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

fun DocumentRepository.addPronunciationRule(find: String, replaceWith: String): List<PronunciationRule> {
    val cleanFind = find.trim()
    if (cleanFind.isBlank()) return loadPronunciationRules()
    val cleanReplace = replaceWith.trim()
    val rule = PronunciationRule(
        id = UUID.randomUUID().toString(),
        find = cleanFind,
        replaceWith = cleanReplace,
        enabled = true,
        createdAt = System.currentTimeMillis()
    )
    savePronunciationRules(listOf(rule) + loadPronunciationRules())
    return loadPronunciationRules()
}

fun DocumentRepository.removePronunciationRule(ruleId: String): List<PronunciationRule> {
    savePronunciationRules(loadPronunciationRules().filterNot { it.id == ruleId })
    return loadPronunciationRules()
}

fun DocumentRepository.togglePronunciationRule(ruleId: String): List<PronunciationRule> {
    savePronunciationRules(loadPronunciationRules().map { rule ->
        if (rule.id == ruleId) rule.copy(enabled = !rule.enabled) else rule
    })
    return loadPronunciationRules()
}

fun DocumentRepository.applyPronunciationRules(text: String): String {
    val rules = compiledPronunciationRules()
    if (rules.isEmpty()) return text
    var output = text
    rules.forEach { rule ->
        output = rule.regex.replace(output) { matchResult ->
            val matchedText = matchResult.value
            val replacement = rule.replaceWith
            when {
                replacement.isEmpty() -> ""
                matchedText.all { it.isUpperCase() } -> replacement.uppercase()
                matchedText.firstOrNull()?.isUpperCase() == true -> {
                    replacement.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
                else -> replacement
            }
        }
    }
    return output
}

internal fun DocumentRepository.compiledPronunciationRules(): List<DocumentRepository.CompiledPronunciationRule> {
    val raw = prefs.getString(DocumentRepository.KEY_PRONUNCIATION_RULES, "[]") ?: "[]"
    if (raw == pronunciationRulesRaw) return compiledPronunciationRules
    val compiled = loadPronunciationRules()
        .filter { it.enabled && it.find.isNotBlank() }
        .map { rule ->
            val escapedFind = Regex.escape(rule.find)
            val startsWithWordChar = rule.find.firstOrNull()?.let { it.isLetterOrDigit() || it == '_' } == true
            val endsWithWordChar = rule.find.lastOrNull()?.let { it.isLetterOrDigit() || it == '_' } == true
            val prefix = if (startsWithWordChar) "\\b" else ""
            val suffix = if (endsWithWordChar) "\\b" else ""
            val pattern = "$prefix$escapedFind$suffix"
            DocumentRepository.CompiledPronunciationRule(Regex(pattern, RegexOption.IGNORE_CASE), rule.replaceWith)
        }
    pronunciationRulesRaw = raw
    compiledPronunciationRules = compiled
    return compiled
}

internal fun DocumentRepository.savePronunciationRules(rules: List<PronunciationRule>) {
    val array = JSONArray()
    rules.forEach { array.put(it.toJson()) }
    prefs.edit { putString(DocumentRepository.KEY_PRONUNCIATION_RULES, array.toString()) }
}

fun DocumentRepository.loadGeneralNotes(): List<GeneralNote> {
    val array = readResilientJsonArray("general_notes")
    val notes = mutableListOf<GeneralNote>()
    for (i in 0 until array.length()) {
        val item = array.optJSONObject(i) ?: continue
        val note = runCatching { GeneralNote.fromJson(item) }.getOrNull() ?: continue
        notes.add(note)
    }
    return notes.sortedByDescending { it.updatedAt }
}

fun DocumentRepository.saveGeneralNotes(notes: List<GeneralNote>) {
    val array = JSONArray()
    notes.forEach { array.put(it.toJson()) }
    commitResilientJson("general_notes", array.toString())
    updateVeritasWidgets(appContext)
}

fun DocumentRepository.loadAllAnnotations(): List<ReaderAnnotation> {
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
    return annotations
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

fun DocumentRepository.saveAllAnnotations(annotations: List<ReaderAnnotation>) {
    runCatching { dbHelper.replaceAllAnnotations(annotations) }
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
    runCatching { dbHelper.replaceAllFlashcards(list) }
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

fun DocumentRepository.renameFlashcardSet(setId: String, newName: String): List<FlashcardProgress> {
    val clean = newName.trim().ifBlank { "Untitled set" }
    val updated = loadAllFlashcards().map {
        if (it.setId == setId) it.copy(setName = clean) else it
    }
    saveAllFlashcards(updated)
    return updated
}

fun DocumentRepository.deleteFlashcardSet(setId: String): List<FlashcardProgress> {
    val remaining = loadAllFlashcards().filterNot { it.setId == setId }
    saveAllFlashcards(remaining)
    return remaining
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

fun DocumentRepository.saveQuiz(quiz: QuizSet): List<QuizSet> {
    val current = loadAllQuizzes().filterNot { it.id == quiz.id }.toMutableList()
    current.add(0, quiz)
    saveAllQuizzes(current)
    return current
}

fun DocumentRepository.deleteQuiz(quizId: String): List<QuizSet> {
    val remaining = loadAllQuizzes().filterNot { it.id == quizId }
    saveAllQuizzes(remaining)
    return remaining
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