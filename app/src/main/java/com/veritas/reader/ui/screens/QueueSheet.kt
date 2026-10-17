package com.veritas.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.SavedDocument
import kotlin.math.roundToInt

/** Height of one queue row; drag distance is converted to positions against it. */
private val ROW_HEIGHT = 64.dp

/**
 * The play queue, in order, with drag-to-reorder.
 *
 * The queue decides what plays next, so order is its whole point — and it had no
 * screen at all. Filtering the library by "queued" could show *which* documents were
 * in it but never the sequence, because that grid sorts by its own rule.
 *
 * Reordering goes through `moveQueueItem(document, offset)`, which shifts one entry
 * by a relative number of places, so a drag is applied as a single move on drop
 * rather than a write per crossed row.
 */
@Composable
fun VeritasQueueSheet(
    queue: List<SavedDocument>,
    onMove: (SavedDocument, Int) -> Unit,
    onRemove: (SavedDocument) -> Unit,
    onClear: () -> Unit,
    onPlay: (SavedDocument) -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val rowPx = with(density) { ROW_HEIGHT.toPx() }

    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Play queue", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (queue.isEmpty()) "Nothing queued"
                    else "${queue.size} reading${if (queue.size == 1) "" else "s"} · hold to reorder",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            if (queue.isEmpty()) {
                Text(
                    "Add readings from the library's action menu to build a play queue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(queue, key = { _, doc -> doc.id }) { index, document ->
                        val isDragging = index == draggingIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ROW_HEIGHT)
                                .graphicsLayer {
                                    if (isDragging) {
                                        translationY = dragOffsetPx
                                        shadowElevation = 12f
                                    }
                                }
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isDragging) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(22.dp)
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 6.dp)
                            ) {
                                Text(
                                    document.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    document.sourceLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onRemove(document) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove from queue",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .pointerInput(document.id, queue.size) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggingIndex = index
                                                dragOffsetPx = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetPx += dragAmount.y
                                            },
                                            onDragEnd = {
                                                // One relative move for the whole gesture, clamped
                                                // so a long drag cannot run past either end.
                                                val places = (dragOffsetPx / rowPx).roundToInt()
                                                val target = (index + places).coerceIn(0, queue.lastIndex)
                                                val delta = target - index
                                                if (delta != 0) onMove(document, delta)
                                                draggingIndex = -1
                                                dragOffsetPx = 0f
                                            },
                                            onDragCancel = {
                                                draggingIndex = -1
                                                dragOffsetPx = 0f
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.DragHandle,
                                    contentDescription = "Reorder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            if (queue.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text("Clear all", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    )
}
