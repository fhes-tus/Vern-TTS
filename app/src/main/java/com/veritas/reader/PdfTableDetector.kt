package com.veritas.reader

import kotlin.math.abs

internal object PdfTableDetector {

    data class TableRegion(
        val startLineIndex: Int,
        val endLineIndexExclusive: Int,
        val columns: List<Pair<Float, Float>>, // minX to maxX of each column
        val rows: List<List<String>>
    )

    /**
     * Inspects positioned visual lines on a PDF page. If one or more tables
     * (multi-column structured blocks like in Image 3) are found, extracts them as
     * standard markdown tables and returns the reconstructed page text.
     * Returns null if no table is present on the page.
     */
    fun detectAndFormat(
        lines: List<PositionedPdfLine>,
        pageWidth: Float,
        pageHeight: Float,
        ruledTables: List<TableRegion> = emptyList(),
        isColumnProse: (TableRegion) -> Boolean = { false }
    ): String? {
        if (lines.size < 3 || pageWidth <= 10f || pageHeight <= 10f) return null

        val tables = if (ruledTables.isEmpty()) {
            findTableRegions(lines, pageWidth, pageHeight)
        } else {
            val regions = mutableListOf<TableRegion>()
            var start = 0
            for (table in ruledTables) {
                regions.addAll(findTableRegions(lines.subList(start, table.startLineIndex), pageWidth, pageHeight)
                    .map { it.copy(startLineIndex = it.startLineIndex + start, endLineIndexExclusive = it.endLineIndexExclusive + start) })
                regions.add(table)
                start = table.endLineIndexExclusive
            }
            regions.addAll(findTableRegions(lines.subList(start, lines.size), pageWidth, pageHeight)
                .map { it.copy(startLineIndex = it.startLineIndex + start, endLineIndexExclusive = it.endLineIndexExclusive + start) })
            regions
        }
        val confirmedTables = tables.filter { table -> table in ruledTables || !isColumnProse(table) }
        if (confirmedTables.isEmpty()) return null

        val output = StringBuilder()
        var currentLineIndex = 0

        for (table in confirmedTables) {
            // Emit preceding prose lines
            if (table.startLineIndex > currentLineIndex) {
                val prose = formatProseLines(lines.subList(currentLineIndex, table.startLineIndex))
                if (prose.isNotBlank()) {
                    if (output.isNotBlank()) output.append("\n\n")
                    output.append(prose)
                }
            }

            // Emit the markdown table
            val tableMarkdown = formatTableMarkdown(table.rows)
            if (tableMarkdown.isNotBlank()) {
                if (output.isNotBlank()) output.append("\n\n")
                output.append(tableMarkdown)
            }

            currentLineIndex = table.endLineIndexExclusive
        }

        // Emit remaining prose lines after last table
        if (currentLineIndex < lines.size) {
            val prose = formatProseLines(lines.subList(currentLineIndex, lines.size))
            if (prose.isNotBlank()) {
                if (output.isNotBlank()) output.append("\n\n")
                output.append(prose)
            }
        }

        return output.toString().trim().ifBlank { null }
    }

    internal fun findTableRegions(
        lines: List<PositionedPdfLine>,
        pageWidth: Float,
        pageHeight: Float
    ): List<TableRegion> {
        val regions = mutableListOf<TableRegion>()
        var idx = 0

        while (idx < lines.size) {
            // Look for start of a multi-column block
            val startIdx = findTableStart(lines, idx, pageWidth)
            if (startIdx == -1) break

            val endIdx = findTableEnd(lines, startIdx, pageWidth)
            val candidateLines = lines.subList(startIdx, endIdx)

            val table = analyzeTableCandidate(candidateLines, startIdx, endIdx, pageWidth)
            if (table != null && table.rows.size >= 2 && table.columns.size >= 2) {
                regions.add(table)
                idx = endIdx
            } else {
                idx = startIdx + 1
            }
        }

        return regions
    }

    private fun isProseParagraph(line: PositionedPdfLine, pageWidth: Float): Boolean {
        // A single wide line spanning across most of the page is running prose, not a table line
        if (line.segments.size <= 1 && line.width >= pageWidth * 0.65f) return true
        val words = line.text.split(Regex("""\s+""")).filter { it.isNotBlank() }
        return line.segments.size <= 1 && words.size >= 9 && line.width >= pageWidth * 0.50f
    }

    private fun findTableStart(lines: List<PositionedPdfLine>, fromIndex: Int, pageWidth: Float): Int {
        for (i in fromIndex until lines.size) {
            val line = lines[i]
            if (isProseParagraph(line, pageWidth)) continue
            if (line.segments.size >= 2) {
                // Check if this or next few lines continue multi-segment layout
                val window = lines.subList(i, minOf(lines.size, i + 3))
                val multiCount = window.count { it.segments.size >= 2 }
                if (multiCount >= 2 || (multiCount >= 1 && window.any { it.segments.size >= 3 })) {
                    return i
                }
            }
        }
        return -1
    }

