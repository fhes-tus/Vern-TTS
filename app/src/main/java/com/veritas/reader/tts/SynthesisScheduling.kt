package com.veritas.reader.tts

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

/** Priority at native-call admission; an already running native call is allowed to finish. */
internal class SynthesisPriorityGate {
    private val nativeMutex = Mutex()
    private val reservationLock = Any()
    private val pending = MutableStateFlow(0)
    val pendingDemands: Int get() = pending.value

    inner class Demand internal constructor() {
        @Volatile private var released = false
        val isActive: Boolean get() = !released
        fun cancel() = synchronized(reservationLock) {
            if (!released) { released = true; pending.value = pending.value - 1 }
        }
    }

    fun reserveDemand(): Demand = synchronized(reservationLock) {
        pending.value = pending.value + 1
        Demand()
    }

    suspend fun <T> demand(ticket: Demand, work: suspend () -> T): T? = try {
        nativeMutex.withLock { if (ticket.isActive) work() else null }
    } finally { ticket.cancel() }

    suspend fun <T> ahead(eligible: () -> Boolean, remainingMs: (() -> Long)? = null, work: suspend () -> T): T? {
        while (eligible()) {
            if (remainingMs == null) pending.first { it == 0 }
            else {
                val wait = remainingMs()
                if (wait <= 0 || withTimeoutOrNull(wait) { pending.first { it == 0 } } == null) return null
            }
            var retry = false
            val result = nativeMutex.withLock {
                if (!eligible()) return null
                // An ahead waiter may have queued before the demand reservation existed.
                if (pending.value > 0) { retry = true; null } else work()
            }
            if (!retry) return result
        }
        return null
    }
}

/** One lifetime worker. Window replacement does not cancel an in-flight native result. */
internal class LookAheadProducer(
    scope: CoroutineScope,
    private val work: suspend (Window, Int) -> Unit,
    private val onFailure: (Exception) -> Unit = {},
    private val shouldContinue: (Window, Int) -> Boolean = { _, _ -> true }
) {
    data class Window(val start: Int, val end: Int, val generation: Long, val rate: Float, val pitch: Float,
        val queuedAtMs: Long = SpeechClock.SYSTEM.nowMs(),
        val textAt: (Int) -> String)

    private val latest = AtomicReference<Window?>(null)
    val current: Window? get() = latest.get()
    private val updates = Channel<Unit>(Channel.CONFLATED)
    private val worker = scope.launch {
        var processed: Window? = null
        for (signal in updates) {
            while (true) {
                val window = latest.get()
                if (window == null) { processed = null; break }
                if (window === processed) break
                for (index in window.start until window.end) {
                    if (latest.get() !== window || !shouldContinue(window, index)) break
                    try { work(window, index) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { onFailure(failure) }
                }
                processed = window
            }
        }
    }

    fun update(window: Window) { if (worker.isActive) { latest.set(window); updates.trySend(Unit) } }
    fun clear() { latest.set(null); updates.trySend(Unit) }
    fun clearIf(window: Window?) { if (window != null && latest.compareAndSet(window, null)) updates.trySend(Unit) }
    fun isLatest(window: Window): Boolean = latest.get() === window
    fun close() { latest.set(null); updates.close(); worker.cancel() }
}
