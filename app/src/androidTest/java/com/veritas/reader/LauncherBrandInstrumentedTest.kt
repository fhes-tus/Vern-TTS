package com.veritas.reader

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LauncherBrandInstrumentedTest {
    @Test fun adaptiveBookAndEarbudsHaveComfortableMaskPaddingAndKeepBrandColors() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val icon = context.getDrawable(R.mipmap.vern_launcher) as AdaptiveIconDrawable
        val bitmap = Bitmap.createBitmap(432, 432, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, 432, 432); icon.draw(Canvas(bitmap))
        var violetPixels = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            if (Color.alpha(pixel) > 200 && Color.blue(pixel) > Color.red(pixel) + 25 && Color.blue(pixel) > Color.green(pixel) + 25) violetPixels++
        }
        assertTrue("Book mark must remain large enough at launcher size", violetPixels > 432 * 432 / 5)
        assertTrue("Ivory padding must separate the mark from the mask edge", violetPixels < 432 * 432 * 2 / 3)
        val file = File(context.getExternalFilesDir(null), "remaining-fixes/launcher-book-earbuds.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
    }
}
