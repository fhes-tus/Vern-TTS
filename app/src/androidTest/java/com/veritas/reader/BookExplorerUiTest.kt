package com.veritas.reader

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.ReaderUiState
import com.veritas.reader.ui.withBookArchive
import com.veritas.reader.ui.screens.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.io.File

class BookExplorerUiTest {
    @get:Rule val ui = createComposeRule()

    private val preferencePrefix = "book-explorer-ui-${UUID.randomUUID()}"
    private val contexts = mutableListOf<Context>()

    private fun content(query: String = "", onBrowse: (String, String, String) -> Unit = { _, _, _ -> }): MutableState<Boolean> {
        val visible = mutableStateOf(true)
        ui.setContent {
            val base = LocalContext.current
            val isolated = remember(base) {
                object : ContextWrapper(base) {
                    override fun getSharedPreferences(name: String, mode: Int) =
                        base.getSharedPreferences("$preferencePrefix-$name", mode)
                }.also { contexts.add(it) }
            }
            CompositionLocalProvider(LocalContext provides isolated) {
                VeritasTheme {
                    if (visible.value) {
                        val controls = rememberClassicsHeaderState()
                        LaunchedEffect(Unit) { controls.query = query }
                        ClassicsCatalogContent(emptyList(), emptyMap(), {}, {}, {}, onBrowse, headerState = controls)
                    } else Text("Another screen")
                }
            }
        }
        return visible
    }

    private fun capture(name: String) {
        ui.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "book-explorer-checks").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    @org.junit.After fun clearFixturePreferences() {
        contexts.forEach { it.getSharedPreferences("vern_classics_discovery", Context.MODE_PRIVATE).edit().clear().commit() }
    }

    @Test fun tipExplainsOnlineDiscoveryAndDismissalSurvivesReturning() {
        val visible = content()
        ui.onNodeWithTag("book_explorer_intro").assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ui.onNodeWithText(context.getString(R.string.book_explorer_intro_message,
            context.getString(R.string.book_explorer_title))).assertIsDisplayed()
        capture("intro")
        ui.onNodeWithTag("book_explorer_intro_dismiss").performClick()
        ui.onNodeWithTag("book_explorer_open").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("classics_sort").assertIsDisplayed()
        capture("header")
        ui.runOnIdle { visible.value = false }
        ui.runOnIdle { visible.value = true }
        ui.onNodeWithTag("book_explorer_intro").assertDoesNotExist()
    }

    @Test fun introductionOpensOrderedSitesAndEverySiteKeepsTheSearch() {
        var routed = ReaderUiState()
        content("  Alice & wonderland  ") { url, title, query -> routed = routed.withBookArchive(url, title, query) }
        ui.onNodeWithTag("book_explorer_intro_open").performClick()
        val expected = listOf("Ocean of PDF", "Project Gutenberg", "Standard Ebooks", "Open Library", "ManyBooks")
        val tops = expected.map { name -> ui.onNodeWithTag("book_explorer_site_$name").fetchSemanticsNode().boundsInRoot.top }
        assertTrue("Sites follow the requested display order", tops.zipWithNext().all { (a, b) -> a < b })
        capture("sites")
        for ((index, name) in expected.withIndex()) {
            if (index > 0) ui.onNodeWithTag("book_explorer_open").performScrollTo().performClick()
            ui.onNodeWithTag("book_explorer_site_$name").performClick()
            ui.runOnIdle {
                assertTrue(routed.showBookBrowser)
                assertEquals(name, routed.bookBrowserTitle)
                assertEquals(FREE_BOOK_SITES.first { it.name == name }.url, routed.bookBrowserUrl)
                assertEquals("Alice & wonderland", routed.bookBrowserQuery)
            }
        }
    }

    @Test fun headerOpensExplorerAndExistingSortStillWorks() {
        content()
        ui.onNodeWithTag("book_explorer_intro_dismiss").performClick()
        ui.onNodeWithTag("classics_sort").performScrollTo().performClick()
        ui.onNodeWithTag("classics_sort_Title").performClick()
        ui.onNodeWithTag("classic_featured").assertDoesNotExist()
        ui.onNodeWithTag("book_explorer_open").performScrollTo().performClick()
        ui.onNodeWithTag("book_explorer_sheet").assertIsDisplayed()
        ui.onNodeWithTag("book_explorer_site_Ocean of PDF").assertIsDisplayed()
    }
}
