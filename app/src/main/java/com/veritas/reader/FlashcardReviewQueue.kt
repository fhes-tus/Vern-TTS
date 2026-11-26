package com.veritas.reader

/** A rating schedules the card once. Subsequent attempts belong to the open review. */
internal data class FlashcardReviewQueue(
    private val scheduledIds: Set<String> = emptySet(),
    val retryIds: List<String> = emptyList()
) {
    fun shouldSchedule(cardId: String): Boolean = cardId !in scheduledIds

    fun rate(cardId: String, recall: String, reviewingRetry: Boolean): FlashcardReviewQueue {
        val again = recall == SpacedRepetitionScheduler.BUCKET_AGAIN
        val remaining = retryIds - cardId
        val retries = when {
            !again -> remaining
            reviewingRetry || cardId !in retryIds -> remaining + cardId
            else -> retryIds
        }
        return FlashcardReviewQueue(scheduledIds + cardId, retries)
    }

    fun remove(cardId: String) = FlashcardReviewQueue(scheduledIds - cardId, retryIds - cardId)
}
