package com.veritas.reader

import android.graphics.RectF
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.PDFTextStripperByArea
import com.tom_roush.pdfbox.text.TextPosition
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class PositionedPdfLine(
    val text: String,
    val minX: Float,
    val maxX: Float,
    val y: Float,
    val height: Float,
    val segments: List<PositionedPdfSegment> = emptyList()
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
    val glyphCount: Int,
    val text: String = ""
) {
    val centerX: Float
        get() = (minX + maxX) / 2f

    val width: Float
        get() = maxX - minX
}

internal data class PdfPageProbe(
    val plainText: String,
    val lines: List<PositionedPdfLine>,
    val segments: List<PositionedPdfSegment>,
    val narrowColumns: PdfColumnLayout? = null,
    val narrowCrossingLines: Set<Int> = emptySet()
)

internal data class PdfColumnLayout(
    val splitX: Float,
    val columnTopY: Float,
    val columnBottomY: Float,
    val pageWidth: Float,
    val pageHeight: Float,
    val rowBreaks: List<Pair<Float, Float>> = emptyList()
)

internal class PdfLayoutProbeStripper(private val excluded: List<PdfCalloutBox> = emptyList()) : PDFTextStripper() {
    val positionedLines = mutableListOf<PositionedPdfLine>()
    val positionedSegments = mutableListOf<PositionedPdfSegment>()

    init {
        sortByPosition = true
        setShouldSeparateByBeads(false)
        lineSeparator = "\n"
    }

    private val glyphs = mutableListOf<TextPosition>()

