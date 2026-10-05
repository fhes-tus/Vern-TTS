package com.veritas.reader.ui

import com.veritas.reader.VeritasScreen
import android.app.Application
import androidx.lifecycle.viewModelScope
import com.veritas.reader.AnnotationType
import com.veritas.reader.GeminiStudyService
import com.veritas.reader.formatVocabularyEntry
import com.veritas.reader.resolveVocabularySource
import com.veritas.reader.VocabularyEntry
import com.veritas.reader.mutateAnnotations
import com.veritas.reader.mutateGeneralNotes
import com.veritas.reader.GeneralNote
import com.veritas.reader.NoteNotebook
import com.veritas.reader.NoteRevision
import com.veritas.reader.archiveGeneralNoteRevision
import com.veritas.reader.deleteNoteNotebook
import com.veritas.reader.loadNoteNotebooks
import com.veritas.reader.loadNoteRevisions
import com.veritas.reader.loadTrashedGeneralNotes
import com.veritas.reader.moveGeneralNoteToNotebook
import com.veritas.reader.moveGeneralNoteToTrash
import com.veritas.reader.permanentlyDeleteTrashedNote
import com.veritas.reader.restoreGeneralNote
import com.veritas.reader.restoreNoteRevision
import com.veritas.reader.renameNoteNotebook
import com.veritas.reader.setGeneralNoteLabels
import com.veritas.reader.createNoteNotebook
import com.veritas.reader.saveNoteNotebooks
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
import kotlinx.coroutines.sync.withLock
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
        repository.mutateAnnotations { existing ->
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
        
        filtered
        }
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

