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


object VeritasPackStyle {
    @Composable
    fun currentPackId(): String =
        VeritasThemePackCatalog.normalizePackId(VeritasThemeState.themePackId)

    @Composable
    fun cardShape(): RoundedCornerShape = when (currentPackId()) {
        "material_you" -> RoundedCornerShape(34.dp)
        "liquid_glass" -> RoundedCornerShape(42.dp)
        "one_ui" -> RoundedCornerShape(28.dp)
        else -> RoundedCornerShape(18.dp)
    }

    @Composable
    fun compactShape(): RoundedCornerShape = when (currentPackId()) {
        "material_you" -> RoundedCornerShape(28.dp)
        "liquid_glass" -> RoundedCornerShape(34.dp)
        "one_ui" -> RoundedCornerShape(18.dp)
        else -> RoundedCornerShape(12.dp)
    }

    @Composable
    fun chipShape(): RoundedCornerShape = when (currentPackId()) {
        "material_you" -> RoundedCornerShape(50)
        "liquid_glass" -> RoundedCornerShape(36.dp)
        "one_ui" -> RoundedCornerShape(16.dp)
        else -> RoundedCornerShape(10.dp)
    }

    @Composable
    fun surfaceAlpha(): Float {
        val isDark = VeritasThemeCatalog.isDark(VeritasThemeState.themeId, isSystemInDarkTheme())
        if (VeritasThemeState.amoledMode || VeritasThemeState.themeId == "amoled") return 1.0f
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
    fun bottomNavShape(): RoundedCornerShape = when (currentPackId()) {
        "material_you" -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
        "one_ui" -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
        else -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
    }

    @Composable
    fun bottomNavPadding(): androidx.compose.ui.unit.Dp = 0.dp

    // Liquid Glass & Liquid Material get a real glass edge: a catch-light that's brightest along the
    // top rim and fades to a faint primary tint â€” the cue that sells "glossy pane".
    // Other packs keep the standard hairline outline.
    @Composable
    fun cardBorder(colorScheme: ColorScheme): BorderStroke = when (currentPackId()) {
        "liquid_glass" -> BorderStroke(
            1.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = 0.60f),
                    Color.White.copy(alpha = 0.12f),
                    colorScheme.primary.copy(alpha = 0.25f)
                )
            )
        )
        else -> BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f))
    }

    @Composable
    fun backgroundBrush(colorScheme: ColorScheme): Brush {
        if (colorScheme.background == Color.Black) {
            return SolidColor(Color.Black)
        }
        return when (currentPackId()) {
        "liquid_glass" -> Brush.verticalGradient(
            listOf(
                colorScheme.background,
                colorScheme.primaryContainer.copy(alpha = 0.14f),
                colorScheme.background
            )
        )

        "one_ui" -> Brush.verticalGradient(
            listOf(
                colorScheme.secondaryContainer.copy(alpha = 0.20f),
                colorScheme.background,
                colorScheme.background,
                colorScheme.surfaceVariant.copy(alpha = 0.72f)
            )
        )

        "material_you" -> Brush.verticalGradient(
            listOf(
                colorScheme.background,
                colorScheme.surfaceVariant.copy(alpha = 0.76f),
                colorScheme.primaryContainer.copy(alpha = 0.22f),
                colorScheme.tertiaryContainer.copy(alpha = 0.16f),
                colorScheme.background
            )
        )

        else -> Brush.verticalGradient(
            listOf(
                colorScheme.background,
                colorScheme.primaryContainer.copy(alpha = 0.08f),
                colorScheme.background,
                colorScheme.secondaryContainer.copy(alpha = 0.10f)
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
        "liquid_glass" -> "translucent â€¢ floating â€¢ glossy"
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
        "blue_high_contrast" -> listOf(Color(0xFF001B3A), Color(0xFFBDE9FF), Color(0xFFFFF176))
        "one_dark_pro" -> listOf(Color(0xFF21252B), Color(0xFF61AFEF), Color(0xFF98C379))
        "github_dark" -> listOf(Color(0xFF0D1117), Color(0xFF58A6FF), Color(0xFF3FB950))
        "github_light" -> listOf(Color(0xFFFFFFFF), Color(0xFF0969DA), Color(0xFF1A7F37))
        "dracula" -> listOf(Color(0xFF282A36), Color(0xFFBD93F9), Color(0xFFFF79C6))
        "material_you" -> listOf(Color(0xFFFFFBFE), Color(0xFF6750A4), Color(0xFF7D5260))
        "midnight_dark" -> listOf(Color(0xFF0F172A), Color(0xFFA79BFF), Color(0xFF9BD8E0))
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
    val darkVibrant = Color(palette.getDarkVibrantColor(base.primary.toArgb()))
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
    
    val resolvedTheme = if (selectedTheme == "system") {
        if (isSystemInDarkTheme()) "dark" else "light"
    } else {
        selectedTheme
    }

    val isLight = when (resolvedTheme) {
        "light", "white_high_contrast", "bw_gradient_light", "github_light" -> true
        else -> false
    }

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
        packId = selectedPack
    )

    val isAmoled = !isLight && (VeritasThemeState.amoledMode || resolvedTheme == "amoled")
    val colorScheme = if (isAmoled) {
        packColorScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainer = Color(0xFF101216),
            surfaceContainerHigh = Color(0xFF181B20),
            surfaceContainerHighest = Color(0xFF22262D),
            surfaceVariant = Color(0xFF1C1F26)
        )
    } else {
        packColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val barColor = if (isAmoled) Color.Black else colorScheme.primaryContainer
                @Suppress("DEPRECATION")
                window.statusBarColor = barColor.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = barColor.toArgb()
                val isLightContainer = barColor.luminance() > 0.45f
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = isLightContainer
                insetsController.isAppearanceLightNavigationBars = isLightContainer
            }
        }
    }

    val uiFont = com.veritas.reader.ui.VeritasUiFont.fromId(VeritasThemeState.uiFontId)
    androidx.compose.runtime.CompositionLocalProvider(
        com.veritas.reader.ui.LocalVeritasMotion provides
            remember(reduceMotion) { com.veritas.reader.ui.VeritasMotionScheme(reduceMotion) }
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = remember(uiFont) { com.veritas.reader.ui.veritasTypography(uiFont) },
            shapes = veritasPackShapes(selectedPack),
            content = content
        )
    }
}

