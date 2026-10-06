package com.veritas.reader

import org.w3c.dom.Element
import java.io.ByteArrayInputStream

data class PptxVisualElement(
    val x: Float, val y: Float, val width: Float, val height: Float,
    val text: String = "", val mediaPath: String? = null,
    val fontWidthFraction: Float = 0.025f, val bold: Boolean = false,
    val alignment: String = "left", val color: Long = 0xff202020,
    val fill: Long? = null, val rotationDegrees: Float = 0f,
    val stroke: Long? = null, val strokeWidthFraction: Float = 0f,
    val paths: List<PptxVectorPath> = emptyList(),
    val insetLeft: Float = 0f, val insetTop: Float = 0f,
    val insetRight: Float = 0f, val insetBottom: Float = 0f,
    val verticalAnchor: String = "t"
)
data class PptxPathCommand(val kind: String, val points: List<Float>)
data class PptxVectorPath(val commands: List<PptxPathCommand>, val filled: Boolean = true, val stroked: Boolean = true)
data class PptxSlideLayout(val aspectRatio: Float, val background: Long, val elements: List<PptxVisualElement>)

/** Preserve explicitly positioned slide objects; use reading layout for unsupported structures. */
internal object PptxSlideLayoutParser {
    private fun attributes(tag: String): Map<String, String> = Regex("""([\w:.-]+)\s*=\s*["']([^"']*)["']""").findAll(tag).associate { it.groupValues[1] to it.groupValues[2] }
    private fun relationships(xml: String): Map<String, String> = Regex("""<Relationship\b[^>]*>""").findAll(xml).map { attributes(it.value) }.mapNotNull {
        if (it["TargetMode"] == "External") null else it["Id"]?.let { id -> it["Target"]?.let { target -> id to target } }
    }.toMap()
    fun slideOrder(presentation: String, rels: String): List<Int> {
        val targets = relationships(rels)
        return Regex("""<p:sldId\b[^>]*>""").findAll(presentation).mapNotNull { tag ->
            val id = attributes(tag.value)["r:id"]
            id?.let(targets::get)?.let { Regex("""(?:^|/)slide(\d+)\.xml$""").find(it)?.groupValues?.get(1)?.toIntOrNull() }
        }.toList()
    }
    fun relatedNumber(rels: String, path: String): Int? = relationships(rels).values.firstNotNullOfOrNull {
        Regex(Regex.escape(path) + "(\\d+)\\.xml$").find(it)?.groupValues?.get(1)?.toIntOrNull()
    }
    fun relatedPart(source: String, rels: String, kind: String): String? = Regex("""<Relationship\b[^>]*>""").findAll(rels)
        .map { attributes(it.value) }.firstOrNull { it["TargetMode"] != "External" && it["Type"].orEmpty().endsWith("/$kind") }
        ?.get("Target")?.let { java.nio.file.Paths.get(source).parent.resolve(it).normalize().toString().replace('\\', '/') }
    fun embeddedMediaPaths(slide: String, rels: String): List<String> {
        val targets = relationships(rels)
        return Regex("""r:embed\s*=\s*["']([^"']+)["']""").findAll(slide)
            .mapNotNull { targets[it.groupValues[1]] }.filter { it.contains("media/") }
            .map { "ppt/media/" + it.substringAfterLast("media/") }.distinct().toList()
    }
    private fun Element.child(tag: String): Element? = (0 until childNodes.length)
        .mapNotNull { childNodes.item(it) as? Element }.firstOrNull { it.tagName == tag }
    private fun Element.first(tag: String): Element? = getElementsByTagName(tag).item(0) as? Element
    private fun Element.all(tag: String): List<Element> = getElementsByTagName(tag).let { nodes -> (0 until nodes.length).mapNotNull { nodes.item(it) as? Element } }
    private fun parseColor(element: Element?, colors: Map<String, Long> = emptyMap()): Long? {
        if (element == null) return null
        val direct = element.first("a:srgbClr") ?: element.first("a:sysClr") ?: element.first("a:schemeClr") ?: return null
        val value = when (direct.tagName) {
            "a:schemeClr" -> colors[direct.getAttribute("val")]
            "a:sysClr" -> direct.getAttribute("lastClr").toLongOrNull(16)?.let { 0xff000000 or it }
            else -> direct.getAttribute("val").toLongOrNull(16)?.let { 0xff000000 or it }
        } ?: return null
        fun fraction(tag: String, default: Float) = direct.first(tag)?.getAttribute("val")?.toFloatOrNull()?.div(100000f) ?: default
        val shade = fraction("a:shade", 1f) * fraction("a:lumMod", 1f)
        val tint = fraction("a:tint", 0f)
        val offset = fraction("a:lumOff", 0f)
        fun channel(shift: Int): Long = ((((value shr shift and 255) * shade + 255 * offset) * (1 - tint) + 255 * tint).toInt().coerceIn(0, 255)).toLong()
        return ((fraction("a:alpha", 1f).coerceIn(0f, 1f) * 255).toLong() shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
    fun parse(xml: String, rels: String, presentation: String, themeXml: String = "", masterXml: String = "", layoutXml: String = ""): PptxSlideLayout? = runCatching {
        val size = Regex("""<p:sldSz\b[^>]*>""").find(presentation)?.value?.let(::attributes)
        val width = size?.get("cx")?.toFloatOrNull() ?: 9144000f
        val height = size?.get("cy")?.toFloatOrNull() ?: 6858000f
        require(width > 0 && height > 0)
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        val builder = factory.newDocumentBuilder().apply { setEntityResolver { _, _ -> org.xml.sax.InputSource(java.io.StringReader("")) } }
        val root = builder.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))).documentElement
        fun optional(source: String) = source.takeIf { it.isNotBlank() }?.let { builder.parse(ByteArrayInputStream(it.toByteArray())).documentElement }
        val theme = optional(themeXml)
        val master = optional(masterXml)
        val inheritedLayout = optional(layoutXml)
        val colors = mutableMapOf("dk1" to 0xff000000L, "lt1" to 0xffffffffL, "tx1" to 0xff000000L, "bg1" to 0xffffffffL)
        theme?.first("a:clrScheme")?.childNodes?.let { nodes -> for (i in 0 until nodes.length) {
            val color = nodes.item(i) as? Element ?: continue
            parseColor(color)?.let { colors[color.tagName.substringAfter(':')] = it }
        } }
        val colorMap = root.first("a:overrideClrMapping") ?: inheritedLayout?.first("a:overrideClrMapping") ?: master?.first("p:clrMap")
        listOf("tx1" to "dk1", "tx2" to "dk2", "bg1" to "lt1", "bg2" to "lt2").forEach { (alias, fallback) ->
            colors[colorMap?.getAttribute(alias)?.ifBlank { fallback } ?: fallback]?.let { colors[alias] = it }
        }
        val bg = root.first("p:bg") ?: inheritedLayout?.first("p:bg") ?: master?.first("p:bg")
        if (bg?.first("a:blip") != null) return null
        val background = parseColor(bg, colors) ?: 0xffffffff
        // Group transforms, charts, SmartArt and tables need a fuller renderer.
        if (root.all("p:grpSp").isNotEmpty() || root.all("p:graphicFrame").isNotEmpty() || root.all("p:cxnSp").isNotEmpty()) return null
        val tree = root.first("p:spTree") ?: return null
        val targets = relationships(rels)
        val elements = mutableListOf<PptxVisualElement>()
        val children = tree.childNodes
        for (index in 0 until children.length) {
            val shape = children.item(index) as? Element ?: continue
            if (shape.tagName !in listOf("p:sp", "p:pic")) continue
            // A rectangle approximation would misrepresent these source objects.
            val geometry = shape.first("a:prstGeom")?.getAttribute("prst")
            if (geometry != null && geometry != "rect") return null
            if (shape.first("a:srcRect") != null || shape.first("a:gradFill") != null) return null
            val textBody = shape.first("p:txBody")
            val text = textBody?.all("a:p")?.joinToString("\n") { paragraph ->
                val words = buildString {
                    for (n in 0 until paragraph.childNodes.length) {
                        val node = paragraph.childNodes.item(n) as? Element ?: continue
                        if (node.tagName == "a:br") append('\n')
                        else node.all("a:t").forEach { append(it.textContent.orEmpty()) }
                    }
                }
                val bullet = paragraph.first("a:buChar")?.getAttribute("char").orEmpty()
                if (bullet.isNotBlank()) "$bullet $words" else words
            }.orEmpty()
            val placeholder = shape.first("p:ph")
            fun inheritedShape(source: Element?): Element? = if (placeholder == null) null else source?.all("p:sp")?.firstOrNull {
                val ph = it.first("p:ph")
                ph != null && ph.getAttribute("idx") == placeholder.getAttribute("idx") &&
                    ph.getAttribute("type").ifBlank { "body" } == placeholder.getAttribute("type").ifBlank { "body" }
            }
            val inherited = inheritedShape(inheritedLayout) ?: inheritedShape(master)
            val transform = shape.first("a:xfrm") ?: inherited?.first("a:xfrm") ?: if (text.isNotBlank() || shape.tagName == "p:pic") return null else continue
            val offset = transform.first("a:off") ?: return null
            val extent = transform.first("a:ext") ?: return null
            val x = offset.getAttribute("x").toFloatOrNull() ?: return null
            val y = offset.getAttribute("y").toFloatOrNull() ?: return null
            val w = extent.getAttribute("cx").toFloatOrNull() ?: return null
            val h = extent.getAttribute("cy").toFloatOrNull() ?: return null
            if (w <= 0 || h <= 0) continue
            val runStyle = textBody?.first("a:rPr") ?: textBody?.first("a:defRPr") ?: inherited?.first("a:defRPr")
            val points = runStyle?.getAttribute("sz")?.toFloatOrNull()?.div(100f) ?: 18f
            val id = shape.first("a:blip")?.getAttribute("r:embed")
            val path = id?.let(targets::get)?.let { target ->
                java.nio.file.Paths.get("ppt/slides").resolve(target).normalize().toString().replace('\\', '/')
            }
            if (shape.tagName == "p:pic" && path == null) return null
            val properties = shape.child("p:spPr")
            // A line's solidFill is not the shape's fill. Only direct properties occlude earlier objects.
            val solidFill = properties?.child("a:solidFill")
            val fill = parseColor(solidFill, colors)
            if (solidFill != null && fill == null) return null
            val body = textBody?.child("a:bodyPr") ?: inherited?.first("a:bodyPr")
            if (body?.getAttribute("vert")?.let { it.isNotBlank() && it != "horz" } == true) return null
            fun inset(name: String, fallback: Float) = body?.getAttribute(name)?.toFloatOrNull() ?: fallback
            val foreground = parseColor(textBody, colors) ?: parseColor(inherited?.first("p:txBody"), colors) ?: colors["tx1"] ?: 0xff202020L
            val readableForeground = readableSlideText(foreground, fill ?: background)
            val line = properties?.child("a:ln")
            val paths = shape.first("a:custGeom")?.all("a:path")?.map { vector ->
                val pw = vector.getAttribute("w").toFloatOrNull()?.takeIf { it > 0 } ?: w
                val ph = vector.getAttribute("h").toFloatOrNull()?.takeIf { it > 0 } ?: h
                val commands = (0 until vector.childNodes.length).mapNotNull { n ->
                    val command = vector.childNodes.item(n) as? Element ?: return@mapNotNull null
                    val kind = command.tagName.substringAfter(':')
                    if (kind !in listOf("moveTo", "lnTo", "cubicBezTo", "quadBezTo", "close")) return null
                    val points = command.all("a:pt").flatMap { point -> listOf(
                        (point.getAttribute("x").toFloatOrNull() ?: return null) / pw,
                        (point.getAttribute("y").toFloatOrNull() ?: return null) / ph) }
                    PptxPathCommand(kind, points)
                }
                PptxVectorPath(commands, vector.getAttribute("fill") != "none", vector.getAttribute("stroke") != "0")
            }.orEmpty()
            elements.add(PptxVisualElement(x / width, y / height, w / width, h / height, text, path,
                points * 12700 / width, runStyle?.getAttribute("b") == "1",
                textBody?.first("a:pPr")?.getAttribute("algn").orEmpty(), readableForeground,
                fill, transform.getAttribute("rot").toFloatOrNull()?.div(60000) ?: 0f,
                if (line?.first("a:noFill") != null) null else parseColor(line, colors),
                (line?.getAttribute("w")?.toFloatOrNull() ?: 9525f) / width, paths,
                inset("lIns", 91440f) / width, inset("tIns", 45720f) / height,
                inset("rIns", 91440f) / width, inset("bIns", 45720f) / height,
                body?.getAttribute("anchor").orEmpty().ifBlank { "t" }))
            if (elements.size > 200) return null
        }
        if (elements.none { it.text.isNotBlank() || it.mediaPath != null }) null
        else PptxSlideLayout(width / height, background, elements)
    }.getOrNull()
}

internal fun readableSlideText(foreground: Long, background: Long): Long {
    fun brightness(c: Long) = (c shr 16 and 255) * .2126 + (c shr 8 and 255) * .7152 + (c and 255) * .0722
    val bg = brightness(background)
    return if (kotlin.math.abs(brightness(foreground) - bg) >= 48) foreground else if (bg < 128) 0xffffffff else 0xff202020
}
