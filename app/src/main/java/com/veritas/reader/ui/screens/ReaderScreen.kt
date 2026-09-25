package com.veritas.reader.ui.screens

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.veritas.reader.PaperToneMode
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
import com.veritas.reader.DocumentPageImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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


data class ReaderScreenState(
    val document: ReaderDocument,
    val currentIndex: Int,
    val isPlaying: Boolean,
    val isBackgroundActive: Boolean,
    val rate: Float,
    val pitch: Float,
    val statusMessage: String,
    val queueCount: Int,
    val isQueued: Boolean,
    val annotations: List<ReaderAnnotation>,
    val pronunciationRuleCount: Int,
    val readerSettings: ReaderSettings,
    val voiceSettings: VoiceSettings,
    val narrationSettings: NarrationSettings,
    val askAiSettings: AskAiSettings,
    val searchQuery: String,
    val searchMatches: List<Int>,
    val searchCursor: Int,
    val outlineEntries: List<VeritasDocumentOutlineEntry>,
    val hasCanvas: Boolean,
    val sleepTimerDurationMillis: Long,
    val sleepTimerEndsAtMillis: Long,
    val sleepTimerAction: VeritasSleepTimerAction,
    val readingListCount: Int,
    val activeDocumentReadingListCount: Int,
    val voices: List<TtsVoiceOption>,
    val readerMode: ReaderMode
)

