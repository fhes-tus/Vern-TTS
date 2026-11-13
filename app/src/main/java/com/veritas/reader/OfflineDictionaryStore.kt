package com.veritas.reader

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.system.Os
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile

/** A replaceable, separate, read-only dictionary. Books, vocabulary and voice files are untouched. */
internal object OfflineDictionaryStore {
    private val lock = Any()
    private fun root(context: Context) = File(context.noBackupFilesDir, "offline-dictionary").apply { mkdirs() }
    private fun file(context: Context) = File(root(context), "${EnglishDictionaryPack.ID}.sqlite")
    fun isInstalled(context: Context): Boolean = file(context).isFile

    fun info(context: Context): DictionaryPackInfo? = synchronized(lock) {
        val source = file(context)
        if (!source.isFile) return@synchronized null
        runCatching { open(source).use { db ->
            require(db.version == 1)
            db.rawQuery("SELECT value FROM metadata WHERE key='sha256'", null).use { c ->
                require(c.moveToFirst() && c.getString(0) == EnglishDictionaryPack.SHA256)
            }
            val words = db.rawQuery("SELECT value FROM metadata WHERE key='words'", null).use { c ->
                require(c.moveToFirst()); c.getString(0).toInt()
            }
            DictionaryPackInfo(words, source.length())
        } }.getOrNull()
    }

    fun lookup(context: Context, input: String): DictionaryEntry? = synchronized(lock) {
        val word = WordNetDictionary.query(input) ?: return@synchronized null
        val source = file(context)
        if (!source.isFile) return@synchronized null
        open(source).use { db ->
            fun find(lemma: String): DictionaryEntry? {
                val senses = mutableListOf<DictionarySense>()
                db.rawQuery("""SELECT s.pos,s.gloss,s.words FROM entries e JOIN synsets s
                    ON e.pos=s.pos AND e.offset=s.offset WHERE e.lemma=?
                    ORDER BY CASE e.pos WHEN 'n' THEN 0 WHEN 'v' THEN 1 WHEN 'a' THEN 2 ELSE 3 END,e.rank LIMIT 16""",
                    arrayOf(lemma)).use { c -> while (c.moveToNext()) {
                    senses.add(WordNetDictionary.sense(c.getString(0), c.getString(1), c.getString(2).split('\n'), lemma))
                } }
                return senses.takeIf { it.isNotEmpty() }?.let { DictionaryEntry(word, lemma, it) }
            }
            // Prefer the literal entry (.22 and 'hood are real headwords), then ignore
            // surrounding selection punctuation before trying recorded/regular word forms.
            for (form in listOf(word, word.trim { !it.isLetterOrDigit() }).filter(String::isNotBlank).distinct()) {
                find(form)?.let { return@synchronized it }
                val exceptions = mutableListOf<String>()
                db.rawQuery("SELECT lemma FROM exceptions WHERE form=? ORDER BY rowid LIMIT 16", arrayOf(form))
                    .use { c -> while (c.moveToNext()) exceptions.add(c.getString(0)) }
                for (candidate in (exceptions + WordNetDictionary.candidateForms(form)).distinct()) {
                    find(candidate)?.let { return@synchronized it }
                }
            }
            null
        }
    }

