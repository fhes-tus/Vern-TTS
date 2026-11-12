package com.veritas.reader

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveNativePdfBoundsTest {
    @Test fun narrowNativeTransportAndFoldedViewportKeepControlsInsideThePane() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        ActivityScenario.launch<VeritasPdfViewerActivity>(Intent(context, VeritasPdfViewerActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                activity.buildLayout("Responsive controls")
                val root = activity.findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as VernFoldFrameLayout
                with(activity) {
                    fun size(width: Int, height: Int) {
                        root.measure(View.MeasureSpec.makeMeasureSpec(width.dp, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height.dp, View.MeasureSpec.EXACTLY))
                        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                    }
                    size(320, 568)
                    fun header(view: View): VernPdfControlsHeader? {
                        if (view is VernPdfControlsHeader) return view
                        if (view is ViewGroup) for (i in 0 until view.childCount) header(view.getChildAt(i))?.let { return it }
                        return null
                    }
                    val transport = requireNotNull(header(root)).transport
                    for (i in 0 until transport.childCount) {
                        val button = transport.getChildAt(i)
                        assertTrue("Every native transport control fits", button.left >= 0 && button.right <= transport.width)
                        assertTrue("Native touch targets retain their minimum size", button.width >= 48.dp && button.height >= 48.dp)
                    }
                    root.folds = listOf(VernFoldBounds(VernPaneBounds(390.dp, 0, 410.dp, 600.dp), true))
                    size(800, 600)
                    // Pixel rounding can make either half the larger usable pane.
                    val deck = requireNotNull(bottomChrome)
                    assertTrue("Native controls avoid the separating hinge",
                        (root.paddingRight >= 800.dp - 390.dp && deck.right <= 390.dp) ||
                            (root.paddingLeft >= 410.dp && deck.left >= 410.dp))
                }
            }
        }
    }
    @Test fun deckUsesMeasuredParentAcrossExpansionShrinkAndInsetChanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        ActivityScenario.launch<VeritasPdfViewerActivity>(Intent(context, VeritasPdfViewerActivity::class.java)).use { scenario ->
            scenario.onActivity { activity ->
                activity.buildLayout("Responsive deck fixture")
                val root = activity.findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as ViewGroup
                val deck = requireNotNull(activity.bottomChrome) as ViewGroup
                val material = deck.getChildAt(0)
                with(activity) {
                    val insets = WindowInsetsCompat.Builder()
                        .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(12.dp, 24.dp, 16.dp, 20.dp)).build()
                    for (widthDp in listOf(320, 1000, 420, 720, 320)) {
                        ViewCompat.dispatchApplyWindowInsets(root, insets)
                        root.measure(View.MeasureSpec.makeMeasureSpec(widthDp.dp, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(640.dp, View.MeasureSpec.EXACTLY))
                        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                        val available = deck.measuredWidth - deck.paddingLeft - deck.paddingRight
                        assertEquals(minOf(580.dp, available), material.measuredWidth)
                        assertTrue(material.left >= deck.paddingLeft)
                        assertTrue(material.right <= deck.width - deck.paddingRight)
                    }
                }
            }
        }
    }
}
