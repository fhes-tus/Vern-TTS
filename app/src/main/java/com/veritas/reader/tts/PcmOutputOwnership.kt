package com.veritas.reader.tts

/** Track lifetime is independent of a model buffer. No writer can lose its borrowed track. */
internal class PcmOutputOwnership<T>(
    private val create: (Int) -> T,
    private val interrupt: (T) -> Unit,
    private val dispose: (T) -> Unit
) {
    class Owner internal constructor(@Volatile internal var active: Boolean)
    class Writer<T> internal constructor(val track: T)
    data class Snapshot(val tracksCreated: Int, val releaseAttempts: Int, val sampleRate: Int?,
        val hasWriter: Boolean, val closed: Boolean)

    private var owner: Owner? = null
    private var track: T? = null
    private var sampleRate: Int? = null
    private var writer: Writer<T>? = null
    private var closed = false
    private var tracksCreated = 0
    private var releaseAttempts = 0

    @Synchronized fun newOwner(): Owner {
        owner?.active = false
        track?.let(interrupt)
        return Owner(!closed).also { owner = it }
    }

    @Synchronized fun acquire(requester: Owner, rate: Int): Writer<T>? {
        if (closed || owner !== requester || !requester.active || writer != null) return null
        require(rate > 0)
        if (track != null && sampleRate != rate) disposeTrack()
        val ready = track ?: create(rate).also { track = it; sampleRate = rate; tracksCreated++ }
        return Writer(ready).also { writer = it }
    }

    @Synchronized fun interrupt(requester: Owner, writingOnly: Boolean = false) {
        if (owner === requester && requester.active && (!writingOnly || writer != null)) track?.let(interrupt)
    }

    @Synchronized fun retire(requester: Owner) {
        requester.active = false
        if (owner === requester) { track?.let(interrupt); owner = null }
    }

    @Synchronized fun release(borrowed: Writer<T>) {
        if (writer !== borrowed) return
        writer = null
        if (closed) disposeTrack()
    }

    @Synchronized fun close() {
        if (closed) return
        closed = true
        owner?.active = false
        owner = null
        track?.let(interrupt)
        if (writer == null) disposeTrack()
    }

    private fun disposeTrack() {
        val old = track ?: return
        track = null; sampleRate = null; releaseAttempts++
        dispose(old)
    }

    @Synchronized fun snapshot() = Snapshot(tracksCreated, releaseAttempts, sampleRate, writer != null, closed)
}
