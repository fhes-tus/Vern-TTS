package com.veritas.reader.ui

import com.veritas.reader.VeritasScreen

/** Update visibility and history together, only when a navigation action occurs. */
internal fun ReaderUiState.withVisibility(screen: VeritasScreen, visible: Boolean): ReaderUiState {
    val stack = if (visible) {
        if (screen in navStack) navStack else navStack + screen
    } else navStack.filterNot { it == screen }
    return when (screen) {
        VeritasScreen.TEXT_EDITOR -> copy(showTextEditor = visible, navStack = stack)
        VeritasScreen.FILE_BROWSER -> copy(showFileBrowser = visible, navStack = stack)
        VeritasScreen.PDF_IMPORT_TOOLS -> copy(showPdfImportTools = visible, navStack = stack)
        VeritasScreen.READER_SETTINGS -> copy(showReaderSettings = visible, navStack = stack)
        VeritasScreen.PRONUNCIATION_RULES -> copy(showPronunciationRules = visible, navStack = stack)
        VeritasScreen.VOICE_STUDIO -> copy(showVoiceStudio = visible, navStack = stack)
        VeritasScreen.NARRATION_STUDIO -> copy(showNarrationStudio = visible, navStack = stack)
        VeritasScreen.AI_STUDY_TOOLS -> copy(showAiStudyTools = visible, navStack = stack)
        VeritasScreen.AI_CENTER -> copy(showAiCenter = visible, navStack = stack)
        VeritasScreen.ASK_AI_SETTINGS -> copy(showAskAiSettings = visible, navStack = stack)
        VeritasScreen.TRANSLATION_TOOLS -> copy(showTranslationTools = visible, navStack = stack)
        VeritasScreen.SLEEP_TIMER -> copy(showSleepTimerDialog = visible, navStack = stack)
        VeritasScreen.READING_LISTS -> copy(showReadingLists = visible, navStack = stack)
        VeritasScreen.READING_HISTORY -> copy(showReadingHistory = visible, navStack = stack)
        VeritasScreen.DOCUMENT_NOTES -> copy(showDocumentNotes = visible, navStack = stack)
        VeritasScreen.SETTINGS_HUB -> copy(showSettingsHub = visible, navStack = stack)
        VeritasScreen.BACKUP_TOOLS -> copy(showBackupTools = visible, navStack = stack)
        VeritasScreen.SYNC_CENTER -> copy(showSyncCenter = visible, navStack = stack)
        VeritasScreen.APP_HEALTH -> copy(showAppHealth = visible, navStack = stack)
        VeritasScreen.TUTORIAL -> copy(showTutorial = visible, navStack = stack)
        VeritasScreen.CANVAS_VIEW -> copy(showCanvasView = visible, navStack = stack)
        VeritasScreen.GENERAL_NOTES_EDITOR -> copy(showGeneralNotesEditor = visible, navStack = stack)
        VeritasScreen.USER_MANUAL -> copy(showUserManual = visible, navStack = stack)
        VeritasScreen.ACCESSIBILITY_SETTINGS -> copy(showAccessibilitySettings = visible, navStack = stack)
        VeritasScreen.NOTES_SETTINGS -> copy(showNotesSettings = visible, navStack = stack)
    }
}