data class ReaderPageItem(
    val pageNumber: Int,
    val text: String,
    val sentenceStartIndex: Int,
    val sentenceEndIndexExclusive: Int,
    val sentenceRanges: List<ReaderPartSentenceRange>
) {
    fun toReaderPart(partIndex: Int = 0): ReaderPart {
        return ReaderPart(
            index = partIndex,
            pageRange = ReaderPageRange(partIndex, pageNumber, pageNumber),
            sentenceStartIndex = sentenceStartIndex,
            sentenceEndIndexExclusive = sentenceEndIndexExclusive,
            text = text,
            sentenceRanges = sentenceRanges
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    state: ReaderScreenState,
    listState: LazyListState,

    hasCanvas: Boolean,
    onBackToLibrary: () -> Unit,
    onSentenceClick: (Int) -> Unit,
    onSentenceDoubleTap: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRateChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onSectionSpacingChange: (Int) -> Unit = {},
    onToggleQueue: () -> Unit,
    onToggleBookmark: (Int) -> Unit,
    onEditNote: (Int) -> Unit,
    onEditNotes: (List<Int>) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onNextSearchMatch: () -> Unit,
    onPreviousSearchMatch: () -> Unit,
    onOpenReaderSettings: () -> Unit,
    onOpenPronunciationRules: () -> Unit,
    onOpenVoiceStudio: () -> Unit,
    onOpenNarrationStudio: () -> Unit,
    onOpenDocumentNotes: () -> Unit,
    onExportStudyGuidePdf: () -> Unit = {},
    onOpenCanvas: () -> Unit,
    onOpenStudyTools: () -> Unit,
    onOpenTranslationTools: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenReadingLists: () -> Unit,
    onOpenReadingHistory: () -> Unit,
    onAskCurrentSection: () -> Unit,
    onSelectAskAiAssistant: (AiAssistantOption, String) -> Unit,
    onOpenTextEditor: () -> Unit,
    onStartRecord: () -> Unit,
    onExportAudio: () -> Unit,
    onCopySelection: (String) -> Unit,
    onShareSelection: (String) -> Unit,
    onGoogleSelection: (String) -> Unit,
    onTranslateSelection: (String) -> Unit,
    onAskAiSelection: (String) -> Unit,
    onEditSpeechSelection: (String) -> Unit,
    onReadSelection: (String) -> Unit,
    onEditExtractedSelection: (ReaderTextSelection) -> Unit,
    onPlayQueue: () -> Unit,
    onVoiceSelected: (TtsVoiceOption) -> Unit,
    onReaderModeChange: (ReaderMode) -> Unit,
    onPaperToneModeChange: (PaperToneMode) -> Unit = {},
    onAddGeneralNote: () -> Unit = {},
    onAddBookmarkGroup: (List<Int>, String) -> Unit = { _, _ -> },
    onShareToAi: (ShareScope, ReaderTextSelection?, IntRange?, Boolean) -> Unit = { _, _, _, _ -> },
    showShareToAi: Boolean = false,
    onDismissShareToAi: () -> Unit = {},
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null
) {
    val document = state.document
    val currentIndex = state.currentIndex
    val isPlaying = state.isPlaying
    val isBackgroundActive = state.isBackgroundActive
    val rate = state.rate
    val pitch = state.pitch
    val statusMessage = state.statusMessage
    val queueCount = state.queueCount
    val isQueued = state.isQueued
    val annotations = state.annotations
    state.pronunciationRuleCount
    val readerSettings = state.readerSettings
    state.voiceSettings
    val narrationSettings = state.narrationSettings
    val askAiSettings = state.askAiSettings
    val sleepTimerSnapshot = VeritasSleepTimerSnapshot(
        durationMillis = state.sleepTimerDurationMillis,
        endsAtMillis = state.sleepTimerEndsAtMillis,
        action = state.sleepTimerAction
    ).takeIf { state.sleepTimerDurationMillis > 0L && it.isActive() }
    val searchQuery = state.searchQuery
    val searchMatches = state.searchMatches
    val searchCursor = state.searchCursor
    val hasCanvas = state.hasCanvas
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    var showTools by remember { mutableStateOf(false) }
    var showDocumentDetails by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showOutline by remember { mutableStateOf(false) }
    var showRsvpSpeedReader by remember(document.id) { mutableStateOf(false) }
    var showJumpToPageDialog by remember(document.id) { mutableStateOf(false) }
    val showAudioMode = state.readerMode == ReaderMode.LISTEN
    var selectedTextSelection by remember(document.id) { mutableStateOf<ReaderTextSelection?>(null) }
    var selectedTextView by remember(document.id) { mutableStateOf<TextView?>(null) }
    var feedbackSentenceIndex by remember(document.id) { mutableStateOf<Int?>(null) }
    var colorPaletteTargetIndexes by remember { mutableStateOf<List<Int>?>(null) }
    var showShareToAiSheet by remember { mutableStateOf(false) }
    var shareToAiSelection by remember { mutableStateOf<ReaderTextSelection?>(null) }
    var shareToAiNoPrompt by remember { mutableStateOf(false) }
    // Bumped to force the auto-scroll effect to re-anchor the active sentence after events
    // that otherwise leave its keys unchanged (bookmarking, switching reader modes), which
    // previously left the page locked at the top of the section.
    var scrollTick by remember(document.id) { mutableStateOf(0) }
    var interactionTrigger by remember { mutableStateOf(0L) }
    LaunchedEffect(showShareToAi) {
        if (showShareToAi) {
            shareToAiSelection = null
            shareToAiNoPrompt = true
            showShareToAiSheet = true
        }
    }
    KeepScreenAwake(enabled = (state.readerMode == ReaderMode.TEXT), interactionTrigger = interactionTrigger)

    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            showDocumentDetails -> showDocumentDetails = false
            showTools -> showTools = false
            showJumpToPageDialog -> showJumpToPageDialog = false
            showOutline -> showOutline = false
            showBookmarks -> showBookmarks = false
            showSearch -> {
                showSearch = false
                onSearchQueryChange("")
            }
            showRsvpSpeedReader -> showRsvpSpeedReader = false
            showShareToAiSheet -> showShareToAiSheet = false
            selectedTextSelection != null -> selectedTextSelection = null
            else -> onBackToLibrary()
        }
    }

    val readerModel = remember(document.rawText, document.pageCount, document.chunks.size) {
        ReaderTextModelCache.get(document.id, document.rawText, document.pageCount)
    }

    val totalPages = remember(readerModel) {
        maxOf(
            readerModel.pageCount,
            readerModel.sentences.maxOfOrNull { it.pageNumber } ?: 1
        ).coerceAtLeast(1)
    }
    val pageItems = remember(readerModel) {
        val sentencesByPage = readerModel.sentences.groupBy { it.pageNumber }
        val maxPage = maxOf(
            totalPages,
            sentencesByPage.keys.maxOrNull() ?: 1
        )
        var runningSentenceIndex = 0
        (1..maxPage).map { pageNum ->
            val pageSentences = sentencesByPage[pageNum].orEmpty()
            if (pageSentences.isNotEmpty()) {
                val textBuilder = StringBuilder()
                val ranges = mutableListOf<ReaderPartSentenceRange>()
                pageSentences.forEachIndexed { i, sentence ->
                    val sep = if (i == 0) "" else if (sentence.separatorBefore.isNotEmpty()) sentence.separatorBefore else " "
                    textBuilder.append(sep)
                    val start = textBuilder.length
                    textBuilder.append(sentence.text)
                    val end = textBuilder.length
                    ranges.add(ReaderPartSentenceRange(sentence.index, start, end))
                }
                val pageText = textBuilder.toString()
                runningSentenceIndex = pageSentences.last().index + 1
                ReaderPageItem(
                    pageNumber = pageNum,
                    text = pageText,
                    sentenceStartIndex = pageSentences.first().index,
                    sentenceEndIndexExclusive = runningSentenceIndex,
                    sentenceRanges = ranges
                )
            } else {
                val safeIndex = runningSentenceIndex.coerceIn(0, (readerModel.sentences.size - 1).coerceAtLeast(0))
                ReaderPageItem(
                    pageNumber = pageNum,
                    text = "",
                    sentenceStartIndex = safeIndex,
                    sentenceEndIndexExclusive = safeIndex,
                    sentenceRanges = emptyList()
                )
            }
        }.ifEmpty {
            val part = readerModel.parts.firstOrNull()
            listOf(
                ReaderPageItem(
                    pageNumber = 1,
                    text = part?.text.orEmpty(),
                    sentenceStartIndex = part?.sentenceStartIndex ?: 0,
                    sentenceEndIndexExclusive = part?.sentenceEndIndexExclusive ?: 0,
                    sentenceRanges = part?.sentenceRanges.orEmpty()
                )
            )
        }
    }

    val currentPageNumber = remember(readerModel.sentences, currentIndex) {
        readerModel.sentences.getOrNull(currentIndex)?.pageNumber ?: 1
    }
    val pagerState = rememberPagerState(
        initialPage = (pageItems.indexOfFirst { it.pageNumber == currentPageNumber }.takeIf { it >= 0 } ?: (currentPageNumber - 1))
            .coerceIn(0, (pageItems.size - 1).coerceAtLeast(0))
    ) { pageItems.size.coerceAtLeast(1) }
    val coroutineScope = rememberCoroutineScope()

    // The swipe listener below is keyed on pagerState alone, whose identity never changes,
    // so it launches once and keeps whatever it captured on that first pass forever. Reading
    // playback state through these instead means it always sees the live values rather than
    // the ones from when the reader opened.
    val latestIsPlaying = rememberUpdatedState(isPlaying)
    val latestCurrentIndex = rememberUpdatedState(currentIndex)
    val latestPageItems = rememberUpdatedState(pageItems)
    val latestSentences = rememberUpdatedState(readerModel.sentences)
    val handleSentenceClick: (Int) -> Unit = remember(onSentenceClick) {
        { idx ->
            onSentenceClick(idx)
        }
    }
    val handleSentenceDoubleTap: (Int) -> Unit = remember(onSentenceDoubleTap) {
        { idx ->
            feedbackSentenceIndex = idx
            onSentenceDoubleTap(idx)
        }
    }

    // Page sync: whether playing TTS or user jumps to a section/sentence via outline/bookmarks/slider
    LaunchedEffect(currentPageNumber, pageItems.size) {
        val targetPage = (pageItems.indexOfFirst { it.pageNumber == currentPageNumber }.takeIf { it >= 0 } ?: (currentPageNumber - 1))
            .coerceIn(0, (pageItems.size - 1).coerceAtLeast(0))
        // A sync landing mid-scroll used to be dropped outright, and because this effect only
        // re-runs when the page number changes it never caught up — narration would carry on
        // reading a page the pager was no longer showing. Wait the scroll out instead: at 380ms
        // per page animation, consecutive short sentences cross boundaries faster than the
        // previous flip settles.
        if (pagerState.isScrollInProgress) {
            androidx.compose.runtime.snapshotFlow { pagerState.isScrollInProgress }
                .first { !it }
        }
        if (pagerState.currentPage != targetPage) {
            if (latestIsPlaying.value) {
                pagerState.animateScrollToPage(
                    page = targetPage,
                    animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
                )
            } else {
                pagerState.scrollToPage(targetPage)
            }
        }
    }

    // Prefetch images for adjacent pages in the background so they are instantly ready when swiping
    val docRepository = remember(context) { DocumentRepository(context.applicationContext) }
    LaunchedEffect(pagerState.currentPage, document.id) {
        val curPageIdx = pagerState.currentPage
        val pagesToPrefetch = listOfNotNull(
            pageItems.getOrNull(curPageIdx)?.pageNumber,
            pageItems.getOrNull(curPageIdx - 1)?.pageNumber,
            pageItems.getOrNull(curPageIdx + 1)?.pageNumber,
            pageItems.getOrNull(curPageIdx + 2)?.pageNumber
        ).distinct()
        withContext(Dispatchers.IO) {
            for (pageNum in pagesToPrefetch) {
                if (DocumentPageImageLoader.getCachedPageImages(document.id.orEmpty(), pageNum) == null) {
                    DocumentPageImageLoader.loadPageImages(context, docRepository, document.id.orEmpty(), pageNum)
                }
            }
        }
    }

    // Manual page swipe -> update playback sentence so it stays on the flipped page smoothly
    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.settledPage }
            .collect { pageIdx ->
                if (!pagerState.isScrollInProgress) {
                    val targetPageItem = latestPageItems.value.getOrNull(pageIdx)
                    // Pages with no sentences of their own (image-only or genuinely blank) still
                    // get a pager entry so they stay swipeable, but their sentence range is
                    // zero-width and holds the *next* page's first index. Moving the reader there
                    // would land it on a different page, and the sync effect above would then
                    // immediately flip away from the blank page the user just swiped to.
                    val hasOwnSentences = targetPageItem != null && targetPageItem.sentenceRanges.isNotEmpty()
                    if (targetPageItem != null && hasOwnSentences && !latestIsPlaying.value) {
                        val currentSentencePage =
                            latestSentences.value.getOrNull(latestCurrentIndex.value)?.pageNumber ?: 1
                        if (currentSentencePage != targetPageItem.pageNumber) {
                            onSentenceClick(targetPageItem.sentenceStartIndex)
                        }
                    }
                }
            }
    }

    // Dismiss active text selection and drop view focus whenever the page changes or swiping begins
    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.currentPage }
            .collect {
                if (selectedTextSelection != null) {
                    clearNativeTextSelection(selectedTextView)
                    selectedTextView?.clearFocus()
                    selectedTextSelection = null
                }
            }
    }
    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.isScrollInProgress }
            .collect { inProgress ->
                if (inProgress && selectedTextSelection != null) {
                    clearNativeTextSelection(selectedTextView)
                    selectedTextView?.clearFocus()
                    selectedTextSelection = null
                }
            }
    }

    val currentPart = readerModel.partForSentence(currentIndex)
    val currentPartIndex = currentPart?.index ?: 0
    val progress =
        if (document.chunks.isEmpty()) 0f else ((currentIndex + 1).toFloat() / document.chunks.size.toFloat()).coerceIn(
            0f,
            1f
        )
    val progressLabel = if (document.chunks.isEmpty()) {
        "0 / 0"
    } else {
        "Section ${currentPartIndex + 1}/${readerModel.parts.size.coerceAtLeast(1)} • Sentence ${currentIndex + 1}/${document.chunks.size}"
    }
    annotations.count { it.type == AnnotationType.BOOKMARK }
    annotations.count { it.type == AnnotationType.NOTE }
    val canGoPreviousPart = currentPartIndex > 0
    val canGoNextPart = currentPartIndex < readerModel.parts.lastIndex
    val previousPartStart =
        readerModel.parts.getOrNull(currentPartIndex - 1)?.sentenceStartIndex ?: 0
    val nextPartStart = readerModel.parts.getOrNull(currentPartIndex + 1)?.sentenceStartIndex
        ?: document.chunks.lastIndex
    val partListItemIndex = 1

    LaunchedEffect(feedbackSentenceIndex) {
        if (feedbackSentenceIndex != null) {
            delay(420)
            feedbackSentenceIndex = null
        }
    }

    val isCollapsible = state.readerSettings.collapsibleReaderBars
    var topBarVisible by remember(isCollapsible) { mutableStateOf(true) }
    var bottomBarVisible by remember(isCollapsible) { mutableStateOf(true) }

    val effectiveTopBarVisible = !isCollapsible || topBarVisible
    val effectiveBottomBarVisible = !isCollapsible || bottomBarVisible

    val topBarOffset by animateFloatAsState(
        targetValue = if (effectiveTopBarVisible) 0f else -650f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "readerTopBarOffset"
    )
    val bottomBarOffset by animateFloatAsState(
        targetValue = if (effectiveBottomBarVisible) 0f else 450f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "readerBottomBarOffset"
    )
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val animatedTopPadding by animateDpAsState(
        targetValue = if (effectiveTopBarVisible) topInset + 132.dp else topInset + 8.dp,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "animatedTopPadding"
    )
    val animatedBottomPadding by animateDpAsState(
        targetValue = if (effectiveBottomBarVisible) bottomInset + 64.dp else bottomInset + 12.dp,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "animatedBottomPadding"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(VeritasPackStyle.backgroundBrush(MaterialTheme.colorScheme))
            .monitorReadingActivity { interactionTrigger = System.currentTimeMillis() }
    ) {
        // 1. Full-screen Reading Content (Animated padding gives smooth toolbar transitions)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = animatedTopPadding, bottom = animatedBottomPadding)
        ) {
            if (document.chunks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No readable text found.")
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = true,
                        pageSpacing = 16.dp,
                        beyondViewportPageCount = 1,
                        key = { pageItems.getOrNull(it)?.pageNumber ?: it }
                    ) { pageIndex ->
                        ReaderPageItemView(
                            pageIndex = pageIndex,
                            pageItems = pageItems,
                            document = document,
                            currentIndex = currentIndex,
                            currentPageNumber = currentPageNumber,
                            isPlaying = isPlaying,
                            feedbackSentenceIndex = feedbackSentenceIndex,
                            annotations = annotations,
                            searchMatches = searchMatches,
                            searchCursor = searchCursor,
                            state = state,
                            readerSettings = readerSettings,
                            pagerState = pagerState,
                            selectedTextView = selectedTextView,
                            selectedTextSelection = selectedTextSelection,
                            readerModel = readerModel,
                            isCollapsible = isCollapsible,
                            bottomBarVisible = bottomBarVisible,
                            onToggleBars = {
                                if (isCollapsible) {
                                    topBarVisible = !topBarVisible
                                    bottomBarVisible = topBarVisible
                                }
                            },
                            onHideBars = {
                                topBarVisible = false
                                bottomBarVisible = false
                            },
                            onSentenceClick = handleSentenceClick,
                            onSentenceDoubleTap = handleSentenceDoubleTap,
                            onSentenceLongPress = { },
                            onToggleBookmark = onToggleBookmark,
                            onEditNotes = onEditNotes,
                            onTranslateSelection = onTranslateSelection,
                            onCopySelection = onCopySelection,
                            onGoogleSelection = onGoogleSelection,
                            onShareSelection = onShareSelection,
                            onEditSpeechSelection = onEditSpeechSelection,
                            onEditExtractedSelection = onEditExtractedSelection,
                            onAskAiSelection = onAskAiSelection,
                            onReadSelection = onReadSelection,
                            onSearchTriggered = { q ->
                                onSearchQueryChange(q)
                                showSearch = true
                            },
                            onSelectionChanged = { selectedTextSelection = it },
                            onTextViewBound = { selectedTextView = it },
                            onOpenColorPalette = { colorPaletteTargetIndexes = it },
                            onOpenShareToAi = { sel, noPrompt ->
                                shareToAiSelection = sel
                                shareToAiNoPrompt = noPrompt
                                showShareToAiSheet = true
                            },
                            onFontSizeChange = onFontSizeChange
                        )
            }
        }
    }
}

        // 2. Docked Top app bar (Collapsible)
        ReaderTopAppBar(
            modifier = Modifier.align(Alignment.TopCenter),
            document = document,
            progressLabel = progressLabel,
            topBarOffset = topBarOffset,
            topBarVisible = topBarVisible,
            onBackToLibrary = onBackToLibrary,
            showSearch = showSearch,
            onToggleSearch = { visible ->
                showSearch = visible
                if (!visible) {
                    onSearchQueryChange("")
                } else {
                    topBarVisible = true
                    bottomBarVisible = true
                }
            },
            onOpenDocumentNotes = onOpenDocumentNotes,
            onOpenOutline = { showOutline = true },
            showTools = showTools,
            onShowToolsChange = { showTools = it },
            showBookmarks = showBookmarks,
            onToggleBookmarks = { showBookmarks = it },
            hasCanvas = hasCanvas,
            noteCount = annotations.count { it.type == AnnotationType.NOTE },
            narrationSettings = narrationSettings,
            isQueued = isQueued,
            queueCount = queueCount,
            askAiSettings = askAiSettings,
            onOpenCanvas = onOpenCanvas,
            onOpenStudyTools = onOpenStudyTools,
            onOpenTranslationTools = onOpenTranslationTools,
            sleepTimerSnapshot = sleepTimerSnapshot,
            onOpenSleepTimer = onOpenSleepTimer,
            state = state,
            onOpenReadingLists = onOpenReadingLists,
            onOpenReadingHistory = onOpenReadingHistory,
            onAskCurrentSection = onAskCurrentSection,
            onOpenAskAi = { noPrompt ->
                shareToAiSelection = null
                shareToAiNoPrompt = noPrompt
                showShareToAiSheet = true
            },
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
            onOpenRsvpSpeedReader = { showRsvpSpeedReader = true },
            onOpenDocumentDetails = { showDocumentDetails = true },
            onOpenJumpToPage = { showJumpToPageDialog = true },
            onAddGeneralNote = onAddGeneralNote,
            onAddSentenceNote = { onEditNote(currentIndex) },
            onOpenBookmarks = { showBookmarks = true },
            onReaderModeChange = onReaderModeChange,
            pageItems = pageItems,
            pagerState = pagerState,
            coroutineScope = coroutineScope,
            progress = progress,
            onSearchQueryChange = onSearchQueryChange,
            onPaperToneModeChange = onPaperToneModeChange,
            onSentenceClick = handleSentenceClick
        )
        // 3. Floating Bottom Player Panel (Collapsible)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = bottomBarOffset }
        ) {
            PlayerPanel(
                isPlaying = isPlaying,
                isBackgroundActive = isBackgroundActive,
                statusMessage = statusMessage,
                rate = rate,
                pitch = pitch,
                fontSizeSp = readerSettings.fontSizeSp,
                sectionSpacingDp = readerSettings.sectionSpacingDp,
                queueCount = queueCount,
                canGoPrevious = currentIndex > 0,
                canGoNext = currentIndex < document.chunks.lastIndex || queueCount > 0,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onStop = onStop,
                onNext = onNext,
                onRateChange = onRateChange,
                onPitchChange = onPitchChange,
                onFontSizeChange = onFontSizeChange,
                onSectionSpacingChange = onSectionSpacingChange,
                onOpenVoiceStudio = onOpenVoiceStudio,
                onPlayQueue = onPlayQueue,
                onOpenAudioMode = { onReaderModeChange(ReaderMode.LISTEN) },
                voices = state.voices,
                voiceSettings = state.voiceSettings,
                onVoiceSelected = onVoiceSelected,
                documentId = document.id,
                onToggleDocumentMode = {
                    if (hasCanvas) {
                        onOpenCanvas()
                    } else {
                        onReaderModeChange(ReaderMode.ORIGINAL)
                    }
                }
            )
        }

        // 4. Floating Search Panel (Anchored cleanly to IME keyboard)
        if (showSearch) {
            SearchPanel(
                query = searchQuery,
                matchCount = searchMatches.size,
                currentMatch = if (searchMatches.isEmpty()) 0 else searchCursor + 1,
                onQueryChange = onSearchQueryChange,
                onPrevious = onPreviousSearchMatch,
                onNext = onNextSearchMatch,
                onClose = {
                    showSearch = false
                    onSearchQueryChange("")
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }

    ReaderDialogsAndSheetsHost(
        showAudioMode = showAudioMode,
        document = document,
        currentIndex = currentIndex,
        isPlaying = isPlaying,
        state = state,
        hasCanvas = hasCanvas,
        onPlayPause = onPlayPause,
        onNext = onNext,
        onPrevious = onPrevious,
        onReaderModeChange = onReaderModeChange,
        onToggleBookmark = onToggleBookmark,
        onRateChange = onRateChange,
        onSentenceClick = handleSentenceClick,
        onSentenceDoubleTap = handleSentenceDoubleTap,
        onOpenSleepTimer = onOpenSleepTimer,
        onOpenVoiceStudio = onOpenVoiceStudio,
        onOpenNarrationStudio = onOpenNarrationStudio,
        onExportAudio = onExportAudio,
        showOutline = showOutline,
        onDismissOutline = { showOutline = false },
        showRsvpSpeedReader = showRsvpSpeedReader,
        onDismissRsvpSpeedReader = { targetSentenceIndex ->
            showRsvpSpeedReader = false
            if (targetSentenceIndex != currentIndex) {
                handleSentenceClick(targetSentenceIndex)
            }
        },
        showDocumentDetails = showDocumentDetails,
        onDismissDocumentDetails = { showDocumentDetails = false },
        colorPaletteTargetIndexes = colorPaletteTargetIndexes,
        onDismissColorPalette = {
            colorPaletteTargetIndexes = null
            clearNativeTextSelection(selectedTextView)
        },
        onSetColorPaletteTarget = { colorPaletteTargetIndexes = it },
        showShareToAiSheet = showShareToAiSheet,
        onDismissShareToAiSheet = { showShareToAiSheet = false },
        shareToAiSelection = shareToAiSelection,
        shareToAiNoPrompt = shareToAiNoPrompt,
        onShareToAi = onShareToAi,
        onSelectAskAiAssistant = onSelectAskAiAssistant,
        onAskAiSelection = onAskAiSelection,
        showBookmarks = showBookmarks,
        onDismissBookmarks = { showBookmarks = false },
        selectedTextView = selectedTextView,
        readerModel = readerModel,
        currentPart = currentPart,
        annotations = annotations,
        onAddBookmarkGroup = onAddBookmarkGroup,
        onDismissShareToAi = onDismissShareToAi
    )

    JumpToPageDialog(
        isOpen = showJumpToPageDialog,
        currentPageIndex = pagerState.currentPage,
        pageCount = pageItems.size.coerceAtLeast(1),
        onDismiss = { showJumpToPageDialog = false },
        onConfirm = { targetPageIndex ->
            coroutineScope.launch {
                pagerState.animateScrollToPage(targetPageIndex.coerceIn(0, (pageItems.size - 1).coerceAtLeast(0)))
            }
        }
    )
}
