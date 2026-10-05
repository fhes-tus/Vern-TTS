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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.TtsEngineOption
import com.veritas.reader.TtsVoiceOption
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.VoiceManager
import com.veritas.reader.VoiceNamingRegistry
import com.veritas.reader.VoiceSettings
import com.veritas.reader.voicePresets
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun VoiceStudioDialog(
    settings: VoiceSettings,
    engines: List<TtsEngineOption>,
    voices: List<TtsVoiceOption>,
    loadingVoices: Boolean,
    onRefreshEngines: () -> Unit,
    onLoadVoices: () -> Unit,
    onUseSystemDefault: () -> Unit,
    onEngineSelected: (TtsEngineOption) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onShowNetworkVoicesChange: (Boolean) -> Unit,
    onVoiceSelected: (TtsVoiceOption) -> Unit,
    onPreviewVoice: (TtsVoiceOption) -> Unit,
    onPreviewActiveVoiceWithPreset: () -> Unit = {},
    onPresetSelected: (String, Float, Float) -> Unit,
    onAddLanguageVoice: () -> Unit,
    onOpenSystemTtsSettings: () -> Unit,
    onOpenSpeechEdits: () -> Unit,
    onOpenNarrationStudio: () -> Unit,
    onDismiss: () -> Unit
) {
    val currentLocale = LocalConfiguration.current.locales[0]
    var managerMenuExpanded by remember { mutableStateOf(false) }
    var languageMenuExpanded by remember { mutableStateOf(false) }
    val visibleVoices = remember(voices, settings.showNetworkVoices, currentLocale) {
        voices
            .filter { settings.showNetworkVoices || !it.requiresNetwork }
            .sortedWith { a, b ->
                val aLoc = a.localeTag
                val bLoc = b.localeTag
                val aEn = aLoc.startsWith("en", ignoreCase = true)
                val bEn = bLoc.startsWith("en", ignoreCase = true)
                val locCompare = when {
                    aEn && !bEn -> -1
                    !aEn && bEn -> 1
                    aEn && bEn -> {
                        val aUS = aLoc.equals("en-US", ignoreCase = true) || aLoc.equals("en_US", ignoreCase = true)
                        val bUS = bLoc.equals("en-US", ignoreCase = true) || bLoc.equals("en_US", ignoreCase = true)
                        val aGB = aLoc.equals("en-GB", ignoreCase = true) || aLoc.equals("en_GB", ignoreCase = true)
                        val bGB = bLoc.equals("en-GB", ignoreCase = true) || bLoc.equals("en_GB", ignoreCase = true)
                        when {
                            aUS && !bUS -> -1
                            !aUS && bUS -> 1
                            aGB && !bGB -> -1
                            !aGB && bGB -> 1
                            else -> aLoc.compareTo(bLoc, ignoreCase = true)
                        }
                    }
                    else -> aLoc.compareTo(bLoc, ignoreCase = true)
                }
                if (locCompare != 0) locCompare
                else a.name.compareTo(b.name, ignoreCase = true)
            }
    }
    val languageTags = remember(visibleVoices) {
        visibleVoices.map { it.localeTag }.distinct().sortedWith { a, b ->
            val aEn = a.startsWith("en", ignoreCase = true)
            val bEn = b.startsWith("en", ignoreCase = true)
            when {
                aEn && !bEn -> -1
                !aEn && bEn -> 1
                aEn && bEn -> {
                    val aUS = a.equals("en-US", ignoreCase = true) || a.equals("en_US", ignoreCase = true)
                    val bUS = b.equals("en-US", ignoreCase = true) || b.equals("en_US", ignoreCase = true)
                    val aGB = a.equals("en-GB", ignoreCase = true) || a.equals("en_GB", ignoreCase = true)
                    val bGB = b.equals("en-GB", ignoreCase = true) || b.equals("en_GB", ignoreCase = true)
                    when {
                        aUS && !bUS -> -1
                        !aUS && bUS -> 1
                        aGB && !bGB -> -1
                        !aGB && bGB -> 1
                        else -> a.compareTo(b, ignoreCase = true)
                    }
                }
                else -> a.compareTo(b, ignoreCase = true)
            }
        }
    }
    var selectedLanguageTag by remember(settings.localeTag, languageTags) {
        mutableStateOf(
            when {
                settings.localeTag.isNotBlank() && settings.localeTag in languageTags -> settings.localeTag
                languageTags.contains(currentLocale.toLanguageTag()) -> currentLocale.toLanguageTag()
                languageTags.isNotEmpty() -> languageTags.first()
                else -> settings.localeTag
            }
        )
    }

    val languageVoices = remember(visibleVoices, selectedLanguageTag) {
        if (selectedLanguageTag.isBlank()) visibleVoices else visibleVoices.filter { it.localeTag == selectedLanguageTag }
    }
    val selectedVoice = voices.firstOrNull { it.name == settings.voiceName }

    val activeVoiceDisplayName = remember(selectedVoice, settings) {
        if (selectedVoice != null) {
            selectedVoice.label.ifBlank {
                VoiceNamingRegistry.resolveHumanName(selectedVoice.name, selectedVoice.localeTag, selectedVoice.requiresNetwork)
            }
        } else if (settings.voiceName.isNotBlank()) {
            VoiceNamingRegistry.resolveHumanName(settings.voiceName, settings.localeTag, false)
        } else {
            settings.displayName.ifBlank { "System default voice" }
        }
    }

    var voiceSearchQuery by remember { mutableStateOf("") }
    val filteredLanguageVoices = remember(languageVoices, voiceSearchQuery) {
        if (voiceSearchQuery.isBlank()) languageVoices
        else languageVoices.filter {
            it.name.contains(voiceSearchQuery, ignoreCase = true) ||
            it.label.contains(voiceSearchQuery, ignoreCase = true) ||
            it.localeTag.contains(voiceSearchQuery, ignoreCase = true)
        }
    }

    LaunchedEffect(settings.enginePackage) {
        onRefreshEngines()
        onLoadVoices()
    }

    FullScreenSettingsScaffold(title = "Voice and language", onBack = onDismiss) {
        // Active Voice Highlight Card
        SettingsHubSectionTitle("Active voice")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = VeritasPackStyle.cardShape(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("CURRENT VOICE MODEL", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            activeVoiceDisplayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            voiceLanguageLabel(selectedLanguageTag, currentLocale),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    if (selectedVoice != null || settings.displayName.isNotBlank()) {
                        FilledTonalButton(onClick = { onPreviewActiveVoiceWithPreset() }) {
                            Icon(Icons.Outlined.GraphicEq, contentDescription = "Play Sample", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sample")
                        }
                    }
                }
            }
        }

        // Voice Presets Gallery
        SettingsHubSectionTitle("Voice presets")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            voicePresets().forEach { preset ->
                val isSelected = settings.profileName.equals(preset.name, ignoreCase = true)
                FilterChip(
                    selected = isSelected,
                    onClick = { onPresetSelected(preset.name, preset.rate, preset.pitch) },
                    label = {
                        Column(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(
                                preset.name,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "${String.format(Locale.US, "%.2f", preset.rate)}x • ${String.format(Locale.US, "%.2f", preset.pitch)}p",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        voicePresets().firstOrNull { settings.profileName.equals(it.name, ignoreCase = true) }?.let { selectedPreset ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = com.veritas.reader.VeritasPackStyle.compactShape(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = selectedPreset.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text(
                                    text = "${String.format(Locale.US, "%.2f", selectedPreset.rate)}x • ${String.format(Locale.US, "%.2f", selectedPreset.pitch)}p",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = selectedPreset.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        // Search & Language Controls
        SettingsHubSectionTitle("Language & voice library")

        OutlinedTextField(
            value = voiceSearchQuery,
            onValueChange = { voiceSearchQuery = it },
            placeholder = { Text("Search voices or languages...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = if (voiceSearchQuery.isNotBlank()) {
                { TextButton(onClick = { voiceSearchQuery = "" }) { Text("Clear", color = MaterialTheme.colorScheme.primary) } }
            } else null,
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.fillMaxWidth()
        )

        val currentEngineLabel = when {
            settings.enginePackage == VoiceManager.VERITAS_STUDIO -> "Vern Studio"
            settings.enginePackage == VoiceManager.VERITAS_LITE -> "Vern Lite"
            settings.engineLabel.isNotBlank() && settings.engineLabel != "System default" -> settings.engineLabel
            settings.enginePackage.isNotBlank() -> settings.enginePackage
            else -> "System Default"
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { languageMenuExpanded = true },
                    enabled = visibleVoices.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        voiceLanguageLabel(selectedLanguageTag, currentLocale),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                }
                DropdownMenu(
                    expanded = languageMenuExpanded,
                    onDismissRequest = { languageMenuExpanded = false },
                    modifier = Modifier.width(320.dp).heightIn(max = 360.dp)
                ) {
                    languageTags.forEach { localeTag ->
                        DropdownMenuItem(
                            text = { Text(voiceLanguageLabel(localeTag, currentLocale), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface) },
                            onClick = {
                                languageMenuExpanded = false
                                selectedLanguageTag = localeTag
                                onLanguageSelected(localeTag)
                            }
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { managerMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        currentEngineLabel,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                }
                DropdownMenu(
                    expanded = managerMenuExpanded,
                    onDismissRequest = { managerMenuExpanded = false },
                    modifier = Modifier.width(280.dp).heightIn(max = 360.dp)
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Use System Default", color = MaterialTheme.colorScheme.onSurface)
                                if (settings.enginePackage.isBlank()) {
                                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        onClick = {
                            managerMenuExpanded = false
                            onUseSystemDefault()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Use Vern Studio", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                if (settings.enginePackage == VoiceManager.VERITAS_STUDIO) {
                                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        onClick = {
                            managerMenuExpanded = false
                            onEngineSelected(TtsEngineOption(VoiceManager.VERITAS_STUDIO, "Vern Studio"))
                            onLoadVoices()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Use Vern Lite", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                if (settings.enginePackage == VoiceManager.VERITAS_LITE) {
                                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        onClick = {
                            managerMenuExpanded = false
                            onEngineSelected(TtsEngineOption(VoiceManager.VERITAS_LITE, "Vern Lite"))
                            onLoadVoices()
                        }
                    )
                    val systemEngines = engines.filterNot { VoiceManager.isVeritasEngine(it.packageName) }
                    if (systemEngines.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        systemEngines.forEach { engine ->
                            val isSelected = settings.enginePackage == engine.packageName
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(engine.label, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                                        if (isSelected) {
                                            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                },
                                onClick = {
                                    managerMenuExpanded = false
                                    onEngineSelected(engine)
                                    onLoadVoices()
                                }
                            )
                        }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = settings.showNetworkVoices,
                onCheckedChange = onShowNetworkVoicesChange
            )
            Text("Include online/network voices", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }

        if (loadingVoices) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text("Loading voice models...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        // Downloading a voice from its own row needs these locally; the progress map
        // is observed so the row flips to a preview button when the install finishes.
        val voiceRowContext = LocalContext.current
        val voiceRowScope = rememberCoroutineScope()
        val voiceDownloadStates by com.veritas.reader.tts.VoiceModelManager.downloadState.collectAsState()

        // Voice List Cards
        filteredLanguageVoices.forEach { voice ->
            val isCurrent = voice.name == settings.voiceName
            val offlineVoice = com.veritas.reader.tts.VoiceModelManager.availableVoices
                .firstOrNull { it.id == voice.name }
            val needsDownload = offlineVoice != null &&
                !com.veritas.reader.tts.VoiceModelManager.isVoiceInstalled(voiceRowContext, voice.name)
            val voiceDownload = voiceDownloadStates[voice.name]
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !needsDownload) { onVoiceSelected(voice) },
                shape = VeritasPackStyle.cardShape(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    else MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = if (isCurrent) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                voice.label.ifBlank { voice.name },
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isCurrent) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = "Active", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                    Text("Active", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        Text(
                            voiceProviderLabel(voice, currentLocale),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (offlineVoice != null && needsDownload) {
                        when (val state = voiceDownload) {
                            is com.veritas.reader.tts.DownloadState.Downloading ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text("${state.percent}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            else -> TextButton(onClick = {
                                voiceRowScope.launch {
                                    com.veritas.reader.tts.VoiceModelManager.downloadVoice(voiceRowContext, offlineVoice)
                                }
                            }) {
                                Text(
                                    "Get · ${offlineVoice.downloadSizeMb.toInt()}MB",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        IconButton(onClick = { onPreviewVoice(voice) }) {
                            Icon(Icons.Outlined.GraphicEq, contentDescription = "Preview voice", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        if (filteredLanguageVoices.isEmpty() && !loadingVoices) {
            Text(
                "No voices found matching search filter.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        SettingsHubSectionTitle("Vern voice models")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = VeritasPackStyle.cardShape(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
        ) {
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            val downloadStates by com.veritas.reader.tts.VoiceModelManager.downloadState.collectAsState()
            var refreshTrigger by remember { mutableIntStateOf(0) }

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Download offline neural voice packages for studio-quality human cadence.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                com.veritas.reader.tts.VoiceModelManager.availablePackages.forEach { pkg ->
                    val isInstalled = remember(refreshTrigger) {
                        com.veritas.reader.tts.VoiceModelManager.isVoiceInstalled(context, pkg.targetVoiceId)
                    }
                    val targetVoice = com.veritas.reader.tts.VoiceModelManager.availableVoices.firstOrNull { it.id == pkg.targetVoiceId }
                    val state = downloadStates[pkg.targetVoiceId] ?: com.veritas.reader.tts.DownloadState.Idle

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pkg.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                pkg.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state is com.veritas.reader.tts.DownloadState.Downloading) {
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { state.percent / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text("Downloading ${state.percent}%...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            } else if (state is com.veritas.reader.tts.DownloadState.Failed) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(state.reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        if (isInstalled) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Installed", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text("Installed", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                IconButton(onClick = {
                                    com.veritas.reader.tts.VoiceModelManager.deleteVoice(context, pkg.targetVoiceId)
                                    refreshTrigger++
                                    onRefreshEngines()
                                    onLoadVoices()
                                }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Delete voice model", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                }
                            }
                        } else if (state is com.veritas.reader.tts.DownloadState.Downloading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Button(
                                onClick = {
                                    if (targetVoice != null) {
                                        coroutineScope.launch {
                                            val ok = com.veritas.reader.tts.VoiceModelManager.downloadVoice(context, targetVoice)
                                            if (ok) {
                                                refreshTrigger++
                                                onRefreshEngines()
                                                onLoadVoices()
                                            }
                                        }
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Download", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        // Quick Navigation Studio Shortcuts
        SettingsHubSectionTitle("Narration shortcuts")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onOpenNarrationStudio,
                modifier = Modifier.weight(1f)
            ) {
                Text("Narration Studio", color = MaterialTheme.colorScheme.onSurface)
            }
            OutlinedButton(
                onClick = onOpenSpeechEdits,
                modifier = Modifier.weight(1f)
            ) {
                Text("Speech Edits", color = MaterialTheme.colorScheme.onSurface)
            }
        }

        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Use Selected Voice")
        }
        OutlinedButton(onClick = onAddLanguageVoice, modifier = Modifier.fillMaxWidth()) {
            Text("Add Language / Voice", color = MaterialTheme.colorScheme.onSurface)
        }
        OutlinedButton(onClick = onOpenSystemTtsSettings, modifier = Modifier.fillMaxWidth()) {
            Text("System TTS Settings", color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun voiceLanguageLabel(localeTag: String, displayLocale: Locale): String {
    if (localeTag.isBlank()) return "Default language"
    val locale = Locale.forLanguageTag(localeTag)
    return locale.getDisplayName(displayLocale).ifBlank { localeTag }
}

private fun voiceProviderLabel(voice: TtsVoiceOption, displayLocale: Locale): String {
    val language = voiceLanguageLabel(voice.localeTag, displayLocale)
    val network = if (voice.requiresNetwork) "Network voice" else "Offline voice"
    return "$language, $network, quality ${voice.quality}, latency ${voice.latency}"
}
