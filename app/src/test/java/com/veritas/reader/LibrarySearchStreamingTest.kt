package com.veritas.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LibrarySearchStreamingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun testFileContainsNeedle(file: File, needle: String): Boolean {
        if (!file.exists() || file.length() == 0L) return false
        val bufferSize = 64
        val overlap = (needle.length - 1).coerceAtLeast(0)
        val charBuffer = CharArray(bufferSize)
        return runCatching {
            file.bufferedReader(Charsets.UTF_8).use { reader ->
                val carryOver = StringBuilder(overlap)
                var charsRead: Int
                while (reader.read(charBuffer, 0, bufferSize).also { charsRead = it } != -1) {
                    val chunk = buildString(carryOver.length + charsRead) {
                        append(carryOver)
                        appendRange(charBuffer, 0, charsRead)
                    }
                    if (chunk.contains(needle, ignoreCase = true)) {
                        return@use true
                    }
                    carryOver.clear()
                    if (chunk.length >= overlap) {
                        carryOver.append(chunk.substring(chunk.length - overlap))
                    } else {
                        carryOver.append(chunk)
                    }
                }
                false
            }
        }.getOrDefault(false)
    }

    @Test
    fun detectsNeedleAtBeginning() {
        val file = tempFolder.newFile("test1.txt").apply {
            writeText("Veritas Reader is an offline reader app with high quality TTS.")
        }
        assertTrue(testFileContainsNeedle(file, "Veritas"))
        assertTrue(testFileContainsNeedle(file, "veritas"))
    }

    @Test
    fun detectsNeedleAcrossBufferBoundary() {
        val prefix = "a".repeat(60)
        val keyword = "BOUNDARYTEST"
        val suffix = "b".repeat(100)
        val file = tempFolder.newFile("test_boundary.txt").apply {
            writeText(prefix + keyword + suffix)
        }
        assertTrue(testFileContainsNeedle(file, "BOUNDARYTEST"))
        assertTrue(testFileContainsNeedle(file, "boundarytest"))
        assertFalse(testFileContainsNeedle(file, "nonexistentword"))
    }

    @Test
    fun returnsFalseForEmptyOrMissingFile() {
        val file = tempFolder.newFile("empty.txt")
        assertFalse(testFileContainsNeedle(file, "hello"))
        val missing = File(tempFolder.root, "does_not_exist.txt")
        assertFalse(testFileContainsNeedle(missing, "hello"))
    }
}
