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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.runtime.mutableFloatStateOf
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
import com.veritas.reader.DocumentPageImageLoader
import com.veritas.reader.PaperToneMode
import com.veritas.reader.getCanvasColors
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
import com.veritas.reader.ui.VeritasUiFont
import com.veritas.reader.ui.asTypeface
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

private class TextViewHolder(
    var renderedPage: Any? = null,
    var fontSizeSp: Int = -1,
    var extraSpacingPx: Float = -1f,
    var lineMultiplier: Float = -1f,
    var textColor: Int = 0,
    var uiFontId: String? = null,
    var onToggleBars: () -> Unit = {},
    var onSentenceDoubleTap: (Int) -> Unit = {},
    var isCollapsible: Boolean = false,
    var part: ReaderPart? = null,
    var haptics: androidx.compose.ui.hapticfeedback.HapticFeedback? = null,
    var detector: GestureDetector? = null
)

@Composable
internal fun ReaderPageItemView(
    pageIndex: Int,
    pageItems: List<ReaderPageItem>,
    document: ReaderDocument,
    currentIndex: Int,
    currentPageNumber: Int,
    isPlaying: Boolean,
    feedbackSentenceIndex: Int?,
    annotations: List<ReaderAnnotation>,
    searchMatches: List<Int>,
    searchCursor: Int,
    state: ReaderScreenState,
    readerSettings: ReaderSettings,
    pagerState: androidx.compose.foundation.pager.PagerState,
    selectedTextView: TextView?,
    selectedTextSelection: ReaderTextSelection?,
    readerModel: ReaderTextModel,
    isCollapsible: Boolean,
    bottomBarVisible: Boolean,
    onToggleBars: () -> Unit,
    onHideBars: () -> Unit,
    onSentenceClick: (Int) -> Unit,
    onSentenceDoubleTap: (Int) -> Unit,
    onSentenceLongPress: (Int) -> Unit,
    onToggleBookmark: (Int) -> Unit,
    onEditNotes: (List<Int>) -> Unit,
    onTranslateSelection: (String) -> Unit,
    onCopySelection: (String) -> Unit,
    onGoogleSelection: (String) -> Unit,
    onShareSelection: (String) -> Unit,
    onEditSpeechSelection: (String) -> Unit,
    onEditExtractedSelection: (ReaderTextSelection) -> Unit,
    onAskAiSelection: (String) -> Unit,
    onReadSelection: (String) -> Unit,
    onSearchTriggered: (String) -> Unit,
    onSelectionChanged: (ReaderTextSelection?) -> Unit,
    onTextViewBound: (TextView?) -> Unit,
    onOpenColorPalette: (List<Int>) -> Unit,
    onOpenShareToAi: (ReaderTextSelection?, Boolean) -> Unit,
    onFontSizeChange: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current

                    val pageItem = pageItems.getOrNull(pageIndex)
                    if (pageItem == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Page not found", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        val pageNumber = pageItem.pageNumber
                        val part = remember(pageItem) { pageItem.toReaderPart(pageIndex) }

                        val docRepository = remember(context) {
                            DocumentRepository(context.applicationContext)
                        }
                        var pageBitmaps by remember(document.id, pageNumber) {
                            mutableStateOf(DocumentPageImageLoader.getCachedPageImages(document.id.orEmpty(), pageNumber).orEmpty())
                        }
                        LaunchedEffect(document.id, pageNumber) {
                            if (pageBitmaps.isEmpty()) {
                                pageBitmaps = DocumentPageImageLoader.loadPageImages(context, docRepository, document.id.orEmpty(), pageNumber)
                            }
                        }

                        val paperTone = PaperToneMode.fromString(readerSettings.paperToneMode)
                        val (canvasBg, canvasTextColor) = getCanvasColors(paperTone)

                        val sentenceBackground =
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        val highlightBackground = MaterialTheme.colorScheme.tertiaryContainer
                        val feedbackBackground =
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
                        val textColor = canvasTextColor.toArgb()
                        val activeSentenceColor = sentenceBackground.toArgb()
                        val highlightColor = highlightBackground.toArgb()
                        val feedbackColor = feedbackBackground.toArgb()
                        val searchMatchColor = Color(0xFFFFD54F).copy(alpha = 0.65f).toArgb()
                        val activeSearchMatchColor = Color(0xFFFFB300).toArgb()

                        val bookmarkedSentenceIndexes = remember(annotations) {
                            annotations
                                .filter { it.type == AnnotationType.BOOKMARK }
                                .map { it.chunkIndex }
                                .toSet()
                        }
                        val bookmarkedSentences = remember(annotations) {
                            annotations
                                .filter { it.type == AnnotationType.BOOKMARK }
                                .associate { it.chunkIndex to (it.highlightColor ?: "#FFE082") }
                        }
                        val bookmarked =
                            annotations.any { it.type == AnnotationType.BOOKMARK && it.chunkIndex in part.sentenceStartIndex until part.sentenceEndIndexExclusive }
                        val note =
                            annotations.firstOrNull { it.type == AnnotationType.NOTE && it.chunkIndex == currentIndex }

                        val unplacedBitmaps = remember(pageBitmaps, part.text) {
                            pageBitmaps.filterIndexed { idx, _ ->
                                !part.text.contains("[[VERITAS_IMAGE:$idx]]")
                            }
                        }

                        var accumulatedPinchZoom by remember { mutableFloatStateOf(1f) }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                .pointerInput(readerSettings.fontSizeSp) {
                                    awaitEachGesture {
                                        do {
                                            val event = awaitPointerEvent()
                                            val pressedPointers = event.changes.filter { it.pressed }
                                            if (pressedPointers.size >= 2) {
                                                val zoomChange = event.calculateZoom()
                                                if (zoomChange != 1f) {
                                                    accumulatedPinchZoom *= zoomChange
                                                    if (accumulatedPinchZoom > 1.15f) {
                                                        val next = (readerSettings.fontSizeSp + 1).coerceAtMost(28)
                                                        if (next != readerSettings.fontSizeSp) {
                                                            onFontSizeChange(next)
                                                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                        }
                                                        accumulatedPinchZoom = 1f
                                                    } else if (accumulatedPinchZoom < 0.85f) {
                                                        val next = (readerSettings.fontSizeSp - 1).coerceAtLeast(10)
                                                        if (next != readerSettings.fontSizeSp) {
                                                            onFontSizeChange(next)
                                                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                                        }
                                                        accumulatedPinchZoom = 1f
                                                    }
                                                    pressedPointers.forEach { it.consume() }
                                                }
                                            }
                                        } while (event.changes.any { it.pressed })
                                    }
                                }
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                shape = RoundedCornerShape(14.dp),
                                color = canvasBg,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (paperTone == PaperToneMode.WARM_SEPIA) {
                                        Color(0xFF8D6E63).copy(alpha = 0.25f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
                                    }
                                ),
                                tonalElevation = 2.dp,
                                shadowElevation = 3.dp
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                    // Page Top Header (Tappable to toggle collapsible bars)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                if (isCollapsible) onToggleBars()
                                            },
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = document.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = canvasTextColor.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (bookmarked) {
                                                AnnotationPill(Icons.Filled.Bookmark, "Bookmarked")
                                                Spacer(modifier = Modifier.width(6.dp))
                                            }
                                            Text(
                                                text = "Page $pageNumber of ${pageItems.size}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (paperTone == PaperToneMode.WARM_SEPIA) Color(0xFF6D4C41) else MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        color = if (paperTone == PaperToneMode.WARM_SEPIA) Color(0xFF8D6E63).copy(alpha = 0.2f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                                    )

                                    // Scrollable content on page
                                    val pageScrollState = rememberScrollState()
                                    LaunchedEffect(pageScrollState.isScrollInProgress) {
                                        if (pageScrollState.isScrollInProgress && pageScrollState.value > 60) onHideBars()
                                    }
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .verticalScroll(pageScrollState)
                                            .padding(bottom = 16.dp)
                                    ) {
                                        if (pageNumber == 1) {
                                            val context = LocalContext.current
                                            val coverFile = remember(document.id) { CoverExtractor.coverFile(context, document.id.orEmpty()) }
                                            val coverBitmap = remember(coverFile, document.id, document.title) {
                                                coverFile?.takeIf { it.exists() }?.let { file ->
                                                    runCatching { android.graphics.BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
                                                } ?: run {
                                                    val classic = CURATED_CLASSICS.firstOrNull { c ->
                                                        document.title.contains(c.title, ignoreCase = true) ||
                                                        (document.id?.contains(c.id, ignoreCase = true) == true)
                                                    }
                                                    if (classic != null) {
                                                        runCatching {
                                                            context.assets.open("covers/${classic.id}.jpg").use { stream ->
                                                                android.graphics.BitmapFactory.decodeStream(stream)
                                                            }
                                                        }.getOrNull()
                                                    } else if (document.title.contains("Who Moved My Cheese", ignoreCase = true)) {
                                                        runCatching {
                                                            context.assets.open("covers/who_moved_my_cheese.jpg").use { stream ->
                                                                android.graphics.BitmapFactory.decodeStream(stream)
                                                            }
                                                        }.getOrNull()
                                                    } else null
                                                }
                                            }
                                            if (coverBitmap != null) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 10.dp),
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(72.dp)
                                                            .height(100.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                    ) {
                                                        androidx.compose.foundation.Image(
                                                            bitmap = coverBitmap.asImageBitmap(),
                                                            contentDescription = "Cover",
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        val isCurrentOnThisPage = currentIndex in part.sentenceStartIndex until part.sentenceEndIndexExclusive
                                        val activeSentenceOnThisPage = if (isCurrentOnThisPage) currentIndex else -1
                                        val renderedPage = remember(
                                            part.text,
                                            activeSentenceOnThisPage,
                                            isPlaying,
                                            feedbackSentenceIndex,
                                            bookmarkedSentences,
                                            searchMatches,
                                            searchCursor,
                                            activeSentenceColor,
                                            highlightColor,
                                            feedbackColor,
                                            searchMatchColor,
                                            activeSearchMatchColor,
                                            state.readerSettings.bionicReading,
                                            pageBitmaps,
                                            readerSettings.sectionSpacingDp,
                                            state.searchQuery
                                        ) {
                                            buildReaderPartSpannable(
                                                part = part,
                                                activeSentenceIndex = activeSentenceOnThisPage,
                                                feedbackSentenceIndex = feedbackSentenceIndex,
                                                highlightedSentences = bookmarkedSentences,
                                                searchMatches = searchMatches,
                                                searchCursor = searchCursor,
                                                activeSentenceColor = activeSentenceColor,
                                                defaultHighlightColor = highlightColor,
                                                feedbackColor = feedbackColor,
                                                searchMatchColor = searchMatchColor,
                                                activeSearchMatchColor = activeSearchMatchColor,
                                                bionicReading = state.readerSettings.bionicReading,
                                                context = context,
                                                pageBitmaps = pageBitmaps,
                                                sectionSpacingDp = readerSettings.sectionSpacingDp,
                                                searchQuery = state.searchQuery,
                                                textColor = textColor
                                            )
                                        }

                                        if (part.text.isNotBlank()) {
                                            AndroidView(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .onGloballyPositioned {
                                                        OnboardingController.updateBounds("reader_text_view", it)
                                                    },
                                                factory = { viewContext ->
                                                    TextView(viewContext).apply {
                                                        setTextIsSelectable(true)
                                                        isFocusable = true
                                                        isFocusableInTouchMode = true
                                                        includeFontPadding = false
                                                        setSpannableFactory(object : android.text.Spannable.Factory() {
                                                            override fun newSpannable(source: CharSequence): android.text.Spannable {
                                                                return if (source is SafeSpannableString) source else SafeSpannableString(source)
                                                            }
                                                        })
                                                        val delegator = DelegatingActionModeCallback()
                                                        customSelectionActionModeCallback = delegator
                                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                            justificationMode = android.text.Layout.JUSTIFICATION_MODE_INTER_WORD
                                                        }
                                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                                            breakStrategy = android.text.Layout.BREAK_STRATEGY_BALANCED
                                                            hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
                                                        }
                                                        val holder = TextViewHolder()
                                                        val touchSlop = android.view.ViewConfiguration.get(viewContext).scaledTouchSlop
                                                        var downX = 0f
                                                        var downY = 0f
                                                        var doubleTapHandled = false

                                                        val detector = GestureDetector(
                                                            viewContext,
                                                            object : GestureDetector.SimpleOnGestureListener() {
                                                                override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                                                                    if (this@apply.hasSelection()) {
                                                                        return false
                                                                    }
                                                                    if (holder.isCollapsible) {
                                                                        holder.onToggleBars()
                                                                        return true
                                                                    }
                                                                    return false
                                                                }

                                                                override fun onDoubleTap(event: MotionEvent): Boolean {
                                                                    val currentPart = holder.part ?: return false
                                                                    val offset = this@apply.getOffsetForPosition(
                                                                        event.x,
                                                                        event.y
                                                                    ).coerceIn(0, currentPart.text.length)
                                                                    val hitRange = currentPart.sentenceRanges.firstOrNull { offset in it.start until it.endExclusive }
                                                                    if (hitRange != null) {
                                                                        clearNativeTextSelection(this@apply)
                                                                        holder.haptics?.performHapticFeedback(HapticFeedbackType.Confirm)
                                                                        holder.onSentenceDoubleTap(hitRange.sentenceIndex)
                                                                        doubleTapHandled = true
                                                                        return true
                                                                    }
                                                                    return false
                                                                }
                                                            }
                                                        )
                                                        holder.detector = detector
                                                        tag = holder

                                                        setOnTouchListener { v, event ->
                                                            when (event.actionMasked) {
                                                                MotionEvent.ACTION_DOWN -> {
                                                                    downX = event.x
                                                                    downY = event.y
                                                                    doubleTapHandled = false
                                                                    if (this.hasSelection()) {
                                                                        v.parent?.requestDisallowInterceptTouchEvent(true)
                                                                    }
                                                                }
                                                                MotionEvent.ACTION_MOVE -> {
                                                                    val dx = kotlin.math.abs(event.x - downX)
                                                                    val dy = kotlin.math.abs(event.y - downY)
                                                                    if (this.hasSelection()) {
                                                                        if (dx > touchSlop * 2 && dx > dy * 1.5f) {
                                                                            clearNativeTextSelection(this)
                                                                            this.clearFocus()
                                                                            onSelectionChanged(null)
                                                                            v.parent?.requestDisallowInterceptTouchEvent(false)
                                                                        } else {
                                                                            v.parent?.requestDisallowInterceptTouchEvent(true)
                                                                        }
                                                                    } else {
                                                                        if (dx > touchSlop || dy > touchSlop) {
                                                                            v.parent?.requestDisallowInterceptTouchEvent(false)
                                                                        }
                                                                    }
                                                                }
                                                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                                                    if (!this.hasSelection()) {
                                                                        v.parent?.requestDisallowInterceptTouchEvent(false)
                                                                    }
                                                                }
                                                            }

                                                            detector.onTouchEvent(event)

                                                            if (doubleTapHandled) {
                                                                true
                                                            } else {
                                                                false
                                                            }
                                                        }
                                                    }
                                                },
                                                onRelease = { released ->
                                                    runCatching {
                                                        clearNativeTextSelection(released)
                                                        released.clearFocus()
                                                    }
                                                },
                                                update = { textView ->
                                                    val holder = textView.tag as? TextViewHolder ?: TextViewHolder().also { textView.tag = it }

                                                    if (currentPageNumber == pageNumber) {
                                                        onTextViewBound(textView)
                                                    } else {
                                                        if (textView.hasSelection() || textView.isFocused) {
                                                            clearNativeTextSelection(textView)
                                                            textView.clearFocus()
                                                        }
                                                    }
                                                    holder.onToggleBars = onToggleBars
                                                    holder.onSentenceDoubleTap = onSentenceDoubleTap
                                                    holder.isCollapsible = isCollapsible
                                                    holder.part = part
                                                    holder.haptics = haptics

                                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                        textView.justificationMode = android.text.Layout.JUSTIFICATION_MODE_INTER_WORD
                                                    }
                                                    if (holder.renderedPage !== renderedPage) {
                                                        if (!textView.hasSelection() || textView.text.toString() != renderedPage.toString()) {
                                                            holder.renderedPage = renderedPage
                                                            textView.text = renderedPage
                                                        }
                                                    }
                                                    if (holder.textColor != textColor) {
                                                        holder.textColor = textColor
                                                        textView.setTextColor(textColor)
                                                    }
                                                    if (holder.fontSizeSp != readerSettings.fontSizeSp) {
                                                        holder.fontSizeSp = readerSettings.fontSizeSp
                                                        textView.textSize = readerSettings.fontSizeSp.toFloat()
                                                    }
                                                    val currentFontId = readerSettings.uiFontId
                                                    if (holder.uiFontId != currentFontId) {
                                                        holder.uiFontId = currentFontId
                                                        val uiFont = VeritasUiFont.fromId(currentFontId)
                                                        textView.typeface = uiFont.asTypeface(context)
                                                    }
                                                    val extraSpacingPx = 0f
                                                    val lineMult = 1.0f + ((readerSettings.sectionSpacingDp - 6).coerceAtLeast(0) * (0.6f / 18f))
                                                    if (holder.lineMultiplier != lineMult || holder.extraSpacingPx != extraSpacingPx) {
                                                        holder.lineMultiplier = lineMult
                                                        holder.extraSpacingPx = extraSpacingPx
                                                        textView.setLineSpacing(
                                                            extraSpacingPx,
                                                            lineMult
                                                        )
                                                    }
                                                    val actionModeCb = readerSelectionActionModeCallback(
                                                        textView = textView,
                                                        part = part,
                                                        documentId = document.id,
                                                        context = context,
                                                        haptics = haptics,
                                                        bookmarkedSentenceIndexes = bookmarkedSentenceIndexes,
                                                        onSelectionChanged = { sel ->
                                                            if (sel != null) onTextViewBound(textView)
                                                            onSelectionChanged(sel)
                                                        },
                                                        onSearchQueryChange = {
                                                            onSearchTriggered(it)
                                                        },
                                                        onToggleBookmark = { idx ->
                                                            onToggleBookmark(idx)
                                                        },
                                                        onHighlightSelection = { sel ->
                                                            onOpenColorPalette(sel.sentenceIndexes)
                                                        },
                                                        onEditNotes = onEditNotes,
                                                        onTranslateSelection = onTranslateSelection,
                                                        onCopySelection = onCopySelection,
                                                        onGoogleSelection = onGoogleSelection,
                                                        onShareSelection = onShareSelection,
                                                        onShareSelectionToAi = { sel ->
                                                            onOpenShareToAi(sel, false)
                                                        },
                                                        onEditSpeechSelection = onEditSpeechSelection,
                                                        onEditExtractedSelection = onEditExtractedSelection,
                                                        onAskAiSelection = onAskAiSelection,
                                                        onReadSelection = onReadSelection
                                                    )
                                                    val delegator = (textView.customSelectionActionModeCallback as? DelegatingActionModeCallback)
                                                        ?: DelegatingActionModeCallback().also {
                                                            textView.customSelectionActionModeCallback = it
                                                        }
                                                    delegator.delegate = actionModeCb
                                                }
                                            )
                                        }

                                        if (unplacedBitmaps.isNotEmpty()) {
                                            unplacedBitmaps.forEachIndexed { imgIdx, bmp ->
                                                Spacer(modifier = Modifier.height(14.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                                        .padding(vertical = 4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    androidx.compose.foundation.Image(
                                                        bitmap = bmp.asImageBitmap(),
                                                        contentDescription = "Illustration ${imgIdx + 1}",
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .heightIn(max = 320.dp),
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(14.dp))
                                            }
                                        }

                                        if (!note?.note.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                                shape = MaterialTheme.shapes.small
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        "Note",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        note.note,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        maxLines = 3,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 450.dp)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    if (isCollapsible) onToggleBars()
                                                }
                                        )
                                    }
                                }

                                // Tactile Book Spine Crease along left edge
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .width(10.dp)
                                        .fillMaxHeight()
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                )
                            }
                        }
                    }
                }

}
