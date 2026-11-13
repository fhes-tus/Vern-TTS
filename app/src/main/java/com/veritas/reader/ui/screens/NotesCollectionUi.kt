package com.veritas.reader.ui.screens
import com.veritas.reader.VernAlertDialog as AlertDialog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Restore
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
        trailingIcon = { Icon(Icons.Default.Close, com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_show_all_notes), Modifier.size(18.dp)) }, modifier = Modifier.padding(horizontal = 6.dp))
}

@Composable
internal fun NotebookPickerDialog(
    notebooks: List<NoteNotebook>, selected: String?, onSelect: (String?) -> Unit, onDismiss: () -> Unit,
    movingNote: Boolean = false, onCreate: (() -> Unit)? = null, onManage: (() -> Unit)? = null
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (movingNote) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_notebooks) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_notebooks)) },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            (listOf(null to if (movingNote) "No notebook" else "All notes") + notebooks.map { it.id to it.name }).forEach { (id, name) ->
                TextButton({ onSelect(id) }, Modifier.fillMaxWidth()) {
                    Text(name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (selected == id) Icon(Icons.Default.Check, com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_selected))
                }
            }
            if (onCreate != null || onManage != null) HorizontalDivider()
            onCreate?.let { TextButton(it) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_new_notebook)) } }
            onManage?.let { TextButton(it) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_manage_notebooks)) } }
        } }, confirmButton = { TextButton(onDismiss) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_close)) } })
}

@Composable
internal fun NotebookNameDialog(
    title: String, value: String, onValue: (String) -> Unit, notebooks: List<NoteNotebook>,
    editingId: String? = null, onDismiss: () -> Unit, onSave: () -> Unit
) {
    val duplicate = notebooks.any { it.id != editingId && it.name.equals(value.trim(), ignoreCase = true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(value, { onValue(it.take(60)) }, singleLine = true,
            label = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_notebook_name)) }, isError = duplicate,
            supportingText = { if (duplicate) Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_a_notebook_with_this_name_already_exists)) }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onSave, enabled = value.isNotBlank() && !duplicate) { Text(if (editingId == null) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_create) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_save)) } },
        dismissButton = { TextButton(onDismiss) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_cancel)) } })
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
        AlertDialog(onDismissRequest = { restore = null }, title = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_restore_this_version)) },
            text = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_your_current_version_will_be_kept_in_history_the_note_keeps_its_c)) },
            confirmButton = { TextButton({ actions.restoreRevision(selected); onDismiss() }) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_restore)) } },
            dismissButton = { TextButton({ restore = null }) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_cancel)) } })
        return
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_revision_history)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    failed -> { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_could_not_load_history)); TextButton({ attempt++ }) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_retry)) } }
                    revisions == null -> CircularProgressIndicator()
                    revisions!!.isEmpty() -> Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_no_earlier_versions_yet_history_is_kept_when_you_edit_this_note))
                    else -> revisions!!.forEach { revision ->
                        Surface(shape = VeritasPackStyle.compactShape(), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                                    .format(java.util.Date(revision.savedAt)), style = MaterialTheme.typography.labelMedium)
                                Text(revision.snapshot.title.ifBlank { com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_untitled_note) }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                val preview = remember(revision) {
                                    VeritasNoteEditing.parseNoteBlocks(NoteLinks.strip(revision.snapshot.content))
                                        .filterIsInstance<NoteBlock.Text>().joinToString("\n") { it.value.text }
                                }
                                Text(RichTextFormatter.transform(preview, MaterialTheme.colorScheme.onSurfaceVariant).text,
                                    maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                TextButton({ restore = revision }) {
                                    Icon(Icons.Outlined.Restore, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_restore_this_version_86a8990))
                                }
                            }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onDismiss) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_done)) } })
}
