package com.veritas.reader.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.AiProvider
import com.veritas.reader.GeminiStudyService
import com.veritas.reader.IconChatGPT
import com.veritas.reader.IconClaude
import com.veritas.reader.IconGemini
import com.veritas.reader.SavedDocument
import com.veritas.reader.VocabularyEntry
import com.veritas.reader.copyTextToClipboard
import com.veritas.reader.shareVocabularyAsImage
import com.veritas.reader.shareVocabularyAsWords


@Composable
internal fun GeminiApiKeyDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selectedProvider by remember { mutableStateOf(GeminiStudyService.getProvider(context)) }
    var keyDraft by remember { mutableStateOf(GeminiStudyService.getApiKey(context)) }
    var modelDraft by remember { mutableStateOf(GeminiStudyService.getModel(context).ifBlank { selectedProvider.defaultModel }) }
    var endpointDraft by remember { mutableStateOf(GeminiStudyService.getCustomEndpoint(context).ifBlank { selectedProvider.defaultEndpoint }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("AI Provider & Key Setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Connect any AI model to generate flashcards, quizzes, and study summaries directly in Veritas Reader.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    "Model",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (selectedProvider.recommendedModels.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        selectedProvider.recommendedModels.forEach { recModel ->
                            FilterChip(
                                selected = modelDraft.trim() == recModel,
                                onClick = { modelDraft = recModel },
                                leadingIcon = if (modelDraft.trim() == recModel) {
                                    {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                                        )
                                    }
                                } else null,
                                label = { Text(recModel, fontSize = 11.sp) },
                                shape = RoundedCornerShape(50)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = modelDraft,
                    onValueChange = { modelDraft = it },
                    label = { Text("Model Name / ID") },
                    placeholder = { Text(selectedProvider.defaultModel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Provider",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        selectedProvider.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiProvider.entries.forEach { provider ->
                        val icon = when (provider) {
                            AiProvider.GEMINI -> IconGemini
                            AiProvider.OPENAI -> IconChatGPT
                            AiProvider.ANTHROPIC -> IconClaude
                            AiProvider.GROQ -> Icons.Outlined.Bolt
                            AiProvider.OPENROUTER -> Icons.Filled.Language
                            AiProvider.CUSTOM -> Icons.Filled.Settings
                        }
                        FilterChip(
                            selected = selectedProvider == provider,
                            onClick = {
                                selectedProvider = provider
                                modelDraft = provider.defaultModel
                                endpointDraft = provider.defaultEndpoint
                            },
                            label = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = provider.label,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }

                OutlinedTextField(
                    value = keyDraft,
                    onValueChange = { keyDraft = it },
                    label = { Text("${selectedProvider.label} API Key") },
                    placeholder = { Text("Add API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (selectedProvider == AiProvider.CUSTOM || selectedProvider == AiProvider.OPENROUTER) {
                    OutlinedTextField(
                        value = endpointDraft,
                        onValueChange = { endpointDraft = it },
                        label = { Text("API Endpoint URL") },
                        placeholder = { Text(selectedProvider.defaultEndpoint) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Text(
                    when (selectedProvider) {
                        AiProvider.GEMINI -> "Free tier available at aistudio.google.com."
                        AiProvider.OPENAI -> "Obtain keys from platform.openai.com."
                        AiProvider.ANTHROPIC -> "Obtain keys from console.anthropic.com."
                        AiProvider.GROQ -> "Ultra-fast inference at console.groq.com."
                        AiProvider.OPENROUTER -> "Access hundreds of models at openrouter.ai."
                        AiProvider.CUSTOM -> "Any standard OpenAI-compatible API base URL."
                    } + " Keys are saved locally on your device only.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    GeminiStudyService.saveProvider(context, selectedProvider)
                    GeminiStudyService.saveApiKey(context, keyDraft.trim())
                    GeminiStudyService.saveModel(context, modelDraft.trim())
                    if (selectedProvider == AiProvider.CUSTOM || selectedProvider == AiProvider.OPENROUTER) {
                        GeminiStudyService.saveCustomEndpoint(context, endpointDraft.trim())
                    }
                    Toast.makeText(
                        context,
                        if (keyDraft.isNotBlank()) "${selectedProvider.label} API key saved!" else "API key cleared",
                        Toast.LENGTH_SHORT
                    ).show()
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
internal fun VocabularyEntryRow(
    entry: VocabularyEntry,
    document: SavedDocument,
    onOpenDocumentAt: (SavedDocument, Int) -> Unit,
    onRemoveVocabularyWord: (String, String) -> Unit
) {
    val context = LocalContext.current
    var itemMenuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Vocabulary Word?") },
            text = { Text("Are you sure you want to remove \"${entry.word}\" from your vocabulary list?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onRemoveVocabularyWord(document.id, entry.word)
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.word, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                if (!entry.pronunciation.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = entry.pronunciation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
            Text(entry.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!entry.contextSentence.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "\"${entry.contextSentence}\"",
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = entry.source,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onOpenDocumentAt(document, entry.sentenceIndex) }
            )
        }

        Box {
            IconButton(onClick = { itemMenuExpanded = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Entry Actions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = itemMenuExpanded,
                onDismissRequest = { itemMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Share as text") },
                    onClick = {
                        itemMenuExpanded = false
                        shareVocabularyAsWords(
                            context = context,
                            bookTitle = document.title,
                            entry = entry
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Share as image") },
                    onClick = {
                        itemMenuExpanded = false
                        shareVocabularyAsImage(
                            context = context,
                            bookTitle = document.title,
                            authorName = "",
                            entry = entry
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Copy") },
                    onClick = {
                        itemMenuExpanded = false
                        val vocabText = buildString {
                            append(entry.word)
                            if (!entry.pronunciation.isNullOrBlank()) append(" (${entry.pronunciation})")
                            append("\n").append(entry.explanation)
                            if (!entry.contextSentence.isNullOrBlank()) append("\n\"${entry.contextSentence}\"")
                        }
                        copyTextToClipboard(context, "Vocabulary Word", vocabText)
                    },
                    leadingIcon = { Icon(Icons.Filled.ContentPaste, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        itemMenuExpanded = false
                        showDeleteConfirm = true
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                )
            }
        }
    }
}


