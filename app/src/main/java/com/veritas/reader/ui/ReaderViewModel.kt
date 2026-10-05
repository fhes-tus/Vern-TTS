package com.veritas.reader.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.veritas.reader.CoverExtractor
import com.veritas.reader.DocumentPageImageLoader
import com.veritas.reader.migrateClassicProvenance
import com.veritas.reader.DocumentRepository
import com.veritas.reader.DocumentTextRepairer
import com.veritas.reader.PlaybackActions
import com.veritas.reader.PlaybackStateStore
import com.veritas.reader.ReaderDocument
import com.veritas.reader.ReaderMode
import com.veritas.reader.ReaderTextModelCache
import com.veritas.reader.SavedDocument
import com.veritas.reader.VeritasFileBrowserScanner
import com.veritas.reader.VeritasScreen
import com.veritas.reader.VeritasSleepTimerRequest
import com.veritas.reader.addReadingHistory
import com.veritas.reader.buildReaderDocument
import com.veritas.reader.completeCurrentAndGetNextQueued
import com.veritas.reader.loadAiPromptHistory
import com.veritas.reader.loadAiPromptTemplates
import com.veritas.reader.loadAllAnnotations
import com.veritas.reader.loadAllDocumentNotes
import com.veritas.reader.loadAllFlashcards
import com.veritas.reader.loadAllQuizzes
import com.veritas.reader.loadAnnotationCount
import com.veritas.reader.loadAnnotations
import com.veritas.reader.loadDocumentNote
import com.veritas.reader.loadGeneralNotes
import com.veritas.reader.loadPronunciationRules
import com.veritas.reader.loadQueueDocuments
import com.veritas.reader.loadReaderTrackerSnapshot
import com.veritas.reader.loadReadingHistory
import com.veritas.reader.loadTrashedGeneralNotes
import com.veritas.reader.loadNoteNotebooks
import com.veritas.reader.loadReadingListCatalog
import com.veritas.reader.recordAppOpen
import com.veritas.reader.recordDocumentProgress
import com.veritas.reader.recordDocumentRead
import com.veritas.reader.recordUsageDuration
import com.veritas.reader.sendPlaybackIntent
import com.veritas.reader.ui.screens.VeritasHomeTab
import com.veritas.reader.updateVeritasWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class ReaderViewModel(application: Application) : AndroidViewModel(application) {

    internal val repository by lazy { DocumentRepository(application) }
    internal val delegateUiState = MutableStateFlow(ReaderUiState())
    internal val _uiState = delegateUiState
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    fun updateState(block: (ReaderUiState) -> ReaderUiState) {
        _uiState.update(block)
    }

    internal val notesSettingsSaveMutex = kotlinx.coroutines.sync.Mutex()
    internal val noteCollectionMutex = kotlinx.coroutines.sync.Mutex()
    internal var generalNoteSaveJob: Job? = null
    internal var generalNoteSaveRevision = 0L
    internal var importJob: Job? = null
    internal var batchImportJob: Job? = null
    internal val fileBrowserCache = android.util.LruCache<com.veritas.reader.VeritasBrowserLocation, com.veritas.reader.VeritasFileBrowserScanResult>(4)
    internal var documentOpenJob: Job? = null
    internal var autoOpenImportId: java.util.UUID? = null
    internal val pendingImportIds = java.util.concurrent.ConcurrentHashMap.newKeySet<java.util.UUID>()
    internal var exportJob: Job? = null
    internal var backupJob: Job? = null
    internal var outlineJob: Job? = null
    internal var voiceJob: Job? = null
    internal var aiStudyJob: Job? = null
    internal var aiStudyRevision = 0L
    internal var voiceSettingsSaveJob: Job? = null
    @Volatile internal var voiceSettingsSaveRevision = 0L
    @Volatile internal var readerSettingsSaveRevision = 0L
    internal var scanJob: Job? = null
    internal var downloadJob: Job? = null
    internal var sleepTimerJob: Job? = null
    internal var appSessionStartedAt: Long = 0L
    internal var activeDocStartedAt: Long = 0L

    init {
        resumeBatchImports()
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
            var documents = repository.migrateClassicProvenance()
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
            val trashedGeneralNotes = repository.loadTrashedGeneralNotes()
            val noteNotebooks = repository.loadNoteNotebooks()
            val flashcards = repository.loadAllFlashcards()
            val quizzes = repository.loadAllQuizzes()
            val userName = repository.loadUserName()
            val readingInterest = repository.loadReadingInterest()
            val hasCompletedOnboarding = repository.hasSeenOnboardingTutorial()
            val hasImportedOrOpenedDocument = repository.hasImportedOrOpenedDocument()
            val questProgress = repository.loadQuestProgress()
            val hasCheeseDoc = documents.any { it.title.contains("Who Moved My Cheese", ignoreCase = true) }
            val seedPrefs = application.getSharedPreferences("veritas_reader_library", android.content.Context.MODE_PRIVATE)
            val initializeSamples = !seedPrefs.getBoolean("bundled_samples_initialized", false)
            // Existing users may already have deleted the samples before upgrading.
            val seedSamples = initializeSamples && documents.isEmpty() && !hasImportedOrOpenedDocument
            if (seedSamples && !hasCheeseDoc) {
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
            if (seedSamples && documents.isEmpty()) {
                repository.createDocument(
                    title = "Vern Welcome Guide",
                    text = "Welcome to Vern! This is a sample document designed to help you explore the reading environment. Vern lets you convert research papers, textbooks, EPUBs, docx files, web articles, and images into high-quality spoken audio. Long-press any sentence in this guide to try highlighting, bookmarking, adding study notes, or asking the AI Assistant a question. Adjust the voice speed or select premium voices in the expandable player panel below. Toggle different layout modes like TEXT for clean reading or LISTEN to follow along sentence-by-sentence. Enjoy your reading journey!",
                    sourceLabel = "System"
                )
            }
            if (initializeSamples) {
                check(seedPrefs.edit().putBoolean("bundled_samples_initialized", true).commit()) { "Could not save sample initialization." }
            }
            if (seedSamples && (!hasCheeseDoc || documents.isEmpty())) {
                documents = repository.loadDocuments()
            }

            val notesSettings = NotesSettingsStore.load(application)

            // Immediately emit library state so the user sees their documents, notes, and settings instantly
            _uiState.update {
                it.withVisibility(VeritasScreen.TUTORIAL, !hasCompletedOnboarding && it.activeDocument == null && !it.isOpeningDocument && it.navStack.isEmpty()).copy(
                    documents = documents,
                    generalNotes = generalNotes,
                    trashedGeneralNotes = trashedGeneralNotes,
                    noteNotebooks = noteNotebooks,
                    queuedDocuments = queuedDocuments,
                    pronunciationRules = pronunciationRules,
                    voiceSettings = if (voiceSettingsSaveRevision == 0L) voiceSettings else it.voiceSettings,
                    narrationSettings = narrationSettings,
                    readerSettings = if (readerSettingsSaveRevision == 0L) readerSettings else it.readerSettings,
                    notesSettings = notesSettings,
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
                    questChecklistDismissed = (questProgress.tourDone && questProgress.importDone && questProgress.speedDone && questProgress.bookmarkDone) || repository.isQuestChecklistDismissed(),
                    dismissedHeroDocId = repository.getDismissedHeroDocId(),
                    dismissedHeroDocIds = repository.getDismissedHeroDocIds(),
                    isHeroContinueDismissed = repository.isHeroContinueDismissed()
                )
            }

            observeClassicDownloads()
            // Repair missing covers for existing files in the background without blocking UI
            documents.forEach { doc ->
                if (doc.title.contains("Who Moved My Cheese", ignoreCase = true)) {
                    val coversDir = CoverExtractor.coversDir(application)
                    val coverFile = File(coversDir, "${doc.id}.cover.jpg")
                    // Upgrade legacy AI-generated cover (>100KB) or repair missing cover to published book cover
                    if (!coverFile.exists() || coverFile.length() > 100_000L) {
                        runCatching {
                            application.assets.open("covers/who_moved_my_cheese.jpg").use { input ->
                                coverFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                } else if (CoverExtractor.coverFile(application, doc.id) == null) {
                    val classic = com.veritas.reader.ui.screens.CURATED_CLASSICS.firstOrNull { it.id == doc.catalogId && doc.catalogId.isNotBlank() }
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
            var previousSessionId = PlaybackStateStore.activeDocumentId
            androidx.compose.runtime.snapshotFlow { PlaybackStateStore.activeDocumentId }
                .collect { newDocId ->
                    val currentId = uiState.value.activeDocument?.id
                    val previousId = previousSessionId
                    previousSessionId = newDocId
                    if (newDocId != null && currentId != null && currentId == previousId && currentId != newDocId) {
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
            val currentIndex = currentReaderIndex
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
        if (serviceId != null && serviceId in documentIds) stopAndForgetPlayback("Reading removed.")
        if (activeId != null && activeId in documentIds) {
            recordActiveDocSessionTime()
            _uiState.update { it.withVisibility(VeritasScreen.CANVAS_VIEW, false).copy(
                activeDocument = null,
                annotations = emptyList(),
                documentNoteDraft = ""
            ) }
        }
    }

    fun clearContinueReading(document: SavedDocument) {
        if (PlaybackStateStore.activeDocumentId == document.id && PlaybackStateStore.isPlaying) {
            stopAndForgetPlayback("Reading dismissed.")
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.setDismissedHeroDocId(document.id)
            repository.addDismissedHeroDocId(document.id)
            repository.setHeroContinueDismissed(true)
            val updatedDismissed = repository.getDismissedHeroDocIds()
            val queue = repository.loadQueueDocuments()
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        dismissedHeroDocId = document.id,
                        dismissedHeroDocIds = updatedDismissed,
                        isHeroContinueDismissed = true,
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
                _uiState.update {
                    it.copy(
                        documents = docs,
                        readerTrackerSnapshot = tracker,
                        readingHistory = history,
                        dismissedHeroDocIds = it.dismissedHeroDocIds - docId,
                        isHeroContinueDismissed = false
                    )
                }
            }
        }
    }

    fun syncPlaybackStateForDocument(readerDocument: ReaderDocument, startIndex: Int) {
        val safeIndex = if (readerDocument.chunks.isEmpty()) 0 else startIndex.coerceIn(0, readerDocument.chunks.lastIndex)
        _uiState.update { it.copy(readerPosition = safeIndex) }
        // Keep the audio session tied to its book when another reading view opens.
        if (PlaybackStateStore.activeDocumentId != null && PlaybackStateStore.activeDocumentId != readerDocument.id) return
        if (PlaybackStateStore.isPlaying && PlaybackStateStore.activeDocumentId == readerDocument.id) return
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
        // Appending pages owns partial content. Do not persist a repair made from
        // an earlier snapshot over pages the worker may just have published.
        val repairedText = if (metadata.partial) rawText else DocumentTextRepairer.repairPseudoTables(rawText)
        val updatedMetadata = if (repairedText != rawText) {
            repository.updateDocumentText(metadata.id, repairedText) ?: metadata
        } else metadata
        buildReaderDocument(updatedMetadata, repairedText)
    }

    fun openSavedDocument(metadata: SavedDocument, startIndex: Int? = null) {
        if (autoOpenImportId?.toString() != metadata.id) {
            autoOpenImportId = null
            _uiState.update { it.copy(importAwaitingReadyPages = false) }
        }
        documentOpenJob?.cancel()
        val currentActive = _uiState.value.activeDocument
        val currentActiveId = currentActive?.id
        if (currentActive != null && currentActiveId != null && currentActiveId != metadata.id) {
            recordActiveDocSessionTime()
            val lastIndex = currentReaderIndex
            val totalChunks = currentActive.chunks.size
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateProgress(currentActiveId, lastIndex, totalChunks)
            }
        }
        repository.removeDismissedHeroDocId(metadata.id)
        repository.setHeroContinueDismissed(false)
        if (metadata.id == repository.getDismissedHeroDocId()) {
            repository.setDismissedHeroDocId(null)
        }
        _uiState.update {
            it.withVisibility(VeritasScreen.FILE_BROWSER, false).copy(
                isOpeningDocument = true,
                importSourceName = metadata.title,
                dismissedHeroDocId = if (it.dismissedHeroDocId == metadata.id) null else it.dismissedHeroDocId,
                dismissedHeroDocIds = it.dismissedHeroDocIds - metadata.id,
                isHeroContinueDismissed = false
            )
        }
        documentOpenJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val latestMetadata = repository.findDocument(metadata.id) ?: metadata
                val readerDocument = loadReaderDocument(latestMetadata)
                coroutineContext.ensureActive()

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

                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.withVisibility(VeritasScreen.CANVAS_VIEW, false).copy(
                            activeDocument = readerDocument,
                            annotations = annotations,
                            documentNoteDraft = documentNote,
                            // Filled in by loadOutlineInBackground once PDFBox has parsed the
                            // file; the reader must not wait on it.
                            documentOutline = emptyList(),
                            searchQuery = "",
                            searchMatches = emptyList(),
                            searchCursor = 0,
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

                // Pre-warm initial page images into cache in background after opening without delaying the reader UI
                launch(Dispatchers.IO) {
                    runCatching {
                        kotlinx.coroutines.delay(600L)
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isOpeningDocument = false,
                        importMessage = "Could not open ${metadata.title}: ${error.message ?: "unknown error"}") }
                }
            }
        }
    }

    fun dismissOpeningDocument() {
        autoOpenImportId = null
        documentOpenJob?.cancel()
        _uiState.update { it.copy(isOpeningDocument = false, importAwaitingReadyPages = false) }
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
        autoPlay: Boolean = isReaderPlaying,
        forcePlaybackStart: Boolean = false,
        charOffset: Int? = null
    ) {
        val doc = uiState.value.activeDocument ?: return
        if (doc.chunks.isEmpty()) return
        val safeIndex = index.coerceIn(0, doc.chunks.lastIndex)
        _uiState.update { it.copy(readerPosition = safeIndex) }
        if (PlaybackStateStore.activeDocumentId == doc.id) PlaybackStateStore.currentIndex = safeIndex
        persistProgress(safeIndex)
        val matches = uiState.value.searchMatches
        val matchIndex = matches.indexOf(safeIndex)
        if (matchIndex >= 0) {
            _uiState.update { it.copy(searchCursor = matchIndex) }
        }
        // While the service is actively speaking, every index move must be sent to it.
        // Otherwise the service finishes its current sentence and then advances from the
        // mutated shared index, speaking the wrong sentence.
        val notifyService = autoPlay || isReaderPlaying
        if (notifyService && doc.id != null) {
            if (forcePlaybackStart) requestNotificationPermissionForPlayback()
            sendPlaybackIntent(
                context = getApplication(),
                action = if (forcePlaybackStart || PlaybackStateStore.activeDocumentId != doc.id) PlaybackActions.ACTION_PLAY else PlaybackActions.ACTION_JUMP_TO,
                documentId = doc.id,
                startIndex = safeIndex,
                charOffset = charOffset?.coerceIn(0, doc.chunks[safeIndex].length)
            )
        }
    }

    fun playOrPause() {
        val doc = uiState.value.activeDocument ?: return
        val docId = doc.id ?: return
        if (isReaderPlaying) {
            sendPlaybackIntent(getApplication(), PlaybackActions.ACTION_PAUSE)
        } else {
            requestNotificationPermissionForPlayback()
            sendPlaybackIntent(
                context = getApplication(),
                action = PlaybackActions.ACTION_PLAY,
                documentId = docId,
                startIndex = currentReaderIndex
            )
        }
    }

    fun playOrPauseSavedDocument(metadata: SavedDocument) {
        repository.removeDismissedHeroDocId(metadata.id)
        repository.setHeroContinueDismissed(false)
        _uiState.update {
            it.copy(
                dismissedHeroDocId = if (it.dismissedHeroDocId == metadata.id) null else it.dismissedHeroDocId,
                dismissedHeroDocIds = it.dismissedHeroDocIds - metadata.id,
                isHeroContinueDismissed = false
            )
        }
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
        val atLastSection = doc.chunks.isNotEmpty() && currentReaderIndex >= doc.chunks.lastIndex
        if (!atLastSection) {
            moveTo(currentReaderIndex + 1, autoPlay = isReaderPlaying)
            return
        }

        if (uiState.value.readerSettings.autoPlayQueue && uiState.value.queuedDocuments.isNotEmpty()) {
            if (isReaderPlaying) {
                sendPlaybackIntent(getApplication(), PlaybackActions.ACTION_NEXT)
            } else {
                openNextQueuedAfterCurrent(autoPlay = false)
            }
        }
    }

    fun returnToLibrary() {
        dismissOpeningDocument()
        val doc = uiState.value.activeDocument
        val docId = doc?.id
        val currentIndex = currentReaderIndex
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
        _uiState.update { it.copy(targetHomeTab = tab, targetLibrarySection = if (tab == VeritasHomeTab.LIBRARY) com.veritas.reader.ui.screens.LibrarySection.MY_LIBRARY else null) }
    }

    fun clearTargetHomeTab() {
        _uiState.update { it.copy(targetHomeTab = null, targetLibrarySection = null) }
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
                        VeritasScreen.FILE_BROWSER -> it.withVisibility(VeritasScreen.FILE_BROWSER, false)
                        VeritasScreen.PDF_IMPORT_TOOLS -> it.withVisibility(VeritasScreen.PDF_IMPORT_TOOLS, false)
                        VeritasScreen.READER_SETTINGS -> it.withVisibility(VeritasScreen.READER_SETTINGS, false)
                        VeritasScreen.PRONUNCIATION_RULES -> it.withVisibility(VeritasScreen.PRONUNCIATION_RULES, false)
                        VeritasScreen.VOICE_STUDIO -> it.withVisibility(VeritasScreen.VOICE_STUDIO, false)
                        VeritasScreen.NARRATION_STUDIO -> it.withVisibility(VeritasScreen.NARRATION_STUDIO, false)
                        VeritasScreen.AI_STUDY_TOOLS -> it.withVisibility(VeritasScreen.AI_STUDY_TOOLS, false)
                        VeritasScreen.AI_CENTER -> it.withVisibility(VeritasScreen.AI_CENTER, false)
                        VeritasScreen.ASK_AI_SETTINGS -> it.withVisibility(VeritasScreen.ASK_AI_SETTINGS, false)
                        VeritasScreen.TRANSLATION_TOOLS -> it.withVisibility(VeritasScreen.TRANSLATION_TOOLS, false)
                        VeritasScreen.SLEEP_TIMER -> it.withVisibility(VeritasScreen.SLEEP_TIMER, false)
                        VeritasScreen.READING_LISTS -> it.withVisibility(VeritasScreen.READING_LISTS, false)
                        VeritasScreen.READING_HISTORY -> it.withVisibility(VeritasScreen.READING_HISTORY, false)
                        VeritasScreen.DOCUMENT_NOTES -> it.withVisibility(VeritasScreen.DOCUMENT_NOTES, false)
                        VeritasScreen.SETTINGS_HUB -> it.withVisibility(VeritasScreen.SETTINGS_HUB, false)
                        VeritasScreen.BACKUP_TOOLS -> it.withVisibility(VeritasScreen.BACKUP_TOOLS, false)
                        VeritasScreen.SYNC_CENTER -> it.withVisibility(VeritasScreen.SYNC_CENTER, false)
                        VeritasScreen.APP_HEALTH -> it.withVisibility(VeritasScreen.APP_HEALTH, false)
                        VeritasScreen.CANVAS_VIEW -> it.withVisibility(VeritasScreen.CANVAS_VIEW, false)
                        VeritasScreen.GENERAL_NOTES_EDITOR -> it.withVisibility(VeritasScreen.GENERAL_NOTES_EDITOR, false)
                        VeritasScreen.USER_MANUAL -> it.withVisibility(VeritasScreen.USER_MANUAL, false)
                        VeritasScreen.ACCESSIBILITY_SETTINGS -> it.withVisibility(VeritasScreen.ACCESSIBILITY_SETTINGS, false)
                        VeritasScreen.NOTES_SETTINGS -> it.withVisibility(VeritasScreen.NOTES_SETTINGS, false)
                    }
                }
            }
        }
    }


    companion object {
        fun cleanVersionString(version: String): String {
            var clean = version.trim().lowercase(Locale.getDefault())
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
            supportedAbis: Array<String> = Build.SUPPORTED_ABIS
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

