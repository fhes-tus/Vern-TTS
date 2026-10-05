package com.veritas.reader.ui.screens


import android.annotation.SuppressLint
import android.graphics.Paint
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.veritas.reader.AnnotationPill
import com.veritas.reader.AnnotationType
import com.veritas.reader.CoverExtractor
import com.veritas.reader.DocumentPageImageLoader
import com.veritas.reader.InlineIllustrationPlanner
import com.veritas.reader.DocumentRepository
import com.veritas.reader.PaperToneMode
import com.veritas.reader.PlaybackStateStore
import com.veritas.reader.ReaderAnnotation
import com.veritas.reader.ReaderDocument
import com.veritas.reader.ReaderPart
import com.veritas.reader.ReaderPartSentenceRange
import com.veritas.reader.ReaderSettings
import com.veritas.reader.ReaderTextModel
import com.veritas.reader.getCanvasColors
import com.veritas.reader.ui.OnboardingController
import com.veritas.reader.ui.VeritasUiFont
import com.veritas.reader.ui.asTypeface
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@SuppressLint("WrongConstant")
private fun TextView.useNaturalWordSpacing() {
    // Justification stretches spaces unevenly on narrow reader pages.
    justificationMode = android.text.Layout.JUSTIFICATION_MODE_NONE
}

