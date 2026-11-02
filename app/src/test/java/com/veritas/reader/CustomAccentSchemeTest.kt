package com.veritas.reader

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class CustomAccentSchemeTest {
    private val accents = listOf("#000000", "#FFFFFF", "#FFFF00", "#001122", "#12ABEF", "#F02090")

    @Test fun everyPackUsesTheChosenAccentExactlyInLightDarkAndAmoled() {
        for ((pack, _) in VeritasThemePackCatalog.packOptions) for (dark in listOf(false, true)) {
            for (amoled in listOf(false, true)) for (hex in accents) {
                val expected = Color(0xFF000000L or hex.drop(1).toLong(16))
                val scheme = veritasAppearanceColorScheme(if (dark) darkColorScheme() else lightColorScheme(),
                    pack, if (dark) "midnight_dark" else "midnight_light", amoled, hex)
                for (actual in listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.surfaceTint, scheme.inversePrimary)) {
                    assertEquals("$pack/dark=$dark/amoled=$amoled/$hex", expected, actual)
                }
                if (dark && amoled) assertEquals(Color.Black, scheme.background)
            }
        }
    }

    @Test fun exactAccentFillsKeepContrastingTextWithoutChangingTheFill() {
        for (dark in listOf(false, true)) for (hex in accents) {
            val scheme = veritasAppearanceColorScheme(if (dark) darkColorScheme() else lightColorScheme(),
                "material_you", if (dark) "midnight_dark" else "midnight_light", customAccentColor = hex)
            for ((fill, ink) in listOf(scheme.primary to scheme.onPrimary, scheme.secondary to scheme.onSecondary,
                scheme.tertiary to scheme.onTertiary, scheme.primaryContainer to scheme.onPrimaryContainer,
                scheme.secondaryContainer to scheme.onSecondaryContainer, scheme.tertiaryContainer to scheme.onTertiaryContainer)) {
                val ratio = (maxOf(fill.luminance(), ink.luminance()) + .05f) /
                    (minOf(fill.luminance(), ink.luminance()) + .05f)
                assertTrue("$hex/dark=$dark contrast $ratio", ratio >= 4.5f)
            }
        }
    }
}
