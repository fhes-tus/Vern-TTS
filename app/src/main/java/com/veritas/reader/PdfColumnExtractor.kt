package com.veritas.reader

import android.graphics.RectF
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.PDFTextStripperByArea
import com.tom_roush.pdfbox.text.TextPosition

internal data class PositionedPdfLine(
    val text: String,
    val minX: Float,
    val maxX: Float,
    val y: Float,
    val height: Float
) {
    val centerX: Float
        get() = (minX + maxX) / 2f

    val width: Float
        get() = maxX - minX
}

internal data class PositionedPdfSegment(
    val minX: Float,
    val maxX: Float,
    val y: Float,
    val height: Float,
    val glyphCount: Int
) {
    val centerX: Float
        get() = (minX + maxX) / 2f

    val width: Float
        get() = maxX - minX
}

internal data class PdfPageProbe(
    val plainText: String,
    val lines: List<PositionedPdfLine>,
    val segments: List<PositionedPdfSegment>
)

internal data class PdfColumnLayout(
    val splitX: Float,
    val columnTopY: Float,
    val columnBottomY: Float,
    val pageWidth: Float,
    val pageHeight: Float,
    val rowBreaks: List<Pair<Float, Float>> = emptyList()
)

internal class PdfLayoutProbeStripper : PDFTextStripper() {
    val positionedLines = mutableListOf<PositionedPdfLine>()
    val positionedSegments = mutableListOf<PositionedPdfSegment>()

    init {
        sortByPosition = true
        setShouldSeparateByBeads(false)
    }

    private val glyphs = mutableListOf<TextPosition>()

    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        glyphs.addAll(textPositions)
        super.writeString(text, textPositions)
    }

    private fun buildGeometry() {
        // PDFTextStripper may call writeString once per WORD. A word-sized probe
        // cannot detect columns; rebuild complete visual rows from all glyphs.
        val rows = mutableListOf<MutableList<TextPosition>>()
        glyphs.sortedWith(compareBy<TextPosition> { it.y }.thenBy { it.x }).forEach { glyph ->
            val last = rows.lastOrNull()
            val tolerance = maxOf(2f, minOf(glyph.heightDir, last?.firstOrNull()?.heightDir ?: glyph.heightDir) * 0.45f)
            if (last != null && kotlin.math.abs(last.first().y - glyph.y) <= tolerance) last.add(glyph)
            else rows.add(mutableListOf(glyph))
        }
        rows.forEach { row ->
            val ordered = row.sortedBy { it.x }
            val widths = ordered.map { it.width }.filter { it > 0 }.sorted()
            val gapThreshold = maxOf(8f, (widths.getOrNull(widths.size / 2) ?: 4f) * 2.5f)
            val y = ordered.map { it.y.toDouble() }.average().toFloat()
            val height = ordered.map { it.heightDir }.sorted().let { it[it.size / 2] }.coerceAtLeast(4f)
            val rowText = buildString {
                ordered.forEachIndexed { index, glyph ->
                    val previous = ordered.getOrNull(index - 1)
                    if (previous != null && glyph.x - (previous.x + previous.width) > maxOf(1f, glyph.widthOfSpace * 0.5f)) append(' ')
                    append(glyph.unicode)
                }
            }.trim()
            positionedLines.add(PositionedPdfLine(rowText, ordered.minOf { it.x }, ordered.maxOf { it.x + it.width }, y, height))
            var start = 0
            fun addSegment(end: Int) {
                val group = ordered.subList(start, end)
                if (group.isNotEmpty()) positionedSegments.add(PositionedPdfSegment(
                    group.minOf { it.x }, group.maxOf { it.x + it.width }, y, height,
                    group.sumOf { it.unicode.length }
                ))
            }
            for (index in 1 until ordered.size) {
                val previous = ordered[index - 1]
                if (ordered[index].x - (previous.x + previous.width) > gapThreshold) {
                    addSegment(index)
                    start = index
                }
            }
            addSegment(ordered.size)
        }
    }

    companion object {
        fun extract(document: PDDocument, pageNumber: Int): PdfPageProbe {
            val stripper = PdfLayoutProbeStripper().apply {
                startPage = pageNumber
                endPage = pageNumber
            }
            val plainText = stripper.getText(document)
            stripper.buildGeometry()
            return PdfPageProbe(
                plainText = plainText,
                lines = stripper.positionedLines.toList(),
                segments = stripper.positionedSegments.toList()
            )
        }
    }
}

