package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test

class LauncherThemeFollowingTest {
    @Test fun systemFamilyFollowsBothModesWithoutChangingTheSavedSelection() {
        val settings = ReaderSettings(launcherPalette = "theme", themeId = "midnight_system")
        assertEquals("stellar", VernCustomization.palette(settings.launcherPalette, settings.themeId, false).id)
        assertEquals("lilac", VernCustomization.palette(settings.launcherPalette, settings.themeId, true).id)
        assertEquals("theme", ReaderSettings.fromJson(settings.toJson()).launcherPalette)
        assertEquals("violet", VernCustomization.palette("theme", "dracula_system", false).id)
        assertEquals("lavender", VernCustomization.palette("theme", "dracula_system", true).id)
        assertEquals("cobalt", VernCustomization.palette("theme", "github_system", false).id)
        assertEquals("azure", VernCustomization.palette("theme", "github_system", true).id)
    }

    @Test fun explicitPalettesStayPinnedAndAccessibilitySchemesFollowTheirColour() {
        assertEquals("forest", VernCustomization.palette("forest", "dracula_system", false).id)
        assertEquals("forest", VernCustomization.palette("forest", "github_system", true).id)
        assertEquals("paper", VernCustomization.palette("theme", "dark_high_contrast", true).id)
        assertEquals("onyx", VernCustomization.palette("theme", "white_high_contrast", false).id)
        assertEquals("neon", VernCustomization.palette("theme", "neon", true).id)
    }
}
