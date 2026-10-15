package com.veritas.reader.tts

/** v1.0 added em_santa at index 53; English indices 0–27 are identical in both exports. */
internal object KokoroPackageShape {
    private const val BYTES_PER_SPEAKER = 510L * 256L * 4L

    fun speakerCount(voicesBytes: Long): Int? = when (voicesBytes) {
        53L * BYTES_PER_SPEAKER -> 53
        54L * BYTES_PER_SPEAKER -> 54
        else -> null
    }

    fun supports(count: Int): Boolean = count == 53 || count == 54
}
