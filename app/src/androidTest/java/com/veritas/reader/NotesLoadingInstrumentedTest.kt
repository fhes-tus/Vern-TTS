package com.veritas.reader

import android.content.Intent
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.veritas.reader.ui.screens.rememberNoteAudioDurationMillis
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Fixtures run only in the isolated checks package, never the user's installed library. */
class NotesLoadingInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var repository: DocumentRepository
    private var originalNotes: String? = null
    private var originalBackup: String? = null
    private val files = mutableListOf<File>()

    @Before fun preserveNotes() {
        check(context.packageName.endsWith(".notesload.checks"))
        repository = DocumentRepository(context)
        originalNotes = repository.prefs.getString("general_notes", null)
        originalBackup = repository.prefs.getString("general_notes__bak", null)
    }

    @After fun restoreNotes() {
        if (!::repository.isInitialized) return
        repository.prefs.edit().apply {
            if (originalNotes == null) remove("general_notes") else putString("general_notes", originalNotes)
            if (originalBackup == null) remove("general_notes__bak") else putString("general_notes__bak", originalBackup)
        }.commit()
        files.forEach { it.delete() }
    }

    private fun fixtureFile(extension: String = "wav"): File =
        File(context.filesDir, "notes_media/notes-loading-${UUID.randomUUID()}.$extension").also {
            it.parentFile!!.mkdirs(); files.add(it)
        }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
        while (!condition()) {
            check(android.os.SystemClock.elapsedRealtime() < deadline) { "Timed out waiting for the composed duration" }
            Thread.sleep(20)
        }
    }

    private fun wav(seconds: Int): File {
        val samples = 8000 * seconds
        val bytes = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
        bytes.putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16)
        bytes.put("data".toByteArray()).putInt(samples * 2)
        return fixtureFile().apply { writeBytes(bytes.array()) }
    }

    @Test fun snapshotsStayFreshAcrossEditTrashRestoreAndDeletionWithoutLosingAttachments() {
        val media = fixtureFile("pdf").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val older = GeneralNote("older", "Older", "Keep this", 10)
        val newer = GeneralNote("newer", "Newer", "[file:Report.pdf:3](${media.path})", 20)
        val oldTrash = GeneralNote("old-trash", "Old trash", "Keep too", 5, deletedAt = 100)
        val newTrash = GeneralNote("new-trash", "New trash", "Keep too", 6, deletedAt = 200)
        repository.replaceStoredGeneralNotes(listOf(older, newTrash, newer, oldTrash))
        val originalJson = repository.prefs.getString("general_notes", null)
        val first = repository.loadGeneralNotesSnapshot()
        assertEquals(listOf("newer", "older"), first.active.map { it.id })
        assertEquals(listOf("new-trash", "old-trash"), first.trashed.map { it.id })
        val attachment = first.active.first().attachments.single()
        assertArrayEquals(byteArrayOf(1, 2, 3), attachment.resolve(context)!!.readBytes())
        assertEquals(originalJson, repository.prefs.getString("general_notes", null))

        repository.mutateGeneralNotes { notes -> notes.map { if (it.id == "older") it.copy(content = "Edited", updatedAt = 30) else it } }
        assertEquals("Edited", repository.loadGeneralNotesSnapshot().active.first().content)
        assertEquals("Keep this", first.active.single { it.id == "older" }.content)
        repository.moveGeneralNoteToTrash("newer")
        val trashed = repository.loadGeneralNotesSnapshot()
        assertFalse(trashed.active.any { it.id == "newer" })
        assertEquals(attachment.id, trashed.trashed.single { it.id == "newer" }.attachments.single().id)
        repository.restoreGeneralNote("newer")
        val restored = DocumentRepository(context).loadGeneralNotesSnapshot()
        assertTrue(restored.active.any { it.id == "newer" })
        assertFalse(restored.trashed.any { it.id == "newer" })
        repository.permanentlyDeleteTrashedNote("old-trash")
        assertFalse(repository.loadGeneralNotesSnapshot().trashed.any { it.id == "old-trash" })
        assertTrue(media.isFile)
    }

    @Test fun malformedPrimaryKeepsBackupRecoveryForBothListsWithoutRewritingStorage() {
        val active = GeneralNote("backup-active", "Active", "Content", 10)
        val trash = GeneralNote("backup-trash", "Trash", "Content", 20, deletedAt = 30)
        val backup = JSONArray().put(active.toJson()).put(trash.toJson()).toString()
        repository.prefs.edit().putString("general_notes", "{broken")
            .putString("general_notes__bak", backup).commit()
        val snapshot = repository.loadGeneralNotesSnapshot()
        assertEquals(listOf(active), snapshot.active)
        assertEquals(listOf(trash), snapshot.trashed)
        assertEquals("{broken", repository.prefs.getString("general_notes", null))
        assertEquals(backup, repository.prefs.getString("general_notes__bak", null))
    }

    @Test fun changedCardClearsOldLabelAndLateCancelledMetadataCannotReplaceItsDuration() {
        val a = fixtureFile().apply { writeText("1000") }
        val b = fixtureFile().apply { writeText("2000") }
        val c = fixtureFile().apply { writeText("3000") }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val reader = NoteAudioDurationReader(readMetadata = { file ->
            assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
            if (file == b.canonicalFile) { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) }
            file.readText().toLong()
        })
        val selected = mutableStateOf(a.path)
        val observed = AtomicReference<Long?>(null)
        val history = CopyOnWriteArrayList<Long?>()
        val scenario = ActivityScenario.launch<ComponentActivity>(Intent(context, ComponentActivity::class.java))
        try {
            scenario.onActivity { activity -> activity.setContent {
                val duration = rememberNoteAudioDurationMillis(selected.value, reader = reader)
                SideEffect { observed.set(duration); history.add(duration) }
                Text(duration?.toString() ?: "pending", Modifier.testTag("duration"))
            } }
            awaitCondition { observed.get() == 1000L }
            scenario.onActivity { selected.value = b.path }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            awaitCondition { observed.get() == null }
            scenario.onActivity { selected.value = c.path }
            release.countDown()
            awaitCondition { observed.get() == 3000L }
            assertFalse(history.contains(2000L))
        } finally { release.countDown(); scenario.close() }
    }

    @Test fun storedDurationNeedsNoFileReadAndUpdatesImmediately() {
        val reads = AtomicInteger()
        val reader = NoteAudioDurationReader(readMetadata = { reads.incrementAndGet(); 1000L })
        val known = mutableStateOf(45_000L)
        val observed = AtomicReference<Long?>(null)
        val scenario = ActivityScenario.launch<ComponentActivity>(Intent(context, ComponentActivity::class.java))
        try {
            scenario.onActivity { activity -> activity.setContent {
                val duration = rememberNoteAudioDurationMillis("missing-recording.wav", knownMillis = known.value, reader = reader)
                SideEffect { observed.set(duration) }
                Text(duration?.toString() ?: "pending", Modifier.testTag("duration"))
            } }
            awaitCondition { observed.get() == 45_000L }
            scenario.onActivity { known.value = 60_000L }
            awaitCondition { observed.get() == 60_000L }
            assertEquals(0, reads.get())
        } finally {
            scenario.close()
        }
    }

    @Test fun nativeWavDurationAndPlaybackSeekingStillWorkAndInvalidMediaIsHandled() {
        val file = wav(3)
        val invalid = fixtureFile().apply { writeText("not audio") }
        val reader = NoteAudioDurationReader()
        runBlocking {
            val duration = reader.read(file.path)
            assertNotNull(duration)
            assertTrue(duration!! in 2900L..3100L)
            assertNull(reader.read(invalid.path))
            assertEquals(duration, reader.read(file.path))
        }
        try {
            instrumentation.runOnMainSync { VoiceNoteRecorder.playAudio(file.path) }
            assertEquals(VoiceRecordingState.PLAYING, VoiceNoteRecorder.recordingState.value)
            instrumentation.runOnMainSync { VoiceNoteRecorder.seekTo(0.5f) }
            val deadline = android.os.SystemClock.elapsedRealtime() + 1500
            while (VoiceNoteRecorder.playbackPositionMs.value < 1000 && android.os.SystemClock.elapsedRealtime() < deadline) {
                Thread.sleep(25)
            }
            assertTrue(VoiceNoteRecorder.playbackPositionMs.value >= 1000)
        } finally { instrumentation.runOnMainSync { VoiceNoteRecorder.stopPlayback() } }
        assertTrue(file.isFile)
    }
}
