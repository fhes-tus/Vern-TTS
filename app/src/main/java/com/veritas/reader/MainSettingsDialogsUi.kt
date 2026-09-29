package com.veritas.reader


import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.veritas.reader.ui.OnboardingController
import com.veritas.reader.ui.ReaderUiState
import com.veritas.reader.ui.ReaderViewModel
import com.veritas.reader.ui.addPronunciationRule
import com.veritas.reader.ui.checkForUpdates
import com.veritas.reader.ui.cleanupOriginals
import com.veritas.reader.ui.clearAppCache
import com.veritas.reader.ui.computeStorage
import com.veritas.reader.ui.createWelcomeDocumentSilently
import com.veritas.reader.ui.loadVoicesForEngine
import com.veritas.reader.ui.openCurrentPartTextEditor
import com.veritas.reader.ui.openFileBrowser
import com.veritas.reader.ui.openSystemTtsSettings
import com.veritas.reader.ui.openTtsDataInstaller
import com.veritas.reader.ui.previewActiveVoiceWithPreset
import com.veritas.reader.ui.previewVoice
import com.veritas.reader.ui.removePronunciationRule
import com.veritas.reader.ui.resetQuestProgress
import com.veritas.reader.ui.saveReaderSettings
import com.veritas.reader.ui.saveVoiceSettings
import com.veritas.reader.ui.screens.AboutDialog
import com.veritas.reader.ui.screens.AccessibilitySettingsDialog
import com.veritas.reader.ui.screens.PronunciationRulesDialog
import com.veritas.reader.ui.screens.ReaderSettingsDialog
import com.veritas.reader.ui.screens.SettingsHubDialog
import com.veritas.reader.ui.screens.StorageDialog
import com.veritas.reader.ui.screens.UserManualDialog
import com.veritas.reader.ui.screens.VeritasHomeTab
import com.veritas.reader.ui.screens.VoiceStudioDialog
import com.veritas.reader.ui.screens.formatVeritasBytes
import com.veritas.reader.ui.startRecordSoundFile
import com.veritas.reader.ui.togglePronunciationRule
import kotlinx.coroutines.launch


