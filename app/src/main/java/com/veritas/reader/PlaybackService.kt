package com.veritas.reader

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    internal val serviceJob = SupervisorJob()
    internal val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    internal val TAG_TTS = "VeritasSystemTts"
    internal lateinit var repository: DocumentRepository
    internal var mediaSession: MediaSession? = null
    internal var mediaSessionPlayer: VeritasMediaSessionPlayer? = null
    internal val mainHandler = Handler(Looper.getMainLooper())

    internal var tts: TextToSpeech? = null
    internal var ttsReady = false
    internal var pendingSpeak = false
    internal var pendingSelectionText: String? = null
    internal var activeEnginePackage: String? = null

    internal var activeDocument: SavedDocument? = null
    // Slide number per chunk for PPTX docs (null otherwise): drives the short
    // silence beats at slide transitions and after slide titles.
    internal var chunkPageNumbers: IntArray? = null
    internal var lastSavedRate = Float.NaN
    internal var lastSavedPitch = Float.NaN
    internal var chunks: List<String> = emptyList()
    internal var artworkDocumentId: String? = null
    internal var notificationArtwork: Bitmap? = null
    internal var artworkBytes: ByteArray? = null
    internal var artworkBytesDocumentId: String? = null
    internal var defaultAppArtwork: Bitmap? = null
    internal var isCoverLoading = false
    internal var activeTtsRate = Float.NaN
    internal var activeTtsPitch = Float.NaN
    internal var queuedChunkUtteranceId: String? = null
    internal var queuedChunkIndex = -1
    internal var queuedChunkSpeechText = ""
    internal var queuedChunkBaseOffset = 0
    internal var activeChunkUtteranceId: String? = null
    internal var activeChunkIndex = -1
    /**
     * Utterances playback has already moved past, whose completion must not advance.
     *
     * onRangeStart promotes the pre-queued sentence the instant the engine starts
     * speaking it, so the previous sentence's onDone always arrives after playback has
     * already moved on. Advancing on it calls speak() with QUEUE_FLUSH over audio that
     * is mid-word, cutting it off and restarting it — the stammer.
     *
     * A single slot was not enough: promotions can outrun onDone callbacks, so more
     * than one retired utterance can be in flight and the older one fell through.
     */
    internal val retiredUtteranceIds = LinkedHashSet<String>()

    internal fun retireUtterance(id: String?) {
        if (id == null) return
        retiredUtteranceIds.add(id)
        while (retiredUtteranceIds.size > 16) {
            retiredUtteranceIds.remove(retiredUtteranceIds.first())
        }
    }

    internal var veritasAudioBuffer: com.veritas.reader.tts.VeritasAudioBuffer? = null
    internal var veritasAudioVoiceId: String? = null
    // Timestamp when the current sentence started speaking; used to attribute background
    // listening time to the active document (see recordBackgroundListening()).
    internal var activeChunkStartedAt = 0L
    internal var activeChunkSpeechText = ""
    internal var activeChunkBaseOffset = 0
    internal var spokenCharOffset = 0
    internal var spokenWordCount = 0
    internal var resumeDocumentId: String? = null
    internal var resumeChunkIndex = -1
    internal var resumeCharOffset = 0
    internal var resumeWordCount = 0
    internal var pendingJumpCharOffset: Int? = null
    internal var sleepTimerRunnable: Runnable? = null

    internal var audioFocusRequest: android.media.AudioFocusRequest? = null
    internal var pausedDueToTransientFocusLoss = false
    internal val audioManager by lazy { getSystemService(AUDIO_SERVICE) as android.media.AudioManager }

    internal var ttsSessionId: Int = android.media.AudioManager.ERROR
    internal var ttsEqualizer: android.media.audiofx.Equalizer? = null
    internal var sleepFadeVolume: Float = 1.0f

    internal var sensorManager: SensorManager? = null
    internal var accelerometer: Sensor? = null
    internal var lastShakeTimestamp = 0L
    internal var lastAcceleration = 0f
    internal var currentAcceleration = 0f
    internal var shakeAcceleration = 0f
    internal var isShakeListenerRegistered = false

    internal val shakeEventListener by lazy { createShakeEventListener() }
    internal val sleepTimerTicker by lazy { createSleepTimerTicker() }

    override fun onCreate() {
        super.onCreate()
        repository = DocumentRepository(applicationContext)
        PlaybackStateStore.restoreFromPersistence(applicationContext)
        PlaybackStateStore.queueCount = repository.loadQueueDocuments().size
        createNotificationChannel()
        setupMediaSession()
        // If the process died while a sleep timer was running, restore it from prefs
        // so the timer survives instead of silently disappearing.
        if (PlaybackStateStore.activeSleepTimerSnapshot() == null) {
            repository.loadPersistedSleepTimer()?.let { persisted ->
                PlaybackStateStore.sleepTimerDurationMillis = persisted.durationMillis
                PlaybackStateStore.sleepTimerEndsAtMillis = persisted.endsAtMillis
                PlaybackStateStore.sleepTimerActionName = persisted.action.name
                PlaybackStateStore.sleepTimerStopAtEndOfSection = persisted.stopAtEndOfSection
            }
        }
        scheduleSleepTimerFromStore()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Satisfy the Android foreground service requirement immediately (Android 12, 14, 15, 16)
        startForegroundNow()

        if (intent == null) {
            // Process was killed and restarted by system via START_STICKY.
            // If nothing is playing or queued, tear down cleanly after meeting system foreground requirement.
            if (!PlaybackStateStore.isPlaying && activeDocument == null && PlaybackStateStore.activeDocumentId == null) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
                return START_NOT_STICKY
            }
        }

        val action = intent?.action
        when (action) {
            PlaybackActions.ACTION_PLAY -> handlePlay(intent.extras)
            PlaybackActions.ACTION_PAUSE -> pauseSpeech()
            PlaybackActions.ACTION_STOP -> stopSpeechAndService()
            PlaybackActions.ACTION_NEXT -> moveBy(1)
            PlaybackActions.ACTION_PREVIOUS -> moveBy(-1)
            PlaybackActions.ACTION_JUMP_TO -> handleJump(intent.extras)
            PlaybackActions.ACTION_SPEAK_SELECTION -> handleSpeakSelection(intent.extras)
            PlaybackActions.ACTION_UPDATE_PLAYBACK_SETTINGS -> handlePlaybackSettingsUpdate(intent.extras)
            PlaybackActions.ACTION_SET_SLEEP_TIMER -> handleSetSleepTimer(intent.extras)
            PlaybackActions.ACTION_CANCEL_SLEEP_TIMER -> cancelSleepTimer("Sleep timer cancelled.")
            // ACTION_MEDIA_BUTTON is intentionally NOT handled here. MediaSessionService's
            // super.onStartCommand() already routes hardware/Bluetooth media keys through the
            // MediaSession to this service's Player (VeritasMediaSessionPlayer), which maps
            // play/pause/next/previous/stop to the controller.
            else -> refreshForegroundNotification()
        }
        return START_STICKY
    }

    internal fun handlePlay(extras: Bundle?) {
        val documentId = extras?.getString(PlaybackActions.EXTRA_DOCUMENT_ID)
            ?: PlaybackStateStore.activeDocumentId
            ?: repository.loadQueueDocuments().firstOrNull()?.id
            ?: return
        val startIndex = extras?.getInt(PlaybackActions.EXTRA_START_INDEX, PlaybackStateStore.currentIndex)
            ?: PlaybackStateStore.currentIndex
        val charOffset = extras?.getInt(PlaybackActions.EXTRA_CHAR_OFFSET, -1).takeIf { it != null && it >= 0 }

        PlaybackStateStore.rate = extras?.getFloat(PlaybackActions.EXTRA_RATE, PlaybackStateStore.rate)
            ?.coerceIn(0.5f, 2.0f) ?: PlaybackStateStore.rate
        PlaybackStateStore.pitch = extras?.getFloat(PlaybackActions.EXTRA_PITCH, PlaybackStateStore.pitch)
            ?.coerceIn(0.7f, 1.4f) ?: PlaybackStateStore.pitch

        pausedDueToTransientFocusLoss = false
        // A fresh PLAY command is also how live voice/pronunciation edits restart the
        // current section. Drop audio synthesized using the previous configuration.
        veritasAudioBuffer?.flush()
        if (!loadDocument(documentId, startIndex)) return

        pendingJumpCharOffset = charOffset
        PlaybackStateStore.isPlaying = true
        PlaybackStateStore.isForegroundActive = true
        PlaybackStateStore.statusMessage = "Preparing playback…"
        if (!requestAudioFocus()) {
            Log.w(TAG, "Audio focus not granted; continuing anyway.")
        }
        startForegroundNow()
        ensureTtsReadyAndSpeak()
    }

    private fun handleJump(extras: Bundle?) {
        val documentId = extras?.getString(PlaybackActions.EXTRA_DOCUMENT_ID)
            ?: PlaybackStateStore.activeDocumentId
            ?: return
        val index = extras?.getInt(PlaybackActions.EXTRA_START_INDEX, PlaybackStateStore.currentIndex)
            ?: PlaybackStateStore.currentIndex
        val charOffset = extras?.getInt(PlaybackActions.EXTRA_CHAR_OFFSET, -1).takeIf { it != null && it >= 0 }

        val wasPlaying = PlaybackStateStore.isPlaying
        if (!loadDocument(documentId, index)) return
        repository.updateProgress(documentId, PlaybackStateStore.currentIndex, chunks.size)

        pendingJumpCharOffset = charOffset
        if (wasPlaying || charOffset != null) {
            PlaybackStateStore.isPlaying = true
            startForegroundNow()
            speakCurrent()
        } else {
            refreshForegroundNotification()
        }
    }

    private fun handleSpeakSelection(extras: Bundle?) {
        val selection = extras?.getString(PlaybackActions.EXTRA_SELECTION_TEXT)
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(1200)
            .orEmpty()
        if (selection.isBlank()) return
        if (PlaybackStateStore.isPlaying) rememberPausePoint()
        // The neural engine writes directly to AudioTrack, so stopping only platform TTS
        // leaves the previous reading audible beneath the selection preview.
        veritasAudioBuffer?.flush()
        PlaybackStateStore.isPlaying = false
        ensureTtsReadyAndSpeakSelection(selection)
    }

    private fun handlePlaybackSettingsUpdate(extras: Bundle?) {
        PlaybackStateStore.rate = extras?.getFloat(PlaybackActions.EXTRA_RATE, PlaybackStateStore.rate)
            ?.coerceIn(0.5f, 2.0f) ?: PlaybackStateStore.rate
        PlaybackStateStore.pitch = extras?.getFloat(PlaybackActions.EXTRA_PITCH, PlaybackStateStore.pitch)
            ?.coerceIn(0.7f, 1.4f) ?: PlaybackStateStore.pitch
        activeDocument?.let { repository.saveDocVoiceMemory(it.id, PlaybackStateStore.rate, PlaybackStateStore.pitch) }
        lastSavedRate = PlaybackStateStore.rate
        lastSavedPitch = PlaybackStateStore.pitch
        veritasAudioBuffer?.flush()
        if (PlaybackStateStore.isPlaying) {
            speakCurrent()
        } else {
            updateMediaSessionState()
            refreshForegroundNotification()
        }
    }

    private fun handleSetSleepTimer(extras: Bundle?) {
        val durationMillis = extras?.getLong(PlaybackActions.EXTRA_SLEEP_TIMER_DURATION_MILLIS, 0L) ?: 0L
        val action = VeritasSleepTimerAction.fromName(extras?.getString(PlaybackActions.EXTRA_SLEEP_TIMER_ACTION))
        val stopAtEndOfSection = extras?.getBoolean(PlaybackActions.EXTRA_SLEEP_TIMER_STOP_AT_END_OF_SECTION, false) ?: false
        val request = runCatching {
            VeritasSleepTimerRequest(durationMillis = durationMillis, action = action, stopAtEndOfSection = stopAtEndOfSection)
        }.getOrNull()

        if (request == null) {
            cancelSleepTimer("Sleep timer could not be started.")
            return
        }

        PlaybackStateStore.setSleepTimer(request)
        repository.saveSleepTimerState(
            durationMillis = PlaybackStateStore.sleepTimerDurationMillis,
            endsAtMillis = PlaybackStateStore.sleepTimerEndsAtMillis,
            actionName = PlaybackStateStore.sleepTimerActionName,
            stopAtEndOfSection = PlaybackStateStore.sleepTimerStopAtEndOfSection
        )
        scheduleSleepTimerFromStore()
        PlaybackStateStore.statusMessage = if (stopAtEndOfSection) {
            "Sleep timer set to stop at end of section."
        } else {
            "Sleep timer set for ${VeritasSleepTimerFormatter.formatDuration(request.durationMillis)}."
        }
        updateMediaSessionState()
        refreshForegroundNotification()
    }

    internal fun loadDocument(documentId: String, requestedIndex: Int): Boolean {
        val existingLoaded = activeDocument?.id == documentId && chunks.isNotEmpty()
        val doc = if (existingLoaded) activeDocument else repository.findDocument(documentId)
        if (doc == null) {
            PlaybackStateStore.statusMessage = "Could not find this saved reading."
            return false
        }

        if (!existingLoaded) {
            // PCM cache keys are sentence indexes, not document IDs. Do not allow a cached
            // sentence from a previous reading to be reused after switching documents.
            veritasAudioBuffer?.flush()
            val rawText = repository.readText(doc)
            chunks = TextChunker.chunk(rawText)
            // Restore this document's remembered narration pace (if any).
            repository.loadDocVoiceMemory(doc.id)?.let { (rate, pitch) ->
                PlaybackStateStore.rate = rate
                PlaybackStateStore.pitch = pitch
            }
            chunkPageNumbers = if (doc.sourceLabel == "PPTX") {
                val model = ReaderTextModelCache.get(doc.id, rawText, doc.pageCount)
                if (model.sentences.size == chunks.size) {
                    IntArray(model.sentences.size) { model.sentences[it].pageNumber }
                } else null
            } else null
            activeDocument = doc
            clearResumePoint()
            if (artworkDocumentId != doc.id) {
                artworkDocumentId = null
                notificationArtwork = null
                artworkBytes = null
                artworkBytesDocumentId = null
            }
        }

        if (chunks.isEmpty()) {
            PlaybackStateStore.statusMessage = "No readable sentences were found."
            return false
        }

        val safeIndex = requestedIndex.coerceIn(0, chunks.lastIndex)
        PlaybackStateStore.activeDocumentId = documentId
        PlaybackStateStore.documentTitle = doc.title
        PlaybackStateStore.sourceLabel = doc.sourceLabel
        PlaybackStateStore.currentIndex = safeIndex
        PlaybackStateStore.chunkCount = chunks.size
        PlaybackStateStore.queueCount = repository.loadQueueDocuments().size
        return true
    }

    private fun maybePrequeueNext(currentIndex: Int) {
        if (!PlaybackStateStore.isPlaying || chunks.isEmpty()) return
        val nextIndex = currentIndex + 1
        if (nextIndex > chunks.lastIndex) return
        if (leadingSilenceMsFor(nextIndex) > 0L) return

        val rawNextText = chunks.getOrNull(nextIndex)?.trim().orEmpty()
        if (rawNextText.isBlank()) return
        val nextText = repository.applyPronunciationRules(rawNextText)
        val speakNextText = SpeechSanitizer.forSpeech(nextText)
        if (speakNextText.isBlank()) return

        val narrationSettings = repository.loadNarrationSettings()
        val nextRate = NarrationAnalyzer.effectiveRate(PlaybackStateStore.rate, narrationSettings, nextText)
        val nextPitch = NarrationAnalyzer.effectivePitch(PlaybackStateStore.pitch, narrationSettings, nextText)

        if (nextRate != activeTtsRate || nextPitch != activeTtsPitch) {
            return
        }

        val nextUtteranceId = "$CHUNK_UTTERANCE_PREFIX${UUID.randomUUID()}"
        val result = tts?.speak(speakNextText, TextToSpeech.QUEUE_ADD, ttsParams(nextUtteranceId), nextUtteranceId)
        if (result == TextToSpeech.SUCCESS) {
            queuedChunkUtteranceId = nextUtteranceId
            queuedChunkIndex = nextIndex
            queuedChunkSpeechText = rawNextText
            queuedChunkBaseOffset = 0
        }
    }

    internal fun attachListener() {
        attachVoiceShaping()
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                mainHandler.post {
                    if (utteranceId != activeChunkUtteranceId && utteranceId != queuedChunkUtteranceId && utteranceId?.startsWith(SELECTION_UTTERANCE_PREFIX) != true) {
                        return@post
                    }
                    if (utteranceId?.startsWith(SELECTION_UTTERANCE_PREFIX) == true) {
                        PlaybackStateStore.statusMessage = "Selected text finished."
                        refreshForegroundNotification()
                    } else if (PlaybackStateStore.isPlaying) {
                        // Attribute this sentence's duration to background listening time.
                        recordBackgroundListening()
                        // clearResumePoint() nulls activeChunkUtteranceId AND calls
                        // clearQueuedChunk(), so every value this decision depends on has to
                        // be captured before it runs.
                        val finishedWasActive = utteranceId == activeChunkUtteranceId
                        val promotedIndex = queuedChunkIndex
                        val promotedUtteranceId = queuedChunkUtteranceId
                        val promotedSpeechText = queuedChunkSpeechText
                        val promotedBaseOffset = queuedChunkBaseOffset

                        clearResumePoint()

                        if (promotedUtteranceId != null && finishedWasActive) {
                            // The next sentence was pre-queued and is ALREADY playing.
                            retireUtterance(utteranceId)
                            activeChunkUtteranceId = promotedUtteranceId
                            activeChunkIndex = promotedIndex
                            activeChunkStartedAt = System.currentTimeMillis()
                            activeChunkSpeechText = promotedSpeechText
                            activeChunkBaseOffset = promotedBaseOffset
                            spokenCharOffset = promotedBaseOffset
                            spokenWordCount = wordCountBefore(promotedSpeechText, promotedBaseOffset)
                            updateCurrentSentenceBounds(promotedBaseOffset)

                            PlaybackStateStore.currentIndex = promotedIndex
                            activeDocument?.let { repository.updateProgress(it.id, promotedIndex, chunks.size) }
                            updateMediaSessionMetadata()
                            updateMediaSessionState()
                            refreshForegroundNotification()

                            maybePrequeueNext(promotedIndex)
                        } else if (retiredUtteranceIds.contains(utteranceId)) {
                            // Playback already moved past this sentence; its completion is
                            // just late news. See retiredUtteranceIds.
                            return@post
                        } else {
                            advanceAfterSection()
                        }
                    }
                }
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                mainHandler.post {
                    if (utteranceId == queuedChunkUtteranceId && queuedChunkUtteranceId != null) {
                        // Engine started speaking the pre-queued sentence.
                        val promotedIndex = queuedChunkIndex
                        val promotedUtteranceId = queuedChunkUtteranceId
                        val promotedSpeechText = queuedChunkSpeechText
                        val promotedBaseOffset = queuedChunkBaseOffset
                        clearQueuedChunk()

                        retireUtterance(activeChunkUtteranceId)
                        activeChunkUtteranceId = promotedUtteranceId
                        activeChunkIndex = promotedIndex
                        activeChunkStartedAt = System.currentTimeMillis()
                        activeChunkSpeechText = promotedSpeechText
                        activeChunkBaseOffset = promotedBaseOffset

                        PlaybackStateStore.currentIndex = promotedIndex
                        activeDocument?.let { repository.updateProgress(it.id, promotedIndex, chunks.size) }
                        updateMediaSessionMetadata()
                        updateMediaSessionState()
                        refreshForegroundNotification()

                        maybePrequeueNext(promotedIndex)
                    }

                    if (utteranceId == activeChunkUtteranceId && activeChunkSpeechText.isNotBlank()) {
                        val absoluteStart = (activeChunkBaseOffset + start).coerceIn(0, activeChunkSpeechText.length)
                        spokenCharOffset = absoluteStart
                        spokenWordCount = wordCountBefore(activeChunkSpeechText, absoluteStart)
                        updateCurrentSentenceBounds(absoluteStart)
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    if (utteranceId != activeChunkUtteranceId && utteranceId != queuedChunkUtteranceId && utteranceId?.startsWith(SELECTION_UTTERANCE_PREFIX) != true) {
                        return@post
                    }
                    clearQueuedChunk()
                    handleTtsFailure("Voice engine failed. Try another sentence.", utteranceId)
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                mainHandler.post {
                    if (utteranceId != activeChunkUtteranceId && utteranceId != queuedChunkUtteranceId && utteranceId?.startsWith(SELECTION_UTTERANCE_PREFIX) != true) {
                        return@post
                    }
                    val errorMsg = when (errorCode) {
                        TextToSpeech.ERROR_NETWORK -> "Network error. Check internet connection."
                        TextToSpeech.ERROR_NETWORK_TIMEOUT -> "Network timeout. Try a local voice."
                        TextToSpeech.ERROR_OUTPUT -> "Audio output error. Check device volume."
                        TextToSpeech.ERROR_NOT_INSTALLED_YET -> "Voice not ready yet. Try again."
                        else -> "Voice engine error (code $errorCode). Try another voice."
                    }
                    clearQueuedChunk()
                    handleTtsFailure(errorMsg, utteranceId)
                }
            }
        })
    }

    /**
     * Attributes the time spent speaking the just-finished sentence to the active document's
     * reading time, but ONLY while the app UI is backgrounded. Foreground reading time is
     * tracked by the ViewModel's session timer, so gating on appInForeground avoids double
     * counting.
     */
    private fun recordBackgroundListening() {
        val startedAt = activeChunkStartedAt
        activeChunkStartedAt = 0L
        if (startedAt <= 0L || PlaybackStateStore.appInForeground) return
        val docId = activeDocument?.id ?: return
        val delta = System.currentTimeMillis() - startedAt
        if (delta in 1L..600_000L) {
            repository.recordDocReadingTime(docId, delta)
        }
    }

    private fun handleTtsFailure(message: String, utteranceId: String? = null) {
        Log.w(TAG, "$message utterance=$utteranceId")
        pendingSpeak = false
        pendingSelectionText = null
        PlaybackStateStore.isPlaying = false
        activeDocument?.let { repository.updateProgress(it.id, PlaybackStateStore.currentIndex, chunks.size) }
        clearResumePoint()
        runCatching { tts?.stop() }
        PlaybackStateStore.statusMessage = message
        updateMediaSessionState()
        refreshForegroundNotification()
    }

    internal fun speakCurrent() {
        if (chunks.isEmpty()) return
        clearQueuedChunk()
        val index = PlaybackStateStore.currentIndex.coerceIn(0, chunks.lastIndex)
        val rawChunkText = chunks.getOrNull(index)?.trim().orEmpty()

        val jumpOffset = pendingJumpCharOffset?.coerceIn(0, rawChunkText.length)
        pendingJumpCharOffset = null

        val resumeOffset = jumpOffset ?: resumeOffsetForCurrentChunk(rawChunkText, index)
        val text = repository.applyPronunciationRules(rawChunkText.substring(resumeOffset).trimStart())
        if (text.isBlank()) {
            activeChunkIndex = index
            PlaybackStateStore.currentIndex = index
            clearResumePoint()
            advanceAfterSection()
            return
        }

        // Persist the pace being used with this document, but only when it changes.
        if (PlaybackStateStore.rate != lastSavedRate || PlaybackStateStore.pitch != lastSavedPitch) {
            activeDocument?.let { repository.saveDocVoiceMemory(it.id, PlaybackStateStore.rate, PlaybackStateStore.pitch) }
            lastSavedRate = PlaybackStateStore.rate
            lastSavedPitch = PlaybackStateStore.pitch
        }
        val narrationSettings = repository.loadNarrationSettings()
        val activeChar = NarrationAnalyzer.getActiveCharacter(text, narrationSettings)
        val effectiveRate = NarrationAnalyzer.effectiveRate(PlaybackStateStore.rate, narrationSettings, text)
        val effectivePitch = NarrationAnalyzer.effectivePitch(PlaybackStateStore.pitch, narrationSettings, text)
        tts?.let { engine ->
            VoiceConfigurator.apply(engine, repository.loadVoiceSettings())
            // Always choose a voice for this utterance. Otherwise a custom voice used by
            // the previous character can leak into narrator/default-character speech.
            val baseVoice = engine.voice
            val characterVoice = if (narrationSettings.enabled && narrationSettings.fullCastEnabled) {
                activeChar.voiceName?.let { name -> engine.voices?.find { it.name == name } }
            } else {
                null
            }
            (characterVoice ?: baseVoice)?.let { engine.voice = it }
        }
        if (effectiveRate != activeTtsRate) {
            tts?.setSpeechRate(effectiveRate)
            activeTtsRate = effectiveRate
        }
        if (effectivePitch != activeTtsPitch) {
            tts?.setPitch(effectivePitch)
            activeTtsPitch = effectivePitch
        }
        PlaybackStateStore.currentIndex = index
        PlaybackStateStore.isPlaying = true
        PlaybackStateStore.statusMessage = if (narrationSettings.enabled) {
            "Reading ${NarrationAnalyzer.labelFor(text, narrationSettings).lowercase()} sentence."
        } else {
            "Reading in background."
        }
        // Silence decorative glyphs (bullets, arrows, dot leaders). Replacements are
        // length-preserving, so word-highlight offsets from onRangeStart stay valid.
        val speakText = SpeechSanitizer.forSpeech(text)
        if (speakText.isBlank()) {
            // Pure decoration (a line of glyphs): treat it as already spoken.
            activeChunkIndex = index
            PlaybackStateStore.currentIndex = index
            clearResumePoint()
            advanceAfterSection()
            return
        }
        activeChunkUtteranceId = "$CHUNK_UTTERANCE_PREFIX${UUID.randomUUID()}"
        activeChunkIndex = index
        activeChunkStartedAt = System.currentTimeMillis()
        activeChunkSpeechText = rawChunkText
        activeChunkBaseOffset = resumeOffset
        spokenCharOffset = resumeOffset
        spokenWordCount = wordCountBefore(rawChunkText, resumeOffset)
        updateCurrentSentenceBounds(resumeOffset)
        PlaybackStateStore.queueCount = repository.loadQueueDocuments().size
        activeDocument?.let { repository.updateProgress(it.id, index, chunks.size) }
        updateMediaSessionMetadata()
        updateMediaSessionState()
        refreshForegroundNotification()
        if (VoiceManager.isVeritasEngine(activeEnginePackage)) {
            val veritasBuffer = veritasBufferFor(repository.loadVoiceSettings())
            veritasBuffer.prebufferAhead(chunks.size, index, 4, effectiveRate, effectivePitch) { chunkIndex ->
                SpeechSanitizer.forSpeech(repository.applyPronunciationRules(chunks[chunkIndex]))
            }
            veritasBuffer.playSentencePcm(index, speakText, effectiveRate, effectivePitch) { success ->
                if (success) {
                    advanceAfterSection()
                } else {
                    handleTtsFailure("Voice synthesis failed. Please download the voice in Voice Studio.", activeChunkUtteranceId)
                }
            }
            return
        }

        // Slide decks get a breathing beat at slide changes and after titles, so the
        // narration has a presenter's cadence instead of an unbroken stream.
        val silenceMs = leadingSilenceMsFor(index)
        val silenceQueued = silenceMs > 0L && tts?.playSilentUtterance(
            silenceMs, TextToSpeech.QUEUE_FLUSH, "silence-${UUID.randomUUID()}"
        ) == TextToSpeech.SUCCESS
        val queueMode = if (silenceQueued) TextToSpeech.QUEUE_ADD else TextToSpeech.QUEUE_FLUSH
        val result = tts?.speak(speakText, queueMode, ttsParams(activeChunkUtteranceId), activeChunkUtteranceId)
            ?: TextToSpeech.ERROR
        if (result == TextToSpeech.ERROR) {
            handleTtsFailure("The voice engine rejected this sentence.", activeChunkUtteranceId)
        } else if (silenceMs == 0L) {
            maybePrequeueNext(index)
        }
    }

    internal fun speakSelectionText(rawText: String) {
        val text = repository.applyPronunciationRules(rawText.trim())
        if (text.isBlank()) return
        val narrationSettings = repository.loadNarrationSettings()
        val effectiveRate = NarrationAnalyzer.effectiveRate(PlaybackStateStore.rate, narrationSettings, text)
        val effectivePitch = NarrationAnalyzer.effectivePitch(PlaybackStateStore.pitch, narrationSettings, text)
        if (VoiceManager.isVeritasEngine(activeEnginePackage)) {
            val speakText = SpeechSanitizer.forSpeech(text)
            if (speakText.isBlank()) return
            PlaybackStateStore.statusMessage = "Reading selected text."
            updateMediaSessionState()
            refreshForegroundNotification()
            veritasBufferFor(repository.loadVoiceSettings()).playSentencePcm(-1, speakText, effectiveRate, effectivePitch) { success ->
                if (success) {
                    PlaybackStateStore.statusMessage = "Finished selected text."
                } else {
                    handleTtsFailure("Voice synthesis failed. Please download the voice in Voice Studio.")
                }
                updateMediaSessionState()
                refreshForegroundNotification()
            }
            return
        }
        tts?.let { VoiceConfigurator.apply(it, repository.loadVoiceSettings()) }
        tts?.setSpeechRate(effectiveRate)
        tts?.setPitch(effectivePitch)
        PlaybackStateStore.statusMessage = "Reading selected text."
        updateMediaSessionState()
        refreshForegroundNotification()
        val utteranceId = "$SELECTION_UTTERANCE_PREFIX${UUID.randomUUID()}"
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, ttsParams(utteranceId), utteranceId) ?: TextToSpeech.ERROR
        if (result == TextToSpeech.ERROR) {
            handleTtsFailure("The voice engine rejected the selected text.", utteranceId)
        }
    }

    private fun advanceAfterSection() {
        if (!PlaybackStateStore.isPlaying || chunks.isEmpty()) return

        // Fallback check: Did the sleep timer expire during deep sleep?
        val now = System.currentTimeMillis()
        val timer = PlaybackStateStore.activeSleepTimerSnapshot(now)
        if (timer != null && !timer.stopAtEndOfSection && now >= timer.endsAtMillis) {
            fireSleepTimer()
            return
        }

        // Advance from the sentence we ACTUALLY just spoke (activeChunkIndex), not the
        // UI-shared PlaybackStateStore.currentIndex.
        val current = PlaybackAdvance.resolveCurrentIndex(
            activeChunkIndex = activeChunkIndex,
            sharedIndex = PlaybackStateStore.currentIndex,
            lastIndex = chunks.lastIndex
        )

        // Section sleep timer check
        if (timer != null && timer.stopAtEndOfSection) {
            val doc = activeDocument
            if (doc != null) {
                val rawText = repository.readText(doc)
                val readerModel = ReaderTextModelCache.get(doc.id, rawText, doc.pageCount)
                val currentPart = readerModel.partForSentence(current)
                if (currentPart != null && current == currentPart.sentenceEndIndexExclusive - 1) {
                    fireSleepTimer()
                    return
                }
            }
        }

        if (current < chunks.lastIndex) {
            PlaybackStateStore.currentIndex = current + 1
            activeDocument?.let { repository.updateProgress(it.id, PlaybackStateStore.currentIndex, chunks.size) }
            speakCurrent()
        } else {
            val completedId = activeDocument?.id
            activeDocument?.let { repository.updateProgress(it.id, current, chunks.size) }
            if (PlaybackStateStore.autoPlayQueue) {
                playNextQueuedOrFinish(completedId)
            } else {
                finishPlayback("Finished reading.")
            }
        }
    }

    private fun playNextQueuedOrFinish(completedDocumentId: String?) {
        val next = repository.completeCurrentAndGetNextQueued(completedDocumentId)
        PlaybackStateStore.queueCount = repository.loadQueueDocuments().size
        if (next == null) {
            finishPlayback("Finished reading. Queue is empty.")
            return
        }

        val startIndex = next.currentIndex.coerceAtLeast(0)
        if (!loadDocument(next.id, startIndex)) {
            finishPlayback("Finished reading. Could not open the next queued item.")
            return
        }

        PlaybackStateStore.isPlaying = true
        PlaybackStateStore.statusMessage = "Auto-playing next in queue."
        startForegroundNow()
        ensureTtsReadyAndSpeak()
    }

    private fun finishPlayback(message: String) {
        PlaybackStateStore.isPlaying = false
        if (PlaybackStateStore.isForegroundActive) {
            PlaybackStateStore.isForegroundActive = false
            stopForeground(STOP_FOREGROUND_DETACH)
        }
        PlaybackStateStore.statusMessage = message
        updateMediaSessionState()
        refreshForegroundNotification()
    }

    private fun moveBy(delta: Int) {
        if (chunks.isEmpty()) {
            val documentId = PlaybackStateStore.activeDocumentId ?: return
            if (!loadDocument(documentId, PlaybackStateStore.currentIndex)) return
        }

        if (delta > 0 && PlaybackStateStore.currentIndex >= chunks.lastIndex && PlaybackStateStore.autoPlayQueue) {
            playNextQueuedOrFinish(activeDocument?.id)
            return
        }

        val newIndex = (PlaybackStateStore.currentIndex + delta).coerceIn(0, chunks.lastIndex)
        clearResumePoint()
        PlaybackStateStore.currentIndex = newIndex
        activeDocument?.let { repository.updateProgress(it.id, newIndex, chunks.size) }
        if (PlaybackStateStore.isPlaying) {
            speakCurrent()
        } else {
            refreshForegroundNotification()
        }
    }

    internal fun pauseSpeech(message: String = "Paused.") {
        pausedDueToTransientFocusLoss = false
        rememberPausePoint()
        tts?.stop()
        veritasAudioBuffer?.flush()
        PlaybackStateStore.isPlaying = false
        if (PlaybackStateStore.isForegroundActive) {
            PlaybackStateStore.isForegroundActive = false
            stopForeground(STOP_FOREGROUND_DETACH)
        }
        PlaybackStateStore.statusMessage = message
        activeDocument?.let { repository.updateProgress(it.id, PlaybackStateStore.currentIndex, chunks.size) }
        updateMediaSessionState()
        refreshForegroundNotification()
        abandonAudioFocus()
    }

    internal fun pauseSpeechTransiently(message: String) {
        rememberPausePoint()
        tts?.stop()
        veritasAudioBuffer?.flush()
        PlaybackStateStore.isPlaying = false
        // Keep foreground service active with a paused notification during transient interruptions
        // so that when audio focus returns (AUDIOFOCUS_GAIN), resuming playback does not trigger
        // ForegroundServiceStartNotAllowedException on Android 12+/14+.
        PlaybackStateStore.statusMessage = message
        activeDocument?.let { repository.updateProgress(it.id, PlaybackStateStore.currentIndex, chunks.size) }
        updateMediaSessionState()
        refreshForegroundNotification()
        // Do NOT call abandonAudioFocus() here
    }

    internal fun stopSpeechAndService(message: String = "Stopped.") {
        pausedDueToTransientFocusLoss = false
        pendingSpeak = false
        pendingSelectionText = null
        cancelSleepTimerCallback()
        PlaybackStateStore.clearSleepTimer()
        repository.clearSleepTimerState()
        clearResumePoint()
        tts?.stop()
        veritasAudioBuffer?.flush()
        activeDocument?.let { repository.updateProgress(it.id, PlaybackStateStore.currentIndex, chunks.size) }
        PlaybackStateStore.isPlaying = false
        PlaybackStateStore.isForegroundActive = false
        PlaybackStateStore.statusMessage = message
        updateMediaSessionState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        abandonAudioFocus()
        stopSelf()
    }

    override fun onDestroy() {
        serviceJob.cancel()
        pendingSpeak = false
        pendingSelectionText = null
        cancelSleepTimerCallback()
        tts?.stop()
        tts?.shutdown()
        veritasAudioBuffer?.shutdown()
        veritasAudioBuffer = null
        tts = null
        ttsReady = false
        mediaSession?.release()
        mediaSession = null
        mediaSessionPlayer?.release()
        mediaSessionPlayer = null
        abandonAudioFocus()
        super.onDestroy()
    }

    companion object {
        internal const val TAG = "PlaybackService"
        internal const val CHANNEL_ID = "reader_playback"
        internal const val NOTIFICATION_ID = 41
        internal const val SELECTION_UTTERANCE_PREFIX = "selection:"
        internal const val CHUNK_UTTERANCE_PREFIX = "chunk:"
        // Slide-deck cadence: a beat when a new slide starts, a shorter one after
        // its title line. Snappy on purpose — the user asked for rhythm, not lag.
        internal const val SLIDE_TRANSITION_SILENCE_MS = 300L
        internal const val TITLE_BEAT_SILENCE_MS = 250L
        internal const val RESUME_WORD_THRESHOLD = 8
        internal const val RESUME_SKIP_CHARS = ".,;:!?)]}\"'»›—–-"

        /**
         * Silence to queue ahead of the chunk at [index], given the slide each chunk
         * belongs to. Pure so the cadence can be checked without a running service:
         * a full beat when the slide changes, a shorter one after the title line,
         * nothing thereafter. A null [pages] means the document is not a deck.
         */
        fun leadingSilenceMs(pages: IntArray?, index: Int): Long {
            if (pages == null || index !in pages.indices) return 0L
            val page = pages[index]
            val firstOfSlide = index == 0 || pages[index - 1] != page
            if (firstOfSlide) return SLIDE_TRANSITION_SILENCE_MS
            val secondOfSlide = index == 1 || pages[index - 2] != page
            if (secondOfSlide) return TITLE_BEAT_SILENCE_MS
            return 0L
        }
    }
}
