package com.veritas.reader

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

/** Native PDF content uses the same unobscured-pane policy as the Compose screens. */
internal class VernFoldFrameLayout(context: Context) : FrameLayout(context) {
    internal var folds: List<VernFoldBounds> = emptyList()
        set(value) { field = value; requestLayout(); ViewCompat.requestApplyInsets(this) }
    private var contentPadding = VernPaneBounds(0, 0, 0, 0)
    private var observation: Job? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val activity = context.vernActivity() ?: return
        val owner = activity as? LifecycleOwner ?: return
        observation = owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) { vernFolds(activity).collect { folds = it } }
        }
    }

    override fun onDetachedFromWindow() { observation?.cancel(); observation = null; super.onDetachedFromWindow() }

    internal fun setContentPadding(left: Int, top: Int, right: Int, bottom: Int) {
        contentPadding = VernPaneBounds(left, top, right, bottom)
        updatePadding(width, height)
    }

    private fun pane(width: Int, height: Int) = vernUsablePane(VernPaneBounds(0, 0, width, height), folds,
        layoutDirection == View.LAYOUT_DIRECTION_RTL)

    private fun updatePadding(width: Int, height: Int) {
        val pane = pane(width, height)
        setPadding(pane.left + contentPadding.left, pane.top + contentPadding.top,
            width - pane.right + contentPadding.right, height - pane.bottom + contentPadding.bottom)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        updatePadding(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun dispatchApplyWindowInsets(insets: WindowInsets): WindowInsets {
        if (folds.isEmpty() || width == 0 || height == 0) return super.dispatchApplyWindowInsets(insets)
        val pane = pane(width, height)
        val compat = WindowInsetsCompat.toWindowInsetsCompat(insets, this)
        val types = WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        val safe = compat.getInsets(types)
        val remaining = androidx.core.graphics.Insets.of(if (pane.left == 0) safe.left else 0,
            if (pane.top == 0) safe.top else 0, if (pane.right == width) safe.right else 0,
            if (pane.bottom == height) safe.bottom else 0)
        return super.dispatchApplyWindowInsets(WindowInsetsCompat.Builder(compat).setInsets(types, remaining).build().toWindowInsets() ?: insets)
    }
}

internal class VernPdfControlsHeader(context: Context) : LinearLayout(context) {
    val summary = LinearLayout(context).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
    val transport = LinearLayout(context).apply { gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.END }
    private var stacked: Boolean? = null
    init { addView(summary); addView(transport) }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val next = MeasureSpec.getSize(widthMeasureSpec) < 360 * resources.displayMetrics.density
        if (next != stacked) {
            stacked = next
            orientation = if (next) VERTICAL else HORIZONTAL
            summary.layoutParams = LayoutParams(if (next) LayoutParams.MATCH_PARENT else 0, LayoutParams.WRAP_CONTENT, if (next) 0f else 1f)
            transport.layoutParams = LayoutParams(if (next) LayoutParams.MATCH_PARENT else LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
