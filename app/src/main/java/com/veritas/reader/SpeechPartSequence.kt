package com.veritas.reader

import java.util.UUID

/** Application text budgets are distinct from model phoneme/token limits. */
internal object SpeechInputBudget {
    // Reuse the existing export request budget; this is not a claim of 800 model tokens.
    const val OFFLINE_TEXT = 800
    fun limit(family: SpeechEngineFamily, androidLimit: Int): Int =
        if (family == SpeechEngineFamily.ANDROID) androidLimit.also { require(it >= 2) } else OFFLINE_TEXT
}

/** Owned by the service thread. Only the currently submitted part can advance this request. */
internal class SpeechPartSequence(
    private val parts: List<SpeechSegment>,
    val selection: Boolean,
    private val prefix: String,
    private val newId: () -> String = { UUID.randomUUID().toString() }
) {
    enum class Completion { IGNORED, NEXT, FINISHED }
    private var index = 0
    init { require(parts.isNotEmpty()) }
    var utteranceId: String? = prefix + newId()
        private set
    val requestId: String = requireNotNull(utteranceId)
    val current: SpeechSegment? get() = if (utteranceId != null) parts[index] else null

    fun accepts(id: String?): Boolean = id != null && id == utteranceId

    fun complete(id: String?): Completion {
        if (!accepts(id)) return Completion.IGNORED
        if (index == parts.lastIndex) {
            utteranceId = null
            return Completion.FINISHED
        }
        index++
        utteranceId = prefix + newId()
        return Completion.NEXT
    }

    fun cancel() { utteranceId = null }
}
