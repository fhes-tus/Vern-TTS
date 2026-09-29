package com.veritas.reader.ui

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.veritas.reader.AnnotationType
import com.veritas.reader.GeminiStudyService
import com.veritas.reader.GeneralNote
import com.veritas.reader.NoteReminderScheduler
import com.veritas.reader.PlaybackStateStore
import com.veritas.reader.ReaderAnnotation
import com.veritas.reader.ReaderTextIndex
import com.veritas.reader.VoiceNoteRecorder
import com.veritas.reader.deleteDocumentNotes
import com.veritas.reader.documentIdFromDocumentNoteStableKey
import com.veritas.reader.documentNoteStableKey
import com.veritas.reader.loadAllAnnotations
import com.veritas.reader.loadAllDocumentNotes
import com.veritas.reader.loadAnnotations
import com.veritas.reader.loadDocumentNote
import com.veritas.reader.loadGeneralNotes
import com.veritas.reader.parseVocabularyNoteContent
import com.veritas.reader.removeAnnotation
import com.veritas.reader.removeAnnotations
import com.veritas.reader.saveAllAnnotations
import com.veritas.reader.saveDocumentNote
import com.veritas.reader.saveGeneralNotes
import com.veritas.reader.upsertAnnotation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

fun ReaderViewModel.addBookmarkGroup(indexes: List<Int>, colorHex: String) {
    val docId = uiState.value.activeDocument?.id ?: return
    viewModelScope.launch(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val groupId = UUID.randomUUID().toString()
        val existing = repository.loadAllAnnotations().toMutableList()
        val keysToRemove = indexes.map { "$docId:$it:${AnnotationType.BOOKMARK.name}" }.toSet()
        val filtered = existing.filterNot { it.stableKey in keysToRemove }.toMutableList()
        
        indexes.forEach { idx ->
            filtered.add(
                ReaderAnnotation(
                    documentId = docId,
                    chunkIndex = idx,
                    type = AnnotationType.BOOKMARK,
                    note = "",
                    createdAt = now,
                    updatedAt = now,
                    highlightColor = colorHex,
                    selectionGroupId = groupId
                )
            )
        }
        
        repository.saveAllAnnotations(filtered)
        val updated = repository.loadAnnotations(docId)
        val allAnnotations = repository.loadAllAnnotations()
        val documentNotes = repository.loadAllDocumentNotes()
        withContext(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                    annotations = updated,
                    allAnnotations = allAnnotations,
                    documentNotes = documentNotes,
                    annotationCount = allAnnotations.size + documentNotes.size
                )
            }
            completeQuestBookmark()
        }
    }
}

fun ReaderViewModel.toggleBookmark(index: Int = PlaybackStateStore.currentIndex) {
    val docId = uiState.value.activeDocument?.id ?: return
    viewModelScope.launch(Dispatchers.IO) {
        val annots = repository.loadAllAnnotations()
        val target = annots.firstOrNull { it.documentId == docId && it.chunkIndex == index && it.type == AnnotationType.BOOKMARK }
        val updated = if (target != null) {
            if (!target.selectionGroupId.isNullOrBlank()) {
                val toRemove = annots.filter { it.documentId == docId && it.selectionGroupId == target.selectionGroupId }.map { it.stableKey }.toSet()
                repository.removeAnnotations(toRemove)
            } else {
                repository.removeAnnotation(docId, index, AnnotationType.BOOKMARK)
            }
            repository.loadAnnotations(docId)
        } else {
            repository.upsertAnnotation(docId, index, AnnotationType.BOOKMARK, highlightColor = "#FFE082")
        }
        val allAnnotations = repository.loadAllAnnotations()
        val documentNotes = repository.loadAllDocumentNotes()
        withContext(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                    annotations = updated,
                    allAnnotations = allAnnotations,
                    documentNotes = documentNotes,
                    annotationCount = allAnnotations.size + documentNotes.size
                )
            }
            completeQuestBookmark()
        }
    }
}

