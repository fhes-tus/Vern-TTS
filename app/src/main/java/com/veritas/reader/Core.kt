package com.veritas.reader

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PlaybackActions {
    const val ACTION_PLAY = "com.veritas.reader.action.PLAY"
    const val ACTION_PAUSE = "com.veritas.reader.action.PAUSE"
    const val ACTION_STOP = "com.veritas.reader.action.STOP"
    const val ACTION_NEXT = "com.veritas.reader.action.NEXT"
    const val ACTION_PREVIOUS = "com.veritas.reader.action.PREVIOUS"
    const val ACTION_JUMP_TO = "com.veritas.reader.action.JUMP_TO"
    const val ACTION_SPEAK_SELECTION = "com.veritas.reader.action.SPEAK_SELECTION"
    const val ACTION_UPDATE_PLAYBACK_SETTINGS = "com.veritas.reader.action.UPDATE_PLAYBACK_SETTINGS"
    const val ACTION_SET_SLEEP_TIMER = "com.veritas.reader.action.SET_SLEEP_TIMER"
    const val ACTION_CANCEL_SLEEP_TIMER = "com.veritas.reader.action.CANCEL_SLEEP_TIMER"
    const val ACTION_MEDIA_BUTTON = "android.intent.action.MEDIA_BUTTON"

    const val EXTRA_DOCUMENT_ID = "document_id"
    const val EXTRA_START_INDEX = "start_index"
    const val EXTRA_RATE = "rate"
    const val EXTRA_PITCH = "pitch"
    const val EXTRA_SELECTION_TEXT = "selection_text"
    const val EXTRA_CHAR_OFFSET = "char_offset"
    const val EXTRA_SLEEP_TIMER_DURATION_MILLIS = "sleep_timer_duration_millis"
    const val EXTRA_SLEEP_TIMER_ACTION = "sleep_timer_action"
    const val EXTRA_SLEEP_TIMER_STOP_AT_END_OF_SECTION = "sleep_timer_stop_at_end_of_section"
}

object PlaybackStateStore {
    var activeDocumentId by mutableStateOf<String?>(null)
    var documentTitle by mutableStateOf("")
    var sourceLabel by mutableStateOf("")
    var currentIndex by mutableIntStateOf(0)
    var chunkCount by mutableIntStateOf(0)
    var isPlaying by mutableStateOf(false)
    var isForegroundActive by mutableStateOf(false)
    var statusMessage by mutableStateOf("Ready.")
    var readerMode by mutableStateOf(ReaderMode.TEXT)
    var pendingPronunciationFixWord by mutableStateOf<String?>(null)
    // Whether the app UI is currently in the foreground. The PlaybackService uses this to
    // attribute listening time to background playback (foreground reading time is tracked
    // separately by the ViewModel's session timer, so this prevents double counting).
    var appInForeground by mutableStateOf(true)

    var sentenceCount: Int
        get() = chunkCount
        set(value) {
            chunkCount = value
        }
    var rate by mutableFloatStateOf(1.0f)
    var pitch by mutableFloatStateOf(1.0f)
    @Volatile internal var pendingVoiceSettings = false
    var queueCount by mutableIntStateOf(0)
    var autoPlayQueue by mutableStateOf(true)
    var currentSentenceStart by mutableIntStateOf(0)
    var currentSentenceEnd by mutableIntStateOf(0)
    var activeTableColumnIndex by mutableIntStateOf(-1)
    var sleepTimerDurationMillis by mutableLongStateOf(0L)
    var sleepTimerEndsAtMillis by mutableLongStateOf(0L)
    var sleepTimerStopAtEndOfSection by mutableStateOf(false)
    var sleepTimerActionName by mutableStateOf(VeritasSleepTimerAction.PAUSE.name)

    val sleepTimerAction: VeritasSleepTimerAction
        get() = VeritasSleepTimerAction.fromName(sleepTimerActionName)

