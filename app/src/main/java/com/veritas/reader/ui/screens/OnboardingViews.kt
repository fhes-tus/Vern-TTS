package com.veritas.reader.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
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
    val slide = VeritasMotion.spatial<androidx.compose.ui.unit.IntOffset>()
    val fade = VeritasMotion.effectsFast<Float>()
    val pages = listOf("Welcome", "Reading", "Listening", "Study tools", "Ready")
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
                    Text(pages[step], style = MaterialTheme.typography.labelLarge)
                    Text("${step + 1} of ${pages.size}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LinearProgressIndicator(progress = { (step + 1f) / pages.size },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(3.dp))
                AnimatedContent(step, modifier = Modifier.weight(1f).fillMaxWidth(), transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(slide) { it / 6 * direction } + fadeIn(fade))
                        .togetherWith(slideOutHorizontally(slide) { -it / 6 * direction } + fadeOut(fade)).using(null)
                }, label = "readingSetup") { page ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        OnboardingArtwork(page, TutorialSpeaker.previewActive && page == 2)
                        when (page) {
                            0 -> {
                                SetupHeading("Make room for a good read.", "Your books, documents, listening and notes, together in Vern.")
                                SetupReadingPreview()
                                SetupFeature("Read", "Import a PDF, EPUB or document. Keep your place across sessions.")
                                SetupFeature("Listen", "Follow the text as it’s read aloud, at your own pace.")
                                SetupFeature("Keep what matters", "Save notes, highlights and flashcards alongside your reading.")
                            }
                            1 -> {
                                SetupHeading("What do you read most?", "This sets your library interest. You can change it later.")
                                ReadingInterestOptions.forEach { option ->
                                    SetupChoice(option.label, option.detail, interest == option.label, glyph = option.id) { interest = option.label }
                                }
                            }
                            2 -> {
                                SetupHeading("Find a comfortable pace.", "Hear a short sample with your phone’s speech engine. Voice models can be changed in Settings.")
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
                                Slider(rate, onValueChange = { TutorialSpeaker.stop(); rate = it }, valueRange = .5f..2f)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Relaxed" to .9f, "Natural" to 1f, "Faster" to 1.25f).forEach { (label, value) ->
                                        FilterChip(selected = kotlin.math.abs(rate - value) < .01f,
                                            onClick = { TutorialSpeaker.stop(); rate = value }, label = { Text(label) })
                                    }
                                }
                                Text("This changes the playback speed, not the voice identity.", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            3 -> {
                                SetupHeading("Study with your own tools.", "Reading and listening work without AI. If you share text with an assistant, choose which one Vern opens.")
                                setupAssistants.forEach { (id, label) ->
                                    SetupChoice(label, if (id == "chooser") "No default assistant" else "Open when you choose Ask AI", assistant == id) { assistant = id }
                                }
                                Text("This doesn’t connect an account. In-app AI generation is configured separately in Settings.",
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
                    }, enabled = !finishing, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = VeritasPackStyle.chipShape()) {
                        Text(if (finishing) "Opening library…" else if (step == 4) "Open my library" else if (step == 0) "Get started" else "Continue")
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupHeading(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
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
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