fun ReaderViewModel.beginSentenceNote(indexes: List<Int>) {
    uiState.value.activeDocument?.id ?: return
    if (indexes.isEmpty()) return
    val existing = uiState.value.annotations.firstOrNull { it.chunkIndex == indexes.first() && it.type == AnnotationType.NOTE }
    _uiState.update { 
        it.copy(
            noteTargetIndexes = indexes,
            noteDraft = existing?.note ?: "",
            noteAudioPath = existing?.audioPath,
            noteAudioDuration = existing?.audioDurationSeconds ?: 0
        )
    }
}

fun ReaderViewModel.saveSentenceNote(audioPath: String? = uiState.value.noteAudioPath, audioDuration: Int = uiState.value.noteAudioDuration) {
    val docId = uiState.value.activeDocument?.id ?: return
    val indexes = uiState.value.noteTargetIndexes.ifEmpty {
        uiState.value.noteTargetIndex?.let(::listOf).orEmpty()
    }
    val text = uiState.value.noteDraft
    viewModelScope.launch(Dispatchers.IO) {
        val annots = repository.loadAllAnnotations()
        val existingGroup = indexes.mapNotNull { idx ->
            annots.firstOrNull { it.documentId == docId && it.chunkIndex == idx && it.type == AnnotationType.NOTE }?.selectionGroupId
        }.firstOrNull { !it.isNullOrBlank() }
        val groupId = existingGroup ?: if (indexes.size >= 2) "note-group-${UUID.randomUUID()}" else null
        
        var lastUpdated: List<ReaderAnnotation> = emptyList()
        indexes.forEach { idx ->
            lastUpdated = repository.upsertAnnotation(
                documentId = docId,
                chunkIndex = idx,
                type = AnnotationType.NOTE,
                note = text,
                selectionGroupId = groupId,
                audioPath = audioPath,
                audioDurationSeconds = audioDuration
            )
        }
        val allAnnotations = repository.loadAllAnnotations()
        val documentNotes = repository.loadAllDocumentNotes()
        withContext(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                    annotations = lastUpdated,
                    allAnnotations = allAnnotations,
                    documentNotes = documentNotes,
                    annotationCount = allAnnotations.size + documentNotes.size,
                    noteTargetIndex = null,
                    noteTargetIndexes = emptyList(),
                    noteDraft = "",
                    noteAudioPath = null,
                    noteAudioDuration = 0
                )
            }
        }
    }
}

fun ReaderViewModel.deleteSentenceNote() {
    val docId = uiState.value.activeDocument?.id ?: return
    val indexes = uiState.value.noteTargetIndexes.ifEmpty {
        uiState.value.noteTargetIndex?.let(::listOf).orEmpty()
    }
    val audioToDelete = uiState.value.noteAudioPath
    if (audioToDelete != null) {
        VoiceNoteRecorder.deleteAudioFile(audioToDelete)
    }
    viewModelScope.launch(Dispatchers.IO) {
        indexes.forEach { idx ->
            repository.removeAnnotation(docId, idx, AnnotationType.NOTE)
        }
        val lastUpdated = repository.loadAnnotations(docId)
        val allAnnotations = repository.loadAllAnnotations()
        val documentNotes = repository.loadAllDocumentNotes()
        withContext(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                    annotations = lastUpdated,
                    allAnnotations = allAnnotations,
                    documentNotes = documentNotes,
                    annotationCount = allAnnotations.size + documentNotes.size,
                    noteTargetIndex = null,
                    noteTargetIndexes = emptyList(),
                    noteDraft = "",
                    noteAudioPath = null,
                    noteAudioDuration = 0
                )
            }
        }
    }
}

fun ReaderViewModel.dismissSentenceNote() {
    VoiceNoteRecorder.stopAll()
    _uiState.update {
        it.copy(
            noteTargetIndex = null,
            noteTargetIndexes = emptyList(),
            noteDraft = "",
            noteAudioPath = null,
            noteAudioDuration = 0
        )
    }
}

