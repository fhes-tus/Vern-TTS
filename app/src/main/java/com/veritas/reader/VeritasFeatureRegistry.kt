package com.veritas.reader

enum class VeritasFeatureId {
    AUDIO_OUTPUT_SAFETY,
    HEADSET_CONTROL_MAPPING,
    SLEEP_TIMER,
    ENCODING_OPTIONS,
    PDF_IMPORT_CONTROLS,
    FILE_BROWSER,
    FILE_BROWSER_SORTING,
    PRONUNCIATION_RULES,
    VOICE_STUDIO,
    OFFLINE_STUDY_TOOLS,
    AI_APP_HANDOFF,
    TRANSLATION_HANDOFF,
    OFFLINE_DICTIONARY,
    WIKIPEDIA_LOOKUP,
    EXTRACTED_TEXT_EDITOR,
    BOOKMARKS_AND_NOTES,
    READING_LISTS,
    READING_HISTORY,
    LOCAL_SYNC_PACK,
    QUEUE_AUDIO_EXPORT,
    DOWNLOAD_MONITOR
}

enum class VeritasFeatureSurface {
    READER_OVERFLOW,
    SELECTION_OVERFLOW,
    SETTINGS_HUB,
    LIBRARY_OVERFLOW,
    FILE_BROWSER_OVERFLOW,
    IMPORT_OPTIONS,
    PLAYBACK_OPTIONS
}

enum class VeritasFeatureCategory {
    PLAYBACK,
    IMPORT,
    READER_TOOLS,
    LIBRARY,
    LOCAL_SYNC,
    SETTINGS
}

enum class VeritasFeatureStage {
    EXISTING,
    EXPANSION,
    NEW_LOCAL
}

enum class VeritasFeatureRequirement(val disabledReason: String) {
    ACTIVE_DOCUMENT("Open a reading first."),
    TEXT_SELECTION("Select text first."),
    SAVED_DOCUMENT("Save or import a reading first."),
    QUEUE_ITEMS("Add readings to a queue first."),
    PDF_DOCUMENT("Open or import a PDF first."),
    FILE_BROWSER_SESSION("Open the file browser first."),
    IMPORTABLE_FILE("Choose a file to import first.")
}

data class VeritasFeatureContext(
    val hasActiveDocument: Boolean = false,
    val hasTextSelection: Boolean = false,
    val hasSavedDocument: Boolean = false,
    val hasPdfDocument: Boolean = false,
    val queueCount: Int = 0,
    val hasFileBrowserSession: Boolean = false,
    val hasImportableFile: Boolean = false
)

data class VeritasFeatureDefinition(
    val id: VeritasFeatureId,
    val title: String,
    val description: String,
    val category: VeritasFeatureCategory,
    val stage: VeritasFeatureStage,
    val surfaces: Set<VeritasFeatureSurface>,
    val requirements: Set<VeritasFeatureRequirement> = emptySet(),
    val surfaceRequirements: Map<VeritasFeatureSurface, Set<VeritasFeatureRequirement>> = emptyMap(),
    val priority: Int,
    val requiresAccountOrPaidApi: Boolean = false
) {
    init {
        require(title.isNotBlank()) { "Feature title cannot be blank." }
        require(description.isNotBlank()) { "Feature description cannot be blank." }
        require(surfaces.isNotEmpty()) { "$id must declare at least one UI surface." }
        require(surfaceRequirements.keys.all { it in surfaces }) { "$id has requirements for an undeclared surface." }
    }

    fun requirementsFor(surface: VeritasFeatureSurface): Set<VeritasFeatureRequirement> =
        requirements + surfaceRequirements[surface].orEmpty()
}

data class ResolvedVeritasFeature(
    val definition: VeritasFeatureDefinition,
    val enabled: Boolean,
    val disabledReason: String? = null
)

