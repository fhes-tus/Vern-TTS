package com.veritas.reader.ui.screens

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.veritas.reader.ui.QuoteCitationStyle

/**
 * Pure text-editing logic for the general notes editor: inline markers, line prefixes
 * (headings/lists), and smart list continuation on Enter. Kept free of composable state so
 * it can be unit-tested — the editor delegates here.
 */
object VeritasNoteEditing {

    val LINE_PREFIX_REGEX = Regex("^(#{1,3} |[-*•] |\\d+\\. |> |(- \\[[ xX]\\] ))")
    private val NUMBERED_LINE_REGEX = Regex("^\\d+\\. .*")
    private val NUMBERED_PREFIX_REGEX = Regex("\\d+\\. ")
    private val LIST_ITEM_REGEX = Regex("^(\\s*)(?:([-*•])|(\\d+)\\.|(\\[[ xX]\\]))\\s+(.*)$")

    fun toggleChecklistItem(
        items: MutableList<Pair<Boolean, TextFieldValue>>,
        index: Int,
        isChecked: Boolean,
        moveCheckedToBottom: Boolean = true
    ): Int {
        if (index !in items.indices) return index
        val tfv = items[index].second
        if (isChecked && moveCheckedToBottom && items.size > 1) {
            items.removeAt(index)
            items.add(true to tfv)
            return items.lastIndex
        } else if (!isChecked && moveCheckedToBottom && items.size > 1) {
            items.removeAt(index)
            val firstChecked = items.indexOfFirst { it.first }
            val targetIdx = if (firstChecked >= 0) firstChecked else items.size
            items.add(targetIdx, false to tfv)
            return targetIdx
        } else {
            items[index] = isChecked to tfv
            return index
        }
    }

    fun addNewChecklistItem(
        items: MutableList<Pair<Boolean, TextFieldValue>>,
        addNewItemsToTop: Boolean = false,
        moveCheckedToBottom: Boolean = true,
        text: String = ""
    ): Int {
        val newItem = false to TextFieldValue(text, TextRange(text.length))
        return if (addNewItemsToTop) {
            items.add(0, newItem)
            0
        } else {
            val targetIdx = if (moveCheckedToBottom) {
                val firstChecked = items.indexOfFirst { it.first }
                if (firstChecked >= 0) firstChecked else items.size
            } else {
                items.size
            }
            items.add(targetIdx, newItem)
            targetIdx
        }
    }

    fun formatQuoteCitation(
        quote: String,
        bookTitle: String = "",
        author: String = "",
        style: QuoteCitationStyle = QuoteCitationStyle.MARKDOWN
    ): String {
        val cleanQuote = quote.trim().trim('"', '“', '”')
        val cite = when {
            bookTitle.isNotBlank() && author.isNotBlank() -> "— $author, $bookTitle"
            bookTitle.isNotBlank() -> "— $bookTitle"
            author.isNotBlank() -> "— $author"
            else -> ""
        }
        return when (style) {
            QuoteCitationStyle.MARKDOWN -> {
                val lines = cleanQuote.lines().map { "> \"$it\"" }.joinToString("\n")
                if (cite.isNotBlank()) "$lines\n> $cite" else lines
            }
            QuoteCitationStyle.CLEAN -> {
                if (cite.isNotBlank()) "\"$cleanQuote\"\n$cite" else "\"$cleanQuote\""
            }
            QuoteCitationStyle.ACADEMIC -> {
                if (cite.isNotBlank()) "\"$cleanQuote\" (In: $bookTitle by $author)" else "\"$cleanQuote\""
            }
        }
    }

