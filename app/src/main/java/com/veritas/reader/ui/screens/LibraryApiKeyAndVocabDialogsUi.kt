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
            Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_ai_provider_key_setup), style = MaterialTheme.typography.titleMedium)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_connect_any_ai_model_to_generate_flashcards_quizzes_and_study_sum),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_model),
                    style = MaterialTheme.typography.labelSmall,
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
                    label = { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_model_name_id)) },
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
                        androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_provider),
                        style = MaterialTheme.typography.labelSmall,
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
                    label = { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.l10n_dynamic_081, com.veritas.reader.ui.screens.localizedUiValue(selectedProvider.label))) },
                    placeholder = { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_add_api_key)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (selectedProvider == AiProvider.CUSTOM || selectedProvider == AiProvider.OPENROUTER) {
                    OutlinedTextField(
                        value = endpointDraft,
                        onValueChange = { endpointDraft = it },
                        label = { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_api_endpoint_url)) },
                        placeholder = { Text(selectedProvider.defaultEndpoint) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Text(
                    when (selectedProvider) {
                        AiProvider.GEMINI -> androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_free_tier_available_at_aistudio_google_com)
                        AiProvider.OPENAI -> androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_obtain_keys_from_platform_openai_com)
                        AiProvider.ANTHROPIC -> androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_obtain_keys_from_console_anthropic_com)
                        AiProvider.GROQ -> androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_ultra_fast_inference_at_console_groq_com)
                        AiProvider.OPENROUTER -> androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_access_hundreds_of_models_at_openrouter_ai)
                        AiProvider.CUSTOM -> androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_any_standard_openai_compatible_api_base_url)
                    } + androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_keys_are_saved_locally_on_your_device_only),
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
                Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_cancel)) }
        }
    )
}

@Composable
internal fun VocabularyEntryRow(
    entry: VocabularyEntry,
    document: SavedDocument,
    onOpenDocumentAt: (SavedDocument, Int) -> Unit,
    onRemoveVocabularyWord: (String, String) -> Unit,
    onRefreshVocabularyMeaning: (String, VocabularyEntry) -> Unit
) {
    val context = LocalContext.current
    var itemMenuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_delete_vocabulary_word)) },
            text = { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.l10n_dynamic_082, com.veritas.reader.ui.screens.localizedUiValue(entry.word))) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onRemoveVocabularyWord(document.id, entry.word)
                    }
                ) {
                    Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_cancel)) }
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.word, style = MaterialTheme.typography.bodyMedium)
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
            entry.meaningSourceUrl?.let { url ->
                Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_definition_wiktionary), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                    })
            }
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
                modifier = Modifier.clickable(enabled = entry.sentenceIndex >= 0) { onOpenDocumentAt(document, entry.sentenceIndex) }
            )
        }

        Box {
            IconButton(onClick = { itemMenuExpanded = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_entry_actions),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = itemMenuExpanded,
                onDismissRequest = { itemMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(if (com.veritas.reader.VocabularyLookup.needsDefinition(entry.explanation)) androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_find_meaning) else androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_refresh_meaning)) },
                    onClick = { itemMenuExpanded = false; onRefreshVocabularyMeaning(document.id, entry) },
                    leadingIcon = { Icon(Icons.Filled.Language, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_share_as_text)) },
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
                    text = { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_share_as_image)) },
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
                    text = { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_copy)) },
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
                    text = { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_delete)) },
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
