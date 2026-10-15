package com.veritas.reader

import android.os.SystemClock
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/** Follows engine PCM, corrected to output frames when the engine supplies word timing. */
internal class SystemSpeechMeter(private val scope: CoroutineScope, private val publish: (Float) -> Unit) {
    private val envelopes = ConcurrentHashMap<String, SpeechEnvelope>()
    private var job: Job? = null
    @Volatile private var activeId: String? = null
    private var startedAt = 0L

    fun begin(id: String?, rate: Int, format: Int, channels: Int) {
        if (id == null || rate <= 0) return
        if (envelopes.size >= 4) envelopes.keys.firstOrNull { it != activeId }?.let(envelopes::remove)
        envelopes[id] = SpeechEnvelope(rate, format, channels)
    }
    fun append(id: String?, audio: ByteArray?) { if (id != null && audio != null) envelopes[id]?.append(audio) }
    fun start(id: String) {
        job?.cancel(); activeId = id; startedAt = SystemClock.elapsedRealtime()
        job = scope.launch {
            while (isActive && PlaybackStateStore.isPlaying && activeId == id) {
                publish(envelopes[id]?.levelAt(SystemClock.elapsedRealtime() - startedAt) ?: 0f)
                delay(50)
            }
            publish(0f)
        }
    }
    fun align(id: String?, frame: Int) {
        if (id == null || frame < 0) return
        val rate = envelopes[id]?.sampleRate ?: return
        if (activeId != id) start(id)
        startedAt = SystemClock.elapsedRealtime() - frame * 1000L / rate
    }
    fun finish(id: String?) {
        if (id == null) return
        envelopes.remove(id)
        if (activeId == id) { job?.cancel(); activeId = null; publish(0f) }
    }
    fun clear() { job?.cancel(); activeId = null; envelopes.clear(); publish(0f) }
}
