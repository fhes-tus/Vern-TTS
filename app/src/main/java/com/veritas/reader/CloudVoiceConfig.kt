package com.veritas.reader

import android.content.Context
import org.json.JSONObject
import java.net.URI
import java.security.MessageDigest

internal enum class CloudVoiceProvider(val title: String, val endpoint: String, val model: String, val voice: String, val website: String) {
    ELEVENLABS("ElevenLabs", "https://api.elevenlabs.io/v1/text-to-speech", "eleven_v4", "JBFqnCBsd6RMkjVDRZzb", "https://elevenlabs.io/docs/overview/models"),
    CARTESIA("Cartesia", "https://api.cartesia.ai/tts/bytes", "sonic-3.6", "db6b0ed5-d5d3-463d-ae85-518a07d3c2b4", "https://docs.cartesia.ai/api-reference/tts/bytes"),
    MINIMAX("MiniMax", "https://api.minimax.io/v1/t2a_v2", "speech-2.8-hd", "English_expressive_narrator", "https://platform.minimax.io/docs/api-reference/speech-t2a-http"),
    KITTEN("KittenML", "https://api.kittenml.com/v1/audio/speech", "kitten-tts-2-latest", "07 — Saoirse — Joyful Irish Female", "https://kittenml.com/"),
    AZURE("Microsoft Azure Speech", "", "neural", "en-US-JennyNeural", "https://learn.microsoft.com/azure/ai-services/speech-service/get-started-text-to-speech"),
    GOOGLE_CLOUD("Google Cloud Text-to-Speech", "https://texttospeech.googleapis.com/v1/text:synthesize", "v1", "en-US-Neural2-F", "https://cloud.google.com/text-to-speech/docs/get-started"),
    CUSTOM("OpenAI-compatible server", "", "", "", "https://github.com/debpalash/VoiceStudio");

    val hasVoiceCatalog: Boolean get() = this == AZURE || this == GOOGLE_CLOUD
}

internal data class CloudVoiceConfig(
    val enabled: Boolean = false,
    val provider: CloudVoiceProvider = CloudVoiceProvider.ELEVENLABS,
    val endpoint: String = provider.endpoint,
    val model: String = provider.model,
    val voice: String = provider.voice,
    val language: String = if (provider.hasVoiceCatalog) "en-US" else "",
    val wifiOnly: Boolean = true,
    val fallbackToPhone: Boolean = false,
    val cacheAudio: Boolean = false,
    val timeoutSeconds: Int = 30,
    val retries: Int = 0,
    val pricePerMillion: Double = 0.0,
    val region: String = ""
) {
    fun checked(): CloudVoiceConfig {
        if (provider == CloudVoiceProvider.AZURE) require(AZURE_REGION.matches(region)) { "region" }
        val speechEndpoint = speechEndpoint()
        val uri = runCatching { URI(speechEndpoint) }.getOrNull()
        require(uri != null && uri.scheme == "https" && !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.fragment == null && uri.rawQuery == null &&
            (uri.port == -1 || uri.port in 1..65535) && endpoint.length <= 2048 &&
            !uri.rawAuthority.endsWith(':') && uri.rawPath.isNotBlank() && uri.rawPath != "/" &&
            endpoint.none { it.isISOControl() }) { "endpoint" }
        require(provider == CloudVoiceProvider.CUSTOM || endpoint == provider.endpoint) { "endpoint" }
        require(model.isNotBlank() && model.length <= 200 && model.none { it.isISOControl() } &&
            voice.isNotBlank() && voice.length <= 200 && voice.none { it.isISOControl() }) { "voice" }
        require(provider != CloudVoiceProvider.ELEVENLABS || voice !in setOf(".","..")) { "voice" }
        // These require the provider's realtime WebSocket protocol, not either HTTP route.
        require(provider != CloudVoiceProvider.ELEVENLABS || model !in setOf("eleven_v4_turbo","eleven_v3_conversational")) { "model" }
        require(language.length <= 35 && language.all { it.isLetterOrDigit() || it == '-' }) { "language" }
        if (provider.hasVoiceCatalog) {
            require(model == provider.model && CATALOG_VOICE_ID.matches(voice)) { "voice" }
            require(CATALOG_LOCALE.matches(language)) { "language" }
        }
        require(timeoutSeconds in 5..120 && retries in 0..2 && pricePerMillion.isFinite() && pricePerMillion >= 0) { "limits" }
        return this
    }

    fun speechEndpoint(): String = if (provider == CloudVoiceProvider.AZURE)
        "https://$region.tts.speech.microsoft.com/cognitiveservices/v1" else endpoint

    val credentialName: String get() = "tts_" + provider.name + when (provider) {
        CloudVoiceProvider.CUSTOM -> "_" + cloudDigest(endpoint)
        CloudVoiceProvider.AZURE -> "_" + cloudDigest(region)
        else -> ""
    }

    override fun toString(): String = "CloudVoiceConfig(provider=$provider, enabled=$enabled, connection=redacted)"

    fun toJson(): JSONObject = JSONObject().put("enabled", enabled).put("provider", provider.name)
        .put("endpoint", endpoint).put("model", model).put("voice", voice).put("language", language)
        .put("wifiOnly", wifiOnly).put("fallback", fallbackToPhone).put("cache", cacheAudio)
        .put("timeout", timeoutSeconds).put("retries", retries).put("price", pricePerMillion).put("region", region)

    companion object {
        private val AZURE_REGION = Regex("[a-z][a-z0-9]{1,39}")
        internal val CATALOG_VOICE_ID = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,199}")
        internal val CATALOG_LOCALE = Regex("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8}){1,4}")

        fun fromJson(obj: JSONObject): CloudVoiceConfig {
            val rawProvider = obj.optString("provider")
            val provider = runCatching { CloudVoiceProvider.valueOf(rawProvider) }.getOrNull()
                ?: return CloudVoiceConfig()
            return CloudVoiceConfig(obj.optBoolean("enabled"), provider,
                obj.optString("endpoint", provider.endpoint), obj.optString("model", provider.model), obj.optString("voice", provider.voice),
                obj.optString("language", if (provider.hasVoiceCatalog) "en-US" else ""), obj.optBoolean("wifiOnly", true), obj.optBoolean("fallback"), obj.optBoolean("cache"),
                obj.optInt("timeout", 30).coerceIn(5,120), obj.optInt("retries",0).coerceIn(0,2), obj.optDouble("price",0.0).takeIf { it.isFinite() && it >= 0 } ?: 0.0,
                obj.optString("region"))
        }
    }
}

