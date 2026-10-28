package com.veritas.reader

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class ScanImportInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun photo(file: File, width: Int = 1600, height: Int = 1000) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 80f }
            canvas.drawText("VERITAS SCAN CHECK", 80f, 200f, ink)
            canvas.drawText("Read this page aloud.", 80f, 350f, ink)
            file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        } finally { bitmap.recycle() }
    }

    @Test fun largeRotatedPhotoIsDecodedWithinMemoryBudget() {
        val file = File.createTempFile("scan_bounds_", ".jpg", context.cacheDir)
        try {
            photo(file, 3200, 2000)
            ExifInterface(file.path).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                saveAttributes()
            }
            val decoded = decodeImageBitmap(context, Uri.fromFile(file))
            try {
                assertTrue(decoded.height > decoded.width)
                assertTrue(maxOf(decoded.width, decoded.height) <= 2560)
                assertTrue(decoded.width.toLong() * decoded.height <= 4_000_000)
                assertTrue(decoded.allocationByteCount <= 16_000_000)
            } finally { decoded.recycle() }
        } finally { file.delete() }
    }

    @Test fun realOnDeviceOcrExtractsTextFromCapturedImage() = runBlocking {
        val file = ScanCaptureStore.create(context)
        try {
            photo(file)
            val uri = requireNotNull(ScanCaptureStore.finish(context, file.path, true))
            val result = DocumentExtractor.extract(context, uri, "Scanned page")
            assertEquals("OCR", result.sourceLabel)
            assertTrue(result.text, result.text.contains("VERITAS SCAN CHECK"))
            assertTrue(result.text, result.text.contains("Read this page aloud"))
        } finally { ScanCaptureStore.discard(context, Uri.fromFile(file)) }
    }

    @Test fun cancelledEmptyAndUnownedCapturesAreHandledSafely() {
        val cancelled = ScanCaptureStore.create(context).apply { writeText("temporary") }
        assertNull(ScanCaptureStore.finish(context, cancelled.path, false))
        assertFalse(cancelled.exists())
        val empty = ScanCaptureStore.create(context)
        assertNull(ScanCaptureStore.finish(context, empty.path, true))
        assertFalse(empty.exists())
        val unrelated = File.createTempFile("unrelated_", ".jpg", context.cacheDir).apply { writeText("keep") }
        try {
            assertNull(ScanCaptureStore.finish(context, unrelated.path, false))
            ScanCaptureStore.discard(context, Uri.fromFile(unrelated))
            assertTrue(unrelated.exists())
        } finally { unrelated.delete() }
    }

    @Test fun oversizedTextMissingProvidersAndCorruptPhotosAreRejected() = runBlocking {
        val large = File.createTempFile("large_text_", ".txt", context.cacheDir)
        val invalidPhoto = File.createTempFile("invalid_scan_", ".jpg", context.cacheDir).apply { writeText("Not an image") }
        try {
            java.io.RandomAccessFile(large, "rw").use { it.setLength(16L * 1024 * 1024 + 1) }
            val sizeError = runCatching { validateDocumentImport(context, Uri.fromFile(large)) }.exceptionOrNull()
            assertTrue(sizeError?.message.orEmpty().contains("16 MB safety limit"))
            assertTrue(runCatching { validateDocumentImport(context, Uri.parse("content://missing.vern.provider/missing.pdf")) }.isFailure)
            assertTrue(runCatching { DocumentExtractor.extract(context, Uri.fromFile(invalidPhoto), "Corrupt photo") }.isFailure)
        } finally { large.delete(); invalidPhoto.delete() }
    }

    @Test fun bothSourceChoicesLaunchTheRightContractAndReturnAnImage() {
        val selected = mutableListOf<Uri>()
        val actions = mutableListOf<String?>()
        val gallery = File.createTempFile("picker_", ".jpg", context.cacheDir).apply { photo(this) }
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
                options: ActivityOptionsCompat?) {
                val intent = contract.createIntent(context, input)
                actions.add(intent.action)
                if (intent.action == android.provider.MediaStore.ACTION_IMAGE_CAPTURE) {
                    @Suppress("DEPRECATION") val output = intent.getParcelableExtra<Uri>(android.provider.MediaStore.EXTRA_OUTPUT)!!
                    val source = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
                    try { context.contentResolver.openOutputStream(output)!!.use { source.compress(Bitmap.CompressFormat.JPEG, 90, it) } }
                    finally { source.recycle() }
                    dispatchResult(requestCode, Activity.RESULT_OK, Intent())
                } else {
                    assertFalse("Photo picker must not allow video", intent.type == "video/*")
                    dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(Uri.fromFile(gallery)))
                }
            }
        }
        val owner = object : ActivityResultRegistryOwner { override val activityResultRegistry = registry }
        try {
            compose.setContent {
                MaterialTheme {
                    CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                        val open = rememberScanImportAction({ selected.add(it) }, { fail(it) })
                        Button(onClick = open) { Text("Open scan") }
                    }
                }
            }
            compose.onNodeWithText("Open scan").performClick()
            compose.onNodeWithText("Take photo").assertIsDisplayed()
            compose.onNodeWithText("Choose photo").assertIsDisplayed()
            compose.waitForIdle()
            val screenshot = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val screenshotFile = File(context.getExternalFilesDir(null), "verification/scan-source-choice.png").apply { parentFile!!.mkdirs() }
            try { screenshotFile.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { screenshot.recycle() }
            compose.onNodeWithText("Take photo").performClick()
            compose.waitUntil { selected.size == 1 }
            assertEquals(android.provider.MediaStore.ACTION_IMAGE_CAPTURE, actions.first())
            assertTrue(File(selected.first().path!!).exists())
            compose.onNodeWithText("Open scan").performClick()
            compose.onNodeWithText("Choose photo").assertIsDisplayed().performClick()
            compose.waitUntil { selected.size == 2 }
            assertEquals(Uri.fromFile(gallery), selected.last())
            assertNotEquals(android.provider.MediaStore.ACTION_IMAGE_CAPTURE, actions.last())
        } finally {
            selected.forEach { ScanCaptureStore.discard(context, it) }
            gallery.delete()
        }
    }

    @Test fun missingCameraShowsAnErrorAndCleansThePendingCapture() {
        var message = ""
        val before = File(context.filesDir, "scan_imports").listFiles().orEmpty().map { it.name }.toSet()
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I,
                    options: ActivityOptionsCompat?) { throw ActivityNotFoundException("No camera installed") }
            }
        }
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                    val open = rememberScanImportAction({ fail("Unexpected photo") }, { message = it })
                    Button(onClick = open) { Text("Open scan") }
                }
            }
        }
        compose.onNodeWithText("Open scan").performClick()
        compose.onNodeWithText("Take photo").performClick()
        compose.waitUntil { message.isNotBlank() }
        assertTrue(message.contains("Could not open the camera"))
        assertEquals(before, File(context.filesDir, "scan_imports").listFiles().orEmpty().map { it.name }.toSet())
    }
}