    fun suggestions(context: Context, input: String): List<String> = synchronized(lock) {
        val word = WordNetDictionary.query(input) ?: return@synchronized emptyList()
        val source = file(context)
        if (!source.isFile) return@synchronized emptyList()
        val escaped = word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        open(source).use { db -> db.rawQuery("SELECT DISTINCT lemma FROM entries WHERE lemma LIKE ? ESCAPE '\\' ORDER BY lemma LIMIT 8",
            arrayOf("$escaped%")).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } } }
    }

    /** Indexed keyset pages keep the full word list out of memory and never save vocabulary. */
    fun browse(context: Context, prefix: String = "", after: String? = null, pageSize: Int = 40): DictionaryWordPage = synchronized(lock) {
        require(pageSize in 1..100)
        val normalized = if (prefix.isBlank()) "" else WordNetDictionary.query(prefix)
            ?: return@synchronized DictionaryWordPage(emptyList(), false)
        val cursor = after?.let { WordNetDictionary.query(it) ?: return@synchronized DictionaryWordPage(emptyList(), false) }
        val source = file(context)
        if (!source.isFile) return@synchronized DictionaryWordPage(emptyList(), false)
        val escaped = normalized.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val args = mutableListOf("$escaped%")
        val cursorClause = if (cursor == null) "" else " AND lemma > ?".also { args.add(cursor) }
        args.add((pageSize + 1).toString())
        val words = open(source).use { db ->
            db.rawQuery("SELECT DISTINCT lemma FROM entries WHERE lemma LIKE ? ESCAPE '\\'$cursorClause ORDER BY lemma LIMIT ?",
                args.toTypedArray()).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
        }
        DictionaryWordPage(words.take(pageSize), words.size > pageSize)
    }

    fun remove(context: Context) = synchronized(lock) {
        val source = file(context)
        require(!source.exists() || source.delete()) { "Could not remove the dictionary. Try again." }
        clearInterrupted(context)
    }

    fun clearInterrupted(context: Context) = synchronized(lock) {
        root(context).listFiles().orEmpty().filter { it.isDirectory && it.name.matches(Regex("staging-[0-9a-f-]{36}")) }
            .forEach { it.deleteRecursively() }
        File(context.cacheDir, "offline-dictionary").listFiles().orEmpty()
            .filter { it.isFile && it.name.matches(Regex("download-[0-9a-f-]{36}\\.zip")) }.forEach { it.delete() }
    }

    private fun open(source: File): SQLiteDatabase = SQLiteDatabase.openDatabase(source.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

    fun install(context: Context, archive: File, checkActive: () -> Unit = {}, progress: (Int) -> Unit = {}): DictionaryPackInfo = synchronized(lock) {
        val digest = MessageDigest.getInstance("SHA-256")
        require(archive.length() == EnglishDictionaryPack.DOWNLOAD_BYTES) { "Dictionary download is incomplete." }
        archive.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { checkActive(); val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        require(digest.digest().joinToString("") { "%02x".format(it) } == EnglishDictionaryPack.SHA256) { "Dictionary download could not be verified." }
        require(root(context).usableSpace >= 160L * 1024 * 1024) { "Free some storage before installing the dictionary." }
        // A killed process cannot run finally; remove only this installer's own old stages.
        root(context).listFiles().orEmpty().filter { it.isDirectory && it.name.matches(Regex("staging-[0-9a-f-]{36}")) }
            .forEach { it.deleteRecursively() }
        val staging = File(root(context), "staging-${UUID.randomUUID()}").apply { require(mkdirs()) }
        val built = File(staging, "dictionary.sqlite")
        try {
            SQLiteDatabase.openOrCreateDatabase(built, null).use { db ->
                db.execSQL("CREATE TABLE synsets(pos TEXT NOT NULL,offset INTEGER NOT NULL,gloss TEXT NOT NULL,words TEXT NOT NULL,PRIMARY KEY(pos,offset))")
                db.execSQL("CREATE TABLE entries(lemma TEXT COLLATE NOCASE NOT NULL,pos TEXT NOT NULL,rank INTEGER NOT NULL,offset INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE exceptions(form TEXT NOT NULL,lemma TEXT NOT NULL)")
                db.execSQL("CREATE TABLE metadata(key TEXT PRIMARY KEY,value TEXT NOT NULL)")
                db.beginTransaction()
                try {
                    ZipFile(archive).use { zip ->
                        require(zip.size() <= 64)
                        val names = mutableSetOf<String>()
                        val all = zip.entries()
                        while (all.hasMoreElements()) {
                            val e = all.nextElement()
                            require(names.add(e.name) && !e.name.contains('\\') && e.name.split('/').none { it == ".." } && !e.name.startsWith('/'))
                        }
                        val notices = linkedSetOf<String>()
                        var rowCount = 0
                        var totalBytes = 0L
                        fun lines(name: String, read: (String) -> Unit) {
                            val entry = requireNotNull(zip.getEntry("oewn2025/$name")) { "Dictionary package is missing a required file." }
                            require(entry.size in 1..24L * 1024 * 1024)
                            totalBytes += entry.size
                            require(totalBytes <= 64L * 1024 * 1024)
                            zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).useLines { records -> records.forEach { line ->
                                require(line.length <= WordNetDictionary.MAX_RECORD_LENGTH) { "Dictionary record is too large." }
                                if (++rowCount % 256 == 0) checkActive()
                                if (line.startsWith(' ')) { if (notices.size < 160) notices.add(line.trim()) }
                                else read(line)
                            } }
                        }
                        db.compileStatement("INSERT INTO synsets VALUES(?,?,?,?)").use { insert ->
                            WordNetDictionary.parts.forEachIndexed { partIndex, (part, pos) ->
                                lines("data.$part") { line -> WordNetDictionary.synset(line, pos)?.let { record ->
                                    insert.bindString(1, record.pos); insert.bindLong(2, record.offset)
                                    insert.bindString(3, record.gloss); insert.bindString(4, record.words.joinToString("\n")); insert.executeInsert()
                                } }
                                progress(10 + partIndex * 10)
                            }
                        }
                        db.compileStatement("INSERT INTO entries VALUES(?,?,?,?)").use { insert ->
                            WordNetDictionary.parts.forEachIndexed { partIndex, (part, pos) ->
                                lines("index.$part") { line -> WordNetDictionary.index(line, pos)?.let { record ->
                                    record.offsets.forEachIndexed { rank, offset ->
                                        insert.bindString(1, record.lemma); insert.bindString(2, pos); insert.bindLong(3, rank.toLong()); insert.bindLong(4, offset); insert.executeInsert()
                                    }
                                } }
                                progress(50 + partIndex * 10)
                            }
                        }
                        db.compileStatement("INSERT INTO exceptions VALUES(?,?)").use { insert ->
                            WordNetDictionary.parts.forEach { (part, _) -> lines("$part.exc") { line ->
                                val fields = line.trim().split(Regex("\\s+"))
                                if (fields.size >= 2) fields.drop(1).forEach { lemma ->
                                    insert.bindString(1, WordNetDictionary.query(fields[0]) ?: error("Invalid word form."))
                                    insert.bindString(2, WordNetDictionary.query(lemma) ?: error("Invalid base word.")); insert.executeInsert()
                                }
                            } }
                        }
                        checkActive()
                        db.execSQL("CREATE INDEX entry_lemma ON entries(lemma)")
                        db.execSQL("CREATE INDEX exception_form ON exceptions(form)")
                        db.rawQuery("SELECT 1 FROM entries e LEFT JOIN synsets s ON e.pos=s.pos AND e.offset=s.offset WHERE s.offset IS NULL LIMIT 1", null)
                            .use { require(!it.moveToFirst()) { "Dictionary references are incomplete." } }
                        val words = db.rawQuery("SELECT COUNT(DISTINCT lemma) FROM entries", null).use { it.moveToFirst(); it.getInt(0) }
                        require(words in 100_000..250_000)
                        db.compileStatement("INSERT INTO metadata VALUES(?,?)").use { insert ->
                            for ((key, value) in mapOf("sha256" to EnglishDictionaryPack.SHA256, "words" to words.toString(),
                                "source" to EnglishDictionaryPack.SOURCE, "credit" to EnglishDictionaryPack.CREDIT,
                                "license" to EnglishDictionaryPack.LICENSE, "notices" to notices.joinToString("\n"))) {
                                insert.bindString(1,key); insert.bindString(2,value); insert.executeInsert()
                            }
                        }
                        db.version = 1
                    }
                    checkActive(); db.setTransactionSuccessful()
                } finally { db.endTransaction() }
            }
            checkActive()
            Os.rename(built.absolutePath, file(context).absolutePath)
            info(context) ?: error("Dictionary installation could not be verified.")
        } finally { staging.deleteRecursively() }
    }
}
