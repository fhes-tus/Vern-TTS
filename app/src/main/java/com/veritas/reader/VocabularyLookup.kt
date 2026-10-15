package com.veritas.reader

import android.content.Context
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

internal data class VocabularyDefinition(val definition: String, val pronunciation: String? = null, val sourceUrl: String? = null)

internal object VocabularyLookup {
    const val PENDING = "Looking up meaning… Waiting for a connection if offline."
    const val UNAVAILABLE = "Definition unavailable. Use Find meaning to try again."

    fun needsDefinition(explanation: String): Boolean = explanation.isBlank() ||
        listOf("Looked up", "Asked AI", "Definition unavailable", "Looking up meaning").any {
            explanation.contains(it, ignoreCase = true)
        }
    fun query(selection: String): String? {
        val word = selection.replace('’', '\'').replace('–', '-').replace('—', '-')
            .trim { !it.isLetterOrDigit() }.replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
        return word.takeIf { it.length in 1..80 && it.matches(Regex("[\\p{L}\\p{M}' -]+")) }
    }

    private fun JSONObject.text(key: String): String? = optString(key)
        .trim().takeIf { it.isNotBlank() && it != "null" }

    fun parse(json: String): VocabularyDefinition? {
        val entries = JSONArray(json)
        val definitions = linkedSetOf<String>()
        var pronunciation: String? = null
        var sourceUrl: String? = null
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            sourceUrl = sourceUrl ?: entry.optJSONArray("sourceUrls")?.optString(0)
                ?.takeIf { it.startsWith("https://en.wiktionary.org/") }
            pronunciation = pronunciation ?: entry.text("phonetic")
            val phonetics = entry.optJSONArray("phonetics")
            if (pronunciation == null && phonetics != null) {
                for (p in 0 until phonetics.length()) {
                    pronunciation = phonetics.optJSONObject(p)?.text("text") ?: continue
                    break
                }
            }
            val meanings = entry.optJSONArray("meanings") ?: continue
            for (m in 0 until meanings.length()) {
                val meaning = meanings.optJSONObject(m) ?: continue
                val items = meaning.optJSONArray("definitions") ?: continue
                for (d in 0 until items.length()) {
                    val text = items.optJSONObject(d)?.text("definition") ?: continue
                    val pos = meaning.text("partOfSpeech")
                    definitions.add(if (pos != null) "($pos) $text" else text)
                    break // One usable definition per sense, rather than only the first JSON slot.
                }
            }
        }
        return definitions.takeIf { it.isNotEmpty() }?.let {
            VocabularyDefinition(it.take(3).joinToString("; "), pronunciation, sourceUrl)
        }
    }

    fun parseWiktionary(json: String, word: String): VocabularyDefinition? {
        val entries = JSONObject(json).optJSONArray("en") ?: return null
        val definitions = linkedSetOf<String>()
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            val items = entry.optJSONArray("definitions") ?: continue
            for (d in 0 until items.length()) {
                val html = items.optJSONObject(d)?.text("definition") ?: continue
                val text = definitionText(html)
                if (text.isEmpty()) continue
                val pos = entry.text("partOfSpeech")?.lowercase(Locale.ROOT)
                definitions.add(if (pos == null) text else "($pos) $text")
                if (definitions.size == 3) break
            }
            if (definitions.size == 3) break
        }
        val path = URLEncoder.encode(word, "UTF-8").replace("+", "%20")
        return definitions.takeIf { it.isNotEmpty() }?.let {
            VocabularyDefinition(it.joinToString("; "), sourceUrl = "https://en.wiktionary.org/wiki/$path")
        }
    }

    private fun definitionText(html: String): String {
        val named = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"",
            "apos" to "'", "nbsp" to " ", "ndash" to "–", "mdash" to "—",
            "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "hellip" to "…")
        val plain = html.replace(Regex("(?is)<(?:script|style)\\b[^>]*>.*?</(?:script|style)>"), " ")
            .replace(Regex("(?i)<br\\s*/?>"), " ")
            .replace(Regex("<[^>]*>"), "")
        return Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);").replace(plain) { match ->
            val entity = match.groupValues[1]
            val code = when {
                entity.startsWith("#x") -> entity.drop(2).toIntOrNull(16)
                entity.startsWith("#") -> entity.drop(1).toIntOrNull()
                else -> null
            }
            if (code != null && Character.isValidCodePoint(code) && code !in 0xD800..0xDFFF) {
                String(Character.toChars(code))
            } else named[entity] ?: match.value
        }.replace(Regex("\\s+"), " ").trim()
    }

    fun fetch(selection: String): VocabularyDefinition? {
        val word = query(selection) ?: return null
        val path = URLEncoder.encode(word, "UTF-8").replace("+", "%20")
        fetchJson("https://api.dictionaryapi.dev/api/v2/entries/en/$path")?.let { body ->
            runCatching { parse(body) }.getOrNull()?.let { return it }
        }
        return fetchJson("https://en.wiktionary.org/api/rest_v1/page/definition/$path")?.let { body ->
            runCatching { parseWiktionary(body, word) }.getOrNull()
        }
    }

    private fun fetchJson(url: String): String? {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 6000
            connection.readTimeout = 6000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "Vern/2.6 (Android reader; dictionary lookup)")
            if (connection.responseCode != 200) return null
            val body = connection.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(4096)
                val result = StringBuilder()
                while (true) {
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (result.length + count > 512_000) return null
                    result.append(buffer, 0, count)
                }
                result.toString()
            }
            body
        } catch (e: CancellationException) {
            throw e
        } catch (failure: Exception) {
            android.util.Log.w("VocabularyLookup", "${connection.url.host}: ${failure.javaClass.simpleName}")
            null
        } finally {
            connection.disconnect()
        }
    }

    suspend fun resolve(context: Context, word: String, explanation: String, sentence: String?, title: String): VocabularyDefinition {
        val placeholder = needsDefinition(explanation)
        if (!placeholder) return VocabularyDefinition(explanation)
        // A web lookup must not depend on a configured or responsive AI provider.
        fetch(word)?.let { return it }
        val apiKey = GeminiStudyService.getApiKey(context)
        if (apiKey.isNotBlank() && !sentence.isNullOrBlank()) {
            val contextual = GeminiStudyService.defineInContext(apiKey, word, sentence, title).getOrNull()
            if (!contextual.isNullOrBlank()) return VocabularyDefinition(contextual)
        }
        return VocabularyDefinition(UNAVAILABLE)
    }
}
