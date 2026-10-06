package com.veritas.reader


import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat


internal val LocalVeritasPackId = staticCompositionLocalOf { "vern_media" }

object VeritasPackStyle {
    /** Shared layout identity used by real screens as well as the pack previews. */
    fun navigationHeight(packId: String, showLabels: Boolean): androidx.compose.ui.unit.Dp {
        return if (showLabels) 70.dp else 66.dp
    }

    @Composable
    fun navigationHeight(showLabels: Boolean = true): androidx.compose.ui.unit.Dp =
        navigationHeight(currentPackId(), showLabels)

    fun cardInset(packId: String): androidx.compose.ui.unit.Dp = when (VeritasThemePackCatalog.normalizePackId(packId)) {
        "one_ui" -> 18.dp
        "material_you" -> 16.dp
        "liquid_glass" -> 14.dp
        else -> 12.dp
    }

    fun playerHeight(packId: String): androidx.compose.ui.unit.Dp = when (VeritasThemePackCatalog.normalizePackId(packId)) {
        "one_ui" -> 80.dp
        "material_you" -> 76.dp
        "liquid_glass" -> 74.dp
        else -> 70.dp
    }

    fun chromeElevation(packId: String): androidx.compose.ui.unit.Dp = when (VeritasThemePackCatalog.normalizePackId(packId)) {
        "liquid_glass" -> 6.dp
        "one_ui" -> 5.dp
        "material_you" -> 3.dp
        else -> 1.dp
    }

    @Composable
    fun navigationBrush(scheme: ColorScheme): Brush {
        val opacity = if (VeritasThemeState.reduceTransparency || VeritasThemeState.themeId.contains("high_contrast")) 1f else chromeOpacity(currentPackId())
        val isDark = scheme.background.luminance() < 0.3f
        val highContrastBlack = VeritasThemeState.themeId.contains("high_contrast") || VeritasThemeCatalog.normalizeThemeId(VeritasThemeState.themeId) == "amoled"
        if (isDark && (VeritasThemeState.amoledMode || highContrastBlack)) return SolidColor(Color.Black.copy(alpha = opacity))
        if (isDark) {
            val top = blendColors(scheme.surface, scheme.primary, 0.035f)
            val bottom = blendColors(scheme.surface, scheme.background, 0.32f)
            return Brush.verticalGradient(listOf(top.copy(alpha = opacity), bottom.copy(alpha = opacity)))
        }
        return when (currentPackId()) {
            "material_you" -> Brush.verticalGradient(listOf(scheme.secondaryContainer.copy(alpha = opacity), scheme.surface.copy(alpha = opacity)))
            "one_ui" -> Brush.verticalGradient(listOf(scheme.surfaceContainerHigh.copy(alpha = opacity), scheme.surface.copy(alpha = opacity)))
            "liquid_glass" -> Brush.verticalGradient(listOf(scheme.surface.copy(alpha = opacity), blendColors(scheme.surface, scheme.primary, 0.04f).copy(alpha = opacity), scheme.surface.copy(alpha = opacity)))
            else -> SolidColor(scheme.surface.copy(alpha = opacity))
        }
    }

    @Composable
    fun navigationBorderBrush(scheme: ColorScheme): Brush {
        if (glassChromeEnabled()) {
            val dark = scheme.background.luminance() < 0.3f
            return Brush.verticalGradient(
                if (dark) listOf(
                    Color.White.copy(alpha = 0.48f),
                    Color.White.copy(alpha = 0.12f),
                    scheme.primary.copy(alpha = 0.20f),
                    Color.White.copy(alpha = 0.22f)
                ) else listOf(
                    Color.White.copy(alpha = 0.82f),
                    scheme.primary.copy(alpha = 0.16f),
                    Color.White.copy(alpha = 0.34f)
                )
            )
        }
        return when (currentPackId()) {
            "material_you" -> SolidColor(scheme.outlineVariant.copy(alpha = 0.55f))
            "one_ui" -> SolidColor(scheme.primary.copy(alpha = 0.28f))
            else -> SolidColor(scheme.outlineVariant.copy(alpha = 0.35f))
        }
    }

