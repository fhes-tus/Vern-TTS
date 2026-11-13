package com.veritas.reader

import java.text.BreakIterator
import java.text.Normalizer
import java.util.Locale

/** Spoken replacements retain their original character positions for range callbacks and resume. */
class SpokenText private constructor(
    val text: String,
    private val positions: IntArray,
    private val ends: IntArray,
    private val sourceLength: Int
) {
    fun sourceOffset(spokenOffset: Int): Int =
        if (positions.isEmpty() || spokenOffset >= positions.size) sourceLength else positions[spokenOffset.coerceAtLeast(0)]

    /** Exclusive end of a spoken range, including the whole source span of an expansion. */
    fun sourceEndOffset(spokenEnd: Int): Int =
        if (spokenEnd <= 0 || ends.isEmpty()) sourceOffset(0) else ends[spokenEnd.coerceAtMost(ends.size) - 1]

    /** Slice spoken text while retaining offsets into the original source. */
    fun slice(start: Int, end: Int): SpokenText =
        SpokenText(text.substring(start, end), positions.copyOfRange(start, end), ends.copyOfRange(start, end),
            maxOf(sourceOffset(end), sourceEndOffset(end)))

    private data class Edit(val start: Int, val end: Int, val value: String)

    fun replace(pattern: Regex, replacement: (MatchResult) -> String): SpokenText {
        return edited(pattern.findAll(text).map { Edit(it.range.first, it.range.last + 1, replacement(it)) }.toList())
    }

    /** Edits are evaluated against the input once; replacement output is never re-matched. */
    internal fun applyRules(rules: List<DocumentRepository.CompiledPronunciationRule>): SpokenText {
        return edited(PronunciationRulesEngine.edits(text, rules).map { Edit(it.range.first, it.range.last + 1, it.replacement) })
    }

    /** Remove presentation markers while retaining exact positions of the enclosed words. */
    internal fun omit(ranges: List<IntRange>): SpokenText {
        val merged = mutableListOf<Edit>()
        ranges.filterNot { it.isEmpty() }.sortedBy { it.first }.forEach { range ->
            require(range.first >= 0 && range.last < text.length)
            val previous = merged.lastOrNull()
            if (previous != null && range.first <= previous.end) {
                merged[merged.lastIndex] = previous.copy(end = maxOf(previous.end, range.last + 1))
            } else merged.add(Edit(range.first, range.last + 1, ""))
        }
        return edited(merged)
    }

    private fun edited(edits: List<Edit>): SpokenText {
        if (edits.isEmpty()) return this
        val output = StringBuilder()
        val offsets = ArrayList<Int>()
        val endOffsets = ArrayList<Int>()
        var cursor = 0
        fun appendOriginal(end: Int) {
            for (i in cursor until end) { output.append(text[i]); offsets.add(positions[i]); endOffsets.add(ends[i]) }
            cursor = end
        }
        edits.forEach { edit ->
            appendOriginal(edit.start)
            if (edit.value == text.substring(edit.start, edit.end)) appendOriginal(edit.end) else {
                output.append(edit.value)
                val start = if (edit.start == text.length) sourceEndOffset(edit.start) else sourceOffset(edit.start)
                val end = if (edit.end > edit.start) sourceEndOffset(edit.end) else start
                repeat(edit.value.length) { offsets.add(start); endOffsets.add(end) }
                cursor = edit.end
            }
        }
        appendOriginal(text.length)
        return SpokenText(output.toString(), offsets.toIntArray(), endOffsets.toIntArray(), sourceLength)
    }

    internal fun mapChars(transform: (Int, Char) -> Char): SpokenText =
        SpokenText(String(CharArray(text.length) { transform(it, text[it]) }), positions, ends, sourceLength)

    internal fun trim(): SpokenText {
        val start = text.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) text.length else it }
        val end = text.indexOfLast { !it.isWhitespace() }.let { if (it < 0) start else it + 1 }
        return SpokenText(text.substring(start, end), positions.copyOfRange(start, end), ends.copyOfRange(start, end), sourceLength)
    }

    /** NFC can merge multiple UTF-16 units; preserve the entire original combining sequence. */
    internal fun normalizeNfc(): SpokenText {
        if (Normalizer.isNormalized(text, Normalizer.Form.NFC)) return this
        val boundaries = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(this@SpokenText.text) }
        val edits = mutableListOf<Edit>()
        var start = boundaries.first()
        var end = boundaries.next()
        while (end != BreakIterator.DONE) {
            val cluster = text.substring(start, end)
            val normalized = Normalizer.normalize(cluster, Normalizer.Form.NFC)
            if (cluster != normalized) edits.add(Edit(start, end, normalized))
            start = end
            end = boundaries.next()
        }
        return edited(edits)
    }

    internal fun appendPause(): SpokenText = edited(listOf(Edit(text.length, text.length, ".")))

    /** Join original mapped cells; generated separators have an empty source span. */
    internal fun join(parts: List<SpokenText>): SpokenText {
        val output = StringBuilder()
        val offsets = ArrayList<Int>()
        val endOffsets = ArrayList<Int>()
        parts.forEachIndexed { index, part ->
            if (index > 0) {
                val boundary = parts[index - 1].sourceEndOffset(parts[index - 1].text.length)
                output.append(' '); offsets.add(boundary); endOffsets.add(boundary)
            }
            output.append(part.text)
            offsets.addAll(part.positions.toList()); endOffsets.addAll(part.ends.toList())
        }
        return SpokenText(output.toString(), offsets.toIntArray(), endOffsets.toIntArray(), sourceLength)
    }

    fun sanitize(): SpokenText = SpeechSanitizer.forSpeech(this)

    companion object {
        fun identity(source: String) = SpokenText(source, IntArray(source.length) { it }, IntArray(source.length) { it + 1 }, source.length)
    }
}

