package com.veritas.reader

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.screens.OfflineDictionarySheet
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class OfflineDictionaryUiTest {
    @get:Rule val rule = createComposeRule()
    @Before fun installFixture() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        org.junit.Assume.assumeTrue("Dictionary checks require their own disposable package.", ctx.packageName.endsWith(".dictionarychecks"))
        if (OfflineDictionaryStore.info(ctx) == null) {
            val fixture = File(ctx.cacheDir, "dictionary-ui-fixture.zip")
            InstrumentationRegistry.getInstrumentation().context.assets.open("english-wordnet-2025.zip").use { input -> fixture.outputStream().use { output -> input.copyTo(output) } }
            OfflineDictionaryStore.install(ctx, fixture)
            fixture.delete()
        }
    }
    @Test fun sheetShowsRealOfflineMeaningsAndSavesVocabulary() {
        rule.setContent {
            VeritasTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    Text("Reading continues here.\n\nThe affairs of the book stay in view behind the dictionary.", Modifier.padding(24.dp))
                    OfflineDictionarySheet("abandon", DictionaryLookupAnchor(), onSaved = {}, onDismiss = {})
                }
            }
        }
        rule.waitUntil(45_000) { rule.onAllNodesWithText("Saved to Vocabulary").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithTag("dictionary-query").assertTextContains("abandon")
        rule.onNodeWithTag("dictionary-source-offline").assertIsSelected()
        rule.onNodeWithText("Saved to Vocabulary").performScrollTo().assertIsDisplayed()
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(instrumentation.targetContext.getExternalFilesDir(null), "dictionary-lookup.png")
        instrumentation.uiAutomation.takeScreenshot().let { bitmap -> output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
    }
}
