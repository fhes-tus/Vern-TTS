package com.veritas.reader.ui.screens

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veritas.reader.SavedDocument
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.blendColors
import com.veritas.reader.trackerDateKey
import com.veritas.reader.ui.OnboardingController
import com.veritas.reader.ui.OnboardingStep
import com.veritas.reader.ui.ReaderUiState
import java.util.Calendar
import kotlinx.coroutines.delay

@Composable
internal fun LibraryHomeTab(
    documents: List<SavedDocument>,
    uiState: ReaderUiState,
    continueDocument: SavedDocument?,
    homeListState: LazyListState,
    isHomeGridView: Boolean,
    onToggleHomeGridView: () -> Unit,
    onRefreshMainPage: () -> Unit,
    onOpenDocument: (SavedDocument) -> Unit,
    onClearContinueDocument: (SavedDocument) -> Unit,
    onShowImportSheet: () -> Unit,
    onOpenClassicsCatalog: () -> Unit,
    onDownloadClassicBook: (ClassicBookEntry) -> Unit,
    onCancelClassicBook: (ClassicBookEntry) -> Unit,
    onNavigateToTab: (VeritasHomeTab) -> Unit,
    onShowReadingStatsHome: () -> Unit,
    onGoalMinutesChange: (Int) -> Unit,
    isQueued: (SavedDocument) -> Boolean,
    onToggleFavorite: (SavedDocument) -> Unit,
    onToggleQueue: (SavedDocument) -> Unit,
    onMoveQueueUp: (SavedDocument) -> Unit,
    onMoveQueueDown: (SavedDocument) -> Unit,
    onSetCollection: (SavedDocument) -> Unit,
    onManageLists: (SavedDocument) -> Unit,
    onRenameDocument: (SavedDocument) -> Unit,
    onShowDetails: (SavedDocument) -> Unit,
    onDeleteDocument: (SavedDocument) -> Unit,
    modifier: Modifier = Modifier
) {
    val floatingBottomPadding = LocalHomeBottomPadding.current
    val recentBooks = remember(documents, continueDocument?.id) {
        documents.filterNot { it.id == continueDocument?.id }.sortedByDescending { it.updatedAt }.take(2)
    }
    val tracker = uiState.readerTrackerSnapshot
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val homeVisible = LocalLibraryPageVisible.current
    val context = LocalContext.current
    val greetingPrefs = remember(context) { context.getSharedPreferences("vern_home_greeting", android.content.Context.MODE_PRIVATE) }
    val previousVisit = remember { greetingPrefs.getLong("last_visit", 0L) }
    val welcomeBack = previousVisit > 0 && now - previousVisit >= 36 * 60 * 60 * 1000L
    LaunchedEffect(homeVisible) {
        if (!homeVisible) return@LaunchedEffect
        now = System.currentTimeMillis()
        greetingPrefs.edit().putLong("last_visit", now).apply()
        while (true) {
            delay(60_000)
            val timestamp = System.currentTimeMillis()
            val dateChanged = trackerDateKey(timestamp) != trackerDateKey(now)
            now = timestamp
            if (dateChanged) onRefreshMainPage()
        }
    }
    val todayKey = trackerDateKey(now)
    val weeklyBook = remember(todayKey) { getBookOfTheWeek() }
    var previewClassicBook by remember { mutableStateOf<ClassicBookEntry?>(null) }
    var showGoalPicker by remember { mutableStateOf(false) }
    val todayMillis = (tracker.readingMillisByDate[todayKey] ?: 0L).coerceAtLeast(0L)
    val weekReadingMillis = remember(todayKey, tracker.readingMillisByDate) {
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val dayIndex = (today.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val monday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -dayIndex) }
        List(7) { offset ->
            val day = (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            if (offset > dayIndex) 0L else
                (tracker.readingMillisByDate[trackerDateKey(day.timeInMillis)] ?: 0L).coerceAtLeast(0L)
        }
    }
    val readingDaysThisWeek = weekReadingMillis.count { it > 0L }
    val weeklyMillis = weekReadingMillis.sum()
    val goalMinutes = uiState.readerSettings.dailyGoalMinutes
    val hasRecordedActivity = tracker.readingMillisByDate.values.any { it > 0L } || tracker.activeDateKeys.isNotEmpty()
    val showTodayCard = goalMinutes > 0 || hasRecordedActivity
    val showWeeklyPick = documents.isNotEmpty() || uiState.hasCompletedOnboarding ||
        uiState.isHeroContinueDismissed || hasRecordedActivity

    val scheme = MaterialTheme.colorScheme
    val gradient: Brush
    val onCard: Color
    if (uiState.readerSettings.vibrantHero) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(scheme.primary.toArgb(), hsv)
        val first = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], (hsv[1] * 0.7f).coerceIn(0f, 1f), (hsv[2] * 1.15f).coerceIn(0f, 1f))))
        val second = Color(android.graphics.Color.HSVToColor(floatArrayOf((hsv[0] + 15f) % 360f, hsv[1], (hsv[2] * 0.85f).coerceIn(0f, 1f))))
        gradient = Brush.linearGradient(listOf(first, second))
        onCard = if ((first.luminance() + second.luminance()) / 2 > 0.4f) Color(0xFF0F172A) else Color.White
    } else {
        gradient = Brush.linearGradient(listOf(blendColors(scheme.surfaceContainer, scheme.primary, 0.10f), scheme.surface))
        onCard = scheme.onSurface
    }

    val classicsIndex = 3 + (if (recentBooks.isNotEmpty()) 1 else 0)
    LaunchedEffect(OnboardingController.activeStep, classicsIndex) {
        if (OnboardingController.activeStep == OnboardingStep.CLASSICS_SPOTLIGHT) homeListState.animateScrollToItem(classicsIndex)
    }
    var lastRefreshAt by remember { mutableLongStateOf(0L) }
    LazyColumn(
        modifier = modifier.fillMaxSize().pointerInput(homeListState) {
            var pull = 0f
            detectVerticalDragGestures(
                onDragStart = { pull = 0f },
                onVerticalDrag = { _, distance ->
                    if (homeListState.firstVisibleItemIndex == 0 && homeListState.firstVisibleItemScrollOffset == 0 && distance > 0) pull += distance
                },
                onDragEnd = {
                    val timestamp = System.currentTimeMillis()
                    if (pull > 96.dp.toPx() && timestamp - lastRefreshAt > 1200L) {
                        lastRefreshAt = timestamp
                        onRefreshMainPage()
                    }
                    pull = 0f
                },
                onDragCancel = { pull = 0f }
            )
        }.padding(horizontal = 18.dp),
        state = homeListState,
        contentPadding = PaddingValues(top = 12.dp, bottom = floatingBottomPadding + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "greeting") {
            Text(homeGreeting(uiState.userName, Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY), welcomeBack),
                style = MaterialTheme.typography.titleMedium, color = scheme.onSurface,
                modifier = Modifier.padding(top = 2.dp, bottom = 2.dp))
        }
        item(key = "continue") {
            Column(Modifier.staggeredEntrance(1)) {
                VeritasHomeHeroCard(continueDocument, weeklyBook.takeIf { showWeeklyPick }, gradient, onCard,
                    onOpenDocument, onClearContinueDocument, onShowImportSheet,
                    onPreviewClassic = { previewClassicBook = it })
            }
        }
        if (showTodayCard) item(key = "today") {
            HomeTodayReadingCard(todayMillis, goalMinutes, readingDaysThisWeek,
                onShowReadingStatsHome, onGoalMinutesChange, Modifier.staggeredEntrance(2), weeklyMillis, weekReadingMillis)
        } else item(key = "goal-prompt") {
            TextButton(onClick = { showGoalPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Set a daily reading goal")
            }
        }
        if (recentBooks.isNotEmpty()) {
            item(key = "recent-books") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent books", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
                    IconButton(onClick = onToggleHomeGridView) {
                        Icon(if (isHomeGridView) Icons.AutoMirrored.Filled.List else Icons.Filled.GridView,
                            if (isHomeGridView) "Switch to list view" else "Switch to grid view",
                            tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    TextButton(onClick = { onNavigateToTab(VeritasHomeTab.LIBRARY) }) { Text("See all →") }
                }
                if (isHomeGridView) Row(Modifier.staggeredEntrance(3).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    recentBooks.forEach { doc -> HomeRecentBookGridItem(doc, { onOpenDocument(doc) }, Modifier.weight(1f)) }
                    if (recentBooks.size == 1) Spacer(Modifier.weight(1f))
                } else Column(Modifier.staggeredEntrance(3).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    recentBooks.forEach { doc ->
                        RecentImportItem(document = doc, isQueued = isQueued(doc), onOpen = { onOpenDocument(doc) },
                            onToggleFavorite = { onToggleFavorite(doc) }, onToggleQueue = { onToggleQueue(doc) },
                            onMoveQueueUp = { onMoveQueueUp(doc) }, onMoveQueueDown = { onMoveQueueDown(doc) },
                            onSetCollection = { onSetCollection(doc) }, onManageLists = { onManageLists(doc) },
                            onRename = { onRenameDocument(doc) }, onShowDetails = { onShowDetails(doc) }, onDelete = { onDeleteDocument(doc) })
                    }
                }
            }
            }
        }
        item(key = "classics") {
            Card(Modifier.staggeredEntrance(4).fillMaxWidth()
                .onGloballyPositioned { OnboardingController.updateBounds("classics_catalog_card", it) },
                shape = VeritasPackStyle.cardShape(), colors = CardDefaults.cardColors(containerColor = VeritasPackStyle.panelColor(scheme)),
                border = VeritasPackStyle.cardBorder(scheme)) {
                HomeActionRow(Icons.AutoMirrored.Filled.LibraryBooks, "Explore the classics", "Find your next read",
                    scheme.tertiaryContainer, scheme.onTertiaryContainer, onOpenClassicsCatalog)
            }
        }
        if (documents.isNotEmpty() || showWeeklyPick) item(key = "add") {
            TextButton(onClick = onShowImportSheet, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, null, Modifier.size(20.dp))
                Text("Add a book or document", Modifier.padding(start = 8.dp))
            }
        }
    }
    if (showGoalPicker) HomeReadingGoalDialog(goalMinutes, onGoalMinutesChange) { showGoalPicker = false }
    previewClassicBook?.let { book ->
        ClassicBookDetailSheet(
            book = book,
            existingDocuments = documents,
            editionDownloadStates = uiState.classicDownloads,
            downloadState = uiState.classicDownloads[book.id] ?: com.veritas.reader.ClassicDownloadState(),
            onDismiss = { previewClassicBook = null },
            onDownloadBook = onDownloadClassicBook,
            onCancelDownload = { onCancelClassicBook(book) },
            onCancelEdition = onCancelClassicBook,
            onOpenBook = { doc ->
                previewClassicBook = null
                onOpenDocument(doc)
            }
        )
    }
}
