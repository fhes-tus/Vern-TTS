package com.veritas.reader

import android.content.Intent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.ReaderViewModel
import com.veritas.reader.ui.screens.VeritasHomeTab
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeNavigationInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun prepareIsolatedLibrary() {
        check(context.packageName.endsWith(".checks")) { "Use the isolated .checks package" }
        DocumentRepository(context).markOnboardingComplete("Navigation check")
        context.getSharedPreferences("veritas_reader_library", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("battery_unrestricted_never_ask", true).commit()
    }

    private fun tab(label: String) = compose.onNode(
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
            hasContentDescription(label)
    )

    private fun librarySection(label: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and hasText(label)

    private fun awaitSelected(label: String) {
        val matcher = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
            hasContentDescription(label) and isSelected()
        compose.waitUntil(20_000) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().size == 1
        }
        tab(label).assertIsSelected()
    }

    private fun awaitLibrarySection(label: String) {
        compose.waitUntil(20_000) {
            compose.onAllNodes(librarySection(label) and isSelected()).fetchSemanticsNodes().size == 1
        }
    }

    @Test fun tabsFollowApprovedOrderAndTapsSelectEachDestination() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
            compose.waitUntil(20_000) {
                compose.onAllNodes(hasContentDescription("Home")).fetchSemanticsNodes().isNotEmpty()
            }
            val labels = listOf("Home", "Library", "Study", "Notes")
            val positions = labels.map { label -> tab(label).fetchSemanticsNode().boundsInRoot.left }
            check(positions.zipWithNext().all { (left, right) -> left < right })
            labels.forEach { label ->
                tab(label).performClick()
                awaitSelected(label)
            }
        }
    }

    @Test fun swipesReachStudyBeforeNotesAndBackReturnsHome() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            awaitSelected("Home")
            tab("Home").performClick()
            val viewportHeight = compose.onNodeWithTag("home_pager").fetchSemanticsNode().boundsInRoot.height
            listOf("Library", "Library", "Study", "Notes").forEachIndexed { index, label ->
                compose.onNodeWithTag("home_pager").performTouchInput {
                    // Shelf rows keep their own horizontal scrolling; use the toolbar area.
                    val swipeY = when (index) {
                        2 -> (110f + com.veritas.reader.ui.screens.LibrarySectionPickerHeight.value) *
                            context.resources.displayMetrics.density
                        3 -> height * .4f
                        else -> centerY
                    }
                    swipe(
                        androidx.compose.ui.geometry.Offset(width * .88f, swipeY),
                        androidx.compose.ui.geometry.Offset(width * .12f, swipeY)
                    )
                }
                awaitSelected(label)
                org.junit.Assert.assertEquals("Swiping must retain the page viewport", viewportHeight,
                    compose.onNodeWithTag("home_pager").fetchSemanticsNode().boundsInRoot.height, 1f)
                if (index == 0) awaitLibrarySection("Added")
                if (index == 1) awaitLibrarySection("Classics")
            }
            compose.onAllNodes(isDialog()).assertCountEquals(0)
            compose.waitUntil(20_000) {
                var focused = false
                scenario.onActivity { focused = it.hasWindowFocus() }
                focused
            }
            androidx.test.espresso.Espresso.pressBack()
            awaitSelected("Home")
        }
    }

    @Test fun libraryIndicatorFollowsAnUnfinishedSwipe() {
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
            awaitSelected("Home")
            tab("Library").performClick()
            awaitSelected("Library")
            compose.onNode(librarySection("Added")).performClick()
            awaitLibrarySection("Added")
            val viewportHeight = compose.onNodeWithTag("home_pager").fetchSemanticsNode().boundsInRoot.height
            compose.onNodeWithTag("home_pager").performTouchInput {
                down(androidx.compose.ui.geometry.Offset(right - 10, centerY))
                moveTo(androidx.compose.ui.geometry.Offset(right - width * .4f, centerY), delayMillis = 350)
            }
            val progress = compose.onNodeWithTag("library_section_indicator").fetchSemanticsNode()
                .config[com.veritas.reader.ui.screens.LibraryTabProgress]
            org.junit.Assert.assertTrue("Indicator must move during the drag: $progress", progress > .05f && progress < .95f)
            org.junit.Assert.assertEquals(viewportHeight,
                compose.onNodeWithTag("home_pager").fetchSemanticsNode().boundsInRoot.height, 1f)
            compose.onNodeWithTag("home_pager").performTouchInput {
                moveTo(androidx.compose.ui.geometry.Offset(left + 10, centerY), delayMillis = 250)
                up()
            }
            awaitLibrarySection("Classics")
        }
    }

    @Test fun notesWidgetDoesNotOverrideLaterStudyLinkOrRecreation() {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_WIDGET_ACTION, MainActivity.ACTION_SHOW_NOTES)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            awaitSelected("Notes")
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[ReaderViewModel::class.java]
                    .navigateToHomeTab(VeritasHomeTab.STUDY)
            }
            awaitSelected("Study")
            scenario.recreate()
            awaitSelected("Study")
        }
    }

    @Test fun studyWidgetStillOpensStudyAfterReordering() {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_WIDGET_ACTION, MainActivity.ACTION_SHOW_STUDY_DASHBOARD)
        ActivityScenario.launch<MainActivity>(intent).use {
            awaitSelected("Study")
        }
    }
}
