package com.veritas.reader

import org.w3c.dom.Element

data class DocxTextRun(val text: String, val bold: Boolean = false, val italic: Boolean = false,
    val underline: Boolean = false, val sizePoints: Float? = null, val color: Long? = null)
data class DocxSourceFormat(val runs: List<DocxTextRun>, val alignment: String = "left",
    val beforePoints: Float = 0f, val afterPoints: Float = 0f, val indentPoints: Float = 0f)

private fun Element.child(tag: String): Element? = (0 until childNodes.length)
    .mapNotNull { childNodes.item(it) as? Element }.firstOrNull { it.tagName == "w:$tag" }
private fun Element.value(tag: String) = child(tag)?.getAttribute("w:val").orEmpty()

/** Resolve styles per document, never in shared mutable parser state. */
internal class DocxStyleResolver(xml: String = "") {
    private val root = if (xml.isBlank()) null else runCatching {
        require(!xml.contains("<!DOCTYPE", ignoreCase = true))
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> org.xml.sax.InputSource(java.io.StringReader("")) }
        }.parse(java.io.ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))).documentElement
    }.getOrNull()
    private val styles = root?.getElementsByTagName("w:style")?.let { nodes ->
        (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }.associateBy { it.getAttribute("w:styleId") }
    }.orEmpty()
    private val defaultParagraph = styles.values.firstOrNull { it.getAttribute("w:type") == "paragraph" && it.getAttribute("w:default") == "1" }

    private fun chain(id: String): List<Element> {
        val result = mutableListOf<Element>()
        val seen = mutableSetOf<String>()
        var next = id
        while (next.isNotBlank() && seen.add(next) && result.size < 32) {
            val style = styles[next] ?: break
            result.add(style)
            next = style.value("basedOn")
        }
        return result.asReversed()
    }

    fun properties(p: Element, kind: String, run: Element? = null): List<Element> {
        val paragraph = p.child("pPr")
        val id = paragraph?.value("pStyle").orEmpty().ifBlank { defaultParagraph?.getAttribute("w:styleId").orEmpty() }
        return buildList {
            root?.child("docDefaults")?.child("${kind}Default")?.child(kind)?.let(::add)
            chain(id).mapNotNullTo(this) { it.child(kind) }
            if (kind == "rPr") {
                paragraph?.child("rPr")?.let(::add)
                chain(run?.child("rPr")?.value("rStyle").orEmpty()).mapNotNullTo(this) { it.child(kind) }
                run?.child(kind)?.let(::add)
            } else paragraph?.let(::add)
        }
    }
}

internal fun docxSourceFormat(p: Element, styles: DocxStyleResolver = DocxStyleResolver()): DocxSourceFormat {
    fun List<Element>.attribute(tag: String, attribute: String = "val"): String? = asReversed()
        .firstNotNullOfOrNull { it.child(tag)?.takeIf { child -> child.hasAttribute("w:$attribute") }?.getAttribute("w:$attribute") }
    fun List<Element>.flag(tag: String): Boolean = asReversed().firstNotNullOfOrNull { it.child(tag) }
        ?.let { it.getAttribute("w:val") !in listOf("0", "false", "off", "none") } ?: false
    val properties = styles.properties(p, "pPr")
    val runNodes = p.getElementsByTagName("w:r")
    val runs = (0 until runNodes.length).mapNotNull { i ->
        val run = runNodes.item(i) as Element
        var ancestor = run.parentNode
        while (ancestor != null && ancestor != p) {
            if ((ancestor as? Element)?.tagName in listOf("w:del", "w:moveFrom")) return@mapNotNull null
            ancestor = ancestor.parentNode
        }
        val style = styles.properties(p, "rPr", run)
        val text = buildString {
            for (n in 0 until run.childNodes.length) {
                val node = run.childNodes.item(n) as? Element ?: continue
                when (node.tagName.substringAfter(':')) { "t" -> append(node.textContent); "tab" -> append('\t'); "br", "cr" -> append('\n') }
            }
        }
        DocxTextRun(text, style.flag("b"), style.flag("i"), style.flag("u"),
            style.attribute("sz")?.toFloatOrNull()?.div(2)?.coerceIn(6f, 96f),
            style.attribute("color")?.takeIf { it.matches(Regex("[0-9a-fA-F]{6}")) }?.toLongOrNull(16)?.let { 0xff000000 or it })
    }
    fun spacing(attribute: String) = properties.attribute("spacing", attribute)?.toFloatOrNull()?.div(20f)?.coerceIn(0f, 72f) ?: 0f
    return DocxSourceFormat(runs, properties.attribute("jc") ?: "left", spacing("before"), spacing("after"),
        (properties.attribute("ind", "start") ?: properties.attribute("ind", "left"))?.toFloatOrNull()?.div(20f)?.coerceIn(0f, 96f) ?: 0f)
}
