package com.veritas.reader

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.screens.PronunciationRulesDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SpeechPatternRulesInstrumentedTest {
    @get:Rule val ui = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also { check(it.packageName.endsWith(".checks")) }

    private fun withEditor(block: (DocumentRepository) -> Unit) {
        val repository = DocumentRepository(context)
        val previous = repository.loadPronunciationRules()
        repository.savePronunciationRules(emptyList())
        try {
            ui.setContent {
                var rules by remember { mutableStateOf(repository.loadPronunciationRules()) }
                var draft by remember { mutableStateOf(SpeechRuleDraft()) }
                VeritasTheme {
                    PronunciationRulesDialog(rules, VoiceSettings(), draft, null, { draft = it }, {
                        val candidate = draft.rule()
                        rules = repository.addPronunciationRule(candidate.find, candidate.replaceWith, draft.kind, draft.caseSensitive, draft.id)
                        draft = SpeechRuleDraft()
                    }, { rules = repository.togglePronunciationRule(it.id) }, {
                        rules = repository.removePronunciationRule(it.id)
                        if (draft.id == it.id) draft = SpeechRuleDraft()
                    }, {})
                }
            }
            block(repository)
        } finally { repository.savePronunciationRules(previous) }
    }

    private fun previewEquals(expected: String) {
        var actual = "<preview unavailable>"
        try {
            ui.waitUntil(10000) {
                actual = runCatching { ui.onNodeWithTag("speech-rule-preview").fetchSemanticsNode()
                    .config[SemanticsProperties.Text].joinToString("") { it.text } }.getOrDefault("<preview unavailable>")
                actual == expected
            }
        } catch (failure: ComposeTimeoutException) {
            throw AssertionError("Expected preview <$expected>, actual <$actual>", failure)
        }
    }

    @Test fun referencePresetCanBeSavedEditedAndPreviewedAsSpokenNumbers() = withEditor { repository ->
        ui.onNodeWithText("References [n]").performClick()
        ui.onNodeWithTag("speech-rule-save").performScrollTo().performClick()
        ui.waitUntil(5000) { repository.loadPronunciationRules().size == 1 }
        val id = repository.loadPronunciationRules().single().id
        assertEquals("Before after.", DocumentRepository(context).applyPronunciationRules("Before[12] after."))
        ui.onNodeWithText("[n] → Skip").performScrollTo().performClick()
        ui.onNodeWithTag("speech-action-say").performScrollTo().performClick()
        ui.onNodeWithTag("speech-rule-replacement").performScrollTo().performTextReplacement("reference \$1")
        ui.onNodeWithTag("speech-rule-test-input").performScrollTo().performTextReplacement("Before[12] after.")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        previewEquals("Before reference 12 after.")
        ui.onNodeWithTag("speech-rule-preview").performScrollTo()
        val screenshot = File(context.filesDir, "speech-pattern-editor.png")
        screenshot.outputStream().use { ui.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        preserveSpeechTestArtifact(context, screenshot)
        ui.onNodeWithTag("speech-rule-save").performScrollTo().performClick()
        ui.waitUntil(5000) { repository.loadPronunciationRules().single().replaceWith == "reference \$1" }
        assertEquals(id, repository.loadPronunciationRules().single().id)
        assertEquals(SpeechRuleKind.PATTERN, DocumentRepository(context).loadPronunciationRules().single().matchKind)
    }

    @Test fun invalidRegexCannotBeSavedAndAnEmptyPreviewCannotBeAuditioned() = withEditor { repository ->
        ui.onNodeWithTag("speech-kind-REGEX").performScrollTo().performClick()
        ui.onNodeWithTag("speech-action-skip").performScrollTo().performClick()
        ui.onNodeWithTag("speech-rule-find").performTextReplacement("(")
        ui.onNodeWithTag("speech-rule-save").assertIsNotEnabled()
        ui.onNodeWithTag("speech-rule-error").assertExists()
        assertTrue(repository.loadPronunciationRules().isEmpty())
        ui.onNodeWithTag("speech-rule-find").performTextReplacement("\\[[0-9]+\\]")
        ui.onNodeWithTag("speech-rule-test-input").performScrollTo().performTextReplacement("[12]")
        previewEquals("Nothing will be spoken.")
        ui.onNodeWithTag("speech-rule-audition").assertIsNotEnabled()
        ui.onNodeWithTag("speech-rule-save").performScrollTo().performClick()
        ui.waitUntil(5000) { repository.loadPronunciationRules().size == 1 }
        assertEquals(SpeechRuleKind.REGEX, repository.loadPronunciationRules().single().matchKind)
    }

    @Test fun starredPresetCanBeDisabledAndRemoved() = withEditor { repository ->
        ui.onNodeWithText("Starred text").performScrollTo().performClick()
        ui.onNodeWithTag("speech-rule-save").performScrollTo().performClick()
        ui.waitUntil(5000) { repository.loadPronunciationRules().size == 1 }
        val saved = repository.loadPronunciationRules().single()
        assertEquals("Keep  end.", repository.applyPronunciationRules("Keep *terro* end."))
        ui.onNodeWithText("*{text}* → Skip").performScrollTo()
        ui.onNode(hasTestTag("speech-rule-toggle-${saved.id}")).performClick()
        ui.waitUntil(5000) { !repository.loadPronunciationRules().single().enabled }
        assertEquals("Keep *terro* end.", repository.applyPronunciationRules("Keep *terro* end."))
        ui.onNodeWithContentDescription("Remove rule *{text}*").performClick()
        ui.waitUntil(5000) { repository.loadPronunciationRules().isEmpty() }
    }
}
