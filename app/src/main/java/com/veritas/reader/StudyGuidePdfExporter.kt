package com.veritas.reader

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import androidx.core.graphics.toColorInt
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StudyGuidePdfExporter {
    private const val TAG = "StudyGuidePdfExporter"
    private const val PAGE_WIDTH = 595 // A4 standard pt at 72 dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - (MARGIN * 2)

    fun generateStudyGuidePdf(
        context: Context,
        documentTitle: String,
        documentId: String,
        annotations: List<ReaderAnnotation>,
        generalNote: String,
        sentences: List<ReaderSentence>
    ): File? {
        try {
            val pdfDocument = PdfDocument()
            val dateFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
            val dateString = dateFormat.format(Date())

            val titlePaint = Paint().apply {
                color = "#1C1B1F".toColorInt()
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val subtitlePaint = Paint().apply {
                color = "#49454F".toColorInt()
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val sectionPaint = Paint().apply {
                color = "#6750A4".toColorInt()
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val bodyPaint = Paint().apply {
                color = "#1C1B1F".toColorInt()
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val quotePaint = Paint().apply {
                color = "#313033".toColorInt()
                textSize = 9.5f
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                isAntiAlias = true
            }

            val tagPaint = Paint().apply {
                color = "#79747E".toColorInt()
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val linePaint = Paint().apply {
                color = "#E7E0EC".toColorInt()
                strokeWidth = 1f
                isAntiAlias = true
            }

            val highlightBarPaint = Paint().apply {
                isAntiAlias = true
            }

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var currentPage = pdfDocument.startPage(pageInfo)
            var canvas: Canvas = currentPage.canvas
            var y = MARGIN

            fun checkNewPage(neededHeight: Float) {
                if (y + neededHeight > PAGE_HEIGHT - MARGIN) {
                    val footerText = "Vern Study Guide • Page $pageNumber"
                    canvas.drawText(footerText, MARGIN, PAGE_HEIGHT - 20f, tagPaint)
                    pdfDocument.finishPage(currentPage)

                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    currentPage = pdfDocument.startPage(pageInfo)
                    canvas = currentPage.canvas
                    y = MARGIN
                }
            }

            // --- Header ---
            canvas.drawText("VERN STUDY GUIDE", MARGIN, y + 10f, tagPaint)
            y += 24f
            canvas.drawText(documentTitle, MARGIN, y + 10f, titlePaint)
            y += 26f
            canvas.drawText("Generated on $dateString • ${annotations.size} Highlights & Bookmarks", MARGIN, y, subtitlePaint)
            y += 14f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 20f

            // --- Section 1: General Booknote ---
            if (generalNote.isNotBlank()) {
                checkNewPage(40f)
                canvas.drawText("📝 Document Study Note", MARGIN, y, sectionPaint)
                y += 16f

                val noteLines = wrapText(generalNote, CONTENT_WIDTH - 16f, bodyPaint)
                checkNewPage((noteLines.size * 14f) + 30f)

                val boxTop = y
                val boxHeight = (noteLines.size * 14f) + 14f
                val bgPaint = Paint().apply { color = "#F7F2FA".toColorInt(); isAntiAlias = true }
                canvas.drawRoundRect(MARGIN, boxTop, PAGE_WIDTH - MARGIN, boxTop + boxHeight, 6f, 6f, bgPaint)

                var lineY = boxTop + 18f
                noteLines.forEach { line ->
                    canvas.drawText(line, MARGIN + 10f, lineY, bodyPaint)
                    lineY += 14f
                }
                y = boxTop + boxHeight + 18f
            }

            // --- Section 2: Sentence Notes ---
            val sentenceNotes = annotations.filter { it.type == AnnotationType.NOTE }
            if (sentenceNotes.isNotEmpty()) {
                checkNewPage(40f)
                canvas.drawText("📌 Sentence Notes", MARGIN, y, sectionPaint)
                y += 18f

                sentenceNotes.forEach { note ->
                    val sentenceText = sentences.getOrNull(note.chunkIndex)?.text?.trim().orEmpty()
                    val quoteLines = if (sentenceText.isNotBlank()) wrapText("“$sentenceText”", CONTENT_WIDTH - 20f, quotePaint) else emptyList()
                    val noteLines = if (note.note.isNotBlank()) wrapText(note.note, CONTENT_WIDTH - 20f, bodyPaint) else emptyList()
                    val audioLabel = if (note.audioPath != null) " • 🎙️ Audio Memo (${note.audioDurationSeconds}s)" else ""
                    val totalH = (quoteLines.size * 13f) + (noteLines.size * 13f) + 26f
                    checkNewPage(totalH)

                    val pageNum = sentences.getOrNull(note.chunkIndex)?.pageNumber ?: 1
                    canvas.drawText("Sentence #${note.chunkIndex + 1} • Page $pageNum$audioLabel", MARGIN + 10f, y + 10f, tagPaint)
                    var textY = y + 24f
                    quoteLines.forEach { line ->
                        canvas.drawText(line, MARGIN + 10f, textY, quotePaint)
                        textY += 13f
                    }
                    noteLines.forEach { line ->
                        canvas.drawText(line, MARGIN + 10f, textY, bodyPaint)
                        textY += 13f
                    }
                    y = textY + 12f
                }
            }

            // --- Section 3: Key Highlights ---
            val highlights = annotations.filter { it.type == AnnotationType.HIGHLIGHT }
            if (highlights.isNotEmpty()) {
                checkNewPage(40f)
                canvas.drawText("✨ Key Highlights", MARGIN, y, sectionPaint)
                y += 18f

                highlights.forEach { hl ->
                    val sentenceText = sentences.getOrNull(hl.chunkIndex)?.text?.trim().orEmpty()
                    if (sentenceText.isNotBlank()) {
                        val quoteLines = wrapText("“$sentenceText”", CONTENT_WIDTH - 20f, quotePaint)
                        val noteLines = if (hl.note.isNotBlank()) wrapText("Note: ${hl.note}", CONTENT_WIDTH - 20f, bodyPaint) else emptyList()
                        val totalH = (quoteLines.size * 13f) + (noteLines.size * 13f) + 24f
                        checkNewPage(totalH)

                        val barColor = when (hl.highlightColor?.lowercase(Locale.getDefault())) {
                            "yellow", "#fff59d" -> "#FBC02D".toColorInt()
                            "green", "#c8e6c9" -> "#4CAF50".toColorInt()
                            "blue", "#bbdefb" -> "#2196F3".toColorInt()
                            "purple", "#e1bee7" -> "#9C27B0".toColorInt()
                            "orange", "#ffe0b2" -> "#FF9800".toColorInt()
                            else -> "#6750A4".toColorInt()
                        }
                        highlightBarPaint.color = barColor

                        val itemTop = y
                        canvas.drawRect(MARGIN, itemTop, MARGIN + 4f, itemTop + totalH - 8f, highlightBarPaint)

                        val pageNum = sentences.getOrNull(hl.chunkIndex)?.pageNumber ?: 1
                        canvas.drawText("Sentence #${hl.chunkIndex + 1} • Page $pageNum", MARGIN + 12f, itemTop + 10f, tagPaint)
                        var textY = itemTop + 24f
                        quoteLines.forEach { line ->
                            canvas.drawText(line, MARGIN + 12f, textY, quotePaint)
                            textY += 13f
                        }
                        noteLines.forEach { line ->
                            canvas.drawText(line, MARGIN + 12f, textY, bodyPaint)
                            textY += 13f
                        }
                        y = itemTop + totalH + 6f
                    }
                }
            }

            // --- Section 4: Bookmarks ---
            val bookmarks = annotations.filter { it.type == AnnotationType.BOOKMARK }
            if (bookmarks.isNotEmpty()) {
                checkNewPage(40f)
                canvas.drawText("🔖 Bookmarks & Audio Memos", MARGIN, y, sectionPaint)
                y += 18f

                bookmarks.forEach { bm ->
                    val sentenceText = sentences.getOrNull(bm.chunkIndex)?.text?.trim().orEmpty()
                    val quoteLines = if (sentenceText.isNotBlank()) wrapText("“$sentenceText”", CONTENT_WIDTH - 20f, quotePaint) else emptyList()
                    val noteLines = if (bm.note.isNotBlank()) wrapText("Note: ${bm.note}", CONTENT_WIDTH - 20f, bodyPaint) else emptyList()
                    val hasAudio = bm.audioPath != null
                    val audioLabel = if (hasAudio) " • 🎙️ Voice Memo (${bm.audioDurationSeconds}s)" else ""
                    val totalH = (quoteLines.size * 13f) + (noteLines.size * 13f) + 24f
                    checkNewPage(totalH)

                    val pageNum = sentences.getOrNull(bm.chunkIndex)?.pageNumber ?: 1
                    canvas.drawText("Bookmark at Sentence #${bm.chunkIndex + 1} • Page $pageNum$audioLabel", MARGIN + 12f, y + 10f, tagPaint)
                    var textY = y + 24f
                    quoteLines.forEach { line ->
                        canvas.drawText(line, MARGIN + 12f, textY, quotePaint)
                        textY += 13f
                    }
                    noteLines.forEach { line ->
                        canvas.drawText(line, MARGIN + 12f, textY, bodyPaint)
                        textY += 13f
                    }
                    y = textY + 10f
                }
            }

            // Draw final footer
            canvas.drawText("Vern Study Guide • Page $pageNumber", MARGIN, PAGE_HEIGHT - 20f, tagPaint)
            pdfDocument.finishPage(currentPage)

            val dir = File(context.cacheDir, "study_guides").apply { if (!exists()) mkdirs() }
            val sanitized = documentTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(40)
            val outFile = File(dir, "Study_Guide_${sanitized}_${System.currentTimeMillis()}.pdf")
            FileOutputStream(outFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()

            Log.d(TAG, "Successfully generated Study Guide PDF: ${outFile.absolutePath}")
            return outFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate Study Guide PDF", e)
            return null
        }
    }

    fun generateVocabularyBatchPdf(
        context: Context,
        documentTitle: String,
        entries: List<VocabularyEntry>
    ): File? {
        try {
            val pdfDocument = PdfDocument()
            val dateFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
            val dateString = dateFormat.format(Date())

            val titlePaint = Paint().apply {
                color = "#1C1B1F".toColorInt()
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val subtitlePaint = Paint().apply {
                color = "#49454F".toColorInt()
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val wordPaint = Paint().apply {
                color = "#1C1B1F".toColorInt()
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val pronPaint = Paint().apply {
                color = "#6750A4".toColorInt()
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                isAntiAlias = true
            }

            val bodyPaint = Paint().apply {
                color = "#313033".toColorInt()
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val quotePaint = Paint().apply {
                color = "#49454F".toColorInt()
                textSize = 9.5f
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                isAntiAlias = true
            }

            val tagPaint = Paint().apply {
                color = "#79747E".toColorInt()
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val linePaint = Paint().apply {
                color = "#E7E0EC".toColorInt()
                strokeWidth = 1f
                isAntiAlias = true
            }

            val quoteBgPaint = Paint().apply {
                color = "#F7F2FA".toColorInt()
                isAntiAlias = true
            }

            val accentBarPaint = Paint().apply {
                color = "#6750A4".toColorInt()
                isAntiAlias = true
            }

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var currentPage = pdfDocument.startPage(pageInfo)
            var canvas: Canvas = currentPage.canvas
            var y = MARGIN

            fun checkNewPage(neededHeight: Float) {
                if (y + neededHeight > PAGE_HEIGHT - MARGIN) {
                    val footerText = "Vern Vocabulary Guide • Page $pageNumber"
                    canvas.drawText(footerText, MARGIN, PAGE_HEIGHT - 20f, tagPaint)
                    pdfDocument.finishPage(currentPage)

                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    currentPage = pdfDocument.startPage(pageInfo)
                    canvas = currentPage.canvas
                    y = MARGIN
                }
            }

            // Header
            canvas.drawText("VERN VOCABULARY GUIDE", MARGIN, y + 10f, tagPaint)
            y += 24f
            canvas.drawText(documentTitle, MARGIN, y + 10f, titlePaint)
            y += 26f
            canvas.drawText("Generated on $dateString • ${entries.size} Vocabulary Word${if (entries.size == 1) "" else "s"}", MARGIN, y, subtitlePaint)
            y += 14f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 20f

            entries.forEachIndexed { index, entry ->
                val wordText = "${index + 1}. ${entry.word}"
                val pronText = if (!entry.pronunciation.isNullOrBlank()) "  ${entry.pronunciation}" else ""
                val explLines = wrapText(entry.explanation, CONTENT_WIDTH - 20f, bodyPaint)
                val contextLines = if (!entry.contextSentence.isNullOrBlank()) {
                    wrapText("“${entry.contextSentence}”", CONTENT_WIDTH - 30f, quotePaint)
                } else emptyList()

                val contextBoxH = if (contextLines.isNotEmpty()) (contextLines.size * 13f) + 16f else 0f
                val totalH = 20f + (explLines.size * 13f) + (if (contextBoxH > 0f) contextBoxH + 10f else 0f) + 20f

                checkNewPage(totalH)

                val itemTop = y
                canvas.drawText(wordText, MARGIN, itemTop + 12f, wordPaint)
                if (pronText.isNotBlank()) {
                    val wWidth = wordPaint.measureText(wordText)
                    canvas.drawText(pronText, MARGIN + wWidth + 6f, itemTop + 12f, pronPaint)
                }
                var curY = itemTop + 26f
                explLines.forEach { line ->
                    canvas.drawText(line, MARGIN, curY, bodyPaint)
                    curY += 13f
                }

                if (contextLines.isNotEmpty()) {
                    curY += 4f
                    val boxTop = curY
                    canvas.drawRoundRect(MARGIN, boxTop, PAGE_WIDTH - MARGIN, boxTop + contextBoxH, 6f, 6f, quoteBgPaint)
                    canvas.drawRoundRect(MARGIN, boxTop, MARGIN + 4f, boxTop + contextBoxH, 2f, 2f, accentBarPaint)

                    var quoteY = boxTop + 14f
                    contextLines.forEach { qLine ->
                        canvas.drawText(qLine, MARGIN + 12f, quoteY, quotePaint)
                        quoteY += 13f
                    }
                    curY = boxTop + contextBoxH + 6f
                }

                if (entry.source.isNotBlank()) {
                    canvas.drawText(entry.source, MARGIN, curY + 6f, tagPaint)
                    curY += 14f
                }

                curY += 6f
                canvas.drawLine(MARGIN, curY, PAGE_WIDTH - MARGIN, curY, linePaint)
                y = curY + 12f
            }

            canvas.drawText("Vern Vocabulary Guide • Page $pageNumber", MARGIN, PAGE_HEIGHT - 20f, tagPaint)
            pdfDocument.finishPage(currentPage)

            val dir = File(context.cacheDir, "study_guides").apply { if (!exists()) mkdirs() }
            val sanitized = documentTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(40)
            val outFile = File(dir, "Vocabulary_${sanitized}_${System.currentTimeMillis()}.pdf")
            FileOutputStream(outFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()
            Log.d(TAG, "Successfully generated Vocabulary PDF: ${outFile.absolutePath}")
            return outFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate Vocabulary PDF", e)
            return null
        }
    }

    fun generateBookmarksBatchPdf(
        context: Context,
        documentTitle: String,
        bookmarks: List<ReaderAnnotation>,
        sentenceTextLookup: (Int) -> String?
    ): File? {
        try {
            val pdfDocument = PdfDocument()
            val dateFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
            val dateString = dateFormat.format(Date())

            val titlePaint = Paint().apply {
                color = "#1C1B1F".toColorInt()
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val subtitlePaint = Paint().apply {
                color = "#49454F".toColorInt()
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val bodyPaint = Paint().apply {
                color = "#313033".toColorInt()
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val quotePaint = Paint().apply {
                color = "#1C1B1F".toColorInt()
                textSize = 10f
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                isAntiAlias = true
            }

            val tagPaint = Paint().apply {
                color = "#79747E".toColorInt()
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val linePaint = Paint().apply {
                color = "#E7E0EC".toColorInt()
                strokeWidth = 1f
                isAntiAlias = true
            }

            val barPaint = Paint().apply {
                color = "#6750A4".toColorInt()
                isAntiAlias = true
            }

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var currentPage = pdfDocument.startPage(pageInfo)
            var canvas: Canvas = currentPage.canvas
            var y = MARGIN

            fun checkNewPage(neededHeight: Float) {
                if (y + neededHeight > PAGE_HEIGHT - MARGIN) {
                    val footerText = "Vern Bookmarks • Page $pageNumber"
                    canvas.drawText(footerText, MARGIN, PAGE_HEIGHT - 20f, tagPaint)
                    pdfDocument.finishPage(currentPage)

                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    currentPage = pdfDocument.startPage(pageInfo)
                    canvas = currentPage.canvas
                    y = MARGIN
                }
            }

            // Header
            canvas.drawText("VERN BOOKMARKS", MARGIN, y + 10f, tagPaint)
            y += 24f
            canvas.drawText(documentTitle, MARGIN, y + 10f, titlePaint)
            y += 26f
            canvas.drawText("Generated on $dateString • ${bookmarks.size} Bookmark${if (bookmarks.size == 1) "" else "s"}", MARGIN, y, subtitlePaint)
            y += 14f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 20f

            bookmarks.sortedBy { it.chunkIndex }.forEachIndexed { index, bm ->
                val sentenceText = sentenceTextLookup(bm.chunkIndex)?.trim().orEmpty()
                val quoteLines = if (sentenceText.isNotBlank()) wrapText("“$sentenceText”", CONTENT_WIDTH - 24f, quotePaint) else emptyList()
                val noteLines = if (bm.note.isNotBlank()) wrapText("Note: ${bm.note}", CONTENT_WIDTH - 24f, bodyPaint) else emptyList()
                val audioLabel = if (bm.audioPath != null) " • 🎙️ Voice Memo (${bm.audioDurationSeconds}s)" else ""

                val totalH = (quoteLines.size * 13f) + (noteLines.size * 13f) + 32f
                checkNewPage(totalH)

                val itemTop = y
                val colorHex = bm.highlightColor ?: "#6750A4"
                barPaint.color = runCatching { colorHex.toColorInt() }.getOrDefault("#6750A4".toColorInt())
                canvas.drawRect(MARGIN, itemTop + 4f, MARGIN + 4f, itemTop + totalH - 8f, barPaint)

                canvas.drawText("${index + 1}. Sentence #${bm.chunkIndex + 1}$audioLabel", MARGIN + 12f, itemTop + 12f, tagPaint)
                var textY = itemTop + 26f
                quoteLines.forEach { line ->
                    canvas.drawText(line, MARGIN + 12f, textY, quotePaint)
                    textY += 13f
                }
                noteLines.forEach { line ->
                    canvas.drawText(line, MARGIN + 12f, textY, bodyPaint)
                    textY += 13f
                }

                textY += 4f
                canvas.drawLine(MARGIN, textY, PAGE_WIDTH - MARGIN, textY, linePaint)
                y = textY + 12f
            }

            canvas.drawText("Vern Bookmarks • Page $pageNumber", MARGIN, PAGE_HEIGHT - 20f, tagPaint)
            pdfDocument.finishPage(currentPage)

            val dir = File(context.cacheDir, "study_guides").apply { if (!exists()) mkdirs() }
            val sanitized = documentTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(40)
            val outFile = File(dir, "Bookmarks_${sanitized}_${System.currentTimeMillis()}.pdf")
            FileOutputStream(outFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()
            Log.d(TAG, "Successfully generated Bookmarks PDF: ${outFile.absolutePath}")
            return outFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate Bookmarks PDF", e)
            return null
        }
    }

    fun generateNotesBatchPdf(
        context: Context,
        documentTitle: String,
        documentNote: String,
        notes: List<ReaderAnnotation>,
        sentenceTextLookup: (Int) -> String?
    ): File? {
        try {
            val pdfDocument = PdfDocument()
            val dateFormat = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault())
            val dateString = dateFormat.format(Date())

            val titlePaint = Paint().apply {
                color = "#1C1B1F".toColorInt()
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val subtitlePaint = Paint().apply {
                color = "#49454F".toColorInt()
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val sectionPaint = Paint().apply {
                color = "#6750A4".toColorInt()
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val bodyPaint = Paint().apply {
                color = "#1C1B1F".toColorInt()
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val quotePaint = Paint().apply {
                color = "#49454F".toColorInt()
                textSize = 9.5f
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                isAntiAlias = true
            }

            val tagPaint = Paint().apply {
                color = "#79747E".toColorInt()
                textSize = 8.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val linePaint = Paint().apply {
                color = "#E7E0EC".toColorInt()
                strokeWidth = 1f
                isAntiAlias = true
            }

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var currentPage = pdfDocument.startPage(pageInfo)
            var canvas: Canvas = currentPage.canvas
            var y = MARGIN

            fun checkNewPage(neededHeight: Float) {
                if (y + neededHeight > PAGE_HEIGHT - MARGIN) {
                    val footerText = "Vern Study Notes • Page $pageNumber"
                    canvas.drawText(footerText, MARGIN, PAGE_HEIGHT - 20f, tagPaint)
                    pdfDocument.finishPage(currentPage)

                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    currentPage = pdfDocument.startPage(pageInfo)
                    canvas = currentPage.canvas
                    y = MARGIN
                }
            }

            // Header
            canvas.drawText("VERN STUDY NOTES", MARGIN, y + 10f, tagPaint)
            y += 24f
            canvas.drawText(documentTitle, MARGIN, y + 10f, titlePaint)
            y += 26f
            val noteCount = notes.size + if (documentNote.isNotBlank()) 1 else 0
            canvas.drawText("Generated on $dateString • $noteCount Note${if (noteCount == 1) "" else "s"}", MARGIN, y, subtitlePaint)
            y += 14f
            canvas.drawLine(MARGIN, y, PAGE_WIDTH - MARGIN, y, linePaint)
            y += 20f

            if (documentNote.isNotBlank()) {
                checkNewPage(40f)
                canvas.drawText("📝 Document Study Note", MARGIN, y, sectionPaint)
                y += 16f

                val noteLines = wrapText(documentNote, CONTENT_WIDTH - 20f, bodyPaint)
                val boxH = (noteLines.size * 14f) + 16f
                checkNewPage(boxH + 16f)

                val boxTop = y
                val bgPaint = Paint().apply { color = "#F7F2FA".toColorInt(); isAntiAlias = true }
                canvas.drawRoundRect(MARGIN, boxTop, PAGE_WIDTH - MARGIN, boxTop + boxH, 6f, 6f, bgPaint)

                var lineY = boxTop + 16f
                noteLines.forEach { line ->
                    canvas.drawText(line, MARGIN + 10f, lineY, bodyPaint)
                    lineY += 14f
                }
                y = boxTop + boxH + 18f
            }

            if (notes.isNotEmpty()) {
                checkNewPage(30f)
                canvas.drawText("📌 Sentence Notes", MARGIN, y, sectionPaint)
                y += 18f

                notes.sortedBy { it.chunkIndex }.forEachIndexed { index, note ->
                    val sentenceText = sentenceTextLookup(note.chunkIndex)?.trim().orEmpty()
                    val quoteLines = if (sentenceText.isNotBlank()) wrapText("“$sentenceText”", CONTENT_WIDTH - 20f, quotePaint) else emptyList()
                    val noteLines = if (note.note.isNotBlank()) wrapText(note.note, CONTENT_WIDTH - 20f, bodyPaint) else emptyList()
                    val audioLabel = if (note.audioPath != null) " • 🎙️ Audio Memo (${note.audioDurationSeconds}s)" else ""

                    val totalH = (quoteLines.size * 13f) + (noteLines.size * 13f) + 28f
                    checkNewPage(totalH)

                    val itemTop = y
                    canvas.drawText("${index + 1}. Sentence #${note.chunkIndex + 1}$audioLabel", MARGIN + 10f, itemTop + 10f, tagPaint)
                    var textY = itemTop + 24f
                    quoteLines.forEach { line ->
                        canvas.drawText(line, MARGIN + 10f, textY, quotePaint)
                        textY += 13f
                    }
                    noteLines.forEach { line ->
                        canvas.drawText(line, MARGIN + 10f, textY, bodyPaint)
                        textY += 13f
                    }
                    textY += 4f
                    canvas.drawLine(MARGIN, textY, PAGE_WIDTH - MARGIN, textY, linePaint)
                    y = textY + 12f
                }
            }

            canvas.drawText("Vern Study Notes • Page $pageNumber", MARGIN, PAGE_HEIGHT - 20f, tagPaint)
            pdfDocument.finishPage(currentPage)

            val dir = File(context.cacheDir, "study_guides").apply { if (!exists()) mkdirs() }
            val sanitized = documentTitle.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(40)
            val outFile = File(dir, "Notes_${sanitized}_${System.currentTimeMillis()}.pdf")
            FileOutputStream(outFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
            pdfDocument.close()
            Log.d(TAG, "Successfully generated Notes PDF: ${outFile.absolutePath}")
            return outFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate Notes PDF", e)
            return null
        }
    }

    fun sharePdfFile(context: Context, file: File, title: String) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri(title, uri)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun wrapText(text: String, maxWidth: Float, paint: Paint): List<String> {
        if (text.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        val words = text.split(Regex("""\s+"""))
        var currentLine = StringBuilder()

        words.forEach { word ->
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val width = paint.measureText(testLine)
            if (width <= maxWidth) {
                currentLine.append(if (currentLine.isEmpty()) word else " $word")
            } else {
                if (currentLine.isNotEmpty()) {
                    result.add(currentLine.toString())
                }
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            result.add(currentLine.toString())
        }
        return result
    }
}
