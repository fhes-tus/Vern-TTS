package com.veritas.reader.ui.screens

import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.veritas.reader.BookCoverLoader
import com.veritas.reader.DocumentRepository
import com.veritas.reader.PlaybackControlIcon
import com.veritas.reader.PlaybackStateStore
import com.veritas.reader.QuoteSelection
import com.veritas.reader.R
import com.veritas.reader.ReaderMode
import com.veritas.reader.ReaderModeToggle
import com.veritas.reader.ReadingSessionTracker
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.VeritasThemeState
import com.veritas.reader.VernCustomization
import com.veritas.reader.VeritasBackdropProvider
import com.veritas.reader.getBookAndAuthor
import com.veritas.reader.isDeviceGlassCapable
import com.veritas.reader.recordVeritasBackdrop
import com.veritas.reader.rememberVeritasLayerBackdrop
import com.veritas.reader.ui.VeritasMotion
import com.veritas.reader.ui.VeritasSleekSlider
import com.veritas.reader.ui.pressScale
import com.veritas.reader.ui.rememberVeritasHaptics
import com.veritas.reader.veritasGlassBackdrop
import com.veritas.reader.veritasGlassOutline
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val LISTEN_HINTS_PREFS_NAME = "vern_listening_nav_hints"
private const val PREF_HIDE_COVER_TAP_HINT = "hide_cover_tap_hint_permanently"
private const val PREF_HIDE_GALLERY_RETURN_HINT = "hide_gallery_return_hint_permanently"

/**
 * Holds session-scoped dismissal state for the Listening Player <-> Read discoverability hints.
 * Selecting "Remove" suppresses that hint for the remainder of the current app session without
 * writing to persistent storage; selecting "Don't show again" persists across app launches.
 */
internal object ListenNavigationHintSessionState {
    var coverTapHintDismissedThisSession by mutableStateOf(false)
    var galleryReturnHintDismissedThisSession by mutableStateOf(false)
}

internal object AudioCoverCache {
    private val memoryCache = object : android.util.LruCache<String, Pair<android.graphics.Bitmap, androidx.palette.graphics.Palette?>>(16) {}

    fun get(key: String): Pair<android.graphics.Bitmap, androidx.palette.graphics.Palette?>? = memoryCache.get(key)
    fun put(key: String, pair: Pair<android.graphics.Bitmap, androidx.palette.graphics.Palette?>) {
        memoryCache.put(key, pair)
    }
}

