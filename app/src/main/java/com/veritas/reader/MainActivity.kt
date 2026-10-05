package com.veritas.reader

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.veritas.reader.ui.ReaderViewModel
import com.veritas.reader.ui.reloadReaderSettings

class MainActivity : ComponentActivity() {

    internal val viewModel: ReaderViewModel by viewModels()

    var onHardwarePlayPause: (() -> Unit)? = null
    var onHardwareNext: (() -> Unit)? = null
    var onHardwarePrevious: (() -> Unit)? = null
    var onIncomingShare: ((String, Uri?, Boolean) -> Unit)? = null

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val widgetAction = intent.getStringExtra(EXTRA_WIDGET_ACTION)
        val noteId = intent.getStringExtra(EXTRA_NOTE_ID)
        val documentId = intent.getStringExtra(EXTRA_DOCUMENT_ID)
        val incomingAction = intent.action
        val isShareToNotes = intent.component?.className?.endsWith("ShareToNotesActivity") == true
        val sharedText = intent.takeIf { incomingAction == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
            .orEmpty()
        val sharedUri = when (incomingAction) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }
            }
            else -> null
        }

        if (widgetAction == ACTION_CONTINUE_READING && !documentId.isNullOrBlank()) {
            PlaybackStateStore.activeDocumentId = documentId
        }

        if (widgetAction != null) {
            viewModel.updateState { state ->
                state.copy(
                    pendingWidgetAction = widgetAction,
                    pendingWidgetDocId = documentId,
                    pendingWidgetNoteId = noteId,
                    pendingImportOnStart = widgetAction == ACTION_IMPORT_DOCUMENTS
                )
            }
        }

        if (sharedText.isNotBlank() || sharedUri != null) {
            onIncomingShare?.invoke(sharedText, sharedUri, isShareToNotes)
        }

        intent.action = null
        intent.data = null
        intent.removeExtra(Intent.EXTRA_TEXT)
        intent.removeExtra(Intent.EXTRA_STREAM)
        intent.removeExtra(EXTRA_WIDGET_ACTION)
        intent.removeExtra(EXTRA_NOTE_ID)
        intent.removeExtra(EXTRA_DOCUMENT_ID)
        updateVeritasWidgets(this)
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForegrounded()
        updateVeritasWidgets(this)
    }

    override fun onResume() {
        super.onResume()
        viewModel.reloadReaderSettings()
        updateVeritasWidgets(this)
    }

    override fun onStop() {
        viewModel.onAppBackgrounded()
        super.onStop()
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isPageKey =
            event.keyCode == KeyEvent.KEYCODE_PAGE_DOWN || event.keyCode == KeyEvent.KEYCODE_PAGE_UP
        if (isPageKey && viewModel.uiState.value.activeDocument == null) {
            return super.dispatchKeyEvent(event)
        }

        val isHardwareControlKey = when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_PAGE_UP -> true

            else -> false
        }

        if (isHardwareControlKey) {
            if (event.action == KeyEvent.ACTION_UP) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    KeyEvent.KEYCODE_MEDIA_PLAY,
                    KeyEvent.KEYCODE_MEDIA_PAUSE,
                    KeyEvent.KEYCODE_HEADSETHOOK -> onHardwarePlayPause?.invoke()

                    KeyEvent.KEYCODE_MEDIA_NEXT,
                    KeyEvent.KEYCODE_PAGE_DOWN -> onHardwareNext?.invoke()

                    KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                    KeyEvent.KEYCODE_PAGE_UP -> onHardwarePrevious?.invoke()
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun maybeShowTtsEngineHint() {
        val prefs = getSharedPreferences("veritas_reader_library", MODE_PRIVATE)
        if (prefs.getBoolean("tts_engine_hint_shown", false)) return

        // 1. Check if Google TTS package is installed on the phone
        val isGoogleTtsInstalled = runCatching {
            packageManager.getPackageInfo("com.google.android.tts", 0)
            true
        }.getOrElse {
            runCatching {
                val tts = android.speech.tts.TextToSpeech(applicationContext, null)
                val engines = tts.engines?.map { it.name } ?: emptyList()
                tts.shutdown()
                engines.any { it.startsWith("com.google.android.tts") }
            }.getOrElse { false }
        }

        // If Google TTS is already installed on the phone, never prompt the user to download it
        if (isGoogleTtsInstalled) {
            prefs.edit().putBoolean("tts_engine_hint_shown", true).apply()
            return
        }

        // 2. Only show the download recommendation dialog if Google TTS is absent from device
        val engine = runCatching {
            android.provider.Settings.Secure.getString(contentResolver, "tts_default_synth")
        }.getOrNull() ?: return
        if (engine.isNotBlank() && engine.startsWith("com.google.android.tts")) return
        prefs.edit().putBoolean("tts_engine_hint_shown", true).apply()
        android.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.tts_hint_title))
            .setMessage(getString(R.string.tts_hint_message))
            .setPositiveButton(getString(R.string.tts_hint_get)) { _, _ ->
                runCatching {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("market://details?id=com.google.android.tts")
                        )
                    )
                }.onFailure {
                    runCatching {
                        startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.tts")
                            )
                        )
                    }
                }
            }
            .setNegativeButton(getString(R.string.tts_hint_dismiss), null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Field visibility: capture uncaught crashes locally and offer (user-initiated,
        // nothing automatic) to share last session's report. Then a one-time hint if the
        // device's TTS engine isn't Google's, which degrades highlighting/voice quality.
        CrashReporter.install(this)
        CrashReporter.offerPendingReport(this)
        maybeShowTtsEngineHint()
        // Weekly data-only safety-net backup (worker checks the setting itself).
        AutoBackupWorker.schedule(this)
        // Evening streak-protection nudge (worker checks the setting itself).
        StreakReminderWorker.schedule(this)
        updateVeritasWidgets(this)

        val incomingAction = intent?.action
        val openVoiceStudioOnStart = intent?.getBooleanExtra(EXTRA_OPEN_VOICE_STUDIO, false) == true
        val widgetAction = intent?.getStringExtra(EXTRA_WIDGET_ACTION)
        val noteId = intent?.getStringExtra(EXTRA_NOTE_ID)
        val documentId = intent?.getStringExtra(EXTRA_DOCUMENT_ID)
        val sharedText = intent?.takeIf { incomingAction == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
            .orEmpty()

        val sharedUri = intent?.let { incoming ->
            when (incomingAction) {
                Intent.ACTION_VIEW -> incoming.data
                Intent.ACTION_SEND -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        incoming.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        incoming.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                    }
                }

                else -> null
            }
        }

        // Handle continue reading action from widgets
        if (widgetAction == ACTION_CONTINUE_READING && !documentId.isNullOrBlank()) {
            PlaybackStateStore.activeDocumentId = documentId
        }

        if (widgetAction != null) {
            viewModel.updateState { state ->
                state.copy(
                    pendingWidgetAction = widgetAction,
                    pendingWidgetDocId = documentId,
                    pendingWidgetNoteId = noteId,
                    pendingImportOnStart = widgetAction == ACTION_IMPORT_DOCUMENTS
                )
            }
        }

        // Invalidate and clear system intent action and data to prevent loop on config change / restart
        intent?.let {
            it.action = null
            it.data = null
            it.removeExtra(Intent.EXTRA_TEXT)
            it.removeExtra(Intent.EXTRA_STREAM)
            it.removeExtra(EXTRA_WIDGET_ACTION)
            it.removeExtra(EXTRA_NOTE_ID)
            it.removeExtra(EXTRA_DOCUMENT_ID)
        }

        val isShareToNotes = intent?.component?.className?.endsWith("ShareToNotesActivity") == true

        // Synchronously load saved reader settings and initialize theme state before setContent
        // to guarantee zero-flash frame-1 rendering in the user's chosen theme, font, and pack.
        // Read only the small appearance preference here. Library migration,
        // credential migration and study loading belong to ViewModel's I/O startup.
        val initialSettings = runCatching {
            getSharedPreferences("veritas_reader_library", MODE_PRIVATE)
                .getString(DocumentRepository.KEY_READER_SETTINGS, null)
                ?.let { ReaderSettings.fromJson(org.json.JSONObject(it)) } ?: ReaderSettings()
        }.getOrDefault(ReaderSettings())
        viewModel.updateState { it.copy(readerSettings = initialSettings) }
        PlaybackStateStore.restoreFromPersistence(this)
        VeritasThemeState.themeId = initialSettings.themeId
        VeritasThemeState.themePackId = initialSettings.themePackId
        VeritasThemeState.adaptiveCover = initialSettings.adaptiveCover
        VeritasThemeState.uiFontId = initialSettings.uiFontId
        VeritasThemeState.reduceMotion = initialSettings.reduceMotion
        VeritasThemeState.reduceTransparency = initialSettings.reduceTransparency
        VeritasThemeState.glassFloatingControls = initialSettings.glassFloatingControls
        VeritasThemeState.amoledMode = initialSettings.amoledMode

        setContent {
            VeritasTheme {
                VeritasReaderApp(
                    initialSharedText = sharedText,
                    initialSharedUri = sharedUri,
                    isShareToNotes = isShareToNotes,
                    openVoiceStudioOnStart = openVoiceStudioOnStart,
                    widgetAction = widgetAction,
                    noteId = noteId
                )
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_VOICE_STUDIO = "com.veritas.reader.extra.OPEN_VOICE_STUDIO"
        const val EXTRA_WIDGET_ACTION = "com.veritas.reader.extra.WIDGET_ACTION"
        const val EXTRA_NOTE_ID = "com.veritas.reader.extra.NOTE_ID"
        const val EXTRA_DOCUMENT_ID = "com.veritas.reader.extra.DOCUMENT_ID"
        const val ACTION_NEW_NOTE = "new_note"
        const val ACTION_NEW_READING_NOTE = "new_reading_note"
        const val ACTION_SHOW_NOTES = "show_notes"
        const val ACTION_ACTIVE_READING = "active_reading"
        const val ACTION_NEW_STUDY_NOTE = "new_study_note"
        const val ACTION_VOICE_NOTE = "voice_note"
        const val ACTION_EDIT_NOTE = "edit_note"
        const val ACTION_CONTINUE_READING = "continue_reading"
        const val ACTION_SHOW_STUDY_DASHBOARD = "show_study_dashboard"
        const val ACTION_SHOW_READER_TRACKER = "show_reader_tracker"
        const val ACTION_SHOW_FLASHCARDS = "show_flashcards"
        const val ACTION_NEW_CHECKLIST_NOTE = "new_checklist_note"
        const val ACTION_NEW_REMINDER_NOTE = "new_reminder_note"
        const val ACTION_NEW_IMAGE_NOTE = "new_image_note"
        const val ACTION_OPEN_LIBRARY = "open_library"
        const val ACTION_IMPORT_DOCUMENTS = "import_documents"
    }
}

