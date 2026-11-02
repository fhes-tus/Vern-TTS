package com.veritas.reader

/** Pure identity routing is shared by planning and UI; it must not initialize Android handlers. */
internal object SpeechEngineRouting {
    fun isVeritasEngine(enginePackage: String?): Boolean = enginePackage == VoiceManager.VERITAS_STUDIO ||
        enginePackage == VoiceManager.VERITAS_LITE || enginePackage == "com.veritas.voice"

    fun engineForVoice(voiceName: String?): String? = when {
        voiceName.isNullOrBlank() -> null
        voiceName.startsWith("piper_", ignoreCase = true) -> VoiceManager.VERITAS_LITE
        voiceName.startsWith("kokoro_", ignoreCase = true) -> VoiceManager.VERITAS_STUDIO
        else -> com.veritas.reader.tts.VoiceModelManager.availableVoices
            .firstOrNull { it.id.equals(voiceName, ignoreCase = true) }?.let {
                if (it.engineType == com.veritas.reader.tts.OfflineEngineType.PIPER) VoiceManager.VERITAS_LITE else VoiceManager.VERITAS_STUDIO
            }
    }
}
