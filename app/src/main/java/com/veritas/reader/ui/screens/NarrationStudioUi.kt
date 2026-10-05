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
    var voices by remember(availableVoices, voiceSettings.enginePackage) { mutableStateOf(availableVoices.filter { VoiceManager.isVeritasVoice(it.name) == VoiceManager.isVeritasEngine(voiceSettings.enginePackage) }) }
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
        if (voices.isEmpty()) {
            runCatching { VoiceManager.loadVoices(context, voiceSettings.enginePackage) }
                .onSuccess { voices = it.filter { voice -> VoiceManager.isVeritasVoice(voice.name) == neural }; voiceError = if (voices.isEmpty()) "No voices available. Choose or download a voice in Voice Studio." else null }
                .onFailure { voiceError = "Could not load voices. Check Voice Studio and try again." }
        }
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
                    item { TextButton(onClick = { editRole { it.copy(voiceName = null) }; chooseVoice = false }) { Text("Use reading voice: ${voiceSettings.displayName}") } }
                    items(voices.filter { it.label.contains(voiceSearch, true) || it.localeTag.contains(voiceSearch, true) }.size) { index ->
                        val voice = voices.filter { it.label.contains(voiceSearch, true) || it.localeTag.contains(voiceSearch, true) }[index]
                        val installed = !neural || com.veritas.reader.tts.VoiceModelManager.isVoiceInstalled(context, voice.name)
                        TextButton(onClick = { editRole { it.copy(voiceName = voice.name) }; chooseVoice = false }, enabled = installed, modifier = Modifier.fillMaxWidth()) {
                            Text(voice.label + if (!installed) " · Download in Voice Studio" else if (voice.requiresNetwork) " · Online" else " · On device", modifier = Modifier.fillMaxWidth())
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
            NarrationToggle("Character voices", "Choose voices from ${voiceSettings.engineLabel}", draft.fullCastEnabled, draft.enabled) { draft = draft.copy(fullCastEnabled = it) }
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
                                val character = NarrationAnalyzer.getActiveCharacter(sentence.text, roles, cue)
                                val name = if (roles.enabled && roles.fullCastEnabled) character.voiceName ?: voiceSettings.voiceName else voiceSettings.voiceName
                                val text = sentence.text.drop(if (roles.enabled && roles.dialogueDetection) cue.labelEnd else 0)
                                val spoken = com.veritas.reader.ReadingSymbols.expand(com.veritas.reader.SpokenText.identity(text)
                                    .applyRules(com.veritas.reader.PronunciationRulesEngine.compile(pronunciationRules)),
                                    voiceSettings.localeTag.ifBlank { java.util.Locale.getDefault().toLanguageTag() }.startsWith("en")).sanitize().text
                                if (spoken.isNotBlank()) {
                                    val error = kotlinx.coroutines.withTimeout(30000) {
                                        kotlinx.coroutines.suspendCancellableCoroutine<String?> { continuation ->
                                            VoiceManager.previewVoice(context, voiceSettings.enginePackage, name, spoken,
                                                NarrationAnalyzer.effectiveRate(voiceSettings.preferredRate, roles, text, !neural, cue),
                                                NarrationAnalyzer.effectivePitch(voiceSettings.preferredPitch, roles, text, !neural, cue),
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
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        VeritasSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun PronunciationRulesDialog(
    rules: List<PronunciationRule>,
    voiceSettings: com.veritas.reader.VoiceSettings = com.veritas.reader.VoiceSettings(),
    newFind: String,
    newReplaceWith: String,
    onNewFindChange: (String) -> Unit,
    onNewReplaceChange: (String) -> Unit,
    onAddRule: () -> Unit,
    onToggleRule: (PronunciationRule) -> Unit,
    onRemoveRule: (PronunciationRule) -> Unit,
    onDismiss: () -> Unit
) {
    var testInput by remember { mutableStateOf("Dr. Mensah met Kofi in Accra.") }
    val testOutput = remember(testInput, rules) { com.veritas.reader.PronunciationRulesEngine.apply(testInput, rules) }

    val context = LocalContext.current
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { VoiceManager.releasePreviewEngine() } }
    val presets = remember {
        listOf(
            "e.g." to "for example",
            "i.e." to "that is",
            "etc." to "etcetera",
            "Dr." to "Doctor",
            "vs." to "versus",
            "approx." to "approximately",
            "w/" to "with",
            "w/o" to "without",
            "ft." to "featuring",
            "no." to "number"
        )
    }

    FullScreenSettingsScaffold(title = "Pronunciation rules", onBack = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Change how a word or phrase is spoken without changing the text on the page. Matching ignores case; the pronunciation is spoken exactly as you enter it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Recommended abbreviation presets
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Common abbreviations",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { (find, replace) ->
                        val isAlreadyAdded = rules.any { it.find.equals(find, ignoreCase = true) }
                        FilterChip(
                            selected = isAlreadyAdded,
                            onClick = {
                                onNewFindChange(find)
                                onNewReplaceChange(replace)
                            },
                            label = { Text("$find → $replace") },
                            leadingIcon = if (isAlreadyAdded) {
                                { Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            }

            // Add new rule section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = com.veritas.reader.VeritasPackStyle.compactShape(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Word or phrase",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    OutlinedTextField(
                        value = newFind,
                        onValueChange = onNewFindChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Find word or phrase (e.g. SQL)") },
                        singleLine = true,
                        shape = com.veritas.reader.VeritasPackStyle.compactShape()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newReplaceWith,
                            onValueChange = onNewReplaceChange,
                            modifier = Modifier.weight(1f),
                            label = { Text("Say instead (e.g. sequel)") },
                            supportingText = { Text("Leave blank to skip this word when speaking.") },
                            singleLine = true,
                            shape = com.veritas.reader.VeritasPackStyle.compactShape()
                        )
                        IconButton(
                            onClick = {
                                if (newReplaceWith.isNotBlank()) {
                                    VoiceManager.previewVoice(
                                        context = context,
                                        enginePackage = voiceSettings.enginePackage,
                                        voiceName = voiceSettings.voiceName,
                                        rate = voiceSettings.preferredRate,
                                        pitch = voiceSettings.preferredPitch,
                                        text = newReplaceWith
                                    )
                                }
                            },
                            enabled = newReplaceWith.isNotBlank(),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.VolumeUp,
                                contentDescription = "Audition pronunciation",
                                tint = if (newReplaceWith.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            )
                        }
                    }

                    Button(
                        onClick = onAddRule,
                        enabled = newFind.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = com.veritas.reader.VeritasPackStyle.chipShape()
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (rules.any { it.find.equals(newFind.trim(), true) }) "Update rule" else "Save rule")
                    }
                }
            }

            // Spoken Reading Preview / Audition Box
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = com.veritas.reader.VeritasPackStyle.compactShape(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Try your rules",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = {
                                if (testOutput.isNotBlank()) {
                                    VoiceManager.previewVoice(
                                        context = context,
                                        enginePackage = voiceSettings.enginePackage,
                                        voiceName = voiceSettings.voiceName,
                                        rate = voiceSettings.preferredRate,
                                        pitch = voiceSettings.preferredPitch,
                                        text = testOutput
                                    )
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Hear preview",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    OutlinedTextField(
                        value = testInput,
                        onValueChange = { testInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Type test sentence") },
                        maxLines = 2,
                        shape = MaterialTheme.shapes.extraSmall
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Spoken output:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                if (testOutput.isNotBlank()) {
                                    VoiceManager.previewVoice(
                                        context = context,
                                        enginePackage = voiceSettings.enginePackage,
                                        voiceName = voiceSettings.voiceName,
                                        rate = voiceSettings.preferredRate,
                                        pitch = voiceSettings.preferredPitch,
                                        text = testOutput
                                    )
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Audition", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            testOutput,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            Text(
                "Saved rules (${rules.count { it.enabled }} enabled)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (rules.isEmpty()) {
                Text("No custom rules configured yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                rules.forEach { rule ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = com.veritas.reader.VeritasPackStyle.compactShape(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f).clickable { onNewFindChange(rule.find); onNewReplaceChange(rule.replaceWith) }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "${rule.find} → ${rule.replaceWith.ifBlank { "Skip when speaking" }}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    if (rule.enabled) "Active rule" else "Disabled",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (rule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        if (rule.replaceWith.isNotBlank()) {
                                            VoiceManager.previewVoice(
                                                context = context,
                                                enginePackage = voiceSettings.enginePackage,
                                                voiceName = voiceSettings.voiceName,
                                        rate = voiceSettings.preferredRate,
                                        pitch = voiceSettings.preferredPitch,
                                                text = rule.replaceWith
                                            )
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.VolumeUp,
                                        contentDescription = "Audition rule",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                VeritasSwitch(
                                    checked = rule.enabled,
                                    onCheckedChange = { onToggleRule(rule) },
                                    modifier = Modifier.scale(0.78f)
                                )
                                IconButton(
                                    onClick = { onRemoveRule(rule) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "Remove rule",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
