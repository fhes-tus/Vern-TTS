package com.veritas.reader.tts

import java.text.BreakIterator
import java.util.Locale

/** Bound unusually long native VITS calls without dropping text or cutting a grapheme. */
internal object PiperSpeechChunks {
    const val LIMIT = 320
    fun split(text: String): List<String> {
        if(text.length <= LIMIT) return listOf(text)
        val boundaries = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
        val parts = mutableListOf<String>()
        var start = 0
        while(start < text.length) {
            var end = minOf(start+LIMIT,text.length)
            if(end < text.length) {
                val space = (end-1 downTo start+LIMIT/2).firstOrNull { text[it].isWhitespace() }
                if(space != null) end = space+1
                if(!boundaries.isBoundary(end)) end = boundaries.preceding(end)
                require(end > start) { "A text character exceeds the Piper input budget" }
            }
            parts += text.substring(start,end)
            start = end
        }
        return parts
    }
}
