package com.veritas.reader

import android.app.LocaleManager
import android.content.res.Configuration
import android.icu.util.ULocale
import android.os.Bundle
import android.os.CancellationSignal
import android.view.translation.TranslationCapability
import android.view.translation.TranslationContext
import android.view.translation.TranslationManager
import android.view.translation.TranslationRequest
import android.view.translation.TranslationRequestValue
import android.view.translation.TranslationResponse
import android.view.translation.TranslationResponseValue
import android.view.translation.TranslationSpec
import android.view.translation.Translator
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Opt-in device diagnostic for the translation service behind Android's public text API. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class SystemMenuTranslationProbe {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val format = TranslationSpec.DATA_FORMAT_TEXT

    @Test
    fun translateVernLanguageSettingsLabels() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("probeSystemTranslation") == "true")
        val manager = context.getSystemService(TranslationManager::class.java)
        val localeManager = context.getSystemService(LocaleManager::class.java)
        val originalSystemLocales = localeManager.systemLocales
        val originalAppLocales = localeManager.applicationLocales
        val english = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(Locale.ENGLISH)
        })
        val labels = listOf(
            R.string.app_language,
            R.string.language_follow_device,
            R.string.language_device_behavior,
            R.string.language_apply
        ).map(english::getString)
        try {
            val capabilities = manager.getOnDeviceTranslationCapabilities(format, format)
            val englishPairs = capabilities.filter { it.sourceSpec.locale.language == "en" }
            val ready = englishPairs.filter { it.state == TranslationCapability.STATE_ON_DEVICE }
            val targets = ready.map { it.targetSpec.locale }.filter(::isUnshipped).distinct()
            report("Reported pairs: ${capabilities.size}; English source: ${englishPairs.size}; ready targets: " +
                ready.joinToString { it.targetSpec.locale.toLanguageTag() })
            // Some services omit pairs from discovery. Only probe unlisted pairs as a control
            // when discovery is empty. A reported pair awaiting download is left alone.
            val candidates = if (capabilities.isEmpty()) {
                val current = originalSystemLocales[0]?.let(ULocale::forLocale)
                (listOfNotNull(current) + listOf(ULocale.ITALIAN, ULocale.JAPANESE, ULocale.forLanguageTag("nl")))
                    .filter(::isUnshipped).distinct()
            } else targets
            val successful = candidates.take(3).any { target ->
                val pair = ready.firstOrNull { it.targetSpec.locale == target }
                translate(manager, pair?.sourceSpec ?: TranslationSpec(ULocale.ENGLISH, format),
                    pair?.targetSpec ?: TranslationSpec(target, format), labels)
            }
            report(if (successful) "OUTCOME: translated Vern labels" else "OUTCOME: no usable system translation")
        } finally {
            assertEquals(originalSystemLocales, localeManager.systemLocales)
            assertEquals(originalAppLocales, localeManager.applicationLocales)
        }
    }

    private fun isUnshipped(locale: ULocale): Boolean =
        locale.language.isNotBlank() && AppLanguageCatalog.canonicalTag(locale.toLanguageTag()) == null

    private fun translate(manager: TranslationManager, source: TranslationSpec, target: TranslationSpec,
        labels: List<String>): Boolean {
        val language = target.locale.toLanguageTag()
        val session = CompletableFuture<Translator?>()
        manager.createOnDeviceTranslator(TranslationContext.Builder(source, target).build(), context.mainExecutor) {
            if (!session.complete(it)) it?.destroy()
        }
        val translator = try {
            session.get(8, TimeUnit.SECONDS)
        } catch (_: TimeoutException) {
            if (!session.cancel(false)) session.getNow(null)?.destroy()
            report("$language: session creation timed out")
            return false
        }
        if (translator == null) {
            report("$language: system declined to create a translation session")
            return false
        }
        val cancellation = CancellationSignal()
        try {
            val request = TranslationRequest.Builder()
                .setTranslationRequestValues(labels.map { TranslationRequestValue.forText(it) }).build()
            val response = CompletableFuture<TranslationResponse>()
            translator.translate(request, cancellation, context.mainExecutor) {
                if (it.isFinalResponse) response.complete(it)
            }
            val result = response.get(8, TimeUnit.SECONDS)
            val translated = labels.indices.map { index ->
                result.translationResponseValues[index]?.takeIf {
                    it.statusCode == TranslationResponseValue.STATUS_SUCCESS
                }?.text?.toString()
            }
            val valid = result.translationStatus == TranslationResponse.TRANSLATION_STATUS_SUCCESS &&
                translated.all { !it.isNullOrBlank() } && translated != labels
            report("$language: status=${result.translationStatus}; translated labels=${translated.count { !it.isNullOrBlank() }}/${labels.size}")
            if (valid) labels.zip(translated).forEach { (input, output) -> report("  $input → $output") }
            return valid
        } catch (_: TimeoutException) {
            report("$language: translation timed out")
            return false
        } finally {
            cancellation.cancel()
            translator.destroy()
        }
    }

    private fun report(message: String) {
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "\n$message\n") })
    }
}
