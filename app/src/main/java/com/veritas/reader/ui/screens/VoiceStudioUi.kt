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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.veritas.reader.R
import com.veritas.reader.VernAlertDialog as AlertDialog
import androidx.compose.ui.semantics.stateDescription
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
    onDismiss: () -> Unit,
    onVoiceShapingChange: (Boolean) -> Unit = {}
) {
    val downloadContext = LocalContext.current
    val downloadScope = rememberCoroutineScope()
    var pendingDownload by remember { mutableStateOf<com.veritas.reader.tts.OfflineVoiceDescriptor?>(null) }
    var pendingDelete by remember { mutableStateOf<com.veritas.reader.tts.OfflineVoiceDescriptor?>(null) }
    pendingDelete?.let { voice ->
        AlertDialog(onDismissRequest = { pendingDelete = null },
            title = { Text(menuString(R.string.voice_delete_title, voice.name)) },
            text = { Text(menuString(when (voice.engineType) {
                com.veritas.reader.tts.OfflineEngineType.KITTEN -> R.string.voice_delete_shared_notice
                com.veritas.reader.tts.OfflineEngineType.KOKORO -> R.string.voice_delete_studio_notice
                else -> R.string.voice_delete_notice
            })) },
            confirmButton = { TextButton(onClick = {
                pendingDelete = null
                VoiceManager.releasePreviewEngine()
                downloadScope.launch {
                    if (!com.veritas.reader.tts.VoiceModelManager.deleteVoice(downloadContext, voice.id)) {
                        android.widget.Toast.makeText(downloadContext, R.string.voice_delete_failed, android.widget.Toast.LENGTH_LONG).show()
                    }
                    onRefreshEngines(); onLoadVoices()
                }
            }) { Text(menuString(R.string.ui_delete_voice_model)) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(menuString(R.string.ui_cancel)) } })
    }
    pendingDownload?.let { voice ->
        AlertDialog(onDismissRequest = { pendingDownload = null },
            title = { Text(menuString(R.string.voice_download_title,
                if (voice.engineType == com.veritas.reader.tts.OfflineEngineType.PIPER) voice.name
                else com.veritas.reader.tts.VoiceModelManager.availablePackages.first { it.engineType == voice.engineType }.title)) },
            text = { Text(menuString(R.string.voice_download_notice,
                java.text.NumberFormat.getNumberInstance(LocalConfiguration.current.locales[0]).apply { maximumFractionDigits = 1 }
                    .format(com.veritas.reader.tts.VoiceModelManager.downloadSizeMb(voice)),
                menuString(when(voice.engineType) {
                    com.veritas.reader.tts.OfflineEngineType.KOKORO -> R.string.voice_studio_help
                    com.veritas.reader.tts.OfflineEngineType.KITTEN -> R.string.voice_kitten_help
                    else -> R.string.voice_lite_help
                }))) },
            confirmButton = { TextButton(onClick = {
                pendingDownload = null
                downloadScope.launch {
                    if(com.veritas.reader.tts.VoiceModelManager.downloadVoice(downloadContext,voice)) { onRefreshEngines(); onLoadVoices() }
                }
            }) { Text(menuString(R.string.ui_download)) } },
            dismissButton = { TextButton(onClick = { pendingDownload = null }) { Text(menuString(R.string.ui_cancel)) } })
    }
    val currentLocale = LocalConfiguration.current.locales[0]
    val voiceShapingLabel = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_voice_shaping)
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

    FullScreenSettingsScaffold(title = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_voice_and_language), onBack = onDismiss) {
        val cloudEditor = CloudVoiceSettingsPanel(onUsePhoneVoice = onUseSystemDefault)
        if (!cloudEditor) {
        // Active Voice Highlight Card
        SettingsHubSectionTitle(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_active_voice))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = VeritasPackStyle.cardShape(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_current_voice_model), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            localizedSettingsOption(activeVoiceDisplayName),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            voiceLanguageLabel(selectedLanguageTag, currentLocale),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        )
                    }
                    if (selectedVoice != null || settings.displayName.isNotBlank()) {
                        FilledTonalButton(onClick = { onPreviewActiveVoiceWithPreset() }) {
                            Icon(Icons.Outlined.GraphicEq, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_play_sample), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_sample))
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // Search & Language Controls
        SettingsHubSectionTitle(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_language_voice_library))

        OutlinedTextField(
            value = voiceSearchQuery,
            onValueChange = { voiceSearchQuery = it },
            placeholder = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_search_voices_or_languages), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = if (voiceSearchQuery.isNotBlank()) {
                { TextButton(onClick = { voiceSearchQuery = "" }) { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_clear), color = MaterialTheme.colorScheme.primary) } }
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
            settings.enginePackage == VoiceManager.KITTEN -> "Kitten"
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
                        localizedSettingsOption(currentEngineLabel),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge)
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
                                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_use_system_default), color = MaterialTheme.colorScheme.onSurface)
                                if (settings.enginePackage.isBlank()) {
                                    Icon(Icons.Filled.Check, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_selected), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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
                                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_use_vern_studio), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                if (settings.enginePackage == VoiceManager.VERITAS_STUDIO) {
                                    Icon(Icons.Filled.Check, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_selected), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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
                                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_use_vern_lite), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                                if (settings.enginePackage == VoiceManager.VERITAS_LITE) {
                                    Icon(Icons.Filled.Check, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_selected), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        onClick = {
                            managerMenuExpanded = false
                            onEngineSelected(TtsEngineOption(VoiceManager.VERITAS_LITE, "Vern Lite"))
                            onLoadVoices()
                        }
                    )
                    DropdownMenuItem(text = { Text("Kitten") },onClick = {
                        managerMenuExpanded = false
                        onEngineSelected(TtsEngineOption(VoiceManager.KITTEN,"Kitten")); onLoadVoices()
                    })
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
                                            Icon(Icons.Filled.Check, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_selected), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
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
            Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_include_online_network_voices), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }

        if (loadingVoices) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_loading_voice_models), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }

        // Downloading a voice from its own row needs these locally; the progress map
        // is observed so the row flips to a preview button when the install finishes.
        val voiceRowContext = LocalContext.current
        val voiceRowScope = rememberCoroutineScope()
        val voiceDownloadStates by com.veritas.reader.tts.VoiceModelManager.downloadState.collectAsState()

        // Voice List Cards
        filteredLanguageVoices.groupBy { it.enginePackage }.forEach { (engine,group) ->
        var expanded by rememberSaveable(engine,selectedLanguageTag) { mutableStateOf(false) }
        val groupTitle = engines.firstOrNull { it.packageName == engine }?.label?.let { localizedSettingsOption(it) }
            ?: menuString(R.string.cloud_phone)
        val expandedLabel = menuString(if(expanded) R.string.voice_list_expanded else R.string.voice_list_collapsed)
        val groupModel = group.firstNotNullOfOrNull { option ->
            com.veritas.reader.tts.VoiceModelManager.availableVoices.firstOrNull { it.id == option.name }
        }?.takeIf { it.engineType != com.veritas.reader.tts.OfflineEngineType.PIPER }
        Row(Modifier.fillMaxWidth().testTag("voice-group-$engine").semantics { stateDescription = expandedLabel }.clickable { expanded = !expanded }
            .padding(vertical = 12.dp),verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(groupTitle,style = MaterialTheme.typography.titleSmall)
                Text(menuString(R.string.voice_list_count,group.size),style = MaterialTheme.typography.bodySmall)
                if (groupModel != null) Text(
                    menuString(R.string.voice_shared_package_size,
                        java.text.NumberFormat.getNumberInstance(currentLocale).apply { maximumFractionDigits = 1 }
                            .format(com.veritas.reader.tts.VoiceModelManager.downloadSizeMb(groupModel)),
                        com.veritas.reader.tts.VoiceModelManager.availableVoices.count { it.engineType == groupModel.engineType }),
                    modifier = Modifier.testTag("voice-package-summary-$engine"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ArrowDropDown,null,Modifier.graphicsLayer { rotationZ = if(expanded) 180f else 0f })
        }
        group.filter { expanded || voiceSearchQuery.isNotBlank() ||
            com.veritas.reader.tts.VoiceModelManager.stateFor(it.name, voiceDownloadStates) is com.veritas.reader.tts.DownloadState.Downloading ||
            com.veritas.reader.tts.VoiceModelManager.stateFor(it.name, voiceDownloadStates) is com.veritas.reader.tts.DownloadState.Failed }.forEach { voice ->
            val isCurrent = voice.name == settings.voiceName
            val offlineVoice = com.veritas.reader.tts.VoiceModelManager.availableVoices
                .firstOrNull { it.id == voice.name }
            val needsDownload = offlineVoice != null &&
                !com.veritas.reader.tts.VoiceModelManager.isVoiceInstalled(voiceRowContext, voice.name)
            val needsQualityUpgrade = offlineVoice != null &&
                com.veritas.reader.tts.VoiceModelManager.needsQualityUpgrade(voiceRowContext, voice.name)
            val voiceDownload = com.veritas.reader.tts.VoiceModelManager.stateFor(voice.name, voiceDownloadStates)
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
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (isCurrent) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_active), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_active), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                        Text(
                            voiceProviderLabel(voice, currentLocale),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (offlineVoice != null && voiceDownload is com.veritas.reader.tts.DownloadState.Downloading) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(downloadStateLabel(voiceDownload), style = MaterialTheme.typography.labelSmall)
                            TextButton(onClick = { voiceRowScope.launch {
                                com.veritas.reader.tts.VoiceModelManager.cancelDownload(voiceRowContext, voice.name)
                            } }) { Text(menuString(R.string.voice_cancel_download)) }
                        }
                    } else if (offlineVoice != null && (needsDownload || needsQualityUpgrade)) {
                        when (val state = voiceDownload) {
                            is com.veritas.reader.tts.DownloadState.Downloading ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text(downloadStateLabel(state), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                            else -> TextButton(onClick = {
                                pendingDownload = offlineVoice
                            }) {
                                Text(
                                    if (offlineVoice.engineType != com.veritas.reader.tts.OfflineEngineType.PIPER)
                                        menuString(R.string.voice_download_package)
                                    else menuString(if (needsQualityUpgrade) R.string.settings_update_voice_size else R.string.settings_get_voice_size, offlineVoice.downloadSizeMb.toInt()),
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    } else {
                        IconButton(onClick = { onPreviewVoice(voice) }) {
                            Icon(Icons.Outlined.GraphicEq, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_preview_voice), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (offlineVoice != null && !needsDownload && voiceDownload !is com.veritas.reader.tts.DownloadState.Downloading) {
                        IconButton(onClick = { pendingDelete = offlineVoice }) {
                            Icon(Icons.Outlined.Delete, contentDescription = menuString(R.string.ui_delete_voice_model))
                        }
                    }
                }
                if(voiceDownload is com.veritas.reader.tts.DownloadState.Failed) Text(
                    if(voiceDownload.messageId != 0) menuString(voiceDownload.messageId) else voiceDownload.reason,
                    style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.error,modifier = Modifier.padding(12.dp))
            }
        }
        }

        if (filteredLanguageVoices.isEmpty() && !loadingVoices) {
            Text(
                com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_no_voices_found_matching_search_filter),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        SettingsHubSectionTitle(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_vern_voice_models))
        var downloadsExpanded by rememberSaveable { mutableStateOf(false) }
        TextButton(onClick = { downloadsExpanded = !downloadsExpanded },modifier = Modifier.fillMaxWidth()) {
            Text(menuString(if(downloadsExpanded) R.string.voice_hide_downloads else R.string.voice_show_downloads))
        }
        if(downloadsExpanded || voiceDownloadStates.values.any { it is com.veritas.reader.tts.DownloadState.Downloading || it is com.veritas.reader.tts.DownloadState.Failed }) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = VeritasPackStyle.cardShape(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
        ) {
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            val downloadStates by com.veritas.reader.tts.VoiceModelManager.downloadState.collectAsState()

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_download_voices_for_offline_reading_update_older_piper_voices_usi),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                com.veritas.reader.tts.VoiceModelManager.availablePackages.forEach { pkg ->
                    val isInstalled = com.veritas.reader.tts.VoiceModelManager.isVoiceInstalled(context, pkg.targetVoiceId)
                    val targetVoice = com.veritas.reader.tts.VoiceModelManager.availableVoices.firstOrNull { it.id == pkg.targetVoiceId }
                    val relatedStates = com.veritas.reader.tts.VoiceModelManager.availableVoices.filter { it.engineType == pkg.engineType }
                        .mapNotNull { com.veritas.reader.tts.VoiceModelManager.stateFor(it.id, downloadStates) }
                    val state = relatedStates.firstOrNull { it is com.veritas.reader.tts.DownloadState.Downloading }
                        ?: relatedStates.firstOrNull { it is com.veritas.reader.tts.DownloadState.Failed }
                        ?: downloadStates[pkg.targetVoiceId] ?: com.veritas.reader.tts.DownloadState.Idle
                    val installedAlpha by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (isInstalled) 1f else 0f,
                        animationSpec = com.veritas.reader.ui.VeritasMotion.effectsFast(), label = "voiceInstalledStatus")

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pkg.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (targetVoice != null && pkg.engineType != com.veritas.reader.tts.OfflineEngineType.PIPER)
                                    menuString(R.string.voice_shared_package_size,
                                        java.text.NumberFormat.getNumberInstance(currentLocale).apply { maximumFractionDigits = 1 }
                                            .format(com.veritas.reader.tts.VoiceModelManager.downloadSizeMb(targetVoice)),
                                        com.veritas.reader.tts.VoiceModelManager.availableVoices.count { it.engineType == pkg.engineType })
                                else menuString(R.string.voice_individual_downloads),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state is com.veritas.reader.tts.DownloadState.Downloading) {
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { state.percent / 100f },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(downloadStateLabel(state), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            } else if (state is com.veritas.reader.tts.DownloadState.Failed) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(if(state.messageId != 0) menuString(state.messageId) else state.reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Box(Modifier.width(148.dp), contentAlignment = androidx.compose.ui.Alignment.CenterEnd) {
                        if (state is com.veritas.reader.tts.DownloadState.Downloading) {
                            TextButton(onClick = { coroutineScope.launch {
                                com.veritas.reader.tts.VoiceModelManager.availableVoices.filter { it.engineType == pkg.engineType }
                                    .forEach { com.veritas.reader.tts.VoiceModelManager.cancelDownload(context, it.id) }
                            } }) { Text(menuString(R.string.voice_cancel_download)) }
                        } else if (pkg.engineType == com.veritas.reader.tts.OfflineEngineType.PIPER) {
                            TextButton(onClick = {
                                onEngineSelected(TtsEngineOption(VoiceManager.VERITAS_LITE, "Vern Lite"))
                                onLoadVoices()
                            }) { Text(menuString(R.string.voice_choose_individual)) }
                        } else if (isInstalled) {
                            Row(modifier = Modifier.graphicsLayer { alpha = installedAlpha },
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_installed), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_installed), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                                IconButton(onClick = {
                                    pendingDelete = targetVoice
                                }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_delete_voice_model), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (targetVoice != null) {
                                        pendingDownload = targetVoice
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_download), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        }
                    }
                }
            }
        }
        }

        // Voice Presets Gallery
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_voice_shaping), style = MaterialTheme.typography.titleSmall)
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_turn_off_the_app_s_tone_profile_speed_and_pitch_stay_as_selected),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            com.veritas.reader.ui.VeritasSwitch(checked = settings.voiceShaping, onCheckedChange = onVoiceShapingChange,
                modifier = Modifier.testTag("voice-shaping-toggle").semantics { contentDescription = voiceShapingLabel })
        }
        SettingsHubSectionTitle(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_voice_presets))
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
                                localizedSettingsOption(preset.name),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "${String.format(Locale.US, "%.2f", preset.rate)}x • ${String.format(Locale.US, "%.2f", preset.pitch)}p",
                                style = MaterialTheme.typography.labelSmall,
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
                                text = localizedSettingsOption(selectedPreset.name),
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
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = localizedSettingsOption(selectedPreset.summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }

        // Quick Navigation Studio Shortcuts
        SettingsHubSectionTitle(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_narration_shortcuts))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onOpenNarrationStudio,
                modifier = Modifier.weight(1f)
            ) {
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_narration_studio), color = MaterialTheme.colorScheme.onSurface)
            }
            OutlinedButton(
                onClick = onOpenSpeechEdits,
                modifier = Modifier.weight(1f)
            ) {
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_speech_edits), color = MaterialTheme.colorScheme.onSurface)
            }
        }

        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_use_selected_voice))
        }
        OutlinedButton(onClick = onAddLanguageVoice, modifier = Modifier.fillMaxWidth()) {
            Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_add_language_voice), color = MaterialTheme.colorScheme.onSurface)
        }
        PhoneVoiceDirectory()
        OutlinedButton(onClick = onOpenSystemTtsSettings, modifier = Modifier.fillMaxWidth()) {
            Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_system_tts_settings), color = MaterialTheme.colorScheme.onSurface)
        }
        }
    }
}

