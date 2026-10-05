package com.veritas.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.veritas.reader.ui.screens.FlashcardViewerDialog
import com.veritas.reader.ui.screens.GeminiApiKeyDialog
import java.io.File


@Composable
internal fun AiFreeModeDialog(
    documentCount: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("AI & study mode") },
        text = {
            Column(
                modifier = Modifier
                    .height(520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Free AI approach",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "Vern does not bundle a large offline model and does not require an API key. It prepares prompts for the AI apps already installed on this phone.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                InfoStepCard(title = "1. AI app handoff") {
                    Text("Open a document, tap Reader tools → AI, choose a task, then send the prepared prompt to ChatGPT, Gemini, Claude, Copilot, Perplexity, or another installed app. The prompt is also copied to your clipboard.")
                }
                InfoStepCard(title = "2. Paste the reply back") {
                    Text("Flashcard and quiz replies can be pasted straight back into Vern — cards join your spaced-repetition deck and quizzes become an in-app scored test.")
                }
                InfoStepCard(title = "3. Base app stays lighter") {
                    Text("No heavy local AI model is bundled in the base app. A real offline model can be optional later as a separate downloadable pack.")
                }
                Text(
                    "Current library: $documentCount saved reading${if (documentCount == 1) "" else "s"}.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
internal fun AiCenterDialog(
    installedAiCount: Int,
    askAiSettings: AskAiSettings,
    documentCount: Int,
    onOpenAskAiSettings: () -> Unit,
    onOpenStudyTools: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val provider = remember { GeminiStudyService.getProvider(context) }
    val configured = remember { GeminiStudyService.hasApiKey(context) }
    com.veritas.reader.ui.screens.FullScreenSettingsScaffold(title = "AI tools", onBack = onDismiss) {
        Text("Work with a passage from your reading, or send it to an assistant you already use.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(shape = VeritasPackStyle.cardShape(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Study a reading", style = MaterialTheme.typography.titleLarge)
                Text("Create flashcards, quizzes, summaries and explanations. Review the results alongside the source text.")
                Text(if (configured) "${provider.label} · API key saved" else "In-app generation needs your provider's API key. External assistant apps can be used without adding one here.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onOpenStudyTools, modifier = Modifier.fillMaxWidth(), shape = VeritasPackStyle.chipShape()) { Text("Open study tools") }
                if (documentCount == 0) Text("Add a reading to choose a passage.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(shape = VeritasPackStyle.cardShape(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ask another assistant", style = MaterialTheme.typography.titleLarge)
                Text("${installedAiCount} compatible app${if (installedAiCount == 1) "" else "s"} detected. Choose your assistant and the instructions to send with selected text.")
                Text("A handoff opens the other app; its response is not automatically read or imported by Vern.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onOpenAskAiSettings, modifier = Modifier.fillMaxWidth(), shape = VeritasPackStyle.chipShape()) { Text("Assistant and prompt settings") }
            }
        }
        CustomAiHandoffCard(includeContextField = true) { instructions, passage ->
            AiPromptLauncher.launchCustomPrompt(context, instructions, passage, askAiSettings)
        }
        Text("AI output can contain mistakes. Keep the original passage available when using generated study material.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ExportAudioStatusDialog(
    inProgress: Boolean,
    message: String?,
    file: File?,
    onShare: (File) -> Unit,
    onCancel: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
        title = { Text("Export audio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (inProgress) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("Creating audio from this reading...")
                    }
                }
                Text(
                    message ?: "Preparing export...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (file != null) {
                    Text(file.name, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            if (file != null) {
                Button(onClick = { onShare(file) }) { Text("Share") }
            }
        },
        dismissButton = {
            if (inProgress && onCancel != null) {
                TextButton(onClick = onCancel) { Text("Cancel") }
            } else {
                TextButton(onClick = onDismiss, enabled = !inProgress) { Text("Close") }
            }
        }
    )
}

@Composable
internal fun AiAppStudyDialog(
    document: ReaderDocument,
    currentIndex: Int,
    templates: List<AiPromptTemplate> = emptyList(),
    history: List<AiPromptHistoryEntry> = emptyList(),
    askAiSettings: AskAiSettings? = null,
    onUpdateAskAiSettings: ((AskAiSettings) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSendToAiApp: (AiPromptType, String, AiPromptScope, IntRange?) -> Unit,
    onSaveTemplate: (String, String) -> Unit = { _, _ -> },
    onDeleteTemplate: (String) -> Unit = {},
    onClearHistory: () -> Unit = {},
    onCopyText: (String, String) -> Unit,
    onSaveAiResultAsNote: (String) -> Unit,
    onImportFlashcards: (String, List<Flashcard>) -> Unit,
    onGenerateInAppFlashcards: ((String, Int, (Boolean, String) -> Unit) -> Unit)? = null,
    onGenerateInAppQuiz: ((String, Int, (Boolean, String, QuizSet?) -> Unit) -> Unit)? = null,
    onGenerateInAppSummary: ((String, (Boolean, String) -> Unit) -> Unit)? = null,
    onGenerateInAppExplanation: ((String, String, (Boolean, String) -> Unit) -> Unit)? = null,
    onGenerateInAppStudyGuide: ((String, (Boolean, String) -> Unit) -> Unit)? = null,
    onSaveQuiz: ((QuizSet) -> Unit)? = null,
    onRecordQuizScore: ((String, Int) -> Unit)? = null,
    onRateFlashcard: ((String, String) -> Unit)? = null,
    onOpenStudyHub: (() -> Unit)? = null,
    onCancelGeneration: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { onCancelGeneration() } }
    val clipboard = remember { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    val safeIndex = if (document.chunks.isEmpty()) 0 else currentIndex.coerceIn(0, document.chunks.lastIndex)
    val currentPage = remember(document, currentIndex) {
        val model = ReaderTextModelCache.get(document.id, document.rawText, document.pageCount)
        val sentence = model.sentences.getOrNull(currentIndex)
        sentence?.pageNumber?.coerceAtLeast(1) ?: 1
    }

    var selectedScope by remember { mutableStateOf(AiPromptScope.CURRENT_PAGE) }
    var customStartPage by remember { mutableStateOf(currentPage.toString()) }
    var customEndPage by remember { mutableStateOf(minOf(currentPage + 2, document.pageCount.coerceAtLeast(1)).toString()) }

    var showGeminiSetup by remember { mutableStateOf(false) }
    var showAssistantChooser by remember { mutableStateOf(false) }
    var showPasteFlashcards by remember { mutableStateOf(false) }
    var showPasteQuiz by remember { mutableStateOf(false) }

    var isGenerating by remember { mutableStateOf(false) }
    var generatingStatus by remember { mutableStateOf("Generating with AI...") }

    var activeResultPreview by remember { mutableStateOf<ModernStudyResultPreview?>(null) }
    var activeQuizQuestions by remember { mutableStateOf<List<QuizQuestion>?>(null) }
    var activeQuizId by remember { mutableStateOf<String?>(null) }
    var activeStudyDeckCards by remember { mutableStateOf<Pair<String, List<Flashcard>>?>(null) }

    // Clipboard auto-detection
    var detectedClipboardText by remember { mutableStateOf<String?>(null) }
    var detectedCards by remember { mutableStateOf<List<Flashcard>>(emptyList()) }
    var detectedQuiz by remember { mutableStateOf<List<QuizQuestion>>(emptyList()) }
    var bannerDismissed by remember { mutableStateOf(false) }

    fun checkClipboard() {
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString().orEmpty().trim()
            if (text.length > 20 && text != detectedClipboardText) {
                val quiz = AiResultParser.parseQuiz(text)
                val isLikelyQuiz = quiz.isNotEmpty() || AiResultParser.isLikelyQuiz(text)
                val cards = if (isLikelyQuiz) emptyList() else AiResultParser.parseFlashcards(text)
                if (cards.isNotEmpty() || quiz.isNotEmpty() || (text.length > 50 && !text.startsWith("You are an expert"))) {
                    detectedClipboardText = text
                    detectedCards = cards
                    detectedQuiz = quiz
                    bannerDismissed = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        checkClipboard()
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                checkClipboard()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val hasGeminiKey = remember(context, showGeminiSetup) {
        GeminiStudyService.hasApiKey(context)
    }

    val installedAiList = remember(context) { installedAiOptions(context) }
    val defaultAiId = remember(askAiSettings, installedAiList) {
        if (askAiSettings == null || askAiSettings.assistantId.isBlank() || askAiSettings.assistantId == "chooser") {
            if (installedAiList.isNotEmpty()) installedAiList.first().first.id else "chooser"
        } else {
            askAiSettings.assistantId
        }
    }
    val defaultAiName = remember(askAiSettings, installedAiList) {
        if (askAiSettings == null || askAiSettings.assistantId.isBlank() || askAiSettings.assistantId == "chooser") {
            if (installedAiList.isNotEmpty()) installedAiList.first().first.label else "Share Menu"
        } else {
            aiAssistantOptions.firstOrNull { it.id == askAiSettings.assistantId }?.label ?: "Share Menu"
        }
    }

    val currentRange = remember(selectedScope, customStartPage, customEndPage, document.pageCount) {
        if (selectedScope == AiPromptScope.CUSTOM_PAGE_RANGE) {
            val start = customStartPage.toIntOrNull() ?: 1
            val end = customEndPage.toIntOrNull() ?: document.pageCount
            minOf(start, end).coerceIn(1, document.pageCount.coerceAtLeast(1))..maxOf(start, end).coerceIn(1, document.pageCount.coerceAtLeast(1))
        } else null
    }

    val scopeSentences = remember(document, currentIndex, selectedScope, currentRange) {
        AiPromptLauncher.getSelectedSentences(document, currentIndex, selectedScope, currentRange)
    }

    val scopeText = remember(scopeSentences) {
        AiPromptLauncher.extractTextForScope(document, currentIndex, selectedScope, currentRange)
    }

    val scopeWordCount = remember(scopeSentences) {
        scopeSentences.sumOf { it.text.split(Regex("\\s+")).size }
    }

    if (showGeminiSetup) {
        GeminiApiKeyDialog(onDismiss = { showGeminiSetup = false })
    }

    if (showPasteFlashcards) {
        PasteFlashcardsDialog(
            onImport = { name, cards ->
                onImportFlashcards(name, cards)
                showPasteFlashcards = false
                activeResultPreview = ModernStudyResultPreview.Flashcards(name, cards)
            },
            onDismiss = { showPasteFlashcards = false }
        )
    }

    if (showPasteQuiz) {
        PasteQuizDialog(onDismiss = { showPasteQuiz = false })
    }

    activeQuizQuestions?.let { questions ->
        QuizPlayerDialog(
            questions = questions,
            quizTitle = "${document.title} Quiz",
            onSaveScore = { score ->
                activeQuizId?.let { id -> onRecordQuizScore?.invoke(id, score) }
            },
            onDismiss = { activeQuizQuestions = null }
        )
    }

    activeStudyDeckCards?.let { (deckTitle, cards) ->
        FlashcardViewerDialog(
            setName = deckTitle,
            cards = cards.mapIndexed { idx, card ->
                FlashcardProgress(
                    id = "${document.id.orEmpty()}_card_$idx",
                    documentId = document.id.orEmpty(),
                    front = card.front,
                    back = card.back,
                    setName = deckTitle
                )
            },
            onRate = { cardId, rating ->
                onRateFlashcard?.invoke(cardId, rating)
            },
            onDeleteCard = {},
            onDismiss = { activeStudyDeckCards = null }
        )
    }

    fun handleExternalHandoff(type: AiPromptType) {
        AiPromptLauncher.buildPrompt(
            title = document.title,
            chunks = document.chunks,
            currentIndex = PlaybackStateStore.currentIndex,
            type = type,
            scope = selectedScope
        )
        onSendToAiApp(type, "", selectedScope, currentRange)
        Toast.makeText(context, "Prompt ready in $defaultAiName. Return here when done!", Toast.LENGTH_LONG).show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Top Header Bar
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 1.dp,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column {
                                Text(
                                    "AI Study Studio",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "${document.title} • Page $currentPage of ${document.pageCount.coerceAtLeast(1)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (onOpenStudyHub != null) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.clickable {
                                        onDismiss()
                                        onOpenStudyHub()
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            "Study Hub",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = {
                                    checkClipboard()
                                    Toast.makeText(context, "Checked clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Refresh Clipboard",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Engine Status & Selector Pill
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (hasGeminiKey) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    1.dp,
                                    if (hasGeminiKey) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.clickable {
                                    if (!hasGeminiKey) showGeminiSetup = true else showAssistantChooser = true
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val activeProvider = remember(hasGeminiKey, showGeminiSetup) { GeminiStudyService.getProvider(context) }
                                    Icon(
                                        imageVector = if (hasGeminiKey) Icons.Outlined.AutoAwesome else aiAssistantIcon(defaultAiId),
                                        contentDescription = null,
                                        tint = if (hasGeminiKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        if (hasGeminiKey) "${activeProvider.label} In-App" else defaultAiName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasGeminiKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Scope Selector Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "STUDY FOCUS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "${scopeSentences.size} sentences • ~$scopeWordCount words",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val scopes = listOf(
                                    AiPromptScope.CURRENT_PAGE to "Page $currentPage",
                                    AiPromptScope.CURRENT_SENTENCE to "Sentence ${(safeIndex + 1)}",
                                    AiPromptScope.CURRENT_SECTION to "Section",
                                    AiPromptScope.WHOLE_DOCUMENT to "Whole Book",
                                    AiPromptScope.CUSTOM_PAGE_RANGE to "Pages..."
                                )
                                scopes.forEach { (scope, label) ->
                                    val isSelected = selectedScope == scope
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedScope = scope },
                                        label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                        shape = RoundedCornerShape(50)
                                    )
                                }
                            }

                            if (selectedScope == AiPromptScope.CUSTOM_PAGE_RANGE) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = customStartPage,
                                        onValueChange = { customStartPage = it.filter { ch -> ch.isDigit() } },
                                        label = { Text("From Page", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = customEndPage,
                                        onValueChange = { customEndPage = it.filter { ch -> ch.isDigit() } },
                                        label = { Text("To Page", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Quick External AI App Hand-off Chips
                    QuickExternalAiChips(
                        context = context,
                        document = document,
                        scopeText = scopeText,
                        safeIndex = safeIndex,
                        selectedScope = selectedScope
                    )

                    // Clipboard Auto-Detection Banner
                    AiStudyClipboardBanner(
                        detectedClipboardText = detectedClipboardText,
                        bannerDismissed = bannerDismissed,
                        detectedCards = detectedCards,
                        detectedQuiz = detectedQuiz,
                        document = document,
                        currentPage = currentPage,
                        onImportFlashcards = onImportFlashcards,
                        onSaveQuiz = onSaveQuiz,
                        onSetResultPreview = { activeResultPreview = it },
                        onDismissBanner = { bannerDismissed = true }
                    )
                    // In-Progress Loading Card
                    if (isGenerating) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        generatingStatus,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Waiting for ${GeminiStudyService.getProvider(context).label}…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { onCancelGeneration(); isGenerating = false }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }

                    // Interactive Results Preview (If Any)
                    activeResultPreview?.let { preview ->
                        ModernStudyResultPreviewCard(
                            preview = preview,
                            onClose = { activeResultPreview = null },
                            onStudyCards = { setName, cards ->
                                activeStudyDeckCards = setName to cards
                            },
                            onStartQuiz = { quiz ->
                                onSaveQuiz?.invoke(quiz)
                                activeQuizId = quiz.id
                                activeQuizQuestions = quiz.questions
                            },
                            onSaveNote = { text ->
                                onSaveAiResultAsNote(text)
                                Toast.makeText(context, "Saved note to sentence ${(safeIndex + 1)}!", Toast.LENGTH_SHORT).show()
                            },
                            onCopy = { label, text -> onCopyText(label, text) }
                        )
                    }


                    // 5 Core Modern Study Tools
                    AiStudyCoreToolsCards(
                        document = document,
                        currentPage = currentPage,
                        safeIndex = safeIndex,
                        scopeText = scopeText,
                        selectedScope = selectedScope,
                        hasGeminiKey = hasGeminiKey,
                        generationBusy = isGenerating,
                        defaultAiName = defaultAiName,
                        onImportFlashcards = onImportFlashcards,
                        onSaveQuiz = onSaveQuiz,
                        onCopyText = onCopyText,
                        onSaveAiResultAsNote = onSaveAiResultAsNote,
                        onGenerateInAppFlashcards = onGenerateInAppFlashcards,
                        onGenerateInAppQuiz = onGenerateInAppQuiz,
                        onGenerateInAppSummary = onGenerateInAppSummary,
                        onGenerateInAppExplanation = onGenerateInAppExplanation,
                        onGenerateInAppStudyGuide = onGenerateInAppStudyGuide,
                        onSetGenerating = { generating, status ->
                            isGenerating = generating
                            if (status.isNotBlank()) generatingStatus = status
                        },
                        onSetResultPreview = { activeResultPreview = it },
                        onExternalHandoff = { handleExternalHandoff(it) }
                    )

                    CustomAiHandoffCard(includeContextField = false) { instructions, _ ->
                        onSendToAiApp(AiPromptType.CUSTOM, instructions, selectedScope, currentRange)
                    }

                    // Manual Importer & Advanced Accordion
                    AiStudyManualAccordion(
                        document = document,
                        onImportFlashcards = onImportFlashcards,
                        onSaveQuiz = onSaveQuiz,
                        onSaveAiResultAsNote = onSaveAiResultAsNote,
                        onSetResultPreview = { activeResultPreview = it },
                        onShowPasteFlashcards = { showPasteFlashcards = true },
                        onShowPasteQuiz = { showPasteQuiz = true },
                        onShowGeminiSetup = { showGeminiSetup = true }
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun QuickExternalAiChips(
    context: Context,
    document: ReaderDocument,
    scopeText: String,
    safeIndex: Int,
    selectedScope: AiPromptScope
) {
    val aiHandoffs = remember {
        listOf(
            Triple("Gemini", "com.google.android.apps.bard", "https://gemini.google.com"),
            Triple("ChatGPT", "com.openai.chatgpt", "https://chatgpt.com"),
            Triple("Claude", "com.anthropic.claude", "https://claude.ai"),
            Triple("Grok", "ai.groq", "https://x.ai"),
            Triple("Perplexity", "ai.perplexity.app.android", "https://www.perplexity.ai"),
            Triple("Copilot", "com.microsoft.copilot", "https://copilot.microsoft.com")
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Quick External AI Hand-off",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Auto-copies prompt",
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
                aiHandoffs.forEach { (name, pkg, webUrl) ->
                    AssistChip(
                        onClick = {
                            val prompt = AiPromptLauncher.buildPrompt(
                                title = document.title,
                                chunks = document.chunks,
                                currentIndex = safeIndex,
                                type = AiPromptType.STUDY_NOTES,
                                scope = selectedScope
                            ) + "\n\n" + scopeText
                            launchExternalAiHandoff(context, name, pkg, webUrl, prompt)
                        },
                        label = { Text(name, fontWeight = FontWeight.SemiBold) },
                        leadingIcon = {
                            Icon(
                                imageVector = aiAssistantIcon(name),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.Unspecified
                            )
                        },
                        shape = RoundedCornerShape(50)
                    )
                }
            }
        }
    }
}

private fun launchExternalAiHandoff(
    context: Context,
    appName: String,
    packageName: String,
    webUrl: String,
    prompt: String
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Vern AI Prompt", prompt))

    var launched = false
    if (packageName.isNotBlank()) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(launchIntent)
                Toast.makeText(context, "Prompt copied! Opening $appName...", Toast.LENGTH_SHORT).show()
                launched = true
            } catch (_: Exception) {}
        }
    }

    if (!launched) {
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
            Toast.makeText(context, "Prompt copied! Opening $appName in browser...", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, "Prompt copied to clipboard!", Toast.LENGTH_SHORT).show()
        }
    }
}


