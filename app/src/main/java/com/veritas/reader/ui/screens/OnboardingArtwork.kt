package com.veritas.reader.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.veritas.reader.ui.VeritasMotion
import com.veritas.reader.ui.LocalVeritasMotion

/** Five distinct vector scenes: no image downloads, extra bitmap payload or theme mismatch. */
@Composable
internal fun OnboardingArtwork(page: Int, playing: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val reduceMotion = LocalVeritasMotion.current.reduceMotion
    // No continuous animation is allocated when reduced motion is enabled.
    val drift = if (reduceMotion) 0f else {
        val transition = rememberInfiniteTransition(label = "setupScene")
        val position by transition.animateFloat(-1f, 1f,
            infiniteRepeatable(tween(2800, easing = EaseInOutSine), RepeatMode.Reverse), label = "setupSceneDrift")
        position
    }
    val pulse by animateFloatAsState(if (playing) 1f else .45f, VeritasMotion.effectsFast(), label = "setupAudioInk")
    Canvas(Modifier.fillMaxWidth().height(if (page == 0) 210.dp else 150.dp).clearAndSetSemantics { }) {
        val unit = size.width.coerceAtMost(size.height * 2.2f) / 360f
        withTransform({
            translate((size.width - 360f * unit) / 2f, (size.height - 180f * unit) / 2f)
            scale(unit, unit, Offset.Zero)
        }) {
            drawOval(colors.secondaryContainer.copy(alpha = .45f), Offset(24f, 16f), Size(312f, 154f))
            drawOval(colors.onSurface.copy(alpha = .06f), Offset(50f, 151f), Size(260f, 13f))
            withTransform({
                translate(0f, drift * 3f)
                rotate(drift * .7f, Offset(180f, 90f))
            }) {
                when (page) {
                    0 -> readingNook(colors)
                    1 -> bookSpread(colors)
                    2 -> listeningScene(colors, pulse, if (playing && !reduceMotion) drift else 0f)
                    3 -> studyScene(colors)
                    else -> newChapter(colors)
                }
            }
        }
    }
}

private fun DrawScope.paper(c: ColorScheme, x: Float, y: Float, w: Float, h: Float) {
    drawRoundRect(c.surfaceContainerLowest, Offset(x, y), Size(w, h), CornerRadius(8f))
    drawRoundRect(c.outlineVariant, Offset(x, y), Size(w, h), CornerRadius(8f), style = Stroke(1.5f))
}
private fun DrawScope.ink(c: ColorScheme, x: Float, y: Float, width: Float) =
    drawLine(c.onSurface.copy(alpha = .25f), Offset(x, y), Offset(x + width, y), 3f, StrokeCap.Round)

private fun DrawScope.readingNook(c: ColorScheme) {
    // A sunny window, reading chair, side table and leafy plant invite the reader in.
    drawRoundRect(c.primaryContainer, Offset(72f, 28f), Size(104f, 98f), CornerRadius(30f, 30f))
    drawCircle(c.tertiary.copy(alpha = .6f), 18f, Offset(142f, 55f))
    drawLine(c.surface, Offset(124f, 29f), Offset(124f, 125f), 4f)
    drawLine(c.surface, Offset(73f, 78f), Offset(175f, 78f), 4f)
    drawRoundRect(c.tertiaryContainer, Offset(107f, 89f), Size(89f, 57f), CornerRadius(20f))
    drawRoundRect(c.tertiary, Offset(99f, 125f), Size(109f, 24f), CornerRadius(10f))
    drawLine(c.onTertiaryContainer, Offset(119f, 147f), Offset(114f, 160f), 4f)
    drawLine(c.onTertiaryContainer, Offset(187f, 147f), Offset(192f, 160f), 4f)
    withTransform({ rotate(-12f, Offset(153f, 118f)) }) {
        paper(c, 132f, 100f, 45f, 29f)
        drawLine(c.primary, Offset(154f, 102f), Offset(154f, 127f), 2f)
        ink(c, 139f, 110f, 9f); ink(c, 160f, 115f, 10f)
    }
    drawLine(c.outline, Offset(252f, 137f), Offset(252f, 159f), 4f)
    drawRoundRect(c.secondary, Offset(220f, 130f), Size(65f, 9f), CornerRadius(4f))
    drawRoundRect(c.primaryContainer, Offset(231f, 119f), Size(41f, 11f), CornerRadius(3f))
    drawRoundRect(c.tertiaryContainer, Offset(235f, 108f), Size(35f, 10f), CornerRadius(3f))
    drawLine(c.secondary, Offset(292f, 128f), Offset(292f, 77f), 3f)
    drawOval(c.secondary, Offset(274f, 80f), Size(18f, 31f))
    drawOval(c.primary, Offset(294f, 65f), Size(18f, 32f))
    drawRoundRect(c.tertiary, Offset(280f, 129f), Size(26f, 26f), CornerRadius(5f))
}