    @Composable
    fun playerSurfaceColor(scheme: ColorScheme): Color = if (VeritasThemeState.reduceTransparency || VeritasThemeState.themeId.contains("high_contrast")) scheme.surface else when (currentPackId()) {
        "material_you" -> scheme.tertiaryContainer.copy(alpha = chromeOpacity(currentPackId()))
        "one_ui" -> scheme.surfaceContainerHigh.copy(alpha = chromeOpacity(currentPackId()))
        "liquid_glass" -> scheme.surface.copy(alpha = playerOpacity(currentPackId()))
        else -> scheme.surface.copy(alpha = chromeOpacity(currentPackId()))
    }

    fun chromeOpacity(packId: String): Float = when (VeritasThemePackCatalog.normalizePackId(packId)) {
        "one_ui" -> 0.90f
        "liquid_glass" -> 0.76f
        "material_you" -> 0.85f
        else -> 0.90f
    }

    fun playerOpacity(packId: String): Float =
        if (VeritasThemePackCatalog.normalizePackId(packId) == "liquid_glass") .68f else chromeOpacity(packId)

    /** AMOLED keeps the page black; only accessibility choices disable chrome blur. */
    @Composable
    fun glassChromeEnabled(): Boolean = (currentPackId() == "liquid_glass" || VeritasThemeState.glassFloatingControls) &&
        !VeritasThemeState.reduceTransparency && !VeritasThemeState.themeId.contains("high_contrast")

    @Composable
    fun filterContainerColor(scheme: ColorScheme): Color =
        if (currentPackId() == "material_you") scheme.tertiaryContainer else scheme.primary

    @Composable
    fun onFilterContainerColor(scheme: ColorScheme): Color =
        if (currentPackId() == "material_you") scheme.onTertiaryContainer else scheme.onPrimary