    private fun findTableEnd(lines: List<PositionedPdfLine>, startIndex: Int, pageWidth: Float): Int {
        var lastTableLine = startIndex

        for (i in startIndex until lines.size) {
            val line = lines[i]
            if (isProseParagraph(line, pageWidth)) {
                return i
            }
            // An all-caps header or section label with 1 segment marks the end of the table
            if (i > startIndex && line.segments.size <= 1) {
                val trimmed = line.text.trim()
                val isHeading = trimmed.length in 2..40 && (trimmed.all { it.isUpperCase() || it.isWhitespace() || it == ':' } || trimmed.startsWith("#"))
                if (isHeading) {
                    return i
                }
            }
            if (line.segments.size >= 2) {
                lastTableLine = i
            }
        }
        return lastTableLine + 1
    }

    private fun analyzeTableCandidate(
        candidateLines: List<PositionedPdfLine>,
        startIdx: Int,
        endIdx: Int,
        pageWidth: Float
    ): TableRegion? {
        if (candidateLines.size < 2) return null

        val multiSegLines = candidateLines.filter { it.segments.size >= 2 }
        if (multiSegLines.isEmpty()) return null

        // Collect all segments in the candidate block
        val allSegments = candidateLines.flatMap { it.segments }
            .filter { it.width > 2f && it.text.isNotBlank() }
        if (allSegments.size < 4) return null

        // Detect column boundaries by finding vertical gutters across candidate lines
        val contentMinX = (allSegments.minOfOrNull { it.minX } ?: 0f).coerceAtLeast(0f)
        val contentMaxX = (allSegments.maxOfOrNull { it.maxX } ?: pageWidth).coerceAtMost(pageWidth)
        val contentWidth = contentMaxX - contentMinX
        if (contentWidth < pageWidth * 0.30f) return null

        // Collect distinct X edges to test for column gutters
        val edges = allSegments.flatMap { listOf(it.minX, it.maxX) }.distinct().sorted()
        val candidateGutters = mutableListOf<Pair<Float, Float>>()

        // Find gaps between segments that appear across multiple lines
        for (i in 0 until edges.size - 1) {
            val left = edges[i]
            val right = edges[i + 1]
            val gapWidth = right - left
            if (gapWidth >= maxOf(6f, pageWidth * 0.008f) && left >= contentMinX + 15f && right <= contentMaxX - 15f) {
                // Check if any segment crosses this gap
                val crossing = allSegments.count { it.minX < right - 2f && it.maxX > left + 2f }
                if (crossing <= maxOf(1, candidateLines.size / 8)) {
                    val hasLeft = allSegments.any { it.maxX <= left + 2f }
                    val hasRight = allSegments.any { it.minX >= right - 2f }
                    if (hasLeft && hasRight) {
                        candidateGutters.add(Pair(left, right))
                    }
                }
            }
        }

        if (candidateGutters.isEmpty()) return null

        // Merge overlapping or adjacent candidate gutters
        val mergedGutters = mutableListOf<Pair<Float, Float>>()
        var curGutter = candidateGutters.first()
        for (next in candidateGutters.drop(1)) {
            if (next.first <= curGutter.second + 4f) {
                curGutter = Pair(curGutter.first, maxOf(curGutter.second, next.second))
            } else {
                mergedGutters.add(curGutter)
                curGutter = next
            }
        }
        mergedGutters.add(curGutter)

        // Filter gutters to only significant column dividers
        val significantGutters = mergedGutters.filter { (gLeft, gRight) ->
            val leftCount = allSegments.count { it.maxX <= gLeft + 4f }
            val rightCount = allSegments.count { it.minX >= gRight - 4f }
            leftCount >= 2 && rightCount >= 2
        }

        if (significantGutters.isEmpty()) return null

        // Build column intervals
        val columns = mutableListOf<Pair<Float, Float>>()
        var colStart = contentMinX
        for (gutter in significantGutters) {
            val colEnd = (gutter.first + gutter.second) / 2f
            if (colEnd > colStart + 10f) {
                columns.add(Pair(colStart, colEnd))
                colStart = colEnd
            }
        }
        if (contentMaxX > colStart + 10f) {
            columns.add(Pair(colStart, contentMaxX))
        }

        if (columns.size < 2) return null

        // For a 2-column candidate, verify it's a table and not running 2-column novel prose:
        if (columns.size == 2) {
            val leftCol = columns[0]
            val rightCol = columns[1]
            val pairedCount = candidateLines.count { line ->
                val hasL = line.segments.any { (it.minX + it.maxX) / 2f in leftCol.first..leftCol.second }
                val hasR = line.segments.any { (it.minX + it.maxX) / 2f in rightCol.first..rightCol.second }
                hasL && hasR
            }
            if (pairedCount < maxOf(2, candidateLines.size / 3)) {
                return null // Running prose, let standard column detector handle it
            }
        }

        // Group candidate lines into logical rows
        val pitches = candidateLines.zipWithNext().map { (a, b) -> b.y - a.y }.filter { it > 4f }
        val intraPitch = pitches.filter { it in 6f..18f }.sorted().let {
            if (it.isNotEmpty()) it[it.size / 2]
            else pitches.sorted().firstOrNull() ?: 12f
        }
        val rowBreakGap = maxOf(16f, intraPitch * 1.40f)

        val tableRows = mutableListOf<List<String>>()
        var currentRowCells = Array(columns.size) { mutableListOf<String>() }

        fun flushRow() {
            val rowStrings = currentRowCells.map { parts ->
                parts.joinToString(" ").replace(Regex("""\s+"""), " ").trim()
            }
            if (rowStrings.any { it.isNotBlank() }) {
                tableRows.add(rowStrings)
            }
            currentRowCells = Array(columns.size) { mutableListOf<String>() }
        }

        candidateLines.forEachIndexed { lineIdx, line ->
            val prevLine = candidateLines.getOrNull(lineIdx - 1)
            val deltaY = if (prevLine != null) line.y - prevLine.y else 0f

            // A row break occurs if there is vertical gap or if a primary column restarts
            val isGapBreak = deltaY >= rowBreakGap
            val col0AlreadyFilled = currentRowCells[0].isNotEmpty()
            val col0HasNewText = line.segments.any { seg ->
                findColumnIndex(seg.centerX, columns) == 0 && seg.text.isNotBlank()
            }
            val isColumnRestartBreak = col0AlreadyFilled && col0HasNewText && deltaY >= intraPitch * 1.20f

            if (lineIdx > 0 && (isGapBreak || isColumnRestartBreak)) {
                flushRow()
            }

            // Assign segments on this line to respective columns
            line.segments.forEach { seg ->
                val colIdx = findColumnIndex(seg.centerX, columns)
                if (colIdx in 0 until columns.size && seg.text.isNotBlank()) {
                    currentRowCells[colIdx].add(seg.text)
                }
            }
        }
        flushRow()

        if (tableRows.size < 2) return null

        return TableRegion(
            startLineIndex = startIdx,
            endLineIndexExclusive = endIdx,
            columns = columns,
            rows = tableRows
        )
    }

