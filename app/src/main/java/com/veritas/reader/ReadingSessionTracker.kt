package com.veritas.reader

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.ZoneId

/** One process-wide owner arbitrates visible reading and actual audio output. All writes run on IO. */
internal class ReadingSessionTracker private constructor(context: Context) {
    private val repository = DocumentRepository(context.applicationContext)
    private val commands = Channel<() -> Unit>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val changes = MutableStateFlow(0L)
    private var lastWidgetUpdate = 0L
    private val clock = ReadingSessionClock(save = {
        repository.putReadingSession(it); changes.value++
        val now = SystemClock.elapsedRealtime()
        if (it.closedReason != null || now - lastWidgetUpdate > 30_000) {
            lastWidgetUpdate = now; scope.launch { updateVeritasWidgets(context.applicationContext) }
        }
    })
    private var readerOwner: String? = null
    private var reader: ReadingSessionTarget? = null
    private var lastInteraction = SystemClock.elapsedRealtime()
    private var requested = false
    private var audio: ReadingSessionTarget? = null
    private var utterance: String? = null
    private var lastAudio: ReadingSessionTarget? = null
    private var explicitDocument: String? = null
    private val models = linkedMapOf<String, ReaderTextModel>()

    init {
        scope.launch {
            synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
                repository.saveReadingSessions(repository.loadReadingSessions().map {
                    if (it.closedReason == null) it.copy(closedReason = "interrupted") else it
                })
            }
            for (command in commands) {
                try { command() } catch (e: CancellationException) { throw e }
                catch (e: Exception) { android.util.Log.e("ReadingSessions", "Session checkpoint failed", e) }
            }
        }
        scope.launch { while (isActive) { delay(5_000); send { reconcile(it) } } }
    }
    private data class EventTime(val wall: Long, val mono: Long, val zone: String)
    private val commandLock = Any()
    // Capture at the UI/audio event, before preference IO or source-page parsing.
    // Serialized enqueue makes event timestamps monotonic even across callers.
    private fun send(command: (EventTime) -> Unit) = synchronized(commandLock) {
        val event = EventTime(System.currentTimeMillis(), SystemClock.elapsedRealtime(), ZoneId.systemDefault().id)
        commands.trySend { command(event) }
        Unit
    }
    private fun target(doc: SavedDocument, mode: ReadingSessionMode, index: Int): ReadingSessionTarget {
        val file = java.io.File(repository.docsDir, doc.fileName)
        val revision = "${doc.fileName}:${file.lastModified()}:${file.length()}:${doc.sentenceIndexVersion}"
        val sourcePages = doc.sourceLabel.uppercase() in setOf("PDF", "PPTX", "PPT")
        val page = if (sourcePages) {
            val model = models[revision] ?: ReaderTextModelCache.get(doc.id, repository.readText(doc), doc.pageCount).also {
                if (models.size >= 2) models.remove(models.keys.first()); models[revision] = it
            }
            model.sentences.getOrNull(index)?.pageNumber
        } else null
        return ReadingSessionTarget(doc.id, doc.title, revision, mode, index.coerceAtLeast(0), page)
    }
    private fun reconcile(event: EventTime, reason: String = "changed") {
        val mono = event.mono; val wall = event.wall; val zone = event.zone
        val timeout = repository.prefs.getInt("reading_idle_seconds", 120).coerceIn(30, 900) * 1000L
        val explicit = explicitDocument != null && reader?.documentId == explicitDocument
        val eligible = reader != null && !requested && (explicit ||
            (!repository.prefs.getBoolean("reading_explicit_only", false) && mono - lastInteraction < timeout))
        val chosen = audio ?: if (requested) lastAudio else if (eligible) reader else null
        val cap = if (clock.current?.mode == ReadingSessionMode.READING && !explicit) minOf(mono, lastInteraction + timeout) else mono
        clock.transition(chosen, audio != null || eligible, wall, mono, zone, reason, cap)
    }
    fun reader(owner: String, docId: String?, index: Int = 0) = send { event ->
        if (docId == null) {
            if (readerOwner == owner) { reader = null; readerOwner = null; reconcile(event, "reader hidden") }
        } else {
            val doc = repository.findDocument(docId)
            if (doc != null) {
                if (readerOwner != owner || reader?.documentId != docId) lastInteraction = event.mono
                readerOwner = owner; reader = target(doc, ReadingSessionMode.READING, index); reconcile(event)
            }
        }
    }
    fun interaction() = send { event ->
        val now = event.mono
        val timeout = repository.prefs.getInt("reading_idle_seconds", 120).coerceIn(30, 900) * 1000L
        if (now - lastInteraction >= timeout) reconcile(event, "idle")
        lastInteraction = now
        if (clock.current == null) reconcile(event)
    }
    fun playbackRequested(value: Boolean) = send { event ->
        requested = value
        if (!value) {
            audio = null; lastAudio = null; utterance = null
            clock.close("paused or stopped", event.wall, event.mono, event.zone)
        }
        reconcile(event)
    }
    fun audioStarted(docId: String, index: Int, token: String) = send { event ->
        val doc = repository.findDocument(docId) ?: return@send
        requested = true; utterance = token
        audio = target(doc, ReadingSessionMode.LISTENING, index); lastAudio = audio; reconcile(event)
    }
    fun audioEnded(token: String? = null) = send { event ->
        if (token == null || token == utterance) { audio = null; reconcile(event, "buffering") }
    }
    fun seek() = send { event ->
        audio = null; lastAudio = null; utterance = null
        clock.close("seek", event.wall, event.mono, event.zone)
        reconcile(event, "seek")
    }
    fun explicitTimer(docId: String?) = send { event -> explicitDocument = docId; reconcile(event, "timer changed") }
    fun flush() = send { event -> reconcile(event); changes.value++; scope.launch { updateVeritasWidgets(repository.appContext) } }
    companion object {
        @Volatile private var instance: ReadingSessionTracker? = null
        fun get(context: Context): ReadingSessionTracker = instance ?: synchronized(this) {
            instance ?: ReadingSessionTracker(context.applicationContext).also { instance = it }
        }
        fun playbackRequested(value: Boolean) { instance?.playbackRequested(value) }
    }
}
