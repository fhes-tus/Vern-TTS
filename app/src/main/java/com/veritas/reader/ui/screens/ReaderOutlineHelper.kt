package com.veritas.reader.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.ReaderDocument
import com.veritas.reader.ReaderTextIndex
import com.veritas.reader.ReaderTextModel
import com.veritas.reader.VeritasDocumentOutlineEntry
import com.veritas.reader.VeritasPackStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

internal object SmartOutlineCache {
    private const val MAX_ENTRIES = 8
    private val cache = object : LinkedHashMap<String, List<SmartOutlineEntry>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SmartOutlineEntry>>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    fun get(key: String): List<SmartOutlineEntry>? {
        synchronized(cache) {
            return cache[key]
        }
    }

    fun put(key: String, entries: List<SmartOutlineEntry>) {
        synchronized(cache) {
            cache[key] = entries
        }
    }

    fun clear() {
        synchronized(cache) {
            cache.clear()
        }
    }
}

internal data class SmartOutlineEntry(
    val index: Int,
    val title: String,
    val preview: String,
    val isHeading: Boolean,
    val level: Int = 0,
    val pageNumber: Int? = null,
    val source: String = "Smart outline"
)

private val WHITESPACE_REGEX = Regex("""\s+""")
private val ALL_DOTS_REGEX = Regex("""^[.\s\u00b7\u2022]+$""")
private val PAGE_PREFIX_REGEX = Regex("""^\d{1,4}\b""")
private val TRAILING_DOTS_REGEX = Regex("""[.\s]{3,}$""")
private val LEADER_DOTS_PAGE_REGEX = Regex("""(?:\.\s*){2,}\s*\d{1,4}""")
private val STRUCTURAL_HEADING_WORD_REGEX = Regex("""^(chapter|part|section|book|adventure|volume)\b""", RegexOption.IGNORE_CASE)
private val LEADING_NUMBER_PUNCT_REGEX = Regex("""^\d+[.)\s]""")
private val DOTTED_LEVEL_4_REGEX = Regex("""^\d+\.\d+\.\d+\.\d+""")
private val DOTTED_LEVEL_3_REGEX = Regex("""^\d+\.\d+\.\d+""")
private val DOTTED_LEVEL_2_REGEX = Regex("""^\d+\.\d+""")
private val DOTTED_LEVEL_1_REGEX = Regex("""^\d+\.""")
private val CHAPTER_KEYWORD_REGEX = Regex("""^(CHAPTER|Chapter|PART|Part|BOOK|Book)\b""", RegexOption.IGNORE_CASE)
private val UNNUMBERED_TOC_LINE_REGEX = Regex("""^(Introduction|Prologue|Preface|Part\s+[IVXLCDM\d]+|Chapter\s+[IVXLCDM\d]+|Book\s+[IVXLCDM\d]+|Conclusions?|Epilogue|Appendix|Notes|Index)\b.*""", RegexOption.IGNORE_CASE)
private val MAJOR_SECTION_KEYWORD_REGEX = Regex("""^(CHAPTER|Chapter|PART|Part|BOOK|Book|INTRODUCTION|Introduction|CONCLUSION|Conclusion)\b""", RegexOption.IGNORE_CASE)
private val LEADING_HASH_REGEX = Regex("""^#{1,6}\s*""")
private val LEADING_PRINTED_PAGE_REGEX = Regex("""^(\d{1,4})\s+(\p{L}.*)$""")
private val SCENE_BREAK_PATTERN = Regex("""^(\*[\s*]{2,}|\-{3,}|§{1,3}|#{3,}|_{3,}|~{3,})$""")
private val LEADING_DIGITS_REGEX = Regex("""^\d+(\.\d+)*\s+""")
private val NON_ALPHANUM_REGEX = Regex("""[^\p{L}\p{N} ]+""")
private val HEADING_KEYWORD_REGEX = Regex(
    pattern = "^(chapter|section|part|unit|lesson|module|book|article|introduction|conclusion|summary|abstract|contents|references|appendix|glossary|index|foreword|preface|prologue|epilogue|bibliography|afterword|notes|citations|sources)\\b",
    option = RegexOption.IGNORE_CASE
)
private val ARABIC_HEADING_REGEX = Regex("""^\d+(\.\d+)*[.)\s:-]+""")
private val ROMAN_HEADING_REGEX = Regex("""^(?!I\b)[IVXLCDM]{1,7}[.)\s:-]+""")
private val LANDMARK_KEYWORD_REGEX = Regex(
    pattern = "^(Task|Requirement|Exercise|Solution|Example|Definition|Theorem|Lemma|Proof|Corollary|Proposition|Remark|Case|Scenario|Feature|Instruction|Step|Goal|Outcome|Impact|Conclusion|Recommendation|Background|Methodology|Result|Discussion|Future Work)\\b",
    option = RegexOption.IGNORE_CASE
)
private val UUID_REGEX = Regex("""^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""")
private val ROMAN_NUMERAL_REGEX = Regex("""^[IVXLCDM]+$""", RegexOption.IGNORE_CASE)
private val ARABIC_NUMERAL_REGEX = Regex("""^\d{1,4}$""")

internal fun isPureNumeral(s: String): Boolean {
    val t = s.trim().trimEnd('.', ':', ')')
    return ARABIC_NUMERAL_REGEX.matches(t) || (t.length <= 7 && ROMAN_NUMERAL_REGEX.matches(t))
}

internal fun parseNumeralValue(s: String): Int? {
    val t = s.trim().trimEnd('.', ':', ')')
    t.toIntOrNull()?.let { return it }
    return romanToInt(t.uppercase(Locale.ROOT))
}

