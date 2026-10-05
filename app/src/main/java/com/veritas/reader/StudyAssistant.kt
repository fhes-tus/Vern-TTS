package com.veritas.reader

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

data class Flashcard(
    val front: String,
    val back: String
)

data class QuizQuestion(
    val question: String,
    val answer: String,
    val options: List<String>,
    val explanation: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("question", question)
        .put("answer", answer)
        .put("options", JSONArray(options))
        .put("explanation", explanation)

    companion object {
        fun fromJson(json: JSONObject): QuizQuestion {
            val optArray = json.optJSONArray("options") ?: JSONArray()
            val opts = mutableListOf<String>()
            for (i in 0 until optArray.length()) {
                opts.add(optArray.optString(i))
            }
            return QuizQuestion(
                question = json.optString("question", ""),
                answer = json.optString("answer", ""),
                options = opts,
                explanation = json.optString("explanation", "")
            )
        }
    }
}

data class QuizSet(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val documentId: String = "",
    val questions: List<QuizQuestion>,
    val bestScore: Int = -1,
    val totalQuestions: Int = questions.size,
    val createdAtTimestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        val qArray = JSONArray()
        questions.forEach { qArray.put(it.toJson()) }
        return JSONObject()
            .put("id", id)
            .put("title", title)
            .put("documentId", documentId)
            .put("questions", qArray)
            .put("bestScore", bestScore)
            .put("totalQuestions", totalQuestions)
            .put("createdAtTimestamp", createdAtTimestamp)
    }

    companion object {
        fun fromJson(json: JSONObject): QuizSet {
            val qArray = json.optJSONArray("questions") ?: JSONArray()
            val qList = mutableListOf<QuizQuestion>()
            for (i in 0 until qArray.length()) {
                val qObj = qArray.optJSONObject(i) ?: continue
                qList.add(QuizQuestion.fromJson(qObj))
            }
            return QuizSet(
                id = json.optString("id", UUID.randomUUID().toString()),
                title = json.optString("title", "Untitled Quiz"),
                documentId = json.optString("documentId", ""),
                questions = qList,
                bestScore = json.optInt("bestScore", -1),
                totalQuestions = json.optInt("totalQuestions", qList.size),
                createdAtTimestamp = json.optLong("createdAtTimestamp", System.currentTimeMillis())
            )
        }
    }
}

/**
 * SuperMemo SM-2 Lite Spaced Repetition Scheduler.
 * Computes optimal review intervals (1d, 3d, 7d, 16d, 30d+) and tracks retention ease.
 */
object SpacedRepetitionScheduler {
    const val BUCKET_AGAIN = "again"
    const val BUCKET_HARD = "hard"
    const val BUCKET_GOOD = "good"
    const val BUCKET_EASY = "easy"

    fun rateCard(
        card: FlashcardProgress,
        rating: String,
        now: Long = System.currentTimeMillis()
    ): FlashcardProgress {
        val currentReps = card.repetitionCount
        val currentInterval = card.intervalDays
        val currentEase = card.easeFactor.coerceIn(1.3f, 3.0f)

        val newReps: Int
        val newInterval: Int
        val newEase: Float

        when (rating.lowercase()) {
            BUCKET_AGAIN -> {
                newReps = 0
                newInterval = 1
                newEase = (currentEase - 0.2f).coerceAtLeast(1.3f)
            }
            BUCKET_HARD -> {
                newReps = currentReps + 1
                newInterval = if (currentInterval <= 1) 1 else (currentInterval * 1.2f).roundToInt()
                newEase = (currentEase - 0.15f).coerceAtLeast(1.3f)
            }
            BUCKET_GOOD -> {
                newReps = currentReps + 1
                newInterval = when (currentReps) {
                    0 -> 1
                    1 -> 3
                    else -> (currentInterval * currentEase).roundToInt().coerceAtLeast(currentInterval + 1)
                }
                newEase = currentEase
            }
            BUCKET_EASY -> {
                newReps = currentReps + 1
                newInterval = when (currentReps) {
                    0 -> 3
                    1 -> 7
                    else -> (currentInterval * currentEase * 1.3f).roundToInt().coerceAtLeast(currentInterval + 2)
                }
                newEase = (currentEase + 0.15f).coerceAtMost(3.0f)
            }
            else -> return card
        }

        val nextDue = now + (newInterval.toLong() * 86_400_000L)

        return card.copy(
            recall = rating.lowercase(),
            nextReviewDueTimestamp = nextDue,
            intervalDays = newInterval,
            repetitionCount = newReps,
            easeFactor = newEase
        )
    }

