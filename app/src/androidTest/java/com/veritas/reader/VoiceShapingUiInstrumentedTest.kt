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
import java.io.File

class VoiceShapingUiInstrumentedTest {
    @get:Rule val ui = createComposeRule()
    @Test fun originalToneControlCanBeChangedInVoiceStudio() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        ui.setContent {
            var settings by remember { mutableStateOf(VoiceSettings()) }
            VeritasTheme {
                VoiceStudioDialog(settings, emptyList(), emptyList(), false, {}, {}, {}, {}, {}, {}, {}, {}, {},
                    { _, _, _ -> }, {}, {}, {}, {}, {}, onVoiceShapingChange = { settings = settings.copy(voiceShaping = it) })
            }
        }
        // A fresh isolated install shows the cloud-voice explanation above Voice Studio.
        val explore = ui.onAllNodesWithText(context.getString(R.string.voice_explore))
        if (explore.fetchSemanticsNodes().isNotEmpty()) explore.onFirst().performClick()
        ui.onNodeWithTag("voice-shaping-toggle").performScrollTo().assertContentDescriptionEquals("Voice shaping")
            .assertIsOn().performClick().assertIsOff()
        val file = File(context.filesDir, "voice-shaping-control.png")
        file.outputStream().use { ui.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        preserveSpeechTestArtifact(context, file)
        ui.onNodeWithTag("voice-shaping-toggle").performClick().assertIsOn()
    }
}
