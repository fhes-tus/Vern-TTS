package com.veritas.reader.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.BookCharacter
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import com.veritas.reader.NarrationAnalyzer
import com.veritas.reader.NarrationSettings
import com.veritas.reader.PronunciationRule
import com.veritas.reader.SpeechPunctuation
import com.veritas.reader.SpeechSanitizer
import com.veritas.reader.TtsVoiceOption
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.VoiceManager
import com.veritas.reader.ui.VeritasSwitch

@Composable
fun NarrationStudioDialog(
    settings: NarrationSettings,
    sampleText: String,
    availableVoices: List<TtsVoiceOption> = emptyList(),
    voiceSettings: com.veritas.reader.VoiceSettings = com.veritas.reader.VoiceSettings(),
    pronunciationRules: List<PronunciationRule> = emptyList(),
    onSettingsChange: (NarrationSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var draft by remember(settings) { mutableStateOf(settings) }
    var sample by remember { mutableStateOf(sampleText.take(600).ifBlank { "Alice said, \"Are you ready to begin?\"" }) }
    var voices by remember(availableVoices, voiceSettings.enginePackage) { mutableStateOf(availableVoices) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var selectedRole by remember { mutableStateOf("narrator") }
    var chooseVoice by remember { mutableStateOf(false) }
    var voiceSearch by remember { mutableStateOf("") }
    var addRole by remember { mutableStateOf(false) }
    var roleName by remember { mutableStateOf("") }
    var discard by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf<String?>(null) }
    val neural = VoiceManager.isVeritasEngine(voiceSettings.enginePackage)
    val previewScope = androidx.compose.runtime.rememberCoroutineScope()
    var previewJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var previewRevision by remember { mutableStateOf(0) }
    var previewSentenceIndex by remember { mutableStateOf(0) }
    val sampleModel = remember(sample) { com.veritas.reader.ReaderTextIndex.build(sample) }
    val sampleCues = remember(sampleModel) { com.veritas.reader.LiteraryDialogue.cues(sampleModel.sentences) }
    val sampleSentence = sampleModel.sentences.getOrNull(previewSentenceIndex)?.text ?: sample
    val sampleCue = sampleCues.getOrNull(previewSentenceIndex)
    val active = NarrationAnalyzer.getActiveCharacter(sampleSentence, draft, sampleCue)
    val rate = NarrationAnalyzer.effectiveRate(voiceSettings.preferredRate, draft, sampleSentence, !neural, sampleCue)
    val pitch = NarrationAnalyzer.effectivePitch(voiceSettings.preferredPitch, draft, sampleSentence, !neural, sampleCue)
    fun stopPreview() { previewRevision++; previewJob?.cancel(); VoiceManager.releasePreviewEngine(); previewing = false }
    LaunchedEffect(sample, draft, voiceSettings) { stopPreview(); previewSentenceIndex = 0 }
    fun close() { if (draft != settings) discard = true else onDismiss() }
    fun editRole(update: (BookCharacter) -> BookCharacter) {
        draft = draft.copy(characterProfiles = draft.characterProfiles.map { if (it.id == selectedRole) update(it) else it })
    }
    androidx.activity.compose.BackHandler { close() }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { previewJob?.cancel(); VoiceManager.releasePreviewEngine() } }
    LaunchedEffect(voiceSettings.enginePackage, availableVoices) {
        val all = mutableListOf<TtsVoiceOption>()
        all.addAll(VoiceManager.loadVeritasVoices(context))
        VoiceManager.loadInstalledEngines(context).filterNot { VoiceManager.isVeritasEngine(it.packageName) }.forEach { engine ->
            runCatching { VoiceManager.loadVoices(context, engine.packageName) }
                .onSuccess { all.addAll(it) }.onFailure { voiceError = "Some engine voices could not be loaded: ${it.message}" }
        }
        voices = all.distinctBy { it.enginePackage to it.name }
    }
    if (discard) AlertDialog(
        onDismissRequest = { discard = false }, title = { Text("Discard narration changes?") },
        text = { Text("Your saved narration settings will stay as they are.") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("Keep editing") } }
    )
    if (addRole) {
        val name = roleName.trim()
        val duplicate = draft.characterProfiles.any { it.name.equals(name, true) }
        AlertDialog(onDismissRequest = { addRole = false }, title = { Text("Add character") },
            text = { OutlinedTextField(roleName, { roleName = it.take(60) }, label = { Text("Name as it appears in the book") },
                supportingText = { Text(if (duplicate) "This name already has a profile." else "A speech tag such as “Alice said” identifies this character.") },
                isError = duplicate, singleLine = true, shape = VeritasPackStyle.compactShape()) },
            confirmButton = { TextButton(enabled = name.isNotBlank() && !duplicate, onClick = {
                val role = BookCharacter(java.util.UUID.randomUUID().toString(), name)
                draft = draft.copy(characterProfiles = draft.characterProfiles + role)
                selectedRole = role.id; roleName = ""; addRole = false
            }) { Text("Add") } }, dismissButton = { TextButton(onClick = { addRole = false }) { Text("Cancel") } })
    }
    if (chooseVoice) AlertDialog(onDismissRequest = { chooseVoice = false }, title = { Text("Character voice") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(voiceSearch, { voiceSearch = it }, label = { Text("Search voices") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = VeritasPackStyle.compactShape())
                androidx.compose.foundation.lazy.LazyColumn(Modifier.height(300.dp)) {
                    item { TextButton(onClick = { editRole { it.copy(voiceName = null, enginePackage = null, localeTag = "") }; chooseVoice = false }) { Text("Use reading voice: ${voiceSettings.displayName}") } }
                    items(voices.filter { it.label.contains(voiceSearch, true) || it.localeTag.contains(voiceSearch, true) }.size) { index ->
                        val voice = voices.filter { it.label.contains(voiceSearch, true) || it.localeTag.contains(voiceSearch, true) }[index]
                        val installed = !VoiceManager.isVeritasVoice(voice.name) || com.veritas.reader.tts.VoiceModelManager.isVoiceInstalled(context, voice.name)
                        TextButton(onClick = { editRole { it.copy(voiceName = voice.name, enginePackage = voice.enginePackage, localeTag = voice.localeTag) }; chooseVoice = false }, enabled = installed, modifier = Modifier.fillMaxWidth()) {
                            Text(voice.label + " · " + when (voice.enginePackage) { VoiceManager.VERITAS_LITE -> "Lite"; VoiceManager.VERITAS_STUDIO -> "Studio"; else -> "System" } + if (!installed) " · Download in Voice Studio" else if (voice.requiresNetwork) " · Online" else " · On device", modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                voiceError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }, confirmButton = { TextButton(onClick = { chooseVoice = false }) { Text("Done") } })
    FullScreenSettingsScaffold(title = "Narration studio", onBack = { close() }) {
        Text("Shape the narrator and dialogue voices, then listen before applying. Your reading speed remains the starting point.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        NarrationPanel {
            NarrationToggle("Narration mode", "Use role-specific pacing and pitch", draft.enabled) { draft = draft.copy(enabled = it) }
            NarrationToggle("Detect dialogue", "Recognize quoted speech and speech tags", draft.dialogueDetection, draft.enabled) { draft = draft.copy(dialogueDetection = it) }
            NarrationToggle("Character voices", "Mix installed system, Lite and Studio voices in one cast", draft.fullCastEnabled, draft.enabled) { draft = draft.copy(fullCastEnabled = it) }
            NarrationToggle("Dialogue badges", "Identify the current role in the reader", draft.showDialogueBadges, draft.enabled) { draft = draft.copy(showDialogueBadges = it) }
            Text("Open quotes carry across sentences. Speaker tags and labels identify named characters; uncertain speech uses the default Dialogue profile.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SettingsHubSectionTitle("Voices and delivery")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            draft.characterProfiles.forEach { role ->
                FilterChip(selected = selectedRole == role.id, onClick = { selectedRole = role.id }, label = { Text(role.name) }, shape = VeritasPackStyle.chipShape())
            }
            OutlinedButton(onClick = { addRole = true }, shape = VeritasPackStyle.chipShape()) { Text("Add character") }
        }
        val role = draft.characterProfiles.firstOrNull { it.id == selectedRole } ?: draft.characterProfiles.firstOrNull()
        if (role != null) NarrationPanel {
            Text(role.name, style = MaterialTheme.typography.titleMedium)
            val label = voices.firstOrNull { it.name == role.voiceName }?.label ?: role.voiceName ?: voiceSettings.displayName
            OutlinedButton(onClick = { selectedRole = role.id; voiceSearch = ""; chooseVoice = true },
                enabled = draft.enabled && draft.fullCastEnabled, modifier = Modifier.fillMaxWidth(), shape = VeritasPackStyle.chipShape()) {
                Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("Pace: ${"%.2f".format(role.rateMultiplier)}× your reading speed", style = MaterialTheme.typography.bodyMedium)
            VeritasRoundSlider(role.rateMultiplier, { selectedRole = role.id; editRole { role -> role.copy(rateMultiplier = it) } },
                valueRange = 0.5f..2f, enabled = draft.enabled)
            Text("Pitch: ${"%.2f".format(role.pitchMultiplier)}×", style = MaterialTheme.typography.bodyMedium)
            VeritasRoundSlider(role.pitchMultiplier, { selectedRole = role.id; editRole { role -> role.copy(pitchMultiplier = it) } },
                valueRange = 0.7f..1.4f, enabled = draft.enabled)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { selectedRole = role.id; editRole { it.copy(rateMultiplier = 1f, pitchMultiplier = 1f) } }) { Text("Reset delivery") }
                if (role.id != "narrator" && role.id != "dialogue") TextButton(onClick = {
                    draft = draft.copy(characterProfiles = draft.characterProfiles.filterNot { it.id == role.id }); selectedRole = "narrator"
                }) { Text("Remove character") }
            }
        }
        SettingsHubSectionTitle("Punctuation")
        NarrationPanel {
            NarrationToggle("Express punctuation", if (neural) "Neural voices already interpret punctuation naturally" else "Gentle pacing cues; natural voice intonation", draft.punctuationExpressionEnabled, !neural) {
                draft = draft.copy(punctuationExpressionEnabled = it)
            }
            Text("Strength: ${(draft.punctuationExpressionStrength * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            VeritasRoundSlider(draft.punctuationExpressionStrength, { draft = draft.copy(punctuationExpressionStrength = it) }, valueRange = 0f..1f,
                enabled = !neural && draft.punctuationExpressionEnabled)
        }
        SettingsHubSectionTitle("Try a passage")
        NarrationPanel {
            OutlinedTextField(sample, { sample = it.take(600) }, label = { Text("Preview text") }, modifier = Modifier.fillMaxWidth(),
                minLines = 2, maxLines = 5, shape = VeritasPackStyle.compactShape())
            Text("${active.name} · ${"%.2f".format(rate)}× speed · ${"%.2f".format(pitch)}× pitch",
                style = MaterialTheme.typography.labelMedium)
            Text("Plays the passage sentence by sentence with the same role detection, voices and pronunciation rules as reading.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(enabled = sample.isNotBlank(), onClick = {
                if (previewing) stopPreview() else {
                    previewError = null; previewing = true
                    val revision = ++previewRevision
                    val roles = draft
                    val model = sampleModel
                    val cues = sampleCues
                    previewJob = previewScope.launch {
                        try {
                            model.sentences.forEachIndexed { index, sentence ->
                                if (revision != previewRevision) return@launch
                                previewSentenceIndex = index
                                val cue = cues[index]
                                if (index > 0 && cue.labelEnd > 0 && roles.enabled && roles.dialogueDetection) kotlinx.coroutines.delay(120)
                                val segment = com.veritas.reader.SpeechPlan.segment("preview", com.veritas.reader.SpeechPlan.revision(listOf(sample)),
                                    index, sentence.text, voiceSettings, roles, voiceSettings.preferredRate, voiceSettings.preferredPitch,
                                    com.veritas.reader.PronunciationRulesEngine.compile(pronunciationRules), cue,
                                    stripSpeakerLabel = roles.enabled && roles.dialogueDetection)
                                val spoken = segment.spoken.text
                                if (spoken.isNotBlank()) {
                                    val error = kotlinx.coroutines.withTimeout(30000) {
                                        kotlinx.coroutines.suspendCancellableCoroutine<String?> { continuation ->
                                            VoiceManager.previewVoice(context, segment.voice.enginePackage, segment.voice.voiceName, spoken,
                                                segment.rate, segment.pitch,
                                                onFinished = { error -> if (continuation.isActive) continuation.resume(error) })
                                            continuation.invokeOnCancellation { VoiceManager.releasePreviewEngine() }
                                        }
                                    }
                                    if (error != null) { previewError = error; return@launch }
                                }
                            }
                        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                            previewError = "The voice did not finish this sentence. Check its installation or connection."
                        } finally {
                            if (revision == previewRevision) { VoiceManager.releasePreviewEngine(); previewing = false }
                        }
                    }
                }
            }, shape = VeritasPackStyle.chipShape()) { Text(if (previewing) "Stop preview" else "Listen to preview") }
            previewError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        Button(onClick = { stopPreview(); onSettingsChange(draft); onDismiss() },
            modifier = Modifier.fillMaxWidth(), shape = VeritasPackStyle.chipShape()) { Text("Apply narration settings") }
    }
}

@Composable
private fun NarrationPanel(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = VeritasPackStyle.cardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun NarrationToggle(title: String, detail: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        VeritasSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
