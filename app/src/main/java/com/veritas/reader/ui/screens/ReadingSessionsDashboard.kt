package com.veritas.reader.ui.screens
import com.veritas.reader.VernAlertDialog as AlertDialog

import com.veritas.reader.vernScatteredCard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veritas.reader.VernDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.veritas.reader.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun ReadingSessionsDashboardDialog(snapshot: ReaderTrackerSnapshot, documents: List<SavedDocument>,
    readerSettings: ReaderSettings, onGoalMinutesChange: (Int) -> Unit, onDismiss: () -> Unit,
    onOpenDocument: (SavedDocument) -> Unit) {
    val context = LocalContext.current
    val repository = remember { DocumentRepository(context) }
    val tracker = remember { ReadingSessionTracker.get(context) }
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var edit by remember { mutableStateOf<ReadingSession?>(null) }
    var manual by remember { mutableStateOf(false) }
    var idle by remember { mutableIntStateOf(repository.prefs.getInt("reading_idle_seconds", 120)) }
    var explicit by remember { mutableStateOf(repository.prefs.getBoolean("reading_explicit_only", false)) }
    LaunchedEffect(idle) {
        kotlinx.coroutines.delay(300)
        kotlinx.coroutines.withContext(Dispatchers.IO) { repository.prefs.edit().putInt("reading_idle_seconds", idle).commit(); tracker.flush() }
    }
    val today = LocalDate.now()
    val todayKey = today.toString()
    val weekStart = today.minusDays(today.dayOfWeek.value.toLong() - 1)
    val weekKeys = List(7) { weekStart.plusDays(it.toLong()).toString() }
    val sessions = snapshot.sessions.filterNot { it.excluded }.filter { it.activeMillis > 0 }
    val locationSummaries = remember(snapshot.sessions) { readingLocationSummaries(snapshot.sessions) }
    val locationsByBook = remember(sessions, locationSummaries) {
        sessions.groupBy { it.documentId }.mapValues { (_, items) ->
            ReadingLocationSummary(items.sumOf { locationSummaries[it.id]?.firstVisitMillis ?: 0 },
                items.sumOf { locationSummaries[it.id]?.revisitMillis ?: 0 }, items.sumOf { locationSummaries[it.id]?.unknownMillis ?: 0 })
        }
    }
    var csvError by remember { mutableStateOf<String?>(null) }
    val sessionsByBook = remember(sessions) { sessions.groupBy { it.documentId } }
    val documentsById = remember(documents) { documents.associateBy { it.id } }
    val historyByDay = remember(snapshot.sessions) {
        snapshot.sessions.filter { it.measuredMillis > 0 }.sortedByDescending { it.startedAt }
            .groupBy { java.time.Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate() }
    }
    val overviewScroll = rememberLazyListState()
    val booksScroll = rememberLazyListState()
    val historyScroll = rememberLazyListState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val dialogView = LocalView.current
        val background = MaterialTheme.colorScheme.background
        val isLight = background.luminance() > 0.45f
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)?.window?.let { window ->
                window.setLayout(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                WindowCompat.getInsetsController(window, dialogView).apply {
                    isAppearanceLightStatusBars = isLight
                    isAppearanceLightNavigationBars = isLight
                }
            }
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier
                        .widthIn(max = 760.dp)
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_reading_insights), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_close_insights)) }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Overview", "Books", "History").forEachIndexed { index, label ->
                        FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(label) })
                    }
                }
                LazyColumn(Modifier.weight(1f).testTag("insights-content"), state = when (tab) { 0 -> overviewScroll; 1 -> booksScroll; else -> historyScroll }, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (tab == 0) {
                        item { InsightsStreaksCard(snapshot, weekKeys) }
                        item { InsightsWeeklyAppCard(snapshot, weekStart) }
                        item {
                            CalendarHeatMap(snapshot.activeDateKeys, readingMillisByDate = snapshot.readingMillisByDate,
                                summary = { periodKeys, periodLabel ->
                                    InsightsReadingRings(snapshot, weekKeys, periodKeys, periodLabel)
                                })
                        }
                    } else if (tab == 1) {
                        item(key = "books-summary") {
                            InsightsDetailCard(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_your_reading_shelf), Icons.AutoMirrored.Filled.MenuBook) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    CompactStat("${documents.size}", com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_books), Modifier.weight(1f))
                                    CompactStat("${documents.count { it.id in snapshot.completionDates }}", com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_finished), Modifier.weight(1f))
                                    CompactStat("${sessionsByBook.keys.count { it in documentsById }}", com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_with_activity), Modifier.weight(1f))
                                }
                                if (documents.isEmpty()) Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_add_a_book_to_start_reading))
                            }
                        }
                        items(documents, key = { "book-${it.id}" }) { doc ->
                            InsightsBookCard(doc, sessionsByBook[doc.id].orEmpty(), snapshot.completionDates[doc.id],
                                onContinue = { onDismiss(); onOpenDocument(doc) },
                                onTimer = { tracker.explicitTimer(doc.id); onDismiss(); onOpenDocument(doc) },
                                onFinish = { scope.launch(Dispatchers.IO) {
                                    repository.recordDocumentCompletion(doc.id, doc.title, System.currentTimeMillis()); tracker.flush()
                                } }, locationSummary = locationsByBook[doc.id])
                        }
                    } else {
                        item {
                            InsightsDetailCard(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_today), Icons.Default.AutoGraph) {
                                InsightsModeBars(snapshot.measuredReadingMillisByDate[todayKey] ?: 0,
                                    snapshot.listeningMillisByDate[todayKey] ?: 0, "today")
                                if ((snapshot.legacyReadingMillisByDate[todayKey] ?: 0) > 0) Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_173, com.veritas.reader.ui.screens.localizedUiValue(formatTrackerDuration(snapshot.legacyReadingMillisByDate.getValue(todayKey)))))
                                if (sessions.isEmpty()) Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_your_first_measured_session_will_appear_here_reading_stops_after))
                            }
                        }
                        item {
                            InsightsDetailCard(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_daily_goal), Icons.Default.Flag) {
                                val todayMillis = snapshot.readingMillisByDate[todayKey] ?: 0
                                val goal = readerSettings.dailyGoalMinutes
                                InsightsGoalRing(todayMillis, goal)
                                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    (listOf(0, 15, 30, 45, 60) + listOf(goal).filter { it > 0 }).distinct().sorted().forEach { minutes ->
                                        FilterChip(selected = goal == minutes, onClick = { onGoalMinutesChange(minutes) },
                                            label = { Text(if (minutes == 0) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_off) else "${minutes}m") })
                                    }
                                }
                            }
                        }
                        item {
                            InsightsDetailCard(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_reading_timer), Icons.Default.Timer) {
                                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_automatic_reading_measures_visible_reader_time_after_interaction))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_use_an_explicit_timer_only), modifier = Modifier.weight(1f))
                                    Switch(checked = explicit, onCheckedChange = { value ->
                                        explicit = value; scope.launch(Dispatchers.IO) { repository.prefs.edit().putBoolean("reading_explicit_only", value).commit(); tracker.flush() }
                                    })
                                }
                                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_174, com.veritas.reader.ui.screens.localizedUiValue(idle / 60), com.veritas.reader.ui.screens.localizedUiValue(idle % 60)))
                                VeritasRoundSlider(value = idle.toFloat(), onValueChange = { idle = it.toInt() }, valueRange = 30f..900f)
                                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_start_a_timer_from_books_it_runs_while_that_book_s_reader_is_visi), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { tracker.explicitTimer(null) }) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_stop_reading_timer)) }
                            }
                        }

                        item {
                            TextButton(onClick = { scope.launch {
                                try {
                                    val file = kotlinx.coroutines.withContext(Dispatchers.IO) {
                                        val folder = java.io.File(context.cacheDir, "VernExports").apply { mkdirs() }
                                        val output = java.io.File(folder, "reading_sessions_${java.util.UUID.randomUUID()}.csv")
                                        output.bufferedWriter(Charsets.UTF_8).use { writeReadingSessionsCsv(snapshot.sessions, it) }
                                        output
                                    }
                                    val uri = com.veritas.reader.SafeFileSharing.uri(context, "${context.packageName}.fileprovider", file)
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/csv")
                                        .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    intent.clipData = android.content.ClipData.newRawUri("Reading sessions", uri)
                                    context.startActivity(android.content.Intent.createChooser(intent, "Export reading history"))
                                } catch (error: Exception) { if (error is kotlinx.coroutines.CancellationException) throw error; csvError = error.message }
                            } }, enabled = snapshot.sessions.isNotEmpty()) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_export_sessions_csv)) }
                            csvError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                        item { TextButton(onClick = { manual = true }, enabled = documents.isNotEmpty()) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_add_manual_session)) } }
                        if (sessions.isEmpty()) item { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_no_measured_sessions_yet_earlier_aggregate_totals_remain_preserve)) }
                        historyByDay.forEach { (date, entries) ->
                            item(key = "history-date-$date") {
                                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.Timeline, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text(if (date == today) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_today_s_sessions) else date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")),
                                        style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                    Text("${entries.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            items(entries, key = { "session-${it.id}" }) { session ->
                                InsightsSessionCard(session, documentsById[session.documentId], onEdit = { edit = session })
                            }
                        }
                    }
                }
                if (com.veritas.reader.ui.OnboardingController.activeStep == com.veritas.reader.ui.OnboardingStep.INSIGHTS_PAGE_SPOTLIGHT) {
                    InsightsCard(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_reading_insights)) {
                        Text(com.veritas.reader.ui.OnboardingStep.INSIGHTS_PAGE_SPOTLIGHT.body)
                        Row {
                            TextButton(onClick = { onDismiss(); com.veritas.reader.ui.OnboardingController.activeStep = com.veritas.reader.ui.OnboardingStep.INSIGHTS_SPOTLIGHT }) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_back)) }
                            TextButton(onClick = { onDismiss(); com.veritas.reader.ui.OnboardingController.activeStep = com.veritas.reader.ui.OnboardingStep.NOTES_TAB_SPOTLIGHT }) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_next)) }
                        }
                    }
                }
            }
        }
    }
    }
    if (manual || edit != null) {
        SessionEditor(documents, edit, onDismiss = { manual = false; edit = null }, onReset = {
            val id = edit?.id
            scope.launch(Dispatchers.IO) { if (id != null) repository.correctReadingSession(id, null, false); tracker.flush() }
            manual = false; edit = null
        }, onSave = { doc, mode, start, duration, exclude ->
            val original = edit
            scope.launch(Dispatchers.IO) {
                if (original != null) repository.correctReadingSession(original.id, duration, exclude)
                else if (doc != null) repository.addManualReadingSession(doc, mode, start, duration)
                tracker.flush()
            }
            manual = false; edit = null
        })
    }
}