    override fun processTextPosition(text: TextPosition) {
        if (excluded.none { it.contains(text) }) super.processTextPosition(text)
    }

    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        glyphs.addAll(textPositions)
        super.writeString(text, textPositions)
    }

    private fun buildGeometry(): List<List<TextPosition>> {
        // PDFTextStripper may call writeString once per WORD. A word-sized probe
        // cannot detect columns; rebuild complete visual rows from all glyphs.
        val rows = mutableListOf<MutableList<TextPosition>>()
        glyphs.sortedWith(compareBy<TextPosition> { it.y }.thenBy { it.x }).forEach { glyph ->
            val last = rows.lastOrNull()
            val tolerance = maxOf(2f, minOf(glyph.heightDir, last?.firstOrNull()?.heightDir ?: glyph.heightDir) * 0.45f)
            if (last != null && kotlin.math.abs(last.first().y - glyph.y) <= tolerance) last.add(glyph)
            else rows.add(mutableListOf(glyph))
        }
        val orderedRows = rows.map { row -> row.sortedBy { it.x } }
        orderedRows.forEach { ordered ->
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
            val lineSegments = mutableListOf<PositionedPdfSegment>()
            var start = 0
            fun addSegment(end: Int) {
                val group = ordered.subList(start, end)
                if (group.isNotEmpty()) {
                    val segText = buildString {
                        group.forEachIndexed { index, glyph ->
                            val previous = group.getOrNull(index - 1)
                            if (previous != null && glyph.x - (previous.x + previous.width) > maxOf(1f, glyph.widthOfSpace * 0.5f)) append(' ')
                            append(glyph.unicode)
                        }
                    }.trim()
                    val seg = PositionedPdfSegment(
                        group.minOf { it.x }, group.maxOf { it.x + it.width }, y, height,
                        group.sumOf { it.unicode.length },
                        segText
                    )
                    lineSegments.add(seg)
                    positionedSegments.add(seg)
                }
            }
            for (index in 1 until ordered.size) {
                val previous = ordered[index - 1]
                if (ordered[index].x - (previous.x + previous.width) > gapThreshold) {
                    addSegment(index)
                    start = index
                }
            }
            addSegment(ordered.size)
            positionedLines.add(PositionedPdfLine(rowText, ordered.minOf { it.x }, ordered.maxOf { it.x + it.width }, y, height, lineSegments))
        }
        return orderedRows
    }

    companion object {
        fun extract(document: PDDocument, pageNumber: Int, excluded: List<PdfCalloutBox> = emptyList()): PdfPageProbe {
            val stripper = PdfLayoutProbeStripper(excluded).apply {
                startPage = pageNumber
                endPage = pageNumber
            }
            val plainText = stripper.getText(document)
            val rows = stripper.buildGeometry()
            val page = document.getPage(pageNumber - 1)
            val box = page.cropBox
            val narrow = if (page.rotation == 0 && PdfColumnDetector.detect(
                    stripper.positionedSegments, box.width, box.height
                ) == null
            ) narrowColumns(rows, stripper.positionedLines, box.width, box.height) else null
            return PdfPageProbe(
                plainText = plainText,
                lines = stripper.positionedLines.toList(),
                segments = stripper.positionedSegments.toList(),
                narrowColumns = narrow?.first,
                narrowCrossingLines = narrow?.second.orEmpty()
            )
        }

        private fun narrowColumns(
            rows: List<List<TextPosition>>,
            lines: List<PositionedPdfLine>,
            width: Float,
            height: Float
        ): Pair<PdfColumnLayout, Set<Int>>? {
            // The usual segment gap can exceed the entire gutter of a tightly set page.
            // Look for a repeated right-column start and test the slit against the glyphs.
            if (rows.size < 12) return null
            val visible = rows.map { row -> row.filter { it.unicode.isNotBlank() && it.width > 0f } }
            val starts = mutableMapOf<Int, Int>()
            for (row in visible) {
                val startsOnRow = row.mapIndexedNotNull { index, glyph ->
                    if (glyph.x !in (width * .28f)..(width * .72f)) return@mapIndexedNotNull null
                    val previous = row.getOrNull(index - 1)
                    if (previous != null && glyph.x - (previous.x + previous.width) < 2.5f) {
                        return@mapIndexedNotNull null
                    }
                    glyph.x.roundToInt()
                }.distinct()
                for (x in startsOnRow) starts[x] = (starts[x] ?: 0) + 1
            }
            var best: Pair<PdfColumnLayout, Set<Int>>? = null
            var bestScore = 0
            val candidates = starts.entries.filter { it.value >= 8 }.sortedByDescending { it.value }.take(12)
            for ((anchor) in candidates) {
                val split = anchor - 2.5f
                val left = mutableListOf<Int>()
                val right = mutableListOf<Int>()
                val crossing = mutableSetOf<Int>()
                val leftOnly = mutableListOf<Int>()
                val rightOnly = mutableListOf<Int>()
                var alignedRight = 0
                var pairedAcrossGutter = 0
                for ((index, row) in visible.withIndex()) {
                    if (row.isEmpty()) continue
                    if (row.any { it.x < split + .6f && it.x + it.width > split - .6f }) {
                        crossing.add(index)
                        continue
                    }
                    val leftEnd = row.lastOrNull { it.x + it.width <= split }
                    val rightStart = row.firstOrNull { it.x >= split }
                    val hasLeft = leftEnd != null && leftEnd.x + leftEnd.width - row.first().x >= width * .2f
                    if (hasLeft) {
                        left.add(index)
                        if (row.last().x + row.last().width <= split) leftOnly.add(index)
                    }
                    if (rightStart != null && row.last().x + row.last().width - rightStart.x >= width * .2f) {
                        right.add(index)
                        if (row.first().x >= split) rightOnly.add(index)
                        if (abs(rightStart.x - anchor) <= 2f) {
                            alignedRight++
                            if (hasLeft) {
                                val gap = rightStart.x - (leftEnd.x + leftEnd.width)
                                val wordSpace = maxOf(leftEnd.widthOfSpace, rightStart.widthOfSpace)
                                if (gap >= maxOf(4.5f, wordSpace * 1.10f)) pairedAcrossGutter++
                            }
                        }
                    }
                }
                if (left.size < 8 || right.size < 8 || alignedRight < maxOf(8, right.size / 2)) continue
                val overlapStart = maxOf(lines[left.first()].y, lines[right.first()].y)
                val overlapEnd = minOf(lines[left.last()].y, lines[right.last()].y)
                if (overlapEnd - overlapStart < height * .2f) continue
                // Justified or repeated prose can align ordinary word spaces. Real columns
                // have wider gaps on shared rows, or separate baselines in the same band.
                val hasIndependentBaselines = leftOnly.count { lines[it].y in overlapStart..overlapEnd } >= 5 &&
                    rightOnly.count { lines[it].y in overlapStart..overlapEnd } >= 5
                if (!hasIndependentBaselines && pairedAcrossGutter < maxOf(6, alignedRight / 2)) continue
                val crossingsInBody = crossing.count { lines[it].y in overlapStart..overlapEnd }
                if (crossingsInBody > maxOf(3, (left.size + right.size) / 12)) continue
                val score = minOf(left.size, right.size) * 2 + alignedRight - crossingsInBody * 3
                if (score <= bestScore) continue
                val first = lines[minOf(left.first(), right.first())]
                val last = lines[maxOf(left.last(), right.last())]
                // Keep nearby short column lines and let the heading-band logic place
                // true spanning lines between the columns.
                val top = (first.y - first.height).coerceAtLeast(0f)
                val bottom = (last.y + last.height * 2).coerceAtMost(height)
                best = PdfColumnLayout(split, top, bottom, width, height) to crossing
                bestScore = score
            }
            return best
        }
    }
}

