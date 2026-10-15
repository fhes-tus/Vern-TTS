package com.veritas.reader

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ManagedAttachmentsInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also { check(it.packageName.endsWith(".checks")) }
    @Test fun legacySentenceAudioGainsIdentityAndSurvivesMissingFileWithoutLosingText() {
        val repo = DocumentRepository(context)
        val document = repo.createDocument("Typed media fixture", "A sentence for a recording.", "TXT")
        val file = File(context.filesDir, "voice_notes/${UUID.randomUUID()}.amr").apply { parentFile!!.mkdirs(); writeText("#!AMR\nfixture") }
        try {
            repo.upsertAnnotation(document.id, 0, AnnotationType.NOTE, note = "Keep this text", audioPath = file.path, audioDurationSeconds = 7)
            val original = repo.loadAnnotations(document.id).single(); val record = requireNotNull(original.audioAttachment)
            assertEquals(AttachmentKind.AUDIO, record.kind); assertEquals(7000, record.durationMillis)
            assertEquals("audio/amr", record.mimeType); assertEquals(file.canonicalFile, record.resolve(context))
            file.delete()
            val reloaded = DocumentRepository(context).loadAnnotations(document.id).single()
            assertEquals("Keep this text", reloaded.note); assertEquals(record.id, reloaded.audioAttachment!!.id)
            assertNull(reloaded.audioAttachment!!.resolve(context))
        } finally { file.delete(); repo.deleteDocument(document.id) }
    }
    @Test fun portableQuizArchiveRebasesMediaWithStableIdentityAndProtectsItsOwner() {
        val repo = DocumentRepository(context); val before = repo.loadAllQuizzes()
        val id = UUID.randomUUID().toString()
        val file = File(context.filesDir, "notes_media/$id.wav").apply { parentFile!!.mkdirs(); PcmWavWriter(this, 24000).use { it.append(ShortArray(2400)) } }
        val record = managedAttachment(context, file.path, AttachmentKind.AUDIO, "quiz:$id:0").copy(id = "stable-$id")
        val quiz = QuizSet(id, "Portable quiz fixture", questions = listOf(QuizQuestion("Question?", "Yes", listOf("Yes", "No"), attachments = listOf(record))))
        val root = JSONObject().put("schema", "veritas.reader.backup.v1").put("documents", JSONArray()).put("quizzes", JSONArray().put(quiz.toJson()))
            .put("noteMedia", JSONArray().put(JSONObject().put("id", record.id).put("path", "attachments/0_$id.wav").put("fileName", "fixture.wav")
                .put("size", file.length()).put("sha256", mediaSha256(file))))
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json")); zip.write(root.toString().toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("note_media/fixture.wav")); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
        }
        val restoredFiles = mutableListOf<File>()
        try {
            repo.restoreBackupAuto(ByteArrayInputStream(bytes.toByteArray()))
            val restored = repo.loadAllQuizzes().first { it.id == id }.questions.single().attachments.single()
            val target = requireNotNull(restored.resolve(context)); restoredFiles.add(target)
            assertEquals(record.id, restored.id); assertNotEquals(file.path, target.path)
            target.setLastModified(System.currentTimeMillis() - 2 * 86400000L)
            assertFalse(repo.findUnusedNoteMedia().any { it.file.canonicalFile == target.canonicalFile })
            ManagedMediaLeases.acquire(file.canonicalPath)
            file.setLastModified(System.currentTimeMillis() - 2 * 86400000L)
            assertFalse(repo.findUnusedNoteMedia().any { it.file.canonicalFile == file.canonicalFile })
            ManagedMediaLeases.release(file.canonicalPath)
            repo.saveAllQuizzes(before)
            assertTrue(repo.findUnusedNoteMedia().any { it.file.canonicalFile == target.canonicalFile })
        } finally { ManagedMediaLeases.release(file.canonicalPath); repo.saveAllQuizzes(before); restoredFiles.forEach(File::delete); file.delete() }
    }
    @Test fun relativePathsCannotResolveOutsideManagedStorage() {
        val record = ManagedAttachment("id", AttachmentKind.IMAGE, "image/png", 0, ownerId = "quiz", relativePath = "notes_media/../other")
        assertNull(record.resolve(context)); assertNull(record.copy(relativePath = "notes_media/..\\other").resolve(context))
    }
}
