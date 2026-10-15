package com.veritas.reader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.NoteNotebook
import com.veritas.reader.VeritasPackStyle

@Composable
internal fun NoteLabelsDialog(labels: List<NoteNotebook>, assigned: List<String>, onSave: (List<String>) -> Unit,
    onDismiss: () -> Unit, onCreate: suspend (String) -> NoteNotebook) {
    var selected by remember { mutableStateOf(assigned.toSet()) }
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Notebooks") },
        text = { Column {
            OutlinedTextField(query, { query = it.take(60); error = null }, label = { Text("Find or create a notebook") },
                singleLine = true, enabled = !creating, isError = error != null,
                supportingText = { error?.let { Text(it) } }, modifier = Modifier.fillMaxWidth())
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                labels.filter { it.name.contains(query.trim(), true) }.forEach { label ->
                    Row(Modifier.fillMaxWidth().toggleable(label.id in selected, role = Role.Checkbox,
                        onValueChange = { checked -> selected = if (checked) selected + label.id else selected - label.id })
                        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(label.id in selected, onCheckedChange = null)
                        Text(label.name, Modifier.weight(1f).padding(start = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (query.isNotBlank() && labels.none { it.name.equals(query.trim(), true) }) {
                    TextButton({
                        creating = true
                        scope.launch {
                            try {
                                val label = onCreate(query.trim())
                                selected = selected + label.id
                                query = ""
                            } catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { error = failure.message ?: "Could not create notebook. Try again." }
                            finally { creating = false }
                        }
                    }, enabled = !creating) {
                        Text(if (!creating) "Create “${query.trim()}”" else "Creating notebook…")
                    }
                } else if (labels.isEmpty()) Text("Create notebooks to organise your notes. A note can belong to more than one.", Modifier.padding(top = 12.dp))
            }
        } },
        confirmButton = { TextButton({ onSave(labels.map { it.id }.filter { it in selected }) }, enabled = !creating) { Text("Done") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NoteLabelsRow(labels: List<NoteNotebook>, assigned: List<String>, compact: Boolean = false, onLabel: (String) -> Unit) {
    val selected = labels.filter { it.id in assigned }
    if (selected.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (if (compact) selected.take(2) else selected).forEach { label ->
            Surface(onClick = { onLabel(label.id) }, shape = VeritasPackStyle.chipShape(),
                color = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface) {
                Text(label.name, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (compact && selected.size > 2) Text("+${selected.size - 2}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(5.dp))
    }
}
