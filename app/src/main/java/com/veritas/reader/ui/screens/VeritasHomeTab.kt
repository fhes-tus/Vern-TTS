package com.veritas.reader.ui.screens

/** Semantic identities stay stable; visual order is defined separately. */
enum class VeritasHomeTab(val label: String) {
    HOME("Home"),
    LIBRARY("Library"),
    NOTES("Notes"),
    STUDY("Study");

    val pagerIndex: Int get() = orderedDestinations.indexOf(this)

    companion object {
        val orderedDestinations: List<VeritasHomeTab> = listOf(HOME, LIBRARY, STUDY, NOTES)

        fun fromPagerIndex(index: Int): VeritasHomeTab? = orderedDestinations.getOrNull(index)

        fun fromWidgetAction(action: String?): VeritasHomeTab? = when (action) {
            "show_study_dashboard", "show_flashcards" -> STUDY
            "show_notes", "new_note", "new_checklist_note", "new_reminder_note" -> NOTES
            "open_library" -> LIBRARY
            else -> null
        }

        internal fun requestedDestination(
            explicitTarget: VeritasHomeTab?,
            widgetAction: String?,
            handledWidgetAction: String?
        ): VeritasHomeTab? = explicitTarget
            ?: widgetAction.takeIf { it != handledWidgetAction }?.let(::fromWidgetAction)
    }
}
