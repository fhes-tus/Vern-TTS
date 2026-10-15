package com.veritas.reader.ui.screens

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.testTag
import kotlin.math.*

/** Capture only for an active turn; stationary pages retain their live selectable text. */
@Composable
internal fun BookPageCurl(enabled: Boolean, scrolling: Boolean, offset: () -> Float,
    paper: Color, content: @Composable () -> Unit) {
    if (!enabled) { Box(Modifier.fillMaxSize()) { content() }; return }
    val layer = rememberGraphicsLayer()
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    val renderer = remember(paper) { CurledPageMesh(paper.toArgb()) }
    LaunchedEffect(scrolling) {
        if (!scrolling) { snapshot = null; return@LaunchedEffect }
        // Adjacent page layers have been recorded by the pager's prefetch drawing.
        repeat(3) {
            withFrameNanos { }
            if (layer.size.width > 0 && layer.size.height > 0) {
                snapshot = runCatching { layer.toImageBitmap() }.getOrNull()
                if (snapshot != null) return@LaunchedEffect
            }
        }
    }
    val raw = offset()
    Box(Modifier.fillMaxSize().testTag(if (snapshot != null) "book-curl-snapshot" else "book-curl-live")
        .zIndex(if (scrolling && raw > .001f && raw < .999f) 2f else if (abs(raw) < .001f) 1f else 0f)
        .graphicsLayer {
            val position = offset()
            // Alpha does not remove a page from hit testing. At rest, let the pager
            // keep neighbours physically offscreen so its live page receives gestures.
            translationX = if (scrolling) position * size.width else 0f
            alpha = if (scrolling && (position < -1.001f || position >= .999f)) 0f else 1f
        }.drawWithContent {
            val turn = offset().coerceIn(0f, 1f)
            val bitmap = snapshot
            if (bitmap != null && turn > .001f) {
                renderer.draw(drawContext.canvas.nativeCanvas, bitmap.asAndroidBitmap(), size.width, size.height, turn)
            } else {
                layer.record { this@drawWithContent.drawContent() }
                drawLayer(layer)
            }
        }.background(paper)) { content() }
}

/** Bounded cylindrical mesh with perspective, a paper reverse and a travelling shadow. */
internal class CurledPageMesh(private val paper: Int) {
    private val columns = 48
    private val rows = 16
    private val vertices = FloatArray((columns + 1) * (rows + 1) * 2)
    private val colours = IntArray((columns + 1) * (rows + 1))
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val reverse = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val reversePath = Path()
    private val point = FloatArray(3)

    private fun bend(x: Float, y: Float, w: Float, h: Float, p: Float) {
        val diagonal = (y / h - .5f) * w * .16f * sin(p * PI).toFloat()
        val spine = w * (1f - p) + diagonal
        if (x <= spine) { point[0] = x; point[1] = y; point[2] = 0f; return }
        val radius = max(1f, min(w * .16f, w * (1f - p) * .55f))
        val distance = max(0f, x - spine)
        val angle = min(PI.toFloat(), distance / radius)
        val bentX = spine + radius * sin(angle) - max(0f, distance - PI.toFloat() * radius)
        val depth = radius * (1f - cos(angle))
        val perspective = (w * 5f) / (w * 5f - depth)
        point[0] = spine + (bentX - spine) * perspective
        point[1] = h * .5f + (y - h * .5f) * perspective
        point[2] = angle
    }

    fun draw(canvas: android.graphics.Canvas, bitmap: Bitmap, width: Float, height: Float, progress: Float) {
        if (width <= 0f || height <= 0f) return
        val p = progress.coerceIn(0f, 1f)
        val spine = width * (1f - p)
        val radius = max(1f, min(width * .16f, width * (1f - p) * .55f))
        val save = canvas.save()
        canvas.clipRect(0f, 0f, width, height)
        shadow.shader = android.graphics.LinearGradient(spine, 0f, spine + radius * 1.65f, 0f,
            intArrayOf(0x50000000, 0x00000000), null, android.graphics.Shader.TileMode.CLAMP)
        canvas.drawRect(spine, 0f, min(width, spine + radius * 1.65f), height, shadow)
        for (row in 0..rows) for (column in 0..columns) {
            val vertex = row * (columns + 1) + column
            bend(width * column / columns, height * row / rows, width, height, p)
            vertices[vertex * 2] = point[0]
            vertices[vertex * 2 + 1] = point[1]
            val shade = ((1f - .20f * sin(point[2]).coerceAtLeast(0f)) * 255).roundToInt().coerceIn(0, 255)
            colours[vertex] = android.graphics.Color.rgb(shade, shade, shade)
        }
        canvas.drawBitmapMesh(bitmap, columns, rows, vertices, 0, colours, 0, ink)
        // The folded material faces away from the reader. A paper tint softens mirrored ink.
        if (width > spine + radius * PI.toFloat() * .5f) {
            reversePath.reset()
            for (row in 0..rows) {
                val y = height * row / rows
                val diagonal = (y / height - .5f) * width * .16f * sin(p * PI).toFloat()
                bend(min(width, spine + diagonal + radius * PI.toFloat() * .5f), y, width, height, p)
                if (row == 0) reversePath.moveTo(point[0], point[1]) else reversePath.lineTo(point[0], point[1])
            }
            for (row in rows downTo 0) {
                bend(width, height * row / rows, width, height, p)
                reversePath.lineTo(point[0], point[1])
            }
            reversePath.close()
            reverse.color = paper
            reverse.alpha = 232
            reverse.shader = null
            canvas.drawPath(reversePath, reverse)
            reverse.alpha = 255
            reverse.shader = android.graphics.LinearGradient(spine - radius, 0f, spine + radius, 0f,
                intArrayOf(0x16000000, 0x00000000, 0x24000000), null, android.graphics.Shader.TileMode.CLAMP)
            canvas.drawPath(reversePath, reverse)
        }
        canvas.restoreToCount(save)
    }
}
