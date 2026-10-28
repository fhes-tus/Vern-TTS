package com.veritas.reader

import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import androidx.pdf.view.PdfView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs

internal fun VeritasPdfViewerActivity.setPdfNavigationMode(mode: String) {
    if (mode !in listOf("swipe", "scroll")) return
    pdfChromeSettings = pdfChromeSettings.copy(pdfNavigationMode = mode)
    pdfSwipeClaimed = false
    pdfVerticalSwipeBlocked = false
    updatePdfSwipeViewport()
    val view = pdfView
    if (view != null) {
        if (mode == "swipe") focusPdfPage(pdfNavigationPage)
        else {
            pdfFocusRequest = null
            pdfFocusInitialized = false
            pdfDefaultPageSpacing?.let { view.verticalPageSpacing = it }
            pdfDefaultMinZoom?.let { view.minZoom = it }
            view.scrollToPage(pdfNavigationPage)
        }
    }
    lifecycleScope.launch(Dispatchers.IO) {
        synchronized(repository) {
            repository.saveReaderSettings(repository.loadReaderSettings().copy(pdfNavigationMode = mode))
        }
    }
}

/** Reserve the measured controls even while hidden, so showing chrome never covers a fitted page. */
internal fun VeritasPdfViewerActivity.updatePdfSwipeViewport() {
    val container = fragmentContainer ?: return
    val params = container.layoutParams as? FrameLayout.LayoutParams ?: return
    val original = pdfDefaultViewportMargins ?: android.graphics.Rect(params.leftMargin, params.topMargin,
        params.rightMargin, params.bottomMargin).also { pdfDefaultViewportMargins = it }
    val swipe = pdfChromeSettings.pdfNavigationMode == "swipe"
    val top = original.top + if (swipe) (toolbarChrome?.height ?: 0) + 8.dp else 0
    val bottom = original.bottom + if (swipe) (bottomChrome?.height ?: 0) + 8.dp else 0
    if (params.topMargin != top || params.bottomMargin != bottom) {
        params.topMargin = top
        params.bottomMargin = bottom
        container.layoutParams = params
    }
}

/** All programmatic destinations, including speech sync and same-page jumps, preserve layout. */
internal fun VeritasPdfViewerActivity.navigateToPdfPage(page: Int) {
    if (pdfChromeSettings.pdfNavigationMode == "swipe") focusPdfPage(page)
    else pdfView?.scrollToPage(page)
}

/** Fit and centre one complete page. A viewport-sized gap keeps adjacent pages outside it. */
internal fun VeritasPdfViewerActivity.focusPdfPage(page: Int) {
    val view = pdfView ?: return
    val count = view.pdfDocument?.pageCount ?: return
    if (count <= 0) return
    val target = page.coerceIn(0, count - 1)
    pdfFocusRequest = target
    if (!pdfPageDimensions.containsKey(target)) view.scrollToPage(target)
    schedulePdfPageFocus()
}

internal fun VeritasPdfViewerActivity.schedulePdfPageFocus() {
    val view = pdfView ?: return
    if (pdfFocusPosted) return
    pdfFocusPosted = true
    view.post {
        pdfFocusPosted = false
        val page = pdfFocusRequest ?: return@post
        val dimensions = pdfPageDimensions[page] ?: return@post
        if (pdfChromeSettings.pdfNavigationMode != "swipe" || view.width <= 0 || view.height <= 0) return@post
        val (width, height) = dimensions
        if (width <= 0f || height <= 0f) return@post
        val zoom = (.94f * minOf(view.width / width, view.height / height)).coerceIn(.01f, view.maxZoom)
        val count = (view.pdfDocument?.pageCount ?: 1).coerceAtLeast(1)
        val maximumGap = (1_000_000_000L / count).coerceAtMost(100_000L).toInt()
        val gap = kotlin.math.ceil(view.height.toDouble() * 2 / zoom).coerceAtMost(maximumGap.toDouble()).toInt()
        pdfFocusRequest = null
        pdfFocusApplying = true
        try {
            view.verticalPageSpacing = gap
            view.minZoom = zoom
            view.zoom = zoom
            view.scrollToPosition(androidx.pdf.PdfPoint(page, width / 2, height / 2))
            pdfFitWidthZoom = zoom
            pdfNavigationPage = page
            pdfFocusInitialized = true
        } finally { pdfFocusApplying = false }
    }
}

/** Keep native selection, multi-touch zoom and zoomed panning with AndroidX PdfView. */
internal fun VeritasPdfViewerActivity.handlePdfPageSwipe(view: PdfView, event: MotionEvent): Boolean {
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            pdfSwipeStartX = event.x
            pdfSwipeStartY = event.y
            pdfSwipeClaimed = false
            pdfVerticalSwipeBlocked = false
            pdfSwipeMultiTouch = false
            pdfSwipeEligible = pdfChromeSettings.pdfNavigationMode == "swipe" && !pdfSelectionActive &&
                pdfFitWidthZoom > 0f && view.zoom <= pdfFitWidthZoom * 1.05f
        }
        MotionEvent.ACTION_POINTER_DOWN -> { pdfSwipeMultiTouch = true; pdfSwipeEligible = false }
        MotionEvent.ACTION_MOVE -> {
            if (pdfVerticalSwipeBlocked) return true
            if (pdfSelectionActive) { pdfSwipeEligible = false; return false }
            if (!pdfSwipeEligible || pdfSwipeMultiTouch || event.pointerCount != 1) return false
            val dx = event.x - pdfSwipeStartX
            val dy = event.y - pdfSwipeStartY
            val slop = ViewConfiguration.get(this).scaledTouchSlop
            if (!pdfSwipeClaimed && abs(dy) > slop && abs(dy) >= abs(dx)) {
                pdfVerticalSwipeBlocked = true
                pdfSwipeClaimed = true
                return true
            }
            if (pdfSwipeEligible && !pdfSwipeClaimed && abs(dx) > slop * 2 && abs(dx) > abs(dy) * 1.4f) {
                pdfSwipeClaimed = true
            }
            return pdfSwipeClaimed
        }
        MotionEvent.ACTION_UP -> {
            val claimed = pdfSwipeClaimed
            if (claimed && !pdfSwipeMultiTouch && !pdfVerticalSwipeBlocked) {
                val dx = event.x - pdfSwipeStartX
                if (abs(dx) > 48.dp) {
                    val count = view.pdfDocument?.pageCount ?: 1
                    val page = (pdfNavigationPage + if (dx < 0f) 1 else -1).coerceIn(0, count - 1)
                    focusPdfPage(page)
                }
            }
            pdfSwipeClaimed = false
            pdfSwipeEligible = false
            pdfVerticalSwipeBlocked = false
            return claimed
        }
        MotionEvent.ACTION_CANCEL -> { pdfSwipeClaimed = false; pdfSwipeEligible = false; pdfVerticalSwipeBlocked = false }
    }
    return false
}
