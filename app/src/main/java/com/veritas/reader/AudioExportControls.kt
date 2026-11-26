package com.veritas.reader

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.veritas.reader.ui.screens.formatVeritasBytes
import com.veritas.reader.ui.screens.menuString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun AudioExportOptions(
    chunks: List<String>,
    rate: Float,
    onExport: (AudioExportFormat) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val storage by produceState<Pair<Double, Long>?>(null, chunks, rate) {
        value = withContext(Dispatchers.IO) { estimateAudioExportSeconds(chunks, rate) to context.cacheDir.usableSpace }
    }
    VernAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(menuString(R.string.ui_export_audio)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val space = storage
                if (space == null) {
                    Text(menuString(R.string.audio_export_estimating))
                } else {
                    val (duration, free) = space
                    Text(menuString(R.string.audio_export_space, formatVeritasBytes(free)))
                    val wavBytes = AudioExportFormat.WAV.estimateBytes(duration)
                    if (wavBytes > free) {
                        Text(menuString(R.string.audio_export_wav_space), color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedButton(onClick = { onExport(AudioExportFormat.M4A) }, modifier = Modifier.fillMaxWidth()) {
                        Text(menuString(R.string.audio_export_m4a, formatVeritasBytes(AudioExportFormat.M4A.estimateBytes(duration))))
                    }
                    OutlinedButton(onClick = { onExport(AudioExportFormat.WAV) }, modifier = Modifier.fillMaxWidth()) {
                        Text(menuString(R.string.audio_export_wav, formatVeritasBytes(wavBytes)))
                    }
                    Text(menuString(R.string.audio_export_estimate_help), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(menuString(R.string.ui_cancel)) } }
    )
}

@Composable
internal fun SaveExportedAudioButton(file: File, onSave: (File, Uri) -> Unit) {
    val format = AudioExportFormat.forFileName(file.name)
    val destination = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(format.mimeType)) { uri ->
        if (uri != null) onSave(file, uri)
    }
    TextButton(onClick = { destination.launch(file.name) }) {
        Text(menuString(R.string.audio_export_save_file))
    }
}
