package com.veritas.reader

import org.junit.Assert.*
import org.junit.Test

class DocumentOverviewTest {
    @Test fun overviewsAndCachedDescriptionsStopAtTwoSentences() {
        val first = "This study examines renewable energy storage systems for isolated communities."
        val second = "It compares battery storage and seasonal demand using three independent regions."
        val third = "A final section discusses the maintenance costs of those energy storage systems."
        assertEquals("$first $second", DocumentOverview.limitSentences("$first\n$second $third"))
        assertEquals("$first $second", DocumentOverview.summarize("Abstract\n$first $second $third\n\nIntroduction"))
        val quoted = "‘She always leaves the keys there for me.’ He pointed at the stone beside the gate to his home. ‘Maybe she left a message.’"
        assertEquals("‘She always leaves the keys there for me.’ He pointed at the stone beside the gate to his home.",
            DocumentOverview.limitSentences(quoted))
    }
    @Test fun fileSamplingIsBoundedAndHandlesUnicodeAcrossByteBoundaries() {
        val file = java.io.File.createTempFile("overview", ".txt")
        try {
            file.writeText("Copyright notice.\n" + "This document explains renewable energy storage in Montréal and isolated communities. ".repeat(50000))
            val sample = DocumentOverview.sample(file)
            assertTrue(sample.toByteArray().size <= 48030)
            assertTrue(sample.contains("Montréal"))
            assertFalse(sample.contains('\uFFFD'))
            assertTrue(DocumentOverview.summarize(sample).contains("energy"))
        } finally { file.delete() }
    }
    @Test fun prefersAbstractOverOpeningAndCopyright() {
        val raw = "Copyright notice. All rights reserved.\n\nThe first unrelated anecdote is merely a preface for the reader.\n\nAbstract\nThis study examines the reliability of renewable energy storage systems in isolated communities. It compares battery storage and seasonal demand using observations from three independent regions.\n\nIntroduction\nThe investigation begins here."
        val result = DocumentOverview.summarize(raw)
        assertTrue(result.startsWith("This study examines"))
        assertTrue(result.contains("battery storage"))
        assertFalse(result.contains("Copyright"))
        assertFalse(result.contains("unrelated anecdote"))
    }
    @Test fun samplesAcrossLongDocumentsAndKeepsCompleteSentences() {
        val raw = ("Opening unrelated anecdote. ".repeat(2000)) +
            ("The battery storage system balances energy demand in isolated communities. Renewable energy storage reduces reliance on fossil fuel generators in these communities. ".repeat(3000))
        val sample = DocumentOverview.sample(raw)
        assertTrue(sample.length <= 48030)
        assertTrue(sample.contains("battery storage"))
        val result = DocumentOverview.summarize(raw)
        assertTrue(result.contains("storage"))
        assertTrue(result.length <= 650)
        assertTrue(result.endsWith("."))
    }
    @Test fun insufficientTextDoesNotInventABookDescription() {
        assertEquals("No readable text is available yet.", DocumentOverview.summarize(""))
        assertTrue(DocumentOverview.summarize("Copyright 2026\n\nISBN 12345").contains("could not be inferred"))
    }
}
