package com.veritas.reader

/** A deterministic, source-text pass. Seeking does not depend on the last voice played. */
data class NarrationCue(val dialogue: Boolean, val speaker: String? = null, val labelEnd: Int = 0)

internal object LiteraryDialogue {
    private const val verbs = "said|asked|replied|answered|whispered|shouted|cried|murmured|continued|responded|exclaimed|yelled|muttered|insisted|warned|demanded|explained|added|remarked|grunted|sighed|urged|begged|groaned|screamed|barked|snapped|called|cries|says|asks|replies|answers"
    private const val name = "[\\p{Lu}][\\p{L}’'-]*(?:\\s+[\\p{Lu}][\\p{L}’'-]*){0,2}"
    private val attribution = Regex("^(?:$name)\\s+(?:$verbs)\\b|^(?:$verbs)\\s+(?:$name)\\b", RegexOption.IGNORE_CASE)
    private val before = Regex("($name)\\s+(?i:$verbs)(?:\\s+(?:to|at)\\s+$name)?\\s*[:,]?\\s*[\"“‘']")
    private val after = Regex("[\"”’']\\s*[,—–-]?\\s*(?i:$verbs)\\s+($name)")
    private val afterName = Regex("[\"”’']\\s*[,—–-]?\\s*($name)\\s+(?i:$verbs)\\b")
    private val colonLabel = Regex("^\\s*([\\p{Lu}][\\p{L}’'-]*(?:[ -][\\p{Lu}][\\p{L}’'-]*){0,2}):\\s*")
    private val shortLabel = Regex("^\\s*(Chris|Chr|Evan|Ev|Hope|Faith|Ignor|Inter|Talk|Pli|Obst|Byends|By-ends)\\.\\s+", RegexOption.IGNORE_CASE)
    private val aliases = mapOf("chris" to "Christian", "chr" to "Christian", "evan" to "Evangelist", "ev" to "Evangelist",
        "hope" to "Hopeful", "faith" to "Faithful", "ignor" to "Ignorance", "inter" to "Interpreter", "talk" to "Talkative",
        "pli" to "Pliable", "obst" to "Obstinate", "byends" to "By-ends", "by-ends" to "By-ends")
    val speakerAbbreviations: Set<String> = aliases.keys
    private val proseLabels = setOf("chapter", "part", "section", "note", "notes", "figure", "table", "warning", "summary", "answer", "question")
    fun startsWithAttribution(text: String): Boolean = attribution.containsMatchIn(text)
    fun speakerName(text: String): String? = label(text)?.let { canonical(it.groupValues[1]) }
        ?: sequenceOf(before, after, afterName).mapNotNull { it.find(text)?.groupValues?.getOrNull(1) }
            .firstOrNull()?.let(::canonical)?.takeUnless { it.lowercase() in setOf("he", "she", "they", "i", "it", "we", "you") }
    private fun canonical(raw: String): String {
        val cleaned = raw.trim().replace(Regex("^(?:Then|And|But|So|Now)\\s+", RegexOption.IGNORE_CASE), "")
        return aliases[cleaned.lowercase(java.util.Locale.ROOT)] ?: cleaned
    }
    fun hasSpeakerLabel(text: String): Boolean = label(text) != null
    private fun label(text: String): MatchResult? = shortLabel.find(text) ?: colonLabel.find(text)?.takeUnless {
        it.groupValues[1].lowercase(java.util.Locale.ROOT) in proseLabels
    }?.takeIf {
        text.drop(it.range.last + 1).trimStart().firstOrNull() in listOf('“', '"', '‘', '\'') ||
            canonical(it.groupValues[1]) in aliases.values
    }

    fun cues(sentences: List<ReaderSentence>): List<NarrationCue> {
        val quoteStack = mutableListOf<Char>()
        var speaker: String? = null
        var unquotedLabelTurn = false
        var previousPage: Int? = null
        return sentences.map { sentence ->
            val text = sentence.text
            val trimmed = text.trimStart()
            val label = label(text)
            val beginsQuote = trimmed.firstOrNull() in listOf('“', '"', '‘', '\'')
            val paragraph = sentence.separatorBefore.count { it == '\n' } >= 2
            // A new prose paragraph ends a turn; a new-page continuation can retain its open quote.
            if (paragraph && previousPage == sentence.pageNumber && !beginsQuote && label == null) {
                quoteStack.clear(); speaker = null; unquotedLabelTurn = false
            }
            if (trimmed.startsWith('#') || trimmed.startsWith("[[VERITAS_")) {
                quoteStack.clear(); speaker = null; unquotedLabelTurn = false
            }
            if (label != null) {
                quoteStack.clear()
                speaker = canonical(label.groupValues[1])
                unquotedLabelTurn = text.drop(label.range.last + 1).trimStart().firstOrNull() !in listOf('“', '"', '‘', '\'')
            }
            val startedInside = quoteStack.isNotEmpty()
            val explicitSpeaker = speakerName(text)
            if (explicitSpeaker != null) speaker = explicitSpeaker
            // A fresh quoted turn without an attribution must not inherit an unrelated speaker.
            if (beginsQuote && !startedInside && explicitSpeaker == null) speaker = null
            var speechLetters = 0
            var proseLetters = 0
            text.forEachIndexed { index, ch ->
                if (label != null && index <= label.range.last) return@forEachIndexed
                val apostrophe = ch in "'’" && text.getOrNull(index - 1)?.isLetterOrDigit() == true && text.getOrNull(index + 1)?.isLetterOrDigit() == true
                if (!apostrophe) {
                    when (ch) {
                        '“', '‘' -> {
                            val closing = if (ch == '“') '”' else '’'
                            // Repeated opening marks at the start of a paragraph continue the same speech.
                            if (!(index == text.indexOfFirst { !it.isWhitespace() } && quoteStack.lastOrNull() == closing)) quoteStack.add(closing)
                        }
                        '”', '’' -> if (quoteStack.lastOrNull() == ch) quoteStack.removeAt(quoteStack.lastIndex)
                        '"', '\'' -> {
                            if (quoteStack.lastOrNull() == ch) quoteStack.removeAt(quoteStack.lastIndex)
                            else if (ch == '"' || text.getOrNull(index - 1)?.isLetterOrDigit() != true) quoteStack.add(ch)
                        }
                    }
                }
                if (ch.isLetterOrDigit()) {
                    if (quoteStack.isNotEmpty() || unquotedLabelTurn) speechLetters++ else proseLetters++
                }
            }
            val dash = trimmed.startsWith("— ") || trimmed.startsWith("– ")
            val dialogue = label != null || dash || (speechLetters > 0 &&
                (startedInside || beginsQuote || explicitSpeaker != null || speechLetters > proseLetters))
            val cue = NarrationCue(dialogue, speaker.takeIf { dialogue }, label?.range?.last?.plus(1) ?: 0)
            if (quoteStack.isEmpty() && !unquotedLabelTurn) speaker = null
            previousPage = sentence.pageNumber
            cue
        }
    }
}
