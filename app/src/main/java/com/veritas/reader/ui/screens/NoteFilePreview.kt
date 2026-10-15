package com.veritas.reader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.VeritasPackStyle

@Composable
internal fun NoteFilePreview(file: NoteBlock.File) {
    val context = LocalContext.current
    Surface(Modifier.fillMaxWidth().testTag("note_file_preview").clickable { openDocumentFile(context, file.path, file.fileName) },
        shape = VeritasPackStyle.cardShape(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Description, "Attached document", tint = MaterialTheme.colorScheme.primary)
            Text(file.fileName.ifBlank { "Attached file" }, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
