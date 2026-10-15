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
