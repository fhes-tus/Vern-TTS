package com.veritas.reader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import com.veritas.reader.DictionaryPackInfo
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.VernAlertDialog

@Composable
internal fun OfflineDictionaryDownloadControls(work: WorkInfo?, onDownload: () -> Unit, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (work?.state?.isFinished == false) {
            val installing = work.progress.getString("phase") == "install"
            val percent = work.progress.getInt("percent", 0)
            Text(if (work.state != WorkInfo.State.RUNNING) "Waiting to download…" else if (installing) "Preparing dictionary… $percent%" else "Downloading… $percent%", style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = onCancel) { Text("Cancel") }
        } else {
            Button(onClick = onDownload, modifier = Modifier.testTag("dictionary-download"), shape = VeritasPackStyle.chipShape()) { Text("Download English dictionary") }
            work?.outputData?.getString("error")?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
internal fun OfflineDictionaryPackDialog(pack: DictionaryPackInfo?, work: WorkInfo?, onDownload: () -> Unit,
    onCancel: () -> Unit, onRemove: () -> Unit, onSource: () -> Unit, onDismiss: () -> Unit) {
    VernAlertDialog(onDismissRequest = onDismiss, title = { Text("Offline dictionary") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(pack?.let { "${localizedUiValue(it.words)} headwords · ${String.format(java.util.Locale.ROOT, "%.1f", it.bytes / 1048576.0)} MB on device" }
                    ?: "English · optional 9.2 MB download", style = MaterialTheme.typography.bodyMedium)
                if (pack == null) OfflineDictionaryDownloadControls(work, onDownload, onCancel)
                TextButton(onClick = onSource) { Text("Source & license") }
                if (pack != null) TextButton(onClick = onRemove, modifier = Modifier.testTag("dictionary-remove")) { Text("Remove pack") }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
