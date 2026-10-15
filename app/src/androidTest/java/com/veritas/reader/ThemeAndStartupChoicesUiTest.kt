package com.veritas.reader

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.screens.CustomizationSettingsDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ThemeAndStartupChoicesUiTest {
    @get:Rule val rule = createComposeRule()
    private fun capture(name: String) {
        rule.waitForIdle()
        val instrument = InstrumentationRegistry.getInstrumentation()
        val directory = java.io.File(instrument.targetContext.getExternalFilesDir(null), "VernUiChecks").apply { mkdirs() }
        val bitmap = instrument.uiAutomation.takeScreenshot() ?: error("No screenshot")
        java.io.File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun everyFollowedThemeMatchesItsPrimaryAndEveryPaletteHasTheCorrectStartingTheme() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (night in listOf(false, true)) {
            val config = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val themed = context.createConfigurationContext(config)
            for (id in VeritasThemeCatalog.themeOptions.map { it.first } + listOf("amoled", "dark_high_contrast", "white_high_contrast")) {
                val palette = VernCustomization.palette("theme", id, night)
                val scheme = veritasColorScheme(id, themed)
                assertEquals("$id/$night background", scheme.primary.toArgb(), palette.background)
                assertEquals("$id/$night foreground", scheme.onPrimary.toArgb(), palette.foreground)
            }
        }
        for (palette in VernCustomization.palettes) for (style in listOf("bloom", "fade", "rise", "none")) {
            val selected = VernCustomization.startupTheme(context, ReaderSettings(launcherPalette = palette.id, splashAnimation = style))
            val expected = "Theme.Vern.Startup${palette.id.replaceFirstChar { it.uppercase() }}${if (style == "bloom") "" else "Static"}"
            assertEquals(expected, context.resources.getResourceEntryName(selected))
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                val theme = context.resources.newTheme().apply { applyStyle(selected, true) }
                val attributes = theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowSplashScreenBackground))
                try { assertEquals(palette.background, attributes.getColor(0, 0)) } finally { attributes.recycle() }
            }
        }
    }

    @Test fun themeChoiceReflectsFamilyChangesAndStartupPreviewIsVisibleBesideTheControls() {
        var settings by mutableStateOf(ReaderSettings(themeId = "midnight_light"))
        rule.setContent { MaterialTheme { CustomizationSettingsDialog(settings, { settings = it }, {}) } }
        rule.onNodeWithTag("icon-follow-theme").performScrollTo().performClick()
        rule.onNodeWithTag("resolved-icon-theme").assertTextContains("Stellar", substring = true)
        rule.runOnIdle { settings = settings.copy(themeId = "dracula") }
        rule.onNodeWithTag("resolved-icon-theme").assertTextContains("Lavender", substring = true)
        try {
            for (style in listOf("bloom", "fade", "rise")) {
                // Bring-into-view scrolling needs frames. Pause the test clock
                // only after navigation, to inspect the replay's midpoint.
                rule.mainClock.autoAdvance = true
                rule.onNodeWithTag("customization-$style").performScrollTo().performClick()
                rule.onNodeWithTag("startup-motion-stage").performScrollTo().assertIsDisplayed()
                rule.onNodeWithTag("preview-startup-motion").performScrollTo()
                rule.mainClock.autoAdvance = false
                rule.onNodeWithTag("preview-startup-motion").performClick()
                rule.mainClock.advanceTimeBy(VernStartupMotion.duration(style) / 2L)
                rule.onNodeWithText("Playing ${style.replaceFirstChar { it.uppercase() }}").assertIsDisplayed()
                capture("startup-preview-$style-midpoint")
                rule.mainClock.advanceTimeBy(1400)
            }
            rule.mainClock.autoAdvance = true
            rule.runOnIdle { settings = settings.copy(reduceMotion = true) }
            rule.mainClock.advanceTimeByFrame()
            rule.onNodeWithTag("preview-startup-motion").assertIsNotEnabled()
        } finally { rule.mainClock.autoAdvance = true }
    }
}
