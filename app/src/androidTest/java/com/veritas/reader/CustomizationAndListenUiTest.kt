package com.veritas.reader

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.LocalVeritasMotion
import com.veritas.reader.ui.VeritasMotionScheme
import com.veritas.reader.ui.screens.AboutDialog
import com.veritas.reader.ui.screens.AudioModeScreen
import com.veritas.reader.ui.screens.CustomizationSettingsDialog
import com.veritas.reader.ui.NotesSettings
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CustomizationAndListenUiTest {
    @get:Rule val rule = createComposeRule()
    private fun capture(name: String) {
        rule.waitForIdle()
        android.os.SystemClock.sleep(300) // Let the platform dialog-window transition finish.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "VernUiChecks").apply { mkdirs() }
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("Screenshot unavailable")
        java.io.File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun settingsUseReadableColoursInLightDarkAndContrastThemes() {
        var screen by mutableIntStateOf(0)
        var inherited = androidx.compose.ui.graphics.Color.Unspecified
        var expected = androidx.compose.ui.graphics.Color.Unspecified
        val oldTheme = VeritasThemeState.themeId
        val oldMotion = VeritasThemeState.reduceMotion
        try {
            VeritasThemeState.reduceMotion = true
            VeritasThemeState.themeId = "dark"
            rule.setContent {
                VeritasTheme {
                    if (screen == 0) {
                        inherited = androidx.compose.material3.LocalContentColor.current
                        expected = MaterialTheme.colorScheme.onBackground
                        androidx.compose.material3.Text("Default themed text")
                    } else if (screen == 1) AboutDialog(onDismiss = {})
                    else if (screen == 2) com.veritas.reader.ui.screens.AccessibilitySettingsDialog(
                        ReaderSettings(themeId = VeritasThemeState.themeId), {}, {},
                        onToggleAdaptiveCover = {}, onToggleSectionNumbers = {}, onGoalMinutesChange = {},
                        onToggleStreakReminder = {}, onToggleReduceMotion = {}, onToggleReduceTransparency = {})
                    else CustomizationSettingsDialog(ReaderSettings(), {}, {})
                }
            }
            rule.runOnIdle { assertEquals(expected, inherited); screen = 1 }
            rule.onNodeWithText("Vern").assertIsDisplayed()
            capture("about-dark")
            rule.runOnIdle { screen = 2 }
            rule.onNodeWithText("Comfort comes first").assertIsDisplayed()
            capture("accessibility-dark")
            rule.runOnIdle { VeritasThemeState.themeId = "light" }
            capture("accessibility-light")
            rule.runOnIdle { VeritasThemeState.themeId = "dark_high_contrast" }
            capture("accessibility-contrast")
            rule.runOnIdle { VeritasThemeState.themeId = "dark"; screen = 3 }
            capture("customization-dark")
            rule.onNodeWithText("Startup animation").performScrollTo().assertIsDisplayed()
        } finally {
            VeritasThemeState.themeId = oldTheme
            VeritasThemeState.reduceMotion = oldMotion
        }
    }

    @Test fun aboutRemainsScrollableWithLargeText() {
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.5f)) {
                MaterialTheme { AboutDialog(onDismiss = {}) }
            }
        }
        capture("about-large-text")
        rule.onNodeWithText("Check for updates").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Open source licenses").performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithText("Open Source Licenses").assertExists()
    }

    @Test fun notesOverflowHasSeparateAppAndNotesSettings() {
        var appOpened = false
        var notesOpened = false
        rule.setContent {
            MaterialTheme {
                Row {
                    com.veritas.reader.ui.screens.NotesHeaderActions(NotesSettings(), {}, { notesOpened = true }, false, {}, false, {},
                        emptyList(), null, {}, false, {}, {}, onAppSettings = { appOpened = true })
                }
            }
        }
        rule.onNodeWithContentDescription("Notes options").performClick()
        rule.onNodeWithText("Notes settings").assertIsDisplayed()
        rule.onNodeWithText("App settings").performClick()
        rule.runOnIdle { assertTrue(appOpened); assertFalse(notesOpened) }
    }

    @Test fun scatteredStudyCardsSettleAndRemainInteractive() {
        val oldScattered = VeritasThemeState.scatteredCards
        val oldMotion = VeritasThemeState.reduceMotion
        val oldTheme = VeritasThemeState.themeId
        var selected: String? = null
        try {
            VeritasThemeState.scatteredCards = true
            VeritasThemeState.reduceMotion = false
            VeritasThemeState.themeId = "dark"
            rule.mainClock.autoAdvance = false
            rule.setContent {
                VeritasTheme {
                    androidx.compose.material3.Surface {
                        Column(Modifier.fillMaxSize().padding(18.dp)) {
                            com.veritas.reader.ui.screens.StudyHubOverview(com.veritas.reader.ui.ReaderUiState(), emptyList(), 0,
                                StudyReviewSummary(emptyList(), 0), { selected = it }, {}, {}, {}, {}, {}, {})
                        }
                    }
                }
            }
            rule.mainClock.advanceTimeBy(100)
            capture("scattered-study-arriving")
            rule.mainClock.advanceTimeBy(1700)
            capture("scattered-study-settled")
            rule.onNodeWithText("Flashcards").performClick()
            rule.runOnIdle { assertEquals("Flashcards", selected) }
        } finally {
            rule.mainClock.autoAdvance = true
            VeritasThemeState.scatteredCards = oldScattered
            VeritasThemeState.reduceMotion = oldMotion
            VeritasThemeState.themeId = oldTheme
        }
    }

    @Test fun listenReadControlsCollapseAndFullScreenCanExitWithoutSeeking() {
        var seeks = 0
        rule.setContent {
            CompositionLocalProvider(LocalVeritasMotion provides VeritasMotionScheme(reduceMotion = true)) {
                MaterialTheme {
                    Box(Modifier.fillMaxSize().width(320.dp)) {
                        AudioModeScreen("A book", 1, 3, "Second sentence.", false,
                            onPlayPause = {}, onNext = {}, onPrevious = {}, onDismiss = {},
                            documentChunks = listOf("First sentence.", "Second sentence.", "Third sentence."),
                            onSentenceClick = { seeks++ }, contentWindowInsets = WindowInsets(0, 0, 0, 0))
                    }
                }
            }
        }
        capture("listen-rounded-placeholder")
        rule.onNodeWithText("Read", useUnmergedTree = true).performClick()
        rule.onNodeWithContentDescription("Collapse player controls").performClick()
        rule.onNodeWithContentDescription("Playback speed").assertDoesNotExist()
        capture("listen-collapsed")
        rule.onNodeWithContentDescription("More options").performClick()
        rule.onNodeWithText("Full screen reading").performClick()
        rule.onNodeWithContentDescription("Exit full screen reading").assertIsDisplayed()
        capture("listen-fullscreen")
        rule.onNodeWithContentDescription("Exit full screen reading").performClick()
        rule.onAllNodesWithContentDescription("Expand player controls")[0].performClick()
        rule.onNodeWithContentDescription("Playback speed").assertIsDisplayed()
        rule.runOnIdle { assertEquals("Visibility controls must not move narration", 0, seeks) }
    }

    @Test fun customizationChoicesPersistAndInvalidValuesFallBack() {
        var settings by mutableStateOf(ReaderSettings())
        rule.setContent { MaterialTheme { CustomizationSettingsDialog(settings, { settings = it }, {}) } }
        rule.onNodeWithTag("icon-palette-forest").performScrollTo().performClick()
        rule.onNodeWithTag("customization-rise").performScrollTo().performClick()
        rule.onNodeWithTag("scattered-cards-switch").performScrollTo().performClick()
        rule.onNodeWithTag("customization-flip").performScrollTo().performClick()
        capture("customization-page-motion")
        rule.runOnIdle {
            assertEquals("forest", settings.launcherPalette)
            assertEquals("rise", settings.splashAnimation)
            assertTrue(settings.scatteredCards)
            assertEquals("flip", settings.pageTurnStyle)
            val restored = ReaderSettings.fromJson(settings.toJson())
            assertEquals(settings.launcherPalette, restored.launcherPalette)
            assertEquals(settings.splashAnimation, restored.splashAnimation)
            assertEquals(settings.scatteredCards, restored.scatteredCards)
            assertEquals(settings.pageTurnStyle, restored.pageTurnStyle)
            val fallback = ReaderSettings.fromJson(JSONObject().put("launcherPalette", "bad").put("splashAnimation", "bad").put("pageTurnStyle", "bad"))
            assertEquals("indigo", fallback.launcherPalette)
            assertEquals("bloom", fallback.splashAnimation)
            assertEquals("slide", fallback.pageTurnStyle)
            val keys = listOf("book-one", "book-two", "note-three", "Aa", "BB", "")
            keys.forEach {
                val pose = VernCustomization.cardPose(it)
                assertEquals(pose, VernCustomization.cardPose(it))
                assertTrue(kotlin.math.abs(pose.first) <= 5.41f)
                assertTrue(kotlin.math.abs(pose.second) <= 7.21f)
                assertTrue(kotlin.math.abs(pose.third) <= 12.01f)
            }
        }
    }

    @Test fun paletteChangesKeepExactlyOneLaunchableEntry() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pm = context.packageManager
        try {
            for (id in VernCustomization.palettes.map { it.id }) {
                VernCustomization.applyLauncherPalette(context, ReaderSettings(launcherPalette = id))
                val launchers = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0)
                assertEquals("One launcher for $id", 1, launchers.size)
                assertTrue(launchers.single().activityInfo.name.endsWith("Launcher${id.replaceFirstChar { it.uppercase() }}"))
            }
        } finally { VernCustomization.applyLauncherPalette(context, ReaderSettings()) }
    }

    @Test fun staticStartupChoicesSelectStaticSystemSplashAndKeepOneIcon() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pm = context.packageManager
        try {
            for (style in listOf("fade", "rise", "none")) {
                VernCustomization.applyLauncherPalette(context, ReaderSettings(launcherPalette = "ocean", splashAnimation = style))
                val launchers = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0)
                assertEquals(1, launchers.size)
                assertTrue(launchers.single().activityInfo.name.endsWith("LauncherOceanStatic"))
            }
            val activity = pm.getActivityInfo(android.content.ComponentName(context.packageName, "com.veritas.reader.StartupOceanStaticActivity"), 0)
            assertEquals(R.style.Theme_Vern_StartupOceanStatic, activity.theme)
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                val theme = context.resources.newTheme().apply { applyStyle(activity.theme, true) }
                val attributes = theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowSplashScreenBackground, android.R.attr.windowSplashScreenAnimatedIcon))
                try {
                    assertEquals(0xFF145B7A.toInt(), attributes.getColor(0, 0))
                    val drawable = attributes.getDrawable(1)
                    assertNotNull(drawable)
                    assertFalse(drawable is android.graphics.drawable.Animatable)
                } finally { attributes.recycle() }
            }
            VernCustomization.applyLauncherPalette(context, ReaderSettings(reduceMotion = true))
            val launchers = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0)
            assertEquals(1, launchers.size)
            assertTrue(launchers.single().activityInfo.name.endsWith("LauncherIndigoStatic"))
        } finally { VernCustomization.applyLauncherPalette(context, ReaderSettings()) }
    }
}
