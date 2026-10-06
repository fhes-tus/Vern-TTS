package com.veritas.reader

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy

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
 * Blurs the captured page behind chrome. The diffuse reflection stays clipped to
 * the chrome outline; text and icons are drawn afterwards and remain sharp.
 */
internal fun Modifier.veritasGlassBackdrop(
    shape: Shape,
    enabled: Boolean,
    blurRadiusDp: Float = 22f,
    surfaceOpacity: Float = 0.76f,
): Modifier = composed {
    val backdrop = LocalVeritasBackdrop.current
    val capable = isDeviceGlassCapable()
    if (!enabled || !capable || backdrop == null) {
        return@composed this
    }

    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val isDark = VeritasThemeCatalog.isDark(VeritasThemeCatalog.resolveConcreteThemeId(VeritasThemeState.themeId, isSystemInDarkTheme()))
    val isAmoled = isDark && (VeritasThemeState.amoledMode ||
        VeritasThemeCatalog.normalizeThemeId(VeritasThemeState.themeId) == "amoled")
    val material = if (isAmoled) Color(0xFF0D1117) else scheme.surface
    val density = LocalDensity.current
    val blurPx = with(density) { blurRadiusDp.dp.toPx() }

    this.drawPlainBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            colorControls(
                brightness = if (isDark) 0.01f else 0.025f,
                contrast = 1.04f,
                saturation = 1.06f,
            )
            blur(blurPx)
        },
        onDrawSurface = {
            // Chrome opacity applies to the material only; foreground text remains opaque.
            drawRect(material.copy(alpha = surfaceOpacity.coerceIn(0f, 1f)))
            // Continuous reflection avoids hard refraction bands when a wide
            // capsule animates down to a small circle.
            drawRect(Brush.linearGradient(
                0f to Color.White.copy(alpha = if (isDark) .14f else .20f),
                .24f to Color.White.copy(alpha = if (isDark) .045f else .065f),
                .52f to Color.White.copy(alpha = .003f),
                1f to Color.White.copy(alpha = if (isDark) .065f else .09f),
                start = Offset.Zero, end = Offset(size.width, size.height)
            ))
            // Broad corner glints give the surface depth without a solid white band.
            drawRect(Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = if (isDark) .065f else .09f), Color.Transparent),
                center = Offset(size.width * .12f, -size.height * .3f),
                radius = (size.height * 2.2f).coerceAtLeast(1f)
            ))
        }
    )
}
