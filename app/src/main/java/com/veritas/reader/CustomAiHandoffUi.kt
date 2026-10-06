package com.veritas.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun CustomAiHandoffCard(includeContextField: Boolean, onSend: (String, String) -> Unit) {
    var instructions by rememberSaveable { mutableStateOf("") }
    var passage by rememberSaveable { mutableStateOf("") }
    Card(shape = VeritasPackStyle.cardShape(), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Your own prompt", style = MaterialTheme.typography.titleLarge)
            Text(if (includeContextField) "Write what you want to ask. Add a passage if your question needs one."
                else "Write your instructions. The selected reading scope will be included with your prompt.",
                style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(instructions, { instructions = it.take(5000) }, label = { Text("Instructions") },
                placeholder = { Text("For example: compare these characters’ motivations.") },
                minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth(), shape = VeritasPackStyle.compactShape())
            if (includeContextField) OutlinedTextField(passage, { passage = it.take(12000) },
                label = { Text("Passage or context (optional)") }, minLines = 2, maxLines = 6,
                modifier = Modifier.fillMaxWidth(), shape = VeritasPackStyle.compactShape())
            Button(onClick = { onSend(instructions.trim(), passage.trim()) }, enabled = instructions.isNotBlank(),
                modifier = Modifier.fillMaxWidth(), shape = VeritasPackStyle.chipShape()) { Text("Send to assistant") }
            Text("Opens your chosen assistant or the share menu. Your draft stays here when you return.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