@Composable private fun InsightsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth().vernScatteredCard("insights-$title", amount = .4f), shape = VeritasPackStyle.cardShape(), border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable private fun SessionEditor(documents: List<SavedDocument>, session: ReadingSession?, onDismiss: () -> Unit,
    onReset: () -> Unit,
    onSave: (SavedDocument?, ReadingSessionMode, Long, Long, Boolean) -> Unit) {
    var book by remember { mutableStateOf(documents.firstOrNull { it.id == session?.documentId } ?: documents.firstOrNull()) }
    var menu by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(session?.mode ?: ReadingSessionMode.READING) }
    var minutes by remember { mutableStateOf(if (session == null) "20" else ((if (session.excluded) session.measuredMillis else session.activeMillis) / 60000.0).toString()) }
    var date by remember { mutableStateOf(session?.let { java.time.Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate().toString() } ?: LocalDate.now().toString()) }
    val duration = minutes.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 && it <= 1440 }?.let { (it * 60_000).toLong() }
    val start = runCatching { LocalDate.parse(date).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (session == null) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_manual_session) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_correct_duration)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (session == null) {
                Box { TextButton(onClick = { menu = true }) { Text(book?.title ?: com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_choose_book)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) { documents.forEach { doc -> DropdownMenuItem(
                        text = { Text(doc.title) }, onClick = { book = doc; menu = false }) } }
                }
                Row {
                    ReadingSessionMode.entries.forEach { value ->
                        FilterChip(mode == value, onClick = { mode = value }, label = {
                            Text(menuString(if (value == ReadingSessionMode.READING) com.veritas.reader.R.string.ui_reading
                                else com.veritas.reader.R.string.ui_listening))
                        })
                    }
                }
                OutlinedTextField(date, onValueChange = { date = it }, label = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_date_yyyy_mm_dd)) }, singleLine = true)
            } else Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_the_measured_duration_remains_in_the_record_this_correction_chang))
            OutlinedTextField(minutes, onValueChange = { minutes = it }, label = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_active_minutes)) }, singleLine = true)
            if (session != null) TextButton(onClick = { onSave(book, mode, session.startedAt, 0, true) }) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_exclude_this_session_from_totals)) }
            if (session != null && (session.correctedMillis != null || session.excluded)) TextButton(onClick = onReset) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_restore_original_measurement)) }
        }
    }, confirmButton = { TextButton(enabled = duration != null && start != null && (book != null || session != null), onClick = {
        onSave(book, mode, session?.startedAt ?: start!!, duration!!, false)
    }) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_save)) } }, dismissButton = { TextButton(onClick = onDismiss) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_cancel)) } })
}