@Composable
internal fun ListenSelectionActionCard(
    selectedCount: Int,
    quoteText: String,
    onPreviewQuote: () -> Unit,
    onReadFromHere: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scheme = MaterialTheme.colorScheme

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = scheme.surfaceVariant.copy(alpha = 0.96f),
            contentColor = scheme.onSurfaceVariant
        ),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.40f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row: Lightbulb in rounded square + Title + Close X
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = scheme.primaryContainer,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            tint = scheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(5.dp)
                                .size(18.dp)
                        )
                    }
                    Text(
                        text = if (selectedCount <= 1) "Sentence Selected" else "$selectedCount Sentences Selected",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface
                    )
                }

                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_cancel),
                        tint = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Quick Actions / Capabilities
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_create_quote_card),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onSurface
                    )
                    Text(
                        text = " — Edit text, omit words with ellipses, share image",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_read_from_here),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onSurface
                    )
                    Text(
                        text = " — Start voice narration from this line",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(quoteText))
                        android.widget.Toast.makeText(
                            context,
                            context.getString(com.veritas.reader.R.string.ui_sentence_copied),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                ) {
                    Text(
                        text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_copy),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onReadFromHere) {
                    Text(
                        text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_read_from_here),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary
                    )
                }
                Spacer(Modifier.width(4.dp))
                Button(
                    onClick = onPreviewQuote,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_create_quote_card),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

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
    BackHandler(showRead && !fullScreenRead) {
        showRead = false
        fullScreenRead = false
    }
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

    // Independent persistent preference flags for the two first-time navigation hints
    val hintPrefs = remember(context) {
        context.getSharedPreferences(LISTEN_HINTS_PREFS_NAME, Context.MODE_PRIVATE)
    }
    var coverHintHiddenPermanently by remember(hintPrefs) {
        mutableStateOf(hintPrefs.getBoolean(PREF_HIDE_COVER_TAP_HINT, false))
    }
    var galleryHintHiddenPermanently by remember(hintPrefs) {
        mutableStateOf(hintPrefs.getBoolean(PREF_HIDE_GALLERY_RETURN_HINT, false))
    }
    val showCoverTapHint = !showRead &&
        !ListenNavigationHintSessionState.coverTapHintDismissedThisSession &&
        !coverHintHiddenPermanently
    val showGalleryReturnHint = showRead &&
        !fullScreenRead &&
        !ListenNavigationHintSessionState.galleryReturnHintDismissedThisSession &&
        !galleryHintHiddenPermanently

    // Synchronize Android system bars with fullScreenRead (immersive Listening Read mode)
    val view = LocalView.current
    LaunchedEffect(fullScreenRead, view) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (fullScreenRead) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(view) {
        onDispose {
            val window = (context as? Activity)?.window ?: return@onDispose
            WindowCompat.getInsetsController(window, view).show(WindowInsetsCompat.Type.systemBars())
        }
    }

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
    if (quoteSelection.isNotEmpty()) {
        QuoteShareDialog(
            showQuotePreview,
            quoteText,
            title,
            documentId,
            coverFile,
            onDismiss = { showQuotePreview = false },
            onShareReady = onShareQuoteReady
        )
    }

    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val sessionTracker = remember(context, documentId) { documentId?.let { ReadingSessionTracker.get(context) } }
    val latestIndex by rememberUpdatedState(index)
    DisposableEffect(sessionTracker, documentId, showRead, lifecycle) {
        fun updateReader() {
            sessionTracker?.reader(
                "listen",
                documentId.takeIf { showRead && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) },
                latestIndex
            )
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ -> updateReader() }
        lifecycle.addObserver(observer)
        updateReader()
        onDispose {
            lifecycle.removeObserver(observer)
            sessionTracker?.reader("listen", null)
        }
    }
    LaunchedEffect(index) {
        if (showRead && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            sessionTracker?.reader("listen", documentId, index)
        }
    }
    val effects = VeritasMotion.effectsSlow<Float>()
    val spatial = VeritasMotion.spatialSlow<androidx.compose.ui.unit.IntOffset>()

    // Entering Read via cover tap shows controls initially (fullScreenRead = false) so the gallery return icon is discoverable.
    val handleEnterReadFromCover: () -> Unit = {
        haptics.select()
        showRead = true
        fullScreenRead = false
    }
    val handleReturnToPlayerFromRead: () -> Unit = {
        haptics.select()
        showRead = false
        fullScreenRead = false
        quoteSelection = emptyList()
        showQuotePreview = false
    }

    VeritasBackdropProvider(backdrop) {
        BoxWithConstraints(Modifier.fillMaxSize().testTag("listen-page")) {
            val isLandscapeSplit = maxWidth >= 600.dp && maxWidth > maxHeight
            Canvas(Modifier.fillMaxSize().recordVeritasBackdrop(backdrop, glass)) {
                drawRect(scheme.background)
                if (glass) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(scheme.primary.copy(alpha = .14f), Color.Transparent),
                            center = Offset(size.width * .78f, size.height * .2f),
                            radius = size.height * .65f
                        )
                    )
                    drawRect(Brush.linearGradient(listOf(Color.Transparent, scheme.tertiary.copy(alpha = .10f), Color.Transparent)))
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(contentWindowInsets)
                    .padding(horizontal = 20.dp, vertical = if (isLandscapeSplit) 8.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (isLandscapeSplit) 8.dp else 12.dp)
            ) {
                // Top toolbar (hidden when in immersive fullScreenRead; Player/Read toggle pills removed per Stage 4 spec)
                if (!fullScreenRead) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ListenControl(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_back),
                            glass,
                            Modifier.size(48.dp),
                            onClick = onDismiss
                        )
                        ReaderModeToggle(readerMode, onReaderModeChange, hasCanvas, Modifier.weight(1f))
                        Box {
                            ListenControl(
                                Icons.Default.MoreVert,
                                com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_more_options_86c0a35),
                                glass,
                                Modifier.size(48.dp),
                                onClick = { toolsExpanded = true }
                            )
                            DropdownMenu(toolsExpanded, { toolsExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_share_quote)) },
                                    leadingIcon = { Icon(Icons.Outlined.FormatQuote, null) },
                                    enabled = quoteChunks.getOrNull(index)?.isNotBlank() == true,
                                    onClick = {
                                        toolsExpanded = false
                                        showRead = true
                                        if (quoteSelection.isEmpty()) selectQuoteSentence(index)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_full_screen_reading)) },
                                    leadingIcon = { Icon(Icons.Default.Fullscreen, null) },
                                    onClick = {
                                        toolsExpanded = false
                                        showRead = true
                                        fullScreenRead = true
                                    }
                                )
                                listOf(
                                    Triple("Sleep timer", Icons.Outlined.Timer, onOpenSleepTimer),
                                    Triple("Voice & language", Icons.Outlined.RecordVoiceOver, onOpenVoiceStudio),
                                    Triple("Narration mode", Icons.Outlined.TheaterComedy, onOpenNarrationStudio),
                                    Triple("Export audio", Icons.Outlined.FileDownload, onExportAudio)
                                ).forEach { (label, icon, action) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        leadingIcon = { Icon(icon, null) },
                                        onClick = {
                                            toolsExpanded = false
                                            action()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Discreet First-Time Discoverability Hints (independent session "Remove" vs persistent "Don't show again")
                if (showCoverTapHint) {
                    ListenNavigationDiscoveryHint(
                        message = "• Tap cover to switch to read view\n• Tap play to listen along",
                        testTagPrefix = "listen-cover-hint",
                        onRemoveForSession = {
                            ListenNavigationHintSessionState.coverTapHintDismissedThisSession = true
                        },
                        onDontShowAgain = {
                            ListenNavigationHintSessionState.coverTapHintDismissedThisSession = true
                            coverHintHiddenPermanently = true
                            hintPrefs.edit().putBoolean(PREF_HIDE_COVER_TAP_HINT, true).apply()
                        }
                    )
                } else if (showGalleryReturnHint) {
                    ListenNavigationDiscoveryHint(
                        message = "• Single tap: Show book cover\n• Double tap: Play from sentence\n• Long press: Select sentence to share",
                        testTagPrefix = "listen-read-hint",
                        onRemoveForSession = {
                            ListenNavigationHintSessionState.galleryReturnHintDismissedThisSession = true
                        },
                        onDontShowAgain = {
                            ListenNavigationHintSessionState.galleryReturnHintDismissedThisSession = true
                            galleryHintHiddenPermanently = true
                            hintPrefs.edit().putBoolean(PREF_HIDE_GALLERY_RETURN_HINT, true).apply()
                        }
                    )
                }

                if (isLandscapeSplit && !fullScreenRead) {
                    // Balanced Landscape 2-Column Layout for BOTH Player (Cover) and Read (Lyrics) view:
                    // Left column: Cover artwork OR scrolling sentences (lyrics view)
                    // Right column: Natural-width playback controls panel (not stretched)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Column: Cover Artwork (Player) or Sentences (Read/Lyrics view)
                        Box(
                            modifier = Modifier
                                .weight(0.54f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = showRead,
                                transitionSpec = {
                                    (fadeIn(effects) + slideInVertically(spatial) { it / 14 }) togetherWith
                                        (fadeOut(effects) + slideOutVertically(spatial) { -it / 14 })
                                },
                                label = "landscapeListenContentTransition"
                            ) { readModeActive ->
                                if (readModeActive) {
                                    Column(Modifier.fillMaxSize()) {
                                        Box(Modifier.weight(1f).fillMaxWidth()) {
                                            ListenSentenceList(
                                                chunks = quoteChunks,
                                                currentIndex = index,
                                                onSentenceClick = { sentence ->
                                                    if (quoteSelection.isNotEmpty()) selectQuoteSentence(sentence)
                                                },
                                                onSentenceDoubleClick = { sentence ->
                                                    if (quoteSelection.isEmpty()) onSentenceClick(sentence)
                                                },
                                                onReturnToCover = handleReturnToPlayerFromRead,
                                                fontSizeSp = fontSizeSp,
                                                quoteSelection = quoteSelection,
                                                onSentenceLongClick = { sentence ->
                                                    haptics.select()
                                                    selectQuoteSentence(sentence)
                                                }
                                            )
                                        }
                                        if (quoteSelection.isNotEmpty()) {
                                            ListenSelectionActionCard(
                                                selectedCount = quoteSelection.size,
                                                quoteText = quoteText,
                                                onPreviewQuote = { showQuotePreview = true },
                                                onReadFromHere = {
                                                    quoteSelection.firstOrNull()?.let { onSentenceClick(it) }
                                                    quoteSelection = emptyList()
                                                },
                                                onCancel = { quoteSelection = emptyList(); showQuotePreview = false }
                                            )
                                        }
                                    }
                                } else {
                                    ListenArtwork(
                                        title = title,
                                        documentId = documentId,
                                        coverFile = coverFile,
                                        progress = if (totalChunks > 0) (index + 1f) / totalChunks else 0f,
                                        onOpenRead = handleEnterReadFromCover,
                                        onNext = { haptics.select(); onNext() },
                                        onPrevious = { haptics.select(); onPrevious() }
                                    )
                                }
                            }
                        }

                        // Right Column: Controls Panel with balanced max-width to prevent stretched controls
                        Box(
                            modifier = Modifier
                                .weight(0.46f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 390.dp)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ListenPlayerControlsPanel(
                                    title = title,
                                    index = index,
                                    totalChunks = totalChunks,
                                    lastIndex = lastIndex,
                                    position = position,
                                    onScrubChange = { scrub = it },
                                    onScrubFinished = {
                                        scrub?.let { onSentenceClick(it.roundToInt().coerceIn(0, lastIndex)) }
                                        scrub = null
                                    },
                                    showRead = showRead,
                                    onReturnToPlayer = handleReturnToPlayerFromRead,
                                    isBookmarked = isBookmarked,
                                    onToggleBookmark = onToggleBookmark,
                                    onEnterFullScreenRead = {
                                        showRead = true
                                        fullScreenRead = true
                                    },
                                    onOpenTextSize = { showTextSize = true },
                                    isPlaying = isPlaying,
                                    preparing = preparing,
                                    onPlayPause = {
                                        haptics.toggle(!isPlaying)
                                        onPlayPause()
                                    },
                                    onPrevious = {
                                        haptics.select()
                                        onPrevious()
                                    },
                                    onNext = {
                                        haptics.select()
                                        onNext()
                                    },
                                    rate = rate,
                                    ratesExpanded = ratesExpanded,
                                    onRatesExpandedChange = { ratesExpanded = it },
                                    onRateChange = onRateChange,
                                    onOpenVoiceStudio = onOpenVoiceStudio,
                                    onOpenSleepTimer = onOpenSleepTimer,
                                    onSentenceClick = onSentenceClick,
                                    glass = glass,
                                    compactVertical = true
                                )
                            }
                        }
                    }
                } else {
                    // Portrait Player & Read (or Immersive FullScreen Read)
                    AnimatedContent(
                        targetState = showRead,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        transitionSpec = {
                            (fadeIn(effects) + slideInVertically(spatial) { it / 12 }) togetherWith
                                (fadeOut(effects) + slideOutVertically(spatial) { -it / 12 })
                        },
                        label = "listenViewTransition"
                    ) { read ->
                        if (read) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = if (fullScreenRead && isLandscapeSplit) 64.dp else 0.dp)
                            ) {
                                ListenSentenceList(
                                    chunks = quoteChunks,
                                    currentIndex = index,
                                    onSentenceClick = { sentence ->
                                        if (quoteSelection.isNotEmpty()) selectQuoteSentence(sentence)
                                    },
                                    onSentenceDoubleClick = { sentence ->
                                        if (quoteSelection.isEmpty()) onSentenceClick(sentence)
                                    },
                                    onReturnToCover = handleReturnToPlayerFromRead,
                                    fontSizeSp = fontSizeSp,
                                    quoteSelection = quoteSelection,
                                    onSentenceLongClick = { sentence ->
                                        haptics.select()
                                        selectQuoteSentence(sentence)
                                    }
                                )
                                if (fullScreenRead && quoteSelection.isEmpty()) {
                                    Row(
                                        Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        ListenControl(
                                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            if (isPlaying) {
                                                com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_pause)
                                            } else {
                                                com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_play)
                                            },
                                            glass,
                                            Modifier.size(52.dp),
                                            playback = true,
                                            preparing = preparing,
                                            onClick = onPlayPause
                                        )
                                        ListenControl(
                                            Icons.Default.FormatSize,
                                            com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_text_size),
                                            glass,
                                            Modifier.size(52.dp),
                                            onClick = { showTextSize = true }
                                        )
                                        ListenControl(
                                            Icons.Default.FullscreenExit,
                                            com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_exit_full_screen_reading),
                                            glass,
                                            Modifier
                                                .size(52.dp)
                                                .testTag("listen-exit-fullscreen"),
                                            onClick = { fullScreenRead = false }
                                        )
                                    }
                                }
                            }
                        } else {
                            ListenArtwork(
                                title = title,
                                documentId = documentId,
                                coverFile = coverFile,
                                progress = if (totalChunks > 0) (index + 1f) / totalChunks else 0f,
                                onOpenRead = handleEnterReadFromCover,
                                onNext = { haptics.select(); onNext() },
                                onPrevious = { haptics.select(); onPrevious() }
                            )
                        }
                    }

                    if (quoteSelection.isNotEmpty()) {
                        ListenSelectionActionCard(
                            selectedCount = quoteSelection.size,
                            quoteText = quoteText,
                            onPreviewQuote = { showQuotePreview = true },
                            onReadFromHere = {
                                quoteSelection.firstOrNull()?.let { onSentenceClick(it) }
                                quoteSelection = emptyList()
                            },
                            onCancel = { quoteSelection = emptyList(); showQuotePreview = false }
                        )
                    }

                    AnimatedVisibility(
                        visible = !isLandscapeSplit && !fullScreenRead && quoteSelection.isEmpty(),
                        enter = if (VeritasMotion.scheme.reduceMotion) EnterTransition.None else fadeIn(effects) + expandVertically(),
                        exit = if (VeritasMotion.scheme.reduceMotion) ExitTransition.None else fadeOut(effects) + shrinkVertically()
                    ) {
                        ListenPlayerControlsPanel(
                            title = title,
                            index = index,
                            totalChunks = totalChunks,
                            lastIndex = lastIndex,
                            position = position,
                            onScrubChange = { scrub = it },
                            onScrubFinished = {
                                scrub?.let { onSentenceClick(it.roundToInt().coerceIn(0, lastIndex)) }
                                scrub = null
                            },
                            showRead = showRead,
                            onReturnToPlayer = handleReturnToPlayerFromRead,
                            isBookmarked = isBookmarked,
                            onToggleBookmark = onToggleBookmark,
                            onEnterFullScreenRead = {
                                showRead = true
                                fullScreenRead = true
                            },
                            onOpenTextSize = { showTextSize = true },
                            isPlaying = isPlaying,
                            preparing = preparing,
                            onPlayPause = {
                                haptics.toggle(!isPlaying)
                                onPlayPause()
                            },
                            onPrevious = {
                                haptics.select()
                                onPrevious()
                            },
                            onNext = {
                                haptics.select()
                                onNext()
                            },
                            rate = rate,
                            ratesExpanded = ratesExpanded,
                            onRatesExpandedChange = { ratesExpanded = it },
                            onRateChange = onRateChange,
                            onOpenVoiceStudio = onOpenVoiceStudio,
                            onOpenSleepTimer = onOpenSleepTimer,
                            onSentenceClick = onSentenceClick,
                            glass = glass,
                            compactVertical = isLandscapeSplit
                        )
                    }
                }
            }
        }
    }

    val readingTextSizeLabel = menuString(com.veritas.reader.R.string.ui_reading_text_size)
    if (showTextSize) {
        AlertDialog(
            onDismissRequest = { showTextSize = false },
            title = { Text(readingTextSizeLabel) },
            text = {
                Column {
                    Text(
                        com.veritas.reader.ui.screens.menuString(
                            com.veritas.reader.R.string.l10n_dynamic_067,
                            com.veritas.reader.ui.screens.localizedUiValue(fontSizeSp)
                        )
                    )
                    VeritasSleekSlider(
                        value = fontSizeSp.toFloat(),
                        onValueChange = { onFontSizeChange(it.roundToInt().coerceIn(10, 28)) },
                        valueRange = 10f..28f,
                        stepIncrement = 1f,
                        sliderHeight = 48.dp,
                        modifier = Modifier.semantics {
                            contentDescription = readingTextSizeLabel
                            progressBarRangeInfo = ProgressBarRangeInfo(fontSizeSp.toFloat(), 10f..28f, 17)
                            setProgress {
                                onFontSizeChange(it.roundToInt().coerceIn(10, 28))
                                true
                            }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton({ showTextSize = false }) {
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_done))
                }
            }
        )
    }
}

