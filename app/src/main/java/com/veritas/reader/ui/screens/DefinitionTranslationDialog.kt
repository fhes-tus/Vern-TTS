package com.veritas.reader.ui.screens

import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.veritas.reader.R

private const val GOOGLE_TRANSLATE_LIMIT = 5_000
private const val TRANSLATE_HOST = "translate.google.com"

internal fun definitionTranslationSections(content: String): List<String> = buildList {
    var start = 0
    while (start < content.length) {
        var end = (start + GOOGLE_TRANSLATE_LIMIT).coerceAtMost(content.length)
        if (end < content.length) {
            val boundary = content.lastIndexOfAny(charArrayOf('\n', ' '), end - 1)
            if (boundary > start + GOOGLE_TRANSLATE_LIMIT / 2) end = boundary + 1
            if (Character.isHighSurrogate(content[end - 1])) end--
        }
        add(content.substring(start, end))
        start = end
    }
}

@Composable
internal fun DefinitionTranslationDialog(content: String, onClose: () -> Unit) {
    val sections = remember(content) { definitionTranslationSections(content) }
    if (sections.isEmpty()) return
    var page by rememberSaveable(content) { mutableIntStateOf(0) }
    var retry by remember { mutableIntStateOf(0) }
    val locale = LocalConfiguration.current.locales[0]
    val appLanguage = locale.toLanguageTag().takeIf { locale.language.isNotBlank() && locale.language != "en" } ?: "es"
    var target by rememberSaveable(content) { mutableStateOf(appLanguage) }
    var languageDraft by rememberSaveable(content) { mutableStateOf(target) }
    val languageCode = languageDraft.trim().takeIf { it.matches(Regex("[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8}){0,3}")) }
    var pageTarget by rememberSaveable(content, page) { mutableStateOf(target) }
    val uri = remember(sections, page, pageTarget) {
        Uri.Builder().scheme("https").authority(TRANSLATE_HOST).path("/")
            .appendQueryParameter("sl", "auto")
            .appendQueryParameter("tl", pageTarget)
            .appendQueryParameter("text", sections[page])
            .appendQueryParameter("op", "translate")
            .build()
    }
    FullScreenSettingsScaffold(
        title = menuString(R.string.definition_google_translate),
        onBack = onClose,
        scrollable = false
    ) {
        Text(menuString(R.string.definition_translation_language_hint), style = MaterialTheme.typography.bodySmall)
        if (sections.size > 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(languageDraft, { languageDraft = it.take(35) }, singleLine = true,
                    label = { Text(menuString(R.string.definition_target_language)) },
                    supportingText = { Text(menuString(R.string.definition_language_examples)) },
                    modifier = Modifier.weight(1f))
                TextButton(enabled = languageCode != null, onClick = {
                    val code = requireNotNull(languageCode)
                    target = code
                    pageTarget = code
                }) { Text(menuString(R.string.ui_apply)) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(enabled = page > 0, onClick = { page-- }) { Text(menuString(R.string.ui_previous_page)) }
                Text(menuString(R.string.l10n_dynamic_057, page + 1, sections.size), modifier = Modifier.padding(top = 12.dp))
                TextButton(enabled = page < sections.lastIndex, onClick = { page++ }) { Text(menuString(R.string.ui_next_page)) }
            }
        }
        key(uri, retry) {
            var loading by remember { mutableStateOf(true) }
            var failed by remember { mutableStateOf(false) }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) {
                Text(menuString(R.string.definition_translation_unavailable), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { retry++ }) { Text(menuString(R.string.ui_retry)) }
            }
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowContentAccess = false
                        settings.allowFileAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                request.url.scheme != "https" || request.url.host != TRANSLATE_HOST

                            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                                val address = Uri.parse(url)
                                if (address.host != TRANSLATE_HOST) return
                                val language = address.getQueryParameter("tl").orEmpty()
                                if (language.isNotBlank() && language != target) {
                                    target = language
                                    languageDraft = language
                                }
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                loading = false
                            }

                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) { failed = true; loading = false }
                            }
                        }
                        loadUrl(uri.toString())
                    }
                },
                onRelease = { view -> view.stopLoading(); view.destroy() }
            )
        }
    }
}
