package com.veritas.reader

/** Snapshot of the scheduled queue, not a count of today's study sessions. */
data class StudyReviewSummary(val dueCards: List<FlashcardProgress>, val notDuePercent: Int) {
    companion object {
        fun from(cards: List<FlashcardProgress>, nowMillis: Long): StudyReviewSummary {
            val due = cards.filter { it.isDue(nowMillis) }
            val percent = if (cards.isEmpty()) 0 else ((cards.size - due.size).toLong() * 100 / cards.size).toInt()
            return StudyReviewSummary(due, percent)
        }
    }
}
