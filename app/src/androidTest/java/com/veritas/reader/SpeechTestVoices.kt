package com.veritas.reader

import android.content.Context
import kotlinx.coroutines.delay

/** A cold external engine can outlive the application's bounded voice-list query. Never skip a failed prerequisite. */
internal suspend fun offlineEnglishTestVoice(context: Context): TtsVoiceOption {
    check(context.packageName.endsWith(".checks"))
    var inventory = emptyList<TtsVoiceOption>()
    repeat(3) {
        inventory = VoiceManager.loadVoices(context, "")
        inventory.firstOrNull { !VoiceManager.isVeritasVoice(it.name) && !it.requiresNetwork && it.localeTag.startsWith("en") }
            ?.let { return it }
        delay(250)
    }
    error("No offline-capable English test voice after three queries; inventory=" +
        inventory.joinToString { "${it.name}:${it.localeTag}:network=${it.requiresNetwork}" })
}
