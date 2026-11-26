package com.veritas.reader

import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import com.tom_roush.pdfbox.text.PDFTextStripperByArea
import java.io.IOException
import kotlin.math.abs

/** Extracts rectangular tables from drawn rules, including wrapped and empty cells. */
internal object PdfRuledTableExtractor {
    private const val EDGE_TOLERANCE = 1.5f
    private const val MIN_CELL_SIZE = 6f
    private const val MAX_RULES = 600
    private const val MAX_CELLS = 512

    internal data class Rule(val axis: Float, val start: Float, val end: Float)

    internal data class Rules(val horizontal: List<Rule>, val vertical: List<Rule>)

    private data class Grid(
        val x: List<Float>,
        val y: List<Float>,
        val startLine: Int,
        val endLine: Int
    ) {
        val cellCount get() = (x.size - 1) * (y.size - 1)
    }

    fun readRules(page: PDPage): Rules? {
        if (page.rotation != 0) return null
        return try {
            val collector = RuleCollector(page).apply { processPage(page) }
            if (collector.overflow) null else Rules(collector.horizontal, collector.vertical)
        } catch (_: IOException) {
            null
        }
    }

    fun extract(page: PDPage, probe: PdfPageProbe, rules: Rules? = readRules(page)): List<PdfTableDetector.TableRegion> {
        if (page.rotation != 0 || probe.lines.none { it.segments.size >= 2 }) return emptyList()
        if (rules == null) return emptyList()
        return try {
            extractGrids(page, probe, rules)
        } catch (_: IOException) {
            // Optional layout evidence: preserve text extraction if a drawing stream is malformed.
            emptyList()
        }
    }

    private fun extractGrids(page: PDPage, probe: PdfPageProbe, rules: Rules): List<PdfTableDetector.TableRegion> {
        val horizontal = mergeRules(rules.horizontal)
        val vertical = mergeRules(rules.vertical)
        val grids = mutableListOf<Grid>()
        var remainingCells = MAX_CELLS
        for (left in vertical.sortedBy { it.start }) {
            val sides = vertical.filter {
                it.axis >= left.axis && abs(it.start - left.start) <= EDGE_TOLERANCE &&
                    abs(it.end - left.end) <= EDGE_TOLERANCE
            }.map { it.axis }.sorted()
            if (sides.size < 3 || sides.zipWithNext().any { (a, b) -> b - a < MIN_CELL_SIZE }) continue
            val levels = horizontal.filter {
                it.axis >= left.start - EDGE_TOLERANCE && it.axis <= left.end + EDGE_TOLERANCE &&
                    it.start <= sides.first() + EDGE_TOLERANCE && it.end >= sides.last() - EDGE_TOLERANCE
            }.map { it.axis }.distinct().sorted()
            if (levels.size < 3 || abs(levels.first() - left.start) > EDGE_TOLERANCE ||
                abs(levels.last() - left.end) > EDGE_TOLERANCE ||
                levels.zipWithNext().any { (a, b) -> b - a < MIN_CELL_SIZE }
            ) continue

            val startLine = probe.lines.indexOfFirst { it.y >= levels.first() }
            if (startLine < 0) continue
            val endLine = probe.lines.indexOfFirst { it.y >= levels.last() }.let {
                if (it < 0) probe.lines.size else it
            }
            if (endLine - startLine < 2 || grids.any { startLine < it.endLine && it.startLine < endLine }) continue
            if (probe.lines.subList(startLine, endLine).any {
                    it.minX < sides.first() - EDGE_TOLERANCE || it.maxX > sides.last() + EDGE_TOLERANCE
                }
            ) continue
            val grid = Grid(sides, levels, startLine, endLine)
            if (grid.cellCount > remainingCells) continue
            grids.add(grid)
            remainingCells -= grid.cellCount
        }
        if (grids.isEmpty()) return emptyList()

        // A shared horizontal band cannot safely represent side-by-side prose and tables.
        val outside = probe.lines.filterIndexed { index, _ ->
            grids.none { index in it.startLine until it.endLine }
        }.flatMap { it.segments }
        val box = page.cropBox
        if (PdfColumnDetector.detect(outside, box.width, box.height) != null) return emptyList()

        val stripper = PDFTextStripperByArea().apply { sortByPosition = true }
        val cellKeys = grids.mapIndexed { gridIndex, grid ->
            grid.y.zipWithNext().mapIndexed { row, (top, bottom) ->
                grid.x.zipWithNext().mapIndexed { column, (left, right) ->
                    "${gridIndex}_${row}_$column".also { key ->
                        stripper.addRegion(key, RectF(left, top, right, bottom))
                    }
                }
            }
        }
        stripper.extractRegions(page)
        return grids.zip(cellKeys).mapNotNull { (grid, keys) ->
            val rows = keys.map { row -> row.map { stripper.getTextForRegion(it).trim() } }
            if (rows.count { row -> row.any { it.isNotBlank() } } < 2) return@mapNotNull null
            val original = probe.lines.subList(grid.startLine, grid.endLine).map { it.text }
            // Region clipping or unusual font positioning can drop glyphs at cell boundaries.
            if (letterCounts(original) != letterCounts(rows.flatten())) return@mapNotNull null
            PdfTableDetector.TableRegion(grid.startLine, grid.endLine, grid.x.zipWithNext(), rows)
        }.sortedBy { it.startLineIndex }
    }

