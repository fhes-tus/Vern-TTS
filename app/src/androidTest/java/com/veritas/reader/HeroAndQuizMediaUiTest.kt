package com.veritas.reader

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.LocalVeritasMotion
import com.veritas.reader.ui.VeritasMotionScheme
import com.veritas.reader.ui.screens.VeritasHomeHeroCard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class HeroAndQuizMediaUiTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also { check(it.packageName.endsWith(".checks")) }

    @Test fun heroKeepsItsActionsAtCompactWidthAndLargeTextInLightAndDark() {
        var dark by mutableStateOf(false)
        var reduced by mutableStateOf(false)
        var document by mutableStateOf<SavedDocument?>(SavedDocument("hero-fixture", "The Complete Sherlock Holmes", "fixture.txt", "PDF", 0, 0, 16, 100, 100, ""))
        var opened = 0; var cleared = 0; var added = 0
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f),
                LocalVeritasMotion provides VeritasMotionScheme(reduced)) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    val scheme = MaterialTheme.colorScheme
                    Column(Modifier.width(320.dp).verticalScroll(rememberScrollState()).background(scheme.surface)) {
                        Box(Modifier.padding(12.dp).testTag("hero-capture")) {
                            VeritasHomeHeroCard(document, null,
                                veritasHeroColors(scheme, true),
                                { opened++ }, { cleared++ }, { added++ }, {})
                        }
                    }
                }
            }
        }
        listOf(false to false, true to false, true to true).forEach { (night, reduce) ->
            rule.runOnIdle { dark = night; reduced = reduce }
            rule.onNodeWithText("Continue reading").assertIsDisplayed()
            rule.onAllNodesWithText("The Complete Sherlock Holmes").assertCountEquals(2)
            // Index 16 is the seventeenth sentence in the 100-sentence fixture.
            rule.onNodeWithText("17% through").performScrollTo().assertIsDisplayed()
            rule.onNodeWithText("Continue", useUnmergedTree = true).performScrollTo().performClick()
            val buttonText = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            rule.onNodeWithText("Continue", useUnmergedTree = true).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(buttonText) }
            assertEquals(1, buttonText.single().lineCount)
            assertFalse(buttonText.single().isLineEllipsized(0))
            assertTrue(buttonText.single().getLineRight(0) - buttonText.single().getLineLeft(0) <= buttonText.single().size.width + 1f)
            rule.onNodeWithContentDescription("Dismiss continue reading").performScrollTo().performClick()
            val bounds = rule.onNodeWithText("Continue", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.width > 0 && bounds.height > 0)
            val file = File(context.getExternalFilesDir(null), "remaining-fixes/hero-${if (night) "dark" else "light"}-${if (reduce) "reduced" else "animated"}.png").apply { parentFile!!.mkdirs() }
            file.outputStream().use { rule.onNodeWithTag("hero-capture").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        rule.runOnIdle { assertEquals(3, opened); assertEquals(3, cleared); document = null }
        rule.onNodeWithText("Add your first read").performScrollTo().performClick()
        rule.runOnIdle { assertEquals(1, added) }
    }

    @Test fun quizRecordingCanPlayStopAndDetachWithoutDeletingItsFile() {
        val audio = File(context.filesDir, "notes_media/quiz-ui-${UUID.randomUUID()}.wav").apply {
            parentFile!!.mkdirs(); PcmWavWriter(this, 24000).use { it.append(ShortArray(24000 * 10)) }
        }
        val record = managedAttachment(context, audio.path, AttachmentKind.AUDIO, "quiz:fixture:0")
        var question by mutableStateOf(QuizQuestion("Question?", "Yes", listOf("Yes", "No"), attachments = listOf(record)))
        try {
            rule.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                QuizQuestionMedia(question, "quiz:fixture:0") { question = question.copy(attachments = it) }
            } } }
            rule.onNodeWithText("Play question recording").performScrollTo().performClick()
            rule.waitUntil(5000) { rule.onAllNodesWithText("Stop question recording").fetchSemanticsNodes().isNotEmpty() }
            rule.onNodeWithText("Stop question recording").performClick()
            rule.onNodeWithText("Play question recording").assertExists()
            rule.onNodeWithText("Remove attachment").performClick()
            rule.runOnIdle { assertTrue(question.attachments.isEmpty()); assertTrue(audio.exists())
                question = question.copy(attachments = listOf(record.copy(relativePath = "notes_media/missing.wav"))) }
            rule.onNodeWithText("Attachment unavailable. Re-add it or restore the portable ZIP backup.").assertExists()
            rule.onNodeWithText("Remove attachment").performClick()
            rule.runOnIdle { assertTrue(question.attachments.isEmpty()) }
            rule.onNodeWithText("Add image").assertExists()
            rule.onNodeWithText("Add recording").assertExists()
            rule.onNodeWithText("Record audio").assertExists()
        } finally { audio.delete() }
    }
}
