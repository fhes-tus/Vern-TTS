package com.veritas.reader

import com.veritas.reader.ui.withUpdateStagingFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CancellationException

class UpdateStagingTest {
    @get:Rule val cache = TemporaryFolder()

    @Test
    fun canceledAttemptLeavesAnotherApkIntact() {
        lateinit var canceled: File
        val completed = withUpdateStagingFile(cache.root) { current ->
            current.writeText("current download")
            assertThrows(CancellationException::class.java) {
                withUpdateStagingFile(cache.root) { attempt ->
                    canceled = attempt
                    assertNotEquals(current, canceled)
                    canceled.writeText("abandoned download")
                    throw CancellationException("injected cancellation")
                }
            }
            assertFalse(canceled.exists())
            assertEquals("current download", current.readText())
            current
        }
        assertFalse(completed.exists())
    }
}
