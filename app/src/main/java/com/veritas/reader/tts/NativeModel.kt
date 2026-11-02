package com.veritas.reader.tts

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SpeechModelRole { LIVE, PREVIEW, EXPORT }
internal interface PlaybackModelPin { fun setPlaybackActive(active: Boolean) }
internal class SpeechModelBusyException(message: String = "Voice tasks are busy or device memory is low. Pause reading and try again.") : IllegalStateException(message)

/** Process admission only. Native instances remain private to their original owners. */
internal class NativeModelResources(
    val policy: SpeechResourcePolicy = SpeechResourcePolicy(),
    val clock: SpeechClock = SpeechClock.SYSTEM,
    capacity: () -> Int = { policy.maxResidentModels },
    mayAddSecond: () -> Boolean = { true },
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    val admission = ModelAdmission(policy, clock, capacity, mayAddSecond)
    @Volatile internal var retirementFailed = false
    suspend fun acquire(owner: ModelAdmission.Owner) {
        val queuedAt = clock.nowMs()
        admission.waiting(owner, true)
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                if (admission.tryAcquire(owner)) return
                if (retirementFailed) throw SpeechModelBusyException("A voice could not be released safely. Restart Vern and try again.")
                if (!owner.live && clock.expired(queuedAt, policy.admissionWaitMs)) throw SpeechModelBusyException()
                delay(25)
            }
        } finally { admission.waiting(owner, false) }
    }
    companion object {
        @Volatile private var process: NativeModelResources? = null
        fun get(context: Context): NativeModelResources = process ?: synchronized(this) {
            process ?: run {
                val app = context.applicationContext
                val activity = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val policy = SpeechResourcePolicy()
                NativeModelResources(policy, clock = SpeechClock { android.os.SystemClock.elapsedRealtime() }, capacity = {
                    if (activity?.isLowRamDevice == true) 1 else policy.maxResidentModels
                }, mayAddSecond = {
                    val info = ActivityManager.MemoryInfo()
                    activity?.getMemoryInfo(info)
                    activity != null && !info.lowMemory && info.availMem > policy.secondModelFreeBytes &&
                        info.availMem / 2 > info.threshold
                }).also { resources ->
                    app.registerComponentCallbacks(object : ComponentCallbacks2 {
                        override fun onConfigurationChanged(config: Configuration) = Unit
                        override fun onLowMemory() { resources.admission.trim(true) }
                        override fun onTrimMemory(level: Int) {
                            if (level != ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) resources.admission.trim(true)
                        }
                    })
                    resources.scope.launch {
                        while (isActive) { delay(1_000); resources.admission.trim(false) }
                    }
                    process = resources
                }
            }
        }
    }
}

/** Lazy, reloadable model slot. A lease covers construction, generation and metadata capture. */
internal class NativeModel<T : Any>(
    private val resources: NativeModelResources,
    role: SpeechModelRole,
    private val create: () -> T,
    private val free: (T) -> Unit
) : PlaybackModelPin {
    private val mutex = Mutex()
    @Volatile private var model: T? = null
    @Volatile private var closed = false
    private val disposed = CompletableDeferred<Unit>()
    private val owner = ModelAdmission.Owner(role == SpeechModelRole.LIVE, ::scheduleRetirement)

    suspend fun <R> use(work: (T) -> R): R {
        check(!closed) { "Speech model is closed" }
        resources.acquire(owner)
        try {
            return mutex.withLock {
                currentCoroutineContext().ensureActive()
                check(!closed) { "Speech model is closed" }
                val active = model ?: create().also { model = it }
                currentCoroutineContext().ensureActive()
                check(!closed) { "Speech model is closed" }
                work(active)
            }
        } finally {
            resources.admission.release(owner)
            if (model == null) resources.admission.unload(owner)
        }
    }
    override fun setPlaybackActive(active: Boolean) { resources.admission.pin(owner, active) }
    fun close() {
        closed = true
        resources.admission.close(owner)
        // No allocation ever took place (or it already retired).
        if (!resources.admission.contains(owner)) disposed.complete(Unit)
    }
    suspend fun awaitClosed() { disposed.await() }
    private fun scheduleRetirement() {
        resources.scope.launch {
            var released = false
            try {
                mutex.withLock {
                    val old = model
                    model = null
                    if (old != null) free(old)
                }
                released = true
            } catch (error: Exception) {
                runCatching { Log.w("NativeModel", "Model retirement failed", error) }
            } finally {
                if (released) resources.admission.retired(owner) else resources.retirementFailed = true
                if (closed) disposed.complete(Unit)
            }
        }
    }
}
