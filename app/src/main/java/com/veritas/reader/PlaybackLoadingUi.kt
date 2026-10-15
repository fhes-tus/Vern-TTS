package com.veritas.reader

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.veritas.reader.ui.VeritasMotion

/** A stationary play glyph with a small loading ring; the parent remains cancellable. */
@Composable
internal fun PlaybackControlIcon(
    isPlaying: Boolean,
    isPreparing: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp
) {
    val description = if (isPreparing) "Cancel starting playback" else if (isPlaying) "Pause" else "Play"
    Box(modifier.size(size).clearAndSetSemantics {
        contentDescription = description
        if (isPreparing) stateDescription = "Preparing playback"
    }.testTag(if (isPreparing) "playback_preparing" else "playback_ready"), contentAlignment = Alignment.Center) {
        if (isPreparing) {
            if (VeritasMotion.scheme.reduceMotion) {
                ComposeCanvas(Modifier.size(size)) {
                    drawArc(tint, -90f, 270f, false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                }
            } else {
                CircularProgressIndicator(Modifier.size(size), color = tint, strokeWidth = 2.dp)
            }
        }
        Icon(if (isPlaying && !isPreparing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = null, tint = tint, modifier = Modifier.size(if (isPreparing) size * .58f else size))
    }
}

/** Native PDF counterpart; only its ring rotates, and its host owns start/stop. */
internal class PlaybackLoadingDrawable(color: Int, private val density: Float, private val reduceMotion: Boolean) : Drawable(), Animatable {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private val triangle = Path()
    private var rotation = -90f
    private val animator = ValueAnimator.ofFloat(-90f, 270f).apply {
        duration = 1200
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { rotation = it.animatedValue as Float; invalidateSelf() }
    }

    override fun draw(canvas: Canvas) {
        val x = bounds.exactCenterX()
        val y = bounds.exactCenterY()
        val radius = 12f * density
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(x - radius, y - radius, x + radius, y + radius, rotation, 270f, false, paint)
        paint.style = Paint.Style.FILL
        triangle.reset()
        triangle.moveTo(x - 3f * density, y - 5f * density)
        triangle.lineTo(x + 5f * density, y)
        triangle.lineTo(x - 3f * density, y + 5f * density)
        triangle.close()
        canvas.drawPath(triangle, paint)
    }

    override fun start() { if (!reduceMotion && !animator.isStarted) animator.start() }
    override fun stop() { animator.cancel() }
    override fun isRunning(): Boolean = animator.isRunning
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Android") override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
