package com.veritas.reader.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.veritas.reader.ui.VeritasMotion

/** Scalable editorial artwork, drawn in the active pack's palette rather than a stock icon hero. */
@Composable
internal fun OnboardingArtwork(page: Int, playing: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val reveal by animateFloatAsState(if (playing) 1f else .45f, VeritasMotion.effectsFast(), label = "setupAudioInk")
    Canvas(Modifier.fillMaxWidth().height(if (page == 0) 210.dp else 140.dp).clearAndSetSemantics { }) {
        val unit = size.width.coerceAtMost(size.height * 2.2f) / 360f
        withTransform({ translate((size.width - 360f * unit) / 2f, (size.height - 180f * unit) / 2f); scale(unit, unit, Offset.Zero) }) {
            drawOval(colors.secondaryContainer.copy(alpha = .55f), Offset(22f, 20f), Size(316f, 153f))
            drawOval(colors.onSurface.copy(alpha = .08f), Offset(53f, 145f), Size(263f, 19f))
            // Books with individual jackets, a ribbon and a softly folded open spread.
            withTransform({ rotate(-9f, Offset(107f, 108f)) }) {
                drawRoundRect(colors.tertiaryContainer, Offset(41f, 42f), Size(102f, 112f), CornerRadius(9f))
                drawLine(colors.tertiary, Offset(53f, 48f), Offset(53f, 148f), 3f)
                drawRoundRect(colors.tertiary.copy(alpha = .24f), Offset(68f, 60f), Size(53f, 8f), CornerRadius(4f))
                drawCircle(colors.onTertiaryContainer.copy(alpha = .28f), 18f, Offset(94f, 101f))
            }
            val pages = Path().apply {
                moveTo(107f, 60f); cubicTo(135f, 46f, 166f, 50f, 187f, 65f)
                cubicTo(208f, 50f, 240f, 46f, 269f, 60f); lineTo(269f, 145f)
                cubicTo(240f, 135f, 210f, 137f, 187f, 151f)
                cubicTo(161f, 137f, 136f, 135f, 107f, 145f); close()
            }
            drawPath(pages, colors.surfaceContainerLowest)
            drawPath(pages, colors.outlineVariant.copy(alpha = .65f), style = Stroke(1.5f))
            drawLine(colors.outlineVariant, Offset(187f, 65f), Offset(187f, 150f), 1.5f)
            repeat(5) { line ->
                val y = 77f + line * 11f
                drawLine(colors.onSurface.copy(alpha = .18f), Offset(121f, y), Offset(173f, y + 3f), 2.5f, StrokeCap.Round)
                drawLine(colors.onSurface.copy(alpha = .18f), Offset(201f, y + 3f), Offset(254f - if (line == 4) 18f else 0f, y), 2.5f, StrokeCap.Round)
            }
            drawLine(colors.primary.copy(alpha = .42f), Offset(121f, 90f), Offset(173f, 93f), 8f, StrokeCap.Round)
            val ribbon = Path().apply { moveTo(231f, 53f); lineTo(245f, 51f); lineTo(245f, 92f); lineTo(238f, 86f); lineTo(231f, 94f); close() }
            drawPath(ribbon, colors.primary)
            // A listening disc, annotation card or study card completes each scene.
            if (page == 2 || page == 0) {
                drawCircle(colors.primaryContainer, 34f, Offset(282f, 129f))
                val arc = Path().apply { moveTo(258f, 132f); cubicTo(258f, 97f, 305f, 97f, 305f, 132f) }
                drawPath(arc, colors.onPrimaryContainer, style = Stroke(4f, cap = StrokeCap.Round))
                drawRoundRect(colors.primary, Offset(254f, 123f), Size(10f, 19f), CornerRadius(5f))
                drawRoundRect(colors.primary, Offset(300f, 123f), Size(10f, 19f), CornerRadius(5f))
                repeat(4) { i -> val h = (8f + (i % 3) * 8f) * reveal; drawLine(colors.primary, Offset(272f + i * 5f, 130f - h / 2f), Offset(272f + i * 5f, 130f + h / 2f), 3f, StrokeCap.Round) }
            } else {
                withTransform({ rotate(8f, Offset(287f, 119f)) }) {
                    drawRoundRect(colors.primaryContainer, Offset(260f, 86f), Size(62f, 70f), CornerRadius(9f))
                    repeat(3) { i -> drawLine(colors.onPrimaryContainer.copy(alpha = .4f), Offset(271f, 104f + i * 12f), Offset(308f - i * 4f, 104f + i * 12f), 3f, StrokeCap.Round) }
                    if (page == 4) {
                        val check = Path().apply { moveTo(271f, 135f); lineTo(279f, 143f); lineTo(294f, 127f) }
                        drawPath(check, colors.primary, style = Stroke(4f, cap = StrokeCap.Round))
                    }
                }
            }
            drawCircle(colors.tertiary.copy(alpha = .45f), 5f, Offset(284f, 46f))
            drawCircle(colors.primary.copy(alpha = .22f), 3f, Offset(69f, 28f))
        }
    }
}