    fun isDue(card: FlashcardProgress, now: Long = System.currentTimeMillis()): Boolean {
        return card.recall.isBlank() || card.nextReviewDueTimestamp <= now
    }

    fun previewNextInterval(card: FlashcardProgress, rating: String): String {
        val rated = rateCard(card, rating)
        return when {
            rated.intervalDays <= 1 -> "1d"
            else -> "${rated.intervalDays}d"
        }
    }
}

/**
 * Universal Resilient Study Parser.
 * Seamlessly parses:
 * 1. Raw JSON arrays or objects from direct LLM generation
 * 2. Markdown tables (| Term | Definition |)
 * 3. Numbered bold term definitions (1. **Term**: Definition)
 * 4. Lenient multi-line Q/A pairs tolerant of conversational AI chatter and various prompt formats.
 */
object AiResultParser {

    private val frontMarkers = listOf("q:", "front:", "question:", "term:", "concept:", "prompt:")
    private val backMarkers = listOf("a:", "back:", "answer:", "definition:", "meaning:", "explanation:")
    private val optionRegex = Regex("""^([A-Da-d0-9])[).:\-]\s*(.+)$""")
    private val answerLineRegex = Regex("""^(?:correct\s*)?answer(?:\s*key)?\s*[:\-]?\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val explanationRegex = Regex("""^(?:explanation|reason|why)\s*[:\-]?\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val boldDefRegex = Regex("""^\s*(?:\d+[.)]|[-*•])\s*\*\*([^*]+)\*\*[:\s—–-]+(.+)$""")

    /** Strips markdown emphasis, list numbering, and stray bullets from a line. */
    fun cleanLine(raw: String): String = raw
        .replace(Regex("""\*\*|__|`"""), "")
        .replace(Regex("""^\s*(?:[-*•]|\d+[.)])\s*"""), "")
        .trim()

    private fun markerContent(line: String, markers: List<String>): String? {
        val cleaned = cleanLine(line)
        val lower = cleaned.lowercase()
        markers.forEach { marker ->
            if (lower.startsWith(marker)) return cleaned.substring(marker.length).trim()
        }
        return null
    }

    fun isLikelyQuiz(text: String): Boolean {
        val distinctOptions = mutableSetOf<Char>()
        var hasAnswerLine = false
        text.lineSequence().forEach { rawLine ->
            val line = cleanLine(rawLine)
            val match = optionRegex.matchEntire(line)
            if (match != null) {
                val key = match.groupValues[1].uppercase().firstOrNull()
                if (key != null) {
                    val isAColon = (key == 'A' && (line.startsWith("A:", ignoreCase = true) || line.startsWith("Answer:", ignoreCase = true)))
                    if (!isAColon) {
                        distinctOptions.add(key)
                    }
                }
            }
            if (answerLineRegex.matches(line)) hasAnswerLine = true
        }
        val hasMultipleOptions = distinctOptions.contains('B') || distinctOptions.size >= 2
        return (hasMultipleOptions && (distinctOptions.size >= 2 || hasAnswerLine))
    }

    fun parseFlashcards(text: String): List<Flashcard> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return emptyList()

        // Strategy 1: JSON array or object detection
        parseFlashcardsFromJson(trimmed)?.let { if (it.isNotEmpty()) return it }

        // If the snippet is formatted as multiple choice options, it is a quiz, not flashcards
        if (isLikelyQuiz(trimmed)) return emptyList()

        // Strategy 2: Markdown table detection (| Term | Definition |)
        parseFlashcardsFromTable(trimmed).let { if (it.isNotEmpty()) return it }

        // Strategy 3: Numbered bold definitions (1. **Term**: Definition)
        parseFlashcardsFromBoldDefs(trimmed).let { if (it.isNotEmpty()) return it }

        // Strategy 4: Lenient line-by-line markers (Q: / A:)
        return parseFlashcardsFromMarkers(trimmed)
    }

