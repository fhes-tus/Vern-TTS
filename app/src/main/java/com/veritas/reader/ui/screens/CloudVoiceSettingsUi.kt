package com.veritas.reader.ui.screens
import com.veritas.reader.VernAlertDialog as AlertDialog

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.veritas.reader.*
import com.veritas.reader.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

/** Source choice is explicit. Editing credentials never silently starts cloud reading. */
@Composable
internal fun CloudVoiceSettingsPanel(onUsePhoneVoice: () -> Unit = {}): Boolean {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf(CloudVoiceStore.load(context)) }
    var draft by remember { mutableStateOf(saved) }
    var cloudEditor by remember { mutableStateOf(saved.enabled) }
    var showNotice by remember { mutableStateOf(!CloudVoiceStore.noticeSeen(context)) }
    var providerMenu by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var previewActive by remember { mutableStateOf(false) }
    var status by remember { mutableIntStateOf(0) }
    var showAdvanced by remember { mutableStateOf(false) }
    var connectionHelp by remember { mutableStateOf(false) }
    var catalog by remember { mutableStateOf<List<CatalogVoice>?>(null) }
    var catalogTransport by remember { mutableStateOf<CloudSpeechTransport?>(null) }
    var timeoutText by remember(draft.provider) { mutableStateOf(draft.timeoutSeconds.toString()) }
    var retriesText by remember(draft.provider) { mutableStateOf(draft.retries.toString()) }
    var priceText by remember(draft.provider) { mutableStateOf(draft.pricePerMillion.toString()) }
    var cacheBytes by remember { mutableLongStateOf(CloudVoicePlayer.cacheBytes(context)) }
    var usage by remember { mutableStateOf(CloudVoiceStore.usage(context,draft.provider)) }
    val preview = remember { CloudVoicePlayer(context,scope) }
    val sample = menuString(R.string.cloud_preview_text)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    DisposableEffect(Unit) { onDispose { preview.stop(); catalogTransport?.cancel() } }

    fun draftConfig(enable: Boolean) = draft.copy(enabled = enable, endpoint = draft.endpoint.trim(),
        model = draft.model.trim(), voice = draft.voice.trim(), language = draft.language.trim(),
        region = draft.region.trim().lowercase(Locale.ROOT), timeoutSeconds = timeoutText.toIntOrNull() ?: -1,
        retries = retriesText.toIntOrNull() ?: -1, pricePerMillion = priceText.replace(',','.').toDoubleOrNull() ?: -1.0)

    suspend fun loadCatalog(config: CloudVoiceConfig, enteredKey: String, transport: CloudSpeechTransport): List<CatalogVoice> {
        CloudVoiceConnection.checkNetwork(context, config)
        return transport.listVoices(config, CloudVoiceConnection.credential(context, config, enteredKey))
    }

    catalog?.let { voices ->
        CloudVoicePicker(voices, locale, onDismiss = { catalog = null }) { voice ->
            draft = draft.copy(voice = voice.id, language = voice.language)
            catalog = null
            status = 0
        }
    }

    if (showNotice) AlertDialog(
        onDismissRequest = { showNotice = false; CloudVoiceStore.dismissNotice(context) },
        title = { Text(menuString(R.string.cloud_default_title)) },
        text = { Text(menuString(R.string.cloud_default_notice),Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = {
            showNotice = false; CloudVoiceStore.dismissNotice(context); preview.stop()
            saved = saved.copy(enabled = false); CloudVoiceStore.save(context,saved); cloudEditor = false
            onUsePhoneVoice()
        }) { Text(menuString(R.string.voice_use_phone)) } },
        dismissButton = { TextButton(onClick = { showNotice = false; CloudVoiceStore.dismissNotice(context) }) {
            Text(menuString(R.string.voice_explore))
        } }
    )
    if(connectionHelp) AlertDialog(onDismissRequest = { connectionHelp = false },
        title = { Text(menuString(R.string.cloud_connection)) },
        text = {
            val help = when (draft.provider) {
                CloudVoiceProvider.AZURE -> R.string.cloud_about_azure
                CloudVoiceProvider.GOOGLE_CLOUD -> R.string.cloud_about_google
                else -> R.string.voice_connection_help
            }
            Text(menuString(help),Modifier.verticalScroll(rememberScrollState()))
        },
        confirmButton = { TextButton(onClick = { connectionHelp = false }) { Text(menuString(R.string.ui_got_it)) } })

    SettingsHubSectionTitle(menuString(R.string.cloud_source))
    Row(Modifier.fillMaxWidth().clickable(enabled = !busy) {
        preview.stop(); busy = false; cloudEditor = false; status = 0
        saved = saved.copy(enabled = false); CloudVoiceStore.save(context,saved)
        refreshReadingVoice(context)
    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = !cloudEditor,onClick = null)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(menuString(R.string.cloud_phone))
            Text(menuString(R.string.cloud_phone_summary),style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { cloudEditor = true }.padding(vertical = 8.dp),verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = cloudEditor,onClick = null)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(menuString(R.string.cloud_personal))
            Text(menuString(if(saved.enabled) R.string.cloud_connected else R.string.cloud_not_connected),style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    TextButton(onClick = { showNotice = true }) { Text(menuString(R.string.cloud_voice_help)) }

    if (cloudEditor) {
        HorizontalDivider()
        SettingsHubSectionTitle(menuString(R.string.cloud_connection))
        Text(menuString(R.string.cloud_privacy),style = MaterialTheme.typography.bodyMedium)
        Text(menuString(R.string.ui_provider),style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { providerMenu = true },enabled = !busy,modifier = Modifier.fillMaxWidth()) {
                Text(if(draft.provider == CloudVoiceProvider.CUSTOM) menuString(R.string.cloud_compatible) else draft.provider.title)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ArrowDropDown,contentDescription = null)
            }
            DropdownMenu(expanded = providerMenu,onDismissRequest = { providerMenu = false }) {
                CloudVoiceProvider.entries.forEach { provider ->
                    DropdownMenuItem(text = { Text(if(provider == CloudVoiceProvider.CUSTOM) menuString(R.string.cloud_compatible) else provider.title) },onClick = {
                        preview.stop(); providerMenu = false; key = ""; status = 0
                        draft = CloudVoiceStore.profile(context,provider)
                        timeoutText = draft.timeoutSeconds.toString(); retriesText = draft.retries.toString(); priceText = draft.pricePerMillion.toString()
                        usage = CloudVoiceStore.usage(context,provider)
                    })
                }
            }
        }
        if (draft.provider == CloudVoiceProvider.CUSTOM) {
            CustomSpeechEndpointField(draft.endpoint, enabled = !busy) {
                draft = draft.copy(endpoint = it)
                status = 0
            }
        }
        Text(menuString(when(draft.provider) {
            CloudVoiceProvider.ELEVENLABS -> R.string.cloud_about_eleven
            CloudVoiceProvider.CARTESIA -> R.string.cloud_about_cartesia
            CloudVoiceProvider.MINIMAX -> R.string.cloud_about_minimax
            CloudVoiceProvider.KITTEN -> R.string.cloud_about_kitten
            CloudVoiceProvider.AZURE -> R.string.cloud_about_azure
            CloudVoiceProvider.GOOGLE_CLOUD -> R.string.cloud_about_google
            else -> R.string.cloud_about_custom
        }),style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { connectionHelp = true }) { Text(menuString(R.string.voice_connection_help_button)) }
        if(draft.provider != CloudVoiceProvider.CUSTOM) TextButton(onClick = { runCatching { uri.openUri(draft.provider.website) }.onFailure { status = R.string.cloud_error_connection } }) {
            Text(menuString(R.string.cloud_provider_site))
        }
        if (draft.provider == CloudVoiceProvider.AZURE) {
            CloudField(menuString(R.string.cloud_azure_region), draft.region, !busy) {
                draft = draft.copy(region = it); status = 0
            }
        }
        OutlinedTextField(value = key,onValueChange = { key = it },enabled = !busy,
            label = { Text(menuString(R.string.cloud_key)) },
            supportingText = { Text(menuString(if (draft.provider.hasVoiceCatalog) R.string.cloud_catalog_key_hint else R.string.cloud_key_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,autoCorrectEnabled = false),
            visualTransformation = PasswordVisualTransformation(),singleLine = true,modifier = Modifier.fillMaxWidth())
        if (draft.provider.hasVoiceCatalog) {
            Text(draft.voice, style = MaterialTheme.typography.bodyMedium)
            Text(Locale.forLanguageTag(draft.language).getDisplayName(locale), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                val config = draftConfig(false)
                if (runCatching { config.checked() }.isFailure) {
                    status = R.string.cloud_error_config
                    return@OutlinedButton
                }
                busy = true; status = 0
                val enteredKey = key
                val transport = CloudSpeechTransport()
                catalogTransport = transport
                scope.launch {
                    try {
                        catalog = withContext(Dispatchers.IO) { loadCatalog(config, enteredKey, transport) }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) {
                        status = (error as? CloudSpeechException)?.messageId ?: R.string.cloud_catalog_error
                    } finally {
                        catalogTransport = null; busy = false
                    }
                }
            }) { Text(menuString(R.string.cloud_choose_voice)) }
        } else {
            CloudField(menuString(R.string.cloud_model),draft.model,!busy) { draft = draft.copy(model = it) }
            if(draft.provider != CloudVoiceProvider.CUSTOM) TextButton(enabled = !busy,onClick = {
                draft = draft.copy(model = draft.provider.model); status = 0
            }) { Text(menuString(R.string.cloud_current_model,draft.provider.model)) }
            CloudField(menuString(R.string.cloud_voice_id),draft.voice,!busy) { draft = draft.copy(voice = it) }
        }
        if(draft.provider == CloudVoiceProvider.CARTESIA) CloudField(menuString(R.string.cloud_language),draft.language,!busy) { draft = draft.copy(language = it) }

        CloudToggle(menuString(R.string.cloud_wifi),draft.wifiOnly,!busy) { draft = draft.copy(wifiOnly = it) }
        CloudToggle(menuString(R.string.cloud_fallback),draft.fallbackToPhone,!busy) { draft = draft.copy(fallbackToPhone = it) }
        CloudToggle(menuString(R.string.cloud_cache),draft.cacheAudio,!busy) { draft = draft.copy(cacheAudio = it) }
        Text(menuString(R.string.cloud_cache_hint),style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { showAdvanced = !showAdvanced }) { Text(menuString(R.string.cloud_advanced)) }
        if(showAdvanced) {
            CloudField(menuString(R.string.cloud_timeout),timeoutText,!busy,KeyboardType.Number) { timeoutText = it }
            CloudField(menuString(R.string.cloud_retries),retriesText,!busy,KeyboardType.Number) { retriesText = it }
            Text(menuString(R.string.cloud_retry_hint),style = MaterialTheme.typography.bodySmall)
            CloudField(menuString(R.string.cloud_price),priceText,!busy,KeyboardType.Decimal) { priceText = it }
        }
        if(status != 0) Text(menuString(status),color = MaterialTheme.colorScheme.onSurfaceVariant)
        if(busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        fun persist(enable: Boolean, previewAfter: Boolean) {
            if(busy) return
            val config = draftConfig(enable)
            if(runCatching { config.checked() }.isFailure) { status = R.string.cloud_error_config; return }
            busy = true; status = 0
            val enteredKey = key
            val transport = if (!previewAfter && config.provider.hasVoiceCatalog) CloudSpeechTransport() else null
            catalogTransport = transport
            scope.launch {
                val prepared = try {
                    withContext(Dispatchers.IO) {
                        if (transport != null && loadCatalog(config, enteredKey, transport).none {
                                it.id == config.voice && it.language == config.language
                            }) throw CloudSpeechException(R.string.cloud_catalog_voice_missing)
                        currentCoroutineContext().ensureActive()
                        CloudVoiceConnection.prepare(context,config,enteredKey,activate = !previewAfter)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    busy = false
                    status = (error as? CloudSpeechException)?.messageId ?: R.string.cloud_error_config
                    return@launch
                } finally { catalogTransport = null }
                if(!previewAfter) { key = ""; saved = config }; draft = config
                if(previewAfter) {
                    if(PlaybackStateStore.isPlaying) sendPlaybackIntent(context,PlaybackActions.ACTION_PAUSE)
                    previewActive = true
                    val spokenSample = if (config.provider.hasVoiceCatalog) {
                        val sampleConfig = Configuration(context.resources.configuration).apply {
                            setLocale(Locale.forLanguageTag(config.language))
                        }
                        context.createConfigurationContext(sampleConfig).getString(R.string.cloud_preview_text)
                    } else sample
                    preview.play(config,spokenSample,1f,1f,onStarted = { status = R.string.cloud_preview_playing },onFinished = {
                    busy = false; previewActive = false; status = R.string.cloud_preview_done; usage = CloudVoiceStore.usage(context,config.provider); cacheBytes = CloudVoicePlayer.cacheBytes(context)
                },onError = { busy = false; previewActive = false; status = it; usage = CloudVoiceStore.usage(context,config.provider); cacheBytes = CloudVoicePlayer.cacheBytes(context) },
                    credentialOverride = prepared.credential)
                }
                else { busy = false; status = R.string.cloud_saved; refreshReadingVoice(context) }
            }
        }
        OutlinedButton(onClick = { persist(saved.enabled && draft.provider == saved.provider,true) },enabled = !busy,modifier = Modifier.fillMaxWidth()) {
            Text(menuString(R.string.cloud_test_preview))
        }
        Button(onClick = { persist(true,false) },enabled = !busy,modifier = Modifier.fillMaxWidth()) { Text(menuString(R.string.cloud_use)) }
        if(busy && previewActive) TextButton(onClick = { preview.stop(); busy = false; previewActive = false; status = R.string.cloud_stopped }) { Text(menuString(R.string.ui_stop)) }
        Text(menuString(R.string.cloud_buffering_hint),style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsHubSectionTitle(menuString(R.string.cloud_usage))
        Text(menuString(R.string.cloud_usage_counts,NumberFormat.getIntegerInstance(locale).format(usage.first),NumberFormat.getIntegerInstance(locale).format(usage.second)))
        if(draft.pricePerMillion > 0) Text(menuString(R.string.cloud_estimate,NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 4 }.format(usage.first / 1_000_000.0 * draft.pricePerMillion)))
        Text(menuString(R.string.cloud_usage_hint),style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { CloudVoicePlayer.clearCache(context); cacheBytes = 0 },enabled = !busy,modifier = Modifier.fillMaxWidth()) {
            Text(menuString(R.string.cloud_clear_cache,NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(cacheBytes / 1048576.0)))
        }
        TextButton(enabled = !busy,onClick = {
            preview.stop(); busy = true
            val removing = draft
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { ApiCredentialStore.save(context,"",removing.credentialName) } }
                if(result.isSuccess) {
                    if(saved.provider == removing.provider && saved.credentialName == removing.credentialName) {
                        saved = saved.copy(enabled = false); CloudVoiceStore.save(context,saved)
                        refreshReadingVoice(context)
                    }
                    key = ""; status = R.string.cloud_key_removed
                } else status = R.string.cloud_error_config
                busy = false
            }
        }) { Text(menuString(R.string.cloud_remove_key)) }
    }
    HorizontalDivider()
    return cloudEditor
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CustomSpeechEndpointField(value: String, enabled: Boolean, onChange: (String) -> Unit) {
    val requester = remember { BringIntoViewRequester() }
    var positioned by remember { mutableStateOf(false) }
    LaunchedEffect(positioned) {
        if (positioned) requester.bringIntoView()
    }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        label = { Text(menuString(R.string.cloud_endpoint)) },
        placeholder = { Text("https://example.com/v1/audio/speech") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(requester)
            .onGloballyPositioned { positioned = true }
    )
}

@Composable
private fun CloudVoicePicker(voices: List<CatalogVoice>, locale: Locale, onDismiss: () -> Unit, onSelect: (CatalogVoice) -> Unit) {
    var search by remember { mutableStateOf("") }
    val languages = remember(voices, locale) {
        voices.map { it.language }.distinct().associateWith { Locale.forLanguageTag(it).getDisplayName(locale) }
    }
    val matches = remember(voices, languages, search) {
        val query = search.trim()
        voices.filter { voice ->
            voice.id.contains(query, ignoreCase = true) || voice.label.contains(query, ignoreCase = true) ||
                languages.getValue(voice.language).contains(query, ignoreCase = true)
        }
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(menuString(R.string.cloud_choose_voice)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = search, onValueChange = { search = it }, singleLine = true,
                    label = { Text(menuString(R.string.cloud_catalog_search)) }, modifier = Modifier.fillMaxWidth())
                if (matches.isEmpty()) Text(menuString(R.string.cloud_catalog_empty))
                else LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(matches, key = { "${it.id}/${it.language}" }) { voice ->
                        Column(Modifier.fillMaxWidth().clickable { onSelect(voice) }.heightIn(min = 48.dp).padding(vertical = 8.dp)) {
                            Text(voice.label, style = MaterialTheme.typography.bodyMedium)
                            Text("${languages.getValue(voice.language)} · ${voice.language}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text(menuString(android.R.string.cancel)) } })
}

private fun refreshReadingVoice(context: android.content.Context) {
    val documentId = PlaybackStateStore.activeDocumentId ?: return
    if(!PlaybackStateStore.isPlaying) return
    val index = PlaybackStateStore.currentIndex
    val offset = PlaybackStateStore.speechPosition?.takeIf { it.documentId == documentId && it.sentenceIndex == index }?.sourceStart
    sendPlaybackIntent(context,PlaybackActions.ACTION_PLAY,documentId,index,
        rate = PlaybackStateStore.rate,pitch = PlaybackStateStore.pitch,charOffset = offset)
}

@Composable
private fun CloudField(title: String,value: String,enabled: Boolean,keyboardType: KeyboardType = KeyboardType.Text,onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title,style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(value = value,onValueChange = onChange,enabled = enabled,singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType,autoCorrectEnabled = false),modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CloudToggle(title: String,checked: Boolean,enabled: Boolean,onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp),verticalAlignment = Alignment.CenterVertically) {
        Text(title,modifier = Modifier.weight(1f))
        Switch(checked = checked,onCheckedChange = onChange,enabled = enabled)
    }
}

@Composable
internal fun PhoneVoiceDirectory() {
    val uri = LocalUriHandler.current
    var status by remember { mutableIntStateOf(0) }
        SettingsHubSectionTitle(menuString(R.string.cloud_more_phone_voices))
        Text(menuString(R.string.cloud_directory_hint),style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(
            R.string.cloud_google to "https://play.google.com/store/apps/details?id=com.google.android.tts",
            R.string.cloud_rhvoice to "https://play.google.com/store/apps/details?id=com.github.olga_yakovleva.rhvoice.android",
            R.string.cloud_espeak to "https://f-droid.org/packages/com.reecedunn.espeak/"
        ).forEach { (label,url) -> TextButton(onClick = { runCatching { uri.openUri(url) }.onFailure { status = R.string.cloud_error_connection } }) { Text(menuString(label)) } }
        if(status != 0) Text(menuString(status))
}
