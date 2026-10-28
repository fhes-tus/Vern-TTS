package com.veritas.reader

import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Keeps native PDF controls and fragment routing; only their material is Compose. */
internal fun VeritasPdfViewerActivity.installPdfPlayerMaterial(controls: View, width: Int): FrameLayout {
    val fallback = controls.background
    val frame = FrameLayout(this).apply {
        layoutParams = android.widget.LinearLayout.LayoutParams(width, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, 32f * resources.displayMetrics.density)
            }
        }
    }
    controls.background = null
    controls.elevation = 0f
    val glass = ComposeView(this).apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val settings = pdfChromeSettings
            val scheme = veritasReaderColorScheme(settings, activity)
            val requested = (VeritasThemePackCatalog.normalizePackId(settings.themePackId) == "liquid_glass" || settings.glassFloatingControls) &&
                !settings.reduceTransparency && !settings.themeId.contains("high_contrast")
            MaterialTheme(colorScheme = scheme) {
                val enabled = requested && isDeviceGlassCapable()
                SideEffect {
                    frame.background = if (enabled) null else if (settings.reduceTransparency || settings.themeId.contains("high_contrast"))
                        android.graphics.drawable.ColorDrawable(colorSurface) else fallback
                    frame.elevation = if (enabled) 0f else (if (isLightTheme) 8f else 10f) * resources.displayMetrics.density
                }
                if (enabled) NativePdfPlayerGlass(requireNotNull(fragmentContainer)) { pdfPaperLayerPaint }
            }
        }
    }
    // Height comes from native controls; the background must not size a wrap-content panel.
    frame.addView(glass, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, 0))
    frame.addView(controls, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
    controls.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
        if (bottom - top != oldBottom - oldTop || glass.layoutParams.height != bottom - top) {
            glass.layoutParams = glass.layoutParams.apply { height = bottom - top }
        }
    }
    return frame
}

@Composable
private fun VeritasPdfViewerActivity.NativePdfPlayerGlass(source: View, paperPaint: () -> Paint?) {
    val host = LocalView.current
    val backdrop = rememberVeritasLayerBackdrop()
    var drawRevision by remember { mutableIntStateOf(0) }
    val sourceLocation = remember { IntArray(2) }
    val hostLocation = remember { IntArray(2) }
    val visibleRect = remember { Rect() }
    LaunchedEffect(source, host) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val visible = host.isShown && host.getGlobalVisibleRect(visibleRect)
                if (visible) drawRevision++
                // Capture only while chrome is visible; no screenshots or bitmap allocations.
                delay(if (visible) 50L else 250L)
            }
        }
    }
    VeritasBackdropProvider(backdrop) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize().recordVeritasBackdrop(backdrop, true)) {
                drawRevision // Invalidates drawing without recomposing the native controls.
                source.getLocationOnScreen(sourceLocation)
                host.getLocationOnScreen(hostLocation)
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    val saved = native.saveLayer(0f, 0f, size.width, size.height, paperPaint())
                    native.translate((sourceLocation[0] - hostLocation[0]).toFloat(), (sourceLocation[1] - hostLocation[1]).toFloat())
                    source.draw(native) // Source is the PDF fragment only: chrome cannot capture itself.
                    native.restoreToCount(saved)
                }
            }
            Box(Modifier.fillMaxSize().veritasGlassBackdrop(RoundedCornerShape(32.dp), true, surfaceOpacity = .36f))
        }
    }
}
