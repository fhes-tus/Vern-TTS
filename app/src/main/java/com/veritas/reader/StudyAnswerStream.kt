package com.veritas.reader

import java.io.BufferedReader
import java.io.EOFException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONException
import org.json.JSONObject

private const val MAX_STUDY_STREAM_EVENT = 256_000

/** An HTTP success can still carry an error, a refusal or a truncated answer in its events. */
internal suspend fun readStudyStream(reader: BufferedReader, provider: AiProvider, onText: suspend (String) -> Unit) {
    val data = StringBuilder()
    suspend fun dispatch(): Boolean {
        if (data.isBlank()) return false
        val body = data.toString().trim()
        data.clear()
        if (body == "[DONE]") return true

        val event = try {
            JSONObject(body)
        } catch (_: JSONException) {
            error("The provider returned an unreadable answer.")
        }
        check(event.isNull("error") && event.optString("type") != "error") {
            "The provider interrupted the answer. Try again."
        }
        return when (provider) {
            AiProvider.GEMINI -> {
                val block = event.optJSONObject("promptFeedback")?.stringOrEmpty("blockReason").orEmpty()
                require(block.isEmpty() || block == "BLOCK_REASON_UNSPECIFIED") {
                    "The provider declined this request."
                }
                val candidate = event.optJSONArray("candidates")?.optJSONObject(0)
                candidate?.geminiAnswerParts()?.forEach { onText(it) }
                checkStreamStop(candidate?.stringOrEmpty("finishReason").orEmpty(), success = "STOP")
            }
            AiProvider.ANTHROPIC -> when (event.optString("type")) {
                "content_block_delta" -> {
                    val delta = event.optJSONObject("delta")
                    if (delta?.optString("type") == "text_delta") {
                        delta.stringOrEmpty("text").takeIf(String::isNotEmpty)?.let { onText(it) }
                    }
                    false
                }
                "message_delta" -> {
                    val stop = event.optJSONObject("delta")?.stringOrEmpty("stop_reason").orEmpty()
                    check(stop.isEmpty() || stop == "end_turn" || stop == "stop_sequence") {
                        "The provider stopped before completing the answer. Try a shorter passage."
                    }
                    false
                }
                else -> event.optString("type") == "message_stop"
            }
            else -> {
                val choice = event.optJSONArray("choices")?.optJSONObject(0)
                choice?.optJSONObject("delta")?.stringOrEmpty("content")?.takeIf(String::isNotEmpty)?.let { onText(it) }
                checkStreamStop(choice?.stringOrEmpty("finish_reason").orEmpty(), success = "stop")
            }
        }
    }

    while (true) {
        currentCoroutineContext().ensureActive()
        val line = reader.readLine()?.removePrefix("\uFEFF") ?: break
        currentCoroutineContext().ensureActive()
        if (line.isEmpty()) {
            if (dispatch()) return
        } else if (line.startsWith("data:")) {
            if (data.isNotEmpty()) data.append('\n')
            data.append(line.substring(5).removePrefix(" "))
            check(data.length <= MAX_STUDY_STREAM_EVENT) { "The provider returned an oversized response." }
        }
    }
    if (!dispatch()) throw EOFException("The provider disconnected before completing the answer. Try again.")
}

internal fun JSONObject.geminiAnswerParts(): List<String> = buildList {
    val parts = optJSONObject("content")?.optJSONArray("parts") ?: return@buildList
    for (index in 0 until parts.length()) {
        val part = parts.optJSONObject(index) ?: continue
        if (!part.optBoolean("thought")) part.stringOrEmpty("text").takeIf(String::isNotEmpty)?.let(::add)
    }
}

private fun JSONObject.stringOrEmpty(name: String): String = if (isNull(name)) "" else optString(name)

private fun checkStreamStop(reason: String, success: String): Boolean {
    if (reason.isEmpty()) return false
    check(reason == success) { "The provider stopped before completing the answer. Try a shorter passage." }
    return true
}
