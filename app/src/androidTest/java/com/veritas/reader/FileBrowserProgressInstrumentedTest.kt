package com.veritas.reader

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.ContextWrapper
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FileBrowserProgressInstrumentedTest {
    @Test fun phoneNavigationPublishesBeforeDeepDiscoveryAndKeepsNestedFiles() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
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
            assertTrue(firstNames!!.contains("first.txt"))
            assertFalse(firstNames!!.contains("nested.pdf"))
            assertTrue(result.files.any { it.name == "first.txt" })
            assertTrue(result.files.any { it.name == "nested.pdf" })
            assertEquals(result.files.size, result.files.map { it.uri }.distinct().size)
        } finally { fixture.deleteRecursively() }
    }

    @Test fun phoneStorageTiming() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("measureFileBrowser") == "true")
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        repeat(4) { sample ->
            val start = SystemClock.elapsedRealtime()
            var firstDocument: Long? = null
            val result = VeritasFileBrowserScanner.scan(context, emptyList(), true, onProgress = { partial ->
                if (firstDocument == null && partial.files.any { !it.isDirectory && it.type != VeritasBrowserTab.OCR }) {
                    firstDocument = SystemClock.elapsedRealtime() - start
                }
            })
            val finished = SystemClock.elapsedRealtime() - start
            android.util.Log.i("FileBrowserRegression",
                "Sample $sample, first document ${firstDocument ?: finished} ms, full scan $finished ms, ${result.files.size} entries")
            assertTrue(result.files.any { it.isDirectory })
        }
    }

    @Test fun approvedFolderPublishesBeforeOpeningNestedCursorsAndStopsWhenCancelled() {
        assumeTrue(Build.VERSION.SDK_INT >= 29)
        val queries = mutableListOf<String>()
        var openedCursor: Cursor? = null
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0

            override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                args: Array<out String>?, order: String?
            ): Cursor {
                assertTrue(openedCursor?.isClosed != false)
                val parent = DocumentsContract.getDocumentId(uri)
                queries.add(parent)
                return MatrixCursor(requireNotNull(projection)).apply {
                    when (parent) {
                        "root" -> {
                            addRow(arrayOf<Any?>("first", "first.txt", "text/plain", 12L, 1L))
                            addRow(arrayOf<Any?>("folder", "nested", DocumentsContract.Document.MIME_TYPE_DIR, null, 2L))
                        }
                        "folder" -> {
                            addRow(arrayOf<Any?>("deep", "nested.pdf", "application/pdf", 42L, 3L))
                            addRow(arrayOf<Any?>("secret", ".hidden.txt", "text/plain", 64L, 4L))
                        }
                    }
                    openedCursor = this
                }
            }
        }
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = ContentResolver.wrap(provider)
        val context = object : ContextWrapper(base) {
            override fun getContentResolver(): ContentResolver = resolver
        }
        val root = VeritasBrowserRoot(Uri.parse("content://veritas.test.documents/tree/root"), "Approved")
        var firstNames: Set<String>? = null
        val result = VeritasFileBrowserScanner.scan(context, listOf(root), onProgress = { partial ->
            if (firstNames == null) {
                assertEquals(listOf("root"), queries)
                assertTrue(openedCursor!!.isClosed)
                firstNames = partial.files.map { it.name }.toSet()
            }
        })
        assertEquals(setOf("first.txt", "nested"), firstNames)
        assertEquals(setOf("first.txt", "nested", "nested.pdf"), result.files.map { it.name }.toSet())

        queries.clear()
        try {
            VeritasFileBrowserScanner.scan(context, listOf(root), checkCancelled = {
                if (queries.lastOrNull() == "folder") throw CancellationException("Stopped in nested cursor")
            })
            fail("Cancellation must propagate from a nested folder")
        } catch (_: CancellationException) {
            assertEquals(listOf("root", "folder"), queries)
            assertTrue(openedCursor!!.isClosed)
        }
    }
}
