package com.veritas.reader.ui.screens

enum class LibrarySection(val label: String) { MY_LIBRARY("Added"), CLASSICS("Classics") }
internal val LibraryTabProgress = androidx.compose.ui.semantics.SemanticsPropertyKey<Float>("LibraryTabProgress")

/** Library's two pages swipe before advancing to the next main destination. */
internal data class LibraryPagerPage(val tab: VeritasHomeTab, val section: LibrarySection? = null)
internal val libraryPagerPages = listOf(
    LibraryPagerPage(VeritasHomeTab.HOME),
    LibraryPagerPage(VeritasHomeTab.LIBRARY, LibrarySection.MY_LIBRARY),
    LibraryPagerPage(VeritasHomeTab.LIBRARY, LibrarySection.CLASSICS),
    LibraryPagerPage(VeritasHomeTab.STUDY),
    LibraryPagerPage(VeritasHomeTab.NOTES)
)
internal fun libraryPagerIndex(tab: VeritasHomeTab, section: LibrarySection = LibrarySection.MY_LIBRARY): Int =
    libraryPagerPages.indexOfFirst { it.tab == tab && (tab != VeritasHomeTab.LIBRARY || it.section == section) }
