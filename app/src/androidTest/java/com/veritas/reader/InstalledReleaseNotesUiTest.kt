package com.veritas.reader

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class InstalledReleaseNotesUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun upgradeShowsBundledNotesOnceAndDropsStaleGitHubText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val prefs = context.getSharedPreferences("veritas_reader_library", Context.MODE_PRIVATE)
        val oldName = prefs.getString("last_run_version_name", null)
        val oldCode = prefs.getInt("last_run_version_code", 0)
        val oldChangelog = prefs.getString("last_downloaded_changelog", null)
        val staleText = "# GitHub 2.7.1 release notes"
        val notes = context.assets.open("release-notes.json").bufferedReader().use {
            releaseNotesForVersion(it.readText(), BuildConfig.VERSION_NAME)
        }
        val repository = DocumentRepository(context)
        repository.markOnboardingComplete("Release notes check")
        repository.setQuestChecklistDismissed(true)
        prefs.edit().putBoolean("battery_unrestricted_never_ask", true)
            .putString("last_run_version_name", "2.7.1").remove("last_run_version_code")
            .putString("last_downloaded_changelog", staleText).commit()
        try {
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
                compose.waitUntil(15_000) {
                    compose.onAllNodes(hasTestTag("installed-release-notes")).fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText(context.getString(R.string.l10n_dynamic_175, BuildConfig.VERSION_NAME))
                    .assertIsDisplayed()
                compose.onNodeWithText("• ${notes.first()}").assertIsDisplayed()
                compose.onNodeWithText(staleText).assertDoesNotExist()
                compose.onNodeWithText(context.getString(R.string.ui_close)).performClick()
                assertEquals(BuildConfig.VERSION_NAME, prefs.getString("last_run_version_name", null))
                assertEquals(BuildConfig.VERSION_CODE, prefs.getInt("last_run_version_code", 0))
                assertFalse(prefs.contains("last_downloaded_changelog"))
            }
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
                compose.waitForIdle()
                compose.onNodeWithTag("installed-release-notes").assertDoesNotExist()
            }
        } finally {
            prefs.edit().putString("last_run_version_name", oldName)
                .putString("last_downloaded_changelog", oldChangelog).apply {
                    if (oldCode == 0) remove("last_run_version_code") else putInt("last_run_version_code", oldCode)
                }.commit()
        }
    }
}
