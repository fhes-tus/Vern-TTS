package com.veritas.reader

import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun PositionedPresentationCanvas(
    slide: PptxSlideContent, count: Int, images: Map<String, Bitmap>, showNotes: Boolean,
    onToggleNotes: () -> Unit, onNext: () -> Unit, onPrevious: () -> Unit,
    onSelectText: ((String) -> Unit)?, modifier: Modifier
) {
    val layout = requireNotNull(slide.layout)
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    var headerHeight by remember { mutableStateOf(48.dp) }
    val notesHeight = if (showNotes && slide.notesLines.isNotEmpty()) minOf(180.dp, maxHeight / 3) else 0.dp
    val slideWidth = minOf((maxWidth - 12.dp).coerceAtLeast(1.dp),
        (maxHeight - headerHeight - notesHeight - 24.dp).coerceAtLeast(48.dp) * layout.aspectRatio)
    Surface(modifier = Modifier.width(maxOf(slideWidth + 12.dp, minOf(maxWidth, 300.dp))).testTag("positioned_slide_card"), shape = VeritasPackStyle.cardShape(), tonalElevation = 2.dp) {
        Column(modifier = Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState()).padding(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth().onSizeChanged { headerHeight = with(density) { it.height.toDp() } }, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Slide ${slide.number} of $count", style = MaterialTheme.typography.labelLarge)
                    Text("Source layout", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (slide.notesLines.isNotEmpty()) TextButton(onClick = onToggleNotes) { Text(if (showNotes) "Hide notes" else "Notes") }
                IconButton(enabled = slide.number > 1, onClick = onPrevious) { Icon(Icons.AutoMirrored.Filled.NavigateBefore, "Previous slide") }
                IconButton(enabled = slide.number < count, onClick = onNext) { Icon(Icons.AutoMirrored.Filled.NavigateNext, "Next slide") }
            }
            BoxWithConstraints(modifier = Modifier.width(slideWidth).height(slideWidth / layout.aspectRatio), contentAlignment = Alignment.Center) {
                val width = maxWidth
                val height = width / layout.aspectRatio
                Box(modifier = Modifier.width(width).height(height).clipToBounds().background(Color(layout.background))) {
                    layout.elements.forEach { element ->
                        val box = Modifier.offset(width * element.x, height * element.y)
                            .width(width * element.width).height(height * element.height)
                            .graphicsLayer { rotationZ = element.rotationDegrees }
                        Box(modifier = box.clipToBounds()) {
                            Canvas(Modifier.fillMaxSize()) {
                                if (element.paths.isEmpty()) {
                                    element.fill?.let { drawRect(Color(it)) }
                                    element.stroke?.let { drawRect(Color(it), style = Stroke(with(density) { width.toPx() } * element.strokeWidthFraction)) }
                                } else element.paths.forEach { vector ->
                                    val path = Path()
                                    vector.commands.forEach { cmd ->
                                        val p = cmd.points
                                        fun x(i: Int) = p[i] * size.width
                                        fun y(i: Int) = p[i] * size.height
                                        when (cmd.kind) {
                                            "moveTo" -> if (p.size == 2) path.moveTo(x(0), y(1))
                                            "lnTo" -> if (p.size == 2) path.lineTo(x(0), y(1))
                                            "quadBezTo" -> if (p.size == 4) path.quadraticBezierTo(x(0), y(1), x(2), y(3))
                                            "cubicBezTo" -> if (p.size == 6) path.cubicTo(x(0), y(1), x(2), y(3), x(4), y(5))
                                            "close" -> path.close()
                                        }
                                    }
                                    if (vector.filled) element.fill?.let { drawPath(path, Color(it)) }
                                    if (vector.stroked) element.stroke?.let { drawPath(path, Color(it), style = Stroke(with(density) { width.toPx() } * element.strokeWidthFraction)) }
                                }
                            }
                            element.mediaPath?.let { path ->
                                images[path]?.let { bitmap -> Image(bitmap.asImageBitmap(), "Slide illustration", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                            }
                            if (element.text.isNotBlank()) Box(
                                Modifier.fillMaxSize().padding(start = width * element.insetLeft, top = height * element.insetTop,
                                    end = width * element.insetRight, bottom = height * element.insetBottom),
                                contentAlignment = when (element.verticalAnchor) { "ctr" -> Alignment.Center; "b" -> Alignment.BottomCenter; else -> Alignment.TopCenter }
                            ) { Text(
                                element.text,
                                modifier = Modifier.fillMaxWidth().then(if (onSelectText != null) Modifier.clickable { onSelectText(element.text) } else Modifier),
                                color = Color(element.color),
                                fontSize = with(density) { (width.toPx() * element.fontWidthFraction).toSp() },
                                lineHeight = with(density) { (width.toPx() * element.fontWidthFraction * 1.15f).toSp() },
                                fontWeight = if (element.bold) FontWeight.Bold else FontWeight.Normal,
                                textAlign = when (element.alignment) { "ctr" -> TextAlign.Center; "r" -> TextAlign.End; else -> TextAlign.Start }
                            ) }
                        }
                    }
                }
            }
            if (showNotes && slide.notesLines.isNotEmpty()) Column(modifier = Modifier.fillMaxWidth().heightIn(max = notesHeight).verticalScroll(rememberScrollState())) {
                Text("Speaker notes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(slide.notesLines.joinToString("\n\n"), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    }
}