internal fun veritasPackShapes(packId: String): Shapes {
    return when (VeritasThemePackCatalog.normalizePackId(packId)) {
        "material_you" -> Shapes(
            extraSmall = RoundedCornerShape(14.dp),
            small = RoundedCornerShape(20.dp),
            medium = RoundedCornerShape(28.dp),
            large = RoundedCornerShape(36.dp),
            extraLarge = RoundedCornerShape(44.dp)
        )

        "liquid_glass" -> Shapes(
            extraSmall = RoundedCornerShape(18.dp),
            small = RoundedCornerShape(26.dp),
            medium = RoundedCornerShape(34.dp),
            large = RoundedCornerShape(44.dp),
            extraLarge = RoundedCornerShape(52.dp)
        )

        "one_ui" -> Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(22.dp),
            large = RoundedCornerShape(30.dp),
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

internal fun veritasPackColorScheme(base: ColorScheme, packId: String): ColorScheme {
    val isDark = base.background.luminance() < 0.3f
    return when (VeritasThemePackCatalog.normalizePackId(packId)) {
        "liquid_glass" -> base.copy(
            surface = base.surface.copy(alpha = if (isDark) 0.28f else 0.38f),
            surfaceVariant = base.surfaceVariant.copy(alpha = if (isDark) 0.20f else 0.28f),
            primaryContainer = base.primaryContainer.copy(alpha = if (isDark) 0.65f else 0.80f),
            secondaryContainer = base.secondaryContainer.copy(alpha = if (isDark) 0.55f else 0.70f),
            tertiaryContainer = base.tertiaryContainer.copy(alpha = if (isDark) 0.50f else 0.65f)
        )

        "one_ui" -> base.copy(
            surface = base.surface,
            surfaceVariant = base.secondaryContainer.copy(alpha = 0.94f),
            primaryContainer = base.primaryContainer.copy(alpha = 0.98f),
            tertiaryContainer = base.tertiaryContainer.copy(alpha = 0.90f)
        )

        "material_you" -> base.copy(
            surface = base.surface.copy(alpha = 0.90f),
            surfaceVariant = base.surfaceVariant.copy(alpha = 0.35f),
            primaryContainer = base.primaryContainer.copy(alpha = 0.50f),
            secondaryContainer = base.secondaryContainer.copy(alpha = 0.45f),
            tertiaryContainer = base.tertiaryContainer.copy(alpha = 0.40f)
        )

        else -> base
    }
}

