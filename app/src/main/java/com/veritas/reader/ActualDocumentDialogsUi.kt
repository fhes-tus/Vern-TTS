package com.veritas.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
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
import com.veritas.reader.VernAlertDialog as AlertDialog
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
        title = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_jump_to_page), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_189, com.veritas.reader.ui.screens.localizedUiValue(if (isPresentation) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_fragment_slide_lower) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_fragment_page_lower)), com.veritas.reader.ui.screens.localizedUiValue(pageCount)),
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
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_go))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_cancel))
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
        title = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_document_info), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_001, com.veritas.reader.ui.screens.localizedUiValue(document.title)), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_190, com.veritas.reader.ui.screens.localizedUiValue(if (isPdf) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_fragment_pdf) else if (isPresentation) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_fragment_ppt) else if (isImage) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_fragment_image) else document.sourceLabel)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (isPdf || isPresentation) {
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_191, com.veritas.reader.ui.screens.localizedUiValue(if (isPresentation) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_fragment_slides) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_fragment_pages)), com.veritas.reader.ui.screens.localizedUiValue(pageCount)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_192, com.veritas.reader.ui.screens.localizedUiValue(if (isPresentation) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_fragment_slide) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_fragment_page)), com.veritas.reader.ui.screens.localizedUiValue(pageIndex + 1), com.veritas.reader.ui.screens.localizedUiValue(((pageIndex + 1) * 100 / pageCount.coerceAtLeast(1)))), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_002, com.veritas.reader.ui.screens.localizedUiValue(document.chunkCount)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (document.originalFileName.isNotBlank()) {
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_003, com.veritas.reader.ui.screens.localizedUiValue(document.originalFileName.substringAfterLast('/'))), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_done))
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
    onAction: (ReaderSelectionAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var showMore by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Surface(
        modifier = modifier.padding(bottom = if (bottomBarVisible && !isLandscape) 96.dp else 16.dp, start = 16.dp, end = 16.dp)
            .fillMaxWidth().navigationBarsPadding(),
        shape = VeritasPackStyle.compactShape(), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp, shadowElevation = 6.dp
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(com.veritas.reader.ui.screens.menuString(R.string.ui_selected_text_actions), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, com.veritas.reader.ui.screens.menuString(R.string.ui_dismiss)) }
            }
            Text(selectedText.take(140), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                ReaderSelectionAction.entries.filter { it.primary }.forEach { action ->
                    TextButton(onClick = { onAction(action); onDismiss() }) { Text(action.title(context)) }
                }
                Box {
                    TextButton(onClick = { showMore = true }) { Text(com.veritas.reader.ui.screens.menuString(R.string.ui_more)) }
                    androidx.compose.material3.DropdownMenu(showMore, { showMore = false }) {
                        ReaderSelectionAction.entries.filterNot { it.primary }.forEach { action ->
                            androidx.compose.material3.DropdownMenuItem(text = { Text(action.title(context)) }, onClick = {
                                showMore = false
                                if (action == ReaderSelectionAction.READ_FROM_HERE && onReadFromSentence != null)
                                    onReadFromSentence(selectedText, pageIndex + 1)
                                else onAction(action)
                                onDismiss()
                            })
                        }
                    }
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
                text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_original_document_view_is_unavailable),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message ?: com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_unrecognized_document_format),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpenExternal, enabled = hasOriginal) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_open_original)) }
                TextButton(onClick = onClose) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_extracted_text)) }
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
            .semantics {
                contentDescription = "Page position"
                progressBarRangeInfo = ProgressBarRangeInfo((pageIndex + 1).toFloat(), 1f..pageCount.toFloat(), (pageCount - 2).coerceAtLeast(0))
                setProgress { requested ->
                    tickingPageSelect((requested.roundToInt() - 1).coerceIn(0, pageCount - 1)); true
                }
            }
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
