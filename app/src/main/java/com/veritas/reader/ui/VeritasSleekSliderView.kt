package com.veritas.reader.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/**
 * A sleek Android View slider matching Veritas Design (Images 4 & 5):
 * - Slim 4.dp rounded track
 * - Theme primary color active track extending to the center of the thumb
 * - Soft muted outline inactive track
 * - Clean, sleek 18.dp circular thumb with 2.dp surface border and drop shadow
 * - Responsive tap and drag gestures with haptics
 */
class VeritasSleekSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var minVal: Float = 0.5f
    var maxVal: Float = 2.0f
    var step: Float = 0.05f
    var currentVal: Float = 1.0f
        set(v) {
            field = v.coerceIn(minVal, maxVal)
            invalidate()
        }

    var isTrackingTouch: Boolean = false
        private set

    var onProgressChangedUser: ((Float) -> Unit)? = null
    var onStopTracking: ((Float) -> Unit)? = null

    private fun quantize(rawVal: Float): Float {
        if (step <= 0f) return rawVal.coerceIn(minVal, maxVal)
        val stepsFromMin = Math.round((rawVal - minVal) / step)
        val snapped = minVal + stepsFromMin * step
        return snapped.coerceIn(minVal, maxVal)
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private fun dp(v: Int) = (v * density).toInt()

    private val trackHeightPx = dp(4f)
    private val thumbRadiusPx = dp(9f) // 18dp diameter
    private val borderWidthPx = dp(2f)

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val thumbBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderWidthPx
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x2E000000.toInt()
    }

    private val trackRect = RectF()
    private val activeTrackRect = RectF()

    fun setSliderColors(primary: Int, surface: Int, outline: Int) {
        activePaint.color = primary
        thumbPaint.color = primary
        thumbBorderPaint.color = surface
        val alpha = 0.45f
        val r = Color.red(outline)
        val g = Color.green(outline)
        val b = Color.blue(outline)
        inactivePaint.color = Color.argb((alpha * 255).toInt(), r, g, b)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = dp(36) + paddingTop + paddingBottom
        val measuredHeight = resolveSize(desiredHeight, heightMeasureSpec)
        val measuredWidth = resolveSize(dp(100), widthMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cy = height / 2f
        val trackStart = paddingLeft.toFloat()
        val trackEnd = (width - paddingRight).toFloat()
        val trackWidth = (trackEnd - trackStart).coerceAtLeast(thumbRadiusPx * 2)

        val span = (maxVal - minVal).takeIf { it > 0f } ?: 1f
        val progress = ((currentVal - minVal) / span).coerceIn(0f, 1f)

        val usableWidth = trackWidth - thumbRadiusPx * 2
        val thumbX = trackStart + thumbRadiusPx + progress * usableWidth

        val halfTrack = trackHeightPx / 2f
        val trackCorner = halfTrack

        // 1. Inactive Track (Full track with rounded ends)
        trackRect.set(trackStart, cy - halfTrack, trackEnd, cy + halfTrack)
        canvas.drawRoundRect(trackRect, trackCorner, trackCorner, inactivePaint)

        // 2. Active Track (From track start to thumb center)
        if (thumbX > trackStart) {
            activeTrackRect.set(trackStart, cy - halfTrack, thumbX, cy + halfTrack)
            canvas.drawRoundRect(activeTrackRect, trackCorner, trackCorner, activePaint)
        }

        // 3. Thumb Shadow (1.5dp offset)
        canvas.drawCircle(thumbX, cy + dp(1.5f), thumbRadiusPx + dp(0.5f), shadowPaint)

        // 4. Thumb Fill (Primary color)
        canvas.drawCircle(thumbX, cy, thumbRadiusPx, thumbPaint)

        // 5. Thumb Border (Surface color, 2dp stroke)
        canvas.drawCircle(thumbX, cy, thumbRadiusPx - borderWidthPx / 2f, thumbBorderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        val span = (maxVal - minVal).takeIf { it > 0f } ?: 1f

        val trackStart = paddingLeft.toFloat()
        val trackEnd = (width - paddingRight).toFloat()
        val trackWidth = (trackEnd - trackStart).coerceAtLeast(thumbRadiusPx * 2)
        val usableWidth = (trackWidth - thumbRadiusPx * 2).coerceAtLeast(1f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isTrackingTouch = true
                parent?.requestDisallowInterceptTouchEvent(true)
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                val rawProgress = ((event.x - trackStart - thumbRadiusPx) / usableWidth).coerceIn(0f, 1f)
                val newVal = quantize(minVal + rawProgress * span)
                if (newVal != currentVal) {
                    currentVal = newVal
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onProgressChangedUser?.invoke(newVal)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val rawProgress = ((event.x - trackStart - thumbRadiusPx) / usableWidth).coerceIn(0f, 1f)
                val newVal = quantize(minVal + rawProgress * span)
                if (newVal != currentVal) {
                    currentVal = newVal
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onProgressChangedUser?.invoke(newVal)
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isTrackingTouch = false
                val rawProgress = ((event.x - trackStart - thumbRadiusPx) / usableWidth).coerceIn(0f, 1f)
                val newVal = quantize(minVal + rawProgress * span)
                if (newVal != currentVal) {
                    currentVal = newVal
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onProgressChangedUser?.invoke(newVal)
                }
                onStopTracking?.invoke(currentVal)
                parent?.requestDisallowInterceptTouchEvent(false)
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
