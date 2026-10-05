package com.veritas.reader

/**
 * Best-effort equation prettifier. Converts common LaTeX / ASCII math notation into
 * Unicode so equations read as equations (x², √, ×, π, ∑…) instead of raw markup —
 * in the reader, flashcards, and notes. Intentionally conservative: it only touches
 * clear math signals (backslash commands, $…$ delimiters, ^{…}/_{…} groups), so
 * ordinary prose, code, dates, and URLs are left alone.
 *
 * Full stacked-fraction / large-operator LAYOUT needs a math engine and is out of
 * scope; this keeps everything inline but legible. Because the output is plain text,
 * modern TTS engines also read the Unicode (², ×, √, π) far better than "caret two".
 */
object MathText {

    private val superscripts = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵',
        '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹', '+' to '⁺', '-' to '⁻',
        '=' to '⁼', '(' to '⁽', ')' to '⁾', 'n' to 'ⁿ', 'i' to 'ⁱ'
    )

    private val commandPatterns by lazy {
        commands.sortedByDescending { it.first.length }.map { (command, replacement) ->
            Regex(Regex.escape(command) + if (command.last().isLetter()) "(?![A-Za-z])" else "") to replacement
        }
    }
    private val subscripts = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄', '5' to '₅',
        '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉', '+' to '₊', '-' to '₋',
        '=' to '₌', '(' to '₍', ')' to '₎', 'a' to 'ₐ', 'e' to 'ₑ', 'o' to 'ₒ',
        'x' to 'ₓ', 'n' to 'ₙ'
    )

    // LaTeX command → Unicode. Longest keys first so "\leftarrow" beats "\left".
    private val commands: List<Pair<String, String>> = listOf(
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
        "\\epsilon" to "ε", "\\varepsilon" to "ε", "\\zeta" to "ζ", "\\eta" to "η",
        "\\theta" to "θ", "\\iota" to "ι", "\\kappa" to "κ", "\\lambda" to "λ",
        "\\mu" to "μ", "\\nu" to "ν", "\\xi" to "ξ", "\\pi" to "π", "\\rho" to "ρ",
        "\\sigma" to "σ", "\\tau" to "τ", "\\upsilon" to "υ", "\\phi" to "φ",
        "\\varphi" to "φ", "\\chi" to "χ", "\\psi" to "ψ", "\\omega" to "ω",
        "\\Gamma" to "Γ", "\\Delta" to "Δ", "\\Theta" to "Θ", "\\Lambda" to "Λ",
        "\\Pi" to "Π", "\\Sigma" to "Σ", "\\Phi" to "Φ", "\\Psi" to "Ψ", "\\Omega" to "Ω",
        "\\times" to "×", "\\div" to "÷", "\\cdot" to "·", "\\pm" to "±", "\\mp" to "∓",
        "\\leq" to "≤", "\\le" to "≤", "\\geq" to "≥", "\\ge" to "≥", "\\neq" to "≠",
        "\\approx" to "≈", "\\equiv" to "≡", "\\propto" to "∝", "\\infty" to "∞",
        "\\sum" to "∑", "\\prod" to "∏", "\\int" to "∫", "\\partial" to "∂",
        "\\nabla" to "∇", "\\sqrt" to "√", "\\degree" to "°", "\\circ" to "°",
        "\\rightarrow" to "→", "\\to" to "→", "\\leftarrow" to "←", "\\Rightarrow" to "⇒",
        "\\Leftarrow" to "⇐", "\\leftrightarrow" to "↔", "\\ldots" to "…", "\\dots" to "…",
        "\\cdots" to "⋯", "\\in" to "∈", "\\notin" to "∉", "\\subset" to "⊂",
        "\\subseteq" to "⊆", "\\cup" to "∪", "\\cap" to "∩", "\\forall" to "∀",
        "\\exists" to "∃", "\\angle" to "∠", "\\perp" to "⊥", "\\parallel" to "∥",
        "\\prime" to "′", "\\star" to "⋆", "\\ast" to "∗", "\\bullet" to "•",
        "\\left" to "", "\\right" to "", "\\," to " ", "\\;" to " ", "\\quad" to "  "
    )

    private val fracRegex = Regex("""\\frac\s*\{([^{}]*)\}\s*\{([^{}]*)\}""")
    private val sqrtArgRegex = Regex("""√\s*\{([^{}]*)\}""")
    private val supGroupRegex = Regex("""\^\{([^{}]*)\}""")
    private val subGroupRegex = Regex("""_\{([^{}]*)\}""")
    private val supCharRegex = Regex("""\^([A-Za-z0-9+\-=()])""")
    // Underscore not adjacent to another underscore, so note markdown's "__underline__"
    // is never mistaken for a subscript.
    private val subCharRegex = Regex("""(?<!_)_(?!_)([A-Za-z0-9+\-=()])""")

    fun beautify(input: String): String {
        if (input.isEmpty()) return input
        // Cheap gate: skip strings with no math signals at all.
        if (!input.contains('\\') && !input.contains('^') && !input.contains('_') && !input.contains('$')) {
            return input
        }
        var s = input
        // Strip inline-math delimiters, keeping the inner content.
        s = s.replace(Regex("""\$([^$\n]{1,200})\$"""), "$1")
        s = s.replace("\\(", "").replace("\\)", "").replace("\\[", "").replace("\\]", "")
        // \frac{a}{b} → (a)/(b) — inline, before command expansion consumes braces.
        s = fracRegex.replace(s) { m ->
            val num = m.groupValues[1].trim()
            val den = m.groupValues[2].trim()
            val np = if (num.length > 1) "($num)" else num
            val dp = if (den.length > 1) "($den)" else den
            "$np⁄$dp"
        }
        // LaTeX commands → Unicode.
        for ((pattern, uni) in commandPatterns) s = pattern.replace(s) { uni }
        // √{x} → √(x)
        s = sqrtArgRegex.replace(s) { m ->
            val inner = m.groupValues[1].trim()
            if (inner.length > 1) "√($inner)" else "√$inner"
        }
        // Single chars first (they can't match a leading "{"), then braced groups.
        // Order matters: a group's legible fallback is "^(…)", and running the
        // single-char pass afterwards would wrongly re-consume that "^(".
        s = supCharRegex.replace(s) { m -> toScript(m.groupValues[1], superscripts, sup = true) }
        s = subCharRegex.replace(s) { m -> toScript(m.groupValues[1], subscripts, sup = false) }
        s = supGroupRegex.replace(s) { m -> toScript(m.groupValues[1], superscripts, sup = true) }
        s = subGroupRegex.replace(s) { m -> toScript(m.groupValues[1], subscripts, sup = false) }
        return s
    }

    private fun toScript(group: String, map: Map<Char, Char>, sup: Boolean): String {
        if (group.isEmpty()) return ""
        // All chars mappable → real Unicode script; otherwise keep a legible fallback.
        if (group.all { map.containsKey(it) }) {
            return group.map { map[it] }.joinToString("")
        }
        val marker = if (sup) "^" else "_"
        return if (group.length > 1) "$marker($group)" else "$marker$group"
    }
}
