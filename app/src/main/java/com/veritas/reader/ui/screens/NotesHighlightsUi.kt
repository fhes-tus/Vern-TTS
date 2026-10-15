package com.veritas.reader.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.veritas.reader.DocumentRepository
import com.veritas.reader.ReaderAnnotation
import com.veritas.reader.SavedDocument
import com.veritas.reader.TextChunker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred

/** Projects the existing book annotations; no duplicate note records are created. */
@Composable
internal fun NotesHighlights(
    documents: List<SavedDocument>, annotations: List<ReaderAnnotation>, query: String,
    onOpenAt: (SavedDocument, Int) -> Unit, onDelete: (Set<String>) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { DocumentRepository(context) }
    val scope = rememberCoroutineScope()
    val sentenceCache = remember(documents, annotations) { mutableMapOf<String, Deferred<Map<Int, String>>>() }
    val selectedIndexes = remember(annotations) { annotations.groupBy { it.documentId }.mapValues { (_, values) -> values.map { it.chunkIndex }.toSet() } }
    val groups = remember(documents, annotations, query) {
        val byDocument = annotations.groupBy { it.documentId }
        documents.flatMap { doc -> groupNotes(doc, byDocument[doc.id].orEmpty()) }
            .filter { query.isBlank() || it.document.title.contains(query.trim(), true) || it.noteText.contains(query.trim(), true) }
            .sortedByDescending { group -> group.annotations.maxOf { it.updatedAt } }
    }
    var deleteKeys by remember { mutableStateOf<Set<String>?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(bottom = LocalHomeBottomPadding.current + 92.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (groups.isEmpty()) item { Text(if (query.isBlank()) "No saved highlights yet." else "No matching highlights.", Modifier.padding(vertical = 24.dp)) }
        items(groups, key = { "${it.document.id}:${it.id}" }) { group ->
            var sentences by remember(group.document.id) { mutableStateOf<Map<Int, String>>(emptyMap()) }
            LaunchedEffect(group.document.id, group.document.updatedAt, selectedIndexes) {
                // Share one background read per book; retain only the annotated sentences.
                sentences = sentenceCache.getOrPut(group.document.id) {
                    scope.async(Dispatchers.IO) {
                        val all = TextChunker.chunk(repository.readText(group.document))
                        selectedIndexes[group.document.id].orEmpty().mapNotNull { index -> all.getOrNull(index)?.let { index to it } }.toMap()
                    }
                }.await()
            }
            NoteGroupCard(group, { sentences[it] }, { onOpenAt(group.document, it) },
                { deleteKeys = group.annotations.map { it.stableKey }.toSet() })
        }
    }
    deleteKeys?.let { keys ->
        AlertDialog(onDismissRequest = { deleteKeys = null }, title = { Text("Delete highlight?") },
            text = { Text("This removes the selected annotation from the book and Study too.") },
            confirmButton = { TextButton({ onDelete(keys); deleteKeys = null }) { Text("Delete") } },
            dismissButton = { TextButton({ deleteKeys = null }) { Text("Cancel") } })
    }
}
