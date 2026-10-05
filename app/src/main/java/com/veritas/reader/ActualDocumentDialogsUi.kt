package com.veritas.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Dialog to jump directly to a page or slide number.
 */
@Composable
internal fun JumpToPageDialog(
    isOpen: Boolean,
    pageCount: Int,
    currentPageIndex: Int,
    isPresentation: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    if (!isOpen) return
    var jumpPageInput by remember(currentPageIndex) { mutableStateOf("${currentPageIndex + 1}") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Jump to Page", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter ${if (isPresentation) "slide" else "page"} number between 1 and $pageCount:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = jumpPageInput,
                    onValueChange = { jumpPageInput = it.filter { ch -> ch.isDigit() } },
                    singleLine = true,
                    placeholder = { Text("1–$pageCount") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = jumpPageInput.toIntOrNull()
                    if (parsed != null && parsed in 1..pageCount) {
                        onConfirm(parsed - 1)
                    }
                    onDismiss()
                }
            ) {
                Text("Go")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog showing metadata and information about the active document.
 */
@Composable
internal fun DocumentInfoDialog(
    isOpen: Boolean,
    document: SavedDocument,
    isPdf: Boolean,
    isPresentation: Boolean,
    isImage: Boolean,
    pageCount: Int,
    pageIndex: Int,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = { Text("Document Info", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Title: ${document.title}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text("Format: ${if (isPdf) "PDF Document" else if (isPresentation) "PowerPoint Presentation" else if (isImage) "Image" else document.sourceLabel}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (isPdf || isPresentation) {
                    Text("Total ${if (isPresentation) "Slides" else "Pages"}: $pageCount", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Current ${if (isPresentation) "Slide" else "Page"}: ${pageIndex + 1} (${((pageIndex + 1) * 100 / pageCount.coerceAtLeast(1))}%)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Chunks / Sentences: ${document.chunkCount}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (document.originalFileName.isNotBlank()) {
                    Text("File: ${document.originalFileName.substringAfterLast('/')}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

/**
 * Action card that appears when text is selected from the document canvas.
 */
@Composable
internal fun SelectedTextActionCard(
    selectedText: String,
    bottomBarVisible: Boolean,
    isLandscape: Boolean,
    pageIndex: Int,
    onDismiss: () -> Unit,
    onReadFromSentence: ((String, Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        modifier = modifier
            .padding(bottom = if (bottomBarVisible && !isLandscape) 96.dp else 16.dp, start = 16.dp, end = 16.dp)
            .fillMaxWidth()
            .navigationBarsPadding(),
        shape = com.veritas.reader.VeritasPackStyle.compactShape(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.FormatQuote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Selected Text Actions",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = "“${selectedText.take(100)}${if (selectedText.length > 100) "…" else ""}”",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        onDismiss()
                        onReadFromSentence?.invoke(selectedText, pageIndex + 1)
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Continue reading from here", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val safeText = if (selectedText.length > 200_000) selectedText.take(200_000) else selectedText
                        clipboard.setPrimaryClip(ClipData.newPlainText("Vern Text", safeText))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                }
                OutlinedButton(
                    onClick = {
                        val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                            putExtra(android.app.SearchManager.QUERY, selectedText)
                        }
                        runCatching { context.startActivity(searchIntent) }
                        onDismiss()
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Search, contentDescription = "Search", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/**
 * Fallback card shown when an original document format cannot be rendered natively.
 */
@Composable
internal fun ActualDocumentUnavailableNotice(
    message: String?,
    hasOriginal: Boolean,
    onOpenExternal: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        shape = com.veritas.reader.VeritasPackStyle.compactShape(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "⚠️ original document view is unavailable",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message ?: "Unrecognized document format.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpenExternal, enabled = hasOriginal) { Text("Open original") }
                TextButton(onClick = onClose) { Text("Extracted text") }
            }
        }
    }
}

/**
 * Slim scrubber bar used for rapid page navigation in the top bar.
 */
@Composable
internal fun SlimPageSlider(
    pageIndex: Int,
    pageCount: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pageCount <= 1) return
    val density = LocalDensity.current
    val tickingPageSelect = com.veritas.reader.ui.rememberStepHaptics(pageIndex, onPageSelected)
    BoxWithConstraints(
        modifier = modifier
            .pointerInput(pageCount) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    tickingPageSelect(((offset.x / width).coerceIn(0f, 1f) * (pageCount - 1)).roundToInt())
                }
            }
            .pointerInput(pageCount) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        tickingPageSelect(((offset.x / width).coerceIn(0f, 1f) * (pageCount - 1)).roundToInt())
                    },
                    onDrag = { change, _ ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        tickingPageSelect(((change.position.x / width).coerceIn(0f, 1f) * (pageCount - 1)).roundToInt())
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val progress = (pageIndex.toFloat() / (pageCount - 1).toFloat()).coerceIn(0f, 1f)
        val thumbSize = 18.dp
        val thumbPx = with(density) { thumbSize.toPx() }
        val trackWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(thumbPx)
        val usableWidthPx = (trackWidthPx - thumbPx).coerceAtLeast(1f)
        val thumbOffsetXPx = (usableWidthPx * progress).roundToInt()

        // Full inactive track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f), CircleShape)
        )

        // Active track reaching center of thumb
        val activeWidthPx = (thumbPx / 2f + usableWidthPx * progress).coerceIn(0f, trackWidthPx)
        val activeWidthDp = with(density) { activeWidthPx.toDp() }
        if (activeWidthPx > 0f) {
            Box(
                modifier = Modifier
                    .width(activeWidthDp)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
        }

        // Circular thumb with 2.dp surface border & subtle drop shadow
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffsetXPx, 0) }
                .size(thumbSize)
                .shadow(2.dp, CircleShape)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
        )
    }
}
