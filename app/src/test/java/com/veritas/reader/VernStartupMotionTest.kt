package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VernStartupMotionTest {
    @Test fun bloomStartsAndEndsWithTheVisibleSystemMark() {
        assertEquals(VernStartupMotion.Pose(), VernStartupMotion.bloomPose(0f))
        assertEquals(VernStartupMotion.Pose(), VernStartupMotion.bloomPose(1f))
        val peak = VernStartupMotion.bloomPose(.45f)
        assertTrue(peak.alpha >= .9f)
        assertTrue(peak.scale > 1f)
    }
}
