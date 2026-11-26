package com.veritas.reader

import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.InputStream

internal class VernSettingsProfile private constructor(private val preferences: Map<String, Any>) {
    fun applyTo(current: ReaderSettings): ReaderSettings {
        val merged = current.toJson()
        preferences.forEach { (key, value) -> merged.put(key, value) }
        return ReaderSettings.fromJson(merged)
    }

    companion object {
        const val MIME_TYPE = "application/json"
        const val VERN_MIME_TYPE = "application/vnd.vern.settings+json"
        const val FILE_SUFFIX = ".vernsettings.json"
        private const val SCHEMA = "vern.reader.settings.v1"
        private const val MAX_BYTES = 32L * 1024

        private val appearance = setOf(
            "themeId", "themePackId", "customAccentColor", "adaptiveCover", "vibrantHero", "glassFloatingControls",
            "uiFontId", "paperToneMode", "amoledMode", "showNavLabels", "launcherPalette", "splashAnimation", "scatteredCards"
        )
        private val reading = setOf(
            "fontSizeSp", "listenFontSizeSp", "sectionSpacingDp", "showSectionNumbers", "collapsibleReaderBars",
            "pageTurnStyle", "readingLayout", "pdfNavigationMode"
        )
        private val fields = appearance + reading

        fun encode(settings: ReaderSettings): String {
            val source = settings.toJson()
            val shared = JSONObject()
            fields.forEach { key -> shared.put(key, source.get(key)) }
            return JSONObject().put("schema", SCHEMA).put("preferences", shared).toString(2)
        }

        fun read(input: InputStream): VernSettingsProfile {
            val bytes = ByteArrayOutputStream()
            BackupRestoreSafety.copyBounded(input, bytes, MAX_BYTES)
            val text = bytes.toByteArray().decodeToString(throwOnInvalidSequence = true).removePrefix("\uFEFF")
            requireShallowJson(text)
            val json = JSONTokener(text)
            val root = json.nextValue() as? JSONObject ?: error("Invalid Vern settings file.")
            require(json.nextClean() == '\u0000' && root.optString("schema") == SCHEMA)
            val shared = root.getJSONObject("preferences")
            val defaults = ReaderSettings().toJson()
            val validated = buildMap {
                fields.forEach { key ->
                    if (!shared.has(key)) return@forEach
                    val value = shared.get(key)
                    when (defaults.get(key)) {
                        is Boolean -> require(value is Boolean)
                        is String -> require(value is String && value.length <= 128 && value.none(Char::isISOControl))
                        is Number -> require(value is Int)
                    }
                    put(key, value)
                }
            }
            require(validated.isNotEmpty())
            return VernSettingsProfile(validated)
        }

        private fun requireShallowJson(text: String) {
            var depth = 0
            var inString = false
            var escaped = false
            for (char in text) {
                require(char != '\u0000')
                if (inString) {
                    if (escaped) escaped = false
                    else if (char == '\\') escaped = true
                    else if (char == '"') inString = false
                } else {
                    when (char) {
                        '"' -> inString = true
                        '{', '[' -> require(++depth <= 8)
                        '}', ']' -> require(--depth >= 0)
                        '/', '#', '\'' -> throw IllegalArgumentException("Invalid Vern settings JSON.")
                    }
                }
            }
            require(!inString && depth == 0)
        }
    }
}