fun ReaderViewModel.deleteAnnotations(stableKeys: Set<String>) {
    if (stableKeys.isEmpty()) return
    viewModelScope.launch(Dispatchers.IO) {
        val documentNoteIds = stableKeys.mapNotNull(::documentIdFromDocumentNoteStableKey).toSet()
        val annotationKeys = stableKeys - documentNoteIds.map(::documentNoteStableKey).toSet()
        val allAnnotations = repository.removeAnnotations(annotationKeys)
        val documentNotes = repository.deleteDocumentNotes(documentNoteIds)
        val activeDocId = uiState.value.activeDocument?.id
        val currentAnnotations = activeDocId?.let { repository.loadAnnotations(it) }.orEmpty()
        val activeDocumentNote = activeDocId?.let { repository.loadDocumentNote(it) }.orEmpty()
        withContext(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                    annotations = currentAnnotations,
                    allAnnotations = allAnnotations,
                    documentNotes = documentNotes,
                    annotationCount = allAnnotations.size + documentNotes.size,
                    documentNoteDraft = activeDocumentNote
                )
            }
        }
    }
}

fun ReaderViewModel.openDocumentNotes() {
    val docId = uiState.value.activeDocument?.id ?: return
    viewModelScope.launch(Dispatchers.IO) {
        val note = repository.loadDocumentNote(docId)
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(showDocumentNotes = true, documentNoteDraft = note) }
        }
    }
}

fun ReaderViewModel.saveDocumentNoteDraft() {
    val docId = uiState.value.activeDocument?.id ?: return
    val draft = uiState.value.documentNoteDraft
    viewModelScope.launch(Dispatchers.IO) {
        val savedNote = repository.saveDocumentNote(docId, draft)
        val allAnnotations = repository.loadAllAnnotations()
        val documentNotes = repository.loadAllDocumentNotes()
        withContext(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                    showDocumentNotes = false,
                    documentNoteDraft = savedNote,
                    allAnnotations = allAnnotations,
                    documentNotes = documentNotes,
                    annotationCount = allAnnotations.size + documentNotes.size
                )
            }
        }
    }
}

fun ReaderViewModel.saveGeneralNote(
    title: String,
    content: String,
    color: String? = null,
    pinned: Boolean = false,
    isChecklist: Boolean = false,
    imageUrl: String? = null,
    audioUrl: String? = null,
    reminderAt: Long? = null,
    closeEditor: Boolean = true,
    audioUrls: List<String> = emptyList()
) {
    viewModelScope.launch(Dispatchers.IO) {
        val existing = repository.loadGeneralNotes().toMutableList()
        val target = uiState.value.generalNoteEditorTarget
        val savedNote: GeneralNote
        val resolvedAudioUrls = if (audioUrls.isNotEmpty()) audioUrls else listOfNotNull(audioUrl)
        if (target == null) {
            savedNote = GeneralNote(
                id = UUID.randomUUID().toString(),
                title = title,
                content = content,
                updatedAt = System.currentTimeMillis(),
                color = color,
                pinned = pinned,
                isChecklist = isChecklist,
                imageUrl = imageUrl,
                audioUrl = resolvedAudioUrls.firstOrNull(),
                audioUrls = resolvedAudioUrls,
                reminderAt = reminderAt
            )
            existing.add(0, savedNote)
        } else {
            val index = existing.indexOfFirst { it.id == target.id }
            savedNote = target.copy(
                title = title,
                content = content,
                updatedAt = System.currentTimeMillis(),
                color = color,
                pinned = pinned,
                isChecklist = isChecklist,
                imageUrl = imageUrl,
                audioUrl = resolvedAudioUrls.firstOrNull(),
                audioUrls = resolvedAudioUrls,
                reminderAt = reminderAt
            )
            if (index != -1) existing[index] = savedNote else existing.add(0, savedNote)
        }
        repository.saveGeneralNotes(existing)
        // (Re)schedule or clear the note's reminder alarm.
        val app = getApplication<Application>()
        val reminderBody = title.ifBlank { content.take(80) }.ifBlank { "Note reminder" }
        if (reminderAt != null && reminderAt > System.currentTimeMillis()) {
            NoteReminderScheduler.ensureChannel(app)
            NoteReminderScheduler.schedule(app, savedNote.id, title.ifBlank { "Vern note" }, reminderBody, reminderAt)
        } else {
            NoteReminderScheduler.cancel(app, savedNote.id)
        }
        val updated = repository.loadGeneralNotes()
        withContext(Dispatchers.Main) {
            _uiState.update {
                if (closeEditor) {
                    it.copy(
                        generalNotes = updated,
                        showGeneralNotesEditor = false,
                        generalNoteEditorTarget = null
                    )
                } else {
                    it.copy(
                        generalNotes = updated,
                        generalNoteEditorTarget = savedNote
                    )
                }
            }
        }
    }
}

