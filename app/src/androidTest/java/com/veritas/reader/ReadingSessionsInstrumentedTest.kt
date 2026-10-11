package com.veritas.reader

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class ReadingSessionsInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext.also { check(it.packageName.endsWith(".checks")) }
    @Test fun sessionsCorrectionsAndRepeatedBackupRestoreKeepMeasuredAndLegacyTotalsSeparate() {
        val repository = DocumentRepository(context)
        val prefs = repository.prefs
        val saved = prefs.getString(READING_SESSIONS_KEY, null)
        val trackerDays = prefs.getString(DocumentRepository.KEY_TRACKER_DAYS, null)
        val completions = prefs.getString(DocumentRepository.KEY_TRACKER_COMPLETIONS, null)
        val doc = repository.createDocument("Session fixture ${UUID.randomUUID()}", "One sentence. Another sentence.", "TXT")
        val now = System.currentTimeMillis()
        try {
            repository.saveReadingSessions(emptyList())
            repository.saveTrackerDays(listOf(ReaderTrackerDay(trackerDateKey(now), readingMillis = 60_000, readDocumentIds = setOf(doc.id))))
            repository.addManualReadingSession(doc, ReadingSessionMode.READING, now - 600_000, 120_000)
            repository.addManualReadingSession(doc, ReadingSessionMode.LISTENING, now - 300_000, 180_000)
            val original = repository.loadReadingSessions().first { it.mode == ReadingSessionMode.READING }
            repository.correctReadingSession(original.id, 240_000)
            val snapshot = repository.loadReaderTrackerSnapshot(now)
            assertEquals(240_000L, snapshot.measuredReadingMillisByDate[trackerDateKey(now)])
            assertEquals(180_000L, snapshot.listeningMillisByDate[trackerDateKey(now)])
            assertEquals(60_000L, snapshot.legacyReadingMillisByDate[trackerDateKey(now)])
            assertEquals(480_000L, snapshot.readingMillisByDate[trackerDateKey(now)])
            val json = repository.buildBackupJson()
            repository.restoreBackupJson(json, false)
            repository.restoreBackupJson(json, false)
            assertEquals(2, repository.loadReadingSessions().size)
            assertEquals(120_000L, repository.loadReadingSessions().single { it.id == original.id }.measuredMillis)
            repository.recordDocumentProgress(doc.copy(currentIndex = doc.chunkCount - 1), now)
            assertFalse(repository.loadTrackerCompletions().any { it.documentId == doc.id })
            repository.recordDocumentCompletion(doc.id, doc.title, now)
            assertTrue(repository.loadReaderTrackerSnapshot(now).completionDates.containsKey(doc.id))
        } finally {
            repository.deleteDocument(doc.id)
            prefs.edit().apply {
                if (saved == null) remove(READING_SESSIONS_KEY) else putString(READING_SESSIONS_KEY, saved)
                if (trackerDays == null) remove(DocumentRepository.KEY_TRACKER_DAYS) else putString(DocumentRepository.KEY_TRACKER_DAYS, trackerDays)
                if (completions == null) remove(DocumentRepository.KEY_TRACKER_COMPLETIONS) else putString(DocumentRepository.KEY_TRACKER_COMPLETIONS, completions)
            }.commit()
        }
    }

    @Test fun cleanupProtectsNotesTrashRevisionsRecoveryAndSharedSentenceRecordings() {
        val repository = DocumentRepository(context)
        val notes = repository.loadGeneralNotes(); val trash = repository.loadTrashedGeneralNotes()
        val revisions = repository.loadAllNoteRevisions(); val annotations = repository.loadAllAnnotations()
        val recovery = repository.prefs.getString("general_notes__bak", null)
        val doc = repository.createDocument("Cleanup fixture ${UUID.randomUUID()}", "One sentence. Another sentence.", "TXT")
        val files = List(7) { i -> File(context.filesDir, "notes_media/cleanup-${UUID.randomUUID()}-$i.m4a").apply {
            parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2)); setLastModified(System.currentTimeMillis() - 172_800_000)
        }.canonicalFile }
        try {
            repository.replaceStoredGeneralNotes(notes + trash + listOf(
                GeneralNote("cleanup-live", "Live", "", 1, audioUrl = files[0].absolutePath),
                GeneralNote("cleanup-trash", "Trash", "", 1, audioUrl = files[1].absolutePath, deletedAt = 2)))
            repository.saveNoteRevisions(revisions + NoteRevision("cleanup-revision", 1, GeneralNote("cleanup-revision", "Revision", "", 1, audioUrl = files[2].absolutePath)))
            repository.prefs.edit().putString("general_notes__bak", org.json.JSONArray().put(GeneralNote("cleanup-recovery", "Recovery", "", 1, audioUrl = files[3].absolutePath).toJson()).toString()).commit()
            repository.upsertAnnotation(doc.id, 0, AnnotationType.NOTE, audioPath = files[4].absolutePath)
            files[5].setLastModified(System.currentTimeMillis())
            val preview = repository.findUnusedNoteMedia().filter { it.file in files }
            assertEquals(listOf(files[6]), preview.map { it.file })
            // A new reference appearing after preview must veto deletion at commit.
            repository.upsertAnnotation(doc.id, 1, AnnotationType.NOTE, audioPath = files[6].absolutePath)
            assertEquals(0L, repository.removeUnusedNoteMedia(preview))
            assertTrue(files.all { it.isFile })
            repository.removeAnnotation(doc.id, 1, AnnotationType.NOTE)
            assertEquals(2L, repository.removeUnusedNoteMedia(preview))
            assertFalse(files[6].exists())
            assertTrue(files.take(6).all { it.isFile })
        } finally {
            repository.replaceStoredGeneralNotes(notes + trash); repository.saveNoteRevisions(revisions); repository.saveAllAnnotations(annotations)
            repository.prefs.edit().apply { if (recovery == null) remove("general_notes__bak") else putString("general_notes__bak", recovery) }.commit()
            repository.deleteDocument(doc.id)
            files.forEach { it.delete() }
        }
    }

    @Test fun sentenceRecordingAndGenericNoteFileRoundTripInFullBackup() {
        val repository = DocumentRepository(context)
        val notes = repository.loadGeneralNotes(); val trash = repository.loadTrashedGeneralNotes(); val annotations = repository.loadAllAnnotations()
        val doc = repository.createDocument("Portable audio fixture ${UUID.randomUUID()}", "A sentence with a note.", "TXT")
        val audio = File(context.filesDir, "voice_notes/portable-${UUID.randomUUID()}.m4a").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(4, 3, 2, 1)) }
        val attachment = File(context.filesDir, "notes_media/portable-${UUID.randomUUID()}.pdf").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(8, 7, 6)) }
        val restored = mutableListOf<String>()
        try {
            repository.upsertAnnotation(doc.id, 0, AnnotationType.NOTE, note = "Keep text", audioPath = audio.absolutePath, audioDurationSeconds = 1)
            repository.replaceStoredGeneralNotes(notes + trash + GeneralNote("portable-file", "File note", "[file](${attachment.absolutePath})", 1))
            val bytes = ByteArrayOutputStream().also(repository::writeFullBackupZip).toByteArray()
            repository.removeAnnotation(doc.id, 0, AnnotationType.NOTE)
            repository.replaceStoredGeneralNotes(notes + trash)
            audio.delete(); attachment.delete()
            repository.restoreFullBackupZip(bytes.inputStream(), false)
            val note = repository.loadAllAnnotations().single { it.documentId == doc.id }
            restored.add(note.audioPath!!)
            assertEquals("Keep text", note.note)
            assertArrayEquals(byteArrayOf(4, 3, 2, 1), File(note.audioPath!!).readBytes())
            val fileNote = repository.loadGeneralNotes().single { it.id == "portable-file" }
            val filePath = com.veritas.reader.ui.screens.NoteSharing.blocks(fileNote).mapNotNull(com.veritas.reader.ui.screens.NoteSharing::path).single()
            restored.add(filePath)
            assertArrayEquals(byteArrayOf(8, 7, 6), File(filePath).readBytes())
        } finally {
            repository.replaceStoredGeneralNotes(notes + trash); repository.saveAllAnnotations(annotations)
            repository.deleteDocument(doc.id); audio.delete(); attachment.delete(); restored.forEach { File(it).delete() }
        }
    }
    @Test fun trackerCountsActualAudioEventsAndExplicitReadingWithoutBufferingOrOverlap() {
        val repository = DocumentRepository(context)
        val prefs = repository.prefs
        val ledger = prefs.getString(READING_SESSIONS_KEY, null)
        val oldExplicit = prefs.getBoolean("reading_explicit_only", false)
        val doc = repository.createDocument("Clock handoff ${UUID.randomUUID()}", "First sentence. Second sentence.", "TXT")
        val tracker = ReadingSessionTracker.get(context)
        fun awaitRecorded(predicate: (List<ReadingSession>) -> Boolean): List<ReadingSession> {
            repeat(120) {
                val sessions = repository.loadReadingSessions().filter { it.documentId == doc.id }
                if (predicate(sessions)) return sessions
                Thread.sleep(50)
            }
            error("Session checkpoint did not arrive")
        }
        try {
            prefs.edit().putBoolean("reading_explicit_only", true).commit()
            tracker.reader("fixture", doc.id)
            tracker.playbackRequested(true)
            Thread.sleep(300)
            tracker.flush()
            assertTrue(repository.loadReadingSessions().none { it.documentId == doc.id && it.activeMillis > 0 })
            tracker.audioStarted(doc.id, 0, "fixture-audio")
            awaitRecorded { it.any { session -> session.mode == ReadingSessionMode.LISTENING } }
            Thread.sleep(300)
            tracker.audioEnded("fixture-audio")
            val beforeBuffer = awaitRecorded { it.sumOf(ReadingSession::activeMillis) >= 250 }.sumOf(ReadingSession::activeMillis)
            Thread.sleep(350)
            val beforeFlush = tracker.changes.value
            tracker.flush()
            repeat(120) { if (tracker.changes.value <= beforeFlush) Thread.sleep(50) }
            assertEquals(beforeBuffer, repository.loadReadingSessions().filter { it.documentId == doc.id }.sumOf(ReadingSession::activeMillis))
            tracker.playbackRequested(false)
            tracker.explicitTimer(doc.id)
            awaitRecorded { it.any { session -> session.mode == ReadingSessionMode.READING } }
            Thread.sleep(300)
            tracker.reader("fixture", null)
            val sessions = awaitRecorded { it.any { session -> session.mode == ReadingSessionMode.READING && session.closedReason != null } }
            assertTrue(sessions.single { it.mode == ReadingSessionMode.READING }.activeMillis >= 250)
            assertEquals(beforeBuffer, sessions.filter { it.mode == ReadingSessionMode.LISTENING }.sumOf(ReadingSession::activeMillis))
            assertTrue(sessions.all { it.closedReason != null })
        } finally {
            tracker.playbackRequested(false); tracker.reader("fixture", null); tracker.explicitTimer(null); tracker.flush()
            Thread.sleep(500)
            repository.deleteDocument(doc.id)
            prefs.edit().putBoolean("reading_explicit_only", oldExplicit).apply {
                if (ledger == null) remove(READING_SESSIONS_KEY) else putString(READING_SESSIONS_KEY, ledger)
            }.commit()
        }
    }

}
