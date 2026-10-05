package com.veritas.reader

import android.content.ContentValues
import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

class VeritasDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "veritas_reader.db"
        const val DATABASE_VERSION = 3

        private const val TABLE_DOCUMENTS = "documents"
        private const val TABLE_ANNOTATIONS = "annotations"
        private const val TABLE_FLASHCARDS = "flashcards"

        // The singleton is constructed only with applicationContext, so it cannot retain an Activity.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: VeritasDatabaseHelper? = null

        fun getInstance(context: Context): VeritasDatabaseHelper =
            instance ?: synchronized(this) {
                instance ?: VeritasDatabaseHelper(context.applicationContext).also { instance = it }
            }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_DOCUMENTS (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                file_name TEXT NOT NULL,
                source_label TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                current_index INTEGER NOT NULL,
                chunk_count INTEGER NOT NULL,
                char_count INTEGER NOT NULL,
                preview TEXT NOT NULL,
                favorite INTEGER NOT NULL DEFAULT 0,
                collection TEXT NOT NULL DEFAULT '',
                original_file_name TEXT NOT NULL DEFAULT '',
                original_mime_type TEXT NOT NULL DEFAULT '',
                page_count INTEGER NOT NULL DEFAULT 0,
                partial INTEGER NOT NULL DEFAULT 0,
                language TEXT NOT NULL DEFAULT '',
                catalog_id TEXT NOT NULL DEFAULT '',
                sentence_index_version INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_docs_updated ON $TABLE_DOCUMENTS (updated_at DESC)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_ANNOTATIONS (
                stable_key TEXT PRIMARY KEY,
                document_id TEXT NOT NULL,
                chunk_index INTEGER NOT NULL,
                type TEXT NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                highlight_color TEXT,
                selection_group_id TEXT,
                audio_path TEXT,
                audio_duration_seconds INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ann_doc ON $TABLE_ANNOTATIONS (document_id, chunk_index)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS $TABLE_FLASHCARDS (
                id TEXT PRIMARY KEY,
                document_id TEXT NOT NULL DEFAULT '',
                front TEXT NOT NULL,
                back TEXT NOT NULL,
                set_id TEXT NOT NULL DEFAULT '',
                set_name TEXT NOT NULL DEFAULT '',
                recall TEXT NOT NULL DEFAULT '',
                next_review_due_timestamp INTEGER NOT NULL DEFAULT 0,
                interval_days INTEGER NOT NULL DEFAULT 0,
                repetition_count INTEGER NOT NULL DEFAULT 0,
                ease_factor REAL NOT NULL DEFAULT 2.5
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_flashcards_set ON $TABLE_FLASHCARDS (set_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_DOCUMENTS ADD COLUMN catalog_id TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE_DOCUMENTS ADD COLUMN sentence_index_version INTEGER NOT NULL DEFAULT 1")
        }
    }

    fun setDocumentCatalogId(id: String, catalogId: String) {
        writableDatabase.update(TABLE_DOCUMENTS, ContentValues().apply {
            put("catalog_id", catalogId)
        }, "id = ? AND catalog_id = ''", arrayOf(id))
    }

    /**
     * Self-migrating check: automatically imports legacy SharedPreferences JSON into SQLite
     * on first run, preserving 100% of user data without any manual intervention.
     */
    fun ensureMigratedFromPrefs(
        context: Context,
        loadDocsFromPrefs: () -> List<SavedDocument>,
        loadAnnotationsFromPrefs: () -> List<ReaderAnnotation>,
        loadFlashcardsFromPrefs: () -> List<FlashcardProgress>
    ) {
        val prefs = context.getSharedPreferences("veritas_reader_library", Context.MODE_PRIVATE)
        if (prefs.getBoolean("db_migrated_v1", false)) return

        synchronized(this) {
            if (prefs.getBoolean("db_migrated_v1", false)) return
            val db = writableDatabase
            db.beginTransaction()
            try {
                val docs = loadDocsFromPrefs()
                docs.forEach { doc ->
                    insertOrReplaceDocumentInternal(db, doc)
                }

                val annotations = loadAnnotationsFromPrefs()
                annotations.forEach { ann ->
                    insertOrReplaceAnnotationInternal(db, ann)
                }

                val flashcards = loadFlashcardsFromPrefs()
                flashcards.forEach { card ->
                    insertOrReplaceFlashcardInternal(db, card)
                }

                db.setTransactionSuccessful()
                prefs.edit().putBoolean("db_migrated_v1", true).apply()
            } catch (e: Exception) {
                // Retry safely next time without losing prefs data
            } finally {
                db.endTransaction()
            }
        }
    }

    // --- Document Operations ---

    fun getAllDocuments(docsDir: File, verifyFiles: Boolean = true): List<SavedDocument> {
        val list = mutableListOf<SavedDocument>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_DOCUMENTS,
            null,
            null,
            null,
            null,
            null,
            "updated_at DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val doc = parseDocument(it)
                if (doc.id.isNotBlank() && doc.fileName.isNotBlank() && (!verifyFiles || File(docsDir, doc.fileName).exists())) {
                    list.add(doc)
                }
            }
        }
        return list
    }

    fun getDocumentById(id: String, docsDir: File): SavedDocument? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_DOCUMENTS,
            null,
            "id = ?",
            arrayOf(id),
            null,
            null,
            null,
            "1"
        )
        cursor.use {
            if (it.moveToFirst()) {
                val doc = parseDocument(it)
                if (doc.fileName.isNotBlank() && File(docsDir, doc.fileName).exists()) {
                    return doc
                }
            }
        }
        return null
    }

    fun upsertDocument(doc: SavedDocument) {
        val db = writableDatabase
        insertOrReplaceDocumentInternal(db, doc)
    }

    fun updateDocumentProgress(documentId: String, index: Int, now: Long) {
        writableDatabase.execSQL(
            "UPDATE $TABLE_DOCUMENTS SET current_index = CASE WHEN chunk_count > 0 THEN MAX(0, MIN(?, chunk_count - 1)) ELSE 0 END, updated_at = ? WHERE id = ?",
            arrayOf<Any>(index, now, documentId)
        )
    }

    fun renameDocument(id: String, title: String, now: Long) {
        writableDatabase.execSQL("UPDATE $TABLE_DOCUMENTS SET title = ?, updated_at = ? WHERE id = ?", arrayOf<Any>(title, now, id))
    }

    fun toggleDocumentFavorite(id: String, now: Long) {
        writableDatabase.execSQL("UPDATE $TABLE_DOCUMENTS SET favorite = CASE WHEN favorite = 0 THEN 1 ELSE 0 END, updated_at = ? WHERE id = ?", arrayOf<Any>(now, id))
    }

    fun setDocumentCollection(id: String, collection: String, now: Long) {
        writableDatabase.execSQL("UPDATE $TABLE_DOCUMENTS SET collection = ?, updated_at = ? WHERE id = ?", arrayOf<Any>(collection, now, id))
    }

    fun upsertDocuments(docs: List<SavedDocument>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            docs.forEach { insertOrReplaceDocumentInternal(db, it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun replaceAllDocuments(docs: List<SavedDocument>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_DOCUMENTS, null, null)
            docs.forEach { insertOrReplaceDocumentInternal(db, it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun deleteDocumentById(id: String) {
        val db = writableDatabase
        db.delete(TABLE_DOCUMENTS, "id = ?", arrayOf(id))
    }

    private fun insertOrReplaceDocumentInternal(db: SQLiteDatabase, doc: SavedDocument) {
        val values = ContentValues().apply {
            put("id", doc.id)
            put("title", doc.title)
            put("file_name", doc.fileName)
            put("source_label", doc.sourceLabel)
            put("created_at", doc.createdAt)
            put("updated_at", doc.updatedAt)
            put("current_index", doc.currentIndex)
            put("chunk_count", doc.chunkCount)
            put("char_count", doc.charCount)
            put("preview", doc.preview)
            put("favorite", if (doc.favorite) 1 else 0)
            put("collection", doc.collection)
            put("original_file_name", doc.originalFileName)
            put("original_mime_type", doc.originalMimeType)
            put("page_count", doc.pageCount)
            put("partial", if (doc.partial) 1 else 0)
            put("language", doc.language)
            put("catalog_id", doc.catalogId)
            put("sentence_index_version", doc.sentenceIndexVersion)
        }
        check(db.insertWithOnConflict(TABLE_DOCUMENTS, null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "Could not save document." }
    }

    private fun parseDocument(c: Cursor): SavedDocument {
        return SavedDocument(
            id = c.getString(c.getColumnIndexOrThrow("id")),
            title = c.getString(c.getColumnIndexOrThrow("title")),
            fileName = c.getString(c.getColumnIndexOrThrow("file_name")),
            sourceLabel = c.getString(c.getColumnIndexOrThrow("source_label")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
            updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
            currentIndex = c.getInt(c.getColumnIndexOrThrow("current_index")),
            chunkCount = c.getInt(c.getColumnIndexOrThrow("chunk_count")),
            charCount = c.getInt(c.getColumnIndexOrThrow("char_count")),
            preview = c.getString(c.getColumnIndexOrThrow("preview")),
            favorite = c.getInt(c.getColumnIndexOrThrow("favorite")) == 1,
            collection = c.getString(c.getColumnIndexOrThrow("collection")),
            originalFileName = c.getString(c.getColumnIndexOrThrow("original_file_name")),
            originalMimeType = c.getString(c.getColumnIndexOrThrow("original_mime_type")),
            pageCount = c.getInt(c.getColumnIndexOrThrow("page_count")),
            partial = c.getInt(c.getColumnIndexOrThrow("partial")) == 1,
            language = c.getString(c.getColumnIndexOrThrow("language")),
            catalogId = c.getString(c.getColumnIndexOrThrow("catalog_id")),
            sentenceIndexVersion = c.getInt(c.getColumnIndexOrThrow("sentence_index_version"))
        )
    }

    // --- Annotations Operations ---

    /** The index version and its anchors must move together, even if the app is interrupted. */
    fun migrateSentenceAnchors(document: SavedDocument, annotations: List<ReaderAnnotation>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            insertOrReplaceDocumentInternal(db, document)
            db.delete(TABLE_ANNOTATIONS, "document_id = ?", arrayOf(document.id))
            annotations.forEach { insertOrReplaceAnnotationInternal(db, it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getAllAnnotations(): List<ReaderAnnotation> {
        val list = mutableListOf<ReaderAnnotation>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_ANNOTATIONS,
            null,
            null,
            null,
            null,
            null,
            "document_id ASC, chunk_index ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val ann = parseAnnotation(it)
                if (ann != null) list.add(ann)
            }
        }
        return list
    }

    fun replaceAllAnnotations(annotations: List<ReaderAnnotation>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_ANNOTATIONS, null, null)
            annotations.forEach { insertOrReplaceAnnotationInternal(db, it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun insertOrReplaceAnnotationInternal(db: SQLiteDatabase, ann: ReaderAnnotation) {
        val values = ContentValues().apply {
            put("stable_key", ann.stableKey)
            put("document_id", ann.documentId)
            put("chunk_index", ann.chunkIndex)
            put("type", ann.type.name)
            put("note", ann.note)
            put("created_at", ann.createdAt)
            put("updated_at", ann.updatedAt)
            put("highlight_color", ann.highlightColor)
            put("selection_group_id", ann.selectionGroupId)
            put("audio_path", ann.audioPath)
            put("audio_duration_seconds", ann.audioDurationSeconds)
        }
        check(db.insertWithOnConflict(TABLE_ANNOTATIONS, null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "Could not save annotation." }
    }

    private fun parseAnnotation(c: Cursor): ReaderAnnotation? {
        val typeName = c.getString(c.getColumnIndexOrThrow("type"))
        val type = runCatching { AnnotationType.valueOf(typeName) }.getOrNull() ?: return null
        return ReaderAnnotation(
            documentId = c.getString(c.getColumnIndexOrThrow("document_id")),
            chunkIndex = c.getInt(c.getColumnIndexOrThrow("chunk_index")),
            type = type,
            note = c.getString(c.getColumnIndexOrThrow("note")),
            createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
            updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
            highlightColor = c.getString(c.getColumnIndexOrThrow("highlight_color")),
            selectionGroupId = c.getString(c.getColumnIndexOrThrow("selection_group_id")),
            audioPath = c.getString(c.getColumnIndexOrThrow("audio_path")),
            audioDurationSeconds = c.getInt(c.getColumnIndexOrThrow("audio_duration_seconds"))
        )
    }

    // --- Flashcards Operations ---

    fun getAllFlashcards(): List<FlashcardProgress> {
        val list = mutableListOf<FlashcardProgress>()
        val db = readableDatabase
        val cursor = db.query(TABLE_FLASHCARDS, null, null, null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(parseFlashcard(it))
            }
        }
        return list
    }

    fun replaceAllFlashcards(cards: List<FlashcardProgress>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_FLASHCARDS, null, null)
            cards.forEach { insertOrReplaceFlashcardInternal(db, it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun insertOrReplaceFlashcardInternal(db: SQLiteDatabase, card: FlashcardProgress) {
        val values = ContentValues().apply {
            put("id", card.id)
            put("document_id", card.documentId)
            put("front", card.front)
            put("back", card.back)
            put("set_id", card.setId)
            put("set_name", card.setName)
            put("recall", card.recall)
            put("next_review_due_timestamp", card.nextReviewDueTimestamp)
            put("interval_days", card.intervalDays)
            put("repetition_count", card.repetitionCount)
            put("ease_factor", card.easeFactor)
        }
        check(db.insertWithOnConflict(TABLE_FLASHCARDS, null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1L) { "Could not save flashcard." }
    }

    private fun parseFlashcard(c: Cursor): FlashcardProgress {
        return FlashcardProgress(
            id = c.getString(c.getColumnIndexOrThrow("id")),
            documentId = c.getString(c.getColumnIndexOrThrow("document_id")),
            front = c.getString(c.getColumnIndexOrThrow("front")),
            back = c.getString(c.getColumnIndexOrThrow("back")),
            setId = c.getString(c.getColumnIndexOrThrow("set_id")),
            setName = c.getString(c.getColumnIndexOrThrow("set_name")),
            recall = c.getString(c.getColumnIndexOrThrow("recall")),
            nextReviewDueTimestamp = c.getLong(c.getColumnIndexOrThrow("next_review_due_timestamp")),
            intervalDays = c.getInt(c.getColumnIndexOrThrow("interval_days")),
            repetitionCount = c.getInt(c.getColumnIndexOrThrow("repetition_count")),
            easeFactor = c.getFloat(c.getColumnIndexOrThrow("ease_factor"))
        )
    }
}
