package com.veritas.desktop.ui.workstation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.desktop.audio.DesktopPlaybackController
import com.veritas.desktop.models.Bookmark
import com.veritas.desktop.models.DesktopDocument
import com.veritas.desktop.models.DesktopThemeType
import com.veritas.desktop.models.HabitTracker
import com.veritas.desktop.models.RichNote
import com.veritas.desktop.models.TextAnnotation
import com.veritas.desktop.models.WorkstationTab
import com.veritas.desktop.ui.components.DesktopPlaybackBar
import com.veritas.desktop.ui.components.DesktopTopBar
import com.veritas.desktop.ui.screens.DesktopBookshelfView
import com.veritas.desktop.ui.screens.DesktopHomeDashboard
import com.veritas.desktop.ui.screens.DesktopInsightsView
import com.veritas.desktop.ui.screens.DesktopNotesStudio

@Composable
fun WorkstationLayout(
    documents: List<DesktopDocument>,
    activeDocument: DesktopDocument?,
    playbackController: DesktopPlaybackController,
    bookmarks: List<Bookmark>,
    annotations: List<TextAnnotation>,
    richNotes: List<RichNote>,
    habitTracker: HabitTracker,
    onSelectDocument: (DesktopDocument) -> Unit,
    onImportFile: () -> Unit,
    onPasteText: () -> Unit,
    onDeleteDocument: (DesktopDocument) -> Unit,
    onToggleFavorite: (DesktopDocument) -> Unit,
    onToggleBookmark: (Int, String) -> Unit,
    onAddAnnotation: (Int, String) -> Unit,
    onDeleteAnnotation: (String) -> Unit,
    onSaveRichNote: (RichNote) -> Unit,
    onDeleteRichNote: (String) -> Unit,
    onLaunchRsvp: (Int) -> Unit,
    onSwitchToFloater: () -> Unit,
    onSelectTheme: (DesktopThemeType) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by playbackController.state.collectAsState()

    var activeTab by remember { mutableStateOf(WorkstationTab.HOME) }
    var isStudyStudioOpen by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Top Bar
        DesktopTopBar(
            documentTitle = if (activeTab == WorkstationTab.READER) activeDocument?.title else null,
            documentSource = if (activeTab == WorkstationTab.READER) activeDocument?.sourceLabel else null,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            showSearch = showSearch,
            onToggleSearch = {
                showSearch = !showSearch
                if (!showSearch) searchQuery = ""
            },
            currentTheme = state.readerSettings.themeType,
            onSelectTheme = onSelectTheme,
            onSwitchToFloater = onSwitchToFloater,
            onOpenSettings = { isStudyStudioOpen = true },
            onToggleSidebar = { /* Handled via Navigation Rail */ },
            onToggleStudyStudio = { isStudyStudioOpen = !isStudyStudioOpen },
            isSidebarOpen = true,
            isStudyStudioOpen = isStudyStudioOpen
        )

        // Main Center Workstation Stage with Left Navigation Rail
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Left Navigation Rail
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxHeight().width(76.dp)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                NavigationRailItem(
                    selected = activeTab == WorkstationTab.HOME,
                    onClick = { activeTab = WorkstationTab.HOME },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home", fontSize = 10.sp) }
                )

                NavigationRailItem(
                    selected = activeTab == WorkstationTab.LIBRARY,
                    onClick = { activeTab = WorkstationTab.LIBRARY },
                    icon = { Icon(Icons.Default.AutoStories, contentDescription = "Bookshelf") },
                    label = { Text("Library", fontSize = 10.sp) }
                )

                NavigationRailItem(
                    selected = activeTab == WorkstationTab.READER,
                    onClick = { activeTab = WorkstationTab.READER },
                    icon = { Icon(Icons.Default.MenuBook, contentDescription = "Reader") },
                    label = { Text("Reader", fontSize = 10.sp) }
                )

                NavigationRailItem(
                    selected = activeTab == WorkstationTab.NOTES,
                    onClick = { activeTab = WorkstationTab.NOTES },
                    icon = { Icon(Icons.Default.EditNote, contentDescription = "Notes") },
                    label = { Text("Notes", fontSize = 10.sp) }
                )

                NavigationRailItem(
                    selected = activeTab == WorkstationTab.STUDY_ANALYTICS,
                    onClick = { activeTab = WorkstationTab.STUDY_ANALYTICS },
                    icon = { Icon(Icons.Default.Insights, contentDescription = "Insights") },
                    label = { Text("Insights", fontSize = 10.sp) }
                )
            }

            VerticalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            // Main Active View Container
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when (activeTab) {
                    WorkstationTab.HOME -> {
                        DesktopHomeDashboard(
                            continueDocument = activeDocument ?: documents.firstOrNull(),
                            recentDocuments = documents,
                            isPlaying = state.isPlaying,
                            activeDocumentId = state.activeDocument?.id,
                            currentIndex = state.currentIndex,
                            habitTracker = habitTracker,
                            onOpenDocument = { doc ->
                                onSelectDocument(doc)
                                activeTab = WorkstationTab.READER
                            },
                            onTogglePlay = { doc ->
                                if (state.activeDocument?.id != doc.id) {
                                    playbackController.loadDocument(doc)
                                }
                                playbackController.togglePlay()
                            },
                            onClearContinue = { /* Optional clear */ },
                            onImportFile = onImportFile,
                            onPasteText = onPasteText,
                            onOpenBookshelf = { activeTab = WorkstationTab.LIBRARY }
                        )
                    }

                    WorkstationTab.LIBRARY -> {
                        DesktopBookshelfView(
                            documents = documents,
                            onOpenDocument = { doc ->
                                onSelectDocument(doc)
                                activeTab = WorkstationTab.READER
                            },
                            onToggleFavorite = onToggleFavorite,
                            onDeleteDocument = onDeleteDocument,
                            onImportFile = onImportFile
                        )
                    }

                    WorkstationTab.READER -> {
                        Row(modifier = Modifier.fillMaxSize()) {
                            ReadingCanvas(
                                document = activeDocument,
                                currentIndex = state.currentIndex,
                                isPlaying = state.isPlaying,
                                settings = state.readerSettings,
                                searchQuery = searchQuery,
                                bookmarks = bookmarks,
                                annotations = annotations,
                                onSentenceClick = { idx -> playbackController.jumpToSentence(idx, autoPlay = false) },
                                onSentenceDoubleTap = { idx -> playbackController.jumpToSentence(idx, autoPlay = true) },
                                onToggleBookmark = onToggleBookmark,
                                onAddAnnotation = onAddAnnotation,
                                onLaunchRsvp = onLaunchRsvp,
                                onBackToLibrary = { activeTab = WorkstationTab.LIBRARY },
                                modifier = Modifier.weight(1f)
                            )

                            // Right: Collapsible Study Studio & Inspector
                            AnimatedVisibility(
                                visible = isStudyStudioOpen,
                                enter = slideInHorizontally(initialOffsetX = { it }),
                                exit = slideOutHorizontally(targetOffsetX = { it })
                            ) {
                                StudyStudio(
                                    state = state,
                                    annotations = annotations,
                                    onAddAnnotation = { _, note -> onAddAnnotation(state.currentIndex, note) },
                                    onDeleteAnnotation = onDeleteAnnotation,
                                    onSelectVoice = { playbackController.setVoice(it) },
                                    onSetSpeed = { playbackController.setSpeed(it) },
                                    onSetPitch = { /* Pitch */ },
                                    onSetSleepTimer = { playbackController.setSleepTimer(it) },
                                    onUpdateReaderSettings = { playbackController.updateReaderSettings(it) },
                                    onCloseStudio = { isStudyStudioOpen = false }
                                )
                            }
                        }
                    }

                    WorkstationTab.NOTES -> {
                        DesktopNotesStudio(
                            notes = richNotes,
                            onSaveNote = onSaveRichNote,
                            onDeleteNote = onDeleteRichNote
                        )
                    }

                    WorkstationTab.STUDY_ANALYTICS -> {
                        DesktopInsightsView(
                            habitTracker = habitTracker,
                            documents = documents
                        )
                    }
                }
            }
        }

        // Bottom Playback HUD Bar
        DesktopPlaybackBar(
            state = state,
            onTogglePlay = { playbackController.togglePlay() },
            onNext = { playbackController.nextSentence() },
            onPrevious = { playbackController.previousSentence() },
            onSetSpeed = { playbackController.setSpeed(it) },
            onOpenVoiceMenu = { isStudyStudioOpen = true }
        )
    }
}
