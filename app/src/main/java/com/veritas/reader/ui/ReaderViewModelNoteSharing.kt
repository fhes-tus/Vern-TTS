package com.veritas.reader.ui

import android.app.Application
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.viewModelScope
import com.veritas.reader.GeneralNote
import com.veritas.reader.IncomingShare
import com.veritas.reader.NoteAttachmentStore
import com.veritas.reader.mutateGeneralNotes
import com.veritas.reader.ui.screens.NoteBlock
import com.veritas.reader.ui.screens.VeritasNoteEditing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Save incoming content before displaying it: an unchanged share is already a real note. */
internal fun ReaderViewModel.importSharedNote(share: IncomingShare): kotlinx.coroutines.Job {
    val previousShare = generalNoteShareJob
    val job = viewModelScope.launch {
        previousShare?.join()
        generalNoteSaveJob?.join()
        val context = getApplication<Application>()
        if (share.uris.isNotEmpty()) Toast.makeText(context, "Saving shared attachments…", Toast.LENGTH_SHORT).show()
        val ownedFiles = mutableListOf<File>()
        var persisted = false
        try {
            val (note, notes) = withContext(Dispatchers.IO) {
                val blocks = mutableListOf<NoteBlock>(NoteBlock.Text(TextFieldValue(share.text)))
                var attachmentTitle = ""
                share.uris.forEach { uri ->
                    val (file, originalName, size) = NoteAttachmentStore.copyDocument(context, uri, share.mimeType)
                    ownedFiles.add(file)
                    if (attachmentTitle.isEmpty()) attachmentTitle = originalName
                    val providerMime = context.contentResolver.getType(uri).orEmpty()
                    val guessedMime = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(originalName.substringAfterLast('.', "").lowercase())
                    val mime = providerMime.takeIf { it != "application/octet-stream" && it.contains('/') && !it.contains('*') }
                        ?: guessedMime ?: share.mimeType.orEmpty()
                    blocks.add(when {
                        mime.startsWith("image/") -> NoteBlock.Image(file.absolutePath)
                        mime.startsWith("audio/") -> NoteBlock.Audio(file.absolutePath)
                        mime.startsWith("video/") -> NoteBlock.Video(file.absolutePath)
                        else -> NoteBlock.File(file.absolutePath, originalName, size)
                    })
                }
                val audios = blocks.filterIsInstance<NoteBlock.Audio>().map { it.path }
                val note = GeneralNote(
                    id = UUID.randomUUID().toString(),
                    title = share.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(50)
                        ?: attachmentTitle.take(80),
                    content = VeritasNoteEditing.serializeNoteBlocks(blocks),
                    updatedAt = System.currentTimeMillis(),
                    imageUrl = blocks.filterIsInstance<NoteBlock.Image>().firstOrNull()?.path,
                    audioUrl = audios.firstOrNull(), audioUrls = audios
                )
                // Once commit begins, cancellation must not discard files referenced by a saved note.
                withContext(NonCancellable) {
                    val notes = repository.mutateGeneralNotes { listOf(note) + it }
                    persisted = true
                    note to notes
                }
            }
            _uiState.update {
                it.copy(generalNotes = notes, generalNoteEditorTarget = note,
                    showGeneralNotesEditor = true, generalNoteSaveStatus = "Saved",
                    generalNoteEditorSession = it.generalNoteEditorSession + 1)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            android.util.Log.e("Notes", "Could not import shared note", failure)
            Toast.makeText(context, "Could not save shared content. Please share it again.", Toast.LENGTH_LONG).show()
        } finally {
            if (!persisted) withContext(NonCancellable + Dispatchers.IO) { ownedFiles.forEach { it.delete() } }
        }
    }
    generalNoteShareJob = job
    return job
}