/** Conservative English readings. Ambiguous dates, ordinary words, and foreign prose stay with the voice. */
object ReadingSymbols {
    private val greek = listOf("alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota", "kappa", "lambda", "mu", "nu", "xi", "omicron", "pi", "rho", "sigma", "tau", "upsilon", "phi", "chi", "psi", "omega")
    private val greekLetters = "αβγδεζηθικλμνξοπρστυφχψω"
    private val symbols = mapOf(
        '−' to "minus", '×' to "times", '÷' to "divided by", '±' to "plus or minus", '∓' to "minus or plus",
        '≤' to "less than or equal to", '≥' to "greater than or equal to", '≠' to "not equal to",
        '≈' to "approximately equal to", '≡' to "equivalent to", '∞' to "infinity", '√' to "square root of",
        '∑' to "sum of", '∏' to "product of", '∫' to "integral of", '∂' to "partial derivative", '∇' to "nabla",
        '∝' to "proportional to", '∈' to "is an element of", '∉' to "is not an element of", '∪' to "union", '∩' to "intersection",
        '⊂' to "is a subset of", '⊆' to "is a subset of or equal to", '∀' to "for all", '∃' to "there exists",
        '∠' to "angle", '⊥' to "perpendicular to", '∥' to "parallel to", '°' to "degrees", '‰' to "per mille",
        '⁄' to "over", 'µ' to "micro", 'Ω' to "ohms", 'ℏ' to "h bar"
    )
    private val superscripts = "⁰¹²³⁴⁵⁶⁷⁸⁹"
    private val subscripts = "₀₁₂₃₄₅₆₇₈₉"
    private val elements = "H:hydrogen He:helium Li:lithium Be:beryllium B:boron C:carbon N:nitrogen O:oxygen F:fluorine Ne:neon Na:sodium Mg:magnesium Al:aluminium Si:silicon P:phosphorus S:sulfur Cl:chlorine Ar:argon K:potassium Ca:calcium Sc:scandium Ti:titanium V:vanadium Cr:chromium Mn:manganese Fe:iron Co:cobalt Ni:nickel Cu:copper Zn:zinc Ga:gallium Ge:germanium As:arsenic Se:selenium Br:bromine Kr:krypton Rb:rubidium Sr:strontium Y:yttrium Zr:zirconium Nb:niobium Mo:molybdenum Tc:technetium Ru:ruthenium Rh:rhodium Pd:palladium Ag:silver Cd:cadmium In:indium Sn:tin Sb:antimony Te:tellurium I:iodine Xe:xenon Cs:caesium Ba:barium La:lanthanum Ce:cerium Pr:praseodymium Nd:neodymium Pm:promethium Sm:samarium Eu:europium Gd:gadolinium Tb:terbium Dy:dysprosium Ho:holmium Er:erbium Tm:thulium Yb:ytterbium Lu:lutetium Hf:hafnium Ta:tantalum W:tungsten Re:rhenium Os:osmium Ir:iridium Pt:platinum Au:gold Hg:mercury Tl:thallium Pb:lead Bi:bismuth Po:polonium At:astatine Rn:radon Fr:francium Ra:radium Ac:actinium Th:thorium Pa:protactinium U:uranium Np:neptunium Pu:plutonium Am:americium Cm:curium Bk:berkelium Cf:californium Es:einsteinium Fm:fermium Md:mendelevium No:nobelium Lr:lawrencium Rf:rutherfordium Db:dubnium Sg:seaborgium Bh:bohrium Hs:hassium Mt:meitnerium Ds:darmstadtium Rg:roentgenium Cn:copernicium Nh:nihonium Fl:flerovium Mc:moscovium Lv:livermorium Ts:tennessine Og:oganesson"
        .split(' ').associate { it.substringBefore(':') to it.substringAfter(':') }
    private val canonicalRoman = Regex("M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})")
    fun romanValue(value: String): Int? {
        if (value.isEmpty() || !canonicalRoman.matches(value)) return null
        val values = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
        var total = 0; var previous = 0
        value.reversed().forEach { char -> val current = values.getValue(char); total += if (current < previous) -current else current; previous = current }
        return total
    }

