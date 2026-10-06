package com.veritas.reader

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DocxFormattedParagraph(block: DocxBlock.Paragraph, color: Color, background: Color, modifier: Modifier = Modifier, textStyle: TextStyle = MaterialTheme.typography.bodyMedium) {
    val format = block.sourceFormat
    val text = if (format == null || format.runs.isEmpty()) AnnotatedString(block.text) else buildAnnotatedString {
        format.runs.forEach { run -> withStyle(SpanStyle(
            fontWeight = if (run.bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (run.italic) FontStyle.Italic else FontStyle.Normal,
            textDecoration = if (run.underline) TextDecoration.Underline else TextDecoration.None,
            fontSize = run.sizePoints?.sp ?: androidx.compose.ui.unit.TextUnit.Unspecified,
            color = run.color?.let { Color(readableSlideText(it, background.toArgb().toLong() and 0xffffffffL)) } ?: color
        )) { append(run.text) } }
    }
    Text(text, modifier.padding(start = (format?.indentPoints ?: 0f).dp,
        top = (format?.beforePoints ?: 0f).dp, bottom = (format?.afterPoints ?: 0f).dp),
        style = textStyle, color = color,
        textAlign = when (format?.alignment) { "center" -> TextAlign.Center; "right", "end" -> TextAlign.End; "both" -> TextAlign.Justify; else -> TextAlign.Start })
}
