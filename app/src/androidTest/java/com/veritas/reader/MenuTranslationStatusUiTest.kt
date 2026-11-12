package com.veritas.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.screens.SettingsTranslation
import com.veritas.reader.ui.screens.SettingsTranslationStatus
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class MenuTranslationStatusUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun namesTheModelAndShowsWhyItIsWaitingThenItsProgress() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val locale = context.resources.configuration.locales[0]
        val name = context.getString(R.string.menu_translation_model_name, Locale.GERMAN.getDisplayName(locale))
        val status = mutableStateOf(SettingsTranslation.Status.WAITING_HEAT)
        rule.setContent {
            MaterialTheme {
                SettingsTranslationStatus("de", status.value, percent = 37, hasTranslations = false)
            }
        }
        rule.onNodeWithText(name).assertIsDisplayed()
        rule.onNodeWithText(context.getString(R.string.menu_translation_thermal)).assertIsDisplayed()
        rule.runOnIdle { status.value = SettingsTranslation.Status.DOWNLOADING }
        rule.onNodeWithText(context.getString(R.string.menu_translation_downloading)).assertIsDisplayed()
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo,
            ProgressBarRangeInfo(0.37f, 0f..1f))).assertIsDisplayed()
        rule.runOnIdle { status.value = SettingsTranslation.Status.ENGINE_DOWNLOADING }
        rule.onNodeWithText(context.getString(R.string.menu_translation_engine_downloading)).assertIsDisplayed()
        rule.runOnIdle { status.value = SettingsTranslation.Status.ENGINE_UNAVAILABLE }
        rule.onNodeWithText(context.getString(R.string.menu_translation_engine_unavailable)).assertIsDisplayed()
    }
}
