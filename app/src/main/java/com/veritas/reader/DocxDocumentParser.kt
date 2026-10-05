package com.veritas.reader

import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.zip.ZipInputStream

sealed class DocxBlock {
    data class Heading(val level: Int, val text: String, val sourceFormat: DocxSourceFormat? = null) : DocxBlock()
    data class Paragraph(val text: String, val sourceFormat: DocxSourceFormat? = null) : DocxBlock()
    data class Bullet(val level: Int, val text: String, val sourceFormat: DocxSourceFormat? = null) : DocxBlock()
    data class Table(val rows: List<List<String>>) : DocxBlock()
    class Image(private val loadBytes: () -> ByteArray, val description: String = "") : DocxBlock() {
        constructor(imageBytes: ByteArray, description: String = "") : this({ imageBytes }, description)
        val imageBytes: ByteArray get() = loadBytes()
    }
}

data class DocxPage(
    val pageNumber: Int,
    val blocks: List<DocxBlock>
)

data class DocxDocument(
    val title: String,
    val pages: List<DocxPage>,
    val totalPages: Int = pages.size
)

object DocxDocumentParser {

    fun parse(bytes: ByteArray, defaultTitle: String, includeImages: Boolean = true, preserveSourceFormatting: Boolean = false): DocxDocument {
        var documentXml: String? = null
        var relsXml: String? = null
        var stylesXml = ""
        val mediaMap = if (includeImages) mutableMapOf<String, ByteArray>() else emptyMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) {
                    val name = entry.name.trimStart('/')
                    if (name == "word/document.xml" || name == "document.xml") {
                        documentXml = zip.readBytes().toString(Charsets.UTF_8)
                    } else if (name == "word/_rels/document.xml.rels" || name == "_rels/document.xml.rels") {
                        relsXml = zip.readBytes().toString(Charsets.UTF_8)
                    } else if (preserveSourceFormatting && name == "word/styles.xml") {
                        stylesXml = zip.readBytes().toString(Charsets.UTF_8)
                    } else if (includeImages && (name.startsWith("word/media/") || name.startsWith("media/"))) {
                        (mediaMap as MutableMap)[name] = zip.readBytes()
                    }
                }
            }
        }

        return parseContents(documentXml, relsXml, mediaMap, defaultTitle, preserveSourceFormatting, stylesXml)
    }

    fun parse(file: java.io.File, defaultTitle: String, includeImages: Boolean = true, preserveSourceFormatting: Boolean = false): DocxDocument {
        val archive = OriginalArchiveEntries(file)
        val text = archive.textMap { it.endsWith("document.xml") || it.endsWith("document.xml.rels") || (preserveSourceFormatting && it == "word/styles.xml") }
        val media = if (includeImages) archive.imageMap { it.startsWith("word/media/") || it.startsWith("media/") } else emptyMap()
        return parseContents(text["word/document.xml"] ?: text["document.xml"], text["word/_rels/document.xml.rels"] ?: text["_rels/document.xml.rels"], media, defaultTitle, preserveSourceFormatting, text["word/styles.xml"].orEmpty())
    }

    private fun parseContents(documentXml: String?, relsXml: String?, mediaMap: Map<String, ByteArray>, defaultTitle: String, preserveSourceFormatting: Boolean, stylesXml: String = ""): DocxDocument {
        if (documentXml == null) {
            return DocxDocument(
                title = defaultTitle,
                pages = listOf(
                    DocxPage(
                        pageNumber = 1,
                        blocks = listOf(DocxBlock.Paragraph("Could not read Word document content."))
                    )
                )
            )
        }

        val relsMap = mutableMapOf<String, String>()
        if (relsXml != null) {
            val relRegex = Regex("""<Relationship\b[^>]*Id="([^"]+)"[^>]*Target="([^"]+)"[^>]*/?>""", RegexOption.IGNORE_CASE)
            relRegex.findAll(relsXml).forEach { match ->
                val id = match.groupValues[1]
                val target = match.groupValues[2].trimStart('/')
                val fullPath = if (target.startsWith("media/")) "word/$target" else if (target.contains("media/")) target else "word/$target"
                relsMap[id] = fullPath
            }
        }

        val allBlocks = parseXmlBlocks(documentXml, relsMap, mediaMap, preserveSourceFormatting, DocxStyleResolver(stylesXml))
        val pages = paginateBlocks(allBlocks)

        return DocxDocument(
            title = defaultTitle,
            pages = if (pages.isNotEmpty()) pages else listOf(DocxPage(1, listOf(DocxBlock.Paragraph("Empty document."))))
        )
    }

    private fun parseXmlBlocks(
        xml: String,
        relsMap: Map<String, String> = emptyMap(),
        mediaMap: Map<String, ByteArray> = emptyMap(),
        preserveSourceFormatting: Boolean = false,
        styles: DocxStyleResolver = DocxStyleResolver()
    ): List<DocxBlock> {
        val blocks = mutableListOf<DocxBlock>()
        val factory = javax.xml.parsers.SAXParserFactory.newInstance()
        factory.isNamespaceAware = false
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        val domFactory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        runCatching { domFactory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        val builder = domFactory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> org.xml.sax.InputSource(java.io.StringReader("")) }
        val handler = object : org.xml.sax.helpers.DefaultHandler() {
            var depth = 0
            var bodyDepth = -1
            var captureDepth = -1
            val fragment = StringBuilder()
            fun escape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
            override fun resolveEntity(publicId: String?, systemId: String?): org.xml.sax.InputSource = org.xml.sax.InputSource(java.io.StringReader(""))
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: org.xml.sax.Attributes) {
                depth++
                val tag = qName.substringAfter(':')
                if (tag == "body") bodyDepth = depth
                if (captureDepth < 0 && depth == bodyDepth + 1 && tag in setOf("p", "tbl")) {
                    captureDepth = depth; fragment.clear()
                }
                if (captureDepth >= 0) {
                    fragment.append('<').append(qName)
                    for (i in 0 until attributes.length) fragment.append(' ').append(attributes.getQName(i)).append("=\"").append(escape(attributes.getValue(i))).append('"')
                    fragment.append('>')
                }
            }
            override fun characters(chars: CharArray, start: Int, length: Int) {
                if (captureDepth >= 0) {
                    require(fragment.length.toLong() + length <= 16L * 1024 * 1024) { "A Word block is too large to display." }
                    fragment.append(escape(String(chars, start, length)))
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                if (captureDepth >= 0) fragment.append("</").append(qName).append('>')
                if (depth == captureDepth) {
                    // Only one paragraph/table becomes a DOM tree at a time.
                    val doc = builder.parse(ByteArrayInputStream(("<root>" + fragment + "</root>").toByteArray(Charsets.UTF_8)))
                    val element = doc.documentElement.firstChild as org.w3c.dom.Element
                    when (qName.substringAfter(':')) {
                        "p" -> { blocks.addAll(parseParagraphElement(element, preserveSourceFormatting, styles)); blocks.addAll(extractInlineImages(element, relsMap, mediaMap)) }
                        "tbl" -> parseTableElement(element).takeIf { it.rows.isNotEmpty() }?.let(blocks::add)
                    }
                    captureDepth = -1; fragment.clear()
                }
                if (depth == bodyDepth) bodyDepth = -1
                depth--
            }
        }
        factory.newSAXParser().parse(org.xml.sax.InputSource(java.io.StringReader(xml)), handler)
        return blocks
    }

    private fun extractInlineImages(
        element: org.w3c.dom.Element,
        relsMap: Map<String, String>,
        mediaMap: Map<String, ByteArray>
    ): List<DocxBlock.Image> {
        if (relsMap.isEmpty() || mediaMap.isEmpty()) return emptyList()
        val images = mutableListOf<DocxBlock.Image>()
        val allDescendants = element.getElementsByTagName("*")
        for (i in 0 until allDescendants.length) {
            val node = allDescendants.item(i) as? org.w3c.dom.Element ?: continue
            val localName = node.tagName.substringAfter(':').lowercase(Locale.getDefault())
            val rId = when (localName) {
                "blip" -> node.getAttribute("r:embed").ifBlank { node.getAttribute("embed") }
                "imagedata" -> node.getAttribute("r:id").ifBlank { node.getAttribute("id") }
                else -> ""
            }
            if (rId.isNotBlank()) {
                val targetPath = relsMap[rId]
                val path = targetPath?.let { target ->
                    listOf(target, "word/$target", target.substringAfterLast("word/")).firstOrNull { mediaMap.containsKey(it) }
                }
                if (path != null) images.add(DocxBlock.Image({ mediaMap[path] ?: ByteArray(0) }))
            }
        }
        return images
    }

    private data class DocxRun(val text: String, val isBold: Boolean)

    private fun extractRuns(element: org.w3c.dom.Element): List<DocxRun> {
        val runs = mutableListOf<DocxRun>()
        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType != org.w3c.dom.Node.ELEMENT_NODE) continue
            val el = child as org.w3c.dom.Element
            val localName = el.tagName.substringAfter(':').lowercase(Locale.getDefault())
            when (localName) {
                "ppr", "tcpr", "tblpr" -> continue
                "r" -> {
                    val isBold = isRunBold(el)
                    val text = extractRunText(el)
                    if (text.isNotEmpty()) {
                        runs.add(DocxRun(text, isBold))
                    }
                }
                "t" -> {
                    val text = el.textContent.orEmpty()
                    if (text.isNotEmpty()) runs.add(DocxRun(text, false))
                }
                "tab", "ptab" -> runs.add(DocxRun("\t", false))
                "br", "cr" -> runs.add(DocxRun("\n", false))
                else -> runs.addAll(extractRuns(el))
            }
        }
        return runs
    }

    private fun isRunBold(r: org.w3c.dom.Element): Boolean {
        val rPrList = r.getElementsByTagName("w:rPr")
        if (rPrList.length == 0) return false
        val rPr = rPrList.item(0) as org.w3c.dom.Element
        val bList = rPr.getElementsByTagName("w:b")
        if (bList.length == 0) return false
        val b = bList.item(0) as org.w3c.dom.Element
        val bVal = b.getAttribute("w:val").ifBlank { b.getAttribute("val") }
        return bVal.isEmpty() || bVal == "true" || bVal == "1" || bVal == "on"
    }

    private fun extractRunText(r: org.w3c.dom.Element): String {
        val sb = StringBuilder()
        val childNodes = r.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType != org.w3c.dom.Node.ELEMENT_NODE) continue
            val el = child as org.w3c.dom.Element
            val localName = el.tagName.substringAfter(':').lowercase(Locale.getDefault())
            when (localName) {
                "t" -> sb.append(el.textContent.orEmpty())
                "tab", "ptab" -> sb.append('\t')
                "br", "cr" -> sb.append('\n')
            }
        }
        return sb.toString()
    }

    private fun parseParagraphElement(p: org.w3c.dom.Element, preserveSourceFormatting: Boolean = false, styles: DocxStyleResolver = DocxStyleResolver()): List<DocxBlock> {
        var headingLevel: Int? = null
        var isToc = false
        var bulletLevel: Int? = null

        val pPrList = p.getElementsByTagName("w:pPr")
        if (pPrList.length > 0) {
            val pPr = pPrList.item(0) as org.w3c.dom.Element
            val pStyleList = pPr.getElementsByTagName("w:pStyle")
            if (pStyleList.length > 0) {
                val pStyle = pStyleList.item(0) as org.w3c.dom.Element
                val rawVal = pStyle.getAttribute("w:val").ifBlank { pStyle.getAttribute("val") }
                val styleVal = rawVal.lowercase(Locale.getDefault()).replace(" ", "").replace("_", "").replace("-", "")

                when {
                    styleVal.startsWith("heading1") || styleVal == "1" || styleVal == "title" -> headingLevel = 1
                    styleVal.startsWith("heading2") || styleVal == "2" || styleVal == "subtitle" -> headingLevel = 2
                    styleVal.startsWith("heading3") || styleVal == "3" -> headingLevel = 3
                    styleVal.startsWith("heading4") || styleVal == "4" -> headingLevel = 4
                    styleVal.startsWith("heading5") || styleVal == "5" -> headingLevel = 5
                    styleVal.startsWith("toc") -> isToc = true
                    styleVal.contains("list") || styleVal.contains("bullet") -> bulletLevel = 0
                }
            }

            val numPrList = pPr.getElementsByTagName("w:numPr")
            if (numPrList.length > 0) {
                val numPr = numPrList.item(0) as org.w3c.dom.Element
                val ilvlNode = numPr.getElementsByTagName("w:ilvl").item(0)
                val ilvl = if (ilvlNode != null) (ilvlNode as org.w3c.dom.Element).getAttribute("w:val").toIntOrNull() ?: 0 else 0
                if (headingLevel == null && !isToc) {
                    bulletLevel = ilvl
                }
            }
        }

        val runs = extractRuns(p)
        val sourceFormat = if (preserveSourceFormatting) docxSourceFormat(p, styles) else null
        val fullText = (sourceFormat?.runs?.joinToString("") { it.text } ?: runs.joinToString("") { it.text }).trim()
        if (fullText.isBlank()) return emptyList()

        if (headingLevel != null) {
            return listOf(DocxBlock.Heading(headingLevel, fullText, sourceFormat))
        }
        if (bulletLevel != null) {
            return listOf(DocxBlock.Bullet(bulletLevel, fullText, sourceFormat))
        }

        // Check if paragraph is a TOC entry (either by style or by trailing tab/dot-leaders + page number)
        val tocMatch = Regex("""^(.{3,140}?)(?:(?:\s*[\.\-_·•…]\s*){2,}|\s{3,}|\t+)\s*(\d{1,5})$""").matchEntire(fullText)
        if (isToc || tocMatch != null) {
            val formatted = if (tocMatch != null) {
                val title = tocMatch.groupValues[1].trim()
                val page = tocMatch.groupValues[2].trim()
                "$title ...... $page"
            } else {
                fullText
            }
            return listOf(DocxBlock.Paragraph(formatted))
        }

        // Original view keeps source run emphasis instead of promoting bold phrases to headings.
        if (sourceFormat != null) return listOf(DocxBlock.Paragraph(fullText, sourceFormat))
        // Check for run-in subheadings (e.g. bold heading at start of paragraph followed by prose)
        if (runs.size >= 2) {
            val firstRun = runs.first()
            val firstRunTrimmed = firstRun.text.trim()
            if (firstRun.isBold && firstRunTrimmed.length in 3..70) {
                val isDottedHeading = Regex("""^\d+(\.\d+){1,4}\s+[A-Za-z].*""").matches(firstRunTrimmed)
                val isTitleCaseHeading = isDottedHeading || (firstRunTrimmed.length in 4..50 && !firstRunTrimmed.endsWith('.') && runs.drop(1).none { it.isBold })
                if (isTitleCaseHeading) {
                    val dotCount = if (isDottedHeading) firstRunTrimmed.substringBefore(' ').count { it == '.' } else 1
                    val level = (dotCount + 1).coerceIn(1, 4)
                    val remainingText = runs.drop(1).joinToString("") { it.text }.trim()
                    if (remainingText.isNotBlank()) {
                        return listOf(
                            DocxBlock.Heading(level, firstRunTrimmed),
                            DocxBlock.Paragraph(remainingText)
                        )
                    } else {
                        return listOf(DocxBlock.Heading(level, firstRunTrimmed))
                    }
                }
            }
        }

        // Fallback check for plain-text run-in numbered headings: e.g. "1.2.1 General Objectives The primary..."
        val plainRunIn = Regex("""^(\d+(?:\.\d+){1,4}\s+[A-Z][A-Za-z0-9\s,\-'\"]{2,60}?)(?:(?:\.|\:)\s+|\s{2,}|\t|\n)(.*)$""", RegexOption.DOT_MATCHES_ALL).matchEntire(fullText)
        if (plainRunIn != null) {
            val headingPart = plainRunIn.groupValues[1].trim()
            val bodyPart = plainRunIn.groupValues[2].trim()
            val dotCount = headingPart.substringBefore(' ').count { it == '.' }
            val level = (dotCount + 1).coerceIn(1, 4)
            if (bodyPart.isNotBlank()) {
                return listOf(
                    DocxBlock.Heading(level, headingPart),
                    DocxBlock.Paragraph(bodyPart)
                )
            } else {
                return listOf(DocxBlock.Heading(level, headingPart))
            }
        }

        return listOf(DocxBlock.Paragraph(fullText))
    }

    private fun parseTableElement(tbl: org.w3c.dom.Element): DocxBlock.Table {
        val rows = mutableListOf<List<String>>()
        val trList = tbl.getElementsByTagName("w:tr")
        for (r in 0 until trList.length) {
            val tr = trList.item(r) as org.w3c.dom.Element
            val cells = mutableListOf<String>()
            val tcList = tr.getElementsByTagName("w:tc")
            for (c in 0 until tcList.length) {
                val tc = tcList.item(c) as org.w3c.dom.Element
                val runs = extractRuns(tc)
                val cellText = runs.joinToString("") { it.text }
                    .replace("\r\n", " ")
                    .replace('\n', ' ')
                    .trim()
                cells.add(cellText)
            }
            if (cells.any { it.isNotBlank() }) {
                rows.add(cells)
            }
        }
        return DocxBlock.Table(rows)
    }

    private fun paginateBlocks(blocks: List<DocxBlock>): List<DocxPage> {
        val pages = mutableListOf<DocxPage>()
        val currentPageBlocks = mutableListOf<DocxBlock>()
        var currentWeight = 0
        var pageNum = 1

        for (block in blocks) {
            val weight = when (block) {
                is DocxBlock.Heading -> 3
                is DocxBlock.Bullet -> 1
                is DocxBlock.Paragraph -> maxOf(1, block.text.length / 150)
                is DocxBlock.Table -> maxOf(3, block.rows.size * 2)
                is DocxBlock.Image -> 4
            }

            if (currentWeight + weight > 12 && currentPageBlocks.isNotEmpty()) {
                pages.add(DocxPage(pageNum, currentPageBlocks.toList()))
                pageNum++
                currentPageBlocks.clear()
                currentWeight = 0
            }

            currentPageBlocks.add(block)
            currentWeight += weight
        }

        if (currentPageBlocks.isNotEmpty()) {
            pages.add(DocxPage(pageNum, currentPageBlocks.toList()))
        }

        return pages
    }
}
