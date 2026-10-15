package com.veritas.reader

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.LocalVeritasMotion
import com.veritas.reader.ui.VeritasMotionScheme
import com.veritas.reader.ui.screens.AudioModeScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ListenRedesignUiTest {
    @get:Rule val rule = createComposeRule()
    @Test fun safeAreaKeepsControlsClearAndDoesNotDoubleConsumedInsets() {
        var parentConsumes by mutableStateOf(false)
        var topInset = 0; var bottomInset = 0
        rule.setContent {
            val density = LocalDensity.current
            topInset = with(density) { 32.dp.roundToPx() }
            bottomInset = with(density) { 24.dp.roundToPx() }
            val insets = WindowInsets(0, topInset, 0, bottomInset)
            MaterialTheme {
                Box(Modifier.fillMaxSize().testTag("safe-frame")) {
                    Box(if (parentConsumes) Modifier.fillMaxSize().windowInsetsPadding(insets) else Modifier.fillMaxSize()) {
                        AudioModeScreen("Safe area fixture", 0, 3, "Sentence", false,
                            onPlayPause = {}, onNext = {}, onPrevious = {}, onDismiss = {}, contentWindowInsets = insets)
                    }
                }
            }
        }
        val frame = rule.onNodeWithTag("safe-frame").fetchSemanticsNode().boundsInRoot
        val back = rule.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot
        val timer = rule.onNodeWithContentDescription("Sleep timer").fetchSemanticsNode().boundsInRoot
        assertTrue("Header clears status bar with breathing room", back.top > frame.top + topInset)
        assertTrue("Tools clear navigation bar with breathing room", timer.bottom < frame.bottom - bottomInset)
        rule.runOnIdle { parentConsumes = true }
        val consumedBack = rule.onNodeWithContentDescription("Back").fetchSemanticsNode().boundsInRoot
        val consumedTimer = rule.onNodeWithContentDescription("Sleep timer").fetchSemanticsNode().boundsInRoot
        assertEquals(back.top, consumedBack.top, 1f)
        assertEquals(timer.bottom, consumedTimer.bottom, 1f)
    }

    @Test fun glassModesRetainToolsScrubbingAndSmoothSentenceSelection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks") || context.packageName.endsWith(".iconcheck"))
        val cover = File(context.cacheDir, "listen-cover-fixture.jpg")
        context.assets.open("covers/classic_pilgrims_progress.jpg").use { input -> cover.outputStream().use { input.copyTo(it) } }
        val oldGlass = VeritasThemeState.glassFloatingControls
        val oldReduce = VeritasThemeState.reduceTransparency
        val oldTheme = VeritasThemeState.themeId
        var pack by mutableStateOf("liquid_glass")
        var dark by mutableStateOf(true)
        var reduced by mutableStateOf(false)
        var compact by mutableStateOf(false)
        var index by mutableIntStateOf(0)
        var playing by mutableStateOf(false)
        var speed by mutableFloatStateOf(1f)
        var bookmarked by mutableStateOf(false)
        var voices = 0; var timers = 0; var narration = 0; var exports = 0
        val sentences = listOf("A quiet moment to begin reading.", "The next sentence moves smoothly into focus, with stable line breaks and a gentle change in emphasis.", "The final sentence stays easy to reach.")
        try {
            VeritasThemeState.glassFloatingControls = true
            VeritasThemeState.reduceTransparency = false
            VeritasThemeState.themeId = "amoled"
            rule.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalVeritasPackId provides pack,
                    LocalVeritasMotion provides VeritasMotionScheme(reduced),
                    LocalDensity provides Density(density.density, if (compact) 1.5f else 1f)) {
                    MaterialTheme(colorScheme = if (dark) darkColorScheme(background = Color(0xFF020510)) else lightColorScheme()) {
                        Box(if (compact) Modifier.width(320.dp).fillMaxSize() else Modifier.fillMaxSize()) {
                            AudioModeScreen("The art of attentive reading", index, sentences.size, sentences[index], playing,
                                coverFile = cover, onPlayPause = { playing = !playing }, onNext = { index = (index + 1).coerceAtMost(2) },
                                onPrevious = { index = (index - 1).coerceAtLeast(0) }, onDismiss = {},
                                isBookmarked = bookmarked, onToggleBookmark = { bookmarked = !bookmarked },
                                rate = speed, onRateChange = { speed = it }, documentChunks = sentences,
                                onSentenceClick = { index = it }, onOpenVoiceStudio = { voices++ }, onOpenSleepTimer = { timers++ },
                                onOpenNarrationStudio = { narration++ }, onExportAudio = { exports++ })
                        }
                    }
                }
            }
            listOf("liquid_glass", "material_you").forEach { mode ->
                rule.runOnIdle { index = 0; playing = false; bookmarked = false; speed = 1f; pack = mode; dark = mode == "liquid_glass"; VeritasThemeState.themeId = if (dark) "amoled" else "light" }
                rule.onNodeWithTag("listen-artwork").assertIsDisplayed()
                rule.onNodeWithContentDescription("Play").performClick()
                assertTrue(playing)
                rule.onNodeWithContentDescription("Pause").performClick()
                rule.onNodeWithContentDescription("Next sentence").performClick()
                assertEquals(1, index)
                rule.onNodeWithContentDescription("Previous sentence").performClick()
                assertEquals(0, index)
                rule.onNodeWithContentDescription("Reading position").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(2f) }
                assertEquals(2, index)
                rule.onNodeWithContentDescription("Add bookmark").performClick()
                rule.onNodeWithContentDescription("Remove bookmark").performClick()
                rule.onNodeWithContentDescription("Playback speed").performClick()
                rule.onNodeWithText("1.5×").performClick()
                assertEquals(1.5f, speed)
                rule.onNodeWithContentDescription("Voice & language").performClick()
                rule.onNodeWithContentDescription("Sleep timer").performClick()
                rule.onNodeWithContentDescription("More options").performClick()
                rule.onNodeWithText("Narration mode").performClick()
                rule.onNodeWithContentDescription("More options").performClick()
                rule.onNodeWithText("Export audio").performClick()
                val output = File(context.getExternalFilesDir(null), "remaining-fixes/listen-$mode.png").apply { parentFile!!.mkdirs() }
                output.outputStream().use { rule.onNodeWithTag("listen-page").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
                rule.onNodeWithText("Read", useUnmergedTree = true).performClick()
                rule.onNodeWithTag("listen-sentences").assertIsDisplayed()
                rule.onNodeWithText(sentences[2]).assertIsSelected()
                rule.runOnIdle { index = 1 }
                rule.onNodeWithText(sentences[1]).assertIsSelected().assertIsDisplayed()
                val read = File(context.getExternalFilesDir(null), "remaining-fixes/listen-read-$mode.png")
                read.outputStream().use { rule.onNodeWithTag("listen-page").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
                rule.onNodeWithText("Player", useUnmergedTree = true).performClick()
            }
            assertEquals(2, voices); assertEquals(2, timers); assertEquals(2, narration); assertEquals(2, exports)
            rule.runOnIdle { compact = true; reduced = true; VeritasThemeState.reduceTransparency = true }
            rule.onNodeWithContentDescription("Play").assertIsDisplayed().performClick()
            rule.onNodeWithContentDescription("Sleep timer").assertIsDisplayed()
            rule.onNodeWithText("Read", useUnmergedTree = true).performClick()
            rule.runOnIdle { index = 2 }
            rule.onNodeWithText(sentences[2]).assertIsSelected().assertIsDisplayed()
        } finally {
            cover.delete()
            rule.runOnIdle {
                PlaybackStateStore.finishPreparing()
                VeritasThemeState.glassFloatingControls = oldGlass
                VeritasThemeState.reduceTransparency = oldReduce
                VeritasThemeState.themeId = oldTheme
            }
        }
    }
}
