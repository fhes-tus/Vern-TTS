package com.veritas.reader

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.screens.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ResponsiveStudySettingsUiTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also { check(it.packageName.endsWith(".checks")) }

    @Composable private fun Frame(width: Dp = 320.dp, height: Dp = 400.dp, scale: Float = 2f, content: @Composable () -> Unit) {
        val base = LocalDensity.current
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val fit = minOf(1f, maxWidth / width, maxHeight / height)
            CompositionLocalProvider(LocalDensity provides Density(base.density * fit, scale)) {
                MaterialTheme { Box(Modifier.requiredSize(width, height).testTag("study-frame")) { content() } }
            }
        }
    }
    private fun capture(tag: String, name: String) {
        val file = File(context.getExternalFilesDir(null), "responsive-45/$name.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun SemanticsNodeInteraction.reveal(): SemanticsNodeInteraction {
        if (!isDisplayed()) performScrollTo()
        return assertIsDisplayed()
    }

    @Test fun batteryInstructionsAndAllActionsAreReachableAtDoubleText() {
        var dismissed = 0; var opened = 0; var never = 0
        rule.setContent { Frame {
            UnrestrictedBatteryDialog({ dismissed++ }, { opened++ }, { never++ }, Modifier.width(320.dp).heightIn(max = 320.dp).testTag("battery-test"))
        } }
        rule.onNodeWithText(context.getString(R.string.ui_not_now)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.ui_don_t_ask_again)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.ui_turn_on_unrestricted)).performScrollTo().assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(1, opened); assertEquals(1, dismissed); assertEquals(0, never) }
        capture("battery-test", "battery-short-double-text")
    }

    @Test fun browserPermissionActionsRemainReachableInAShortBody() {
        var folder = false; var broadAccess = false
        rule.setContent { Frame(height = 220.dp) { FileBrowserEmptyState({ folder = true }, { broadAccess = true }) } }
        rule.onNodeWithText(context.getString(R.string.ui_folders_to_scan)).performScrollTo().assertIsDisplayed().performClick()
        rule.onNodeWithText(context.getString(R.string.ui_grant_all_files_access)).performScrollTo().assertIsDisplayed().performClick()
        rule.runOnIdle { assertTrue(folder); assertTrue(broadAccess) }
    }

    @Test fun readingGoalChoicesScrollToTheLastOptionWithLargeText() {
        var goal = -1
        rule.setContent { Frame { HomeReadingGoalDialog(0, { goal = it }, {}) } }
        val label = context.resources.getQuantityString(R.plurals.reading_minutes_daily, 60, 60)
        rule.onNodeWithText(label).performScrollTo().assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals(60, goal) }
    }

    @Test fun flashcardPasteRetainsDraftAndImportsAfterRestorationWithLargeText() {
        val restore = StateRestorationTester(rule)
        var result: List<Flashcard> = emptyList(); var name = ""
        restore.setContent { Frame(height = 568.dp) {
            PasteFlashcardsDialog({ title, cards -> name = title; result = cards }, {})
        } }
        val title = hasSetTextAction() and hasText(context.getString(R.string.ui_set_name_optional))
        val reply = hasSetTextAction() and hasText(context.getString(R.string.ui_ai_reply))
        rule.onNode(title).performScrollTo().performTextInput("My cards")
        rule.onNode(reply).performScrollTo().performTextInput("Q: A capital?\nA: Accra")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        restore.emulateSavedInstanceStateRestore()
        rule.onNode(title).assertTextContains("My cards")
        rule.onNode(reply).assertTextContains("Accra", substring = true)
        rule.onNode(hasText("Import", substring = true) and hasClickAction()).performClick()
        rule.runOnIdle { assertEquals("My cards", name); assertEquals(listOf(Flashcard("A capital?", "Accra")), result) }
    }

    @Test fun quizKeepsAnswersAndCurrentQuestionAcrossRestoration() {
        val restore = StateRestorationTester(rule)
        var score = -1
        val questions = listOf(QuizQuestion("First question", "Alpha", listOf("Alpha", "Other")), QuizQuestion("Second question", "Beta", listOf("Beta", "Other")))
        restore.setContent { Frame(height = 568.dp) { QuizPlayerDialog(questions, onSaveScore = { score = it }, onDismiss = {}) } }
        rule.onNodeWithText("Alpha").performScrollTo().performClick()
        rule.onNodeWithText(context.getString(R.string.ui_next_2f04eb1)).reveal().performClick()
        rule.onNodeWithText("Second question").performScrollTo().assertIsDisplayed()
        restore.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Second question").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Beta").performScrollTo().performClick()
        rule.onNode(hasText("Finish", substring = true) and hasClickAction()).reveal().performClick()
        rule.runOnIdle { assertEquals(2, score) }
    }

    @Test fun recordingFloaterRemainsReachableAfterDragAndResize() {
        var narrow by mutableStateOf(false); var saved = false
        rule.setContent { Frame(if (narrow) 320.dp else 600.dp, if (narrow) 300.dp else 400.dp) {
            FloatingRecordOverlay(false, true, true, 63, {}, { saved = true }, {})
        } }
        rule.onNodeWithTag("recording-floater").performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(-500f, -300f), 700) }
        rule.runOnIdle { narrow = true }
        val frame = rule.onNodeWithTag("study-frame").fetchSemanticsNode().boundsInRoot
        val pill = rule.onNodeWithTag("recording-floater").fetchSemanticsNode().boundsInRoot
        assertTrue("Recording actions must remain in the resized host", pill.left >= frame.left && pill.top >= frame.top && pill.right <= frame.right && pill.bottom <= frame.bottom)
        rule.onNodeWithText(context.getString(R.string.ui_save)).assertIsDisplayed().performClick()
        rule.runOnIdle { assertTrue(saved) }
        capture("study-frame", "recording-resized")
    }

    @Test fun settingsLongFormKeepsLastActionReachableInShortLargeTextWindow() {
        var applied = false
        rule.setContent { Frame {
            FullScreenSettingsScaffold("Voice and language settings", {}, modifier = Modifier.width(320.dp).height(350.dp)) {
                repeat(12) { Text("Settings paragraph $it", style = MaterialTheme.typography.bodyLarge) }
                Button(onClick = { applied = true }) { Text("Apply fixture") }
            }
        } }
        rule.onNodeWithText("Apply fixture").performScrollTo().assertIsDisplayed().performClick()
        rule.runOnIdle { assertTrue(applied) }
        capture("settings-content", "settings-short-large-text")
    }

    @Test fun noteDraftAndSaveRemainAvailableAcrossExpandedToNarrowResize() {
        var narrow by mutableStateOf(false); var saved = ""
        rule.setContent { Frame(if (narrow) 320.dp else 1200.dp, if (narrow) 568.dp else 900.dp, if (narrow) 2f else 1f) {
            GeneralNotesEditor(GeneralNote("responsive-note", "Note", "Original paragraph", 123),
                onSave = { _, content, _, _, _, _, _, _, _, _, _ -> saved = content }, onDelete = {}, onDismiss = {})
        } }
        rule.onNodeWithText("Original paragraph").performTextReplacement("A retained draft")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        rule.runOnIdle { narrow = true }
        rule.onNodeWithText("A retained draft").assertExists()
        rule.onNodeWithContentDescription("Save Note").assertIsDisplayed().performClick()
        rule.runOnIdle { assertTrue(saved.contains("A retained draft")) }
    }

    @Test fun narrationDraftSurvivesRestorationAndCanStillBeApplied() {
        val restore = StateRestorationTester(rule)
        var applied: NarrationSettings? = null
        restore.setContent { Frame(height = 568.dp) {
            NarrationStudioDialog(NarrationSettings(enabled = false), "A fixture passage.", onSettingsChange = { applied = it }, onDismiss = {})
        } }
        rule.onAllNodes(isToggleable())[0].performScrollTo().performClick()
        restore.emulateSavedInstanceStateRestore()
        rule.onAllNodes(isToggleable())[0].assertIsOn()
        rule.onNodeWithText(context.getString(R.string.ui_apply_narration_settings)).performScrollTo().performClick()
        rule.runOnIdle { assertTrue(applied?.enabled == true) }
    }
}
