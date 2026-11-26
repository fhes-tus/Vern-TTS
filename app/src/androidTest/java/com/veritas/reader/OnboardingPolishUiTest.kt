package com.veritas.reader

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.LocalVeritasMotion
import com.veritas.reader.ui.VeritasMotionScheme
import com.veritas.reader.ui.screens.FirstChapterPrompt
import com.veritas.reader.ui.screens.OnboardingPaper
import com.veritas.reader.ui.screens.RevampedOnboardingFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class OnboardingPolishUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun listeningSpeedSurvivesBackAndContinueWithMotion() = exerciseSetup(false)
    @Test fun reducedMotionKeepsTheSameSetupControlsAndSavedTuning() = exerciseSetup(true)

    @Test fun setupKeepsTheSameLightPaperAndIllustrationInBothThemes() {
        val dark = mutableStateOf(false)
        rule.setContent {
            MaterialTheme(colorScheme = if (dark.value) darkColorScheme() else lightColorScheme()) {
                RevampedOnboardingFlow("Reader", "Books & Novels", "chooser", onComplete = { _, _, _, _, _ -> }, onDismiss = {})
            }
        }
        val art = rule.onNodeWithTag("onboarding-artwork")
        val original = art.captureToImage().toPixelMap()
        val picture = original[original.width / 2, original.height / 2].toArgb()
        rule.runOnIdle { dark.value = true }
        val pixels = art.captureToImage().toPixelMap()
        assertEquals(picture, pixels[pixels.width / 2, pixels.height / 2].toArgb())
        val edge = pixels[pixels.width / 2, 0].toArgb()
        val canvas = rule.onNodeWithTag("onboarding-paper").captureToImage().toPixelMap()
        assertEquals(OnboardingPaper.toArgb(), canvas[canvas.width / 2, 0].toArgb())
        for (shift in listOf(0, 8, 16)) {
            assertTrue(kotlin.math.abs((edge shr shift and 255) - (OnboardingPaper.toArgb() shr shift and 255)) <= 2)
        }
    }

    @Test fun highlightedFirstChapterCardOpensTheChapter() {
        var opened = false
        var dismissed = false
        rule.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    FirstChapterPrompt(
                        target = Rect(24f, 88f, 296f, 184f),
                        onOpen = { opened = true },
                        onDismiss = { dismissed = true }
                    )
                }
            }
        }
        rule.onNodeWithContentDescription("First Chapter").assertHasClickAction().performClick()
        rule.runOnIdle {
            assertTrue(opened)
            assertFalse(dismissed)
        }
    }

    private fun exerciseSetup(reduceMotion: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        var completed = false
        var savedName = ""
        var savedRate = 0f
        var savedPitch = 0f
        rule.setContent {
            CompositionLocalProvider(LocalVeritasMotion provides VeritasMotionScheme(reduceMotion)) {
                MaterialTheme {
                    RevampedOnboardingFlow("Reader", "Books & Novels", "chooser",
                        onComplete = { name, _, _, rate, pitch ->
                            completed = true
                            savedName = name
                            savedRate = rate
                            savedPitch = pitch
                        },
                        onDismiss = {}, initialPitch = 1.12f)
                }
            }
        }
        rule.onNodeWithText("Get started").performClick()
        rule.onNodeWithText("What do you usually read?").assertExists()
        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithContentDescription("Listening speed").performScrollTo().assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1.25f) }
        rule.onNodeWithText("Listening speed · 1.25×").assertExists()
        rule.onRoot().captureToImage().asAndroidBitmap().let { image ->
            File(context.getExternalFilesDir(null), "onboarding-listening-${if (reduceMotion) "reduced" else "motion"}.png")
                .outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithContentDescription("Previous setup step").performClick()
        rule.onNodeWithText("Listening speed · 1.25×").assertExists()
        rule.onNodeWithText("Continue").performClick()
        rule.onNodeWithText("Continue").performClick()
        rule.onNode(hasSetTextAction()).performScrollTo().performTextInput("Ama")
        rule.onNodeWithText("Finish setup").performClick()
        rule.runOnIdle {
            assertTrue(completed)
            assertEquals("Ama", savedName)
            assertEquals(1.25f, savedRate, .001f)
            assertEquals(1.12f, savedPitch, .001f)
        }
    }
}
