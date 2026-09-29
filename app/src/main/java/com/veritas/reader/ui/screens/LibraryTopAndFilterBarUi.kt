package com.veritas.reader.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
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
    modifier: Modifier = Modifier
) {
                val scheme = MaterialTheme.colorScheme
                val isDark = scheme.surface.luminance() < 0.5f
                val topBarColor = if (isDark) scheme.surface else scheme.primaryContainer
                val topBarContentColor = if (isDark) scheme.onSurface else scheme.onPrimaryContainer
                Surface(
                    color = topBarColor,
                    contentColor = topBarContentColor,
                    tonalElevation = if (isDark) 0.dp else 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = 760.dp)
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            when (activeNavTab) {
                                VeritasHomeTab.HOME -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
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
                                                    .onGloballyPositioned { OnboardingController.updateBounds("insights_trigger", it) }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Menu,
                                                    contentDescription = "Menu",
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            VeritasWordmark()
                                        }
                                        IconButton(
                                            onClick = onOpenSettingsHub,
                                            modifier = Modifier.onGloballyPositioned { OnboardingController.updateBounds("settings_trigger", it) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Settings,
                                                contentDescription = "Settings",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                                VeritasHomeTab.LIBRARY -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Your library",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        IconButton(
                                            onClick = onOpenSettingsHub,
                                            modifier = Modifier.onGloballyPositioned { OnboardingController.updateBounds("settings_trigger", it) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Settings,
                                                contentDescription = "Settings",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
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

                                        val collections = remember(documents) {
                                            documents.map { it.collection.trim() }
                                                .filter { it.isNotEmpty() }
                                                .distinct()
                                                .sorted()
                                        }
                                        val readingLists = remember(uiState.readingListCatalog, documents) {
                                            uiState.readingListCatalog.lists
                                                .filterNot { it.archived }
                                                .filter { list -> documents.any { doc -> list.contains(doc.id) } || readingListFilter == list.id }
                                                .sortedBy { it.title.lowercase(Locale.getDefault()) }
                                        }
                                        val formats = remember(documents) {
                                            documents.map { it.sourceLabel.trim() }.filter { it.isNotBlank() }.distinct().sorted()
                                        }

                                        data class LibraryChip(
                                            val label: String,
                                            val active: Boolean,
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
                                                "All (${documents.size})",
                                                statusFilter == "All" && sourceFilter == "All" &&
                                                    collectionFilter == "All" && readingListFilter == "All"
                                            ) { reset() })

                                            if (hasInProgress || statusFilter == "In progress") {
                                                add(LibraryChip("In progress ($inProgressCount)", statusFilter == "In progress") {
                                                    if (statusFilter == "In progress") reset() else { reset(); onStatusFilterChange("In progress") }
                                                })
                                            }

                                            if (hasUnread || statusFilter == "Unread") {
                                                add(LibraryChip("Unread ($unreadCount)", statusFilter == "Unread") {
                                                    if (statusFilter == "Unread") reset() else { reset(); onStatusFilterChange("Unread") }
                                                })
                                            }

                                            if (hasCompleted || statusFilter == "Completed") {
                                                add(LibraryChip("Completed ($completedCount)", statusFilter == "Completed") {
                                                    if (statusFilter == "Completed") reset() else { reset(); onStatusFilterChange("Completed") }
                                                })
                                            }

                                            if (hasFavorites || statusFilter == "Favorites") {
                                                add(LibraryChip("Favorites ($favoritesCount)", statusFilter == "Favorites") {
                                                    if (statusFilter == "Favorites") reset() else { reset(); onStatusFilterChange("Favorites") }
                                                })
                                            }

                                            if (queuedDocuments.isNotEmpty()) {
                                                add(LibraryChip("Queue ${queuedDocuments.size}", statusFilter == "Queued") {
                                                    reset(); onStatusFilterChange("Queued")
                                                })
                                            }

                                            collections.forEach { name ->
                                                val count = documents.count { it.collection.trim() == name }
                                                add(LibraryChip("$name ($count)", collectionFilter == name) {
                                                    if (collectionFilter == name) reset() else { reset(); onCollectionFilterChange(name) }
                                                })
                                            }

                                            readingLists.forEach { readingList ->
                                                val count = documents.count { readingList.contains(it.id) }
                                                add(LibraryChip("≡ ${readingList.title} ($count)", readingListFilter == readingList.id) {
                                                    if (readingListFilter == readingList.id) reset() else { reset(); onReadingListFilterChange(readingList.id) }
                                                })
                                            }

                                            formats.forEach { fmt ->
                                                val count = documents.count { it.sourceLabel.trim().equals(fmt, ignoreCase = true) }
                                                add(LibraryChip("$fmt ($count)", sourceFilter == fmt) {
                                                    if (sourceFilter == fmt) reset() else { reset(); onSourceFilterChange(fmt) }
                                                })
                                            }
                                        }

                                        chips.forEach { chip ->
                                            if (chip.active) {
                                                Button(
                                                    onClick = chip.apply,
                                                    shape = VeritasPackStyle.chipShape(),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                                                ) {
                                                    Text(chip.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
                                                    Text(chip.label, style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
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
                                                "Notes & Annotations",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            val totalNotesCount = remember(uiState.generalNotes) { uiState.generalNotes.size }
                                            Text(
                                                "$totalNotesCount note${if (totalNotesCount == 1) "" else "s"}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            )
                                        }
                                        IconButton(onClick = onOpenSettingsHub) {
                                            Icon(
                                                imageVector = Icons.Filled.Settings,
                                                contentDescription = "Settings",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val noteFilterOptions = remember(uiState.generalNotes, uiState.allAnnotations) {
                                            buildList {
                                                add("All Notes")
                                                if (uiState.generalNotes.any { it.pinned }) add("Pinned")
                                                if (uiState.generalNotes.any { it.allAudioUrls.isNotEmpty() }) add("Voice Memos")
                                                if (uiState.generalNotes.any { it.isChecklist }) add("Checklists")
                                                if (uiState.generalNotes.any { it.reminderAt != null }) add("Reminders")
                                                if (uiState.allAnnotations.isNotEmpty()) add("Highlights")
                                            }
                                        }
                                        noteFilterOptions.forEach { option ->
                                            val active = when (option) {
                                                "All Notes" -> selectedGeneralNoteTag == "All"
                                                "Pinned" -> selectedGeneralNoteTag == "Pinned"
                                                "Voice Memos" -> selectedGeneralNoteTag == "Audio"
                                                "Checklists" -> selectedGeneralNoteTag == "Checklists"
                                                "Reminders" -> selectedGeneralNoteTag == "Reminders"
                                                "Highlights" -> selectedGeneralNoteTag == "Highlights"
                                                else -> selectedGeneralNoteTag == option
                                            }
                                            if (active) {
                                                Button(
                                                    onClick = {
                                                        onSelectedGeneralNoteTagChange(when (option) {
                                                            "All Notes" -> "All"
                                                            "Pinned" -> "Pinned"
                                                            "Voice Memos" -> "Audio"
                                                            "Checklists" -> "Checklists"
                                                            "Reminders" -> "Reminders"
                                                            "Highlights" -> "Highlights"
                                                            else -> option
                                                        })
                                                    },
                                                    shape = VeritasPackStyle.chipShape(),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                                                ) {
                                                    Text(option, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                                }
                                            } else {
                                                OutlinedButton(
                                                    onClick = {
                                                        onSelectedGeneralNoteTagChange(when (option) {
                                                            "All Notes" -> "All"
                                                            "Pinned" -> "Pinned"
                                                            "Voice Memos" -> "Audio"
                                                            "Checklists" -> "Checklists"
                                                            "Reminders" -> "Reminders"
                                                            "Highlights" -> "Highlights"
                                                            else -> option
                                                        })
                                                    },
                                                    shape = VeritasPackStyle.chipShape(),
                                                    border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                                                ) {
                                                    Text(option, style = MaterialTheme.typography.labelMedium)
                                                }
                                            }
                                        }
                                    }
                                }
                                VeritasHomeTab.STUDY -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "Study Hub",
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text("🔥", fontSize = 14.sp)
                                                val streak = uiState.readerTrackerSnapshot.currentStreak
                                                Text(
                                                    text = if (streak > 0) "$streak-Day Streak" else "0-Day Streak",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text("🔥", fontSize = 14.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
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