    fun prepare(source: String, english: Boolean = true): SpokenText = expand(SpokenText.identity(source), english).sanitize()

    fun expand(input: SpokenText, english: Boolean = true): SpokenText {
        var spoken = input
        // Presentation ligatures are typographic variants. æ and œ are language letters.
        val ligatures = mapOf("ﬁ" to "fi", "ﬂ" to "fl", "ﬀ" to "ff", "ﬃ" to "ffi", "ﬄ" to "ffl")
        spoken = spoken.replace(Regex("[ﬁﬂﬀﬃﬄ]")) { ligatures.getValue(it.value) }
        if (!english) return spoken
        spoken = SpeechDates.expand(spoken)
        spoken = spoken.replace(Regex("\\b(?:chapter|part|book|section|volume|act|world war)\\s+([IVXLCDM]+)\\b", RegexOption.IGNORE_CASE)) { match ->
            val numeral = match.groupValues[1]
            romanValue(numeral.uppercase(Locale.ROOT))?.let { match.value.dropLast(numeral.length) + it } ?: match.value
        }
        spoken = spoken.replace(Regex("\\b(?:Henry|Edward|Louis|George|Elizabeth|Charles|Pope [A-Z][a-z]+)\\s+([IVXLCDM]+)\\b")) { match ->
            val numeral = match.groupValues[1]; val number = romanValue(numeral)
            if (number == null) match.value else match.value.dropLast(numeral.length) + "the " + ordinal(number)
        }
        val elementIntroducer = Regex("\\b(?:element|(?:chemical |atomic )?symbol|formula)\\s+(?:is\\s+)?$", RegexOption.IGNORE_CASE)
        spoken = spoken.replace(Regex("(?<![\\p{L}\\p{N}_])(?:[A-Z][a-z]?[0-9₀-₉]*)+(?![\\p{L}\\p{N}_])")) { match ->
            val formula = match.value
            val tokens = Regex("([A-Z][a-z]?)([0-9₀-₉]*)").findAll(formula).toList()
            val explicitElement = elementIntroducer.containsMatchIn(spoken.text.substring(0, match.range.first))
            val signal = explicitElement || formula.any { it.isDigit() || it in subscripts } || (tokens.size > 1 && formula.any { it.isLowerCase() })
            if (!signal || tokens.any { it.groupValues[1] !in elements }) formula else tokens.joinToString(" ") {
                val count = it.groupValues[2].map { c -> subscripts.indexOf(c).takeIf { n -> n >= 0 }?.digitToChar() ?: c }.joinToString("")
                elements.getValue(it.groupValues[1]) + if (count.isNotEmpty()) " $count" else ""
            }
        }
        spoken = spoken.replace(Regex("[⁰¹²³⁴⁵⁶⁷⁸⁹]+")) { match ->
            if (!hasExponentBase(spoken.text, match.range.first)) return@replace match.value
            val power = match.value.map { superscripts.indexOf(it).digitToChar() }.joinToString("")
            when (power) { "2" -> " squared "; "3" -> " cubed "; else -> " to the power of $power " }
        }
        spoken = spoken.replace(Regex("[₀₁₂₃₄₅₆₇₈₉]+")) { match -> " subscript " + match.value.map { subscripts.indexOf(it).digitToChar() }.joinToString("") + " " }
        val rangeContext = Regex("\\b(pages?|years?|between|from|range|aged?|ages?|through|dates?|chapters?|verses?)\\b", RegexOption.IGNORE_CASE).containsMatchIn(input.text)
        spoken = spoken.replace(Regex("(?<=\\d)\\s*[-–]\\s*(?=\\d)")) { match ->
            val before = spoken.text.substring(0, match.range.first).takeLastWhile(Char::isDigit)
            val after = spoken.text.substring(match.range.last + 1).takeWhile(Char::isDigit)
            val preceding = spoken.text.getOrNull(match.range.first - before.length - 1)
            val following = spoken.text.getOrNull(match.range.last + 1 + after.length)
            if (preceding == '-' || following == '-') match.value
            else if (match.value.contains('–') || rangeContext || (before.length == 4 && after.length == 4 && !match.value.contains(' '))) " to " else " minus "
        }
        spoken = spoken.replace(Regex("(?<![\\p{L}\\p{N}])-(?=\\d)")) { "minus " }
        val mathematical = Regex("[=+×÷≤≥≠√∑∫]|\\b\\d+\\s*-\\s*\\d+\\b").containsMatchIn(input.text)
        spoken = spoken.replace(Regex("[α-ωΑ-Ωϑϕς]|[−×÷±∓≤≥≠≈≡∞√∑∏∫∂∇∝∈∉∪∩⊂⊆∀∃∠⊥∥°‰⁄µΩℏ]")) { match ->
            val char = match.value[0]
            val greekWord = char.code in 0x0370..0x03ff &&
                listOfNotNull(spoken.text.getOrNull(match.range.first - 1), spoken.text.getOrNull(match.range.last + 1))
                    .any { it.code in 0x0370..0x03ff || it.code in 0x1f00..0x1fff }
            if (greekWord) return@replace match.value
            val lower = when (char) { 'ϑ' -> 'θ'; 'ϕ' -> 'φ'; 'ς' -> 'σ'; else -> char.lowercaseChar() }
            val name = greekLetters.indexOf(lower).takeIf { it >= 0 }?.let { greek[it] } ?: symbols[char]
            if (name != null) " $name " else match.value
        }
        if (mathematical) spoken = spoken.replace(Regex("[=+<>→⇒↔·]|(?<=\\d)\\s*[x*]\\s*(?=\\d)")) { match ->
            " " + when (match.value.trim()) { "=" -> "equals"; "+" -> "plus"; "<" -> "less than"; ">" -> "greater than"; "→" -> "goes to"; "⇒" -> "implies"; "↔" -> "if and only if"; else -> "times" } + " "
        }
        spoken = spoken.replace(Regex("\\b(?:e\\.g\\.|i\\.e\\.|et al\\.)(?=\\s|[,;:]|$)", RegexOption.IGNORE_CASE)) { match ->
            when (match.value.lowercase(Locale.ROOT)) { "e.g." -> "for example"; "i.e." -> "that is"; else -> "and others" }
        }

        // Bible citations: e.g. "Matt 4:5-9" -> "Matthew 4 verse 5 to 9", "1 Cor 2:3" or "I Cor 2:3" -> "First Corinthians 2 verse 3"
        spoken = spoken.replace(Regex("\\b((?:[123I]{1,3}\\s+)?[A-Za-z]+)\\.?\\s+(\\d+)[:.](\\d+)(?:[-–](\\d+))?\\b")) { match ->
            val bookKey = match.groupValues[1].lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
            val bookName = bibleBooks[bookKey]
            if (bookName != null) {
                val chapter = match.groupValues[2]
                val startVerse = match.groupValues[3]
                val endVerse = match.groupValues[4]
                if (endVerse.isNotEmpty()) {
                    "$bookName $chapter verse $startVerse to $endVerse"
                } else {
                    "$bookName $chapter verse $startVerse"
                }
            } else {
                match.value
            }
        }

        // Honorifics and title abbreviations with trailing dots: convert to full words without dots
        // so the TTS engine doesn't pause or articulate a full stop
        spoken = spoken.replace(Regex("\\b(Mr|Mrs|Ms|Dr|Prof|Sr|Jr|Gen|Col|Capt|Lt|Sgt|Rev|Hon|Gov|Sen|Rep)\\.\\s*(?=[A-Z])")) { match ->
            when (match.groupValues[1].lowercase(Locale.ROOT)) {
                "mr" -> "Mister "
                "mrs" -> "Missus "
                "ms" -> "Miz "
                "dr" -> "Doctor "
                "prof" -> "Professor "
                "sr" -> "Senior "
                "jr" -> "Junior "
                "gen" -> "General "
                "col" -> "Colonel "
                "capt" -> "Captain "
                "lt" -> "Lieutenant "
                "sgt" -> "Sergeant "
                "rev" -> "Reverend "
                "hon" -> "Honorable "
                "gov" -> "Governor "
                "sen" -> "Senator "
                "rep" -> "Representative "
                else -> match.value
            }
        }
        spoken = spoken.replace(Regex("\\bSt\\.\\s*(?=[A-Z])")) { "Saint " }
        spoken = spoken.replace(Regex("\\bvs\\.\\b", RegexOption.IGNORE_CASE)) { "versus " }

        return spoken
    }

