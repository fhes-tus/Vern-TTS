package com.veritas.reader

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight

internal val LocalVeritasBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
internal fun isDeviceGlassCapable(): Boolean {
    val view = LocalView.current
    if (view.isInEditMode) return false
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    if (!view.isHardwareAccelerated) return false
    val context = LocalContext.current
    val am = remember(context) {
        runCatching { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }.getOrNull()
    }
    if (am?.isLowRamDevice == true) return false
    return true
}

@Composable
internal fun rememberVeritasLayerBackdrop(): LayerBackdrop =
    rememberLayerBackdrop {
        drawContent()
    }

@Composable
internal fun VeritasBackdropProvider(
    backdrop: Backdrop?,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalVeritasBackdrop provides backdrop, content = content)
}

internal fun Modifier.recordVeritasBackdrop(
    backdrop: LayerBackdrop?,
    enabled: Boolean
): Modifier {
    if (!enabled || backdrop == null) return this
    return this.layerBackdrop(backdrop)
}

/**
 * Refracts the captured page behind chrome using the LastWave glass material:
 * modest blur, a rounded lens and a narrow edge highlight. Text/icons stay sharp.
 */
internal fun Modifier.veritasGlassBackdrop(
    shape: Shape,
    enabled: Boolean,
    blurRadiusDp: Float = 10f,
    surfaceOpacity: Float = 0.32f,
): Modifier = composed {
    val backdrop = LocalVeritasBackdrop.current
    val capable = isDeviceGlassCapable()
    if (!enabled || !capable || backdrop == null) {
        return@composed this
    }

    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val isDark = scheme.surface.luminance() < .5f
    val isAmoled = isDark && (VeritasThemeState.amoledMode ||
        VeritasThemeCatalog.normalizeThemeId(VeritasThemeState.themeId) == "amoled")
    val material = if (isAmoled) Color(0xFF0D1117) else scheme.surface
    val density = LocalDensity.current
    val blurPx = with(density) { (blurRadiusDp * .8f).dp.toPx() }

    val cornerShape = shape as? CornerBasedShape
    val safeBackdropShape: Shape = cornerShape ?: RoundedCornerShape(8.dp)

    val effects: BackdropEffectScope.() -> Unit = {
        vibrancy()
        colorControls(brightness = .05f, contrast = 1f, saturation = 1.5f)
        blur(blurPx)
        // com.kyant.backdrop lens shader strictly requires a CornerBasedShape or
        // RoundedRectangularShape to compute signed distance fields (SDF).
        // Only apply lens if the shape is corner-based, and catch any runtime SDF exceptions.
        if (cornerShape != null) {
            val radius = minOf(size.minDimension / 2f,
                maxOf(cornerShape.topStart.toPx(size, density), cornerShape.topEnd.toPx(size, density),
                    cornerShape.bottomStart.toPx(size, density), cornerShape.bottomEnd.toPx(size, density)))
            if (radius > 0f) {
                runCatching {
                    lens(radius / 2f, radius, false)
                }
            }
        }
    }
    val surface: DrawScope.() -> Unit = {
        drawRect(material.copy(alpha = (surfaceOpacity * .78f).coerceIn(.18f, .38f)))
    }
    this.drawBackdrop(
        backdrop = backdrop, shape = { safeBackdropShape }, highlight = { Highlight(width = 1.dp) },
        effects = effects, onDrawSurface = surface
    )
}

/** A uniform outline defines the glass edge without a glossy highlight. */
internal fun Modifier.veritasGlassOutline(shape: Shape, enabled: Boolean): Modifier = composed {
    if (!enabled) return@composed this
    val edge = androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)
    this.clip(shape).drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawWithContent {
            drawContent()
            drawOutline(outline, edge, style = Stroke(1.dp.toPx()))
        }
    }
}
