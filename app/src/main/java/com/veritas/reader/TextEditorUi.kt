package com.veritas.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

internal fun textEditorDownloadName(
    document: ReaderDocument,
    target: VeritasTextEditTarget
): String {
    val scope = when (target) {
        is VeritasTextEditTarget.Part -> target.label
        is VeritasTextEditTarget.SentenceRange -> target.label
    }
    val safeTitle = document.title
        .replace(Regex("[^A-Za-z0-9._ -]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(48)
        .ifBlank { "vern_text" }
    val safeScope = scope
        .replace(Regex("[^A-Za-z0-9._ -]"), " ")
        .replace(Regex("\\s+"), "_")
        .trim('_')
        .ifBlank { "edited" }
    return "${safeTitle}_$safeScope.txt"
}

internal fun countSearchOccurrences(source: String, query: String): Int {
    val needle = query.trim()
    if (source.isBlank() || needle.isBlank()) return 0
    var count = 0
    var cursor = 0
    while (cursor <= source.length - needle.length) {
        val found = source.indexOf(needle, startIndex = cursor, ignoreCase = true)
        if (found < 0) break
        count++
        cursor = found + needle.length.coerceAtLeast(1)
        if (count >= 500) break
    }
    return count
}

@Composable
internal fun TextEditorDialog(
    document: ReaderDocument,
    currentIndex: Int,
    text: String,
    target: VeritasTextEditTarget,
    onTextChange: (String) -> Unit,
    onSave: (partIndex: Int, text: String) -> Unit,
    onDownloadToPhone: () -> Unit,
    onDismiss: () -> Unit
) {
    val model = remember(document.id, document.rawText) {
        ReaderTextModelCache.get(document.id, document.rawText, document.pageCount)
    }
    val parts = remember(model) { model.parts }
    val totalPages = remember(parts) { parts.size.coerceAtLeast(1) }

    val initialPartIndex = remember(target, currentIndex, parts) {
        when (target) {
            is VeritasTextEditTarget.Part -> target.partIndex.coerceIn(0, totalPages - 1)
            is VeritasTextEditTarget.SentenceRange -> {
                val p = model.partForSentence(target.startSentenceIndex)
                p?.index?.coerceIn(0, totalPages - 1) ?: 0
            }
        }
    }

    var currentPartIndex by remember { mutableIntStateOf(initialPartIndex) }
    var showSearch by remember { mutableStateOf(false) }
    var showReplace by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }

    var editorValue by remember(currentPartIndex) {
        val initialText = if (currentPartIndex == initialPartIndex && text.isNotBlank()) {
            text
        } else {
            parts.getOrNull(currentPartIndex)?.text.orEmpty()
        }
        mutableStateOf(
            TextFieldValue(
                text = initialText,
                selection = TextRange(initialText.length)
            )
        )
    }

    var undoStack by remember(currentPartIndex) { mutableStateOf<List<TextFieldValue>>(emptyList()) }
    var redoStack by remember(currentPartIndex) { mutableStateOf<List<TextFieldValue>>(emptyList()) }

    fun commitValue(next: TextFieldValue) {
        if (next.text == editorValue.text && next.selection == editorValue.selection) {
            editorValue = next
            return
        }
        undoStack = (undoStack + editorValue).takeLast(80)
        redoStack = emptyList()
        editorValue = next
        onTextChange(next.text)
    }

    fun transformSelection(transform: (String) -> String) {
        val value = editorValue
        val start = value.selection.min.coerceIn(0, value.text.length)
        val end = value.selection.max.coerceIn(0, value.text.length)
        if (start == end) return
        val selected = value.text.substring(start, end)
        val transformed = transform(selected)
        val nextText = value.text.replaceRange(start, end, transformed)
        commitValue(TextFieldValue(nextText, selection = TextRange(start, start + transformed.length)))
    }

    fun wrapSelection(prefix: String, suffix: String = prefix, defaultPlaceholder: String = "text") {
        val value = editorValue
        val start = value.selection.min.coerceIn(0, value.text.length)
        val end = value.selection.max.coerceIn(0, value.text.length)
        if (start == end) {
            val inserted = "$prefix$defaultPlaceholder$suffix"
            val nextText = value.text.replaceRange(start, end, inserted)
            commitValue(TextFieldValue(nextText, selection = TextRange(start + prefix.length, start + prefix.length + defaultPlaceholder.length)))
        } else {
            val selected = value.text.substring(start, end)
            if (selected.startsWith(prefix) && selected.endsWith(suffix) && selected.length >= prefix.length + suffix.length) {
                val unwrapped = selected.removePrefix(prefix).removeSuffix(suffix)
                val nextText = value.text.replaceRange(start, end, unwrapped)
                commitValue(TextFieldValue(nextText, selection = TextRange(start, start + unwrapped.length)))
            } else {
                val wrapped = "$prefix$selected$suffix"
                val nextText = value.text.replaceRange(start, end, wrapped)
                commitValue(TextFieldValue(nextText, selection = TextRange(start, start + wrapped.length)))
            }
        }
    }

    fun toggleLinePrefix(prefix: String) {
        val value = editorValue
        val start = value.selection.min.coerceIn(0, value.text.length)
        val end = value.selection.max.coerceIn(0, value.text.length)
        val lineStart = value.text.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val lineEnd = value.text.indexOf('\n', end).let { if (it < 0) value.text.length else it }
        val chunk = value.text.substring(lineStart, lineEnd)
        val lines = chunk.lines()
        val allHavePrefix = lines.all { it.startsWith(prefix) }
        val transformed = lines.joinToString("\n") { line ->
            if (allHavePrefix) line.removePrefix(prefix) else "$prefix$line"
        }
        val nextText = value.text.replaceRange(lineStart, lineEnd, transformed)
        commitValue(TextFieldValue(nextText, selection = TextRange(lineStart, lineStart + transformed.length)))
    }

    fun applyBulletList() {
        val value = editorValue
        val start = value.selection.min.coerceIn(0, value.text.length)
        val end = value.selection.max.coerceIn(0, value.text.length)
        val selected = value.text.substring(start, end)
        val lines = if (selected.contains("\n")) selected.lines() else listOf(selected)
        val transformed = lines.joinToString("\n") { line ->
            if (line.isNotBlank()) {
                "• ${line.replace(Regex("^\\d+\\.\\s*|^[•\\-*]\\s*"), "")}"
            } else line
        }
        val nextText = value.text.replaceRange(start, end, transformed)
        commitValue(TextFieldValue(nextText, selection = TextRange(start, start + transformed.length)))
    }

    fun applyNumberedList() {
        val value = editorValue
        val start = value.selection.min.coerceIn(0, value.text.length)
        val end = value.selection.max.coerceIn(0, value.text.length)
        val selected = value.text.substring(start, end)
        val lines = if (selected.contains("\n")) selected.lines() else listOf(selected)
        var counter = 1
        val transformed = lines.joinToString("\n") { line ->
            if (line.isNotBlank()) {
                "${counter++}. ${line.replace(Regex("^\\d+\\.\\s*|^[•\\-*]\\s*"), "")}"
            } else line
        }
        val nextText = value.text.replaceRange(start, end, transformed)
        commitValue(TextFieldValue(nextText, selection = TextRange(start, start + transformed.length)))
    }

    fun applyIndent() {
        val value = editorValue
        val start = value.selection.min.coerceIn(0, value.text.length)
        val end = value.selection.max.coerceIn(0, value.text.length)
        val selected = value.text.substring(start, end)
        val transformed = if (selected.contains("\n")) {
            selected.lines().joinToString("\n") { if (it.isNotBlank()) "    $it" else it }
        } else {
            "    $selected"
        }
        val nextText = value.text.replaceRange(start, end, transformed)
        commitValue(TextFieldValue(nextText, selection = TextRange(start, start + transformed.length)))
    }

    fun cleanAndNormalizeText() {
        val clean = editorValue.text
            .replace(Regex("(\\w+)-\\n(\\w+)"), "$1$2")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        commitValue(TextFieldValue(clean, selection = TextRange(clean.length)))
    }

    fun findNextSearchMatch() {
        val needle = searchQuery.trim()
        if (needle.isBlank()) return
        val start = editorValue.selection.max.coerceIn(0, editorValue.text.length)
        val first = editorValue.text.indexOf(needle, startIndex = start, ignoreCase = true)
        val match = if (first >= 0) first else editorValue.text.indexOf(needle, ignoreCase = true)
        if (match >= 0) {
            editorValue = editorValue.copy(selection = TextRange(match, match + needle.length))
        }
    }

    fun replaceCurrentMatch() {
        val needle = searchQuery.trim()
        if (needle.isBlank()) return
        val value = editorValue
        val start = value.selection.min.coerceIn(0, value.text.length)
        val end = value.selection.max.coerceIn(0, value.text.length)
        val selected = value.text.substring(start, end)
        if (selected.equals(needle, ignoreCase = true)) {
            val nextText = value.text.replaceRange(start, end, replaceQuery)
            commitValue(TextFieldValue(nextText, selection = TextRange(start + replaceQuery.length)))
            findNextSearchMatch()
        } else {
            findNextSearchMatch()
        }
    }

    fun replaceAllMatches() {
        val needle = searchQuery.trim()
        if (needle.isBlank()) return
        val nextText = editorValue.text.replace(needle, replaceQuery, ignoreCase = true)
        commitValue(TextFieldValue(nextText, selection = TextRange(nextText.length)))
    }

    val wordCount = remember(editorValue.text) {
        editorValue.text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    }
    val charCount = editorValue.text.length
    val searchMatches = remember(editorValue.text, searchQuery) {
        countSearchOccurrences(editorValue.text, searchQuery)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Top App Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            "Edit extracted text",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            document.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { showSearch = !showSearch; if (showSearch) showReplace = false },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = "Search",
                            tint = if (showSearch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { showReplace = !showReplace; if (showReplace) showSearch = false },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Filled.FindReplace,
                            contentDescription = "Find & Replace",
                            tint = if (showReplace) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onDownloadToPhone,
                        enabled = editorValue.text.isNotBlank(),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = "Export Text", modifier = Modifier.size(20.dp))
                    }
                    Button(
                        onClick = {
                            val currentText = editorValue.text
                            onTextChange(currentText)
                            onSave(currentPartIndex, currentText)
                        },
                        enabled = editorValue.text.isNotBlank(),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save", style = MaterialTheme.typography.labelMedium)
                    }
                }

                // Per-Page Navigator Header
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                if (currentPartIndex > 0) {
                                    currentPartIndex--
                                }
                            },
                            enabled = currentPartIndex > 0
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous Page")
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "Page ${currentPartIndex + 1} of $totalPages",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (currentPartIndex < totalPages - 1) {
                                    currentPartIndex++
                                }
                            },
                            enabled = currentPartIndex < totalPages - 1
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next Page")
                        }
                    }
                }

                // Search Panel
                if (showSearch) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it.take(120) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("Find in page") },
                                shape = RoundedCornerShape(8.dp)
                            )
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(
                                    "$searchMatches found",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                            Button(
                                onClick = ::findNextSearchMatch,
                                enabled = searchMatches > 0,
                                shape = RoundedCornerShape(50)
                            ) { Text("Next") }
                        }
                    }
                }

                // Replace Panel
                if (showReplace) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it.take(120) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    label = { Text("Find") },
                                    shape = RoundedCornerShape(8.dp)
                                )
                                OutlinedTextField(
                                    value = replaceQuery,
                                    onValueChange = { replaceQuery = it.take(120) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    label = { Text("Replace with") },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = ::replaceCurrentMatch, enabled = searchQuery.isNotBlank()) {
                                    Text("Replace Next")
                                }
                                Button(onClick = ::replaceAllMatches, enabled = searchQuery.isNotBlank(), shape = RoundedCornerShape(50)) {
                                    Text("Replace All")
                                }
                            }
                        }
                    }
                }

                // Word count banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$wordCount words • $charCount characters",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Rich Text Editor Area
                val editorScrollState = rememberScrollState()
                BasicTextField(
                    value = editorValue,
                    onValueChange = { next -> commitValue(next) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = (MaterialTheme.typography.bodyLarge.fontSize.value + 8).sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.background)
                        .verticalScroll(editorScrollState)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Material 3 Rich Editing Toolbar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val previous = undoStack.lastOrNull() ?: return@IconButton
                                undoStack = undoStack.dropLast(1)
                                redoStack = (redoStack + editorValue).takeLast(80)
                                editorValue = previous
                                onTextChange(previous.text)
                            },
                            enabled = undoStack.isNotEmpty()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                        }

                        IconButton(
                            onClick = {
                                val next = redoStack.lastOrNull() ?: return@IconButton
                                redoStack = redoStack.dropLast(1)
                                undoStack = (undoStack + editorValue).takeLast(80)
                                editorValue = next
                                onTextChange(next.text)
                            },
                            enabled = redoStack.isNotEmpty()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )

                        // Bold
                        IconButton(onClick = { wrapSelection("**") }) {
                            Text("B", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        }

                        // Italic
                        IconButton(onClick = { wrapSelection("*") }) {
                            Text("I", style = MaterialTheme.typography.titleMedium, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontWeight = FontWeight.Bold)
                        }

                        // Strikethrough
                        IconButton(onClick = { wrapSelection("~~") }) {
                            Text("S", style = MaterialTheme.typography.titleMedium, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough, fontWeight = FontWeight.Bold)
                        }

                        // Inline code
                        IconButton(onClick = { wrapSelection("`") }) {
                            Text("<>", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )

                        // Headings
                        IconButton(onClick = { toggleLinePrefix("# ") }) {
                            Text("H1", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                        }

                        IconButton(onClick = { toggleLinePrefix("## ") }) {
                            Text("H2", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }

                        // Quote
                        IconButton(onClick = { toggleLinePrefix("> ") }) {
                            Text("❝", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )

                        // Lists & Indent
                        IconButton(onClick = ::applyBulletList) {
                            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Bulleted List")
                        }

                        IconButton(onClick = ::applyNumberedList) {
                            Icon(Icons.Filled.FormatListNumbered, contentDescription = "Numbered List")
                        }

                        IconButton(onClick = ::applyIndent) {
                            Icon(Icons.AutoMirrored.Filled.FormatIndentIncrease, contentDescription = "Indent")
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )

                        // Case toggles
                        IconButton(onClick = {
                            transformSelection { it.uppercase() }
                        }) {
                            Text("AA", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }

                        IconButton(onClick = {
                            transformSelection {
                                it.split(Regex("(?<=\\s)|(?=\\s)")).joinToString("") { word ->
                                    word.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() }
                                }
                            }
                        }) {
                            Text("Aa", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }

                        // Clean whitespace & formatting
                        IconButton(onClick = ::cleanAndNormalizeText) {
                            Icon(Icons.Filled.CleaningServices, contentDescription = "Clean whitespace & formatting")
                        }
                    }
                }
            }
        }
    }
}