private fun romanToInt(s: String): Int? {
    if (s.isEmpty() || !Regex("M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})").matches(s)) return null
    val values = mapOf('I' to 1, 'V' to 5, 'X' to 10, 'L' to 50, 'C' to 100, 'D' to 500, 'M' to 1000)
    var sum = 0
    var prev = 0
    for (char in s.reversed()) {
        val curr = values[char] ?: return null
        if (curr < prev) sum -= curr else sum += curr
        prev = curr
    }
    return sum.takeIf { it > 0 }
}

internal fun resolveHumanDocumentTitle(title: String, sourceLabel: String = ""): String {
    val trimmed = title.trim()
    val isUuidOrCache = UUID_REGEX.containsMatchIn(trimmed) ||
        trimmed.startsWith("temp_", ignoreCase = true) ||
        trimmed.startsWith("cached_", ignoreCase = true)
    if (isUuidOrCache) {
        val cleanSource = sourceLabel.trim()
        if (cleanSource.isNotBlank() && cleanSource != "PDF" && cleanSource != "Text" && cleanSource != "DOCX" && cleanSource != "EPUB") {
            return cleanSource
        }
        return "Document outline"
    }
    return trimmed.ifBlank { "Document outline" }
}
private val SENTENCE_LIKE_REGEX = Regex("""[.!?]\s+\p{Lu}""")

private const val MAX_SMART_OUTLINE_SCAN_SENTENCES = 1200
private const val MAX_SMART_OUTLINE_ENTRIES = 220
/** How many leading sentences may hold a contents page. */
private const val MAX_SMART_OUTLINE_TOC_SCAN = 400
/** How far a contents listing may run past its heading. */
private const val MAX_SMART_OUTLINE_TOC_SPAN = 120
/**
 * The span of sentences occupied by a table of contents, or null if there is none.
 *
 * Sentence splitting shreds a contents page: "The Sign of the Four . . . . . 63"
 * arrives as a chunk reading "1 The Sign of the Four ." with the page number split
 * away. Nothing about that fragment looks like a contents row any more — but it does
 * satisfy the numbered-heading rule, so each fragment became its own outline entry
 * pointing back at the contents page. Excluding the region by position is the only
 * reliable defence, since the pattern is gone by the time we see it.
 */
internal fun findContentsRange(
    chunks: List<String>,
    readerModel: ReaderTextModel? = null
): IntRange? {
    val startIndex = chunks.take(MAX_SMART_OUTLINE_TOC_SCAN).indexOfFirst { chunk ->
        val head = chunk.take(200).lowercase()
        head.contains("table of contents") ||
            head.contains("brief contents") ||
            head.contains("summary of contents") ||
            head.contains("index of chapters") ||
            chunk.lineSequence().any { line ->
                val trimmed = line.trim().lowercase()
                trimmed == "contents" || trimmed == "table of contents" ||
                    trimmed == "brief contents" || trimmed == "summary of contents"
            }
    }
    if (startIndex < 0) return null

    val tocPage = readerModel?.sentences?.getOrNull(startIndex)?.pageNumber

    // Walk forward while the chunks still look like listing debris or belong to the TOC page:
    var end = startIndex
    var misses = 0
    var index = startIndex + 1
    val maxEnd = (startIndex + MAX_SMART_OUTLINE_TOC_SPAN).coerceAtMost(chunks.lastIndex)
    while (index <= maxEnd) {
        val sentencePage = readerModel?.sentences?.getOrNull(index)?.pageNumber
        // If we have page numbers and we have moved past the TOC pages, stop
        if (sentencePage != null && tocPage != null && sentencePage > tocPage + 1) {
            break
        }

        val text = chunks[index].replace(WHITESPACE_REGEX, " ").trim()
        val onSameTocPage = sentencePage != null && tocPage != null && sentencePage == tocPage
        val isTocLine = looksLikeTableOfContentsRow(text) ||
            LEADING_NUMBER_PUNCT_REGEX.containsMatchIn(text) ||
            STRUCTURAL_HEADING_WORD_REGEX.containsMatchIn(text) ||
            UNNUMBERED_TOC_LINE_REGEX.containsMatchIn(text)
        val isDottedDebris = ALL_DOTS_REGEX.matches(text) ||
            PAGE_PREFIX_REGEX.containsMatchIn(text) ||
            TRAILING_DOTS_REGEX.containsMatchIn(text) ||
            LEADER_DOTS_PAGE_REGEX.containsMatchIn(text) ||
            (text.length < 8 && text.all { it.isDigit() })
        val debris = onSameTocPage ||
            text.isBlank() ||
            isDottedDebris ||
            isTocLine
        if (debris) {
            end = index
            misses = 0
        } else {
            misses++
            if (misses >= 3) break
        }
        index++
    }
    return startIndex..end
}

/** Position markers offered only when a document has no detectable structure. */
private const val MAX_SMART_OUTLINE_FALLBACK_MARKERS = 40
/** A weak-signal heading repeating this often is a running header. */
private const val MAX_OUTLINE_TITLE_REPEATS = 3

