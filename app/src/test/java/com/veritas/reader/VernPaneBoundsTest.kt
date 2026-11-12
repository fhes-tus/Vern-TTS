package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test

class VernPaneBoundsTest {
    private val screen = VernPaneBounds(0, 0, 1000, 800)
    @Test fun flatWindowUsesAllAvailableSpace() { assertEquals(screen, vernUsablePane(screen, emptyList())) }
    @Test fun bookHingeSelectsReadingStartPaneAndAvoidsOcclusion() {
        val hinge = VernFoldBounds(VernPaneBounds(490, 0, 510, 800), true)
        assertEquals(VernPaneBounds(0, 0, 490, 800), vernUsablePane(screen, listOf(hinge)))
        assertEquals(VernPaneBounds(510, 0, 1000, 800), vernUsablePane(screen, listOf(hinge), true))
    }
    @Test fun separatingCreaseNeedsNoPhysicalGap() {
        assertEquals(VernPaneBounds(0, 0, 500, 800), vernUsablePane(screen,
            listOf(VernFoldBounds(VernPaneBounds(500, 0, 500, 800), true))))
    }
    @Test fun tabletopChoosesLargerUsableRegion() {
        assertEquals(VernPaneBounds(0, 330, 1000, 800), vernUsablePane(screen,
            listOf(VernFoldBounds(VernPaneBounds(0, 310, 1000, 330), false))))
    }
    @Test fun alreadyConstrainedPaneDoesNotLoseMoreSpace() {
        val pane = VernPaneBounds(520, 40, 980, 780)
        assertEquals(pane, vernUsablePane(pane, listOf(VernFoldBounds(VernPaneBounds(490, 0, 510, 800), true))))
    }
    @Test fun multipleFeaturesLeaveAUsableRectangle() {
        val result = vernUsablePane(screen, listOf(VernFoldBounds(VernPaneBounds(490, 0, 510, 800), true),
            VernFoldBounds(VernPaneBounds(0, 300, 1000, 320), false)))
        assertEquals(VernPaneBounds(0, 320, 490, 800), result)
    }
}
