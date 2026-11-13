package com.veritas.reader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.veritas.reader.GeneralNote
import kotlin.math.floor

internal fun vernCollectionColumns(availableWidthDp: Float, minimumCardDp: Float, fontScale: Float,
    gapDp: Float = 8f, maximum: Int = 4): Int {
    val minimum = maxOf(minimumCardDp, minimumCardDp * fontScale / 1.25f)
    return floor((availableWidthDp.coerceAtLeast(0f) + gapDp) / (minimum + gapDp)).toInt().coerceIn(1, maximum)
}

/** Independent column heights preserve Notes' masonry layout and its existing sort order. */
@Composable
internal fun AdaptiveNoteColumns(notes: List<GeneralNote>, columns: Int, modifier: Modifier = Modifier,
    noteContent: @Composable (GeneralNote) -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(columns) { column ->
            Column(Modifier.weight(1f)) {
                notes.forEachIndexed { index, note -> if (index % columns == column) key(note.id) { noteContent(note) } }
            }
        }
    }
}
