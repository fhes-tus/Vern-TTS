package com.veritas.desktop.study

import com.veritas.desktop.models.Flashcard
import com.veritas.desktop.models.QuizQuestion
import com.veritas.desktop.models.StudyPack
import java.util.Locale

object StudyAssistant {

    fun generateStudyPack(title: String, chunks: List<String>, currentIndex: Int = 0): StudyPack {
        val summary = generateSummary(chunks)
        val keyPoints = extractKeyPoints(chunks)
        val flashcards = generateFlashcards(title, chunks)
        val quiz = generateQuiz(title, chunks)

        return StudyPack(
            summary = summary,
            keyPoints = keyPoints,
            flashcards = flashcards,
            quizQuestions = quiz
        )
    }

    private fun generateSummary(chunks: List<String>): String {
        if (chunks.isEmpty()) return "No content available to summarize."
        if (chunks.size <= 5) return chunks.joinToString(" ")

        // Select informative introductory, middle, and concluding sentences
        val sample = mutableListOf<String>()
        sample.add(chunks.first())

        val step = (chunks.size / 4).coerceAtLeast(1)
        for (i in step until chunks.size - 1 step step) {
            val chunk = chunks[i]
            if (chunk.length in 30..220 && !sample.contains(chunk)) {
                sample.add(chunk)
            }
            if (sample.size >= 4) break
        }

        if (!sample.contains(chunks.last())) {
            sample.add(chunks.last())
        }

        return sample.joinToString(" ")
    }

    private fun extractKeyPoints(chunks: List<String>): List<String> {
        val points = mutableListOf<String>()
        val markerWords = listOf(
            "important", "key", "crucial", "essential", "first", "second", "therefore",
            "however", "consequently", "specifically", "remember", "must", "result"
        )

        for (chunk in chunks) {
            val lower = chunk.lowercase(Locale.getDefault())
            if (markerWords.any { lower.contains(it) } && chunk.length in 25..200) {
                points.add(chunk)
            }
            if (points.size >= 6) break
        }

        if (points.isEmpty()) {
            points.addAll(chunks.take(4))
        }

        return points
    }

    private fun generateFlashcards(title: String, chunks: List<String>): List<Flashcard> {
        val cards = mutableListOf<Flashcard>()

        // Definition patterns: "X is defined as Y", "X refers to Y", "X means Y"
        val definitionRegex = Regex("([A-Z][A-Za-z0-9\\s]{2,25})\\s+(is|are|refers to|means|represents)\\s+(.+)", RegexOption.IGNORE_CASE)

        for (chunk in chunks) {
            val match = definitionRegex.find(chunk)
            if (match != null && match.groupValues.size >= 4) {
                val term = match.groupValues[1].trim()
                val def = match.groupValues[3].trim()
                if (term.split(" ").size <= 5 && def.length > 10) {
                    cards.add(
                        Flashcard(
                            question = "What is $term?",
                            answer = "$term ${match.groupValues[2]} $def"
                        )
                    )
                }
            }
            if (cards.size >= 8) break
        }

        // Fallbacks if few definitions found
        if (cards.size < 3) {
            for ((idx, chunk) in chunks.take(5).withIndex()) {
                if (chunk.length > 20) {
                    cards.add(
                        Flashcard(
                            question = "Core concept from section ${idx + 1} of \"$title\":",
                            answer = chunk
                        )
                    )
                }
            }
        }

        return cards
    }

    private fun generateQuiz(title: String, chunks: List<String>): List<QuizQuestion> {
        val questions = mutableListOf<QuizQuestion>()

        for ((idx, chunk) in chunks.take(6).withIndex()) {
            if (chunk.length in 30..220) {
                val words = chunk.split(" ")
                if (words.size >= 6) {
                    val keyWord = words.filter { it.length > 5 }.randomOrNull() ?: words[words.size / 2]
                    val blankedSentence = chunk.replaceFirst(keyWord, "______", ignoreCase = true)

                    val options = mutableListOf(keyWord, "Factor", "Process", "Element").shuffled()
                    val correctIdx = options.indexOf(keyWord)

                    questions.add(
                        QuizQuestion(
                            question = "Fill in the blank: \"$blankedSentence\"",
                            options = options,
                            correctIndex = correctIdx,
                            explanation = "Full context: \"$chunk\""
                        )
                    )
                }
            }
            if (questions.size >= 4) break
        }

        return questions
    }
}