fun ReaderViewModel.duplicateGeneralNote(note: GeneralNote) {
    viewModelScope.launch(Dispatchers.IO) {
        val existing = repository.loadGeneralNotes().toMutableList()
        val newNote = note.copy(
            id = UUID.randomUUID().toString(),
            title = if (note.title.endsWith(" (Copy)")) note.title else note.title + " (Copy)",
            updatedAt = System.currentTimeMillis()
        )
        existing.add(0, newNote)
        repository.saveGeneralNotes(existing)
        val updated = repository.loadGeneralNotes()
        withContext(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                    generalNotes = updated,
                    showGeneralNotesEditor = false,
                    generalNoteEditorTarget = null
                )
            }
        }
    }
}

fun ReaderViewModel.clearNoteEditorFlags() {
    _uiState.update {
        it.copy(
            noteEditorChecklistOnStart = false,
            noteEditorReminderOnStart = false,
            noteEditorImageOnStart = false
        )
    }
}

fun ReaderViewModel.toggleGeneralNotePin(noteId: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val existing = repository.loadGeneralNotes().toMutableList()
        val index = existing.indexOfFirst { it.id == noteId }
        if (index != -1) {
            val target = existing[index]
            existing[index] = target.copy(pinned = !target.pinned, updatedAt = System.currentTimeMillis())
            repository.saveGeneralNotes(existing)
            val updated = repository.loadGeneralNotes()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(generalNotes = updated) }
            }
        }
    }
}

fun ReaderViewModel.changeGeneralNoteColor(noteId: String, colorHex: String?) {
    viewModelScope.launch(Dispatchers.IO) {
        val existing = repository.loadGeneralNotes().toMutableList()
        val index = existing.indexOfFirst { it.id == noteId }
        if (index != -1) {
            val target = existing[index]
            existing[index] = target.copy(color = colorHex, updatedAt = System.currentTimeMillis())
            repository.saveGeneralNotes(existing)
            val updated = repository.loadGeneralNotes()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(generalNotes = updated) }
            }
        }
    }
}

fun ReaderViewModel.deleteGeneralNote(noteId: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val existing = repository.loadGeneralNotes().filterNot { it.id == noteId }
        repository.saveGeneralNotes(existing)
        NoteReminderScheduler.cancel(getApplication(), noteId)
        val updated = repository.loadGeneralNotes()
        withContext(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                     generalNotes = updated,
                     showGeneralNotesEditor = false,
                     generalNoteEditorTarget = null
                )
            }
        }
    }
}

private data class DictionaryDefinition(
    val definition: String,
    val pronunciation: String? = null
)

private fun fetchDictionaryDefinition(word: String): DictionaryDefinition? {
    val cleanWord = word.trim().lowercase(Locale.getDefault()).replace(Regex("[^a-z\\-]"), "")
    if (cleanWord.isBlank() || cleanWord.length > 30) return null
    var conn: HttpURLConnection? = null
    return try {
        val url = URL("https://api.dictionaryapi.dev/api/v2/entries/en/$cleanWord")
        conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        if (conn.responseCode == 200) {
            val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
            val array = JSONArray(jsonText)
            if (array.length() > 0) {
                val entry = array.getJSONObject(0)
                val phonetic = entry.optString("phonetic").takeIf { it.isNotBlank() }
                val meanings = entry.optJSONArray("meanings")
                if (meanings != null && meanings.length() > 0) {
                    val firstMeaning = meanings.getJSONObject(0)
                    val pos = firstMeaning.optString("partOfSpeech", "")
                    val definitions = firstMeaning.optJSONArray("definitions")
                    if (definitions != null && definitions.length() > 0) {
                        val defObj = definitions.getJSONObject(0)
                        val defText = defObj.optString("definition", "")
                        if (defText.isNotBlank()) {
                            val fullDef = if (pos.isNotBlank()) "($pos) $defText" else defText
                            return DictionaryDefinition(fullDef, phonetic)
                        }
                    }
                }
            }
        }
        null
    } catch (e: Exception) {
        android.util.Log.w("ReaderViewModel", "Failed to fetch meaning for $cleanWord", e)
        null
    } finally {
        conn?.disconnect()
    }
}

