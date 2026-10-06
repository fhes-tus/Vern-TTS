package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VeritasThemeCatalogTest {

    @Test fun accessibilityPresetsSurviveThemeFamilyMigration() {
        for (id in listOf("amoled", "dark_high_contrast", "white_high_contrast")) {
            assertEquals(id, VeritasThemeCatalog.normalizeThemeId(id))
            assertEquals(id, VeritasThemeCatalog.resolveConcreteThemeId(id, true))
        }
        assertFalse(VeritasThemeCatalog.isDark("white_high_contrast"))
        assertTrue(VeritasThemeCatalog.isDark("dark_high_contrast"))
        assertEquals("White High Contrast", VeritasThemeCatalog.displayName("white_high_contrast"))
        assertEquals("github_light", VeritasThemeCatalog.normalizeThemeId("one_light"))
        assertEquals("system", VeritasThemeCatalog.resolveThemeIdForStorage("unknown", "system"))
    }

    @Test
    fun newLightThemesAreRegistered() {
        val options = VeritasThemeCatalog.themeOptions.toMap()
        assertEquals("Dracula Light", options["dracula_light"])
        assertEquals("Midnight Light", options["midnight_light"])
        assertEquals("Dracula", options["dracula"])
        assertEquals("Midnight Dark", options["midnight_dark"])
        assertEquals("GitHub Light", options["github_light"])
        assertEquals("GitHub Dark", options["github_dark"])
    }

    @Test
    fun newLightThemesResolveAsLight() {
        assertFalse("dracula_light should not be dark", VeritasThemeCatalog.isDark("dracula_light"))
        assertFalse("midnight_light should not be dark", VeritasThemeCatalog.isDark("midnight_light"))
        assertFalse("github_light should not be dark", VeritasThemeCatalog.isDark("github_light"))
        assertFalse("one_light should not be dark", VeritasThemeCatalog.isDark("one_light"))

        assertTrue("dracula should be dark", VeritasThemeCatalog.isDark("dracula"))
        assertTrue("midnight_dark should be dark", VeritasThemeCatalog.isDark("midnight_dark"))
        assertTrue("github_dark should be dark", VeritasThemeCatalog.isDark("github_dark"))
        assertTrue("neon should be dark", VeritasThemeCatalog.isDark("neon"))
    }

    @Test
    fun themeFamiliesAndModeResolution() {
        val families = VeritasThemeCatalog.families.map { it.id }
        assertTrue(families.contains("sage"))
        assertTrue(families.contains("midnight"))
        assertTrue(families.contains("dracula"))
        assertTrue(families.contains("github"))
        assertTrue(families.contains("bw_gradient"))
        assertTrue(families.contains("neon"))

        // System mode resolution
        assertEquals("dark", VeritasThemeCatalog.resolveConcreteThemeId("system", systemInDarkTheme = true))
        assertEquals("light", VeritasThemeCatalog.resolveConcreteThemeId("system", systemInDarkTheme = false))

        assertEquals("dracula", VeritasThemeCatalog.resolveConcreteThemeId("dracula_system", systemInDarkTheme = true))
        assertEquals("dracula_light", VeritasThemeCatalog.resolveConcreteThemeId("dracula_system", systemInDarkTheme = false))

        assertEquals("midnight_dark", VeritasThemeCatalog.resolveConcreteThemeId("midnight_system", systemInDarkTheme = true))
        assertEquals("midnight_light", VeritasThemeCatalog.resolveConcreteThemeId("midnight_system", systemInDarkTheme = false))

        assertEquals("github_dark", VeritasThemeCatalog.resolveConcreteThemeId("github_system", systemInDarkTheme = true))
        assertEquals("github_light", VeritasThemeCatalog.resolveConcreteThemeId("github_system", systemInDarkTheme = false))

        // Family and mode extraction
        assertEquals("dracula", VeritasThemeCatalog.familyForThemeId("dracula_system"))
        assertEquals("system", VeritasThemeCatalog.modeForThemeId("dracula_system"))
        assertEquals("dracula", VeritasThemeCatalog.familyForThemeId("dracula_light"))
        assertEquals("light", VeritasThemeCatalog.modeForThemeId("dracula_light"))
        assertEquals("sage", VeritasThemeCatalog.familyForThemeId("system"))
        assertEquals("system", VeritasThemeCatalog.modeForThemeId("system"))
    }

    @Test
    fun themePreviewColorsReturnTriosForAllOptions() {
        for ((id, _) in VeritasThemeCatalog.themeOptions) {
            val colors = themePreviewColors(id)
            assertNotNull("Colors for theme $id should not be null", colors)
            assertEquals("Theme $id preview should have exactly 3 swatch colors", 3, colors.size)
        }
    }
}
