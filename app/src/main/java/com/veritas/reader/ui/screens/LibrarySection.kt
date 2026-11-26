package com.veritas.reader.ui.screens

/** Prefetched pages keep their content ready without running offscreen decoration. */
internal val LocalLibraryPageVisible = androidx.compose.runtime.compositionLocalOf { true }

enum class LibrarySection(val label: String) {
    MY_LIBRARY("Added"), CLASSICS("Classics");

    val labelResource: Int get() = when (this) {
        MY_LIBRARY -> com.veritas.reader.R.string.ui_added
        CLASSICS -> com.veritas.reader.R.string.ui_classics
    }
}
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

internal fun libraryPagerNavPosition(page: Float): Float {
    val position = page.coerceIn(0f, libraryPagerPages.lastIndex.toFloat())
    val index = position.toInt()
    val from = libraryPagerPages[index].tab.pagerIndex
    val to = libraryPagerPages.getOrNull(index + 1)?.tab?.pagerIndex ?: from
    return from + (to - from) * (position - index)
}

/** In the outer Library transitions, the toolbar is part of the swiped page. */
internal fun libraryHeaderMoves(fromPage: Int, toPage: Int): Boolean {
    if (kotlin.math.abs(fromPage - toPage) != 1) return false
    return when (minOf(fromPage, toPage)) {
        libraryPagerIndex(VeritasHomeTab.HOME),
        libraryPagerIndex(VeritasHomeTab.LIBRARY, LibrarySection.CLASSICS) -> true
        else -> false
    }
}

/** Fraction for a pair whose headers stay centered while the bodies move. */
internal fun libraryPinnedHeaderProgress(page: Int, position: Float): Float? {
    val first = position.toInt()
    val second = first + 1
    val fraction = position - first
    if (fraction <= 0f || second !in libraryPagerPages.indices || page !in first..second ||
        libraryHeaderMoves(first, second)
    ) return null
    return fraction
}
