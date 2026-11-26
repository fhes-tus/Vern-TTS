package com.veritas.reader.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.SavedDocument
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.VeritasWordmark
import com.veritas.reader.ui.OnboardingController
import com.veritas.reader.ui.ReaderUiState
import java.util.Locale


@Composable
internal fun LibraryTopAndFilterBar(
    activeNavTab: VeritasHomeTab,
    documents: List<SavedDocument>,
    queuedDocuments: List<SavedDocument>,
    uiState: ReaderUiState,
    currentStreak: Int,
    statusFilter: String,
    onStatusFilterChange: (String) -> Unit,
    sourceFilter: String,
    onSourceFilterChange: (String) -> Unit,
    collectionFilter: String,
    onCollectionFilterChange: (String) -> Unit,
    readingListFilter: String,
    onReadingListFilterChange: (String) -> Unit,
    selectedGeneralNoteTag: String,
    onSelectedGeneralNoteTagChange: (String) -> Unit,
    onOpenHomeSidebar: () -> Unit,
    onOpenSettingsHub: () -> Unit,
    isActiveHeader: Boolean = true,
    onOpenNotesSettings: () -> Unit = onOpenSettingsHub,
    headerActions: @Composable () -> Unit = {},
    notesSearch: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    includeStatusBar: Boolean = true
) {
                val scheme = MaterialTheme.colorScheme
                val isDark = scheme.surface.luminance() < 0.5f
                val topBarColor = VeritasPackStyle.headerColor(scheme)
                val topBarContentColor = scheme.onSurface
                Surface(
                    color = topBarColor,
                    contentColor = topBarContentColor,
                    tonalElevation = if (isDark) 0.dp else 3.dp,
                    modifier = modifier.fillMaxWidth().testTag("shared_home_header")
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 760.dp)
                                .fillMaxWidth()
                                .then(if (includeStatusBar) Modifier.statusBarsPadding() else Modifier)
                                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            when (activeNavTab) {
                                VeritasHomeTab.HOME -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            TextButton(
                                                onClick = { onOpenHomeSidebar() },
                                                contentPadding = PaddingValues(horizontal = 0.dp),
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .onGloballyPositioned { if (isActiveHeader) OnboardingController.updateBounds("insights_trigger", it) }
                                            ) {
                                                Icon(imageVector = Icons.Filled.Menu,
                                                    contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_menu),
                                                    tint = MaterialTheme.colorScheme.onSurface)
                                            }
                                            VeritasWordmark()
                                        }
                                        IconButton(
                                            onClick = onOpenSettingsHub,
                                            modifier = Modifier.onGloballyPositioned { if (isActiveHeader) OnboardingController.updateBounds("settings_trigger", it) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Settings,
                                                modifier = Modifier.size(20.dp),
                                                contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_settings),
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                                VeritasHomeTab.LIBRARY -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_my_library),
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        headerActions()
                                        IconButton(
                                            onClick = onOpenSettingsHub,
                                            modifier = Modifier.onGloballyPositioned { if (isActiveHeader) OnboardingController.updateBounds("settings_trigger", it) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Settings,
                                                modifier = Modifier.size(20.dp),
                                                contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_settings),
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                                VeritasHomeTab.NOTES -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_notes_annotations),
                                                style = MaterialTheme.typography.titleLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                                }
                                    headerActions()
                                    }
                                    notesSearch()
                                }
                                VeritasHomeTab.STUDY -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_study_hub),
                                            modifier = Modifier.testTag("study-title"),
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            modifier = Modifier.testTag("study-streak"),
                                            shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                val streak = uiState.readerTrackerSnapshot.currentStreak
                                                Icon(
                                                    if (streak > 0) Icons.Outlined.LocalFireDepartment else Icons.Outlined.Spa,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = if (streak > 0) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_121, com.veritas.reader.ui.screens.localizedUiValue(streak)) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_0_day_streak),
                                                    style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

}

private val LibrarySectionTabHeight = 40.dp
private val LibrarySectionIndicatorHeight = 3.5.dp
internal val LibrarySectionPickerHeight = LibrarySectionTabHeight + LibrarySectionIndicatorHeight

