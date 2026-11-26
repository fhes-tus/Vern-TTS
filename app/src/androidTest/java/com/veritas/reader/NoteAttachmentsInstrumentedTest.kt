package com.veritas.reader

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class NoteAttachmentsInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun deletedBookNoteSurvivesOtherEditsAndBackupRestore() {
        check(context.packageName.endsWith(".checks"))
        val repository = DocumentRepository(context)
        val deleted = repository.createDocument("Deleted book fixture", "An ordinary reading.", "TXT")
        val kept = repository.createDocument("Kept book fixture", "A separate reading.", "TXT")
        try {
            repository.saveDocumentNote(deleted.id, "A note worth keeping")
            repository.deleteDocument(deleted.id)
            repository.saveDocumentNote(kept.id, "Another note")
            assertEquals("A note worth keeping", repository.loadAllDocumentNotes()[deleted.id])
            val backup = ByteArrayOutputStream().also(repository::writeFullBackupZip).toByteArray()
            repository.deleteDocumentNote(deleted.id)
            repository.restoreBackupAuto(ByteArrayInputStream(backup), replaceExisting = false)
            assertNull(repository.findDocument(deleted.id))
            assertEquals("A note worth keeping", repository.loadAllDocumentNotes()[deleted.id])
        } finally {
            repository.deleteDocumentNotes(setOf(deleted.id, kept.id))
            repository.deleteDocument(deleted.id)
            repository.deleteDocument(kept.id)
        }
    }

    @Test fun newAndRecoveredVoiceMemosSurviveCacheRemovalAndBackupRestore() = runBlocking {
        check(context.packageName.endsWith(".checks"))
        val repository = DocumentRepository(context)
        val newId = UUID.randomUUID().toString()
        val oldId = UUID.randomUUID().toString()
        val exportFolder = File(context.cacheDir, "shared_exports").apply { mkdirs() }
        val bytes = byteArrayOf(10, 20, 30, 40)
        val fresh = File(exportFolder, "$newId.wav").apply { writeBytes(bytes) }
        val legacy = File(exportFolder, "$oldId.wav").apply { writeBytes(bytes) }
        val managed = mutableSetOf<File>()
        fun readMemos() = repository.loadGeneralNotes().filter { it.id == newId || it.id == oldId }
        fun assertPortableMemos() {
            val memos = readMemos()
            assertEquals(setOf(newId, oldId), memos.map { it.id }.toSet())
            for (memo in memos) {
                val audio = requireNotNull(memo.attachments.single().resolve(context))
                managed.add(audio)
                assertEquals("audio/wav", memo.attachments.single().mimeType)
                assertArrayEquals(bytes, audio.readBytes())
            }
        }
        try {
            repository.saveRecordedVoiceMemo(GeneralNote(newId, "New memo", "Recorded audio", 1), fresh)
            repository.mutateGeneralNotes {
                it + GeneralNote(oldId, "Existing memo", "Recorded audio", 2, audioUrl = legacy.path)
            }
            repository.recoverCachedVoiceMemos()
            fresh.delete()
            legacy.delete()
            assertPortableMemos()
            val backup = ByteArrayOutputStream().also(repository::writeFullBackupZip).toByteArray()
            repository.mutateGeneralNotes { notes -> notes.filterNot { it.id == newId || it.id == oldId } }
            managed.forEach { it.delete() }
            repository.restoreBackupAuto(ByteArrayInputStream(backup), replaceExisting = false)
            assertPortableMemos()
        } finally {
            repository.mutateGeneralNotes { notes -> notes.filterNot { it.id == newId || it.id == oldId } }
            managed.forEach { it.delete() }
            fresh.delete()
            legacy.delete()
        }
    }

    @Test fun copyPublishesCompleteOwnedMediaAndRejectsEmptyInputs() = runBlocking {
        val input = File(context.cacheDir, "attachment-${UUID.randomUUID()}.png")
        val contents = ByteArray(140_000) { (it % 251).toByte() }
        var copied: File? = null
        try {
            input.writeBytes(contents)
            copied = NoteAttachmentStore.copy(context, Uri.fromFile(input), false)
            assertArrayEquals(contents, copied.readBytes())
            assertEquals(File(context.filesDir, "notes_media").canonicalFile, copied.parentFile?.canonicalFile)
            input.writeBytes(byteArrayOf())
            try { NoteAttachmentStore.copy(context, Uri.fromFile(input), false); fail("Empty attachment accepted") }
            catch (expected: IllegalArgumentException) { assertTrue(expected.message.orEmpty().contains("empty")) }
            assertTrue(File(context.filesDir, "notes_media").listFiles().orEmpty().none { it.name.endsWith(".partial") })
        } finally { input.delete(); copied?.delete() }
    }

    @Test fun fullBackupRestoresImageAudioAndVideoAndRemapsTheirInlinePaths() {
        check(context.packageName.endsWith(".checks")) { "Run in the isolated test app" }
        val repository = DocumentRepository(context)
        val previousNotes = repository.loadGeneralNotes()
        val directory = File(context.filesDir, "notes_media").apply { mkdirs() }
        val id = UUID.randomUUID().toString()
        val image = File(directory, "$id.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val audio = File(directory, "$id.3gp").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val video = File(directory, "$id.mp4").apply { writeBytes(byteArrayOf(7, 8, 9)) }
        var restoredPaths = emptyList<String>()
        try {
            val note = GeneralNote(id, "Portable attachments", "Before\n![image](${image.absolutePath})\nBetween\n[audio](${audio.absolutePath})\n[video](${video.absolutePath})\nAfter", 1,
                imageUrl = image.absolutePath, audioUrl = audio.absolutePath, audioUrls = listOf(audio.absolutePath))
            repository.mutateGeneralNotes { listOf(note) + it }
            val zip = ByteArrayOutputStream().also { repository.writeFullBackupZip(it) }.toByteArray()
            repository.mutateGeneralNotes { notes -> notes.filterNot { it.id == id } }
            listOf(image, audio, video).forEach { it.delete() }
            repository.restoreBackupAuto(ByteArrayInputStream(zip), replaceExisting = false)
            val restored = repository.loadGeneralNotes().single { it.id == id }
            restoredPaths = restored.allImageUrls + restored.allAudioUrls + restored.allVideoUrls
            assertEquals(3, restoredPaths.size)
            assertFalse(restored.content.contains(image.absolutePath))
            assertFalse(restored.content.contains(audio.absolutePath))
            assertFalse(restored.content.contains(video.absolutePath))
            assertTrue(restored.content.contains("Before") && restored.content.contains("After"))
            assertArrayEquals(byteArrayOf(1, 2, 3), File(restored.allImageUrls.single()).readBytes())
            assertArrayEquals(byteArrayOf(4, 5, 6), File(restored.allAudioUrls.single()).readBytes())
            assertArrayEquals(byteArrayOf(7, 8, 9), File(restored.allVideoUrls.single()).readBytes())
        } finally {
            repository.saveGeneralNotes(previousNotes)
            restoredPaths.forEach { File(it).delete() }
            listOf(image, audio, video).forEach { it.delete() }
        }
    }
}
