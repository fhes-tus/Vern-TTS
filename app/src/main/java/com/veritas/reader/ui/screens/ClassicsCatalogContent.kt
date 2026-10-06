package com.veritas.reader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.*
import kotlinx.coroutines.launch

/** Embedded discovery shelves share Library's card and cover treatment, without dialog chrome. */
@Composable
fun ClassicsCatalogContent(
    documents: List<SavedDocument>,
    downloads: Map<String, ClassicDownloadState>,
    onDownload: (ClassicBookEntry) -> Unit,
    onCancel: (ClassicBookEntry) -> Unit,
    onOpen: (SavedDocument) -> Unit,
    onBrowse: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
    headerState: ClassicsHeaderState? = null
) {
    val floatingBottomPadding = LocalHomeBottomPadding.current
    val controls = headerState ?: rememberClassicsHeaderState()
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var genre by rememberSaveable { mutableStateOf("All") }
    var sort by rememberSaveable { mutableStateOf("Featured") }
    var previewId by rememberSaveable { mutableStateOf<String?>(null) }
    var showArchives by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val genres = remember { listOf("All") + CURATED_CLASSICS.map { it.genre }.distinct() }
    val filtered = remember(controls.query, genre, sort) {
        val result = CURATED_CLASSICS.filter {
            (genre == "All" || it.genre == genre) &&
                (controls.query.isBlank() || listOf(it.title, it.author, it.genre, it.description).any { value -> value.contains(controls.query.trim(), true) })
        }
        when (sort) {
            "Title" -> result.sortedBy { it.title }
            "Author" -> result.sortedBy { it.author }
            "Shortest" -> result.sortedBy { it.estimatedMinutes }
            else -> result
        }
    }
    fun state(book: ClassicBookEntry): ClassicDownloadState {
        if (findCatalogDocument(book, documents) != null) return ClassicDownloadState(ClassicDownloadPhase.AVAILABLE)
        // A completed work record is historical after a book is deleted.
        return catalogDownloadState(book, downloads)
    }
    LazyColumn(state = listState, modifier = modifier.fillMaxSize().testTag("classics_catalog"),
        contentPadding = PaddingValues(bottom = floatingBottomPadding + 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item("genres") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(genres, key = { it }) { value ->
                    FilterChip(genre == value, { genre = value }, label = { Text(value) }, shape = VeritasPackStyle.chipShape(),
                        colors = VeritasPackStyle.filterChipColors(MaterialTheme.colorScheme),
                        modifier = Modifier.testTag("classic_genre_$value"))
                }
            }
        }
        if (headerState == null) item("search") {
            Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                .onGloballyPositioned { com.veritas.reader.ui.OnboardingController.updateBounds("classics_shelves", it) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibrarySearchField(controls.query, { controls.query = it }, "Search books or authors", Modifier.weight(1f).testTag("classics_search"))
                IconButton({ focusManager.clearFocus(); controls.listMode = !controls.listMode; scope.launch { listState.scrollToItem(0) } },
                    Modifier.size(38.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).testTag("classics_view_mode")) {
                    Icon(if (controls.listMode) Icons.AutoMirrored.Filled.List else Icons.Filled.GridView,
                        if (controls.listMode) "Show horizontal shelves" else "Show vertical list",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item("sort") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${filtered.size} books", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                var expanded by remember { mutableStateOf(false) }
                Box {
                    TextButton({ expanded = true }, Modifier.testTag("classics_sort")) { Text("Sort: $sort") }
                    DropdownMenu(expanded, { expanded = false }) {
                        listOf("Featured", "Title", "Author", "Shortest").forEach { value ->
                            DropdownMenuItem({ Text(value) }, { sort = value; expanded = false })
                        }
                    }
                }
            }
        }
        if (controls.query.isBlank() && genre == "All" && sort == "Featured") {
            item("featured") {
                val book = getBookOfTheDay()
                LibraryBookSurface(Modifier.padding(horizontal = 16.dp).fillMaxWidth().testTag("classic_featured").clickable { previewId = book.id }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ClassicBookCover(book, width = 72.dp, height = 108.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Today’s pick", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(book.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${book.genre} · ~${book.estimatedMinutes} min", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        if (filtered.isEmpty()) item("empty") {
            LibraryBookSurface(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No books found", style = MaterialTheme.typography.titleMedium)
                    if (controls.query.isNotBlank()) {
                        Text("Search “${controls.query.trim()}” in book archives", style = MaterialTheme.typography.bodyMedium)
                        FREE_BOOK_SITES.forEach { site ->
                            OutlinedButton({ onBrowse(site.url, site.name, controls.query.trim()) }, Modifier.fillMaxWidth()
                                .testTag("classic_archive_${site.name}"), shape = VeritasPackStyle.chipShape()) {
                                Text("${site.icon} Search ${site.name}")
                            }
                        }
                    } else Text("Try another title, author or genre.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton({ controls.query = ""; genre = "All" }) { Text("Show all books") }
                }
            }
        }
        if (controls.listMode) {
            items(filtered, key = { "book_${it.id}" }) { book ->
                ClassicCatalogRow(book, state(book), { previewId = book.id }, {
                    val installed = findCatalogDocument(book, documents)
                    if (installed != null) onOpen(installed) else if (book.editions.isNotEmpty()) previewId = book.id else onDownload(book)
                }, { onCancel(book) })
            }
        } else {
        val shelves = if (genre == "All") filtered.groupBy { it.genre } else mapOf(genre to filtered)
        shelves.forEach { (name, books) ->
            item("shelf_$name") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${books.size} books", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.testTag("classic_shelf_$name")) {
                        items(books, key = { it.id }) { book ->
                            ClassicShelfCard(book, state(book), { previewId = book.id }, {
                                val installed = findCatalogDocument(book, documents)
                                if (installed != null) onOpen(installed) else if (book.editions.isNotEmpty()) previewId = book.id else onDownload(book)
                            }, { onCancel(book) })
                        }
                    }
                }
            }
        }
        }
        item("archives") {
            Column(Modifier.padding(horizontal = 16.dp)) {
                TextButton({ showArchives = !showArchives }) { Text(if (showArchives) "Hide book archives" else "Explore more book archives") }
                if (showArchives) FREE_BOOK_SITES.forEach { site ->
                    TextButton({ onBrowse(site.url, site.name, controls.query) }, Modifier.fillMaxWidth()) { Text(site.name) }
                }
            }
        }
    }
    CURATED_CLASSICS.firstOrNull { it.id == previewId }?.let { book ->
        ClassicBookDetailSheet(book, documents, state(book).busy,
            onDismiss = { previewId = null }, onDownloadBook = onDownload, onOpenBook = onOpen,
            downloadState = state(book), onCancelDownload = { onCancel(book) },
            editionDownloadStates = downloads, onCancelEdition = onCancel)
    }
}

@Composable
private fun ClassicShelfCard(book: ClassicBookEntry, state: ClassicDownloadState, onPreview: () -> Unit, onAction: () -> Unit, onCancel: () -> Unit) {
    LibraryBookSurface(Modifier.width(174.dp).testTag("classic_card_${book.id}")) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.clickable(onClickLabel = "Details for ${book.title}", onClick = onPreview)) {
                BookCoverStage(height = 144.dp) { ClassicBookCover(book, width = 80.dp, height = 120.dp) }
                Spacer(Modifier.height(6.dp))
                Text(book.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${book.author} · ~${book.estimatedMinutes} min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (state.busy) {
                Text(state.actionLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                if (state.percent != null) LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                TextButton(onCancel, Modifier.fillMaxWidth().heightIn(min = 40.dp)) { Text("Cancel") }
            } else {
                FilledTonalButton(onAction, Modifier.fillMaxWidth().heightIn(min = 40.dp), shape = VeritasPackStyle.chipShape(), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(if (state.phase == ClassicDownloadPhase.AVAILABLE || book.hasDirectTextDownload) state.actionLabel else "Visit website", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (state.phase == ClassicDownloadPhase.FAILED) Text("Download failed. Tap Retry.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ClassicCatalogRow(book: ClassicBookEntry, state: ClassicDownloadState,
    onPreview: () -> Unit, onAction: () -> Unit, onCancel: () -> Unit) {
    LibraryBookSurface(Modifier.padding(horizontal = 16.dp).fillMaxWidth().testTag("classic_row_${book.id}")) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.clickable(onClickLabel = "Details for ${book.title}", onClick = onPreview)) {
                ClassicBookCover(book, width = 64.dp, height = 96.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Column(Modifier.clickable(onClick = onPreview)) {
                    Text(book.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${book.author} · ${book.genre}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(book.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("~${book.estimatedMinutes} min", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                    FilledTonalButton(if (state.busy) onCancel else onAction, shape = VeritasPackStyle.chipShape(),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                        Text(if (state.busy) "Cancel" else if (state.phase == ClassicDownloadPhase.AVAILABLE || book.hasDirectTextDownload)
                            state.actionLabel else "Visit website", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (state.busy) {
                    Text(state.actionLabel, style = MaterialTheme.typography.labelSmall)
                    if (state.percent != null) LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (state.phase == ClassicDownloadPhase.FAILED)
                    Text("Download failed. Tap Retry.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
