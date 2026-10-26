package com.veritas.reader.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.veritas.reader.ReaderSettings
import com.veritas.reader.renderPdfPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/** Only the visible PDF and its neighbours are composed/rendered. Zoom and pan belong to the host. */
@Composable
internal fun OriginalPdfPager(
    original: Uri,
    pageIndex: Int,
    pageCount: Int,
    currentBitmap: Bitmap?,
    settings: ReaderSettings,
    zoomed: Boolean,
    colorFilter: ColorFilter?,
    onPageChanged: (Int) -> Unit
) {
    val context = LocalContext.current
    val pager = rememberPagerState(initialPage = pageIndex.coerceIn(0, pageCount - 1)) { pageCount }
    val latestPage by rememberUpdatedState(pageIndex)
    val latestCallback by rememberUpdatedState(onPageChanged)
    LaunchedEffect(pageIndex, pageCount) {
        snapshotFlow { pager.isScrollInProgress }.first { !it }
        if (pager.currentPage != pageIndex) pager.scrollToPage(pageIndex.coerceIn(0, pageCount - 1))
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage to pager.isScrollInProgress }.collect { (page, scrolling) ->
            if (!scrolling && page != latestPage) latestCallback(page)
        }
    }
    val motion = !settings.reduceMotion && settings.pageTurnStyle != "none"
    HorizontalPager(pager, Modifier.fillMaxSize().testTag("original-pdf-pager"),
        userScrollEnabled = !zoomed, beyondViewportPageCount = 1,
        pageSpacing = if (settings.pageTurnStyle == "slide") 16.dp else 0.dp,
        flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(pager,
            snapAnimationSpec = if (!motion) androidx.compose.animation.core.snap() else com.veritas.reader.ui.VeritasMotion.spatial())) { page ->
        val rendered by produceState<Result<Bitmap>?>(null, original, page) {
            value = withContext(Dispatchers.IO) {
                runCatching { renderPdfPage(context, original, page).bitmap }.also {
                    it.exceptionOrNull()?.let { error -> if (error is kotlinx.coroutines.CancellationException) throw error }
                }
            }
        }
        val image = rendered?.getOrNull() ?: currentBitmap.takeIf { page == pageIndex }
        val position = (pager.currentPage - page) + pager.currentPageOffsetFraction
        Box(Modifier.fillMaxSize().zIndex(if (motion && settings.pageTurnStyle == "flip" && position > 0f && position < 1f) 2f else 0f)
            .graphicsLayer {
                if (motion && settings.pageTurnStyle == "hinge" && pager.isScrollInProgress) {
                    translationX = position * size.width
                    rotationY = -position.coerceIn(-1f, 1f) * 90f
                    transformOrigin = TransformOrigin(if (position > 0f) 0f else 1f, .5f)
                    cameraDistance = 18f * density
                    shadowElevation = abs(position) * 10.dp.toPx()
                }
            }) {
            BookPageCurl(motion && settings.pageTurnStyle == "flip", pager.isScrollInProgress,
                { (pager.currentPage - page) + pager.currentPageOffsetFraction }, MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (image != null) Image(image.asImageBitmap(), "Original PDF page ${page + 1} of $pageCount",
                        Modifier.fillMaxSize(), contentScale = ContentScale.Fit, colorFilter = colorFilter)
                    else if (rendered?.isFailure == true) Text("Could not render this page. Use page navigation to try again.")
                    else CircularProgressIndicator()
                }
            }
        }
    }
}
