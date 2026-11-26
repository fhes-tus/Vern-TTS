package com.veritas.reader

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID

/** Uses separate files, preferences, and SQLite data; never clears the user's library. */
@RunWith(AndroidJUnit4::class)
class DocumentReliabilityInstrumentedTest {
    private class IsolatedContext(base: Context) : ContextWrapper(base) {
        val prefix = "reliability_${UUID.randomUUID()}_"
        val directory = File(base.cacheDir, prefix).apply { mkdirs() }
        var rejectPreferenceCommit = false
        var reportFailureAfterNextPreferenceWrite = false
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = directory
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val actual = baseContext.getSharedPreferences(prefix + name, mode)
            return object : SharedPreferences by actual {
                override fun edit(): SharedPreferences.Editor {
                    val editor = actual.edit()
                    return object : SharedPreferences.Editor by editor {
                        override fun commit(): Boolean {
                            if (reportFailureAfterNextPreferenceWrite) {
                                reportFailureAfterNextPreferenceWrite = false
                                editor.apply()
                                return false
                            }
                            return !rejectPreferenceCommit && editor.commit()
                        }
                    }
                }
            }
        }
        override fun getDatabasePath(name: String) = baseContext.getDatabasePath(prefix + name)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?) =
            baseContext.openOrCreateDatabase(prefix + name, mode, factory)
        override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, handler: DatabaseErrorHandler?) =
            baseContext.openOrCreateDatabase(prefix + name, mode, factory, handler)
    }

    private lateinit var context: IsolatedContext
    private lateinit var database: VeritasDatabaseHelper
    private lateinit var repository: DocumentRepository

    @Before fun setUp() {
        context = IsolatedContext(InstrumentationRegistry.getInstrumentation().targetContext)
        database = VeritasDatabaseHelper(context)
        repository = DocumentRepository(context, database)
    }

    @After fun tearDown() {
        database.close()
        context.baseContext.deleteDatabase(context.prefix + VeritasDatabaseHelper.DATABASE_NAME)
        context.baseContext.deleteSharedPreferences(context.prefix + "veritas_reader_library")
        context.directory.deleteRecursively()
    }

    @Test fun invalidReplacementPreservesCurrentFilesAndMetadata() {
        val doc = repository.createDocument("Previous", "Previous sentence.", "TXT")
        val backup = JSONObject(repository.buildBackupJson())
        backup.getJSONArray("documents").getJSONObject(0).put("id", "../escape")
        assertThrows(IllegalArgumentException::class.java) { repository.restoreBackupJson(backup.toString(), replaceExisting = true) }
        assertEquals(doc, repository.findDocument(doc.id))
        assertEquals("Previous sentence.", repository.readText(doc))
    }

    @Test fun repositoryReopenRollsBackInterruptedFilesAndPreferences() {
        val target = File(context.filesDir, "recovery.txt").apply { writeText("Previous") }
        repository.prefs.edit().putString("recovery_setting", "Previous").commit()
        val interrupted = RestoreFileTransaction(context.filesDir)
        RestoreRecoveryJournal.prepare(interrupted, repository.prefs.all, database.writableDatabase)
        interrupted.stage(target) { it.write("Restored".toByteArray()) }
        interrupted.publish()
        repository.prefs.edit().putString("recovery_setting", "Restored").commit()
        // Reopening represents the next process; no successful DB commit marker exists.
        DocumentRepository(context, database)
        assertEquals("Previous", target.readText())
        assertEquals("Previous", repository.prefs.getString("recovery_setting", null))
    }

    @Test fun repositoryReopenKeepsCommittedRestoreAfterInterruptedCleanup() {
        val target = File(context.filesDir, "recovery.txt").apply { writeText("Previous") }
        repository.prefs.edit().putString("recovery_setting", "Previous").commit()
        val interrupted = RestoreFileTransaction(context.filesDir)
        RestoreRecoveryJournal.prepare(interrupted, repository.prefs.all, database.writableDatabase)
        interrupted.stage(target) { it.write("Restored".toByteArray()) }
        interrupted.publish()
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            repository.prefs.edit().putString("recovery_setting", "Restored").commit()
            RestoreRecoveryJournal.markCommitted(interrupted, repository.prefs, db)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        DocumentRepository(context, database)
        assertEquals("Restored", target.readText())
        assertEquals("Restored", repository.prefs.getString("recovery_setting", null))
    }

    @Test fun sqliteFailureAfterFilePublicationRollsBackTheRestore() {
        val doc = repository.createDocument("Previous", "Previous sentence.", "TXT")
        repository.prefs.edit().putString("reader_name", "Previous setting").commit()
        val backup = JSONObject(repository.buildBackupJson())
        backup.getJSONArray("documents").getJSONObject(0).put("text", "Replacement sentence.")
        backup.put("annotations", JSONArray().put(JSONObject()
            .put("documentId", doc.id).put("chunkIndex", 0).put("type", "NOTE")
            .put("note", "reject restore").put("createdAt", 1L).put("updatedAt", 1L)))
        database.writableDatabase.execSQL("CREATE TRIGGER fail_restore BEFORE INSERT ON annotations WHEN NEW.note = 'reject restore' BEGIN SELECT RAISE(ABORT, 'injected storage failure'); END")
        assertThrows(SQLiteConstraintException::class.java) { repository.restoreBackupJson(backup.toString(), replaceExisting = true) }
        assertEquals(doc, repository.findDocument(doc.id))
        assertEquals("Previous sentence.", repository.readText(doc))
        assertEquals("Previous setting", repository.prefs.getString("reader_name", null))
        assertEquals(listOf(doc.fileName), repository.docsDir.listFiles()!!.map { it.name })
    }

    @Test fun stalePlaybackProgressCannotShrinkAnImportedDocument() {
        val doc = repository.createDocument("Import", "First sentence.", "PDF", partial = true)
        val finished = repository.appendDocumentText(doc.id, "Second sentence. Third sentence.", isComplete = true)!!
        repository.updateProgress(doc.id, 1, doc.chunkCount)
        val reloaded = repository.findDocument(doc.id)!!
        assertEquals(finished.chunkCount, reloaded.chunkCount)
        assertEquals(1, reloaded.currentIndex)
        assertFalse(reloaded.partial)
    }

    @Test fun appendAndTextRepairPreservePartialStateAndAccurateCounts() {
        val doc = repository.createDocument("Import", "First sentence.", "PDF", partial = true)
        val appended = repository.appendDocumentText(doc.id, "Second sentence.")!!
        val text = repository.readText(appended)
        assertEquals(text.length, appended.charCount)
        assertEquals(TextChunker.chunk(text).size, appended.chunkCount)
        val repaired = repository.updateDocumentText(doc.id, "Repaired first sentence. Second sentence.")!!
        assertTrue(repaired.partial)
    }

    @Test fun successfulReplacementPublishesNewTextAndKeepsOriginalReferences() {
        val doc = repository.createDocument("Previous", "Previous sentence.", "TXT")
        val backup = JSONObject(repository.buildBackupJson())
        backup.getJSONArray("documents").getJSONObject(0).put("text", "Restored sentence.")
        val result = repository.restoreBackupJson(backup.toString(), replaceExisting = true)
        val restored = repository.findDocument(doc.id)!!
        assertEquals(1, result.documentCount)
        assertEquals("Restored sentence.", repository.readText(restored))
        assertFalse(File(repository.docsDir, doc.fileName).exists())
        assertTrue(File(repository.docsDir, restored.fileName).exists())
    }

    @Test fun mergePreservesLocalEditsAndSkipsAnnotationsFromTheOtherRevision() {
        val doc = repository.createDocument("Reading", "Previous first sentence. Previous second sentence.", "TXT")
        repository.upsertAnnotation(doc.id, 0, AnnotationType.BOOKMARK)
        val backup = repository.buildBackupJson()
        repository.removeAnnotation(doc.id, 0, AnnotationType.BOOKMARK)
        val localText = "Edited first sentence. Edited second sentence. Edited third sentence."
        repository.updateDocumentText(doc.id, localText)
        repository.saveProgress(doc.id, 1)
        repository.upsertAnnotation(doc.id, 1, AnnotationType.NOTE, "Local note")
        val local = repository.findDocument(doc.id)!!

        repeat(2) {
            val result = repository.restoreBackupJson(backup)
            assertEquals(1, result.documentConflictCount)
            assertEquals(0, result.documentCount)
            assertEquals(local, repository.findDocument(doc.id))
            assertEquals(localText, repository.readText(local))
            assertEquals(listOf("Local note"), repository.loadAnnotations(doc.id).map { it.note })
            assertEquals(listOf(local.fileName), repository.docsDir.listFiles()!!.map { it.name })
        }
    }

    @Test fun mergeIdenticalTextRestoresMissingAnnotationsWithoutResettingProgress() {
        val doc = repository.createDocument("Reading", "First sentence. Second sentence. Third sentence.", "TXT")
        repository.upsertAnnotation(doc.id, 0, AnnotationType.BOOKMARK)
        repository.saveDocumentNote(doc.id, "Previous note")
        val backup = repository.buildBackupJson()
        repository.removeAnnotation(doc.id, 0, AnnotationType.BOOKMARK)
        repository.saveDocumentNote(doc.id, "Local note")
        repository.saveProgress(doc.id, 1)
        repository.renameDocument(doc.id, "New title")
        val local = repository.findDocument(doc.id)!!

        val result = repository.restoreBackupJson(backup)
        assertEquals(0, result.documentConflictCount)
        assertEquals(1, result.annotationCount)
        assertEquals(local, repository.findDocument(doc.id))
        assertEquals("Local note", repository.loadDocumentNotes()[doc.id])
        assertEquals(AnnotationType.BOOKMARK, repository.loadAnnotations(doc.id).single().type)
        assertEquals(listOf(local.fileName), repository.docsDir.listFiles()!!.map { it.name })
    }

    @Test fun originalCopyExceedingTheTextLimitKeepsReadingAndRemovesStagingFile() {
        val source = File(context.filesDir, "oversized-source.txt")
        RandomAccessFile(source, "rw").use { it.setLength(ImportFilePolicy.byteLimit(ImportFormat.TEXT) + 1L) }
        val result = repository.createDocumentWithResult(
            title = "Extracted reading", text = "The extracted text stays readable.", sourceLabel = "TXT",
            originalUri = Uri.fromFile(source), originalDisplayName = source.name, originalMimeType = "text/plain"
        )
        assertNotNull(result.originalCopyError)
        assertTrue(result.document.originalFileName.isBlank())
        assertEquals("The extracted text stays readable.", repository.readText(repository.findDocument(result.document.id)!!))
        assertTrue(repository.originalsDir.listFiles().orEmpty().isEmpty())
    }

    @Test fun originalCopyPublishesCompleteFileAndRemovesStagingFile() {
        val source = File(context.filesDir, "source.txt").apply { writeText("The original file.") }
        val result = repository.createDocumentWithResult(
            title = "Extracted reading", text = "Extracted text.", sourceLabel = "TXT",
            originalUri = Uri.fromFile(source), originalDisplayName = source.name, originalMimeType = "text/plain"
        )
        assertNull(result.originalCopyError)
        assertEquals(source.readText(), repository.originalFile(result.document)?.readText())
        assertEquals(listOf(result.document.originalFileName), repository.originalsDir.listFiles().orEmpty().map { it.name })
    }

    @Test fun explicitEditorSaveRemapsAnchorsAndRejectsAnOverlappingStaleDraft() {
        val original = "First sentence. Second sentence."
        val edited = "A new introduction. $original"
        val doc = repository.createDocument("Reading", original, "TXT")
        repository.saveDocuments(listOf(doc))
        repository.upsertAnnotation(doc.id, 1, AnnotationType.NOTE, "Keep this note")

        val saved = repository.saveEditedDocumentText(doc.id, original, edited)!!
        assertNotEquals(doc.fileName, saved.fileName)
        assertEquals(edited, repository.readText(doc))
        val fallback = JSONArray(repository.prefs.getString("${DocumentRepository.KEY_DOCUMENTS}__bak", "[]"))
        assertEquals(saved.fileName, fallback.getJSONObject(0).getString("fileName"))
        assertEquals(2, repository.loadAnnotations(doc.id).single().chunkIndex)
        assertEquals("Keep this note", repository.loadAnnotations(doc.id).single().note)
        assertNull(repository.saveEditedDocumentText(doc.id, original, "Stale draft."))
        assertEquals(edited, repository.readText(repository.findDocument(doc.id)!!))
        assertEquals(listOf(saved.fileName), repository.docsDir.listFiles().orEmpty().map { it.name })
    }

    @Test fun explicitEditorSaveKeepsThePreviousBodyAndAnchorsAfterSqliteFailure() {
        val original = "First sentence. Second sentence."
        val doc = repository.createDocument("Reading", original, "TXT")
        repository.upsertAnnotation(doc.id, 1, AnnotationType.NOTE, "Keep this note")
        database.writableDatabase.execSQL("CREATE TRIGGER fail_editor BEFORE INSERT ON annotations WHEN NEW.chunk_index = 2 BEGIN SELECT RAISE(ABORT, 'injected editor failure'); END")

        assertThrows(SQLiteConstraintException::class.java) {
            repository.saveEditedDocumentText(doc.id, original, "A new introduction. $original")
        }
        assertEquals(doc, repository.findDocument(doc.id))
        assertEquals(original, repository.readText(doc))
        assertEquals(1, repository.loadAnnotations(doc.id).single().chunkIndex)
        assertEquals(listOf(doc.fileName), repository.docsDir.listFiles().orEmpty().map { it.name })
    }

    @Test fun explicitEditorSaveRetainsOldBodyWhenCompatibilityCommitFails() {
        val original = "Previous reading."
        val doc = repository.createDocument("Reading", original, "TXT")
        repository.saveDocuments(listOf(doc))
        context.rejectPreferenceCommit = true

        val saved = repository.saveEditedDocumentText(doc.id, original, "Edited reading.")!!
        assertEquals(saved, repository.findDocument(doc.id))
        assertEquals("Edited reading.", repository.readText(saved))
        assertEquals(original, repository.readText(doc))
        val fallback = JSONArray(repository.prefs.getString(DocumentRepository.KEY_DOCUMENTS, "[]"))
        assertEquals(doc.fileName, fallback.getJSONObject(0).getString("fileName"))
        assertEquals(setOf(doc.fileName, saved.fileName), repository.docsDir.listFiles().orEmpty().map { it.name }.toSet())
    }

    @Test fun failedNoteCommitRestoresPreviousLiveValuesBeforeRetry() {
        val original = GeneralNote("note", "Saved title", "Saved body", 1)
        val updated = original.copy(content = "Draft body", updatedAt = 2)
        repository.saveGeneralNotes(listOf(original))
        val previousBackup = repository.prefs.getString("general_notes__bak", null)
        context.reportFailureAfterNextPreferenceWrite = true

        assertThrows(IllegalStateException::class.java) { repository.saveGeneralNotes(listOf(updated)) }
        assertEquals(listOf(original), repository.loadGeneralNotes())
        assertEquals(previousBackup, repository.prefs.getString("general_notes__bak", null))
        repository.saveGeneralNotes(listOf(updated))
        assertEquals(listOf(updated), repository.loadGeneralNotes())
    }
}