internal fun cloudDigest(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

internal object CloudVoiceStore {
    private fun prefs(context: Context) = context.getSharedPreferences("vern_cloud_voice", Context.MODE_PRIVATE)
    fun load(context: Context): CloudVoiceConfig = runCatching {
        CloudVoiceConfig.fromJson(JSONObject(prefs(context).getString("config", "{}").orEmpty())).also {
            if(it.enabled) it.checked()
        }
    }.getOrDefault(CloudVoiceConfig())
    fun save(context: Context, config: CloudVoiceConfig) {
        if (config.enabled) config.checked()
        check(prefs(context).edit().putString("config", config.toJson().toString())
            .putString("profile_${config.provider.name}",config.copy(enabled = false).toJson().toString()).commit())
    }
    fun saveProfile(context: Context, config: CloudVoiceConfig) {
        config.checked()
        check(prefs(context).edit().putString("profile_${config.provider.name}",config.copy(enabled = false).toJson().toString()).commit())
    }
    fun profile(context: Context, provider: CloudVoiceProvider): CloudVoiceConfig = runCatching {
        prefs(context).getString("profile_${provider.name}",null)?.let { CloudVoiceConfig.fromJson(JSONObject(it)) }
            ?.takeIf { it.provider == provider }
    }.getOrNull() ?: CloudVoiceConfig(provider = provider)
    fun noticeSeen(context: Context): Boolean = prefs(context).getBoolean("notice_seen_v2", false)
    fun dismissNotice(context: Context) { prefs(context).edit().putBoolean("notice_seen_v2", true).apply() }
    @Synchronized fun recordSubmission(context: Context, provider: CloudVoiceProvider, chars: Int) {
        val p = prefs(context)
        p.edit().putLong("chars_${provider.name}", p.getLong("chars_${provider.name}",0)+chars)
            .putLong("requests_${provider.name}",p.getLong("requests_${provider.name}",0)+1).apply()
    }
    fun usage(context: Context, provider: CloudVoiceProvider): Pair<Long,Long> = prefs(context).let {
        it.getLong("chars_${provider.name}",0) to it.getLong("requests_${provider.name}",0)
    }
}
