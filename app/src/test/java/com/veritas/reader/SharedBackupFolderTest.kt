package com.veritas.reader

import java.io.ByteArrayInputStream
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SharedBackupFolderTest {
    private val epoch = Instant.parse("2026-11-21T08:35:42.123Z")
    private val hash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test fun repeatedSendsKeepTheirOwnNamedCopyAndTheExactExpectedDigest() {
        val first = SharedBackupSnapshotName.create(epoch, hash)
        val second = SharedBackupSnapshotName.create(epoch, hash)

        assertNotEquals(first, second)
        assertEquals(SharedBackupSnapshotName(epoch, hash), SharedBackupSnapshotName.parse(first))
        assertEquals(SharedBackupSnapshotName(epoch, hash), SharedBackupSnapshotName.parse(second))
    }

    @Test fun ordinaryFilesAndChangedManagedNamesDoNotEnterReview() {
        val name = SharedBackupSnapshotName.create(epoch, hash)
        assertNull(SharedBackupSnapshotName.parse("backup.json"))
        assertNull(SharedBackupSnapshotName.parse("../$name"))
        assertNull(SharedBackupSnapshotName.parse(name.replace("20261121", "20260230")))
        assertNull(SharedBackupSnapshotName.parse(name.replace(hash, "z".repeat(64))))
        assertNull(SharedBackupSnapshotName.parse("$name.pending"))
    }

    @Test fun verifiesTheCompleteProviderCopyAndRejectsShortLongOrChangedBytes() {
        fun verify(body: String) = SharedBackupFolder.verifyWrittenBytes(ByteArrayInputStream(body.toByteArray()), 3, hash) {}
        verify("abc")
        assertThrows(IllegalStateException::class.java) { verify("acb") }
        assertThrows(IllegalStateException::class.java) { verify("ab") }
        assertThrows(IllegalStateException::class.java) { verify("abcd") }
    }
}