@Composable
private fun voiceLanguageLabel(localeTag: String, displayLocale: Locale): String {
    if (localeTag.isBlank()) return localizedSettingsOption(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_option_default_language))
    val locale = Locale.forLanguageTag(localeTag)
    return locale.getDisplayName(displayLocale).ifBlank { localeTag }
}

@Composable
private fun voiceProviderLabel(voice: TtsVoiceOption, displayLocale: Locale): String {
    val language = voiceLanguageLabel(voice.localeTag, displayLocale)
    val network = localizedSettingsOption(if (voice.requiresNetwork) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_option_network_voice) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_option_offline_voice))
    if(VoiceManager.isVeritasVoice(voice.name)) return "$language · $network"
    return com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_voice_details, language, network, voice.quality, voice.latency)
}

@Composable
private fun downloadStateLabel(state: com.veritas.reader.tts.DownloadState.Downloading): String =
    com.veritas.reader.ui.screens.menuString(when {
        state.retrying -> com.veritas.reader.R.string.voice_download_retrying
        state.queued -> com.veritas.reader.R.string.settings_queued
        state.installing -> com.veritas.reader.R.string.settings_installing
        state.totalBytes <= 0 -> com.veritas.reader.R.string.settings_downloading
        else -> com.veritas.reader.R.string.settings_downloading_percent
    }, state.percent)