@Composable
private fun ListenPlayerControlsPanel(
    title: String,
    index: Int,
    totalChunks: Int,
    lastIndex: Int,
    position: Float,
    onScrubChange: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    showRead: Boolean,
    onReturnToPlayer: () -> Unit,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onEnterFullScreenRead: () -> Unit,
    onOpenTextSize: () -> Unit,
    isPlaying: Boolean,
    preparing: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    rate: Float,
    ratesExpanded: Boolean,
    onRatesExpandedChange: (Boolean) -> Unit,
    onRateChange: (Float) -> Unit,
    onOpenVoiceStudio: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onSentenceClick: (Int) -> Unit,
    glass: Boolean,
    compactVertical: Boolean
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(if (compactVertical) 8.dp else 12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(if (compactVertical) 4.dp else 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = scheme.onBackground,
                maxLines = if (compactVertical) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (totalChunks > 0) {
                        com.veritas.reader.ui.screens.menuString(
                            com.veritas.reader.R.string.l10n_dynamic_065,
                            com.veritas.reader.ui.screens.localizedUiValue(index + 1),
                            com.veritas.reader.ui.screens.localizedUiValue(totalChunks)
                        )
                    } else {
                        com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_ready_to_listen)
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ListenControl(
                        if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkAdd,
                        if (isBookmarked) {
                            com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_remove_bookmark)
                        } else {
                            com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_add_bookmark)
                        },
                        glass,
                        Modifier.size(48.dp),
                        onClick = onToggleBookmark
                    )
                    ListenControl(
                        Icons.Default.Fullscreen,
                        com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_full_screen_reading),
                        glass,
                        Modifier
                            .size(48.dp)
                            .testTag("listen-fullscreen"),
                        onClick = onEnterFullScreenRead
                    )
                    ListenControl(
                        Icons.Default.FormatSize,
                        com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_text_size),
                        glass,
                        Modifier
                            .size(48.dp)
                            .testTag("listen-text-size"),
                        onClick = onOpenTextSize
                    )
                }
            }
        }
        // Retained Listening player sentence progress / seek slider
        Column {
            val readingPositionLabel = menuString(com.veritas.reader.R.string.ui_reading_progress)
            VeritasSleekSlider(
                position,
                onScrubChange,
                modifier = Modifier.semantics {
                    contentDescription = readingPositionLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        position,
                        0f..lastIndex.coerceAtLeast(1).toFloat(),
                        lastIndex.coerceAtLeast(1) - 1
                    )
                    setProgress { requested ->
                        onSentenceClick(requested.roundToInt().coerceIn(0, lastIndex))
                        true
                    }
                },
                valueRange = 0f..lastIndex.coerceAtLeast(1).toFloat(),
                stepIncrement = 1f,
                enabled = totalChunks > 1,
                sliderHeight = if (compactVertical) 34.dp else 40.dp,
                trackHeight = 3.dp,
                thumbSize = 14.dp,
                wavy = true,
                isPlaying = isPlaying,
                onValueChangeFinished = onScrubFinished
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val currentPosition = if (totalChunks > 0) position.roundToInt() + 1 else 0
                Text(
                    "${localizedUiValue(currentPosition)} / ${localizedUiValue(totalChunks)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
                Text(
                    com.veritas.reader.ui.screens.menuString(
                        com.veritas.reader.R.string.l10n_dynamic_066,
                        com.veritas.reader.ui.screens.localizedUiValue((totalChunks - position.roundToInt() - 1).coerceAtLeast(0))
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ListenControl(
                Icons.Default.SkipPrevious,
                com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_previous_sentence),
                glass,
                Modifier.size(if (compactVertical) 52.dp else 60.dp),
                onClick = onPrevious
            )
            ListenControl(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (preparing) {
                    com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_cancel_starting_playback)
                } else if (isPlaying) {
                    com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_pause)
                } else {
                    com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_play)
                },
                glass,
                Modifier
                    .weight(1f)
                    .height(if (compactVertical) 56.dp else 64.dp),
                label = if (preparing) {
                    com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_starting)
                } else if (isPlaying) {
                    com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_pause)
                } else {
                    com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_play)
                },
                playback = true,
                preparing = preparing,
                emphasized = true,
                onClick = onPlayPause
            )
            ListenControl(
                Icons.Default.SkipNext,
                com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_next_sentence),
                glass,
                Modifier.size(if (compactVertical) 52.dp else 60.dp),
                onClick = onNext
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) {
                ListenControl(
                    Icons.Outlined.Speed,
                    com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_playback_speed),
                    glass,
                    Modifier
                        .fillMaxWidth()
                        .height(if (compactVertical) 44.dp else 48.dp),
                    label = String.format(Locale.US, "%.2f", rate).trimEnd('0').trimEnd('.') + "x",
                    onClick = { onRatesExpandedChange(true) }
                )
                DropdownMenu(ratesExpanded, { onRatesExpandedChange(false) }) {
                    listOf(.8f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                        DropdownMenuItem(
                            text = { Text("${speed}x") },
                            onClick = {
                                onRateChange(speed)
                                onRatesExpandedChange(false)
                            }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_slower_0_05)) },
                        onClick = { onRateChange((rate - .05f).coerceAtLeast(.5f)) }
                    )
                    DropdownMenuItem(
                        text = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_faster_0_05)) },
                        onClick = { onRateChange((rate + .05f).coerceAtMost(3f)) }
                    )
                }
            }
            ListenControl(
                Icons.Outlined.RecordVoiceOver,
                com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_voice_language),
                glass,
                Modifier
                    .weight(1f)
                    .height(if (compactVertical) 44.dp else 48.dp),
                label = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_voice_3091c84),
                onClick = onOpenVoiceStudio
            )
            ListenControl(
                Icons.Outlined.Timer,
                com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_sleep_timer),
                glass,
                Modifier
                    .weight(1f)
                    .height(if (compactVertical) 44.dp else 48.dp),
                label = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_timer),
                onClick = onOpenSleepTimer
            )
        }
    }
}

