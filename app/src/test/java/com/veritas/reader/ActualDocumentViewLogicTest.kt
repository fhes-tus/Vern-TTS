package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActualDocumentViewLogicTest {

    private val MIN_CANVAS_ZOOM = 0.75f
    private val MAX_CANVAS_ZOOM = 5.0f
    private val CANVAS_ZOOM_STEP = 0.25f

    private fun stepZoomIn(current: Float): Float {
        val next = ((Math.round(current * 4f) + 1) / 4f)
        return next.coerceIn(MIN_CANVAS_ZOOM, MAX_CANVAS_ZOOM)
    }

    private fun stepZoomOut(current: Float): Float {
        val next = ((Math.round(current * 4f) - 1) / 4f)
        return next.coerceIn(MIN_CANVAS_ZOOM, MAX_CANVAS_ZOOM)
    }

    private fun clampOffset(
        offsetX: Float,
        offsetY: Float,
        scale: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float
    ): Pair<Float, Float> {
        val maxOffsetX = (viewportWidthPx * (scale - 1f).coerceAtLeast(0f)) / 2f
        val maxOffsetY = (viewportHeightPx * (scale - 1f).coerceAtLeast(0f)) / 2f
        return Pair(
            offsetX.coerceIn(-maxOffsetX, maxOffsetX),
            offsetY.coerceIn(-maxOffsetY, maxOffsetY)
        )
    }

    @Test
    fun testZoomStepIncrements25Percent() {
        var zoom = 1.0f
        zoom = stepZoomIn(zoom)
        assertEquals(1.25f, zoom, 0.001f)
        zoom = stepZoomIn(zoom)
        assertEquals(1.50f, zoom, 0.001f)
        zoom = stepZoomIn(zoom)
        assertEquals(1.75f, zoom, 0.001f)
        zoom = stepZoomIn(zoom)
        assertEquals(2.00f, zoom, 0.001f)
    }

    @Test
    fun testZoomStepDecrementsDownTo75Percent() {
        var zoom = 1.0f
        zoom = stepZoomOut(zoom)
        assertEquals(0.75f, zoom, 0.001f)
        // Stepping out further must clamp to 75%
        zoom = stepZoomOut(zoom)
        assertEquals(0.75f, zoom, 0.001f)
    }

    @Test
    fun testZoomClampsAtMax500Percent() {
        var zoom = 4.75f
        zoom = stepZoomIn(zoom)
        assertEquals(5.0f, zoom, 0.001f)
        zoom = stepZoomIn(zoom)
        assertEquals(5.0f, zoom, 0.001f)
    }

    @Test
    fun testCentroidFocalPointOffsetCalculations() {
        val viewportW = 1080f
        val viewportH = 1920f
        val currentOffset = Pair(0f, 0f)
        val oldScale = 1.0f
        val nextScale = 2.0f
        val actualFactor = nextScale / oldScale

        // Finger pinched at (800, 1200) relative to viewport
        val focalX = 800f - (viewportW / 2f)
        val focalY = 1200f - (viewportH / 2f)

        val targetOffsetX = (currentOffset.first - focalX) * actualFactor + focalX
        val targetOffsetY = (currentOffset.second - focalY) * actualFactor + focalY

        val clamped = clampOffset(targetOffsetX, targetOffsetY, nextScale, viewportW, viewportH)
        val maxBoundX = (viewportW * (nextScale - 1f)) / 2f
        val maxBoundY = (viewportH * (nextScale - 1f)) / 2f

        assertTrue(clamped.first >= -maxBoundX && clamped.first <= maxBoundX)
        assertTrue(clamped.second >= -maxBoundY && clamped.second <= maxBoundY)
    }

    @Test
    fun testPaperToneModeTransitions() {
        fun nextTone(tone: PaperToneMode): PaperToneMode = when (tone) {
            PaperToneMode.ACTIVE_THEME -> PaperToneMode.DARK
            PaperToneMode.DARK -> PaperToneMode.NATURAL_WHITE
            PaperToneMode.NATURAL_WHITE -> PaperToneMode.WARM_SEPIA
            PaperToneMode.WARM_SEPIA -> PaperToneMode.ACTIVE_THEME
        }

        var tone = PaperToneMode.ACTIVE_THEME
        tone = nextTone(tone)
        assertEquals(PaperToneMode.DARK, tone)

        tone = nextTone(tone)
        assertEquals(PaperToneMode.NATURAL_WHITE, tone)

        tone = nextTone(tone)
        assertEquals(PaperToneMode.WARM_SEPIA, tone)

        tone = nextTone(tone)
        assertEquals(PaperToneMode.ACTIVE_THEME, tone)
    }

    @Test
    fun testClampOffsetBelowOneAndAboveOne() {
        val viewportW = 1080f
        val viewportH = 1920f

        // When scale <= 1.0f (e.g. 0.75f), clampOffset must return (0,0) and never crash with empty range
        val sub1Offset = clampOffset(100f, -50f, 0.75f, viewportW, viewportH)
        assertEquals(0f, sub1Offset.first, 0.001f)
        assertEquals(0f, sub1Offset.second, 0.001f)

        val oneOffset = clampOffset(100f, -50f, 1.0f, viewportW, viewportH)
        assertEquals(0f, oneOffset.first, 0.001f)
        assertEquals(0f, oneOffset.second, 0.001f)

        // When scale > 1.0f (e.g. 2.0f), clampOffset permits panning within viewport bounds
        val zoomedOffset = clampOffset(200f, -300f, 2.0f, viewportW, viewportH)
        assertEquals(200f, zoomedOffset.first, 0.001f)
        assertEquals(-300f, zoomedOffset.second, 0.001f)

        // Beyond maximum bounds
        val overBoundOffset = clampOffset(1000f, -2000f, 2.0f, viewportW, viewportH)
        val maxBoundX = (viewportW * (2.0f - 1f)) / 2f
        val maxBoundY = (viewportH * (2.0f - 1f)) / 2f
        assertEquals(maxBoundX, overBoundOffset.first, 0.001f)
        assertEquals(-maxBoundY, overBoundOffset.second, 0.001f)
    }
}
