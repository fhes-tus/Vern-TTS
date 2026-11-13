package com.veritas.reader.ui.screens

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.veritas.reader.SavedDocument
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.trackerDateKey
import com.veritas.reader.ui.OnboardingController
import com.veritas.reader.ui.OnboardingStep
import com.veritas.reader.ui.ReaderUiState
import java.util.Calendar
import kotlinx.coroutines.delay

/**
 * Responsive Home layout thresholds derived from minimum comfortable card widths:
 * - Featured Hero Card minimum width: 360.dp
 * - Compact Weekly Reading / Insights Card minimum width: 320.dp
 * - Inter-column gap: 16.dp
 * - Minimum horizontal outer gutters: 2 * 22.dp = 44.dp
 * Total two-column usable width threshold = 360 + 320 + 16 + 44 = 740.dp.
 * (Accommodates Samsung Galaxy A17 in landscape at ~800-860.dp usable width after cutout insets.)
 */
private val HOME_MIN_HERO_CARD_WIDTH = 360.dp
private val HOME_MIN_INSIGHTS_CARD_WIDTH = 320.dp
private val HOME_TWO_COLUMN_GAP = 16.dp
private val HOME_WIDE_MIN_GUTTER = 22.dp
internal val HOME_TWO_COLUMN_BREAKPOINT: Dp =
    HOME_MIN_HERO_CARD_WIDTH + HOME_MIN_INSIGHTS_CARD_WIDTH + HOME_TWO_COLUMN_GAP + (HOME_WIDE_MIN_GUTTER * 2)
