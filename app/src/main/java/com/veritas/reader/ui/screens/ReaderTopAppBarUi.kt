package com.veritas.reader.ui.screens


import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.AiAssistantOption
import com.veritas.reader.AskAiSettings
import com.veritas.reader.NarrationSettings
import com.veritas.reader.PaperToneMode
import com.veritas.reader.R
import com.veritas.reader.ReaderDocument
import com.veritas.reader.ReaderMode
import com.veritas.reader.ReaderModeToggle
import com.veritas.reader.SlimPageSlider
import com.veritas.reader.VeritasSleepTimerSnapshot
import com.veritas.reader.ui.OnboardingController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun ReaderTopAppBar(
    modifier: Modifier = Modifier,
    document: ReaderDocument,
    progressLabel: String,
    topBarOffset: Float,
    topBarVisible: Boolean,
    onBackToLibrary: () -> Unit,
    showSearch: Boolean,
    onToggleSearch: (Boolean) -> Unit,
    onOpenDocumentNotes: () -> Unit,
    onOpenOutline: () -> Unit,
    showTools: Boolean,
    onShowToolsChange: (Boolean) -> Unit,
    showBookmarks: Boolean,
    onToggleBookmarks: (Boolean) -> Unit,
    hasCanvas: Boolean,
    noteCount: Int,
    narrationSettings: NarrationSettings,
    isQueued: Boolean,
    queueCount: Int,
    askAiSettings: AskAiSettings,
    onOpenCanvas: () -> Unit,
    onOpenStudyTools: () -> Unit,
    onOpenTranslationTools: () -> Unit,
    sleepTimerSnapshot: VeritasSleepTimerSnapshot?,
    onOpenSleepTimer: () -> Unit,
    state: ReaderScreenState,
    onOpenReadingLists: () -> Unit,
    onOpenReadingHistory: () -> Unit,
    onAskCurrentSection: () -> Unit,
    onOpenAskAi: (Boolean) -> Unit,
    onSelectAskAiAssistant: (AiAssistantOption, String) -> Unit,
    onOpenTextEditor: () -> Unit,
    onStartRecord: () -> Unit,
    onOpenReaderSettings: () -> Unit,
    onOpenVoiceStudio: () -> Unit,
    onOpenNarrationStudio: () -> Unit,
    onOpenPronunciationRules: () -> Unit,
    onExportAudio: () -> Unit,
    onExportStudyGuidePdf: () -> Unit,
    onToggleQueue: () -> Unit,
    onPlayQueue: () -> Unit,
    onOpenRsvpSpeedReader: () -> Unit,
    onOpenDocumentDetails: () -> Unit,
    onOpenJumpToPage: () -> Unit = {},
    onAddGeneralNote: () -> Unit = {},
    onAddSentenceNote: () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
    onReaderModeChange: (ReaderMode) -> Unit,
    pageItems: List<ReaderPageItem>,
    pagerState: androidx.compose.foundation.pager.PagerState,
    coroutineScope: CoroutineScope,
    progress: Float,
    onSearchQueryChange: (String) -> Unit,
    onPaperToneModeChange: (PaperToneMode) -> Unit = {},
    onSentenceClick: (Int) -> Unit = {}
) {
    var internalShowTools by remember(showTools) { mutableStateOf(showTools) }

        // 2. Docked Top app bar (Collapsible)
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = topBarOffset },
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shadowElevation = if (topBarVisible) 3.dp else 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBackToLibrary) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            document.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "${document.sourceLabel} • $progressLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val context = LocalContext.current
                    IconButton(
                        onClick = { onToggleSearch(!showSearch) }
                    ) { Icon(Icons.Outlined.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurface) }
                    IconButton(
                        onClick = onOpenOutline
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_m3_toc),
                            contentDescription = "Outline",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = {
                            val currentTone = PaperToneMode.fromString(state.readerSettings.paperToneMode)
                            val nextTone = when (currentTone) {
                                PaperToneMode.ACTIVE_THEME -> PaperToneMode.DARK
                                PaperToneMode.DARK -> PaperToneMode.NATURAL_WHITE
                                PaperToneMode.NATURAL_WHITE -> PaperToneMode.WARM_SEPIA
                                PaperToneMode.WARM_SEPIA -> PaperToneMode.ACTIVE_THEME
                            }
                            onPaperToneModeChange(nextTone)
                            val toneLabel = when (nextTone) {
                                PaperToneMode.ACTIVE_THEME -> "Default"
                                PaperToneMode.DARK -> "Dark slate"
                                PaperToneMode.NATURAL_WHITE -> "Bone"
                                PaperToneMode.WARM_SEPIA -> "Sepia"
                            }
                            Toast.makeText(context, "Paper tone: $toneLabel", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.InvertColors,
                            contentDescription = "Paper tone",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { internalShowTools = true; onShowToolsChange(true) }
                        ) { Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = MaterialTheme.colorScheme.onSurface) }
                        ReaderToolsMenu(
                            expanded = internalShowTools,
                            onDismiss = { internalShowTools = false; onShowToolsChange(false) },
                            summary = "${document.sourceLabel} • $progressLabel",
                            showSearch = showSearch,
                            showBookmarks = showBookmarks,
                            hasCanvas = hasCanvas,
                            noteCount = noteCount,
                            narrationEnabled = narrationSettings.enabled,
                            isQueued = isQueued,
                            queueCount = queueCount,
                            askAiSettings = askAiSettings,
onToggleSearch = { onToggleSearch(!showSearch) },
                            onToggleBookmarks = { onToggleBookmarks(!showBookmarks) },
                            onOpenDocumentNotes = onOpenDocumentNotes,
                            onOpenCanvas = onOpenCanvas,
                            onOpenStudyTools = onOpenStudyTools,
                            onOpenTranslationTools = onOpenTranslationTools,
                            sleepTimerLabel = sleepTimerSnapshot?.menuLabel() ?: "",
                            onOpenSleepTimer = onOpenSleepTimer,
                            readingListCount = state.readingListCount,
                            activeDocumentReadingListCount = state.activeDocumentReadingListCount,
                            onOpenReadingLists = onOpenReadingLists,
                            onOpenReadingHistory = onOpenReadingHistory,
                            onAskCurrentSection = onAskCurrentSection,
onOpenAskAi = onOpenAskAi,
                            onSelectAskAiAssistant = onSelectAskAiAssistant,
                            onOpenTextEditor = onOpenTextEditor,
                            onStartRecord = onStartRecord,
                            onOpenReaderSettings = onOpenReaderSettings,
                            onOpenVoiceStudio = onOpenVoiceStudio,
                            onOpenNarrationStudio = onOpenNarrationStudio,
                            onOpenPronunciationRules = onOpenPronunciationRules,
                            onExportAudio = onExportAudio,
                            onExportStudyGuidePdf = onExportStudyGuidePdf,
                            onToggleQueue = onToggleQueue,
                            onPlayQueue = onPlayQueue,
                            onOpenRsvpSpeedReader = onOpenRsvpSpeedReader,
                            onOpenDocumentDetails = onOpenDocumentDetails,
                            onOpenJumpToPage = onOpenJumpToPage,
                            onAddGeneralNote = onAddGeneralNote,
                            onAddSentenceNote = onAddSentenceNote,
                            onOpenBookmarks = onOpenBookmarks
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                ReaderModeToggle(
                    currentMode = state.readerMode,
                    onModeSelected = onReaderModeChange,
                    hasCanvas = hasCanvas,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { OnboardingController.updateBounds("reader_mode_toggle", it) }
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (pageItems.size > 1) {
                    SlimPageSlider(
                        pageIndex = pagerState.currentPage,
                        pageCount = pageItems.size,
                        onPageSelected = { targetPage ->
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(targetPage)
                                val pageItem = pageItems.getOrNull(targetPage)
                                if (pageItem != null && pageItem.sentenceRanges.isNotEmpty()) {
                                    onSentenceClick(pageItem.sentenceStartIndex)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .padding(horizontal = 4.dp)
                    )
                } else {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
            }
        }


}
