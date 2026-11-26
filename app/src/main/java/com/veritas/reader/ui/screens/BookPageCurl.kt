package com.veritas.reader.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.zIndex
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

internal val PageCurlSettle = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = 520f, visibilityThreshold = .5f)
private const val CURL_STRIPS = 6
private val curlAngles = FloatArray(CURL_STRIPS + 1) { (PI * it / (2 * CURL_STRIPS)).toFloat() }
private val curlProjection = FloatArray(CURL_STRIPS + 1) { sin(curlAngles[it]) }

@Composable
internal fun pageCurlFlingBehavior(pager: PagerState) = PagerDefaults.flingBehavior(
    state = pager,
    decayAnimationSpec = remember { exponentialDecay<Float>(frictionMultiplier = 3f) },
    snapAnimationSpec = PageCurlSettle
)

internal enum class CurlDragAxis { Undecided, Horizontal, Vertical }

internal fun curlDragAxis(displacement: Offset, slop: Float): CurlDragAxis {
    val horizontal = abs(displacement.x)
    val vertical = abs(displacement.y)
    return when {
        horizontal > slop && horizontal > vertical * 1.35f -> CurlDragAxis.Horizontal
        vertical > slop -> CurlDragAxis.Vertical
        else -> CurlDragAxis.Undecided
    }
}

private class CurlDragLock : NestedScrollConnection {
    var axis: CurlDragAxis? = null
    var touching = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (source == NestedScrollSource.UserInput && touching && axis != CurlDragAxis.Horizontal)
            Offset(available.x, 0f) else Offset.Zero

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (available.x == 0f) return Velocity.Zero
        val blockFling = axis != null && axis != CurlDragAxis.Horizontal
        if (!touching) axis = null
        return if (blockFling) Velocity(available.x, 0f) else Velocity.Zero
    }
}

/** Gate only horizontal pager motion. The text page keeps its own vertical deltas and fling. */
@Composable
internal fun Modifier.pageCurlDragLock(): Modifier {
    val lock = remember { CurlDragLock() }
    return this.nestedScroll(lock).pointerInput(lock) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            lock.axis = CurlDragAxis.Undecided
            lock.touching = true
            var displacement = Offset.Zero
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } > 1) lock.axis = CurlDragAxis.Vertical
                    val primary = event.changes.firstOrNull { it.id == down.id }
                    if (lock.axis == CurlDragAxis.Undecided && primary?.pressed == true) {
                        displacement += primary.position - primary.previousPosition
                        lock.axis = curlDragAxis(displacement, viewConfiguration.touchSlop)
                    }
                    if (event.changes.none { it.pressed }) break
                }
            } finally {
                lock.touching = false
            }
        }
    }
}

internal data class PageCurlFold(val spine: Float, val radius: Float, val arc: Float) {
    val edge: Float get() = spine + radius * sin(arc)
    val sourceEdge: Float get() = spine + radius * arc

    companion object {
        fun at(width: Float, progress: Float): PageCurlFold {
            val fraction = progress.coerceIn(0f, 1f)
            val spine = width * (1f - fraction)
            // The page starts and ends flat, with no sudden change in fold width.
            val bend = if (fraction > 0f && fraction < 1f) sin(PI * fraction).toFloat() else 0f
            val radius = width * .155f * bend * bend
            return PageCurlFold(spine, radius, (PI / 2).toFloat())
        }
    }
}

private fun PagerState.curlPosition(page: Int): Float =
    if (isScrollInProgress) currentPage - page + currentPageOffsetFraction else 0f

