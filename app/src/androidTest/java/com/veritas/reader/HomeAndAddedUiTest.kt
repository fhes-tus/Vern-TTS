package com.veritas.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.veritas.reader.ui.ReaderUiState
import com.veritas.reader.ui.screens.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeAndAddedUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun weeklyStatisticUsesRecordedDaysWithoutRepeatingTheHeadline() {
        rule.setContent {
            MaterialTheme {
                HomeTodayReadingCard(0, 30, 2, {}, {}, weeklyMillis = 180_000,
                    weekReadingMillis = listOf(60_000, 0, 120_000, 0, 0, 0, 0))
            }
        }
        rule.waitForIdle()
        val initialHeight = rule.onNodeWithTag("home-today-reading-card").fetchSemanticsNode().boundsInRoot.height
        rule.waitUntil(15_000) { rule.onAllNodesWithText("This week").fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        rule.onNodeWithText("This week").assertIsDisplayed()
        rule.onNodeWithText("2 days").assertIsDisplayed()
        rule.onNodeWithText("2 active days").assertDoesNotExist()
        rule.onNodeWithContentDescription("Reading days, Monday to Sunday: read, no reading, read, no reading, no reading, no reading, no reading").assertIsDisplayed()
        rule.mainClock.autoAdvance = false
        repeat(2) {
            val actions = rule.onNode(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsActions.CustomActions))
                .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
            rule.runOnIdle { actions.first { it.label == "Next reading statistic" }.action() }
            rule.mainClock.advanceTimeBy(700)
        }
        rule.onNodeWithContentDescription("Reading minutes, Monday to Sunday: 1, 0, 2, 0, 0, 0, 0").assertIsDisplayed()
        rule.mainClock.autoAdvance = true
        assertEquals(initialHeight, rule.onNodeWithTag("home-today-reading-card").fetchSemanticsNode().boundsInRoot.height, 1f)
    }

    @Test fun readingGoalActionsFitWithLargeTextInNarrowWindow() {
        var opened = false
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.5f)) {
                MaterialTheme {
                    Column(Modifier.width(320.dp)) {
                        HomeTodayReadingCard(0, 30, 0, { opened = true }, {})
                    }
                }
            }
        }
        rule.onNodeWithText("Edit goal").assertIsDisplayed()
        rule.onNodeWithText("View insights →").assertIsDisplayed().performClick()
        assertEquals(true, opened)
    }

    @Test fun homeGoalCanBeSetChangedAndDisabled() {
        var goal by mutableIntStateOf(0)
        var insightsOpened = false
        rule.setContent {
            MaterialTheme {
                HomeTodayReadingCard(60_000, goal, 1, { insightsOpened = true }, { goal = it })
            }
        }
        rule.onNodeWithText("Set a goal").performClick()
        rule.onNodeWithText("15 minutes a day").performClick()
        rule.onNodeWithText("1 min").assertExists()
        rule.onNodeWithText("15 min target").assertExists()
        rule.onNodeWithText("Edit goal").performClick()
        rule.onNodeWithText("30 minutes a day").performClick()
        assertEquals(30, goal)
        rule.onNodeWithText("Edit goal").performClick()
        rule.onNodeWithText("No goal").performClick()
        rule.onNodeWithText("Your reading today").assertExists()
        assertEquals(0, goal)
        rule.onNodeWithText("View insights →").performClick()
        assertEquals(true, insightsOpened)
    }

    @Test fun addedSearchCanBeEditedAndClearedWithLargeText() {
        var query by mutableStateOf("")
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.5f)) {
                MaterialTheme {
                    Column(Modifier.width(320.dp)) {
                        LibrarySearchField(query, { query = it }, "Search added books", Modifier.fillMaxWidth())
                    }
                }
            }
        }
        rule.onNode(hasSetTextAction()).performTextInput("A very long book title")
        assertEquals("A very long book title", query)
        rule.onNodeWithContentDescription("Clear search").assertIsDisplayed().performClick()
        assertEquals("", query)
        rule.onNodeWithText("Search added books").assertIsDisplayed()
    }

    @Test fun addedResetRestoresBooksFromCombinedFilters() {
        var query by mutableStateOf("missing")
        var status by mutableStateOf("Favorites")
        var source by mutableStateOf("PDF")
        var collection by mutableStateOf("Study")
        var readingList by mutableStateOf("missing-list")
        val book = SavedDocument("polish-test", "A book without a cover", "missing.txt", "TXT", 1, 1, 0, 10, 100, "")
        rule.setContent {
            MaterialTheme {
                LibraryBooksTab(
                    documents = listOf(book), queuedDocuments = emptyList(), uiState = ReaderUiState(),
                    libraryListState = rememberLazyListState(), libraryQuery = query, onLibraryQueryChange = { query = it },
                    statusFilter = status, onStatusFilterChange = { status = it },
                    sourceFilter = source, onSourceFilterChange = { source = it },
                    collectionFilter = collection, onCollectionFilterChange = { collection = it },
                    readingListFilter = readingList, onReadingListFilterChange = { readingList = it },
                    sortMode = "Newest", onSortModeChange = {}, columnCount = 2, libraryViewMode = LibraryViewMode.LIST,
                    onLibraryViewModeChange = {}, selectedDocumentIds = emptySet(), onSelectedDocumentIdsChange = {},
                    onBatchFavoriteDocuments = {}, onBatchQueueDocuments = {}, onShowBatchCollectionDialog = {}, onConfirmBatchDelete = {},
                    onOpenDocument = {}, onDeleteDocument = {}, onToggleQueue = {}, onMoveQueueUp = {}, onMoveQueueDown = {},
                    onToggleFavorite = {}, onRenameDocument = {}, onSetCollection = {}, onShowDetails = {}, onManageLists = {},
                    onImportFile = {}, onOpenReadingLists = {}, onOpenReadingHistory = {}, onSearchLibraryContent = {},
                    onRefreshMainPage = {}, isQueued = { false }
                )
            }
        }
        rule.onNodeWithText("0 of 1 items").assertExists()
        rule.onNodeWithText("No results for “missing”").assertExists()
        rule.onNodeWithText("Reset").performClick()
        rule.onNodeWithText("1 saved item").assertExists()
        rule.onNodeWithText(book.title).assertExists()
        assertEquals(listOf("", "All", "All", "All", "All"), listOf(query, status, source, collection, readingList))
    }
}
