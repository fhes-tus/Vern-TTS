package com.veritas.reader


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import com.veritas.reader.VernAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.veritas.reader.ui.VeritasPendingImport
import com.veritas.reader.ui.VeritasSwitch
import java.util.Locale


@Composable
fun VeritasImportPreviewDialog(
    pendingImport: VeritasPendingImport,
    onConfirm: (String, PdfImportOptions, TextImportOptions, PptxImportOptions) -> Unit,
    onCancel: () -> Unit
) {
    var title by remember { mutableStateOf(cleanDocumentTitle(pendingImport.name)) }
    var pdfOptions by remember { mutableStateOf(pendingImport.pdfOptions) }
    var textOptions by remember { mutableStateOf(pendingImport.textOptions) }
    var pptxOptions by remember { mutableStateOf(pendingImport.pptxOptions) }

    var startPageDraft by remember { mutableStateOf(pdfOptions.startPage?.toString().orEmpty()) }
    var endPageDraft by remember { mutableStateOf(pdfOptions.endPage?.toString().orEmpty()) }

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

    val sizeKB = pendingImport.sizeBytes / 1024.0
    val sizeMB = sizeKB / 1024.0
    val sizeText = if (sizeMB >= 1.0) {
        String.format(Locale.US, "%.2f MB", sizeMB)
    } else {
        String.format(Locale.US, "%.1f KB", sizeKB)
    }

    val pageCount = pendingImport.pageCount
    val isPageRangeInvalid = pendingImport.isPdf && pageCount > 0 && run {
        val start = startPageDraft.toIntOrNull()
        val end = endPageDraft.toIntOrNull()
        start == null || end == null || start < 1 || end > pageCount || start > end
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_import_settings)) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_061, com.veritas.reader.ui.screens.localizedUiValue(sizeText)) + if (pendingImport.isPdf && pageCount > 0) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_062, com.veritas.reader.ui.screens.localizedUiValue(pageCount)) else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_document_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (pendingImport.isPptx) {
                    item {
                        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_slides_options), style = MaterialTheme.typography.titleSmall)
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_speaker_notes_3d6e76c), fontWeight = FontWeight.SemiBold)
                                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_read_presenter_notes_after_each_slide), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            VeritasSwitch(
                                checked = pptxOptions.includeSpeakerNotes,
                                onCheckedChange = { pptxOptions = pptxOptions.copy(includeSpeakerNotes = it) }
                            )
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_auto_punctuate), fontWeight = FontWeight.SemiBold)
                                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_add_end_punctuation_to_unpunctuated_lines_for_smoother_listening), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            VeritasSwitch(
                                checked = pptxOptions.autoPunctuate,
                                onCheckedChange = { pptxOptions = pptxOptions.copy(autoPunctuate = it) }
                            )
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_read_text_in_slide_images), fontWeight = FontWeight.SemiBold)
                                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_ocr_screenshots_and_figures_large_decks_import_slower), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            VeritasSwitch(
                                checked = pptxOptions.ocrSlideImages,
                                onCheckedChange = { pptxOptions = pptxOptions.copy(ocrSlideImages = it) }
                            )
                        }
                    }
                }

                if (pendingImport.isPdf) {
                    item {
                        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_pdf_options), style = MaterialTheme.typography.titleSmall)
                    }

                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = startPageDraft,
                                onValueChange = { value ->
                                    startPageDraft = value.filter { it.isDigit() }.take(5)
                                    val startVal = startPageDraft.toIntOrNull()
                                    pdfOptions = pdfOptions.copy(startPage = startVal)
                                },
                                label = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_start_page)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = endPageDraft,
                                onValueChange = { value ->
                                    endPageDraft = value.filter { it.isDigit() }.take(5)
                                    val endVal = endPageDraft.toIntOrNull()
                                    pdfOptions = pdfOptions.copy(endPage = endVal)
                                },
                                label = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_end_page)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (isPageRangeInvalid && pageCount > 0) {
                        item {
                            Text(
                                text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_063, com.veritas.reader.ui.screens.localizedUiValue(pageCount)),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    item {
                        Column {
                            Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_extraction_mode), fontWeight = FontWeight.Bold)
                            Box {
                                OutlinedButton(
                                    onClick = { modeExpanded = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        pdfOptions.extractionMode.ifBlank { com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_html_with_images) },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                DropdownMenu(
                                    expanded = modeExpanded,
                                    onDismissRequest = { modeExpanded = false }
                                ) {
                                    extractionModes.forEach { mode ->
                                        DropdownMenuItem(
                                            text = { Text(mode) },
                                            onClick = {
                                                modeExpanded = false
                                                pdfOptions = pdfOptions.copy(
                                                    extractionMode = mode,
                                                    forceOcr = mode == "Force OCR",
                                                    preferOcrWhenLowText = mode != "Plain text"
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        PdfImportToggleRow(
                            title = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_clean_repeated_headers_and_footers),
                            checked = pdfOptions.cleanupRepeatedLines,
                            onCheckedChange = { pdfOptions = pdfOptions.copy(cleanupRepeatedLines = it) }
                        )
                    }

                    item {
                        PdfImportToggleRow(
                            title = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_remove_page_numbers),
                            checked = pdfOptions.removePageNumbers,
                            onCheckedChange = { pdfOptions = pdfOptions.copy(removePageNumbers = it) }
                        )
                    }

                    item {
                        PdfImportToggleRow(
                            title = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_repair_hyphenated_line_breaks),
                            checked = pdfOptions.repairHyphenation,
                            onCheckedChange = { pdfOptions = pdfOptions.copy(repairHyphenation = it) }
                        )
                    }

                    item {
                        PdfImportToggleRow(
                            title = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_include_page_markers),
                            checked = pdfOptions.includePageMarkers,
                            onCheckedChange = { pdfOptions = pdfOptions.copy(includePageMarkers = it) }
                        )
                    }

                    item {
                        PdfImportToggleRow(
                            title = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_remove_top_page_noise),
                            checked = pdfOptions.removeTopPageNoise,
                            onCheckedChange = { pdfOptions = pdfOptions.copy(removeTopPageNoise = it) }
                        )
                    }

                    item {
                        PdfImportToggleRow(
                            title = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_remove_bottom_page_noise),
                            checked = pdfOptions.removeBottomPageNoise,
                            onCheckedChange = { pdfOptions = pdfOptions.copy(removeBottomPageNoise = it) }
                        )
                    }
                } else if (pendingImport.mimeType.contains("text") || pendingImport.mimeType.contains("html") || pendingImport.name.endsWith(".txt") || pendingImport.name.endsWith(".html") || pendingImport.name.endsWith(".htm")) {
                    item {
                        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_text_options), style = MaterialTheme.typography.titleSmall)
                    }

                    item {
                        Column {
                            Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_text_encoding), fontWeight = FontWeight.Bold)
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
                                    onDismissRequest = { encodingExpanded = false }
                                ) {
                                    TextImportEncodingCatalog.options.forEach { encoding ->
                                        DropdownMenuItem(
                                            text = { Text(encoding.label) },
                                            onClick = {
                                                encodingExpanded = false
                                                textOptions = textOptions.copy(encodingId = encoding.id)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(title.ifBlank { pendingImport.name }, pdfOptions, textOptions, pptxOptions)
                },
                enabled = !isPageRangeInvalid
            ) {
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_import))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_cancel))
            }
        }
    )
}

