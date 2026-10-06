package com.veritas.reader

import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FileBrowserProgressInstrumentedTest {
    @Test fun phoneNavigationPublishesBeforeDeepDiscoveryAndKeepsNestedFiles() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.executeShellCommand("appops set ${context.packageName} MANAGE_EXTERNAL_STORAGE allow").let {
            ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> input.readBytes() }
        }
        val started = SystemClock.elapsedRealtime()
        var firstMillis = -1L
        try {
            VeritasFileBrowserScanner.scan(context, emptyList(), true, onProgress = { partial ->
                firstMillis = SystemClock.elapsedRealtime() - started
                assertTrue(partial.files.any { it.isDirectory })
                throw CancellationException("First snapshot verified; don't scan user files in this test")
            })
            fail("Scan cancellation must propagate")
        } catch (_: CancellationException) { }
        assertTrue("Phone navigation must appear promptly ($firstMillis ms)", firstMillis in 0..2000)
        android.util.Log.i("FileBrowserRegression", "Phone folders ready after ${firstMillis}ms")

        val fixture = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "vern_check_${UUID.randomUUID()}")
        try {
            val nested = File(fixture, "nested/deeper").apply { assertTrue(mkdirs()) }
            File(fixture, "first.txt").writeText("First document.")
            File(nested, "nested.pdf").writeText("Discovery checks metadata rather than parsing this file.")
            var firstNames: List<String>? = null
            val result = VeritasFileBrowserScanner.scan(context, emptyList(), true,
                VeritasBrowserLocation("Phone storage", filePath = fixture.absolutePath),
                onProgress = { partial -> if (firstNames == null) firstNames = partial.files.map { it.name } })
            assertTrue(firstNames!!.contains("nested"))
            assertFalse(firstNames!!.contains("nested.pdf"))
            assertTrue(result.files.any { it.name == "first.txt" })
            assertTrue(result.files.any { it.name == "nested.pdf" })
            assertEquals(result.files.size, result.files.map { it.uri }.distinct().size)
        } finally { fixture.deleteRecursively() }
    }
}