    @Composable
    fun filterChipColors(scheme: ColorScheme): androidx.compose.material3.SelectableChipColors =
        if (currentPackId() == "material_you") androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = scheme.tertiaryContainer,
            selectedLabelColor = scheme.onTertiaryContainer,
            selectedLeadingIconColor = scheme.onTertiaryContainer
        ) else androidx.compose.material3.FilterChipDefaults.filterChipColors()

    @Composable
    fun currentPackId(): String = VeritasThemePackCatalog.normalizePackId(LocalVeritasPackId.current)

    @Composable
    fun cardShape(): RoundedCornerShape = when (currentPackId()) {
        "material_you" -> RoundedCornerShape(34.dp)
        "liquid_glass" -> RoundedCornerShape(34.dp)
        "one_ui" -> RoundedCornerShape(28.dp)
        else -> RoundedCornerShape(18.dp)
    }

    @Composable
    fun sheetShape(): RoundedCornerShape {
        val radius = navigationCornerRadius()
        return RoundedCornerShape(topStart = radius, topEnd = radius)
    }

    fun headerColorForPack(scheme: ColorScheme, packId: String): Color = when (VeritasThemePackCatalog.normalizePackId(packId)) {
        "material_you" -> scheme.primaryContainer
        "one_ui" -> scheme.background
        "liquid_glass" -> scheme.surfaceContainerLow
        else -> scheme.surface
    }

    @Composable
    fun headerColor(scheme: ColorScheme): Color = headerColorForPack(scheme, currentPackId())

    @Composable
    fun panelColor(scheme: ColorScheme): Color = when (currentPackId()) {
        "material_you" -> scheme.surfaceContainer
        "one_ui" -> scheme.surfaceContainer
        "liquid_glass" -> scheme.surfaceContainerLow.copy(alpha = surfaceAlpha())
        else -> scheme.surface
    }

    @Composable
    fun coverStageShape(inset: androidx.compose.ui.unit.Dp = 8.dp, bottomRadius: androidx.compose.ui.unit.Dp = 10.dp): RoundedCornerShape {
        val outerRadius = when (currentPackId()) {
            "material_you", "liquid_glass" -> 34.dp
            "one_ui" -> 28.dp
            else -> 18.dp
        }
        val topRadius = (outerRadius - inset).coerceAtLeast(8.dp)
        return RoundedCornerShape(
            topStart = topRadius,
            topEnd = topRadius,
            bottomStart = bottomRadius,
            bottomEnd = bottomRadius
        )
    }

    @Composable
    fun compactShape(): RoundedCornerShape = when (currentPackId()) {
        "material_you" -> RoundedCornerShape(28.dp)
        "liquid_glass" -> RoundedCornerShape(28.dp)
        "one_ui" -> RoundedCornerShape(18.dp)
        else -> RoundedCornerShape(12.dp)
    }

    @Composable
    fun chipShape(): RoundedCornerShape = when (currentPackId()) {
        "material_you" -> RoundedCornerShape(50)
        "liquid_glass" -> RoundedCornerShape(50)
        "one_ui" -> RoundedCornerShape(16.dp)
        else -> RoundedCornerShape(10.dp)
    }

    @Composable
    fun surfaceAlpha(): Float {
        val scheme = MaterialTheme.colorScheme
        val isDark = scheme.background.luminance() < 0.3f
        val highContrast = VeritasThemeState.themeId.contains("high_contrast")
        if (VeritasThemeState.reduceTransparency || highContrast || (isDark && (VeritasThemeState.amoledMode || VeritasThemeCatalog.normalizeThemeId(VeritasThemeState.themeId) == "amoled"))) return 1.0f
        val pack = currentPackId()
        if (pack == "liquid_glass") return if (isDark) 0.85f else 0.92f
        if (!isDark) return 1.0f
        return when (pack) {
            "one_ui" -> 0.96f
            "material_you" -> 0.92f
            else -> 1.0f
        }
    }

    @Composable
    fun navigationCornerRadius(): androidx.compose.ui.unit.Dp = when (currentPackId()) {
        "material_you", "liquid_glass" -> 34.dp
        "one_ui" -> 28.dp
        else -> 18.dp
    }

    @Composable
    fun bottomNavShape(): RoundedCornerShape {
        val radius = navigationCornerRadius()
        return RoundedCornerShape(topStart = radius, topEnd = radius, bottomStart = 0.dp, bottomEnd = 0.dp)
    }

    @Composable
    fun bottomNavPadding(): androidx.compose.ui.unit.Dp = 0.dp

    @Composable
    fun cardBorder(colorScheme: ColorScheme): BorderStroke = if (VeritasThemeState.themeId.contains("high_contrast"))
        BorderStroke(1.dp, colorScheme.outline) else when (currentPackId()) {
        "liquid_glass" -> {
            val dark = colorScheme.background.luminance() < 0.3f
            BorderStroke(
                1.dp,
                Brush.verticalGradient(
                    if (dark) listOf(
                        Color.White.copy(alpha = 0.44f),
                        Color.White.copy(alpha = 0.10f),
                        colorScheme.primary.copy(alpha = 0.17f),
                        Color.White.copy(alpha = 0.20f)
                    ) else listOf(
                        Color.White.copy(alpha = 0.76f),
                        colorScheme.primary.copy(alpha = 0.14f),
                        Color.White.copy(alpha = 0.32f)
                    )
                )
            )
        }
        "material_you", "one_ui" -> BorderStroke(0.dp, Color.Transparent)
        else -> BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f))
    }

    @Composable
    fun backgroundBrush(colorScheme: ColorScheme): Brush {
        val dark = colorScheme.background.luminance() < 0.3f
        val highContrastBlack = VeritasThemeState.themeId.contains("high_contrast") || VeritasThemeCatalog.normalizeThemeId(VeritasThemeState.themeId) == "amoled"
        if (dark && (VeritasThemeState.amoledMode || highContrastBlack)) {
            return SolidColor(Color.Black)
        }
        if (dark) {
            val base = colorScheme.background
            val shaded = blendColors(base, colorScheme.surface, 0.24f)
            val themeTint = blendColors(base, colorScheme.primary, 0.045f)
            return Brush.verticalGradient(listOf(base, shaded, themeTint, base))
        }
        return when (currentPackId()) {
        "liquid_glass" -> Brush.verticalGradient(
            listOf(
                colorScheme.background,
                blendColors(colorScheme.background, colorScheme.primaryContainer, 0.14f),
                colorScheme.background
            )
        )

        "one_ui" -> Brush.verticalGradient(
            listOf(
                blendColors(colorScheme.background, colorScheme.secondaryContainer, 0.20f),
                colorScheme.background,
                colorScheme.background,
                blendColors(colorScheme.background, colorScheme.surfaceVariant, 0.72f)
            )
        )

        "material_you" -> Brush.verticalGradient(
            listOf(
                colorScheme.background,
                blendColors(colorScheme.background, colorScheme.surfaceVariant, 0.76f),
                blendColors(colorScheme.background, colorScheme.primaryContainer, 0.22f),
                blendColors(colorScheme.background, colorScheme.tertiaryContainer, 0.16f),
                colorScheme.background
            )
        )

        else -> Brush.verticalGradient(
            listOf(
                colorScheme.background,
                blendColors(colorScheme.background, colorScheme.primaryContainer, 0.08f),
                colorScheme.background,
                blendColors(colorScheme.background, colorScheme.secondaryContainer, 0.10f)
            )
        )
        }
    }

    @Composable
    fun label(): String = VeritasThemePackCatalog.displayName(currentPackId())
}


