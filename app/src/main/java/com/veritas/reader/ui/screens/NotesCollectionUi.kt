package com.veritas.reader.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.NoteNotebook
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.NoteRevision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException

@Composable
internal fun NotesFilterRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, value) ->
            FilterChip(selected == value, { onSelect(value) }, label = { Text(label) }, shape = VeritasPackStyle.chipShape(),
                colors = VeritasPackStyle.filterChipColors(MaterialTheme.colorScheme))
        }
    }
}

@Composable
internal fun NotesCollectionLabel(label: String, onClear: () -> Unit) {
    InputChip(selected = true, onClick = onClear, label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingIcon = { Icon(Icons.Default.Close, "Show all notes", Modifier.size(18.dp)) }, modifier = Modifier.padding(horizontal = 6.dp))
}

@Composable
internal fun NotebookPickerDialog(
    notebooks: List<NoteNotebook>, selected: String?, onSelect: (String?) -> Unit, onDismiss: () -> Unit,
    movingNote: Boolean = false, onCreate: (() -> Unit)? = null, onManage: (() -> Unit)? = null
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (movingNote) "Notebooks" else "Notebooks") },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            (listOf(null to if (movingNote) "No notebook" else "All notes") + notebooks.map { it.id to it.name }).forEach { (id, name) ->
                TextButton({ onSelect(id) }, Modifier.fillMaxWidth()) {
                    Text(name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (selected == id) Icon(Icons.Default.Check, "Selected")
                }
            }
            if (onCreate != null || onManage != null) HorizontalDivider()
            onCreate?.let { TextButton(it) { Text("New notebook") } }
            onManage?.let { TextButton(it) { Text("Manage notebooks") } }
        } }, confirmButton = { TextButton(onDismiss) { Text("Close") } })
}

@Composable
internal fun NotebookNameDialog(
    title: String, value: String, onValue: (String) -> Unit, notebooks: List<NoteNotebook>,
    editingId: String? = null, onDismiss: () -> Unit, onSave: () -> Unit
) {
    val duplicate = notebooks.any { it.id != editingId && it.name.equals(value.trim(), ignoreCase = true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(value, { onValue(it.take(60)) }, singleLine = true,
            label = { Text("Notebook name") }, isError = duplicate,
            supportingText = { if (duplicate) Text("A notebook with this name already exists.") }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onSave, enabled = value.isNotBlank() && !duplicate) { Text(if (editingId == null) "Create" else "Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@Composable
internal fun NoteRevisionHistoryDialog(noteId: String, actions: NoteCollectionActions, onDismiss: () -> Unit) {
    var revisions by remember(noteId) { mutableStateOf<List<NoteRevision>?>(null) }
    var failed by remember(noteId) { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var restore by remember { mutableStateOf<NoteRevision?>(null) }
    LaunchedEffect(noteId, attempt) {
        failed = false
        try {
            revisions = withContext(Dispatchers.IO) { actions.revisionsFor(noteId) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }
    val selected = restore
    if (selected != null) {
        AlertDialog(onDismissRequest = { restore = null }, title = { Text("Restore this version?") },
            text = { Text("Your current version will be kept in history. The note keeps its current notebooks.") },
            confirmButton = { TextButton({ actions.restoreRevision(selected); onDismiss() }) { Text("Restore") } },
            dismissButton = { TextButton({ restore = null }) { Text("Cancel") } })
        return
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Revision history") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    failed -> { Text("Could not load history."); TextButton({ attempt++ }) { Text("Retry") } }
                    revisions == null -> CircularProgressIndicator()
                    revisions!!.isEmpty() -> Text("No earlier versions yet. History is kept when you edit this note.")
                    else -> revisions!!.forEach { revision ->
                        Surface(shape = VeritasPackStyle.compactShape(), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                                    .format(java.util.Date(revision.savedAt)), style = MaterialTheme.typography.labelMedium)
                                Text(revision.snapshot.title.ifBlank { "Untitled note" }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val preview = remember(revision) {
                                    VeritasNoteEditing.parseNoteBlocks(NoteLinks.strip(revision.snapshot.content))
                                        .filterIsInstance<NoteBlock.Text>().joinToString("\n") { it.value.text }
                                }
                                Text(RichTextFormatter.transform(preview, MaterialTheme.colorScheme.onSurfaceVariant).text,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                TextButton({ restore = revision }) { Text("Restore this version") }
                            }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onDismiss) { Text("Done") } })
}
