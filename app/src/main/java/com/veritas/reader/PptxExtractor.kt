package com.veritas.reader

import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.zip.ZipInputStream

data class PptxImportOptions(
    val includeSpeakerNotes: Boolean = true,
    val autoPunctuate: Boolean = false,
    val ocrSlideImages: Boolean = true
)

data class PptxSlideContent(
    val number: Int,
    // Title paragraphs, plain (always rendered first, followed by a blank line).
    val titleLines: List<String>,
    // Body/table/text-box lines in slide order, already bullet-prefixed where
    // the source paragraph came from a list placeholder.
    val contentLines: List<String>,
    val notesLines: List<String>,
    // Zip paths of images referenced by this slide (for optional OCR).
    val mediaPaths: List<String>,
    val layout: PptxSlideLayout? = null
)

data class PptxDeck(
    val slides: List<PptxSlideContent>,
    val slideCount: Int
)

/**
 * Lightweight .pptx (OOXML presentation) text extractor. Parses slide XML with a
 * simple tag scanner — no Apache POI, no Android XML APIs — so it runs identically
 * in unit tests and on device. Bullet glyphs added here are display-only: the
 * speech sanitizer silences them before TTS.
 */
object PptxExtractor {

    private val slidePathRegex = Regex("""^ppt/slides/slide(\d+)\.xml$""")
    private val notesPathRegex = Regex("""^ppt/notesSlides/notesSlide(\d+)\.xml$""")
    private val relsPathRegex = Regex("""^ppt/slides/_rels/slide(\d+)\.xml\.rels$""")
    private val tagRegex = Regex("""<[^>]+>""")
    private val relationshipRegex =
        Regex("""<Relationship\b[^>]*Id="([^"]+)"[^>]*Target="([^"]+)"[^>]*/?>""")
    private val embedRegex = Regex("""r:embed="([^"]+)"""")
    private val terminalPunctuation = setOf('.', '!', '?', ':', ';', ',')

    fun isPptxEntryName(name: String): Boolean = name == "ppt/presentation.xml"

    fun parseDeck(zipBytes: ByteArray, includeSpeakerNotes: Boolean): PptxDeck {
        val slideXml = sortedMapOf<Int, String>()
        val notesXml = mutableMapOf<Int, String>()
        val relsXml = mutableMapOf<Int, String>()
        var presentationXml = ""
        var presentationRels = ""
        val sourceStyles = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name.trimStart('/')
                if ((name.startsWith("ppt/theme/") || name.startsWith("ppt/slideLayouts/") || name.startsWith("ppt/slideMasters/")) &&
                    (name.endsWith(".xml") || name.endsWith(".rels"))) {
                    sourceStyles[name] = OriginalArchiveEntries.readBounded(zip, 16 * 1024 * 1024).toString(Charsets.UTF_8)
                }
                if (name == "ppt/presentation.xml") presentationXml = OriginalArchiveEntries.readBounded(zip, 16 * 1024 * 1024).toString(Charsets.UTF_8)
                if (name == "ppt/_rels/presentation.xml.rels") presentationRels = OriginalArchiveEntries.readBounded(zip, 16 * 1024 * 1024).toString(Charsets.UTF_8)
                slidePathRegex.matchEntire(name)?.let {
                    slideXml[it.groupValues[1].toInt()] = zip.readBytes().toString(Charsets.UTF_8)
                }
                notesPathRegex.matchEntire(name)?.let {
                    if (includeSpeakerNotes) {
                        notesXml[it.groupValues[1].toInt()] = zip.readBytes().toString(Charsets.UTF_8)
                    }
                }
                relsPathRegex.matchEntire(name)?.let {
                    relsXml[it.groupValues[1].toInt()] = zip.readBytes().toString(Charsets.UTF_8)
                }
            }
        }