    private fun parseFlashcardsFromJson(text: String): List<Flashcard>? {
        if (!text.contains('{') && !text.contains('[')) return null
        return runCatching {
            val cards = mutableListOf<Flashcard>()
            val jsonSnippet = extractJsonSubstring(text)
            if (jsonSnippet.startsWith("[")) {
                val array = JSONArray(jsonSnippet)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val front = obj.optString("front", "").ifBlank { obj.optString("question", "").ifBlank { obj.optString("term", "") } }
                    val back = obj.optString("back", "").ifBlank { obj.optString("answer", "").ifBlank { obj.optString("definition", "") } }
                    if (front.isNotBlank() && back.isNotBlank()) {
                        cards.add(Flashcard(front.trim(), back.trim()))
                    }
                }
            } else if (jsonSnippet.startsWith("{")) {
                val obj = JSONObject(jsonSnippet)
                val array = obj.optJSONArray("flashcards") ?: obj.optJSONArray("cards") ?: obj.optJSONArray("deck")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        val cardObj = array.optJSONObject(i) ?: continue
                        val front = cardObj.optString("front", "").ifBlank { cardObj.optString("question", "").ifBlank { cardObj.optString("term", "") } }
                        val back = cardObj.optString("back", "").ifBlank { cardObj.optString("answer", "").ifBlank { cardObj.optString("definition", "") } }
                        if (front.isNotBlank() && back.isNotBlank()) {
                            cards.add(Flashcard(front.trim(), back.trim()))
                        }
                    }
                }
            }
            cards.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun parseFlashcardsFromTable(text: String): List<Flashcard> {
        val tableLines = text.lineSequence().map { it.trim() }.filter { it.startsWith("|") && it.endsWith("|") }.toList()
        if (tableLines.size < 2) return emptyList()

        val cards = mutableListOf<Flashcard>()
        val skipHeaderWords = setOf("front", "back", "term", "definition", "question", "answer", "concept", "meaning", "#")

        for (line in tableLines) {
            if (line.contains("---")) continue
            val cols = line.split("|").map { cleanLine(it) }.filter { it.isNotBlank() }
            if (cols.size >= 2) {
                val front = cols[0]
                val back = cols[1]
                if (skipHeaderWords.contains(front.lowercase()) && skipHeaderWords.contains(back.lowercase())) {
                    continue
                }
                cards.add(Flashcard(front, back))
            }
        }
        return cards
    }

    private fun parseFlashcardsFromBoldDefs(text: String): List<Flashcard> {
        val cards = mutableListOf<Flashcard>()
        text.lineSequence().forEach { rawLine ->
            val match = boldDefRegex.find(rawLine)
            if (match != null) {
                val front = cleanLine(match.groupValues[1])
                val back = cleanLine(match.groupValues[2])
                if (front.isNotBlank() && back.isNotBlank() && !front.equals("note", true) && !front.equals("tip", true)) {
                    cards.add(Flashcard(front, back))
                }
            }
        }
        return cards
    }

    private fun parseFlashcardsFromMarkers(text: String): List<Flashcard> {
        val cards = mutableListOf<Flashcard>()
        var front: String? = null
        var back = StringBuilder()
        var inBack = false

        fun flush() {
            val f = front?.trim().orEmpty()
            val b = back.toString().trim()
            if (f.isNotBlank() && b.isNotBlank()) cards.add(Flashcard(f, b))
            front = null
            back = StringBuilder()
            inBack = false
        }

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            val frontContent = markerContent(line, frontMarkers)
            val backContent = markerContent(line, backMarkers)
            when {
                frontContent != null -> {
                    flush()
                    front = frontContent
                }
                backContent != null && front != null -> {
                    inBack = true
                    back.append(backContent)
                }
                line.isBlank() -> if (inBack) flush()
                inBack -> back.append(' ').append(cleanLine(line))
                front != null && markerContent(line, backMarkers) == null && line.isNotBlank() ->
                    front = "${front} ${cleanLine(line)}".trim()
            }
        }
        flush()
        return cards
    }

    fun parseQuiz(text: String): List<QuizQuestion> {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return emptyList()

        // Strategy 1: JSON detection
        parseQuizFromJson(trimmed)?.let { if (it.isNotEmpty()) return it }

        // Strategy 2: Structured line parser
        return parseQuizFromText(trimmed)
    }

    private fun parseQuizFromJson(text: String): List<QuizQuestion>? {
        if (!text.contains('{') && !text.contains('[')) return null
        return runCatching {
            val questions = mutableListOf<QuizQuestion>()
            val jsonSnippet = extractJsonSubstring(text)
            val array = if (jsonSnippet.startsWith("[")) {
                JSONArray(jsonSnippet)
            } else if (jsonSnippet.startsWith("{")) {
                val obj = JSONObject(jsonSnippet)
                obj.optJSONArray("quiz") ?: obj.optJSONArray("questions") ?: obj.optJSONArray("items")
            } else null

            if (array != null) {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val q = obj.optString("question", "").ifBlank { obj.optString("q", "") }
                    val explanation = obj.optString("explanation", "").ifBlank { obj.optString("reason", "") }
                    val opts = mutableListOf<String>()

                    val optArray = obj.optJSONArray("options")
                    if (optArray != null) {
                        for (j in 0 until optArray.length()) {
                            opts.add(optArray.optString(j))
                        }
                    } else {
                        val optObj = obj.optJSONObject("options")
                        if (optObj != null) {
                            listOf("A", "B", "C", "D", "a", "b", "c", "d").forEach { key ->
                                if (optObj.has(key)) opts.add(optObj.optString(key))
                            }
                        }
                    }

                    var ans = obj.optString("answer", "").ifBlank { obj.optString("correct", "") }
                    val ansIndex = obj.optInt("answerIndex", -1)
                    if (ansIndex in opts.indices) {
                        ans = opts[ansIndex]
                    } else if (ans.length == 1) {
                        val letterIdx = ans.uppercase()[0] - 'A'
                        if (letterIdx in opts.indices) ans = opts[letterIdx]
                    }

                    if (q.isNotBlank() && opts.size >= 2 && ans.isNotBlank()) {
                        questions.add(QuizQuestion(q.trim(), ans.trim(), opts, explanation.trim()))
                    }
                }
            }
            questions.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun parseQuizFromText(text: String): List<QuizQuestion> {
        val questions = mutableListOf<QuizQuestion>()
        var question: String? = null
        val options = linkedMapOf<String, String>()
        var answerKeyOrText: String? = null
        var explanation = ""

        fun flush() {
            val q = question?.trim().orEmpty()
            val answerRaw = answerKeyOrText?.trim().orEmpty()
            if (q.isNotBlank() && options.size >= 2 && answerRaw.isNotBlank()) {
                val answerLetter = answerRaw.take(1).uppercase()
                val answerText = options[answerLetter] ?: answerRaw
                questions.add(
                    QuizQuestion(
                        question = q,
                        answer = answerText,
                        options = options.values.toList(),
                        explanation = explanation.trim()
                    )
                )
            }
            question = null
            options.clear()
            answerKeyOrText = null
            explanation = ""
        }

        text.lineSequence().forEach { rawLine ->
            val line = cleanLine(rawLine)
            val questionContent = markerContent(rawLine.trim(), listOf("q:", "question:"))
            val optionMatch = optionRegex.matchEntire(line)
            val answerMatch = answerLineRegex.matchEntire(line)
            val explanationMatch = explanationRegex.matchEntire(line)

            when {
                questionContent != null -> {
                    flush()
                    question = questionContent
                }
                answerMatch != null && question != null -> {
                    answerKeyOrText = answerMatch.groupValues[1]
                }
                explanationMatch != null && question != null -> {
                    explanation = explanationMatch.groupValues[1]
                }
                optionMatch != null && question != null -> {
                    val key = optionMatch.groupValues[1].uppercase()
                    val value = optionMatch.groupValues[2].trim()
                    options[key] = value
                }
                (rawLine.trim().matches(Regex("""^\d+[.)]\s+.+\?$""")) || rawLine.trim().startsWith("**Q")) && question == null -> {
                    flush()
                    question = cleanLine(rawLine)
                }
                question != null && options.isEmpty() && answerKeyOrText == null && line.isNotBlank() -> {
                    question = "${question} $line".trim()
                }
            }
        }
        flush()
        return questions
    }

    private fun extractJsonSubstring(text: String): String {
        val firstBrace = text.indexOf('{')
        val firstBracket = text.indexOf('[')
        val start = when {
            firstBrace >= 0 && firstBracket >= 0 -> minOf(firstBrace, firstBracket)
            firstBrace >= 0 -> firstBrace
            firstBracket >= 0 -> firstBracket
            else -> return text
        }
        val lastBrace = text.lastIndexOf('}')
        val lastBracket = text.lastIndexOf(']')
        val end = maxOf(lastBrace, lastBracket)
        return if (end > start) text.substring(start, end + 1) else text
    }
}
