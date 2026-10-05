package com.veritas.reader

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal fun veritasColorScheme(themeId: String, context: Context): ColorScheme {
    val isSystemDark = try {
        (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    } catch (_: Exception) {
        false
    }
    val concreteId = VeritasThemeCatalog.resolveConcreteThemeId(themeId, isSystemDark)

    return when (concreteId) {
        "light" -> lightColorScheme(
            primary = Color(0xFFC07318),          // Rich Darker Amber Gold
            onPrimary = Color.White,
            primaryContainer = Color(0xFFFDE8CC),
            onPrimaryContainer = Color(0xFF3B1E00),
            secondary = Color(0xFF8C5E3C),        // Warm Terracotta
            secondaryContainer = Color(0xFFF5ECE4),
            onSecondaryContainer = Color(0xFF321A0C),
            tertiary = Color(0xFF52695C),         // Soft Sage
            tertiaryContainer = Color(0xFFDCEDE4),
            onTertiaryContainer = Color(0xFF102117),
            background = Color(0xFFFAF7F2),       // Warm Cream Paper
            surface = Color(0xFFFFFFFF),          // Crisp White Card
            surfaceVariant = Color(0xFFF0EAE1),   // Warm Linen Container / Chip
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF7F3EC),
            surfaceContainer = Color(0xFFF2ECE3),
            surfaceContainerHigh = Color(0xFFEBE4D9),
            surfaceContainerHighest = Color(0xFFE3DCCE),
            onSurface = Color(0xFF1E1B18),        // Warm Espresso Charcoal Text
            onSurfaceVariant = Color(0xFF6E675E), // Warm Neutral Muted Gray
            outline = Color(0xFFD6CEC4),
            outlineVariant = Color(0xFFE8E1D7),
            error = Color(0xFFBA1A1A),
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF93000A)
        )

        "neon" -> darkColorScheme(
            primary = Color(0xFF00E5FF),          // Vibrant Luminous Electric Cyan
            onPrimary = Color(0xFF001B20),
            primaryContainer = Color(0xFF003844),
            onPrimaryContainer = Color(0xFFB8F5FF),
            secondary = Color(0xFF00E676),        // Cyber Emerald Mint
            onSecondary = Color(0xFF00220E),
            secondaryContainer = Color(0xFF003D1A),
            onSecondaryContainer = Color(0xFF82FFBC),
            tertiary = Color(0xFFFF2A85),         // Cyber Neon Coral / Pink
            onTertiary = Color(0xFF3B0018),
            tertiaryContainer = Color(0xFF5C0028),
            onTertiaryContainer = Color(0xFFFFB2D1),
            background = Color(0xFF08090C),       // Deep OLED Obsidian
            onBackground = Color(0xFFF0F6FC),
            surface = Color(0xFF10141B),          // Elevated Cyber Slate
            onSurface = Color(0xFFF0F6FC),
            surfaceVariant = Color(0xFF181F29),   // Smooth Cyber Container / Chip
            surfaceContainerLowest = Color(0xFF040507),
            surfaceContainerLow = Color(0xFF0C0F14),
            surfaceContainer = Color(0xFF10141B),
            surfaceContainerHigh = Color(0xFF161C24),
            surfaceContainerHighest = Color(0xFF1D242F),
            onSurfaceVariant = Color(0xFF8BA2B8), // Readable Neutral Cyber Gray
            outline = Color(0xFF263242),
            outlineVariant = Color(0xFF181F29)
        )

        "solarized_dark" -> darkColorScheme(
            primary = Color(0xFF2AA198),          // Teal
            onPrimary = Color(0xFF002B36),
            primaryContainer = Color(0xFF073642),
            onPrimaryContainer = Color(0xFF93A1A1),
            secondary = Color(0xFF268BD2),        // Blue
            secondaryContainer = Color(0xFF002B36),
            onSecondaryContainer = Color(0xFF2AA198),
            tertiary = Color(0xFFB58900),         // Yellow/Gold
            tertiaryContainer = Color(0xFF073642),
            onTertiaryContainer = Color(0xFFFDF6E3),
            background = Color(0xFF002B36),
            onBackground = Color(0xFFEEE8D5),
            surface = Color(0xFF073642),
            onSurface = Color(0xFFEEE8D5),        // High Contrast Solarized Base3
            surfaceVariant = Color(0xFF0A3F4E),
            surfaceContainerLowest = Color(0xFF00212B),
            surfaceContainerLow = Color(0xFF002B36),
            surfaceContainer = Color(0xFF073642),
            surfaceContainerHigh = Color(0xFF0B414F),
            surfaceContainerHighest = Color(0xFF0F4D5D),
            onSurfaceVariant = Color(0xFF93A1A1), // Solarized Base1
            outline = Color(0xFF586E75),
            outlineVariant = Color(0xFF0A3F4E)
        )

        "tomorrow_night_blue" -> darkColorScheme(
            primary = Color(0xFFBBDAFF),
            onPrimary = Color(0xFF002451),
            primaryContainer = Color(0xFF002047),
            onPrimaryContainer = Color(0xFFEEFFFF),
            secondary = Color(0xFFEBBBFF),
            secondaryContainer = Color(0xFF002451),
            onSecondaryContainer = Color(0xFFEBBBFF),
            tertiary = Color(0xFFFFC58F),
            tertiaryContainer = Color(0xFF00346B),
            onTertiaryContainer = Color(0xFFFFE5CC),
            background = Color(0xFF002451),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF002F6C),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF003C7A),
            surfaceContainerLowest = Color(0xFF001B3E),
            surfaceContainerLow = Color(0xFF002451),
            surfaceContainer = Color(0xFF002F6C),
            surfaceContainerHigh = Color(0xFF00387B),
            surfaceContainerHighest = Color(0xFF00438E),
            onSurfaceVariant = Color(0xFFB2CCD6),
            outline = Color(0xFF395F91),
            outlineVariant = Color(0xFF003C7A)
        )

        "dark_high_contrast" -> darkColorScheme(
            primary = Color(0xFFFFFFFF),
            onPrimary = Color.Black,
            primaryContainer = Color(0xFFE2E2E2),
            onPrimaryContainer = Color.Black,
            secondary = Color(0xFFFFD400),
            secondaryContainer = Color(0xFF3A3000),
            onSecondaryContainer = Color(0xFFFFFFD1),
            tertiary = Color(0xFF00E5FF),
            tertiaryContainer = Color(0xFF003D44),
            onTertiaryContainer = Color(0xFFB8F5FF),
            background = Color(0xFF000000),
            onBackground = Color(0xFFFFFFFF),
            surface = Color(0xFF0A0A0A),
            onSurface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFF1D1D1D),
            surfaceContainerLowest = Color(0xFF000000),
            surfaceContainerLow = Color(0xFF050505),
            surfaceContainer = Color(0xFF0A0A0A),
            surfaceContainerHigh = Color(0xFF141414),
            surfaceContainerHighest = Color(0xFF1D1D1D),
            onSurfaceVariant = Color(0xFFE0E0E0),
            outline = Color(0xFFFFFFFF),
            outlineVariant = Color(0xFF666666)
        )

        "amoled" -> darkColorScheme(
            primary = Color(0xFF90CAF9),
            onPrimary = Color(0xFF003258),
            primaryContainer = Color(0xFF004881),
            onPrimaryContainer = Color(0xFFD1E4FF),
            secondary = Color(0xFF80CBC4),
            secondaryContainer = Color(0xFF004D40),
            onSecondaryContainer = Color(0xFFB2DFDB),
            tertiary = Color(0xFFCE93D8),
            tertiaryContainer = Color(0xFF4A148C),
            onTertiaryContainer = Color(0xFFE1BEE7),
            background = Color(0xFF000000),
            onBackground = Color(0xFFE2E2E2),
            surface = Color(0xFF000000),
            onSurface = Color(0xFFE2E2E2),
            surfaceVariant = Color(0xFF1C1F26),
            surfaceContainerLowest = Color(0xFF000000),
            surfaceContainerLow = Color(0xFF000000),
            surfaceContainer = Color(0xFF101216),
            surfaceContainerHigh = Color(0xFF181B20),
            surfaceContainerHighest = Color(0xFF22262D),
            onSurfaceVariant = Color(0xFFB0B0B0),
            outline = Color(0xFF444444),
            outlineVariant = Color(0xFF2B2E35)
        )

        "white_high_contrast" -> lightColorScheme(
            primary = Color(0xFF000000),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDADADA),
            onPrimaryContainer = Color.Black,
            secondary = Color(0xFF004B65),
            secondaryContainer = Color(0xFFC8EFFF),
            onSecondaryContainer = Color(0xFF001E2B),
            tertiary = Color(0xFF6B3A00),
            tertiaryContainer = Color(0xFFFFDDB5),
            onTertiaryContainer = Color(0xFF2C1500),
            background = Color(0xFFFFFFFF),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFEFEFEF),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF7F7F7),
            surfaceContainer = Color(0xFFEFEFEF),
            surfaceContainerHigh = Color(0xFFE5E5E5),
            surfaceContainerHighest = Color(0xFFDADADA),
            onSurface = Color(0xFF000000),
            onSurfaceVariant = Color(0xFF202020),
            outline = Color(0xFF000000),
            outlineVariant = Color(0xFF333333)
        )

        "bw_gradient_light" -> lightColorScheme(
            primary = Color(0xFF111111),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE7E7E7),
            onPrimaryContainer = Color(0xFF111111),
            secondary = Color(0xFF4A4A4A),
            secondaryContainer = Color(0xFFF0F0F0),
            onSecondaryContainer = Color(0xFF161616),
            tertiary = Color(0xFF707070),
            tertiaryContainer = Color(0xFFE0E0E0),
            onTertiaryContainer = Color(0xFF111111),
            background = Color(0xFFFAFAFA),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFEDEDED),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF7F7F7),
            surfaceContainer = Color(0xFFF0F0F0),
            surfaceContainerHigh = Color(0xFFE8E8E8),
            surfaceContainerHighest = Color(0xFFDFDFDF),
            onSurface = Color(0xFF101010),
            onSurfaceVariant = Color(0xFF3F3F3F),
            outline = Color(0xFFB0B0B0),
            outlineVariant = Color(0xFFEDEDED)
        )

        "bw_gradient_dark" -> darkColorScheme(
            primary = Color(0xFFF2F2F2),
            onPrimary = Color(0xFF090909),
            primaryContainer = Color(0xFF2D2D2D),
            onPrimaryContainer = Color(0xFFF6F6F6),
            secondary = Color(0xFFC7C7C7),
            secondaryContainer = Color(0xFF1E1E1E),
            onSecondaryContainer = Color(0xFFEDEDED),
            tertiary = Color(0xFF9A9A9A),
            tertiaryContainer = Color(0xFF252525),
            onTertiaryContainer = Color(0xFFE0E0E0),
            background = Color(0xFF050505),
            onBackground = Color(0xFFF5F5F5),
            surface = Color(0xFF111111),
            onSurface = Color(0xFFF5F5F5),
            surfaceVariant = Color(0xFF242424),
            surfaceContainerLowest = Color(0xFF000000),
            surfaceContainerLow = Color(0xFF0B0B0B),
            surfaceContainer = Color(0xFF111111),
            surfaceContainerHigh = Color(0xFF1A1A1A),
            surfaceContainerHighest = Color(0xFF242424),
            onSurfaceVariant = Color(0xFFC9C9C9),
            outline = Color(0xFF404040),
            outlineVariant = Color(0xFF242424)
        )

        "github_light", "one_light" -> lightColorScheme(
            primary = Color(0xFF4078F2),          // Atom / One Light Blue
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE5EDFF),
            onPrimaryContainer = Color(0xFF0D328C),
            secondary = Color(0xFF50A14F),        // One Light Meadow Green
            secondaryContainer = Color(0xFFE6F4E6),
            onSecondaryContainer = Color(0xFF194D18),
            tertiary = Color(0xFFA626A4),         // One Light Mulberry
            tertiaryContainer = Color(0xFFFCEBFC),
            onTertiaryContainer = Color(0xFF4E074D),
            background = Color(0xFFFAFAFA),       // One Light Warm Platinum
            onBackground = Color(0xFF383A42),     // One Light Dark Charcoal
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF383A42),
            surfaceVariant = Color(0xFFEAEAEB),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF7F7F8),
            surfaceContainer = Color(0xFFF0F0F1),
            surfaceContainerHigh = Color(0xFFE5E5E6),
            surfaceContainerHighest = Color(0xFFDCDDDE),
            onSurfaceVariant = Color(0xFF696C77), // One Light Secondary Muted
            outline = Color(0xFFBDC0C4),
            outlineVariant = Color(0xFFE5E5E6)
        )

        "github_dark", "one_dark_pro" -> darkColorScheme(
            primary = Color(0xFF58A6FF),
            onPrimary = Color(0xFF0D1117),
            primaryContainer = Color(0xFF124391),
            onPrimaryContainer = Color(0xFFF0F6FC),
            secondary = Color(0xFF3FB950),
            secondaryContainer = Color(0xFF0D1117),
            onSecondaryContainer = Color(0xFF3FB950),
            tertiary = Color(0xFFFFA657),
            tertiaryContainer = Color(0xFF161B22),
            onTertiaryContainer = Color(0xFFFFD1A9),
            background = Color(0xFF0D1117),
            onBackground = Color(0xFFF0F6FC),
            surface = Color(0xFF161B22),
            onSurface = Color(0xFFF0F6FC),        // GitHub fg.default
            surfaceVariant = Color(0xFF21262D),
            surfaceContainerLowest = Color(0xFF090D12),
            surfaceContainerLow = Color(0xFF11161D),
            surfaceContainer = Color(0xFF161B22),
            surfaceContainerHigh = Color(0xFF1C2129),
            surfaceContainerHighest = Color(0xFF21262D),
            onSurfaceVariant = Color(0xFFC9D1D9), // GitHub fg.muted
            outline = Color(0xFF30363D),
            outlineVariant = Color(0xFF21262D)
        )

        "dracula_light" -> lightColorScheme(
            primary = Color(0xFF7D4EBA),          // Deep Dracula Purple (High contrast daylight)
            onPrimary = Color.White,
            primaryContainer = Color(0xFFEFE7FB), // Soft lilac purple
            onPrimaryContainer = Color(0xFF351B5A),
            secondary = Color(0xFF1E7E34),        // Dracula Forest Green
            secondaryContainer = Color(0xFFE1F5E8),
            onSecondaryContainer = Color(0xFF0D3E18),
            tertiary = Color(0xFFC72C76),         // Vibrant Dracula Pink / Magenta
            tertiaryContainer = Color(0xFFFDE8F1),
            onTertiaryContainer = Color(0xFF500827),
            background = Color(0xFFF8F7FA),       // Alabaster Lilac
            onBackground = Color(0xFF282A36),     // Classic Dracula Dark Slate text
            surface = Color(0xFFFFFFFF),          // Pure White Card
            onSurface = Color(0xFF282A36),        // High Contrast Dracula text
            surfaceVariant = Color(0xFFEDEAF2),   // Soft Lavender container
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF6F4F9),
            surfaceContainer = Color(0xFFF0EDF5),
            surfaceContainerHigh = Color(0xFFEAE5F0),
            surfaceContainerHighest = Color(0xFFE2DEEB),
            onSurfaceVariant = Color(0xFF6272A4), // Dracula Comment Blue-Slate
            outline = Color(0xFFCBD0E0),
            outlineVariant = Color(0xFFE4E7F0)
        )

        "dracula" -> darkColorScheme(
            primary = Color(0xFFBD93F9),          // Purple
            onPrimary = Color(0xFF282A36),
            primaryContainer = Color(0xFF2F3142),
            onPrimaryContainer = Color(0xFFF8F8F2),
            secondary = Color(0xFF50FA7B),        // Green
            secondaryContainer = Color(0xFF282A36),
            onSecondaryContainer = Color(0xFF50FA7B),
            tertiary = Color(0xFFFF79C6),         // Pink
            tertiaryContainer = Color(0xFF44475A),
            onTertiaryContainer = Color(0xFFFFB2D1),
            background = Color(0xFF282A36),
            onBackground = Color(0xFFF8F8F2),
            surface = Color(0xFF1E1F29),
            onSurface = Color(0xFFF8F8F2),
            surfaceVariant = Color(0xFF44475A),
            surfaceContainerLowest = Color(0xFF191A23),
            surfaceContainerLow = Color(0xFF21222C),
            surfaceContainer = Color(0xFF282A36),
            surfaceContainerHigh = Color(0xFF343746),
            surfaceContainerHighest = Color(0xFF44475A),
            onSurfaceVariant = Color(0xFF8E9AC9),
            outline = Color(0xFF6272A4),
            outlineVariant = Color(0xFF343746)
        )

        "material_you" -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val isSystemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                if (isSystemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                lightColorScheme(
                    primary = Color(0xFF6750A4),
                    onPrimary = Color.White,
                    primaryContainer = Color(0xFFEADDFF),
                    onPrimaryContainer = Color(0xFF21005D),
                    secondary = Color(0xFF625B71),
                    secondaryContainer = Color(0xFFE8DEF8),
                    onSecondaryContainer = Color(0xFF1D192B),
                    tertiary = Color(0xFF7D5260),
                    tertiaryContainer = Color(0xFFFFD8E4),
                    background = Color(0xFFFFFBFE),
                    surface = Color(0xFFFFFBFE),
                    surfaceVariant = Color(0xFFE7E0EC),
                    onSurface = Color(0xFF1C1B1F),
                    onSurfaceVariant = Color(0xFF49454F)
                )
            }
        }

        "midnight_light" -> lightColorScheme(
            primary = Color(0xFF4338CA),          // Stellar Indigo
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE0E7FF), // Soft Periwinkle
            onPrimaryContainer = Color(0xFF1E1B4B),
            secondary = Color(0xFF0E7490),        // Oceanic Cyan
            secondaryContainer = Color(0xFFCFFAFE),
            onSecondaryContainer = Color(0xFF155E75),
            tertiary = Color(0xFF6D28D9),         // Twilight Violet
            tertiaryContainer = Color(0xFFEDE9FE),
            onTertiaryContainer = Color(0xFF2E1065),
            background = Color(0xFFF8FAFC),       // Frosted Ice Slate
            onBackground = Color(0xFF0F172A),     // Midnight Navy text
            surface = Color(0xFFFFFFFF),          // Pure White
            onSurface = Color(0xFF0F172A),
            surfaceVariant = Color(0xFFE2E8F0),   // Soft Slate Container
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF8FAFC),
            surfaceContainer = Color(0xFFF1F5F9),
            surfaceContainerHigh = Color(0xFFE2E8F0),
            surfaceContainerHighest = Color(0xFFCBD5E1),
            onSurfaceVariant = Color(0xFF475569), // Slate 600
            outline = Color(0xFF94A3B8),
            outlineVariant = Color(0xFFE2E8F0)
        )

        "midnight_dark" -> darkColorScheme(
            primary = Color(0xFFA79BFF),
            onPrimary = Color(0xFF221656),
            primaryContainer = Color(0xFF261D50),
            onPrimaryContainer = Color(0xFFE6DFFF),
            secondary = Color(0xFFC9D6DF),
            secondaryContainer = Color(0xFF29333B),
            onSecondaryContainer = Color(0xFFE6EEF3),
            tertiary = Color(0xFF9BD8E0),
            tertiaryContainer = Color(0xFF164B54),
            onTertiaryContainer = Color(0xFFB8F5FF),
            background = Color(0xFF0F172A),
            onBackground = Color(0xFFF8FAFC),
            surface = Color(0xFF1E293B),
            onSurface = Color(0xFFF8FAFC),
            surfaceVariant = Color(0xFF334155),
            surfaceContainerLowest = Color(0xFF0A0F1D),
            surfaceContainerLow = Color(0xFF141E33),
            surfaceContainer = Color(0xFF1E293B),
            surfaceContainerHigh = Color(0xFF28354A),
            surfaceContainerHighest = Color(0xFF334155),
            onSurfaceVariant = Color(0xFF94A3B8),
            outline = Color(0xFF475569),
            outlineVariant = Color(0xFF1E293B)
        )

        else -> darkColorScheme(
            primary = Color(0xFFE5A93C),          // Luminous Amber Gold (Dark counterpart of #C07318)
            onPrimary = Color(0xFF2C1E00),
            primaryContainer = Color(0xFF3D2A10),
            onPrimaryContainer = Color(0xFFFDE8CC),
            secondary = Color(0xFFD4A373),        // Warm Terracotta Sand (Dark counterpart of #8C5E3C)
            onSecondary = Color(0xFF3C2000),
            secondaryContainer = Color(0xFF3B2A1E),
            onSecondaryContainer = Color(0xFFF5ECE4),
            tertiary = Color(0xFF81B29A),         // Soft Sage (Dark counterpart of #52695C)
            onTertiary = Color(0xFF003828),
            tertiaryContainer = Color(0xFF1B2B24),
            onTertiaryContainer = Color(0xFFDCEDE4),
            background = Color(0xFF151515),       // User Requested #151515 Dark Mode Background
            onBackground = Color(0xFFF5F2EB),
            surface = Color(0xFF1E1E1E),          // Elevated Warm Dark Card matching #151515
            onSurface = Color(0xFFF5F2EB),        // Warm Cream Off-White
            surfaceVariant = Color(0xFF282828),   // Dark Chip container
            surfaceContainerLowest = Color(0xFF111111),
            surfaceContainerLow = Color(0xFF181818),
            surfaceContainer = Color(0xFF1E1E1E),
            surfaceContainerHigh = Color(0xFF242424),
            surfaceContainerHighest = Color(0xFF2C2C2C),
            onSurfaceVariant = Color(0xFFA8A29E), // Muted Neutral
            outline = Color(0xFF424242),
            outlineVariant = Color(0xFF2C2C2C)
        )
    }
}