internal object PdfPageTextExtractor {
    fun extractPage(document: PDDocument, pageNumber: Int): String {
        val page = document.getPage((pageNumber - 1).coerceAtLeast(0))
        val probe = PdfLayoutProbeStripper.extract(document, pageNumber)
        val layout = detectColumns(probe, page)
        return if (layout == null) {
            probe.plainText
        } else {
            extractColumnPage(page, layout).ifBlank { probe.plainText }
        }
    }

    internal fun detectColumns(probe: PdfPageProbe, page: PDPage): PdfColumnLayout? {
        val box = page.cropBox ?: page.mediaBox ?: return null
        // Area extraction uses rotation-adjusted x/y; probe and regions must share it.
        val sideways = ((page.rotation % 360) + 360) % 360 in listOf(90, 270)
        val pageWidth = (if (sideways) box.height else box.width).coerceAtLeast(1f)
        val pageHeight = (if (sideways) box.width else box.height).coerceAtLeast(1f)
        val usefulLines = probe.lines.filter { it.text.length >= 2 && it.width > 8f }
        val contentWidth = (usefulLines.maxOfOrNull { it.maxX } ?: pageWidth) -
            (usefulLines.minOfOrNull { it.minX } ?: 0f)
        val finalBase = PdfColumnDetector.detect(probe.segments, pageWidth, pageHeight) ?: return null
        // Detect horizontal breaks / chapter dividers spanning across the page within the column area
        val breakLines = usefulLines.filter { line ->
            val isHeader = Regex("""^(CHAPTER|Chapter|PROLOGUE|Prologue|EPILOGUE|Epilogue|INTRODUCTION|Introduction|PART|Part|BOOK|Book|SECTION|Section)\b.*""", RegexOption.IGNORE_CASE).containsMatchIn(line.text)
            val crossesGutter = probe.segments.any { segment ->
                kotlin.math.abs(segment.y - line.y) <= maxOf(segment.height, line.height) &&
                    segment.minX < finalBase.splitX - 4f && segment.maxX > finalBase.splitX + 4f
            }
            val isPageSpanningHeader = crossesGutter && (isHeader || line.width >= contentWidth * 0.45f)
            isPageSpanningHeader && line.y in (finalBase.columnTopY + 35f)..(finalBase.columnBottomY - 35f)
        }

        val rowBreaks = if (breakLines.isNotEmpty()) {
            val sortedBreaks = breakLines.sortedBy { it.y }
            val clusters = mutableListOf<MutableList<PositionedPdfLine>>()
            sortedBreaks.forEach { line ->
                val lastCluster = clusters.lastOrNull()
                if (lastCluster != null && line.y - lastCluster.last().y < 28f) {
                    lastCluster.add(line)
                } else {
                    clusters.add(mutableListOf(line))
                }
            }
            clusters.map { cluster ->
                val topY = (cluster.minOf { it.y } - 8f).coerceAtLeast(finalBase.columnTopY)
                val bottomY = (cluster.maxOf { it.y + it.height } + 8f).coerceAtMost(finalBase.columnBottomY)
                Pair(topY, bottomY)
            }
        } else emptyList()

        return finalBase.copy(rowBreaks = rowBreaks)
    }

