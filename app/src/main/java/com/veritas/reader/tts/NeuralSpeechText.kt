package com.veritas.reader.tts

import com.veritas.reader.SpokenText
import com.veritas.reader.SpeechDates
import java.util.Locale

/** Final neural preparation, with a composed map back to the unchanged stored book. */
internal object NeuralSpeechText {
    private val invisible = Regex("[\\u00AD\\u200B\\u2060\\uFEFF\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]")
    private val whitespace = Regex("[\\s\\u00A0\\u202F]+")

    private val PRESERVED_ACRONYMS = setOf(
        "NASA", "FBI", "CIA", "USA", "UK", "UN", "NATO", "EU", "HTML", "CSS", "PDF", "API",
        "URL", "SDK", "UI", "UX", "TTS", "AI", "GPS", "CPU", "RAM", "ROM", "USB", "DVD", "CD",
        "TV", "FM", "AM", "CEO", "CFO", "CTO", "HR", "PR", "VIP", "IQ", "EQ", "ID", "PIN",
        "ATM", "SOS", "RIP", "DIY", "FAQ", "ASAP", "HIV", "AIDS", "COVID", "WHO", "IMF", "BBC", "CNN"
    )

    private val COMMON_UPPERCASE_WORDS = setOf(
        "IT", "IS", "A", "AN", "THE", "IN", "ON", "AT", "TO", "FOR", "OF", "WITH", "BY", "FROM",
        "UP", "ABOUT", "INTO", "OVER", "AFTER", "BENEATH", "UNDER", "ABOVE", "AND", "BUT", "OR",
        "NOR", "SO", "YET", "IF", "WHILE", "AS", "THAT", "THIS", "THESE", "THOSE", "THEN", "THAN",
        "BE", "ARE", "WAS", "WERE", "BEEN", "BEING", "HAVE", "HAS", "HAD", "DO", "DOES", "DID",
        "WILL", "WOULD", "SHALL", "SHOULD", "CAN", "COULD", "MAY", "MIGHT", "MUST",
        "HE", "SHE", "THEY", "WE", "YOU", "ME", "HIM", "HER", "US", "THEM", "MY", "YOUR", "HIS",
        "ITS", "OUR", "THEIR", "MINE", "YOURS", "HERS", "OURS", "THEIRS", "WHO", "WHOM", "WHOSE",
        "WHICH", "WHAT", "WHERE", "WHEN", "WHY", "HOW", "ALL", "ANY", "BOTH", "EACH", "FEW",
        "MORE", "MOST", "OTHER", "SOME", "SUCH", "NO", "NOT", "ONLY", "OWN", "SAME", "TOO", "VERY",
        "JUST", "NOW", "BOY", "GIRL", "MAN", "MEN", "WOMAN", "WOMEN", "CHILD", "CHILDREN",
        "DAY", "NIGHT", "TIME", "YEAR", "GOOD", "BAD", "NEW", "OLD", "FIRST", "LAST", "LONG",
        "GREAT", "LITTLE", "OWN", "OTHER", "RIGHT", "BIG", "HIGH", "SMALL", "LARGE", "NEXT", "EARLY",
        "YOUNG", "IMPORTANT", "PUBLIC", "ABLE", "CHAPTER", "BOOK", "PAGE",
        "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN", "EIGHT", "NINE", "TEN"
    )

    fun prepare(text: String): String = prepare(SpokenText.identity(text)).text