@Composable
private fun ListenNavigationDiscoveryHint(
    message: String,
    testTagPrefix: String,
    onRemoveForSession: () -> Unit,
    onDontShowAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("$testTagPrefix-banner"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = scheme.surfaceVariant.copy(alpha = 0.96f),
            contentColor = scheme.onSurfaceVariant
        ),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.40f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = scheme.primaryContainer,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            tint = scheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(5.dp)
                                .size(18.dp)
                        )
                    }
                    Text(
                        text = "Listen Gestures",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface
                    )
                }

                IconButton(
                    onClick = onRemoveForSession,
                    modifier = Modifier.size(24.dp).testTag("$testTagPrefix-remove")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = scheme.onSurfaceVariant.copy(alpha = 0.70f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val lines = message.split("\n").filter { it.isNotBlank() }
            lines.forEachIndexed { idx, line ->
                if (idx > 0) Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDontShowAgain,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("$testTagPrefix-dont-show-again")
                ) {
                    Text(
                        text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_don_t_show_again),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(
                    onClick = onRemoveForSession,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Got it",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ListenArtwork(
    title: String,
    documentId: String?,
    coverFile: File?,
    progress: Float,
    onOpenRead: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val openRead by rememberUpdatedState(onOpenRead)
    val next by rememberUpdatedState(onNext)
    val previous by rememberUpdatedState(onPrevious)
    val cacheKey = "${documentId ?: title}:${coverFile?.absolutePath}:${coverFile?.lastModified()}"
    val initialArtwork = remember(cacheKey) {
        AudioCoverCache.get(cacheKey) ?: documentId?.let { id ->
            BookCoverLoader.document(context, id)?.let { bmp -> bmp to null }
        }
    }
    val artwork by produceState<Pair<android.graphics.Bitmap, androidx.palette.graphics.Palette?>?>(
        initialValue = initialArtwork,
        documentId,
        title,
        coverFile?.absolutePath,
        coverFile?.lastModified()
    ) {
        if (value?.first != null && value?.second != null) return@produceState
        val loaded = withContext(Dispatchers.IO) {
            val existingBmp = value?.first
            val bitmap = existingBmp ?: coverFile?.takeIf { it.isFile }?.let { file ->
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, bounds)
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 900) sample *= 2
                    BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                }.getOrNull()
            } ?: documentId?.let { DocumentRepository(context).findDocument(it) }?.let { BookCoverLoader.document(context, it.id, it.catalogId) }
              ?: BookCoverLoader.generated(
                    context,
                    documentId ?: title,
                    getBookAndAuthor(title).first,
                    documentId?.let { DocumentRepository(context).findDocument(it) }?.sourceLabel.orEmpty(),
                    getBookAndAuthor(title).second
                )
            val palette = runCatching {
                androidx.palette.graphics.Palette.from(bitmap).resizeBitmapArea(16000).generate()
            }.getOrNull()
            val pair = bitmap to palette
            AudioCoverCache.put(cacheKey, pair)
            pair
        }
        value = loaded
    }
    val bitmap = artwork?.first
    val brand = VernCustomization.palette(VeritasThemeState.launcherPalette, VeritasThemeState.themeId, isSystemInDarkTheme())
    val dominant = artwork?.second?.getDominantColor(brand.background)?.let { Color(it) } ?: Color(brand.background)
    val accent = artwork?.second?.getVibrantColor(brand.background)?.let { Color(it) } ?: dominant
    val cardStart = lerp(scheme.surfaceContainer, accent, .42f)
    val cardEnd = lerp(scheme.surfaceContainer, dominant, .22f)
    val coverClickLabel = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_read)

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Enlarged cover footprint occupying the space freed by removing the Player/Read toggle pills,
        // while strictly preserving the cover artwork's native aspect ratio (ContentScale.Fit).
        val side = minOf(maxWidth * .92f, maxHeight * .96f, 396.dp).coerceAtLeast(1.dp)
        val shape = RoundedCornerShape(32.dp)
        Surface(
            Modifier
                .size(side)
                .veritasGlassOutline(shape, true)
                .testTag("listen-artwork")
                .semantics {
                    role = Role.Button
                }
                .clickable(
                    onClickLabel = coverClickLabel,
                    onClick = { openRead() }
                )
                .pointerInput(Unit) {
                    var drag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragCancel = { drag = 0f },
                        onDragEnd = {
                            if (drag < -72.dp.toPx()) next() else if (drag > 72.dp.toPx()) previous()
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            drag += amount
                        }
                    )
                },
            shape = shape,
            color = scheme.surfaceContainer,
            shadowElevation = 12.dp
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(cardStart, cardEnd))),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    val available = (side - 24.dp).coerceAtLeast(1.dp)
                    val ratio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
                    val width = if (ratio < 1f) available * ratio else available
                    val height = if (ratio < 1f) available else available / ratio
                    Image(
                        bitmap.asImageBitmap(),
                        com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_book_cover),
                        Modifier
                            .size(width, height)
                            .clip(RoundedCornerShape(24.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    val available = (side - 24.dp).coerceAtLeast(1.dp)
                    VeritasCoverPlaceholder(
                        documentId ?: title,
                        title,
                        "",
                        Modifier
                            .width(available * 2f / 3f)
                            .height(available)
                    )
                }
                Surface(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp),
                    shape = RoundedCornerShape(50),
                    color = scheme.surface.copy(alpha = .85f),
                    contentColor = scheme.onSurface
                ) {
                    Text(
                        "${(progress.coerceIn(0f, 1f) * 100).roundToInt()}%",
                        Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListenSentenceList(
    chunks: List<String>,
    currentIndex: Int,
    onSentenceClick: (Int) -> Unit,
    onSentenceDoubleClick: (Int) -> Unit,
    onReturnToCover: () -> Unit,
    fontSizeSp: Int,
    quoteSelection: List<Int>,
    onSentenceLongClick: (Int) -> Unit
) {
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
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(quoteSelection.isEmpty()) {
                detectTapGestures(
                    onTap = {
                        if (quoteSelection.isEmpty()) onReturnToCover()
                    }
                )
            }
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .testTag("listen-sentences")
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // Fade only the sentences so the page's glass backdrop remains continuous.
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            .08f to Color.Black,
                            .92f to Color.Black,
                            1f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                },
            state = state,
            contentPadding = PaddingValues(vertical = 72.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            itemsIndexed(chunks) { index, sentence ->
                val displayText = remember(sentence) { com.veritas.reader.ReadingTextCleanup.forDisplay(sentence) }
                if (displayText.isBlank()) return@itemsIndexed
                val active = index == currentIndex
                val quoteSelected = index in quoteSelection
                val alpha by animateFloatAsState(if (active || quoteSelected) 1f else .46f, VeritasMotion.effectsSlow(), label = "sentenceOpacity")
                val scale by animateFloatAsState(if (active || quoteSelected || reducedMotion) 1f else .95f, VeritasMotion.spatialSlow(), label = "sentenceScale")
                val lift by animateFloatAsState(if (active || quoteSelected || reducedMotion) 0f else 5f, VeritasMotion.spatialSlow(), label = "sentenceLift")
                // Stable typography prevents intermediate font sizes from rewrapping the paragraph.
                Text(
                    displayText,
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .graphicsLayer {
                            this.alpha = alpha
                            scaleX = scale
                            scaleY = scale
                            translationY = with(density) { lift.dp.toPx() }
                        }
                        .background(if (quoteSelected) scheme.primaryContainer else Color.Transparent, RoundedCornerShape(12.dp))
                        .testTag("listen-sentence-$index")
                        .semantics { selected = if (quoteSelection.isNotEmpty()) quoteSelected else active }
                        .combinedClickable(
                            onClick = {
                                if (quoteSelection.isNotEmpty()) {
                                    onSentenceClick(index)
                                } else {
                                    onReturnToCover()
                                }
                            },
                            onDoubleClick = {
                                if (quoteSelection.isEmpty()) {
                                    onSentenceDoubleClick(index)
                                }
                            },
                            onLongClickLabel = "Select for quote",
                            onLongClick = { onSentenceLongClick(index) }
                        ),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSizeSp.sp, lineHeight = (fontSizeSp * 1.5f).sp),
                    color = scheme.onBackground
                )
            }
        }
    }
}

@Composable
private fun ListenControl(
    icon: ImageVector,
    description: String,
    glass: Boolean,
    modifier: Modifier = Modifier,
    label: String? = null,
    playback: Boolean = false,
    preparing: Boolean = false,
    emphasized: Boolean = false,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        modifier = modifier
            .pressScale(interaction, .97f)
            .veritasGlassBackdrop(shape, glass, surfaceOpacity = .28f)
            .semantics { if (!playback) contentDescription = description },
        shape = shape,
        interactionSource = interaction,
        color = if (glass) Color.Transparent else if (emphasized) scheme.primaryContainer else scheme.surfaceContainerHigh,
        contentColor = scheme.onSurface,
        border = if (glass) null else BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = .6f))
    ) {
        Row(
            Modifier.padding(horizontal = if (label == null) 0.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            if (playback) {
                PlaybackControlIcon(icon == Icons.Default.Pause, preparing, tint = scheme.onSurface, size = 30.dp)
            } else {
                Icon(icon, null, Modifier.size(if (label == null) 25.dp else 20.dp))
            }
            if (label != null) {
                Text(
                    label,
                    style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