fun packPreviewSymbols(packId: String): String =
    when (VeritasThemePackCatalog.normalizePackId(packId)) {
        "material_you" -> "rounded â€¢ adaptive â€¢ soft"
        "liquid_glass" -> "frosted / translucent / floating"
        "one_ui" -> "large â€¢ reachable â€¢ calm"
        else -> "media â€¢ compact â€¢ bold"
    }


// Swatches are the theme's REAL scheme values â€” [background, primary, key accent] â€”
// so what the picker shows is exactly what the app paints once the theme is applied.
fun themePreviewColors(themeId: String): List<Color> {
    return when (VeritasThemeCatalog.normalizeThemeId(themeId)) {
        "light" -> listOf(Color(0xFFFAF7F2), Color(0xFFC07318), Color(0xFF52695C))
        "neon" -> listOf(Color(0xFF08090C), Color(0xFF00E5FF), Color(0xFF00E676))
        "solarized_dark" -> listOf(Color(0xFF002B36), Color(0xFF2AA198), Color(0xFFB58900))
        "tomorrow_night_blue" -> listOf(Color(0xFF002451), Color(0xFFBBDAFF), Color(0xFFFFC58F))
        "dark_high_contrast" -> listOf(Color.Black, Color.White, Color(0xFFFFD400))
        "white_high_contrast" -> listOf(Color.White, Color.Black, Color(0xFF004B65))
        "amoled" -> listOf(Color.Black, Color(0xFF90CAF9), Color(0xFF80CBC4))
        "bw_gradient_light" -> listOf(Color(0xFFFAFAFA), Color(0xFF111111), Color(0xFF707070))
        "bw_gradient_dark" -> listOf(Color(0xFF050505), Color(0xFFF2F2F2), Color(0xFF9A9A9A))
        "bw_gradient_system" -> listOf(Color(0xFFFAFAFA), Color(0xFF050505), Color(0xFF707070))
        "github_light", "one_light" -> listOf(Color(0xFFFAFAFA), Color(0xFF4078F2), Color(0xFF50A14F))
        "github_dark", "one_dark_pro" -> listOf(Color(0xFF0D1117), Color(0xFF58A6FF), Color(0xFF3FB950))
        "github_system" -> listOf(Color(0xFFFAFAFA), Color(0xFF0D1117), Color(0xFF58A6FF))
        "dracula_light" -> listOf(Color(0xFFF8F7FA), Color(0xFF7D4EBA), Color(0xFFC72C76))
        "dracula" -> listOf(Color(0xFF282A36), Color(0xFFBD93F9), Color(0xFFFF79C6))
        "dracula_system" -> listOf(Color(0xFFF8F7FA), Color(0xFF282A36), Color(0xFFBD93F9))
        "material_you" -> listOf(Color(0xFFFFFBFE), Color(0xFF6750A4), Color(0xFF7D5260))
        "midnight_light" -> listOf(Color(0xFFF8FAFC), Color(0xFF4338CA), Color(0xFF0E7490))
        "midnight_dark" -> listOf(Color(0xFF0F172A), Color(0xFFA79BFF), Color(0xFF9BD8E0))
        "midnight_system" -> listOf(Color(0xFFF8FAFC), Color(0xFF0F172A), Color(0xFFA79BFF))
        "dark" -> listOf(Color(0xFF151515), Color(0xFFE5A93C), Color(0xFF81B29A))
        "system" -> listOf(Color(0xFFFAF7F2), Color(0xFF151515), Color(0xFFC07318))
        else -> listOf(Color(0xFF151515), Color(0xFFE5A93C), Color(0xFF81B29A))
    }
}


