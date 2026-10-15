package com.veritas.reader.ui.screens

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.veritas.reader.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun NoteMediaCleanupControl() {
    val context = LocalContext.current
    val repository = remember { DocumentRepository(context) }
    val scope = rememberCoroutineScope()
    var candidates by remember { mutableStateOf<List<UnusedNoteMedia>>(emptyList()) }
    var confirmation by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Checking unused attachments…") }
    LaunchedEffect(Unit) {
        runCatching { withContext(Dispatchers.IO) { repository.findUnusedNoteMedia() } }
            .onSuccess { candidates = it; status = if (it.isEmpty()) "No old unused note attachments" else "${it.size} unused attachments · ${formatVeritasBytes(it.sumOf { it.size })}" }
            .onFailure { status = "Could not safely check attachment references" }
    }
    Text(status, style = MaterialTheme.typography.bodySmall)
    OutlinedButton(enabled = candidates.isNotEmpty(), onClick = { confirmation = true }) { Text("Review attachment cleanup") }
    if (confirmation) AlertDialog(onDismissRequest = { confirmation = false }, title = { Text("Remove unused attachments?") },
        text = { Text("Remove ${candidates.size} old files using ${formatVeritasBytes(candidates.sumOf { it.size })}. Files referenced by notes, Trash, revisions, recovery snapshots and sentence notes are kept. Completed backup archives are separate copies. Files created within the last day are kept.") },
        confirmButton = { TextButton(onClick = {
            confirmation = false
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { repository.removeUnusedNoteMedia(candidates) } }
                    .onSuccess { freed -> candidates = emptyList(); status = "Freed ${formatVeritasBytes(freed)} from unused attachments" }
                    .onFailure { status = "Cleanup stopped; attachment references could not be verified" }
            }
        }) { Text("Remove unused files") } }, dismissButton = { TextButton(onClick = { confirmation = false }) { Text("Cancel") } })
}