private val HOME_TWO_COLUMN_MAX_CONTENT_WIDTH = 1080.dp
private val HOME_PORTRAIT_PHONE_THRESHOLD = 480.dp
private val HOME_SINGLE_COLUMN_LANDSCAPE_MAX_WIDTH = 640.dp

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
    val heroColors = com.veritas.reader.veritasHeroColors(
        scheme,
        uiState.readerSettings.vibrantHero,
        preserveAccent = com.veritas.reader.normalizeCustomAccent(uiState.readerSettings.customAccentColor).isNotEmpty() &&
                !uiState.readerSettings.themeId.contains("high_contrast")
    )

    var lastRefreshAt by remember { mutableLongStateOf(0L) }
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val horizontalSafeInset = safeDrawingPadding.calculateLeftPadding(layoutDirection) +
            safeDrawingPadding.calculateRightPadding(layoutDirection)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Measure actual usable window width in dp (accounting for any horizontal cutout/navigation insets)
        val usableWidth = (maxWidth - horizontalSafeInset).coerceAtLeast(320.dp)
        val useTwoColumnFeatured = usableWidth >= HOME_TWO_COLUMN_BREAKPOINT

        // Derive bounded content width and horizontal gutters:
        // 1. Portrait phones (<= 480.dp): standard 18.dp horizontal padding.
        // 2. Narrower landscape / small tablet fallback (480.dp .. < 740.dp): one balanced, centered
        //    landscape-width column (~88% of usable width, capped at 640.dp, min 20.dp side gutters).
        // 3. Wide / Galaxy A17 landscape & tablets (>= 740.dp): two-column featured layout bounded to 1080.dp max width.
        val targetContentMaxWidth = when {
            useTwoColumnFeatured -> HOME_TWO_COLUMN_MAX_CONTENT_WIDTH
            usableWidth > HOME_PORTRAIT_PHONE_THRESHOLD ->
                (usableWidth * 0.88f).coerceAtMost(HOME_SINGLE_COLUMN_LANDSCAPE_MAX_WIDTH)
            else -> usableWidth
        }
        val baseGutter = when {
            useTwoColumnFeatured -> HOME_WIDE_MIN_GUTTER
            usableWidth > HOME_PORTRAIT_PHONE_THRESHOLD ->
                ((usableWidth - targetContentMaxWidth) / 2f).coerceAtLeast(20.dp)
            else -> 18.dp
        }

        val classicsIndex = (if (useTwoColumnFeatured) 2 else 3) + (if (recentBooks.isNotEmpty()) 1 else 0)
        LaunchedEffect(OnboardingController.activeStep, classicsIndex) {
            if (OnboardingController.activeStep == OnboardingStep.CLASSICS_SPOTLIGHT) {
                homeListState.animateScrollToItem(classicsIndex)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(homeListState) {
                    var pull = 0f
                    detectVerticalDragGestures(
                        onDragStart = { pull = 0f },
                        onVerticalDrag = { _, distance ->
                            if (homeListState.firstVisibleItemIndex == 0 && homeListState.firstVisibleItemScrollOffset == 0 && distance > 0) {
                                pull += distance
                            }
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
                }
                .padding(horizontal = baseGutter),
            state = homeListState,
            contentPadding = PaddingValues(top = 12.dp, bottom = floatingBottomPadding + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "greeting") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = targetContentMaxWidth)
                ) {
                    Text(
                        localizedHomeGreeting(
                            uiState.userName,
                            Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY),
                            welcomeBack
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.onSurface,
                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                    )
                }
            }

            if (useTwoColumnFeatured) {
                // Wide layout (including Galaxy A17 landscape & tablets):
                // Two-column top section with Hero Card alongside compact Insights Card.
                // Both cards size naturally from Alignment.Top without forced equal height inflation.
                item(key = "continue") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = targetContentMaxWidth),
                        horizontalArrangement = Arrangement.spacedBy(HOME_TWO_COLUMN_GAP),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(0.54f)
                                .staggeredEntrance(1)
                        ) {
                            VeritasHomeHeroCard(
                                continueDocument,
                                weeklyBook.takeIf { showWeeklyPick },
                                heroColors,
                                onOpenDocument,
                                onClearContinueDocument,
                                onShowImportSheet,
                                onPreviewClassic = { previewClassicBook = it }
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(0.46f)
                                .staggeredEntrance(2)
                        ) {
                            if (showTodayCard) {
                                HomeTodayReadingCard(
                                    todayMillis = todayMillis,
                                    goalMinutes = goalMinutes,
                                    readingDaysThisWeek = readingDaysThisWeek,
                                    onOpenInsights = onShowReadingStatsHome,
                                    onGoalMinutesChange = onGoalMinutesChange,
                                    weeklyMillis = weeklyMillis,
                                    weekReadingMillis = weekReadingMillis
                                )
                            } else {
                                TextButton(
                                    onClick = { showGoalPicker = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_set_a_daily_reading_goal))
                                }
                            }
                        }
                    }
                }
            } else {
                // Single-column layout (portrait phones & balanced centered narrower landscape fallback)
                item(key = "continue") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = targetContentMaxWidth)
                            .staggeredEntrance(1)
                    ) {
                        VeritasHomeHeroCard(
                            continueDocument,
                            weeklyBook.takeIf { showWeeklyPick },
                            heroColors,
                            onOpenDocument,
                            onClearContinueDocument,
                            onShowImportSheet,
                            onPreviewClassic = { previewClassicBook = it }
                        )
                    }
                }
                if (showTodayCard) {
                    item(key = "today") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = targetContentMaxWidth)
                        ) {
                            HomeTodayReadingCard(
                                todayMillis,
                                goalMinutes,
                                readingDaysThisWeek,
                                onShowReadingStatsHome,
                                onGoalMinutesChange,
                                Modifier.staggeredEntrance(2),
                                weeklyMillis,
                                weekReadingMillis
                            )
                        }
                    }
                } else {
                    item(key = "goal-prompt") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = targetContentMaxWidth)
                        ) {
                            TextButton(
                                onClick = { showGoalPicker = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_set_a_daily_reading_goal))
                            }
                        }
                    }
                }
            }

            if (recentBooks.isNotEmpty()) {
                item(key = "recent-books") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = targetContentMaxWidth),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_recent_books),
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                color = scheme.onSurface
                            )
                            IconButton(onClick = onToggleHomeGridView) {
                                Icon(
                                    if (isHomeGridView) Icons.AutoMirrored.Filled.List else Icons.Filled.GridView,
                                    if (isHomeGridView) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_switch_to_list_view)
                                    else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_switch_to_grid_view),
                                    tint = scheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            TextButton(onClick = { onNavigateToTab(VeritasHomeTab.LIBRARY) }) {
                                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_see_all))
                            }
                        }
                        if (isHomeGridView) {
                            Row(
                                Modifier
                                    .staggeredEntrance(3)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                recentBooks.forEach { doc ->
                                    HomeRecentBookGridItem(doc, { onOpenDocument(doc) }, Modifier.weight(1f))
                                }
                                if (recentBooks.size == 1) Spacer(Modifier.weight(1f))
                            }
                        } else {
                            Column(
                                Modifier
                                    .staggeredEntrance(3)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                recentBooks.forEach { doc ->
                                    RecentImportItem(
                                        document = doc,
                                        isQueued = isQueued(doc),
                                        onOpen = { onOpenDocument(doc) },
                                        onToggleFavorite = { onToggleFavorite(doc) },
                                        onToggleQueue = { onToggleQueue(doc) },
                                        onMoveQueueUp = { onMoveQueueUp(doc) },
                                        onMoveQueueDown = { onMoveQueueDown(doc) },
                                        onSetCollection = { onSetCollection(doc) },
                                        onManageLists = { onManageLists(doc) },
                                        onRename = { onRenameDocument(doc) },
                                        onShowDetails = { onShowDetails(doc) },
                                        onDelete = { onDeleteDocument(doc) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "classics") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = targetContentMaxWidth)
                ) {
                    Card(
                        Modifier
                            .staggeredEntrance(4)
                            .fillMaxWidth()
                            .onGloballyPositioned { OnboardingController.updateBounds("classics_catalog_card", it) },
                        shape = VeritasPackStyle.cardShape(),
                        colors = CardDefaults.cardColors(containerColor = VeritasPackStyle.panelColor(scheme)),
                        border = VeritasPackStyle.cardBorder(scheme)
                    ) {
                        HomeActionRow(
                            Icons.AutoMirrored.Filled.LibraryBooks,
                            com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_explore_the_classics),
                            com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_find_your_next_read),
                            scheme.tertiaryContainer,
                            scheme.onTertiaryContainer,
                            onOpenClassicsCatalog
                        )
                    }
                }
            }

            if (documents.isNotEmpty() || showWeeklyPick) {
                item(key = "add") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = targetContentMaxWidth)
                    ) {
                        TextButton(onClick = onShowImportSheet, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Add, null, Modifier.size(20.dp))
                            Text(
                                com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_add_a_book_or_document),
                                Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
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