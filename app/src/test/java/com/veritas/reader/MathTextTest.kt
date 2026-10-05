package com.veritas.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class MathTextTest {

    @Test fun longerCommandsAndUnknownCommandsRemainIntact() {
        assertEquals("⊆ ← ≤", MathText.beautify("\\subseteq \\leftarrow \\le"))
        assertEquals("\\today \\introduction", MathText.beautify("\\today \\introduction"))
    }

    @Test
    fun `plain prose is untouched`() {
        val prose = "The cat sat on the mat and read 3 books."
        assertEquals(prose, MathText.beautify(prose))
    }

    @Test
    fun `single-char superscripts and subscripts`() {
        assertEquals("x²", MathText.beautify("x^2"))
        assertEquals("H₂O", MathText.beautify("H_2O"))
        assertEquals("aⁿ", MathText.beautify("a^n"))
    }

    @Test
    fun `braced scripts`() {
        assertEquals("x²⁵", MathText.beautify("x^{25}"))
        assertEquals("v₀", MathText.beautify("v_{0}"))
    }

    @Test
    fun `latex commands become unicode`() {
        assertEquals("α + β = γ", MathText.beautify("\\alpha + \\beta = \\gamma"))
        assertEquals("3 × 4 ≤ 20", MathText.beautify("3 \\times 4 \\leq 20"))
        assertEquals("π ≈ 3.14", MathText.beautify("\\pi \\approx 3.14"))
    }

    @Test
    fun `fractions and roots`() {
        assertEquals("(a+b)⁄2", MathText.beautify("\\frac{a+b}{2}"))
        assertEquals("√(x+1)", MathText.beautify("\\sqrt{x+1}"))
    }

    @Test
    fun `dollar delimiters are stripped`() {
        assertEquals("E = mc²", MathText.beautify("\$E = mc^2\$"))
    }

    @Test
    fun `unmappable superscript keeps legible fallback`() {
        // 'abc' has no superscript glyphs → parenthesised caret form, not a crash.
        assertEquals("x^(abc)", MathText.beautify("x^{abc}"))
    }

    @Test
    fun `empty and no-signal strings pass through`() {
        assertEquals("", MathText.beautify(""))
        assertEquals("hello world", MathText.beautify("hello world"))
    }

    @Test
    fun `markdown double-underscore underline is preserved`() {
        // Notes use __word__ for underline — must not be eaten as subscripts.
        assertEquals("__bold__", MathText.beautify("__bold__"))
        assertEquals("__2 items__", MathText.beautify("__2 items__"))
        // A genuine single-underscore subscript still converts.
        assertEquals("H₂O", MathText.beautify("H_2O"))
    }
}