@Composable
internal fun SmartOutlineDialog(
    document: ReaderDocument,
    documentOutline: List<VeritasDocumentOutlineEntry>,
    currentIndex: Int,
    onJumpToDestination: (pageNumber: Int?, sentenceIndex: Int) -> Unit,
    onDismiss: () -> Unit,
    readerModel: ReaderTextModel? = null
) {
    var query by remember(document.id) { mutableStateOf("") }
    var jumpPage by remember(document.id) { mutableStateOf("") }
    val pageCount = readerModel?.pageCount?.coerceAtLeast(1) ?: 1
    val requestedPage = jumpPage.toIntOrNull()?.takeIf { it in 1..pageCount }
    var entries by remember(document.id, document.chunks.size, documentOutline.size) {
        mutableStateOf<List<SmartOutlineEntry>>(emptyList())
    }
    LaunchedEffect(document.id, document.chunks.size, documentOutline, readerModel) {
        withContext(Dispatchers.Default) {
            val cacheKey = "${document.id.orEmpty()}:${document.chunks.size}:${documentOutline.hashCode()}:${document.rawText.hashCode()}"
            val cached = SmartOutlineCache.get(cacheKey)
            if (cached != null) {
                entries = cached
                return@withContext
            }
            val result = if (documentOutline.isNotEmpty()) {
                documentOutline.mapNotNull { outline ->
                    val title = outline.title.replace(WHITESPACE_REGEX, " ").trim()
                    if (title.isBlank()) return@mapNotNull null
                    val preview = document.chunks.getOrNull(outline.targetIndex).orEmpty()
                        .replace(WHITESPACE_REGEX, " ").trim()
                    SmartOutlineEntry(
                        index = outline.targetIndex,
                        title = title,
                        preview = preview.take(180),
                        isHeading = true,
                        level = outline.level,
                        pageNumber = outline.pageNumber,
                        source = outline.source
                    )
                }.distinctBy { Triple(it.title, it.pageNumber, it.index) }
            } else {
                buildSmartOutline(document.chunks, readerModel)
            }
            SmartOutlineCache.put(cacheKey, result)
            entries = result
        }
    }
    val filteredEntries = remember(entries, query) {
        val needle = query.trim()
        if (needle.isBlank()) {
            entries
        } else {
            entries.filter { entry ->
                entry.title.contains(needle, ignoreCase = true) ||
                        entry.preview.contains(needle, ignoreCase = true) ||
                        entry.source.contains(needle, ignoreCase = true) ||
                        entry.pageNumber?.toString() == needle ||
                        (entry.index + 1).toString() == needle
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                Text(if (documentOutline.isNotEmpty()) "Table of contents" else "Smart outline")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    resolveHumanDocumentTitle(document.title, document.sourceLabel),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Filter outline") },
                    placeholder = { Text("Chapter, topic, sentence number…") },
                    singleLine = true,
                    shape = VeritasPackStyle.chipShape()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = jumpPage,
                        onValueChange = { jumpPage = it.filter(Char::isDigit).take(6) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Jump to page (1–$pageCount)") },
                        singleLine = true,
                        isError = jumpPage.isNotEmpty() && requestedPage == null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    Button(enabled = requestedPage != null, onClick = {
                        requestedPage?.let { page ->
                            val index = readerModel?.sentences?.firstOrNull { it.pageNumber >= page }?.index ?: currentIndex
                            onJumpToDestination(page, index)
                        }
                    }) { Text("Go") }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 390.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filteredEntries.isEmpty()) {
                        item {
                            Text(
                                "No outline matches.",
                                modifier = Modifier.padding(vertical = 18.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    itemsIndexed(
                        filteredEntries,
                        key = { index, entry -> "$index:${entry.index}:${entry.title}" }) { idx, entry ->
                        val nextEntryIndex = filteredEntries.getOrNull(idx + 1)?.index ?: Int.MAX_VALUE
                        val active = currentIndex >= entry.index && (currentIndex < nextEntryIndex || idx == filteredEntries.lastIndex)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = (entry.level.coerceIn(0, 5) * 14).dp)
                                .clickable { onJumpToDestination(entry.pageNumber, entry.index) },
                            shape = VeritasPackStyle.compactShape(),
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    active -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = VeritasPackStyle.surfaceAlpha())
                                    entry.isHeading -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = VeritasPackStyle.surfaceAlpha())
                                    else -> MaterialTheme.colorScheme.surface.copy(alpha = VeritasPackStyle.surfaceAlpha())
                                }
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    if (entry.source.startsWith("PDF") || entry.source.contains("table of contents", ignoreCase = true)) "☰" else if (entry.isHeading) "◆" else "§",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        entry.title,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (active || entry.isHeading) FontWeight.Black else FontWeight.SemiBold,
                                        color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    // Location only: Clean page number or sentence fallback
                                    Text(
                                        listOfNotNull(
                                            entry.pageNumber?.let { "Page $it" },
                                            if (entry.pageNumber == null) "Sentence ${entry.index + 1}" else null
                                        ).joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = VeritasPackStyle.chipShape()) { Text("Close") }
        }
    )
}

internal fun isSelfReferentialTocHeading(title: String): Boolean {
    val clean = cleanTocTitle(title).lowercase(Locale.ROOT)
        .trim(':', '-', '•', '·', ' ')
    return clean == "contents" || clean == "table of contents" ||
        clean == "brief contents" || clean == "summary of contents" ||
        clean == "index of chapters" || clean == "toc"
}

internal fun filterAndFormatNumberedOutlineEntries(entries: List<SmartOutlineEntry>): List<SmartOutlineEntry> {
    if (entries.isEmpty()) return emptyList()

    val numericIndices = mutableListOf<Int>()
    val numericValues = mutableListOf<Int>()

    entries.forEachIndexed { i, entry ->
        if (isPureNumeral(entry.title)) {
            val v = parseNumeralValue(entry.title)
            if (v != null) {
                numericIndices.add(i)
                numericValues.add(v)
            }
        }
    }

    // Determine which numeric entries belong to a valid sequence (at least 2 ascending entries)
    val validNumericSet = mutableSetOf<Int>()
    if (numericValues.size >= 2) {
        for (j in 0 until numericValues.size - 1) {
            val diff = numericValues[j + 1] - numericValues[j]
            if (diff in 1..5) {
                validNumericSet.add(numericIndices[j])
                validNumericSet.add(numericIndices[j + 1])
            }
        }
    }

    return entries.mapIndexedNotNull { i, entry ->
        if (isPureNumeral(entry.title)) {
            if (i in validNumericSet || entry.source.contains("table of contents", ignoreCase = true)) {
                val cleanNum = entry.title.trim().trimEnd('.', ':', ')')
                entry.copy(title = "Chapter $cleanNum")
            } else {
                // Solitary orphan number without sequence or TOC origin -> filter out!
                null
            }
        } else {
            entry
        }
    }
}

internal fun buildSmartOutline(
    chunks: List<String>,
    readerModel: ReaderTextModel? = null
): List<SmartOutlineEntry> {
    if (chunks.isEmpty()) return emptyList()

    val contentsRange = findContentsRange(chunks, readerModel)
    val tocStart = contentsRange?.first
    val tocPage = if (tocStart != null && readerModel != null) {
        readerModel.sentences.getOrNull(tocStart)?.pageNumber
    } else null

    val tocEntries = extractTableOfContentsOutline(chunks, readerModel)
    val headingEntries = extractHeadingOutline(chunks, contentsRange, readerModel)

    val allCandidates = tocEntries + headingEntries
    val hasBodyPages = allCandidates.any { entry ->
        val p = entry.pageNumber
        p != null && (tocPage == null || p > tocPage)
    }

    // Filter out self-referential headings and any entries pointing to the TOC page itself when body chapters exist
    val validEntries = allCandidates
        .filterNot { isSelfReferentialTocHeading(it.title) }
        .filterNot { entry ->
            hasBodyPages && contentsRange != null && entry.index in contentsRange
        }

    val deduplicated = dropRunningHeaders(validEntries)
        .distinctBy { it.index to normalizeOutlineNeedle(it.title) }

    val structuralEntries = deduplicated
        .distinctBy { it.index }
        .let(::dropRunningHeaders)
        .let(::filterAndFormatNumberedOutlineEntries)
        .sortedBy { it.index }
        .take(MAX_SMART_OUTLINE_ENTRIES)

    if (structuralEntries.isNotEmpty()) return structuralEntries

    // Layer 3: Narrative Scene Breaks (if headings are sparse)
    val sceneBreaks = extractSceneBreaks(chunks, readerModel)
    if (sceneBreaks.size >= 2) {
        return sceneBreaks.take(MAX_SMART_OUTLINE_ENTRIES)
    }

    // Layer 4: Page-Decade Milestones (Natural, 100% paginated)
    return extractPageMilestones(chunks, readerModel)
}

/**
 * Removes running headers that repeat across the book.
 *
 * A book title printed at the top of every page produces one identical candidate per
 * page — "The Hound of the Baskervilles" five times here — none of which is a section.
 *
 * Only the weak title-case and all-caps matches are filtered. Keyword and numbered
 * headings are left alone deliberately: "CHAPTER I." legitimately recurs once per
 * story in a collection, and frequency-filtering those would delete real entries.
 */
internal fun dropRunningHeaders(entries: List<SmartOutlineEntry>): List<SmartOutlineEntry> {
    val counts = entries.groupingBy { normalizeOutlineNeedle(it.title) }.eachCount()
    return entries.filter { entry ->
        val key = normalizeOutlineNeedle(entry.title)
        val repeats = counts[key] ?: 0
        if (repeats < MAX_OUTLINE_TITLE_REPEATS) return@filter true
        // Keep a repeated title only when it carries an explicit structural marker.
        STRUCTURAL_HEADING_WORD_REGEX.containsMatchIn(entry.title.trim()) ||
            LEADING_NUMBER_PUNCT_REGEX.containsMatchIn(entry.title.trim())
    }
}

/**
 * Parses a table-of-contents page into outline entries.
 *
 * Extracted PDF text mangles contents pages two ways this has to survive. Leader dots
 * end up inside the line ("The Red-Headed League . . . . . 119"), and adjacent rows
 * often merge, so a line arrives carrying the *previous* entry's page number on the
 * front ("63 The Adventures of Sherlock Holmes ... 119"). Both are stripped.
 *
 * Targets resolve from *after* the contents region. Searching the whole document
 * matched each title inside the contents listing itself, so every entry navigated
 * back to the table of contents instead of to its chapter.
 */
internal fun extractTableOfContentsOutline(
    chunks: List<String>,
    readerModel: ReaderTextModel? = null
): List<SmartOutlineEntry> {
    val contentsIndexes = mutableListOf<Int>()
    chunks.take(MAX_SMART_OUTLINE_TOC_SCAN).forEachIndexed { index, chunk ->
        val head = chunk.take(400).lowercase()
        val isContents = head.contains("table of contents") ||
            head.contains("brief contents") ||
            head.contains("summary of contents") ||
            head.contains("index of chapters") ||
            chunk.lineSequence().any { line ->
                val trimmed = line.trim().lowercase()
                trimmed == "contents" || trimmed == "table of contents" ||
                    trimmed == "brief contents" || trimmed == "summary of contents"
            }
        if (isContents) contentsIndexes.add(index)
    }
    if (contentsIndexes.isEmpty()) return emptyList()

    val tocStart = contentsIndexes.first()
    val tocPage = readerModel?.sentences?.getOrNull(tocStart)?.pageNumber
    val contentsRange = findContentsRange(chunks, readerModel)
    val tocEnd = if (tocPage != null) {
        val maxTocPage = tocPage + 2
        val lastSentenceOnTocPage = readerModel.sentences
            .indexOfLast { it.pageNumber <= maxTocPage && it.index <= tocStart + MAX_SMART_OUTLINE_TOC_SPAN }
            .takeIf { it >= tocStart } ?: tocStart
        contentsRange?.last?.coerceAtMost(lastSentenceOnTocPage) ?: lastSentenceOnTocPage
    } else {
        contentsRange?.last ?: (tocStart + 15).coerceAtMost(chunks.lastIndex)
    }
    val bodyStart = if (tocPage != null) {
        readerModel.sentences.indexOfFirst { it.pageNumber > tocPage && it.index > tocEnd }
            .takeIf { it >= 0 } ?: (tocEnd + 1).coerceAtMost(chunks.lastIndex)
    } else {
        (tocEnd + 1).coerceAtMost(chunks.lastIndex)
    }

    // Reassemble sentence fragments before parsing rows: dot leaders often split one row into many sentences.
    val contentsText = if (readerModel != null && readerModel.sentences.size == chunks.size) {
        (tocStart..tocEnd).joinToString("") { index -> readerModel.sentences[index].separatorBefore + chunks[index] }
    } else (tocStart..tocEnd).joinToString("\n") { chunks[it] }
    val contentsLines = contentsText.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
    val verifiedOffsets = contentsLines.mapNotNull { line ->
        val parsed = parseTocLine(line) ?: return@mapNotNull null
        val target = locateOutlineTarget(chunks, cleanTocTitle(parsed.first), bodyStart) ?: return@mapNotNull null
        readerModel?.sentences?.getOrNull(target)?.pageNumber?.minus(parsed.second)
    }.groupingBy { it }.eachCount()
    val pageOffset = verifiedOffsets.entries.maxByOrNull { it.value }?.takeIf { it.value >= 2 }?.key

    val seen = mutableSetOf<String>()
    val entries = mutableListOf<SmartOutlineEntry>()

    // First pass: try standard numbered/dot-leader TOC lines
    run {
        contentsLines.asSequence()
            .filter { it.length in 4..160 }
            .forEach { line ->
                val parsed = parseTocLine(line) ?: return@forEach
                val rawTitle = parsed.first
                val printedPage = parsed.second
                val title = cleanTocTitle(rawTitle)
                val isNum = isPureNumeral(title)
                if ((title.length < 3 && !isNum) || isSelfReferentialTocHeading(title)) return@forEach
                val formattedTitle = if (isNum) "Chapter ${title.trimEnd('.', ':')}" else title
                val key = normalizeOutlineNeedle(formattedTitle)
                if (key.length < 2 || !seen.add(key)) return@forEach

                var targetIndex: Int? = locateOutlineTarget(chunks, formattedTitle, bodyStart)
                    ?: if (isNum) locateOutlineTarget(chunks, title, bodyStart) else null
                var resolvedPage: Int? = targetIndex?.let { readerModel?.sentences?.getOrNull(it)?.pageNumber } ?: printedPage

                if (targetIndex == null && pageOffset != null && readerModel != null) {
                    val physicalPage = printedPage + pageOffset
                    if (physicalPage > (tocPage ?: 0) && physicalPage <= readerModel.pageCount) {
                        targetIndex = readerModel.sentences.firstOrNull { it.pageNumber >= physicalPage }?.index
                        resolvedPage = physicalPage
                    }
                }

                if (targetIndex == null) return@forEach

                if (resolvedPage == null && readerModel != null) {
                    resolvedPage = readerModel.sentences.getOrNull(targetIndex)?.pageNumber
                }

                val clean = chunks.getOrNull(targetIndex).orEmpty().replace(WHITESPACE_REGEX, " ").trim()
                val dottedLevel = when {
                    DOTTED_LEVEL_4_REGEX.containsMatchIn(formattedTitle) -> 3
                    DOTTED_LEVEL_3_REGEX.containsMatchIn(formattedTitle) -> 2
                    DOTTED_LEVEL_2_REGEX.containsMatchIn(formattedTitle) -> 1
                    DOTTED_LEVEL_1_REGEX.containsMatchIn(formattedTitle) -> 0
                    else -> if (CHAPTER_KEYWORD_REGEX.containsMatchIn(formattedTitle) || isNum) 0 else 1
                }
                entries.add(
                    SmartOutlineEntry(
                        index = targetIndex,
                        title = formattedTitle.take(96),
                        preview = clean.take(180),
                        isHeading = true,
                        level = dottedLevel,
                        pageNumber = resolvedPage,
                        source = "Printed table of contents"
                    )
                )
            }
    }

    // Second pass: unnumbered / hyperlinked / title-style TOC lines (e.g. Kahneman PDF Page 4: "Introduction", "Part 1: Two Systems", "1. The Characters of the Story", etc.)
    run {
        contentsLines.asSequence()
            .filter { it.length in 3..120 }
            .forEach { line ->
                if (isSelfReferentialTocHeading(line)) return@forEach
                val isChapterListing = looksLikeOutlineHeading(line) ||
                    UNNUMBERED_TOC_LINE_REGEX.containsMatchIn(line) ||
                    LEADING_NUMBER_PUNCT_REGEX.containsMatchIn(line)
                if (!isChapterListing) return@forEach

                val title = cleanTocTitle(line)
                if (title.length < 3 || isSelfReferentialTocHeading(title)) return@forEach
                val key = normalizeOutlineNeedle(title)
                if (key.length < 3 || !seen.add(key)) return@forEach

                val targetIndex = locateOutlineTarget(chunks, title, bodyStart) ?: return@forEach
                val resolvedPage = readerModel?.sentences?.getOrNull(targetIndex)?.pageNumber
                val clean = chunks.getOrNull(targetIndex).orEmpty().replace(WHITESPACE_REGEX, " ").trim()
                val isMajor = MAJOR_SECTION_KEYWORD_REGEX.containsMatchIn(title)

                entries.add(
                    SmartOutlineEntry(
                        index = targetIndex,
                        title = title.take(96),
                        preview = clean.take(180),
                        isHeading = true,
                        level = if (isMajor) 0 else 1,
                        pageNumber = resolvedPage,
                        source = "Printed table of contents"
                    )
                )
            }
    }

    return entries
}

/**
 * Strips leader dots, hashes, and a stray leading page number from a contents line.
 *
 * The number in "63 The Adventures of Sherlock Holmes" belongs to the row above it.
 * It is only dropped when enough text follows for that text to be the real title, so
 * a genuinely numbered heading is left intact.
 */
internal fun parseTocLine(line: String): Pair<String, Int>? {
    val trimmed = line.trim()
    if (trimmed.length !in 4..160) return null
    if (!trimmed.last().isDigit()) return null

    var i = trimmed.length - 1
    while (i >= 0 && trimmed[i].isDigit()) i--
    val digitLen = (trimmed.length - 1) - i
    if (digitLen !in 1..5 || i < 0) return null
    val pageNum = trimmed.substring(i + 1).toIntOrNull() ?: return null

    var wsCount = 0
    while (i >= 0 && (trimmed[i] == ' ' || trimmed[i] == '\t')) {
        if (trimmed[i] == '\t') wsCount += 4 else wsCount++
        i--
    }
    if (i < 0) return null

    var leaderCharCount = 0
    while (i >= 0 && (trimmed[i] in ".·•…-_" || trimmed[i] == ' ')) {
        if (trimmed[i] in ".·•…-_") leaderCharCount++
        i--
    }

    if (leaderCharCount < 2 && wsCount < 2) return null
    if (i < 0) return null

    val rawTitle = trimmed.substring(0, i + 1).trim()
    if (rawTitle.isEmpty() || rawTitle.length > 140) return null
    return Pair(rawTitle, pageNum)
}

/**
 * Strips leader dots, hashes, and a stray leading page number from a contents line.
 *
 * The number in "63 The Adventures of Sherlock Holmes" belongs to the row above it.
 * It is only dropped when enough text follows for that text to be the real title, so
 * a genuinely numbered heading is left intact.
 */
internal fun cleanTocTitle(raw: String): String {
    var title = raw.trim()
    // Strip leading markdown hashes (#, ##, ###, etc.)
    title = title.replace(LEADING_HASH_REGEX, "").trim()
    title = title.trim('.', '-', '\u2022', '\u00b7', ' ')

    // Strip trailing leader dots/dashes/bullets followed by trailing page number
    if (title.isNotEmpty() && title.last().isDigit()) {
        var i = title.length - 1
        while (i >= 0 && title[i].isDigit()) i--
        var ws = 0
        while (i >= 0 && (title[i] == ' ' || title[i] == '\t')) { ws++; i-- }
        var leaders = 0
        while (i >= 0 && (title[i] in ".·•…-_" || title[i] == ' ')) {
            if (title[i] in ".·•…-_") leaders++
            i--
        }
        if (leaders >= 2 || ws >= 2) {
            title = title.substring(0, i + 1).trim()
        }
    }

    // Strip trailing runs of leader dots, dashes, or ellipses without page numbers
    var end = title.length - 1
    var strippedLeaders = 0
    while (end >= 0 && (title[end] in ".·•…-_" || title[end] == ' ')) {
        if (title[end] in ".·•…-_") strippedLeaders++
        end--
    }
    if (strippedLeaders >= 2) {
        title = title.substring(0, end + 1).trim()
    }
    title = title.trim('.', '-', '\u2022', '\u00b7', ' ')

    // Strip leading printed page numbers (e.g. "12 Introduction") while preserving real numbered headings
    LEADING_PRINTED_PAGE_REGEX.matchEntire(title)?.let { m ->
        val rest = m.groupValues[2].trim()
        if (rest.length >= 4) title = rest
    }
    return title.replace(WHITESPACE_REGEX, " ").trim()
}
/**
 * Reassembles a heading the text extractor split mid-word.
 *
 * "CHAPTER II." routinely arrives as two chunks — "CHAPT" then "ER II." — because the
 * extractor breaks on the page's column boundary. Measured on The Complete Sherlock
 * Holmes: 33 occurrences of the fragment "CHAPT", against 30 intact "CHAPTER n."
 * lines, so roughly half the book's chapter headings were unreachable as headings and
 * showed up as meaningless stubs instead.
 *
 * A join is only attempted when the first fragment is a short run of letters with no
 * spaces and no terminal punctuation — a word cut in half, never a real short heading.
 */
internal fun joinSplitHeading(chunks: List<String>, index: Int): String? {
    val head = chunks.getOrNull(index)?.trim() ?: return null
    if (head.length > 8 || head.isEmpty()) return null
    if (head.any { it.isWhitespace() } || head.any { !it.isLetter() }) return null
    val tail = chunks.getOrNull(index + 1)?.trim().orEmpty()
    if (tail.isEmpty() || tail.first().isWhitespace()) return null
    val joined = (head + tail).trim()
    return joined.takeIf { it.length in 4..120 }
}


/**
 * Headings found in the body of the document.
 *
 * Contents-page rows are excluded. A line like "1 The Sign of the Four . . . . 63"
 * satisfies the numbered-heading rule, so every row of a table of contents used to
 * become its own outline entry pointing at the contents page — which is why the
 * outline read like the TOC and every entry jumped to the same few sentences.
 */
internal fun extractHeadingOutline(
    chunks: List<String>,
    contentsRange: IntRange? = null,
    readerModel: ReaderTextModel? = null
): List<SmartOutlineEntry> {
    // An adjacent run of numbered items is a list, not several section starts.
    // Real numbered sections have body text between them. Embedded bookmarks and
    // verified printed contents use their own paths and retain their numbering.
    val numberedItem = Regex("""^\s*(\d+|[IVXLCDM]+)[.)]\s+\S""")
    val listChunks = chunks.indices.filter { index ->
        val lines = chunks[index].lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        val first = lines.firstOrNull().orEmpty()
        numberedItem.containsMatchIn(first) && (
            lines.count { numberedItem.containsMatchIn(it) } >= 2 ||
            listOf(index - 1, index + 1).any { other ->
                chunks.getOrNull(other)?.trim()?.let { numberedItem.containsMatchIn(it) && it.length <= 120 } == true
            }
        )
    }.toSet()
    return chunks.mapIndexedNotNull { index, chunk ->
        if (contentsRange != null && index in contentsRange) return@mapIndexedNotNull null
        if (index in listChunks) return@mapIndexedNotNull null
        if (looksLikeTableOfContentsRow(chunk)) return@mapIndexedNotNull null

        var headingText: String? = null
        var isRepaired = false

        // A heading the extractor cut in half is repaired before it is judged.
        joinSplitHeading(chunks, index)?.let { repaired ->
            if (looksLikeOutlineHeading(repaired)) {
                headingText = repaired
                isRepaired = true
            }
        }

        if (headingText == null) {
            headingText = chunk.lineSequence()
                .map { it.trim() }
                .take(8)
                .firstOrNull { looksLikeOutlineHeading(it) }
                ?: chunk.replace(WHITESPACE_REGEX, " ").trim()
                    .takeIf { looksLikeOutlineHeading(it) }
                ?: return@mapIndexedNotNull null
        }

        val rawHashes = chunk.lineSequence().firstOrNull { it.trim().startsWith("#") }?.takeWhile { it == '#' }?.length ?: 0
        val clean = chunk.replace(WHITESPACE_REGEX, " ").trim()
        val pageNum = readerModel?.sentences?.getOrNull(index)?.pageNumber
        val cleanTitle = outlineTitle(cleanTocTitle(headingText), index)
        if (isSelfReferentialTocHeading(cleanTitle) || isSelfReferentialTocHeading(headingText)) return@mapIndexedNotNull null

        val dottedLevel = when {
            DOTTED_LEVEL_4_REGEX.containsMatchIn(cleanTitle) -> 3
            DOTTED_LEVEL_3_REGEX.containsMatchIn(cleanTitle) -> 2
            DOTTED_LEVEL_2_REGEX.containsMatchIn(cleanTitle) -> 1
            DOTTED_LEVEL_1_REGEX.containsMatchIn(cleanTitle) -> 0
            else -> -1
        }
        val level = when {
            rawHashes in 1..4 -> rawHashes - 1
            dottedLevel >= 0 -> dottedLevel
            CHAPTER_KEYWORD_REGEX.containsMatchIn(cleanTitle) -> 0
            else -> 1
        }

        SmartOutlineEntry(
            index = index,
            title = cleanTitle,
            preview = if (isRepaired || clean.startsWith(headingText)) clean.removePrefix(headingText).trim().take(180) else clean.take(180),
            isHeading = true,
            level = level,
            pageNumber = pageNum,
            source = "Document heading"
        )
    }
}

internal fun extractSceneBreaks(
    chunks: List<String>,
    readerModel: ReaderTextModel? = null
): List<SmartOutlineEntry> {
    val entries = mutableListOf<SmartOutlineEntry>()
    val seenPages = mutableSetOf<Int>()

    chunks.forEachIndexed { index, chunk ->
        val trimmed = chunk.trim()
        val isSceneBreak = SCENE_BREAK_PATTERN.matches(trimmed) ||
            trimmed == "* * *" || trimmed == "***" || trimmed == "---"
        if (isSceneBreak) {
            val pageNum = readerModel?.sentences?.getOrNull(index)?.pageNumber
            if (pageNum == null || seenPages.add(pageNum)) {
                val nextChunk = chunks.getOrNull(index + 1)?.replace(WHITESPACE_REGEX, " ")?.trim().orEmpty()
                entries.add(
                    SmartOutlineEntry(
                        index = index,
                        title = "§ Scene Break",
                        preview = nextChunk.take(180),
                        isHeading = false,
                        level = 1,
                        pageNumber = pageNum,
                        source = "Scene break"
                    )
                )
            }
        }
    }
    return entries
}

internal fun extractPageMilestones(
    chunks: List<String>,
    readerModel: ReaderTextModel? = null
): List<SmartOutlineEntry> {
    val totalPages = readerModel?.let {
        maxOf(it.pageCount, it.sentences.maxOfOrNull { s -> s.pageNumber } ?: 1)
    } ?: 1

    if (readerModel != null && totalPages > 1) {
        val step = when {
            totalPages <= 20 -> 2
            totalPages <= 50 -> 5
            totalPages <= 150 -> 10
            totalPages <= 300 -> 20
            else -> 25
        }
        val milestonePages = (1..totalPages step step).toMutableList()
        if (milestonePages.lastOrNull() != totalPages) {
            milestonePages.add(totalPages)
        }
        val sentencesByPage = readerModel.sentences.groupBy { it.pageNumber }
        return milestonePages.mapNotNull { page ->
            val pageSentences = sentencesByPage[page].orEmpty()
            val firstSentence = pageSentences.firstOrNull() ?: return@mapNotNull null
            val preview = firstSentence.text.replace(WHITESPACE_REGEX, " ").trim().take(180)
            SmartOutlineEntry(
                index = firstSentence.index,
                title = "Page $page",
                preview = preview,
                isHeading = false,
                level = 0,
                pageNumber = page,
                source = "Page milestone"
            )
        }
    }

    val markerCount = MAX_SMART_OUTLINE_FALLBACK_MARKERS.coerceAtMost(chunks.size)
    if (markerCount <= 0) return emptyList()
    val step = (chunks.size / markerCount).coerceAtLeast(1)
    val fallbackIndexes = (0 until chunks.size step step).toMutableList().also { marks ->
        if (chunks.isNotEmpty() && marks.lastOrNull() != chunks.lastIndex) marks.add(chunks.lastIndex)
    }

    return fallbackIndexes.mapNotNull { index ->
        val chunk = chunks.getOrNull(index).orEmpty()
        val clean = chunk.replace(WHITESPACE_REGEX, " ").trim()
        if (clean.isBlank()) return@mapNotNull null
        SmartOutlineEntry(
            index = index,
            title = "Sentence ${index + 1}",
            preview = clean.take(180),
            isHeading = false,
            level = 0,
            pageNumber = null,
            source = "Sentence milestone"
        )
    }.take(MAX_SMART_OUTLINE_ENTRIES)
}

/**
 * True for a line shaped like a contents listing: leader dots or a wide gap followed
 * by a page number, or a run of leader dots on its own.
 */
internal fun looksLikeTableOfContentsRow(chunk: String): Boolean {
    val line = chunk.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
    if (line.isBlank()) return false
    if (ALL_DOTS_REGEX.matches(line)) return true
    return ReaderTextIndex.looksLikeTableOfContentsRow(line)
}

/**
 * Finds where a contents title actually appears in the body.
 *
 * [from] skips the contents region so a title cannot resolve to its own listing, and
 * the scan runs to the end of the document rather than stopping at a fixed window —
 * chapter headings in a long book sit far past any leading cap.
 */
internal fun locateOutlineTarget(chunks: List<String>, title: String, from: Int = 0): Int? {
    val needle = normalizeOutlineNeedle(title)
    if (needle.length < 4) return null
    for (index in from.coerceAtLeast(0)..chunks.lastIndex) {
        if (chunks[index].lineSequence().any { normalizeOutlineNeedle(it) == needle }) return index
    }
    val firstWord = needle.split(' ').firstOrNull { it.length >= 3 }
    for (index in from..chunks.lastIndex) {
        val chunk = chunks[index]
        if (firstWord != null && !chunk.contains(firstWord, ignoreCase = true)) continue
        if (normalizeOutlineNeedle(chunk.take(600)).contains(needle)) return index
    }
    val compact = needle.split(' ').take(6).joinToString(" ")
    if (compact.length >= 8) {
        val compactFirstWord = compact.split(' ').firstOrNull { it.length >= 3 }
        for (index in from..chunks.lastIndex) {
            val chunk = chunks[index]
            if (compactFirstWord != null && !chunk.contains(compactFirstWord, ignoreCase = true)) continue
            if (normalizeOutlineNeedle(chunk.take(600)).contains(compact)) return index
        }
    }
    return null
}
internal fun normalizeOutlineNeedle(value: String): String {
    return value
        .replace(LEADING_DIGITS_REGEX, "")
        .replace(NON_ALPHANUM_REGEX, " ")
        .replace(WHITESPACE_REGEX, " ")
        .trim()
        .lowercase(Locale.ROOT)
}

internal fun outlineTitle(source: String, index: Int): String {
    val clean = source.replace(WHITESPACE_REGEX, " ").trim()
    if (clean.isBlank()) return "Sentence ${index + 1}"

    val sentenceEnd = clean.indexOfAny(charArrayOf('.', '!', '?'))
    val title = if (sentenceEnd in 20..120) clean.take(sentenceEnd + 1) else clean.take(96)
    return title.trim().ifBlank { "Sentence ${index + 1}" }
}

internal fun looksLikeOutlineHeading(firstLine: String): Boolean {
    val trimmed = firstLine.trim()
    if (Regex("^#{1,6}\\s+\\S").containsMatchIn(trimmed)) return trimmed.length <= 160
    if (Regex("""^(?:[•●◦▪‣⁃*+-]|\[[ xX]\])\s+""").containsMatchIn(trimmed)) return false

    val clean = trimmed.trim(':', '-', '•', '#').trim()
    val isNumeralCandidate = isPureNumeral(clean)
    if (!isNumeralCandidate && clean.length !in 3..120) return false
    if (clean.length > 120) return false

    val words = clean.split(WHITESPACE_REGEX).filter { word -> word.any { it.isLetter() } }

    val headingKeyword = HEADING_KEYWORD_REGEX.containsMatchIn(clean)
    val arabicHeading = ARABIC_HEADING_REGEX.containsMatchIn(clean)
    val romanHeading = ROMAN_HEADING_REGEX.containsMatchIn(clean)
    val numberedHeading = arabicHeading || romanHeading || isNumeralCandidate

    val landmarkKeyword = LANDMARK_KEYWORD_REGEX.containsMatchIn(clean)

    val titleCaseWords =
        words.count { word -> word.firstOrNull { it.isLetter() }?.isUpperCase() == true }
    val mostlyTitleCase =
        words.isNotEmpty() && titleCaseWords >= maxOf(1, (words.size * 0.70f).roundToInt())
    val allCaps =
        words.isNotEmpty() && words.all { word -> word.all { !it.isLetter() || it.isUpperCase() } }
    val compactHeading = !clean.endsWith(".") && clean.count { it == ',' } <= 1 && clean.length < 90

    // A heading is a label, not a sentence. Sentence-like punctuation disqualifies the
    // weaker signals even when a keyword matched.
    val structuralNumber = Regex("^(chapter|part|book|section|unit|lesson|module|volume)\\s+(?:[IVXLCDM]+|\\d+)[.:]?$", RegexOption.IGNORE_CASE).matches(clean)
    val sentenceLike = (clean.endsWith(".") && !structuralNumber) || clean.endsWith("?") || clean.endsWith("!") || clean.length > 90 || clean.count { it == ',' } > 1 ||
        SENTENCE_LIKE_REGEX.containsMatchIn(clean.replace(Regex("""^(?:\d+|[IVXLCDM]+)[.)]\s+"""), ""))
    if (sentenceLike) return false

    // A bare page number off a running header is not a heading (unless it is a candidate numeral evaluated in sequence)
    if (!isNumeralCandidate && clean.none { it.isLetter() }) return false

    // Neither is a one-word fragment such as the "CHAPT" left behind when a running
    // header is split mid-word. Real one-word headings ("Introduction", "Appendix")
    // come through the keyword rules instead. Numbered headings ("1. Introduction")
    // are also valid with a single following word.
    if (words.size < 2 && !headingKeyword && !landmarkKeyword && !numberedHeading) return false

    return headingKeyword || numberedHeading || landmarkKeyword ||
        ((mostlyTitleCase || allCaps) && compactHeading)
}




