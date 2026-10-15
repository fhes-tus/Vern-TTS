package com.veritas.reader

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class BackupRestoreSafetyTest {
    @Test fun interruptedPublicationRecoversPreviousFilesOnRestart() {
        val root = java.nio.file.Files.createTempDirectory("restore-restart").toFile()
        try {
            val previous = java.io.File(root, "previous.txt").apply { writeText("Old text") }
            val added = java.io.File(root, "new.txt")
            val interrupted = RestoreFileTransaction(root)
            interrupted.stage(previous) { it.write("Replacement".toByteArray()) }
            interrupted.stage(added) { it.write("New reading".toByteArray()) }
            interrupted.publish()
            var restoredPreferences = false
            // Deliberately do not close: this represents process death after publication.
            RestoreFileTransaction.recoverPending(root, { false }) { restoredPreferences = true }
            assertEquals("Old text", previous.readText())
            assertFalse(added.exists())
            assertTrue(restoredPreferences)
            RestoreFileTransaction.recoverPending(root, { false })
            assertEquals("Old text", previous.readText())
        } finally { root.deleteRecursively() }
    }

    @Test fun committedRestoreSurvivesDeathBeforeJournalCleanup() {
        val root = java.nio.file.Files.createTempDirectory("restore-committed").toFile()
        try {
            val target = java.io.File(root, "book.txt").apply { writeText("Previous") }
            val interrupted = RestoreFileTransaction(root)
            interrupted.stage(target) { it.write("Restored".toByteArray()) }
            interrupted.publish()
            RestoreFileTransaction.recoverPending(root, { it == interrupted.id }) { error("Committed preferences must stay") }
            assertEquals("Restored", target.readText())
        } finally { root.deleteRecursively() }
    }
    @get:Rule val temporary = TemporaryFolder()

    private fun backup(id: String = "document-1", original: String = "book.pdf") = JSONObject()
        .put("schema", "veritas.reader.backup.v1")
        .put("documents", JSONArray().put(JSONObject().put("id", id).put("text", "A readable sentence.").put("originalFileName", original)))

    @Test fun validatesCurrentAndLegacyBackups() {
        assertNotNull(BackupRestoreSafety.parseAndValidate(backup().toString()))
        assertNotNull(BackupRestoreSafety.parseAndValidate(backup().apply { remove("schema") }.toString()))
    }

    @Test fun rejectsPathTraversalAndUnsafeOriginalReferences() {
        listOf("../escape", "..\\escape", "/absolute", "C:drive", "bad\u0000id").forEach { id ->
            assertThrows(IllegalArgumentException::class.java) { BackupRestoreSafety.parseAndValidate(backup(id).toString()) }
            assertThrows(IllegalArgumentException::class.java) { BackupRestoreSafety.parseAndValidate(backup(original = id).toString()) }
        }
    }

    @Test fun rejectsDuplicateDocumentIdsBeforePublication() {
        val root = backup()
        root.getJSONArray("documents").put(root.getJSONArray("documents").getJSONObject(0))
        assertThrows(IllegalArgumentException::class.java) { BackupRestoreSafety.parseAndValidate(root.toString()) }
    }

    @Test fun rejectsMalformedLaterSectionsAndUnsupportedSchemas() {
        assertThrows(IllegalArgumentException::class.java) { BackupRestoreSafety.parseAndValidate(backup().put("annotations", "broken").toString()) }
        assertThrows(IllegalArgumentException::class.java) { BackupRestoreSafety.parseAndValidate(backup().put("schema", "future.v99").toString()) }
    }

    @Test fun legacyContentUriIsNotTreatedAsAFilePath() {
        assertNotNull(BackupRestoreSafety.parseAndValidate(backup(original = "content://provider/books/1").toString()))
    }

    @Test fun boundsReadsAndAcceptsTheExactLimit() {
        val output = ByteArrayOutputStream()
        assertEquals(4L, BackupRestoreSafety.copyBounded(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), output, 4))
        assertThrows(IllegalArgumentException::class.java) {
            BackupRestoreSafety.copyBounded(ByteArrayInputStream(ByteArray(5)), ByteArrayOutputStream(), 4)
        }
    }

    @Test fun failedStagingLeavesExistingFilesUntouched() {
        val root = temporary.newFolder()
        val existing = File(root, "reading.txt").apply { writeText("previous text") }
        assertThrows(java.io.IOException::class.java) {
            RestoreFileTransaction(root).use { transaction ->
                transaction.stage(existing) { it.write("replacement".toByteArray()); throw java.io.IOException("disk failure") }
            }
        }
        assertEquals("previous text", existing.readText())
    }

    @Test fun failedMetadataRollsBackPublishedFiles() {
        val root = temporary.newFolder()
        val existing = File(root, "reading.txt").apply { writeText("previous text") }
        val added = File(root, "new.txt")
        RestoreFileTransaction(root).use { transaction ->
            transaction.stage(existing) { it.write("replacement".toByteArray()) }
            transaction.stage(added) { it.write("new reading".toByteArray()) }
            assertEquals("previous text", existing.readText())
            transaction.publish()
            assertEquals("replacement", existing.readText())
            // Leaving without commit models an exception from metadata persistence.
        }
        assertEquals("previous text", existing.readText())
        assertFalse(added.exists())
        assertEquals(listOf("reading.txt"), root.listFiles()!!.map { it.name })
    }

    @Test fun successfulCommitKeepsAllPublishedFiles() {
        val root = temporary.newFolder()
        val target = File(root, "new.txt")
        RestoreFileTransaction(root).use { transaction ->
            transaction.stage(target) { it.write("new reading".toByteArray()) }
            transaction.publish()
            transaction.commit()
        }
        assertEquals("new reading", target.readText())
        assertEquals(listOf("new.txt"), root.listFiles()!!.map { it.name })
    }

    @Test fun partialPublicationFailureRestoresPreviouslyPublishedFiles() {
        val root = temporary.newFolder()
        val existing = File(root, "reading.txt").apply { writeText("previous text") }
        val blockedParent = File(root, "blocked").apply { writeText("not a directory") }
        assertThrows(java.io.IOException::class.java) {
            RestoreFileTransaction(root).use { transaction ->
                transaction.stage(existing) { it.write("replacement".toByteArray()) }
                transaction.stage(File(blockedParent, "new.txt")) { it.write("new reading".toByteArray()) }
                transaction.publish()
                transaction.commit()
            }
        }
        assertEquals("previous text", existing.readText())
        assertEquals("not a directory", blockedParent.readText())
    }

    @Test fun stagingCannotWriteOutsideItsRoot() {
        val root = temporary.newFolder()
        RestoreFileTransaction(root).use { transaction ->
            assertThrows(IllegalArgumentException::class.java) { transaction.stage(File(root, "../escape.txt")) {} }
        }
    }
}
