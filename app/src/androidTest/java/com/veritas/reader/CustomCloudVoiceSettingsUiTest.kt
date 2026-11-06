package com.veritas.reader

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.screens.CloudVoiceSettingsPanel
import org.junit.Rule
import org.junit.Test

class CustomCloudVoiceSettingsUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun selectingCustomRevealsAnEditableEndpointInAShortViewport() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = "cloud_endpoint_ui_test_${System.nanoTime()}"
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int) =
                context.getSharedPreferences(preferences, mode)
        }
        try {
            CloudVoiceStore.dismissNotice(isolated)
            rule.setContent {
                CompositionLocalProvider(LocalContext provides isolated) {
                    MaterialTheme {
                        Column(Modifier.height(380.dp).verticalScroll(rememberScrollState())) {
                            CloudVoiceSettingsPanel()
                        }
                    }
                }
            }
            rule.onNodeWithText(context.getString(R.string.cloud_personal)).performClick()
            rule.onNodeWithText(CloudVoiceProvider.ELEVENLABS.title).performScrollTo().performClick()
            rule.onNodeWithText(context.getString(R.string.cloud_compatible)).performClick()
            val field = rule.onNode(hasSetTextAction() and hasText(context.getString(R.string.cloud_endpoint)))
            field.assertIsDisplayed()
            val endpoint = "https://voice.example.test/v1/audio/speech"
            field.performTextInput(endpoint)
            field.assertTextContains(endpoint)
        } finally {
            context.deleteSharedPreferences(preferences)
        }
    }
}
