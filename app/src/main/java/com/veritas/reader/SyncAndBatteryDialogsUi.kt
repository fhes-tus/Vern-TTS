package com.veritas.reader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PowerSettingsNew
import com.veritas.reader.VernAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.veritas.reader.ui.screens.menuString
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.VernDialog as Dialog
import androidx.compose.ui.window.DialogProperties


@Composable
internal fun SyncCenterDialog(
    documentCount: Int,
    annotationCount: Int,
    queueCount: Int,
    pronunciationRuleCount: Int,
    inProgress: Boolean,
    message: String?,
    onRestoreAutomatic: (AutomaticBackupEntry) -> Unit,
    onSaveAutomaticCopy: (AutomaticBackupEntry, Uri) -> Unit,
    onSendShared: (Uri) -> Unit,
    onReviewShared: (SharedBackupFolderEntry) -> Unit,
    onReviewSettings: (Uri) -> Unit,
    onExportSyncPack: () -> Unit,
    onShareSyncPack: () -> Unit,
    onImportSyncPack: () -> Unit,
    onDismiss: () -> Unit,
    fullBackupEstimateBytes: Long = 0L,
    autoBackupEnabled: Boolean = true,
    onToggleAutoBackup: () -> Unit = {},
    onExportFull: () -> Unit = {}
) {
    SyncAndBackupCenterDialog(
        documentCount = documentCount,
        annotationCount = annotationCount,
        queueCount = queueCount,
        pronunciationRuleCount = pronunciationRuleCount,
        inProgress = inProgress,
        message = message,
        onRestoreAutomatic = onRestoreAutomatic,
        onSaveAutomaticCopy = onSaveAutomaticCopy,
        onSendShared = onSendShared,
        onReviewShared = onReviewShared,
        onReviewSettings = onReviewSettings,
        onExportSyncPack = onExportSyncPack,
        onShareSyncPack = onShareSyncPack,
        onImportSyncPack = onImportSyncPack,
        onDismiss = onDismiss,
        fullBackupEstimateBytes = fullBackupEstimateBytes,
        autoBackupEnabled = autoBackupEnabled,
        onToggleAutoBackup = onToggleAutoBackup,
        onExportFull = onExportFull
    )
}

private enum class BackupCreation { TEXT, MEDIA, SHARED_FOLDER, LOCAL }

@Composable
internal fun SyncAndBackupCenterDialog(
    documentCount: Int,
    annotationCount: Int,
    queueCount: Int,
    pronunciationRuleCount: Int,
    inProgress: Boolean,
    message: String?,
    onRestoreAutomatic: (AutomaticBackupEntry) -> Unit,
    onSaveAutomaticCopy: (AutomaticBackupEntry, Uri) -> Unit,
    onSendShared: (Uri) -> Unit,
    onReviewShared: (SharedBackupFolderEntry) -> Unit,
    onReviewSettings: (Uri) -> Unit,
    onExportSyncPack: () -> Unit,
    onShareSyncPack: () -> Unit,
    onImportSyncPack: () -> Unit,
    onDismiss: () -> Unit,
    fullBackupEstimateBytes: Long = 0L,
    autoBackupEnabled: Boolean = true,
    onToggleAutoBackup: () -> Unit = {},
    onExportFull: () -> Unit = {}
) {
    val locale = LocalConfiguration.current.locales[0]
    var creation by remember { mutableStateOf<BackupCreation?>(null) }
    var sharedFolder by remember { mutableStateOf<Uri?>(null) }
    var manualBackup by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun closeReview() { creation = null; manualBackup = null; sharedFolder = null }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .background(VeritasPackStyle.backgroundBrush(MaterialTheme.colorScheme))
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .widthIn(max = 840.dp)
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_back),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_sync_backup_center),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Summary Banner Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = VeritasPackStyle.cardShape(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_library_sync_backup_engine),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_vern_sync_backup_creates_portable_backup_files_containing_your_en),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f))
                        }
                    }

                    BackupStatusBlock(inProgress = inProgress, message = message)

                    // Actions Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = VeritasPackStyle.cardShape(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = VeritasPackStyle.surfaceAlpha())
                        ),
                        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_portable_backup_actions),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Button(
                                onClick = { creation = BackupCreation.TEXT },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !inProgress,
                                shape = VeritasPackStyle.chipShape(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(menuString(R.string.backup_create_json), fontWeight = FontWeight.Bold)
                            }

                            Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_use_the_zip_backup_to_carry_notes_recordings_images_and_quiz_medi), style = MaterialTheme.typography.bodySmall)

                            val fullBackupSizeMb = fullBackupEstimateBytes / (1024.0 * 1024.0)
                            OutlinedButton(
                                onClick = { creation = BackupCreation.MEDIA },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !inProgress,
                                shape = VeritasPackStyle.chipShape()
                            ) {
                                Text(
                                    if (fullBackupEstimateBytes > 0)
                                        com.veritas.reader.ui.screens.menuString(R.string.settings_export_zip_size, String.format(locale, "%.1f", fullBackupSizeMb))
                                    else
                                        com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_export_portable_backup_with_media_zip),
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = onImportSyncPack,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !inProgress,
                                shape = VeritasPackStyle.chipShape()
                            ) {
                                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_import_sync_backup_pack), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    AutomaticBackupCard(
                        weeklyEnabled = autoBackupEnabled,
                        transferInProgress = inProgress,
                        onReviewBackup = { action -> manualBackup = action; creation = BackupCreation.LOCAL },
                        onToggleWeekly = onToggleAutoBackup,
                        onRestore = onRestoreAutomatic,
                        onSaveCopy = onSaveAutomaticCopy
                    )
                    VernSettingsSharingCard(inProgress, onReviewSettings)
                    SharedBackupFolderCard(inProgress, { folder -> sharedFolder = folder; creation = BackupCreation.SHARED_FOLDER }, onReviewShared)
                }
            }
        }
    }
    creation?.let { type ->
        AlertDialog(
            onDismissRequest = ::closeReview,
            title = { Text(menuString(when (type) {
                BackupCreation.TEXT -> R.string.backup_create_json
                BackupCreation.MEDIA -> R.string.ui_export_portable_backup_with_media_zip
                BackupCreation.SHARED_FOLDER -> R.string.backup_copy_to_folder
                BackupCreation.LOCAL -> R.string.auto_backup_now
            })) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(menuString(R.string.ui_data_included_in_backup_sync), style = MaterialTheme.typography.titleSmall)
                    SyncInfoRow(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_saved_readings), documentCount.toString())
                    SyncInfoRow(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_bookmarks_notes), annotationCount.toString())
                    SyncInfoRow(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_reading_queue), queueCount.toString())
                    SyncInfoRow(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_pronunciation_rules), pronunciationRuleCount.toString())
                    SyncInfoRow(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_flashcards_decks_progress), com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_included))
                    SyncInfoRow(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_reading_streaks_history), com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_included))
                    SyncInfoRow(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_voice_reader_settings), com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_included))
                    Text(menuString(if (type != BackupCreation.MEDIA) R.string.backup_json_excludes_media
                        else R.string.backup_includes_media), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Row {
                    if (type == BackupCreation.TEXT) {
                        TextButton(enabled = !inProgress, onClick = { closeReview(); onShareSyncPack() }) {
                            Text(menuString(R.string.ui_share))
                        }
                    }
                    TextButton(enabled = !inProgress, onClick = {
                        val folder = sharedFolder
                        val startManualBackup = manualBackup
                        closeReview()
                        when (type) {
                            BackupCreation.TEXT -> onExportSyncPack()
                            BackupCreation.MEDIA -> onExportFull()
                            BackupCreation.SHARED_FOLDER -> folder?.let(onSendShared)
                            BackupCreation.LOCAL -> startManualBackup?.invoke()
                        }
                    }) { Text(menuString(if (type == BackupCreation.LOCAL) R.string.auto_backup_now else R.string.backup_save_file)) }
                }
            },
            dismissButton = { TextButton(onClick = ::closeReview) { Text(menuString(R.string.ui_cancel)) } }
        )
    }
}

