package com.veritas.reader.tts

/** v1.0 added em_santa at index 53; English indices 0–27 are identical in both exports. */
internal object KokoroPackageShape {
    private const val BYTES_PER_SPEAKER = 510L * 256L * 4L
    private val englishSpeakers = listOf(
        "af_alloy", "af_aoede", "af_bella", "af_heart", "af_jessica", "af_kore", "af_nicole", "af_nova", "af_river", "af_sarah", "af_sky",
        "am_adam", "am_echo", "am_eric", "am_fenrir", "am_liam", "am_michael", "am_onyx", "am_puck", "am_santa",
        "bf_alice", "bf_emma", "bf_isabella", "bf_lily", "bm_daniel", "bm_fable", "bm_george", "bm_lewis"
    )

    fun speakerCount(voicesBytes: Long): Int? = when (voicesBytes) {
        53L * BYTES_PER_SPEAKER -> 53
        54L * BYTES_PER_SPEAKER -> 54
        else -> null
    }

    fun supports(count: Int): Boolean = count == 53 || count == 54

    fun speakerId(voiceId: String): Int = englishSpeakers.indexOf(voiceId.removePrefix("kokoro_").lowercase())
}
