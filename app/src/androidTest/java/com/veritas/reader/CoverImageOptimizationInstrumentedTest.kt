package com.veritas.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CoverImageOptimizationInstrumentedTest {
    @Test fun importedCoversAreBoundedWithoutUpscalingAndRespectOrientation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixtures = listOf(
            Triple(2400, 1600, 600 to 400),
            Triple(1600, 4000, 480 to 1200),
            Triple(80, 40, 80 to 40)
        )
        for ((width, height, expected) in fixtures) {
            checkCover(width, height, false, expected.first, expected.second)
        }
        checkCover(2400, 1600, true, 600, 900)

        val invalid = File(context.cacheDir, "shared_exports/invalid-cover.jpg").apply {
            parentFile!!.mkdirs()
            writeText("not an image")
        }
        val cover = File(CoverExtractor.coversDir(context), "invalid-cover.cover.webp")
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", invalid)
            assertNull(CoverExtractor.extractImageCover(context, "invalid-cover", uri))
            assertFalse(cover.exists())
        } finally {
            invalid.delete()
            cover.delete()
        }
    }

    private fun checkCover(width: Int, height: Int, rotated: Boolean, expectedWidth: Int, expectedHeight: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "optimized-cover-$width-$height-$rotated"
        val source = File(context.cacheDir, "shared_exports/$id.jpg").apply { parentFile!!.mkdirs() }
        val cover = File(CoverExtractor.coversDir(context), "$id.cover.webp")
        try {
            val original = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                original.eraseColor(Color.BLUE)
                source.outputStream().use { assertTrue(original.compress(Bitmap.CompressFormat.JPEG, 90, it)) }
            } finally { original.recycle() }
            if (rotated) ExifInterface(source.path).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val sourceBytes = source.readBytes()
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", source)
            assertEquals(cover.name, CoverExtractor.extractImageCover(context, id, uri))
            val decoded = requireNotNull(BitmapFactory.decodeFile(cover.path))
            try {
                assertEquals(expectedWidth, decoded.width)
                assertEquals(expectedHeight, decoded.height)
                assertTrue(decoded.allocationByteCount <= 600 * 1200 * 4)
            } finally { decoded.recycle() }
            assertArrayEquals(sourceBytes, source.readBytes())
        } finally {
            source.delete()
            cover.delete()
        }
    }
}
