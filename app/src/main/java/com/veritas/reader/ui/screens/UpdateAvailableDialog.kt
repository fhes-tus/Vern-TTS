package com.veritas.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun UpdateAvailableDialog(
    versionName: String,
    changelog: String,
    isDownloading: Boolean,
    downloadProgress: Float,
    downloadError: String?,
    isPlayStoreInstall: Boolean = false,
    onUpdate: () -> Unit,
    onOpenPlayStore: () -> Unit = {},
    onCancelDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val summaryBullets = cleanChangelogSummary(changelog)

    AlertDialog(
        onDismissRequest = {
            if (isDownloading) {
                onCancelDownload()
            }
            onDismiss()
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            Text(
                text = "New Update Available: $versionName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isDownloading) {
                    Text(
                        text = "Downloading update: ${(downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                } else {
                    Text(
                        text = if (isPlayStoreInstall) {
                            "A new version of Veritas Reader is available on Google Play and GitHub."
                        } else {
                            "A new version of Veritas Reader is ready with latest enhancements."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (downloadError != null) {
                        Text(
                            text = "Download failed: $downloadError",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (summaryBullets.isNotEmpty()) {
                        Text(
                            text = "What's New:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            summaryBullets.forEach { bullet ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 6.dp)
                                            .size(5.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = CircleShape
                                            )
                                    )
                                    Text(
                                        text = bullet,
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
            if (!isDownloading) {
                if (isPlayStoreInstall) {
                    Button(
                        onClick = {
                            onOpenPlayStore()
                            onDismiss()
                        }
                    ) {
                        Text("Update on Google Play")
                    }
                } else {
                    Button(onClick = onUpdate) {
                        Text(if (downloadError != null) "Retry Update" else "Update Now")
                    }
                }
            }
        },
        dismissButton = {
            if (isDownloading) {
                TextButton(onClick = onCancelDownload) {
                    Text("Cancel")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPlayStoreInstall) {
                        TextButton(onClick = onUpdate) {
                            Text("Direct APK", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        TextButton(
                            onClick = {
                                onOpenPlayStore()
                                onDismiss()
                            }
                        ) {
                            Text("Google Play", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Later", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    )
}

private fun cleanChangelogSummary(rawChangelog: String): List<String> {
    if (rawChangelog.isBlank()) return emptyList()
    val lines = rawChangelog.lines()
    val bullets = mutableListOf<String>()
    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isBlank()) continue
        if (trimmed.startsWith("#") || trimmed.startsWith("---") || trimmed.startsWith("===")) continue
        // Strip markdown formatting symbols
        val cleaned = trimmed
            .removePrefix("- ")
            .removePrefix("* ")
            .removePrefix("+ ")
            .replace(Regex("""\[(.*?)\]\(.*?\)"""), "$1") // markdown links -> label
            .replace(Regex("""\*\*(.*?)\*\*"""), "$1")     // bold
            .replace(Regex("""\*(.*?)\*"""), "$1")         // italics
            .replace(Regex("""`(.*?)`"""), "$1")           // inline code
            .trim()
        if (cleaned.isNotBlank() &&
            !cleaned.equals("Full Changelog", ignoreCase = true) &&
            !cleaned.startsWith("See the assets", ignoreCase = true) &&
            !cleaned.startsWith("What's Changed", ignoreCase = true)
        ) {
            bullets.add(cleaned)
        }
    }
    return if (bullets.isNotEmpty()) bullets.take(6) else listOf("Performance improvements and bug fixes.")
}
