package com.veritas.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun TranslationToolsDialog(
    document: ReaderDocument,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onSend: (String, TranslationLauncher.Mode) -> Unit
) {
    val context = LocalContext.current
    var targetLanguage by remember { mutableStateOf("English") }
    var selectedMode by remember { mutableStateOf(TranslationLauncher.Mode.CURRENT_SECTION) }
    var translatedTextInput by remember { mutableStateOf("") }
    val currentSentence = remember(document, currentIndex) {
        document.chunks.getOrNull(currentIndex).orEmpty().trim()
    }
    val popularLanguages = listOf("English", "Spanish", "French", "German", "Chinese", "Japanese", "Arabic", "Portuguese", "Italian", "Russian")

    val priorityApps = remember {
        listOf(
            Triple("Google", "com.google.android.apps.translate", Icons.Outlined.Translate),
            Triple("ChatGPT", "com.openai.chatgpt", IconChatGPT),
            Triple("Gemini", "com.google.android.apps.bard", IconGemini),
            Triple("Claude", "com.anthropic.claude", IconClaude),
            Triple("Copilot", "com.microsoft.copilot", IconCopilot),
            Triple("Perplexity", "ai.perplexity.app.android", IconPerplexity),
            Triple("Grok", "com.x.android", IconGrok)
        )
    }

    fun executeHandoff(targetPackage: String?) {
        TranslationLauncher.launchTarget(
            context = context,
            targetPackage = targetPackage,
            title = document.title,
            chunks = document.chunks,
            currentIndex = currentIndex,
            targetLanguage = targetLanguage,
            mode = selectedMode
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { Button(onClick = onDismiss, shape = RoundedCornerShape(50)) { Text("Done") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Translation handoff")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Send structured translation prompts to priority translator & AI apps, or listen to audio read-out of original and translated text.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Priority Apps Quick Actions
                Text(
                    "Priority translation targets",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    priorityApps.forEach { (label, pkg, icon) ->
                        OutlinedButton(
                            onClick = { executeHandoff(pkg) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(label, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Button(
                        onClick = { executeHandoff(null) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("All Apps", style = MaterialTheme.typography.labelMedium)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                // Audio Read-Out Section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text(
                                "Spoken Audio Read-Out",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Play Original Sentence
                        if (currentSentence.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Original Sentence ${currentIndex + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        currentSentence.take(90) + if (currentSentence.length > 90) "…" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        VoiceManager.previewVoice(
                                            context = context,
                                            enginePackage = "",
                                            voiceName = "",
                                            text = currentSentence
                                        )
                                    }
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Read Original", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                        // Play Spoken Translation
                        Text(
                            "Translated Text (type or paste to listen)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedTextField(
                            value = translatedTextInput,
                            onValueChange = { translatedTextInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Paste translated result here...") },
                            maxLines = 3,
                            shape = RoundedCornerShape(8.dp)
                        )
                        Button(
                            onClick = {
                                if (translatedTextInput.isNotBlank()) {
                                    VoiceManager.previewVoice(
                                        context = context,
                                        enginePackage = "",
                                        voiceName = "",
                                        text = translatedTextInput
                                    )
                                }
                            },
                            enabled = translatedTextInput.isNotBlank(),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Read Out Translation")
                        }
                    }
                }

                Text(
                    "Target language",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Quick Language Selection Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    popularLanguages.forEach { lang ->
                        val isSelected = targetLanguage.equals(lang, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { targetLanguage = lang },
                            label = { Text(lang) },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }

                OutlinedTextField(
                    value = targetLanguage,
                    onValueChange = { targetLanguage = it },
                    label = { Text("Language name or code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        Icon(Icons.Outlined.Translate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                )

                Text(
                    "Translation scope & mode",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Mode 1: Current Sentence
                TranslationModeCard(
                    title = "Translate current sentence",
                    description = "Accurate translation of sentence ${currentIndex + 1} into $targetLanguage.",
                    icon = Icons.Outlined.Translate,
                    isSelected = selectedMode == TranslationLauncher.Mode.CURRENT_SECTION,
                    onClick = {
                        selectedMode = TranslationLauncher.Mode.CURRENT_SECTION
                        executeHandoff(null)
                    }
                )

                // Mode 2: Full Document
                TranslationModeCard(
                    title = "Translate full document",
                    description = "Extract and send all ${document.chunks.size} sentences formatted for translation.",
                    icon = Icons.AutoMirrored.Outlined.Article,
                    isSelected = selectedMode == TranslationLauncher.Mode.DOCUMENT,
                    onClick = {
                        selectedMode = TranslationLauncher.Mode.DOCUMENT
                        executeHandoff(null)
                    }
                )

                // Mode 3: Bilingual Sentence
                TranslationModeCard(
                    title = "Bilingual current sentence",
                    description = "Side-by-side parallel text comparing original with $targetLanguage.",
                    icon = Icons.Outlined.Language,
                    isSelected = selectedMode == TranslationLauncher.Mode.BILINGUAL_SECTION,
                    onClick = {
                        selectedMode = TranslationLauncher.Mode.BILINGUAL_SECTION
                        executeHandoff(null)
                    }
                )

                // Mode 4: Bilingual Full Document
                TranslationModeCard(
                    title = "Bilingual full document",
                    description = "Structured interlinear lines for the entire document.",
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    isSelected = selectedMode == TranslationLauncher.Mode.BILINGUAL_DOCUMENT,
                    onClick = {
                        selectedMode = TranslationLauncher.Mode.BILINGUAL_DOCUMENT
                        executeHandoff(null)
                    }
                )
            }
        }
    )
}

@Composable
private fun TranslationModeCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
