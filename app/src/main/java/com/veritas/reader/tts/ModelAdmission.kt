package com.veritas.reader.tts

/** Counts native graphs, including loads and retirement. It does not estimate native heap bytes. */
internal class ModelAdmission(
    private val policy: SpeechResourcePolicy,
    private val clock: SpeechClock,
    private val capacity: () -> Int = { policy.maxResidentModels },
    private val mayAddSecond: () -> Boolean = { true }
) {
    class Owner(val live: Boolean, val retire: () -> Unit) {
        internal var pinned = false
        internal var closed = false
    }
    private class Entry(val owner: Owner, var users: Int = 0, var idleAt: Long = 0, var retiring: Boolean = false)
    data class Snapshot(val resident: Int, val busy: Int, val pinned: Int, val retiring: Int)
    private val entries = LinkedHashMap<Owner, Entry>()
    private var waitingLive = 0

    @Synchronized fun waiting(owner: Owner, added: Boolean) { if (owner.live) waitingLive += if (added) 1 else -1 }
    @Synchronized fun pin(owner: Owner, active: Boolean) {
        if (owner.pinned && !active) entries[owner]?.takeIf { it.users == 0 }?.idleAt = clock.nowMs()
        owner.pinned = active && !owner.closed
    }
    @Synchronized fun contains(owner: Owner) = entries.containsKey(owner)

    /** Null means wait. Selected retirements remain charged until their callback completes. */
    @Synchronized fun tryAcquire(owner: Owner): Boolean {
        check(!owner.closed) { "Speech model owner is closed" }
        if (!owner.live && waitingLive > 0) return false
        entries[owner]?.let { if (it.retiring) return false; it.users++; return true }
        val idle = entries.values.filter { it.users == 0 && !it.owner.pinned && !it.retiring }
        if (entries.size >= capacity() || (entries.isNotEmpty() && !mayAddSecond())) {
            idle.minByOrNull { it.idleAt }?.let(::retire)
            return false
        }
        entries[owner] = Entry(owner, users = 1)
        return true
    }

    @Synchronized fun release(owner: Owner) {
        val entry = entries[owner] ?: return
        check(entry.users > 0)
        if (--entry.users == 0) {
            entry.idleAt = clock.nowMs()
            if (owner.closed) retire(entry) else trim(false)
        }
    }
    @Synchronized fun close(owner: Owner) {
        owner.closed = true; owner.pinned = false
        entries[owner]?.takeIf { it.users == 0 }?.let(::retire)
    }
    @Synchronized fun unload(owner: Owner) { entries[owner]?.takeIf { it.users == 0 }?.let(::retire) }
    @Synchronized fun trim(pressure: Boolean) {
        val idle = entries.values.filter { it.users == 0 && !it.owner.pinned && !it.retiring }.sortedBy { it.idleAt }
        idle.forEachIndexed { index, entry ->
            if (pressure || clock.expired(entry.idleAt, policy.modelIdleMs) || index < idle.size - policy.maxIdleModels) retire(entry)
        }
    }
    private fun retire(entry: Entry) {
        if (entry.retiring) return
        entry.retiring = true
        entry.owner.retire() // Schedules work only; must never block or free under this monitor.
    }
    @Synchronized fun retired(owner: Owner) {
        val entry = entries[owner] ?: return
        check(entry.retiring && entry.users == 0)
        entries.remove(owner)
    }
    @Synchronized fun snapshot() = Snapshot(entries.size, entries.values.count { it.users > 0 },
        entries.values.count { it.owner.pinned }, entries.values.count { it.retiring })
}