@Composable
internal fun LibrarySectionPicker(
    selected: LibrarySection,
    progress: () -> Float,
    onSelect: (LibrarySection) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        color = VeritasPackStyle.headerColor(scheme),
        contentColor = scheme.onSurface,
        tonalElevation = if (scheme.surface.luminance() < 0.5f) 0.dp else 3.dp
    ) {
        SectionTabs(
            labels = LibrarySection.entries.map { it.labelResource },
            selectedIndex = selected.ordinal,
            progress = progress,
            onSelect = { onSelect(LibrarySection.entries[it]) },
            modifier = Modifier.padding(horizontal = 12.dp),
            indicatorModifier = Modifier.testTag("library_section_indicator")
                .semantics { this[LibraryTabProgress] = progress().coerceIn(0f, 1f) }
        )
    }
}

@Composable
internal fun SectionTabs(
    labels: List<Int>,
    selectedIndex: Int,
    progress: () -> Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    indicatorModifier: Modifier = Modifier,
    isTabEnabled: (Int) -> Boolean = { true }
) {
    val scheme = MaterialTheme.colorScheme
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            labels.forEachIndexed { index, label ->
                Tab(
                    selected = index == selectedIndex,
                    onClick = { onSelect(index) },
                    enabled = isTabEnabled(index),
                    modifier = Modifier.weight(1f).height(LibrarySectionTabHeight),
                    selectedContentColor = scheme.primary,
                    unselectedContentColor = scheme.onSurfaceVariant
                ) {
                    Text(menuString(label), fontWeight = FontWeight.SemiBold, maxLines = 2,
                        overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().height(LibrarySectionIndicatorHeight).then(indicatorModifier)) {
            val position = progress().coerceIn(0f, labels.lastIndex.toFloat())
            val index = if (rtl) labels.lastIndex - position else position
            val tabWidth = size.width / labels.size
            val indicatorWidth = minOf(56.dp.toPx(), tabWidth)
            drawRoundRect(
                color = scheme.primary,
                topLeft = Offset(tabWidth * (0.5f + index) - indicatorWidth / 2f, 0f),
                size = Size(indicatorWidth, size.height),
                cornerRadius = CornerRadius(size.height / 2f)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun LibraryTopAndFilterBarPreview() {
    MaterialTheme {
        LibraryTopAndFilterBar(
            activeNavTab = VeritasHomeTab.HOME,
            documents = emptyList(),
            queuedDocuments = emptyList(),
            uiState = ReaderUiState(),
            currentStreak = 3,
            statusFilter = "All",
            onStatusFilterChange = {},
            sourceFilter = "All",
            onSourceFilterChange = {},
            collectionFilter = "All",
            onCollectionFilterChange = {},
            readingListFilter = "All",
            onReadingListFilterChange = {},
            selectedGeneralNoteTag = "All",
            onSelectedGeneralNoteTagChange = {},
            onOpenHomeSidebar = {},
            onOpenSettingsHub = {}
        )
    }
}

@Composable
internal fun LibraryDocumentFilters(
    documents: List<SavedDocument>, queuedDocuments: List<SavedDocument>, uiState: ReaderUiState,
    statusFilter: String, onStatusFilterChange: (String) -> Unit,
    sourceFilter: String, onSourceFilterChange: (String) -> Unit,
    collectionFilter: String, onCollectionFilterChange: (String) -> Unit,
    readingListFilter: String, onReadingListFilterChange: (String) -> Unit
) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.material3.LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                                        // Dynamic filters based on actual available document states and collections
                                        val hasFavorites = remember(documents) { documents.any { it.favorite } }
                                        val hasUnread = remember(documents) { documents.any { it.currentIndex <= 0 } }
                                        val hasInProgress = remember(documents) { documents.any { it.chunkCount > 1 && it.currentIndex in 1 until it.chunkCount - 1 } }
                                        val hasCompleted = remember(documents) { documents.any { it.chunkCount > 0 && it.currentIndex >= it.chunkCount - 1 } }

                                        val inProgressCount = remember(documents) { documents.count { it.chunkCount > 1 && it.currentIndex in 1 until it.chunkCount - 1 } }
                                        val unreadCount = remember(documents) { documents.count { it.currentIndex <= 0 } }
                                        val completedCount = remember(documents) { documents.count { it.chunkCount > 0 && it.currentIndex >= it.chunkCount - 1 } }
                                        val favoritesCount = remember(documents) { documents.count { it.favorite } }

                                        val collections = uiState.readingListCatalog.activeLists
                                        val formats = remember(documents) {
                                            documents.map { it.sourceLabel.trim() }.filter { it.isNotBlank() }.distinct().sorted()
                                        }

                                        data class LibraryChip(
                                            val label: String,
                                            val active: Boolean,
                                            val count: Int,
                                            val catalog: Boolean = true,
                                            val apply: () -> Unit
                                        )

                                        fun reset() {
                                            onStatusFilterChange("All")
                                            onSourceFilterChange("All")
                                            onCollectionFilterChange("All")
                                            onReadingListFilterChange("All")
                                        }

                                        val chips = buildList {
                                            add(LibraryChip(
                                                "All",
                                                statusFilter == "All" && sourceFilter == "All" &&
                                                    collectionFilter == "All" && readingListFilter == "All",
                                                documents.size
                                            ) { reset() })

                                            if (hasInProgress || statusFilter == "In progress") {
                                                add(LibraryChip("In progress", statusFilter == "In progress", inProgressCount) {
                                                    if (statusFilter == "In progress") reset() else { reset(); onStatusFilterChange("In progress") }
                                                })
                                            }

                                            if (hasUnread || statusFilter == "Unread") {
                                                add(LibraryChip("Unread", statusFilter == "Unread", unreadCount) {
                                                    if (statusFilter == "Unread") reset() else { reset(); onStatusFilterChange("Unread") }
                                                })
                                            }

                                            if (hasCompleted || statusFilter == "Completed") {
                                                add(LibraryChip("Completed", statusFilter == "Completed", completedCount) {
                                                    if (statusFilter == "Completed") reset() else { reset(); onStatusFilterChange("Completed") }
                                                })
                                            }

                                            if (hasFavorites || statusFilter == "Favorites") {
                                                add(LibraryChip("Favorites", statusFilter == "Favorites", favoritesCount) {
                                                    if (statusFilter == "Favorites") reset() else { reset(); onStatusFilterChange("Favorites") }
                                                })
                                            }

                                            if (queuedDocuments.isNotEmpty()) {
                                                add(LibraryChip("Queue", statusFilter == "Queued", queuedDocuments.size) {
                                                    reset(); onStatusFilterChange("Queued")
                                                })
                                            }

                                            collections.forEach { readingList ->
                                                val count = documents.count { readingList.contains(it.id) }
                                                add(LibraryChip(readingList.title, readingListFilter == readingList.id, count, catalog = false) {
                                                    if (readingListFilter == readingList.id) reset() else { reset(); onReadingListFilterChange(readingList.id) }
                                                })
                                            }

                                            formats.forEach { fmt ->
                                                val count = documents.count { it.sourceLabel.trim().equals(fmt, ignoreCase = true) }
                                                add(LibraryChip(fmt, sourceFilter == fmt, count) {
                                                    if (sourceFilter == fmt) reset() else { reset(); onSourceFilterChange(fmt) }
                                                })
                                            }
                                        }

                                        chips.forEach { chip ->
                                            val label = if (chip.catalog) localizedCatalogLabel(chip.label) else chip.label
                                            val countedLabel = menuString(com.veritas.reader.R.string.filter_count, label, localizedUiValue(chip.count))
                                            if (chip.active) {
                                                Button(
                                                    onClick = chip.apply,
                                                    shape = VeritasPackStyle.chipShape(),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = VeritasPackStyle.filterContainerColor(MaterialTheme.colorScheme),
                                                        contentColor = VeritasPackStyle.onFilterContainerColor(MaterialTheme.colorScheme)
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                                                ) {
                                                    Text(countedLabel, style = MaterialTheme.typography.labelMedium)
                                                }
                                            } else {
                                                OutlinedButton(
                                                    onClick = chip.apply,
                                                    shape = VeritasPackStyle.chipShape(),
                                                    border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                                                ) {
                                                    Text(countedLabel, style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                        }
                                    }
    }
}
