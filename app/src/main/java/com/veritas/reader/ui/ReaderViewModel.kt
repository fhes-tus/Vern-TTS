package com.veritas.reader.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.veritas.reader.*
import com.veritas.reader.ui.screens.VeritasHomeTab
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    internal val repository = DocumentRepository(application)
    internal val delegateUiState = MutableStateFlow(
        run {
            val hasCompleted = repository.hasSeenOnboardingTutorial()
            val questProgress = repository.loadQuestProgress()
            val allQuestsDone = questProgress.tourDone && questProgress.importDone && questProgress.speedDone && questProgress.bookmarkDone
            val savedReaderSettings = repository.loadReaderSettings()
            val savedVoiceSettings = repository.loadVoiceSettings()
            val savedNarrationSettings = repository.loadNarrationSettings()
            val savedAskAiSettings = repository.loadAskAiSettings()
            val savedUserName = repository.loadUserName()
            val savedReadingInterest = repository.loadReadingInterest()
            val savedReadingTimes = repository.loadDocReadingTimes()
            val savedFlashcards = repository.loadAllFlashcards()
            val savedQuizzes = repository.loadAllQuizzes()
            ReaderUiState(
                readerSettings = savedReaderSettings,
                voiceSettings = savedVoiceSettings,
                narrationSettings = savedNarrationSettings,
                askAiSettings = savedAskAiSettings,
                userName = savedUserName,
                readingInterest = savedReadingInterest,
                documentReadingTimes = savedReadingTimes,
                flashcards = savedFlashcards,
                quizzes = savedQuizzes,
                hasCompletedOnboarding = hasCompleted,
                questTourDone = questProgress.tourDone,
                questImportDone = questProgress.importDone,
                questSpeedDone = questProgress.speedDone,
                questBookmarkDone = questProgress.bookmarkDone,
                questChecklistDismissed = allQuestsDone || repository.isQuestChecklistDismissed(),
                dismissedHeroDocId = repository.getDismissedHeroDocId(),
                showTutorial = !hasCompleted
            )
        }
    )
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.InternalCoroutinesApi::class)
    internal val _uiState = object : MutableStateFlow<ReaderUiState> by delegateUiState {
        override var value: ReaderUiState
            get() = delegateUiState.value
            set(newVal) {
                delegateUiState.value = synchronizeNavStack(delegateUiState.value, newVal)
            }

        override fun tryEmit(value: ReaderUiState): Boolean {
            val synced = synchronizeNavStack(delegateUiState.value, value)
            return delegateUiState.tryEmit(synced)
        }

        override suspend fun emit(value: ReaderUiState) {
            val synced = synchronizeNavStack(delegateUiState.value, value)
            delegateUiState.emit(synced)
        }

        override fun compareAndSet(expect: ReaderUiState, update: ReaderUiState): Boolean {
            val synced = synchronizeNavStack(expect, update)
            return delegateUiState.compareAndSet(expect, synced)
        }
    }
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    fun updateState(block: (ReaderUiState) -> ReaderUiState) {
        _uiState.update(block)
    }

    internal var importJob: Job? = null
    internal var exportJob: Job? = null
    internal var backupJob: Job? = null
    internal var outlineJob: Job? = null
    internal var voiceJob: Job? = null
    internal var scanJob: Job? = null
    internal var downloadJob: Job? = null
    internal var sleepTimerJob: Job? = null
    internal var appSessionStartedAt: Long = 0L
    internal var activeDocStartedAt: Long = 0L

    init {
        viewModelScope.launch(Dispatchers.IO) {
            checkForUpdates()
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val currentVersion = runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull() ?: ""
                val prefs = context.getSharedPreferences("veritas_reader_library", android.content.Context.MODE_PRIVATE)
                val lastRunVersion = prefs.getString("last_run_version_name", "") ?: ""
                
                if (currentVersion.isNotEmpty() && lastRunVersion.isNotEmpty() && currentVersion != lastRunVersion) {
                    val savedChangelog = prefs.getString("last_downloaded_changelog", "") ?: ""
                    if (savedChangelog.isNotEmpty()) {
                        _uiState.update {
                            it.copy(
                                showReleaseNotesDialog = true,
                                releaseNotesChangelog = savedChangelog,
                                releaseNotesVersionName = currentVersion
                            )
                        }
                        prefs.edit().remove("last_downloaded_changelog").apply()
                    }
                }
                
                if (currentVersion.isNotEmpty() && currentVersion != lastRunVersion) {
                    prefs.edit().putString("last_run_version_name", currentVersion).apply()
                }
            } catch (e: Exception) {
                android.util.Log.e("ReaderViewModel", "Error checking for post-update release notes", e)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val trackerSnapshot = repository.recordAppOpen()
            var documents = repository.loadDocuments()
            val documentReadingTimes = repository.loadDocReadingTimes()
            val queuedDocuments = repository.loadQueueDocuments()
            val pronunciationRules = repository.loadPronunciationRules()
            val voiceSettings = repository.loadVoiceSettings()
            val narrationSettings = repository.loadNarrationSettings()
            val readerSettings = repository.loadReaderSettings()
            val askAiSettings = repository.loadAskAiSettings()
            val aiPromptTemplates = repository.loadAiPromptTemplates()
            val aiPromptHistory = repository.loadAiPromptHistory()
            val readingListCatalog = repository.loadReadingListCatalog()
            val readingHistory = repository.loadReadingHistory()
            val allAnnotations = repository.loadAllAnnotations()
            val documentNotes = repository.loadAllDocumentNotes()
            val documentTitles = repository.loadAllDocumentTitles()
            val annotationCount = repository.loadAnnotationCount()
            val fileBrowserRoots = VeritasFileBrowserScanner.persistedRoots(application)
            val generalNotes = repository.loadGeneralNotes()
            val flashcards = repository.loadAllFlashcards()
            val quizzes = repository.loadAllQuizzes()
            val userName = repository.loadUserName()
            val readingInterest = repository.loadReadingInterest()
            val hasCompletedOnboarding = repository.hasSeenOnboardingTutorial()
            val hasImportedOrOpenedDocument = repository.hasImportedOrOpenedDocument()
            val questProgress = repository.loadQuestProgress()
            val hasCheeseDoc = documents.any { it.title.contains("Who Moved My Cheese", ignoreCase = true) }
            if (!hasCheeseDoc) {
                val cheeseText = runCatching {
                    application.assets.open("books/who_moved_my_cheese.txt").bufferedReader().use { it.readText() }
                }.getOrNull()
                if (!cheeseText.isNullOrBlank()) {
                    val cheeseDoc = repository.createDocument(
                        title = "Who Moved My Cheese?",
                        text = cheeseText,
                        sourceLabel = "Spencer Johnson, M.D.",
                        originalDisplayName = "Who Moved My Cheese? - Spencer Johnson, M.D."
                    )
                    runCatching {
                        application.assets.open("covers/who_moved_my_cheese.jpg").use { input ->
                            val coversDir = CoverExtractor.coversDir(application)
                            val coverFile = File(coversDir, "${cheeseDoc.id}.cover.jpg")
                            coverFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
            if (documents.isEmpty()) {
                repository.createDocument(
                    title = "Vern Welcome Guide",
                    text = "Welcome to Vern! This is a sample document designed to help you explore the reading environment. Vern lets you convert research papers, textbooks, EPUBs, docx files, web articles, and images into high-quality spoken audio. Long-press any sentence in this guide to try highlighting, bookmarking, adding study notes, or asking the AI Assistant a question. Adjust the voice speed or select premium voices in the expandable player panel below. Toggle different layout modes like TEXT for clean reading or LISTEN to follow along sentence-by-sentence. Enjoy your reading journey!",
                    sourceLabel = "System"
                )
            }
            if (!hasCheeseDoc || documents.isEmpty()) {
                documents = repository.loadDocuments()
            }

            // Immediately emit library state so the user sees their documents, notes, and settings instantly
            _uiState.update {
                it.copy(
                    documents = documents,
                    generalNotes = generalNotes,
                    queuedDocuments = queuedDocuments,
                    pronunciationRules = pronunciationRules,
                    voiceSettings = voiceSettings,
                    narrationSettings = narrationSettings,
                    readerSettings = readerSettings,
                    askAiSettings = askAiSettings,
                    aiPromptTemplates = aiPromptTemplates,
                    aiPromptHistory = aiPromptHistory,
                    readingListCatalog = readingListCatalog,
                    readingHistory = readingHistory,
                    allAnnotations = allAnnotations,
                    documentNotes = documentNotes,
                    documentTitles = documentTitles,
                    flashcards = flashcards,
                    quizzes = quizzes,
                    annotationCount = annotationCount,
                    fileBrowserRoots = fileBrowserRoots,
                    fileBrowserAllFilesGranted = hasAllFilesAccess(),
                    userName = userName,
                    readingInterest = readingInterest,
                    hasCompletedOnboarding = hasCompletedOnboarding,
                    hasImportedOrOpenedDocument = hasImportedOrOpenedDocument,
                    questTourDone = questProgress.tourDone,
                    questImportDone = questProgress.importDone,
                    questSpeedDone = questProgress.speedDone,
                    questBookmarkDone = questProgress.bookmarkDone,
                    readerTrackerSnapshot = trackerSnapshot,
                    documentReadingTimes = documentReadingTimes,
                    showTutorial = !hasCompletedOnboarding
                )
            }

            // Repair missing covers for existing files in the background without blocking UI
            documents.forEach { doc ->
                if (CoverExtractor.coverFile(application, doc.id) == null) {
                    val classic = com.veritas.reader.ui.screens.CURATED_CLASSICS.firstOrNull { c ->
                        doc.title.contains(c.title, ignoreCase = true) ||
                        (doc.originalFileName.isNotBlank() && doc.originalFileName.contains(c.id, ignoreCase = true))
                    }
                    if (classic != null) {
                        runCatching {
                            application.assets.open("covers/${classic.id}.jpg").use { input ->
                                val coversDir = CoverExtractor.coversDir(application)
                                val coverFile = File(coversDir, "${doc.id}.cover.jpg")
                                coverFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    } else if (doc.title.contains("Who Moved My Cheese", ignoreCase = true)) {
                        runCatching {
                            application.assets.open("covers/who_moved_my_cheese.jpg").use { input ->
                                val coversDir = CoverExtractor.coversDir(application)
                                val coverFile = File(coversDir, "${doc.id}.cover.jpg")
                                coverFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                }
                if (doc.originalFileName.isNotBlank() && CoverExtractor.coverFile(application, doc.id) == null) {
                    val originalFile = repository.originalFile(doc)
                    if (originalFile != null && originalFile.exists()) {
                        CoverExtractor.extractCoverFromFile(application, doc.id, originalFile)
                    } else if (doc.originalFileName.startsWith("content://")) {
                        runCatching {
                            val uri = Uri.parse(doc.originalFileName)
                            val mimeType = application.contentResolver.getType(uri).orEmpty().lowercase(
                                Locale.getDefault())
                            val isPdf = mimeType.contains("pdf") || doc.originalFileName.lowercase(
                                Locale.getDefault()).contains(".pdf")
                            val isEpub = mimeType.contains("epub") || doc.originalFileName.lowercase(
                                Locale.getDefault()).contains(".epub")
                            val isImage = mimeType.startsWith("image/")
                            when {
                                isPdf -> CoverExtractor.extractPdfCover(application, doc.id, uri)
                                isEpub -> CoverExtractor.extractEpubCover(application, doc.id, uri)
                                isImage -> CoverExtractor.extractImageCover(application, doc.id, uri)
                            }
                        }
                    }
                }
            }
        }
        viewModelScope.launch {
            val savedSleepTimer = repository.loadPersistedSleepTimer()
            if (savedSleepTimer != null && (savedSleepTimer.stopAtEndOfSection || savedSleepTimer.remainingMillis() > 0L)) {
                PlaybackStateStore.setSleepTimer(
                    VeritasSleepTimerRequest(
                        durationMillis = savedSleepTimer.durationMillis,
                        action = savedSleepTimer.action,
                        stopAtEndOfSection = savedSleepTimer.stopAtEndOfSection
                    ),
                    nowMillis = savedSleepTimer.endsAtMillis - savedSleepTimer.durationMillis
                )
                startSleepTimerTicker()
            }
        }
        viewModelScope.launch {
            androidx.compose.runtime.snapshotFlow { PlaybackStateStore.activeDocumentId }
                .collect { newDocId ->
                    val currentId = uiState.value.activeDocument?.id
                    if (newDocId != null && currentId != newDocId) {
                        val saved = repository.findDocument(newDocId)
                        if (saved != null) {
                            withContext(Dispatchers.Main) {
                                openSavedDocument(saved, PlaybackStateStore.currentIndex)
                            }
                        }
                    }
                }
        }
    }

    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                getApplication(),
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestNotificationPermissionForPlayback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || uiState.value.notificationPermissionRequested) return
        val alreadyGranted = ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!alreadyGranted) {
            _uiState.update { it.copy(notificationPermissionRequested = true) }
        }
    }

    fun refreshAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val docs = repository.loadDocuments()
            val queue = repository.loadQueueDocuments()
            val tracker = repository.loadReaderTrackerSnapshot()
            val generalNotes = repository.loadGeneralNotes()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(documents = docs, queuedDocuments = queue, readerTrackerSnapshot = tracker, generalNotes = generalNotes) }
                refreshAnnotationCatalog()
                PlaybackStateStore.queueCount = queue.size
            }
        }
    }

    fun onAppForegrounded() {
        PlaybackStateStore.appInForeground = true
        appSessionStartedAt = System.currentTimeMillis()
        startActiveDocSessionTime()
        viewModelScope.launch(Dispatchers.IO) {
            val tracker = repository.recordAppOpen(appSessionStartedAt)
            // Pick up everything the PlaybackService wrote while we were backgrounded:
            // accrued reading time AND per-document progress (currentIndex). Without the
            // documents reload, the home hero card kept showing stale progress until a
            // full process restart.
            val readingTimes = repository.loadDocReadingTimes()
            val docs = repository.loadDocuments()
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        readerTrackerSnapshot = tracker,
                        documentReadingTimes = readingTimes,
                        documents = docs
                    )
                }
                updateVeritasWidgets(getApplication())
            }
        }
    }

    fun onAppBackgrounded() {
        PlaybackStateStore.appInForeground = false
        recordActiveDocSessionTime()
        val doc = uiState.value.activeDocument
        val docId = doc?.id
        if (doc != null && docId != null) {
            val currentIndex = PlaybackStateStore.currentIndex
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateProgress(docId, currentIndex, doc.chunks.size)
            }
        }
        val startedAt = appSessionStartedAt
        if (startedAt <= 0L) return
        appSessionStartedAt = 0L
        val endedAt = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            val tracker = repository.recordUsageDuration(endedAt - startedAt, endedAt)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(readerTrackerSnapshot = tracker) }
            }
        }
    }

    private fun recordActiveDocSessionTime() {
        val docId = uiState.value.activeDocument?.id
        val startedAt = activeDocStartedAt
        if (docId != null && startedAt > 0L) {
            val duration = System.currentTimeMillis() - startedAt
            activeDocStartedAt = 0L
            if (duration > 0L) {
                viewModelScope.launch(Dispatchers.IO) {
                    repository.recordDocReadingTime(docId, duration)
                    val updatedTimes = repository.loadDocReadingTimes()
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(documentReadingTimes = updatedTimes) }
                    }
                }
            }
        }
    }

    private fun startActiveDocSessionTime() {
        val docId = uiState.value.activeDocument?.id
        if (docId != null) {
            activeDocStartedAt = System.currentTimeMillis()
        }
    }

    private fun refreshAnnotationCatalog() {
        val annotations = repository.loadAllAnnotations()
        val documentNotes = repository.loadAllDocumentNotes()
        val documentTitles = repository.loadAllDocumentTitles()
        _uiState.update {
            it.copy(
                allAnnotations = annotations,
                documentNotes = documentNotes,
                documentTitles = documentTitles,
                annotationCount = annotations.size + documentNotes.size
            )
        }
    }

    fun refreshAnnotations() {
        val docId = uiState.value.activeDocument?.id
        if (docId.isNullOrBlank()) {
            _uiState.update { it.copy(annotations = emptyList()) }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val loaded = repository.loadAnnotations(docId)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(annotations = loaded) }
                }
            }
        }
    }

    fun stopServicePlayback() {
        if (PlaybackStateStore.isForegroundActive || PlaybackStateStore.isPlaying) {
            sendPlaybackIntent(getApplication(), PlaybackActions.ACTION_STOP)
        }
    }

    fun stopAndForgetPlayback(message: String = "Stopped.") {
        stopServicePlayback()
        PlaybackStateStore.reset()
        PlaybackStateStore.queueCount = uiState.value.queuedDocuments.size
        PlaybackStateStore.statusMessage = message
    }

    fun stopPlaybackIfDocumentsRemoved(documentIds: Set<String>) {
        val activeId = uiState.value.activeDocument?.id
        val serviceId = PlaybackStateStore.activeDocumentId
        if ((activeId != null && activeId in documentIds) || (serviceId != null && serviceId in documentIds)) {
            stopAndForgetPlayback("Reading removed.")
            recordActiveDocSessionTime()
            _uiState.update { it.copy(activeDocument = null, annotations = emptyList(), documentNoteDraft = "", showCanvasView = false) }
        }
    }

    fun clearContinueReading(document: SavedDocument) {
        if (PlaybackStateStore.activeDocumentId == document.id && PlaybackStateStore.isPlaying) {
            stopAndForgetPlayback("Reading dismissed.")
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.setDismissedHeroDocId(document.id)
            val queue = repository.loadQueueDocuments()
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        dismissedHeroDocId = document.id,
                        queuedDocuments = queue,
                        importMessage = "Removed ${document.title} from Continue reading."
                    )
                }
                PlaybackStateStore.queueCount = queue.size
            }
        }
    }

    fun persistProgress(index: Int) {
        val doc = uiState.value.activeDocument ?: return
        val docId = doc.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val docs = repository.updateProgress(docId, index, doc.chunks.size)
            val updatedDoc = docs.firstOrNull { it.id == docId }
            val tracker = updatedDoc?.let { repository.recordDocumentProgress(it) }
                ?: repository.loadReaderTrackerSnapshot()
            val history = updatedDoc?.let { repository.addReadingHistory(it, index) }
                ?: repository.loadReadingHistory()
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(documents = docs, readerTrackerSnapshot = tracker, readingHistory = history) }
            }
        }
    }

    fun syncPlaybackStateForDocument(readerDocument: ReaderDocument, startIndex: Int) {
        val liveSameDocument = PlaybackStateStore.isPlaying &&
            PlaybackStateStore.activeDocumentId == readerDocument.id
        if (liveSameDocument) {
            // The service is actively reading this document; its position is the source
            // of truth. Overwriting it here would yank playback to a stale index.
            return
        }
        if (PlaybackStateStore.isPlaying) {
            // A different document is being read aloud; pause it before this one takes
            // over the shared playback state, otherwise the service keeps mutating it.
            sendPlaybackIntent(getApplication(), PlaybackActions.ACTION_PAUSE)
        }
        val safeIndex = if (readerDocument.chunks.isEmpty()) 0 else startIndex.coerceIn(0, readerDocument.chunks.lastIndex)
        PlaybackStateStore.activeDocumentId = readerDocument.id
        PlaybackStateStore.documentTitle = readerDocument.title
        PlaybackStateStore.sourceLabel = readerDocument.sourceLabel
        PlaybackStateStore.currentIndex = safeIndex
        PlaybackStateStore.chunkCount = readerDocument.chunks.size
        PlaybackStateStore.statusMessage = "Ready."
        PlaybackStateStore.queueCount = uiState.value.queuedDocuments.size
    }

    internal suspend fun loadReaderDocument(metadata: SavedDocument): ReaderDocument = withContext(Dispatchers.IO) {
        val rawText = repository.readText(metadata)
        val repairedText = DocumentTextRepairer.repairPseudoTables(rawText)
        if (repairedText != rawText) {
            runCatching { repository.updateDocumentText(metadata.id, repairedText) }
        }
        buildReaderDocument(metadata, repairedText)
    }

    fun openSavedDocument(metadata: SavedDocument, startIndex: Int? = null) {
        val currentActive = _uiState.value.activeDocument
        val currentActiveId = currentActive?.id
        if (currentActive != null && currentActiveId != null && currentActiveId != metadata.id) {
            recordActiveDocSessionTime()
            val lastIndex = PlaybackStateStore.currentIndex
            val totalChunks = currentActive.chunks.size
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateProgress(currentActiveId, lastIndex, totalChunks)
            }
        }
        if (metadata.id == repository.getDismissedHeroDocId()) {
            repository.setDismissedHeroDocId(null)
            _uiState.update { it.copy(dismissedHeroDocId = null) }
        }
        _uiState.update { it.copy(isOpeningDocument = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val latestMetadata = repository.findDocument(metadata.id) ?: metadata
            val readerDocument = loadReaderDocument(latestMetadata)
            
            if (latestMetadata.language.isNotBlank()) {
                val currentVoiceSettings = repository.loadVoiceSettings()
                // Only realign the locale for the SYSTEM DEFAULT voice. If the user has
                // explicitly chosen a voice (voiceName set), that voice carries its own
                // locale and must be preserved — previously we wiped voiceName here, which
                // reset the chosen voice to default on almost every document open.
                if (currentVoiceSettings.voiceName.isBlank() &&
                    currentVoiceSettings.localeTag != latestMetadata.language) {
                    val updated = currentVoiceSettings.copy(localeTag = latestMetadata.language)
                    repository.saveVoiceSettings(updated)
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(voiceSettings = updated) }
                    }
                }
            }

            val annotations = repository.loadAnnotations(latestMetadata.id)
            val documentNote = repository.loadDocumentNote(latestMetadata.id)
            val targetIndex = startIndex 
                ?: (if (PlaybackStateStore.activeDocumentId == latestMetadata.id) PlaybackStateStore.currentIndex else latestMetadata.currentIndex)
            repository.markImportedOrOpenedDocument()
            val tracker = repository.recordDocumentRead(latestMetadata.id, latestMetadata.title)
            val history = repository.addReadingHistory(latestMetadata, targetIndex)

            // Pre-warm initial page images into cache so the first frame renders instantly with images
            launch(Dispatchers.IO) {
                runCatching {
                    val model = ReaderTextModelCache.get(latestMetadata.id, readerDocument.rawText, readerDocument.pageCount)
                    val startPage = model.sentences.getOrNull(targetIndex)?.pageNumber ?: 1
                    DocumentPageImageLoader.loadPageImages(getApplication(), repository, latestMetadata.id, startPage)
                    kotlinx.coroutines.delay(1200L)
                    if (startPage > 1) {
                        DocumentPageImageLoader.loadPageImages(getApplication(), repository, latestMetadata.id, startPage - 1)
                    }
                    DocumentPageImageLoader.loadPageImages(getApplication(), repository, latestMetadata.id, startPage + 1)
                }
            }

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        activeDocument = readerDocument,
                        annotations = annotations,
                        documentNoteDraft = documentNote,
                        // Filled in by loadOutlineInBackground once PDFBox has parsed the
                        // file; the reader must not wait on it.
                        documentOutline = emptyList(),
                        searchQuery = "",
                        searchMatches = emptyList(),
                        searchCursor = 0,
                        showCanvasView = false,
                        hasImportedOrOpenedDocument = true,
                        readerTrackerSnapshot = tracker,
                        readingHistory = history,
                        isOpeningDocument = false
                    )
                }
                startActiveDocSessionTime()
                syncPlaybackStateForDocument(readerDocument, targetIndex)
            }
            loadOutlineInBackground(latestMetadata, readerDocument.chunks)
        }
    }

    /**
     * Reads a PDF's embedded outline off the critical path.
     *
     * PDFBox has to parse the whole file to reach its bookmarks — on a 1,600-page book
     * that is tens of seconds — and this used to run before `activeDocument` was set,
     * so opening any large PDF blocked for the entire parse. The reader now appears at
     * once and the outline populates when it is ready; until then the Outline sheet
     * falls back to its heuristics, exactly as it does for files with no bookmarks.
     */
    private fun loadOutlineInBackground(document: SavedDocument, chunks: List<String>) {
        outlineJob?.cancel()
        outlineJob = viewModelScope.launch(Dispatchers.IO) {
            val outline = runCatching { repository.loadDocumentOutline(document, chunks) }
                .getOrDefault(emptyList())
            if (outline.isEmpty()) return@launch
            withContext(Dispatchers.Main) {
                // Ignore a late result for a document the user has already left.
                if (_uiState.value.activeDocument?.id == document.id) {
                    _uiState.update { it.copy(documentOutline = outline) }
                }
            }
        }
    }

    fun moveTo(
        index: Int,
        autoPlay: Boolean = PlaybackStateStore.isPlaying,
        forcePlaybackStart: Boolean = false
    ) {
        val doc = uiState.value.activeDocument ?: return
        if (doc.chunks.isEmpty()) return
        val safeIndex = index.coerceIn(0, doc.chunks.lastIndex)
        PlaybackStateStore.currentIndex = safeIndex
        persistProgress(safeIndex)
        val matches = uiState.value.searchMatches
        val matchIndex = matches.indexOf(safeIndex)
        if (matchIndex >= 0) {
            _uiState.update { it.copy(searchCursor = matchIndex) }
        }
        // While the service is actively speaking, every index move must be sent to it.
        // Otherwise the service finishes its current sentence and then advances from the
        // mutated shared index, speaking the wrong sentence.
        val notifyService = autoPlay || PlaybackStateStore.isPlaying
        if (notifyService && doc.id != null) {
            if (forcePlaybackStart) requestNotificationPermissionForPlayback()
            sendPlaybackIntent(
                context = getApplication(),
                action = if (forcePlaybackStart) PlaybackActions.ACTION_PLAY else PlaybackActions.ACTION_JUMP_TO,
                documentId = doc.id,
                startIndex = safeIndex
            )
        }
    }

    fun playOrPause() {
        val doc = uiState.value.activeDocument ?: return
        val docId = doc.id ?: return
        if (PlaybackStateStore.isPlaying) {
            sendPlaybackIntent(getApplication(), PlaybackActions.ACTION_PAUSE)
        } else {
            requestNotificationPermissionForPlayback()
            sendPlaybackIntent(
                context = getApplication(),
                action = PlaybackActions.ACTION_PLAY,
                documentId = docId,
                startIndex = PlaybackStateStore.currentIndex
            )
        }
    }

    fun playOrPauseSavedDocument(metadata: SavedDocument) {
        if (PlaybackStateStore.activeDocumentId == metadata.id) {
            if (PlaybackStateStore.isPlaying) {
                sendPlaybackIntent(getApplication(), PlaybackActions.ACTION_PAUSE)
            } else {
                requestNotificationPermissionForPlayback()
                sendPlaybackIntent(
                    context = getApplication(),
                    action = PlaybackActions.ACTION_PLAY,
                    documentId = metadata.id,
                    startIndex = PlaybackStateStore.currentIndex
                )
            }
        } else {
            requestNotificationPermissionForPlayback()
            sendPlaybackIntent(
                context = getApplication(),
                action = PlaybackActions.ACTION_PLAY,
                documentId = metadata.id,
                startIndex = metadata.currentIndex
            )
        }
    }

    fun playQueue() {
        viewModelScope.launch(Dispatchers.IO) {
            val first = repository.loadQueueDocuments().firstOrNull() ?: return@launch
            val readerDocument = loadReaderDocument(first)
            val annotations = repository.loadAnnotations(first.id)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(activeDocument = readerDocument, annotations = annotations) }
                syncPlaybackStateForDocument(readerDocument, first.currentIndex)
                requestNotificationPermissionForPlayback()
                sendPlaybackIntent(
                    context = getApplication(),
                    action = PlaybackActions.ACTION_PLAY,
                    documentId = first.id,
                    startIndex = first.currentIndex
                )
            }
        }
    }

    fun openNextQueuedAfterCurrent(autoPlay: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val completedId = uiState.value.activeDocument?.id
            val next = repository.completeCurrentAndGetNextQueued(completedId) ?: return@launch
            val readerDocument = loadReaderDocument(next)
            val annotations = repository.loadAnnotations(next.id)
            withContext(Dispatchers.Main) {
                refreshAll()
                _uiState.update { it.copy(activeDocument = readerDocument, annotations = annotations) }
                syncPlaybackStateForDocument(readerDocument, next.currentIndex)
                if (autoPlay) {
                    requestNotificationPermissionForPlayback()
                    sendPlaybackIntent(
                        context = getApplication(),
                        action = PlaybackActions.ACTION_PLAY,
                        documentId = next.id,
                        startIndex = next.currentIndex
                    )
                }
            }
        }
    }

    fun goToNextSectionOrQueuedDocument() {
        val doc = uiState.value.activeDocument ?: return
        val atLastSection = doc.chunks.isNotEmpty() && PlaybackStateStore.currentIndex >= doc.chunks.lastIndex
        if (!atLastSection) {
            moveTo(PlaybackStateStore.currentIndex + 1, autoPlay = PlaybackStateStore.isPlaying)
            return
        }

        if (uiState.value.readerSettings.autoPlayQueue && uiState.value.queuedDocuments.isNotEmpty()) {
            if (PlaybackStateStore.isPlaying) {
                sendPlaybackIntent(getApplication(), PlaybackActions.ACTION_NEXT)
            } else {
                openNextQueuedAfterCurrent(autoPlay = false)
            }
        }
    }

    fun returnToLibrary() {
        val doc = uiState.value.activeDocument
        val docId = doc?.id
        val currentIndex = PlaybackStateStore.currentIndex
        PlaybackStateStore.readerMode = ReaderMode.TEXT
        recordActiveDocSessionTime()
        _uiState.update {
            it.copy(
                activeDocument = null,
                annotations = emptyList(),
                documentOutline = emptyList(),
                documentNoteDraft = "",
                searchQuery = "",
                searchCursor = 0
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            if (doc != null && docId != null) {
                repository.updateProgress(docId, currentIndex, doc.chunks.size)
            }
            val docs = repository.loadDocuments()
            val queue = repository.loadQueueDocuments()
            val tracker = repository.loadReaderTrackerSnapshot()
            val generalNotes = repository.loadGeneralNotes()
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        documents = docs,
                        queuedDocuments = queue,
                        readerTrackerSnapshot = tracker,
                        generalNotes = generalNotes
                    )
                }
                refreshAnnotationCatalog()
                PlaybackStateStore.queueCount = queue.size
            }
        }
    }

    fun navigateToHomeTab(tab: VeritasHomeTab) {
        returnToLibrary()
        _uiState.update { it.copy(targetHomeTab = tab) }
    }

    fun clearTargetHomeTab() {
        _uiState.update { it.copy(targetHomeTab = null) }
    }

    private fun synchronizeNavStack(old: ReaderUiState, next: ReaderUiState): ReaderUiState {
        var newStack = next.navStack
        val mappings = listOf(
            VeritasScreen.TEXT_EDITOR to { state: ReaderUiState -> state.showTextEditor },
            VeritasScreen.FILE_BROWSER to { state: ReaderUiState -> state.showFileBrowser },
            VeritasScreen.PDF_IMPORT_TOOLS to { state: ReaderUiState -> state.showPdfImportTools },
            VeritasScreen.READER_SETTINGS to { state: ReaderUiState -> state.showReaderSettings },
            VeritasScreen.PRONUNCIATION_RULES to { state: ReaderUiState -> state.showPronunciationRules },
            VeritasScreen.VOICE_STUDIO to { state: ReaderUiState -> state.showVoiceStudio },
            VeritasScreen.NARRATION_STUDIO to { state: ReaderUiState -> state.showNarrationStudio },
            VeritasScreen.AI_STUDY_TOOLS to { state: ReaderUiState -> state.showAiStudyTools },
            VeritasScreen.AI_CENTER to { state: ReaderUiState -> state.showAiCenter },
            VeritasScreen.ASK_AI_SETTINGS to { state: ReaderUiState -> state.showAskAiSettings },
            VeritasScreen.TRANSLATION_TOOLS to { state: ReaderUiState -> state.showTranslationTools },
            VeritasScreen.SLEEP_TIMER to { state: ReaderUiState -> state.showSleepTimerDialog },
            VeritasScreen.READING_LISTS to { state: ReaderUiState -> state.showReadingLists },
            VeritasScreen.READING_HISTORY to { state: ReaderUiState -> state.showReadingHistory },
            VeritasScreen.DOCUMENT_NOTES to { state: ReaderUiState -> state.showDocumentNotes },
            VeritasScreen.SETTINGS_HUB to { state: ReaderUiState -> state.showSettingsHub },
            VeritasScreen.BACKUP_TOOLS to { state: ReaderUiState -> state.showBackupTools },
            VeritasScreen.SYNC_CENTER to { state: ReaderUiState -> state.showSyncCenter },
            VeritasScreen.APP_HEALTH to { state: ReaderUiState -> state.showAppHealth },
            VeritasScreen.TUTORIAL to { state: ReaderUiState -> state.showTutorial },
            VeritasScreen.CANVAS_VIEW to { state: ReaderUiState -> state.showCanvasView },
            VeritasScreen.GENERAL_NOTES_EDITOR to { state: ReaderUiState -> state.showGeneralNotesEditor },
            VeritasScreen.USER_MANUAL to { state: ReaderUiState -> state.showUserManual },
            VeritasScreen.ACCESSIBILITY_SETTINGS to { state: ReaderUiState -> state.showAccessibilitySettings }
        )
        for ((screen, getter) in mappings) {
            val wasVisible = getter(old)
            val isVisible = getter(next)
            if (wasVisible != isVisible) {
                if (isVisible) {
                    if (!newStack.contains(screen)) {
                        newStack = newStack + screen
                    }
                } else {
                    newStack = newStack.filter { it != screen }
                }
            }
        }
        return next.copy(navStack = newStack)
    }

    fun navigateBack() {
        val stack = uiState.value.navStack
        if (stack.isEmpty()) return
        when (val top = stack.last()) {
            VeritasScreen.TEXT_EDITOR -> dismissTextEditor()
            VeritasScreen.TUTORIAL -> finishTutorial()
            else -> {
                updateState {
                    when (top) {
                        VeritasScreen.FILE_BROWSER -> it.copy(showFileBrowser = false)
                        VeritasScreen.PDF_IMPORT_TOOLS -> it.copy(showPdfImportTools = false)
                        VeritasScreen.READER_SETTINGS -> it.copy(showReaderSettings = false)
                        VeritasScreen.PRONUNCIATION_RULES -> it.copy(showPronunciationRules = false)
                        VeritasScreen.VOICE_STUDIO -> it.copy(showVoiceStudio = false)
                        VeritasScreen.NARRATION_STUDIO -> it.copy(showNarrationStudio = false)
                        VeritasScreen.AI_STUDY_TOOLS -> it.copy(showAiStudyTools = false)
                        VeritasScreen.AI_CENTER -> it.copy(showAiCenter = false)
                        VeritasScreen.ASK_AI_SETTINGS -> it.copy(showAskAiSettings = false)
                        VeritasScreen.TRANSLATION_TOOLS -> it.copy(showTranslationTools = false)
                        VeritasScreen.SLEEP_TIMER -> it.copy(showSleepTimerDialog = false)
                        VeritasScreen.READING_LISTS -> it.copy(showReadingLists = false)
                        VeritasScreen.READING_HISTORY -> it.copy(showReadingHistory = false)
                        VeritasScreen.DOCUMENT_NOTES -> it.copy(showDocumentNotes = false)
                        VeritasScreen.SETTINGS_HUB -> it.copy(showSettingsHub = false)
                        VeritasScreen.BACKUP_TOOLS -> it.copy(showBackupTools = false)
                        VeritasScreen.SYNC_CENTER -> it.copy(showSyncCenter = false)
                        VeritasScreen.APP_HEALTH -> it.copy(showAppHealth = false)
                        VeritasScreen.CANVAS_VIEW -> it.copy(showCanvasView = false)
                        VeritasScreen.GENERAL_NOTES_EDITOR -> it.copy(showGeneralNotesEditor = false)
                        VeritasScreen.USER_MANUAL -> it.copy(showUserManual = false)
                        VeritasScreen.ACCESSIBILITY_SETTINGS -> it.copy(showAccessibilitySettings = false)
                    }
                }
            }
        }
    }


    companion object {
        fun cleanVersionString(version: String): String {
            var clean = version.trim().lowercase(java.util.Locale.getDefault())
            if (clean.startsWith("v_")) {
                clean = clean.substring(2)
            } else if (clean.startsWith("v")) {
                clean = clean.substring(1)
            }
            val builder = StringBuilder()
            var lastWasDot = false
            for (char in clean) {
                if (char.isDigit()) {
                    builder.append(char)
                    lastWasDot = false
                } else if (char == '.') {
                    if (!lastWasDot) {
                        builder.append(char)
                        lastWasDot = true
                    }
                } else {
                    break
                }
            }
            return builder.toString().trimEnd('.')
        }

        fun isVersionNewer(local: String, remote: String): Boolean {
            val cleanLocal = cleanVersionString(local)
            val cleanRemote = cleanVersionString(remote)
            val localParts = cleanLocal.split(".").map { it.toIntOrNull() ?: 0 }
            val remoteParts = cleanRemote.split(".").map { it.toIntOrNull() ?: 0 }
            val length = kotlin.math.max(localParts.size, remoteParts.size)
            for (i in 0 until length) {
                val l = localParts.getOrElse(i) { 0 }
                val r = remoteParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (l > r) return false
            }
            return false
        }

        /**
         * Intelligently select the best matching APK for the current device architecture:
         * - If the device supports 64-bit ARM (arm64-v8a), prioritize arm64 packages.
         * - If the device only supports 32-bit ARM (armeabi-v7a), prioritize armeabi-v7a packages.
         * - Fall back to universal packages or the first available APK.
         */
        fun selectOptimalApkUrl(
            assets: org.json.JSONArray?,
            supportedAbis: Array<String> = android.os.Build.SUPPORTED_ABIS
        ): String {
            if (assets == null) return ""
            val apks = mutableListOf<Pair<String, String>>()
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val name = asset.optString("name", "")
                val url = asset.optString("browser_download_url", "")
                if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                    apks.add(name to url)
                }
            }
            if (apks.isEmpty()) return ""

            val supportsArm64 = supportedAbis.any {
                it.contains("arm64", ignoreCase = true) || it.contains("aarch64", ignoreCase = true)
            }
            if (supportsArm64) {
                val arm64Apk = apks.firstOrNull { (name, _) ->
                    name.contains("arm64", ignoreCase = true) || name.contains("v8a", ignoreCase = true)
                }
                if (arm64Apk != null) return arm64Apk.second
            }

            val supportsArm32 = supportedAbis.any {
                it.contains("armeabi", ignoreCase = true) || it.contains("v7a", ignoreCase = true)
            }
            if (supportsArm32) {
                val arm32Apk = apks.firstOrNull { (name, _) ->
                    (name.contains("v7a", ignoreCase = true) || name.contains("armeabi", ignoreCase = true)) &&
                        !name.contains("arm64", ignoreCase = true)
                }
                if (arm32Apk != null) return arm32Apk.second
            }

            val universalApk = apks.firstOrNull { (name, _) ->
                name.contains("universal", ignoreCase = true)
            }
            if (universalApk != null) return universalApk.second

            return apks.first().second
        }
    }
}

