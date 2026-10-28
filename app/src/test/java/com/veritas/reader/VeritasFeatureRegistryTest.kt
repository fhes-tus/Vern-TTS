package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VeritasFeatureRegistryTest {
    @Test fun readingListsRemainInLibraryAndSettingsOnly() {
        val surfaces = VeritasFeatureRegistry.feature(VeritasFeatureId.READING_LISTS).surfaces
        assertFalse(VeritasFeatureSurface.READER_OVERFLOW in surfaces)
        assertTrue(VeritasFeatureSurface.LIBRARY_OVERFLOW in surfaces)
        assertTrue(VeritasFeatureSurface.SETTINGS_HUB in surfaces)
    }

    @Test
    fun registryDefinesEveryFeatureId() {
        assertTrue(VeritasFeatureRegistry.definitions.isNotEmpty())
        assertEquals(
            VeritasFeatureId.entries.toSet(),
            VeritasFeatureRegistry.definitions.map { it.id }.toSet()
        )
    }

    @Test
    fun futurePaidOrApiFeatureCanBeModeledWithoutEnablingItInCurrentMenus() {
        val futureDefinition = VeritasFeatureDefinition(
            id = VeritasFeatureId.AI_APP_HANDOFF,
            title = "Future direct AI provider",
            description = "A future provider-backed AI workflow outside the current local-first menu scope.",
            category = VeritasFeatureCategory.READER_TOOLS,
            stage = VeritasFeatureStage.EXPANSION,
            surfaces = setOf(VeritasFeatureSurface.SETTINGS_HUB),
            priority = 999,
            requiresAccountOrPaidApi = true
        )

        assertTrue(futureDefinition.requiresAccountOrPaidApi)
        assertTrue(futureDefinition.surfaces.contains(VeritasFeatureSurface.SETTINGS_HUB))
    }

    @Test
    fun currentBuildMenusDoNotExposePaidOrApiFeatures() {
        val resolvedFeatures = VeritasFeatureSurface.entries.flatMap { surface ->
            VeritasFeatureRegistry.resolve(
                surface,
                VeritasFeatureContext(
                    hasActiveDocument = true,
                    hasTextSelection = true,
                    hasSavedDocument = true,
                    hasPdfDocument = true,
                    queueCount = 1,
                    hasFileBrowserSession = true,
                    hasImportableFile = true
                )
            )
        }

        assertTrue(resolvedFeatures.none { it.definition.requiresAccountOrPaidApi })
    }

    @Test
    fun featureLookupReturnsDefinitionById() {
        val definition = VeritasFeatureRegistry.feature(VeritasFeatureId.SLEEP_TIMER)

        assertEquals("Sleep timer", definition.title)
    }

    @Test
    fun selectionFeaturesRequireSelectedTextWhenNeeded() {
        val withoutSelection = VeritasFeatureRegistry.resolve(
            VeritasFeatureSurface.SELECTION_OVERFLOW,
            VeritasFeatureContext(hasActiveDocument = true)
        )
        val dictionary = withoutSelection.first { it.definition.id == VeritasFeatureId.OFFLINE_DICTIONARY }

        assertFalse(dictionary.enabled)
        assertEquals("Select text first.", dictionary.disabledReason)

        val withSelection = VeritasFeatureRegistry.resolve(
            VeritasFeatureSurface.SELECTION_OVERFLOW,
            VeritasFeatureContext(hasActiveDocument = true, hasTextSelection = true)
        )
        val enabledDictionary = withSelection.first { it.definition.id == VeritasFeatureId.OFFLINE_DICTIONARY }

        assertTrue(enabledDictionary.enabled)
    }

    @Test
    fun settingsOnlyImportFeaturesDoNotRequireAChosenFile() {
        val settingsActions = VeritasFeatureRegistry.resolve(
            VeritasFeatureSurface.SETTINGS_HUB,
            VeritasFeatureContext()
        )
        val encoding = settingsActions.first { it.definition.id == VeritasFeatureId.ENCODING_OPTIONS }
        val pdfImport = settingsActions.first { it.definition.id == VeritasFeatureId.PDF_IMPORT_CONTROLS }

        assertTrue(encoding.enabled)
        assertTrue(pdfImport.enabled)
    }

    @Test
    fun readerOverflowKeepsActionsOrderedByPriority() {
        val readerActions = VeritasFeatureRegistry.resolve(
            VeritasFeatureSurface.READER_OVERFLOW,
            VeritasFeatureContext(hasActiveDocument = true, queueCount = 2)
        )
        val priorities = readerActions.map { it.definition.priority }

        assertEquals(priorities.sorted(), priorities)
    }

    @Test
    fun queueAudioExportRequiresQueuedItems() {
        val readerActions = VeritasFeatureRegistry.resolve(
            VeritasFeatureSurface.READER_OVERFLOW,
            VeritasFeatureContext(hasActiveDocument = true)
        )
        val readerExport = readerActions.first { it.definition.id == VeritasFeatureId.QUEUE_AUDIO_EXPORT }

        assertTrue(readerExport.enabled)

        val emptyQueueActions = VeritasFeatureRegistry.resolve(
            VeritasFeatureSurface.LIBRARY_OVERFLOW,
            VeritasFeatureContext(hasSavedDocument = true)
        )
        val export = emptyQueueActions.first { it.definition.id == VeritasFeatureId.QUEUE_AUDIO_EXPORT }

        assertFalse(export.enabled)
        assertEquals("Add readings to a queue first.", export.disabledReason)

        val queuedActions = VeritasFeatureRegistry.resolve(
            VeritasFeatureSurface.LIBRARY_OVERFLOW,
            VeritasFeatureContext(hasSavedDocument = true, queueCount = 1)
        )
        val enabledExport = queuedActions.first { it.definition.id == VeritasFeatureId.QUEUE_AUDIO_EXPORT }

        assertTrue(enabledExport.enabled)
    }
}