fun ReaderViewModel.toggleBookmark(index: Int = currentReaderIndex) {
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
                audioDurationSeconds = audioDuration,
                replaceAudio = true
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
            _uiState.update { it.withVisibility(VeritasScreen.DOCUMENT_NOTES, true).copy(
                documentNoteDraft = note
            ) }
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
                it.withVisibility(VeritasScreen.DOCUMENT_NOTES, false).copy(
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
    audioUrls: List<String> = emptyList(),
    notebookIds: List<String>? = null
) {
    val target = uiState.value.generalNoteEditorTarget ?: GeneralNote(
        id = UUID.randomUUID().toString(), title = "", content = "", updatedAt = System.currentTimeMillis()
    )
    val revision = ++generalNoteSaveRevision
    _uiState.update { it.copy(generalNoteEditorTarget = target, generalNoteSaveStatus = "Saving…") }
    val previousSave = generalNoteSaveJob
    generalNoteSaveJob = viewModelScope.launch {
        previousSave?.join()
        try {
        val savedNote = target.copy(
            title = title, content = content, updatedAt = System.currentTimeMillis(),
            color = color, pinned = pinned, isChecklist = isChecklist, imageUrl = imageUrl,
            audioUrl = audioUrls.firstOrNull() ?: audioUrl, audioUrls = audioUrls,
            reminderAt = reminderAt
        )
        var effectiveNote = savedNote
        val updated = withContext(Dispatchers.IO) {
            synchronized(com.veritas.reader.DocumentRepository.LIBRARY_WRITE_LOCK) {
            check(repository.loadTrashedGeneralNotes().none { it.id == target.id }) { "This note is in Trash." }
            val existingNotes = repository.loadGeneralNotes()
            val existing = existingNotes.firstOrNull { it.id == target.id }
            val availableLabels = repository.loadNoteNotebooks().map { it.id }.toSet()
            effectiveNote = savedNote.withLabels((notebookIds ?: existing?.allLabelIds ?: savedNote.allLabelIds).filter { it in availableLabels })
            if (existing != null && effectiveNote.copy(updatedAt = existing.updatedAt) == existing) {
                effectiveNote = existing
                existingNotes
            } else {
                if (existing != null) repository.archiveGeneralNoteRevision(existing)
                repository.mutateGeneralNotes { notes -> listOf(effectiveNote) + notes.filterNot { it.id == target.id } }
            }
            }
        }
        val trashed = withContext(Dispatchers.IO) { repository.loadTrashedGeneralNotes() }
        // (Re)schedule or clear the note's reminder alarm.
        val app = getApplication<Application>()
        val reminderBody = title.ifBlank { content.take(80) }.ifBlank { "Note reminder" }
        if (reminderAt != null && reminderAt > System.currentTimeMillis()) {
            NoteReminderScheduler.ensureChannel(app)
            NoteReminderScheduler.schedule(app, savedNote.id, title.ifBlank { "Vern note" }, reminderBody, reminderAt)
        } else {
            NoteReminderScheduler.cancel(app, savedNote.id)
        }
        withContext(Dispatchers.Main) {
            if (revision != generalNoteSaveRevision || uiState.value.generalNoteEditorTarget?.id != target.id) return@withContext
            _uiState.update {
                if (closeEditor) {
                    it.withVisibility(VeritasScreen.GENERAL_NOTES_EDITOR, false).copy(
                        generalNotes = updated,
                        trashedGeneralNotes = trashed,
                        generalNoteSaveStatus = "Saved",
                        generalNoteEditorTarget = null
                    )
                } else {
                    it.copy(
                        generalNotes = updated,
                        trashedGeneralNotes = trashed,
                        generalNoteSaveStatus = "Saved",
                        generalNoteEditorTarget = effectiveNote
                    )
                }
            }
        }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            android.util.Log.e("Notes", "Could not save note", failure)
            if (revision == generalNoteSaveRevision) _uiState.update { it.copy(generalNoteSaveStatus = "Could not save. Tap Save to retry.") }
        }
    }
}

fun ReaderViewModel.duplicateGeneralNote(note: GeneralNote) {
    viewModelScope.launch(Dispatchers.IO) {
        val newNote = note.copy(
            id = UUID.randomUUID().toString(),
            title = if (note.title.endsWith(" (Copy)")) note.title else note.title + " (Copy)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val updated = repository.mutateGeneralNotes { listOf(newNote) + it }
        withContext(Dispatchers.Main) {
            _uiState.update {
                it.withVisibility(VeritasScreen.GENERAL_NOTES_EDITOR, false).copy(
                    generalNotes = updated,
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
        val updated = repository.mutateGeneralNotes { notes ->
            notes.map { note -> if (note.id == noteId) note.copy(pinned = !note.pinned, updatedAt = System.currentTimeMillis()) else note }
        }
        withContext(Dispatchers.Main) { _uiState.update { it.copy(generalNotes = updated) } }
    }
}

fun ReaderViewModel.changeGeneralNoteColor(noteId: String, colorHex: String?) {
    viewModelScope.launch(Dispatchers.IO) {
        val updated = repository.mutateGeneralNotes { notes ->
            notes.map { note -> if (note.id == noteId) note.copy(color = colorHex, updatedAt = System.currentTimeMillis()) else note }
        }
        withContext(Dispatchers.Main) { _uiState.update { it.copy(generalNotes = updated) } }
    }
}

private fun ReaderViewModel.launchNoteCollectionAction(action: suspend () -> Unit) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            noteCollectionMutex.withLock { action() }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            android.util.Log.e("Notes", "Note collection operation failed", failure)
            _uiState.update { it.copy(importMessage = failure.message ?: "Could not update notes. Please try again.") }
        }
    }
}

fun ReaderViewModel.deleteGeneralNote(noteId: String) {
    launchNoteCollectionAction {
        generalNoteSaveJob?.join()
        repository.moveGeneralNoteToTrash(noteId)
        NoteReminderScheduler.cancel(getApplication(), noteId)
        val updated = repository.loadGeneralNotes()
        val trashed = repository.loadTrashedGeneralNotes()
        withContext(Dispatchers.Main) {
            _uiState.update {
                it.withVisibility(VeritasScreen.GENERAL_NOTES_EDITOR, false).copy(
                    generalNotes = updated,
                    trashedGeneralNotes = trashed,
                    generalNoteEditorTarget = null
                )
            }
        }
    }
}

fun ReaderViewModel.restoreTrashedGeneralNote(noteId: String) {
    launchNoteCollectionAction {
        val active = repository.restoreGeneralNote(noteId)
        val trashed = repository.loadTrashedGeneralNotes()
        val restored = active.firstOrNull { it.id == noteId }
        if (restored?.reminderAt != null && restored.reminderAt > System.currentTimeMillis()) {
            NoteReminderScheduler.ensureChannel(getApplication())
            NoteReminderScheduler.schedule(getApplication(), restored.id, restored.title.ifBlank { "Vern note" }, restored.content.take(80).ifBlank { "Note reminder" }, restored.reminderAt)
        }
        withContext(Dispatchers.Main) { _uiState.update { it.copy(generalNotes = active, trashedGeneralNotes = trashed) } }
    }
}

fun ReaderViewModel.renameNoteNotebook(notebookId: String, name: String) {
    launchNoteCollectionAction {
        val notebooks = repository.renameNoteNotebook(notebookId, name)
        withContext(Dispatchers.Main) { _uiState.update { it.copy(noteNotebooks = notebooks) } }
    }
}

fun ReaderViewModel.permanentlyDeleteTrashedGeneralNote(noteId: String) {
    launchNoteCollectionAction {
        repository.permanentlyDeleteTrashedNote(noteId)
        val trashed = repository.loadTrashedGeneralNotes()
        withContext(Dispatchers.Main) { _uiState.update { it.copy(trashedGeneralNotes = trashed) } }
    }
}

fun ReaderViewModel.createNoteNotebook(name: String) {
    launchNoteCollectionAction {
        val notebooks = repository.createNoteNotebook(name)
        _uiState.update { it.copy(noteNotebooks = notebooks) }
    }
}

fun ReaderViewModel.moveGeneralNoteToNotebook(noteId: String, notebookId: String?) {
    launchNoteCollectionAction {
        val active = repository.moveGeneralNoteToNotebook(noteId, notebookId)
        withContext(Dispatchers.Main) { _uiState.update { it.copy(generalNotes = active) } }
    }
}

fun ReaderViewModel.setGeneralNoteLabels(noteId: String, ids: List<String>) {
    launchNoteCollectionAction {
        generalNoteSaveJob?.join()
        val notes = repository.setGeneralNoteLabels(noteId, ids)
        _uiState.update { state -> state.copy(generalNotes = notes,
            generalNoteEditorTarget = state.generalNoteEditorTarget?.let { if (it.id == noteId) it.withLabels(ids) else it }) }
    }
}

fun ReaderViewModel.setDraftNoteLabels(ids: List<String>) {
    val target = uiState.value.generalNoteEditorTarget
        ?: GeneralNote(UUID.randomUUID().toString(), "", "", System.currentTimeMillis())
    _uiState.update { it.copy(generalNoteEditorTarget = it.generalNoteEditorTarget ?: target) }
    launchNoteCollectionAction {
        generalNoteSaveJob?.join()
        val available = repository.loadNoteNotebooks().map { it.id }.toSet()
        require(ids.all { it in available }) { "A selected label no longer exists." }
        val notes = repository.setGeneralNoteLabels(target.id, ids)
        _uiState.update { state -> state.copy(generalNotes = notes,
            generalNoteEditorTarget = state.generalNoteEditorTarget?.let { if (it.id == target.id) it.withLabels(ids) else it }) }
    }
}

suspend fun ReaderViewModel.createNoteLabel(name: String): NoteNotebook = withContext(Dispatchers.IO) {
    val labels = repository.createNoteNotebook(name)
    _uiState.update { it.copy(noteNotebooks = labels) }
    labels.first { it.name.equals(name.trim(), ignoreCase = true) }
}

fun ReaderViewModel.deleteNoteNotebook(notebookId: String) {
    launchNoteCollectionAction {
        repository.deleteNoteNotebook(notebookId)
        val active = repository.loadGeneralNotes()
        val notebooks = repository.loadNoteNotebooks()
        val trash = repository.loadTrashedGeneralNotes()
        _uiState.update { it.copy(noteNotebooks = notebooks, generalNotes = active, trashedGeneralNotes = trash) }
    }
}

fun ReaderViewModel.noteRevisions(noteId: String): List<NoteRevision> = repository.loadNoteRevisions(noteId)

fun ReaderViewModel.restoreGeneralNoteRevision(revision: NoteRevision) {
    launchNoteCollectionAction {
        generalNoteSaveJob?.join()
        val active = repository.restoreNoteRevision(revision)
        active.firstOrNull { it.id == revision.noteId }?.let { note ->
            NoteReminderScheduler.cancel(getApplication(), note.id)
            note.reminderAt?.takeIf { it > System.currentTimeMillis() }?.let { time ->
                NoteReminderScheduler.ensureChannel(getApplication())
                NoteReminderScheduler.schedule(getApplication(), note.id, note.title.ifBlank { "Vern note" }, note.content.take(80).ifBlank { "Note reminder" }, time)
            }
        }
        withContext(Dispatchers.Main) { _uiState.update { it.copy(generalNotes = active) } }
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

fun ReaderViewModel.appendVocabularyWord(word: String, explanation: String, selectedContext: String? = null, selectedSentenceIndex: Int? = null) {
    val activeDoc = uiState.value.activeDocument ?: return
    val docId = activeDoc.id ?: return
    val source = resolveVocabularySource(activeDoc.sentences, word, selectedSentenceIndex, selectedContext)
    viewModelScope.launch(Dispatchers.IO) {
        val currentIndex = source.sentenceIndex
        val textModel = ReaderTextIndex.build(activeDoc.rawText, activeDoc.pageCount)
        val part = currentIndex?.let { textModel.partForSentence(it) }
        val sectionNum = (part?.index ?: 0) + 1
        val contextSentence = source.contextSentence

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
                finalExplanation = fetched?.definition ?: "Definition unavailable. Look up this word again when connected."
                pronunciation = fetched?.pronunciation
            }
        }

        val now = System.currentTimeMillis()
        val formattedTime = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(now))

        val sourceLabel = if (currentIndex != null) "(looked up: Section $sectionNum, sentence ${currentIndex + 1})" else "(looked up: source location unavailable)"
        val entryText = formatVocabularyEntry(VocabularyEntry(word.trim(), finalExplanation, sourceLabel, currentIndex ?: -1, contextSentence, pronunciation), formattedTime)

        val updated = repository.mutateGeneralNotes { notes ->
            val existing = notes.toMutableList()
            val targetTitle = "__vocab__$docId"
            val vocabIndex = existing.indexOfFirst { it.title == targetTitle }
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
            existing
        }
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(generalNotes = updated) }
        }
    }
}

fun ReaderViewModel.removeVocabularyWord(documentId: String, wordToRemove: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val updated = repository.mutateGeneralNotes { notes ->
            val existing = notes.toMutableList()
            val index = existing.indexOfFirst { it.title == "__vocab__$documentId" }
            if (index != -1) {
                val note = existing[index]
                val filtered = parseVocabularyNoteContent(note.content).filterNot { it.word.equals(wordToRemove, ignoreCase = true) }
                val newContent = filtered.joinToString("\n\n") { formatVocabularyEntry(it) }.trim()
                if (newContent.isBlank()) existing.removeAt(index)
                else existing[index] = note.copy(content = newContent, updatedAt = System.currentTimeMillis())
            }
            existing
        }
        withContext(Dispatchers.Main) {
            _uiState.update { it.copy(generalNotes = updated) }
        }
    }
}

