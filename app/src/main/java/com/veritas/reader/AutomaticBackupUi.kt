package com.veritas.reader

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.veritas.reader.ui.VeritasSwitch
import com.veritas.reader.ui.screens.formatVeritasBytes
import com.veritas.reader.ui.screens.menuString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private data class BackupOverview(
    val files: List<AutomaticBackupEntry>,
    val lastResult: AutomaticBackupStatus?,
    val weeklyRegistered: Boolean,
    val running: Boolean,
    val manualPending: Boolean,
    val retryPending: Boolean
)

@Composable
internal fun AutomaticBackupCard(
    weeklyEnabled: Boolean,
    transferInProgress: Boolean,
    onReviewBackup: (() -> Unit) -> Unit,
    onToggleWeekly: () -> Unit,
    onRestore: (AutomaticBackupEntry) -> Unit,
    onSaveCopy: (AutomaticBackupEntry, Uri) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val workManager = remember(context) { WorkManager.getInstance(context) }
    val overview by produceState<BackupOverview?>(null, workManager) {
        combine(
            workManager.getWorkInfosForUniqueWorkFlow(AutoBackupWorker.WORK_NAME),
            workManager.getWorkInfosForUniqueWorkFlow(AutoBackupWorker.MANUAL_WORK_NAME)
        ) { weekly, manual ->
            withContext(Dispatchers.IO) {
                val pending = (weekly + manual).filter { !it.state.isFinished }
                BackupOverview(
                    files = AutomaticBackupFiles.list(context),
                    lastResult = AutoBackupWorker.lastStatus(context),
                    weeklyRegistered = weekly.any { !it.state.isFinished },
                    running = pending.any { it.state == WorkInfo.State.RUNNING },
                    manualPending = manual.any { !it.state.isFinished },
                    retryPending = pending.any { it.runAttemptCount > 0 }
                )
            }
        }.collect { value = it }
    }
    val current = overview
    var expanded by rememberSaveable { mutableStateOf(false) }
    var exportEntry by rememberSaveable { mutableStateOf<AutomaticBackupEntry?>(null) }
    var requesting by remember { mutableStateOf(false) }
    var requestFailed by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val entry = exportEntry
        exportEntry = null
        if (uri != null && entry != null) onSaveCopy(entry, uri)
    }
    val locale = LocalConfiguration.current.locales[0]
    val dayFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val files = current?.files.orEmpty()
    val latest = files.firstOrNull()
    val weeklyLabel = menuString(R.string.ui_automatic_weekly_backups)
    val workStatus = when {
        current?.running == true -> R.string.auto_backup_running
        requestFailed -> R.string.auto_backup_request_failed
        requesting || (current?.manualPending == true && !current.retryPending) -> R.string.auto_backup_queued
        current?.lastResult == AutomaticBackupStatus.LOCAL_ONLY -> R.string.auto_backup_local_only
        current?.lastResult == AutomaticBackupStatus.RETRYING ->
            if (current.retryPending) R.string.auto_backup_retrying else R.string.auto_backup_failed
        current?.lastResult == AutomaticBackupStatus.FAILED -> R.string.auto_backup_failed
        else -> null
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    weeklyLabel,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                VeritasSwitch(
                    checked = weeklyEnabled,
                    onCheckedChange = { onToggleWeekly() },
                    modifier = Modifier.semantics { contentDescription = weeklyLabel },
                    enabled = !transferInProgress
                )
            }
            Text(
                menuString(R.string.auto_backup_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (latest != null) {
                Text(
                    menuString(R.string.auto_backup_latest, latest.date.format(dayFormat), formatVeritasBytes(latest.size)),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else if (current != null) {
                Text(menuString(R.string.auto_backup_empty), style = MaterialTheme.typography.bodyMedium)
            }
            if (workStatus != null) {
                Text(menuString(workStatus), style = MaterialTheme.typography.bodySmall)
            }
            if (weeklyEnabled && current?.weeklyRegistered == false) {
                Text(menuString(R.string.auto_backup_weekly_unavailable), style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = { onReviewBackup {
                    requesting = true
                    requestFailed = false
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { AutoBackupWorker.backUpNow(context).result.get() }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            requestFailed = true
                        } finally {
                            requesting = false
                        }
                    }
                } },
                enabled = !transferInProgress && !requesting && current?.manualPending != true && current?.running != true,
                shape = VeritasPackStyle.chipShape()
            ) {
                Text(menuString(R.string.auto_backup_now))
            }
            if (latest != null) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(menuString(if (expanded) R.string.auto_backup_hide_files else R.string.auto_backup_show_files))
                }
                if (expanded) {
                    files.forEach { entry ->
                        HorizontalDivider()
                        Column {
                            Text(entry.date.format(dayFormat), style = MaterialTheme.typography.titleSmall)
                            val location = when (entry.location) {
                                AutomaticBackupLocation.APP_STORAGE -> R.string.auto_backup_app_storage
                                AutomaticBackupLocation.EXTERNAL_COPY -> R.string.auto_backup_device_copy
                            }
                            Text(
                                menuString(R.string.auto_backup_file_details, menuString(location), formatVeritasBytes(entry.size)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row {
                                TextButton(onClick = { onRestore(entry) }, enabled = !transferInProgress) {
                                    Text(menuString(R.string.backup_review_action))
                                }
                                TextButton(
                                    onClick = {
                                        exportEntry = entry
                                        exportLauncher.launch(entry.name)
                                    },
                                    enabled = !transferInProgress && exportEntry == null
                                ) {
                                    Text(menuString(R.string.auto_backup_save_copy))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