@Composable
fun AnnotationPill(label: String) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AnnotationPill(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String) {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
    }
}


internal fun blendColors(color1: Color, color2: Color, ratio: Float): Color {
    val r = color1.red * (1f - ratio) + color2.red * ratio
    val g = color1.green * (1f - ratio) + color2.green * ratio
    val b = color1.blue * (1f - ratio) + color2.blue * ratio
    return Color(r, g, b, 1f)
}

internal fun adaptColorScheme(base: ColorScheme, palette: androidx.palette.graphics.Palette, isLight: Boolean): ColorScheme {
    val dominantColor = Color(palette.getDominantColor(base.primary.toArgb()))
    val vibrantColor = Color(palette.getVibrantColor(base.primary.toArgb()))
    Color(palette.getDarkVibrantColor(base.primary.toArgb()))
    val lightVibrant = Color(palette.getLightVibrantColor(base.primary.toArgb()))
    val muted = Color(palette.getMutedColor(base.secondary.toArgb()))

    return if (!isLight) {
        val bgTint = blendColors(dominantColor, base.background, 0.88f)
        val surfTint = blendColors(dominantColor, base.surface, 0.88f)
        val surfVarTint = blendColors(dominantColor, base.surfaceVariant, 0.88f)
        
        base.copy(
            primary = if (vibrantColor != base.primary) vibrantColor else lightVibrant,
            primaryContainer = blendColors(dominantColor, base.primaryContainer, 0.7f),
            secondary = if (muted != base.secondary) muted else dominantColor,
            secondaryContainer = blendColors(dominantColor, base.secondaryContainer, 0.8f),
            background = bgTint,
            surface = surfTint,
            surfaceVariant = surfVarTint
        )
    } else {
        val bgTint = blendColors(dominantColor, base.background, 0.93f)
        val surfTint = blendColors(dominantColor, base.surface, 0.95f)
        val surfVarTint = blendColors(dominantColor, base.surfaceVariant, 0.93f)
        
        base.copy(
            primary = if (vibrantColor != base.primary) vibrantColor else dominantColor,
            primaryContainer = blendColors(dominantColor, base.primaryContainer, 0.85f),
            secondary = if (muted != base.secondary) muted else dominantColor,
            secondaryContainer = blendColors(dominantColor, base.secondaryContainer, 0.9f),
            background = bgTint,
            surface = surfTint,
            surfaceVariant = surfVarTint
        )
    }
}

