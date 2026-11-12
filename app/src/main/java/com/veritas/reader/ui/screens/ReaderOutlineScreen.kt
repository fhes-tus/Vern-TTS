package com.veritas.reader.ui.screens

import android.animation.ValueAnimator
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.VernDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.veritas.reader.VeritasThemeState
import kotlin.math.hypot
import kotlin.math.max

private fun View.windowToScreen(): Offset {
    val screen = IntArray(2)
    val window = IntArray(2)
    getLocationOnScreen(screen)
    getLocationInWindow(window)
    return Offset((screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat())
}

/** Capture screen coordinates so the reveal also works in an edge-to-edge dialog window. */
internal fun Modifier.outlineRevealAnchor(onPositioned: (Offset) -> Unit): Modifier = composed {
    val view = LocalView.current
    onGloballyPositioned { onPositioned(it.boundsInWindow().center + view.windowToScreen()) }
}

internal data class OutlineLink(val title: String, val detail: String, val searchText: String = detail)

/** One overlay shared by original documents, extracted text and the native PDF activity. */
@Composable
internal fun ReaderOutlineScreen(
    title: String,
    documentTitle: String,
    entries: List<SmartOutlineEntry>,
    currentIndex: Int,
    pageCount: Int,
    onJumpToDestination: (Int?, Int) -> Unit,
    onDismiss: () -> Unit,
    origin: Offset = Offset.Unspecified,
    currentPage: Int? = null,
    sentenceForPage: (Int) -> Int = { currentIndex },
    loading: Boolean = false,
    reduceMotion: Boolean = VeritasThemeState.reduceMotion,
    links: List<OutlineLink> = emptyList(),
    showLinks: Boolean = false,
    onOpenLink: (Int) -> Unit = {}
) {
    var query by rememberSaveable { mutableStateOf("") }
    var jumpPage by rememberSaveable { mutableStateOf("") }
    var linkTab by rememberSaveable { mutableStateOf(false) }
    var collapsed by remember { mutableStateOf(emptySet<Int>()) }
    val rows = remember(entries) { buildOutlineHierarchy(entries.map { it.level }) }
    val branches = remember(rows) { rows.filter { it.hasChildren }.map { it.entryIndex }.toSet() }
    val matches = remember(entries, query) {
        query.trim().takeIf { it.isNotEmpty() }?.let { needle ->
            entries.indices.filter { index ->
                val entry = entries[index]
                entry.title.contains(needle, true) || entry.preview.contains(needle, true) ||
                    entry.pageNumber?.toString() == needle || (entry.index + 1).toString() == needle
            }.toSet()
        }
    }
    val visibleRows = remember(rows, collapsed, matches) { visibleOutlineRows(rows, collapsed, matches) }
    val activeIndex = remember(entries, currentIndex, currentPage) {
        entries.indexOfLast { entry ->
            if (currentPage != null && entry.pageNumber != null) entry.pageNumber <= currentPage
            else entry.index <= currentIndex
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(entries) {
        if (activeIndex >= 0) listState.scrollToItem(activeIndex.coerceAtMost((entries.size - 1).coerceAtLeast(0)))
    }
    val motionOff = reduceMotion || !ValueAnimator.areAnimatorsEnabled()
    val reveal = remember { Animatable(if (motionOff) 1f else 0f) }
    var afterClose by remember { mutableStateOf<(() -> Unit)?>(null) }
    val closing = afterClose != null
    fun close(action: () -> Unit) { if (!closing) afterClose = action }
    LaunchedEffect(closing) {
        if (closing) {
            reveal.animateTo(0f, tween(if (motionOff) 0 else 220, easing = FastOutSlowInEasing))
            afterClose?.invoke()
        } else {
            reveal.animateTo(1f, tween(if (motionOff) 0 else 420, easing = FastOutSlowInEasing))
        }
    }

    Dialog(
        onDismissRequest = { close(onDismiss) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val view = LocalView.current
        var screenPosition by remember { mutableStateOf(Offset.Zero) }
        val lightBars = MaterialTheme.colorScheme.surface.luminance() > 0.45f
        SideEffect {
            (view.parent as? DialogWindowProvider)?.window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                val bars = androidx.core.view.WindowCompat.getInsetsController(this, view)
                bars.isAppearanceLightStatusBars = lightBars
                bars.isAppearanceLightNavigationBars = lightBars
                setDimAmount(0f)
                setWindowAnimations(0)
            }
        }
        BackHandler { close(onDismiss) }
        Surface(
            modifier = Modifier.fillMaxSize()
                .onGloballyPositioned { screenPosition = it.positionInWindow() + view.windowToScreen() }
                .drawWithContent {
                    if (reveal.value >= 1f || motionOff) drawContent()
                    else {
                        val center = if (origin != Offset.Unspecified) origin - screenPosition else Offset(size.width - 32.dp.toPx(), 48.dp.toPx())
                        val farX = max(center.x, size.width - center.x)
                        val farY = max(center.y, size.height - center.y)
                        val radius = 20.dp.toPx() + hypot(farX, farY) * reveal.value
                        val path = Path().apply { addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)) }
                        clipPath(path) { this@drawWithContent.drawContent() }
                    }
                }
                .testTag("reader-outline-screen")
                .semantics { paneTitle = title },
            color = MaterialTheme.colorScheme.surface
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            val headerLimit = maxHeight * .55f
            Column(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().heightIn(max = headerLimit).verticalScroll(rememberScrollState()).testTag("outline-header")) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { close(onDismiss) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_back_to_reader)) }
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        Text(documentTitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { close(onDismiss) }) { Icon(Icons.Filled.Close, com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_close_outline)) }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(if (linkTab) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_search_links) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_filter_outline)) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_clear_filter)) } })
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val requestedPage = jumpPage.toIntOrNull()?.takeIf { it in 1..pageCount }
                        OutlinedTextField(jumpPage, { jumpPage = it.filter(Char::isDigit).take(6) }, Modifier.weight(1f), singleLine = true,
                            label = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_145, com.veritas.reader.ui.screens.localizedUiValue(pageCount))) },
                            isError = jumpPage.isNotEmpty() && requestedPage == null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        TextButton(enabled = requestedPage != null && !closing, onClick = {
                            requestedPage?.let { page -> close { onJumpToDestination(page, sentenceForPage(page)) } }
                        }) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_go)) }
                    }
                }
                if (showLinks) {
                    Row(Modifier.padding(horizontal = 16.dp)) {
                        TextButton(onClick = { linkTab = false }) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_146, com.veritas.reader.ui.screens.localizedUiValue(entries.size)), fontWeight = if (!linkTab) FontWeight.Bold else FontWeight.Normal) }
                        TextButton(onClick = { linkTab = true }) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_147, com.veritas.reader.ui.screens.localizedUiValue(links.size)), fontWeight = if (linkTab) FontWeight.Bold else FontWeight.Normal) }
                    }
                }
                if (!linkTab) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (matches != null) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_148, com.veritas.reader.ui.screens.localizedUiValue(matches.size)) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_149, com.veritas.reader.ui.screens.localizedUiValue(entries.size)),
                            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (branches.isNotEmpty()) {
                            TextButton(onClick = { collapsed = emptySet() }, enabled = query.isBlank() && collapsed.isNotEmpty()) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_expand_all)) }
                            TextButton(onClick = { collapsed = branches }, enabled = query.isBlank() && collapsed != branches) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_collapse_all)) }
                        }
                    }
                }
                }
                HorizontalDivider()
                if (loading) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_building_outline)) }
                } else if (linkTab) {
                    val filteredLinks = links.withIndex().filter { (_, link) ->
                        query.isBlank() || link.title.contains(query.trim(), true) || link.searchText.contains(query.trim(), true)
                    }
                    LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(16.dp)) {
                        if (filteredLinks.isEmpty()) item { Text(if (links.isEmpty()) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_no_hyperlinks_detected_in_this_document) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_no_matching_links_found)) }
                        items(filteredLinks, key = { it.index }) { (index, link) ->
                            Column(Modifier.fillMaxWidth().clickable(enabled = !closing) { close { onOpenLink(index) } }.padding(vertical = 14.dp)) {
                                Text(link.title, style = MaterialTheme.typography.bodyLarge)
                                Text(link.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("outline-entries"), state = listState,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        if (visibleRows.isEmpty()) item { Text(if (entries.isEmpty()) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_no_outline_is_available_for_this_document) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_no_outline_matches), Modifier.padding(16.dp)) }
                        items(visibleRows, key = { it.entryIndex }) { row ->
                            val entry = entries[row.entryIndex]
                            val active = row.entryIndex == activeIndex
                            val expanded = matches != null || row.entryIndex !in collapsed
                            val ink = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            Row(
                                Modifier.fillMaxWidth().padding(start = (row.depth.coerceAtMost(5) * 16).dp)
                                    .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                                    .testTag("outline-row-${row.entryIndex}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (row.hasChildren) {
                                    IconButton(onClick = {
                                        collapsed = if (expanded) collapsed + row.entryIndex else collapsed - row.entryIndex
                                    }, enabled = matches == null && !closing,
                                        modifier = Modifier.semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }) {
                                        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                            "${if (expanded) "Collapse" else "Expand"} ${entry.title}", tint = ink)
                                    }
                                } else Spacer(Modifier.width(48.dp))
                                Row(Modifier.weight(1f).clickable(enabled = !closing) { close { onJumpToDestination(entry.pageNumber, entry.index) } }
                                    .heightIn(min = 56.dp).padding(vertical = 12.dp, horizontal = 8.dp)
                                    .semantics { if (active) stateDescription = "Current section" },
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(entry.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (row.hasChildren || active) FontWeight.SemiBold else FontWeight.Normal,
                                        color = ink)
                                    Text(entry.pageNumber?.let { "p. $it" } ?: "§ ${entry.index + 1}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (active) ink else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
}
