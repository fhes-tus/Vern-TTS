package com.veritas.reader

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.compose
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ResponsivePlayerWidgetTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext.also { check(it.packageName.endsWith(".checks")) }

    @Test fun playerLoadingLayoutFitsItsAdvertisedMinimum() {
        instrumentation.runOnMainSync {
            val view = LayoutInflater.from(context).inflate(R.layout.widget_veritas_player, FrameLayout(context), false)
            val density = context.resources.displayMetrics.density
            for (size in listOf(130 to 64, 320 to 84)) {
                val width = (size.first * density).toInt(); val height = (size.second * density).toInt()
                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, width, height)
                assertVisibleBounds(view, width, height)
            }
        }
    }

    @OptIn(ExperimentalGlanceApi::class)
    @Test fun playerRemoteViewsFitMinimumAndExpandedBoundsAtBothFontScales() = runBlocking {
        for (font in listOf(1f, 2f)) for (dark in listOf(false, true)) {
            val config = Configuration(context.resources.configuration).apply {
                fontScale = font
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val themed = context.createConfigurationContext(config)
            for (size in listOf(DpSize(130.dp, 64.dp), DpSize(320.dp, 84.dp), DpSize(600.dp, 120.dp))) {
                val remote = VeritasPlayerWidget().compose(themed, size = size)
                instrumentation.runOnMainSync {
                    val view = remote.apply(themed, FrameLayout(themed))
                    val density = themed.resources.displayMetrics.density
                    val width = (size.width.value * density).toInt(); val height = (size.height.value * density).toInt()
                    view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    view.layout(0, 0, width, height)
                    assertVisibleBounds(view, width, height)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    val file = File(context.getExternalFilesDir(null), "responsive-45/player-${size.width.value.toInt()}-${size.height.value.toInt()}-$font-$dark.png").apply { parentFile!!.mkdirs() }
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
                }
            }
        }
    }

    private fun assertVisibleBounds(view: View, width: Int, height: Int) {
        fun visit(node: View) {
            if (node.visibility != View.VISIBLE) return
            if (node !is ViewGroup) {
                val bounds = android.graphics.Rect(0, 0, node.width, node.height)
                (view as ViewGroup).offsetDescendantRectToMyCoords(node, bounds)
                assertTrue("Visible widget content outside ${width}x$height: $bounds", bounds.left >= 0 && bounds.top >= 0 && bounds.right <= width + 2 && bounds.bottom <= height + 2)
                assertTrue("Visible widget content must have area", node.width > 0 && node.height > 0)
            }
            if (node is ViewGroup) for (i in 0 until node.childCount) visit(node.getChildAt(i))
        }
        visit(view)
    }
}
