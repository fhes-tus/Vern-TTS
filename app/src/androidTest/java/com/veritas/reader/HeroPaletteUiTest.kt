package com.veritas.reader

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.LocalVeritasMotion
import com.veritas.reader.ui.VeritasMotionScheme
import com.veritas.reader.ui.screens.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class HeroPaletteUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun homeAndThemePreviewShareTheSameThemeFollowingHeroColors() {
        var theme by mutableStateOf("light")
        var vibrant by mutableStateOf(true)
        var expected = Color.Unspecified
        var opened = 0
        val previousAmoled = VeritasThemeState.amoledMode
        val document = SavedDocument("hero-colors", "The Complete Sherlock Holmes", "fixture.txt", "EPUB", 0, 0, 16, 100, 100, "")
        rule.setContent {
            val scheme = veritasReaderColorScheme(ReaderSettings(themeId = theme, amoledMode = VeritasThemeState.amoledMode), LocalContext.current)
            val hero = veritasHeroColors(scheme, vibrant)
            SideEffect { expected = hero.container }
            MaterialTheme(colorScheme = scheme) {
                CompositionLocalProvider(LocalVeritasPackId provides "veritas_media", LocalVeritasMotion provides VeritasMotionScheme(true)) {
                    Column(Modifier.fillMaxSize().background(scheme.background).verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        VeritasHomeHeroCard(document, null, hero, { opened++ }, {}, {}, {})
                        ThemePreviewCard("veritas_media", theme, vibrant)
                    }
                }
            }
        }
        try {
            val choices = listOf("light" to false, "dark" to false, "midnight_light" to false,
                "midnight_dark" to false, "midnight_dark" to true, "dracula_light" to false, "dracula" to true,
                "github_light" to false, "github_dark" to true, "bw_gradient_light" to false, "bw_gradient_dark" to true, "neon" to true)
            for ((id, black) in choices) {
                for (rich in listOf(true, false)) {
                rule.runOnIdle { theme = id; VeritasThemeState.amoledMode = black; vibrant = rich }
                for (tag in listOf("home-hero-card", "theme-preview-hero")) {
                    rule.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
                    val bitmap = rule.onNodeWithTag(tag, useUnmergedTree = true).captureToImage().asAndroidBitmap()
                    val actual = bitmap.getPixel(bitmap.width / 2, bitmap.height - 8)
                    for (shift in listOf(0, 8, 16)) assertTrue("$id/$black $tag expected ${expected.toArgb().toUInt().toString(16)}, got ${actual.toUInt().toString(16)}",
                        kotlin.math.abs((expected.toArgb() shr shift and 255) - (actual shr shift and 255)) <= 3)
                    if (tag == "home-hero-card") {
                        val ring = bitmap.getPixel((bitmap.width * .78f).toInt(), (bitmap.height * .24f).toInt())
                        assertNotEquals("Keep the original hero ring artwork", expected.toArgb(), ring)
                    }
                    save(bitmap, "$tag-$id-${if (black) "amoled" else "normal"}-${if (rich) "vibrant" else "subtle"}")
                    bitmap.recycle()
                }
                }
            }
            rule.onNodeWithText("Continue", useUnmergedTree = true).performScrollTo().performClick()
            assertEquals(1, opened)
            rule.runOnIdle { vibrant = false }
            rule.onNodeWithTag("home-hero-card", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        } finally { rule.runOnIdle { VeritasThemeState.amoledMode = previousAmoled } }
    }

    @Test fun originalHeroRingsAnimateAndRespectReducedMotion() {
        var reduced by mutableStateOf(false)
        val document = SavedDocument("hero-motion", "The Complete Sherlock Holmes", "fixture.txt", "EPUB", 0, 0, 16, 100, 100, "")
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val scheme = veritasDarkSurfaceScheme(darkColorScheme(primary = Color(0xFFA79BFF)), true)
            MaterialTheme(colorScheme = scheme) {
                CompositionLocalProvider(LocalVeritasPackId provides "veritas_media", LocalLibraryPageVisible provides true,
                    LocalVeritasMotion provides VeritasMotionScheme(reduced)) {
                    VeritasHomeHeroCard(document, null, veritasHeroColors(scheme, true), {}, {}, {}, {})
                }
            }
        }
        fun ringPixels(): Int {
            val bitmap = rule.onNodeWithTag("home-hero-card", useUnmergedTree = true).captureToImage().asAndroidBitmap()
            var hash = 1
            for (y in 8 until (bitmap.height * .28f).toInt() step 3)
                for (x in (bitmap.width * .72f).toInt() until bitmap.width - 10 step 3) hash = 31 * hash + bitmap.getPixel(x, y)
            bitmap.recycle()
            return hash
        }
        rule.mainClock.advanceTimeBy(64)
        val first = ringPixels()
        rule.mainClock.advanceTimeBy(1050)
        assertNotEquals("Original rings should move", first, ringPixels())
        rule.runOnIdle { reduced = true }
        rule.mainClock.advanceTimeBy(64)
        val still = ringPixels()
        rule.mainClock.advanceTimeBy(1050)
        assertEquals("Reduced motion should retain a static ring", still, ringPixels())
        rule.mainClock.autoAdvance = true
    }

    private fun save(bitmap: Bitmap, name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.getExternalFilesDir(null), "hero-colors/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
