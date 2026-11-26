package com.veritas.reader

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.veritas.reader.ui.screens.formatVeritasBytes
import com.veritas.reader.ui.screens.menuString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun BackupRestoreDialog(
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
    fullBackupEstimateBytes: Long = 0L,
    autoBackupEnabled: Boolean = true,
    onToggleAutoBackup: () -> Unit = {},
    onExport: () -> Unit,
    onShare: () -> Unit,
    onExportFull: () -> Unit = {},
    onImport: () -> Unit,
    onDismiss: () -> Unit
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
        onExportSyncPack = onExport,
        onShareSyncPack = onShare,
        onImportSyncPack = onImport,
        onDismiss = onDismiss,
        fullBackupEstimateBytes = fullBackupEstimateBytes,
        autoBackupEnabled = autoBackupEnabled,
        onToggleAutoBackup = onToggleAutoBackup,
        onExportFull = onExportFull
    )
}

@Composable
internal fun BackupReviewDialog(
    review: BackupRestoreInspection,
    checking: Boolean,
    message: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val locale = LocalConfiguration.current.locales[0]
    VernAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(menuString(R.string.backup_review_title)) },
        text = {
            Column(
                Modifier.heightIn(max = 395.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium)
                Text(review.sourceName, style = MaterialTheme.typography.titleSmall)
                val format = menuString(if (review.isArchive) R.string.backup_review_zip else R.string.backup_review_json)
                Text(menuString(R.string.backup_review_format, format, formatVeritasBytes(review.byteCount)))
                review.createdAt?.let { time ->
                    val date = java.text.DateFormat.getDateTimeInstance(
                        java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT, locale
                    ).format(Date(time))
                    Text(date, style = MaterialTheme.typography.bodySmall)
                }
                SyncInfoRow(menuString(R.string.backup_review_added), review.newReadings.toString())
                SyncInfoRow(menuString(R.string.backup_review_matched), review.matchingReadings.toString())
                SyncInfoRow(menuString(R.string.backup_review_conflicting), review.conflictCount.toString())
                if (review.conflictCount > 0) {
                    Text(menuString(R.string.backup_review_conflict_explanation))
                    review.conflictTitles.forEach { name ->
                        Text(name.ifBlank { menuString(R.string.backup_review_untitled) }, style = MaterialTheme.typography.bodySmall)
                    }
                    if (review.conflictTitles.size < review.conflictCount) {
                        Text(menuString(R.string.backup_review_other_conflicts, review.conflictCount - review.conflictTitles.size))
                    }
                }
                if (review.includesSettings) Text(menuString(R.string.backup_review_settings))
                if (review.isArchive) Text(menuString(R.string.backup_review_archive_assets), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !checking) { Text(menuString(R.string.auto_backup_restore)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(menuString(R.string.ui_cancel)) }
        }
    )
}

@Composable
internal fun BackupStatusBlock(
    inProgress: Boolean,
    message: String?
) {
    if (!inProgress && message.isNullOrBlank()) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (inProgress) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_working_on_backup_file), style = MaterialTheme.typography.bodySmall)
            }
            if (!message.isNullOrBlank()) {
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

internal fun veritasBackupFileName(prefix: String): String {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return "${prefix}_$timestamp.json"
}

internal fun veritasBackupZipFileName(prefix: String): String {
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return "${prefix}_$timestamp.zip"
}

internal fun veritasBackupMimeTypes(): Array<String> = arrayOf(
    "application/json",
    "text/json",
    "text/plain",
    "application/zip",
    "application/octet-stream"
)
