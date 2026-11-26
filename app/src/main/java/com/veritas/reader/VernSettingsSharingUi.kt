package com.veritas.reader

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.veritas.reader.ui.screens.menuString
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal suspend fun IncomingShare.settingsFile(context: Context): Uri? {
    val uri = uris.singleOrNull()?.takeIf { it.scheme == "content" } ?: return null
    val type = mimeType?.substringBefore(';')?.trim()?.lowercase(java.util.Locale.ROOT)
    if (type == VernSettingsProfile.MIME_TYPE || type == VernSettingsProfile.VERN_MIME_TYPE) return uri
    if (uri.lastPathSegment?.endsWith(VernSettingsProfile.FILE_SUFFIX, ignoreCase = true) == true) return uri
    if (type != null && type !in setOf("application/octet-stream", "text/plain", "*/*")) return null
    val name = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
    return uri.takeIf { name?.endsWith(VernSettingsProfile.FILE_SUFFIX, ignoreCase = true) == true }
}

@Composable
internal fun VernSettingsSharingCard(busy: Boolean, onReview: (Uri) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onReview(uri)
    }
    Card(Modifier.fillMaxWidth(), shape = VeritasPackStyle.cardShape()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(menuString(R.string.vern_settings_share_title), style = MaterialTheme.typography.titleMedium)
            Text(menuString(R.string.vern_settings_share_description), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = {
                    sending = true
                    failed = false
                    scope.launch {
                        try {
                            val uri = withContext(Dispatchers.IO) {
                                val profile = VernSettingsProfile.encode(DocumentRepository(context.applicationContext).loadReaderSettings())
                                val folder = File(context.cacheDir, "shared_exports").apply { check(isDirectory || mkdirs()) }
                                val file = File.createTempFile("Vern-settings-", VernSettingsProfile.FILE_SUFFIX, folder)
                                try {
                                    file.writeText(profile, Charsets.UTF_8)
                                    SafeFileSharing.uri(context, "${context.packageName}.fileprovider", file)
                                } catch (error: Exception) {
                                    file.delete()
                                    throw error
                                }
                            }
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = VernSettingsProfile.MIME_TYPE
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newRawUri("Vern settings", uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, context.getString(R.string.vern_settings_share_action)))
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            failed = true
                        } finally {
                            sending = false
                        }
                    }
                },
                enabled = !busy && !sending
            ) { Text(menuString(R.string.vern_settings_share_action)) }
            OutlinedButton(
                onClick = { picker.launch(arrayOf(VernSettingsProfile.MIME_TYPE, VernSettingsProfile.VERN_MIME_TYPE,
                    "application/octet-stream", "text/plain")) },
                enabled = !busy && !sending
            ) { Text(menuString(R.string.vern_settings_import_action)) }
            if (failed) Text(menuString(R.string.vern_settings_share_failed), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun VernSettingsReviewDialog(
    uri: Uri,
    current: ReaderSettings,
    busy: Boolean,
    onApply: (VernSettingsProfile) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val result by produceState<Result<VernSettingsProfile>?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                require(uri.scheme == "content")
                val stream = checkNotNull(context.contentResolver.openInputStream(uri))
                Result.success(stream.use { VernSettingsProfile.read(it) })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
    }
    val profile = result?.getOrNull()
    VernAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(menuString(R.string.vern_settings_import_title)) },
        text = {
            when {
                profile != null -> {
                    val preview = profile.applyTo(current)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(menuString(R.string.vern_settings_import_description))
                        Text(VeritasThemeCatalog.displayName(preview.themeId), style = MaterialTheme.typography.titleSmall)
                        Text(menuString(R.string.vern_settings_share_reading_size, preview.fontSizeSp))
                    }
                }
                result == null -> Text(menuString(R.string.vern_settings_import_loading))
                else -> Text(menuString(R.string.vern_settings_import_invalid))
            }
        },
        confirmButton = {
            if (profile != null) {
                TextButton(onClick = { onApply(profile) }, enabled = !busy) {
                    Text(menuString(R.string.vern_settings_import_apply))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(menuString(R.string.ui_cancel)) } }
    )
}
