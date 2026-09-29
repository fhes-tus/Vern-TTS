package com.veritas.reader.ui.screens


import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.BookmarkRemove
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.veritas.reader.AiAssistantOption
import com.veritas.reader.AnnotationType
import com.veritas.reader.CoverExtractor
import com.veritas.reader.DocumentRepository
import com.veritas.reader.R
import com.veritas.reader.ReaderAnnotation
import com.veritas.reader.ReaderDocument
import com.veritas.reader.ReaderMode
import com.veritas.reader.ReaderPart
import com.veritas.reader.ReaderTextModel
import com.veritas.reader.ShareScope
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.VeritasSleepTimerAction
import com.veritas.reader.VeritasSleepTimerFormatter
import com.veritas.reader.VeritasSleepTimerPresets
import com.veritas.reader.VeritasSleepTimerRequest
import com.veritas.reader.VeritasSleepTimerSnapshot


internal data class ReaderDocDetailItem(val label: String, val value: String)

@Composable
internal fun ReaderDocumentDetailsDialog(
    document: ReaderDocument,
    currentIndex: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val savedDoc = remember(document.id) {
        val docId = document.id
        if (docId != null) {
            runCatching { DocumentRepository(context).findDocument(docId) }.getOrNull()
        } else null
    }

    val totalSentences = document.sentences.size
    val currentSentence = (currentIndex + 1).coerceAtMost(totalSentences)
    val progressPct = if (totalSentences > 0) ((currentSentence * 100) / totalSentences) else 0
    val estMinutes = (totalSentences * 2.5 / 60).toInt().coerceAtLeast(1)

    val details = remember(document, savedDoc, currentSentence, totalSentences, progressPct, estMinutes) {
        val list = mutableListOf<ReaderDocDetailItem>()
        list.add(ReaderDocDetailItem("Title", document.title))
        list.add(ReaderDocDetailItem("Format", document.sourceLabel.ifBlank { "Text Document" }))
        list.add(ReaderDocDetailItem("Progress", "$currentSentence / $totalSentences sentences ($progressPct%)"))
        if (document.pageCount > 0) {
            list.add(ReaderDocDetailItem("Pages", "${document.pageCount}"))
        }
        list.add(ReaderDocDetailItem("Est. Reading Time", "$estMinutes min"))
        if (document.rawText.isNotBlank()) {
            val words = document.rawText.split(Regex("\\s+")).count { it.isNotBlank() }
            list.add(ReaderDocDetailItem("Word Count", "%,d words (%,d characters)".format(words, document.rawText.length)))
        }
        if (savedDoc != null) {
            if (savedDoc.language.isNotBlank()) {
                list.add(ReaderDocDetailItem("Language", savedDoc.language))
            }
            val addedDate = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(savedDoc.createdAt))
            list.add(ReaderDocDetailItem("Added Date", addedDate))
            if (savedDoc.updatedAt > 0) {
                val lastRead = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(java.util.Date(savedDoc.updatedAt))
                if (lastRead != addedDate) {
                    list.add(ReaderDocDetailItem("Last Read", lastRead))
                }
            }
            if (savedDoc.originalFileName.isNotBlank()) {
                list.add(ReaderDocDetailItem("Source File", savedDoc.originalFileName.substringAfterLast('/').substringAfterLast('\\')))
            }
        }
        list
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Document Details", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                details.forEach { item ->
                    Column {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = item.value,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}



@Composable
fun SleepTimerDialog(
    activeTimer: VeritasSleepTimerSnapshot?,
    onSetTimer: (VeritasSleepTimerRequest) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    var tickerNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activeTimer) {
        if (activeTimer != null && (activeTimer.stopAtEndOfSection || activeTimer.isActive())) {
            while (true) {
                kotlinx.coroutines.delay(1000L)
                tickerNow = System.currentTimeMillis()
            }
        }
    }

    var selectedDurationMillis by remember {
        mutableLongStateOf(activeTimer?.durationMillis?.takeIf { it > 0L } ?: (15L * 60L * 1000L))
    }
    var selectedAction by remember {
        mutableStateOf(activeTimer?.action ?: VeritasSleepTimerAction.PAUSE)
    }
    var modeBySection by remember {
        mutableStateOf(activeTimer?.stopAtEndOfSection ?: false)
    }
    val isTimerActive = activeTimer != null && (activeTimer.stopAtEndOfSection || activeTimer.isActive(tickerNow))
    val activeLabel = activeTimer?.takeIf { it.stopAtEndOfSection || it.isActive(tickerNow) }?.menuLabel(tickerNow)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Sleep timer")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isTimerActive && activeLabel != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = VeritasPackStyle.cardShape(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = VeritasPackStyle.surfaceAlpha())),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(activeLabel, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    activeTimer.action.label,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            OutlinedButton(
                                onClick = onCancelTimer,
                                shape = VeritasPackStyle.chipShape(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                            ) {
                                Text("Cancel timer")
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.clickable { modeBySection = false },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = !modeBySection, onClick = { modeBySection = false })
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Countdown")
                    }
                    Row(
                        modifier = Modifier.clickable { modeBySection = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = modeBySection, onClick = { modeBySection = true })
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("End of section")
                    }
                }

                if (!modeBySection) {
                    VeritasSleepTimerPresets.durationsMillis.chunked(3).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            row.forEach { duration ->
                                val selected = selectedDurationMillis == duration
                                val content: @Composable () -> Unit = {
                                    Text(VeritasSleepTimerFormatter.formatDuration(duration))
                                }
                                if (selected) {
                                    Button(
                                        onClick = { selectedDurationMillis = duration },
                                        modifier = Modifier.weight(1f),
                                        shape = VeritasPackStyle.chipShape()
                                    ) { content() }
                                } else {
                                    OutlinedButton(
                                        onClick = { selectedDurationMillis = duration },
                                        modifier = Modifier.weight(1f),
                                        shape = VeritasPackStyle.chipShape()
                                    ) { content() }
                                }
                            }
                        }
                    }
                }

                VeritasSleepTimerAction.entries.forEach { action ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedAction = action },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedAction == action,
                            onClick = { selectedAction = action })
                        Text(action.label)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSetTimer(
                        VeritasSleepTimerRequest(
                            durationMillis = if (modeBySection) 0L else selectedDurationMillis,
                            action = selectedAction,
                            stopAtEndOfSection = modeBySection
                        )
                    )
                    onDismiss()
                },
                shape = VeritasPackStyle.chipShape()
            ) {
                Text(if (isTimerActive) "Update timer" else "Start timer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = VeritasPackStyle.chipShape()) {
                Text("Close")
            }
        }
    )
}





