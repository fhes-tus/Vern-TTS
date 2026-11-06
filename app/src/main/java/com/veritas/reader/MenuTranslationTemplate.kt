package com.veritas.reader

import java.util.Locale

/** Sends only the built-in resource template to translation. Values are formatted after validation. */
internal class MenuTranslationTemplate private constructor(val source: String, private val formats: List<String>) {
    fun format(translated: String, locale: Locale, args: Array<out Any>): String? {
        val found = marker.findAll(translated).map { it.value }.toList()
        if (found.sorted() != formats.indices.map(::token).sorted()) return null
        val restored = marker.replace(translated.replace("%", "%%")) {
            formats[it.groupValues[1].toInt()]
        }
        return runCatching { String.format(locale, restored, *args) }.getOrNull()
    }

    companion object {
        private val placeholder = Regex("""%(?:[1-9][0-9]*\$[sd]|%)""")
        private val marker = Regex("""\[\[V[ ]?(\d+)\]\]""")
        private fun token(index: Int) = "[[V$index]]"

        fun from(source: String): MenuTranslationTemplate? {
            if (marker.containsMatchIn(source)) return null
            val bareText = placeholder.replace(source, "")
            if ('%' in bareText || bareText.none(Char::isLetter)) return null
            val formats = mutableListOf<String>()
            val protected = placeholder.replace(source) {
                token(formats.size).also { _ -> formats.add(it.value) }
            }
            return MenuTranslationTemplate(protected, formats)
        }
    }
}
