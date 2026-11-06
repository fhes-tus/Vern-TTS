package com.veritas.reader.ui.screens

import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.util.LruCache
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.mlkit.nl.translate.Translator
import com.veritas.reader.AppLanguages
import com.veritas.reader.DeviceMenuTranslation
import com.veritas.reader.MenuTranslationTemplate
import com.veritas.reader.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

internal val LocalSettingsTranslation = staticCompositionLocalOf<SettingsTranslation?> { null }
private val LocalDeviceTranslationActive = staticCompositionLocalOf { false }

internal class SettingsTranslation(private val context: Context, val target: String) {
    private val preferences = context.getSharedPreferences("vern_menu_translation", Context.MODE_PRIVATE)
    private val english = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) })
    var enabled by mutableStateOf(preferences.getBoolean("enabled", false))
        private set
    var allowMobile by mutableStateOf(preferences.getBoolean("allow_mobile", false))
        private set
    var attempt by mutableStateOf(0)
        private set
    var status by mutableStateOf(Status.CHECKING)
        private set
    var percent by mutableStateOf<Int?>(null)
        private set
    private val results = mutableStateMapOf<String, String>()
    private val pending = Channel<String>(Channel.UNLIMITED)
    private val requested = mutableSetOf<String>()
    private val failed = mutableSetOf<String>()

    enum class Status { CHECKING, WAITING_NETWORK, WAITING_WIFI, WAITING_HEAT, QUEUED, DOWNLOADING, APPLYING, READY, FAILED }

    fun enable() {
        preferences.edit().putBoolean("enabled", true).apply()
        requested.removeAll(failed)
        failed.clear()
        enabled = true
        attempt++
    }

    fun disable() {
        preferences.edit().putBoolean("enabled", false).apply()
        enabled = false
    }

    fun useMobileData(allow: Boolean) {
        preferences.edit().putBoolean("allow_mobile", allow).apply()
        allowMobile = allow
    }

    fun lookup(source: String): String? = results[source] ?: cache.get(target to source)
    fun source(@StringRes id: Int): String = english.getString(id)
    val hasTranslations: Boolean get() = results.isNotEmpty()

    fun request(source: String) {
        val cached = lookup(source)
        if (cached != null) {
            results[source] = cached
            if (status == Status.APPLYING) status = Status.READY
        } else if (source.isNotBlank() && requested.add(source)) {
            pending.trySend(source)
        }
    }

    suspend fun run() {
        status = Status.CHECKING
        percent = null
        val client = try { DeviceMenuTranslation.client(target) } catch (_: Exception) {
            status = Status.FAILED
            return
        }
        try {
            val downloaded = withTimeoutOrNull(8_000) { DeviceMenuTranslation.isDownloaded(target) } == true
            if (!downloaded) {
                var blocked = downloadBlock()
                while (blocked != null) {
                    status = blocked
                    delay(2_000)
                    blocked = downloadBlock()
                }
                prepare(client)
            }
            status = if (results.isEmpty()) Status.APPLYING else Status.READY
            for (source in pending) {
                val result = try {
                    withTimeoutOrNull(12_000) { DeviceMenuTranslation.translate(client, source) }
                } catch (cancelled: CancellationException) {
                    pending.trySend(source)
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                if (result.isNullOrBlank()) {
                    failed.add(source)
                    if (status == Status.APPLYING) status = Status.FAILED
                    continue
                }
                cache.put(target to source, result)
                results[source] = result
                requested.remove(source)
                status = Status.READY
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            status = Status.FAILED
        } finally {
            client.close()
        }
    }

    private fun downloadBlock(): Status? {
        if (Build.VERSION.SDK_INT >= 29 && context.getSystemService(PowerManager::class.java)
                .currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) return Status.WAITING_HEAT
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        if (network?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) != true) return Status.WAITING_NETWORK
        if (!allowMobile && !network.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return Status.WAITING_WIFI
        return null
    }

    private suspend fun prepare(client: Translator) = coroutineScope {
        val download = async { DeviceMenuTranslation.prepare(client, allowMobile) }
        while (!download.isCompleted) {
            val blocked = downloadBlock()
            val transfer = withContext(Dispatchers.IO) { DeviceMenuTranslation.transfer(context, target) }
            status = blocked ?: if (transfer?.running == true) Status.DOWNLOADING else Status.QUEUED
            percent = transfer?.percent
            delay(1_000)
        }
        download.await()
        percent = null
    }

    companion object {
        private val cache = LruCache<Pair<String, String>, String>(768)
    }
}

@Composable
private fun rememberSettingsTranslation(selectedLanguage: String): SettingsTranslation? {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val target = remember(context, configuration) {
        AppLanguages.deviceLocales(context)[0]?.let { DeviceMenuTranslation.target(it, "") }
    } ?: return null
    val session = remember(target) { SettingsTranslation(context.applicationContext, target) }
    LaunchedEffect(session, selectedLanguage, session.enabled, session.attempt, session.allowMobile) {
        if (session.enabled && selectedLanguage.isEmpty()) session.run()
    }
    return session
}

@Composable
internal fun TranslatedAppMenus(content: @Composable () -> Unit) {
    val selected = rememberAppLanguageSelection()
    val session = rememberSettingsTranslation(selected)
    val active = selected.isEmpty() && session?.enabled == true
    CompositionLocalProvider(LocalSettingsTranslation provides session, LocalDeviceTranslationActive provides active) {
        val showBadge = active && session?.hasTranslations == true
        Column(Modifier.fillMaxSize().then(if (showBadge) Modifier.statusBarsPadding() else Modifier)) {
            if (showBadge) MenuTranslationBadge()
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

@Composable
internal fun menuString(@StringRes id: Int, vararg formatArgs: Any): String {
    val original = if (formatArgs.isEmpty()) stringResource(id) else stringResource(id, *formatArgs)
    if (!LocalDeviceTranslationActive.current || id == R.string.ui_ern || id == R.string.app_name || id ushr 24 == 1) return original
    val session = LocalSettingsTranslation.current?.takeIf { it.enabled } ?: return original
    val source = remember(session, id) { session.source(id) }
    val template = remember(source) { MenuTranslationTemplate.from(source) }
    val request = if (formatArgs.isEmpty()) source else template?.source ?: return original
    LaunchedEffect(session, request, session.attempt) { session.request(request) }
    val translated = session.lookup(request) ?: return original
    return if (formatArgs.isEmpty()) translated else
        template?.format(translated, LocalConfiguration.current.locales[0], formatArgs) ?: original
}

@Composable
internal fun SettingsTranslationStatus() {
    val session = LocalSettingsTranslation.current
    if (!LocalDeviceTranslationActive.current || session?.enabled != true) return
    SettingsTranslationStatus(session.target, session.status, session.percent, session.hasTranslations)
}

@Composable
internal fun SettingsTranslationStatus(target: String, status: SettingsTranslation.Status, percent: Int?, hasTranslations: Boolean) {
    val locale = LocalConfiguration.current.locales[0]
    val model = Locale.forLanguageTag(target).getDisplayName(locale)
    Column {
        Text(stringResource(R.string.menu_translation_model_name, model), style = MaterialTheme.typography.labelLarge)
        val message = when (status) {
            SettingsTranslation.Status.CHECKING, SettingsTranslation.Status.QUEUED -> R.string.menu_translation_preparing
            SettingsTranslation.Status.WAITING_NETWORK -> R.string.menu_translation_waiting_network
            SettingsTranslation.Status.WAITING_WIFI -> R.string.menu_translation_waiting_wifi
            SettingsTranslation.Status.WAITING_HEAT -> R.string.menu_translation_thermal
            SettingsTranslation.Status.DOWNLOADING -> R.string.menu_translation_downloading
            SettingsTranslation.Status.APPLYING -> R.string.menu_translation_applying
            SettingsTranslation.Status.READY -> R.string.menu_translation_ready
            SettingsTranslation.Status.FAILED -> R.string.menu_translation_failed
        }
        Text(if (hasTranslations) menuString(message) else stringResource(message), style = MaterialTheme.typography.bodySmall)
        if (status == SettingsTranslation.Status.DOWNLOADING) {
            if (percent == null) LinearProgressIndicator(Modifier.fillMaxWidth())
            else LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
        }
        if (hasTranslations) {
            MenuTranslationBadge()
        }
    }
}

@Composable
internal fun AppMenuTranslationBadge() {
    if (LocalDeviceTranslationActive.current && LocalSettingsTranslation.current?.hasTranslations == true) MenuTranslationBadge()
}

@Composable
private fun MenuTranslationBadge() {
    Image(painterResource(R.drawable.menu_translation_google), stringResource(R.string.menu_translation_attribution),
        modifier = Modifier.height(24.dp))
}

@Composable
internal fun SettingsTranslationControls(session: SettingsTranslation, activeSelection: Boolean, onEnable: () -> Unit) {
    val uri = LocalUriHandler.current
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.menu_translation_explanation), style = MaterialTheme.typography.bodySmall)
        if (!session.enabled || !activeSelection) {
            val model = Locale.forLanguageTag(session.target).getDisplayName(LocalConfiguration.current.locales[0])
            Text(stringResource(R.string.menu_translation_model_name, model), style = MaterialTheme.typography.labelLarge)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = session.allowMobile, onCheckedChange = session::useMobileData)
            Text(stringResource(R.string.menu_translation_mobile))
        }
        if (!session.enabled || !activeSelection || session.status == SettingsTranslation.Status.FAILED) {
            TextButton(onClick = onEnable) { Text(stringResource(R.string.menu_translation_enable)) }
        }
        if (session.enabled) {
            TextButton(onClick = session::disable) { Text(stringResource(R.string.menu_translation_disable)) }
        }
        TextButton(onClick = { uri.openUri("https://cloud.google.com/translate") }) {
            Text(stringResource(R.string.menu_translation_about))
        }
        Text(stringResource(R.string.menu_translation_disclaimer), modifier = Modifier.padding(bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall)
    }
}
