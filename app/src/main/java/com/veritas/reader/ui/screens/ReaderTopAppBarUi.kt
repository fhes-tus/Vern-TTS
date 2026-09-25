package com.veritas.reader.ui.screens


import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.os.Build
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ReplacementSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.ActionMode
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import com.veritas.reader.aiAssistantIcon
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.ui.res.painterResource
import com.veritas.reader.R
import com.veritas.reader.PaperToneMode
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Spellcheck
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.luminance
import androidx.compose.material.icons.outlined.Info
import com.veritas.reader.DocumentRepository
import com.veritas.reader.blendColors
import com.veritas.reader.ShareScope
import com.veritas.reader.AiAssistantOption
import com.veritas.reader.AnnotationPill
import com.veritas.reader.AnnotationType
import com.veritas.reader.AskAiSettings
import com.veritas.reader.BouncyFilledButton
import com.veritas.reader.BouncyTextButton
import com.veritas.reader.BrandMark
import com.veritas.reader.NarrationSettings
import com.veritas.reader.PlaybackActions
import com.veritas.reader.PlaybackService
import com.veritas.reader.ReaderAnnotation
import com.veritas.reader.ReaderDocument
import com.veritas.reader.ReaderPageRange
import com.veritas.reader.ReaderPart
import com.veritas.reader.ReaderPartSentenceRange
import com.veritas.reader.ReaderSettings
import com.veritas.reader.CoverExtractor
import com.veritas.reader.ReaderTextModelCache
import com.veritas.reader.ResolvedVeritasFeature
import com.veritas.reader.VeritasDocumentOutlineEntry
import com.veritas.reader.VeritasFeatureContext
import com.veritas.reader.VeritasFeatureId
import com.veritas.reader.VeritasFeatureRegistry
import com.veritas.reader.VeritasFeatureSurface
import com.veritas.reader.VeritasSleepTimerAction
import com.veritas.reader.VeritasSleepTimerFormatter
import com.veritas.reader.VeritasSleepTimerPresets
import com.veritas.reader.VeritasSleepTimerRequest
import com.veritas.reader.VeritasSleepTimerSnapshot
import com.veritas.reader.VoiceSettings
import com.veritas.reader.TtsVoiceOption
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.ReaderMode
import com.veritas.reader.ReaderModeToggle
import com.veritas.reader.aiAssistantOptions
import com.veritas.reader.capWords
import com.veritas.reader.installedPackageForOption
import com.veritas.reader.openPlayStoreForPackage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import androidx.compose.ui.layout.onGloballyPositioned
import com.veritas.reader.ui.OnboardingController
import com.veritas.reader.ui.rememberSliderHaptics
import com.veritas.reader.ui.VeritasSleekSlider
import com.veritas.reader.ui.VeritasThinRoundSlider
import com.veritas.reader.SlimPageSlider
import java.util.Locale
import kotlin.math.roundToInt



import com.veritas.reader.ReaderTextModel


import kotlinx.coroutines.CoroutineScope

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
