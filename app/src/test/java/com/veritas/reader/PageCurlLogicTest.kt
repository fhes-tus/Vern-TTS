package com.veritas.reader

import androidx.compose.ui.geometry.Offset
import com.veritas.reader.ui.screens.CurlDragAxis
import com.veritas.reader.ui.screens.PageCurlFold
import com.veritas.reader.ui.screens.curlDragAxis
import org.junit.Assert.*
import org.junit.Test

class PageCurlLogicTest {
    @Test fun verticalDriftDoesNotBecomeAHorizontalPageTurn() {
        assertEquals(CurlDragAxis.Undecided, curlDragAxis(Offset(6f, 3f), 8f))
        assertEquals(CurlDragAxis.Vertical, curlDragAxis(Offset(12f, 36f), 8f))
        assertEquals(CurlDragAxis.Vertical, curlDragAxis(Offset(28f, 24f), 8f))
        assertEquals(CurlDragAxis.Horizontal, curlDragAxis(Offset(-34f, 9f), 8f))
        assertEquals(CurlDragAxis.Horizontal, curlDragAxis(Offset(34f, 9f), 8f))
    }

    @Test fun theFrontEdgeRecedesContinuouslyAndRevealsTheNextPage() {
        for (width in listOf(1f, 360f, 1080f)) {
            val folds = (0..100).map { PageCurlFold.at(width, it / 100f) }
            assertEquals(width, folds.first().edge, 0f)
            assertEquals(0f, folds.last().edge, 0f)
            assertTrue(PageCurlFold.at(width, .01f).radius < width * .0002f)
            assertTrue(PageCurlFold.at(width, .99f).radius < width * .0002f)
            assertTrue(folds.zipWithNext().all { (a, b) -> b.edge <= a.edge })
            assertTrue(folds.all { it.edge >= it.spine && it.sourceEdge <= width + .01f })
            assertTrue(folds[75].edge < width * .4f)
        }
    }
}