        return parseEntries(slideXml, notesXml, relsXml, presentationXml, presentationRels, sourceStyles)
    }

    fun parseDeck(file: java.io.File, includeSpeakerNotes: Boolean): PptxDeck {
        val archive = OriginalArchiveEntries(file)
        val entries = archive.textMap { it.endsWith(".xml") || it.endsWith(".rels") }
        fun byPattern(pattern: Regex): Map<Int, String> = entries.keys.mapNotNull { path ->
            pattern.matchEntire(path)?.let { it.groupValues[1].toInt() to entries.getValue(path) }
        }.toMap()
        return parseEntries(byPattern(slidePathRegex), if (includeSpeakerNotes) byPattern(notesPathRegex) else emptyMap(), byPattern(relsPathRegex),
            entries["ppt/presentation.xml"].orEmpty(), entries["ppt/_rels/presentation.xml.rels"].orEmpty(), entries)
    }

    private fun parseEntries(slideXml: Map<Int, String>, notesXml: Map<Int, String>, relsXml: Map<Int, String>, presentationXml: String, presentationRels: String,
        sourceStyles: Map<String, String> = emptyMap()): PptxDeck {
        val orderedNumbers = PptxSlideLayoutParser.slideOrder(presentationXml, presentationRels).filter { it in slideXml.keys }
            .takeIf { it.isNotEmpty() } ?: slideXml.keys.sorted()
        val slides = orderedNumbers.mapIndexed { index, sourceNumber ->
            val number = index + 1
            val xml = slideXml.getValue(sourceNumber)
            val body = parseSlideBody(xml)
            // PowerPoint pairs notesSlideN with slideN by number.
            val notesNumber = PptxSlideLayoutParser.relatedNumber(relsXml[sourceNumber].orEmpty(), "notesSlides/notesSlide") ?: sourceNumber
            val notes = notesXml[notesNumber]?.let { parseNotesBody(it) }.orEmpty()
            val media = resolveMediaPaths(xml, relsXml[sourceNumber])
            fun relationshipXml(part: String?): String = part?.let {
                sourceStyles[it.substringBeforeLast('/') + "/_rels/" + it.substringAfterLast('/') + ".rels"]
            }.orEmpty()
            val layoutPath = PptxSlideLayoutParser.relatedPart("ppt/slides/slide$sourceNumber.xml", relsXml[sourceNumber].orEmpty(), "slideLayout")
            val masterPath = layoutPath?.let { PptxSlideLayoutParser.relatedPart(it, relationshipXml(it), "slideMaster") }
            val themePath = masterPath?.let { PptxSlideLayoutParser.relatedPart(it, relationshipXml(it), "theme") }
            PptxSlideContent(
                number = number,
                titleLines = body.titleLines,
                contentLines = body.contentLines,
                notesLines = notes,
                mediaPaths = media,
                layout = PptxSlideLayoutParser.parse(xml, relsXml[sourceNumber].orEmpty(), presentationXml,
                    sourceStyles[themePath].orEmpty(), sourceStyles[masterPath].orEmpty(), sourceStyles[layoutPath].orEmpty())
            )
        }
        return PptxDeck(slides = slides, slideCount = slides.size)
    }

    /**
     * Extracts all embedded image byte arrays for a given slide.
     */
    fun extractSlideImages(zipBytes: ByteArray, slide: PptxSlideContent): List<ByteArray> {
        if (slide.mediaPaths.isEmpty()) return emptyList()
        val mediaSet = slide.mediaPaths.map { it.trimStart('/') }.toSet()
        val images = mutableListOf<ByteArray>()
        runCatching {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name.trimStart('/')
                    if (mediaSet.contains(name)) {
                        images.add(zip.readBytes())
                    }
                }
            }
        }
        return images
    }

    /**
     * Renders the stored document text: one [[VERITAS_PAGE:n]] marker per slide so
     * every sentence maps to its exact slide, title set off with a blank line,
     * bullets/tables/notes/OCR beneath it in reading order.
     */
    fun renderDeckText(
        deck: PptxDeck,
        ocrLinesBySlide: Map<Int, List<String>> = emptyMap(),
        autoPunctuate: Boolean = false
    ): String {
        val output = StringBuilder()
        deck.slides.forEach { slide ->
            val lines = buildList {
                addAll(slide.titleLines)
                if (slide.titleLines.isNotEmpty() &&
                    (slide.contentLines.isNotEmpty() || slide.notesLines.isNotEmpty() ||
                        ocrLinesBySlide[slide.number].orEmpty().isNotEmpty())
                ) {
                    add("")
                }
                addAll(slide.contentLines)
                ocrLinesBySlide[slide.number].orEmpty().let { ocr ->
                    if (ocr.isNotEmpty()) {
                        if (slide.contentLines.isNotEmpty()) add("")
                        addAll(ocr)
                    }
                }
                if (slide.notesLines.isNotEmpty()) {
                    add("")
                    slide.notesLines.forEachIndexed { index, line ->
                        add(if (index == 0) "Notes: $line" else line)
                    }
                }
            }
            val rendered = lines
                .map { line -> if (autoPunctuate) autoPunctuateLine(line) else line }
                .joinToString("\n")
                .trim()
            if (rendered.isNotBlank()) {
                if (output.isNotBlank()) output.append("\n\n")
                output.append(ReaderTextIndex.pageMarker(slide.number)).append('\n')
                output.append(rendered)
            }
        }
        return output.toString()
    }

    /**
     * Gentle terminal punctuation: appends a period only to lines that end with no
     * punctuation at all; existing . ! ? : ; , endings are respected untouched.
     */
    fun autoPunctuateLine(line: String): String {
        val trimmed = line.trimEnd()
        if (trimmed.isBlank()) return line
        val lastLetterOrDigit = trimmed.last().isLetterOrDigit() || trimmed.last() in ")%\"'”’"
        if (!lastLetterOrDigit) return line
        if (trimmed.last() in terminalPunctuation) return line
        return "$trimmed."
    }

    // ── Slide XML parsing ────────────────────────────────────────────────────

    private class ShapeState {
        var placeholderType: String? = null
        var hasPlaceholder = false
        val paragraphs = mutableListOf<Pair<Int, String>>() // level to text
    }

    private data class SlideBody(
        val titleLines: List<String>,
        val contentLines: List<String>
    )

    private fun parseSlideBody(xml: String): SlideBody {
        val titleLines = mutableListOf<String>()
        val contentLines = mutableListOf<String>()

        var shape: ShapeState? = null
        var inTable = false
        var paragraphLevel = 0
        val paragraphBuffer = StringBuilder()
        val cellBuffer = StringBuilder()
        val rowCells = mutableListOf<String>()
        var inTextRun = false

        fun flushShape(state: ShapeState) {
            val type = state.placeholderType?.lowercase(Locale.US)
            val isTitle = type == "title" || type == "ctrtitle"
            // Bullets only for list-style placeholders; free text boxes and
            // subtitles stay plain so captions don't grow fake bullets.
            val bulleted = state.hasPlaceholder && !isTitle && type != "subtitle"
            state.paragraphs.forEach { (level, text) ->
                text.split('\n').map { it.trim() }.filter { it.isNotBlank() }
                    .forEachIndexed { lineIndex, line ->
                        when {
                            isTitle -> titleLines.add(line)
                            bulleted && lineIndex == 0 && level <= 0 -> contentLines.add("• $line")
                            bulleted && lineIndex == 0 -> contentLines.add("  ◦ $line")
                            bulleted -> contentLines.add("  $line")
                            else -> contentLines.add(line)
                        }
                    }
            }
        }

        var cursor = 0
        for (match in tagRegex.findAll(xml)) {
            val text = xml.substring(cursor, match.range.first)
            if (inTextRun && text.isNotEmpty()) {
                if (inTable) cellBuffer.append(decodeXmlEntities(text))
                else paragraphBuffer.append(decodeXmlEntities(text))
            }
            cursor = match.range.last + 1

            val tag = match.value
            val selfClosing = tag.endsWith("/>")
            val closing = tag.startsWith("</")
            val name = tag.trimStart('<', '/').substringBefore('>').substringBefore(' ')
                .substringBefore('/').substringAfter(':').lowercase(Locale.US)

            when {
                name == "t" && !closing -> if (!selfClosing) inTextRun = true
                name == "t" && closing -> inTextRun = false
                name == "sp" && !closing && !selfClosing -> shape = ShapeState()
                name == "sp" && closing -> {
                    shape?.let { flushShape(it) }
                    shape = null
                }
                name == "ph" && !closing -> {
                    shape?.let {
                        it.hasPlaceholder = true
                        it.placeholderType = attributeValue(tag, "type") ?: it.placeholderType
                    }
                }
                name == "ppr" && !closing ->
                    paragraphLevel = attributeValue(tag, "lvl")?.toIntOrNull() ?: 0
                name == "p" && !closing && !selfClosing -> {
                    paragraphLevel = 0
                    paragraphBuffer.clear()
                }
                name == "p" && closing -> {
                    val paragraph = paragraphBuffer.toString().trim('\n')
                    if (inTable) {
                        if (paragraph.isNotBlank()) {
                            if (cellBuffer.isNotBlank()) cellBuffer.append(' ')
                            cellBuffer.append(paragraph.replace('\n', ' ').trim())
                        }
                    } else if (paragraph.isNotBlank()) {
                        (shape ?: ShapeState().also { shape = it })
                            .paragraphs.add(paragraphLevel to paragraph)
                    }
                    paragraphBuffer.clear()
                }
                name == "br" -> if (inTable) cellBuffer.append(' ') else paragraphBuffer.append('\n')
                name == "tbl" && !closing -> inTable = true
                name == "tbl" && closing -> inTable = false
                name == "tc" && !closing && !selfClosing -> cellBuffer.clear()
                name == "tc" && closing -> {
                    rowCells.add(cellBuffer.toString().trim())
                    cellBuffer.clear()
                }
                name == "tr" && !closing && !selfClosing -> rowCells.clear()
                name == "tr" && closing -> {
                    if (rowCells.any { it.isNotBlank() }) {
                        val row = "| " + rowCells.joinToString(" | ") { it.trim().replace('\n', ' ') } + " |"
                        contentLines.add(row)
                    }
                    rowCells.clear()
                }
            }
        }
        // Unclosed shape (malformed XML) — keep whatever was collected.
        shape?.let { flushShape(it) }
        return SlideBody(titleLines, contentLines)
    }

    private fun parseNotesBody(xml: String): List<String> {
        // Notes pages contain slide-image, slide-number, and date placeholders too;
        // only the "body" placeholder holds the presenter's notes.
        val body = parseSlideBody(xml)
        val skipMarkers = listOf(Regex("""^\d+$"""))
        return (body.titleLines + body.contentLines)
            .map { it.removePrefix("• ").removePrefix("  ◦ ").trim() }
            .filter { line -> line.isNotBlank() && skipMarkers.none { it.matches(line) } }
    }

    private fun resolveMediaPaths(slideXml: String, relsXml: String?): List<String> {
        return PptxSlideLayoutParser.embeddedMediaPaths(slideXml, relsXml.orEmpty())
    }

    private fun attributeValue(tag: String, attribute: String): String? =
        Regex("""\b$attribute="([^"]*)"""").find(tag)?.groupValues?.get(1)

    private fun decodeXmlEntities(text: String): String {
        if (!text.contains('&')) return text
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace(Regex("""&#x([0-9a-fA-F]+);""")) { m ->
                m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
            }
            .replace(Regex("""&#(\d+);""")) { m ->
                m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
            }
            .replace("&amp;", "&")
    }
}
