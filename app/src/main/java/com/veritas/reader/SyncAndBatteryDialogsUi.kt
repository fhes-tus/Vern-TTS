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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.veritas.reader.ui.VeritasSwitch


@Composable
internal fun SyncCenterDialog(
    documentCount: Int,
    annotationCount: Int,
    queueCount: Int,
    pronunciationRuleCount: Int,
    inProgress: Boolean,
    message: String?,
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

@Composable
internal fun SyncAndBackupCenterDialog(
    documentCount: Int,
    annotationCount: Int,
    queueCount: Int,
    pronunciationRuleCount: Int,
    inProgress: Boolean,
    message: String?,
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
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sync & Backup Center",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
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
                                text = "Library Sync & Backup Engine",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Vern Sync & Backup creates portable backup files containing your entire library, reading progress, flashcards, notes, bookmarks, and custom voice rules. Import safely merges data without deleting local readings.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                lineHeight = 20.sp
                            )
                        }
                    }

                    // Included Data Summary Card
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
                                text = "Data Included in Backup & Sync",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            SyncInfoRow("Saved Readings", documentCount.toString())
                            SyncInfoRow("Bookmarks & Notes", annotationCount.toString())
                            SyncInfoRow("Reading Queue", queueCount.toString())
                            SyncInfoRow("Pronunciation Rules", pronunciationRuleCount.toString())
                            SyncInfoRow("Flashcards, Decks & Progress", "Included")
                            SyncInfoRow("Reading Streaks & History", "Included")
                            SyncInfoRow("Voice & Reader Settings", "Included")
                        }
                    }

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
                                text = "Backup & Sync Actions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Button(
                                onClick = onExportSyncPack,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = documentCount > 0,
                                shape = VeritasPackStyle.chipShape(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("Export Data Backup (.json)", fontWeight = FontWeight.Bold)
                            }

                            val fullBackupSizeMb = fullBackupEstimateBytes / (1024.0 * 1024.0)
                            OutlinedButton(
                                onClick = onExportFull,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = documentCount > 0,
                                shape = VeritasPackStyle.chipShape()
                            ) {
                                Text(
                                    if (fullBackupEstimateBytes > 0)
                                        String.format(locale, "Export Full Library (.zip ~%.1f MB)", fullBackupSizeMb)
                                    else
                                        "Export Full Library (.zip)",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = onShareSyncPack,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = documentCount > 0,
                                shape = VeritasPackStyle.chipShape()
                            ) {
                                Text("Share Sync Pack (Drive / WhatsApp / Files)", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = onImportSyncPack,
                                modifier = Modifier.fillMaxWidth(),
                                shape = VeritasPackStyle.chipShape()
                            ) {
                                Text("Import Sync / Backup Pack", fontWeight = FontWeight.Bold)
                            }

                            BackupStatusBlock(inProgress = inProgress, message = message)
                        }
                    }

                    // Auto-Backup Settings Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = VeritasPackStyle.cardShape(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = VeritasPackStyle.surfaceAlpha())
                        ),
                        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Automatic Weekly Backups",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Automatically save local backup archives every 7 days",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            VeritasSwitch(
                                checked = autoBackupEnabled,
                                onCheckedChange = { onToggleAutoBackup() }
                            )
                        }
                    }
                }
            }
        }
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
fun UnrestrictedBatteryDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onNeverAskAgain: (() -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
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
        },
        title = {
            Text(
                text = "Unrestricted Battery Playback",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Android battery optimizations will pause or kill Vern speech playback shortly after your screen locks or turns off.",
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
                            text = "How to enable uninterrupted audio:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Tap 'Turn On Unrestricted' and choose 'Allow' on the prompt, or select 'Battery' \u2192 'Unrestricted' in App Info.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onOpenSettings()
                    onDismiss()
                },
                shape = RoundedCornerShape(50)
            ) {
                Text("Turn On Unrestricted")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onNeverAskAgain != null) {
                    TextButton(
                        onClick = {
                            onNeverAskAgain()
                            onDismiss()
                        }
                    ) {
                        Text("Don't Ask Again")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Not Now")
                }
            }
        }
    )
}