    private fun letterCounts(text: List<String>): Map<Char, Int> =
        text.asSequence().flatMap { it.asSequence() }.filter { it.isLetterOrDigit() }.groupingBy { it }.eachCount()

    private fun mergeRules(rules: List<Rule>): List<Rule> {
        val byAxis = mutableListOf<MutableList<Rule>>()
        for (rule in rules.sortedBy { it.axis }) {
            val group = byAxis.lastOrNull()
            if (group != null && rule.axis - group.first().axis <= EDGE_TOLERANCE) group.add(rule)
            else byAxis.add(mutableListOf(rule))
        }
        return byAxis.flatMap { group ->
            val axis = group.map { it.axis.toDouble() }.average().toFloat()
            val merged = mutableListOf<Rule>()
            for (rule in group.sortedBy { it.start }) {
                val last = merged.lastOrNull()
                if (last != null && rule.start <= last.end + EDGE_TOLERANCE) {
                    merged[merged.lastIndex] = last.copy(end = maxOf(last.end, rule.end))
                } else {
                    merged.add(rule.copy(axis = axis))
                }
            }
            merged
        }
    }

    private class RuleCollector(page: PDPage) : PDFGraphicsStreamEngine(page) {
        private val box = page.cropBox
        val horizontal = mutableListOf<Rule>()
        val vertical = mutableListOf<Rule>()
        var overflow = false
            private set
        private var point = PointF()
        private var pathStart = point
        private val pending = mutableListOf<Pair<PointF, PointF>>()

        override fun moveTo(x: Float, y: Float) {
            point = PointF(x, y)
            pathStart = point
        }

        override fun lineTo(x: Float, y: Float) {
            val next = PointF(x, y)
            if (!overflow && pending.size < MAX_RULES) pending.add(point to next)
            else overflow = true
            point = next
        }

        override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
            point = PointF(x3, y3)
        }

        override fun appendRectangle(p0: PointF, p1: PointF, p2: PointF, p3: PointF) {
            moveTo(p0.x, p0.y)
            lineTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p3.x, p3.y)
            closePath()
        }

        override fun closePath() = lineTo(pathStart.x, pathStart.y)
        override fun getCurrentPoint(): PointF = point
        override fun endPath() = pending.clear()
        override fun clip(windingRule: Path.FillType) = Unit
        override fun drawImage(image: PDImage) = Unit
        override fun shadingFill(shadingName: COSName) = Unit
        override fun showText(string: ByteArray) = Unit

        override fun strokePath() {
            pending.forEach { (a, b) -> record(a, b) }
            endPath()
        }

        override fun fillPath(windingRule: Path.FillType) {
            // Some generators paint thin rectangles instead of stroking their rules.
            if (pending.size == 4) {
                val vertices = pending.map { it.first }
                val x0 = vertices.minOf { it.x }
                val x1 = vertices.maxOf { it.x }
                val y0 = vertices.minOf { it.y }
                val y1 = vertices.maxOf { it.y }
                if (y1 - y0 <= EDGE_TOLERANCE) {
                    val y = (y0 + y1) / 2
                    record(PointF(x0, y), PointF(x1, y))
                } else if (x1 - x0 <= EDGE_TOLERANCE) {
                    val x = (x0 + x1) / 2
                    record(PointF(x, y0), PointF(x, y1))
                }
            }
            endPath()
        }

        override fun fillAndStrokePath(windingRule: Path.FillType) = strokePath()

        private fun record(a: PointF, b: PointF) {
            val dx = abs(a.x - b.x)
            val dy = abs(a.y - b.y)
            if (horizontal.size + vertical.size >= MAX_RULES) {
                overflow = true
            } else if (dy <= 0.4f && dx >= MIN_CELL_SIZE) {
                horizontal.add(Rule(box.upperRightY - (a.y + b.y) / 2,
                    minOf(a.x, b.x) - box.lowerLeftX, maxOf(a.x, b.x) - box.lowerLeftX))
            } else if (dx <= 0.4f && dy >= MIN_CELL_SIZE) {
                vertical.add(Rule((a.x + b.x) / 2 - box.lowerLeftX,
                    box.upperRightY - maxOf(a.y, b.y), box.upperRightY - minOf(a.y, b.y)))
            }
        }
    }
}
