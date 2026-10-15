package com.veritas.reader.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.veritas.reader.ui.LocalVeritasMotion
import kotlin.math.PI
import kotlin.math.sin

/** Quiet, fanned book pages; the artwork stays behind the statistics and never affects layout. */
@Composable
internal fun Modifier.readingPageCanvas(): Modifier {
    val ink = MaterialTheme.colorScheme.onSurface
    val phase = if (LocalVeritasMotion.current.reduceMotion) remember { mutableFloatStateOf(0f) }
    else rememberInfiniteTransition(label = "readingPages").animateFloat(
        0f, 1f, infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart),
        label = "pageTurnPhase"
    )
    return drawBehind {
        val w = size.width
        val h = size.height
        val spine = Offset(w * .94f, h * 1.22f)
        val turn = sin(phase.value * 2f * PI.toFloat())
        // Oversized sheets fan out from a shared spine beyond the lower-right corner.
        // A gentle change of angle evokes turning pages without bouncing the canvas.
        repeat(4) { index ->
            rotate(-22f + index * 12f + turn * (index + 1) * .7f, spine) {
                val left = w * .72f
                val right = w * 1.06f
                val top = -h * .25f
                val page = Path().apply {
                    moveTo(left, spine.y)
                    lineTo(left, top + h * .08f)
                    quadraticTo(w * .89f, top - h * .025f, right, top)
                    lineTo(right, spine.y)
                    close()
                }
                drawPath(page, ink.copy(alpha = .018f))
                drawPath(page, ink.copy(alpha = .045f), style = Stroke(.8.dp.toPx()))
                if (index == 3) {
                    // Typeset lines and a small bookmark cutout make the silhouette read as a page.
                    repeat(7) { line ->
                        val y = top + h * (.22f + line * .095f)
                        drawLine(ink.copy(alpha = .045f), Offset(left + w * .035f, y),
                            Offset(right - w * (if (line == 6) .11f else .04f), y), .8.dp.toPx())
                    }
                    val ribbon = Path().apply {
                        moveTo(right - w * .08f, top)
                        lineTo(right - w * .08f, top + h * .28f)
                        lineTo(right - w * .055f, top + h * .23f)
                        lineTo(right - w * .03f, top + h * .28f)
                        lineTo(right - w * .03f, top)
                        close()
                    }
                    drawPath(ribbon, ink.copy(alpha = .055f))
                }
            }
        }
    }
}
