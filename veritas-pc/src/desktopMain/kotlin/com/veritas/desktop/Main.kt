package com.veritas.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.veritas.desktop.audio.DesktopPlaybackController
import com.veritas.desktop.models.AppWindowMode
import com.veritas.desktop.models.TextAnnotation
import com.veritas.desktop.parser.DocumentParser
import com.veritas.desktop.storage.DesktopStorage
import com.veritas.desktop.system.ClipboardHelper
import com.veritas.desktop.system.DesktopSystemTray
import com.veritas.desktop.system.GlobalHotkeyManager
import com.veritas.desktop.ui.floater.FloaterCapsuleView
import com.veritas.desktop.ui.screens.DesktopRsvpReader
import com.veritas.desktop.ui.theme.VeritasDesktopTheme
import com.veritas.desktop.ui.workstation.WorkstationLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDropEvent
import java.io.File

fun main() {
    // Suppress PDFBox log noise
    java.util.logging.Logger.getLogger("org.apache.pdfbox").level = java.util.logging.Level.SEVERE
    java.util.logging.Logger.getLogger("org.apache.fontbox").level = java.util.logging.Level.SEVERE

    application {
        val coroutineScope = rememberCoroutineScope()
        val playbackController = remember { DesktopPlaybackController(coroutineScope) }
        val playbackState by playbackController.state.collectAsState()

        var windowMode by remember { mutableStateOf(AppWindowMode.WORKSTATION) }
        var documents by remember { mutableStateOf(DesktopStorage.loadLibrary()) }
        var richNotes by remember { mutableStateOf(DesktopStorage.loadRichNotes()) }
        var habitTracker by remember { mutableStateOf(DesktopStorage.loadHabitTracker()) }

        val activeDocument = playbackState.activeDocument ?: documents.firstOrNull()

        var bookmarks by remember(activeDocument?.id) {
            mutableStateOf(activeDocument?.id?.let { DesktopStorage.loadBookmarks(it) } ?: emptyList())
        }
        var annotations by remember(activeDocument?.id) {
            mutableStateOf(activeDocument?.id?.let { DesktopStorage.loadAnnotations(it) } ?: emptyList())
        }

        // Active RSVP mode state
        var rsvpActive by remember { mutableStateOf(false) }
        var rsvpSentenceStartIndex by remember { mutableStateOf(0) }

        // Initialize active document on first launch
        LaunchedEffect(Unit) {
            if (playbackState.activeDocument == null && documents.isNotEmpty()) {
                playbackController.loadDocument(documents.first())
            }
        }

        // Reading Habit Time Tracker: increments daily minutes every 60s while audio is playing
        LaunchedEffect(playbackState.isPlaying) {
            while (playbackState.isPlaying) {
                delay(60_000L)
                DesktopStorage.recordReadingMinutes(1)
                habitTracker = DesktopStorage.loadHabitTracker()
            }
        }

        // Initialize System Tray and Global Hotkeys
        LaunchedEffect(Unit) {
            DesktopSystemTray.initialize(
                onOpenWorkstation = { windowMode = AppWindowMode.WORKSTATION },
                onOpenFloater = { windowMode = AppWindowMode.FLOATER },
                onReadClipboard = {
                    val clipText = ClipboardHelper.captureSelectionFromActiveApp()
                    if (clipText.isNotBlank()) {
                        val tempDoc = DocumentParser.parseFromRawText("Quick Selection", clipText, "Captured Text")
                        playbackController.loadDocument(tempDoc)
                        windowMode = AppWindowMode.FLOATER
                        playbackController.play()
                    }
                },
                onTogglePlay = { playbackController.togglePlay() },
                onExit = { exitApplication() }
            )

            GlobalHotkeyManager.onReadSelectionTriggered = {
                val captured = ClipboardHelper.captureSelectionFromActiveApp()
                if (captured.isNotBlank()) {
                    val tempDoc = DocumentParser.parseFromRawText("Quick Selection", captured, "Captured Text")
                    playbackController.loadDocument(tempDoc)
                    windowMode = AppWindowMode.FLOATER
                    playbackController.play()
                }
            }

            GlobalHotkeyManager.onToggleFloaterTriggered = {
                windowMode = if (windowMode == AppWindowMode.FLOATER) AppWindowMode.WORKSTATION else AppWindowMode.FLOATER
            }

            GlobalHotkeyManager.start()
        }

        // Handle file import
        fun handleImportFile(file: File) {
            coroutineScope.launch(Dispatchers.IO) {
                val parsed = DocumentParser.parseFile(file)
                val updated = listOf(parsed) + documents.filter { it.id != parsed.id }
                DesktopStorage.saveLibrary(updated)
                launch(Dispatchers.Main) {
                    documents = updated
                    playbackController.loadDocument(parsed)
                }
            }
        }

        fun openNativeFilePicker() {
            val dialog = FileDialog(null as Frame?, "Select Document or Book to Read", FileDialog.LOAD)
            dialog.setFilenameFilter { _, name ->
                val ext = name.substringAfterLast('.', "").lowercase()
                ext in listOf("pdf", "epub", "docx", "txt", "md", "markdown", "html")
            }
            dialog.isVisible = true
            val selectedFile = dialog.file?.let { File(dialog.directory, it) }
            if (selectedFile != null && selectedFile.exists()) {
                handleImportFile(selectedFile)
            }
        }

        fun handlePasteText() {
            val clipboardText = ClipboardHelper.getClipboardText()
            if (clipboardText.isNotBlank()) {
                val pastedDoc = DocumentParser.parseFromRawText("Pasted Text", clipboardText, "Pasted Content")
                val updated = listOf(pastedDoc) + documents
                DesktopStorage.saveLibrary(updated)
                documents = updated
                playbackController.loadDocument(pastedDoc)
            }
        }

        // Render Window Mode
        when (windowMode) {
            AppWindowMode.WORKSTATION -> {
                val workstationWindowState = rememberWindowState(
                    size = DpSize(1320.dp, 880.dp),
                    position = WindowPosition(Alignment.Center)
                )

                Window(
                    onCloseRequest = ::exitApplication,
                    state = workstationWindowState,
                    title = "Veritas Reader Desktop",
                    onKeyEvent = { event ->
                        if (event.type == KeyEventType.KeyDown && !rsvpActive) {
                            when (event.key) {
                                Key.Spacebar -> { playbackController.togglePlay(); true }
                                Key.DirectionRight -> { playbackController.nextSentence(); true }
                                Key.DirectionLeft -> { playbackController.previousSentence(); true }
                                Key.DirectionUp -> { playbackController.adjustSpeed(0.1f); true }
                                Key.DirectionDown -> { playbackController.adjustSpeed(-0.1f); true }
                                else -> false
                            }
                        } else false
                    }
                ) {
                    // Set up Drag & Drop for document files
                    SideEffect {
                        window.dropTarget = object : DropTarget() {
                            override fun drop(evt: DropTargetDropEvent) {
                                evt.acceptDrop(DnDConstants.ACTION_COPY)
                                val droppedFiles = evt.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
                                val firstFile = droppedFiles?.firstOrNull() as? File
                                if (firstFile != null && firstFile.exists()) {
                                    handleImportFile(firstFile)
                                }
                            }
                        }
                    }

                    VeritasDesktopTheme(themeType = playbackState.readerSettings.themeType) {
                        if (rsvpActive && activeDocument != null) {
                            DesktopRsvpReader(
                                document = activeDocument,
                                initialSentenceIndex = rsvpSentenceStartIndex,
                                onClose = { targetIndex ->
                                    rsvpActive = false
                                    playbackController.jumpToSentence(targetIndex, autoPlay = false)
                                }
                            )
                        } else {
                            WorkstationLayout(
                                documents = documents,
                                activeDocument = activeDocument,
                                playbackController = playbackController,
                                bookmarks = bookmarks,
                                annotations = annotations,
                                richNotes = richNotes,
                                habitTracker = habitTracker,
                                onSelectDocument = { doc ->
                                    playbackController.loadDocument(doc)
                                    bookmarks = DesktopStorage.loadBookmarks(doc.id)
                                    annotations = DesktopStorage.loadAnnotations(doc.id)
                                },
                                onImportFile = ::openNativeFilePicker,
                                onPasteText = ::handlePasteText,
                                onDeleteDocument = { doc ->
                                    val updated = documents.filter { it.id != doc.id }
                                    DesktopStorage.saveLibrary(updated)
                                    documents = updated
                                    if (activeDocument?.id == doc.id) {
                                        documents.firstOrNull()?.let { playbackController.loadDocument(it) }
                                    }
                                },
                                onToggleFavorite = { doc ->
                                    val updated = documents.map { if (it.id == doc.id) it.copy(isFavorite = !it.isFavorite) else it }
                                    DesktopStorage.saveLibrary(updated)
                                    documents = updated
                                },
                                onToggleBookmark = { chunkIdx, preview ->
                                    activeDocument?.let { doc ->
                                        DesktopStorage.toggleBookmark(doc.id, chunkIdx, preview)
                                        bookmarks = DesktopStorage.loadBookmarks(doc.id)
                                    }
                                },
                                onAddAnnotation = { chunkIdx, noteText ->
                                    activeDocument?.let { doc ->
                                        val newAnnotation = TextAnnotation(
                                            documentId = doc.id,
                                            chunkIndex = chunkIdx,
                                            selectedText = doc.chunks.getOrNull(chunkIdx) ?: "",
                                            noteContent = noteText
                                        )
                                        DesktopStorage.saveAnnotation(newAnnotation)
                                        annotations = DesktopStorage.loadAnnotations(doc.id)
                                    }
                                },
                                onDeleteAnnotation = { annotationId ->
                                    DesktopStorage.deleteAnnotation(annotationId)
                                    activeDocument?.let { doc ->
                                        annotations = DesktopStorage.loadAnnotations(doc.id)
                                    }
                                },
                                onSaveRichNote = { note ->
                                    DesktopStorage.saveRichNote(note)
                                    richNotes = DesktopStorage.loadRichNotes()
                                },
                                onDeleteRichNote = { noteId ->
                                    DesktopStorage.deleteRichNote(noteId)
                                    richNotes = DesktopStorage.loadRichNotes()
                                },
                                onLaunchRsvp = { sentenceIdx ->
                                    rsvpSentenceStartIndex = sentenceIdx
                                    rsvpActive = true
                                },
                                onSwitchToFloater = { windowMode = AppWindowMode.FLOATER },
                                onSelectTheme = { newTheme ->
                                    playbackController.updateReaderSettings(playbackState.readerSettings.copy(themeType = newTheme))
                                }
                            )
                        }
                    }
                }
            }

            AppWindowMode.FLOATER -> {
                val floaterWindowState = rememberWindowState(
                    size = DpSize(520.dp, 150.dp),
                    position = WindowPosition(Alignment.BottomEnd)
                )

                Window(
                    onCloseRequest = { windowMode = AppWindowMode.WORKSTATION },
                    state = floaterWindowState,
                    title = "Veritas Floater",
                    alwaysOnTop = true,
                    resizable = false,
                    undecorated = true,
                    transparent = true,
                    onKeyEvent = { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.Spacebar -> { playbackController.togglePlay(); true }
                                Key.DirectionRight -> { playbackController.nextSentence(); true }
                                Key.DirectionLeft -> { playbackController.previousSentence(); true }
                                else -> false
                            }
                        } else false
                    }
                ) {
                    VeritasDesktopTheme(themeType = playbackState.readerSettings.themeType) {
                        FloaterCapsuleView(
                            state = playbackState,
                            onTogglePlay = { playbackController.togglePlay() },
                            onNext = { playbackController.nextSentence() },
                            onPrevious = { playbackController.previousSentence() },
                            onSetSpeed = { playbackController.setSpeed(it) },
                            onExpandToWorkstation = { windowMode = AppWindowMode.WORKSTATION },
                            onReadClipboard = {
                                val captured = ClipboardHelper.captureSelectionFromActiveApp()
                                if (captured.isNotBlank()) {
                                    val tempDoc = DocumentParser.parseFromRawText("Quick Selection", captured, "Captured Text")
                                    playbackController.loadDocument(tempDoc)
                                    playbackController.play()
                                }
                            },
                            onCloseFloater = { windowMode = AppWindowMode.WORKSTATION }
                        )
                    }
                }
            }
        }
    }
}
