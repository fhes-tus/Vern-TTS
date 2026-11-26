package com.veritas.reader

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.screens.VoiceStudioDialog
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import java.io.File

class VoiceShapingUiInstrumentedTest {
    @get:Rule val ui = createComposeRule()
    @Test fun originalToneControlCanBeChangedInVoiceStudio() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        ui.setContent {
            var settings by remember { mutableStateOf(VoiceSettings()) }
            VeritasTheme {
                VoiceStudioDialog(
                    settings = settings,
                    engines = emptyList(), voices = emptyList(), loadingVoices = false,
                    onRefreshEngines = {}, onLoadVoices = {}, onUseSystemDefault = {}, onEngineSelected = {},
                    onLanguageSelected = {}, onShowNetworkVoicesChange = {}, onVoiceSelected = {}, onPreviewVoice = {},
                    onPresetSelected = { _, _, _ -> }, onAddLanguageVoice = {}, onOpenSystemTtsSettings = {}, onDismiss = {},
                    onVoiceShapingChange = { settings = settings.copy(voiceShaping = it) }
                )
            }
        }
        ui.onNodeWithTag("voice-shaping-toggle").performScrollTo().assertContentDescriptionEquals("Voice shaping")
            .assertIsOn().performClick().assertIsOff()
        val file = File(context.filesDir, "voice-shaping-control.png")
        file.outputStream().use { ui.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        preserveSpeechTestArtifact(context, file)
        ui.onNodeWithTag("voice-shaping-toggle").performClick().assertIsOn()
    }

    @Test fun browsingLanguageDoesNotSelectVoiceAndPresetPreviewDoesNotSave() {
        val english = TtsVoiceOption("phone-english", "English fixture", "en-US", false, 400, 100)
        val french = TtsVoiceOption("phone-french", "French fixture", "fr-FR", false, 400, 100)
        var changedLanguage: String? = null
        var preview: Pair<Float, Float>? = null
        var applied: Triple<String, Float, Float>? = null
        ui.setContent {
            VeritasTheme {
                VoiceStudioDialog(
                    settings = VoiceSettings(voiceName = english.name, voiceLabel = english.label, localeTag = english.localeTag),
                    engines = emptyList(), voices = listOf(english, french), loadingVoices = false,
                    onRefreshEngines = {}, onLoadVoices = {}, onUseSystemDefault = {}, onEngineSelected = {},
                    onLanguageSelected = { changedLanguage = it }, onShowNetworkVoicesChange = {},
                    onVoiceSelected = {}, onPreviewVoice = {}, onPreviewActiveVoiceWithPreset = { rate, pitch -> preview = rate to pitch },
                    onPresetSelected = { name, rate, pitch -> applied = Triple(name, rate, pitch) },
                    onAddLanguageVoice = {}, onOpenSystemTtsSettings = {}, onDismiss = {}
                )
            }
        }
        ui.onNodeWithTag("voice-library-language").performScrollTo().performClick()
        ui.onNodeWithTag("voice-library-language-fr-FR").performClick()
        ui.onNodeWithText("English fixture").assertIsDisplayed()
        ui.runOnIdle { assertNull(changedLanguage) }

        ui.onNodeWithTag("voice-studio-customize").performScrollTo().performClick()
        ui.onNodeWithText("Study focus").performScrollTo().performClick()
        ui.onNodeWithTag("voice-preset-preview").performScrollTo().performClick()
        ui.runOnIdle {
            assertEquals(.88f to 1f, preview)
            assertNull(applied)
        }
        ui.onNodeWithText("Apply").performClick()
        ui.runOnIdle { assertEquals(Triple("Study focus", .88f, 1f), applied) }
    }

    @Test fun choosingCurrentModelInEnginePickerReopensItsVoiceList() {
        ui.setContent {
            VeritasTheme {
                VoiceStudioDialog(
                    settings = VoiceSettings(enginePackage = VoiceManager.KITTEN, localeTag = "en-US"),
                    engines = emptyList(), voices = emptyList(), loadingVoices = false,
                    onRefreshEngines = {}, onLoadVoices = {}, onUseSystemDefault = {},
                    onEngineSelected = { error("Reopening the current model should preserve its voice selection") },
                    onLanguageSelected = {}, onShowNetworkVoicesChange = {}, onVoiceSelected = {}, onPreviewVoice = {},
                    onPresetSelected = { _, _, _ -> }, onAddLanguageVoice = {}, onOpenSystemTtsSettings = {}, onDismiss = {}
                )
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val group = ui.onNodeWithTag("voice-group-${VoiceManager.KITTEN}")
        ui.waitUntil(5_000) {
            ui.onAllNodesWithTag("voice-group-${VoiceManager.KITTEN}").fetchSemanticsNodes().isNotEmpty()
        }
        val collapsed = SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
            context.getString(R.string.voice_list_collapsed)
        )
        val expanded = SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription,
            context.getString(R.string.voice_list_expanded)
        )
        group.assert(collapsed)
        ui.onNodeWithContentDescription(context.getString(R.string.voice_studio_phone_engine))
            .performScrollTo().performClick()
        ui.onNode(hasText("Kitten") and hasAnyAncestor(isPopup())).performClick()
        group.performScrollTo().assert(expanded).performClick().assert(collapsed)
    }
}
