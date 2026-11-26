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
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AutoAwesome
import com.veritas.reader.VernAlertDialog as AlertDialog
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
import com.veritas.reader.ui.screens.menuString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.VernDialog as Dialog
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
        confirmButton = { TextButton(onClick = onDismiss) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_close)) } },
        title = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_ai_study_mode)) },
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
                            com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_free_ai_approach),
                            style = MaterialTheme.typography.titleMedium)
                        Text(
                            com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_vern_does_not_bundle_a_large_offline_model_and_does_not_require_a),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                InfoStepCard(title = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_1_ai_app_handoff)) {
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_open_a_document_tap_reader_tools_ai_choose_a_task_then_send_the_p))
                }
                InfoStepCard(title = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_2_paste_the_reply_back)) {
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_flashcard_and_quiz_replies_can_be_pasted_straight_back_into_vern))
                }
                InfoStepCard(title = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_3_base_app_stays_lighter)) {
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_no_heavy_local_ai_model_is_bundled_in_the_base_app_a_real_offline))
                }
                Text(
                    com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_198, com.veritas.reader.ui.screens.localizedUiValue(documentCount)),
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
    com.veritas.reader.ui.screens.FullScreenSettingsScaffold(title = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_ai_tools), onBack = onDismiss) {
        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_work_with_a_passage_from_your_reading_or_send_it_to_an_assistant),
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(shape = VeritasPackStyle.cardShape(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_study_a_reading), style = MaterialTheme.typography.titleLarge)
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_create_flashcards_quizzes_summaries_and_explanations_review_the_r))
                Text(if (configured) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_039, com.veritas.reader.ui.screens.localizedUiValue(provider.label)) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_in_app_generation_needs_your_provider_s_api_key_external_assistan),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onOpenStudyTools, modifier = Modifier.fillMaxWidth(), shape = VeritasPackStyle.chipShape()) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_open_study_tools)) }
                if (documentCount == 0) Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_add_a_reading_to_choose_a_passage), style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(shape = VeritasPackStyle.cardShape(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_ask_another_assistant), style = MaterialTheme.typography.titleLarge)
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_199, com.veritas.reader.ui.screens.localizedUiValue(installedAiCount)))
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_a_handoff_opens_the_other_app_its_response_is_not_automatically_r), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onOpenAskAiSettings, modifier = Modifier.fillMaxWidth(), shape = VeritasPackStyle.chipShape()) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_assistant_and_prompt_settings)) }
            }
        }
        CustomAiHandoffCard(includeContextField = true) { instructions, passage ->
            AiPromptLauncher.launchCustomPrompt(context, instructions, passage, askAiSettings)
        }
        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_ai_output_can_contain_mistakes_keep_the_original_passage_availabl),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ExportAudioStatusDialog(
    inProgress: Boolean,
    message: String?,
    file: File?,
    onShare: (File) -> Unit,
    onSave: (File, android.net.Uri) -> Unit,
    onCancel: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!inProgress) onDismiss() },
        title = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_export_audio)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (inProgress) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_creating_audio_from_this_reading))
                    }
                }
                Text(
                    message ?: com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_preparing_export),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (file != null) {
                    Text(file.name, fontWeight = FontWeight.SemiBold)
                    SaveExportedAudioButton(file, onSave)
                }
            }
        },
        confirmButton = {
            if (file != null) {
                Button(onClick = { onShare(file) }) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_share)) }
            }
        },
        dismissButton = {
            if (inProgress && onCancel != null) {
                TextButton(onClick = onCancel) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_cancel)) }
            } else {
                TextButton(onClick = onDismiss, enabled = !inProgress) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_close)) }
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
    onSaveAiResultAsNote: (String, (Boolean) -> Unit) -> Unit,
    onImportFlashcards: (String, List<Flashcard>) -> Unit,
    onGenerateInAppFlashcards: ((String, Int, (Boolean, String) -> Unit) -> Unit)? = null,
    onGenerateInAppQuiz: ((String, Int, (Boolean, String, QuizSet?) -> Unit) -> Unit)? = null,
    onGenerateInAppText: (AiPromptType, String, String, (String) -> Unit, (Boolean, String) -> Unit) -> Unit,
    onSaveQuiz: ((QuizSet) -> Unit)? = null,
    onRecordQuizScore: ((String, Int) -> Unit)? = null,
    onOpenStudyHub: (() -> Unit)? = null,
    onCancelGeneration: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardScope = rememberCoroutineScope()
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
    var streamedAnswer by remember { mutableStateOf("") }
    val responseLocation = remember { BringIntoViewRequester() }

    var activeResultPreview by remember { mutableStateOf<ModernStudyResultPreview?>(null) }
    var activeQuizQuestions by remember { mutableStateOf<List<QuizQuestion>?>(null) }
    var activeQuizId by remember { mutableStateOf<String?>(null) }
    var activeStudyDeckCards by remember { mutableStateOf<Pair<String, List<Flashcard>>?>(null) }
    var savingAiNote by remember { mutableStateOf(false) }

    var detectedClipboardText by remember { mutableStateOf<String?>(null) }
    var detectedCards by remember { mutableStateOf<List<Flashcard>>(emptyList()) }
    var detectedQuiz by remember { mutableStateOf<List<QuizQuestion>>(emptyList()) }
    var bannerDismissed by remember { mutableStateOf(false) }

    var readingClipboard by remember { mutableStateOf(false) }
    var clipboardMessage by remember { mutableStateOf<Int?>(null) }

    fun checkClipboard() {
        readingClipboard = true
        clipboardMessage = null
        detectedClipboardText = null
        detectedCards = emptyList()
        detectedQuiz = emptyList()
        clipboardScope.launch {
            try {
                val clip = clipboard.primaryClip
                val raw = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text
                if (raw == null || raw.length !in 1..30_000) {
                    clipboardMessage = R.string.study_ai_clipboard_invalid
                    return@launch
                }
                val text = raw.toString().trim()
                val (quiz, cards) = withContext(Dispatchers.Default) {
                    val parsedQuiz = AiResultParser.parseQuiz(text)
                    parsedQuiz to if (parsedQuiz.isEmpty()) AiResultParser.parseFlashcards(text) else emptyList()
                }
                detectedClipboardText = text.takeIf { cards.isNotEmpty() || quiz.isNotEmpty() }
                detectedCards = cards
                detectedQuiz = quiz
                bannerDismissed = false
                if (detectedClipboardText == null) clipboardMessage = R.string.study_ai_clipboard_invalid
            } catch (_: SecurityException) {
                clipboardMessage = R.string.study_ai_clipboard_invalid
            } finally {
                readingClipboard = false
            }
        }
    }

    val hasGeminiKey = remember(context, showGeminiSetup) {
        GeminiStudyService.hasApiKey(context)
    }

    val preferredPackage = remember(context, askAiSettings) { AiPromptLauncher.preferredAiPackage(context, askAiSettings) }
    val defaultAiId = askAiSettings?.assistantId?.takeIf { preferredPackage != null } ?: "chooser"
    val defaultAiName = askAiSettings?.assistantLabel?.takeIf { preferredPackage != null && it.isNotBlank() } ?: "Share Menu"

    val currentRange = remember(selectedScope, customStartPage, customEndPage, document.pageCount) {
        if (selectedScope == AiPromptScope.CUSTOM_PAGE_RANGE) {
            explicitStudyPageRange(customStartPage, customEndPage, document.pageCount.coerceAtLeast(1))
        } else null
    }

    val scopeSentences = remember(document, currentIndex, selectedScope, currentRange) {
        AiPromptLauncher.getSelectedSentences(document, currentIndex, selectedScope, currentRange)
    }

    val scopeText = remember(scopeSentences) { AiPromptLauncher.buildMarkdownForSentences(scopeSentences) }

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
        PasteQuizDialog(
            onSaveQuiz = onSaveQuiz?.let { save -> { quiz -> save(quiz.copy(documentId = document.id.orEmpty())) } },
            onDismiss = { showPasteQuiz = false }
        )
    }

    activeQuizQuestions?.let { questions ->
        QuizPlayerDialog(
            questions = questions,
            quizTitle = "${document.title} Quiz",
            quizId = activeQuizId,
            onQuestionsChange = if (activeQuizId != null && onSaveQuiz != null) { updated ->
                activeQuizQuestions = updated
                val saved = DocumentRepository(context).loadAllQuizzes().firstOrNull { it.id == activeQuizId }
                if (saved != null) onSaveQuiz(saved.copy(questions = updated))
            } else null,
            onSaveScore = { score ->
                activeQuizId?.let { id -> onRecordQuizScore?.invoke(id, score) }
            },
            onDismiss = { activeQuizQuestions = null }
        )
    }

    activeStudyDeckCards?.let { (deckTitle, cards) ->
        FlashcardViewerDialog(
            setName = menuString(R.string.study_ai_practice_deck_title, deckTitle),
            cards = cards.mapIndexed { idx, card ->
                FlashcardProgress(
                    id = "${document.id.orEmpty()}_card_$idx",
                    documentId = document.id.orEmpty(),
                    front = card.front,
                    back = card.back,
                    setName = deckTitle
                )
            },
            onRate = { _, _, finish -> finish(true) },
            onDeleteCard = {},
            practiceOnly = true,
            onDismiss = { activeStudyDeckCards = null }
        )
    }

    fun handleExternalHandoff(type: AiPromptType) {
        if (scopeText.isBlank()) return
        onSendToAiApp(type, "", selectedScope, currentRange)
    }

    fun generateText(type: AiPromptType, question: String) {
        if (isGenerating || scopeText.isBlank()) return
        val title = context.getString(when (type) {
            AiPromptType.SUMMARY -> R.string.l10n_executive_summary
            AiPromptType.EXPLAIN_SECTION -> R.string.l10n_explain_simplify
            AiPromptType.STUDY_NOTES -> R.string.study_ai_guide_title
            else -> R.string.study_ai_answer_title
        })
        generatingStatus = context.getString(R.string.study_ai_answer_streaming)
        streamedAnswer = ""
        isGenerating = true
        onGenerateInAppText(type, scopeText, question, { streamedAnswer = it }) { success, result ->
            isGenerating = false
            streamedAnswer = ""
            if (success) activeResultPreview = ModernStudyResultPreview.NoteText(type, title, result)
            else Toast.makeText(context, result, Toast.LENGTH_LONG).show()
        }
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
                                    contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_back),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Column {
                                Text(
                                    com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_ai_study_studio),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_040, com.veritas.reader.ui.screens.localizedUiValue(document.title), com.veritas.reader.ui.screens.localizedUiValue(currentPage), com.veritas.reader.ui.screens.localizedUiValue(document.pageCount.coerceAtLeast(1))),
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
                                            com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_study_hub),
                                            style = MaterialTheme.typography.labelSmall,
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
                                    contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_refresh_clipboard),
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
                                        if (hasGeminiKey) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_041, com.veritas.reader.ui.screens.localizedUiValue(activeProvider.label)) else defaultAiName,
                                        style = MaterialTheme.typography.labelSmall,
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
                                    com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_study_focus),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_042, com.veritas.reader.ui.screens.localizedUiValue(scopeSentences.size), com.veritas.reader.ui.screens.localizedUiValue(scopeWordCount)),
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
                                        label = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_from_page), fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = customEndPage,
                                        onValueChange = { customEndPage = it.filter { ch -> ch.isDigit() } },
                                        label = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_to_page), fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                            if (scopeText.isBlank()) {
                                Text(menuString(R.string.study_ai_empty_scope), color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall)
                            } else if (hasGeminiKey && scopeText.length > AI_STUDY_EXCERPT_LIMIT) {
                                Text(menuString(R.string.study_ai_scope_limit), style = MaterialTheme.typography.bodySmall)
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

                    TextButton(onClick = { checkClipboard() }, enabled = !readingClipboard) {
                        Text(menuString(R.string.study_ai_check_clipboard))
                    }
                    clipboardMessage?.let { Text(menuString(it), style = MaterialTheme.typography.bodySmall) }
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
                        LaunchedEffect(Unit) { responseLocation.bringIntoView() }
                        Card(
                            modifier = Modifier.bringIntoViewRequester(responseLocation),
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
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_043, com.veritas.reader.ui.screens.localizedUiValue(GeminiStudyService.getProvider(context).label)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = {
                                    onCancelGeneration()
                                    isGenerating = false
                                    streamedAnswer = ""
                                }) {
                                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_cancel))
                                }
                            }
                        }
                        if (streamedAnswer.isNotBlank()) {
                            Text(streamedAnswer, modifier = Modifier.padding(horizontal = 8.dp),
                                style = MaterialTheme.typography.bodyMedium)
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
                                if (!savingAiNote) {
                                    savingAiNote = true
                                    onSaveAiResultAsNote(text) { success ->
                                        savingAiNote = false
                                        val message = if (success) R.string.study_ai_note_saved else R.string.study_ai_note_save_failed
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                }
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
                        onGenerateInAppFlashcards = onGenerateInAppFlashcards,
                        onGenerateInAppQuiz = onGenerateInAppQuiz,
                        onGenerateText = ::generateText,
                        onSetGenerating = { generating, status ->
                            isGenerating = generating
                            if (status.isNotBlank()) generatingStatus = status
                        },
                        onSetResultPreview = { activeResultPreview = it },
                        onExternalHandoff = { handleExternalHandoff(it) }
                    )

                    CustomAiHandoffCard(
                        includeContextField = false,
                        canSend = !isGenerating && scopeText.isNotBlank(),
                        onAskInVern = if (hasGeminiKey) { question -> generateText(AiPromptType.CUSTOM, question) } else null
                    ) { instructions, _ ->
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun QuickExternalAiChips(
    context: Context,
    document: ReaderDocument,
    scopeText: String,
    safeIndex: Int,
    selectedScope: AiPromptScope
) {
    val sites = remember {
        mapOf(
            "gemini" to "https://gemini.google.com/",
            "chatgpt" to "https://chatgpt.com/",
            "claude" to "https://claude.ai/",
            "grok" to "https://grok.com/",
            "perplexity" to "https://www.perplexity.ai/",
            "copilot" to "https://copilot.microsoft.com/"
        )
    }
    val providers = remember { sites.keys.mapNotNull { id -> aiAssistantOptions.firstOrNull { it.id == id } } }
    var selectedProvider by remember { mutableStateOf<AiAssistantOption?>(null) }
    selectedProvider?.let { provider ->
        val instructions = remember(document.title, safeIndex, selectedScope) {
            AiPromptLauncher.buildPrompt(document.title, document.chunks, safeIndex, AiPromptType.STUDY_NOTES,
                scope = selectedScope)
        }
        val prompt = remember(instructions, scopeText) { "$instructions\n\n$scopeText" }
        AlertDialog(
            onDismissRequest = { selectedProvider = null },
            title = { Text(menuString(R.string.study_handoff_open_provider, provider.label)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(menuString(R.string.study_handoff_review))
                    Text(instructions, style = MaterialTheme.typography.bodySmall,
                        maxLines = 5, overflow = TextOverflow.Ellipsis)
                    Text(menuString(R.string.study_handoff_context_count, scopeText.length),
                        style = MaterialTheme.typography.labelMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedProvider = null
                    if (prompt.length > 60_000) {
                        AiPromptLauncher.sharePreparedStudyPrompt(context, prompt, provider)
                    } else {
                        launchExternalAiHandoff(context, provider, sites.getValue(provider.id), prompt)
                    }
                }) { Text(menuString(if (prompt.length > 60_000) R.string.study_handoff_share_file else R.string.study_handoff_copy_open)) }
            },
            dismissButton = { TextButton(onClick = { selectedProvider = null }) { Text(menuString(R.string.ui_cancel)) } }
        )
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = VeritasPackStyle.compactShape(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(menuString(R.string.study_handoff_title), style = MaterialTheme.typography.titleMedium)
            Text(menuString(R.string.study_handoff_review), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                providers.forEach { provider ->
                    AssistChip(
                        enabled = scopeText.isNotBlank(),
                        onClick = { selectedProvider = provider },
                        label = { Text(provider.label) },
                        leadingIcon = {
                            Icon(aiAssistantIcon(provider.id), null, Modifier.size(17.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        shape = VeritasPackStyle.chipShape()
                    )
                }
            }
        }
    }
}

private fun launchExternalAiHandoff(
    context: Context,
    provider: AiAssistantOption,
    webUrl: String,
    prompt: String
) {
    AiPromptLauncher.copyPromptToClipboard(context, prompt)
    val app = context.packageManager.getLaunchIntentForPackage(provider.packageName)
    val opened = app != null && runCatching { context.startActivity(app) }.isSuccess
    if (!opened) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))) }
    Toast.makeText(context, context.getString(R.string.study_handoff_prompt_copied), Toast.LENGTH_SHORT).show()
}
