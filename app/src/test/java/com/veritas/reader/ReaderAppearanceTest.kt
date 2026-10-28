package com.veritas.reader

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class ReaderAppearanceTest {
    private val packs = VeritasThemePackCatalog.packOptions.map { it.first }

    @Test fun amoledChangesDarkSurfacesButKeepsLightAndAccessibilityChoices() {
        for (pack in packs) {
            val base = darkColorScheme(primary = Color(0xFFA79BFF))
            val dark = veritasPackColorScheme(base, pack, "midnight_dark")
            val amoled = veritasPackColorScheme(base, pack, "midnight_dark", true)
            assertEquals(Color.Black, amoled.background)
            assertEquals(dark.background, amoled.surface)
            assertEquals(dark.primary, amoled.primary)
            val light = lightColorScheme()
            val lightBefore = veritasPackColorScheme(light, pack, "midnight_light")
            val lightAfter = veritasPackColorScheme(light, pack, "midnight_light", true)
            assertEquals(lightBefore.background, lightAfter.background)
            assertEquals(lightBefore.surface, lightAfter.surface)
            assertEquals(lightBefore.primaryContainer, lightAfter.primaryContainer)
            assertSame(base, veritasPackColorScheme(base, pack, "dark_high_contrast", true))
            assertEquals(Color.Black, veritasPackColorScheme(base, pack, "amoled").background)
        }
    }

    @Test fun paperAndPlaybackUseTheResolvedSurfaceAcrossPacksAndDarkModes() {
        for (pack in packs) for (amoled in listOf(false, true)) {
            val scheme = veritasPackColorScheme(darkColorScheme(), pack, "dark", amoled)
            assertEquals(scheme.surface, readerPaperColors(scheme, PaperToneMode.ACTIVE_THEME).first)
            assertEquals(scheme.onSurface, readerPaperColors(scheme, PaperToneMode.ACTIVE_THEME).second)
            assertEquals(scheme.surface, VeritasPackStyle.playerSurfaceColorForPack(scheme, pack))
            assertEquals(Color.White, readerPaperColors(scheme, PaperToneMode.NATURAL_WHITE).first)
            assertEquals(Color(0xFFFBF0D9), readerPaperColors(scheme, PaperToneMode.WARM_SEPIA).first)
            assertEquals(Color(0xFF141414), readerPaperColors(scheme, PaperToneMode.DARK).first)
        }
    }

    @Test fun sourceInkStaysReadableWhenPaperAndAppHaveOppositeBrightness() {
        val schemes = listOf(lightColorScheme(), veritasDarkSurfaceScheme(darkColorScheme(), false),
            veritasDarkSurfaceScheme(darkColorScheme(), true))
        for (scheme in schemes) for (tone in PaperToneMode.entries) {
            val paper = readerPaperColors(scheme, tone).first
            for (source in listOf(Color.Black, Color.White, Color(0xFF6272A4))) {
                val ink = readableDocumentInk(source, paper)
                val contrast = (maxOf(ink.luminance(), paper.luminance()) + .05f) /
                    (minOf(ink.luminance(), paper.luminance()) + .05f)
                assertTrue("$tone source $source on $paper: $contrast", contrast >= 4.5f)
            }
        }
        assertEquals(Color.Black, readableDocumentInk(Color.Black, Color.White))
        assertEquals(Color.White, readableDocumentInk(Color.White, Color.Black))
    }
}
