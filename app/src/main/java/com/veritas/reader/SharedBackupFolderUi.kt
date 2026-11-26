package com.veritas.reader

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.veritas.reader.ui.screens.formatVeritasBytes
import com.veritas.reader.ui.screens.menuString
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SharedBackupFolderCard(
    transferInProgress: Boolean,
    onSend: (Uri) -> Unit,
    onReview: (SharedBackupFolderEntry) -> Unit
) {
    var reviewFolder by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pickerFailed by remember { mutableStateOf(false) }
    val sendLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && !transferInProgress) onSend(uri)
    }
    val reviewLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && !transferInProgress) reviewFolder = uri
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = VeritasPackStyle.cardShape(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = VeritasPackStyle.surfaceAlpha())
        ),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(menuString(R.string.shared_sync_title), style = MaterialTheme.typography.titleMedium)
            Text(
                menuString(R.string.backup_shared_folder_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = { pickerFailed = runCatching { sendLauncher.launch(null) }.isFailure },
                    enabled = !transferInProgress,
                    shape = VeritasPackStyle.chipShape()
                ) { Text(menuString(R.string.backup_copy_to_folder)) }
                OutlinedButton(
                    onClick = { pickerFailed = runCatching { reviewLauncher.launch(null) }.isFailure },
                    enabled = !transferInProgress,
                    shape = VeritasPackStyle.chipShape()
                ) { Text(menuString(R.string.shared_sync_review)) }
            }
            if (pickerFailed) Text(menuString(R.string.shared_sync_picker_unavailable), color = MaterialTheme.colorScheme.error)
        }
    }
    reviewFolder?.let { folder ->
        SharedBackupFolderPicker(folder, transferInProgress, onDismiss = { reviewFolder = null }) { entry ->
            reviewFolder = null
            onReview(entry)
        }
    }
}

@Composable
private fun SharedBackupFolderPicker(
    folder: Uri,
    transferInProgress: Boolean,
    onDismiss: () -> Unit,
    onSelect: (SharedBackupFolderEntry) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale).withZone(ZoneId.systemDefault())
    }
    var refresh by remember(folder) { mutableIntStateOf(0) }
    val result by produceState<Result<SharedBackupFolderListing>?>(null, folder, refresh) {
        value = null
        value = try {
            Result.success(SharedBackupFolder.list(context, folder))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Result.failure(failure)
        }
    }
    val listing = result?.getOrNull()
    VernAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(menuString(R.string.backup_choose_file)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    result == null -> Text(menuString(R.string.shared_sync_loading))
                    listing == null -> Text(menuString(R.string.shared_sync_folder_unavailable))
                    listing.entries.isEmpty() && !listing.providerLoading -> Text(menuString(R.string.shared_sync_empty))
                    else -> {
                        if (listing.providerLoading) Text(menuString(R.string.shared_sync_provider_loading))
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                            items(listing.entries, key = { it.uri.toString() }) { entry ->
                                Column(
                                    Modifier.fillMaxWidth()
                                        .clickable(enabled = !transferInProgress, role = Role.Button) { onSelect(entry) }
                                        .padding(horizontal = 8.dp, vertical = 12.dp)
                                ) {
                                    Text(format.format(entry.createdAt), style = MaterialTheme.typography.bodyLarge)
                                    val identity = entry.expectedSha256.take(8)
                                    Text(
                                        if (entry.sizeBytes == null) identity else menuString(R.string.shared_sync_snapshot_details,
                                            formatVeritasBytes(entry.sizeBytes), identity),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { refresh++ }, enabled = result != null && !transferInProgress) {
                Text(menuString(R.string.shared_sync_refresh))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(menuString(R.string.ui_cancel)) } }
    )
}
