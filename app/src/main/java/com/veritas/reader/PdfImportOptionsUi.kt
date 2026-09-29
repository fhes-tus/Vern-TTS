package com.veritas.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.veritas.reader.ui.VeritasSwitch


@Composable
internal fun PdfImportOptionsDialog(
    options: PdfImportOptions,
    textOptions: TextImportOptions,
    onOptionsChange: (PdfImportOptions) -> Unit,
    onTextOptionsChange: (TextImportOptions) -> Unit,
    onPickPdf: () -> Unit,
    onDismiss: () -> Unit
) {
    var startPageDraft by remember(options.startPage) {
        mutableStateOf(
            options.startPage?.toString().orEmpty()
        )
    }
    var endPageDraft by remember(options.endPage) {
        mutableStateOf(
            options.endPage?.toString().orEmpty()
        )
    }
    var modeExpanded by remember { mutableStateOf(false) }
    var encodingExpanded by remember { mutableStateOf(false) }
    val extractionModes = remember {
        listOf(
            "HTML with images",
            "Plain text",
            "Prefer OCR when text is poor",
            "Force OCR"
        )
    }
    val selectedEncoding = TextImportEncodingCatalog.byId(textOptions.encodingId)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            // imePadding: this page has page-number inputs; with edge-to-edge the soft
            // keyboard must not cover them.
            Column(modifier = Modifier.fillMaxSize().imePadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text("PDF & import tools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "Control how PDFs and text files are imported before they enter the reader.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = startPageDraft,
                            onValueChange = { value ->
                                startPageDraft = value.filter { it.isDigit() }.take(5)
                                onOptionsChange(
                                    options.copy(
                                        startPage = startPageDraft.toIntOrNull()?.takeIf { it > 0 })
                                )
                            },
                            label = { Text("Start page") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endPageDraft,
                            onValueChange = { value ->
                                endPageDraft = value.filter { it.isDigit() }.take(5)
                                onOptionsChange(
                                    options.copy(
                                        endPage = endPageDraft.toIntOrNull()?.takeIf { it > 0 })
                                )
                            },
                            label = { Text("End page") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Column {
                        Text("Extraction mode", fontWeight = FontWeight.Bold)
                        Box {
                            OutlinedButton(
                                onClick = { modeExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    options.extractionMode.ifBlank { "HTML with images" },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            DropdownMenu(
                                expanded = modeExpanded,
                                onDismissRequest = { modeExpanded = false }) {
                                extractionModes.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode) },
                                        onClick = {
                                            modeExpanded = false
                                            onOptionsChange(
                                                options.copy(
                                                    extractionMode = mode,
                                                    forceOcr = mode == "Force OCR",
                                                    preferOcrWhenLowText = mode != "Plain text"
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Column {
                        Text("Text encoding", fontWeight = FontWeight.Bold)
                        Box {
                            OutlinedButton(
                                onClick = { encodingExpanded = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    selectedEncoding.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            DropdownMenu(
                                expanded = encodingExpanded,
                                onDismissRequest = { encodingExpanded = false }) {
                                TextImportEncodingCatalog.options.forEach { encoding ->
                                    DropdownMenuItem(
                                        text = { Text(encoding.label) },
                                        onClick = {
                                            encodingExpanded = false
                                            onTextOptionsChange(textOptions.copy(encodingId = encoding.id))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    PdfImportToggleRow(
                        title = "Clean repeated headers and footers",
                        checked = options.cleanupRepeatedLines,
                        onCheckedChange = { onOptionsChange(options.copy(cleanupRepeatedLines = it)) }
                    )
                }
                item {
                    PdfImportToggleRow(
                        title = "Remove page numbers",
                        checked = options.removePageNumbers,
                        onCheckedChange = { onOptionsChange(options.copy(removePageNumbers = it)) }
                    )
                }
                item {
                    PdfImportToggleRow(
                        title = "Repair hyphenated line breaks",
                        checked = options.repairHyphenation,
                        onCheckedChange = { onOptionsChange(options.copy(repairHyphenation = it)) }
                    )
                }
                item {
                    PdfImportToggleRow(
                        title = "Include page markers",
                        checked = options.includePageMarkers,
                        onCheckedChange = { onOptionsChange(options.copy(includePageMarkers = it)) }
                    )
                }
                item {
                    PdfImportToggleRow(
                        title = "Match original layout where possible",
                        checked = options.markPdfLinesForCanvas,
                        onCheckedChange = { onOptionsChange(options.copy(markPdfLinesForCanvas = it)) }
                    )
                }
                item {
                    PdfImportToggleRow(
                        title = "Remove top page noise",
                        checked = options.removeTopPageNoise,
                        onCheckedChange = { onOptionsChange(options.copy(removeTopPageNoise = it)) }
                    )
                }
                item {
                    PdfImportToggleRow(
                        title = "Remove bottom page noise",
                        checked = options.removeBottomPageNoise,
                        onCheckedChange = { onOptionsChange(options.copy(removeBottomPageNoise = it)) }
                    )
                }
                item {
                    PdfImportToggleRow(
                        title = "Manual crop before extract",
                        checked = options.manualCropBeforeExtract,
                        onCheckedChange = { onOptionsChange(options.copy(manualCropBeforeExtract = it)) }
                    )
                }
                item {
                    OutlinedTextField(
                        value = options.minWordGap,
                        onValueChange = { value ->
                            onOptionsChange(options.copy(minWordGap = value.filter { it.isDigit() || it == '.' }
                                .take(6).ifBlank { "0.1" }))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Minimum word gap") },
                        singleLine = true
                    )
                }
                item {
                    PdfImportToggleRow(
                        title = "Separate words when font changes",
                        checked = options.separateWordsOnFontChange,
                        onCheckedChange = { onOptionsChange(options.copy(separateWordsOnFontChange = it)) }
                    )
                }
                item {
                    PdfImportToggleRow(
                        title = "Prefer OCR when PDF text is weak",
                        checked = options.preferOcrWhenLowText,
                        onCheckedChange = { onOptionsChange(options.copy(preferOcrWhenLowText = it)) }
                    )
                }
                item {
                    PdfImportToggleRow(
                        title = "Force OCR for PDF import",
                        checked = options.forceOcr,
                        onCheckedChange = { onOptionsChange(options.copy(forceOcr = it)) }
                    )
                }
                item {
                    PdfImportToggleRow(
                        title = "Force fresh extraction",
                        checked = options.forceFreshExtraction,
                        onCheckedChange = { onOptionsChange(options.copy(forceFreshExtraction = it)) }
                    )
                }
            }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Done") }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = onPickPdf, shape = RoundedCornerShape(50)) { Text("Open file browser") }
                }
            }
        }
    }
}

@Composable
internal fun PdfImportToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, modifier = Modifier.weight(1f))
        VeritasSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}


