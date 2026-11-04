package com.veritas.reader

import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Isolated resource contexts do not change the phone's language or the user's saved preferences. */
@RunWith(AndroidJUnit4::class)
class AppLanguageResourcesInstrumentedTest {
    @Test fun eachLanguageLoadsItsMenusAndKeepsEnglishFallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = context.resources.configuration.locales.toLanguageTags()
        val english = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(Locale.ENGLISH))
        })
        val names = mapOf("en" to "App language", "fr" to "Langue de l’application",
            "es" to "Idioma de la aplicación", "hi" to "ऐप की भाषा", "zh-Hans" to "应用语言", "ar" to "لغة التطبيق")
        names.forEach { (tag, expected) ->
            val config = Configuration(context.resources.configuration).apply { setLocales(LocaleList(Locale.forLanguageTag(tag))) }
            val localized = context.createConfigurationContext(config)
            assertEquals(expected, localized.getString(R.string.app_language))
            assertTrue(localized.getString(R.string.language_preview_notice).isNotBlank())
            assertTrue(localized.getString(R.string.ui_settings).isNotBlank())
            assertTrue(localized.resources.getQuantityString(R.plurals.reading_minutes, 2, 2).isNotBlank())
        }
        val unsupported = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList(Locale.GERMAN))
        })
        assertEquals(english.getString(R.string.ui_a_notebook_with_this_name_already_exists),
            unsupported.getString(R.string.ui_a_notebook_with_this_name_already_exists))
        assertEquals(original, context.resources.configuration.locales.toLanguageTags())
    }
}
