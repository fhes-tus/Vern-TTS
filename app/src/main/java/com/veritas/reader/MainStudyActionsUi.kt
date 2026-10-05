package com.veritas.reader


import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch


@Composable
internal fun AiStudyClipboardBanner(
    detectedClipboardText: String?,
    bannerDismissed: Boolean,
    detectedCards: List<Flashcard>,
    detectedQuiz: List<QuizQuestion>,
    document: ReaderDocument,
    currentPage: Int,
    onImportFlashcards: (String, List<Flashcard>) -> Unit,
    onSaveQuiz: ((QuizSet) -> Unit)?,
    onSetResultPreview: (ModernStudyResultPreview) -> Unit,
    onDismissBanner: () -> Unit
) {
    val context = LocalContext.current
    // Clipboard Auto-Detection Banner
    if (detectedClipboardText != null && !bannerDismissed) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Study Content Found on Clipboard!",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    when {
                        detectedCards.isNotEmpty() -> "✨ Detected ${detectedCards.size} Q&A flashcards ready to import"
                        detectedQuiz.isNotEmpty() -> "🎯 Detected ${detectedQuiz.size} practice exam questions ready to play"
                        else -> "📝 Detected study notes / explanation on clipboard"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            when {
                                detectedCards.isNotEmpty() -> {
                                    val setName = "${document.title} - Page $currentPage"
                                    onImportFlashcards(setName, detectedCards)
                                    onSetResultPreview(ModernStudyResultPreview.Flashcards(setName, detectedCards))
                                    onDismissBanner()
                                    Toast.makeText(context, "Imported ${detectedCards.size} flashcards!", Toast.LENGTH_SHORT).show()
                                }
                                detectedQuiz.isNotEmpty() -> {
                                    val newQuiz = QuizSet(
                                        title = "${document.title} - Page $currentPage Quiz",
                                        documentId = document.id.orEmpty(),
                                        questions = detectedQuiz
                                    )
                                    onSaveQuiz?.invoke(newQuiz)
                                    onSetResultPreview(ModernStudyResultPreview.Quiz(newQuiz))
                                    onDismissBanner()
                                }
                                else -> {
                                    onSetResultPreview(
                                        ModernStudyResultPreview.NoteText(
                                            AiPromptType.STUDY_NOTES,
                                            "Imported Study Notes",
                                            detectedClipboardText.orEmpty()
                                        )
                                    )
                                    onDismissBanner()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Review & Import", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { onDismissBanner() },
                        shape = RoundedCornerShape(50)
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }


}


@Composable
internal fun AiStudyCoreToolsCards(
    document: ReaderDocument,
    currentPage: Int,
    safeIndex: Int,
    scopeText: String,
    selectedScope: AiPromptScope,
    hasGeminiKey: Boolean,
    generationBusy: Boolean = false,
    defaultAiName: String,
    onImportFlashcards: (String, List<Flashcard>) -> Unit,
    onSaveQuiz: ((QuizSet) -> Unit)?,
    onCopyText: (String, String) -> Unit,
    onSaveAiResultAsNote: (String) -> Unit,
    onGenerateInAppFlashcards: ((String, Int, (Boolean, String) -> Unit) -> Unit)?,
    onGenerateInAppQuiz: ((String, Int, (Boolean, String, QuizSet?) -> Unit) -> Unit)?,
    onGenerateInAppSummary: ((String, (Boolean, String) -> Unit) -> Unit)?,
    onGenerateInAppExplanation: ((String, String, (Boolean, String) -> Unit) -> Unit)?,
    onGenerateInAppStudyGuide: ((String, (Boolean, String) -> Unit) -> Unit)?,
    onSetGenerating: (Boolean, String) -> Unit,
    onSetResultPreview: (ModernStudyResultPreview) -> Unit,
    onExternalHandoff: (AiPromptType) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 5 Core Modern Study Tools
    ModernToolActionCard(
        title = "AI Flashcard Deck",
        badge = "SM-2 Active Recall",
        description = "Extracts definitions, formulas and key facts into interactive spaced repetition cards.",
        icon = Icons.Outlined.Style,
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
        iconTint = MaterialTheme.colorScheme.secondary,
        primaryActionLabel = if (hasGeminiKey) "Generate Flashcards" else "Open in $defaultAiName",
        isPrimaryInApp = hasGeminiKey,
        enabled = !generationBusy,
        onPrimaryAction = {
            if (hasGeminiKey) {
                onSetGenerating(true, "Creating flashcards with ${GeminiStudyService.getProvider(context).label}…")
                if (onGenerateInAppFlashcards != null) {
                    onGenerateInAppFlashcards(scopeText, 8) { success, msg ->
                        onSetGenerating(false, "")
                        if (success) {
                            val cards = AiResultParser.parseFlashcards(msg)
                            val setName = "${document.title} - Page $currentPage"
                            onImportFlashcards(setName, cards)
                            onSetResultPreview(ModernStudyResultPreview.Flashcards(setName, cards))
                            Toast.makeText(context, "Flashcards ready!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    coroutineScope.launch {
                        val res = GeminiStudyService.generateFlashcards(
                            apiKey = GeminiStudyService.getApiKey(context),
                            documentTitle = document.title,
                            textContext = scopeText,
                            cardCount = 8
                        )
                        onSetGenerating(false, "")
                        res.onSuccess { cards ->
                            val setName = "${document.title} - Page $currentPage"
                            onImportFlashcards(setName, cards)
                            onSetResultPreview(ModernStudyResultPreview.Flashcards(setName, cards))
                            Toast.makeText(context, "Generated ${cards.size} flashcards!", Toast.LENGTH_SHORT).show()
                        }.onFailure { err ->
                            Toast.makeText(context, err.message ?: "Generation failed", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                onExternalHandoff(AiPromptType.FLASHCARDS)
            }
        },
        onCopyPrompt = {
            val prompt = AiPromptLauncher.buildPrompt(
                title = document.title,
                chunks = document.chunks,
                currentIndex = safeIndex,
                type = AiPromptType.FLASHCARDS,
                scope = selectedScope
            )
            onCopyText("Vern Flashcard Prompt", prompt)
        },
        onExternalLaunch = { onExternalHandoff(AiPromptType.FLASHCARDS) }
    )

    ModernToolActionCard(
        title = "Practice Exam Quiz",
        badge = "Interactive Studio",
        description = "Generates multiple-choice exam questions with instant scoring and detailed explanations.",
        icon = Icons.Outlined.Quiz,
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        iconTint = MaterialTheme.colorScheme.primary,
        primaryActionLabel = if (hasGeminiKey) "Create Practice Quiz" else "Open in $defaultAiName",
        isPrimaryInApp = hasGeminiKey,
        enabled = !generationBusy,
        onPrimaryAction = {
            if (hasGeminiKey) {
                onSetGenerating(true, "Creating exam questions with ${GeminiStudyService.getProvider(context).label}…")
                if (onGenerateInAppQuiz != null) {
                    onGenerateInAppQuiz(scopeText, 5) { success, msg, quiz ->
                        onSetGenerating(false, "")
                        if (success && quiz != null) {
                            onSaveQuiz?.invoke(quiz)
                            onSetResultPreview(ModernStudyResultPreview.Quiz(quiz))
                            Toast.makeText(context, "Quiz ready!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    coroutineScope.launch {
                        val res = GeminiStudyService.generateQuiz(
                            apiKey = GeminiStudyService.getApiKey(context),
                            documentTitle = document.title,
                            textContext = scopeText,
                            questionCount = 5
                        )
                        onSetGenerating(false, "")
                        res.onSuccess { questions ->
                            val newQuiz = QuizSet(
                                title = "${document.title} - Page $currentPage Quiz",
                                documentId = document.id.orEmpty(),
                                questions = questions
                            )
                            onSaveQuiz?.invoke(newQuiz)
                            onSetResultPreview(ModernStudyResultPreview.Quiz(newQuiz))
                            Toast.makeText(context, "Created ${questions.size} questions!", Toast.LENGTH_SHORT).show()
                        }.onFailure { err ->
                            Toast.makeText(context, err.message ?: "Quiz creation failed", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                onExternalHandoff(AiPromptType.QUIZ)
            }
        },
        onCopyPrompt = {
            val prompt = AiPromptLauncher.buildPrompt(
                title = document.title,
                chunks = document.chunks,
                currentIndex = safeIndex,
                type = AiPromptType.QUIZ,
                scope = selectedScope
            )
            onCopyText("Vern Quiz Prompt", prompt)
        },
        onExternalLaunch = { onExternalHandoff(AiPromptType.QUIZ) }
    )

    ModernToolActionCard(
        title = "Executive Summary",
        badge = "BLUF Framework",
        description = "High-yield summary with core thesis, 4-6 bullet takeaways with citations, and conclusions.",
        icon = Icons.Outlined.Summarize,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
        iconTint = MaterialTheme.colorScheme.tertiary,
        primaryActionLabel = if (hasGeminiKey) "Synthesize Summary" else "Open in $defaultAiName",
        isPrimaryInApp = hasGeminiKey,
        enabled = !generationBusy,
        onPrimaryAction = {
            if (hasGeminiKey) {
                onSetGenerating(true, "Creating executive summary...")
                if (onGenerateInAppSummary != null) {
                    onGenerateInAppSummary(scopeText) { success, result ->
                        onSetGenerating(false, "")
                        if (success) {
                            onSetResultPreview(ModernStudyResultPreview.NoteText(
                                AiPromptType.SUMMARY,
                                "Executive Summary (Page $currentPage)",
                                result
                            ))
                        } else {
                            Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    coroutineScope.launch {
                        val res = GeminiStudyService.generateStudySummary(
                            apiKey = GeminiStudyService.getApiKey(context),
                            documentTitle = document.title,
                            textContext = scopeText
                        )
                        onSetGenerating(false, "")
                        res.onSuccess { summary ->
                            onSetResultPreview(ModernStudyResultPreview.NoteText(
                                AiPromptType.SUMMARY,
                                "Executive Summary (Page $currentPage)",
                                summary
                            ))
                        }.onFailure { err ->
                            Toast.makeText(context, err.message ?: "Summary failed", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                onExternalHandoff(AiPromptType.SUMMARY)
            }
        },
        onCopyPrompt = {
            val prompt = AiPromptLauncher.buildPrompt(
                title = document.title,
                chunks = document.chunks,
                currentIndex = safeIndex,
                type = AiPromptType.SUMMARY,
                scope = selectedScope
            )
            onCopyText("Vern Summary Prompt", prompt)
        },
        onExternalLaunch = { onExternalHandoff(AiPromptType.SUMMARY) }
    )

    ModernToolActionCard(
        title = "Explain & Simplify",
        badge = "Feynman Technique",
        description = "Deconstructs difficult passages into plain English with relatable analogies and definitions.",
        icon = Icons.Outlined.School,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        iconTint = MaterialTheme.colorScheme.primary,
        primaryActionLabel = if (hasGeminiKey) "Explain Passage" else "Open in $defaultAiName",
        isPrimaryInApp = hasGeminiKey,
        enabled = !generationBusy,
        onPrimaryAction = {
            if (hasGeminiKey) {
                onSetGenerating(true, "Explaining concepts...")
                val targetPassage = document.chunks.getOrNull(safeIndex).orEmpty()
                if (onGenerateInAppExplanation != null) {
                    onGenerateInAppExplanation(scopeText, targetPassage) { success, result ->
                        onSetGenerating(false, "")
                        if (success) {
                            onSetResultPreview(ModernStudyResultPreview.NoteText(
                                AiPromptType.EXPLAIN_SECTION,
                                "Feynman Explanation",
                                result
                            ))
                        } else {
                            Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    coroutineScope.launch {
                        val res = GeminiStudyService.generateExplanation(
                            apiKey = GeminiStudyService.getApiKey(context),
                            documentTitle = document.title,
                            textContext = scopeText,
                            targetPassage = targetPassage
                        )
                        onSetGenerating(false, "")
                        res.onSuccess { explanation ->
                            onSetResultPreview(ModernStudyResultPreview.NoteText(
                                AiPromptType.EXPLAIN_SECTION,
                                "Feynman Explanation",
                                explanation
                            ))
                        }.onFailure { err ->
                            Toast.makeText(context, err.message ?: "Explanation failed", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                onExternalHandoff(AiPromptType.EXPLAIN_SECTION)
            }
        },
        onCopyPrompt = {
            val prompt = AiPromptLauncher.buildPrompt(
                title = document.title,
                chunks = document.chunks,
                currentIndex = safeIndex,
                type = AiPromptType.EXPLAIN_SECTION,
                scope = selectedScope
            )
            onCopyText("Vern Explainer Prompt", prompt)
        },
        onExternalLaunch = { onExternalHandoff(AiPromptType.EXPLAIN_SECTION) }
    )

    ModernToolActionCard(
        title = "Study Guide & Cheatsheet",
        badge = "Structured Framework",
        description = "Generates organized headings, definitions glossary, core formulas and self-review checklist.",
        icon = Icons.Outlined.Description,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        iconTint = MaterialTheme.colorScheme.secondary,
        primaryActionLabel = if (hasGeminiKey) "Build Cheatsheet" else "Open in $defaultAiName",
        isPrimaryInApp = hasGeminiKey,
        enabled = !generationBusy,
        onPrimaryAction = {
            if (hasGeminiKey) {
                onSetGenerating(true, "Building study study guide...")
                if (onGenerateInAppStudyGuide != null) {
                    onGenerateInAppStudyGuide(scopeText) { success, result ->
                        onSetGenerating(false, "")
                        if (success) {
                            onSetResultPreview(ModernStudyResultPreview.NoteText(
                                AiPromptType.STUDY_NOTES,
                                "Study Cheatsheet",
                                result
                            ))
                        } else {
                            Toast.makeText(context, result, Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    coroutineScope.launch {
                        val res = GeminiStudyService.generateStudyGuide(
                            apiKey = GeminiStudyService.getApiKey(context),
                            documentTitle = document.title,
                            textContext = scopeText
                        )
                        onSetGenerating(false, "")
                        res.onSuccess { guide ->
                            onSetResultPreview(ModernStudyResultPreview.NoteText(
                                AiPromptType.STUDY_NOTES,
                                "Study Cheatsheet",
                                guide
                            ))
                        }.onFailure { err ->
                            Toast.makeText(context, err.message ?: "Cheatsheet build failed", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                onExternalHandoff(AiPromptType.STUDY_NOTES)
            }
        },
        onCopyPrompt = {
            val prompt = AiPromptLauncher.buildPrompt(
                title = document.title,
                chunks = document.chunks,
                currentIndex = safeIndex,
                type = AiPromptType.STUDY_NOTES,
                scope = selectedScope
            )
            onCopyText("Vern Study Guide Prompt", prompt)
        },
        onExternalLaunch = { onExternalHandoff(AiPromptType.STUDY_NOTES) }
    )


}


@Composable
internal fun AiStudyManualAccordion(
    document: ReaderDocument,
    onImportFlashcards: (String, List<Flashcard>) -> Unit,
    onSaveQuiz: ((QuizSet) -> Unit)?,
    onSaveAiResultAsNote: (String) -> Unit,
    onSetResultPreview: (ModernStudyResultPreview) -> Unit,
    onShowPasteFlashcards: () -> Unit,
    onShowPasteQuiz: () -> Unit,
    onShowGeminiSetup: () -> Unit
) {
    val context = LocalContext.current
    var showManualTools by remember { mutableStateOf(false) }
    var manualInputDraft by remember { mutableStateOf("") }

                    // Manual Importer & Advanced Accordion
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showManualTools = !showManualTools },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Manual Importers & AI Settings",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(
                                    imageVector = if (showManualTools) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (showManualTools) {
                                OutlinedTextField(
                                    value = manualInputDraft,
                                    onValueChange = { manualInputDraft = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp),
                                    label = { Text("Paste AI response here") },
                                    placeholder = { Text("Paste flashcards, quiz, or study notes...") },
                                    shape = RoundedCornerShape(12.dp)
                                )

                                val parsedCards = remember(manualInputDraft) { AiResultParser.parseFlashcards(manualInputDraft) }
                                val parsedQuiz = remember(manualInputDraft) { AiResultParser.parseQuiz(manualInputDraft) }

                                if (manualInputDraft.isNotBlank()) {
                                    Text(
                                        when {
                                            parsedCards.isNotEmpty() -> "✓ Recognized ${parsedCards.size} Flashcards"
                                            parsedQuiz.isNotEmpty() -> "✓ Recognized ${parsedQuiz.size} Quiz Questions"
                                            else -> "✓ Recognized Study Notes / Summary"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = {
                                                when {
                                                    parsedCards.isNotEmpty() -> {
                                                        val setName = "${document.title} - Imported"
                                                        onImportFlashcards(setName, parsedCards)
                                                        onSetResultPreview(ModernStudyResultPreview.Flashcards(setName, parsedCards))
                                                        manualInputDraft = ""
                                                    }
                                                    parsedQuiz.isNotEmpty() -> {
                                                        val newQuiz = QuizSet(
                                                            title = "${document.title} - Imported Quiz",
                                                            documentId = document.id.orEmpty(),
                                                            questions = parsedQuiz
                                                        )
                                                        onSaveQuiz?.invoke(newQuiz)
                                                        onSetResultPreview(ModernStudyResultPreview.Quiz(newQuiz))
                                                        manualInputDraft = ""
                                                    }
                                                    else -> {
                                                        onSaveAiResultAsNote(manualInputDraft)
                                                        manualInputDraft = ""
                                                        Toast.makeText(context, "Saved as sentence note!", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(50)
                                        ) {
                                            Text("Import Recognized Content")
                                        }
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = { onShowPasteFlashcards() },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text("Card Importer", fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { onShowPasteQuiz() },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text("Quiz Importer", fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = { onShowGeminiSetup() },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text("Gemini Key", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }


}