@Composable
internal fun VeritasTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val selectedTheme = VeritasThemeCatalog.normalizeThemeId(VeritasThemeState.themeId)
    val selectedPack = VeritasThemePackCatalog.normalizePackId(VeritasThemeState.themePackId)
    val reduceMotion = VeritasThemeState.reduceMotion
    
    val resolvedTheme = VeritasThemeCatalog.resolveConcreteThemeId(
        themeId = selectedTheme,
        systemInDarkTheme = isSystemInDarkTheme()
    )

    val isLight = !VeritasThemeCatalog.isDark(resolvedTheme)

    val activeDocId = VeritasThemeState.activeDocumentId
    val adaptiveCover = VeritasThemeState.adaptiveCover
    var coverPalette by remember(activeDocId) { mutableStateOf<androidx.palette.graphics.Palette?>(null) }
    
    LaunchedEffect(activeDocId, adaptiveCover) {
        if (adaptiveCover && activeDocId != null) {
            val palette = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val file = CoverExtractor.coverFile(context, activeDocId)
                if (file != null && file.exists()) {
                    runCatching {
                        val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                        if (bmp != null) androidx.palette.graphics.Palette.from(bmp).generate() else null
                    }.getOrNull()
                } else null
            }
            coverPalette = palette
        } else {
            coverPalette = null
        }
    }

    val baseColorScheme = veritasColorScheme(resolvedTheme, context)
    val palette = coverPalette
    
    val blendedColorScheme = if (adaptiveCover && palette != null) {
        adaptColorScheme(baseColorScheme, palette, isLight)
    } else {
        baseColorScheme
    }

    val packColorScheme = veritasPackColorScheme(
        base = blendedColorScheme,
        packId = selectedPack,
        themeId = resolvedTheme
    )

    val isAmoled = !isLight && (VeritasThemeState.amoledMode || resolvedTheme == "amoled")
    val colorScheme = if (isAmoled) veritasAmoledColorScheme(packColorScheme) else packColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val statusBarColor = VeritasPackStyle.headerColorForPack(colorScheme, selectedPack)
                val navBarColor = if (isAmoled) Color.Black else if (!isLight) colorScheme.background else colorScheme.surface
                @Suppress("DEPRECATION")
                window.statusBarColor = statusBarColor.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = navBarColor.toArgb()
                val isLightStatusBar = statusBarColor.luminance() > 0.45f
                val isLightNavBar = navBarColor.luminance() > 0.45f
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = isLightStatusBar
                insetsController.isAppearanceLightNavigationBars = isLightNavBar
            }
        }
    }

    val uiFont = com.veritas.reader.ui.VeritasUiFont.fromId(VeritasThemeState.uiFontId)
    androidx.compose.runtime.CompositionLocalProvider(LocalVeritasPackId provides selectedPack) {
        androidx.compose.runtime.CompositionLocalProvider(
            com.veritas.reader.ui.LocalVeritasMotion provides
                remember(reduceMotion) { com.veritas.reader.ui.VeritasMotionScheme(reduceMotion) }
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = remember(uiFont, selectedPack) {
                    veritasPackTypography(com.veritas.reader.ui.veritasTypography(uiFont), selectedPack)
                },
                shapes = veritasPackShapes(selectedPack),
                content = content
            )
        }
    }
}

internal fun veritasPackTypography(base: androidx.compose.material3.Typography, packId: String): androidx.compose.material3.Typography {
    val headingWeight = when (VeritasThemePackCatalog.normalizePackId(packId)) {
        "one_ui" -> FontWeight.Medium
        "material_you" -> FontWeight.Normal
        "liquid_glass" -> FontWeight.SemiBold
        else -> FontWeight.Bold
    }
    val labelWeight = when (VeritasThemePackCatalog.normalizePackId(packId)) {
        "one_ui" -> FontWeight.Medium
        "material_you" -> FontWeight.Medium
        else -> FontWeight.SemiBold
    }
    val headingScale = if (VeritasThemePackCatalog.normalizePackId(packId) == "one_ui") 1.08f else 1f
    return base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = headingWeight, fontSize = base.headlineLarge.fontSize * headingScale, lineHeight = base.headlineLarge.lineHeight * headingScale),
        headlineMedium = base.headlineMedium.copy(fontWeight = headingWeight, fontSize = base.headlineMedium.fontSize * headingScale, lineHeight = base.headlineMedium.lineHeight * headingScale),
        headlineSmall = base.headlineSmall.copy(fontWeight = headingWeight, fontSize = base.headlineSmall.fontSize * headingScale, lineHeight = base.headlineSmall.lineHeight * headingScale),
        titleLarge = base.titleLarge.copy(fontWeight = headingWeight, fontSize = base.titleLarge.fontSize * headingScale, lineHeight = base.titleLarge.lineHeight * headingScale),
        titleMedium = base.titleMedium.copy(fontWeight = labelWeight),
        labelLarge = base.labelLarge.copy(fontWeight = labelWeight)
    )
}