    private val bibleBooks = mapOf(
        "gen" to "Genesis", "ge" to "Genesis", "genesis" to "Genesis",
        "ex" to "Exodus", "exod" to "Exodus", "exodus" to "Exodus",
        "lev" to "Leviticus", "le" to "Leviticus", "leviticus" to "Leviticus",
        "num" to "Numbers", "nu" to "Numbers", "numbers" to "Numbers",
        "deut" to "Deuteronomy", "de" to "Deuteronomy", "deuteronomy" to "Deuteronomy",
        "josh" to "Joshua", "jos" to "Joshua", "joshua" to "Joshua",
        "judg" to "Judges", "jdg" to "Judges", "judges" to "Judges",
        "ruth" to "Ruth", "ru" to "Ruth",
        "1 sam" to "First Samuel", "2 sam" to "Second Samuel",
        "1sam" to "First Samuel", "2sam" to "Second Samuel",
        "i sam" to "First Samuel", "ii sam" to "Second Samuel",
        "1 samuel" to "First Samuel", "2 samuel" to "Second Samuel",
        "i samuel" to "First Samuel", "ii samuel" to "Second Samuel",
        "1 kings" to "First Kings", "2 kings" to "Second Kings",
        "1 kgs" to "First Kings", "2 kgs" to "Second Kings",
        "1ki" to "First Kings", "2ki" to "Second Kings",
        "i kings" to "First Kings", "ii kings" to "Second Kings",
        "1 chron" to "First Chronicles", "2 chron" to "Second Chronicles",
        "1 chr" to "First Chronicles", "2 chr" to "Second Chronicles",
        "1ch" to "First Chronicles", "2ch" to "Second Chronicles",
        "i chron" to "First Chronicles", "ii chron" to "Second Chronicles",
        "ezra" to "Ezra", "ezr" to "Ezra",
        "neh" to "Nehemiah", "ne" to "Nehemiah", "nehemiah" to "Nehemiah",
        "esth" to "Esther", "es" to "Esther", "esther" to "Esther",
        "job" to "Job", "jb" to "Job",
        "ps" to "Psalm", "psa" to "Psalm", "psalm" to "Psalm", "psalms" to "Psalms",
        "prov" to "Proverbs", "pr" to "Proverbs", "proverbs" to "Proverbs",
        "eccles" to "Ecclesiastes", "eccl" to "Ecclesiastes", "ecclesiastes" to "Ecclesiastes",
        "song" to "Song of Solomon", "song of solomon" to "Song of Solomon",
        "isa" to "Isaiah", "is" to "Isaiah", "isaiah" to "Isaiah",
        "jer" to "Jeremiah", "je" to "Jeremiah", "jeremiah" to "Jeremiah",
        "lam" to "Lamentations", "lamentations" to "Lamentations",
        "ezek" to "Ezekiel", "eze" to "Ezekiel", "ezekiel" to "Ezekiel",
        "dan" to "Daniel", "da" to "Daniel", "daniel" to "Daniel",
        "hos" to "Hosea", "hosea" to "Hosea",
        "joel" to "Joel", "joe" to "Joel",
        "amos" to "Amos", "am" to "Amos",
        "obad" to "Obadiah", "obadiah" to "Obadiah",
        "jonah" to "Jonah", "jon" to "Jonah",
        "mic" to "Micah", "micah" to "Micah",
        "nah" to "Nahum", "nahum" to "Nahum",
        "hab" to "Habakkuk", "habakkuk" to "Habakkuk",
        "zeph" to "Zephaniah", "zephaniah" to "Zephaniah",
        "hag" to "Haggai", "haggai" to "Haggai",
        "zech" to "Zechariah", "zechariah" to "Zechariah",
        "mal" to "Malachi", "malachi" to "Malachi",
        "matt" to "Matthew", "mat" to "Matthew", "mt" to "Matthew", "matthew" to "Matthew",
        "mark" to "Mark", "mk" to "Mark",
        "luke" to "Luke", "lk" to "Luke",
        "john" to "John", "jn" to "John",
        "acts" to "Acts", "ac" to "Acts",
        "rom" to "Romans", "ro" to "Romans", "romans" to "Romans",
        "1 cor" to "First Corinthians", "2 cor" to "Second Corinthians",
        "1cor" to "First Corinthians", "2cor" to "Second Corinthians",
        "i cor" to "First Corinthians", "ii cor" to "Second Corinthians",
        "1 corinthians" to "First Corinthians", "2 corinthians" to "Second Corinthians",
        "i corinthians" to "First Corinthians", "ii corinthians" to "Second Corinthians",
        "gal" to "Galatians", "ga" to "Galatians", "galatians" to "Galatians",
        "eph" to "Ephesians", "ep" to "Ephesians", "ephesians" to "Ephesians",
        "phil" to "Philippians", "php" to "Philippians", "philippians" to "Philippians",
        "col" to "Colossians", "colossians" to "Colossians",
        "1 thess" to "First Thessalonians", "2 thess" to "Second Thessalonians",
        "1th" to "First Thessalonians", "2th" to "Second Thessalonians",
        "i thess" to "First Thessalonians", "ii thess" to "Second Thessalonians",
        "1 thessalonians" to "First Thessalonians", "2 thessalonians" to "Second Thessalonians",
        "1 tim" to "First Timothy", "2 tim" to "Second Timothy",
        "1ti" to "First Timothy", "2ti" to "Second Timothy",
        "i tim" to "First Timothy", "ii tim" to "Second Timothy",
        "1 timothy" to "First Timothy", "2 timothy" to "Second Timothy",
        "titus" to "Titus", "tit" to "Titus",
        "philem" to "Philemon", "phm" to "Philemon", "philemon" to "Philemon",
        "heb" to "Hebrews", "he" to "Hebrews", "hebrews" to "Hebrews",
        "james" to "James", "jas" to "James", "jm" to "James",
        "1 pet" to "First Peter", "2 pet" to "Second Peter",
        "1pe" to "First Peter", "2pe" to "Second Peter",
        "i pet" to "First Peter", "ii pet" to "Second Peter",
        "1 peter" to "First Peter", "2 peter" to "Second Peter",
        "1 john" to "First John", "2 john" to "Second John", "3 john" to "Third John",
        "1jn" to "First John", "2jn" to "Second John", "3jn" to "Third John",
        "i john" to "First John", "ii john" to "Second John", "iii john" to "Third John",
        "jude" to "Jude", "jud" to "Jude",
        "rev" to "Revelation", "re" to "Revelation", "revelation" to "Revelation", "revelations" to "Revelation"
    )

    private fun hasExponentBase(text: String, offset: Int): Boolean {
        val before = text.substring(0, offset)
        val base = before.takeLastWhile { it.isLetterOrDigit() || it == '.' }
        if (base.matches(Regex("\\d+(?:\\.\\d+)?"))) return true
        if (base.length == 1 && base[0].isLetter()) return true
        if (base in setOf("m", "cm", "mm", "km", "ft", "in")) return true
        // A short variable product directly after an operator, as in E=mc².
        return base.length in 2..3 && base.all { it in 'a'..'z' } &&
            before.dropLast(base.length).lastOrNull() in listOf('=', '+', '-', '×', '÷', '*', '/', '(')
    }

    private fun ordinal(number: Int): String = "$number" + when {
        number % 100 in 11..13 -> "th"
        number % 10 == 1 -> "st"; number % 10 == 2 -> "nd"; number % 10 == 3 -> "rd"; else -> "th"
    }
}

internal fun DocumentRepository.prepareSpokenText(source: String, english: Boolean = true): SpokenText {
    val spoken = SpokenText.identity(source).applyRules(compiledPronunciationRules())
    return ReadingSymbols.expand(spoken, english).sanitize()
}