private fun DrawScope.bookSpread(c: ColorScheme) {
    val pages = Path().apply {
        moveTo(67f, 46f); cubicTo(104f, 30f, 146f, 34f, 180f, 52f)
        cubicTo(214f, 34f, 256f, 30f, 293f, 46f); lineTo(293f, 144f)
        cubicTo(254f, 133f, 214f, 137f, 180f, 155f)
        cubicTo(145f, 137f, 106f, 133f, 67f, 144f); close()
    }
    drawPath(pages, c.surfaceContainerLowest)
    drawPath(pages, c.outlineVariant, style = Stroke(2f))
    drawLine(c.outlineVariant, Offset(180f, 52f), Offset(180f, 155f), 2f)
    repeat(6) { line ->
        ink(c, 86f, 68f + line * 12f, if (line == 5) 48f else 73f)
        ink(c, 201f, 68f + line * 12f, if (line == 5) 44f else 73f)
    }
    drawLine(c.primary.copy(alpha = .3f), Offset(86f, 92f), Offset(159f, 92f), 10f, StrokeCap.Round)
    val ribbon = Path().apply { moveTo(249f, 38f); lineTo(267f, 40f); lineTo(267f, 86f); lineTo(258f, 79f); lineTo(249f, 86f); close() }
    drawPath(ribbon, c.primary)
    drawCircle(c.tertiaryContainer, 19f, Offset(57f, 126f))
    drawLine(c.onTertiaryContainer, Offset(47f, 126f), Offset(67f, 126f), 3f, StrokeCap.Round)
}

private fun DrawScope.listeningScene(c: ColorScheme, pulse: Float, wave: Float) {
    paper(c, 122f, 38f, 119f, 118f)
    drawCircle(c.primaryContainer, 27f, Offset(181f, 77f))
    val play = Path().apply { moveTo(174f, 63f); lineTo(174f, 90f); lineTo(195f, 77f); close() }
    drawPath(play, c.primary)
    repeat(11) { i ->
        val heights = listOf(10f, 19f, 29f, 17f, 36f, 25f, 40f, 22f, 30f, 18f, 10f)
        val h = heights[i] * (.6f + pulse * .4f) * (1f + wave * (if (i % 2 == 0) .2f else -.2f))
        drawLine(c.primary, Offset(141f + i * 8f, 126f - h / 2), Offset(141f + i * 8f, 126f + h / 2), 4f, StrokeCap.Round)
    }
    val band = Path().apply { moveTo(83f, 111f); cubicTo(79f, -3f, 279f, -3f, 278f, 111f) }
    drawPath(band, c.secondary, style = Stroke(11f, cap = StrokeCap.Round))
    drawRoundRect(c.primary, Offset(71f, 92f), Size(26f, 55f), CornerRadius(12f))
    drawRoundRect(c.primary, Offset(265f, 92f), Size(26f, 55f), CornerRadius(12f))
    drawRoundRect(c.primaryContainer, Offset(83f, 100f), Size(14f, 39f), CornerRadius(7f))
    drawRoundRect(c.primaryContainer, Offset(265f, 100f), Size(14f, 39f), CornerRadius(7f))
}

private fun DrawScope.studyScene(c: ColorScheme) {
    withTransform({ rotate(-8f, Offset(130f, 92f)) }) {
        paper(c, 71f, 30f, 130f, 123f)
        drawRoundRect(c.primaryContainer, Offset(80f, 43f), Size(112f, 19f), CornerRadius(4f))
        repeat(4) { i ->
            val y = 80f + i * 16f
            drawRoundRect(c.primary, Offset(85f, y - 4f), Size(7f, 7f), CornerRadius(2f))
            ink(c, 103f, y, 70f - i * 7f)
        }
    }
    withTransform({ rotate(9f, Offset(249f, 104f)) }) {
        drawRoundRect(c.tertiaryContainer, Offset(191f, 49f), Size(113f, 100f), CornerRadius(9f))
        paper(c, 183f, 62f, 113f, 94f)
        drawCircle(c.tertiary, 13f, Offset(210f, 87f))
        ink(c, 232f, 86f, 45f); ink(c, 199f, 114f, 77f); ink(c, 199f, 130f, 59f)
    }
    drawLine(c.secondary, Offset(301f, 112f), Offset(314f, 52f), 8f, StrokeCap.Round)
    drawCircle(c.primary, 5f, Offset(180f, 26f))
}

private fun DrawScope.newChapter(c: ColorScheme) {
    drawCircle(c.tertiaryContainer, 36f, Offset(255f, 61f))
    drawCircle(c.tertiary, 21f, Offset(255f, 61f))
    val trail = Path().apply { moveTo(108f, 165f); cubicTo(206f, 151f, 174f, 104f, 264f, 105f) }
    drawPath(trail, c.secondaryContainer, style = Stroke(24f, cap = StrokeCap.Round))
    drawPath(trail, c.secondary.copy(alpha = .4f), style = Stroke(2f, cap = StrokeCap.Round))
    paper(c, 83f, 36f, 103f, 105f)
    drawCircle(c.primaryContainer, 25f, Offset(135f, 81f))
    val check = Path().apply { moveTo(119f, 80f); lineTo(131f, 92f); lineTo(151f, 70f) }
    drawPath(check, c.primary, style = Stroke(5f, cap = StrokeCap.Round))
    ink(c, 106f, 117f, 57f)
    drawLine(c.primary, Offset(291f, 148f), Offset(291f, 120f), 3f)
    drawOval(c.secondary, Offset(271f, 111f), Size(20f, 14f))
    drawOval(c.primary, Offset(291f, 101f), Size(20f, 18f))
    drawCircle(c.primary.copy(alpha = .4f), 4f, Offset(218f, 37f))
}
