package com.veritas.reader

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class NoteCameraCaptureInstrumentedTest {
    @Test fun cameraPublishesCapturedPhotosAndCancelsOnlyItsOwnTemporaryFiles() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".checks"))
        val captured = NoteCameraCapture.create(context)
        val cancelled = NoteCameraCapture.create(context)
        val empty = NoteCameraCapture.create(context)
        val unrelated = File.createTempFile("existing-attachment", ".jpg", context.cacheDir)
        var published: File? = null
        try {
            val bitmap = android.graphics.Bitmap.createBitmap(16, 16, android.graphics.Bitmap.Config.ARGB_8888)
            captured.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
            val bytes = captured.readBytes()
            published = NoteCameraCapture.finish(context, captured.absolutePath, true)
            assertNotNull(published)
            assertArrayEquals(bytes, published!!.readBytes())
            assertFalse(captured.exists())
            cancelled.writeBytes(bytes)
            assertNull(NoteCameraCapture.finish(context, cancelled.absolutePath, false))
            assertFalse(cancelled.exists())
            assertNull(NoteCameraCapture.finish(context, empty.absolutePath, true))
            assertFalse(empty.exists())
            unrelated.writeBytes(bytes)
            assertNull(NoteCameraCapture.finish(context, unrelated.absolutePath, false))
            assertTrue(unrelated.exists())
        } finally {
            listOf(captured, cancelled, empty, unrelated).forEach { it.delete() }
            published?.delete()
        }
    }
}