object VeritasFeatureRegistry {
    val definitions: List<VeritasFeatureDefinition> = listOf(
        VeritasFeatureDefinition(
            id = VeritasFeatureId.AUDIO_OUTPUT_SAFETY,
            title = "Audio output safety",
            description = "Pause or duck speech during audio focus changes and headphone disconnects.",
            category = VeritasFeatureCategory.PLAYBACK,
            stage = VeritasFeatureStage.NEW_LOCAL,
            surfaces = setOf(VeritasFeatureSurface.SETTINGS_HUB, VeritasFeatureSurface.PLAYBACK_OPTIONS),
            priority = 10
        ),
        VeritasFeatureDefinition(
            id = VeritasFeatureId.HEADSET_CONTROL_MAPPING,
            title = "Headset controls",
            description = "Map wired, Bluetooth, and media-button actions to reading controls.",
            category = VeritasFeatureCategory.PLAYBACK,
            stage = VeritasFeatureStage.EXPANSION,
            surfaces = setOf(VeritasFeatureSurface.SETTINGS_HUB, VeritasFeatureSurface.PLAYBACK_OPTIONS),
            priority = 20
        ),
        VeritasFeatureDefinition(
            id = VeritasFeatureId.SLEEP_TIMER,
            title = "Sleep timer",
            description = "Stop or pause reading after a chosen duration with optional shake reset.",
            category = VeritasFeatureCategory.PLAYBACK,
            stage = VeritasFeatureStage.NEW_LOCAL,
            surfaces = setOf(VeritasFeatureSurface.READER_OVERFLOW, VeritasFeatureSurface.SETTINGS_HUB, VeritasFeatureSurface.PLAYBACK_OPTIONS),
            requirements = setOf(VeritasFeatureRequirement.ACTIVE_DOCUMENT),
            priority = 30
        ),
        VeritasFeatureDefinition(
            id = VeritasFeatureId.ENCODING_OPTIONS,
            title = "Text encoding",
            description = "Choose auto-detect, Unicode, or legacy encodings before saving imported text.",
            category = VeritasFeatureCategory.IMPORT,
            stage = VeritasFeatureStage.NEW_LOCAL,
            surfaces = setOf(VeritasFeatureSurface.IMPORT_OPTIONS, VeritasFeatureSurface.SETTINGS_HUB),
            surfaceRequirements = mapOf(
                VeritasFeatureSurface.IMPORT_OPTIONS to setOf(VeritasFeatureRequirement.IMPORTABLE_FILE)
            ),
            priority = 40
        ),
        VeritasFeatureDefinition(
            id = VeritasFeatureId.PDF_IMPORT_CONTROLS,
            title = "PDF import controls",
            description = "Control OCR, page ranges, column handling, and text cleanup for PDF imports.",
            category = VeritasFeatureCategory.IMPORT,
            stage = VeritasFeatureStage.EXPANSION,
            surfaces = setOf(VeritasFeatureSurface.IMPORT_OPTIONS, VeritasFeatureSurface.SETTINGS_HUB),
            surfaceRequirements = mapOf(
                VeritasFeatureSurface.IMPORT_OPTIONS to setOf(VeritasFeatureRequirement.IMPORTABLE_FILE)
            ),
            priority = 50
        ),
        VeritasFeatureDefinition(
            id = VeritasFeatureId.FILE_BROWSER,
            title = "File browser",
            description = "Browse approved folders and import supported local files from inside Vern.",
            category = VeritasFeatureCategory.IMPORT,
            stage = VeritasFeatureStage.NEW_LOCAL,
            surfaces = setOf(VeritasFeatureSurface.SETTINGS_HUB, VeritasFeatureSurface.FILE_BROWSER_OVERFLOW),
            priority = 60
        ),
        VeritasFeatureDefinition(
            id = VeritasFeatureId.FILE_BROWSER_SORTING,
            title = "Sort files",
            description = "Sort browsed files by name, title, author, date, size, or path.",
            category = VeritasFeatureCategory.IMPORT,
            stage = VeritasFeatureStage.NEW_LOCAL,
            surfaces = setOf(VeritasFeatureSurface.FILE_BROWSER_OVERFLOW),
            surfaceRequirements = mapOf(
                VeritasFeatureSurface.FILE_BROWSER_OVERFLOW to setOf(VeritasFeatureRequirement.FILE_BROWSER_SESSION)
            ),
            priority = 70
        ),
        VeritasFeatureDefinition(
            id = VeritasFeatureId.PRONUNCIATION_RULES,
            title = "Pronunciation rules",
            description = "Apply local speech replacements before playback and audio export.",
            category = VeritasFeatureCategory.READER_TOOLS,
            stage = VeritasFeatureStage.EXPANSION,
            surfaces = setOf(VeritasFeatureSurface.READER_OVERFLOW, VeritasFeatureSurface.SELECTION_OVERFLOW, VeritasFeatureSurface.SETTINGS_HUB),
            surfaceRequirements = mapOf(
                VeritasFeatureSurface.SELECTION_OVERFLOW to setOf(VeritasFeatureRequirement.TEXT_SELECTION)
            ),
            priority = 80
        ),
        VeritasFeatureDefinition(
            id = VeritasFeatureId.VOICE_STUDIO,
            title = "Voice and language",
            description = "Manage installed Android TTS engines, voices, rate, pitch, and local presets.",
            category = VeritasFeatureCategory.PLAYBACK,
            stage = VeritasFeatureStage.EXPANSION,
            surfaces = setOf(VeritasFeatureSurface.READER_OVERFLOW, VeritasFeatureSurface.SETTINGS_HUB, VeritasFeatureSurface.PLAYBACK_OPTIONS),
            priority = 90
        ),
        VeritasFeatureDefinition(
            id = VeritasFeatureId.OFFLINE_STUDY_TOOLS,
            title = "Offline study tools",
            description = "Generate local summaries, key points, terms, cards, and quizzes from saved text.",
            category = VeritasFeatureCategory.READER_TOOLS,
            stage = VeritasFeatureStage.EXISTING,
            surfaces = setOf(VeritasFeatureSurface.READER_OVERFLOW, VeritasFeatureSurface.SETTINGS_HUB),
            surfaceRequirements = mapOf(
                VeritasFeatureSurface.READER_OVERFLOW to setOf(VeritasFeatureRequirement.ACTIVE_DOCUMENT)
            ),
            priority = 100
        ),
        VeritasFeatureDefinition(
            id = VeritasFeatureId.AI_APP_HANDOFF,
            title = "Ask AI handoff",
            description = "Prepare selected text or a reading part for an installed AI app without API keys.",
            category = VeritasFeatureCategory.READER_TOOLS,
            stage = VeritasFeatureStage.EXISTING,
            surfaces = setOf(VeritasFeatureSurface.READER_OVERFLOW, VeritasFeatureSurface.SELECTION_OVERFLOW, VeritasFeatureSurface.SETTINGS_HUB),
            surfaceRequirements = mapOf(
                VeritasFeatureSurface.READER_OVERFLOW to setOf(VeritasFeatureRequirement.ACTIVE_DOCUMENT),
                VeritasFeatureSurface.SELECTION_OVERFLOW to setOf(VeritasFeatureRequirement.TEXT_SELECTION)
            ),
            priority = 110
        ),
        VeritasFeatureDefinition(
            id = VeritasFeatureId.TRANSLATION_HANDOFF,
            title = "Translation handoff",
            description = "Prepare selected text, sections, or documents for installed translation apps.",
            category = VeritasFeatureCategory.READER_TOOLS,
            stage = VeritasFeatureStage.EXISTING,
            surfaces = setOf(VeritasFeatureSurface.READER_OVERFLOW, VeritasFeatureSurface.SELECTION_OVERFLOW, VeritasFeatureSurface.SETTINGS_HUB),
            surfaceRequirements = mapOf(
                VeritasFeatureSurface.READER_OVERFLOW to setOf(VeritasFeatureRequirement.ACTIVE_DOCUMENT),
                VeritasFeatureSurface.SELECTION_OVERFLOW to setOf(VeritasFeatureRequirement.TEXT_SELECTION)
            ),
            priority = 120
        ),
        VeritasFeatureDefinition(
            id = VeritasFeatureId.OFFLINE_DICTIONARY,
            title = "Dictionary lookup",
            description = "Look up selected words through local dictionary data or installed dictionary targets.",
            category = VeritasFeatureCategory.READER_TOOLS,
            stage = VeritasFeatureStage.NEW_LOCAL,
            surfaces = setOf(VeritasFeatureSurface.SELECTION_OVERFLOW, VeritasFeatureSurface.SETTINGS_HUB),
            surfaceRequirements = mapOf(
                VeritasFeatureSurface.SELECTION_OVERFLOW to setOf(VeritasFeatureRequirement.TEXT_SELECTION)
            ),
            priority = 130
        ),
        VeritasFeatureDefinition(
            id = VeritasFeatureId.WIKIPEDIA_LOOKUP,
            title = "Wikipedia lookup",
            description = "Open a selected term in a user-initiated web or app lookup flow.",
            category = VeritasFeatureCategory.READER_TOOLS,
            stage = VeritasFeatureStage.NEW_LOCAL,
            surfaces = setOf(VeritasFeatureSurface.SELECTION_OVERFLOW),
            surfaceRequirements = mapOf(
                VeritasFeatureSurface.SELECTION_OVERFLOW to setOf(VeritasFeatureRequirement.TEXT_SELECTION)
            ),
            priority = 140
        ),
        VeritasFeatureDefinition(
            id = VeritasFeatureId.EXTRACTED_TEXT_EDITOR,
            title = "Edit extracted text",
            description = "Repair selected sentences or the current reader part without editing the whole book.",
            category = VeritasFeatureCategory.READER_TOOLS,
            stage = VeritasFeatureStage.EXISTING,
            surfaces = setOf(VeritasFeatureSurface.READER_OVERFLOW, VeritasFeatureSurface.SELECTION_OVERFLOW, VeritasFeatureSurface.SETTINGS_HUB),
            requirements = setOf(VeritasFeatureRequirement.ACTIVE_DOCUMENT),
            surfaceRequirements = mapOf(
                VeritasFeatureSurface.SELECTION_OVERFLOW to setOf(VeritasFeatureRequirement.TEXT_SELECTION)
            ),
            priority = 150
        ),
        VeritasFeatureDefinition(
            id = VeritasFeatureId.BOOKMARKS_AND_NOTES,
            title = "Bookmarks and notes",
            description = "Manage sentence bookmarks, bookmark highlighting, notes, and document notes.",
            category = VeritasFeatureCategory.READER_TOOLS,
            stage = VeritasFeatureStage.EXISTING,
            surfaces = setOf(VeritasFeatureSurface.READER_OVERFLOW, VeritasFeatureSurface.SELECTION_OVERFLOW),
            requirements = setOf(VeritasFeatureRequirement.ACTIVE_DOCUMENT),
            surfaceRequirements = mapOf(
                VeritasFeatureSurface.SELECTION_OVERFLOW to setOf(VeritasFeatureRequirement.TEXT_SELECTION)
            ),
            priority = 160
        ),
        VeritasFeatureDefinition(
            id = VeritasFeatureId.READING_LISTS,
            title = "Reading lists",
            description = "Organize saved readings into local lists and playback queues.",
            category = VeritasFeatureCategory.LIBRARY,
            stage = VeritasFeatureStage.EXPANSION,
            surfaces = setOf(VeritasFeatureSurface.LIBRARY_OVERFLOW, VeritasFeatureSurface.SETTINGS_HUB),
            surfaceRequirements = mapOf(
                VeritasFeatureSurface.LIBRARY_OVERFLOW to setOf(VeritasFeatureRequirement.SAVED_DOCUMENT)
            ),
            priority = 170
        ),
        VeritasFeatureDefinition(
            id = VeritasFeatureId.READING_HISTORY,
            title = "Reading history",
            description = "Show recently opened local readings with last-read position.",
            category = VeritasFeatureCategory.LIBRARY,
            stage = VeritasFeatureStage.NEW_LOCAL,
            surfaces = setOf(VeritasFeatureSurface.READER_OVERFLOW, VeritasFeatureSurface.LIBRARY_OVERFLOW),
            surfaceRequirements = mapOf(
                VeritasFeatureSurface.READER_OVERFLOW to setOf(VeritasFeatureRequirement.ACTIVE_DOCUMENT),
                VeritasFeatureSurface.LIBRARY_OVERFLOW to setOf(VeritasFeatureRequirement.SAVED_DOCUMENT)
            ),
            priority = 175
        ),
        VeritasFeatureDefinition(
            id = VeritasFeatureId.LOCAL_SYNC_PACK,
            title = "Local sync pack",
            description = "Export, share, and import local progress, notes, lists, and settings without login.",
            category = VeritasFeatureCategory.LOCAL_SYNC,
            stage = VeritasFeatureStage.EXISTING,
            surfaces = setOf(VeritasFeatureSurface.SETTINGS_HUB, VeritasFeatureSurface.LIBRARY_OVERFLOW),
            priority = 180
        ),
        VeritasFeatureDefinition(
            id = VeritasFeatureId.QUEUE_AUDIO_EXPORT,
            title = "Queue audio export",
            description = "Render the active reading or queue to audio through the installed Android TTS engine.",
            category = VeritasFeatureCategory.PLAYBACK,
            stage = VeritasFeatureStage.EXPANSION,
            surfaces = setOf(VeritasFeatureSurface.READER_OVERFLOW, VeritasFeatureSurface.LIBRARY_OVERFLOW, VeritasFeatureSurface.SETTINGS_HUB),
            surfaceRequirements = mapOf(
                VeritasFeatureSurface.READER_OVERFLOW to setOf(VeritasFeatureRequirement.ACTIVE_DOCUMENT),
                VeritasFeatureSurface.LIBRARY_OVERFLOW to setOf(VeritasFeatureRequirement.QUEUE_ITEMS),
                VeritasFeatureSurface.SETTINGS_HUB to setOf(VeritasFeatureRequirement.ACTIVE_DOCUMENT)
            ),
            priority = 190
        ),
        VeritasFeatureDefinition(
            id = VeritasFeatureId.DOWNLOAD_MONITOR,
            title = "Downloaded page monitor",
            description = "Watch approved folders for newly saved readable files or web pages.",
            category = VeritasFeatureCategory.IMPORT,
            stage = VeritasFeatureStage.NEW_LOCAL,
            surfaces = setOf(VeritasFeatureSurface.SETTINGS_HUB, VeritasFeatureSurface.FILE_BROWSER_OVERFLOW),
            priority = 200
        )
    ).also(::validateDefinitions)