    fun prepare(text: SpokenText): SpokenText {
        // PDF/web copies can contain soft hyphens and decomposed accents. Remove discretionary
        // breaks without separating the two halves of a word; keep real apostrophes/hyphens.
        val normalized = text.replace(invisible) { "" }.normalizeNfc()
        var sanitized = normalized.sanitize().replace(whitespace) { " " }.trim()
        sanitized = SpeechDates.expand(sanitized, ::verbalizeYear)

        // 1. Strip dangling quotes that trigger neural engine vocoder pops at chunk boundaries
        sanitized = sanitized.replace(Regex("""^[\"“'‘»›]+\s*""")) { "" }
        sanitized = sanitized.replace(Regex("""\s*[\"”'’«‹]+$""")) { "" }
        sanitized = sanitized.replace(Regex("""([.!?])[\"”'’«‹]+""")) { it.groupValues[1] }

        // 2. Normalize all-caps words so words like "IT IS A BOY" are read as words, not spelled acronyms
        sanitized = sanitized.replace(Regex("\\b[A-Z]{1,10}\\b")) { match ->
            val word = match.value
            if (word in PRESERVED_ACRONYMS) {
                word
            } else if (word in COMMON_UPPERCASE_WORDS) {
                word.lowercase(Locale.ROOT)
            } else {
                word
            }
        }

        // 3. 4-digit years (1500..2099): verbalize as paired words e.g. "1982" -> "nineteen eighty-two"
        // Avoids Kitten neural TTS mumbling the initial two digits of standalone numbers
        sanitized = sanitized.replace(Regex("(?<![\\d.])\\b(1[5-9]\\d{2}|20\\d{2})\\b(?![\\d.])")) { match ->
            val year = match.value.toIntOrNull()
            if (year != null) verbalizeYear(year) else match.value
        }

        return sanitized.replace(whitespace) { " " }.trim()
    }

    private fun verbalizeYear(year: Int): String {
        if (year in 1500..1999) {
            val century = year / 100
            val rem = year % 100
            val centuryWord = when (century) {
                15 -> "fifteen"; 16 -> "sixteen"; 17 -> "seventeen"
                18 -> "eighteen"; 19 -> "nineteen"; else -> return year.toString()
            }
            return when {
                rem == 0 -> "$centuryWord hundred"
                rem < 10 -> "$centuryWord oh ${numberUnderTwenty(rem)}"
                rem < 20 -> "$centuryWord ${numberUnderTwenty(rem)}"
                else -> {
                    val tens = when (rem / 10) {
                        2 -> "twenty"; 3 -> "thirty"; 4 -> "forty"; 5 -> "fifty"
                        6 -> "sixty"; 7 -> "seventy"; 8 -> "eighty"; 9 -> "ninety"
                        else -> ""
                    }
                    val ones = rem % 10
                    if (ones == 0) "$centuryWord $tens" else "$centuryWord $tens-${numberUnderTwenty(ones)}"
                }
            }
        } else if (year in 2000..2009) {
            val rem = year % 100
            return if (rem == 0) "two thousand" else "two thousand ${numberUnderTwenty(rem)}"
        } else if (year in 2010..2099) {
            val rem = year % 100
            val remWord = when {
                rem < 20 -> numberUnderTwenty(rem)
                else -> {
                    val tens = when (rem / 10) {
                        1 -> "ten"; 2 -> "twenty"; 3 -> "thirty"; 4 -> "forty"; 5 -> "fifty"
                        6 -> "sixty"; 7 -> "seventy"; 8 -> "eighty"; 9 -> "ninety"
                        else -> ""
                    }
                    val ones = rem % 10
                    if (ones == 0) tens else "$tens-${numberUnderTwenty(ones)}"
                }
            }
            return "twenty $remWord"
        }
        return year.toString()
    }

    private fun numberUnderTwenty(n: Int): String = when (n) {
        1 -> "one"; 2 -> "two"; 3 -> "three"; 4 -> "four"; 5 -> "five"
        6 -> "six"; 7 -> "seven"; 8 -> "eight"; 9 -> "nine"; 10 -> "ten"
        11 -> "eleven"; 12 -> "twelve"; 13 -> "thirteen"; 14 -> "fourteen"
        15 -> "fifteen"; 16 -> "sixteen"; 17 -> "seventeen"; 18 -> "eighteen"
        19 -> "nineteen"; else -> ""
    }
}
