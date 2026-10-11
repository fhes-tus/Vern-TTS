package com.veritas.reader.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.*
import com.veritas.reader.ui.ReaderUiState

private data class StudyCollection(val section: String, val count: Int, val unit: String, val icon: ImageVector)

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
    onSetupAi: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val collections = listOf(
        StudyCollection("Flashcards", state.flashcards.size, "cards", Icons.Outlined.Style),
        StudyCollection("Quizzes", state.quizzes.size, "quizzes", Icons.Outlined.Quiz),
        StudyCollection("Vocab", vocabularyCount, "words", Icons.Outlined.Translate),
        StudyCollection("Bookmarks", state.allAnnotations.count { it.type == AnnotationType.BOOKMARK }, "bookmarks", Icons.Outlined.BookmarkBorder),
        StudyCollection("Booknotes", state.allAnnotations.count { it.type == AnnotationType.NOTE } + state.documentNotes.values.count { it.isNotBlank() }, "notes", Icons.AutoMirrored.Outlined.MenuBook),
        StudyCollection("History", state.readingHistory.size, "recent books", Icons.Outlined.History)
    )
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        StudyDailyReviewHeroCard(review.notDuePercent, review.dueCards.size, onStartReview)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Your study material", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            collections.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { collection ->
                        Card(
                            onClick = { onSelectSection(collection.section) },
                            modifier = Modifier.weight(1f),
                            shape = VeritasPackStyle.cardShape(),
                            colors = CardDefaults.cardColors(containerColor = VeritasPackStyle.panelColor(colors)),
                            border = VeritasPackStyle.cardBorder(colors)
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(collection.icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(24.dp))
                                Text(if (collection.section == "Vocab") "Vocabulary" else collection.section, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                val unit = if (collection.count != 1) collection.unit else when (collection.unit) {
                                    "quizzes" -> "quiz"
                                    else -> collection.unit.removeSuffix("s")
                                }
                                Text("${collection.count} $unit", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        if (sets.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Your decks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onSelectSection("Flashcards") }) { Text("See all") }
                }
                sets.take(3).forEachIndexed { index, set ->
                    StudyActiveDeckItem(index + 1, set.name, set.cards.size, Icons.Outlined.Style, { onOpenDeck(set) })
                }
            }
        }
        Card(
            shape = VeritasPackStyle.cardShape(),
            colors = CardDefaults.cardColors(containerColor = colors.secondaryContainer.copy(alpha = .45f)),
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .5f))
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Build your study material", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Paste flashcards or quizzes, or choose a book for AI flashcards.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onImportFlashcards, modifier = Modifier.weight(1f)) { Text("Paste cards") }
                    OutlinedButton(onClick = onImportQuiz, modifier = Modifier.weight(1f)) { Text("Paste quiz") }
                }
                Button(onClick = onCreateWithAi, modifier = Modifier.fillMaxWidth(), enabled = !state.isGeneratingAiStudy) {
                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Create with AI")
                }
                TextButton(onClick = onSetupAi, modifier = Modifier.align(Alignment.End)) { Text("AI settings") }
            }
        }
    }
}

@Composable
internal fun StudyBookPickerDialog(documents: List<SavedDocument>, onSelect: (SavedDocument) -> Unit, onGoToLibrary: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a book for flashcards") },
        text = {
            if (documents.isEmpty()) Text("Add a book to your library first. You can also paste flashcards into Study Hub.")
            else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(documents, key = { it.id }) { document ->
                    TextButton(onClick = { onSelect(document) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(document.title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { if (documents.isEmpty()) TextButton(onClick = onGoToLibrary) { Text("Open library") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
