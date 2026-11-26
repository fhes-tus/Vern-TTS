package com.veritas.reader

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale

data class TextImportOptions(
    val encodingId: String = TextImportEncodingCatalog.AUTO_DETECT_ID
)

data class TextImportEncoding(
    val id: String,
    val label: String,
    val charsetName: String? = null,
    val useDeclaredHtmlCharset: Boolean = false
) {
    val isAvailable: Boolean
        get() = charsetName == null || Charset.isSupported(charsetName)
}

object TextImportEncodingCatalog {
    const val AUTO_DETECT_ID = "auto_detect"
    const val DECLARED_HTML_ID = "declared_html"

    private val rawOptions: List<TextImportEncoding> = listOf(
        TextImportEncoding(AUTO_DETECT_ID, "Auto-detect"),
        TextImportEncoding(DECLARED_HTML_ID, "Declared in HTML", useDeclaredHtmlCharset = true),
        TextImportEncoding("utf_8", "Unicode (UTF-8)", "UTF-8"),
        TextImportEncoding("utf_16le", "Unicode (UTF-16LE)", "UTF-16LE"),
        TextImportEncoding("utf_16be", "Unicode (UTF-16BE)", "UTF-16BE"),
        TextImportEncoding("windows_1256", "Arabic (windows-1256)", "windows-1256"),
        TextImportEncoding("iso_8859_6", "Arabic (ISO-8859-6)", "ISO-8859-6"),
        TextImportEncoding("iso_8859_4", "Baltic (ISO-8859-4)", "ISO-8859-4"),
        TextImportEncoding("iso_8859_13", "Baltic (ISO-8859-13)", "ISO-8859-13"),
        TextImportEncoding("windows_1257", "Baltic (windows-1257)", "windows-1257"),
        TextImportEncoding("iso_8859_14", "Celtic (ISO-8859-14)", "ISO-8859-14"),
        TextImportEncoding("iso_8859_2", "Central European Latin-2 (ISO-8859-2)", "ISO-8859-2"),
        TextImportEncoding("windows_1250", "Central European (windows-1250)", "windows-1250"),
        TextImportEncoding("cp852", "Central European (cp852)", "IBM852"),
        TextImportEncoding("gb2312", "Chinese Simplified (GB2312)", "GB2312"),
        TextImportEncoding("gb18030", "Chinese Simplified (GB18030)", "GB18030"),
        TextImportEncoding("big5", "Chinese Traditional (big5)", "Big5"),
        TextImportEncoding("iso_8859_5", "Cyrillic (ISO-8859-5)", "ISO-8859-5"),
        TextImportEncoding("koi8_r", "Cyrillic (KOI8-R)", "KOI8-R"),
        TextImportEncoding("koi8_u", "Cyrillic (KOI8-U)", "KOI8-U"),
        TextImportEncoding("windows_1251", "Cyrillic (windows-1251)", "windows-1251"),
        TextImportEncoding("cp866", "Cyrillic/Russian DOS (cp-866)", "IBM866"),
        TextImportEncoding("ibm855", "Cyrillic/DOS Alt (IBM855)", "IBM855"),
        TextImportEncoding("x_mac_cyrillic", "Cyrillic/Mac (x-MacCyrillic)", "x-MacCyrillic"),
        TextImportEncoding("windows_1253", "Greek (windows-1253)", "windows-1253"),
        TextImportEncoding("iso_8859_7", "Greek (ISO-8859-7)", "ISO-8859-7"),
        TextImportEncoding("iso_8859_8_i", "Hebrew (ISO-8859-8-I)", "ISO-8859-8"),
        TextImportEncoding("iso_8859_8", "Hebrew (ISO-8859-8)", "ISO-8859-8"),
        TextImportEncoding("windows_1255", "Hebrew (windows-1255)", "windows-1255"),
        TextImportEncoding("shift_jis", "Japanese (Shift_JIS)", "Shift_JIS"),
        TextImportEncoding("euc_jp", "Japanese (EUC-JP)", "EUC-JP"),
        TextImportEncoding("iso_2022_jp", "Japanese (ISO-2022-JP)", "ISO-2022-JP"),
        TextImportEncoding("euc_kr", "Korean (EUC-KR)", "EUC-KR"),
        TextImportEncoding("iso_8859_10", "Nordic Latin-6 (ISO-8859-10)", "ISO-8859-10"),
        TextImportEncoding("iso_8859_3", "South European Latin-3 (ISO-8859-3)", "ISO-8859-3"),
        TextImportEncoding("iso_8859_9", "Turkish Latin-5 (ISO-8859-9)", "ISO-8859-9"),
        TextImportEncoding("windows_1254", "Turkish (windows-1254)", "windows-1254"),
        TextImportEncoding("windows_1258", "Vietnamese (windows-1258)", "windows-1258"),
        TextImportEncoding("iso_8859_1", "West European Latin-1 (ISO-8859-1)", "ISO-8859-1"),
        TextImportEncoding("iso_8859_15", "West European Latin-9 (ISO-8859-15)", "ISO-8859-15"),
        TextImportEncoding("windows_1252", "West European (windows-1252)", "windows-1252")
    )

    val options: List<TextImportEncoding> = rawOptions.filter { it.isAvailable }

    fun byId(id: String): TextImportEncoding =
        options.firstOrNull { it.id == id } ?: options.first()
}

internal data class TextDecodingResult(
    val text: String,
    val encodingLabel: String,
    val diagnostics: List<String>
)