    fun applyIndent(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val selMin = value.selection.min
        val selMax = value.selection.max
        val blockStart = text.lastIndexOf('\n', (selMin - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val blockEnd = text.indexOf('\n', selMax).let { if (it < 0) text.length else it }
        val block = text.substring(blockStart, blockEnd)
        val lines = block.split("\n")
        val indented = lines.joinToString("\n") { "  $it" }
        val nt = text.substring(0, blockStart) + indented + text.substring(blockEnd)
        val added = indented.length - block.length
        return TextFieldValue(nt, TextRange(selMin + 2, (selMax + added).coerceIn(0, nt.length)))
    }

    fun applyOutdent(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val selMin = value.selection.min
        val selMax = value.selection.max
        val blockStart = text.lastIndexOf('\n', (selMin - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val blockEnd = text.indexOf('\n', selMax).let { if (it < 0) text.length else it }
        val block = text.substring(blockStart, blockEnd)
        val lines = block.split("\n")
        var firstLineTrimmed = 0
        var totalTrimmed = 0
        val outdented = lines.mapIndexed { idx, line ->
            var trimmed = 0
            var l = line
            if (l.startsWith("  ")) {
                l = l.substring(2)
                trimmed = 2
            } else if (l.startsWith(" ")) {
                l = l.substring(1)
                trimmed = 1
            }
            if (idx == 0) firstLineTrimmed = trimmed
            totalTrimmed += trimmed
            l
        }.joinToString("\n")
        val nt = text.substring(0, blockStart) + outdented + text.substring(blockEnd)
        val newStart = (selMin - firstLineTrimmed).coerceAtLeast(blockStart)
        val newEnd = (selMax - totalTrimmed).coerceAtLeast(newStart)
        return TextFieldValue(nt, TextRange(newStart, newEnd))
    }

    fun toggleQuotePrefix(value: TextFieldValue): TextFieldValue = toggleLinePrefix(value, "> ")

    fun toggleTaskCheckbox(value: TextFieldValue): TextFieldValue = toggleLinePrefix(value, "- [ ] ")

    fun cycleHeading(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val selMin = value.selection.min
        val blockStart = text.lastIndexOf('\n', (selMin - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val blockEnd = text.indexOf('\n', selMin).let { if (it < 0) text.length else it }
        val line = text.substring(blockStart, blockEnd)
        val nextLine = when {
            line.startsWith("### ") -> line.removePrefix("### ")
            line.startsWith("## ") -> "### " + line.removePrefix("## ")
            line.startsWith("# ") -> "## " + line.removePrefix("# ")
            else -> "# $line"
        }
        val nt = text.substring(0, blockStart) + nextLine + text.substring(blockEnd)
        val shift = nextLine.length - line.length
        return TextFieldValue(nt, TextRange((selMin + shift).coerceIn(0, nt.length)))
    }

    /**
     * Wraps the selection (or inserts empty markers at the caret) with an inline marker,
     * or unwraps if the selection is already wrapped.
     */
    fun toggleInlineMarker(value: TextFieldValue, marker: String): TextFieldValue {
        val text = value.text
        val s = value.selection.min
        val e = value.selection.max
        val selected = text.substring(s, e)
        val mlen = marker.length
        return when {
            selected.length >= 2 * mlen && selected.startsWith(marker) && selected.endsWith(marker) -> {
                val inner = selected.substring(mlen, selected.length - mlen)
                val nt = text.substring(0, s) + inner + text.substring(e)
                TextFieldValue(nt, TextRange(s, s + inner.length))
            }
            s >= mlen && e + mlen <= text.length &&
                text.substring(s - mlen, s) == marker && text.substring(e, e + mlen) == marker -> {
                val nt = text.substring(0, s - mlen) + selected + text.substring(e + mlen)
                TextFieldValue(nt, TextRange(s - mlen, e - mlen))
            }
            else -> {
                val nt = text.substring(0, s) + marker + selected + marker + text.substring(e)
                val sel = if (s == e) TextRange(s + mlen) else TextRange(s + mlen, e + mlen)
                TextFieldValue(nt, sel)
            }
        }
    }

    /**
     * Toggles a line-level prefix (heading / list marker) across every line touched by the
     * selection. A numbered-list prefix numbers each selected line sequentially (1., 2., 3.,
     * …) so "select all → numbered list" numbers the whole block; bullets/headings apply the
     * same marker per line. With no selection it toggles the single caret line.
     */
    fun toggleLinePrefix(value: TextFieldValue, prefix: String): TextFieldValue {
        val text = value.text
        val selMin = value.selection.min
        val selMax = value.selection.max
        val blockStart = text.lastIndexOf('\n', (selMin - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        val blockEnd = text.indexOf('\n', selMax).let { if (it < 0) text.length else it }
        val block = text.substring(blockStart, blockEnd)
        val lines = block.split("\n")
        val isNumbered = prefix.matches(NUMBERED_PREFIX_REGEX)
        // Toggle off when every non-blank line already carries this kind of marker.
        val allHave = lines.all { line ->
            line.isBlank() || if (isNumbered) line.matches(NUMBERED_LINE_REGEX) else line.startsWith(prefix)
        }
        val rebuilt = if (allHave) {
            lines.joinToString("\n") { it.replaceFirst(LINE_PREFIX_REGEX, "") }
        } else {
            var n = 1
            lines.joinToString("\n") { line ->
                if (line.isBlank()) {
                    line
                } else {
                    val stripped = line.replaceFirst(LINE_PREFIX_REGEX, "")
                    if (isNumbered) "${n++}. $stripped" else "$prefix$stripped"
                }
            }
        }
        val nt = text.substring(0, blockStart) + rebuilt + text.substring(blockEnd)
        val newCaret = (blockStart + rebuilt.length).coerceIn(0, nt.length)
        return TextFieldValue(nt, TextRange(newCaret))
    }

    /**
     * Continues list markers when Enter is pressed and ends the list when Enter is pressed
     * on an empty item.
     *
     * Detection is diff-based rather than requiring the text to grow by exactly one char:
     * IMEs frequently commit an autocorrected word plus the newline as a single batch edit,
     * which is why naive +1 detection made numbering stop after a couple of items. We accept
     * any edit whose inserted segment ends with '\n' and whose caret sits right after it.
     */
    fun continueListOnNewline(old: TextFieldValue, candidate: TextFieldValue): TextFieldValue {
        val oldText = old.text
        val newText = candidate.text
        if (newText.length <= oldText.length) return candidate
        val caret = candidate.selection.start
        if (caret <= 0 || newText.getOrNull(caret - 1) != '\n') return candidate

        // Diff old vs new via common prefix/suffix to find the inserted segment.
        var prefixLen = 0
        val maxPrefix = minOf(oldText.length, newText.length)
        while (prefixLen < maxPrefix && oldText[prefixLen] == newText[prefixLen]) prefixLen++
        var suffixLen = 0
        val maxSuffix = minOf(oldText.length, newText.length) - prefixLen
        while (suffixLen < maxSuffix &&
            oldText[oldText.length - 1 - suffixLen] == newText[newText.length - 1 - suffixLen]
        ) suffixLen++
        val inserted = newText.substring(prefixLen, newText.length - suffixLen)
        // Only react to edits that actually introduced the newline the caret sits after.
        if (!inserted.contains('\n') || caret < prefixLen || caret > newText.length - suffixLen + 1) {
            return candidate
        }

        val before = newText.substring(0, caret - 1)
        val lineStart = before.lastIndexOf('\n') + 1
        val line = before.substring(lineStart)
        val match = LIST_ITEM_REGEX.find(line) ?: return candidate
        val indent = match.groupValues[1]
        val bullet = match.groupValues[2]
        val number = match.groupValues[3]
        val check = match.groupValues[4]
        val itemContent = match.groupValues[5]
        if (itemContent.isBlank()) {
            // Empty list item + Enter => remove the marker and end the list.
            val nt = newText.substring(0, lineStart) + newText.substring(caret)
            return candidate.copy(text = nt, selection = TextRange(lineStart))
        }
        val nextMarker = when {
            bullet.isNotEmpty() -> "$indent$bullet "
            number.isNotEmpty() -> "$indent${(number.toIntOrNull() ?: 1) + 1}. "
            check.isNotEmpty() -> "$indent[ ] "
            else -> return candidate
        }
        val nt = newText.substring(0, caret) + nextMarker + newText.substring(caret)
        return candidate.copy(text = nt, selection = TextRange(caret + nextMarker.length))
    }

    fun parseNoteBlocks(content: String): MutableList<NoteBlock> {
        if (content.isEmpty()) return mutableListOf(NoteBlock.Text(TextFieldValue("")))
        val regex = Regex("""(!\[(?:image|photo)?\]\([^)]+\)|\[image:[^]]+\]|\[image\]\([^)]+\)|\[audio\]\([^)]+\)|\[audio:[^]]+\]|\[video\]\([^)]+\)|\[video:[^]]+\]|\[file(?::[^\n]*?)?\]\((?:[^()\n]|\([^()\n]*\))+\)|\[file:[^]]+\])""", RegexOption.IGNORE_CASE)

        val blocks = mutableListOf<NoteBlock>()
        var lastIndex = 0
        regex.findAll(content).forEach { match ->
            var textBefore = content.substring(lastIndex, match.range.first)
            // Strip structural newline delimiter so no artificial blank space is forced above the attachment
            if (textBefore.endsWith("\n")) {
                textBefore = textBefore.substring(0, textBefore.length - 1)
            }
            if (textBefore.isNotEmpty() || blocks.isEmpty()) {
                blocks.add(NoteBlock.Text(TextFieldValue(textBefore)))
            }
            val matchStr = match.value
            when {
                matchStr.startsWith("![", ignoreCase = true) || matchStr.startsWith("[image", ignoreCase = true) -> {
                    val path = extractPathFromTag(matchStr)
                    blocks.add(NoteBlock.Image(path))
                }
                matchStr.startsWith("[audio", ignoreCase = true) -> {
                    val path = extractPathFromTag(matchStr)
                    blocks.add(NoteBlock.Audio(path))
                }
                matchStr.startsWith("[video", ignoreCase = true) -> {
                    val path = extractPathFromTag(matchStr)
                    blocks.add(NoteBlock.Video(path))
                }
                matchStr.startsWith("[file", ignoreCase = true) -> {
                    val path = extractPathFromTag(matchStr)
                    var fileName = ""
                    var sizeBytes = 0L
                    val parenIdx = matchStr.indexOf("](").let { if (it >= 0) it + 1 else -1 }
                    val tagPrefix = if (parenIdx >= 0) matchStr.substring(0, parenIdx) else matchStr
                    if (tagPrefix.startsWith("[file:", ignoreCase = true)) {
                        val metaStr = tagPrefix.substring(6).removeSuffix("]").trim()
                        val sizeIndex = metaStr.lastIndexOf(':')
                        val hasSize = sizeIndex >= 0 && metaStr.substring(sizeIndex + 1).toLongOrNull() != null
                        val parts = if (hasSize) listOf(metaStr.substring(0, sizeIndex), metaStr.substring(sizeIndex + 1)) else listOf(metaStr)
                        if (parts.isNotEmpty()) fileName = parts[0].trim().let { if (it.startsWith("~")) runCatching { java.net.URLDecoder.decode(it.drop(1), "UTF-8") }.getOrDefault(it) else it }
                        if (parts.size > 1) sizeBytes = parts[1].trim().toLongOrNull() ?: 0L
                    }
                    if (fileName.isBlank() && path.isNotBlank()) {
                        fileName = java.io.File(path).name
                    }
                    if (sizeBytes == 0L && path.isNotBlank()) {
                        runCatching {
                            val f = java.io.File(path)
                            if (f.exists()) sizeBytes = f.length()
                        }
                    }
                    blocks.add(NoteBlock.File(path = path, fileName = fileName, sizeBytes = sizeBytes))
                }
            }
            lastIndex = match.range.last + 1
            if (lastIndex < content.length && content[lastIndex] == '\n') {
                lastIndex++
            }
        }
        if (lastIndex < content.length) {
            val remaining = content.substring(lastIndex)
            blocks.add(NoteBlock.Text(TextFieldValue(remaining)))
        }
        if (blocks.isEmpty() || blocks.last() !is NoteBlock.Text) {
            blocks.add(NoteBlock.Text(TextFieldValue("")))
        }
        return blocks
    }

    private fun extractPathFromTag(tag: String): String {
        val parenStart = tag.indexOf("](").let { if (it >= 0) it + 1 else -1 }
        val parenEnd = tag.lastIndexOf(')')
        if (parenStart != -1 && parenEnd > parenStart) {
            return tag.substring(parenStart + 1, parenEnd).trim()
        }
        val colonIdx = tag.indexOf(':')
        val bracketEnd = tag.lastIndexOf(']')
        if (colonIdx != -1 && bracketEnd > colonIdx) {
            return tag.substring(colonIdx + 1, bracketEnd).trim()
        }
        return tag.trim()
    }

    fun serializeNoteBlocks(blocks: List<NoteBlock>): String {
        val sb = StringBuilder()
        for (i in blocks.indices) {
            when (val block = blocks[i]) {
                is NoteBlock.Text -> {
                    sb.append(block.value.text)
                }
                is NoteBlock.Image -> {
                    if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
                    sb.append("![image](").append(block.path).append(")")
                    if (i < blocks.lastIndex) sb.append("\n")
                }
                is NoteBlock.Audio -> {
                    if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
                    sb.append("[audio](").append(block.path).append(")")
                    if (i < blocks.lastIndex) sb.append("\n")
                }
                is NoteBlock.Video -> {
                    if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
                    sb.append("[video](").append(block.path).append(")")
                    if (i < blocks.lastIndex) sb.append("\n")
                }
                is NoteBlock.File -> {
                    if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append("\n")
                    if (block.fileName.isNotBlank() && block.sizeBytes > 0L) {
                        sb.append("[file:").append("~" + java.net.URLEncoder.encode(block.fileName, "UTF-8")).append(":").append(block.sizeBytes).append("](")
                    } else if (block.fileName.isNotBlank()) {
                        sb.append("[file:").append("~" + java.net.URLEncoder.encode(block.fileName, "UTF-8")).append("](")
                    } else {
                        sb.append("[file](")
                    }
                    sb.append(block.path).append(")")
                    if (i < blocks.lastIndex) sb.append("\n")
                }
            }
        }
        return sb.toString()
    }
}

sealed class NoteBlock {
    data class Text(var value: TextFieldValue) : NoteBlock()
    data class Image(val path: String) : NoteBlock()
    data class Audio(val path: String) : NoteBlock()
    data class Video(val path: String) : NoteBlock()
    data class File(val path: String, val fileName: String = "", val sizeBytes: Long = 0L) : NoteBlock()
}

internal data class NoteEditorSnapshot(val content: String, val checklist: Boolean, val blockIndex: Int, val selection: TextRange, val title: String = "", val at: Long = System.currentTimeMillis())
