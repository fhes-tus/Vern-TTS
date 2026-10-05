package com.veritas.reader

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Test
    fun appLaunchesAndShowsLibraryTab() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // The app clears incoming actions, so use an explicit action-free intent.
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
            composeTestRule.onNodeWithText("Library", ignoreCase = true).assertExists()
        }
    }
}
