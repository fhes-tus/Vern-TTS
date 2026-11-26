package com.veritas.reader

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

class BackupRestoreReviewTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun classificationUsesTheSameTextAndIndexRuleAsTheMerger() {
        val root = JSONObject().put("documents", JSONArray().apply {
            put(reading("new", "New"))
            put(reading("match", "Same"))
            put(reading("different-text", "Different"))
            put(reading("different-index", "Same").put("sentenceIndexVersion", 2))
            put(reading("blank", " "))
        })
        val local = listOf("match", "different-text", "different-index").map { id ->
            SavedDocument(
                id = id, title = id, fileName = "$id.txt", sourceLabel = "Test",
                createdAt = 1, updatedAt = 1, currentIndex = 0, chunkCount = 1, charCount = 4, preview = "Same",
                sentenceIndexVersion = 1
            )
        }
        assertEquals(
            listOf(BackupReadingAction.ADD, BackupReadingAction.MATCH, BackupReadingAction.CONFLICT, BackupReadingAction.CONFLICT),
            classifyBackupReadings(root, local) { "Same" }.map { it.action }
        )
    }

    @Test
    fun verifiedSharedReviewKeepsTheExactInspectedBytesAndRemovesIncompleteCopies() {
        val cache = temp.newFolder()
        val original = """{"documents":[]}""".toByteArray()
        val sha256 = MessageDigest.getInstance("SHA-256").digest(original).joinToString("") { "%02x".format(it) }
        val staged = stageBackupForReview(cache, { original.inputStream() }, sha256) {}
        assertEquals(original.toList(), staged.readBytes().toList())

        assertThrows(IllegalArgumentException::class.java) {
            stageBackupForReview(cache, { original.copyOf(original.size - 1).inputStream() }, sha256) {}
        }
        assertEquals(listOf(staged), cache.listFiles()?.toList())
        assertFalse(staged.readText().isBlank())
    }

    private fun reading(id: String, text: String) = JSONObject().put("id", id).put("text", text)
}
