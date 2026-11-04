package com.veritas.reader

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CloudSpeechProtocolTest {
    @Test fun providerWireFormatsUseTheirOwnContracts() {
        val eleven = CloudSpeechProtocol.request(CloudVoiceConfig(),"Bonjour العربية नमस्ते 中文")
        assertEquals("https://api.elevenlabs.io/v1/text-to-dialogue/stream?output_format=mp3_44100_128",eleven.url)
        assertEquals("Bonjour العربية नमस्ते 中文",eleven.body.getJSONArray("inputs").getJSONObject(0).getString("text"))
        assertEquals(CloudVoiceProvider.ELEVENLABS.voice,eleven.body.getJSONArray("inputs").getJSONObject(0).getString("voice_id"))
        assertEquals("eleven_v4",eleven.body.getString("model_id"))
        val cartesia = CloudSpeechProtocol.request(CloudVoiceConfig(provider = CloudVoiceProvider.CARTESIA,language = "ar"),"مرحبا")
        assertEquals("2026-08-14",cartesia.headers["Cartesia-Version"])
        assertEquals("sonic-3.6",cartesia.body.getString("model_id"))
        assertEquals("ar",cartesia.body.getString("language"))
        assertEquals("pcm_s16le",cartesia.body.getJSONObject("output_format").getString("encoding"))
        assertTrue(cartesia.body.get("voice") is String)
        val mini = CloudSpeechProtocol.request(CloudVoiceConfig(provider = CloudVoiceProvider.MINIMAX),"Hello")
        assertFalse(mini.body.getBoolean("stream"))
        assertEquals("speech-2.8-hd",mini.body.getString("model"))
        assertEquals("hex",mini.body.getString("output_format"))
        assertEquals("mp3",mini.body.getJSONObject("audio_setting").getString("format"))
        val kitten = CloudSpeechProtocol.request(CloudVoiceConfig(provider = CloudVoiceProvider.KITTEN),"Hello")
        assertEquals("https://api.kittenml.com/v1/audio/speech",kitten.url)
        assertEquals("kitten-tts-2-latest",kitten.body.getString("model"))
        assertEquals("07 — Saoirse — Joyful Irish Female",kitten.body.getString("voice"))
        assertEquals("mp3",kitten.body.getString("response_format"))
    }

    @Test fun customEndpointIsExactAndRejectsCredentialBearingUrlsAndRedirectTargets() {
        val config = CloudVoiceConfig(provider = CloudVoiceProvider.CUSTOM,endpoint = "https://speech.example.test/v1/audio/speech",model = "my-model",voice = "my-voice")
        assertEquals(config.endpoint,CloudSpeechProtocol.request(config,"Hello").url)
        for(url in listOf("http://speech.example.test/v1/audio/speech","https://key@host.test/tts","https://host.test/tts?key=secret","https://host.test/tts#secret","file:///tmp/a","https:///tts",
            "https://host.test:0/tts","https://host.test:65536/tts","https://host.test:/tts","https://host.test","https://host.test/","https://host.test/tt\ns")) {
            assertThrows(IllegalArgumentException::class.java) { config.copy(endpoint = url).checked() }
        }
        assertThrows(IllegalArgumentException::class.java) { CloudVoiceConfig(endpoint = "https://other.test/speech").checked() }
    }

    @Test fun supportedOlderModelsKeepTheirCorrectRouteButRealtimeModelsCannotReachHttp() {
        for(model in listOf("eleven_v3","eleven_multilingual_v2","eleven_flash_v2_5")) {
            val request = CloudSpeechProtocol.request(CloudVoiceConfig(model = model),"Hello")
            assertEquals("https://api.elevenlabs.io/v1/text-to-speech/JBFqnCBsd6RMkjVDRZzb/stream?output_format=mp3_44100_128",request.url)
            assertEquals("Hello",request.body.getString("text"))
            assertEquals(model,request.body.getString("model_id"))
            assertFalse(request.body.has("inputs"))
        }
        for(model in listOf("eleven_v4_turbo","eleven_v3_conversational"))
            assertThrows(IllegalArgumentException::class.java) { CloudVoiceConfig(model = model).checked() }
        for(voice in listOf(".",".."))
            assertThrows(IllegalArgumentException::class.java) { CloudVoiceConfig(voice = voice).checked() }
    }

    @Test fun clonedKittenVoiceUsesItsObjectContractAndDiagnosticsHideConfigurationAndText() {
        val request = CloudSpeechProtocol.request(CloudVoiceConfig(provider = CloudVoiceProvider.KITTEN,voice = "voice_testclone"),"private book passage")
        assertEquals("voice_testclone",request.body.getJSONObject("voice").getString("id"))
        assertFalse(request.toString().contains("private book"))
        assertFalse(request.toString().contains("voice_testclone"))
        assertFalse(CloudVoiceConfig(provider = CloudVoiceProvider.CUSTOM,endpoint = "https://private.example/tts").toString().contains("private.example"))
        assertFalse(CloudVoiceConfig.fromJson(JSONObject("""{"provider":"UNKNOWN","enabled":true}""")).enabled)
    }

    @Test fun nonAudioAndTruncatedResponsesCannotEnterTheCache() {
        val mp3 = byteArrayOf(73,68,51,4,0,0,0,0,0,0)
        val wave = ByteArray(44).apply { "RIFF".toByteArray().copyInto(this); "WAVE".toByteArray().copyInto(this,8) }
        assertTrue(CloudSpeechProtocol.isAudio(CloudVoiceProvider.ELEVENLABS,mp3))
        assertTrue(CloudSpeechProtocol.isAudio(CloudVoiceProvider.CARTESIA,wave))
        assertTrue(CloudSpeechProtocol.isAudio(CloudVoiceProvider.CUSTOM,wave))
        assertFalse(CloudSpeechProtocol.isAudio(CloudVoiceProvider.CARTESIA,mp3))
        for(bytes in listOf(byteArrayOf(73,68,51),"<html>proxy error</html>".toByteArray(),"{\"error\":\"invalid key\"}".toByteArray(),byteArrayOf()))
            assertFalse(CloudSpeechProtocol.isAudio(CloudVoiceProvider.CUSTOM,bytes))
    }

    @Test fun keyScopesAndAudioCacheCannotCrossAccountsOrVoiceSettings() {
        val base = CloudVoiceConfig()
        val key = CloudSpeechProtocol.cacheKey(base,"original text","secret-A")
        for(next in listOf(base.copy(model = "other"),base.copy(voice = "other"),base.copy(language = "fr")))
            assertNotEquals(key,CloudSpeechProtocol.cacheKey(next,"original text","secret-A"))
        assertNotEquals(key,CloudSpeechProtocol.cacheKey(base,"changed text","secret-A"))
        assertNotEquals(key,CloudSpeechProtocol.cacheKey(base,"original text","secret-B"))
        assertEquals(key,CloudSpeechProtocol.cacheKey(base.copy(wifiOnly = false,cacheAudio = true),"original text","secret-A"))
        assertFalse(key.contains("secret"))
        assertFalse(base.toJson().toString().contains("credential"))
        assertNotEquals(CloudVoiceConfig(provider = CloudVoiceProvider.CUSTOM,endpoint = "https://a.test/tts").credentialName,
            CloudVoiceConfig(provider = CloudVoiceProvider.CUSTOM,endpoint = "https://b.test/tts").credentialName)
    }

    @Test fun minimaxAudioDecodingRejectsProviderFailureAndMalformedData() {
        assertArrayEquals(byteArrayOf(0x49,0x44,0x33),CloudSpeechProtocol.audio(CloudVoiceProvider.MINIMAX,
            """{"base_resp":{"status_code":0},"data":{"audio":"494433"}}""".toByteArray()))
        for(json in listOf("""{"base_resp":{"status_code":1008},"data":{"audio":"494433"}}""",
            """{"base_resp":{"status_code":0},"data":{"audio":"abc"}}""",
            """{"base_resp":{"status_code":0},"data":{"audio":"zz"}}""")) {
            assertThrows(Exception::class.java) { CloudSpeechProtocol.audio(CloudVoiceProvider.MINIMAX,json.toByteArray()) }
        }
    }

    @Test fun defaultIsPhoneAndSavedLimitsRemainBounded() {
        assertFalse(CloudVoiceConfig().enabled)
        assertTrue(CloudVoiceConfig().wifiOnly)
        assertFalse(CloudVoiceConfig().fallbackToPhone)
        assertFalse(CloudVoiceConfig().cacheAudio)
        assertEquals(0,CloudVoiceConfig().retries)
        val config = CloudVoiceConfig.fromJson(JSONObject("""{"provider":"MINIMAX","timeout":999999,"retries":99}"""))
        assertEquals(120,config.timeoutSeconds)
        assertEquals(2,config.retries)
        assertThrows(IllegalArgumentException::class.java) { CloudSpeechProtocol.request(CloudVoiceConfig(),"x".repeat(801)) }
        assertThrows(IllegalArgumentException::class.java) { CloudVoiceConfig(timeoutSeconds = 1).checked() }
    }
}
