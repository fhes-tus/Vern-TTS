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
            Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_your_own_prompt), style = MaterialTheme.typography.titleLarge)
            Text(if (includeContextField) androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_write_what_you_want_to_ask_add_a_passage_if_your_question_needs_o)
                else androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_write_your_instructions_the_selected_reading_scope_will_be_includ),
                style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(instructions, { instructions = it.take(5000) }, label = { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_instructions)) },
                placeholder = { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_for_example_compare_these_characters_motivations)) },
                minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth(), shape = VeritasPackStyle.compactShape())
            if (includeContextField) OutlinedTextField(passage, { passage = it.take(12000) },
                label = { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_passage_or_context_optional)) }, minLines = 2, maxLines = 6,
                modifier = Modifier.fillMaxWidth(), shape = VeritasPackStyle.compactShape())
            Button(onClick = { onSend(instructions.trim(), passage.trim()) }, enabled = instructions.isNotBlank(),
                modifier = Modifier.fillMaxWidth(), shape = VeritasPackStyle.chipShape()) { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_send_to_assistant)) }
            Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_opens_your_chosen_assistant_or_the_share_menu_your_draft_stays_he),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
