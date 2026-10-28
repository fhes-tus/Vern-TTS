package com.veritas.reader

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import com.veritas.reader.ui.VeritasUiFont
import com.veritas.reader.ui.veritasTypography
import com.veritas.reader.ui.screens.homeGreeting
import org.junit.Assert.*
import org.junit.Test

class VisualHierarchyTest {
    @Test fun greetingUsesTimeAndSavedNameWithoutRandomRecompositionChanges() {
        val expected = mapOf(0 to "Good to see you", 4 to "Welcome to a new day", 6 to "Rise and shine", 8 to "Good morning",
            12 to "Good afternoon", 17 to "Good evening", 22 to "Good night")
        expected.forEach { (hour, greeting) ->
            assertEquals("$greeting, Festus", homeGreeting("  Festus  ", hour))
            assertEquals(greeting, homeGreeting("", hour))
        }
        assertEquals("Welcome back, Festus", homeGreeting("Festus", 8, true))
    }

    @Test fun amoledMovesTheOrdinaryDarkPageToneOntoCardsForEveryAccent() {
        listOf(Color(0xFFFFB4AB), Color(0xFF58A6FF), Color(0xFFB4E1BB), Color(0xFFFFD580)).forEach { accent ->
            val base = darkColorScheme(primary = accent)
            val dark = veritasDarkSurfaceScheme(base, false)
            val amoled = veritasDarkSurfaceScheme(base, true)
            assertEquals(Color.Black, amoled.background)
            assertEquals(dark.background, amoled.surface)
            assertEquals(dark.background, amoled.surfaceContainer)
            assertTrue(dark.background.luminance() < dark.surface.luminance())
            assertTrue(dark.surface.luminance() < dark.surfaceContainerHigh.luminance())
            assertEquals(accent, dark.primary)
            for (surface in listOf(dark.background, dark.surface, dark.surfaceContainerHighest, amoled.surface)) {
                assertTrue((dark.onSurface.luminance() + .05f) / (surface.luminance() + .05f) >= 7f)
                assertTrue((dark.onSurfaceVariant.luminance() + .05f) / (surface.luminance() + .05f) >= 4.5f)
            }
        }
    }

    @Test fun fontRolesKeepTheirHierarchyAcrossFontsAndThemePacks() {
        VeritasUiFont.entries.forEach { font ->
            val base = veritasTypography(font)
            assertTrue(base.titleLarge.fontSize.value > base.titleMedium.fontSize.value)
            assertTrue(base.titleMedium.fontSize.value > base.bodyMedium.fontSize.value)
            assertEquals(FontWeight.SemiBold, base.titleLarge.fontWeight)
            assertEquals(FontWeight.Medium, base.titleMedium.fontWeight)
            assertEquals(FontWeight.Normal, base.bodyMedium.fontWeight)
            listOf("vern_media", "one_ui", "material_you", "liquid_glass").forEach { pack ->
                assertEquals(base, veritasPackTypography(base, pack))
            }
        }
    }

    @Test fun accentButtonsKeepReadableInkForLightAndDarkPalettes() {
        for (accent in listOf(Color.Yellow, Color(0xFF0753A3), Color(0xFFCA7918), Color(0xFF2AA198))) {
            val colors = veritasReadableInk(lightColorScheme(primary = accent, onPrimary = Color.White))
            val light = maxOf(colors.primary.luminance(), colors.onPrimary.luminance())
            val dark = minOf(colors.primary.luminance(), colors.onPrimary.luminance())
            assertTrue((light + .05f) / (dark + .05f) >= 4.5f)
        }
    }

    @Test fun bookRowsShowReadingStatusInsteadOfTechnicalSentenceCounts() {
        val book = SavedDocument("summary", "A book", "book.pdf", "PDF", 1, 1, 50, 101, 20_000, "")
        assertEquals("50% read · ~10m left", libraryReadingSummary(book))
        assertEquals("Finished", libraryReadingSummary(book.copy(currentIndex = 100)))
        assertEquals("Not started · ~20m", libraryReadingSummary(book.copy(currentIndex = 0)))
        assertFalse(libraryReadingSummary(book).contains("PDF"))
        assertFalse(libraryReadingSummary(book).contains("sent"))
    }
}
