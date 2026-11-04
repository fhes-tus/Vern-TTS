package com.veritas.reader.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.veritas.reader.AppLanguageCatalog
import com.veritas.reader.AppLanguages
import com.veritas.reader.R

@Composable
fun AppLanguageDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val current = rememberAppLanguageSelection()
    var selected by rememberSaveable(current) { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_language)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.language_explanation))
                Text(stringResource(R.string.language_device_behavior),style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.language_preview_notice), style = MaterialTheme.typography.bodySmall)
                Column(Modifier.selectableGroup()) {
                    LanguageChoice("", deviceLanguageChoiceLabel(), selected) { selected = it }
                    AppLanguageCatalog.choices.forEach { choice ->
                        LanguageChoice(choice.tag, choice.nativeName, selected) { selected = it }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                if(selected.isEmpty() || selected != current) AppLanguages.select(context, selected)
            }) { Text(stringResource(R.string.language_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun LanguageChoice(tag: String, name: String, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected == tag, role = Role.RadioButton, onClick = { onSelect(tag) })
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected == tag, onClick = null)
        Text(name, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content))
    }
}