private class TextViewHolder(
    var touchInProgress: Boolean = false,
    var renderedPage: Any? = null,
    var presentationSpans: List<Any> = emptyList(),
    var fontSizeSp: Int = -1,
    var extraSpacingPx: Float = -1f,
    var lineMultiplier: Float = -1f,
    var textColor: Int = 0,
    var uiFontId: String? = null,
    var targetWeight: Int = -1,
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
    onTranslateSelection: (ReaderTextSelection) -> Unit,
    onCopySelection: (String) -> Unit,
    onGoogleSelection: (ReaderTextSelection) -> Unit,
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
    androidx.compose.ui.platform.LocalDensity.current

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
                        var pageMedia by remember(document.id, pageNumber) {
                            mutableStateOf(DocumentPageImageLoader.getCachedPageMedia(document.id.orEmpty(), pageNumber)
                                ?: com.veritas.reader.DocumentPageMedia(emptyList()))
                        }
                        val pageBitmaps = pageMedia.bitmaps
                        var inlineFollowingTexts by remember(document.id, pageNumber) { mutableStateOf(pageMedia.followingTexts) }
                        var pageMediaReady by remember(document.id, pageNumber) {
                            mutableStateOf(DocumentPageImageLoader.getCachedPageImages(document.id.orEmpty(), pageNumber) != null)
                        }
                        LaunchedEffect(document.id, pageNumber) {
                            if (!pageMediaReady) {
                                // Freeze placement when prose becomes visible. Slow media
                                // falls below this visit's text; cached revisits can use its
                                // inline anchors without inserting height during reading.
                                try {
                                    coroutineScope {
                                        val loading = async { DocumentPageImageLoader.loadPageMedia(context, docRepository, document.id.orEmpty(), pageNumber) }
                                        val early = kotlinx.coroutines.withTimeoutOrNull(300L) { loading.await() }
                                        if (early != null) {
                                            pageMedia = early
                                            inlineFollowingTexts = early.followingTexts
                                        }
                                        pageMediaReady = true
                                        if (early == null) pageMedia = loading.await()
                                    }
                                } finally {
                                    pageMediaReady = true
                                }
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

                        val imageAnchors = remember(part, inlineFollowingTexts) {
                            InlineIllustrationPlanner.anchors(part, inlineFollowingTexts)
                        }
                        val placedImageIndexes = imageAnchors.map { it.imageIndex }.toSet()
                        val unplacedBitmaps = pageBitmaps.filterIndexed { index, _ -> index !in placedImageIndexes }.filterNotNull()

                        val latestToggleBars by rememberUpdatedState(onToggleBars)
                        var accumulatedPinchZoom by remember { mutableFloatStateOf(1f) }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                .pointerInput(isCollapsible) {
                                    if (!isCollapsible) return@pointerInput
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                        val origin = down.position
                                        var eligible = true
                                        var released = false
                                        do {
                                            val event = awaitPointerEvent(PointerEventPass.Final)
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                            if (event.changes.size > 1 || change == null) eligible = false
                                            if (change != null) {
                                                if (change.isConsumed || (change.position - origin).getDistance() > viewConfiguration.touchSlop ||
                                                    change.uptimeMillis - down.uptimeMillis >= viewConfiguration.longPressTimeoutMillis) eligible = false
                                                released = !change.pressed
                                            }
                                        } while (event.changes.any { it.pressed })
                                        if (eligible && released) latestToggleBars()
                                    }
                                }
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
                                                            haptics.performHapticFeedback(
                                                                HapticFeedbackType.TextHandleMove)
                                                        }
                                                        accumulatedPinchZoom = 1f
                                                    } else if (accumulatedPinchZoom < 0.85f) {
                                                        val next = (readerSettings.fontSizeSp - 1).coerceAtLeast(10)
                                                        if (next != readerSettings.fontSizeSp) {
                                                            onFontSizeChange(next)
                                                            haptics.performHapticFeedback(
                                                                HapticFeedbackType.TextHandleMove)
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
                                    ) pageContent@{
                                        if (!pageMediaReady) {
                                            Text("Preparing page illustrations…", color = canvasTextColor,
                                                modifier = Modifier.padding(vertical = 24.dp))
                                            return@pageContent
                                        }
                                        if (pageItem.text.isBlank()) {
                                            Text(
                                                text = if (document.partial) {
                                                    "Only part of this document has been extracted. No text is available for this page yet."
                                                } else {
                                                    "No readable text was extracted for this page."
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = canvasTextColor.copy(alpha = 0.7f),
                                                modifier = Modifier.padding(vertical = 24.dp)
                                            )
                                        }
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

                                        val currentFontId = readerSettings.uiFontId
                                        val uiFont = remember(currentFontId) { VeritasUiFont.fromId(currentFontId) }
                                        val boldTypeface = remember(uiFont, context) {
                                            uiFont.asTypeface(context, weight = 700, isBold = true)
                                        }

                                        val isCurrentOnThisPage = currentIndex in part.sentenceStartIndex until part.sentenceEndIndexExclusive
                                        val activeSentenceOnThisPage = if (isCurrentOnThisPage) currentIndex else -1
                                        val segments = remember(part, imageAnchors) { parsePageContentSegments(part, imageAnchors) }
                                        val segmentTopPx = remember { mutableStateMapOf<Int, Float>() }
                                        val segmentTextViews = remember { mutableMapOf<Int, TextView>() }
                                        var activeTextView by remember { mutableStateOf<TextView?>(null) }
                                        var textViewContentTopPx by remember { mutableFloatStateOf(0f) }

                                        LaunchedEffect(activeSentenceOnThisPage) {
                                            if (selectedTextSelection != null || segmentTextViews.values.any { it.hasSelection() }) return@LaunchedEffect
                                            if (activeSentenceOnThisPage >= 0) {
                                                val activeSegment = segments.firstOrNull { segment ->
                                                    when (segment) {
                                                        is PageContentSegment.Prose -> activeSentenceOnThisPage in segment.subPart.sentenceStartIndex until segment.subPart.sentenceEndIndexExclusive
                                                        is PageContentSegment.Table -> activeSentenceOnThisPage in segment.sentenceStartIndex until segment.sentenceEndIndexExclusive
                                                        is PageContentSegment.Image -> false
                                                    }
                                                }
                                                if (activeSegment is PageContentSegment.Table) {
                                                    val tableTop = segmentTopPx[activeSegment.segmentIndex] ?: 0f
                                                    val currentScroll = pageScrollState.value
                                                    val viewportHeight = pageScrollState.viewportSize
                                                    val isVisible = viewportHeight > 0 && tableTop >= currentScroll && tableTop <= (currentScroll + viewportHeight - 80)
                                                    if (!isVisible) {
                                                        pageScrollState.animateScrollTo((tableTop - 60).toInt().coerceAtLeast(0))
                                                    }
                                                } else if (activeSegment is PageContentSegment.Prose) {
                                                    val tv = segmentTextViews[activeSegment.segmentIndex] ?: return@LaunchedEffect
                                                    var attempts = 0
                                                    while (tv.layout == null && attempts < 10) {
                                                        kotlinx.coroutines.delay(30)
                                                        attempts++
                                                    }
                                                    val layout = tv.layout ?: return@LaunchedEffect
                                                    val range = activeSegment.subPart.sentenceRanges.firstOrNull { it.sentenceIndex == activeSentenceOnThisPage } ?: return@LaunchedEffect
                                                    val safeOffset = range.start.coerceIn(0, (tv.text?.length ?: 1) - 1)
                                                    val line = layout.getLineForOffset(safeOffset)
                                                    val lineTop = layout.getLineTop(line)
                                                    val lineBottom = layout.getLineBottom(line)
                                                    val segmentTop = segmentTopPx[activeSegment.segmentIndex] ?: textViewContentTopPx
                                                    val sentenceY = (segmentTop + lineTop).toInt()
                                                    val currentScroll = pageScrollState.value
                                                    val viewportHeight = pageScrollState.viewportSize
                                                    val isVisible = viewportHeight > 0 &&
                                                        sentenceY >= currentScroll &&
                                                        (sentenceY + (lineBottom - lineTop)) <= (currentScroll + viewportHeight - 80)
                                                    if (!isVisible) {
                                                        val targetY = (sentenceY - 60).coerceAtLeast(0)
                                                        pageScrollState.animateScrollTo(targetY)
                                                    }
                                                }
                                            }
                                        }

                                        segments.forEach { segment ->
                                            androidx.compose.runtime.key(segment.segmentIndex, segment::class) {
                                            when (segment) {
                                                is PageContentSegment.Image -> ReaderInlineIllustration(pageBitmaps.getOrNull(segment.imageIndex), segment.imageIndex)
                                                is PageContentSegment.Prose -> {
                                                    ReaderProseBlock(
                                                        subPart = segment.subPart,
                                                        segmentIndex = segment.segmentIndex,
                                                        activeSentenceOnThisPage = activeSentenceOnThisPage,
                                                        isPlaying = isPlaying,
                                                        feedbackSentenceIndex = feedbackSentenceIndex,
                                                        bookmarkedSentences = bookmarkedSentences,
                                                        searchMatches = searchMatches,
                                                        searchCursor = searchCursor,
                                                        activeSentenceColor = activeSentenceColor,
                                                        highlightColor = highlightColor,
                                                        feedbackColor = feedbackColor,
                                                        searchMatchColor = searchMatchColor,
                                                        activeSearchMatchColor = activeSearchMatchColor,
                                                        state = state,
                                                        readerSettings = readerSettings,
                                                        paperTone = paperTone,
                                                        pageBitmaps = pageBitmaps,
                                                        textColor = textColor,
                                                        boldTypeface = boldTypeface,
                                                        currentPageNumber = currentPageNumber,
                                                        isVisiblePage = pagerState.currentPage == pageIndex,
                                                        pageNumber = pageNumber,
                                                        document = document,
                                                        bookmarkedSentenceIndexes = bookmarkedSentenceIndexes,
                                                        isCollapsible = isCollapsible,
                                                        onToggleBars = onToggleBars,
                                                        onSentenceDoubleTap = onSentenceDoubleTap,
                                                        onTextViewBound = onTextViewBound,
                                                        onSelectionChanged = onSelectionChanged,
                                                        onSearchTriggered = onSearchTriggered,
                                                        onToggleBookmark = onToggleBookmark,
                                                        onOpenColorPalette = onOpenColorPalette,
                                                        onEditNotes = onEditNotes,
                                                        onTranslateSelection = onTranslateSelection,
                                                        onCopySelection = onCopySelection,
                                                        onGoogleSelection = onGoogleSelection,
                                                        onShareSelection = onShareSelection,
                                                        onOpenShareToAi = onOpenShareToAi,
                                                        onEditSpeechSelection = onEditSpeechSelection,
                                                        onEditExtractedSelection = onEditExtractedSelection,
                                                        onAskAiSelection = onAskAiSelection,
                                                        onReadSelection = onReadSelection,
                                                        onGloballyPositionedTop = { top ->
                                                            segmentTopPx[segment.segmentIndex] = top
                                                            if (segment.segmentIndex == 0) textViewContentTopPx = top
                                                        },
                                                        onTextViewActive = { tv ->
                                                            activeTextView = tv
                                                            segmentTextViews[segment.segmentIndex] = tv
                                                        }
                                                    )
                                                }
                                                is PageContentSegment.Table -> {
                                                    ReaderComposeTableCard(
                                                        table = segment,
                                                        activeSentenceIndex = activeSentenceOnThisPage,
                                                        paperTone = paperTone,
                                                        canvasTextColor = canvasTextColor,
                                                        onSentenceClick = onSentenceClick,
                                                        onSentenceDoubleTap = onSentenceDoubleTap,
                                                        onSkipTable = {
                                                            onSentenceClick(segment.sentenceEndIndexExclusive.coerceAtMost((document.chunks.size - 1).coerceAtLeast(0)))
                                                        },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .onGloballyPositioned {
                                                                segmentTopPx[segment.segmentIndex] = it.positionInParent().y + pageScrollState.value
                                                            }
                                                    )
                                                }
                                            }
                                        }

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

@Composable
internal fun ReaderInlineIllustration(bitmap: android.graphics.Bitmap?, imageIndex: Int) {
    // The frame exists before decoding and keeps the same height afterwards.
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(280.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = bitmap.asImageBitmap(), contentDescription = "Illustration ${imageIndex + 1}",
                modifier = Modifier.fillMaxSize().padding(6.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
        } else {
            Text("Illustration ${imageIndex + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal sealed class PageContentSegment {
    abstract val segmentIndex: Int
    data class Image(val imageIndex: Int, override val segmentIndex: Int) : PageContentSegment()
    data class Prose(
        val subPart: ReaderPart,
        override val segmentIndex: Int
    ) : PageContentSegment()

    data class Table(
        val rows: List<List<String>>,
        val sentenceStartIndex: Int,
        val sentenceEndIndexExclusive: Int,
        override val segmentIndex: Int,
        val rowSentenceIndices: List<Int> = emptyList()
    ) : PageContentSegment()
}

internal fun parsePageContentSegments(
    part: ReaderPart,
    anchors: List<com.veritas.reader.InlineIllustrationAnchor> = InlineIllustrationPlanner.anchors(part)
): List<PageContentSegment> {
    if (anchors.isEmpty()) return parseTableContentSegments(part)
    val result = mutableListOf<PageContentSegment>()
    var cursor = 0
    fun proseUntil(end: Int) {
        if (end > cursor && part.text.substring(cursor, end).isNotBlank()) {
            result.addAll(parseTableContentSegments(createSubPart(part, cursor, end, 0).subPart))
        }
    }
    anchors.forEach { anchor ->
        if (anchor.start >= cursor && anchor.endExclusive <= part.text.length) {
            proseUntil(anchor.start)
            result.add(PageContentSegment.Image(anchor.imageIndex, 0))
            cursor = anchor.endExclusive
        }
    }
    proseUntil(part.text.length)
    return result.mapIndexed { index, segment ->
        when (segment) {
            is PageContentSegment.Prose -> segment.copy(segmentIndex = index)
            is PageContentSegment.Table -> segment.copy(segmentIndex = index)
            is PageContentSegment.Image -> segment.copy(segmentIndex = index)
        }
    }
}

private fun parseTableContentSegments(part: ReaderPart): List<PageContentSegment> {
    val text = part.text
    if (text.isBlank()) return emptyList()

    if (!text.contains('|')) {
        return listOf(PageContentSegment.Prose(part, 0))
    }

    data class LineInfo(val text: String, val start: Int, val end: Int)
    val lines = mutableListOf<LineInfo>()
    var lineStart = 0
    while (lineStart <= text.length) {
        val newline = text.indexOf('\n', lineStart)
        val lineEnd = if (newline == -1) text.length else newline
        lines.add(LineInfo(text.substring(lineStart, lineEnd), lineStart, lineEnd))
        if (newline == -1) break
        lineStart = newline + 1
    }

    data class TableRange(
        val startLineIdx: Int,
        val endLineIdxExclusive: Int,
        val startOffset: Int,
        val endOffset: Int
    )
    val tableRanges = mutableListOf<TableRange>()

    var idx = 0
    while (idx < lines.size) {
        val trimmed = lines[idx].text.trim()
        val isTable = isTableLine(trimmed)
        val isSep = isTableSeparator(trimmed)

        if (isTable || isSep) {
            val tableStartLine = idx
            val tableStartOffset = lines[idx].start

            var endIdx = idx + 1
            while (endIdx < lines.size) {
                val nextTrimmed = lines[endIdx].text.trim()
                if (isTableLine(nextTrimmed) || isTableSeparator(nextTrimmed)) {
                    endIdx++
                } else if (nextTrimmed.isEmpty()) {
                    // Check if there is another table line or separator after empty lines
                    var lookAhead = endIdx + 1
                    while (lookAhead < lines.size && lines[lookAhead].text.trim().isEmpty()) {
                        lookAhead++
                    }
                    if (lookAhead < lines.size && (isTableLine(lines[lookAhead].text.trim()) || isTableSeparator(lines[lookAhead].text.trim()))) {
                        endIdx = lookAhead + 1
                    } else {
                        break
                    }
                } else {
                    break
                }
            }

            val groupLines = lines.subList(tableStartLine, endIdx)
            val contentRows = groupLines.filter {
                val t = it.text.trim()
                t.isNotEmpty() && !isTableSeparator(t)
            }
            if (contentRows.isNotEmpty()) {
                val endOffset = lines[endIdx - 1].end
                tableRanges.add(TableRange(tableStartLine, endIdx, tableStartOffset, endOffset))
            }
            idx = endIdx
        } else {
            idx++
        }
    }

    if (tableRanges.isEmpty()) {
        return listOf(PageContentSegment.Prose(part, 0))
    }

    val segments = mutableListOf<PageContentSegment>()
    var currentOffset = 0
    var segIndex = 0

    for (range in tableRanges) {
        if (range.startOffset > currentOffset) {
            val proseText = text.substring(currentOffset, range.startOffset)
            if (proseText.isNotBlank()) {
                segments.add(createSubPart(part, currentOffset, range.startOffset, segIndex++))
            }
        }

        val groupLines = lines.subList(range.startLineIdx, range.endLineIdxExclusive)
        val parsedRows = mutableListOf<List<String>>()
        val rowSentenceIndices = mutableListOf<Int>()

        groupLines.forEach { lineInfo ->
            val rowStr = lineInfo.text.trim()
            if (isTableSeparator(rowStr) || rowStr.isEmpty()) return@forEach

            val matchingSentence = part.sentenceRanges.maxByOrNull { sr ->
                maxOf(0, minOf(sr.endExclusive, lineInfo.end) - maxOf(sr.start, lineInfo.start))
            }?.takeIf { sr ->
                maxOf(0, minOf(sr.endExclusive, lineInfo.end) - maxOf(sr.start, lineInfo.start)) > 0
            }?.sentenceIndex

            if (isTableLine(rowStr)) {
                val cells = rowStr.trim('|')
                    .split('|')
                    .map { it.trim() }
                if (cells.any { it.isNotBlank() }) {
                    parsedRows.add(cells)
                    val fallback = (rowSentenceIndices.lastOrNull()?.plus(1)) ?: part.sentenceStartIndex
                    rowSentenceIndices.add(matchingSentence ?: fallback)
                }
            }
        }

        if (parsedRows.isNotEmpty()) {
            val tableSentenceRanges = part.sentenceRanges.filter {
                it.endExclusive > range.startOffset && it.start < range.endOffset
            }
            val startSentence = rowSentenceIndices.minOrNull()
                ?: tableSentenceRanges.minOfOrNull { it.sentenceIndex }
                ?: part.sentenceStartIndex
            val endSentence = (rowSentenceIndices.maxOrNull()?.plus(1))
                ?: (tableSentenceRanges.maxOfOrNull { it.sentenceIndex }?.plus(1))
                ?: (startSentence + parsedRows.size)

            segments.add(
                PageContentSegment.Table(
                    rows = parsedRows,
                    sentenceStartIndex = startSentence,
                    sentenceEndIndexExclusive = endSentence,
                    segmentIndex = segIndex++,
                    rowSentenceIndices = rowSentenceIndices
                )
            )
        }

        currentOffset = range.endOffset
    }

    if (currentOffset < text.length) {
        val proseText = text.substring(currentOffset)
        if (proseText.isNotBlank()) {
            segments.add(createSubPart(part, currentOffset, text.length, segIndex++))
        }
    }

    return segments.ifEmpty { listOf(PageContentSegment.Prose(part, 0)) }
}

private fun createSubPart(part: ReaderPart, startOffset: Int, endOffset: Int, segmentIndex: Int): PageContentSegment.Prose {
    val subText = part.text.substring(startOffset, endOffset)
    val subSentenceRanges = part.sentenceRanges.mapNotNull { r ->
        if (r.endExclusive <= startOffset || r.start >= endOffset) null
        else {
            val shiftedStart = (r.start - startOffset).coerceIn(0, subText.length)
            val shiftedEnd = (r.endExclusive - startOffset).coerceIn(shiftedStart, subText.length)
            if (shiftedEnd > shiftedStart) {
                ReaderPartSentenceRange(r.sentenceIndex, shiftedStart, shiftedEnd,
                    r.sentenceCharOffset + (startOffset - r.start).coerceAtLeast(0))
            } else null
        }
    }
    val startSentence = subSentenceRanges.minOfOrNull { it.sentenceIndex } ?: part.sentenceStartIndex
    val endSentence = (subSentenceRanges.maxOfOrNull { it.sentenceIndex }?.plus(1)) ?: (startSentence + 1)
    val subPart = ReaderPart(
        index = part.index,
        pageRange = part.pageRange,
        sentenceStartIndex = startSentence,
        sentenceEndIndexExclusive = endSentence,
        text = subText,
        sentenceRanges = subSentenceRanges
    )
    return PageContentSegment.Prose(subPart, segmentIndex)
}

@Composable
internal fun ReaderComposeTableCard(
    table: PageContentSegment.Table,
    activeSentenceIndex: Int,
    paperTone: PaperToneMode,
    canvasTextColor: Color,
    onSentenceClick: (Int) -> Unit,
    onSentenceDoubleTap: (Int) -> Unit,
    onSkipTable: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maxCols = (table.rows.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)
    val isWide = maxCols >= 3 || table.rows.any { row -> row.any { it.length > 25 } }
    val cardBg = when (paperTone) {
        PaperToneMode.WARM_SEPIA -> Color(0xFFF3ECE0)
        PaperToneMode.DARK -> Color(0xFF22252A)
        PaperToneMode.NATURAL_WHITE -> Color(0xFFF8F9FA)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val borderColor = when (paperTone) {
        PaperToneMode.WARM_SEPIA -> Color(0xFF8D6E63).copy(alpha = 0.25f)
        PaperToneMode.DARK -> Color(0xFF44474E).copy(alpha = 0.35f)
        PaperToneMode.NATURAL_WHITE -> Color(0xFFE2E4E8)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
    }
    val headerBg = when (paperTone) {
        PaperToneMode.NATURAL_WHITE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        PaperToneMode.WARM_SEPIA -> Color(0xFF8D6E63).copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    }
    val rowTextColor = when (paperTone) {
        PaperToneMode.NATURAL_WHITE -> Color(0xFF1C1B1F)
        else -> canvasTextColor
    }
    val activeRowBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

    Surface(
        modifier = modifier.padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Table • ${table.rows.size} rows",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.clickable { onSkipTable() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Skip Table",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "⏭",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            HorizontalDivider(
                color = borderColor.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 4.dp)
            )

            val contentModifier = if (isWide) {
                Modifier.horizontalScroll(rememberScrollState())
            } else {
                Modifier.fillMaxWidth()
            }

            Column(modifier = contentModifier) {
                table.rows.forEachIndexed { rowIndex, row ->
                    val isHeaderRow = (rowIndex == 0)
                    val rowSentenceIndex = table.rowSentenceIndices.getOrNull(rowIndex)
                        ?: (table.sentenceStartIndex + rowIndex)
                            .coerceAtMost((table.sentenceEndIndexExclusive - 1).coerceAtLeast(table.sentenceStartIndex))
                    val isRowActive = (activeSentenceIndex == rowSentenceIndex)
                    val activeColIndex = if (isRowActive) PlaybackStateStore.activeTableColumnIndex else -1

                    val rowBg = when {
                        isRowActive -> activeRowBg
                        isHeaderRow -> headerBg
                        else -> Color.Transparent
                    }

                    Row(
                        modifier = Modifier
                            .then(if (isWide) Modifier.widthIn(min = (maxCols * 115).dp) else Modifier.fillMaxWidth())
                            .background(rowBg)
                            .clickable { onSentenceClick(rowSentenceIndex) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (colIdx in 0 until maxCols) {
                            val cellText = row.getOrNull(colIdx).orEmpty()
                            val isCellSpotlighted = isRowActive && (activeColIndex == colIdx || (activeColIndex == -1 && colIdx == 0))
                            val cellModifier = if (isWide) {
                                Modifier.widthIn(min = 105.dp, max = 220.dp)
                            } else {
                                Modifier.weight(1f)
                            }

                            Box(
                                modifier = cellModifier
                                    .background(
                                        if (isCellSpotlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Transparent,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = cellText,
                                    style = if (isHeaderRow) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isHeaderRow || isRowActive) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCellSpotlighted || isHeaderRow) MaterialTheme.colorScheme.primary else rowTextColor,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (rowIndex < table.rows.lastIndex) {
                        HorizontalDivider(
                            color = borderColor.copy(alpha = 0.3f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderProseBlock(
    subPart: ReaderPart,
    segmentIndex: Int,
    activeSentenceOnThisPage: Int,
    isPlaying: Boolean,
    feedbackSentenceIndex: Int?,
    bookmarkedSentences: Map<Int, String>,
    searchMatches: List<Int>,
    searchCursor: Int,
    activeSentenceColor: Int,
    highlightColor: Int,
    feedbackColor: Int,
    searchMatchColor: Int,
    activeSearchMatchColor: Int,
    state: ReaderScreenState,
    readerSettings: ReaderSettings,
    paperTone: PaperToneMode,
    pageBitmaps: List<android.graphics.Bitmap?>,
    textColor: Int,
    boldTypeface: android.graphics.Typeface?,
    currentPageNumber: Int,
    isVisiblePage: Boolean,
    pageNumber: Int,
    document: ReaderDocument,
    bookmarkedSentenceIndexes: Set<Int>,
    isCollapsible: Boolean,
    onToggleBars: () -> Unit,
    onSentenceDoubleTap: (Int) -> Unit,
    onTextViewBound: (TextView?) -> Unit,
    onSelectionChanged: (ReaderTextSelection?) -> Unit,
    onSearchTriggered: (String) -> Unit,
    onToggleBookmark: (Int) -> Unit,
    onOpenColorPalette: (List<Int>) -> Unit,
    onEditNotes: (List<Int>) -> Unit,
    onTranslateSelection: (ReaderTextSelection) -> Unit,
    onCopySelection: (String) -> Unit,
    onGoogleSelection: (ReaderTextSelection) -> Unit,
    onShareSelection: (String) -> Unit,
    onOpenShareToAi: (ReaderTextSelection?, Boolean) -> Unit,
    onEditSpeechSelection: (String) -> Unit,
    onEditExtractedSelection: (ReaderTextSelection) -> Unit,
    onAskAiSelection: (String) -> Unit,
    onReadSelection: (String) -> Unit,
    onGloballyPositionedTop: (Float) -> Unit,
    onTextViewActive: (TextView) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    val renderedPage = remember(
        subPart,
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
        readerSettings.sectionSpacingDp,
        state.searchQuery,
        boldTypeface,
        textColor,
        context
    ) {
        buildReaderPartSpannable(
            part = subPart,
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
            pageBitmaps = emptyList(),
            sectionSpacingDp = readerSettings.sectionSpacingDp,
            searchQuery = state.searchQuery,
            textColor = textColor,
            boldTypeface = boldTypeface
        )
    }

    if (subPart.text.isNotBlank()) {
        AndroidView(
            modifier = modifier
                .fillMaxWidth()
                .onGloballyPositioned {
                    if (segmentIndex == 0) {
                        OnboardingController.updateBounds("reader_text_view", it)
                    }
                    onGloballyPositionedTop(it.positionInParent().y)
                },
            factory = { viewContext ->
                TextView(viewContext).apply {
                    setTextIsSelectable(true)
                    isFocusable = true
                    isFocusableInTouchMode = true
                    includeFontPadding = false
                    paintFlags = paintFlags or Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG
                    setSpannableFactory(object : android.text.Spannable.Factory() {
                        override fun newSpannable(source: CharSequence): android.text.Spannable {
                            return if (source is SafeSpannableString) source else SafeSpannableString(source)
                        }
                    })
                    val delegator = DelegatingActionModeCallback()
                    customSelectionActionModeCallback = delegator
                    useNaturalWordSpacing()
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_BALANCED
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
                                holder.touchInProgress = true
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
                                    // Dragging a selection handle horizontally must not turn
                                    // into a page swipe or discard the selected phrase.
                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                } else {
                                    if (dx > touchSlop || dy > touchSlop) {
                                        v.parent?.requestDisallowInterceptTouchEvent(false)
                                    }
                                }
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                holder.touchInProgress = false
                                if (!this.hasSelection()) {
                                    v.parent?.requestDisallowInterceptTouchEvent(false)
                                }
                            }
                        }

                        detector.onTouchEvent(event)

                        doubleTapHandled
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
                onTextViewActive(textView)
                val holder = textView.tag as? TextViewHolder ?: TextViewHolder().also { textView.tag = it }

                if (isVisiblePage) {
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
                holder.haptics = haptics

                textView.useNaturalWordSpacing()
                if (holder.renderedPage !== renderedPage && !textView.hasSelection() && !holder.touchInProgress) {
                    val currentText = textView.text
                    if (currentText is android.text.Spannable && currentText.toString() == renderedPage.toString()) {
                        holder.presentationSpans = updateReaderPresentationSpans(currentText, holder.presentationSpans, renderedPage)
                        textView.invalidate()
                    } else {
                        clearNativeTextSelection(textView)
                        holder.presentationSpans = renderedPage.getSpans(0, renderedPage.length, Any::class.java).toList()
                        textView.text = renderedPage
                    }
                    holder.part = subPart
                    holder.renderedPage = renderedPage
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
                val targetWeight = if (paperTone == PaperToneMode.DARK) 400 else 450
                if (holder.uiFontId != currentFontId || holder.targetWeight != targetWeight) {
                    holder.uiFontId = currentFontId
                    holder.targetWeight = targetWeight
                    val uiFont = VeritasUiFont.fromId(currentFontId)
                    textView.typeface = uiFont.asTypeface(context, weight = targetWeight)
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
                    part = holder.part ?: subPart,
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
}