    fun activeSleepTimerSnapshot(nowMillis: Long = System.currentTimeMillis()): VeritasSleepTimerSnapshot? {
        val snapshot = VeritasSleepTimerSnapshot(
            durationMillis = sleepTimerDurationMillis,
            endsAtMillis = sleepTimerEndsAtMillis,
            action = sleepTimerAction,
            stopAtEndOfSection = sleepTimerStopAtEndOfSection
        )
        return snapshot.takeIf { sleepTimerStopAtEndOfSection || (sleepTimerDurationMillis > 0L && snapshot.isActive(nowMillis)) }
    }

    fun setSleepTimer(request: VeritasSleepTimerRequest, nowMillis: Long = System.currentTimeMillis()) {
        sleepTimerDurationMillis = request.durationMillis
        sleepTimerEndsAtMillis = if (request.stopAtEndOfSection) 0L else request.endsAt(nowMillis)
        sleepTimerActionName = request.action.name
        sleepTimerStopAtEndOfSection = request.stopAtEndOfSection
    }

    fun clearSleepTimer() {
        sleepTimerDurationMillis = 0L
        sleepTimerEndsAtMillis = 0L
        sleepTimerStopAtEndOfSection = false
        sleepTimerActionName = VeritasSleepTimerAction.PAUSE.name
    }

    fun reset() {
        activeDocumentId = null
        documentTitle = ""
        sourceLabel = ""
        currentIndex = 0
        chunkCount = 0
        isPlaying = false
        isForegroundActive = false
        statusMessage = "Ready."
        queueCount = 0
        currentSentenceStart = 0
        currentSentenceEnd = 0
        activeTableColumnIndex = -1
        clearSleepTimer()
    }

    fun restoreFromPersistence(context: Context) {
        if (activeDocumentId != null) return
        val repo = DocumentRepository(context)
        val resumePoint = runCatching { repo.loadPersistedResumePoint() }.getOrNull()
        if (resumePoint != null && resumePoint.documentId.isNotBlank()) {
            activeDocumentId = resumePoint.documentId
            currentIndex = resumePoint.chunkIndex
            val title = repo.getDocumentTitle(resumePoint.documentId)
            if (title != "Deleted Book") {
                documentTitle = title
            }
        }
        if (sleepTimerDurationMillis <= 0L) {
            val timer = runCatching { repo.loadPersistedSleepTimer() }.getOrNull()
            if (timer != null) {
                sleepTimerDurationMillis = timer.durationMillis
                sleepTimerEndsAtMillis = timer.endsAtMillis
                sleepTimerActionName = timer.action.name
                sleepTimerStopAtEndOfSection = timer.stopAtEndOfSection
            }
        }
    }
}


object ReaderTextModelCache {
    private const val MAX_ENTRIES = 8
    private val cache = object : LinkedHashMap<String, ReaderTextModel>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ReaderTextModel>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    fun get(documentId: String?, rawText: String, pageCount: Int): ReaderTextModel {
        val key = buildKey(documentId, rawText, pageCount)
        synchronized(cache) {
            cache[key]?.let { return it }
        }
        val model = ReaderTextIndex.build(rawText, pageCount)
        synchronized(cache) {
            cache[key] = model
        }
        return model
    }

    fun invalidate(documentId: String) {
        synchronized(cache) {
            val prefix = "${documentId.ifBlank { "anonymous" }}:"
            cache.keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
        }
    }

    private fun buildKey(documentId: String?, rawText: String, pageCount: Int): String {
        val id = documentId?.ifBlank { null } ?: "anonymous"
        return "$id:${pageCount.coerceAtLeast(0)}:${rawText.length}:${rawText.hashCode()}"
    }
}

fun buildReaderDocument(metadata: SavedDocument, rawText: String): ReaderDocument {
    val model = ReaderTextModelCache.get(metadata.id, rawText, metadata.pageCount)
    return ReaderDocument(
        id = metadata.id,
        title = metadata.title.ifBlank { "Untitled reading" },
        sourceLabel = metadata.sourceLabel,
        rawText = rawText,
        sentences = model.sentences.map { it.text },
        pageCount = model.pageCount,
        partial = metadata.partial
    )
}

object TextChunker {
    fun chunk(text: String): List<String> {
        return ReaderTextIndex.sentences(text)
    }
}

fun formatUpdated(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd/MM/yyyy, HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
