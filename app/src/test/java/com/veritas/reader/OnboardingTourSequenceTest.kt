package com.veritas.reader

import com.veritas.reader.ui.OnboardingStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingTourSequenceTest {

    @Test
    fun testClassicsSpotlightStepProperties() {
        val classicsStep = OnboardingStep.CLASSICS_SPOTLIGHT
        assertEquals("classics", classicsStep.key)
        assertTrue(classicsStep.targetKey == "classics_shelves" || classicsStep.targetKey == "classics_catalog_card")
        assertTrue(classicsStep.title.contains("Classic", ignoreCase = true))
        assertTrue(classicsStep.body.contains("books", ignoreCase = true) || classicsStep.body.contains("classic", ignoreCase = true))
    }

    @Test
    fun testAllOnboardingStepsHaveUniqueKeys() {
        val steps = OnboardingStep.entries
        val keys = steps.map { it.key }
        assertEquals("All onboarding steps must have unique keys", keys.size, keys.toSet().size)

        steps.forEach { step ->
            assertNotNull("Title must not be null", step.title)
            assertTrue("Title must not be empty", step.title.isNotBlank())
            assertNotNull("Body must not be null", step.body)
            assertTrue("Body must not be empty", step.body.isNotBlank())
        }
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
