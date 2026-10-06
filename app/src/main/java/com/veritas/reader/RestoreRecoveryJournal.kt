package com.veritas.reader

import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** SQLite's commit marker decides whether files/preferences roll back after process death. */
internal object RestoreRecoveryJournal {
    private fun ensureTable(database: SQLiteDatabase) {
        database.execSQL("CREATE TABLE IF NOT EXISTS restore_commits (id TEXT PRIMARY KEY)")
    }
    fun prepare(files: RestoreFileTransaction, preferences: Map<String, *>, database: SQLiteDatabase) {
        ensureTable(database)
        val snapshot = JSONObject()
        preferences.forEach { (key, value) ->
            val type = when (value) {
                is String -> "string"
                is Boolean -> "boolean"
                is Int -> "int"
                is Long -> "long"
                is Float -> "float"
                is Set<*> -> "set"
                else -> return@forEach
            }
            snapshot.put(key, JSONObject().put("type", type).put("value", if (value is Set<*>) JSONArray(value.toList()) else value))
        }
        RestoreFileTransaction.writeDurable(File(files.staging, "preferences.json"), snapshot.toString())
    }
    fun markCommitted(files: RestoreFileTransaction, prefs: SharedPreferences, database: SQLiteDatabase) {
        // commit waits for previous apply writes too. Preferences are durable before
        // the same SQLite transaction commits all restored metadata and this marker.
        check(prefs.edit().putString("last_restore_commit", files.id).commit()) { "Could not publish restored preferences." }
        database.execSQL("INSERT INTO restore_commits(id) VALUES(?)", arrayOf(files.id))
    }
    fun recover(root: File, prefs: SharedPreferences, database: SQLiteDatabase) {
        if (File(root, "restore_transactions").listFiles().isNullOrEmpty()) return
        ensureTable(database)
        RestoreFileTransaction.recoverPending(root, { id ->
            database.rawQuery("SELECT 1 FROM restore_commits WHERE id = ?", arrayOf(id)).use { it.moveToFirst() }
        }, { folder ->
            val snapshot = JSONObject(File(folder, "preferences.json").readText())
            val editor = prefs.edit().clear()
            snapshot.keys().forEach { key ->
                val item = snapshot.getJSONObject(key)
                when (item.getString("type")) {
                    "string" -> editor.putString(key, item.getString("value"))
                    "boolean" -> editor.putBoolean(key, item.getBoolean("value"))
                    "int" -> editor.putInt(key, item.getInt("value"))
                    "long" -> editor.putLong(key, item.getLong("value"))
                    "float" -> editor.putFloat(key, item.getDouble("value").toFloat())
                    "set" -> editor.putStringSet(key, item.getJSONArray("value").let { array -> (0 until array.length()).map { array.getString(it) }.toSet() })
                    else -> error("Invalid recovery preference type.")
                }
            }
            check(editor.commit()) { "Could not recover preferences after interrupted restore." }
        })
        database.delete("restore_commits", null, null)
    }
}
