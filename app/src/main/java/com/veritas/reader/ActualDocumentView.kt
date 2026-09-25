package com.veritas.reader

import com.veritas.reader.ui.screens.SmartOutlineDialog

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.luminance
import com.veritas.reader.blendColors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.veritas.reader.ui.VeritasSleekSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import com.veritas.reader.ui.screens.KeepScreenAwake
import com.veritas.reader.ui.rememberSliderHaptics
import com.veritas.reader.ui.screens.monitorReadingActivity

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ActualDocumentView(
    document: SavedDocument,
    repository: DocumentRepository,
    /** 1-based page the active sentence sits on; 0 when it is not known yet. */
    activeSentencePage: Int,
    /** Text of the sentence being spoken, used to highlight the matching line. */
    activeSentenceText: String,
    isPlaying: Boolean,
    statusMessage: String,
    queueCount: Int,
    rate: Float,
    pitch: Float,
    fontSizeSp: Int,
    sectionSpacingDp: Int = 10,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPageChanged: (Int, Int) -> Unit,
    onOpenExternal: () -> Unit,
    onRateChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onSectionSpacingChange: (Int) -> Unit = {},
    onOpenVoiceStudio: () -> Unit,
    voices: List<TtsVoiceOption>,
    voiceSettings: VoiceSettings,
    onVoiceSelected: (TtsVoiceOption) -> Unit,
    /** Selected text plus the 1-based page it was selected on, so the match can be scoped. */
    onReadFromSentence: ((String, Int) -> Unit)? = null,
    initialPaperToneMode: PaperToneMode = PaperToneMode.fromString(repository.loadReaderSettings().paperToneMode),
    onPaperToneModeChange: ((PaperToneMode) -> Unit)? = null,
    onClose: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val original = repository.originalUri(document)
    // Opening Original view used to discard where the reader actually was and start at
    // page 1. Seed from the active sentence's own page instead.
    var pageIndex by remember(document.id) {
        mutableIntStateOf((activeSentencePage - 1).coerceAtLeast(0))
    }
    var pageCount by remember(document.id) { mutableIntStateOf(1) }
    var bitmap by remember(document.id) { mutableStateOf<Bitmap?>(null) }
    var message by remember(document.id) { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var zoomScale by remember(document.id) { mutableFloatStateOf(1f) }
    var zoomOffset by remember(document.id) { mutableStateOf(Offset.Zero) }
    var rotationDegrees by remember(document.id) { mutableIntStateOf(0) }
    var pageTurnDirection by remember(document.id) { mutableIntStateOf(0) }
    var topBarVisible by remember { mutableStateOf(!isLandscape) }
    var bottomBarVisible by remember { mutableStateOf(!isLandscape) }
    var paperToneMode by remember(initialPaperToneMode) {
        mutableStateOf(initialPaperToneMode)
    }
    var selectedCanvasText by remember { mutableStateOf<String?>(null) }
    var showJumpToPageDialog by remember { mutableStateOf(false) }
    var showDocInfoDialog by remember { mutableStateOf(false) }
    var showOutlineDialog by remember { mutableStateOf(false) }
    val readerDocument = remember(document) { buildReaderDocument(document, repository.readText(document)) }
    val documentOutline = remember(document.id) { repository.loadDocumentOutline(document, readerDocument.chunks) }
    var interactionTrigger by remember { mutableStateOf(0L) }
    KeepScreenAwake(enabled = true, interactionTrigger = interactionTrigger)

    // Bars retire on their own after a quiet spell so the page owns the screen while reading.
    // Keyed on interactionTrigger, which monitorReadingActivity bumps on any touch, so each
    // touch restarts this countdown rather than stacking another one behind it. Tapping the
    // page brings them back. Held open while a menu, dialog or text selection is up: those are
    // all driven from the bars, and collapsing underneath them would strand the user again.
    LaunchedEffect(
        interactionTrigger,
        topBarVisible,
        bottomBarVisible,
        showMenu,
        showJumpToPageDialog,
        showDocInfoDialog,
        showOutlineDialog,
        selectedCanvasText
    ) {
        val overlayOpen = showMenu ||
            showJumpToPageDialog ||
            showDocInfoDialog ||
            showOutlineDialog ||
            selectedCanvasText != null
        if ((topBarVisible || bottomBarVisible) && !overlayOpen) {
            kotlinx.coroutines.delay(BARS_AUTO_HIDE_MS)
            topBarVisible = false
            bottomBarVisible = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            (context as? android.app.Activity)?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val isPdf = remember(document) { detectIsPdf(document, repository, context) }
    val isImage = remember(document, isPdf) { detectIsImage(document, repository, context, isPdf) }
    val isPresentation = remember(document, isPdf, isImage) { detectIsPresentation(document, isPdf, isImage) }
    val isEpub = remember(document, isPdf, isImage, isPresentation) { detectIsEpub(document, isPdf, isImage, isPresentation) }
    val isDocx = remember(document, isPdf, isImage, isPresentation, isEpub) { detectIsDocx(document, isPdf, isImage, isPresentation, isEpub) }

    var pptxDeck by remember { mutableStateOf<PptxDeck?>(null) }
    var currentSlideImages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var showSpeakerNotes by remember { mutableStateOf(false) }
    var epubBook by remember { mutableStateOf<EpubBook?>(null) }
    var docxDoc by remember { mutableStateOf<DocxDocument?>(null) }

    // Parsing belongs to the document, not the page.
    LaunchedEffect(original?.toString()) {
        message = null
        if (original == null) {
            message = "No stored original is available for this reading. Re-import the file to enable Original View."
            return@LaunchedEffect
        }
        if (isPresentation) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { loadPresentationDeck(context, original, document) }
            }
            loaded.onSuccess { deck ->
                pptxDeck = deck
                pageCount = deck.slideCount.coerceAtLeast(1)
                if (pageIndex > pageCount - 1) pageIndex = pageCount - 1
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                message = "Could not load presentation slides: ${e.message ?: "unknown error"}"
            }
        } else if (isEpub) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { loadEpubBook(context, original, document.title) }
            }
            loaded.onSuccess { book ->
                epubBook = book
                pageCount = book.totalChapters.coerceAtLeast(1)
                if (pageIndex > pageCount - 1) pageIndex = pageCount - 1
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                message = "Could not parse EPUB book: ${e.message ?: "unknown error"}"
            }
        } else if (isDocx) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { loadDocxDocument(context, original, document.title) }
            }
            loaded.onSuccess { doc ->
                docxDoc = doc
                pageCount = doc.totalPages.coerceAtLeast(1)
                if (pageIndex > pageCount - 1) pageIndex = pageCount - 1
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                message = "Could not parse Word document: ${e.message ?: "unknown error"}"
            }
        } else if (!isPdf && !isImage) {
            message = "This file type is preserved as an original document, but Vern cannot render it in-app yet. Use Open original from the menu."
        }
    }

    // Page-scoped work only: the rendered PDF page or the decoded image.
    LaunchedEffect(original?.toString(), pageIndex) {
        if (original == null) return@LaunchedEffect
        if (isPdf || isImage) bitmap = null
        if (isPdf) {
            val rendered = withContext(Dispatchers.IO) {
                runCatching { renderPdfPage(context, original, pageIndex) }
            }
            rendered.onSuccess {
                pageCount = it.pageCount
                if (pageIndex > it.pageCount - 1) pageIndex = it.pageCount - 1
                bitmap = it.bitmap
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                message = "Could not render this PDF page: ${e.message ?: "unknown error"}"
            }
        } else if (isImage) {
            val decoded = withContext(Dispatchers.IO) {
                runCatching { decodeImageBitmap(context, original) }
            }
            decoded.onSuccess { image ->
                bitmap = image
                pageCount = 1
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                message = "Could not open this image: ${e.message ?: "unknown error"}"
            }
        }
    }

    // Slide images are page work and are re-read per slide rather than held for the whole deck.
    LaunchedEffect(original?.toString(), pageIndex, pptxDeck) {
        if (original == null || !isPresentation) return@LaunchedEffect
        currentSlideImages = emptyList()
        val deck = pptxDeck ?: return@LaunchedEffect
        val slide = deck.slides.getOrNull(pageIndex.coerceIn(0, (deck.slideCount - 1).coerceAtLeast(0)))
            ?: return@LaunchedEffect
        val images = withContext(Dispatchers.IO) {
            loadSlideImages(context, original, slide)
        }
        currentSlideImages = images
    }

    LaunchedEffect(activeSentencePage, pageCount) {
        // Sentences are not spread evenly across pages, so scaling reading progress by page
        // count drifted further the longer the document ran. The sentence carries its own
        // page number; use it.
        if ((isPdf || isPresentation || isEpub || isDocx) && pageCount > 1 && activeSentencePage > 0) {
            val syncedPage = (activeSentencePage - 1).coerceIn(0, pageCount - 1)
            if (syncedPage != pageIndex) {
                pageTurnDirection = if (syncedPage > pageIndex) 1 else -1
                pageIndex = syncedPage
            }
        }
    }

    LaunchedEffect(document.id, pageIndex) {
        zoomScale = 1f
        zoomOffset = Offset.Zero
    }

    fun selectPage(target: Int) {
        if ((!isPdf && !isPresentation && !isEpub && !isDocx) || pageCount <= 1) return
        val safeTarget = target.coerceIn(0, pageCount - 1)
        if (safeTarget != pageIndex) {
            pageTurnDirection = if (safeTarget > pageIndex) 1 else -1
            pageIndex = safeTarget
            onPageChanged(safeTarget, pageCount)
        }
    }

    // Page-turn entrance: when a freshly rendered page arrives, it slides in a little
    // from the swipe direction while fading, instead of hard-cutting. 0 = just arrived.
    val pageEnter = remember { Animatable(1f) }
    LaunchedEffect(bitmap) {
        if (bitmap != null) {
            pageEnter.snapTo(0f)
            pageEnter.animateTo(1f, tween(durationMillis = 260, easing = LinearOutSlowInEasing))
        }
    }

    val topBarOffset by animateFloatAsState(
        targetValue = if (topBarVisible) 0f else -300f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "topBarOffset"
    )
    val bottomBarOffset by animateFloatAsState(
        targetValue = if (bottomBarVisible) 0f else 400f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "bottomBarOffset"
    )

    val colorScheme = MaterialTheme.colorScheme
    val activeBg = colorScheme.background
    val activeSurface = colorScheme.surface
    val activeOnSurface = colorScheme.onSurface
    val isThemeDark = remember(activeBg) {
        val bg = activeBg
        (0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue) < 0.5f
    }

    // Dynamic theme palette ColorMatrix: Active Theme maps white paper to theme's dark surface,
    // Dark mode maps to high contrast inverted paper, and Natural White disables filter.
    val darkThemeColorFilter = remember(activeSurface, activeOnSurface, isThemeDark, paperToneMode) {
        when (paperToneMode) {
            PaperToneMode.ACTIVE_THEME -> {
                if (isThemeDark) {
                    val bgR = activeSurface.red * 255f
                    val bgG = activeSurface.green * 255f
                    val bgB = activeSurface.blue * 255f
                    val fgR = activeOnSurface.red * 255f
                    val fgG = activeOnSurface.green * 255f
                    val fgB = activeOnSurface.blue * 255f

                    val scaleR = (bgR - fgR) / 255f
                    val scaleG = (bgG - fgG) / 255f
                    val scaleB = (bgB - fgB) / 255f

                    ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                        scaleR, 0f,     0f,     0f, fgR,
                        0f,     scaleG, 0f,     0f, fgG,
                        0f,     0f,     scaleB, 0f, fgB,
                        0f,     0f,     0f,     1f, 0f
                    )))
                } else {
                    val rScale = activeSurface.red
                    val gScale = activeSurface.green
                    val bScale = activeSurface.blue
                    ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                        rScale, 0f,     0f,     0f, 0f,
                        0f,     gScale, 0f,     0f, 0f,
                        0f,     0f,     bScale, 0f, 0f,
                        0f,     0f,     0f,     1f, 0f
                    )))
                }
            }
            PaperToneMode.DARK -> {
                // Kindle Dark Slate: #141414 background (20), #E4E4E4 text (228)
                val scale = (20f - 228f) / 255f
                ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                    scale, 0f,    0f,    0f, 228f,
                    0f,    scale, 0f,    0f, 228f,
                    0f,    0f,    scale, 0f, 228f,
                    0f,    0f,    0f,    1f, 0f
                )))
            }
            PaperToneMode.WARM_SEPIA -> {
                // Multiplicative Tint: #FBF0D9 background (251, 240, 217). Preserves 100% natural colors in photos!
                val rScale = 251f / 255f
                val gScale = 240f / 255f
                val bScale = 217f / 255f
                ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
                    rScale, 0f,     0f,     0f, 0f,
                    0f,     gScale, 0f,     0f, 0f,
                    0f,     0f,     bScale, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f
                )))
            }
            PaperToneMode.NATURAL_WHITE -> null
        }
    }

    val originalToolbar = androidx.compose.ui.platform.LocalTextToolbar.current
    val customToolbar = remember(originalToolbar) {
        object : androidx.compose.ui.platform.TextToolbar {
            override val status: androidx.compose.ui.platform.TextToolbarStatus
                get() = originalToolbar.status

            override fun showMenu(
                rect: androidx.compose.ui.geometry.Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                val wrappedCopy = onCopyRequested?.let { originalCopy ->
                    {
                        originalCopy.invoke()
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                        if (!clipText.isNullOrBlank()) {
                            selectedCanvasText = clipText
                        }
                    }
                }
                originalToolbar.showMenu(
                    rect = rect,
                    onCopyRequested = wrappedCopy ?: onCopyRequested,
                    onPasteRequested = onPasteRequested,
                    onCutRequested = onCutRequested,
                    onSelectAllRequested = onSelectAllRequested
                )
            }

            override fun hide() {
                originalToolbar.hide()
            }
        }
    }

    CompositionLocalProvider(androidx.compose.ui.platform.LocalTextToolbar provides customToolbar) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
                .monitorReadingActivity { interactionTrigger = System.currentTimeMillis() }
        ) {
            // 1. Full-screen rendering canvas with universal Zoom & Pan
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
            val density = LocalDensity.current
            val viewportWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            val viewportHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
            fun clampOffset(offset: Offset, scale: Float): Offset {
                if (scale <= 1.0f) return Offset.Zero
                val maxOffsetX = maxOf(0f, (viewportWidthPx * (scale - 1f)) / 2f)
                val maxOffsetY = maxOf(0f, (viewportHeightPx * (scale - 1f)) / 2f)
                return Offset(
                    offset.x.coerceIn(-maxOffsetX, maxOffsetX),
                    offset.y.coerceIn(-maxOffsetY, maxOffsetY)
                )
            }
            fun setZoom(nextScale: Float, nextOffset: Offset = zoomOffset) {
                val safeScale = nextScale.coerceIn(MIN_CANVAS_ZOOM, MAX_CANVAS_ZOOM)
                zoomScale = safeScale
                zoomOffset = clampOffset(nextOffset, safeScale)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val enter = pageEnter.value
                        scaleX = zoomScale
                        scaleY = zoomScale
                        rotationZ = rotationDegrees.toFloat()
                        translationX = zoomOffset.x + (1f - enter) * pageTurnDirection * 48.dp.toPx()
                        translationY = zoomOffset.y
                        alpha = 0.3f + 0.7f * enter
                    }
                    .pointerInput(pageIndex, pageCount, viewportWidthPx, viewportHeightPx, zoomScale) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var zoomCentroid = down.position
                            var dragTotalX = 0f
                            var dragTotalY = 0f
                            var isPinchZoom = false

                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size > 1) {
                                    isPinchZoom = true
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    if (centroid != Offset.Unspecified) {
                                        zoomCentroid = centroid
                                    }

                                    val oldScale = zoomScale
                                    val nextScale = (oldScale * zoom).coerceIn(MIN_CANVAS_ZOOM, MAX_CANVAS_ZOOM)
                                    val actualFactor = if (oldScale > 0.0001f) nextScale / oldScale else 1f

                                    val center = Offset(viewportWidthPx / 2f, viewportHeightPx / 2f)
                                    val focal = zoomCentroid - center

                                    val targetOffset = if (nextScale > 0.80f) {
                                        (zoomOffset - focal) * actualFactor + focal + pan
                                    } else {
                                        Offset.Zero
                                    }
                                    setZoom(nextScale, targetOffset)
                                    pressed.forEach { it.consume() }
                                    dragTotalX = 0f
                                    dragTotalY = 0f
                                } else if (pressed.size == 1) {
                                    val change = pressed.first()
                                    val pan = change.positionChange()
                                    if (zoomScale > 1.05f) {
                                        if (pan != Offset.Zero) {
                                            zoomOffset = clampOffset(zoomOffset + pan, zoomScale)
                                            change.consume()
                                        }
                                    } else if (!isPinchZoom) {
                                        dragTotalX += pan.x
                                        dragTotalY += pan.y
                                        if (kotlin.math.abs(dragTotalX) > 24f && kotlin.math.abs(dragTotalX) > kotlin.math.abs(dragTotalY)) {
                                            change.consume()
                                        }
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            if (!isPinchZoom && zoomScale <= 1.05f) {
                                val absX = kotlin.math.abs(dragTotalX)
                                val absY = kotlin.math.abs(dragTotalY)
                                if (absX > 40f && absX > absY) {
                                    if (dragTotalX < -40f) {
                                        selectPage(pageIndex + 1)
                                    } else if (dragTotalX > 40f) {
                                        selectPage(pageIndex - 1)
                                    }
                                } else if (absX < 12f && absY < 12f) {
                                    // Tapping the page is the only way back to the bars on this
                                    // path. Full Screen Mode is toggled from the overflow menu,
                                    // which lives in the top bar it hides, and landscape starts
                                    // with both bars hidden — so without this the PDF view can
                                    // be left with no reachable control at all. The slide, EPUB
                                    // and DOCX canvases already have their own tap toggle.
                                    topBarVisible = !topBarVisible
                                    bottomBarVisible = !bottomBarVisible
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                val image = bitmap
                when {
                    image != null -> {
                        Image(
                            bitmap = image.asImageBitmap(),
                            contentDescription = "Rendered original document",
                            colorFilter = darkThemeColorFilter,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    isPresentation && pptxDeck != null -> {
                        val currentSlide = pptxDeck?.slides?.getOrNull(pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
                        if (currentSlide != null) {
                            PresentationSlideCanvas(
                                slide = currentSlide,
                                slideCount = pageCount,
                                slideImages = currentSlideImages,
                                showNotes = showSpeakerNotes,
                                onToggleNotes = { showSpeakerNotes = !showSpeakerNotes },
                                onNextSlide = { selectPage(pageIndex + 1) },
                                onPrevSlide = { selectPage(pageIndex - 1) },
                                onToggleBars = {
                                    topBarVisible = !topBarVisible
                                    bottomBarVisible = !bottomBarVisible
                                },
                                onSelectText = { selectedCanvasText = it },
                                isPlaying = isPlaying,
                                activeSentencePage = activeSentencePage,
                                activeSentenceText = activeSentenceText,
                                paperToneMode = paperToneMode,
                                rotationDegrees = rotationDegrees,
                                isLandscape = isLandscape,
                                modifier = if (isLandscape) {
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 4.dp, vertical = if (topBarVisible) 40.dp else 4.dp)
                                } else {
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = if (!topBarVisible) 4.dp else 72.dp)
                                }
                            )
                        }
                    }
                    isEpub && epubBook != null -> {
                        val currentChapter = epubBook?.chapters?.getOrNull(pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
                        if (currentChapter != null) {
                            EpubBookCanvas(
                                bookTitle = epubBook?.title ?: document.title,
                                chapter = currentChapter,
                                chapterCount = pageCount,
                                onNextChapter = { selectPage(pageIndex + 1) },
                                onPrevChapter = { selectPage(pageIndex - 1) },
                                onToggleBars = {
                                    topBarVisible = !topBarVisible
                                    bottomBarVisible = !bottomBarVisible
                                },
                                onSelectText = { selectedCanvasText = it },
                                isPlaying = isPlaying,
                                activeSentencePage = activeSentencePage,
                                activeSentenceText = activeSentenceText,
                                paperToneMode = paperToneMode,
                                rotationDegrees = rotationDegrees,
                                isLandscape = isLandscape,
                                modifier = if (isLandscape) {
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 4.dp, vertical = if (topBarVisible) 40.dp else 4.dp)
                                } else {
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = if (!topBarVisible) 4.dp else 72.dp)
                                }
                            )
                        }
                    }
                    isDocx && docxDoc != null -> {
                        val currentPage = docxDoc?.pages?.getOrNull(pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
                        if (currentPage != null) {
                            DocxDocumentCanvas(
                                docTitle = docxDoc?.title ?: document.title,
                                page = currentPage,
                                pageCount = pageCount,
                                onNextPage = { selectPage(pageIndex + 1) },
                                onPrevPage = { selectPage(pageIndex - 1) },
                                onToggleBars = {
                                    topBarVisible = !topBarVisible
                                    bottomBarVisible = !bottomBarVisible
                                },
                                onSelectText = { selectedCanvasText = it },
                                isPlaying = isPlaying,
                                activeSentencePage = activeSentencePage,
                                activeSentenceText = activeSentenceText,
                                paperToneMode = paperToneMode,
                                rotationDegrees = rotationDegrees,
                                isLandscape = isLandscape,
                                modifier = if (isLandscape) {
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 4.dp, vertical = if (topBarVisible) 40.dp else 4.dp)
                                } else {
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = if (!topBarVisible) 4.dp else 72.dp)
                                }
                            )
                        }
                    }
                    message == null && (isPdf || isImage || isPresentation || isEpub || isDocx) -> {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    else -> {
                        ActualDocumentUnavailableNotice(
                            message = message,
                            hasOriginal = original != null,
                            onOpenExternal = onOpenExternal,
                            onClose = onClose
                        )
                    }
                }
            }

            // Universal Zoom Control Pill at Bottom-Right
            ActualDocumentZoomPill(
                zoomScale = zoomScale,
                bottomBarVisible = bottomBarVisible,
                isLandscape = isLandscape,
                onZoomIn = {
                    val next = ((Math.round(zoomScale * 4f) + 1) / 4f).coerceIn(MIN_CANVAS_ZOOM, MAX_CANVAS_ZOOM)
                    setZoom(next)
                },
                onZoomOut = {
                    val next = ((Math.round(zoomScale * 4f) - 1) / 4f).coerceIn(MIN_CANVAS_ZOOM, MAX_CANVAS_ZOOM)
                    setZoom(next)
                },
                onZoomReset = { setZoom(1f, Offset.Zero) },
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        // 2. Floating Top app bar (Modernized Branded Identity)
        ActualDocumentTopBar(
            topBarOffset = topBarOffset,
            document = document,
            isPdf = isPdf,
            isPresentation = isPresentation,
            isEpub = isEpub,
            isDocx = isDocx,
            isImage = isImage,
            pageIndex = pageIndex,
            pageCount = pageCount,
            zoomScale = zoomScale,
            rotationDegrees = rotationDegrees,
            isLandscape = isLandscape,
            onPageSelected = ::selectPage,
            onClose = onClose,
            onToggleOrientation = {
                val activity = context as? android.app.Activity
                activity?.let { act ->
                    act.requestedOrientation = if (isLandscape) {
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    } else {
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                }
                zoomScale = 1f
                zoomOffset = Offset.Zero
            },
            topBarVisible = topBarVisible,
            onToggleFullScreen = {
                topBarVisible = !topBarVisible
                bottomBarVisible = !bottomBarVisible
            },
            onResetZoom = {
                zoomScale = 1f
                zoomOffset = Offset.Zero
            },
            paperToneMode = paperToneMode,
            onPaperToneModeChange = { newMode ->
                paperToneMode = newMode
                val curSettings = repository.loadReaderSettings()
                repository.saveReaderSettings(curSettings.copy(paperToneMode = newMode.name.lowercase()))
                val toneLabel = when (newMode) {
                    PaperToneMode.ACTIVE_THEME -> "Default"
                    PaperToneMode.DARK -> "Dark slate"
                    PaperToneMode.NATURAL_WHITE -> "Bone"
                    PaperToneMode.WARM_SEPIA -> "Sepia"
                }
                android.widget.Toast.makeText(context, "Paper tone: $toneLabel", android.widget.Toast.LENGTH_SHORT).show()
                onPaperToneModeChange?.invoke(newMode)
            },
            showMenu = showMenu,
            onMenuVisibilityChange = { showMenu = it },
            onOpenJumpToPageDialog = { showJumpToPageDialog = true },
            onOpenExternal = onOpenExternal,
            onOpenVoiceStudio = onOpenVoiceStudio,
            onOpenDocInfo = { showDocInfoDialog = true },
            onOpenOutline = { showOutlineDialog = true },
            hasOriginal = original != null,
            originalUri = original,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Table of Contents / Smart Outline Dialog
        if (showOutlineDialog) {
            SmartOutlineDialog(
                document = readerDocument,
                documentOutline = documentOutline,
                currentIndex = 0,
                onJumpToSection = { targetIndex ->
                    showOutlineDialog = false
                    val model = ReaderTextModelCache.get(document.id, readerDocument.rawText, document.pageCount)
                    val targetPage = model.sentences.getOrNull(targetIndex)?.pageNumber
                        ?: documentOutline.firstOrNull { it.targetIndex == targetIndex }?.pageNumber
                        ?: 1
                    selectPage((targetPage - 1).coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
                },
                onDismiss = { showOutlineDialog = false }
            )
        }

        // Jump to Page Dialog
        JumpToPageDialog(
            isOpen = showJumpToPageDialog,
            pageCount = pageCount,
            currentPageIndex = pageIndex,
            isPresentation = isPresentation,
            onDismiss = { showJumpToPageDialog = false },
            onConfirm = { selectPage(it) }
        )

        // Document Info Dialog
        DocumentInfoDialog(
            isOpen = showDocInfoDialog,
            document = document,
            isPdf = isPdf,
            isPresentation = isPresentation,
            isImage = isImage,
            pageCount = pageCount,
            pageIndex = pageIndex,
            onDismiss = { showDocInfoDialog = false }
        )

        // Selected Text Action Card
        if (selectedCanvasText != null) {
            SelectedTextActionCard(
                selectedText = selectedCanvasText.orEmpty(),
                bottomBarVisible = bottomBarVisible,
                isLandscape = isLandscape,
                pageIndex = pageIndex,
                onDismiss = { selectedCanvasText = null },
                onReadFromSentence = onReadFromSentence,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        // 3. Floating Bottom Player Panel
        ActualDocumentBottomBar(
            bottomBarOffset = bottomBarOffset,
            isPlaying = isPlaying,
            statusMessage = statusMessage,
            rate = rate,
            pitch = pitch,
            fontSizeSp = fontSizeSp,
            sectionSpacingDp = sectionSpacingDp,
            queueCount = queueCount,
            canGoPrevious = canGoPrevious,
            canGoNext = canGoNext,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onRateChange = onRateChange,
            onPitchChange = onPitchChange,
            onFontSizeChange = onFontSizeChange,
            onSectionSpacingChange = onSectionSpacingChange,
            onOpenVoiceStudio = onOpenVoiceStudio,
            voices = voices,
            voiceSettings = voiceSettings,
            onVoiceSelected = onVoiceSelected,
            onClose = onClose,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
}





/** Quiet time before the reader's chrome collapses on its own. */
private const val BARS_AUTO_HIDE_MS = 5_000L
