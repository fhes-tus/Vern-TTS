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
                    }, enabled = !finishing) { Text("Set up later") }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AnimatedContent(pages[step], transitionSpec = { fadeIn(fade).togetherWith(fadeOut(fade)) }, label = "setupPageLabel") {
                        Text(it, style = MaterialTheme.typography.labelLarge)
                    }
                    Text("${step + 1} of ${pages.size}", style = MaterialTheme.typography.labelMedium,
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
                                SetupHeading("Your next chapter starts here.", "Read, listen and keep your ideas together in Vern.")
                                SetupReadingPreview()
                                SetupFeature("Read", "Bring in your books and documents. Pick up where you left off.")
                                SetupFeature("Listen", "Follow the text as it’s read aloud, at your own pace.")
                                SetupFeature("Keep what matters", "Save notes, highlights and flashcards alongside your reading.")
                            }
                            1 -> {
                                SetupHeading("What fills your bookshelf?", "Choose the reading interest that fits you best. Change it anytime in Settings.")
                                ReadingInterestOptions.forEach { option ->
                                    SetupChoice(option.label, option.detail, interest == option.label, glyph = option.id) { interest = option.label }
                                }
                            }
                            2 -> {
                                SetupHeading("Settle into your listening pace.", "Try a sample with your phone’s voice. Explore downloadable Vern voices later in Settings.")
                                Surface(shape = VeritasPackStyle.cardShape(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("“The city was still quiet. Kofi opened his book and returned to the sentence he had marked yesterday.”",
                                            style = MaterialTheme.typography.bodyLarge)
                                        FilledTonalButton(onClick = {
                                            previewTimedOut = false
                                            if (TutorialSpeaker.previewActive) TutorialSpeaker.stop()
                                            else TutorialSpeaker.speakWithTuning("The city was still quiet. Kofi opened his book and returned to the sentence he had marked yesterday.", rate, initialPitch)
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Icon(if (TutorialSpeaker.previewActive) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                                            Spacer(Modifier.width(8.dp))
                                            Text(if (TutorialSpeaker.previewActive) "Stop preview" else "Listen to a sample")
                                        }
                                        (TutorialSpeaker.previewError ?: if (previewTimedOut) "The preview took too long. Try again or continue." else null)?.let {
                                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                                Text("Listening speed · ${String.format(java.util.Locale.ROOT, "%.2f", rate)}×", style = MaterialTheme.typography.titleMedium)
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
                                Text("Choose a pace you can follow comfortably.", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            3 -> {
                                SetupHeading("Turn reading into understanding.", "Keep notes and flashcards as you read. Choose an assistant to open when you use Ask AI.")
                                setupAssistants.forEach { (id, label) ->
                                    SetupChoice(label, if (id == "chooser") "No default assistant" else "Open when you choose Ask AI", assistant == id) { assistant = id }
                                }
                                Text("You can use the study tools without an assistant. Set up in-app AI separately in Settings.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            4 -> {
                                SetupHeading("You’re ready to read.", "Start with the welcome document, or add something of your own.")
                                OutlinedTextField(name, { name = it.take(60) }, label = { Text("Your name (optional)") },
                                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = VeritasPackStyle.compactShape())
                                Surface(shape = VeritasPackStyle.cardShape(), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        SetupSummary("Reading", interest)
                                        SetupSummary("Listening", "${String.format(java.util.Locale.ROOT, "%.2f", rate)}× speed")
                                        SetupSummary("Ask AI", setupAssistants.firstOrNull { it.first == assistant }?.second ?: "Choose when I share")
                                    }
                                }
                                Text("These preferences can be changed at any time in Settings.", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (step > 0) IconButton(onClick = { back() }, enabled = !finishing) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous setup step")
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
                Text("A place to begin", style = MaterialTheme.typography.titleMedium)
            }
            Text("The city was still quiet.", style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, VeritasPackStyle.compactShape()).padding(8.dp))
            Text("Kofi opened his book and returned to the sentence he had marked yesterday.", style = MaterialTheme.typography.bodyLarge)
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                Text("Follow each sentence as you listen", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
