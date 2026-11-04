package com.veritas.reader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.veritas.reader.*
import com.veritas.reader.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat

/** Source choice is explicit. Editing credentials never silently starts cloud reading. */
@Composable
internal fun CloudVoiceSettingsPanel(): Boolean {
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
    var cacheBytes by remember { mutableLongStateOf(CloudVoicePlayer.cacheBytes(context)) }
    var usage by remember { mutableStateOf(CloudVoiceStore.usage(context,draft.provider)) }
    val preview = remember { CloudVoicePlayer(context,scope) }
    val sample = stringResource(R.string.cloud_preview_text)
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    DisposableEffect(Unit) { onDispose { preview.stop() } }

    if (showNotice) AlertDialog(
        onDismissRequest = { showNotice = false; CloudVoiceStore.dismissNotice(context) },
        title = { Text(stringResource(R.string.cloud_default_title)) },
        text = { Text(stringResource(R.string.cloud_default_notice)) },
        confirmButton = { TextButton(onClick = { showNotice = false; CloudVoiceStore.dismissNotice(context) }) {
            Text(stringResource(R.string.ui_got_it))
        } }
    )

    SettingsHubSectionTitle(stringResource(R.string.cloud_source))
    Row(Modifier.fillMaxWidth().clickable(enabled = !busy) {
        preview.stop(); busy = false; cloudEditor = false; status = 0
        saved = saved.copy(enabled = false); CloudVoiceStore.save(context,saved)
        refreshReadingVoice(context)
    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = !cloudEditor,onClick = null)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(stringResource(R.string.cloud_phone))
            Text(stringResource(R.string.cloud_phone_summary),style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { cloudEditor = true }.padding(vertical = 8.dp),verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = cloudEditor,onClick = null)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(stringResource(R.string.cloud_personal))
            Text(stringResource(if(saved.enabled) R.string.cloud_connected else R.string.cloud_not_connected),style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    TextButton(onClick = { showNotice = true }) { Text(stringResource(R.string.cloud_voice_help)) }

    if (cloudEditor) {
        HorizontalDivider()
        SettingsHubSectionTitle(stringResource(R.string.cloud_connection))
        Text(stringResource(R.string.cloud_privacy),style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.ui_provider),style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { providerMenu = true },enabled = !busy,modifier = Modifier.fillMaxWidth()) {
                Text(if(draft.provider == CloudVoiceProvider.CUSTOM) stringResource(R.string.cloud_compatible) else draft.provider.title)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ArrowDropDown,contentDescription = null)
            }
            DropdownMenu(expanded = providerMenu,onDismissRequest = { providerMenu = false }) {
                CloudVoiceProvider.entries.forEach { provider ->
                    DropdownMenuItem(text = { Text(if(provider == CloudVoiceProvider.CUSTOM) stringResource(R.string.cloud_compatible) else provider.title) },onClick = {
                        preview.stop(); providerMenu = false; key = ""; status = 0
                        draft = CloudVoiceStore.profile(context,provider)
                        usage = CloudVoiceStore.usage(context,provider)
                    })
                }
            }
        }
        Text(stringResource(when(draft.provider) {
            CloudVoiceProvider.ELEVENLABS -> R.string.cloud_about_eleven
            CloudVoiceProvider.CARTESIA -> R.string.cloud_about_cartesia
            CloudVoiceProvider.MINIMAX -> R.string.cloud_about_minimax
            CloudVoiceProvider.KITTEN -> R.string.cloud_about_kitten
            else -> R.string.cloud_about_custom
        }),style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { runCatching { uri.openUri(draft.provider.website) }.onFailure { status = R.string.cloud_error_connection } }) {
            Text(stringResource(R.string.cloud_provider_site))
        }
        if(draft.provider == CloudVoiceProvider.CUSTOM) CloudField(stringResource(R.string.cloud_endpoint),draft.endpoint,!busy) { draft = draft.copy(endpoint = it) }
        OutlinedTextField(value = key,onValueChange = { key = it },enabled = !busy,
            label = { Text(stringResource(R.string.cloud_key)) },
            supportingText = { Text(stringResource(R.string.cloud_key_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password,autoCorrectEnabled = false),
            visualTransformation = PasswordVisualTransformation(),singleLine = true,modifier = Modifier.fillMaxWidth())
        CloudField(stringResource(R.string.cloud_model),draft.model,!busy) { draft = draft.copy(model = it) }
        CloudField(stringResource(R.string.cloud_voice_id),draft.voice,!busy) { draft = draft.copy(voice = it) }
        if(draft.provider == CloudVoiceProvider.CARTESIA) CloudField(stringResource(R.string.cloud_language),draft.language,!busy) { draft = draft.copy(language = it) }

        CloudToggle(stringResource(R.string.cloud_wifi),draft.wifiOnly,!busy) { draft = draft.copy(wifiOnly = it) }
        CloudToggle(stringResource(R.string.cloud_fallback),draft.fallbackToPhone,!busy) { draft = draft.copy(fallbackToPhone = it) }
        CloudToggle(stringResource(R.string.cloud_cache),draft.cacheAudio,!busy) { draft = draft.copy(cacheAudio = it) }
        Text(stringResource(R.string.cloud_cache_hint),style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { showAdvanced = !showAdvanced }) { Text(stringResource(R.string.cloud_advanced)) }
        if(showAdvanced) {
            CloudField(stringResource(R.string.cloud_timeout),draft.timeoutSeconds.toString(),!busy) { it.toIntOrNull()?.let { n -> draft = draft.copy(timeoutSeconds = n) } }
            CloudField(stringResource(R.string.cloud_retries),draft.retries.toString(),!busy) { it.toIntOrNull()?.let { n -> draft = draft.copy(retries = n) } }
            Text(stringResource(R.string.cloud_retry_hint),style = MaterialTheme.typography.bodySmall)
            CloudField(stringResource(R.string.cloud_price),draft.pricePerMillion.toString(),!busy) { it.toDoubleOrNull()?.let { n -> draft = draft.copy(pricePerMillion = n) } }
        }
        if(status != 0) Text(stringResource(status),color = MaterialTheme.colorScheme.onSurfaceVariant)
        if(busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        fun persist(enable: Boolean, previewAfter: Boolean) {
            if(busy) return
            val config = draft.copy(enabled = enable)
            if(runCatching { config.checked() }.isFailure) { status = R.string.cloud_error_config; return }
            busy = true; status = 0
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching {
                    if(key.isNotBlank()) ApiCredentialStore.save(context,key,config.credentialName)
                    if(config.provider != CloudVoiceProvider.CUSTOM && ApiCredentialStore.get(context,config.credentialName).isBlank()) throw CloudSpeechException(R.string.cloud_error_key)
                    if(previewAfter) CloudVoiceStore.saveProfile(context,config) else CloudVoiceStore.save(context,config)
                } }
                if(result.isFailure) { busy = false; status = (result.exceptionOrNull() as? CloudSpeechException)?.messageId ?: R.string.cloud_error_config; return@launch }
                key = ""; if(!previewAfter) saved = config; draft = config
                if(previewAfter) {
                    if(PlaybackStateStore.isPlaying) sendPlaybackIntent(context,PlaybackActions.ACTION_PAUSE)
                    previewActive = true
                    preview.play(config,sample,1f,1f,onStarted = { status = R.string.cloud_preview_playing },onFinished = {
                    busy = false; previewActive = false; status = R.string.cloud_preview_done; usage = CloudVoiceStore.usage(context,config.provider); cacheBytes = CloudVoicePlayer.cacheBytes(context)
                },onError = { busy = false; previewActive = false; status = it; usage = CloudVoiceStore.usage(context,config.provider) })
                }
                else { busy = false; status = R.string.cloud_saved; refreshReadingVoice(context) }
            }
        }
        OutlinedButton(onClick = { persist(saved.enabled && draft.provider == saved.provider,true) },enabled = !busy,modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cloud_test_preview))
        }
        Button(onClick = { persist(true,false) },enabled = !busy,modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cloud_use)) }
        if(busy && previewActive) TextButton(onClick = { preview.stop(); busy = false; previewActive = false; status = R.string.cloud_stopped }) { Text(stringResource(R.string.ui_stop)) }
        Text(stringResource(R.string.cloud_buffering_hint),style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsHubSectionTitle(stringResource(R.string.cloud_usage))
        Text(stringResource(R.string.cloud_usage_counts,NumberFormat.getIntegerInstance(locale).format(usage.first),NumberFormat.getIntegerInstance(locale).format(usage.second)))
        if(draft.pricePerMillion > 0) Text(stringResource(R.string.cloud_estimate,NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 4 }.format(usage.first / 1_000_000.0 * draft.pricePerMillion)))
        Text(stringResource(R.string.cloud_usage_hint),style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { CloudVoicePlayer.clearCache(context); cacheBytes = 0 },enabled = !busy,modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cloud_clear_cache,NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(cacheBytes / 1048576.0)))
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
        }) { Text(stringResource(R.string.cloud_remove_key)) }
    }
    HorizontalDivider()
    return cloudEditor
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
private fun CloudField(title: String,value: String,enabled: Boolean,onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp),verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title,style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(value = value,onValueChange = onChange,enabled = enabled,singleLine = true,modifier = Modifier.fillMaxWidth())
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
        SettingsHubSectionTitle(stringResource(R.string.cloud_more_phone_voices))
        Text(stringResource(R.string.cloud_directory_hint),style = MaterialTheme.typography.bodySmall,color = MaterialTheme.colorScheme.onSurfaceVariant)
        listOf(
            R.string.cloud_google to "https://play.google.com/store/apps/details?id=com.google.android.tts",
            R.string.cloud_rhvoice to "https://play.google.com/store/apps/details?id=com.github.olga_yakovleva.rhvoice.android",
            R.string.cloud_espeak to "https://f-droid.org/packages/com.reecedunn.espeak/"
        ).forEach { (label,url) -> TextButton(onClick = { runCatching { uri.openUri(url) }.onFailure { status = R.string.cloud_error_connection } }) { Text(stringResource(label)) } }
        if(status != 0) Text(stringResource(status))
}