    private fun extractColumnPage(page: PDPage, layout: PdfColumnLayout): String {
        val stripper = PDFTextStripperByArea().apply {
            sortByPosition = true
        }

        val top = RectF(0f, 0f, layout.pageWidth, layout.columnTopY)
        if (top.height() > 4f) stripper.addRegion("top", top)

        val bottom = RectF(0f, layout.columnBottomY, layout.pageWidth, layout.pageHeight)
        if (bottom.height() > 4f) stripper.addRegion("bottom", bottom)

        val resultParts = mutableListOf<String>()

        if (layout.rowBreaks.isEmpty()) {
            val left = RectF(0f, layout.columnTopY, layout.splitX, layout.columnBottomY)
            val right = RectF(layout.splitX, layout.columnTopY, layout.pageWidth, layout.columnBottomY)
            stripper.addRegion("left", left)
            stripper.addRegion("right", right)
            stripper.extractRegions(page)

            fun regionText(name: String): String =
                if (name in stripper.regions) cleanRegionText(stripper.getTextForRegion(name)) else ""

            val topText = regionText("top")
            val leftText = regionText("left")
            val rightText = regionText("right")
            val bottomText = regionText("bottom")
            val middleText = listOf(leftText, rightText).filter { it.isNotBlank() }.joinToString("\n\n")
            return listOf(topText, middleText, bottomText).filter { it.isNotBlank() }.joinToString("\n\n").trim()
        } else {
            var currentY = layout.columnTopY
            layout.rowBreaks.forEachIndexed { index, (breakTop, breakBottom) ->
                if (breakTop > currentY + 10f) {
                    val leftBand = RectF(0f, currentY, layout.splitX, breakTop)
                    val rightBand = RectF(layout.splitX, currentY, layout.pageWidth, breakTop)
                    stripper.addRegion("left_$index", leftBand)
                    stripper.addRegion("right_$index", rightBand)
                }
                val headerBand = RectF(0f, breakTop, layout.pageWidth, breakBottom)
                stripper.addRegion("header_$index", headerBand)
                currentY = breakBottom
            }
            if (layout.columnBottomY > currentY + 10f) {
                val lastIdx = layout.rowBreaks.size
                val leftBand = RectF(0f, currentY, layout.splitX, layout.columnBottomY)
                val rightBand = RectF(layout.splitX, currentY, layout.pageWidth, layout.columnBottomY)
                stripper.addRegion("left_$lastIdx", leftBand)
                stripper.addRegion("right_$lastIdx", rightBand)
            }

            stripper.extractRegions(page)

            fun regionText(name: String): String =
                if (name in stripper.regions) cleanRegionText(stripper.getTextForRegion(name)) else ""

            val topText = regionText("top")
            if (topText.isNotBlank()) resultParts.add(topText)

            var bandY = layout.columnTopY
            layout.rowBreaks.forEachIndexed { index, (breakTop, breakBottom) ->
                if (breakTop > bandY + 10f) {
                    val l = regionText("left_$index")
                    val r = regionText("right_$index")
                    if (l.isNotBlank()) resultParts.add(l)
                    if (r.isNotBlank()) resultParts.add(r)
                }
                val h = regionText("header_$index")
                if (h.isNotBlank()) resultParts.add(h)
                bandY = breakBottom
            }
            if (layout.columnBottomY > bandY + 10f) {
                val lastIdx = layout.rowBreaks.size
                val l = regionText("left_$lastIdx")
                val r = regionText("right_$lastIdx")
                if (l.isNotBlank()) resultParts.add(l)
                if (r.isNotBlank()) resultParts.add(r)
            }

            val bottomText = regionText("bottom")
            if (bottomText.isNotBlank()) resultParts.add(bottomText)

            return resultParts.filter { it.isNotBlank() }.joinToString("\n\n").trim()
        }
    }

    private fun cleanRegionText(text: String): String {
        return text.replace('\r', '\n')
            .lineSequence()
            .map { it.trimEnd() }
            .dropWhile { it.isBlank() }
            .joinToString("\n")
            .trim()
    }

}