@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderDialogsAndSheetsHost(
    showAudioMode: Boolean,
    document: ReaderDocument,
    currentIndex: Int,
    isPlaying: Boolean,
    state: ReaderScreenState,
    hasCanvas: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onReaderModeChange: (ReaderMode) -> Unit,
    onToggleBookmark: (Int) -> Unit,
    onRateChange: (Float) -> Unit,
    onSentenceClick: (Int) -> Unit,
    onSentenceDoubleTap: (Int) -> Unit,
    onJumpToDestination: (Int?, Int) -> Unit = { _, idx -> onSentenceClick(idx) },
    onOpenSleepTimer: () -> Unit,
    onOpenVoiceStudio: () -> Unit,
    onOpenNarrationStudio: () -> Unit,
    onExportAudio: () -> Unit,
    showOutline: Boolean,
    onDismissOutline: () -> Unit,
    showRsvpSpeedReader: Boolean,
    onDismissRsvpSpeedReader: (Int) -> Unit,
    showDocumentDetails: Boolean,
    onDismissDocumentDetails: () -> Unit,
    colorPaletteTargetIndexes: List<Int>?,
    onDismissColorPalette: () -> Unit,
    onSetColorPaletteTarget: (List<Int>?) -> Unit,
    showShareToAiSheet: Boolean,
    onDismissShareToAiSheet: () -> Unit,
    shareToAiSelection: ReaderTextSelection?,
    shareToAiNoPrompt: Boolean,
    onShareToAi: (ShareScope, ReaderTextSelection?, IntRange?, Boolean) -> Unit,
    onSelectAskAiAssistant: (AiAssistantOption, String) -> Unit,
    onAskAiSelection: (String) -> Unit,
    showBookmarks: Boolean,
    onDismissBookmarks: () -> Unit,
    selectedTextView: TextView?,
    readerModel: ReaderTextModel,
    currentPart: ReaderPart?,
    annotations: List<ReaderAnnotation>,
    onAddBookmarkGroup: (List<Int>, String) -> Unit,
    onDismissShareToAi: () -> Unit
) {
    val context = LocalContext.current
    var activeColorPaletteTargets by remember(colorPaletteTargetIndexes) { mutableStateOf(colorPaletteTargetIndexes) }

    if (showAudioMode) {
        val coverFile = remember(document.id) { CoverExtractor.coverFile(context, document.id.orEmpty()) }
        val isBookmarked = remember(state.annotations, currentIndex) {
            state.annotations.any { it.chunkIndex == currentIndex && it.type == AnnotationType.BOOKMARK }
        }
        AudioModeScreen(
            title = document.title,
            currentIndex = currentIndex,
            totalChunks = document.chunks.size,
            currentSentence = document.chunks.getOrNull(currentIndex).orEmpty(),
            isPlaying = isPlaying,
            coverFile = coverFile,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
            onDismiss = { onReaderModeChange(ReaderMode.TEXT) },
            isBookmarked = isBookmarked,
            onToggleBookmark = {
                onToggleBookmark(currentIndex)
            },
            rate = state.rate,
            onRateChange = onRateChange,
            readerMode = state.readerMode,
            onReaderModeChange = onReaderModeChange,
            hasCanvas = hasCanvas,
            documentChunks = document.chunks,
            onSentenceClick = { index -> onSentenceDoubleTap(index) },
            onOpenSleepTimer = onOpenSleepTimer,
            onOpenVoiceStudio = onOpenVoiceStudio,
            onOpenNarrationStudio = onOpenNarrationStudio,
            onExportAudio = onExportAudio
        )
    }

    if (showOutline) {
        SmartOutlineDialog(
            document = document,
            documentOutline = state.outlineEntries,
            currentIndex = currentIndex,
            onJumpToDestination = { pageNum, index ->
                onDismissOutline()
                onJumpToDestination(pageNum, index)
            },
            onDismiss = { onDismissOutline() },
            readerModel = readerModel
        )
    }

    if (showRsvpSpeedReader) {
        RsvpSpeedReader(
            document = document,
            initialSentenceIndex = currentIndex,
            onClose = { targetSentenceIndex ->
                onDismissRsvpSpeedReader(targetSentenceIndex)
            }
        )
    }

    if (showDocumentDetails) {
        ReaderDocumentDetailsDialog(
            document = document,
            currentIndex = currentIndex,
            onDismiss = { onDismissDocumentDetails() }
        )
    }

    if (colorPaletteTargetIndexes != null) {
        val targetIndexes = colorPaletteTargetIndexes ?: emptyList()
        val existingBookmarks = remember(targetIndexes, state.annotations) {
            state.annotations.filter { it.type == AnnotationType.BOOKMARK && targetIndexes.contains(it.chunkIndex) }
        }
        val isAlreadyBookmarked = existingBookmarks.isNotEmpty()
        val currentHex = existingBookmarks.firstOrNull()?.highlightColor
        val colorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = {
                onDismissColorPalette()
                clearNativeTextSelection(selectedTextView)
            },
            sheetState = colorSheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isAlreadyBookmarked) "Bookmark Options" else "Highlight Passage",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (isAlreadyBookmarked) {
                        OutlinedButton(
                            onClick = {
                                targetIndexes.forEach { idx ->
                                    if (state.annotations.any { it.chunkIndex == idx && it.type == AnnotationType.BOOKMARK }) {
                                        onToggleBookmark(idx)
                                    }
                                }
                                onDismissColorPalette()
                                clearNativeTextSelection(selectedTextView)
                            },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                Icons.Outlined.BookmarkRemove,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Remove")
                        }
                    }
                }
                Text(
                    text = if (isAlreadyBookmarked) "Select another color to update bookmark:" else "Select a highlight color:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val colors = listOf(
                        "#FFE082" to "Yellow",
                        "#A5D6A7" to "Green",
                        "#90CAF9" to "Blue",
                        "#F48FB1" to "Pink",
                        "#B39DDB" to "Purple",
                        "#FFCC80" to "Orange"
                    )
                    colors.forEach { (hex, name) ->
                        val isSelectedColor = isAlreadyBookmarked && (currentHex.equals(hex, ignoreCase = true) || (currentHex.isNullOrBlank() && hex == "#FFE082"))
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(android.graphics.Color.parseColor(hex)), CircleShape)
                                .border(
                                    width = if (isSelectedColor) 3.dp else 2.dp,
                                    color = if (isSelectedColor) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    colorPaletteTargetIndexes?.let { indexes ->
                                        onAddBookmarkGroup(indexes, hex)
                                    }
                                    onDismissColorPalette()
                                    clearNativeTextSelection(selectedTextView)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelectedColor) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Selected color",
                                    tint = Color.Black.copy(alpha = 0.75f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    val currentPage = remember(readerModel.sentences, currentIndex) {
        readerModel.sentences.getOrNull(currentIndex)?.pageNumber ?: 1
    }

    if (showShareToAiSheet) {
        val selectionVal = shareToAiSelection
        var selectedScope by remember { mutableStateOf<ShareScope>(if (selectionVal != null) ShareScope.SELECTED_TEXT else ShareScope.CURRENT_SECTION) }
        var startPageStr by remember { mutableStateOf(currentPage.toString()) }
        var endPageStr by remember { mutableStateOf(currentPage.toString()) }
        
        AlertDialog(
            onDismissRequest = { 
                onDismissShareToAiSheet()
                onDismissShareToAi()
            },
            title = {
                Text(
                    text = "Ask AI",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Select how much of the document content you'd like to share as a Markdown file (.md):",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    if (selectionVal != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedScope = ShareScope.SELECTED_TEXT }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = (selectedScope == ShareScope.SELECTED_TEXT),
                                onClick = { selectedScope = ShareScope.SELECTED_TEXT }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Selected Text (${selectionVal.sentenceIndexes.size} sentences)", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    
                    val partLabel = currentPart?.let { "Section ${it.index + 1} (${if (document.sourceLabel == "PPTX") "Slides" else "Pages"} ${it.pageRange.startPage}-${it.pageRange.endPage})" } ?: "Current Section"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedScope = ShareScope.CURRENT_SECTION }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = (selectedScope == ShareScope.CURRENT_SECTION),
                            onClick = { selectedScope = ShareScope.CURRENT_SECTION }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(partLabel, style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedScope = ShareScope.CUSTOM_PAGE_RANGE }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = (selectedScope == ShareScope.CUSTOM_PAGE_RANGE),
                            onClick = { selectedScope = ShareScope.CUSTOM_PAGE_RANGE }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Custom Page Range", style = MaterialTheme.typography.bodyMedium)
                    }
                    
                    if (selectedScope == ShareScope.CUSTOM_PAGE_RANGE) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(start = 32.dp)
                        ) {
                            OutlinedTextField(
                                value = startPageStr,
                                onValueChange = { startPageStr = it.filter { char -> char.isDigit() } },
                                label = { Text("From") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = endPageStr,
                                onValueChange = { endPageStr = it.filter { char -> char.isDigit() } },
                                label = { Text("To") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedScope = ShareScope.ENTIRE_DOCUMENT }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = (selectedScope == ShareScope.ENTIRE_DOCUMENT),
                            onClick = { selectedScope = ShareScope.ENTIRE_DOCUMENT }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Entire Document (${document.pageCount} pages)", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = startPageStr.toIntOrNull() ?: 1
                        val end = endPageStr.toIntOrNull() ?: 1
                        val range = if (selectedScope == ShareScope.CUSTOM_PAGE_RANGE) {
                            val min = minOf(start, end).coerceIn(1, document.pageCount)
                            val max = maxOf(start, end).coerceIn(1, document.pageCount)
                            min..max
                        } else {
                            null
                        }
                        
                        onShareToAi(selectedScope, shareToAiSelection, range, shareToAiNoPrompt)
                        onDismissShareToAiSheet()
                        onDismissShareToAi()
                    }
                ) {
                    Text("Share", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    onDismissShareToAiSheet()
                    onDismissShareToAi()
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showBookmarks) {
        BookmarksOverviewDialog(
            document = document,
            annotations = annotations,
            onJumpToSection = { index ->
                onDismissBookmarks()
                onSentenceClick(index)
            },
            onDismiss = { onDismissBookmarks() }
        )
    }

}

@Composable
fun JumpToPageDialog(
    isOpen: Boolean,
    currentPageIndex: Int,
    pageCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    if (!isOpen) return
    var jumpInput by remember(currentPageIndex) { mutableStateOf("${currentPageIndex + 1}") }
    val parsed = jumpInput.toIntOrNull()
    val isValid = parsed != null && parsed in 1..pageCount

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_m3_jump_page),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                "Jump to Page",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Enter a page number between 1 and $pageCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                OutlinedTextField(
                    value = jumpInput,
                    onValueChange = { input ->
                        val digits = input.filter { it.isDigit() }.take(5)
                        jumpInput = digits
                    },
                    singleLine = true,
                    isError = jumpInput.isNotBlank() && !isValid,
                    supportingText = if (jumpInput.isNotBlank() && !isValid) {
                        { Text("Please enter 1 to $pageCount", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    textStyle = MaterialTheme.typography.titleLarge.copy(
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    ),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValid && parsed != null) {
                        onConfirm(parsed - 1)
                        onDismiss()
                    }
                },
                enabled = isValid,
                shape = VeritasPackStyle.chipShape()
            ) {
                Text("Jump", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = VeritasPackStyle.cardShape(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

