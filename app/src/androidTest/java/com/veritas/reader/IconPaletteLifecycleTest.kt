package com.veritas.reader

import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.saveReaderSettings
import com.veritas.reader.ui.screens.CustomizationSettingsDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class IconPaletteLifecycleTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Test fun iconChangeKeepsSettingsOpenAndAppliesWhenAppLeavesForeground() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pm = context.packageManager
        val prefs = context.getSharedPreferences("veritas_reader_library", android.content.Context.MODE_PRIVATE)
        val originalSettings = prefs.getString("reader_settings", null)
        VernCustomization.applyLauncherPalette(context, ReaderSettings())
        var settings by mutableStateOf(ReaderSettings())
        // MainActivity consumes the incoming action before ON_CREATE is reported.
        // An explicit alias intent retains the same component without a consumed action.
        val intent = Intent().addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(context.packageName, "com.veritas.reader.LauncherIndigo"))
        val scenario = ActivityScenario.launch<MainActivity>(intent)
        try {
            scenario.onActivity { activity ->
                activity.setContent {
                    VeritasTheme {
                        CustomizationSettingsDialog(settings, {
                            settings = it
                            activity.viewModel.saveReaderSettings(it)
                            VernCustomization.queueLauncherPalette(it)
                        }, {})
                    }
                }
            }
            rule.onNodeWithTag("icon-palette-ocean").performScrollTo().performClick()
            rule.onNodeWithTag("icon-palette-ocean").assertIsDisplayed()
            android.os.SystemClock.sleep(700)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            scenario.onActivity { assertFalse(it.isFinishing) }
            val before = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0)
            assertEquals(1, before.size)
            assertTrue(before.single().activityInfo.name.endsWith("LauncherIndigo"))
            scenario.moveToState(Lifecycle.State.CREATED)
            rule.waitUntil(5000) {
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName), 0)
                    .singleOrNull()?.activityInfo?.name?.endsWith("LauncherOcean") == true
            }
        } finally {
            scenario.close()
            if (originalSettings == null) prefs.edit().remove("reader_settings").commit()
            else prefs.edit().putString("reader_settings", originalSettings).commit()
            VernCustomization.queueLauncherPalette(ReaderSettings())
            VernCustomization.applyLauncherPalette(context, ReaderSettings())
        }
    }
}