    private fun findColumnIndex(x: Float, columns: List<Pair<Float, Float>>): Int {
        for (i in columns.indices) {
            val (minX, maxX) = columns[i]
            if (x in minX..maxX) return i
        }
        // Fallback: find closest column by center distance
        return columns.indices.minByOrNull { i ->
            val (minX, maxX) = columns[i]
            abs(x - (minX + maxX) / 2f)
        } ?: 0
    }

    internal fun formatTableMarkdown(rows: List<List<String>>): String {
        if (rows.isEmpty()) return ""
        val numCols = (rows.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)

        fun sanitizeCell(cell: String): String =
            cell.replace("|", "/").replace("\n", " ").trim()

        val headerRow = rows.first()
        val dataRows = rows.drop(1)

        val sb = StringBuilder()

        // Header Row
        sb.append("| ")
        for (c in 0 until numCols) {
            val text = sanitizeCell(headerRow.getOrNull(c).orEmpty())
            sb.append(text.ifBlank { " " })
            sb.append(" | ")
        }
        sb.append("\n")

        // Separator Row
        sb.append("| ")
        for (c in 0 until numCols) {
            sb.append("--- | ")
        }
        sb.append("\n")

        // Data Rows
        dataRows.forEach { row ->
            sb.append("| ")
            for (c in 0 until numCols) {
                val text = sanitizeCell(row.getOrNull(c).orEmpty())
                sb.append(text)
                sb.append(" | ")
            }
            sb.append("\n")
        }

        return sb.toString().trim()
    }

    private fun formatProseLines(lines: List<PositionedPdfLine>): String {
        if (lines.isEmpty()) return ""
        val sb = StringBuilder()
        lines.forEachIndexed { i, line ->
            val prev = lines.getOrNull(i - 1)
            if (prev != null) {
                val deltaY = line.y - prev.y
                val avgHeight = (line.height + prev.height) / 2f
                if (deltaY >= maxOf(20f, avgHeight * 1.8f)) {
                    sb.append("\n\n")
                } else {
                    sb.append("\n")
                }
            }
            sb.append(line.text.trim())
        }
        return sb.toString().trim()
    }
}
