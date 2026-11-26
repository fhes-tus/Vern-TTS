package com.veritas.reader

import com.veritas.reader.ui.OnboardingStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingTourSequenceTest {

    @Test
    fun testClassicsSpotlightStepProperties() {
        val classicsStep = OnboardingStep.CLASSICS_SPOTLIGHT
        assertEquals("classics", classicsStep.key)
        assertTrue(classicsStep.targetKey == "classics_shelves" || classicsStep.targetKey == "classics_catalog_card")
        assertEquals(R.string.tour_classics_spotlight_title, classicsStep.titleRes)
        assertEquals(R.string.tour_classics_spotlight_body, classicsStep.bodyRes)
    }

    @Test
    fun testAllOnboardingStepsHaveUniqueKeys() {
        val steps = OnboardingStep.entries
        val keys = steps.map { it.key }
        assertEquals("All onboarding steps must have unique keys", keys.size, keys.toSet().size)

        val copy = steps.flatMap { listOf(it.titleRes, it.bodyRes) }
        assertTrue("All steps must have title and body resources", copy.all { it != 0 })
        assertEquals("Each title and body has its own resource", copy.size, copy.toSet().size)
    }

    @Test
    fun testTourContainsClassicsSpotlight() {
        val stepNames = OnboardingStep.entries.map { it.name }
        assertTrue(stepNames.contains("CLASSICS_SPOTLIGHT"))
        assertTrue(stepNames.contains("FAB_SPOTLIGHT"))
        assertTrue(stepNames.contains("CHECKLIST_SPOTLIGHT"))
        assertTrue(stepNames.contains("INSIGHTS_SPOTLIGHT"))
    }
}
