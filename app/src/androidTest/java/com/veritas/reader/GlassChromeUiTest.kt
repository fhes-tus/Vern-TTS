package com.veritas.reader

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.abs
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.screens.LibraryBottomNavBar
import com.veritas.reader.ui.screens.VeritasHomeTab
import com.veritas.reader.ui.screens.PlayerPanel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class GlassChromeUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun liquidGlassRefractsTheBackdropInsteadOfOnlyBlurringIt() {
        var refraction by mutableStateOf(false)
        var capable = false
        rule.setContent {
            MaterialTheme {
                capable = isDeviceGlassCapable()
                val backdrop = rememberVeritasLayerBackdrop()
                val shape = RoundedCornerShape(40.dp)
                val scheme = MaterialTheme.colorScheme
                val blurPx = with(LocalDensity.current) { 8.dp.toPx() }
                VeritasBackdropProvider(backdrop) {
                    Box(Modifier.fillMaxSize().testTag("refraction-fixture")) {
                        Canvas(Modifier.fillMaxSize().recordVeritasBackdrop(backdrop, true)) {
                            drawRect(Color(0xFF162338))
                            val stripe = 26.dp.toPx()
                            for (i in 0..(size.width / stripe).toInt()) {
                                drawRect(if (i % 2 == 0) Color(0xFFFFB34D) else Color(0xFF348ABC),
                                    topLeft = androidx.compose.ui.geometry.Offset(i * stripe, 0f),
                                    size = androidx.compose.ui.geometry.Size(stripe / 2, size.height))
                            }
                            // Top/bottom lens edges displace the page vertically. A
                            // two-dimensional pattern exposes that displacement too.
                            for (i in 0..(size.height / stripe).toInt()) {
                                drawRect(Color.White.copy(alpha = .65f),
                                    topLeft = androidx.compose.ui.geometry.Offset(0f, i * stripe),
                                    size = androidx.compose.ui.geometry.Size(size.width, stripe / 3))
                            }
                        }
                        Box(Modifier.align(Alignment.Center).size(280.dp, 80.dp).testTag("glass-sample")
                            .then(if (refraction) Modifier.veritasGlassBackdrop(shape, true, surfaceOpacity = .36f)
                            else Modifier.drawPlainBackdrop(backdrop, shape = { shape }, effects = {
                                vibrancy(); colorControls(brightness = .05f, contrast = 1f, saturation = 1.5f); blur(blurPx)
                            }, onDrawSurface = { drawRect(scheme.surface.copy(alpha = .36f * .78f)) })))
                    }
                }
            }
        }
        assertTrue("This device must support the requested glass material", capable)
        val plain = rule.onNodeWithTag("glass-sample").captureToImage().toPixelMap()
        rule.runOnIdle { refraction = true }
        val refracted = rule.onNodeWithTag("glass-sample").captureToImage().toPixelMap()
        var changed = 0; var samples = 0
        // Sample inside the curved lens band, away from its 1dp highlight.
        // The centre of this rounded capsule is intentionally a flat pane.
        for (y in plain.height / 10 until plain.height / 5 step 2) {
            for (x in plain.width / 8 until plain.width * 7 / 8 step 3) {
                val a = plain[x, y]; val b = refracted[x, y]
                if (abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) > .06f) changed++
                samples++
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "remaining-fixes/refractive-glass.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { rule.onNodeWithTag("refraction-fixture").captureToImage().asAndroidBitmap()
            .compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("Curved lens must visibly distort the page beyond a blurred tint ($changed/$samples)", changed > samples / 20)
    }

    @Test fun readerPanelsKeepPlaybackAndExpandedControlsInBothGlassModes() {
        val originalGlass = VeritasThemeState.glassFloatingControls
        val originalReduce = VeritasThemeState.reduceTransparency
        val originalTheme = VeritasThemeState.themeId
        var pack by mutableStateOf("liquid_glass")
        var actualDocument by mutableStateOf(false)
        var played = 0
        try {
            VeritasThemeState.reduceTransparency = false
            VeritasThemeState.themeId = "light"
            rule.setContent {
                CompositionLocalProvider(LocalVeritasPackId provides pack) {
                    MaterialTheme {
                        val backdrop = rememberVeritasLayerBackdrop()
                        VeritasBackdropProvider(backdrop) {
                            Box(Modifier.fillMaxSize().testTag("reader-fixture")) {
                                androidx.compose.foundation.Canvas(Modifier.fillMaxSize().recordVeritasBackdrop(backdrop, true)) {
                                    drawRect(Color(0xFF35A8C8))
                                    for (stripe in 0..30) drawRect(Color.White,
                                        topLeft = androidx.compose.ui.geometry.Offset(0f, stripe * 80f),
                                        size = androidx.compose.ui.geometry.Size(size.width, 22f))
                                }
                                Box(Modifier.align(Alignment.BottomCenter)) {
                                    key(pack, actualDocument) {
                                        if (actualDocument) DocPlayerPanel(
                                            false, "Ready to read", 1f, 1f, 18, queueCount = 0,
                                            canGoPrevious = true, canGoNext = true, onPrevious = {}, onPlayPause = { played++ }, onNext = {},
                                            onRateChange = {}, onPitchChange = {}, onFontSizeChange = {}, onOpenVoiceStudio = {},
                                            voices = emptyList(), voiceSettings = VoiceSettings(), onVoiceSelected = {}
                                        ) else PlayerPanel(
                                            false, false, "Ready to read", 1f, 1f, 18, queueCount = 0,
                                            canGoPrevious = true, canGoNext = true, onPrevious = {}, onPlayPause = { played++ }, onStop = {}, onNext = {},
                                            onRateChange = {}, onPitchChange = {}, onFontSizeChange = {}, onOpenVoiceStudio = {}, onPlayQueue = {}, onOpenAudioMode = {},
                                            voices = emptyList(), voiceSettings = VoiceSettings(), onVoiceSelected = {}
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            listOf("liquid_glass", "material_you").forEach { mode ->
                listOf(false, true).forEach { actual ->
                    rule.runOnIdle {
                        pack = mode
                        actualDocument = actual
                        VeritasThemeState.glassFloatingControls = mode != "liquid_glass"
                    }
                    rule.waitForIdle()
                    val before = played
                    rule.runOnIdle { PlaybackStateStore.beginPreparing(null) }
                    rule.onNodeWithContentDescription("Cancel starting playback").assertExists()
                    rule.runOnIdle { PlaybackStateStore.finishPreparing() }
                    rule.onNodeWithContentDescription("Play").performClick()
                    assertEquals(before + 1, played)
                    if (!actual) rule.onNodeWithContentDescription("Expand Player Panel").performClick()
                    else {
                        val play = rule.onNodeWithContentDescription("Play").fetchSemanticsNode().boundsInRoot
                        val root = rule.onNodeWithTag("reader-fixture").fetchSemanticsNode().boundsInRoot
                        rule.onNodeWithTag("reader-fixture").performTouchInput {
                            swipe(play.center - root.topLeft, play.center - root.topLeft - androidx.compose.ui.geometry.Offset(0f, 800f), 600)
                        }
                    }
                    rule.waitForIdle()
                    rule.onNodeWithText("Speed", substring = true).assertExists()
                    val context = InstrumentationRegistry.getInstrumentation().targetContext
                    File(context.getExternalFilesDir(null), "reader-${if (actual) "actual" else "text"}-$mode.png").outputStream().use {
                        rule.onNodeWithTag("reader-fixture").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
            }
        } finally {
            rule.runOnIdle {
                VeritasThemeState.glassFloatingControls = originalGlass
                VeritasThemeState.reduceTransparency = originalReduce
                VeritasThemeState.themeId = originalTheme
            }
        }
    }

    @Test fun notesKeepsARealGapAndAllTabsWorkInBothGlassModes() {
        val originalGlass = VeritasThemeState.glassFloatingControls
        val originalReduce = VeritasThemeState.reduceTransparency
        val originalTheme = VeritasThemeState.themeId
        var pack by mutableStateOf("liquid_glass")
        var selected by mutableStateOf(VeritasHomeTab.NOTES)
        try {
            VeritasThemeState.reduceTransparency = false
            VeritasThemeState.themeId = "light"
            rule.setContent {
                CompositionLocalProvider(LocalVeritasPackId provides pack) {
                    MaterialTheme {
                        val backdrop = rememberVeritasLayerBackdrop()
                        VeritasBackdropProvider(backdrop) {
                            Box(Modifier.fillMaxSize().testTag("glass-fixture")) {
                                Box(Modifier.fillMaxSize().recordVeritasBackdrop(backdrop, true).background(Color.Magenta))
                                LibraryBottomNavBar(selected, true, { selected = it }, Modifier.align(Alignment.BottomCenter))
                            }
                        }
                    }
                }
            }
            listOf("liquid_glass", "material_you").forEach { mode ->
                rule.runOnIdle {
                    pack = mode
                    VeritasThemeState.glassFloatingControls = mode != "liquid_glass"
                    selected = VeritasHomeTab.NOTES
                }
                rule.waitForIdle()
                val main = rule.onNodeWithTag("navigation-main-capsule").fetchSemanticsNode().boundsInRoot
                val notes = rule.onNodeWithTag("navigation-notes-capsule").fetchSemanticsNode().boundsInRoot
                assertTrue("Notes must be a separate capsule in $mode", notes.left - main.right > 2f)
                val root = rule.onNodeWithTag("glass-fixture")
                val bounds = root.fetchSemanticsNode().boundsInRoot
                val screenshot = root.captureToImage()
                val pixel = screenshot.toPixelMap()[((notes.left + main.right) / 2 - bounds.left).toInt(), (main.center.y - bounds.top).toInt()]
                assertTrue("The gap must expose magenta page, allowing only a slight edge shadow", pixel.green < .02f && pixel.red > .85f && pixel.blue > .85f)
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                File(context.getExternalFilesDir(null), "nav-$mode.png").outputStream().use {
                    screenshot.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                rule.onNodeWithContentDescription("Home").performClick()
                rule.waitForIdle()
                rule.onNodeWithTag("navigation-notes-capsule").assertDoesNotExist()
                listOf("Library", "Study", "Notes").forEach { label ->
                    rule.onNodeWithContentDescription(label).performClick()
                    assertEquals(label, selected.label)
                }
            }
        } finally {
            rule.runOnIdle {
                VeritasThemeState.glassFloatingControls = originalGlass
                VeritasThemeState.reduceTransparency = originalReduce
                VeritasThemeState.themeId = originalTheme
            }
        }
    }
}
