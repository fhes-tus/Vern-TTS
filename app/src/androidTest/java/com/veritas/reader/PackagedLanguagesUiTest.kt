package com.veritas.reader

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.ReaderUiState
import com.veritas.reader.ui.screens.LibraryDocumentFilters
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class PackagedLanguagesUiTest {
    @get:Rule val rule = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun newLanguagesUsePackagedRegionalResources() {
        val cases = mapOf("pt-BR" to "Configurações", "ru-RU" to "Настройки",
            "de-DE" to "Einstellungen", "ko-KR" to "설정")
        for ((tag, settings) in cases) {
            val config = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
            val localized = context.createConfigurationContext(config)
            assertEquals(tag, settings, localized.getString(R.string.ui_settings))
        }
    }

    @Test fun libraryFilterUsesFrenchTextAndKeepsTheOriginalFilterValue() {
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.FRANCE) }
        val french = context.createConfigurationContext(config)
        val document = SavedDocument("fixture", "Fixture", "fixture.txt", "TXT", 1, 1, 1, 3, 42, "",
            favorite = true, collection = "Favorites")
        var filter = "All"
        rule.setContent {
            CompositionLocalProvider(LocalConfiguration provides config, LocalResources provides french.resources) {
                MaterialTheme {
                    LibraryDocumentFilters(listOf(document), emptyList(), ReaderUiState(), "All", { filter = it },
                        "All", {}, "All", {}, "All", {})
                }
            }
        }
        val favorites = french.getString(R.string.filter_count, french.getString(R.string.ui_favorites), "1")
        rule.onNodeWithText(favorites).assertIsDisplayed().performClick()
        rule.runOnIdle { assertEquals("Favorites", filter) }
    }
}
