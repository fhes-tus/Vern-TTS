package com.veritas.reader

import android.animation.ValueAnimator
import androidx.core.animation.doOnEnd
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.luminance
import kotlin.math.roundToInt
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.veritas.reader.ui.VeritasSleekSliderView
import android.widget.Toast
import android.graphics.drawable.StateListDrawable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.pdf.PdfRect
import androidx.pdf.view.Highlight
import androidx.pdf.view.PdfView
import androidx.pdf.viewer.fragment.PdfViewerFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class VeritasPdfViewerActivity : AppCompatActivity() {
    internal val activity: VeritasPdfViewerActivity get() = this
    internal lateinit var repository: DocumentRepository
    internal var document: SavedDocument? = null
    internal var fragmentContainer: FrameLayout? = null
    internal var viewerFragment: PdfViewerFragment? = null
    internal var pdfView: PdfView? = null
    internal var playPauseControl: TextView? = null
    internal var rotateControl: TextView? = null
    internal var isSyncEnabled = true
    internal var syncPill: LinearLayout? = null
    internal var syncLabel: TextView? = null
    internal var highlightJob: Job? = null
    internal var extractedChunks: List<String> = emptyList()
    internal var readerTextModel: ReaderTextModel? = null
    internal var lastHighlightKey: String = ""
    internal var lastHighlightPage: Int? = null
    internal var pendingManualPageSync = false
    internal var lastSyncedTargetPage: Int? = null
    internal var toolbarChrome: View? = null
    internal var bottomChrome: View? = null
    internal var chromeHideJob: Job? = null
    internal var chromeVisible = false
    internal var chromeMenuOpen = false
    internal var tapDownX = 0f
    internal var tapDownY = 0f
    internal var tapDownTime = 0L
    internal var tapMoved = false
    // Expandable bottom panel state
    internal var panelExpanded = false
    internal var expandedPanelContent: LinearLayout? = null
    internal var panelExpandArrow: TextView? = null
    internal var panelStatusLabel: TextView? = null
    internal var panelSpeedLabel: TextView? = null
    internal var panelPitchLabel: TextView? = null
    internal var panelSpeedSlider: VeritasSleekSliderView? = null
    internal var panelPitchSlider: VeritasSleekSliderView? = null
    internal var keepAwakeTimerJob: Job? = null
    internal var pdfTocItems: List<PdfTocItem> = emptyList()
    internal var pdfLinksByPage: Map<Int, List<PdfLinkItem>> = emptyMap()
    internal var allDocumentLinks: List<PdfLinkItem> = emptyList()
    internal var isExtractingToc = false

    internal var isLightTheme = false
    internal var colorPrimary = 0
    internal var colorBackground = 0
    internal var colorToolbar = 0
    internal var colorSurface = 0
    internal var colorSurfaceVariant = 0
    internal var colorTextPrimary = 0
    internal var colorTextSecondary = 0
    internal var colorOutline = 0
    internal var colorSyncBackground = 0
    internal var colorActiveStrip = 0
    internal var colorAccentButton = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        repository = DocumentRepository(applicationContext)

        val settings = repository.loadReaderSettings()
        paperToneMode = PaperToneMode.fromString(settings.paperToneMode)
        val themeId = settings.themeId
        val packId = settings.themePackId

        val resolvedTheme = if (themeId == "system") {
            val mode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            if (mode == android.content.res.Configuration.UI_MODE_NIGHT_YES) "dark" else "light"
        } else {
            themeId
        }

        val baseScheme = veritasColorScheme(resolvedTheme, this)
        val scheme = veritasPackColorScheme(baseScheme, packId)

        isLightTheme = scheme.background.luminance() > 0.45f

        colorPrimary = scheme.primary.toArgb()
        colorBackground = scheme.background.toArgb()
        colorToolbar = scheme.surface.toArgb()
        colorSurface = scheme.surface.toArgb()
        colorSurfaceVariant = scheme.surfaceVariant.toArgb()
        colorTextPrimary = scheme.onSurface.toArgb()
        colorTextSecondary = scheme.onSurfaceVariant.toArgb()
        colorOutline = scheme.outlineVariant.toArgb()
        colorSyncBackground = scheme.primaryContainer.toArgb()
        colorActiveStrip = scheme.primary.toArgb()
        colorAccentButton = scheme.primary.toArgb()

        configureSystemBars()

        val documentId = intent.getStringExtra(EXTRA_DOCUMENT_ID).orEmpty()
        val metadata = repository.findDocument(documentId)
        val uri = metadata?.let { repository.originalUri(it) }
        if (metadata == null || uri == null) {
            showFallback("The original PDF is no longer available.")
            return
        }
        document = metadata
        buildLayout(metadata.title.ifBlank { getString(R.string.app_name) })
        applyPaperToneMode()
        loadHighlightTextAsync(metadata)
        loadPdfMetadataAndLinks(uri)
        runCatching {
            var fragment = supportFragmentManager.findFragmentByTag(VIEWER_TAG) as? PdfViewerFragment
            if (fragment == null) {
                fragment = PdfViewerFragment()
                supportFragmentManager.beginTransaction()
                    .replace(requireNotNull(fragmentContainer).id, fragment, VIEWER_TAG)
                    .commitNowAllowingStateLoss()
                fragment.documentUri = uri
            } else {
                fragment.documentUri = uri
            }
            viewerFragment = fragment
            schedulePdfViewLookup(fragment)
        }.onFailure { error ->
            showFallback("Vern could not open this PDF viewer: ${error.message ?: "unknown error"}")
        }
    }



    private fun resetInactivityTimer() {
        keepAwakeTimerJob?.cancel()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        keepAwakeTimerJob = lifecycleScope.launch {
            delay(20L * 60L * 1000L) // 20 minutes
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Silent reading in this viewer happens while MainActivity is stopped, so the app's
    // session timer isn't running — without this, Original-mode reading earned no reading
    // time and no streak day. Wall-clock while resumed, recorded on pause.
    private var viewerReadingStartedAt = 0L

    override fun onResume() {
        super.onResume()
        viewerReadingStartedAt = System.currentTimeMillis()
        val settings = repository.loadReaderSettings()
        val newTone = PaperToneMode.fromString(settings.paperToneMode)
        if (newTone != paperToneMode) {
            paperToneMode = newTone
            applyPaperToneMode()
        }
        updateRotateIcon()
        updatePlaybackControls()
        if (chromeVisible) scheduleChromeAutoHide()
        resetInactivityTimer()
    }

    override fun onPause() {
        super.onPause()
        saveCurrentProgress()
        recordViewerReadingSession()
        keepAwakeTimerJob?.cancel()
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun recordViewerReadingSession() {
        val doc = document ?: return
        val startedAt = viewerReadingStartedAt
        viewerReadingStartedAt = 0L
        if (startedAt <= 0L) return
        val delta = System.currentTimeMillis() - startedAt
        // Cap defensively: a forgotten open viewer overnight shouldn't count 8 hours.
        if (delta in 1_000L..(3L * 60L * 60L * 1000L)) {
            lifecycleScope.launch(Dispatchers.IO) {
                repository.recordDocReadingTime(doc.id, delta)
                repository.recordDocumentRead(doc.id, doc.title)
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        resetInactivityTimer()
        return super.dispatchTouchEvent(ev)
    }

    override fun onDestroy() {
        highlightJob?.cancel()
        chromeHideJob?.cancel()
        super.onDestroy()
    }

    private fun saveCurrentProgress() {
        val metadata = document ?: return
        val view = pdfView ?: return
        val visiblePage = runCatching { view.firstVisiblePage }.getOrNull() ?: return
        val model = readerTextModel ?: return
        // Eyes don't move the voice: while TTS is actively reading this document, the page
        // being LOOKED at must not overwrite the sentence being SPOKEN — that stomped the
        // live position and caused playback to restart from far above after navigation.
        if (PlaybackStateStore.isPlaying && PlaybackStateStore.activeDocumentId == metadata.id) return

        lifecycleScope.launch(Dispatchers.IO) {
            val pageNum = visiblePage + 1
            var sentenceIndex = model.sentences.indexOfFirst { it.pageNumber == pageNum }
            if (sentenceIndex == -1) {
                sentenceIndex = model.sentences.mapIndexed { idx, s -> idx to abs(s.pageNumber - pageNum) }
                    .minByOrNull { it.second }?.first ?: -1
            }
            
            if (sentenceIndex != -1) {
                repository.updateProgress(metadata.id, sentenceIndex, model.sentences.size)
                if (PlaybackStateStore.activeDocumentId == metadata.id) {
                    PlaybackStateStore.currentIndex = sentenceIndex
                }
            }
        }
    }




    override fun onActionModeStarted(mode: android.view.ActionMode?) {
        super.onActionModeStarted(mode)
        handleActionModeStarted(mode)
    }


    internal fun toggleSearch() {
        val fragment = viewerFragment ?: return
        runCatching {
            fragment.isTextSearchActive = !fragment.isTextSearchActive
        }.onFailure {
            Toast.makeText(this, "Search is not available for this PDF.", Toast.LENGTH_SHORT).show()
        }
    }

    internal fun rotateViewer() {
        requestedOrientation = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        updateRotateIcon()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updateRotateIcon()
    }

    internal fun openOriginal() {
        val metadata = document ?: return
        val uri = repository.originalUri(metadata) ?: run {
            Toast.makeText(this, "Could not prepare the file for opening.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, metadata.originalMimeType.ifBlank { "application/pdf" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(intent, "Open original document")) }
    }



    internal var paperToneMode = PaperToneMode.ACTIVE_THEME

    internal fun toggleFullScreen() {
        if (chromeVisible) {
            hideChrome()
        } else {
            showChrome(keepVisible = true)
        }
    }

    internal fun applyPaperToneMode() {
        when (paperToneMode) {
            PaperToneMode.ACTIVE_THEME -> {
                val settings = repository.loadReaderSettings()
                val resolvedTheme = if (settings.themeId == "system") {
                    val mode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                    if (mode == android.content.res.Configuration.UI_MODE_NIGHT_YES) "dark" else "light"
                } else settings.themeId
                val scheme = veritasPackColorScheme(veritasColorScheme(resolvedTheme, activity), settings.themePackId)
                val bgR = scheme.surface.red
                val bgG = scheme.surface.green
                val bgB = scheme.surface.blue
                val fgR = scheme.onSurface.red
                val fgG = scheme.onSurface.green
                val fgB = scheme.onSurface.blue

                val isDark = resolvedTheme == "dark" || resolvedTheme == "amoled" || (0.299f * bgR + 0.587f * bgG + 0.114f * bgB) < 0.5f
                if (isDark) {
                    val scaleR = (bgR * 255f - fgR * 255f) / 255f
                    val scaleG = (bgG * 255f - fgG * 255f) / 255f
                    val scaleB = (bgB * 255f - fgB * 255f) / 255f
                    val paint = android.graphics.Paint().apply {
                        colorFilter = android.graphics.ColorMatrixColorFilter(floatArrayOf(
                            scaleR, 0f,     0f,     0f, fgR * 255f,
                            0f,     scaleG, 0f,     0f, fgG * 255f,
                            0f,     0f,     scaleB, 0f, fgB * 255f,
                            0f,     0f,     0f,     1f, 0f
                        ))
                    }
                    fragmentContainer?.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
                } else {
                    val paint = android.graphics.Paint().apply {
                        colorFilter = android.graphics.ColorMatrixColorFilter(floatArrayOf(
                            bgR, 0f,  0f,  0f, 0f,
                            0f,  bgG, 0f,  0f, 0f,
                            0f,  0f,  bgB, 0f, 0f,
                            0f,  0f,  0f,  1f, 0f
                        ))
                    }
                    fragmentContainer?.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
                }
            }
            PaperToneMode.DARK -> {
                // Kindle Dark Slate: #141414 background (20), #E4E4E4 text (228)
                val scale = (20f - 228f) / 255f
                val paint = android.graphics.Paint().apply {
                    colorFilter = android.graphics.ColorMatrixColorFilter(floatArrayOf(
                        scale, 0f,    0f,    0f, 228f,
                        0f,    scale, 0f,    0f, 228f,
                        0f,    0f,    scale, 0f, 228f,
                        0f,    0f,    0f,    1f, 0f
                    ))
                }
                fragmentContainer?.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
            }
            PaperToneMode.WARM_SEPIA -> {
                // Multiplicative Tint: #FBF0D9 background (251, 240, 217). Preserves 100% natural colors in photos!
                val rScale = 251f / 255f
                val gScale = 240f / 255f
                val bScale = 217f / 255f
                val paint = android.graphics.Paint().apply {
                    colorFilter = android.graphics.ColorMatrixColorFilter(floatArrayOf(
                        rScale, 0f,     0f,     0f, 0f,
                        0f,     gScale, 0f,     0f, 0f,
                        0f,     0f,     bScale, 0f, 0f,
                        0f,     0f,     0f,     1f, 0f
                    ))
                }
                fragmentContainer?.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
            }
            PaperToneMode.NATURAL_WHITE -> {
                fragmentContainer?.setLayerType(View.LAYER_TYPE_NONE, null)
            }
        }
    }

    internal fun cyclePaperToneMode() {
        paperToneMode = when (paperToneMode) {
            PaperToneMode.ACTIVE_THEME -> PaperToneMode.DARK
            PaperToneMode.DARK -> PaperToneMode.NATURAL_WHITE
            PaperToneMode.NATURAL_WHITE -> PaperToneMode.WARM_SEPIA
            PaperToneMode.WARM_SEPIA -> PaperToneMode.ACTIVE_THEME
        }
        val currentSettings = repository.loadReaderSettings()
        repository.saveReaderSettings(currentSettings.copy(paperToneMode = paperToneMode.name.lowercase()))
        applyPaperToneMode()
        val toneLabel = when (paperToneMode) {
            PaperToneMode.ACTIVE_THEME -> "Default"
            PaperToneMode.DARK -> "Dark slate"
            PaperToneMode.NATURAL_WHITE -> "Bone"
            PaperToneMode.WARM_SEPIA -> "Sepia"
        }
        Toast.makeText(this, "Paper tone: $toneLabel", Toast.LENGTH_SHORT).show()
    }



    private fun startHighlightUpdates() {
        if (highlightJob?.isActive == true) return
        highlightJob = lifecycleScope.launch {
            while (true) {
                updateSentenceHighlight()
                updatePlaybackControls()
                delay(900)
            }
        }
    }

    private fun loadHighlightTextAsync(metadata: SavedDocument) {
        lifecycleScope.launch {
            val model = withContext(Dispatchers.IO) {
                ReaderTextModelCache.get(metadata.id, repository.readText(metadata), metadata.pageCount)
            }
            readerTextModel = model
            extractedChunks = model.sentences.map { it.text }
        }
    }

    private fun schedulePdfViewLookup(fragment: PdfViewerFragment) {
        lifecycleScope.launch {
            repeat(24) {
                val found = fragment.view?.let(::findPdfView) ?: fragmentContainer?.let(::findPdfView)
                if (found != null) {
                    pdfView = found
                    found.setOnTouchListener { _, event ->
                        handleDocumentChromeTouch(found, event)
                        false
                    }
                    startHighlightUpdates()
                    
                    // Initial scroll to saved page
                    launch {
                        val metadata = document ?: return@launch
                        var retryCount = 0
                        while (readerTextModel == null && retryCount < 50) {
                            delay(100)
                            retryCount++
                        }
                        val model = readerTextModel ?: return@launch
                        
                        var docRetryCount = 0
                        while (runCatching { found.pdfDocument }.getOrNull() == null && docRetryCount < 50) {
                            delay(100)
                            docRetryCount++
                        }
                        val pdfDoc = runCatching { found.pdfDocument }.getOrNull() ?: return@launch
                        
                        val safeIndex = metadata.currentIndex.coerceIn(0, model.sentences.lastIndex.coerceAtLeast(0))
                        val targetPage = model.sentences.getOrNull(safeIndex)?.pageNumber?.minus(1) ?: 0
                        val pageCount = pdfDoc.pageCount.coerceAtLeast(1)
                        val scrollPage = targetPage.coerceIn(0, pageCount - 1)
                        runCatching { found.scrollToPage(scrollPage) }
                    }
                    
                    return@launch
                }
                delay(150)
            }
        }
    }

    private fun findPdfView(view: View): PdfView? {
        if (view is PdfView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findPdfView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }

    internal fun handleDocumentChromeTouch(view: View, event: MotionEvent) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tapDownX = event.x
                tapDownY = event.y
                tapDownTime = event.eventTime
                tapMoved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - tapDownX
                val dy = event.y - tapDownY
                if ((dx * dx + dy * dy) > (slop * slop)) {
                    // The finger is scrolling, not tapping — glide the bars away
                    // the moment the scroll starts.
                    if (!tapMoved && chromeVisible) hideChrome()
                    tapMoved = true
                }
            }
            MotionEvent.ACTION_UP -> {
                val quickTap = event.eventTime - tapDownTime < 360L
                val dx = event.x - tapDownX
                val dy = event.y - tapDownY
                val moved = tapMoved || (dx * dx + dy * dy) > (slop * slop)
                // A clean tap anywhere on the document checks links or toggles chrome.
                if (!moved && quickTap) {
                    if (!handleLinkTap(view, event.x, event.y)) {
                        toggleChromeFromDocumentTap()
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> tapMoved = false
        }
    }

    private fun toggleChromeFromDocumentTap() {
        if (chromeMenuOpen) return
        if (chromeVisible) {
            hideChrome()
        } else {
            showChrome()
            scheduleChromeAutoHide()
        }
    }

    internal fun showChrome(keepVisible: Boolean = false) {
        chromeVisible = true
        toolbarChrome?.let { slideChromeIn(it, offscreenY = -it.height.toFloat()) }
        bottomChrome?.let { slideChromeIn(it, offscreenY = it.height.toFloat()) }
        chromeHideJob?.cancel()
        if (!keepVisible) scheduleChromeAutoHide()
    }

    internal fun hideChrome() {
        chromeHideJob?.cancel()
        // While the bottom panel is expanded the user is actively adjusting
        // controls (speed/pitch sliders, queue) — never pull the bars away.
        if (chromeMenuOpen || panelExpanded || !chromeVisible) return
        chromeVisible = false
        toolbarChrome?.let { slideChromeOut(it, offscreenY = -it.height.toFloat()) }
        bottomChrome?.let { slideChromeOut(it, offscreenY = it.height.toFloat()) }
    }

    // The bars glide on/off screen (top bar upward, bottom bar downward) with a
    // decelerating ease instead of a hard fade — they read as part of the UI
    // sliding away, and the document underneath never moves.
    private fun slideChromeIn(bar: View, offscreenY: Float) {
        bar.animate().cancel()
        if (bar.visibility != View.VISIBLE) {
            bar.translationY = offscreenY
            bar.alpha = 0f
            bar.visibility = View.VISIBLE
        }
        bar.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(240L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun slideChromeOut(bar: View, offscreenY: Float) {
        bar.animate().cancel()
        bar.animate()
            .translationY(offscreenY)
            .alpha(0f)
            .setDuration(250L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (!chromeVisible) bar.visibility = View.INVISIBLE
            }
            .start()
    }

    internal fun scheduleChromeAutoHide() {
        chromeHideJob?.cancel()
        if (!chromeVisible || chromeMenuOpen || panelExpanded) return
        chromeHideJob = lifecycleScope.launch {
            delay(CHROME_AUTO_HIDE_MS)
            hideChrome()
        }
    }

    internal suspend fun updateSentenceHighlight(forceSync: Boolean = false) {
        val view = pdfView ?: return
        val metadata = document ?: return
        if (PlaybackStateStore.activeDocumentId != metadata.id || !PlaybackStateStore.isPlaying) {
            if (lastHighlightKey.isNotBlank()) {
                view.setHighlights(emptyList())
                lastHighlightKey = ""
                lastHighlightPage = null
            }
            return
        }
        val manualSync = forceSync || pendingManualPageSync
        if (manualSync) pendingManualPageSync = false
        val safeSentenceIndex = PlaybackStateStore.currentIndex.coerceAtLeast(0)
        val chunk = extractedChunks.getOrNull(safeSentenceIndex).orEmpty()
        val start = PlaybackStateStore.currentSentenceStart.coerceIn(0, chunk.length)
        val end = PlaybackStateStore.currentSentenceEnd.coerceIn(0, chunk.length)
        val document = runCatching { view.pdfDocument }.getOrNull() ?: return
        val pageCount = document.pageCount.coerceAtLeast(1)
        val estimatedPage = readerTextModel
            ?.sentences
            ?.getOrNull(safeSentenceIndex)
            ?.pageNumber
            ?.minus(1)
            ?: if (PlaybackStateStore.chunkCount > 1) {
                ((safeSentenceIndex.toFloat() / (PlaybackStateStore.chunkCount - 1).toFloat()) * (pageCount - 1)).toInt()
            } else {
                view.firstVisiblePage.coerceIn(0, pageCount - 1)
            }
        val targetPage = estimatedPage.coerceIn(0, pageCount - 1)
        val syncEnabled = isSyncEnabled
        val visiblePage = runCatching { view.firstVisiblePage }.getOrDefault(targetPage).coerceIn(0, pageCount - 1)
        if (syncEnabled && !manualSync && lastSyncedTargetPage == targetPage && abs(visiblePage - targetPage) >= 1) {
            syncPill?.post {
                isSyncEnabled = false
                updateSyncPillUi()
            }
            lastSyncedTargetPage = null
            return
        }
        if (!syncEnabled && !manualSync) {
            lastSyncedTargetPage = null
        }
        if (chunk.isBlank()) return
        val sentence = chunk.substring(start, end.coerceAtLeast(start)).replace(Regex("\\s+"), " ").trim()
        if (sentence.length < 12) {
            view.setHighlights(emptyList())
            lastHighlightKey = ""
            lastHighlightPage = null
            return
        }
        val key = "$safeSentenceIndex:$start:$end"
        if (key == lastHighlightKey && !manualSync) return
        lastHighlightKey = key

        val previousHighlightPage = lastHighlightPage
        val centerPage = (previousHighlightPage ?: targetPage).coerceIn(0, pageCount - 1)
        val pageRange = maxOf(0, centerPage - 4)..minOf(pageCount - 1, centerPage + 4)
        lastHighlightPage = null
        val highlights = withContext(Dispatchers.IO) { findSentenceHighlights(document, sentence, pageRange) }
        view.setHighlights(highlights)
        if (syncEnabled || manualSync) {
            val scrollPage = (lastHighlightPage ?: targetPage).coerceIn(0, pageCount - 1)
            if (manualSync || lastSyncedTargetPage != scrollPage) {
                runCatching { view.scrollToPage(scrollPage) }
                lastSyncedTargetPage = scrollPage
            }
        }
    }

    private suspend fun findSentenceHighlights(
        document: androidx.pdf.PdfDocument,
        sentence: String,
        pageRange: IntRange
    ): List<Highlight> {
        fun sanitize(str: String): String = str
            .replace('\u2018', '\'')
            .replace('\u2019', '\'')
            .replace('\u201C', '"')
            .replace('\u201D', '"')
            .replace('\u2014', ' ') // em-dash
            .replace('\u2013', ' ') // en-dash
            .replace('\u2212', '-') // minus
            .replace("ﬁ", "fi")
            .replace("ﬂ", "fl")
            .replace("ﬀ", "ff")
            .replace("ﬃ", "ffi")
            .replace("ﬄ", "ffl")
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val cleanSentence = sanitize(sentence)
        val words = cleanSentence.split(' ').filter { it.isNotBlank() }
        val alphanumericWords = words.map { it.replace(Regex("[^a-zA-Z0-9]"), "") }.filter { it.length >= 3 }
        val stopWords = setOf("the", "and", "for", "are", "but", "not", "you", "all", "any", "can", "had", "her", "was", "one", "our", "out", "day", "get", "has", "him", "his", "how", "man", "new", "now", "old", "see", "two", "way", "who", "boy", "did", "its", "let", "put", "say", "she", "too", "use", "with", "from", "that", "this", "they", "have", "been", "were", "what", "when", "your", "said", "each", "which", "their", "time", "will", "about", "many", "then", "them", "some", "into", "more", "other")
        val distinctiveWords = alphanumericWords.filter { it.lowercase() !in stopWords }

        val candidates = buildList {
            // Level 1: Full raw sentence prefix
            add(cleanSentence.take(160))
            if (cleanSentence.length > 80) add(cleanSentence.take(80))

            // Level 2: Punctuation-stripped prefix
            val stripped = cleanSentence.replace(Regex("[.,:;!?\"'()\\[\\]{}]"), " ").replace(Regex("\\s+"), " ").trim()
            if (stripped != cleanSentence) {
                add(stripped.take(120))
                if (stripped.length > 60) add(stripped.take(60))
            }

            // Level 3: Distinctive word n-grams (3-5 words)
            if (distinctiveWords.size >= 4) {
                add(distinctiveWords.take(4).joinToString(" "))
                if (distinctiveWords.size >= 8) {
                    add(distinctiveWords.drop(distinctiveWords.size / 2).take(4).joinToString(" "))
                }
            }

            // Level 4: Sliding consecutive word windows (3-5 words)
            if (words.size >= 5) {
                add(words.take(5).joinToString(" "))
                add(words.takeLast(minOf(5, words.size)).joinToString(" "))
                if (words.size >= 8) {
                    val mid = words.size / 2
                    add(words.subList(maxOf(0, mid - 2), minOf(words.size, mid + 3)).joinToString(" "))
                }
            }
            if (words.size in 3..4) {
                add(words.joinToString(" "))
            }

            // Level 5: Longest distinctive words
            val longestWords = alphanumericWords.sortedByDescending { it.length }.take(3)
            if (longestWords.size >= 2 && longestWords.first().length >= 6) {
                add(longestWords.joinToString(" "))
            }
        }.map { sanitize(it) }
            .filter { it.length >= 6 }
            .distinct()

        for (query in candidates) {
            val highlights = runCatching {
                val matches = document.searchDocument(query, pageRange)
                buildList {
                    for (i in 0 until matches.size()) {
                        val page = matches.keyAt(i)
                        if (lastHighlightPage == null) lastHighlightPage = page
                        matches.valueAt(i).firstOrNull()?.bounds?.forEach { rect ->
                            add(Highlight(PdfRect(page, rect), Color.argb(170, 255, 200, 0)))
                        }
                    }
                }
            }.getOrDefault(emptyList())
            if (highlights.isNotEmpty()) return highlights
        }
        return emptyList()
    }



    companion object {
        private const val EXTRA_DOCUMENT_ID = "document_id"
        private const val VIEWER_TAG = "veritas_pdf_viewer"
        private const val CHROME_AUTO_HIDE_MS = 4_000L

        fun intent(context: Context, documentId: String): Intent {
            return Intent(context, VeritasPdfViewerActivity::class.java)
                .putExtra(EXTRA_DOCUMENT_ID, documentId)
        }
    }
}




data class OverflowMenuItem(
    val title: String,
    val subtitle: String? = null,
    val iconRes: Int? = null,
    val isHeader: Boolean = false,
    val enabled: Boolean = true,
    val action: (() -> Unit)? = null
)