/** The fold reuses a hardware display list of the live page, with no bitmap capture window. */
@Composable
internal fun BookPageCurl(
    enabled: Boolean,
    pager: PagerState,
    page: Int,
    paper: Color,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        Box(Modifier.fillMaxSize()) { content() }
        return
    }
    val layer = rememberGraphicsLayer()
    val paperBack = remember { Path() }
    val frontIsTurning by remember(pager, page) {
        derivedStateOf {
            val progress = pager.curlPosition(page)
            progress > 0f && progress < 1f
        }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        Modifier.fillMaxSize()
            .testTag(if (frontIsTurning) "book-curl-turning" else "book-curl-live")
            // The earlier page covers the next page in either direction, even when
            // Pager changes its current page halfway through the same turn.
            .zIndex(-page.toFloat())
            .graphicsLayer {
                val position = pager.curlPosition(page)
                translationX = if (abs(position) < 1f)
                    (position * size.width).roundToInt() * if (rtl) -1f else 1f else 0f
            }
            .drawWithContent {
                val progress = pager.curlPosition(page)
                if (progress > 0f && progress < 1f && size.width >= 1f && size.height >= 1f) {
                    layer.record { this@drawWithContent.drawContent() }
                    drawPageFold(layer, PageCurlFold.at(size.width, progress), paper, paperBack, rtl)
                } else {
                    drawContent()
                }
            }
            // The source display list can reference the page's layer while its content stays clean.
            .graphicsLayer()
    ) { content() }
}

private fun DrawScope.drawPageFold(
    layer: GraphicsLayer,
    fold: PageCurlFold,
    paper: Color,
    paperBack: Path,
    rtl: Boolean
) {
    val width = size.width
    val height = size.height
    val edge = fold.edge
    fun x(logical: Float) = if (rtl) width - logical else logical
    val shade = (fold.radius / (width * .05f)).coerceIn(0f, 1f)

    val shadowEnd = (edge + fold.radius * .85f).coerceAtMost(width)
    if (shadowEnd > edge) {
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .24f * shade), Color.Transparent),
                startX = x(edge), endX = x(shadowEnd)),
            topLeft = Offset(minOf(x(edge), x(shadowEnd)), 0f),
            size = Size(shadowEnd - edge, height)
        )
    }

    drawRect(paper, topLeft = Offset(minOf(x(0f), x(edge)), 0f), size = Size(edge, height))
    clipRect(left = minOf(x(0f), x(fold.spine)), right = maxOf(x(0f), x(fold.spine))) {
        drawLayer(layer)
    }

    // Only the narrow curved edge needs slices. The rest of the page is one clipped draw.
    for (index in 0 until CURL_STRIPS) {
        val sourceA = x(fold.spine + fold.radius * curlAngles[index])
        val sourceB = x(fold.spine + fold.radius * curlAngles[index + 1])
        val destA = x(fold.spine + fold.radius * curlProjection[index])
        val destB = x(fold.spine + fold.radius * curlProjection[index + 1])
        val sourceWidth = abs(sourceB - sourceA)
        val destWidth = abs(destB - destA)
        if (sourceWidth <= .001f || destWidth <= .001f) continue
        val left = minOf(destA, destB)
        clipRect(left = left, right = maxOf(destA, destB)) {
            withTransform({
                translate(left, 0f)
                scale(destWidth / sourceWidth, 1f, Offset.Zero)
                translate(-minOf(sourceA, sourceB), 0f)
            }) {
                drawLayer(layer)
            }
        }
    }
    if (edge > fold.spine) {
        drawRect(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .26f * shade)),
                startX = x(fold.spine), endX = x(edge)),
            topLeft = Offset(minOf(x(fold.spine), x(edge)), 0f),
            size = Size(edge - fold.spine, height)
        )
    }

    val reverse = minOf((width - fold.sourceEdge) * .4f, fold.radius * 1.35f, edge)
    if (reverse > 0f) {
        val inner = edge - reverse
        paperBack.reset()
        paperBack.moveTo(x(edge), 0f)
        paperBack.quadraticTo(x(edge - reverse * .22f), height * .46f, x(edge), height)
        paperBack.lineTo(x(inner + reverse * .2f), height)
        paperBack.quadraticTo(x(inner - reverse * .24f), height * .48f, x(inner), 0f)
        paperBack.close()
        drawPath(paperBack, Brush.horizontalGradient(
            listOf(lerp(paper, Color.Black, .14f), paper, lerp(paper, Color.Black, .24f)),
            startX = x(inner), endX = x(edge)
        ))
    }
}
