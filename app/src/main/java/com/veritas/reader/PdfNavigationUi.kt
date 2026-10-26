package com.veritas.reader

import android.graphics.drawable.ColorDrawable
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.lifecycle.lifecycleScope
import androidx.pdf.view.PdfView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

internal fun VeritasPdfViewerActivity.showPdfNavigationSettings() {
    showChrome(keepVisible = true)
    chromeMenuOpen = true
    val modes = listOf("swipe", "scroll")
    val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
        .setTitle("PDF settings")
        .setSingleChoiceItems(arrayOf("Swipe", "Scroll"), modes.indexOf(pdfChromeSettings.pdfNavigationMode)) { _, selected ->
            setPdfNavigationMode(modes[selected])
        }
        .setPositiveButton("Done", null)
        .create()
    dialog.setOnDismissListener { chromeMenuOpen = false; showChrome() }
    dialog.show()
    dialog.window?.setBackgroundDrawable(ColorDrawable(colorSurface))
    dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(colorPrimary)
}

internal fun VeritasPdfViewerActivity.setPdfNavigationMode(mode: String) {
    if (mode !in listOf("swipe", "scroll")) return
    pdfChromeSettings = pdfChromeSettings.copy(pdfNavigationMode = mode)
    pdfSwipeClaimed = false
    lifecycleScope.launch(Dispatchers.IO) {
        synchronized(repository) {
            repository.saveReaderSettings(repository.loadReaderSettings().copy(pdfNavigationMode = mode))
        }
    }
}

/** Keep native selection, multi-touch zoom and zoomed panning with AndroidX PdfView. */
internal fun VeritasPdfViewerActivity.handlePdfPageSwipe(view: PdfView, event: MotionEvent): Boolean {
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            pdfSwipeStartX = event.x
            pdfSwipeStartY = event.y
            pdfSwipeClaimed = false
            pdfSwipeMultiTouch = false
            pdfSwipeEligible = pdfChromeSettings.pdfNavigationMode == "swipe" && !pdfSelectionActive &&
                pdfFitWidthZoom > 0f && view.zoom <= pdfFitWidthZoom * 1.05f
        }
        MotionEvent.ACTION_POINTER_DOWN -> { pdfSwipeMultiTouch = true; pdfSwipeEligible = false }
        MotionEvent.ACTION_MOVE -> {
            if (!pdfSwipeEligible || pdfSwipeMultiTouch || event.pointerCount != 1) return false
            val dx = event.x - pdfSwipeStartX
            val dy = event.y - pdfSwipeStartY
            val slop = ViewConfiguration.get(this).scaledTouchSlop
            if (!pdfSwipeClaimed && abs(dy) > slop && abs(dy) >= abs(dx)) pdfSwipeEligible = false
            if (pdfSwipeEligible && !pdfSwipeClaimed && abs(dx) > slop * 2 && abs(dx) > abs(dy) * 1.4f) {
                pdfSwipeClaimed = true
            }
            return pdfSwipeClaimed
        }
        MotionEvent.ACTION_UP -> {
            val claimed = pdfSwipeClaimed
            if (claimed && !pdfSwipeMultiTouch) {
                val dx = event.x - pdfSwipeStartX
                if (abs(dx) > 48.dp) {
                    val count = view.pdfDocument?.pageCount ?: 1
                    val page = (pdfNavigationPage + if (dx < 0f) 1 else -1).coerceIn(0, count - 1)
                    view.scrollToPage(page)
                }
            }
            pdfSwipeClaimed = false
            pdfSwipeEligible = false
            return claimed
        }
        MotionEvent.ACTION_CANCEL -> { pdfSwipeClaimed = false; pdfSwipeEligible = false }
    }
    return false
}
