package com.veritas.reader

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.*
import com.veritas.reader.ui.screens.*
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StudyHierarchyUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun dueReviewAndMaterialShortcutsKeepTheirActionsAcrossDarkModes() {
        var amoled by mutableStateOf(false)
        var selected = ""
        var reviews = 0
        val cards = List(8) { FlashcardProgress("fixture-$it", front = "Question $it", back = "Answer $it") }
        rule.setContent {
            val colors = veritasDarkSurfaceScheme(darkColorScheme(primary = Color(0xFF58A6FF), onPrimary = Color(0xFF0D1117)), amoled)
            MaterialTheme(colorScheme = colors, typography = veritasTypography(VeritasUiFont.CRISP)) {
                CompositionLocalProvider(LocalVeritasPackId provides "material_you", LocalVeritasMotion provides VeritasMotionScheme(true)) {
                    Column(Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text("Study Hub", style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                        StudyHubOverview(ReaderUiState(flashcards = cards), listOf(FlashcardSet("fixture", "Ideas worth keeping", cards)), 32,
                            StudyReviewSummary(cards, 0), { selected = it }, { reviews++ }, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        rule.onNodeWithText("8 cards ready").assertIsDisplayed()
        rule.onNodeWithText("Not due").assertDoesNotExist()
        rule.onNodeWithText("0%").assertDoesNotExist()
        rule.onNodeWithText("Start review").performClick()
        assertEquals(1, reviews)
        rule.onNodeWithText("Vocabulary").performScrollTo().performClick()
        assertEquals("Vocab", selected)
        rule.mainClock.advanceTimeBy(1_000)
        capture("study-dark")
        rule.runOnIdle { amoled = true }
        capture("study-amoled")
    }

    @Test fun emptyReviewIsHonestAndUsableWithLargeTextInNarrowWindow() {
        var opened = false
        rule.setContent {
            val colors = veritasDarkSurfaceScheme(darkColorScheme(), false)
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f),
                LocalVeritasPackId provides "material_you", LocalVeritasMotion provides VeritasMotionScheme(true)) {
                MaterialTheme(colorScheme = colors, typography = veritasTypography(VeritasUiFont.CRISP)) {
                    Column(Modifier.width(320.dp).background(colors.background).verticalScroll(rememberScrollState()).padding(16.dp)) {
                        StudyDailyReviewHeroCard(0, { opened = true })
                    }
                }
            }
        }
        rule.onNodeWithText("All caught up").assertIsDisplayed()
        rule.onNodeWithText("Browse cards").assertIsDisplayed().performClick()
        assertEquals(true, opened)
        rule.onNodeWithText("Start review").assertDoesNotExist()
        capture("study-large-text")
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = File(context.getExternalFilesDir(null), "visual-hierarchy/$name.png").apply { parentFile!!.mkdirs() }
        image.outputStream().use { rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
