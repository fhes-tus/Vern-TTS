package com.veritas.reader

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class EdgeToEdgeInsetsInstrumentedTest {
    @Test fun fallbackIsVisibleInPortraitAndLandscapeWithLightAndDarkThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".checks"))
        val repository = DocumentRepository(context)
        val originalSettings = repository.loadReaderSettings()
        try {
            for (theme in listOf("light", "dark")) {
                repository.saveReaderSettings(originalSettings.copy(themeId = theme))
                for (landscape in listOf(false, true)) {
                    ActivityScenario.launch<VeritasPdfViewerActivity>(Intent(context, VeritasPdfViewerActivity::class.java)).use { scenario ->
                        scenario.onActivity { it.requestedOrientation = if (landscape)
                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                        val expected = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
                        var rotated = false
                        for (attempt in 0 until 40) {
                            scenario.onActivity { rotated = it.resources.configuration.orientation == expected }
                            if (rotated) break
                            Thread.sleep(100)
                        }
                        assertTrue("Orientation change timed out", rotated)
                        // Samsung's rotation surface animation outlives the configuration callback.
                        Thread.sleep(800)
                        instrumentation.waitForIdleSync()
                        var tapX = 0f
                        var tapY = 0f
                        scenario.onActivity { activity ->
                            val root = activity.findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as ViewGroup
                            val safe = requireNotNull(ViewCompat.getRootWindowInsets(root)).getInsets(
                                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                            assertTrue(root.paddingLeft >= safe.left)
                            assertTrue(root.paddingTop >= safe.top)
                            assertTrue(root.paddingRight >= safe.right)
                            assertTrue(root.paddingBottom >= safe.bottom)
                            val button = root.getChildAt(2)
                            val visible = Rect()
                            assertTrue(button.getGlobalVisibleRect(visible))
                            assertEquals(button.width, visible.width())
                            assertEquals(button.height, visible.height())
                            tapX = visible.exactCenterX()
                            tapY = visible.exactCenterY()
                            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                            assertEquals(activity.isLightTheme, controller.isAppearanceLightStatusBars)
                            assertEquals(activity.isLightTheme, controller.isAppearanceLightNavigationBars)
                        }
                        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                        try {
                            File(context.getExternalFilesDir(null), "edge-$theme-${if (landscape) "landscape" else "portrait"}.png")
                                .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        } finally { screenshot.recycle() }
                        // Exercise actual touch dispatch through the protection overlay.
                        val downTime = SystemClock.uptimeMillis()
                        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, tapX, tapY, 0)
                            try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
                        }
                        instrumentation.waitForIdleSync()
                        // finish() completes through the activity manager after input dispatch.
                        for (attempt in 0 until 40) {
                            if (scenario.state == Lifecycle.State.DESTROYED) break
                            Thread.sleep(100)
                        }
                        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
                    }
                }
            }
        } finally { repository.saveReaderSettings(originalSettings) }
    }

    @Test fun pdfFallbackAndChromeRespectInsetsOnEveryEdgeWithoutAccumulatingPadding() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        // A missing document deliberately exercises the fallback branch.
        ActivityScenario.launch<VeritasPdfViewerActivity>(Intent(context, VeritasPdfViewerActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                val content = activity.findViewById<FrameLayout>(android.R.id.content)
                val fallback = content.getChildAt(0)
                val insets = WindowInsetsCompat.Builder()
                    .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(50, 70, 60, 80))
                    .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(90, 100, 110, 0))
                    .build()
                repeat(2) { ViewCompat.dispatchApplyWindowInsets(fallback, insets) }
                with(activity) {
                    assertEquals(24.dp + 90, fallback.paddingLeft)
                    assertEquals(24.dp + 100, fallback.paddingTop)
                    assertEquals(24.dp + 110, fallback.paddingRight)
                    assertEquals(24.dp + 80, fallback.paddingBottom)
                }
                activity.buildLayout("Insets check")
                val root = content.getChildAt(0) as ViewGroup
                repeat(2) { ViewCompat.dispatchApplyWindowInsets(root, insets) }
                with(activity) {
                    val toolbar = requireNotNull(toolbarChrome)
                    val deck = requireNotNull(bottomChrome)
                    assertEquals(8.dp + 90, toolbar.paddingLeft)
                    assertEquals(100 + 14.dp, toolbar.paddingTop)
                    assertEquals(6.dp + 110, toolbar.paddingRight)
                    assertEquals(20.dp + 90, deck.paddingLeft)
                    assertEquals(20.dp + 110, deck.paddingRight)
                    assertEquals(80 + 10.dp, deck.paddingBottom)
                }
                // Theme changes update one background layer instead of stacking overlays.
                val protection = content.getChildAt(1)
                repeat(3) { activity.configureSystemBars() }
                assertEquals(2, content.childCount)
                assertSame(protection, content.getChildAt(1))
                assertFalse(protection.isClickable)
            }
        }
    }
}
