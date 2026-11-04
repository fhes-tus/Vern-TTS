package com.veritas.reader

import org.json.JSONObject
import java.net.URLEncoder

/** Pure request construction: provider data never contains the credential. */
internal object CloudSpeechProtocol {
    data class Request(val url: String, val headers: Map<String,String>, val body: JSONObject)

    fun request(config: CloudVoiceConfig, text: String): Request {
        config.checked()
        require(text.isNotBlank() && text.length <= 800)
        val body = when (config.provider) {
            CloudVoiceProvider.ELEVENLABS -> JSONObject().put("text",text).put("model_id",config.model)
            CloudVoiceProvider.CARTESIA -> JSONObject().put("transcript",text).put("model_id",config.model)
                .put("voice",config.voice).put("output_format",JSONObject().put("container","wav").put("encoding","pcm_s16le").put("sample_rate",44100))
                .also { if (config.language.isNotBlank()) it.put("language",config.language) }
            CloudVoiceProvider.MINIMAX -> JSONObject().put("text",text).put("model",config.model).put("stream",false)
                .put("output_format","hex").put("language_boost","auto")
                .put("voice_setting",JSONObject().put("voice_id",config.voice).put("speed",1).put("vol",1).put("pitch",0))
                .put("audio_setting",JSONObject().put("format","mp3").put("sample_rate",32000).put("bitrate",128000).put("channel",1))
            else -> JSONObject().put("input",text).put("model",config.model).put("voice",config.voice)
                .put("response_format","mp3").put("speed",1)
        }
        val url = if (config.provider == CloudVoiceProvider.ELEVENLABS)
            config.endpoint + "/" + URLEncoder.encode(config.voice,"UTF-8") + "/stream?output_format=mp3_44100_128" else config.endpoint
        return Request(url, if(config.provider == CloudVoiceProvider.CARTESIA) mapOf("Cartesia-Version" to "2026-08-14") else emptyMap(),body)
    }

    fun audio(provider: CloudVoiceProvider, data: ByteArray): ByteArray {
        if (provider != CloudVoiceProvider.MINIMAX) return data
        val obj = JSONObject(String(data,Charsets.UTF_8))
        require(obj.optJSONObject("base_resp")?.optInt("status_code",-1) == 0) { "provider" }
        val hex = obj.getJSONObject("data").getString("audio")
        require(hex.isNotEmpty() && hex.length % 2 == 0 && hex.length <= 32 * 1024 * 1024)
        return ByteArray(hex.length/2) { i -> hex.substring(i*2,i*2+2).toInt(16).toByte() }
    }

    fun cacheKey(config: CloudVoiceConfig, text: String, credential: String): String =
        cloudDigest(listOf("v1",config.provider.name,config.endpoint,config.model,config.voice,config.language,
            cloudDigest(credential),text).joinToString("\u0000"))
}
