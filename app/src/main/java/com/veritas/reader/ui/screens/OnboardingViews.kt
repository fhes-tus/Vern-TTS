package com.veritas.reader.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veritas.reader.BrandMark
import com.veritas.reader.TutorialSpeaker
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.ui.ReadingInterestOptions
import com.veritas.reader.ui.VeritasMotion
import com.veritas.reader.ui.pressScale
import kotlinx.coroutines.delay

private val setupAssistants = listOf("chooser" to "Choose when I share", "gemini" to "Google Gemini",
    "chatgpt" to "ChatGPT", "claude" to "Claude", "copilot" to "Microsoft Copilot",
    "perplexity" to "Perplexity", "grok" to "Grok")

@Composable
fun RevampedOnboardingFlow(
    initialUserName: String,
    initialReadingInterest: String,
    initialAiAssistant: String,
    onComplete: (name: String, interest: String, aiAssistant: String, speed: Float, pitch: Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialRate: Float = 1f,
    initialPitch: Float = 1f
) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(initialUserName.takeUnless { it.equals("Reader", true) }.orEmpty()) }
    var interest by rememberSaveable { mutableStateOf(initialReadingInterest.ifBlank { "Books & Novels" }) }
    var assistant by rememberSaveable { mutableStateOf(initialAiAssistant.ifBlank { "chooser" }) }
    var rate by rememberSaveable { mutableFloatStateOf(initialRate.coerceIn(.5f, 2f)) }
    var finishing by remember { mutableStateOf(false) }
    var previewTimedOut by remember { mutableStateOf(false) }
    val slide = VeritasMotion.spatialSlow<androidx.compose.ui.unit.IntOffset>()
    val fade = VeritasMotion.effectsSlow<Float>()
    val scale = VeritasMotion.spatialSlow<Float>()
    val continueInteraction = remember { MutableInteractionSource() }
    val pages = listOf("Welcome", "Reading", "Listening", "Study tools", "Ready")
    val progress by animateFloatAsState((step + 1f) / pages.size, VeritasMotion.spatialSlow(), label = "setupProgress")
    DisposableEffect(context) {
        TutorialSpeaker.init(context)
        onDispose { TutorialSpeaker.shutdown() }
    }
    LaunchedEffect(step) { TutorialSpeaker.stop(); previewTimedOut = false }
    LaunchedEffect(TutorialSpeaker.previewActive) {
        if (TutorialSpeaker.previewActive) {
            delay(20_000)
            if (TutorialSpeaker.previewActive) { TutorialSpeaker.stop(); previewTimedOut = true }
        }
    }
    fun back() { if (!finishing && step > 0) { TutorialSpeaker.stop(); step-- } }
    BackHandler { back() }
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.statusBarsPadding().navigationBarsPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(compact = true)
                    Spacer(Modifier.weight(1f))
                    if (step < 4) TextButton(onClick = {
                        if (!finishing) { finishing = true; TutorialSpeaker.stop(); onDismiss() }
                    }, enabled = !finishing) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_set_up_later)) }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AnimatedContent(pages[step], transitionSpec = { fadeIn(fade).togetherWith(fadeOut(fade)) }, label = "setupPageLabel") {
                        Text(it, style = MaterialTheme.typography.labelLarge)
                    }
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_127, com.veritas.reader.ui.screens.localizedUiValue(step + 1), com.veritas.reader.ui.screens.localizedUiValue(pages.size)), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LinearProgressIndicator(progress = { progress },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(3.dp))
                AnimatedContent(step, modifier = Modifier.weight(1f).fillMaxWidth(), transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(slide) { it / 3 * direction } + fadeIn(fade) + scaleIn(scale, initialScale = .97f))
                        .togetherWith(slideOutHorizontally(slide) { -it / 3 * direction } + fadeOut(fade) + scaleOut(scale, targetScale = .97f)).using(null)
                }, label = "readingSetup") { page ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OnboardingArtwork(page, TutorialSpeaker.previewActive && page == 2)
                        when (page) {
                            0 -> {
                                SetupHeading(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_your_next_chapter_starts_here), com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_read_listen_and_keep_your_ideas_together_in_vern))
                                SetupReadingPreview()
                                SetupFeature(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_read), com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_bring_in_your_books_and_documents_pick_up_where_you_left_off))
                                SetupFeature(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_listen), com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_follow_the_text_as_it_s_read_aloud_at_your_own_pace))
                                SetupFeature(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_keep_what_matters), com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_save_notes_highlights_and_flashcards_alongside_your_reading))
                            }
                            1 -> {
                                SetupHeading(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_what_fills_your_bookshelf), com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_choose_the_reading_interest_that_fits_you_best_change_it_anytime))
                                ReadingInterestOptions.forEach { option ->
                                    SetupChoice(option.label, option.detail, interest == option.label, glyph = option.id) { interest = option.label }
                                }
                            }
                            2 -> {
                                SetupHeading(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_settle_into_your_listening_pace), com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_try_a_sample_with_your_phone_s_voice_explore_downloadable_vern_vo))
                                Surface(shape = VeritasPackStyle.cardShape(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_the_city_was_still_quiet_kofi_opened_his_book_and_returned_to_the),
                                            style = MaterialTheme.typography.bodyLarge)
                                        FilledTonalButton(onClick = {
                                            previewTimedOut = false
                                            if (TutorialSpeaker.previewActive) TutorialSpeaker.stop()
                                            else TutorialSpeaker.speakWithTuning("The city was still quiet. Kofi opened his book and returned to the sentence he had marked yesterday.", rate, initialPitch)
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Icon(if (TutorialSpeaker.previewActive) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(if (TutorialSpeaker.previewActive) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_stop_preview) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_listen_to_a_sample))
                                        }
                                        (TutorialSpeaker.previewError ?: if (previewTimedOut) "The preview took too long. Try again or continue." else null)?.let {
                                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_128, com.veritas.reader.ui.screens.localizedUiValue(String.format(java.util.Locale.ROOT, "%.2f", rate))), style = MaterialTheme.typography.titleMedium)
                                VeritasRoundSlider(rate, onValueChange = { TutorialSpeaker.stop(); rate = it },
                                    valueRange = .5f..2f, modifier = Modifier.heightIn(min = 48.dp)
                                        .semantics {
                                            contentDescription = "Listening speed"
                                            progressBarRangeInfo = ProgressBarRangeInfo(rate, .5f..2f)
                                            setProgress { TutorialSpeaker.stop(); rate = it.coerceIn(.5f, 2f); true }
                                        })
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Relaxed" to .9f, "Natural" to 1f, "Faster" to 1.25f).forEach { (label, value) ->
                                        FilterChip(selected = kotlin.math.abs(rate - value) < .01f,
                                            onClick = { TutorialSpeaker.stop(); rate = value }, label = { Text(label) })
                                    }
                                }
                                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_choose_a_pace_you_can_follow_comfortably), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            3 -> {
                                SetupHeading(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_turn_reading_into_understanding), com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_keep_notes_and_flashcards_as_you_read_choose_an_assistant_to_open))
                                setupAssistants.forEach { (id, label) ->
                                    SetupChoice(label, if (id == "chooser") com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_no_default_assistant) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_open_when_you_choose_ask_ai), assistant == id) { assistant = id }
                                }
                                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_you_can_use_the_study_tools_without_an_assistant_set_up_in_app_ai),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            4 -> {
                                SetupHeading(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_you_re_ready_to_read), com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_start_with_the_welcome_document_or_add_something_of_your_own))
                                OutlinedTextField(name, { name = it.take(60) }, label = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_your_name_optional)) },
                                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = VeritasPackStyle.compactShape())
                                Surface(shape = VeritasPackStyle.cardShape(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        SetupSummary(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_reading), interest)
                                        SetupSummary(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_listening), "${String.format(java.util.Locale.ROOT, "%.2f", rate)}× speed")
                                        SetupSummary(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_ask_ai), setupAssistants.firstOrNull { it.first == assistant }?.second ?: "Choose when I share")
                                    }
                                }
                                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_these_preferences_can_be_changed_at_any_time_in_settings), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (step > 0) IconButton(onClick = { back() }, enabled = !finishing) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_previous_setup_step))
                    }
                    Button(onClick = {
                        if (!finishing) {
                            TutorialSpeaker.stop()
                            if (step == 4) {
                                finishing = true
                                onComplete(name.trim().ifBlank { "Reader" }, interest, assistant, rate, initialPitch)
                            } else step++
                        }
                    }, enabled = !finishing, interactionSource = continueInteraction,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp).pressScale(continueInteraction), shape = VeritasPackStyle.chipShape()) {
                        AnimatedContent(if (finishing) "Opening library…" else if (step == 4) "Open my library" else if (step == 0) "Get started" else "Continue",
                            transitionSpec = { fadeIn(fade).togetherWith(fadeOut(fade)) }, label = "setupContinueLabel") { Text(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupHeading(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineLarge)
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SetupChoice(title: String, body: String, checked: Boolean, glyph: String? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().semantics { role = Role.RadioButton; selected = checked },
        shape = VeritasPackStyle.compactShape(), color = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (checked) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (glyph != null) {
                val animated = checked && !com.veritas.reader.ui.LocalVeritasMotion.current.reduceMotion
                Box(Modifier.padding(end = 14.dp).size(48.dp)) {
                    when (glyph) {
                        "books" -> LiteratureGlyph(animated)
                        "pdf" -> DeepStudyGlyph(animated)
                        "web" -> SpeedAndWorkGlyph(animated)
                        else -> AudioFirstGlyph(animated)
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
            if (checked) Icon(Icons.Default.Check, null, Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
private fun SetupFeature(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SetupSummary(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SetupReadingPreview() {
    Surface(shape = VeritasPackStyle.cardShape(), color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = MaterialTheme.colorScheme.primary)
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_a_place_to_begin), style = MaterialTheme.typography.titleMedium)
            }
            Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_the_city_was_still_quiet), style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, VeritasPackStyle.compactShape()).padding(8.dp))
            Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_kofi_opened_his_book_and_returned_to_the_sentence_he_had_marked_y), style = MaterialTheme.typography.bodyLarge)
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_follow_each_sentence_as_you_listen), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
