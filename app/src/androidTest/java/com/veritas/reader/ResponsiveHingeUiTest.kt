package com.veritas.reader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ResponsiveHingeUiTest {
    @get:Rule val rule = createComposeRule()
    @Test fun changingFoldRegionsRetainsRememberedScreenState() {
        check(InstrumentationRegistry.getInstrumentation().targetContext.packageName.endsWith(".checks"))
        var folded by mutableStateOf(false)
        rule.setContent { MaterialTheme { BoxWithConstraints(Modifier.fillMaxSize()) {
            val width = with(LocalDensity.current) { maxWidth.roundToPx() }
            val folds = if (folded) listOf(VernFoldBounds(VernPaneBounds(width / 2 - 12, 0, width / 2 + 12, 100000), true)) else emptyList()
            CompositionLocalProvider(LocalVernFoldOverride provides folds) {
                VernFoldAwarePane(Modifier.fillMaxSize().testTag("fold-frame")) {
                    var count by remember { mutableIntStateOf(0) }
                    Box(Modifier.fillMaxSize().testTag("fold-content")) {
                        Button(onClick = { count++ }) { Text("Count $count") }
                    }
                }
            }
        } } }
        rule.onNodeWithText("Count 0").performClick()
        rule.runOnIdle { folded = true }
        val full = rule.onNodeWithTag("fold-frame").fetchSemanticsNode().boundsInRoot
        val pane = rule.onNodeWithTag("fold-content").fetchSemanticsNode().boundsInRoot
        assertTrue(pane.right <= full.center.x - 10)
        assertTrue(pane.width > full.width * .4f)
        rule.onNodeWithText("Count 1").performClick()
        rule.runOnIdle { folded = false }
        rule.onNodeWithText("Count 2").assertIsDisplayed()
        assertEquals(full.width, rule.onNodeWithTag("fold-content").fetchSemanticsNode().boundsInRoot.width, 1f)
    }

    @androidx.test.filters.SdkSuppress(minSdkVersion = 29)
    @Test fun floatingDialogMovesClearOfASeparatingHingeAndKeepsItsAction() {
        var hingeLeft = 0; var confirmed = false
        rule.setContent { MaterialTheme { BoxWithConstraints(Modifier.fillMaxSize()) {
            val width = with(LocalDensity.current) { maxWidth.roundToPx() }
            hingeLeft = width / 2 - 12
            CompositionLocalProvider(LocalVernFoldOverride provides listOf(VernFoldBounds(VernPaneBounds(hingeLeft, 0, width / 2 + 12, 100000), true))) {
                VernAlertDialog({}, title = { Text("A fold-safe dialog") }, text = { Text("Its confirmation remains reachable.") },
                    confirmButton = { TextButton(onClick = { confirmed = true }) { Text("Confirm fixture") } })
            }
        } } }
        var positioned = false
        rule.waitUntil(10_000) {
            rule.runOnIdle {
                fun window(view: android.view.View): android.view.Window? {
                    if (view is androidx.compose.ui.window.DialogWindowProvider) return view.window
                    if (view is android.view.ViewGroup) for (i in 0 until view.childCount) window(view.getChildAt(i))?.let { return it }
                    return null
                }
                val dialog = android.view.inspector.WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::window)?.decorView
                if (dialog != null) {
                    val xy = IntArray(2); dialog.getLocationOnScreen(xy)
                    positioned = dialog.width > 0 && xy[0] >= 0 && xy[0] + dialog.width <= hingeLeft + 2
                }
            }
            positioned
        }
        rule.onNodeWithText("Confirm fixture").assertIsDisplayed().performClick()
        rule.runOnIdle { assertTrue(confirmed) }
    }
}
