package com.veritas.reader

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.window.DialogWindowProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.screens.CustomAccentControl
import com.veritas.reader.ui.screens.ReaderSettingsDialog
import com.veritas.reader.ui.screens.ThemePreviewCard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CustomAppearanceUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun everyPackAndSystemModeKeepsCustomAccentsReadableAndAmoledBlack() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun contrast(a: Color, b: Color) =
            (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
        for (night in listOf(false, true)) {
            val configuration = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val themed = context.createConfigurationContext(configuration)
            for ((pack, _) in VeritasThemePackCatalog.packOptions) {
                for (family in VeritasThemeCatalog.families.filterNot { it.isAccent }) {
                    for (hex in listOf("#000000", "#FFFFFF", "#FFFF00", "#001122", "#12ABEF", "#F02090")) for (amoled in listOf(false, true)) {
                        val settings = ReaderSettings(themeId = VeritasThemeCatalog.resolveThemeIdForStorage(family.id, "system"),
                            themePackId = pack, customAccentColor = hex, amoledMode = amoled)
                        val scheme = veritasReaderColorScheme(settings, themed)
                        val label = "$night/$pack/${family.id}/$hex/amoled=$amoled"
                        assertEquals(label, night, scheme.background.luminance() < .3f)
                        if (night && amoled) assertEquals(label, Color.Black, scheme.background)
                        val chosen = Color(0xFF000000L or hex.drop(1).toLong(16))
                        assertEquals("$label selected accent", chosen, scheme.primary)
                        assertEquals("$label secondary accent", chosen, scheme.secondary)
                        assertEquals("$label tertiary accent", chosen, scheme.tertiary)
                        for ((fill, ink) in listOf(scheme.primary to scheme.onPrimary, scheme.secondary to scheme.onSecondary,
                            scheme.tertiary to scheme.onTertiary, scheme.primaryContainer to scheme.onPrimaryContainer,
                            scheme.secondaryContainer to scheme.onSecondaryContainer, scheme.tertiaryContainer to scheme.onTertiaryContainer)) {
                            assertTrue("$label control contrast", contrast(fill, ink) >= 4.5f)
                        }
                    }
                }
            }
        }
        for (id in listOf("dark_high_contrast", "white_high_contrast")) {
            val original = veritasReaderColorScheme(ReaderSettings(themeId = id), context)
            val custom = veritasReaderColorScheme(ReaderSettings(themeId = id, customAccentColor = "#F02090"), context)
            assertEquals(original.primary, custom.primary)
            assertEquals(original.onPrimary, custom.onPrimary)
            assertEquals(original.primaryContainer, custom.primaryContainer)
            assertEquals(original.background, custom.background)
            assertEquals(original.onBackground, custom.onBackground)
            assertEquals(original.surface, custom.surface)
            assertEquals(original.onSurface, custom.onSurface)
        }
    }

    @Test fun pickerChoiceMatchesTheActualButtonFillInEveryPackAndMode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var settings by mutableStateOf(ReaderSettings())
        rule.setContent {
            MaterialTheme(colorScheme = veritasReaderColorScheme(settings, context)) {
                Column {
                    CustomAccentControl(settings.customAccentColor, settings.themeId) { settings = settings.copy(customAccentColor = it) }
                    Button({}, Modifier.fillMaxWidth().testTag("applied-accent-button")) { Text("Applied accent") }
                    ThemePreviewCard(settings.themePackId, settings.themeId, vibrantHero = true,
                        customAccentColor = settings.customAccentColor)
                }
            }
        }
        for ((pack, _) in VeritasThemePackCatalog.packOptions) for (mode in listOf("light", "dark")) {
            rule.runOnIdle { settings = settings.copy(themePackId = pack, themeId = "midnight_$mode", customAccentColor = "") }
            rule.onNodeWithTag("choose-custom-accent").performClick()
            rule.onNodeWithTag("custom-accent-preset-cyan").performScrollTo().performClick()
            val preview = rule.onNodeWithTag("custom-accent-preview").captureToImage().asAndroidBitmap()
            val selected = try { preview.getPixel(preview.width / 2, preview.height / 2) } finally { preview.recycle() }
            rule.onNodeWithTag("apply-custom-accent").performClick()
            val button = rule.onNodeWithTag("applied-accent-button").captureToImage().asAndroidBitmap()
            try { assertEquals("$pack/$mode chosen versus applied", selected, button.getPixel(button.width / 8, button.height / 2)) }
            finally { button.recycle() }
            val hero = rule.onNodeWithTag("theme-preview-hero").captureToImage().asAndroidBitmap()
            try { assertEquals("$pack/$mode vibrant card colour", selected, hero.getPixel(hero.width / 2, hero.height - 5)) }
            finally { hero.recycle() }
        }
    }

    @Test fun pickerUsesColourFieldHueSwatchesAndLiveCircleWithoutHexInput() {
        var saved by mutableStateOf("")
        rule.setContent { MaterialTheme { CustomAccentControl(saved, "midnight_system") { saved = it } } }
        fun assertPreview(expected: Color) {
            rule.onNodeWithTag("custom-accent-preview").assertIsDisplayed()
            val bitmap = rule.onNodeWithTag("custom-accent-preview").captureToImage().asAndroidBitmap()
            try { assertEquals(expected.toArgb(), bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)) }
            finally { bitmap.recycle() }
        }
        rule.onNodeWithTag("choose-custom-accent").performClick()
        rule.onNodeWithText("Hex colour").assertDoesNotExist()
        rule.onNodeWithTag("custom-accent-hex").assertDoesNotExist()
        rule.onNodeWithTag("custom-accent-hue").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(240f) }
        rule.onNodeWithTag("custom-accent-field").performTouchInput {
            swipe(Offset(width * .25f, height * .75f), Offset(width - .1f, .1f), durationMillis = 300)
        }
        assertPreview(Color.Blue)
        rule.onNodeWithTag("apply-custom-accent").performClick()
        rule.runOnIdle { assertEquals("#0000FF", saved) }
        rule.onNodeWithText("#0000FF").assertDoesNotExist()
        rule.onNodeWithTag("choose-custom-accent").performClick()
        rule.onNodeWithTag("custom-accent-hue").performTouchInput {
            swipe(Offset(width * .8f, center.y), Offset(width / 3f, center.y), durationMillis = 300)
        }
        assertPreview(Color.Green)
        rule.onNodeWithText("Cancel").performClick()
        rule.runOnIdle { assertEquals("#0000FF", saved) }
        rule.onNodeWithTag("choose-custom-accent").performClick()
        rule.onNodeWithTag("custom-accent-preset-black").performScrollTo().performClick()
        assertPreview(Color.Black)
        rule.onNodeWithTag("apply-custom-accent").performClick()
        rule.runOnIdle { assertEquals("#000000", saved) }
        rule.onNodeWithTag("reset-custom-accent").performClick()
        rule.runOnIdle { assertEquals("", saved) }
    }

    @Test fun customAccentPersistsThroughTheAndroidRepository() {
        val repository = DocumentRepository(InstrumentationRegistry.getInstrumentation().targetContext)
        val previous = repository.loadReaderSettings()
        try {
            repository.saveReaderSettings(previous.copy(customAccentColor = "#12abef", themePackId = "one_ui", themeId = "github_system"))
            val loaded = repository.loadReaderSettings()
            assertEquals("#12ABEF", loaded.customAccentColor)
            assertEquals("one_ui", loaded.themePackId)
            assertEquals("github_system", loaded.themeId)
        } finally { repository.saveReaderSettings(previous) }
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun displaySettingsShowsCustomColourAcrossEveryPackInLightAndDark() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var settings by mutableStateOf(ReaderSettings(customAccentColor = "#12ABEF", reduceMotion = true))
        rule.setContent {
            CompositionLocalProvider(LocalVeritasPackId provides settings.themePackId) {
                MaterialTheme(colorScheme = veritasReaderColorScheme(settings, context), shapes = veritasPackShapes(settings.themePackId)) {
                    ReaderSettingsDialog(settings, {}, {}, {}, {}, {}, {}, {}, {}, onReadingSettingsChange = { settings = it })
                }
            }
        }
        val directory = java.io.File(context.getExternalFilesDir(null), "CustomAppearanceChecks").apply { mkdirs() }
        fun capture(name: String) {
            rule.waitForIdle()
            // Platform window fades and SurfaceFlinger commits outlive Compose idleness.
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            android.os.SystemClock.sleep(500)
            // Nested Compose dialogs have their own surfaces. Copy the focused dialog's
            // window, excluding the empty host activity and other apps' overlays.
            val window = rule.runOnIdle {
                fun dialogWindow(view: android.view.View): android.view.Window? {
                    if (view is DialogWindowProvider) return view.window
                    if (view is android.view.ViewGroup) for (index in 0 until view.childCount) {
                        dialogWindow(view.getChildAt(index))?.let { return it }
                    }
                    return null
                }
                android.view.inspector.WindowInspector.getGlobalWindowViews().asReversed()
                    .filter { it.hasWindowFocus() }.firstNotNullOfOrNull(::dialogWindow)
                    ?: error("No focused appearance dialog window")
            }
            val bitmap = android.graphics.Bitmap.createBitmap(window.decorView.width, window.decorView.height,
                android.graphics.Bitmap.Config.ARGB_8888)
            val copied = java.util.concurrent.CountDownLatch(1)
            var result = -1
            android.view.PixelCopy.request(window, bitmap, { result = it; copied.countDown() },
                android.os.Handler(android.os.Looper.getMainLooper()))
            assertTrue("Dialog capture timed out", copied.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals("Dialog capture failed", android.view.PixelCopy.SUCCESS, result)
            val samples = mutableSetOf<Int>()
            for (y in 0 until bitmap.height step 8) for (x in 0 until bitmap.width step 8) samples += bitmap.getPixel(x, y)
            assertTrue("Dialog capture contains no rendered controls", samples.size > 10)
            java.io.File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        for ((pack, _) in VeritasThemePackCatalog.packOptions) for (mode in listOf("light", "dark")) {
            rule.runOnIdle { settings = settings.copy(themePackId = pack, themeId = "midnight_$mode") }
            rule.onNodeWithText("Theme pack: ${VeritasThemePackCatalog.displayName(pack)}").assertExists()
            rule.onNodeWithText("Colour theme: ${VeritasThemeCatalog.displayName("midnight_$mode")}").assertExists()
            rule.onNodeWithTag("choose-custom-accent").performScrollTo().assertIsDisplayed()
            capture("$pack-$mode")
        }
        rule.onNodeWithTag("choose-custom-accent").performClick()
        rule.onNodeWithTag("custom-accent-field").assertIsDisplayed()
        capture("picker")
        rule.onNodeWithText("Cancel").performClick()
    }
}
