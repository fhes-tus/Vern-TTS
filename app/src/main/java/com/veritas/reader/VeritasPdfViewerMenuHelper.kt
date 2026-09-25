package com.veritas.reader

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.veritas.reader.ui.VeritasSleekSliderView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

    internal fun VeritasPdfViewerActivity.showJumpToPageDialog() {
        val total = runCatching { pdfView?.pdfDocument?.pageCount }.getOrNull() ?: 1
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "1–$total"
            setTextColor(colorTextPrimary)
            setHintTextColor(colorTextSecondary)
            setPadding(16.dp, 12.dp, 16.dp, 12.dp)
        }
        val layout = FrameLayout(this).apply {
            setPadding(20.dp, 8.dp, 20.dp, 8.dp)
            addView(input)
        }
        val titleView = TextView(this).apply {
            text = "Jump to Page"
            setTextColor(colorTextPrimary)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(20.dp, 16.dp, 20.dp, 4.dp)
        }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setCustomTitle(titleView)
            .setView(layout)
            .setPositiveButton("Go") { _, _ ->
                val num = input.text.toString().trim().toIntOrNull()
                if (num != null && num in 1..total) {
                    pdfView?.scrollToPage(num - 1)
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(colorSurface))
        dialog.show()
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(colorPrimary)
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(colorTextSecondary)
    }

    internal fun VeritasPdfViewerActivity.sharePdf() {
        val metadata = document ?: return
        val uri = repository.getShareableUri(metadata) ?: repository.originalUri(metadata) ?: run {
            Toast.makeText(this, "Could not locate PDF file to share.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = repository.getShareMimeType(metadata)
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, metadata.title)
            clipData = android.content.ClipData.newUri(contentResolver, metadata.title, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(intent, "Share PDF Document")) }
    }

    internal fun VeritasPdfViewerActivity.showTopMenu(anchor: View) {
        showChrome(keepVisible = true)
        val pageCount = runCatching { pdfView?.pdfDocument?.pageCount }.getOrNull() ?: 1
        val items = mutableListOf<OverflowMenuItem>()

        // Display
        items.add(OverflowMenuItem(title = "Display", isHeader = true))

        items.add(OverflowMenuItem(
            title = "Fit to Screen",
            subtitle = "Reset zoom and fit page width",
            iconRes = R.drawable.ic_m3_fitscreen,
            action = { pdfView?.scrollToPage(pdfView?.firstVisiblePage ?: 0) }
        ))
        items.add(OverflowMenuItem(
            title = "Rotate View (90°)",
            subtitle = "Switch screen orientation",
            iconRes = R.drawable.ic_m3_rotate_right,
            action = { rotateViewer() }
        ))
        items.add(OverflowMenuItem(
            title = when (paperToneMode) {
                PaperToneMode.ACTIVE_THEME -> "Default"
                PaperToneMode.DARK -> "Dark slate"
                PaperToneMode.NATURAL_WHITE -> "Bone"
                PaperToneMode.WARM_SEPIA -> "Sepia"
            },
            subtitle = when (paperToneMode) {
                PaperToneMode.ACTIVE_THEME -> "Tap for Dark slate"
                PaperToneMode.DARK -> "Tap for Bone"
                PaperToneMode.NATURAL_WHITE -> "Tap for Sepia"
                PaperToneMode.WARM_SEPIA -> "Tap for Default"
            },
            iconRes = R.drawable.ic_m3_contrast,
            action = { cyclePaperToneMode() }
        ))

        // Navigation
        items.add(OverflowMenuItem(title = "Navigation", isHeader = true))
        items.add(OverflowMenuItem(
            title = "Table of Contents",
            subtitle = if (pdfTocItems.isNotEmpty()) "${pdfTocItems.size} chapters & sections" else "Chapters, sections & links",
            iconRes = R.drawable.ic_m3_toc,
            action = { showTableOfContentsDialog() }
        ))
        if (pageCount > 1) {
            items.add(OverflowMenuItem(
                title = "Jump to Page...",
                subtitle = "Go to 1–$pageCount",
                iconRes = R.drawable.ic_m3_jump_page,
                action = { showJumpToPageDialog() }
            ))
            items.add(OverflowMenuItem(
                title = "First Page (1)",
                subtitle = "Jump to beginning",
                iconRes = R.drawable.ic_m3_first_page,
                action = { pdfView?.scrollToPage(0) }
            ))
            items.add(OverflowMenuItem(
                title = "Last Page ($pageCount)",
                subtitle = "Jump to end of document",
                iconRes = R.drawable.ic_m3_last_page,
                action = { pdfView?.scrollToPage(pageCount - 1) }
            ))
        }

        // Reading & Audio
        items.add(OverflowMenuItem(title = "Reading & Audio", isHeader = true))
        items.add(OverflowMenuItem(
            title = if (isSyncEnabled) "Live Sync (Enabled)" else "Live Sync (Disabled)",
            subtitle = if (isSyncEnabled) "Tap to pause auto-highlighting" else "Tap to sync speech with PDF",
            iconRes = R.drawable.ic_m3_link,
            action = {
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
        ))
        items.add(OverflowMenuItem(
            title = "Switch to Text Reader",
            subtitle = "Flowing text, notes & speed reader",
            iconRes = R.drawable.ic_m3_book,
            action = { finish() }
        ))
        items.add(OverflowMenuItem(
            title = "Voice Studio",
            subtitle = "Narrators, speed & audio tuning",
            iconRes = R.drawable.ic_m3_mic,
            action = { togglePanelExpand() }
        ))

        // File & Share
        items.add(OverflowMenuItem(title = "File & Share", isHeader = true))
        items.add(OverflowMenuItem(
            title = "Share Original File",
            subtitle = "Send to other apps",
            iconRes = R.drawable.ic_m3_share,
            action = { sharePdf() }
        ))
        items.add(OverflowMenuItem(
            title = "Open in External App",
            subtitle = "Use system PDF or photo viewer",
            iconRes = R.drawable.ic_m3_open_in_new,
            action = { openOriginal() }
        ))
        items.add(OverflowMenuItem(
            title = "Document Information",
            subtitle = "Metadata, length & format",
            iconRes = R.drawable.ic_m3_info,
            action = { showDocInfoDialog() }
        ))

        showOverflowPopup(anchor, items)
    }

    internal fun VeritasPdfViewerActivity.showOverflowPopup(
        anchor: View,
        items: List<OverflowMenuItem>
    ) {
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(300.dp, (resources.displayMetrics.heightPixels * 0.70f).toInt())
            isVerticalScrollBarEnabled = false
        }
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12.dp, 0, 12.dp)
            background = rounded(colorSurface, 18.dp)
        }
        scroll.addView(menu)

        // Header Title matching Screenshot 2
        val headerCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp, 4.dp, 18.dp, 6.dp)
        }
        val headerTitle = TextView(this).apply {
            text = "Document Tools"
            setTextColor(colorTextPrimary)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
        }
        val headerSubtitle = TextView(this).apply {
            text = document?.title.orEmpty()
            setTextColor(colorTextSecondary)
            textSize = 11f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 2.dp, 0, 0)
        }
        headerCol.addView(headerTitle)
        headerCol.addView(headerSubtitle)
        menu.addView(headerCol)

        val popup = PopupWindow(scroll, 300.dp, LinearLayout.LayoutParams.WRAP_CONTENT, true).apply {
            elevation = 14f
            isOutsideTouchable = true
            isClippingEnabled = false
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        showChrome(keepVisible = true)
        chromeMenuOpen = true

        items.forEach { item ->
            if (item.isHeader) {
                val divider = View(this).apply {
                    setBackgroundColor(colorSyncBackground)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1.dp).apply {
                        setMargins(16.dp, 6.dp, 16.dp, 6.dp)
                    }
                }
                menu.addView(divider)

                val headerView = TextView(this).apply {
                    text = item.title
                    setTextColor(colorPrimary)
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(18.dp, 4.dp, 18.dp, 2.dp)
                }
                menu.addView(headerView)
            } else {
                val itemRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(16.dp, 9.dp, 16.dp, 9.dp)
                    isClickable = item.enabled
                    isFocusable = item.enabled
                    background = StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_pressed), rounded(colorSyncBackground, 8.dp))
                    }

                    if (item.iconRes != null) {
                        val iconView = ImageView(activity).apply {
                            setImageResource(item.iconRes)
                            setColorFilter(colorPrimary)
                            layoutParams = LinearLayout.LayoutParams(24.dp, 24.dp).apply {
                                marginEnd = 14.dp
                            }
                        }
                        addView(iconView)
                    } else {
                        val spacer = View(activity).apply {
                            layoutParams = LinearLayout.LayoutParams(24.dp, 24.dp).apply {
                                marginEnd = 14.dp
                            }
                        }
                        addView(spacer)
                    }

                    val textCol = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val titleView = TextView(activity).apply {
                        text = item.title
                        setTextColor(if (item.enabled) colorTextPrimary else colorTextSecondary)
                        textSize = 13.5f
                        typeface = Typeface.DEFAULT_BOLD
                    }
                    textCol.addView(titleView)
                    if (item.subtitle != null) {
                        val subView = TextView(activity).apply {
                            text = item.subtitle
                            setTextColor(colorTextSecondary)
                            textSize = 10.5f
                            setPadding(0, 2.dp, 0, 0)
                        }
                        textCol.addView(subView)
                    }
                    addView(textCol)

                    setOnClickListener {
                        popup.dismiss()
                        item.action?.invoke()
                    }
                }
                menu.addView(itemRow)
            }
        }

        popup.setOnDismissListener {
            chromeMenuOpen = false
            scheduleChromeAutoHide()
        }
        popup.showAtLocation(window.decorView, Gravity.TOP or Gravity.END, 12.dp, statusBarHeight() + 56.dp)
    }

    internal fun VeritasPdfViewerActivity.showDocInfoDialog() {
        val metadata = document ?: return
        val items = listOf(
            "Title" to metadata.title,
            "Format" to "PDF (Original Document)",
            "Total Sentences" to "${metadata.sentenceCount}",
            "Est. Reading Time" to "${(metadata.sentenceCount * 2.5 / 60).toInt().coerceAtLeast(1)} min",
            "Added Date" to SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(metadata.createdAt))
        )
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 16.dp, 20.dp, 16.dp)
            items.forEach { (label, value) ->
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 6.dp, 0, 6.dp)
                    addView(TextView(activity).apply {
                        text = label
                        setTextColor(colorTextSecondary)
                        textSize = 13f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    addView(TextView(activity).apply {
                        text = value
                        setTextColor(colorTextPrimary)
                        textSize = 13f
                        typeface = Typeface.DEFAULT_BOLD
                    })
                }
                addView(row)
            }
        }
        val titleView = TextView(this).apply {
            text = "Document Details"
            setTextColor(colorTextPrimary)
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(20.dp, 16.dp, 20.dp, 4.dp)
        }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setCustomTitle(titleView)
            .setView(view)
            .setPositiveButton("Close", null)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(colorSurface))
        dialog.show()
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(colorPrimary)
    }

    internal fun VeritasPdfViewerActivity.updateSyncPillUi() {
        val pill = syncPill ?: return
        val label = syncLabel ?: return
        if (isSyncEnabled) {
            pill.background = rounded(colorSyncBackground, 14.dp)
            label.setTextColor(colorPrimary)
            label.text = "🔗 Live Sync"
        } else {
            pill.background = rounded(colorSurface, 14.dp)
            label.setTextColor(colorTextSecondary)
            label.text = "🔗 Sync Off"
        }
    }

    internal fun VeritasPdfViewerActivity.showPlaybackMenu(anchor: View) {
        showChrome(keepVisible = true)
        chromeMenuOpen = true
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 14.dp, 16.dp, 14.dp)
            background = rounded(colorSurface, 18.dp)
        }
        val popup = PopupWindow(menu, 330.dp, LinearLayout.LayoutParams.WRAP_CONTENT, true).apply {
            elevation = 12f
            isOutsideTouchable = true
            isClippingEnabled = false
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        val status = PlaybackStateStore.statusMessage.ifBlank {
            if (PlaybackStateStore.isPlaying) "Reading." else "Paused."
        }
        menu.addView(TextView(this).apply {
            text = "Playback"
            setTextColor(colorTextPrimary)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 10.dp)
        })
        menu.addView(TextView(this).apply {
            text = status
            setTextColor(colorTextSecondary)
            textSize = 15f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, 12.dp)
        })
        menu.addView(View(this).apply {
            setBackgroundColor(colorOutline)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1.dp).apply {
            bottomMargin = 12.dp
        })
        val speedLabel = labeledSeekBar(
            menu = menu,
            title = "Speed",
            min = 0.5f,
            max = 2.0f,
            current = PlaybackStateStore.rate,
            suffix = "x"
        ) { value -> adjustPlayback(rate = value, pitch = PlaybackStateStore.pitch) }
        val pitchLabel = labeledSeekBar(
            menu = menu,
            title = "Pitch",
            min = 0.7f,
            max = 1.4f,
            current = PlaybackStateStore.pitch,
            suffix = ""
        ) { value -> adjustPlayback(rate = PlaybackStateStore.rate, pitch = value) }
        menu.addView(TextView(this).apply {
            text = "Voice and language"
            setTextColor(colorTextPrimary)
            textSize = 15f
            setPadding(0, 12.dp, 0, 12.dp)
            setOnClickListener {
                popup.dismiss()
                openVoiceAndLanguage()
            }
        })
        menu.addView(TextView(this).apply {
            text = if (PlaybackStateStore.queueCount == 0) "Queue empty" else "Continue queue (${PlaybackStateStore.queueCount})"
            setTextColor(colorTextSecondary)
            textSize = 14f
            setPadding(0, 8.dp, 0, 0)
        })
        popup.setOnDismissListener {
            speedLabel.text = ""
            pitchLabel.text = ""
            chromeMenuOpen = false
            scheduleChromeAutoHide()
        }
        popup.showAtLocation(window.decorView, Gravity.BOTTOM or Gravity.END, 12.dp, navigationBarHeight() + 92.dp)
    }

    internal fun VeritasPdfViewerActivity.labeledSeekBar(
        menu: LinearLayout,
        title: String,
        min: Float,
        max: Float,
        current: Float,
        suffix: String,
        stepIncrement: Float = 0.05f,
        onSliderCreated: ((VeritasSleekSliderView) -> Unit)? = null,
        onCommitted: (Float) -> Unit
    ): TextView {
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 6.dp, 0, 4.dp)
        }
        val label = TextView(this).apply {
            text = "$title ${"%.2f".format(current)}$suffix"
            setTextColor(colorTextPrimary)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerRow.addView(label)

        var sliderRef: VeritasSleekSliderView? = null

        val nudgeMinus = TextView(this).apply {
            text = "-"
            setTextColor(colorTextPrimary)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val size = 26.dp
            layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = 6.dp }
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(colorSurfaceVariant)
            }
            setOnClickListener {
                val s = sliderRef ?: return@setOnClickListener
                val next = (((s.currentVal - stepIncrement) * 20f).roundToInt().toFloat() / 20f).coerceIn(min, max)
                s.currentVal = next
                label.text = "$title ${"%.2f".format(next)}$suffix"
                onCommitted(next)
            }
        }
        headerRow.addView(nudgeMinus)

        val nudgePlus = TextView(this).apply {
            text = "+"
            setTextColor(colorTextPrimary)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            val size = 26.dp
            layoutParams = LinearLayout.LayoutParams(size, size)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(colorSurfaceVariant)
            }
            setOnClickListener {
                val s = sliderRef ?: return@setOnClickListener
                val next = (((s.currentVal + stepIncrement) * 20f).roundToInt().toFloat() / 20f).coerceIn(min, max)
                s.currentVal = next
                label.text = "$title ${"%.2f".format(next)}$suffix"
                onCommitted(next)
            }
        }
        headerRow.addView(nudgePlus)
        menu.addView(headerRow)

        val slider = VeritasSleekSliderView(this).apply {
            minVal = min
            maxVal = max
            step = stepIncrement
            currentVal = current
            setSliderColors(
                primary = colorPrimary,
                surface = colorSurface,
                outline = colorOutline
            )
            onProgressChangedUser = { value ->
                label.text = "$title ${"%.2f".format(value)}$suffix"
            }
            onStopTracking = { value ->
                onCommitted(value)
            }
        }
        sliderRef = slider
        onSliderCreated?.invoke(slider)
        menu.addView(slider, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 14.dp
        })
        return label
    }

    internal fun VeritasPdfViewerActivity.adjustPlayback(rate: Float = PlaybackStateStore.rate, pitch: Float = PlaybackStateStore.pitch) {
        val voiceSettings = repository.loadVoiceSettings()
        val newRate = rate.coerceIn(0.5f, 2.0f)
        val newPitch = pitch.coerceIn(0.7f, 1.4f)
        PlaybackStateStore.rate = newRate
        PlaybackStateStore.pitch = newPitch
        repository.saveVoiceSettings(
            voiceSettings.copy(preferredRate = newRate, preferredPitch = newPitch)
        )
        if (PlaybackStateStore.isForegroundActive || PlaybackStateStore.activeDocumentId != null) {
            sendPlaybackIntent(
                this,
                PlaybackActions.ACTION_UPDATE_PLAYBACK_SETTINGS,
                rate = newRate,
                pitch = newPitch
            )
        }
    }

    internal fun VeritasPdfViewerActivity.openVoiceAndLanguage() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_VOICE_STUDIO, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    internal fun VeritasPdfViewerActivity.showMenu(
        anchor: View,
        actions: List<Pair<String, () -> Unit>>,
        alignTopEnd: Boolean = false,
        alignBottom: Boolean = false
    ) {
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8.dp, 0, 8.dp)
            background = rounded(colorSurface, 18.dp)
        }
        val popup = PopupWindow(menu, 260.dp, LinearLayout.LayoutParams.WRAP_CONTENT, true).apply {
            elevation = 10f
            isOutsideTouchable = true
            isClippingEnabled = false
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        showChrome(keepVisible = true)
        chromeMenuOpen = true
        actions.forEach { (label, action) ->
            menu.addView(TextView(this).apply {
                text = label
                setTextColor(colorTextPrimary)
                textSize = 16f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(18.dp, 12.dp, 18.dp, 12.dp)
                setOnClickListener {
                    popup.dismiss()
                    action()
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        popup.setOnDismissListener {
            chromeMenuOpen = false
            scheduleChromeAutoHide()
        }
        if (alignBottom) {
            popup.showAtLocation(window.decorView, Gravity.BOTTOM or Gravity.END, 12.dp, navigationBarHeight() + 92.dp)
        } else if (alignTopEnd) {
            popup.showAtLocation(window.decorView, Gravity.TOP or Gravity.END, 8.dp, statusBarHeight() + 56.dp)
        } else {
            popup.showAsDropDown(anchor, -230.dp, 0)
        }
    }