internal object PdfPageTextExtractor {
    fun extractPage(document: PDDocument, pageNumber: Int): String {
        val page = document.getPage((pageNumber - 1).coerceAtLeast(0))
        val originalProbe = PdfLayoutProbeStripper.extract(document, pageNumber)

        val box = page.cropBox ?: page.mediaBox
        val sideways = ((page.rotation % 360) + 360) % 360 in listOf(90, 270)
        val pageWidth = if (box != null) (if (sideways) box.height else box.width).coerceAtLeast(1f) else 600f
        val pageHeight = if (box != null) (if (sideways) box.width else box.height).coerceAtLeast(1f) else 800f

        val rules = if (originalProbe.lines.isEmpty()) null else PdfRuledTableExtractor.readRules(page)
        val detected = rules?.let { PdfCalloutExtractor.detect(originalProbe, it, pageWidth, pageHeight) }.orEmpty()
        val calloutText = if (detected.isEmpty()) null else PdfCalloutExtractor.read(page, detected)
        val bodyProbe = calloutText?.let { texts ->
            PdfLayoutProbeStripper.extract(document, pageNumber, detected).takeIf {
                letterCounts(it.plainText + texts.joinToString("")) == letterCounts(originalProbe.plainText)
            }
        }
        val callouts = if (bodyProbe == null) emptyList() else detected
        val probe = bodyProbe ?: originalProbe
        val underlines = rules?.horizontal.orEmpty()
        val ruledTables = PdfRuledTableExtractor.extract(page, probe, rules)
        val layout = detectColumns(probe, page)
        val tableFormattedText = PdfTableDetector.detectAndFormat(probe.lines, pageWidth, pageHeight, ruledTables) { table ->
            layout != null && isColumnProseRegion(probe, layout, table)
        }
        val body = if (!tableFormattedText.isNullOrBlank()) tableFormattedText else if (layout == null) {
            if (underlines.isEmpty() && callouts.isEmpty()) probe.plainText else {
                val stripper = PdfProseStripper(underlines, callouts)
                stripper.addRegion("body", RectF(0f, 0f, pageWidth, pageHeight))
                stripper.extractRegions(page)
                cleanRegionText(stripper.getTextForRegion("body")).ifBlank { probe.plainText }
            }
        } else {
            extractColumnPage(page, layout, underlines, callouts).ifBlank { probe.plainText }
        }
        if (callouts.isEmpty()) return body
        val passages = callouts.zip(requireNotNull(calloutText)).map { (callout, text) ->
            val anchor = probe.lines.lastOrNull { it.y in callout.top..callout.bottom }?.text.orEmpty()
            anchor to cleanRegionText(text)
        }
        return PdfCalloutExtractor.place(body, passages)
    }

    private fun letterCounts(text: String) = text.asSequence().filter { it.isLetterOrDigit() }.groupingBy { it }.eachCount()

