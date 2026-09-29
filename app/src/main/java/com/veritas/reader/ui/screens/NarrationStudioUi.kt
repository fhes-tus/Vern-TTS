package com.veritas.reader.ui.screens

import androidx.compose.foundation.BorderStroke
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
import com.veritas.reader.NarrationAnalyzer
import com.veritas.reader.NarrationSettings
import com.veritas.reader.PronunciationRule
import com.veritas.reader.TtsVoiceOption
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.VoiceManager
import com.veritas.reader.ui.VeritasSwitch

@Composable
fun NarrationStudioDialog(
    settings: NarrationSettings,
    sampleText: String,
    availableVoices: List<TtsVoiceOption> = emptyList(),
    onSettingsChange: (NarrationSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val sample = sampleText.ifBlank { "\"This is a sample dialogue line,\" she said. The narrator continues with a calmer sentence." }
    val sampleLabel = NarrationAnalyzer.labelFor(sample, settings)
    val sampleRate = NarrationAnalyzer.effectiveRate(1.0f, settings, sample)
    val samplePitch = NarrationAnalyzer.effectivePitch(1.0f, settings, sample)

    val context = LocalContext.current
    var loadedVoiceList by remember(availableVoices) { mutableStateOf(availableVoices) }

    LaunchedEffect(Unit) {
        if (loadedVoiceList.isEmpty()) {
            val list = runCatching { VoiceManager.loadVoices(context, "") }.getOrDefault(emptyList())
            if (list.isNotEmpty()) {
                loadedVoiceList = list
            }
        }
    }

    FullScreenSettingsScaffold(title = "Narration studio", onBack = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = VeritasPackStyle.cardShape(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Outlined.RecordVoiceOver,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        if (settings.enabled) "Narration mode active" else "Narration mode inactive",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Text(
                    "Dynamically adjusts speech delivery and character voices for spoken dialogue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }

        SettingsHubSectionTitle("Dialogue detection")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = VeritasPackStyle.cardShape(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable narration mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Custom character voices & pacing for dialogue", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    VeritasSwitch(checked = settings.enabled, onCheckedChange = { onSettingsChange(settings.copy(enabled = it)) })
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Detect dialogue quotes", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Auto-detect quotes, dash dialogue, and speech tags", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    VeritasSwitch(
                        checked = settings.dialogueDetection,
                        onCheckedChange = { onSettingsChange(settings.copy(dialogueDetection = it)) },
                        enabled = settings.enabled
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show dialogue indicators", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Display badges for spoken dialogue in reader", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    VeritasSwitch(
                        checked = settings.showDialogueBadges,
                        onCheckedChange = { onSettingsChange(settings.copy(showDialogueBadges = it)) },
                        enabled = settings.enabled
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Full-cast multi-voice mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Assign unique voice models per character", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    VeritasSwitch(
                        checked = settings.fullCastEnabled,
                        onCheckedChange = { onSettingsChange(settings.copy(fullCastEnabled = it)) },
                        enabled = settings.enabled
                    )
                }
            }
        }

        SettingsHubSectionTitle("Character voice profiles")

        var newCharacterName by remember { mutableStateOf("") }
        var showAddCharDialog by remember { mutableStateOf(false) }

        if (showAddCharDialog) {
            AlertDialog(
                onDismissRequest = { showAddCharDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Add character profile")
                    }
                },
                text = {
                    OutlinedTextField(
                        value = newCharacterName,
                        onValueChange = { newCharacterName = it },
                        label = { Text("Character name (e.g. Alice)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newCharacterName.isNotBlank()) {
                                val newChar = BookCharacter(
                                    id = java.util.UUID.randomUUID().toString(),
                                    name = newCharacterName.trim(),
                                    genderLabel = "Neutral"
                                )
                                onSettingsChange(settings.copy(characterProfiles = settings.characterProfiles + newChar))
                                newCharacterName = ""
                                showAddCharDialog = false
                            }
                        },
                        shape = RoundedCornerShape(50)
                    ) { Text("Add") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddCharDialog = false }, shape = RoundedCornerShape(50)) { Text("Cancel") }
                }
            )
        }

        settings.characterProfiles.forEach { char ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = VeritasPackStyle.cardShape(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            char.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val previewText = when (char.id) {
                                    "narrator" -> "This is how the narrator speaks descriptive scenes and exposition."
                                    "dialogue" -> "\"Hello there!\" exclaimed the speaker with emotion."
                                    else -> "\"My name is ${char.name}, and this is my character voice.\""
                                }
                                VoiceManager.previewVoice(
                                    context = context,
                                    enginePackage = "",
                                    voiceName = char.voiceName.orEmpty(),
                                    text = previewText,
                                    pitch = char.pitchMultiplier
                                )
                            },
                            enabled = settings.enabled
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Preview voice",
                                tint = if (settings.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        }
                        if (char.id != "narrator" && char.id != "dialogue") {
                            IconButton(
                                onClick = {
                                    onSettingsChange(settings.copy(characterProfiles = settings.characterProfiles.filterNot { it.id == char.id }))
                                }
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Remove character", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    var voiceMenuExpanded by remember { mutableStateOf(false) }
                    val currentVoiceLabel = loadedVoiceList.find { it.name == char.voiceName }?.label
                        ?: if (char.voiceName.isNullOrBlank()) "System Default Voice" else char.voiceName

                    Text("Assigned Voice Model:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box {
                        OutlinedButton(
                            onClick = { voiceMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = settings.enabled,
                            shape = VeritasPackStyle.chipShape()
                        ) {
                            Text(currentVoiceLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        DropdownMenu(
                            expanded = voiceMenuExpanded,
                            onDismissRequest = { voiceMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("System Default Voice") },
                                onClick = {
                                    val updated = settings.characterProfiles.map { if (it.id == char.id) it.copy(voiceName = null) else it }
                                    onSettingsChange(settings.copy(characterProfiles = updated))
                                    voiceMenuExpanded = false
                                }
                            )
                            loadedVoiceList.forEach { voiceOpt ->
                                DropdownMenuItem(
                                    text = { Text(voiceOpt.label) },
                                    onClick = {
                                        val updated = settings.characterProfiles.map { if (it.id == char.id) it.copy(voiceName = voiceOpt.name) else it }
                                        onSettingsChange(settings.copy(characterProfiles = updated))
                                        voiceMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val archetypes = listOf(
                            Triple("🎙️ Natural", 1.00f, 1.00f),
                            Triple("📖 Deep/Warm", 0.85f, 0.95f),
                            Triple("💬 Bright/High", 1.20f, 1.05f),
                            Triple("🎓 Scholar", 0.90f, 0.90f)
                        )
                        archetypes.forEach { (label, p, r) ->
                            FilterChip(
                                selected = (kotlin.math.abs(char.pitchMultiplier - p) < 0.025f && kotlin.math.abs(char.rateMultiplier - r) < 0.025f),
                                onClick = {
                                    val updated = settings.characterProfiles.map {
                                        if (it.id == char.id) it.copy(pitchMultiplier = p, rateMultiplier = r) else it
                                    }
                                    onSettingsChange(settings.copy(characterProfiles = updated))
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                enabled = settings.enabled,
                                shape = VeritasPackStyle.chipShape()
                            )
                        }
                    }

                    val pitchOffsetPercent = ((char.pitchMultiplier - 1.0f) * 100).toInt()
                    val pitchLabel = when {
                        char.pitchMultiplier >= 1.15f -> "Bright / Crisp High"
                        char.pitchMultiplier > 1.03f -> "Slightly High"
                        char.pitchMultiplier <= 0.85f -> "Rich Deep / Bass"
                        char.pitchMultiplier < 0.97f -> "Warm Deep"
                        else -> "Natural / Normal"
                    }
                    Text("Pitch Tone: ${"%.2f".format(char.pitchMultiplier)}x ($pitchLabel)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    VeritasRoundSlider(
                        value = char.pitchMultiplier,
                        onValueChange = { newP ->
                            val updated = settings.characterProfiles.map { if (it.id == char.id) it.copy(pitchMultiplier = newP) else it }
                            onSettingsChange(settings.copy(characterProfiles = updated))
                        },
                        valueRange = 0.70f..1.35f,
                        enabled = settings.enabled
                    )

                    val rateOffsetPercent = ((char.rateMultiplier - 1.0f) * 100).toInt()
                    val rateLabel = when {
                        rateOffsetPercent > 0 -> "+$rateOffsetPercent% faster"
                        rateOffsetPercent < 0 -> "$rateOffsetPercent% slower"
                        else -> "Standard pacing"
                    }
                    Text("Speech Pace: ${"%.2f".format(char.rateMultiplier)}x ($rateLabel)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    VeritasRoundSlider(
                        value = char.rateMultiplier,
                        onValueChange = { newR ->
                            val updated = settings.characterProfiles.map { if (it.id == char.id) it.copy(rateMultiplier = newR) else it }
                            onSettingsChange(settings.copy(characterProfiles = updated))
                        },
                        valueRange = 0.85f..1.20f,
                        enabled = settings.enabled
                    )
                }
            }
        }

        Button(
            onClick = { showAddCharDialog = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = settings.enabled,
            shape = RoundedCornerShape(50)
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Add Character Profile")
        }

        SettingsHubSectionTitle("Preview classification & spoken audio")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = VeritasPackStyle.cardShape(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(sample.take(260), maxLines = 5, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            "Detected as: $sampleLabel • ${"%.2f".format(sampleRate)}× • pitch ${"%.2f".format(samplePitch)}",
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    IconButton(
                        onClick = {
                            VoiceManager.previewVoice(
                                context = context,
                                enginePackage = "",
                                voiceName = "",
                                text = sample,
                                pitch = samplePitch
                            )
                        }
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play classification preview", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun PronunciationRulesDialog(
    rules: List<PronunciationRule>,
    newFind: String,
    newReplaceWith: String,
    onNewFindChange: (String) -> Unit,
    onNewReplaceChange: (String) -> Unit,
    onAddRule: () -> Unit,
    onToggleRule: (PronunciationRule) -> Unit,
    onRemoveRule: (PronunciationRule) -> Unit,
    onDismiss: () -> Unit
) {
    var testInput by remember { mutableStateOf("e.g. Test pronunciation replacements here.") }
    val testOutput = remember(testInput, rules) {
        var output = testInput
        rules.filter { it.enabled && it.find.isNotBlank() }.forEach { rule ->
            val escapedFind = Regex.escape(rule.find)
            val startsWithWordChar = rule.find.firstOrNull()?.let { it.isLetterOrDigit() || it == '_' } == true
            val endsWithWordChar = rule.find.lastOrNull()?.let { it.isLetterOrDigit() || it == '_' } == true
            val prefix = if (startsWithWordChar) "\\b" else ""
            val suffix = if (endsWithWordChar) "\\b" else ""
            val pattern = "$prefix$escapedFind$suffix"
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            output = regex.replace(output) { matchResult ->
                val matchedText = matchResult.value
                val replacement = rule.replaceWith
                when {
                    replacement.isEmpty() -> ""
                    matchedText.all { it.isUpperCase() } -> replacement.uppercase(java.util.Locale.getDefault())
                    matchedText.firstOrNull()?.isUpperCase() == true -> {
                        replacement.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                    }
                    else -> replacement
                }
            }
        }
        output
    }

    val context = LocalContext.current
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
                "Replace awkward TTS pronunciations, abbreviations, and symbols before sentences are spoken.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Recommended abbreviation presets
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Quick Presets",
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
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Add New Rule",
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
                        shape = RoundedCornerShape(12.dp)
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
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        IconButton(
                            onClick = {
                                if (newReplaceWith.isNotBlank()) {
                                    VoiceManager.previewVoice(
                                        context = context,
                                        enginePackage = "",
                                        voiceName = "",
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
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Rule")
                    }
                }
            }

            // Spoken Reading Preview / Audition Box
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
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
                            "Live Audition & Test",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = {
                                if (testOutput.isNotBlank()) {
                                    VoiceManager.previewVoice(
                                        context = context,
                                        enginePackage = "",
                                        voiceName = "",
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
                        shape = RoundedCornerShape(8.dp)
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
                                        enginePackage = "",
                                        voiceName = "",
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
                        shape = RoundedCornerShape(8.dp),
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
                "Active rules (${rules.size})",
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
                        shape = RoundedCornerShape(12.dp),
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
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    "${rule.find} → ${rule.replaceWith}",
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
                                                enginePackage = "",
                                                voiceName = "",
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