@Composable
internal fun SyncInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SoftChip(value)
    }
}

fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

/** Opens a reversible settings screen, even when the exemption is already enabled. */
fun openAppBatterySettings(context: Context) {
    val packageUri = Uri.fromParts("package", context.packageName, null)
    val routes = listOf(
        // Android Settings' app-specific battery detail action; OEMs may not expose it.
        Intent("android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL").setData(packageUri)
            .putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(packageUri),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    )
    for (intent in routes) {
        if (runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)) return
    }
    android.widget.Toast.makeText(context, "Open phone Settings → Apps → Vern → Battery.", android.widget.Toast.LENGTH_LONG).show()
}

fun requestIgnoreBatteryOptimizations(context: Context) {
    if (!isBatteryOptimizationIgnored(context)) {
        val request = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Try the intent directly: package visibility can hide a valid system handler from queries.
        if (runCatching { context.startActivity(request); true }.getOrDefault(false)) return
    }
    openAppBatterySettings(context)
}

@Composable
fun rememberBatteryOptimizationIgnored(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var ignored by androidx.compose.runtime.remember(context) { androidx.compose.runtime.mutableStateOf(isBatteryOptimizationIgnored(context)) }
    androidx.compose.runtime.DisposableEffect(lifecycle, context) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) ignored = isBatteryOptimizationIgnored(context)
        }
        lifecycle.addObserver(observer)
        ignored = isBatteryOptimizationIgnored(context)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return ignored
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun UnrestrictedBatteryDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onNeverAskAgain: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier.vernDialogPane(),
            shape = androidx.compose.material3.AlertDialogDefaults.shape,
            color = androidx.compose.material3.AlertDialogDefaults.containerColor,
            tonalElevation = androidx.compose.material3.AlertDialogDefaults.TonalElevation
        ) {
        Column(
            modifier = Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.PowerSettingsNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_unrestricted_battery_playback),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_android_battery_optimizations_will_pause_or_kill_vern_speech_play),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_how_to_enable_uninterrupted_audio),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_tap_turn_on_unrestricted_and_choose_allow_on_the_prompt_or_select),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    onOpenSettings()
                    onDismiss()
                },
                shape = RoundedCornerShape(50)
            ) {
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_turn_on_unrestricted))
            }
                if (onNeverAskAgain != null) {
                    TextButton(
                        onClick = {
                            onNeverAskAgain()
                            onDismiss()
                        }
                    ) {
                        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_don_t_ask_again))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_not_now))
                }
        }
        }
        }
    }
}
