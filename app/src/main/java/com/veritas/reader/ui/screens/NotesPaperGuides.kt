package com.veritas.reader.ui.screens

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import com.veritas.reader.ui.NotesPaperTemplate

/** Guides follow the measured, transformed text, including wrapping and larger headings.
 * Apply at the text's origin, inside its padding, so scrolling carries both together. */
internal fun Modifier.notesTextPaper(
    template: NotesPaperTemplate,
    color: Color,
    layout: TextLayoutResult?
): Modifier = drawBehind {
    if (template == NotesPaperTemplate.BLANK || layout == null || layout.lineCount == 0) return@drawBehind
    val last = layout.lineCount - 1
    val step = (layout.getLineBottom(last) - layout.getLineTop(last)).coerceAtLeast(8.dp.toPx())
    val stroke = .6.dp.toPx()
    fun guide(y: Float) {
        if (template == NotesPaperTemplate.DOTS) {
            var x = 0f
            while (x < size.width) {
                drawCircle(color, 1.dp.toPx(), Offset(x, y))
                x += step
            }
        } else drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
    }
    for (line in 0..last) guide(layout.getLineBottom(line) - 1.dp.toPx())
    var y = layout.getLineBottom(last) - 1.dp.toPx() + step
    while (y < size.height) { guide(y); y += step }
    if (template == NotesPaperTemplate.GRID) {
        var x = 0f
        while (x < size.width) {
            drawLine(color, Offset(x, 0f), Offset(x, size.height), stroke)
            x += step
        }
    }
}

/** Dense samples remain legible in the four small paper tiles. */
internal fun Modifier.notesPaperSwatch(template: NotesPaperTemplate, color: Color): Modifier = drawBehind {
    val step = 8.dp.toPx()
    var y = step
    while (y < size.height) {
        if (template == NotesPaperTemplate.DOTS) {
            var x = step
            while (x < size.width) { drawCircle(color, .9.dp.toPx(), Offset(x, y)); x += step }
        } else if (template != NotesPaperTemplate.BLANK) {
            drawLine(color, Offset(0f, y), Offset(size.width, y), .6.dp.toPx())
        }
        y += step
    }
    if (template == NotesPaperTemplate.GRID) {
        var x = step
        while (x < size.width) { drawLine(color, Offset(x, 0f), Offset(x, size.height), .6.dp.toPx()); x += step }
    }
}
