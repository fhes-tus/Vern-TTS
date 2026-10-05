package com.veritas.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.ui.rememberVeritasHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

internal const val GO_UP_FILE_NAME = ".. (Go up)"

@Composable
internal fun getFileColorAndIcon(file: VeritasBrowserFile): Triple<ImageVector, Color, Color> {
    if (file.isDirectory) {
        if (file.name == GO_UP_FILE_NAME) {
            val tint = MaterialTheme.colorScheme.primary
            val bg = MaterialTheme.colorScheme.primaryContainer
            return Triple(Icons.AutoMirrored.Filled.ArrowBack, tint, bg)
        }
        val tint = Color(0xFFF2994A)
        val bg = tint.copy(alpha = 0.18f)
        return Triple(Icons.Outlined.Folder, tint, bg)
    }
    val tint = when (file.type) {
        VeritasBrowserTab.PDF -> Color(0xFFE24B4A)
        VeritasBrowserTab.DOC -> Color(0xFF7C6FFF)
        VeritasBrowserTab.BOOKS -> Color(0xFF0288D1)
        VeritasBrowserTab.SLIDES -> Color(0xFFE65100)
        VeritasBrowserTab.HTML -> Color(0xFF2F80ED)
        VeritasBrowserTab.TXT -> MaterialTheme.colorScheme.secondary
        VeritasBrowserTab.OCR -> Color(0xFF00897B)
        else -> MaterialTheme.colorScheme.secondary
    }
    val bg = tint.copy(alpha = 0.18f)
    val icon = when (file.type) {
        VeritasBrowserTab.PDF -> Icons.Outlined.PictureAsPdf
        VeritasBrowserTab.DOC -> Icons.Outlined.Description
        VeritasBrowserTab.BOOKS -> Icons.Outlined.Book
        VeritasBrowserTab.SLIDES -> Icons.Outlined.Slideshow
        VeritasBrowserTab.HTML -> Icons.Outlined.Language
        VeritasBrowserTab.TXT -> Icons.AutoMirrored.Outlined.Article
        VeritasBrowserTab.OCR -> Icons.Outlined.PhotoCamera
        else -> Icons.AutoMirrored.Outlined.Article
    }
    return Triple(icon, tint, bg)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileBrowserFileRow(
    file: VeritasBrowserFile,
    viewMode: LibraryViewMode,
    importing: Boolean,
    onOpenDirectory: () -> Unit,
    onImport: () -> Unit,
    isSelected: Boolean? = null,
    onSelectedChange: ((Boolean) -> Unit)? = null,
    selectionMode: Boolean = false
) {
    val enabled = if (file.isDirectory) file.targetLocation != null else file.isSupported && !importing
    val action = if (file.isDirectory) onOpenDirectory else onImport
    val haptic = rememberVeritasHaptics()

    val padding = when (viewMode) {
        LibraryViewMode.SMALL -> 6.dp
        LibraryViewMode.LIST -> 8.dp
        LibraryViewMode.MEDIUM -> 12.dp
        LibraryViewMode.DETAILS -> 14.dp
        else -> 10.dp
    }
    val coverSize = when (viewMode) {
        LibraryViewMode.SMALL -> 36.dp
        LibraryViewMode.LIST -> 46.dp
        LibraryViewMode.MEDIUM -> 58.dp
        LibraryViewMode.DETAILS -> 72.dp
        else -> 54.dp
    }
    val titleStyle = when (viewMode) {
        LibraryViewMode.SMALL -> MaterialTheme.typography.bodyMedium
        LibraryViewMode.LIST -> MaterialTheme.typography.bodyLarge
        LibraryViewMode.MEDIUM -> MaterialTheme.typography.titleSmall
        LibraryViewMode.DETAILS -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.bodyLarge
    }
    val showDetails = viewMode == LibraryViewMode.DETAILS || viewMode == LibraryViewMode.LIST

    val (icon, tint, bg) = getFileColorAndIcon(file)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = enabled,
                onClick = {
                    if (selectionMode && !file.isDirectory) {
                        haptic.toggle(isSelected != true)
                        if (isSelected == true) onSelectedChange?.invoke(false)
                        else onSelectedChange?.invoke(true)
                    } else {
                        action()
                    }
                },
                onLongClick = {
                    if (!file.isDirectory && onSelectedChange != null) {
                        haptic.longPress()
                        onSelectedChange(true)
                    }
                }
            ),
        shape = VeritasPackStyle.cardShape(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = VeritasPackStyle.surfaceAlpha())
        ),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
    ) {
        Row(
            modifier = Modifier.padding(padding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (selectionMode && isSelected != null && onSelectedChange != null && !file.isDirectory) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { checked ->
                        haptic.toggle(checked)
                        onSelectedChange(checked)
                    },
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(coverSize)
                    .background(bg, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (viewMode == LibraryViewMode.SMALL) 20.dp else 24.dp),
                    tint = tint
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = file.name,
                    maxLines = if (showDetails) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold,
                    style = titleStyle,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (showDetails) {
                    Text(
                        text = fileBrowserFolderLine(file),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = when {
                        file.name == GO_UP_FILE_NAME -> "Go up one folder level"
                        file.isDirectory && file.targetLocation != null -> "Folder • ${formatBrowserModified(file.modifiedAt)}"
                        file.isDirectory -> "Protected folder • Android may block this path"
                        file.isSupported -> "${formatBrowserFileSize(file.sizeBytes)} • ${formatBrowserModified(file.modifiedAt)}"
                        else -> "Unsupported file • ${formatBrowserFileSize(file.sizeBytes)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val buttonEnabled = enabled && (!selectionMode || file.isDirectory)
            Box(
                modifier = Modifier
                    .background(
                        if (buttonEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(50)
                    )
                    .clickable(enabled = buttonEnabled) {
                        if (file.isDirectory) onOpenDirectory() else onImport()
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (file.isDirectory) "Open" else if (file.isSupported) "Import" else "Unsupported",
                    color = if (buttonEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun FileBrowserFileTileCard(
    file: VeritasBrowserFile,
    importing: Boolean,
    onOpenDirectory: () -> Unit,
    onImport: () -> Unit,
    isSelected: Boolean? = null,
    onSelectedChange: ((Boolean) -> Unit)? = null,
    selectionMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val enabled = if (file.isDirectory) file.targetLocation != null else file.isSupported && !importing
    val action = if (file.isDirectory) onOpenDirectory else onImport
    val haptic = rememberVeritasHaptics()

    val (icon, tint, bg) = getFileColorAndIcon(file)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = enabled,
                onClick = {
                    if (selectionMode && !file.isDirectory) {
                        haptic.toggle(isSelected != true)
                        if (isSelected == true) onSelectedChange?.invoke(false)
                        else onSelectedChange?.invoke(true)
                    } else {
                        action()
                    }
                },
                onLongClick = {
                    if (!file.isDirectory && onSelectedChange != null) {
                        haptic.longPress()
                        onSelectedChange(true)
                    }
                }
            ),
        shape = com.veritas.reader.VeritasPackStyle.compactShape(),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.2f)
                        .background(bg, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = tint
                    )
                }
                if (selectionMode && isSelected != null && onSelectedChange != null && !file.isDirectory) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { checked ->
                            haptic.toggle(checked)
                            onSelectedChange(checked)
                        },
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                    )
                }
            }
            Text(
                text = file.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = when {
                    file.name == GO_UP_FILE_NAME -> "Parent directory"
                    file.isDirectory && file.targetLocation != null -> "Folder • ${formatBrowserModified(file.modifiedAt)}"
                    file.isDirectory -> "Protected folder"
                    file.isSupported -> "${formatBrowserFileSize(file.sizeBytes)} • ${formatBrowserModified(file.modifiedAt)}"
                    else -> "Unsupported file"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                val buttonEnabled = enabled && (!selectionMode || file.isDirectory)
                Box(
                    modifier = Modifier
                        .background(
                            if (buttonEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(50)
                        )
                        .clickable(enabled = buttonEnabled) {
                            if (file.isDirectory) onOpenDirectory() else onImport()
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (file.name == GO_UP_FILE_NAME) "Go Up" else if (file.isDirectory) "Open" else if (file.isSupported) "Import" else "Unsupported",
                        color = if (buttonEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
internal fun FileBrowserSortDialog(
    sortMode: VeritasBrowserSort,
    sortAscending: Boolean,
    onSortModeChange: (VeritasBrowserSort) -> Unit,
    onSortAscendingChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        title = { Text("Sort files by") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VeritasBrowserSort.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSortModeChange(option) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = sortMode == option,
                            onClick = { onSortModeChange(option) })
                        Text(option.label, modifier = Modifier.weight(1f))
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text("Order:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSortAscendingChange(true) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = sortAscending, onClick = { onSortAscendingChange(true) })
                    Text("Ascending", modifier = Modifier.weight(1f))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSortAscendingChange(false) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = !sortAscending,
                        onClick = { onSortAscendingChange(false) })
                    Text("Descending", modifier = Modifier.weight(1f))
                }
            }
        }
    )
}

internal fun fileBrowserFolderLine(file: VeritasBrowserFile): String {
    if (file.name == GO_UP_FILE_NAME) return "Parent directory"
    val folderPath = file.relativePath.substringBeforeLast('/', missingDelimiterValue = "")
    return if (folderPath.isBlank()) file.rootLabel else "${file.rootLabel}/$folderPath"
}

internal fun formatBrowserFileSize(bytes: Long): String {
    if (bytes <= 0L) return "Unknown size"
    val units = listOf("B", "kB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${bytes} ${units[unitIndex]}"
    } else {
        String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
    }
}

internal fun formatBrowserModified(timestamp: Long): String =
    if (timestamp > 0L) formatUpdated(timestamp) else "Unknown date"

/**
 * Small thumbnail for an image entry, decoded off the main thread and downsampled hard —
 * these are only ever drawn at 56dp, and a delete confirmation may show several at once.
 * Returns null for anything that is not a decodable image, so callers fall back to the name.
 */
@Composable
internal fun rememberFileThumbnail(file: VeritasBrowserFile): Bitmap? {
    val context = LocalContext.current
    var thumb by remember(file.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file.uri) {
        if (file.type != VeritasBrowserTab.OCR) return@LaunchedEffect
        thumb = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(file.uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1
                while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(file.uri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
            }.getOrNull()
        }
    }
    return thumb
}

/** One doomed file: its picture when we have one, otherwise its name and folder. */
@Composable
internal fun DeletePreviewRow(file: VeritasBrowserFile) {
    val thumb = rememberFileThumbnail(file)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            val bmp = thumb
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                val (icon, tint, _) = getFileColorAndIcon(file)
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                file.relativePath.ifBlank { file.rootLabel },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun FileBrowserDeleteConfirmationDialog(
    doomedFiles: List<VeritasBrowserFile>,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    if (doomedFiles.isEmpty()) return
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        title = {
            Text(
                if (doomedFiles.size == 1) "Delete this file?" else "Delete ${doomedFiles.size} files?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This removes the file from your phone's storage, not just from Vern. It cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Column(
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    doomedFiles.take(5).forEach { file -> DeletePreviewRow(file) }
                }
                if (doomedFiles.size > 5) {
                    Text(
                        "and ${doomedFiles.size - 5} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
internal fun FileBrowserEmptyState(
    onPickFolder: () -> Unit,
    onRequestAllFilesAccess: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "No file access yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Grant All Files access for broad phone storage browsing, or choose specific folders to scan.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onRequestAllFilesAccess,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Grant all files access") }
                Button(
                    onClick = onPickFolder,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Folders to scan") }
            }
        }
    }
}

