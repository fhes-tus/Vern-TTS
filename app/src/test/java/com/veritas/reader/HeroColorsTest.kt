package com.veritas.reader

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class HeroColorsTest {
    @Test fun vibrantThemesKeepBodySecondaryTextAndActionsReadable() {
        val accents = listOf(Color.Black, Color.White, Color.Red, Color.Green, Color.Blue, Color.Cyan, Color.Yellow,
            Color.Magenta, Color(0xFFC07318), Color(0xFFE5A93C), Color(0xFFA79BFF), Color(0xFFBD93F9), Color(0xFF58A6FF))
        for (accent in accents) for (amoled in listOf(false, true)) {
            val scheme = veritasDarkSurfaceScheme(darkColorScheme(primary = accent), amoled)
            val hero = veritasHeroColors(scheme, true)
            assertTrue("$accent primary text", contrast(hero.content, hero.container) >= 7f)
            assertTrue("$accent secondary text", contrast(hero.secondaryContent, hero.container) >= 4.5f)
            assertTrue("$accent button", contrast(hero.actionContent, hero.actionContainer) >= 7f)
            assertEquals(1f, hero.container.alpha, 0f)
            assertEquals(accent, scheme.primary)
        }
    }

    @Test fun monochromeStaysNeutralAndAmoledLowersTheHeroTone() {
        for (accent in listOf(Color.Black, Color.White, Color(0xFF707070), Color(0xFFA79BFF))) {
            val base = darkColorScheme(primary = accent)
            val normal = veritasHeroColors(veritasDarkSurfaceScheme(base, false), true)
            val amoled = veritasHeroColors(veritasDarkSurfaceScheme(base, true), true)
            assertTrue(amoled.container.luminance() < normal.container.luminance())
            if (accent.red == accent.green && accent.green == accent.blue) {
                assertEquals(normal.container.red, normal.container.green, .001f)
                assertEquals(normal.container.green, normal.container.blue, .001f)
            }
        }
    }

    @Test fun quietHeroHasAReadableThemeTintInLightDarkAndAmoled() {
        val accent = Color(0xFF6750A4)
        for (scheme in listOf(lightColorScheme(primary = accent),
            veritasDarkSurfaceScheme(darkColorScheme(primary = accent), false),
            veritasDarkSurfaceScheme(darkColorScheme(primary = accent), true))) {
            val hero = veritasHeroColors(scheme, false)
            assertNotEquals(scheme.surface, hero.container)
            // The purple theme should tint the card purple, including over charcoal.
            assertTrue(hero.container.blue > hero.container.red)
            assertTrue(hero.container.red > hero.container.green)
            assertEquals(scheme.onSurface, hero.content)
            assertEquals(scheme.onSurfaceVariant, hero.secondaryContent)
            assertTrue(contrast(hero.content, hero.container) >= 4.5f)
            assertTrue(contrast(hero.secondaryContent, hero.container) >= 4.5f)
            assertEquals(scheme.primary, hero.actionContainer)
            assertEquals(scheme.onPrimary, hero.actionContent)
            assertFalse(hero.vibrant)
        }
    }

    private fun contrast(a: Color, b: Color) = (maxOf(a.luminance(), b.luminance()) + .05f) /
        (minOf(a.luminance(), b.luminance()) + .05f)
}