internal fun veritasPackShapes(packId: String): Shapes {
    return when (VeritasThemePackCatalog.normalizePackId(packId)) {
        "material_you" -> Shapes(
            extraSmall = RoundedCornerShape(14.dp),
            small = RoundedCornerShape(20.dp),
            medium = RoundedCornerShape(28.dp),
            large = RoundedCornerShape(34.dp),
            extraLarge = RoundedCornerShape(44.dp)
        )

        "liquid_glass" -> Shapes(
            extraSmall = RoundedCornerShape(14.dp),
            small = RoundedCornerShape(20.dp),
            medium = RoundedCornerShape(28.dp),
            large = RoundedCornerShape(34.dp),
            extraLarge = RoundedCornerShape(44.dp)
        )

        "one_ui" -> Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(22.dp),
            large = RoundedCornerShape(28.dp),
            extraLarge = RoundedCornerShape(38.dp)
        )

        else -> Shapes(
            extraSmall = RoundedCornerShape(6.dp),
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(14.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(26.dp)
        )
    }
}

internal fun veritasAmoledColorScheme(base: ColorScheme): ColorScheme = base.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color(0xFF101216),
    surfaceContainerHigh = Color(0xFF181B20),
    surfaceContainerHighest = Color(0xFF22262D),
    surfaceVariant = Color(0xFF1C1F26)
)

internal fun veritasPackColorScheme(base: ColorScheme, packId: String, themeId: String = ""): ColorScheme {
    val pack = VeritasThemePackCatalog.normalizePackId(packId)
    // Keep color identity and accessibility schemes separate from the component material.
    if (themeId.contains("high_contrast") || themeId == "amoled") return base
    val dark = base.background.luminance() < 0.3f
    return when (pack) {
        "material_you" -> {
            // Keep three expressive tonal roles, with a softer tertiary tint for
            // playback and filters. Wallpaper palettes retain their generated tertiary.
            val third = if (themeId.isBlank() || themeId == "material_you") base.tertiary
                else themePreviewColors(themeId).last()
            val primaryContainer = blendColors(base.surface, base.primary, if (dark) .24f else .22f)
            val secondaryContainer = blendColors(base.surface, base.secondary, if (dark) .22f else .18f)
            val tertiaryContainer = blendColors(base.surface, third, if (dark) .14f else .12f)
            base.copy(
                primaryContainer = primaryContainer, onPrimaryContainer = base.onSurface,
                secondaryContainer = secondaryContainer, onSecondaryContainer = base.onSurface,
                tertiary = third, tertiaryContainer = tertiaryContainer, onTertiaryContainer = base.onSurface,
                surfaceContainerLowest = base.surface,
                surfaceContainerLow = blendColors(base.surface, base.primary, if (dark) .045f else .055f),
                surfaceContainer = blendColors(base.surface, base.secondary, if (dark) .065f else .08f),
                surfaceContainerHigh = blendColors(base.surface, base.primary, if (dark) .11f else .14f),
                surfaceContainerHighest = blendColors(base.surface, third, if (dark) .13f else .16f)
            )
        }
        "one_ui" -> base.copy(
            surfaceContainerLowest = base.background,
            surfaceContainerLow = blendColors(base.surface, base.background, .30f),
            surfaceContainer = base.surface,
            surfaceContainerHigh = blendColors(base.surface, base.onSurface, if (dark) .04f else .025f),
            surfaceContainerHighest = blendColors(base.surface, base.onSurface, if (dark) .07f else .045f)
        )
        // Glass transparency is controlled per surface, keeping text and dialogs opaque.
        else -> base
    }
}
