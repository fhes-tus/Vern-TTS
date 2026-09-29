package com.veritas.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.veritas.desktop.models.DesktopFontFamily
import com.veritas.desktop.models.DesktopThemeType

// Slate Dark Colors
val SlateDarkBackground = Color(0xFF0F172A)
val SlateDarkSurface = Color(0xFF1E293B)
val SlateDarkCard = Color(0xFF334155)
val SlateDarkPrimary = Color(0xFF38BDF8)
val SlateDarkOnPrimary = Color(0xFF0F172A)
val SlateDarkText = Color(0xFFF1F5F9)
val SlateDarkTextMuted = Color(0xFF94A3B8)
val SlateDarkBorder = Color(0xFF334155)
val SlateDarkHighlight = Color(0xFF0369A1)

// Light Air Colors
val LightAirBackground = Color(0xFFF8FAFC)
val LightAirSurface = Color(0xFFFFFFFF)
val LightAirCard = Color(0xFFF1F5F9)
val LightAirPrimary = Color(0xFF0284C7)
val LightAirOnPrimary = Color(0xFFFFFFFF)
val LightAirText = Color(0xFF0F172A)
val LightAirTextMuted = Color(0xFF64748B)
val LightAirBorder = Color(0xFFE2E8F0)
val LightAirHighlight = Color(0xFFBAE6FD)

// Warm Sepia Colors
val SepiaBackground = Color(0xFFF8F1E5)
val SepiaSurface = Color(0xFFEFE4D2)
val SepiaCard = Color(0xFFE5D7C2)
val SepiaPrimary = Color(0xFF96532B)
val SepiaOnPrimary = Color(0xFFFFFFFF)
val SepiaText = Color(0xFF3E2D22)
val SepiaTextMuted = Color(0xFF7D6855)
val SepiaBorder = Color(0xFFDCCBB5)
val SepiaHighlight = Color(0xFFFED7AA)

// Obsidian OLED Colors
val OledBackground = Color(0xFF000000)
val OledSurface = Color(0xFF0A0A0A)
val OledCard = Color(0xFF141414)
val OledPrimary = Color(0xFF38BDF8)
val OledOnPrimary = Color(0xFF000000)
val OledText = Color(0xFFFFFFFF)
val OledTextMuted = Color(0xFF888888)
val OledBorder = Color(0xFF222222)
val OledHighlight = Color(0xFF0C4A6E)

fun getComposeFontFamily(font: DesktopFontFamily): FontFamily {
    return when (font) {
        DesktopFontFamily.DEFAULT -> FontFamily.SansSerif
        DesktopFontFamily.ATKINSON -> FontFamily.SansSerif
        DesktopFontFamily.LITERATA -> FontFamily.Serif
        DesktopFontFamily.SERIF -> FontFamily.Serif
        DesktopFontFamily.MONOSPACE -> FontFamily.Monospace
    }
}

@Composable
fun VeritasDesktopTheme(
    themeType: DesktopThemeType = DesktopThemeType.SLATE_DARK,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeType) {
        DesktopThemeType.SLATE_DARK -> darkColorScheme(
            primary = SlateDarkPrimary,
            onPrimary = SlateDarkOnPrimary,
            background = SlateDarkBackground,
            surface = SlateDarkSurface,
            surfaceVariant = SlateDarkCard,
            onBackground = SlateDarkText,
            onSurface = SlateDarkText,
            onSurfaceVariant = SlateDarkTextMuted,
            outline = SlateDarkBorder
        )
        DesktopThemeType.LIGHT_AIR -> lightColorScheme(
            primary = LightAirPrimary,
            onPrimary = LightAirOnPrimary,
            background = LightAirBackground,
            surface = LightAirSurface,
            surfaceVariant = LightAirCard,
            onBackground = LightAirText,
            onSurface = LightAirText,
            onSurfaceVariant = LightAirTextMuted,
            outline = LightAirBorder
        )
        DesktopThemeType.WARM_SEPIA -> lightColorScheme(
            primary = SepiaPrimary,
            onPrimary = SepiaOnPrimary,
            background = SepiaBackground,
            surface = SepiaSurface,
            surfaceVariant = SepiaCard,
            onBackground = SepiaText,
            onSurface = SepiaText,
            onSurfaceVariant = SepiaTextMuted,
            outline = SepiaBorder
        )
        DesktopThemeType.OBSIDIAN_OLED -> darkColorScheme(
            primary = OledPrimary,
            onPrimary = OledOnPrimary,
            background = OledBackground,
            surface = OledSurface,
            surfaceVariant = OledCard,
            onBackground = OledText,
            onSurface = OledText,
            onSurfaceVariant = OledTextMuted,
            outline = OledBorder
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
