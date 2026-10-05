package com.veritas.reader.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veritas.reader.SavedDocument
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.blendColors
import com.veritas.reader.ui.OnboardingController
import com.veritas.reader.ui.OnboardingStep
import com.veritas.reader.ui.ReaderUiState
import java.util.Locale


@Composable
internal fun LibraryHomeTab(
    documents: List<SavedDocument>,
    uiState: ReaderUiState,
    currentStreak: Int,
    continueDocument: SavedDocument?,
    homeListState: LazyListState,
    isHomeGridView: Boolean,
    onToggleHomeGridView: () -> Unit,
    onRefreshMainPage: () -> Unit,
    onOpenDocument: (SavedDocument) -> Unit,
    onPlayPauseContinue: (SavedDocument) -> Unit,
    onClearContinueDocument: (SavedDocument) -> Unit,
    onShowImportSheet: () -> Unit,
    onOpenClassicsCatalog: () -> Unit,
    onNavigateToTab: (VeritasHomeTab) -> Unit,
    onSetSourceFilter: (String) -> Unit,
    onShowReadingStatsHome: () -> Unit,
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
    onDownloadClassicBook: (ClassicBookEntry) -> Unit = {},
    onCancelClassicBook: (ClassicBookEntry) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val floatingBottomPadding = LocalHomeBottomPadding.current

    var previewClassicBook by remember { mutableStateOf<ClassicBookEntry?>(null) }
    var lastMainPageRefreshAt by remember { mutableLongStateOf(0L) }
    val context = LocalContext.current
    remember { context.getSharedPreferences("veritas_library_settings", Context.MODE_PRIVATE) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val pastedColor = MaterialTheme.colorScheme.inversePrimary
    val slidesColor = blendColors(secondaryColor, errorColor, 0.5f)

    val formatSlices = remember(documents, primaryColor, secondaryColor, tertiaryColor, errorColor, pastedColor, slidesColor) {
        var pdfCount = 0
        var webCount = 0
        var ebookCount = 0
        var slidesCount = 0
        var docCount = 0
        var pastedCount = 0
        documents.forEach { doc ->
            val mime = doc.originalMimeType.lowercase(Locale.US)
            val title = doc.title.lowercase(Locale.US)
            val label = doc.sourceLabel.lowercase(Locale.US)
            when {
                label == "pdf" || mime.contains("pdf") || title.endsWith(".pdf") -> pdfCount++
                label.contains("web") || label.contains("http") || label.contains("article") ||
                    mime.contains("html") -> webCount++
                label == "epub" || mime.contains("epub") || title.endsWith(".epub") -> ebookCount++
                label == "pptx" || mime.contains("presentationml") || title.endsWith(".pptx") -> slidesCount++
                label in setOf("docx", "txt", "ocr") ||
                    mime.contains("word") || mime.contains("wordprocessingml") ||
                    mime.startsWith("image/") ||
                    title.endsWith(".docx") || title.endsWith(".txt") -> docCount++
                else -> pastedCount++
            }
        }
        listOf(
            DonutSlice(
                label = "PDF Documents",
                value = pdfCount.toFloat(),
                color = primaryColor,
                description = "PDFs imported from local file storage or other directories."
            ),
            DonutSlice(
                label = "Web Articles",
                value = webCount.toFloat(),
                color = secondaryColor,
                description = "Online articles, blogs, and papers saved via URL import."
            ),
            DonutSlice(
                label = "E-Books",
                value = ebookCount.toFloat(),
                color = tertiaryColor,
                description = "EPUB e-books imported into your library."
            ),
            DonutSlice(
                label = "Slide Decks",
                value = slidesCount.toFloat(),
                color = slidesColor,
                description = "PowerPoint presentations read slide by slide."
            ),
            DonutSlice(
                label = "Documents",
                value = docCount.toFloat(),
                color = errorColor,
                description = "Word documents, text files, and scanned (OCR) documents."
            ),
            DonutSlice(
                label = "Pasted Text",
                value = pastedCount.toFloat(),
                color = pastedColor,
                description = "Text pasted directly into the reader interface."
            )
        ).filter { it.value > 0f }
    }

    val timeSlices = remember(documents, uiState.documentReadingTimes, primaryColor, secondaryColor, tertiaryColor, errorColor) {
        val docTimes = documents.mapNotNull { doc ->
            val readingTime = uiState.documentReadingTimes[doc.id] ?: 0L
            if (readingTime > 0L) doc to readingTime else null
        }.sortedByDescending { it.second }

        docTimes.sumOf { it.second }

        if (docTimes.isEmpty()) {
            emptyList()
        } else {
            val top4 = docTimes.take(4)
            val othersTime = if (docTimes.size > 4) docTimes.drop(4).sumOf { it.second } else 0L
            
            val colors = listOf(
                primaryColor,
                secondaryColor,
                tertiaryColor,
                errorColor,
                Color.Gray
            )
            
            val list = mutableListOf<DonutSlice>()
            top4.forEachIndexed { idx, (doc, time) ->
                list.add(
                    DonutSlice(
                        label = doc.title,
                        value = time.toFloat() / 60000f,
                        color = colors[idx % colors.size],
                        description = "You spent ${time / 60000} minutes reading this document."
                    )
                )
            }
            if (othersTime > 0L) {
                list.add(
                    DonutSlice(
                        label = "Others",
                        value = othersTime.toFloat() / 60000f,
                        color = colors[4],
                        description = "All other documents combined account for ${othersTime / 60000} minutes."
                    )
                )
            }
            list
        }
    }
    val welcomeName = uiState.userName.trim().ifBlank { "Reader" }
    val (_, _) = when {
        documents.isEmpty() -> {
            "Welcome to Veritas." to "Add your first reading to get started."
        }
        currentStreak >= 2 -> {
            val subMsg = if (currentStreak >= 7) "You're on a roll!" else "Keep it going!"
            "$welcomeName, you're on a $currentStreak-day streak 🔥" to subMsg
        }
        else -> {
            "Welcome back, $welcomeName." to "Pick up where you left off."
        }
    }


                                val recentImports = remember(documents) {
                                    documents.sortedByDescending { maxOf(it.createdAt, it.updatedAt) }.take(4)
                                }

                                LaunchedEffect(OnboardingController.activeStep, recentImports.isNotEmpty()) {
                                    if (OnboardingController.activeStep == OnboardingStep.CLASSICS_SPOTLIGHT) {
                                        val targetIndex = if (recentImports.isNotEmpty()) 4 else 2
                                        homeListState.animateScrollToItem(targetIndex)
                                    }
                                }
                                val tracker = uiState.readerTrackerSnapshot
                                val weeklyMinutes = tracker.weeklyUsageMillis / 60000L

                                // Two user-selectable hero styles (Settings → Vibrant hero card):
                                // subtle (default) uses container tones so the card reads as a
                                // large themed surface in every palette; vibrant is the original
                                // accent-derived HSV "poster" gradient. Both track the active
                                // color scheme, so adaptive cover keeps working in either mode.
                                val heroScheme = MaterialTheme.colorScheme
                                val streakGradient: Brush
                                val streakOnCard: Color
                                if (uiState.readerSettings.vibrantHero) {
                                    val streakPrimary = heroScheme.primary
                                    val streakHsl = FloatArray(3)
                                    android.graphics.Color.colorToHSV(streakPrimary.toArgb(), streakHsl)
                                    val color1 = Color(android.graphics.Color.HSVToColor(floatArrayOf(streakHsl[0], (streakHsl[1] * 0.7f).coerceIn(0f, 1f), (streakHsl[2] * 1.15f).coerceIn(0f, 1f))))
                                    val color2 = Color(android.graphics.Color.HSVToColor(floatArrayOf((streakHsl[0] + 15f) % 360f, streakHsl[1].coerceIn(0f, 1f), (streakHsl[2] * 0.85f).coerceIn(0f, 1f))))
                                    streakGradient = Brush.linearGradient(listOf(color1, color2))
                                    val avgLuminance = (color1.luminance() + color2.luminance()) / 2f
                                    streakOnCard = if (avgLuminance > 0.40f) Color(0xFF0F172A) else Color.White
                                } else {
                                    streakGradient = Brush.linearGradient(
                                        listOf(
                                            heroScheme.primaryContainer,
                                            blendColors(heroScheme.primaryContainer, heroScheme.surface, 0.55f)
                                        )
                                    )
                                    streakOnCard = heroScheme.onPrimaryContainer
                                }

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pointerInput(homeListState) {
                                            var pullDistance = 0f
                                            detectVerticalDragGestures(
                                                onDragStart = { pullDistance = 0f },
                                                onVerticalDrag = { _, dragAmount ->
                                                    val atTop = homeListState.firstVisibleItemIndex == 0 && homeListState.firstVisibleItemScrollOffset == 0
                                                    if (atTop && dragAmount > 0f) pullDistance += dragAmount
                                                },
                                                onDragEnd = {
                                                    val now = System.currentTimeMillis()
                                                    if (pullDistance > 120f && now - lastMainPageRefreshAt > 1500L) {
                                                        lastMainPageRefreshAt = now
                                                        onRefreshMainPage()
                                                    }
                                                    pullDistance = 0f
                                                },
                                                onDragCancel = { pullDistance = 0f }
                                            )
                                        }
                                        .padding(horizontal = 18.dp),
                                    state = homeListState,
                                    contentPadding = PaddingValues(top = 10.dp, bottom = floatingBottomPadding + 22.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // 1. Hero Card: continue-reading-first, playback-aware,
                                    // with data-earned rotating insight copy.
                                    item {
                                        val todayMinutes = remember(tracker.weeklyUsageByDay) {
                                            val idx = (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
                                            (tracker.weeklyUsageByDay.getOrNull(idx) ?: 0L) / 60000L
                                        }
                                        Box(modifier = Modifier.staggeredEntrance(1)) {
                                        VeritasHomeHeroCard(
                                            tracker = tracker,
                                            continueDocument = continueDocument,
                                            gradient = streakGradient,
                                            onCardColor = streakOnCard,
                                            weeklyMinutes = weeklyMinutes,
                                            todayMinutes = todayMinutes,
                                            dailyGoalMinutes = uiState.readerSettings.dailyGoalMinutes,
                                            onOpen = { onOpenDocument(it) },
                                            onPlayPause = { onPlayPauseContinue(it) },
                                            onClear = { onClearContinueDocument(it) },
                                            onAddContent = { onShowImportSheet() },
                                            onPreviewClassic = { previewClassicBook = it },
                                            onDownloadAndOpenClassic = { book ->
                                                val installed = com.veritas.reader.findCatalogDocument(book, documents)
                                                if (installed != null) {
                                                    onOpenDocument(installed)
                                                } else if (book.editions.isNotEmpty()) {
                                                    previewClassicBook = book
                                                } else {
                                                    onDownloadClassicBook(book)
                                                }
                                            },
                                            onShowDetails = { onShowDetails(it) }
                                        )
                                        }
                                    }

                                    // 3. Recent in Library (Supports Grid and Flat List view modes)
                                    if (recentImports.isNotEmpty()) {
                                        item {
                                            Row(
                                                modifier = Modifier.staggeredEntrance(2).fillMaxWidth().padding(top = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Recent in Library",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    IconButton(
                                                        onClick = {
                                                            onToggleHomeGridView()
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isHomeGridView) Icons.Filled.GridView else Icons.AutoMirrored.Filled.List,
                                                            contentDescription = if (isHomeGridView) "Switch to list view" else "Switch to grid view",
                                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = "See all →",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .clickable { onNavigateToTab(VeritasHomeTab.LIBRARY) }
                                                    )
                                                }
                                            }
                                        }
                                        if (isHomeGridView) {
                                            item {
                                                Column(
                                                    modifier = Modifier.staggeredEntrance(3).fillMaxWidth(),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    recentImports.chunked(2).forEach { row ->
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                        ) {
                                                            row.forEach { doc ->
                                                                HomeRecentBookGridItem(
                                                                    document = doc,
                                                                    onOpen = { onOpenDocument(doc) },
                                                                    modifier = Modifier.weight(1f)
                                                                )
                                                            }
                                                            if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                                                        }
                                                    }
                                                }
                                            }
                                        } else {
                                            item {
                                                Column(
                                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                                    modifier = Modifier.staggeredEntrance(3).fillMaxWidth()
                                                ) {
                                                    recentImports.forEach { doc ->
                                                        val isQueued = isQueued(doc)
                                                        RecentImportItem(
                                                            document = doc,
                                                            isQueued = isQueued,
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

                                    // 4. Continue Reading now lives inside the hero card above.

                                    // 5. Add Content card (No section heading, updated styling)
                                    item {
                                        Card(
                                            modifier = Modifier.staggeredEntrance(4).fillMaxWidth(),
                                            shape = VeritasPackStyle.cardShape(),
                                            colors = CardDefaults.cardColors(containerColor = com.veritas.reader.VeritasPackStyle.panelColor(MaterialTheme.colorScheme)),
                                            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
                                        ) {
                                            Box {
                                                HomeActionRow(
                                                    icon = Icons.Filled.Add,
                                                    title = "Add content",
                                                    body = "Import file - Upload PDF, EPUB, DOCX, PPTX, TXT, or HTML",
                                                    iconBackground = MaterialTheme.colorScheme.primaryContainer,
                                                    iconForeground = MaterialTheme.colorScheme.primary,
                                                    onClick = { onShowImportSheet() }
                                                )
                                            }
                                        }
                                    }

                                    // 5b. Explore Classic Books card
                                    item {
                                        Card(
                                            modifier = Modifier
                                                .staggeredEntrance(4)
                                                .fillMaxWidth()
                                                .padding(top = 8.dp)
                                                .onGloballyPositioned { OnboardingController.updateBounds("classics_catalog_card", it) },
                                            shape = VeritasPackStyle.cardShape(),
                                            colors = CardDefaults.cardColors(containerColor = com.veritas.reader.VeritasPackStyle.panelColor(MaterialTheme.colorScheme)),
                                            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
                                        ) {
                                            HomeActionRow(
                                                icon = Icons.AutoMirrored.Filled.LibraryBooks,
                                                title = "Explore Classic Books",
                                                body = "Free public domain classics - Meditations, Art of War & more",
                                                iconBackground = MaterialTheme.colorScheme.tertiaryContainer,
                                                iconForeground = MaterialTheme.colorScheme.tertiary,
                                                onClick = onOpenClassicsCatalog
                                            )
                                        }
                                    }

                                    // 6. Side-by-side Animated Donut Charts
                                    item {
                                        val displayTimeSlices = if (timeSlices.isEmpty()) {
                                            listOf(
                                                DonutSlice(
                                                    label = "No reading yet",
                                                    value = 0f,
                                                    color = Color.Gray.copy(alpha = 0.4f),
                                                    description = "Start reading to track your time allocation."
                                                )
                                            )
                                        } else {
                                            timeSlices
                                        }

                                        Row(
                                            modifier = Modifier.staggeredEntrance(5).fillMaxWidth().padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            DashboardDonutChart(
                                                title = "Library Source Distribution",
                                                slices = formatSlices,
                                                totalLabel = "Readings",
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    onNavigateToTab(VeritasHomeTab.LIBRARY)
                                                },
                                                onSliceClick = { slice ->
                                                    val mappedSource = when (slice.label) {
                                                        "PDF Documents" -> "PDF"
                                                        "Web Articles" -> "Web"
                                                        "E-Books" -> "EPUB"
                                                        "Slide Decks" -> "PPTX"
                                                        "Documents" -> "DOCX"
                                                        else -> "All"
                                                    }
                                                    onSetSourceFilter(mappedSource)
                                                    onNavigateToTab(VeritasHomeTab.LIBRARY)
                                                }
                                            )
                                            DashboardDonutChart(
                                                title = "Time Allocation — This Month",
                                                slices = displayTimeSlices,
                                                totalLabel = "Minutes",
                                                modifier = Modifier.weight(1f),
                                                onClick = {
                                                    onShowReadingStatsHome()
                                                },
                                                onSliceClick = {
                                                    onShowReadingStatsHome()
                                                }
                                            )
                                        }
                                    }
                                }

    previewClassicBook?.let { book ->
        ClassicBookDetailSheet(
            book = book,
            existingDocuments = documents,
            editionDownloadStates = uiState.classicDownloads,
            onCancelEdition = onCancelClassicBook,
            downloadState = if (com.veritas.reader.findCatalogDocument(book, documents) != null) com.veritas.reader.ClassicDownloadState(com.veritas.reader.ClassicDownloadPhase.AVAILABLE) else uiState.classicDownloads[book.id]?.takeUnless { it.phase == com.veritas.reader.ClassicDownloadPhase.AVAILABLE } ?: com.veritas.reader.ClassicDownloadState(),
            onCancelDownload = { onCancelClassicBook(book) },
            onDismiss = { previewClassicBook = null },
            onDownloadBook = { b ->
                onDownloadClassicBook(b)
            },
            onOpenBook = { doc ->
                previewClassicBook = null
                onOpenDocument(doc)
            }
        )
    }
}
