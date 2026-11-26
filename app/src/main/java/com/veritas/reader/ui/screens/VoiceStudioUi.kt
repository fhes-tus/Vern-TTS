package com.veritas.reader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.SpeechDelivery
import com.veritas.reader.TtsEngineOption
import com.veritas.reader.TtsVoiceOption
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.VoiceManager
import com.veritas.reader.VoiceSettings
import com.veritas.reader.voicePresets
import com.veritas.reader.tts.DownloadState
import com.veritas.reader.tts.OfflineEngineType
import com.veritas.reader.tts.OfflineVoiceDescriptor
import com.veritas.reader.tts.VoiceModelManager
import com.veritas.reader.R
import com.veritas.reader.VernAlertDialog as AlertDialog
import androidx.compose.ui.semantics.stateDescription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import kotlin.math.roundToInt
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
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
    onPreviewActiveVoiceWithPreset: (Float, Float) -> Unit = { _, _ -> },
    onPresetSelected: (String, Float, Float) -> Unit,
    onAddLanguageVoice: () -> Unit,
    onOpenSystemTtsSettings: () -> Unit,
    onDismiss: () -> Unit,
    onVoiceShapingChange: (Boolean) -> Unit = {}
) {
    val downloadContext = LocalContext.current
    val downloadScope = rememberCoroutineScope()
    var pendingDownload by remember { mutableStateOf<OfflineVoiceDescriptor?>(null) }
    var pendingDelete by remember { mutableStateOf<OfflineVoiceDescriptor?>(null) }
    pendingDelete?.let { voice ->
        AlertDialog(onDismissRequest = { pendingDelete = null },
            title = { Text(menuString(R.string.voice_delete_title, voice.name)) },
            text = { Text(menuString(when (voice.engineType) {
                OfflineEngineType.KITTEN -> R.string.voice_delete_shared_notice
                OfflineEngineType.KOKORO -> R.string.voice_delete_studio_notice
                else -> R.string.voice_delete_notice
            })) },
            confirmButton = { TextButton(onClick = {
                pendingDelete = null
                VoiceManager.releasePreviewEngine()
                downloadScope.launch {
                    if (!VoiceModelManager.deleteVoice(downloadContext, voice.id)) {
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
                if (voice.engineType == OfflineEngineType.PIPER) voice.name
                else VoiceModelManager.availablePackages.first { it.engineType == voice.engineType }.title)) },
            text = { Text(menuString(R.string.voice_download_notice,
                java.text.NumberFormat.getNumberInstance(LocalConfiguration.current.locales[0]).apply { maximumFractionDigits = 1 }
                    .format(VoiceModelManager.downloadSizeMb(voice)),
                menuString(when(voice.engineType) {
                    OfflineEngineType.KOKORO -> R.string.voice_studio_help
                    OfflineEngineType.KITTEN -> R.string.voice_kitten_help
                    else -> R.string.voice_lite_help
                }))) },
            confirmButton = { TextButton(onClick = {
                pendingDownload = null
                downloadScope.launch {
                    if(VoiceModelManager.downloadVoice(downloadContext,voice)) { onRefreshEngines(); onLoadVoices() }
                }
            }) { Text(menuString(R.string.ui_download)) } },
            dismissButton = { TextButton(onClick = { pendingDownload = null }) { Text(menuString(R.string.ui_cancel)) } })
    }
    val currentLocale = LocalConfiguration.current.locales[0]
    val voiceShapingLabel = menuString(R.string.ui_voice_shaping)
    val voiceDownloadStates by VoiceModelManager.downloadState.collectAsState()
    val libraryAnchor = remember { BringIntoViewRequester() }
    var managerMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var languageMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var browsingLanguage by rememberSaveable { mutableStateOf<String?>(null) }
    var voiceSearchQuery by rememberSaveable { mutableStateOf("") }
    var expandedEngine by rememberSaveable { mutableStateOf<String?>(null) }
    var downloadsExpanded by rememberSaveable { mutableStateOf(false) }
    var phoneDirectoryExpanded by rememberSaveable { mutableStateOf(false) }
    var presetsOpen by rememberSaveable { mutableStateOf(false) }

    val vernVoices by produceState<List<TtsVoiceOption>>(emptyList(), voices) {
        value = withContext(Dispatchers.IO) { VoiceManager.loadVeritasVoices(downloadContext) }
    }
    val visibleVoices = remember(voices, vernVoices, settings.showNetworkVoices, currentLocale) {
        (voices + vernVoices)
            .distinctBy { it.enginePackage to it.name }
            .filter { settings.showNetworkVoices || !it.requiresNetwork }
            .sortedWith(
                compareBy<TtsVoiceOption>(
                    { if (it.localeTag.startsWith("en", ignoreCase = true)) 0 else 1 },
                    { when (it.localeTag.uppercase(Locale.ROOT)) { "EN-US" -> 0; "EN-GB" -> 1; else -> 2 } },
                    { it.localeTag.lowercase(Locale.ROOT) },
                    { VoiceManager.isVeritasEngine(it.enginePackage) },
                    { it.label.lowercase(currentLocale) }
                )
            )
    }
    val languageTags = remember(visibleVoices) { visibleVoices.map { it.localeTag }.distinct() }
    val selectedLanguageTag = listOfNotNull(browsingLanguage, settings.localeTag.takeIf { it.isNotBlank() }, currentLocale.toLanguageTag())
        .firstOrNull { it in languageTags } ?: languageTags.firstOrNull() ?: settings.localeTag
    val filteredLanguageVoices = remember(visibleVoices, selectedLanguageTag, voiceSearchQuery, currentLocale) {
        val query = voiceSearchQuery.trim()
        if (query.isBlank()) {
            visibleVoices.filter { selectedLanguageTag.isBlank() || it.localeTag == selectedLanguageTag }
        } else {
            visibleVoices.filter { voice ->
                voice.name.contains(query, ignoreCase = true) ||
                    voice.label.contains(query, ignoreCase = true) ||
                    voice.localeTag.contains(query, ignoreCase = true) ||
                    Locale.forLanguageTag(voice.localeTag).getDisplayName(currentLocale).contains(query, ignoreCase = true)
            }
        }
    }
    val selectedVoice = voices.firstOrNull {
        it.name == settings.voiceName &&
            it.enginePackage == (VoiceManager.engineForVoice(settings.voiceName) ?: settings.enginePackage)
    }
    val activeVoiceDisplayName = selectedVoice?.label?.takeIf { it.isNotBlank() } ?: settings.displayName

    fun showEngineVoices(enginePackage: String) {
        voiceSearchQuery = ""
        expandedEngine = enginePackage
        if (visibleVoices.none { it.enginePackage == enginePackage && it.localeTag == selectedLanguageTag }) {
            browsingLanguage = visibleVoices.firstOrNull { it.enginePackage == enginePackage }?.localeTag
        }
    }

    LaunchedEffect(settings.enginePackage) {
        onRefreshEngines()
        onLoadVoices()
    }

    FullScreenSettingsScaffold(title = menuString(R.string.settings_voice_and_language), onBack = onDismiss) {
        val cloudEditor = CloudVoiceSettingsPanel(
            onUsePhoneVoice = onUseSystemDefault,
            previewRate = SpeechDelivery.userRate(settings.preferredRate),
            previewPitch = SpeechDelivery.userPitch(settings.preferredPitch)
        ) { editingCloud ->
            if (!editingCloud) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = VeritasPackStyle.cardShape(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(menuString(R.string.ui_active_voice), style = MaterialTheme.typography.labelMedium)
                        Text(localizedSettingsOption(activeVoiceDisplayName), style = MaterialTheme.typography.titleLarge)
                        Text(
                            voiceLanguageLabel(selectedVoice?.localeTag ?: settings.localeTag, currentLocale),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = {
                                onPreviewActiveVoiceWithPreset(settings.preferredRate, settings.preferredPitch)
                            }) {
                                Icon(Icons.Outlined.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(menuString(R.string.ui_sample))
                            }
                            TextButton(onClick = { presetsOpen = true }, modifier = Modifier.testTag("voice-studio-customize")) {
                                Text(menuString(R.string.voice_studio_customize))
                            }
                        }
                    }
                }

                Box(Modifier.bringIntoViewRequester(libraryAnchor)) {
                    SettingsHubSectionTitle(menuString(R.string.ui_language_voice_library))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(
                            onClick = { languageMenuExpanded = true },
                            enabled = languageTags.isNotEmpty(),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("voice-library-language")
                        ) {
                            Text(
                                voiceLanguageLabel(selectedLanguageTag, currentLocale),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = languageMenuExpanded,
                            onDismissRequest = { languageMenuExpanded = false },
                            modifier = Modifier.widthIn(max = 320.dp).heightIn(max = 360.dp)
                        ) {
                            DropdownMenuItem(
                                text = { Text(menuString(R.string.voice_studio_use_language_default)) },
                                onClick = {
                                    languageMenuExpanded = false
                                    onLanguageSelected(selectedLanguageTag)
                                }
                            )
                            HorizontalDivider()
                            languageTags.forEach { localeTag ->
                                DropdownMenuItem(
                                    text = { Text(voiceLanguageLabel(localeTag, currentLocale)) },
                                    modifier = Modifier.testTag("voice-library-language-$localeTag"),
                                    trailingIcon = if (localeTag == selectedLanguageTag) {
                                        { Icon(Icons.Default.Check, contentDescription = menuString(R.string.ui_selected)) }
                                    } else null,
                                    onClick = {
                                        languageMenuExpanded = false
                                        voiceSearchQuery = ""
                                        browsingLanguage = localeTag
                                    }
                                )
                            }
                        }
                    }
                    VoiceStudioEnginePicker(
                        settings = settings,
                        engines = engines,
                        expanded = managerMenuExpanded,
                        onExpandedChange = { managerMenuExpanded = it },
                        onUseSystemDefault = {
                            showEngineVoices("")
                            onUseSystemDefault()
                        },
                        onSelect = { engine ->
                            showEngineVoices(engine.packageName)
                            if (VoiceManager.isVeritasEngine(engine.packageName) &&
                                vernVoices.none {
                                    it.enginePackage == engine.packageName &&
                                        VoiceModelManager.isVoiceInstalled(downloadContext, it.name)
                                }
                            ) {
                                downloadsExpanded = true
                            } else if (settings.enginePackage != engine.packageName) {
                                onEngineSelected(engine)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = settings.showNetworkVoices,
                        onCheckedChange = onShowNetworkVoicesChange
                    )
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_include_online_network_voices), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                OutlinedTextField(
                    value = voiceSearchQuery,
                    onValueChange = { voiceSearchQuery = it },
                    label = { Text(menuString(R.string.ui_search_voices_or_languages)) },
                    trailingIcon = if (voiceSearchQuery.isNotBlank()) {
                        { TextButton(onClick = { voiceSearchQuery = "" }) { Text(menuString(R.string.ui_clear)) } }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                VoiceStudioLibrary(
                    voices = filteredLanguageVoices,
                    engines = engines,
                    settings = settings,
                    loading = loadingVoices,
                    searching = voiceSearchQuery.isNotBlank(),
                    expandedEngine = expandedEngine,
                    downloadStates = voiceDownloadStates,
                    onToggleGroup = { engine -> expandedEngine = if (expandedEngine == engine) null else engine },
                    onSelect = onVoiceSelected,
                    onPreview = onPreviewVoice,
                    onDownload = { pendingDownload = it },
                    onDelete = { pendingDelete = it }
                )

                val showDownloads = downloadsExpanded || voiceDownloadStates.values.any {
                    it is DownloadState.Downloading || it is DownloadState.Failed
                }
                VoiceStudioDisclosure(
                    title = menuString(R.string.ui_vern_voice_models),
                    summary = menuString(R.string.voice_studio_models_hint),
                    expanded = showDownloads,
                    onClick = { downloadsExpanded = !downloadsExpanded }
                )
                if (showDownloads) {
                    VoiceStudioModelDownloads(
                        downloadStates = voiceDownloadStates,
                        onBrowseLite = {
                            showEngineVoices(VoiceManager.VERITAS_LITE)
                            downloadScope.launch { libraryAnchor.bringIntoView() }
                        },
                        onDownload = { pendingDownload = it },
                        onDelete = { pendingDelete = it }
                    )
                }
            }
        }
        if (!cloudEditor) {
            VoiceStudioDisclosure(
                title = menuString(R.string.cloud_more_phone_voices),
                summary = menuString(R.string.voice_studio_phone_more_hint),
                expanded = phoneDirectoryExpanded,
                onClick = { phoneDirectoryExpanded = !phoneDirectoryExpanded }
            )
            if (phoneDirectoryExpanded) {
                PhoneVoiceDirectory()
                TextButton(onClick = { onRefreshEngines(); onLoadVoices() }) {
                    Text(menuString(R.string.voice_studio_refresh_engines))
                }
                TextButton(onClick = onAddLanguageVoice) {
                    Text(menuString(R.string.ui_add_language_voice))
                }
                TextButton(onClick = onOpenSystemTtsSettings) {
                    Text(menuString(R.string.ui_system_tts_settings))
                }
            }

            HorizontalDivider()
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
        }
        VoiceStudioDisclosure(
            title = menuString(R.string.ui_voice_presets),
            summary = menuString(
                R.string.voice_studio_preset_values,
                localizedSettingsOption(settings.profileName),
                voiceMultiplier(settings.preferredRate, currentLocale),
                voiceMultiplier(settings.preferredPitch, currentLocale)
            ),
            onClick = { presetsOpen = true }
        )
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text(menuString(R.string.ui_done))
        }
        if (presetsOpen) {
            VoiceStudioPresetsDialog(
                settings = settings,
                onPreview = onPreviewActiveVoiceWithPreset.takeUnless { cloudEditor },
                onApply = { name, rate, pitch ->
                    onPresetSelected(name, rate, pitch)
                    presetsOpen = false
                },
                onDismiss = { presetsOpen = false }
            )
        }
    }
}

@Composable
private fun VoiceStudioEnginePicker(
    settings: VoiceSettings,
    engines: List<TtsEngineOption>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onUseSystemDefault: () -> Unit,
    onSelect: (TtsEngineOption) -> Unit,
    modifier: Modifier
) {
    val options = listOf(
        TtsEngineOption(VoiceManager.VERITAS_STUDIO, "Vern Studio"),
        TtsEngineOption(VoiceManager.VERITAS_LITE, "Vern Lite"),
        TtsEngineOption(VoiceManager.KITTEN, "Kitten")
    ) + engines.filterNot { VoiceManager.isVeritasEngine(it.packageName) }
    val current = options.firstOrNull { it.packageName == settings.enginePackage }?.label
        ?: settings.engineLabel.ifBlank { settings.enginePackage }
    val engineLabel = menuString(R.string.voice_studio_phone_engine)
    Box(modifier) {
        OutlinedButton(
            onClick = { onExpandedChange(true) },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = engineLabel },
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Text(localizedSettingsOption(current), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded, onDismissRequest = { onExpandedChange(false) }, modifier = Modifier.heightIn(max = 360.dp)) {
            DropdownMenuItem(
                text = { Text(menuString(R.string.ui_use_system_default)) },
                onClick = { onExpandedChange(false); onUseSystemDefault() }
            )
            options.forEach { engine ->
                DropdownMenuItem(
                    text = { Text(engine.label) },
                    trailingIcon = if (settings.enginePackage == engine.packageName) {
                        { Icon(Icons.Default.Check, contentDescription = menuString(R.string.ui_selected)) }
                    } else null,
                    onClick = {
                        onExpandedChange(false)
                        onSelect(engine)
                    }
                )
            }
        }
    }
}

@Composable
private fun VoiceStudioLibrary(
    voices: List<TtsVoiceOption>,
    engines: List<TtsEngineOption>,
    settings: VoiceSettings,
    loading: Boolean,
    searching: Boolean,
    expandedEngine: String?,
    downloadStates: Map<String, DownloadState>,
    onToggleGroup: (String) -> Unit,
    onSelect: (TtsVoiceOption) -> Unit,
    onPreview: (TtsVoiceOption) -> Unit,
    onDownload: (OfflineVoiceDescriptor) -> Unit,
    onDelete: (OfflineVoiceDescriptor) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]
    if (loading) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(menuString(R.string.ui_loading_voice_models), style = MaterialTheme.typography.bodyMedium)
        }
    } else if (voices.isEmpty()) {
        Text(menuString(R.string.ui_no_voices_found_matching_search_filter), style = MaterialTheme.typography.bodyMedium)
    }

    val activeEngine = VoiceManager.engineForVoice(settings.voiceName) ?: settings.enginePackage
    val models = remember { VoiceModelManager.availableVoices.associateBy { it.id } }
    voices.groupBy { it.enginePackage }.forEach { (engine, group) ->
        val expanded = searching || expandedEngine == engine
        val groupTitle = when (engine) {
            VoiceManager.VERITAS_STUDIO -> "Vern Studio"
            VoiceManager.VERITAS_LITE -> "Vern Lite"
            VoiceManager.KITTEN -> "Kitten"
            else -> engines.firstOrNull { it.packageName == engine }?.label ?: menuString(R.string.voice_studio_phone_engine)
        }
        val shared = group.firstNotNullOfOrNull { models[it.name] }?.takeIf { it.engineType != OfflineEngineType.PIPER }
        val expansion = menuString(if (expanded) R.string.voice_list_expanded else R.string.voice_list_collapsed)
        Row(
            Modifier.fillMaxWidth().testTag("voice-group-$engine")
                .semantics { stateDescription = expansion }
                .clickable(enabled = !searching, role = Role.Button) { onToggleGroup(engine) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(groupTitle, style = MaterialTheme.typography.titleSmall)
                Text(menuString(R.string.voice_list_count, group.size), style = MaterialTheme.typography.bodySmall)
                if (shared != null) {
                    Text(voicePackageSummary(shared, locale), Modifier.testTag("voice-package-summary-$engine"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Default.ArrowDropDown, null, Modifier.graphicsLayer { rotationZ = if (expanded) 180f else 0f })
        }
        group.forEach { voice ->
            val state = VoiceModelManager.stateFor(voice.name, downloadStates)
            if (expanded || state is DownloadState.Downloading || state is DownloadState.Failed) {
                val model = models[voice.name]
                val installed = model == null || VoiceModelManager.isVoiceInstalled(context, model.id)
                val canUpgrade = model != null && VoiceModelManager.needsQualityUpgrade(context, model.id)
                val isActive = activeEngine == engine && settings.voiceName == voice.name
                Surface(
                    modifier = Modifier.fillMaxWidth().selectable(selected = isActive, enabled = installed, role = Role.RadioButton) { onSelect(voice) },
                    shape = VeritasPackStyle.compactShape(),
                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (installed) RadioButton(selected = isActive, onClick = null)
                            Column(Modifier.weight(1f)) {
                                Text(model?.name ?: voice.label.ifBlank { voice.name }, style = MaterialTheme.typography.bodyLarge)
                                Text(voiceProviderLabel(voice, locale), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (installed) {
                                IconButton(onClick = { onPreview(voice) }) {
                                    Icon(Icons.Outlined.GraphicEq, contentDescription = menuString(R.string.ui_preview_voice))
                                }
                            }
                            if (model?.engineType == OfflineEngineType.PIPER && installed && state !is DownloadState.Downloading) {
                                IconButton(onClick = { onDelete(model) }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = menuString(R.string.ui_delete_voice_model))
                                }
                            }
                        }
                        if (model != null && state is DownloadState.Downloading) {
                            Text(downloadStateLabel(state), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { scope.launch { VoiceModelManager.cancelDownload(context, model.id) } }) {
                                Text(menuString(R.string.voice_cancel_download))
                            }
                        } else if (model != null && (!installed || canUpgrade)) {
                            TextButton(onClick = { onDownload(model) }) {
                                Text(when {
                                    model.engineType != OfflineEngineType.PIPER -> menuString(R.string.voice_download_package)
                                    canUpgrade -> menuString(R.string.settings_update_voice_size, model.downloadSizeMb.toInt())
                                    else -> menuString(R.string.settings_get_voice_size, model.downloadSizeMb.toInt())
                                })
                            }
                        }
                        if (state is DownloadState.Failed) {
                            Text(if (state.messageId != 0) menuString(state.messageId) else state.reason,
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceStudioModelDownloads(
    downloadStates: Map<String, DownloadState>,
    onBrowseLite: () -> Unit,
    onDownload: (OfflineVoiceDescriptor) -> Unit,
    onDelete: (OfflineVoiceDescriptor) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = LocalConfiguration.current.locales[0]
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = VeritasPackStyle.cardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(menuString(R.string.ui_download_voices_for_offline_reading_update_older_piper_voices_usi),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            VoiceModelManager.availablePackages.forEach { pack ->
                val models = VoiceModelManager.availableVoices.filter { it.engineType == pack.engineType }
                val target = models.firstOrNull { it.id == pack.targetVoiceId }
                val installed = VoiceModelManager.isVoiceInstalled(context, pack.targetVoiceId)
                val related = models.mapNotNull { VoiceModelManager.stateFor(it.id, downloadStates) }
                val state = related.firstOrNull { it is DownloadState.Downloading }
                    ?: related.firstOrNull { it is DownloadState.Failed }
                    ?: downloadStates[pack.targetVoiceId]
                HorizontalDivider()
                Text(pack.title, style = MaterialTheme.typography.titleSmall)
                Text(if (target != null && pack.engineType != OfflineEngineType.PIPER) voicePackageSummary(target, locale)
                    else menuString(R.string.voice_individual_downloads),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state is DownloadState.Downloading) {
                    LinearProgressIndicator(progress = { (state.percent / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(downloadStateLabel(state), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { scope.launch { models.forEach { VoiceModelManager.cancelDownload(context, it.id) } } }) {
                        Text(menuString(R.string.voice_cancel_download))
                    }
                } else {
                    if (state is DownloadState.Failed) {
                        Text(if (state.messageId != 0) menuString(state.messageId) else state.reason,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    when {
                        pack.engineType == OfflineEngineType.PIPER -> TextButton(onClick = onBrowseLite) {
                            Text(menuString(R.string.voice_choose_individual))
                        }
                        installed && target != null -> FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(menuString(R.string.ui_installed), Modifier.align(Alignment.CenterVertically),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            TextButton(onClick = { onDelete(target) }) { Text(menuString(R.string.ui_delete_voice_model)) }
                        }
                        target != null -> FilledTonalButton(onClick = { onDownload(target) }) {
                            Text(menuString(R.string.ui_download))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun voicePackageSummary(model: OfflineVoiceDescriptor, locale: Locale): String = menuString(
    R.string.voice_shared_package_size,
    NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(VoiceModelManager.downloadSizeMb(model)),
    VoiceModelManager.availableVoices.count { it.engineType == model.engineType }
)

@Composable
private fun VoiceStudioDisclosure(
    title: String,
    summary: String,
    onClick: () -> Unit,
    expanded: Boolean? = null,
    modifier: Modifier = Modifier
) {
    val expansion = expanded?.let { menuString(if (it) R.string.voice_list_expanded else R.string.voice_list_collapsed) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = VeritasPackStyle.compactShape(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .semantics { expansion?.let { stateDescription = it } }
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (expanded != null) {
                Icon(Icons.Default.ArrowDropDown, null, Modifier.graphicsLayer { rotationZ = if (expanded) 180f else 0f })
            } else {
                Icon(Icons.Outlined.GraphicEq, null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VoiceStudioPresetsDialog(
    settings: VoiceSettings,
    onPreview: ((Float, Float) -> Unit)?,
    onApply: (String, Float, Float) -> Unit,
    onDismiss: () -> Unit
) {
    val presets = remember { voicePresets() }
    var profile by rememberSaveable(settings.profileName) { mutableStateOf(settings.profileName) }
    var rate by rememberSaveable(settings.preferredRate) { mutableStateOf(SpeechDelivery.userRate(settings.preferredRate)) }
    var pitch by rememberSaveable(settings.preferredPitch) { mutableStateOf(SpeechDelivery.userPitch(settings.preferredPitch)) }
    val preset = presets.firstOrNull { it.name.equals(profile, ignoreCase = true) }
    val locale = LocalConfiguration.current.locales[0]

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(menuString(R.string.ui_voice_presets)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { choice ->
                        FilterChip(
                            selected = choice == preset,
                            onClick = {
                                profile = choice.name
                                rate = choice.rate
                                pitch = choice.pitch
                            },
                            label = { Text(localizedSettingsOption(choice.name)) }
                        )
                    }
                }
                preset?.let {
                    Text(menuString(it.summaryRes), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                VoiceStudioDeliverySlider(menuString(R.string.ui_speed), rate, .5f..2f, locale) { rate = it }
                VoiceStudioDeliverySlider(menuString(R.string.ui_pitch), pitch, .7f..1.4f, locale) { pitch = it }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onPreview != null) {
                        FilledTonalButton(onClick = { onPreview(rate, pitch) }, modifier = Modifier.testTag("voice-preset-preview")) {
                            Text(menuString(R.string.ui_sample))
                        }
                    }
                    TextButton(onClick = {
                        rate = preset?.rate ?: 1f
                        pitch = preset?.pitch ?: 1f
                    }) { Text(menuString(R.string.ui_reset)) }
                }
                if (onPreview == null) {
                    Text(menuString(R.string.voice_studio_cloud_preview_help), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(profile, rate, pitch) }) { Text(menuString(R.string.ui_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(menuString(R.string.ui_cancel)) } }
    )
}

@Composable
private fun VoiceStudioDeliverySlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    locale: Locale,
    onChange: (Float) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(menuString(R.string.voice_studio_multiplier, voiceMultiplier(value, locale)))
    }
    Slider(
        value = value,
        onValueChange = { onChange((it * 100f).roundToInt() / 100f) },
        valueRange = range,
        modifier = Modifier.semantics { contentDescription = label }
    )
}

private fun voiceMultiplier(value: Float, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }.format(value)

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
private fun downloadStateLabel(state: DownloadState.Downloading): String =
    com.veritas.reader.ui.screens.menuString(when {
        state.retrying -> com.veritas.reader.R.string.voice_download_retrying
        state.queued -> com.veritas.reader.R.string.settings_queued
        state.installing -> com.veritas.reader.R.string.settings_installing
        state.totalBytes <= 0 -> com.veritas.reader.R.string.settings_downloading
        else -> com.veritas.reader.R.string.settings_downloading_percent
    }, state.percent)