fun ReaderViewModel.appendVocabularyWord(word: String, explanation: String, selectedContext: String? = null) {
    val activeDoc = uiState.value.activeDocument ?: return
    val docId = activeDoc.id ?: return
    viewModelScope.launch(Dispatchers.IO) {
        val currentIndex = PlaybackStateStore.currentIndex
        val textModel = ReaderTextIndex.build(activeDoc.rawText, activeDoc.pageCount)
        val part = textModel.partForSentence(currentIndex)
        val sectionNum = (part?.index ?: 0) + 1
        val contextSentence = selectedContext?.trim()
            .takeIf { !it.isNullOrBlank() }
            ?: activeDoc.sentences.getOrNull(currentIndex)?.trim()

        val isPlaceholder = explanation.contains("Looked up") || explanation.contains("Asked AI")
        var finalExplanation: String = explanation
        var pronunciation: String? = null

        if (isPlaceholder) {
            val apiKey = GeminiStudyService.getApiKey(getApplication())
            val contextDef = if (apiKey.isNotBlank() && !contextSentence.isNullOrBlank()) {
                GeminiStudyService.defineInContext(
                    apiKey = apiKey,
                    word = word,
                    contextSentence = contextSentence,
                    bookTitle = activeDoc.title
                ).getOrNull()
            } else null

            if (!contextDef.isNullOrBlank()) {
                finalExplanation = contextDef
            } else {
                val fetched = fetchDictionaryDefinition(word)
                finalExplanation = fetched?.definition ?: explanation
                pronunciation = fetched?.pronunciation
            }
        }

        val existing = repository.loadGeneralNotes().toMutableList()
        val targetTitle = "__vocab__$docId"
        val vocabIndex = existing.indexOfFirst { it.title == targetTitle }

        val now = System.currentTimeMillis()
        val formattedTime = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(now))

        val entryText = buildString {
            appendLine(word.trim())
            appendLine("  $finalExplanation")
            appendLine("  (looked up: Section $sectionNum, sentence ${currentIndex + 1})")
            appendLine("  [$formattedTime]")
            if (!contextSentence.isNullOrBlank()) {
                appendLine("  context: \"$contextSentence\"")
            }
            if (!pronunciation.isNullOrBlank()) {
                appendLine("  pronunciation: $pronunciation")
            }
        }

        if (vocabIndex != -1) {
            val oldNote = existing[vocabIndex]
            val newContent = if (oldNote.content.isBlank()) entryText else oldNote.content + "\n\n" + entryText
            existing[vocabIndex] = oldNote.copy(content = newContent, updatedAt = now)
        } else {
            val newNote = GeneralNote(
                id = UUID.randomUUID().toString(),
                title = targetTitle,
                content = entryText,
                updatedAt = now
            )
            existing.add(0, newNote)
        }

        repository.saveGeneralNotes(existing)
        val updated = repository.loadGeneralNotes()
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(generalNotes = updated) }
        }
    }
}

fun ReaderViewModel.removeVocabularyWord(documentId: String, wordToRemove: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val existing = repository.loadGeneralNotes().toMutableList()
        val targetTitle = "__vocab__$documentId"
        val index = existing.indexOfFirst { it.title == targetTitle }
        if (index != -1) {
            val note = existing[index]
            val parsed = parseVocabularyNoteContent(note.content)
            val filtered = parsed.filterNot { it.word.equals(wordToRemove, ignoreCase = true) }
            val newContent = filtered.joinToString("\n\n") { entry ->
                buildString {
                    appendLine(entry.word)
                    appendLine("  ${entry.explanation}")
                    appendLine("  ${entry.source}")
                }
            }.trim()
            if (newContent.isBlank()) {
                existing.removeAt(index)
            } else {
                existing[index] = note.copy(content = newContent, updatedAt = System.currentTimeMillis())
            }
            repository.saveGeneralNotes(existing)
            val updated = repository.loadGeneralNotes()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(generalNotes = updated) }
            }
        }
    }
}

