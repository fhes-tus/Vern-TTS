package com.veritas.reader

import android.app.LocaleManager
import android.content.res.Configuration
import android.os.LocaleList
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class AppLanguageDefaultInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = context.getSystemService(LocaleManager::class.java)

    @Test fun choosingDeviceLanguageClearsOverrideAndLoadsThePhonesResources() {
        val previous = manager.applicationLocales
        val phone = manager.systemLocales.toLanguageTags()
        try {
            instrumentation.runOnMainSync { AppLanguages.select(context,"fr") }
            waitUntil { AppLanguages.selectedTag(context) == "fr" && context.getString(R.string.app_language) == "Langue de l’application" }
            instrumentation.runOnMainSync { AppLanguages.select(context,"") }
            waitUntil { manager.applicationLocales.isEmpty && AppLanguages.selectedTag(context).isEmpty() }
            val expected = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocales(manager.systemLocales)
            }).getString(R.string.app_language)
            waitUntil { context.getString(R.string.app_language) == expected }
            assertEquals(phone,manager.systemLocales.toLanguageTags())
            assertEquals(phone,AppLanguages.deviceLocales(context).toLanguageTags())
            // Re-applying the default must remain a real empty override, not a pinned phone language.
            instrumentation.runOnMainSync { AppLanguages.select(context,"") }
            assertTrue(manager.applicationLocales.isEmpty)
        } finally {
            instrumentation.runOnMainSync { manager.applicationLocales = previous }
        }
    }

    @Test fun externalUnsupportedOverrideIsDistinguishedFromDeviceDefault() {
        val previous = manager.applicationLocales
        val phone = manager.systemLocales.toLanguageTags()
        try {
            instrumentation.runOnMainSync { manager.applicationLocales = LocaleList(Locale.ITALY) }
            waitUntil { AppLanguages.selectedTag(context) == "it-IT" }
            assertFalse(AppLanguages.selectedTag(context).isEmpty())
            instrumentation.runOnMainSync { AppLanguages.select(context,"") }
            waitUntil { manager.applicationLocales.isEmpty && AppLanguages.selectedTag(context).isEmpty() }
            assertEquals(phone,manager.systemLocales.toLanguageTags())
        } finally {
            instrumentation.runOnMainSync { manager.applicationLocales = previous }
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime()+10_000
        while(!condition() && SystemClock.elapsedRealtime()<end) SystemClock.sleep(50)
        assertTrue("App language did not synchronize with Android",condition())
    }
}
