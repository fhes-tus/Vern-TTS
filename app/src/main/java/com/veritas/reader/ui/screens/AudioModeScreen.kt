package com.veritas.reader.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.*
import com.veritas.reader.R
import com.veritas.reader.ui.VeritasMotion
import com.veritas.reader.ui.VeritasSleekSlider
import com.veritas.reader.ui.pressScale
import com.veritas.reader.ui.rememberVeritasHaptics
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AudioModeScreen(
    title: String,
    currentIndex: Int,
    totalChunks: Int,
    currentSentence: String,
    isPlaying: Boolean,
    coverFile: File? = null,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDismiss: () -> Unit,
    isBookmarked: Boolean = false,
    onToggleBookmark: () -> Unit = {},
    rate: Float = 1.0f,
    onRateChange: (Float) -> Unit = {},
    readerMode: ReaderMode = ReaderMode.LISTEN,
    onReaderModeChange: (ReaderMode) -> Unit = {},
    hasCanvas: Boolean = false,
    documentChunks: List<String> = emptyList(),
    onSentenceClick: (Int) -> Unit = {},
    onOpenSleepTimer: () -> Unit = {},
    onOpenVoiceStudio: () -> Unit = {},
    onOpenNarrationStudio: () -> Unit = {},
    onExportAudio: () -> Unit = {},
    documentId: String? = null,
    fontSizeSp: Int = 18,
    onFontSizeChange: (Int) -> Unit = {},
    contentWindowInsets: WindowInsets = WindowInsets.safeDrawing,
    onShareQuoteReady: ((File) -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val glass = VeritasPackStyle.glassChromeEnabled() && isDeviceGlassCapable()
    // The Listen page may be in a separate window: capture its own backdrop.
    val backdrop = if (glass) rememberVeritasLayerBackdrop() else null
    val haptics = rememberVeritasHaptics()
    var showRead by rememberSaveable(documentId) { mutableStateOf(false) }
    var fullScreenRead by rememberSaveable(documentId) { mutableStateOf(false) }
    BackHandler(fullScreenRead) { fullScreenRead = false }
    var quoteSelection by rememberSaveable(documentId, title) { mutableStateOf<List<Int>>(emptyList()) }
    var showQuotePreview by rememberSaveable(documentId, title) { mutableStateOf(false) }
    val quoteChunks = documentChunks.ifEmpty { listOf(currentSentence) }
    val quoteText = remember(quoteChunks, quoteSelection) {
        if (quoteSelection.isEmpty()) "" else runCatching { QuoteSelection.text(quoteChunks, quoteSelection) }.getOrDefault("")
    }
    BackHandler(quoteSelection.isNotEmpty() && !showQuotePreview) { quoteSelection = emptyList() }
    var toolsExpanded by remember { mutableStateOf(false) }
    var showTextSize by remember { mutableStateOf(false) }
    var ratesExpanded by remember { mutableStateOf(false) }
    var scrub by remember(documentId) { mutableStateOf<Float?>(null) }
    val lastIndex = (totalChunks - 1).coerceAtLeast(0)
    val index = currentIndex.coerceIn(0, lastIndex)
    val position = scrub ?: index.toFloat()
    val preparing = PlaybackStateStore.isPreparingFor(documentId)
    val context = LocalContext.current
    fun selectQuoteSentence(sentence: Int) {
        try {
            val next = QuoteSelection.toggle(quoteChunks, quoteSelection, sentence)
            if (quoteSelection.isEmpty() && next.isNotEmpty() && isPlaying) onPlayPause()
            quoteSelection = next
            if (next.isEmpty()) showQuotePreview = false
        } catch (error: IllegalArgumentException) {
            android.widget.Toast.makeText(context, error.message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    if (quoteSelection.isNotEmpty()) QuoteShareDialog(showQuotePreview, quoteText, title, documentId, coverFile,
        onDismiss = { showQuotePreview = false }, onShareReady = onShareQuoteReady)
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val sessionTracker = remember(context, documentId) { documentId?.let { ReadingSessionTracker.get(context) } }
    val latestIndex by rememberUpdatedState(index)
    DisposableEffect(sessionTracker, documentId, showRead, lifecycle) {
        fun updateReader() {
            sessionTracker?.reader("listen", documentId.takeIf { showRead && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) }, latestIndex)
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ -> updateReader() }
        lifecycle.addObserver(observer); updateReader()
        onDispose { lifecycle.removeObserver(observer); sessionTracker?.reader("listen", null) }
    }
    LaunchedEffect(index) {
        if (showRead && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) sessionTracker?.reader("listen", documentId, index)
    }
    val effects = VeritasMotion.effectsSlow<Float>()
    val spatial = VeritasMotion.spatialSlow<androidx.compose.ui.unit.IntOffset>()

    VeritasBackdropProvider(backdrop) {
        Box(Modifier.fillMaxSize().testTag("listen-page")) {
            Canvas(Modifier.fillMaxSize().recordVeritasBackdrop(backdrop, glass)) {
                drawRect(scheme.background)
                if (glass) {
                    drawRect(Brush.radialGradient(listOf(scheme.primary.copy(alpha = .14f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * .78f, size.height * .2f), radius = size.height * .65f))
                    drawRect(Brush.linearGradient(listOf(Color.Transparent, scheme.tertiary.copy(alpha = .10f), Color.Transparent)))
                }
            }
            Column(Modifier.fillMaxSize().windowInsetsPadding(contentWindowInsets).padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!fullScreenRead) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ListenControl(Icons.AutoMirrored.Filled.ArrowBack, "Back", glass, Modifier.size(48.dp), onClick = onDismiss)
                    ReaderModeToggle(readerMode, onReaderModeChange, hasCanvas, Modifier.weight(1f))
                    Box {
                        ListenControl(Icons.Default.MoreVert, "More options", glass, Modifier.size(48.dp), onClick = { toolsExpanded = true })
                        DropdownMenu(toolsExpanded, { toolsExpanded = false }) {
                            DropdownMenuItem(text = { Text("Share quote") }, leadingIcon = { Icon(Icons.Outlined.FormatQuote, null) },
                                enabled = quoteChunks.getOrNull(index)?.isNotBlank() == true,
                                onClick = {
                                    toolsExpanded = false
                                    showRead = true
                                    if (quoteSelection.isEmpty()) selectQuoteSentence(index)
                                })
                            DropdownMenuItem(text = { Text("Full screen reading") }, leadingIcon = { Icon(Icons.Default.Fullscreen, null) },
                                onClick = { toolsExpanded = false; showRead = true; fullScreenRead = true })
                            listOf(
                                Triple("Sleep timer", Icons.Outlined.Timer, onOpenSleepTimer),
                                Triple("Voice & language", Icons.Outlined.RecordVoiceOver, onOpenVoiceStudio),
                                Triple("Narration mode", Icons.Outlined.TheaterComedy, onOpenNarrationStudio),
                                Triple("Export audio", Icons.Outlined.FileDownload, onExportAudio)
                            ).forEach { (label, icon, action) ->
                                DropdownMenuItem(text = { Text(label) }, leadingIcon = { Icon(icon, null) },
                                    onClick = { toolsExpanded = false; action() })
                            }
                        }
                    }
                }
                Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !showRead, onClick = { showRead = false; quoteSelection = emptyList(); showQuotePreview = false }, label = { Text("Player") })
                    FilterChip(selected = showRead, onClick = { showRead = true }, label = { Text("Read") })

                }
                }
                AnimatedContent(showRead, modifier = Modifier.weight(1f).fillMaxWidth(),
                    transitionSpec = {
                        (fadeIn(effects) + slideInVertically(spatial) { it / 12 }) togetherWith
                            (fadeOut(effects) + slideOutVertically(spatial) { -it / 12 })
                    }, label = "listenViewTransition") { read ->
                    if (read) Box(Modifier.fillMaxSize()) {
                        ListenSentenceList(quoteChunks, index,
                            { sentence -> if (quoteSelection.isEmpty()) onSentenceClick(sentence) else selectQuoteSentence(sentence) }, fontSizeSp,
                            quoteSelection, { sentence -> haptics.select(); selectQuoteSentence(sentence) })
                        if (fullScreenRead && quoteSelection.isEmpty()) Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ListenControl(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Pause" else "Play", glass,
                                Modifier.size(52.dp), playback = true, preparing = preparing, onClick = onPlayPause)
                            ListenControl(Icons.Default.FormatSize, "Text size", glass, Modifier.size(52.dp),
                                onClick = { showTextSize = true })
                            ListenControl(Icons.Default.FullscreenExit, "Exit full screen reading", glass, Modifier.size(52.dp).testTag("listen-exit-fullscreen"),
                                onClick = { fullScreenRead = false })
                        }
                    }
                    else ListenArtwork(title, documentId, coverFile, if (totalChunks > 0) (index + 1f) / totalChunks else 0f,
                        { haptics.select(); onNext() }, { haptics.select(); onPrevious() })
                }
                if (quoteSelection.isNotEmpty()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${quoteSelection.size} of 5 selected", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { quoteSelection = emptyList(); showQuotePreview = false }) { Text("Cancel") }
                    Button(onClick = { showQuotePreview = true }, enabled = quoteText.isNotBlank()) { Text("Preview") }
                }
                AnimatedVisibility(visible = !fullScreenRead && quoteSelection.isEmpty(),
                    enter = if (VeritasMotion.scheme.reduceMotion) EnterTransition.None else fadeIn(effects) + expandVertically(),
                    exit = if (VeritasMotion.scheme.reduceMotion) ExitTransition.None else fadeOut(effects) + shrinkVertically()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge,
                        color = scheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(if (totalChunks > 0) "Sentence ${index + 1} of $totalChunks" else "Ready to listen",
                            modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ListenControl(if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkAdd,
                            if (isBookmarked) "Remove bookmark" else "Add bookmark", glass, Modifier.size(48.dp), onClick = onToggleBookmark)
                        ListenControl(Icons.Default.Fullscreen, "Full screen reading", glass, Modifier.size(48.dp).testTag("listen-fullscreen"),
                            onClick = { showRead = true; fullScreenRead = true })
                        ListenControl(Icons.Default.FormatSize, "Text size", glass, Modifier.size(48.dp).testTag("listen-text-size"),
                            onClick = { showTextSize = true })
                        }
                    }
                }
                Column {
                    VeritasSleekSlider(position, { scrub = it },
                        modifier = Modifier.semantics {
                            contentDescription = "Reading position"
                            progressBarRangeInfo = ProgressBarRangeInfo(position, 0f..lastIndex.coerceAtLeast(1).toFloat(), lastIndex.coerceAtLeast(1) - 1)
                            setProgress { requested ->
                                onSentenceClick(requested.roundToInt().coerceIn(0, lastIndex)); true
                            }
                        }, valueRange = 0f..lastIndex.coerceAtLeast(1).toFloat(), stepIncrement = 1f,
                        enabled = totalChunks > 1, sliderHeight = 40.dp, trackHeight = 3.dp, thumbSize = 14.dp,
                        onValueChangeFinished = { scrub?.let { onSentenceClick(it.roundToInt().coerceIn(0, lastIndex)) }; scrub = null })
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (totalChunks > 0) "${position.roundToInt() + 1} / $totalChunks" else "0 / 0",
                            style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                        Text("${(totalChunks - position.roundToInt() - 1).coerceAtLeast(0)} remaining",
                            style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    ListenControl(Icons.Default.SkipPrevious, "Previous sentence", glass, Modifier.size(60.dp), onClick = { haptics.select(); onPrevious() })
                    ListenControl(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (preparing) "Cancel starting playback" else if (isPlaying) "Pause" else "Play", glass,
                        Modifier.weight(1f).height(64.dp), label = if (preparing) "Starting" else if (isPlaying) "Pause" else "Play",
                        playback = true, preparing = preparing, emphasized = true, onClick = { haptics.toggle(!isPlaying); onPlayPause() })
                    ListenControl(Icons.Default.SkipNext, "Next sentence", glass, Modifier.size(60.dp), onClick = { haptics.select(); onNext() })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.weight(1f)) {
                        ListenControl(Icons.Outlined.Speed, "Playback speed", glass, Modifier.fillMaxWidth().height(48.dp),
                            label = String.format(Locale.US, "%.2f", rate).trimEnd('0').trimEnd('.') + "×", onClick = { ratesExpanded = true })
                        DropdownMenu(ratesExpanded, { ratesExpanded = false }) {
                            listOf(.8f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                                DropdownMenuItem(text = { Text("${speed}×") }, onClick = { onRateChange(speed); ratesExpanded = false })
                            }
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Slower −0.05×") }, onClick = { onRateChange((rate - .05f).coerceAtLeast(.5f)) })
                            DropdownMenuItem(text = { Text("Faster +0.05×") }, onClick = { onRateChange((rate + .05f).coerceAtMost(3f)) })
                        }
                    }
                    ListenControl(Icons.Outlined.RecordVoiceOver, "Voice & language", glass, Modifier.weight(1f).height(48.dp), label = "Voice", onClick = onOpenVoiceStudio)
                    ListenControl(Icons.Outlined.Timer, "Sleep timer", glass, Modifier.weight(1f).height(48.dp), label = "Timer", onClick = onOpenSleepTimer)
                }
                }
                }
            }
        }
    }
    if (showTextSize) AlertDialog(onDismissRequest = { showTextSize = false },
        title = { Text("Reading text size") },
        text = { Column { Text("$fontSizeSp sp")
            VeritasSleekSlider(value = fontSizeSp.toFloat(), onValueChange = { onFontSizeChange(it.roundToInt().coerceIn(10, 28)) },
                valueRange = 10f..28f, stepIncrement = 1f, sliderHeight = 48.dp,
                modifier = Modifier.semantics {
                    contentDescription = "Reading text size"
                    progressBarRangeInfo = ProgressBarRangeInfo(fontSizeSp.toFloat(), 10f..28f, 17)
                    setProgress { onFontSizeChange(it.roundToInt().coerceIn(10, 28)); true }
                })
        } }, confirmButton = { TextButton({ showTextSize = false }) { Text("Done") } })

}

