package com.veritas.reader

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.tts.OfflineEngineType
import com.veritas.reader.tts.VoiceModelManager
import com.veritas.reader.ui.screens.VoiceStudioDialog
import org.junit.Rule
import org.junit.Test
import java.text.NumberFormat

class SharedVoicePackageUiTest {
    @get:Rule val ui = createComposeRule()

    @Test fun sharedPackagesShowOneTotalAndNoPerSpeakerDownloadPrice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val type = mutableStateOf(OfflineEngineType.KITTEN)
        fun engine(t: OfflineEngineType) = if (t == OfflineEngineType.KITTEN) VoiceManager.KITTEN else VoiceManager.VERITAS_STUDIO
        ui.setContent {
            val selected = type.value
            val voices = VoiceModelManager.availableVoices.filter { it.engineType == selected }.map {
                TtsVoiceOption(it.id, it.name, it.localeTag, false, 500, 100, engine(selected))
            }
            VeritasTheme {
                VoiceStudioDialog(VoiceSettings(enginePackage = engine(selected)), emptyList(), voices, false,
                    {}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _, _ -> }, {}, {}, {}, {}, {})
            }
        }
        for (selected in listOf(OfflineEngineType.KITTEN, OfflineEngineType.KOKORO)) {
            ui.runOnIdle { type.value = selected }
            val voices = VoiceModelManager.availableVoices.filter { it.engineType == selected }
            val size = NumberFormat.getNumberInstance(context.resources.configuration.locales[0])
                .apply { maximumFractionDigits = 1 }.format(VoiceModelManager.downloadSizeMb(voices.first()))
            ui.onNodeWithTag("voice-package-summary-${engine(selected)}", useUnmergedTree = true)
                .performScrollTo().assertTextEquals(context.getString(R.string.voice_shared_package_size, size, voices.size))
            ui.onNodeWithTag("voice-group-${engine(selected)}").performScrollTo().performClick()
            ui.onAllNodesWithText(context.getString(R.string.voice_download_package))[0].performScrollTo().assertIsDisplayed()
        }
    }
}
