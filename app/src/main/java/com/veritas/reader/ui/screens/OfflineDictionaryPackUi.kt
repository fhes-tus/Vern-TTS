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
import com.veritas.reader.R
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.VernAlertDialog

@Composable
internal fun OfflineDictionaryDownloadControls(work: WorkInfo?, onDownload: () -> Unit, onCancel: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (work?.state?.isFinished == false) {
            val installing = work.progress.getString("phase") == "install"
            val percent = work.progress.getInt("percent", 0)
            Text(if (work.state != WorkInfo.State.RUNNING) menuString(R.string.dict_waiting_download) else if (installing) menuString(R.string.dict_preparing, percent) else menuString(R.string.dict_downloading, percent), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = onCancel) { Text(menuString(R.string.dict_cancel)) }
        } else {
            Button(onClick = onDownload, modifier = Modifier.testTag("dictionary-download"), shape = VeritasPackStyle.chipShape()) { Text(menuString(R.string.dict_download_pack)) }
            work?.outputData?.getString("error")?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
internal fun OfflineDictionaryPackDialog(pack: DictionaryPackInfo?, work: WorkInfo?, onDownload: () -> Unit,
    onCancel: () -> Unit, onRemove: () -> Unit, onSource: () -> Unit, onDismiss: () -> Unit) {
    VernAlertDialog(onDismissRequest = onDismiss, title = { Text(menuString(R.string.dict_offline_pack_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(pack?.let { menuString(R.string.dict_pack_installed, localizedUiValue(it.words), String.format(java.util.Locale.ROOT, "%.1f", it.bytes / 1048576.0)) }
                    ?: menuString(R.string.dict_pack_desc), style = MaterialTheme.typography.bodyMedium)
                if (pack == null) OfflineDictionaryDownloadControls(work, onDownload, onCancel)
                TextButton(onClick = onSource) { Text(menuString(R.string.dict_source_license)) }
                if (pack != null) TextButton(onClick = onRemove, modifier = Modifier.testTag("dictionary-remove")) { Text(menuString(R.string.dict_remove_pack)) }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text(menuString(R.string.dict_done)) } })
}
