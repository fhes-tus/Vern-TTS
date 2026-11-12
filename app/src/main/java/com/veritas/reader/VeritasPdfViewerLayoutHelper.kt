package com.veritas.reader

import android.annotation.SuppressLint
import android.animation.ValueAnimator
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.animation.doOnEnd
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

internal fun VeritasPdfViewerActivity.buildLayout(title: String) {
        // FrameLayout so the PDF stays full-screen underneath and the bars float
        // OVER it — sliding them away never resizes or jumps the document.
        val root = VernFoldFrameLayout(this).apply {
            setBackgroundColor(colorBackground)
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8.dp, statusBarHeight() + 8.dp, 6.dp, 4.dp)
            setBackgroundColor(colorToolbar)
            elevation = 6f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        }
        applyToolbarInsets(toolbar)
        toolbar.addView(iconButton(R.drawable.ic_m3_chevron_left) { finish() }.apply {
            contentDescription = getString(R.string.ui_back)
        })

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(4.dp, 0, 4.dp, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val titleView = TextView(this).apply {
            text = title
            setTextColor(colorTextPrimary)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val subRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 2.dp, 0, 0)
        }
        val badgeView = TextView(this).apply {
            text = "PDF"
            setTextColor(colorPrimary)
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(5.dp, 1.dp, 5.dp, 1.dp)
            background = rounded(colorSyncBackground, 4.dp)
        }
        val subTextView = TextView(this).apply {
            text = " • ${getString(R.string.ui_original_view)}"
            setTextColor(colorTextSecondary)
            textSize = 11f
            maxLines = 1
        }
        subRow.addView(badgeView)
        subRow.addView(subTextView)
        titleCol.addView(titleView)
        titleCol.addView(subRow)
        toolbar.addView(titleCol)

        // Modern Material 3 Live Sync Pill Chip
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8.dp, 5.dp, 8.dp, 5.dp)
            background = rounded(colorSyncBackground, 14.dp)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = 4.dp
            }
        }
        val label = TextView(this).apply {
            text = "🔗 ${getString(R.string.pdf_live_sync)}"
            setTextColor(colorPrimary)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
        }
        pill.addView(label)
        syncPill = pill
        syncLabel = label

        pill.setOnClickListener {
            isSyncEnabled = !isSyncEnabled
            updateSyncPillUi()
            if (isSyncEnabled) {
                pendingManualPageSync = true
                lifecycleScope.launch { updateSentenceHighlight(forceSync = true) }
                Toast.makeText(activity, R.string.pdf_sync_enabled, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, R.string.pdf_sync_disabled, Toast.LENGTH_SHORT).show()
            }
        }
        toolbar.addView(pill)

        toolbar.addView(iconButton(R.drawable.ic_m3_search) { toggleSearch() }.apply {
            contentDescription = getString(R.string.ui_search)
        })
        toolbar.addView(iconButton(R.drawable.ic_m3_toc) { showTableOfContentsDialog() }.also {
            it.contentDescription = getString(R.string.ui_table_of_contents)
            outlineButton = it
        })
        toolbar.addView(iconButton(R.drawable.ic_m3_contrast) { cyclePaperToneMode() }.apply {
            contentDescription = getString(R.string.ui_paper_tone)
        })
        toolbar.addView(iconButton(R.drawable.ic_m3_more_vert) { showTopMenu(toolbar) }.apply {
            contentDescription = getString(R.string.ui_more_options)
        })
        toolbarChrome = toolbar

        fragmentContainer = FrameLayout(this).apply {
            id = R.id.pdf_fragment_container
            // Paper starts white; applyPaperToneMode owns its single color filter.
            setBackgroundColor(Color.WHITE)
            setOnTouchListener { _, event ->
                handleDocumentChromeTouch(this, event)
                false
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val controlsOuter = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(20.dp, 6.dp, 20.dp, 10.dp)
            setBackgroundColor(Color.TRANSPARENT)
            elevation = 0f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }
        applyDeckInsets(controlsOuter)
        bottomChrome = controlsOuter

        // The material host caps its measured parent width, including after split-window resize.
        val barWidthPx = LinearLayout.LayoutParams.MATCH_PARENT

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipToOutline = true
            background = GradientDrawable().apply {
                setColor(colorPlayerSurface)
                cornerRadius = 32.dp.toFloat()
                setStroke(1.dp, colorOutline)
            }
            elevation = if (!isLightTheme) 10.dp.toFloat() else 8.dp.toFloat()
            layoutParams = LinearLayout.LayoutParams(
                barWidthPx,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }

        // Progress strip (thin coloured bar at top of panel)
        val progressStrip = View(this).apply {
            setBackgroundColor(if (PlaybackStateStore.isPlaying) colorActiveStrip else colorOutline)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 3.dp)
        }

        // ── Header row (always visible) ─────────────────────────────────────
        val controlRow = VernPdfControlsHeader(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(10.dp, 8.dp, 8.dp, 8.dp)
        }

        // Brand tile — tap to expand/collapse
        val brand = FrameLayout(this).apply {
            background = rounded(colorSyncBackground, 12.dp)
            layoutParams = LinearLayout.LayoutParams(50.dp, 50.dp)
            addView(ImageView(context).apply {
                setImageResource(R.drawable.veritas_reader_icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(5.dp, 5.dp, 5.dp, 5.dp)
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            setOnClickListener { togglePanelExpand() }
        }
        controlRow.summary.addView(brand)

        // Status text
        panelStatusLabel = TextView(this).apply {
            text = getString(if (PlaybackStateStore.isPlaying) R.string.ui_now_reading else R.string.pdf_ready_to_read)
            setTextColor(colorTextPrimary)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 8.dp
                marginEnd = 2.dp
            }
        }
        controlRow.summary.addView(panelStatusLabel)

        // Playback controls
        controlRow.transport.addView(iconButton(R.drawable.ic_m3_chevron_left) { sendPlaybackIntent(this, PlaybackActions.ACTION_PREVIOUS) })
        playPauseControl = prominentButton("") {
            val preparing = PlaybackStateStore.isPreparingFor(document?.id)
            sendPlaybackIntent(this, if (PlaybackStateStore.isPlaying || preparing) PlaybackActions.ACTION_PAUSE else PlaybackActions.ACTION_PLAY,
                documentId = document?.id)
            updatePlaybackControls()
        }
        applyPlayPauseIcon(PlaybackStateStore.isPlaying)
        controlRow.transport.addView(requireNotNull(playPauseControl))
        controlRow.transport.addView(iconButton(R.drawable.ic_m3_chevron_right) { sendPlaybackIntent(this, PlaybackActions.ACTION_NEXT) })
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val rotateIcon = if (isLandscape) R.drawable.ic_m3_stay_primary_portrait else R.drawable.ic_m3_stay_primary_landscape
        val rotBtn = iconButton(rotateIcon) { rotateViewer() }
        rotateControl = rotBtn
        controlRow.transport.addView(rotBtn)

        // Expand arrow indicator
        panelExpandArrow = TextView(this).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(48.dp, 48.dp)
            setOnClickListener { togglePanelExpand() }
        }
        panelExpandArrow?.let { arrow ->
            applyIconGlyph(arrow, R.drawable.ic_m3_expand_less)
            arrow.foreground?.setTint(colorTextSecondary)
        }
        controlRow.transport.addView(panelExpandArrow)

        // ── Expanded content (initially hidden) ─────────────────────────────
        val expandedSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 0.dp, 16.dp, 8.dp)
            visibility = View.GONE
            // height = 0 initially so animation starts from 0
        }
        expandedPanelContent = expandedSection

        // Divider
        expandedSection.addView(View(this).apply {
            setBackgroundColor(colorOutline)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1.dp).apply {
            topMargin = 2.dp; bottomMargin = 10.dp
        })

        // Status message row
        val expandedStatus = TextView(this).apply {
            text = PlaybackStateStore.statusMessage.ifBlank { getString(R.string.ui_original_view) }
            setTextColor(colorTextSecondary)
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, 10.dp)
        }
        expandedSection.addView(expandedStatus)

        // Speed slider
        panelSpeedLabel = labeledSeekBar(
            menu = expandedSection,
            valueLabel = R.string.pdf_speed_value,
            min = 0.5f,
            max = 2.0f,
            current = PlaybackStateStore.rate,
            onSliderCreated = { panelSpeedSlider = it }
        ) { value -> adjustPlayback(rate = value, pitch = PlaybackStateStore.pitch) }

        // Pitch slider
        panelPitchLabel = labeledSeekBar(
            menu = expandedSection,
            valueLabel = R.string.pdf_pitch_value,
            min = 0.7f,
            max = 1.4f,
            current = PlaybackStateStore.pitch,
            onSliderCreated = { panelPitchSlider = it }
        ) { value -> adjustPlayback(rate = PlaybackStateStore.rate, pitch = value) }

        // Bottom row: Voice Studio + Queue
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8.dp, 0, 0)
        }
        bottomRow.addView(TextView(this).apply {
            text = if (PlaybackStateStore.queueCount == 0) getString(R.string.ui_queue_empty)
                   else getString(R.string.pdf_queue_count, PlaybackStateStore.queueCount)
            setTextColor(colorTextSecondary)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        bottomRow.addView(TextView(this).apply {
            text = "${getString(R.string.ui_voice_studio)} ›"
            setTextColor(colorPrimary)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { openVoiceAndLanguage() }
        })
        expandedSection.addView(bottomRow)

        controls.addView(progressStrip)
        controls.addView(controlRow)
        controls.addView(expandedSection)
        val controlViewport = android.widget.ScrollView(this).apply {
            background = controls.background
            controls.background = null
            addView(controls, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        }
        controlsOuter.addView(installPdfPlayerMaterial(controlViewport, barWidthPx))

        root.addView(fragmentContainer)
        root.addView(toolbar)
        root.addView(controlsOuter)
        setContentView(root)
        configureSystemBars()
        ViewCompat.requestApplyInsets(root)
        // Minimized chrome is the default. INVISIBLE (not GONE) so the bars are
        // measured on the first layout pass and can slide in from their real heights.
        listOfNotNull(toolbarChrome, bottomChrome).forEach { it.visibility = View.INVISIBLE }
    }



    private fun VeritasPdfViewerActivity.applyToolbarInsets(toolbar: View) {
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(8.dp + safe.left, safe.top + 14.dp, 6.dp + safe.right, 8.dp)
            insets
        }
    }

    private fun VeritasPdfViewerActivity.applyDeckInsets(deck: View) {
        ViewCompat.setOnApplyWindowInsetsListener(deck) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(20.dp + safe.left, 6.dp, 20.dp + safe.right, safe.bottom + 10.dp)
            insets
        }
    }

    internal fun VeritasPdfViewerActivity.configureSystemBars() {
        updateVeritasSystemBarProtection(window, colorToolbar, colorBackground)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = isLightTheme
        controller.isAppearanceLightNavigationBars = isLightTheme
    }

    internal fun VeritasPdfViewerActivity.showFallback(message: String) {
        val root = VernFoldFrameLayout(this).apply {
            setBackgroundColor(colorBackground)
            setContentPadding(24.dp, 24.dp, 24.dp, 24.dp)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(colorBackground)
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.ui_original_view)
            setTextColor(colorTextPrimary)
            textSize = 22f
        })
        content.addView(TextView(this).apply {
            text = message
            setTextColor(colorTextSecondary)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 12.dp, 0, 18.dp)
        })
        content.addView(prominentButton(getString(R.string.pdf_back_to_vern)) { finish() })
        root.addView(android.widget.ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            root.setContentPadding(24.dp + safe.left, 24.dp + safe.top, 24.dp + safe.right, 24.dp + safe.bottom)
            insets
        }
        setContentView(root)
        configureSystemBars()
        ViewCompat.requestApplyInsets(root)
    }

    // Material play/pause glyphs rendered as a centered foreground drawable; the
    // control stays a TextView so the prominentButton styling is unchanged.
    internal fun VeritasPdfViewerActivity.applyPlayPauseIcon(playing: Boolean) {
        val control = playPauseControl ?: return
        control.text = ""
        val preparing = PlaybackStateStore.isPreparingFor(document?.id)
        if (preparing) {
            val loading = (control.foreground as? PlaybackLoadingDrawable) ?: PlaybackLoadingDrawable(
                if (isLightTheme) Color.WHITE else Color.rgb(8, 34, 40), resources.displayMetrics.density,
                pdfChromeSettings.reduceMotion).also { control.foreground = it }
            control.foregroundGravity = Gravity.CENTER
            control.contentDescription = getString(R.string.pdf_cancel_preparing)
            if (control.isShown && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) loading.start()
            else loading.stop()
            return
        }
        (control.foreground as? PlaybackLoadingDrawable)?.stop()
        control.contentDescription = getString(if (playing) R.string.ui_pause else R.string.ui_play)
        val icon = androidx.core.content.ContextCompat.getDrawable(
            this,
            if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play
        )?.mutate()
        icon?.setTint(if (isLightTheme) Color.WHITE else Color.rgb(8, 34, 40))
        control.foreground = icon
        control.foregroundGravity = Gravity.CENTER
    }

    internal fun VeritasPdfViewerActivity.updatePlaybackControls() {
        val playing = PlaybackStateStore.isPlaying
        applyPlayPauseIcon(playing)
        panelStatusLabel?.text = getString(when {
            PlaybackStateStore.isPreparingFor(document?.id) -> R.string.pdf_preparing_reading
            playing -> R.string.ui_now_reading
            else -> R.string.pdf_ready_to_read
        })
        panelSpeedLabel?.text = getString(R.string.pdf_speed_value, PlaybackStateStore.rate)
        panelPitchLabel?.text = getString(R.string.pdf_pitch_value, PlaybackStateStore.pitch)
        panelSpeedSlider?.let { if (!it.isTrackingTouch) it.currentVal = PlaybackStateStore.rate }
        panelPitchSlider?.let { if (!it.isTrackingTouch) it.currentVal = PlaybackStateStore.pitch }
    }

    internal fun VeritasPdfViewerActivity.togglePanelExpand() {
        val section = expandedPanelContent ?: return
        panelExpanded = !panelExpanded
        panelExpandArrow?.let { arrow ->
            applyIconGlyph(arrow, if (panelExpanded) R.drawable.ic_m3_expand_more else R.drawable.ic_m3_expand_less)
            arrow.foreground?.setTint(colorTextSecondary)
        }
        showChrome(keepVisible = true)
        if (panelExpanded) {
            section.visibility = View.VISIBLE
            section.measure(
                View.MeasureSpec.makeMeasureSpec(
                    (section.parent as? View)?.width?.takeIf { it > 0 } ?: section.width,
                    View.MeasureSpec.AT_MOST
                ),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val target = section.measuredHeight
            section.layoutParams.height = 0
            section.requestLayout()
            ValueAnimator.ofInt(0, target).apply {
                duration = 240
                addUpdateListener {
                    section.layoutParams.height = it.animatedValue as Int
                    section.requestLayout()
                }
                start()
            }
        } else {
            val start = section.measuredHeight
            ValueAnimator.ofInt(start, 0).apply {
                duration = 200
                addUpdateListener {
                    section.layoutParams.height = it.animatedValue as Int
                    section.requestLayout()
                }
                doOnEnd { section.visibility = View.GONE }
                start()
            }
        }
        if (chromeVisible && !chromeMenuOpen) scheduleChromeAutoHide()
    }

    internal fun VeritasPdfViewerActivity.iconButton(label: String, action: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(colorTextPrimary)
            textSize = 25f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(48.dp, 48.dp)
            setOnClickListener {
                action()
                if (chromeVisible && !chromeMenuOpen) scheduleChromeAutoHide()
            }
        }
    }

    // Material icon variant: the glyph renders as a centered, tinted foreground
    // drawable so the buttons match the Compose screens' Material 3 icons.
    internal fun VeritasPdfViewerActivity.iconButton(iconRes: Int, action: () -> Unit): TextView {
        return iconButton("", action).apply { applyIconGlyph(this, iconRes) }
    }

    private fun VeritasPdfViewerActivity.applyIconGlyph(view: TextView, iconRes: Int) {
        val icon = androidx.core.content.ContextCompat.getDrawable(this, iconRes)?.mutate()
        icon?.setTint(colorTextPrimary)
        view.text = ""
        view.foreground = icon
        view.foregroundGravity = Gravity.CENTER
    }

    internal fun VeritasPdfViewerActivity.updateRotateIcon() {
        val rotBtn = rotateControl ?: return
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val rotateIcon = if (isLandscape) R.drawable.ic_m3_stay_primary_portrait else R.drawable.ic_m3_stay_primary_landscape
        applyIconGlyph(rotBtn, rotateIcon)
    }

    internal fun VeritasPdfViewerActivity.prominentButton(label: String, action: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            setTextColor(if (isLightTheme) Color.WHITE else Color.rgb(8, 34, 40))
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = rounded(colorAccentButton, 22.dp)
            minimumWidth = if (label.isBlank()) 56.dp else 170.dp
            minimumHeight = 48.dp
            setPadding(12.dp, 8.dp, 12.dp, 8.dp)
            val targetWidth = if (label.isBlank()) 56.dp else LinearLayout.LayoutParams.WRAP_CONTENT
            layoutParams = LinearLayout.LayoutParams(targetWidth, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = 4.dp
                marginEnd = 4.dp
            }
            setOnClickListener {
                action()
                if (chromeVisible && !chromeMenuOpen) scheduleChromeAutoHide()
            }
        }
    }

    internal fun VeritasPdfViewerActivity.brandTile(): View {
        return FrameLayout(this).apply {
            background = rounded(colorSyncBackground, 12.dp)
            layoutParams = LinearLayout.LayoutParams(50.dp, 50.dp)
            addView(ImageView(context).apply {
                setImageResource(R.drawable.veritas_reader_icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(5.dp, 5.dp, 5.dp, 5.dp)
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
    }

    internal fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    internal fun VeritasPdfViewerActivity.statusBarHeight(): Int =
        systemBarHeight(WindowInsetsCompat.Type.statusBars(), "status_bar_height", top = true)

    internal fun VeritasPdfViewerActivity.navigationBarHeight(): Int =
        systemBarHeight(WindowInsetsCompat.Type.navigationBars(), "navigation_bar_height", top = false)

    @SuppressLint("DiscouragedApi")
    internal fun VeritasPdfViewerActivity.systemBarHeight(type: Int, fallbackName: String, top: Boolean): Int {
        ViewCompat.getRootWindowInsets(window.decorView)?.getInsets(type)?.let { insets ->
            return if (top) insets.top else insets.bottom
        }
        // Layout may run before window insets arrive; use the framework dimension until then.
        val id = resources.getIdentifier(fallbackName, "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    internal val Int.dp: Int
    get() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
