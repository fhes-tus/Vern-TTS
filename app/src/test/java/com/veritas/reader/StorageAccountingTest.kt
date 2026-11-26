package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageAccountingTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun ownedFoldersAreCountedOnlyOnceInTheBreakdown() {
        val files = temp.newFolder("files")
        val originals = File(files, "original_documents").apply { mkdir() }
        File(originals, "reading.pdf").writeBytes(ByteArray(512))
        File(files, "preferences").writeBytes(ByteArray(37))
        val breakdown = StorageBreakdown(
            textBytes = 0,
            originalsBytes = originals.storageBytes(),
            coversBytes = 0,
            documentCount = 1,
            otherBytes = temp.root.storageBytes(setOf(originals.absoluteFile.normalize()))
        )
        assertEquals(512L, breakdown.originalsBytes)
        assertEquals(37L, breakdown.otherBytes)
        assertEquals(549L, breakdown.totalBytes)
    }
}
