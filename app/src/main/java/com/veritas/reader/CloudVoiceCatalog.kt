package com.veritas.reader

import org.json.JSONArray
import org.json.JSONObject

internal data class CatalogVoice(val id: String, val language: String, val label: String)

internal object CloudVoiceCatalog {
    fun request(config: CloudVoiceConfig): CloudSpeechProtocol.Request {
        config.checked()
        val url = when (config.provider) {
            CloudVoiceProvider.AZURE -> config.speechEndpoint().removeSuffix("/v1") + "/voices/list"
            CloudVoiceProvider.GOOGLE_CLOUD -> "https://texttospeech.googleapis.com/v1/voices"
            else -> error("catalog")
        }
        return CloudSpeechProtocol.Request(url)
    }

    fun parse(provider: CloudVoiceProvider, data: ByteArray): List<CatalogVoice> {
        val json = String(data, Charsets.UTF_8)
        val azure = provider == CloudVoiceProvider.AZURE
        val voices = if (azure) JSONArray(json) else JSONObject(json).getJSONArray("voices")
        return buildList {
            for (index in 0 until voices.length()) {
                val voice = voices.optJSONObject(index) ?: continue
                val id = voice.optString(if (azure) "ShortName" else "name")
                if (!CloudVoiceConfig.CATALOG_VOICE_ID.matches(id)) continue
                val label = if (azure) voice.optString("DisplayName", id) else id
                val locales = if (azure) JSONArray().put(voice.optString("Locale"))
                    else voice.optJSONArray("languageCodes") ?: continue
                for (localeIndex in 0 until locales.length()) {
                    val language = locales.optString(localeIndex)
                    if (CloudVoiceConfig.CATALOG_LOCALE.matches(language)) add(CatalogVoice(id, language, label))
                }
            }
        }.distinctBy { it.id to it.language }.sortedWith(compareBy(CatalogVoice::language, CatalogVoice::id))
    }
}
