package com.veritas.reader

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.ReaderUiState
import com.veritas.reader.ui.LocalVeritasMotion
import com.veritas.reader.ui.VeritasMotionScheme
import com.veritas.reader.ui.screens.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class HomeBrandThemeUiTest {
    @get:Rule val rule = createComposeRule()
    @Test fun sidebarCloseReleasesHomeWithoutSpringSettlingPause() {
        var visible by mutableStateOf(true)
        var reduced by mutableStateOf(false)
        var dismissals = 0
        var homeClicks = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalVeritasMotion provides VeritasMotionScheme(reduced)) {
                MaterialTheme {
                    Button(onClick = { homeClicks++ }) { Text("Home is ready") }
                    if (visible) HomeSidebarDialog("Reader", ReaderTrackerSnapshot.empty(),
                        onDismiss = { dismissals++; visible = false },
                        onOpenLibrary = {}, onOpenStats = {}, onOpenSettings = {})
                }
            }
        }
        listOf(false, true).forEach { reduce ->
            rule.runOnIdle { reduced = reduce; visible = true }
            rule.mainClock.advanceTimeBy(1200)
            rule.onNodeWithContentDescription("Close").assertExists("Drawer must open before the close check; reduced=$reduce")
            rule.onNodeWithContentDescription("Close").performClick()
            rule.mainClock.advanceTimeBy(224)
            rule.runOnIdle { assertFalse("Drawer must release Home after its bounded exit", visible) }
            // Commit removal before reopening: a stopped test clock otherwise
            // coalesces false/true into one frame and retains the closed Dialog.
            rule.mainClock.advanceTimeByFrame()
            rule.onNodeWithContentDescription("Close").assertDoesNotExist()
            rule.onNodeWithText("Home is ready").assertIsDisplayed().performClick()
        }
        rule.runOnIdle { assertEquals(2, dismissals); assertEquals(2, homeClicks) }
    }

    @Test fun vWordmarkFitsLargeTextHeaderAcrossThemes() {
        assertEquals("midnight_system", ReaderSettings().themeId)
        assertEquals("midnight_system", ReaderSettings.fromJson(org.json.JSONObject()).themeId)
        assertEquals("light", ReaderSettings.fromJson(org.json.JSONObject().put("themeId", "light")).themeId)
        var dark by mutableStateOf(false)
        var green by mutableStateOf(false)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                val primary = if (green) { if (dark) Color(0xFF90D9B7) else Color(0xFF126C4A) }
                    else { if (dark) Color(0xFFD0B8FF) else Color(0xFF6750A4) }
                MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = primary) else lightColorScheme(primary = primary)) {
                    Box(Modifier.width(320.dp)) {
                        LibraryTopAndFilterBar(VeritasHomeTab.HOME, emptyList(), emptyList(), ReaderUiState(), 0,
                            "All", {}, "All", {}, "All", {}, "All", {}, "All", {}, {}, {})
                    }
                }
            }
        }
        listOf(false to false, false to true, true to true, true to false).forEach { (night, forest) ->
            rule.runOnIdle { dark = night; green = forest }
            rule.onNodeWithText("ern").assertIsDisplayed()
            rule.onNodeWithContentDescription("Menu").assertIsDisplayed()
            rule.onNodeWithContentDescription("Settings").assertIsDisplayed()
            rule.onAllNodesWithTag("vern-brand-mark", useUnmergedTree = true).assertCountEquals(1)
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            rule.onNodeWithText("ern").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val title = layouts.single()
            assertEquals(if (night) darkColorScheme().onSurface else lightColorScheme().onSurface, title.layoutInput.style.color)
            assertEquals(26.sp, title.layoutInput.style.fontSize)
            assertEquals(FontWeight.Black, title.layoutInput.style.fontWeight)
            val mark = rule.onNodeWithTag("vern-wordmark").fetchSemanticsNode().boundsInRoot
            val header = rule.onNodeWithTag("shared_home_header").fetchSemanticsNode().boundsInRoot
            assertTrue(mark.left >= header.left && mark.right <= header.right && mark.top >= header.top && mark.bottom <= header.bottom)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val file = File(context.getExternalFilesDir(null), "remaining-fixes/home-brand-${if (night) "dark" else "light"}-${if (forest) "green" else "purple"}.png").apply { parentFile!!.mkdirs() }
            file.outputStream().use { rule.onNodeWithTag("shared_home_header").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
            // Paragraph width reflects the available constraint, whereas the Text
            // node can shrink to its actual line. Check the rendered line itself.
            assertEquals(3, title.getLineEnd(0))
            assertFalse(title.isLineEllipsized(0))
            assertFalse(title.didOverflowHeight)
            assertTrue(title.getLineRight(0) - title.getLineLeft(0) <= title.size.width + 1f)
        }
    }
}
