package com.veritas.reader

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
        val root = FrameLayout(this).apply {
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
        toolbar.addView(iconButton(R.drawable.ic_m3_chevron_left) { finish() })

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
            text = " • Original View"
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
            text = "🔗 Live Sync"
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
                Toast.makeText(activity, "Live sync enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(activity, "Live sync disabled", Toast.LENGTH_SHORT).show()
            }
        }
        toolbar.addView(pill)

        toolbar.addView(iconButton(R.drawable.ic_m3_search) { toggleSearch() })
        toolbar.addView(iconButton(R.drawable.ic_m3_toc) { showTableOfContentsDialog() })
        toolbar.addView(iconButton(R.drawable.ic_m3_contrast) { cyclePaperToneMode() })
        toolbar.addView(iconButton(R.drawable.ic_m3_more_vert) { showTopMenu(toolbar) })
        toolbarChrome = toolbar

        fragmentContainer = FrameLayout(this).apply {
            id = R.id.pdf_fragment_container
            setBackgroundColor(if (isLightTheme) colorBackground else Color.WHITE)
            if (!isLightTheme) {
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
                setLayerType(View.LAYER_TYPE_HARDWARE, paint)
            }
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

        val maxWidthPx = 580.dp
        val screenWidthPx = resources.displayMetrics.widthPixels
        val barWidthPx = if (screenWidthPx > maxWidthPx) maxWidthPx else LinearLayout.LayoutParams.MATCH_PARENT

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipToOutline = true
            background = capsuleGradientDrawable(
                isDark = !isLightTheme,
                surfaceColor = colorSurface,
                primaryColor = colorPrimary,
                containerColor = colorSyncBackground,
                cornerRadius = 32.dp
            )
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
        val controlRow = LinearLayout(this).apply {
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
        controlRow.addView(brand)

        // Status text
        panelStatusLabel = TextView(this).apply {
            text = if (PlaybackStateStore.isPlaying) "Now reading" else "Ready to read"
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
        controlRow.addView(panelStatusLabel)

        // Playback controls
        controlRow.addView(iconButton(R.drawable.ic_m3_chevron_left) { sendPlaybackIntent(this, PlaybackActions.ACTION_PREVIOUS) })
        playPauseControl = prominentButton("") {
            sendPlaybackIntent(this, if (PlaybackStateStore.isPlaying) PlaybackActions.ACTION_PAUSE else PlaybackActions.ACTION_PLAY)
            playPauseControl?.postDelayed({ updatePlaybackControls() }, 180)
        }
        applyPlayPauseIcon(PlaybackStateStore.isPlaying)
        controlRow.addView(requireNotNull(playPauseControl))
        controlRow.addView(iconButton(R.drawable.ic_m3_chevron_right) { sendPlaybackIntent(this, PlaybackActions.ACTION_NEXT) })
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val rotateIcon = if (isLandscape) R.drawable.ic_m3_stay_primary_portrait else R.drawable.ic_m3_stay_primary_landscape
        val rotBtn = iconButton(rotateIcon) { rotateViewer() }
        rotateControl = rotBtn
        controlRow.addView(rotBtn)

        // Expand arrow indicator
        panelExpandArrow = TextView(this).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(36.dp, 44.dp)
            setOnClickListener { togglePanelExpand() }
        }
        panelExpandArrow?.let { arrow ->
            applyIconGlyph(arrow, R.drawable.ic_m3_expand_less)
            arrow.foreground?.setTint(colorTextSecondary)
        }
        controlRow.addView(panelExpandArrow)

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
            text = PlaybackStateStore.statusMessage.ifBlank { "Original View" }
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
            title = "Speed",
            min = 0.5f,
            max = 2.0f,
            current = PlaybackStateStore.rate,
            suffix = "x",
            onSliderCreated = { panelSpeedSlider = it }
        ) { value -> adjustPlayback(rate = value, pitch = PlaybackStateStore.pitch) }

        // Pitch slider
        panelPitchLabel = labeledSeekBar(
            menu = expandedSection,
            title = "Pitch",
            min = 0.7f,
            max = 1.4f,
            current = PlaybackStateStore.pitch,
            suffix = "",
            onSliderCreated = { panelPitchSlider = it }
        ) { value -> adjustPlayback(rate = PlaybackStateStore.rate, pitch = value) }

        // Bottom row: Voice Studio + Queue
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8.dp, 0, 0)
        }
        bottomRow.addView(TextView(this).apply {
            text = if (PlaybackStateStore.queueCount == 0) "Queue empty"
                   else "Queue (${PlaybackStateStore.queueCount})"
            setTextColor(colorTextSecondary)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        bottomRow.addView(TextView(this).apply {
            text = "Voice Studio ›"
            setTextColor(colorPrimary)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setOnClickListener { openVoiceAndLanguage() }
        })
        expandedSection.addView(bottomRow)

        controls.addView(progressStrip)
        controls.addView(controlRow)
        controls.addView(expandedSection)
        controlsOuter.addView(controls)

        root.addView(fragmentContainer)
        root.addView(toolbar)
        root.addView(controlsOuter)
        setContentView(root)
        ViewCompat.requestApplyInsets(root)
        // Minimized chrome is the default. INVISIBLE (not GONE) so the bars are
        // measured on the first layout pass and can slide in from their real heights.
        listOfNotNull(toolbarChrome, bottomChrome).forEach { it.visibility = View.INVISIBLE }
    }



    private fun VeritasPdfViewerActivity.applyToolbarInsets(toolbar: View) {
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val top = maxOf(
                insets.getInsets(WindowInsetsCompat.Type.statusBars()).top,
                statusBarHeight(),
                34.dp
            )
            view.setPadding(8.dp, top + 14.dp, 6.dp, 8.dp)
            insets
        }
    }

    private fun VeritasPdfViewerActivity.applyDeckInsets(deck: View) {
        ViewCompat.setOnApplyWindowInsetsListener(deck) { view, insets ->
            val bottom = maxOf(
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom,
                navigationBarHeight(),
                6.dp
            )
            view.setPadding(20.dp, 6.dp, 20.dp, bottom + 10.dp)
            insets
        }
    }

    @Suppress("DEPRECATION")
    internal fun VeritasPdfViewerActivity.configureSystemBars() {
        window.statusBarColor = colorToolbar
        window.navigationBarColor = colorBackground
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = isLightTheme
        controller.isAppearanceLightNavigationBars = isLightTheme
    }

    internal fun VeritasPdfViewerActivity.showFallback(message: String) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24.dp, 24.dp, 24.dp, 24.dp)
            setBackgroundColor(colorBackground)
        }
        root.addView(TextView(this).apply {
            text = "Original View"
            setTextColor(colorTextPrimary)
            textSize = 22f
        })
        root.addView(TextView(this).apply {
            text = message
            setTextColor(colorTextSecondary)
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, 12.dp, 0, 18.dp)
        })
        root.addView(prominentButton("Back to Vern") { finish() })
        setContentView(root)
    }

    // Material play/pause glyphs rendered as a centered foreground drawable; the
    // control stays a TextView so the prominentButton styling is unchanged.
    internal fun VeritasPdfViewerActivity.applyPlayPauseIcon(playing: Boolean) {
        val control = playPauseControl ?: return
        control.text = ""
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
        panelStatusLabel?.text = if (playing) "Now reading" else "Ready to read"
        panelSpeedLabel?.text = "Speed ${"%.2f".format(PlaybackStateStore.rate)}x"
        panelPitchLabel?.text = "Pitch ${"%.2f".format(PlaybackStateStore.pitch)}"
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
                    (resources.displayMetrics.widthPixels * 0.95f).toInt(),
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
            layoutParams = LinearLayout.LayoutParams(44.dp, 44.dp)
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
            val targetWidth = if (label.length > 3) 170.dp else 70.dp
            layoutParams = LinearLayout.LayoutParams(targetWidth, 46.dp).apply {
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

    internal fun capsuleGradientDrawable(
        isDark: Boolean,
        surfaceColor: Int,
        primaryColor: Int,
        containerColor: Int,
        cornerRadius: Int
    ): GradientDrawable {
        val topColor: Int
        val bottomColor: Int
        val strokeColor: Int
        if (isDark) {
            topColor = androidx.core.graphics.ColorUtils.blendARGB(surfaceColor, primaryColor, 0.12f)
            bottomColor = androidx.core.graphics.ColorUtils.blendARGB(surfaceColor, Color.BLACK, 0.20f)
            strokeColor = Color.argb((0.22f * 255).toInt(), 255, 255, 255)
        } else {
            topColor = androidx.core.graphics.ColorUtils.blendARGB(surfaceColor, containerColor, 0.35f)
            bottomColor = androidx.core.graphics.ColorUtils.blendARGB(surfaceColor, primaryColor, 0.08f)
            strokeColor = Color.argb((0.65f * 255).toInt(), 255, 255, 255)
        }
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                androidx.core.graphics.ColorUtils.setAlphaComponent(topColor, (0.94f * 255).toInt()),
                androidx.core.graphics.ColorUtils.setAlphaComponent(bottomColor, (0.96f * 255).toInt())
            )
        ).apply {
            this.cornerRadius = cornerRadius.toFloat()
            setStroke(1.dp, strokeColor)
        }
    }

    internal fun VeritasPdfViewerActivity.statusBarHeight(): Int = systemBarHeight("status_bar")

    internal fun VeritasPdfViewerActivity.navigationBarHeight(): Int = systemBarHeight("navigation_bar")

    internal fun VeritasPdfViewerActivity.systemBarHeight(name: String): Int {
        val id = resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    internal val Int.dp: Int
    get() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()



