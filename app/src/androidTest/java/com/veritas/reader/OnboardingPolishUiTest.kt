package com.veritas.reader

import android.graphics.Bitmap
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.LocalVeritasMotion
import com.veritas.reader.ui.VeritasMotionScheme
import com.veritas.reader.ui.screens.RevampedOnboardingFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class OnboardingPolishUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun listeningSpeedSurvivesBackAndContinueWithMotion() = exerciseSetup(false)
    @Test fun reducedMotionKeepsTheSameSetupControlsAndSavedTuning() = exerciseSetup(true)

    private fun exerciseSetup(reduceMotion: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        var completed = false
        var savedRate = 0f
        var savedPitch = 0f
        rule.setContent {
            CompositionLocalProvider(LocalVeritasMotion provides VeritasMotionScheme(reduceMotion)) {
                MaterialTheme {
                    RevampedOnboardingFlow("Reader", "Books & Novels", "chooser",
                        onComplete = { _, _, _, rate, pitch -> completed = true; savedRate = rate; savedPitch = pitch },
                        onDismiss = {}, initialPitch = 1.12f)
                }
            }
        }
        rule.onNodeWithText("Get started").performClick()
        rule.onNodeWithText("What fills your bookshelf?").assertExists()
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
        rule.onNodeWithText("Open my library").performClick()
        rule.runOnIdle {
            assertTrue(completed)
            assertEquals(1.25f, savedRate, .001f)
            assertEquals(1.12f, savedPitch, .001f)
        }
    }
}
