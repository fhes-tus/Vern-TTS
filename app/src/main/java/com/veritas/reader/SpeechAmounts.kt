package com.veritas.reader

import java.util.Locale

/** English names for the notation found in books and imported financial tables. */
internal object SpeechAmounts {
    private enum class Currency(
        val singular: String,
        val plural: String,
        val smallUnit: String? = null,
        val smallUnits: String? = null
    ) {
        DOLLAR("dollar", "dollars", "cent", "cents"),
        POUND("pound", "pounds", "penny", "pence"),
        EURO("euro", "euros", "cent", "cents"),
        CFA("CFA franc", "CFA francs"),
        CEDI("cedi", "cedis", "pesewa", "pesewas")
    }

    private const val SPACE = """[ \t\u00A0\u2007\u202F]"""
    private const val NUMBER = """[+\-−]?(?:\d{1,3}(?:(?:[.,]|$SPACE)\d{3})+|\d+)(?:[.,]\d+)?"""
    private const val MARK = """(?:US\$|GH$SPACE*[₵¢]|USD|GBP|EUR|GHS|F$SPACE*CFA|CFA|XOF|XAF|[$£€₵])"""
    private val currencyAmount = Regex(
        """(?<![\p{L}\p{N}_])(?:($MARK)$SPACE*($NUMBER)|($NUMBER)$SPACE*($MARK))(?![\p{L}\p{N}_])""",
        RegexOption.IGNORE_CASE
    )
    private val currencySymbol = Regex("""(?<![\p{L}\p{N}_])[$£€₵](?![\p{L}\p{N}_])""")
    private val horizontalSpace = Regex(SPACE)
    private val groupedInteger = Regex("""[+\-−]?\d{1,3}([.,])\d{3}(?:\1\d{3})*""")
    private val percent = Regex("""$SPACE*%$SPACE*""")
    private val ampersand = Regex("""$SPACE*&(?:amp;)?$SPACE*""", RegexOption.IGNORE_CASE)

    fun expand(input: SpokenText): SpokenText {
        val withAmounts = input.replace(currencyAmount) { match ->
            val mark = match.groupValues[1].ifEmpty { match.groupValues[4] }
            val number = match.groupValues[2].ifEmpty { match.groupValues[3] }
            describe(number, currencyFor(mark))
        }.replace(currencySymbol) { currencyFor(it.value).singular }
        return expandWord(expandWord(withAmounts, percent, "percent"), ampersand, "and")
    }

    private fun expandWord(input: SpokenText, pattern: Regex, word: String): SpokenText =
        input.replace(pattern) { match ->
            val before = input.text.getOrNull(match.range.first - 1)
            val after = input.text.getOrNull(match.range.last + 1)
            val prefix = if (before != null && !before.isWhitespace() && before !in "([") " " else ""
            val suffix = if (after != null && (after.isLetterOrDigit() || after in "([\"“‘")) " " else ""
            prefix + word + suffix
        }

    private fun currencyFor(mark: String): Currency = when (mark.replace(horizontalSpace, "").uppercase(Locale.ROOT)) {
        "$", "US$", "USD" -> Currency.DOLLAR
        "£", "GBP" -> Currency.POUND
        "€", "EUR" -> Currency.EURO
        "₵", "GH₵", "GH¢", "GHS" -> Currency.CEDI
        else -> Currency.CFA
    }

    private fun describe(number: String, currency: Currency): String {
        val compact = number.replace(horizontalSpace, "")
        val separator = compact.indexOfLast { it == '.' || it == ',' }
        val fractional = if (separator >= 0 && !groupedInteger.matches(compact)) {
            compact.substring(separator + 1)
        } else null
        val whole = (if (fractional != null) compact.substring(0, separator) else compact)
            .replace(",", "").replace(".", "")
        val wholeDigits = whole.trimStart('+', '-', '−').trimStart('0')
        if (fractional != null && fractional.length > 2) return "$whole.$fractional " + currency.plural
        val small = fractional?.padEnd(2, '0')?.toIntOrNull() ?: 0
        val unit = if (wholeDigits == "1" && small == 0) currency.singular else currency.plural
        if (small == 0) return "$whole $unit"
        val smallUnit = if (small == 1) currency.smallUnit else currency.smallUnits
        if (smallUnit == null) return "$whole.$fractional $unit"
        val wholeUnit = if (wholeDigits == "1") currency.singular else currency.plural
        if (wholeDigits.isEmpty()) {
            val sign = whole.firstOrNull()?.takeIf { it in "+-−" }?.toString().orEmpty()
            return "$sign$small $smallUnit"
        }
        return "$whole $wholeUnit and $small $smallUnit"
    }
}