internal object TextImportDecoder {
    // A single scan lets tags inside comments, raw text, and quoted attributes stay inert.
    private val tagAttributes = """(?:[^<>"']++|"[^"]*"|'[^']*')*"""
    private val htmlHeaderToken = Regex(
        """<!--.*?(?:-->|$)""" +
            """|<(script|style|title|textarea)(?=[\s/>])$tagAttributes>.*?(?:</\1\s*>|$)""" +
            """|<(/?)([a-z][a-z0-9:-]*)(?=[\s/>])($tagAttributes)>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val attribute = Regex("""(?is)([a-z][a-z0-9:_-]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""")
    private val charsetParameter = Regex("""(?i)(?:^|;)\s*charset\s*=\s*["']?\s*([A-Za-z0-9._-]+)""")
    private val xmlEncoding = Regex("""(?is)^<\?xml\s+[^?]*\bencoding\s*=\s*["']([^"']+)["'][^?]*\?>""")

    fun decode(bytes: ByteArray, options: TextImportOptions, isHtml: Boolean = false): TextDecodingResult {
        val requested = TextImportEncodingCatalog.byId(options.encodingId)
        val resolved = when {
            requested.useDeclaredHtmlCharset && isHtml -> {
                val declared = detectBom(bytes) ?: declaredHtmlCharset(bytes)?.let { charset ->
                    DecodeCandidate(charset, requested.label, "Declared HTML charset: ${charset.name()}.")
                }
                declared ?: autoDetectCandidate(bytes, isHtml = false).let {
                    it.copy(diagnostic = "No supported HTML charset was declared. ${it.diagnostic}")
                }
            }
            requested.useDeclaredHtmlCharset -> autoDetectCandidate(bytes, isHtml = false).let {
                it.copy(diagnostic = "HTML charset declarations apply only to HTML imports. ${it.diagnostic}")
            }
            requested.charsetName != null -> DecodeCandidate(
                charset = Charset.forName(requested.charsetName),
                requestLabel = requested.label,
                diagnostic = "Text decoded as ${requested.label}."
            )
            else -> autoDetectCandidate(bytes, isHtml)
        }

        val text = decodeLenient(bytes, resolved.charset).trimLeadingBom()
        return TextDecodingResult(
            text = text,
            encodingLabel = resolved.requestLabel,
            diagnostics = listOf(resolved.diagnostic)
        )
    }

    private fun autoDetectCandidate(bytes: ByteArray, isHtml: Boolean): DecodeCandidate {
        detectBom(bytes)?.let { return it }
        if (isHtml) {
            declaredHtmlCharset(bytes)?.let { charset ->
                return DecodeCandidate(charset, "Auto-detect", "Auto-detected declared HTML charset: ${charset.name()}.")
            }
        }
        if (decodeStrict(bytes, StandardCharsets.UTF_8) != null) {
            return DecodeCandidate(StandardCharsets.UTF_8, "Auto-detect", "Auto-detected Unicode (UTF-8).")
        }
        val fallback = Charset.forName("windows-1252")
        return DecodeCandidate(fallback, "Auto-detect", "Auto-detect fell back to West European (windows-1252).")
    }

    private fun detectBom(bytes: ByteArray): DecodeCandidate? {
        return when {
            bytes.startsWith(0xEF, 0xBB, 0xBF) ->
                DecodeCandidate(StandardCharsets.UTF_8, "Auto-detect", "Auto-detected Unicode (UTF-8) byte order mark.")
            bytes.startsWith(0xFF, 0xFE) ->
                DecodeCandidate(StandardCharsets.UTF_16LE, "Auto-detect", "Auto-detected Unicode (UTF-16LE) byte order mark.")
            bytes.startsWith(0xFE, 0xFF) ->
                DecodeCandidate(StandardCharsets.UTF_16BE, "Auto-detect", "Auto-detected Unicode (UTF-16BE) byte order mark.")
            else -> null
        }
    }

    private fun declaredHtmlCharset(bytes: ByteArray): Charset? {
        val header = String(bytes, 0, minOf(bytes.size, 4096), StandardCharsets.ISO_8859_1)
            .removePrefix("\u00EF\u00BB\u00BF").trimStart()
        for (token in htmlHeaderToken.findAll(header)) {
            val tagName = token.groups[3]?.value?.lowercase(Locale.ROOT) ?: continue
            val closing = token.groupValues[2].isNotEmpty()
            if (tagName == "body" || tagName == "head" && closing) break
            if (tagName != "meta" || closing) continue
            val attributes = attribute.findAll(token.groupValues[4]).associate { match ->
                match.groupValues[1].lowercase(Locale.ROOT) to
                    (match.groups[2]?.value ?: match.groups[3]?.value ?: match.groupValues[4])
            }
            val name = attributes["charset"] ?: attributes["content"]
                ?.takeIf { attributes["http-equiv"]?.trim().equals("content-type", ignoreCase = true) }
                ?.let { charsetParameter.find(it)?.groupValues?.get(1) }
            supportedCharset(name)?.let { return it }
        }
        return supportedCharset(xmlEncoding.find(header)?.groupValues?.get(1))
    }

    private fun supportedCharset(name: String?): Charset? = name?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
        ?.let { runCatching { Charset.forName(it) }.getOrNull() }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? {
        return try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }

    private fun decodeLenient(bytes: ByteArray, charset: Charset): String =
        charset.decode(ByteBuffer.wrap(bytes)).toString()

    private fun ByteArray.startsWith(vararg values: Int): Boolean =
        size >= values.size && values.indices.all { index -> (this[index].toInt() and 0xFF) == values[index] }

    private fun String.trimLeadingBom(): String = trimStart('\uFEFF')

    private data class DecodeCandidate(
        val charset: Charset,
        val requestLabel: String,
        val diagnostic: String
    )
}