@Composable
internal fun MainSettingsDialogsHost(
    viewModel: ReaderViewModel,
    uiState: ReaderUiState,
    showUnrestrictedBatteryDialog: Boolean,
    onDismissUnrestrictedBatteryDialog: () -> Unit
) {
    val context = LocalContext.current
    var showStorageTools by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showBatteryDialog by remember(showUnrestrictedBatteryDialog) { mutableStateOf(showUnrestrictedBatteryDialog) }
    val batteryPrefs = remember(context) { context.getSharedPreferences("veritas_reader_library", Context.MODE_PRIVATE) }

        if (uiState.showSettingsHub) {
            SettingsHubDialog(
                uiState = uiState,
                onDismiss = { viewModel.updateState { it.copy(showSettingsHub = false) } },
                onOpenReaderSettings = { viewModel.updateState { it.copy(showReaderSettings = true) } },
                onOpenVoiceStudio = { viewModel.updateState { it.copy(showVoiceStudio = true) } },
                onOpenNarrationStudio = { viewModel.updateState { it.copy(showNarrationStudio = true) } },
                onOpenPronunciationRules = { viewModel.updateState { it.copy(showPronunciationRules = true) } },
                onOpenBackupRestore = { viewModel.updateState { it.copy(showBackupTools = true) } },
                onOpenSyncCenter = { viewModel.updateState { it.copy(showSyncCenter = true) } },
                onOpenAiCenter = { viewModel.updateState { it.copy(showAiCenter = true) } },
                onOpenAskAiSettings = { viewModel.updateState { it.copy(showAskAiSettings = true) } },
                onStartRecord = { viewModel.startRecordSoundFile() },
                onOpenTextEditor = { viewModel.openCurrentPartTextEditor() },
                onOpenTutorial = {
                    viewModel.resetQuestProgress()
                    viewModel.updateState { it.copy(showSettingsHub = false) }
                    viewModel.createWelcomeDocumentSilently()
                    OnboardingController.activeStep = null
                },
                onOpenPdfTools = { viewModel.updateState { it.copy(showPdfImportTools = true) } },
                onOpenFileBrowser = { viewModel.openFileBrowser() },
                onOpenSleepTimer = { viewModel.updateState { it.copy(showSleepTimerDialog = true) } },
                onOpenReadingLists = { viewModel.updateState { it.copy(showReadingLists = true) } },
                onOpenUserManual = { viewModel.updateState { it.copy(showUserManual = true) } },
                onOpenStorage = { showStorageTools = true },
                onOpenAccessibility = { viewModel.updateState { it.copy(showAccessibilitySettings = true) } },
                onCheckForUpdates = { viewModel.checkForUpdates(isManual = true) }
            )
        }

        if (showStorageTools) {
            var breakdown by remember { mutableStateOf<StorageBreakdown?>(null) }
            var candidates by remember { mutableStateOf<List<Pair<SavedDocument, Long>>>(emptyList()) }
            var cleanupMessage by remember { mutableStateOf<String?>(null) }
            var refreshTick by remember { mutableStateOf(0) }
            val storageScope = rememberCoroutineScope()
            LaunchedEffect(refreshTick) {
                val (computedBreakdown, computedCandidates) = viewModel.computeStorage()
                breakdown = computedBreakdown
                candidates = computedCandidates
            }
            StorageDialog(
                breakdown = breakdown,
                candidates = candidates,
                cleanupMessage = cleanupMessage,
                onSmartCleanup = {
                    val ids = candidates.map { it.first.id }.toSet()
                    val count = candidates.size
                    storageScope.launch {
                        val freed = viewModel.cleanupOriginals(ids)
                        cleanupMessage = "Freed ${formatVeritasBytes(freed)} from $count document${if (count == 1) "" else "s"}."
                        refreshTick++
                    }
                },
                onClearCache = {
                    storageScope.launch {
                        val freed = viewModel.clearAppCache()
                        cleanupMessage = "Cleared ${formatVeritasBytes(freed)} of temporary cache."
                        refreshTick++
                    }
                },
                onDismiss = { showStorageTools = false }
            )
        }

        var userManualTipTitle by remember { mutableStateOf("") }
        var userManualTipText by remember { mutableStateOf<String?>(null) }

        if (uiState.showUserManual) {
            UserManualDialog(
                onDismiss = { viewModel.updateState { it.copy(showUserManual = false) } },
                onNavigateToSetting = { setting ->
                    when (setting) {
                        "settings_hub" -> viewModel.updateState { it.copy(showUserManual = false, showSettingsHub = true) }
                        "reader_settings" -> viewModel.updateState { it.copy(showUserManual = false, showReaderSettings = true) }
                        "voice_studio" -> viewModel.updateState { it.copy(showUserManual = false, showVoiceStudio = true) }
                        "narration_studio" -> viewModel.updateState { it.copy(showUserManual = false, showNarrationStudio = true) }
                        "pronunciation" -> viewModel.updateState { it.copy(showUserManual = false, showPronunciationRules = true) }
                        "sleep_timer" -> viewModel.updateState { it.copy(showUserManual = false, showSleepTimerDialog = true) }
                        "pdf_tools" -> viewModel.updateState { it.copy(showUserManual = false, showPdfImportTools = true) }
                        "history" -> viewModel.updateState { it.copy(showUserManual = false, showReadingHistory = true) }
                        "reading_lists" -> viewModel.updateState { it.copy(showUserManual = false, showReadingLists = true) }
                        "sync_center" -> viewModel.updateState { it.copy(showUserManual = false, showSyncCenter = true) }
                        "ai_center" -> viewModel.updateState { it.copy(showUserManual = false, showAiCenter = true) }
                        "ask_ai" -> viewModel.updateState { it.copy(showUserManual = false, showAskAiSettings = true) }
                        "file_browser" -> {
                            viewModel.updateState { it.copy(showUserManual = false) }
                            viewModel.openFileBrowser()
                        }
                        "backup_tools" -> viewModel.updateState { it.copy(showUserManual = false, showBackupTools = true) }
                        "classics_catalog" -> viewModel.updateState { it.copy(showUserManual = false, showClassicsCatalog = true) }
                        "storage_manager" -> {
                            viewModel.updateState { it.copy(showUserManual = false) }
                            showStorageTools = true
                        }
                        "about" -> {
                            viewModel.updateState { it.copy(showUserManual = false) }
                            showAboutDialog = true
                        }
                        "study_general", "study_flashcards" -> {
                            viewModel.updateState { it.copy(showUserManual = false) }
                            viewModel.navigateToHomeTab(VeritasHomeTab.STUDY)
                        }
                        "notes_tab" -> {
                            viewModel.updateState { it.copy(showUserManual = false) }
                            viewModel.navigateToHomeTab(VeritasHomeTab.NOTES)
                        }
                        "library" -> {
                            viewModel.updateState { it.copy(showUserManual = false) }
                            viewModel.navigateToHomeTab(VeritasHomeTab.LIBRARY)
                        }
                        "library_options" -> {
                            viewModel.updateState { it.copy(showUserManual = false) }
                            viewModel.navigateToHomeTab(VeritasHomeTab.LIBRARY)
                            userManualTipTitle = "Document Actions"
                            userManualTipText = "Tap the three-dot overflow button on any book card in your library to edit metadata, rename files, assign categories, add to custom lists, reset progress, or delete files from storage."
                        }
                        "bulk_edit" -> {
                            viewModel.updateState { it.copy(showUserManual = false) }
                            viewModel.navigateToHomeTab(VeritasHomeTab.LIBRARY)
                            userManualTipTitle = "Batch Organization"
                            userManualTipText = "Long-press any document card in your Library to enter multi-select mode. You can then tap other cards to select them and perform bulk actions like category assignment or batch deletion from the top toolbar."
                        }
                        "file_browser_filters" -> {
                            viewModel.updateState { it.copy(showUserManual = false) }
                            viewModel.openFileBrowser()
                            userManualTipTitle = "Browser Sorting & Filters"
                            userManualTipText = "Tap the options menu (three dots) at the top-right of the integrated File Browser to change sorting (name, date, size), filter by file type, or toggle hidden files and folders."
                        }
                        "text_selection" -> {
                            viewModel.updateState { it.copy(showUserManual = false) }
                            userManualTipTitle = "Interactive Text Selection"
                            userManualTipText = "Double-tap or long-press on any word or sentence in the reader screen to highlight it. Use the selection handles to expand the text range, and access options like copying, notes, dictionary definitions, and TTS narration controls."
                        }
                        "reader_tools" -> {
                            viewModel.updateState { it.copy(showUserManual = false) }
                            userManualTipTitle = "Reader Tools Menu"
                            userManualTipText = "Tap the top-right tool menu button (three dots) inside the Reader Screen to access bookmarks, text search inside the book, theme settings, and notes export actions."
                        }
                    }
                }
            )
        }

        if (userManualTipText != null) {
            AlertDialog(
                onDismissRequest = { userManualTipText = null },
                title = {
                    Text(
                        text = userManualTipTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = userManualTipText!!,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { userManualTipText = null }
                    ) {
                        Text("Got it")
                    }
                }
            )
        }

        if (showAboutDialog) {
            AboutDialog(
                uiState = uiState,
                onCheckForUpdates = { viewModel.checkForUpdates(isManual = true) },
                onDismiss = { showAboutDialog = false }
            )
        }

        if (showBatteryDialog) {
            UnrestrictedBatteryDialog(
                onDismiss = { showBatteryDialog = false; onDismissUnrestrictedBatteryDialog() },
                onOpenSettings = {
                    requestIgnoreBatteryOptimizations(context)
                },
                onNeverAskAgain = {
                    batteryPrefs.edit().putBoolean("battery_unrestricted_never_ask", true).apply()
                }
            )
        }

        if (uiState.showVoiceStudio) {
            VoiceStudioDialog(
                settings = uiState.voiceSettings,
                engines = uiState.ttsEngines,
                voices = uiState.ttsVoices,
                loadingVoices = uiState.voiceLoadInProgress,
                onRefreshEngines = {
                    viewModel.updateState {
                        it.copy(
                            ttsEngines = VoiceManager.loadInstalledEngines(
                                context
                            )
                        )
                    }
                },
                onLoadVoices = { viewModel.loadVoicesForEngine() },
                onUseSystemDefault = {
                    viewModel.saveVoiceSettings(
                        uiState.voiceSettings.copy(
                            enginePackage = "",
                            engineLabel = "System default",
                            voiceName = "",
                            voiceLabel = "System default voice"
                        )
                    )
                },
                onEngineSelected = { engine ->
                    viewModel.saveVoiceSettings(
                        uiState.voiceSettings.copy(
                            enginePackage = engine.packageName,
                            engineLabel = engine.label,
                            voiceName = "",
                            voiceLabel = "System default voice"
                        )
                    )
                },
                onLanguageSelected = { localeTag ->
                    viewModel.saveVoiceSettings(
                        uiState.voiceSettings.copy(
                            localeTag = localeTag,
                            voiceName = "",
                            voiceLabel = "System default voice"
                        )
                    )
                },
                onShowNetworkVoicesChange = { showNetwork ->
                    viewModel.saveVoiceSettings(
                        uiState.voiceSettings.copy(
                            showNetworkVoices = showNetwork
                        )
                    )
                },
                onVoiceSelected = { voice ->
                    val detectedEngine = VoiceManager.engineForVoice(voice.name)
                    val targetEngine = if (detectedEngine != null) {
                        detectedEngine
                    } else if (VoiceManager.isVeritasEngine(uiState.voiceSettings.enginePackage)) {
                        ""
                    } else {
                        uiState.voiceSettings.enginePackage
                    }
                    val targetEngineLabel = when (targetEngine) {
                        VoiceManager.VERITAS_LITE -> "Vern Lite"
                        VoiceManager.VERITAS_STUDIO -> "Vern Studio"
                        "" -> "System default"
                        else -> uiState.voiceSettings.engineLabel
                    }
                    viewModel.saveVoiceSettings(
                        uiState.voiceSettings.copy(
                            voiceName = voice.name,
                            voiceLabel = voice.label.ifBlank { voice.name },
                            localeTag = voice.localeTag,
                            enginePackage = targetEngine,
                            engineLabel = targetEngineLabel
                        )
                    )
                },
                onPreviewVoice = { voice -> viewModel.previewVoice(voice) },
                onPreviewActiveVoiceWithPreset = { viewModel.previewActiveVoiceWithPreset() },
                onPresetSelected = { name, rate, pitch ->
                    viewModel.saveVoiceSettings(
                        uiState.voiceSettings.copy(
                            profileName = name,
                            preferredRate = rate,
                            preferredPitch = pitch
                        )
                    )
                },
                onAddLanguageVoice = { viewModel.openTtsDataInstaller() },
                onOpenSystemTtsSettings = { viewModel.openSystemTtsSettings() },
                onOpenSpeechEdits = {
                    viewModel.updateState {
                        it.copy(
                            showVoiceStudio = false,
                            showPronunciationRules = true
                        )
                    }
                },
                onOpenNarrationStudio = {
                    viewModel.updateState {
                        it.copy(
                            showVoiceStudio = false,
                            showNarrationStudio = true
                        )
                    }
                },
                onDismiss = { viewModel.updateState { it.copy(showVoiceStudio = false) } }
            )
        }

        if (uiState.showReaderSettings) {
            val activeDoc = uiState.activeDocument
            val totalSentences = activeDoc?.chunks?.size ?: 1
            val totalPages = (activeDoc?.pageCount?.takeIf { it > 0 } ?: ((totalSentences + 19) / 20)).coerceAtLeast(1)
            val currentSentence = PlaybackStateStore.currentIndex
            val currentPage = if (activeDoc?.pageCount != null && activeDoc.pageCount > 0) {
                (((currentSentence.toFloat() / totalSentences.coerceAtLeast(1).toFloat()) * totalPages).toInt() + 1).coerceIn(1, totalPages)
            } else {
                ((currentSentence / 20) + 1).coerceIn(1, totalPages)
            }

            ReaderSettingsDialog(
                settings = uiState.readerSettings,
                onDismiss = { viewModel.updateState { it.copy(showReaderSettings = false) } },
                currentPage = currentPage,
                totalPages = totalPages,
                onJumpToPage = if (activeDoc != null) {
                    { pageNo ->
                        val targetSentence = ((pageNo - 1).toFloat() / totalPages.toFloat() * totalSentences).toInt().coerceIn(0, totalSentences - 1)
                        viewModel.moveTo(targetSentence, false)
                    }
                } else null,
                onFontSizeChange = { size ->
                    viewModel.saveReaderSettings(
                        uiState.readerSettings.copy(
                            fontSizeSp = size
                        )
                    )
                },
                onSpacingChange = { spacing ->
                    viewModel.saveReaderSettings(
                        uiState.readerSettings.copy(
                            sectionSpacingDp = spacing
                        )
                    )
                },
                onThemeChange = { themeId ->
                    val isHc = themeId == "dark_high_contrast" || themeId == "white_high_contrast" || themeId == "blue_high_contrast"
                    viewModel.saveReaderSettings(
                        uiState.readerSettings.copy(
                            themeId = themeId,
                            previousThemeId = if (isHc) uiState.readerSettings.previousThemeId else null
                        )
                    )
                },
                onThemePackChange = { packId ->
                    viewModel.saveReaderSettings(
                        uiState.readerSettings.copy(
                            themePackId = packId
                        )
                    )
                },
                onToggleVibrantHero = {
                    viewModel.saveReaderSettings(
                        uiState.readerSettings.copy(
                            vibrantHero = !uiState.readerSettings.vibrantHero
                        )
                    )
                },
                onToggleAutoPlayQueue = {
                    viewModel.saveReaderSettings(
                        uiState.readerSettings.copy(
                            autoPlayQueue = !uiState.readerSettings.autoPlayQueue
                        )
                    )
                },
                onUiFontChange = { fontId ->
                    viewModel.saveReaderSettings(
                        uiState.readerSettings.copy(
                            uiFontId = fontId
                        )
                    )
                },
                onPaperToneModeChange = { mode ->
                    viewModel.saveReaderSettings(
                        uiState.readerSettings.copy(
                            paperToneMode = mode.name.lowercase()
                        )
                    )
                },
                onToggleAmoledMode = {
                    viewModel.saveReaderSettings(
                        uiState.readerSettings.copy(
                            amoledMode = !uiState.readerSettings.amoledMode
                        )
                    )
                }
            )
        }

        if (uiState.showAccessibilitySettings) {
            AccessibilitySettingsDialog(
                settings = uiState.readerSettings,
                onDismiss = { viewModel.updateState { it.copy(showAccessibilitySettings = false) } },
                onThemeChange = { themeId ->
                    viewModel.saveReaderSettings(uiState.readerSettings.copy(themeId = themeId))
                },
                onToggleContrastTheme = { themeId, previousThemeId ->
                    viewModel.saveReaderSettings(
                        uiState.readerSettings.copy(
                            themeId = themeId,
                            previousThemeId = previousThemeId
                        )
                    )
                },
                onToggleAdaptiveCover = {
                    viewModel.saveReaderSettings(
                        uiState.readerSettings.copy(adaptiveCover = !uiState.readerSettings.adaptiveCover)
                    )
                },
                onToggleSectionNumbers = {
                    viewModel.saveReaderSettings(
                        uiState.readerSettings.copy(showSectionNumbers = !uiState.readerSettings.showSectionNumbers)
                    )
                },
                onGoalMinutesChange = { minutes ->
                    viewModel.saveReaderSettings(uiState.readerSettings.copy(dailyGoalMinutes = minutes))
                },
                onToggleStreakReminder = {
                    viewModel.saveReaderSettings(
                        uiState.readerSettings.copy(streakReminderEnabled = !uiState.readerSettings.streakReminderEnabled)
                    )
                },
                onToggleReduceMotion = {
                    viewModel.saveReaderSettings(
                        uiState.readerSettings.copy(reduceMotion = !uiState.readerSettings.reduceMotion)
                    )
                },
                onToggleBionicReading = {
                    viewModel.saveReaderSettings(
                        uiState.readerSettings.copy(bionicReading = !uiState.readerSettings.bionicReading)
                    )
                },
                onToggleShakeToExtend = {
                    viewModel.saveReaderSettings(
                        uiState.readerSettings.copy(shakeToExtendSleepTimer = !uiState.readerSettings.shakeToExtendSleepTimer)
                    )
                },
                onToggleCollapsibleBars = {
                    viewModel.saveReaderSettings(
                        uiState.readerSettings.copy(collapsibleReaderBars = !uiState.readerSettings.collapsibleReaderBars)
                    )
                },
                onToggleNavLabels = {
                    viewModel.saveReaderSettings(
                        uiState.readerSettings.copy(showNavLabels = !uiState.readerSettings.showNavLabels)
                    )
                }
            )
        }

        if (uiState.showPronunciationRules) {
            PronunciationRulesDialog(
                rules = uiState.pronunciationRules,
                newFind = uiState.newRuleFind,
                newReplaceWith = uiState.newRuleReplaceWith,
                onNewFindChange = { value ->
                    viewModel.updateState {
                        it.copy(
                            newRuleFind = value.take(
                                120
                            )
                        )
                    }
                },
                onNewReplaceChange = { value ->
                    viewModel.updateState {
                        it.copy(
                            newRuleReplaceWith = value.take(
                                120
                            )
                        )
                    }
                },
                onAddRule = { viewModel.addPronunciationRule() },
                onToggleRule = { rule -> viewModel.togglePronunciationRule(rule) },
                onRemoveRule = { rule -> viewModel.removePronunciationRule(rule) },
                onDismiss = { viewModel.updateState { it.copy(showPronunciationRules = false) } }
            )
        }


}
