package com.veritas.reader

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri

data class VoicePreset(
    val name: String,
    val rate: Float,
    val pitch: Float,
    val summary: String
)

fun voicePresets(): List<VoicePreset> = listOf(
    VoicePreset("Balanced", 1.0f, 1.0f, "Natural everyday audio reading with standard timing."),
    VoicePreset("Study focus", 0.92f, 0.99f, "Deliberate pacing with grounded tone for deep comprehension."),
    VoicePreset("Quick scan", 1.25f, 1.01f, "Fast energetic review with crisp consonant clarity."),
    VoicePreset("Storyteller", 0.95f, 0.97f, "Warm, chest-resonant narration for fiction and novels."),
    VoicePreset("Lecture bright", 1.10f, 1.03f, "Confident, articulate delivery for academic and business text."),
    VoicePreset("Bedtime calm", 0.85f, 0.98f, "Slow, soothing, mellow tone for quiet night-time listening.")
)

internal fun openOriginalDocument(
    context: Context,
    repository: DocumentRepository,
    document: SavedDocument
) {
    val original = repository.originalFile(document)
    if (original == null) {
        Toast.makeText(
            context,
            "No stored original is available for this reading.",
            Toast.LENGTH_SHORT
        ).show()
        return
    }
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", original)
    }.getOrNull()
    if (uri == null) {
        Toast.makeText(context, "Could not prepare the original file.", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, document.originalMimeType.ifBlank { "application/octet-stream" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Open original document"))
    }.onFailure {
        Toast.makeText(context, "No app can open this original document.", Toast.LENGTH_SHORT)
            .show()
    }
}

internal fun openAllFilesAccessSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        Toast.makeText(
            context,
            "All Files access is already available on this Android version.",
            Toast.LENGTH_SHORT
        ).show()
        return
    }
    val appIntent = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        "package:${context.packageName}".toUri()
    )
    val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    runCatching {
        context.startActivity(appIntent)
    }.onFailure {
        runCatching { context.startActivity(fallback) }
            .onFailure {
                Toast.makeText(
                    context,
                    "Could not open All Files access settings.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }
}

@Composable
internal fun FloatingRecordOverlay(
    inProgress: Boolean,
    fileReady: Boolean,
    awaitingDecision: Boolean,
    elapsedSeconds: Long,
    onStopRecording: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val elapsed = formatRecordElapsed(elapsedSeconds)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(22.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Surface(
            modifier = Modifier
                .graphicsLayer {
                    translationX = offsetX
                    translationY = offsetY
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                },
            shape = if (awaitingDecision) MaterialTheme.shapes.large else MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            if (awaitingDecision) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RecordPillDot(inProgress = inProgress)
                    if (fileReady) {
                        TextButton(onClick = onSave) { Text("Save") }
                        TextButton(onClick = onDiscard) { Text("Discard") }
                    } else {
                        Text(
                            if (inProgress) "Finishing…" else "No audio yet",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = onDiscard) { Text("Discard") }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                        .clickable { onStopRecording() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RecordPillDot(
                        inProgress = inProgress,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        elapsed,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
internal fun RecordPillDot(
    inProgress: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .background(
                if (inProgress) Color(0xFFE53935) else MaterialTheme.colorScheme.primary,
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (inProgress) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(Color.White, CircleShape)
            )
        } else {
            Text(
                "✓",
                color = Color.White,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

internal fun formatRecordElapsed(seconds: Long): String {
    val safeSeconds = seconds.coerceAtLeast(0L)
    val minutes = safeSeconds / 60L
    val remainingSeconds = safeSeconds % 60L
    return "%02d:%02d".format(minutes, remainingSeconds)
}

@Composable
internal fun AppHealthDialog(
    documentCount: Int,
    queueCount: Int,
    themePackName: String,
    themeName: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss, shape = RoundedCornerShape(50)) { Text("Close") } },
        title = { Text("App health") },
        text = {
            Column(
                modifier = Modifier
                    .height(560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Vern",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                        val context = LocalContext.current
                        val appVersion = remember(context) {
                            runCatching {
                                context.packageManager.getPackageInfo(context.packageName, 0).versionName
                            }.getOrNull() ?: "2.0.0"
                        }
                        Text(
                            "Version: $appVersion",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Library: $documentCount reading${if (documentCount == 1) "" else "s"} • $queueCount queued",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Theme: $themePackName / $themeName",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ReadinessBlock(
                    title = "Permissions review",
                    items = listOf(
                        "Notifications are only used for media playback controls.",
                        "Foreground service is used for reading aloud while the app is outside the screen.",
                        "Document access uses Android file pickers and shared files; no broad storage permission is required.",
                        "Internet is not required for core reading. AI handoff uses installed apps through Android sharing."
                    )
                )

                ReadinessBlock(
                    title = "Before sharing an APK",
                    items = listOf(
                        "Run the final QA checklist on a physical phone.",
                        "Test import, playback, background controls, OCR, export, sync pack merge, notes export, and AI handoff.",
                        "Make a backup copy of this project folder once it passes.",
                        "Use a debug APK for private testing only. Use a signed release APK/AAB for wider distribution."
                    )
                )

                ReadinessBlock(
                    title = "Current limits",
                    items = listOf(
                        "Google Drive login is planned for a later OAuth pass.",
                        "Long scanned PDFs and WAV exports may be slow on low-memory phones.",
                        "Canvas/source view requires newly imported originals to be preserved.",
                        "AI features depend on installed AI apps or the small offline helper."
                    )
                )
            }
        }
    )
}

@Composable
internal fun ReadinessBlock(title: String, items: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
            items.forEach { item ->
                Text(
                    "• $item",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
