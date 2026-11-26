package com.veritas.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.veritas.reader.ui.screens.RichTextVisualTransformation
import com.veritas.reader.ui.screens.VeritasNoteEditing
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.VernDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import com.veritas.reader.ui.VeritasMotion

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
    isSaving: Boolean,
    saveError: String?,
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

    var currentPartIndex by rememberSaveable(initialPartIndex) { mutableIntStateOf(initialPartIndex) }
    val originalText = remember(model, target) {
        when (target) {
            is VeritasTextEditTarget.Part -> parts.getOrNull(initialPartIndex)?.text.orEmpty()
            is VeritasTextEditTarget.SentenceRange -> document.chunks
                .subList(target.startSentenceIndex, target.endSentenceIndexExclusive).joinToString("\n\n")
        }
    }
    val editorFocus = remember { FocusRequester() }
    val underlineLabel = com.veritas.reader.ui.screens.menuString(R.string.l10n_underline)
    var showSearch by remember { mutableStateOf(false) }
    var showReplace by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceQuery by remember { mutableStateOf("") }

    var editorValue by rememberSaveable(currentPartIndex, stateSaver = TextFieldValue.Saver) {
        val initialText = if (currentPartIndex == initialPartIndex) {
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

    val canClearSelection = when (target) {
        is VeritasTextEditTarget.Part -> parts.getOrNull(currentPartIndex)?.let {
            it.sentenceStartIndex > 0 || it.sentenceEndIndexExclusive < model.sentences.size
        } == true
        is VeritasTextEditTarget.SentenceRange -> target.startSentenceIndex > 0 || target.endSentenceIndexExclusive < model.sentences.size
    }
    val originalPart = if (currentPartIndex == initialPartIndex) originalText else parts.getOrNull(currentPartIndex)?.text.orEmpty()
    var pendingPart by remember { mutableStateOf<Int?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    fun selectPart(index: Int) {
        currentPartIndex = index
        onTextChange(if (index == initialPartIndex) originalText else parts.getOrNull(index)?.text.orEmpty())
    }
    fun closeEditor() {
        if (isSaving) return
        if (editorValue.text != originalPart) confirmClose = true else onDismiss()
    }
    pendingPart?.let { index ->
        EditorDiscardDialog(onDiscard = { pendingPart = null; selectPart(index) }, onKeep = { pendingPart = null })
    }
    if (confirmClose) EditorDiscardDialog(onDiscard = onDismiss, onKeep = { confirmClose = false })

    var undoStack by remember(currentPartIndex) { mutableStateOf<List<TextFieldValue>>(emptyList()) }
    var redoStack by remember(currentPartIndex) { mutableStateOf<List<TextFieldValue>>(emptyList()) }

    fun commitValue(next: TextFieldValue) {
        if (isSaving) return
        if (next.text == editorValue.text) {
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

    fun format(action: (TextFieldValue) -> TextFieldValue) {
        commitValue(action(editorValue))
        editorFocus.requestFocus()
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
        onDismissRequest = ::closeEditor,
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
                    IconButton(onClick = ::closeEditor, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_back))
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_edit_extracted_text),
                            style = MaterialTheme.typography.titleMedium,
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
                            contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_search),
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
                            contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_find_replace),
                            tint = if (showReplace) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onDownloadToPhone,
                        enabled = !isSaving && editorValue.text.isNotBlank(),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_export_text), modifier = Modifier.size(20.dp))
                    }
                    Button(
                        onClick = {
                            val currentText = editorValue.text
                            onTextChange(currentText)
                            onSave(currentPartIndex, currentText)
                        },
                        enabled = !isSaving && editorValue.text != originalPart && (editorValue.text.isNotBlank() || canClearSelection),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_save), style = MaterialTheme.typography.labelMedium)
                    }
                }

                saveError?.let { message ->
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite })
                }

                // Browse sections only when the editor was opened for a section.
                if (target is VeritasTextEditTarget.Part) Card(
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
                                    if (editorValue.text == originalPart) selectPart(currentPartIndex - 1)
                                    else pendingPart = currentPartIndex - 1
                                }
                            },
                            enabled = !isSaving && currentPartIndex > 0
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_previous_page))
                        }

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_057, com.veritas.reader.ui.screens.localizedUiValue(currentPartIndex + 1), com.veritas.reader.ui.screens.localizedUiValue(totalPages)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (currentPartIndex < totalPages - 1) {
                                    if (editorValue.text == originalPart) selectPart(currentPartIndex + 1)
                                    else pendingPart = currentPartIndex + 1
                                }
                            },
                            enabled = !isSaving && currentPartIndex < totalPages - 1
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_next_page))
                        }
                    }
                }

                // Search Panel
                AnimatedVisibility(
                    visible = showSearch,
                    enter = fadeIn(VeritasMotion.effectsFast()) + expandVertically(VeritasMotion.spatialFast(), expandFrom = Alignment.Top),
                    exit = fadeOut(VeritasMotion.effectsFast()) + shrinkVertically(VeritasMotion.spatialFast(), shrinkTowards = Alignment.Top)
                ) {
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
                                enabled = showSearch,
                                onValueChange = { searchQuery = it.take(120) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_find_in_page)) },
                                shape = RoundedCornerShape(8.dp)
                            )
                            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text(
                                    com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_058, com.veritas.reader.ui.screens.localizedUiValue(searchMatches)),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                            Button(
                                onClick = ::findNextSearchMatch,
                                enabled = showSearch && searchMatches > 0,
                                shape = RoundedCornerShape(50)
                            ) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_next)) }
                        }
                    }
                }

                // Replace Panel
                AnimatedVisibility(
                    visible = showReplace,
                    enter = fadeIn(VeritasMotion.effectsFast()) + expandVertically(VeritasMotion.spatialFast(), expandFrom = Alignment.Top),
                    exit = fadeOut(VeritasMotion.effectsFast()) + shrinkVertically(VeritasMotion.spatialFast(), shrinkTowards = Alignment.Top)
                ) {
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
                                    enabled = showReplace,
                                    onValueChange = { searchQuery = it.take(120) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    label = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_find)) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                                OutlinedTextField(
                                    value = replaceQuery,
                                    enabled = showReplace,
                                    onValueChange = { replaceQuery = it.take(120) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    label = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_replace_with)) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = ::replaceCurrentMatch, enabled = showReplace && searchQuery.isNotBlank()) {
                                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_replace_next))
                                }
                                Button(onClick = ::replaceAllMatches, enabled = showReplace && searchQuery.isNotBlank(), shape = RoundedCornerShape(50)) {
                                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_replace_all))
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
                        com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_059, com.veritas.reader.ui.screens.localizedUiValue(wordCount), com.veritas.reader.ui.screens.localizedUiValue(charCount)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Rich Text Editor Area
                val editorScrollState = rememberScrollState()
                val editorColor = MaterialTheme.colorScheme.onBackground
                val editorTransformation = remember(editorColor) { RichTextVisualTransformation(editorColor) }
                BasicTextField(
                    value = editorValue,
                    visualTransformation = editorTransformation,
                    readOnly = isSaving,
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
                        .focusRequester(editorFocus)
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
                            enabled = !isSaving && undoStack.isNotEmpty()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_undo))
                        }

                        IconButton(
                            onClick = {
                                val next = redoStack.lastOrNull() ?: return@IconButton
                                redoStack = redoStack.dropLast(1)
                                undoStack = (undoStack + editorValue).takeLast(80)
                                editorValue = next
                                onTextChange(next.text)
                            },
                            enabled = !isSaving && redoStack.isNotEmpty()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_redo))
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )

                        // Bold
                        IconButton(onClick = { format { VeritasNoteEditing.toggleInlineMarker(it, "**") } }) {
                            Text("B", style = MaterialTheme.typography.titleMedium)
                        }

                        // Italic
                        IconButton(onClick = { format { VeritasNoteEditing.toggleInlineMarker(it, "*") } }) {
                            Text("I", style = MaterialTheme.typography.titleMedium, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                        }

                        IconButton(onClick = { format { VeritasNoteEditing.toggleInlineMarker(it, "__") } }) {
                            Text("U", modifier = Modifier.semantics {
                                contentDescription = underlineLabel
                            }, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                        }

                        // Strikethrough
                        IconButton(onClick = { format { VeritasNoteEditing.toggleInlineMarker(it, "~~") } }) {
                            Text("S", style = MaterialTheme.typography.titleMedium, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                        }

                        // Inline code
                        IconButton(onClick = { format { VeritasNoteEditing.toggleInlineMarker(it, "`") } }) {
                            Text("<>", style = MaterialTheme.typography.labelMedium)
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )

                        // Headings
                        IconButton(onClick = { format { VeritasNoteEditing.toggleLinePrefix(it, "# ") } }) {
                            Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_h1), style = MaterialTheme.typography.labelMedium)
                        }

                        IconButton(onClick = { format { VeritasNoteEditing.toggleLinePrefix(it, "## ") } }) {
                            Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_h2), style = MaterialTheme.typography.labelMedium)
                        }

                        // Quote
                        IconButton(onClick = { format { VeritasNoteEditing.toggleLinePrefix(it, "> ") } }) {
                            Text("❝", style = MaterialTheme.typography.titleMedium)
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )

                        // Lists & Indent
                        IconButton(onClick = { format { VeritasNoteEditing.toggleLinePrefix(it, "- ") } }) {
                            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_bulleted_list))
                        }

                        IconButton(onClick = { format { VeritasNoteEditing.toggleLinePrefix(it, "1. ") } }) {
                            Icon(Icons.Filled.FormatListNumbered, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_numbered_list))
                        }

                        IconButton(onClick = { format(VeritasNoteEditing::applyIndent) }) {
                            Icon(Icons.AutoMirrored.Filled.FormatIndentIncrease, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_indent))
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
                            Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_aa), style = MaterialTheme.typography.labelMedium)
                        }

                        IconButton(onClick = {
                            transformSelection {
                                it.split(Regex("(?<=\\s)|(?=\\s)")).joinToString("") { word ->
                                    word.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() }
                                }
                            }
                        }) {
                            Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_aa_2c419ec), style = MaterialTheme.typography.labelMedium)
                        }

                        // Clean whitespace & formatting
                        IconButton(onClick = ::cleanAndNormalizeText) {
                            Icon(Icons.Filled.CleaningServices, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_clean_whitespace_formatting))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorDiscardDialog(onDiscard: () -> Unit, onKeep: () -> Unit) {
    VernAlertDialog(
        onDismissRequest = onKeep,
        title = { Text(com.veritas.reader.ui.screens.menuString(R.string.editor_discard_changes)) },
        confirmButton = { TextButton(onClick = onDiscard) { Text(com.veritas.reader.ui.screens.menuString(R.string.ui_discard)) } },
        dismissButton = { TextButton(onClick = onKeep) { Text(com.veritas.reader.ui.screens.menuString(R.string.ui_keep_editing)) } }
    )
}
