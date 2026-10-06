package com.veritas.reader.ui.screens

/** Per-note editing history survives closing/reopening for one hour in this app session. */
internal object NoteUndoHistory {
    const val LIFETIME_MS = 60 * 60 * 1000L
    data class Entry(val current: NoteEditorSnapshot, val undo: List<NoteEditorSnapshot>, val redo: List<NoteEditorSnapshot>, val at: Long)
    private val entries = linkedMapOf<String, Entry>()
    @Synchronized fun read(id: String?, current: NoteEditorSnapshot, now: Long = System.currentTimeMillis()): Entry? {
        entries.entries.removeAll { now - it.value.at >= LIFETIME_MS }
        return entries[id]?.takeIf { it.current.content == current.content && it.current.title == current.title && it.current.checklist == current.checklist }
    }
    @Synchronized fun write(id: String?, current: NoteEditorSnapshot, undo: List<NoteEditorSnapshot>, redo: List<NoteEditorSnapshot>, now: Long = System.currentTimeMillis()) {
        if (id == null) return
        entries.entries.removeAll { now - it.value.at >= LIFETIME_MS }
        entries[id] = Entry(current, undo.filter { now - it.at < LIFETIME_MS }, redo.filter { now - it.at < LIFETIME_MS }, now)
        while (entries.size > 20) entries.remove(entries.keys.first())
    }
}
