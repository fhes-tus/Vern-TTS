package com.veritas.reader

import org.json.JSONObject
import org.json.JSONArray
import java.net.URLEncoder
import java.util.Base64

/** Pure request construction: provider data never contains the credential. */
internal object CloudSpeechProtocol {
    data class Request(val url: String, val headers: Map<String,String> = emptyMap(),
        val body: String? = null, val contentType: String = "application/json") {
        override fun toString(): String = "CloudSpeechRequest(redacted)"
    }

    fun request(config: CloudVoiceConfig, text: String): Request {
        config.checked()
        require(text.isNotBlank() && text.length <= 800)
        if (config.provider == CloudVoiceProvider.AZURE) {
            val ssml = """<speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="${config.language}"><voice name="${config.voice}">${escapeSsml(text)}</voice></speak>"""
            return Request(config.speechEndpoint(), mapOf("X-Microsoft-OutputFormat" to "audio-24khz-96kbitrate-mono-mp3"),
                ssml, "application/ssml+xml")
        }
        val body = when (config.provider) {
            CloudVoiceProvider.ELEVENLABS -> (if(config.model == "eleven_v4")
                JSONObject().put("inputs",JSONArray().put(JSONObject().put("text",text).put("voice_id",config.voice)))
                else JSONObject().put("text",text)).put("model_id",config.model)
            CloudVoiceProvider.CARTESIA -> JSONObject().put("transcript",text).put("model_id",config.model)
                .put("voice",config.voice).put("output_format",JSONObject().put("container","wav").put("encoding","pcm_s16le").put("sample_rate",44100))
                .also { if (config.language.isNotBlank()) it.put("language",config.language) }
            CloudVoiceProvider.MINIMAX -> JSONObject().put("text",text).put("model",config.model).put("stream",false)
                .put("output_format","hex").put("language_boost","auto")
                .put("voice_setting",JSONObject().put("voice_id",config.voice).put("speed",1).put("vol",1).put("pitch",0))
                .put("audio_setting",JSONObject().put("format","mp3").put("sample_rate",32000).put("bitrate",128000).put("channel",1))
            CloudVoiceProvider.GOOGLE_CLOUD -> JSONObject().put("input", JSONObject().put("text", text))
                .put("voice", JSONObject().put("languageCode", config.language).put("name", config.voice))
                .put("audioConfig", JSONObject().put("audioEncoding", "MP3"))
            else -> JSONObject().put("input",text).put("model",config.model).put("voice",
                if(config.provider == CloudVoiceProvider.KITTEN && config.voice.startsWith("voice_")) JSONObject().put("id",config.voice) else config.voice)
                .put("response_format","mp3").put("speed",1)
        }
        val url = if (config.provider == CloudVoiceProvider.ELEVENLABS) {
            if(config.model == "eleven_v4") "https://api.elevenlabs.io/v1/text-to-dialogue/stream?output_format=mp3_44100_128"
            else config.endpoint + "/" + URLEncoder.encode(config.voice,"UTF-8") + "/stream?output_format=mp3_44100_128"
        } else config.endpoint
        return Request(url, if(config.provider == CloudVoiceProvider.CARTESIA) mapOf("Cartesia-Version" to "2026-08-14") else emptyMap(),body.toString())
    }

    private fun escapeSsml(text: String): String = buildString {
        text.codePoints().forEach { point ->
            when (point) {
                38 -> append("&amp;")
                60 -> append("&lt;")
                62 -> append("&gt;")
                9, 10, 13, in 0x20..0xD7FF, in 0xE000..0xFFFD, in 0x10000..0x10FFFF -> appendCodePoint(point)
            }
        }
    }

    fun audio(provider: CloudVoiceProvider, data: ByteArray): ByteArray {
        if (provider == CloudVoiceProvider.GOOGLE_CLOUD) {
            val encoded = JSONObject(String(data, Charsets.UTF_8)).getString("audioContent")
            require(encoded.isNotEmpty() && encoded.length <= (CloudSpeechTransport.MAX_AUDIO_BYTES + 2) / 3 * 4)
            return Base64.getDecoder().decode(encoded)
        }
        if (provider != CloudVoiceProvider.MINIMAX) return data
        val obj = JSONObject(String(data,Charsets.UTF_8))
        val code = obj.optJSONObject("base_resp")?.optInt("status_code",-1) ?: -1
        if(code != 0) throw CloudSpeechException(when(code) {
            1004,2049 -> R.string.cloud_error_key
            1002,1008,1039,1045,2053,2056,2062,2067 -> R.string.cloud_error_quota
            else -> R.string.cloud_error_provider
        })
        val hex = obj.getJSONObject("data").getString("audio")
        require(hex.isNotEmpty() && hex.length % 2 == 0 && hex.length <= 32 * 1024 * 1024)
        return ByteArray(hex.length/2) { i -> hex.substring(i*2,i*2+2).toInt(16).toByte() }
    }

    /** Container sniffing rejects HTML/JSON and truncated headers before a decoder or cache sees them. */
    fun isAudio(provider: CloudVoiceProvider, bytes: ByteArray): Boolean {
        val wave = bytes.size >= 44 && String(bytes,0,4,Charsets.US_ASCII) in setOf("RIFF","RF64") &&
            String(bytes,8,4,Charsets.US_ASCII) == "WAVE"
        val mp3 = bytes.size >= 10 && (String(bytes,0,3,Charsets.US_ASCII) == "ID3" ||
            ((bytes[0].toInt() and 255) == 255 && (bytes[1].toInt() and 224) == 224 &&
                (bytes[1].toInt() and 6) != 0))
        return when(provider) {
            CloudVoiceProvider.CARTESIA -> wave
            CloudVoiceProvider.CUSTOM -> wave || mp3
            else -> mp3
        }
    }

    fun cacheKey(config: CloudVoiceConfig, text: String, credential: String): String =
        cloudDigest(listOf("v1",config.provider.name,config.speechEndpoint(),config.model,config.voice,config.language,
            cloudDigest(credential),text).joinToString("\u0000"))
}
