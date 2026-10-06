package com.veritas.reader

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteConstraintException
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
import java.util.UUID

/** Uses separate files, preferences, and SQLite data; never clears the user's library. */
@RunWith(AndroidJUnit4::class)
class DocumentReliabilityInstrumentedTest {
    private class IsolatedContext(base: Context) : ContextWrapper(base) {
        val prefix = "reliability_${UUID.randomUUID()}_"
        val directory = File(base.cacheDir, prefix).apply { mkdirs() }
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = directory
        override fun getSharedPreferences(name: String, mode: Int) = baseContext.getSharedPreferences(prefix + name, mode)
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
}
