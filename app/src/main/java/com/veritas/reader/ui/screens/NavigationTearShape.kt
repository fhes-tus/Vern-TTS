package com.veritas.reader.ui.screens

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection

/** Rounded at rest; only the facing edge stretches to a point while splitting/rejoining. */
internal data class NavigationTearShape(
    val tear: Float,
    val leftEdge: Boolean,
    val cornerRadius: Dp? = null,
    val joiningProgress: Float = 1f
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val maxRadius = minOf(w, h) / 2f
        val r = cornerRadius?.let { with(density) { it.toPx() } }?.coerceIn(0f, maxRadius) ?: maxRadius
        val t = tear.coerceIn(0f, 1f)
        val edgeRadius = r * joiningProgress.coerceIn(0f, 1f)
        val k = .5522848f
        val path = Path()
        fun x(value: Float) = if (leftEdge) w - value else value
        path.moveTo(x(r), 0f)
        path.lineTo(x(w - edgeRadius), 0f)
        val topTip = edgeRadius + (h / 2f - edgeRadius) * t
        val topControl = edgeRadius * (1f - k) * (1f - t) + h * .25f * t
        path.cubicTo(x(w - edgeRadius + edgeRadius * k), 0f, x(w - edgeRadius * .55f * t), topControl, x(w), topTip)
        path.lineTo(x(w), h - topTip)
        path.cubicTo(x(w - edgeRadius * .55f * t), h - topControl, x(w - edgeRadius + edgeRadius * k), h, x(w - edgeRadius), h)
        path.lineTo(x(r), h)
        path.cubicTo(x(r - r * k), h, x(0f), h - r + r * k, x(0f), h - r)
        path.lineTo(x(0f), r)
        path.cubicTo(x(0f), r - r * k, x(r - r * k), 0f, x(r), 0f)
        path.close()
        return Outline.Generic(path)
    }
}

/** One material layer, with two outlines during the tear: no overlapping opacity. */
internal data class NavigationSplitShape(val separation: Float, val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = separation.coerceIn(0f, 1f)
        if (p < .001f) return RoundedCornerShape(radius).createOutline(size, layoutDirection, density)
        val gap = with(density) { 12.dp.toPx() } * p
        val cellWidth = (size.width - gap) / 4f
        val breakPoint = .48f
        fun smooth(value: Float): Float {
            val v = value.coerceIn(0f, 1f)
            return v * v * (3f - 2f * v)
        }
        val pinch = smooth(p / breakPoint)
        val tear = if (p < breakPoint) pinch else 1f - smooth((p - breakPoint) / (1f - breakPoint))
        val rtl = layoutDirection == LayoutDirection.Rtl
        val main = NavigationTearShape(tear, rtl, radius, p)
            .createOutline(Size(cellWidth * 3f, size.height), layoutDirection, density) as Outline.Generic
        val notes = NavigationTearShape(tear, !rtl, radius, p)
            .createOutline(Size(cellWidth, size.height), layoutDirection, density) as Outline.Generic
        val pieces = Path().apply {
            addPath(main.path, Offset(if (rtl) cellWidth + gap else 0f, 0f))
            addPath(notes.path, Offset(if (rtl) 0f else cellWidth * 3f + gap, 0f))
        }
        if (p >= breakPoint) return Outline.Generic(pieces)
        // Remain connected while the neck narrows. Union removes interior rims
        // and avoids a doubled translucent material where the shapes meet.
        val left = if (rtl) cellWidth else cellWidth * 3f
        val right = left + gap
        val centre = size.height / 2f
        val neck = centre * (1f - pinch) * (1f - pinch)
        val overlap = with(density) { 6.dp.toPx() }
        val bridge = Path().apply {
            moveTo(left - overlap, centre - neck)
            cubicTo(left + gap * .3f, centre - neck * .45f,
                right - gap * .3f, centre - neck * .45f, right + overlap, centre - neck)
            lineTo(right + overlap, centre + neck)
            cubicTo(right - gap * .3f, centre + neck * .45f,
                left + gap * .3f, centre + neck * .45f, left - overlap, centre + neck)
            close()
        }
        return Outline.Generic(Path.combine(PathOperation.Union, pieces, bridge))
    }
}