@Composable
private fun ListenArtwork(title: String, documentId: String?, coverFile: File?, progress: Float, onNext: () -> Unit, onPrevious: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    val artwork by produceState<Pair<android.graphics.Bitmap, androidx.palette.graphics.Palette>?>(null, documentId, title, coverFile?.absolutePath, coverFile?.lastModified()) {
        value = withContext(Dispatchers.IO) {
            val actual = coverFile?.takeIf { it.isFile }?.let { file -> runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 900) sample *= 2
                BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull() }
            val saved = documentId?.let { DocumentRepository(context).findDocument(it) }
            val bitmap = actual ?: saved?.let { BookCoverLoader.document(context, it.id, it.catalogId) }
                ?: BookCoverLoader.generated(context, documentId ?: title, getBookAndAuthor(title).first, saved?.sourceLabel.orEmpty(), getBookAndAuthor(title).second)
            bitmap to androidx.palette.graphics.Palette.from(bitmap).resizeBitmapArea(16000).generate()
        }
    }
    val bitmap = artwork?.first
    val brand = VernCustomization.palette(VeritasThemeState.launcherPalette, VeritasThemeState.themeId, androidx.compose.foundation.isSystemInDarkTheme())
    val dominant = artwork?.second?.getDominantColor(brand.background)?.let { Color(it) } ?: Color(brand.background)
    val accent = artwork?.second?.getVibrantColor(brand.background)?.let { Color(it) } ?: dominant
    val cardStart = lerp(scheme.surfaceContainer, accent, .42f)
    val cardEnd = lerp(scheme.surfaceContainer, dominant, .22f)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth * .88f, maxHeight * .94f, 340.dp).coerceAtLeast(1.dp)
        val shape = RoundedCornerShape(32.dp)
        Surface(Modifier.size(side).veritasGlassOutline(shape, true).testTag("listen-artwork")
            .pointerInput(Unit) {
                var drag = 0f
                detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragCancel = { drag = 0f },
                    onDragEnd = { if (drag < -72.dp.toPx()) next() else if (drag > 72.dp.toPx()) previous() },
                    onHorizontalDrag = { change, amount -> change.consume(); drag += amount })
            }, shape = shape,
            color = scheme.surfaceContainer, shadowElevation = 12.dp) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(cardStart, cardEnd))), contentAlignment = Alignment.Center) {
                if (bitmap != null) {
                    val available = side - 28.dp
                    val ratio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
                    val width = if (ratio < 1f) available * ratio else available
                    val height = if (ratio < 1f) available else available / ratio
                    Image(bitmap.asImageBitmap(), "Book cover", Modifier.size(width, height).clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Fit)
                } else {
                    VeritasCoverPlaceholder(documentId ?: title, title, "", Modifier.width((side - 28.dp) * 2f / 3f).height(side - 28.dp))
                }
                Surface(Modifier.align(Alignment.BottomEnd).padding(14.dp), shape = RoundedCornerShape(50),
                    color = scheme.surface.copy(alpha = .85f), contentColor = scheme.onSurface) {
                    Text("${(progress.coerceIn(0f, 1f) * 100).roundToInt()}%", Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListenSentenceList(chunks: List<String>, currentIndex: Int, onSentenceClick: (Int) -> Unit, fontSizeSp: Int,
    quoteSelection: List<Int>, onSentenceLongClick: (Int) -> Unit) {
    val state = rememberLazyListState()
    val scheme = MaterialTheme.colorScheme
    val reducedMotion = VeritasMotion.scheme.reduceMotion
    val density = LocalDensity.current
    LaunchedEffect(currentIndex, chunks.size, reducedMotion, fontSizeSp, quoteSelection.isNotEmpty()) {
        if (quoteSelection.isNotEmpty() || currentIndex !in chunks.indices) return@LaunchedEffect
        repeat(15) {
            if (state.layoutInfo.viewportEndOffset <= 0) delay(30)
        }
        val viewport = state.layoutInfo.viewportEndOffset - state.layoutInfo.viewportStartOffset
        val offset = -(viewport * .14f).toInt()
        if (reducedMotion) state.scrollToItem(currentIndex, offset) else state.animateScrollToItem(currentIndex, offset)
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().testTag("listen-sentences")
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // Fade only the sentences so the page's glass backdrop remains continuous.
                drawRect(Brush.verticalGradient(0f to Color.Transparent, .08f to Color.Black,
                    .92f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
            }, state = state,
            contentPadding = PaddingValues(vertical = 72.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            itemsIndexed(chunks) { index, sentence ->
                val active = index == currentIndex
                val quoteSelected = index in quoteSelection
                val alpha by animateFloatAsState(if (active || quoteSelected) 1f else .46f, VeritasMotion.effectsSlow(), label = "sentenceOpacity")
                val scale by animateFloatAsState(if (active || quoteSelected || reducedMotion) 1f else .95f, VeritasMotion.spatialSlow(), label = "sentenceScale")
                val lift by animateFloatAsState(if (active || quoteSelected || reducedMotion) 0f else 5f, VeritasMotion.spatialSlow(), label = "sentenceLift")
                // Stable typography prevents intermediate font sizes from rewrapping the paragraph.
                Text(sentence, Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale; translationY = with(density) { lift.dp.toPx() } }
                    .background(if (quoteSelected) scheme.primaryContainer else Color.Transparent, RoundedCornerShape(12.dp))
                    .testTag("listen-sentence-$index")
                    .semantics { selected = if (quoteSelection.isNotEmpty()) quoteSelected else active }
                    .combinedClickable(onClick = { onSentenceClick(index) }, onLongClickLabel = "Select for quote", onLongClick = { onSentenceLongClick(index) }),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSizeSp.sp, lineHeight = (fontSizeSp * 1.5f).sp),
                    color = scheme.onBackground)
            }
        }
    }
}

@Composable
private fun ListenControl(icon: ImageVector, description: String, glass: Boolean, modifier: Modifier = Modifier,
    label: String? = null, playback: Boolean = false, preparing: Boolean = false, emphasized: Boolean = false, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    Surface(onClick = onClick, modifier = modifier.pressScale(interaction, .97f)
        .veritasGlassBackdrop(shape, glass, surfaceOpacity = .28f)
        .semantics { if (!playback) contentDescription = description }, shape = shape, interactionSource = interaction,
        color = if (glass) Color.Transparent else if (emphasized) scheme.primaryContainer else scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        border = if (glass) null else BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = .6f))) {
        Row(Modifier.padding(horizontal = if (label == null) 0.dp else 10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            if (playback) PlaybackControlIcon(icon == Icons.Default.Pause, preparing, tint = scheme.onSurface, size = 30.dp)
            else Icon(icon, null, Modifier.size(if (label == null) 25.dp else 20.dp))
            if (label != null) Text(label, style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
