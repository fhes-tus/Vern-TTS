package com.veritas.reader

import android.graphics.RectF
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripperByArea
import com.tom_roush.pdfbox.text.TextPosition
import java.io.IOException
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.abs

internal data class PdfCalloutBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(position: TextPosition): Boolean =
        position.x + position.width / 2 in left..right && position.y in top..bottom
}

/** Identifies closed side boxes where prose visibly wraps beside the box and resumes across its lane. */
internal object PdfCalloutExtractor {
    private const val EDGE = 1.5f
    private val paragraphBreak = Regex("\n(?:[ \\t]*\n)+")
    private val sentenceTail = Regex("[.!?…。！？][’'”\"»）)\\]]*\\s*\\d{0,3}$")
    private val abbreviation = Regex("(?:\\b(?:Mr|Mrs|Ms|Dr|Prof|St|vs|etc)|\\b\\p{Lu})\\.$", RegexOption.IGNORE_CASE)

    fun detect(probe: PdfPageProbe, rules: PdfRuledTableExtractor.Rules, pageWidth: Float, pageHeight: Float): List<PdfCalloutBox> {
        val boxes = mutableListOf<PdfCalloutBox>()
        for (top in rules.horizontal) {
            val width = top.end - top.start
            if (width !in 40f..(pageWidth * .53f)) continue
            val left = rules.vertical.firstOrNull { abs(it.axis - top.start) <= EDGE && abs(it.start - top.axis) <= EDGE } ?: continue
            val height = left.end - left.start
            if (height !in 16f..(pageHeight * .48f)) continue
            if (rules.vertical.none { abs(it.axis - top.end) <= EDGE && abs(it.start - top.axis) <= EDGE && abs(it.end - left.end) <= EDGE }) continue
            if (rules.horizontal.none { abs(it.axis - left.end) <= EDGE && abs(it.start - top.start) <= EDGE && abs(it.end - top.end) <= EDGE }) continue
            val box = PdfCalloutBox(top.start, top.axis, top.end, left.end)
            if (rules.horizontal.any { it.axis > box.top + EDGE && it.axis < box.bottom - EDGE &&
                    it.start <= box.left + EDGE && it.end >= box.right - EDGE } ||
                rules.vertical.any { it.axis > box.left + EDGE && it.axis < box.right - EDGE &&
                    it.start <= box.top + EDGE && it.end >= box.bottom - EDGE }) continue
            val band = probe.lines.filter { it.y in box.top..box.bottom }
            if (band.count { line -> line.segments.any { it.minX >= box.left - EDGE && it.maxX <= box.right + EDGE && it.glyphCount >= 3 } } < 2) continue
            val proseOnLeft = band.count { line -> line.segments.any { it.maxX <= box.left + EDGE && it.glyphCount >= 14 } } >= 2
            val proseOnRight = band.count { line -> line.segments.any { it.minX >= box.right - EDGE && it.glyphCount >= 14 } } >= 2
            if (proseOnLeft == proseOnRight) continue
            val bodySpansBoxEdge = probe.lines.any { line ->
                val edge = if (proseOnLeft) box.left else box.right
                line.y !in box.top..box.bottom && minOf(abs(line.y - box.top), abs(line.y - box.bottom)) < pageHeight * .22f &&
                    line.segments.any { it.minX < edge - 14f && it.maxX > edge + 14f && it.glyphCount >= 25 }
            }
            if (bodySpansBoxEdge && boxes.none { box.left < it.right && it.left < box.right && box.top < it.bottom && it.top < box.bottom }) {
                boxes.add(box)
            }
        }
        return boxes.sortedBy { it.top }
    }

    fun read(page: PDPage, boxes: List<PdfCalloutBox>): List<String>? = try {
        val stripper = PDFTextStripperByArea().apply { sortByPosition = true }
        boxes.forEachIndexed { index, box ->
            stripper.addRegion(index.toString(), RectF(box.left, box.top, box.right, box.bottom))
        }
        stripper.extractRegions(page)
        boxes.indices.map { stripper.getTextForRegion(it.toString()).trim() }.takeIf { texts -> texts.none { it.isBlank() } }
    } catch (_: IOException) {
        null
    }

    fun place(body: String, callouts: List<Pair<String, String>>): String {
        val paragraphs = body.split(paragraphBreak).toMutableList()
        for ((anchor, text) in callouts.asReversed()) {
            val key = anchor.key()
            val anchorIndex = paragraphs.indexOfFirst { key.isNotBlank() && it.key().contains(key) }.coerceAtLeast(0)
            val following = (anchorIndex until paragraphs.size).firstOrNull { endsSentence(paragraphs[it]) }
            val previous = (anchorIndex - 1 downTo 0).firstOrNull { endsSentence(paragraphs[it]) }
            val paragraph = following ?: previous
            if (paragraph != null) {
                paragraphs.add(paragraph + 1, text)
                continue
            }
            // Some pages have no explicit paragraph breaks. Look for a complete sentence within the prose.
            val prose = paragraphs[anchorIndex]
            val sentences = BreakIterator.getSentenceInstance(Locale.ROOT).apply { setText(prose) }
            val boundaries = generateSequence(sentences.next().takeUnless { it == BreakIterator.DONE }) {
                sentences.next().takeUnless { it == BreakIterator.DONE }
            }
            val boundary = boundaries.lastOrNull { endsSentence(prose.substring(0, it)) }
            if (boundary != null) {
                paragraphs[anchorIndex] = prose.substring(0, boundary).trimEnd()
                paragraphs.addAll(anchorIndex + 1, listOf(text, prose.substring(boundary).trimStart()).filter { it.isNotBlank() })
            } else {
                paragraphs.add(0, text)
            }
        }
        return paragraphs.joinToString("\n\n")
    }

    private fun endsSentence(text: String): Boolean {
        val end = text.trimEnd().removeSuffix("</u>")
        return sentenceTail.containsMatchIn(end) && !abbreviation.containsMatchIn(end)
    }

    private fun String.key(): String = replace("<u>", "").replace("</u>", "").filter { it.isLetterOrDigit() }
}
