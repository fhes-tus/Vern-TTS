package com.veritas.reader.ui.screens
import com.veritas.reader.VernAlertDialog as AlertDialog

import com.veritas.reader.vernScatteredCard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.R
import com.veritas.reader.*
import com.veritas.reader.ui.ReaderUiState

private data class StudyCollection(val section: String, val count: Int)

@Composable
internal fun StudyHubOverview(
    state: ReaderUiState,
    sets: List<FlashcardSet>,
    vocabularyCount: Int,
    review: StudyReviewSummary,
    onSelectSection: (String) -> Unit,
    onStartReview: () -> Unit,
    onOpenDeck: (FlashcardSet) -> Unit,
    onImportFlashcards: () -> Unit,
    onImportQuiz: () -> Unit,
    onCreateWithAi: () -> Unit,
    onSetupAi: () -> Unit,
    onExportStudyGuide: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val collections = listOf(
        StudyCollection("Flashcards", state.flashcards.size),
        StudyCollection("Quizzes", state.quizzes.size),
        StudyCollection("Vocab", vocabularyCount),
        StudyCollection("Bookmarks", state.allAnnotations.count { it.type == AnnotationType.BOOKMARK }),
        StudyCollection("Booknotes", state.allAnnotations.count { it.type == AnnotationType.NOTE } + state.documentNotes.values.count { it.isNotBlank() }),
        StudyCollection("History", state.readingHistory.size)
    )
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val usePanels = maxWidth >= 840.dp && androidx.compose.ui.platform.LocalDensity.current.fontScale < 1.3f
    val panelWidth = if (usePanels) with(androidx.compose.ui.platform.LocalDensity.current) {
        ((constraints.maxWidth - 20.dp.roundToPx()) / 2).toDp()
    } else maxWidth
    val materialColumns = vernCollectionColumns(panelWidth.value, if (panelWidth < 600.dp) 168f else 236f,
        androidx.compose.ui.platform.LocalDensity.current.fontScale, gapDp = 12f, maximum = 3)
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp), maxItemsInEachRow = if (usePanels) 2 else 1) {
        StudyDailyReviewHeroCard(review.dueCards.size, onStartReview, Modifier.width(panelWidth).testTag("study-review-panel"))
        if (sets.isNotEmpty()) {
            Column(Modifier.width(panelWidth).testTag("study-decks-panel"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_your_decks), style = MaterialTheme.typography.titleMedium, color = colors.onSurface, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onSelectSection("Flashcards") }) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_see_all_2941c5c)) }
                }
                sets.take(3).forEachIndexed { index, set ->
                    StudyActiveDeckItem(index + 1, set.name, set.cards.size, Icons.Outlined.Style, { onOpenDeck(set) })
                }
            }
        }
        Column(Modifier.width(panelWidth).testTag("study-materials-panel"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_your_study_material), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
            collections.chunked(materialColumns).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { collection ->
                        TextButton(onClick = { onSelectSection(collection.section) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            .vernScatteredCard("study-material-${collection.section}", amount = .5f)
                            .testTag("study-material-${collection.section}"),
                            shape = VeritasPackStyle.compactShape(),
                            colors = ButtonDefaults.textButtonColors(containerColor = colors.surfaceContainerHigh.copy(alpha = .65f))) {
                            Text(localizedCatalogLabel(collection.section),
                                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                            Text(localizedUiValue(collection.count), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Card(
            onClick = { onSelectSection("Dictionary") },
            modifier = Modifier
                .width(panelWidth)
                .vernScatteredCard("study-dictionary", amount = .45f)
                .testTag("study-dictionary-entry"),
            shape = VeritasPackStyle.cardShape(),
            colors = CardDefaults.cardColors(containerColor = VeritasPackStyle.panelColor(colors)),
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(colors.primaryContainer.copy(alpha = .5f), shape = VeritasPackStyle.compactShape()),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        androidx.compose.ui.res.painterResource(R.drawable.ic_m3_dictionary),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = colors.primary
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        menuString(R.string.dict_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        menuString(R.string.dict_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colors.onSurfaceVariant.copy(alpha = .7f)
                )
            }
        }
        Column(Modifier.width(panelWidth), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(menuString(R.string.study_import_material), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = if (materialColumns == 1) 1 else 2) {
                OutlinedButton(onClick = onImportFlashcards, modifier = Modifier.weight(1f)) {
                    Text(menuString(R.string.ui_paste_cards))
                }
                OutlinedButton(onClick = onImportQuiz, modifier = Modifier.weight(1f)) {
                    Text(menuString(R.string.ui_paste_quiz))
                }
            }
            TextButton(onClick = onExportStudyGuide, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(menuString(R.string.ui_export_study_guide_pdf))
            }
        }
        Card(
            modifier = Modifier.width(panelWidth).testTag("study-ai-workspace"),
            shape = VeritasPackStyle.cardShape(),
            colors = CardDefaults.cardColors(containerColor = colors.primaryContainer),
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .5f))
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = colors.onPrimaryContainer)
                    Text(menuString(R.string.study_ai_workspace), color = colors.onPrimaryContainer,
                        style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
                Text(menuString(R.string.study_ai_workspace_description), color = colors.onPrimaryContainer,
                    style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onCreateWithAi, modifier = Modifier.fillMaxWidth(), enabled = !state.isGeneratingAiStudy) {
                    Text(menuString(R.string.study_ai_choose_book))
                }
                TextButton(onClick = onSetupAi, colors = ButtonDefaults.textButtonColors(contentColor = colors.onPrimaryContainer)) {
                    Text(menuString(R.string.ui_ai_settings))
                }
            }
        }
    }
}

}

@Composable
internal fun StudyBookPickerDialog(documents: List<SavedDocument>, onSelect: (SavedDocument) -> Unit, onGoToLibrary: () -> Unit, onDismiss: () -> Unit,
    title: String = menuString(R.string.study_choose_flashcard_book),
    emptyDescription: String = menuString(R.string.study_flashcard_picker_empty)) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (documents.isEmpty()) Text(emptyDescription)
            else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(documents, key = { it.id }) { document ->
                    TextButton(onClick = { onSelect(document) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(document.title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { if (documents.isEmpty()) TextButton(onClick = onGoToLibrary) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_open_library)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_cancel)) } }
    )
}