    private fun isColumnProseRegion(
        probe: PdfPageProbe,
        layout: PdfColumnLayout,
        table: PdfTableDetector.TableRegion
    ): Boolean {
        if (table.columns.size != 2 || abs(table.columns.first().second - layout.splitX) > layout.pageWidth * .035f) {
            return false
        }
        val lines = probe.lines.subList(table.startLineIndex, table.endLineIndexExclusive)
        if (lines.first().y < layout.columnTopY || lines.last().y > layout.columnBottomY) return false
        if (probe.narrowColumns != null) {
            return lines.last().y - lines.first().y >= layout.pageHeight * .16f
        }
        // Only discard heuristic rows that coincide with a sustained two-column prose band.
        val segments = lines.flatMap { it.segments }
        val body = segments.filter {
            it.width >= layout.pageWidth * 0.26f && it.glyphCount >= 28
        }
        return body.count { it.maxX < layout.splitX } >= 8 &&
            body.count { it.minX > layout.splitX } >= 8
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
        val finalBase = probe.narrowColumns ?: PdfColumnDetector.detect(probe.segments, pageWidth, pageHeight) ?: return null
        // Detect horizontal breaks / chapter dividers spanning across the page within the column area
        val breakLines = probe.lines.filterIndexed { index, line ->
            if (line.text.length < 2 || line.width <= 8f) return@filterIndexed false
            val isHeader = Regex("""^(CHAPTER|Chapter|PROLOGUE|Prologue|EPILOGUE|Epilogue|INTRODUCTION|Introduction|PART|Part|BOOK|Book|SECTION|Section)\b.*""", RegexOption.IGNORE_CASE).containsMatchIn(line.text)
            val crossesGutter = if (probe.narrowColumns != null) index in probe.narrowCrossingLines else probe.segments.any { segment ->
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

    private fun extractColumnPage(page: PDPage, layout: PdfColumnLayout, underlines: List<PdfRuledTableExtractor.Rule>,
        callouts: List<PdfCalloutBox>): String {
        val stripper = PdfProseStripper(underlines, callouts)

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
        return text.replace("\r\n", "\n").replace('\r', '\n')
            .lineSequence()
            .map { line ->
                val joined = line.trimEnd().replace(adjacentUnderlines, "$1")
                    .replace(underlineEndSpace, "").replace(pdfBulletStart, "$1•")
                val content = joined.trimStart()
                val label = content.removePrefix("<u>").removeSuffix("</u>")
                if (content.startsWith("<u>") && content.endsWith("</u>") && label.length <= 105 &&
                    label.any { it.isUpperCase() } && label.none { it.isLowerCase() }
                ) "## $content"
                else if (paragraphLabel.containsMatchIn(content)) "\n$joined"
                else joined.replace(compactListStart, "$1 ")
            }
            .dropWhile { it.isBlank() }
            .joinToString("\n")
            .trim()
    }

    private val adjacentUnderlines = Regex("</u>([ \\t]*)<u>")
    private val underlineEndSpace = Regex("[ \\t]+(?=</u>$)")
    private val pdfBulletStart = Regex("^( *)(?:●|‣|▪)(?=\\s|\\p{L})")
    private val compactListStart = Regex("^( *[•]| *[0-9]{1,3}[.)])(?=\\p{Lu})")
    private val paragraphLabel = Regex("""^\p{Lu}[\p{Lu}\p{M} ’'–-]{2,58}:\s""")

    private class PdfProseStripper(
        private val rules: List<PdfRuledTableExtractor.Rule>,
        private val excluded: List<PdfCalloutBox>
    ) : PDFTextStripperByArea() {
        private var regionWriter: java.io.Writer? = null
        private var leftMargin = 0f
        private var rightMargin = 0f
        private var pitch = 12f
        private var lineBottom = Float.NaN
        private var lineRight = 0f
        private var newLine = true

        init {
            sortByPosition = true
            lineSeparator = "\n"
        }

        override fun processTextPosition(text: TextPosition) {
            if (excluded.none { it.contains(text) }) super.processTextPosition(text)
        }

        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            val glyphs = textPositions.filter { it.unicode.isNotBlank() }
            if (glyphs.isEmpty()) {
                super.writeString(text, textPositions)
                return
            }
            val left = glyphs.minOf { it.x }
            if (regionWriter !== output) {
                regionWriter = output
                val region = charactersByArticle.asSequence().flatten()
                    .filter { it.unicode.isNotBlank() }.toList()
                leftMargin = region.minOfOrNull { it.x } ?: left
                rightMargin = region.maxOfOrNull { it.x + it.width } ?: left
                val baselines = mutableListOf<Float>()
                for (y in region.map { it.y }.sorted()) {
                    if (baselines.lastOrNull()?.let { y - it >= 3f } != false) baselines.add(y)
                }
                val distances = baselines.zipWithNext { a, b -> b - a }.sorted()
                pitch = distances.getOrNull(distances.size / 2)?.coerceAtLeast(7f) ?: 12f
                lineBottom = Float.NaN
                newLine = true
            }
            val right = glyphs.maxOf { it.x + it.width }
            val baseline = glyphs.first().y
            if (newLine) {
                val indent = left - leftMargin
                val previousWasShort = lineRight < rightMargin - maxOf(15f, (rightMargin - leftMargin) * .1f)
                val newParagraph = lineBottom.isNaN() || baseline - lineBottom > pitch * 1.43f ||
                    (indent in 7f..36f && previousWasShort)
                if (!lineBottom.isNaN()) output.write(if (newParagraph) "\n\n" else "\n")
                if (newParagraph && indent in 7f..36f) output.write(" ".repeat((indent / 4f).roundToInt()))
                lineBottom = baseline
                lineRight = right
                newLine = false
            } else {
                lineRight = maxOf(lineRight, right)
            }
            val underline = rules.any { rule ->
                rule.axis - baseline in -.4f..3f && rule.start <= left + .8f && rule.end >= right - .8f
            }
            super.writeString(if (underline) "<u>$text</u>" else text, textPositions)
        }

        override fun writeLineSeparator() { newLine = true }
    }
}
