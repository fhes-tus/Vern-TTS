package com.veritas.reader

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

data class FlashcardWidgetDeck(
    val id: String,
    val title: String,
    val totalCount: Int,
    val dueCount: Int
)

class FlashcardWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FlashcardWidget()
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        removeFlashcardWidgetState(context, appWidgetIds)
        super.onDeleted(context, appWidgetIds)
    }
}

class FlashcardWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = DocumentRepository(context)
        val allCards = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repository.loadAllFlashcards() }
        val prefs = flashcardWidgetPreferences(context, id)
        val storedDeckId = prefs.getString("selected_deck_id", "").orEmpty()
        // Adopt a legacy deck name only when it identifies exactly one set.
        val selectedDeckId = when {
            storedDeckId.isBlank() || storedDeckId == "__ALL__" || allCards.any { it.setId == storedDeckId } -> storedDeckId
            else -> allCards.filter { it.setName == storedDeckId }.map { it.setId }.distinct().singleOrNull().orEmpty()
        }
        if (selectedDeckId != storedDeckId) prefs.edit().putString("selected_deck_id", selectedDeckId).putBoolean("show_answer", false).commit()

        val decks = mutableListOf<FlashcardWidgetDeck>()
        if (allCards.isNotEmpty()) {
            val totalDue = allCards.count { it.isDue() }
            decks.add(
                FlashcardWidgetDeck(
                    id = "__ALL__",
                    title = "All Flashcards",
                    totalCount = allCards.size,
                    dueCount = totalDue
                )
            )
            val grouped = allCards.groupBy { it.setId }
            grouped.forEach { (setId, cards) ->
                val name = cards.firstOrNull()?.setName?.ifBlank { "General Deck" } ?: "General Deck"
                val due = cards.count { it.isDue() }
                decks.add(
                    FlashcardWidgetDeck(
                        id = setId,
                        title = name,
                        totalCount = cards.size,
                        dueCount = due
                    )
                )
            }
        }

        val deckCards = if (selectedDeckId.isBlank()) {
            emptyList()
        } else if (selectedDeckId == "__ALL__") {
            allCards
        } else {
            allCards.filter { it.setId == selectedDeckId }
        }

        val isReviewMode = selectedDeckId.isNotBlank() && deckCards.isNotEmpty()

        val dueCards = if (isReviewMode) {
            deckCards.filter { it.isDue() }
        } else emptyList()

        val activeDeck = if (prefs.getBoolean("practice_mode", false)) deckCards else dueCards

        val rawIndex = prefs.getInt("card_index", 0)
        val currentIndex = if (activeDeck.isNotEmpty()) {
            ((rawIndex % activeDeck.size) + activeDeck.size) % activeDeck.size
        } else 0
        val currentCard = activeDeck.getOrNull(currentIndex)
        val showAnswer = prefs.getBoolean("show_answer", false) && prefs.getString("answer_card_id", "") == currentCard?.id

        val selectedDeckTitle = if (selectedDeckId == "__ALL__") {
            "All Decks"
        } else {
            decks.firstOrNull { it.id == selectedDeckId }?.title ?: "Flashcards"
        }

        provideContent {
            val size = androidx.glance.LocalSize.current
            val isCompact = size.height < 120.dp
            val isWide = size.width >= 240.dp
            val isTall = size.height >= 200.dp
            val isMedium = size.height >= 140.dp

            val cardFontSize = when {
                isTall && isWide -> 18.sp
                isTall || isWide -> 15.sp
                isCompact -> 11.sp
                else -> 13.sp
            }
            val cardMaxLines = when {
                isTall -> 8
                isMedium -> 5
                isCompact -> 2
                else -> 4
            }
            val buttonPaddingV = when {
                isTall -> 12.dp
                isCompact -> 6.dp
                else -> 8.dp
            }
            val buttonFontSize = when {
                isTall -> 14.sp
                isWide -> 13.sp
                else -> 11.sp
            }

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(32.dp)
                    .background(VeritasWidgetColors.widgetBackground)
                    .padding(if (isCompact) 10.dp else 12.dp)
            ) {
                if (allCards.isEmpty()) {
                    // Empty state when no cards exist
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Flashcard Sets",
                            style = TextStyle(
                                fontSize = if (isWide) 15.sp else 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = VeritasWidgetColors.textPrimary
                            ),
                            modifier = GlanceModifier.defaultWeight()
                        )
                    }
                    Spacer(modifier = GlanceModifier.height(8.dp))
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .fillMaxWidth()
                            .cornerRadius(18.dp)
                            .background(VeritasWidgetColors.cardBackground)
                            .padding(14.dp)
                            .clickable(actionStartActivity(widgetActivityIntent(context, MainActivity.ACTION_SHOW_FLASHCARDS))),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No flashcards yet 📚",
                                style = TextStyle(
                                    fontSize = if (isWide) 15.sp else 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VeritasWidgetColors.textPrimary
                                )
                            )
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            Text(
                                text = "Create or import cards in Study Hub",
                                style = TextStyle(
                                    fontSize = if (isWide) 12.sp else 10.sp,
                                    color = VeritasWidgetColors.textMuted
                                )
                            )
                        }
                    }
                } else if (!isReviewMode) {
                    // ── MODE 1: DECK SELECTION LIST ───────────────────────────
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Select Flashcard Set 📚",
                            style = TextStyle(
                                fontSize = if (isWide) 15.sp else 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = VeritasWidgetColors.textPrimary
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight()
                        )
                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(12.dp)
                                .background(VeritasWidgetColors.cardBackground)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${decks.size} sets",
                                style = TextStyle(
                                    fontSize = if (isWide) 12.sp else 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VeritasWidgetColors.primaryAccent
                                )
                            )
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(if (isCompact) 6.dp else 8.dp))

                    val maxDecksToShow = when {
                        isTall -> 5
                        isMedium -> 3
                        isCompact -> 2
                        else -> 3
                    }
                    val visibleDecks = decks.take(maxDecksToShow)

                    Column(
                        modifier = GlanceModifier.fillMaxSize()
                    ) {
                        for (deck in visibleDecks) {
                            val selectParams = actionParametersOf(
                                FlashcardSelectDeckCallback.DeckIdKey to deck.id
                            )
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Row(
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .cornerRadius(12.dp)
                                        .background(VeritasWidgetColors.cardBackground)
                                        .clickable(actionRunCallback<FlashcardSelectDeckCallback>(selectParams))
                                        .padding(horizontal = 10.dp, vertical = if (isCompact) 4.dp else 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = GlanceModifier.defaultWeight()
                                    ) {
                                        Text(
                                            text = deck.title,
                                            style = TextStyle(
                                                fontSize = if (isWide) 13.sp else 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = VeritasWidgetColors.textPrimary
                                            ),
                                            maxLines = 1
                                        )
                                        Spacer(modifier = GlanceModifier.height(1.dp))
                                        Text(
                                            text = "${deck.totalCount} cards${if (deck.dueCount > 0) " • ${deck.dueCount} due" else ""}",
                                            style = TextStyle(
                                                fontSize = if (isWide) 10.sp else 9.sp,
                                                color = if (deck.dueCount > 0) VeritasWidgetColors.streakAccent else VeritasWidgetColors.textMuted
                                            )
                                        )
                                    }

                                    Box(
                                        modifier = GlanceModifier
                                            .cornerRadius(8.dp)
                                            .background(VeritasWidgetColors.playButtonAccent)
                                            .clickable(actionRunCallback<FlashcardSelectDeckCallback>(selectParams))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Review ›",
                                            style = TextStyle(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = VeritasWidgetColors.playIconColor
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        if (decks.size > maxDecksToShow) {
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .cornerRadius(8.dp)
                                    .background(VeritasWidgetColors.cardElevated)
                                    .clickable(actionStartActivity(widgetActivityIntent(context, MainActivity.ACTION_SHOW_FLASHCARDS)))
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+${decks.size - maxDecksToShow} more in Study Hub ›",
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = VeritasWidgetColors.textMuted
                                    )
                                )
                            }
                        }
                    }
                } else {
                    // ── MODE 2: IN-WIDGET CARD REVIEW ─────────────────────────
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Switch Set Button
                        Box(
                            modifier = GlanceModifier
                                .cornerRadius(10.dp)
                                .background(VeritasWidgetColors.cardElevated)
                                .clickable(actionRunCallback<FlashcardClearDeckCallback>())
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "‹ Sets",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = VeritasWidgetColors.primaryAccent
                                )
                            )
                        }

                        Spacer(modifier = GlanceModifier.width(6.dp))

                        Text(
                            text = selectedDeckTitle,
                            style = TextStyle(
                                fontSize = if (isWide) 14.sp else 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = VeritasWidgetColors.textPrimary
                            ),
                            maxLines = 1,
                            modifier = GlanceModifier.defaultWeight()
                        )

                        if (activeDeck.isNotEmpty()) {
                            Box(
                                modifier = GlanceModifier
                                    .cornerRadius(12.dp)
                                    .background(VeritasWidgetColors.cardBackground)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (activeDeck.isEmpty()) "No reviews due" else "${currentIndex + 1}/${activeDeck.size}${if (dueCards.isNotEmpty()) " due" else " · practice"}",
                                    style = TextStyle(
                                        fontSize = if (isWide) 12.sp else 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (dueCards.isNotEmpty()) VeritasWidgetColors.streakAccent else VeritasWidgetColors.successAccent
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = GlanceModifier.height(if (isCompact) 6.dp else 8.dp))

                    if (currentCard == null) {
                        Box(
                            modifier = GlanceModifier
                                .defaultWeight()
                                .fillMaxWidth()
                                .cornerRadius(18.dp)
                                .background(VeritasWidgetColors.cardBackground)
                                .padding(14.dp)
                                .clickable(actionRunCallback<FlashcardPracticeCallback>()),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No cards due. Tap to practise this deck.",
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    color = VeritasWidgetColors.textPrimary
                                )
                            )
                        }
                    } else {
                        // Flashcard container (tap card to flip)
                        Box(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .defaultWeight()
                                .cornerRadius(18.dp)
                                .background(VeritasWidgetColors.cardBackground)
                                .padding(if (isCompact) 10.dp else 12.dp)
                                .clickable(actionRunCallback<FlashcardFlipCallback>(actionParametersOf(FlashcardRateCallback.CardIdKey to currentCard.id))),
                            contentAlignment = Alignment.TopStart
                        ) {
                            Column(modifier = GlanceModifier.fillMaxSize()) {
                                Row(
                                    modifier = GlanceModifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = GlanceModifier
                                            .cornerRadius(6.dp)
                                            .background(
                                                if (showAnswer) VeritasWidgetColors.successAccent else VeritasWidgetColors.primaryAccent
                                            )
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (showAnswer) "ANSWER" else "QUESTION",
                                            style = TextStyle(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ColorProvider(Color.White)
                                            )
                                        )
                                    }
                                    Spacer(modifier = GlanceModifier.defaultWeight())
                                    Text(
                                        text = if (showAnswer) "Tap to flip back" else "Tap card to flip",
                                        style = TextStyle(
                                            fontSize = 10.sp,
                                            color = VeritasWidgetColors.textMuted
                                        )
                                    )
                                }

                                Spacer(modifier = GlanceModifier.height(6.dp))

                                Box(
                                    modifier = GlanceModifier
                                        .fillMaxWidth()
                                        .defaultWeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (showAnswer) currentCard.back else currentCard.front,
                                        style = TextStyle(
                                            fontSize = cardFontSize,
                                            fontWeight = FontWeight.Medium,
                                            color = VeritasWidgetColors.textPrimary
                                        ),
                                        maxLines = cardMaxLines
                                    )
                                }
                            }
                        }

                        Spacer(modifier = GlanceModifier.height(8.dp))

                        // Controls Row
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!showAnswer) {
                                // Question controls: Prev, Flip, Next
                                Box(
                                    modifier = GlanceModifier
                                        .cornerRadius(16.dp)
                                        .background(VeritasWidgetColors.cardElevated)
                                        .clickable(actionRunCallback<FlashcardPrevCallback>())
                                        .padding(horizontal = 12.dp, vertical = buttonPaddingV),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "‹ Prev",
                                        style = TextStyle(
                                            fontSize = buttonFontSize,
                                            fontWeight = FontWeight.Bold,
                                            color = VeritasWidgetColors.textPrimary
                                        )
                                    )
                                }

                                Spacer(modifier = GlanceModifier.width(8.dp))

                                Box(
                                    modifier = GlanceModifier
                                        .defaultWeight()
                                        .cornerRadius(16.dp)
                                        .background(VeritasWidgetColors.playButtonAccent)
                                        .clickable(actionRunCallback<FlashcardFlipCallback>(actionParametersOf(FlashcardRateCallback.CardIdKey to currentCard.id)))
                                        .padding(vertical = buttonPaddingV),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Flip to Answer",
                                        style = TextStyle(
                                            fontSize = buttonFontSize,
                                            fontWeight = FontWeight.Bold,
                                            color = VeritasWidgetColors.playIconColor
                                        )
                                    )
                                }

                                Spacer(modifier = GlanceModifier.width(8.dp))

                                Box(
                                    modifier = GlanceModifier
                                        .cornerRadius(16.dp)
                                        .background(VeritasWidgetColors.cardElevated)
                                        .clickable(actionRunCallback<FlashcardNextCallback>())
                                        .padding(horizontal = 14.dp, vertical = buttonPaddingV),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Next ›",
                                        style = TextStyle(
                                            fontSize = buttonFontSize,
                                            fontWeight = FontWeight.Bold,
                                            color = VeritasWidgetColors.textPrimary
                                        )
                                    )
                                }
                            } else {
                                // Answer controls: Again, Good, Skip
                                val againParams = actionParametersOf(
                                    FlashcardRateCallback.RatingKey to "again",
                                    FlashcardRateCallback.CardIdKey to currentCard.id
                                )
                                val goodParams = actionParametersOf(
                                    FlashcardRateCallback.RatingKey to "good",
                                    FlashcardRateCallback.CardIdKey to currentCard.id
                                )

                                Box(
                                    modifier = GlanceModifier
                                        .defaultWeight()
                                        .cornerRadius(16.dp)
                                        .background(VeritasWidgetColors.cardElevated)
                                        .clickable(actionRunCallback<FlashcardRateCallback>(againParams))
                                        .padding(vertical = buttonPaddingV),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Again",
                                        style = TextStyle(
                                            fontSize = buttonFontSize,
                                            fontWeight = FontWeight.Bold,
                                            color = VeritasWidgetColors.streakAccent
                                        )
                                    )
                                }

                                Spacer(modifier = GlanceModifier.width(8.dp))

                                Box(
                                    modifier = GlanceModifier
                                        .defaultWeight()
                                        .cornerRadius(16.dp)
                                        .background(VeritasWidgetColors.playButtonAccent)
                                        .clickable(actionRunCallback<FlashcardRateCallback>(goodParams))
                                        .padding(vertical = buttonPaddingV),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Good ✓",
                                        style = TextStyle(
                                            fontSize = buttonFontSize,
                                            fontWeight = FontWeight.Bold,
                                            color = VeritasWidgetColors.playIconColor
                                        )
                                    )
                                }

                                Spacer(modifier = GlanceModifier.width(8.dp))

                                Box(
                                    modifier = GlanceModifier
                                        .cornerRadius(16.dp)
                                        .background(VeritasWidgetColors.cardElevated)
                                        .clickable(actionRunCallback<FlashcardNextCallback>())
                                        .padding(horizontal = 12.dp, vertical = buttonPaddingV),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Skip ›",
                                        style = TextStyle(
                                            fontSize = buttonFontSize,
                                            fontWeight = FontWeight.Bold,
                                            color = VeritasWidgetColors.textMuted
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class FlashcardSelectDeckCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val deckId = parameters[DeckIdKey] ?: return
        val prefs = flashcardWidgetPreferences(context, glanceId)
        prefs.edit()
            .putString("selected_deck_id", deckId)
            .putInt("card_index", 0)
            .putBoolean("practice_mode", false)
            .putBoolean("show_answer", false)
            .commit()
        FlashcardWidget().update(context, glanceId)

    }

    companion object {
        val DeckIdKey = ActionParameters.Key<String>("deck_id")
    }
}

class FlashcardClearDeckCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val prefs = flashcardWidgetPreferences(context, glanceId)
        prefs.edit()
            .putString("selected_deck_id", "")
            .putBoolean("show_answer", false)
            .commit()
        FlashcardWidget().update(context, glanceId)

    }
}

class FlashcardPrevCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val prefs = flashcardWidgetPreferences(context, glanceId)
        val count = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { flashcardWidgetDeck(DocumentRepository(context), prefs).size }
        val currentIdx = prefs.getInt("card_index", 0)
        prefs.edit()
            .putInt("card_index", if (count > 0) Math.floorMod(currentIdx - 1, count) else 0)
            .putBoolean("show_answer", false)
            .commit()
        FlashcardWidget().update(context, glanceId)

    }
}

class FlashcardFlipCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val prefs = flashcardWidgetPreferences(context, glanceId)
        val card = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val deck = flashcardWidgetDeck(DocumentRepository(context), prefs)
            deck.getOrNull(if (deck.isEmpty()) 0 else Math.floorMod(prefs.getInt("card_index", 0), deck.size))
        } ?: return
        if (parameters[FlashcardRateCallback.CardIdKey]?.let { it != card.id } == true) return
        val current = prefs.getBoolean("show_answer", false) && prefs.getString("answer_card_id", "") == card.id
        prefs.edit().putBoolean("show_answer", !current).putString("answer_card_id", card.id).commit()
        FlashcardWidget().update(context, glanceId)

    }
}

class FlashcardNextCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val prefs = flashcardWidgetPreferences(context, glanceId)
        val currentIdx = prefs.getInt("card_index", 0)
        prefs.edit()
            .putInt("card_index", currentIdx + 1)
            .putBoolean("show_answer", false)
            .commit()
        FlashcardWidget().update(context, glanceId)

    }
}

class FlashcardRateCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val rating = parameters[RatingKey] ?: "good"
        val cardId = parameters[CardIdKey] ?: return
        if (rating !in setOf("again", "good")) return
        val prefs = flashcardWidgetPreferences(context, glanceId)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            synchronized(DocumentRepository.LIBRARY_WRITE_LOCK) {
                if (!prefs.getBoolean("show_answer", false) || prefs.getString("answer_card_id", "") != cardId) return@synchronized
                DocumentRepository(context).mutateStudyFlashcards { cards ->
                    cards.map { if (it.id == cardId) SpacedRepetitionScheduler.rateCard(it, rating) else it }
                }
                // Due review shrinks after a rating. Keep the index so the next card is not skipped.
                val index = prefs.getInt("card_index", 0)
                prefs.edit().putInt("card_index", if (prefs.getBoolean("practice_mode", false)) index + 1 else index)
                    .putBoolean("show_answer", false).putString("answer_card_id", "").commit()
            }
        }
        FlashcardWidget().update(context, glanceId)

        runCatching { StudyDashboardWidget().updateAll(context) }
    }

    companion object {
        val RatingKey = ActionParameters.Key<String>("rating")
        val CardIdKey = ActionParameters.Key<String>("card_id")
    }
}

class FlashcardOpenStudyHubCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_WIDGET_ACTION, MainActivity.ACTION_SHOW_FLASHCARDS)
        }
        context.startActivity(intent)
    }
}

class FlashcardPracticeCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        flashcardWidgetPreferences(context, glanceId).edit().putBoolean("practice_mode", true).putInt("card_index", 0).putBoolean("show_answer", false).commit()
        FlashcardWidget().update(context, glanceId)
    }
}