    private val currentBuildDefinitions: List<VeritasFeatureDefinition> =
        definitions.filterNot { it.requiresAccountOrPaidApi }

    private val definitionsById: Map<VeritasFeatureId, VeritasFeatureDefinition> =
        definitions.associateBy { it.id }

    private val definitionsBySurface: Map<VeritasFeatureSurface, List<VeritasFeatureDefinition>> =
        currentBuildDefinitions
            .flatMap { definition -> definition.surfaces.map { surface -> surface to definition } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, surfaceDefinitions) -> surfaceDefinitions.sortedBy { it.priority } }

    fun feature(id: VeritasFeatureId): VeritasFeatureDefinition =
        definitionsById.getValue(id)

    fun resolve(surface: VeritasFeatureSurface, context: VeritasFeatureContext): List<ResolvedVeritasFeature> {
        return definitionsBySurface[surface].orEmpty().map { definition ->
            val unmet = definition.requirementsFor(surface).firstOrNull { !context.satisfies(it) }
            ResolvedVeritasFeature(
                definition = definition,
                enabled = unmet == null,
                disabledReason = unmet?.disabledReason
            )
        }
    }

    fun enabled(surface: VeritasFeatureSurface, context: VeritasFeatureContext): List<VeritasFeatureDefinition> {
        return resolve(surface, context)
            .filter { it.enabled }
            .map { it.definition }
    }

    private fun validateDefinitions(items: List<VeritasFeatureDefinition>) {
        val definedIds = items.map { it.id }.toSet()
        val missingIds = VeritasFeatureId.entries.toSet() - definedIds
        require(missingIds.isEmpty()) { "Missing feature definitions for: $missingIds" }
        require(items.size == definedIds.size) { "Duplicate feature IDs detected." }
    }

    private fun VeritasFeatureContext.satisfies(requirement: VeritasFeatureRequirement): Boolean {
        return when (requirement) {
            VeritasFeatureRequirement.ACTIVE_DOCUMENT -> hasActiveDocument
            VeritasFeatureRequirement.TEXT_SELECTION -> hasTextSelection
            VeritasFeatureRequirement.SAVED_DOCUMENT -> hasSavedDocument
            VeritasFeatureRequirement.QUEUE_ITEMS -> queueCount > 0
            VeritasFeatureRequirement.PDF_DOCUMENT -> hasPdfDocument
            VeritasFeatureRequirement.FILE_BROWSER_SESSION -> hasFileBrowserSession
            VeritasFeatureRequirement.IMPORTABLE_FILE -> hasImportableFile
        }
    }
}
