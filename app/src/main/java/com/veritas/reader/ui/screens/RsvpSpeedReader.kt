package com.veritas.reader.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.DocumentRepository
import com.veritas.reader.PlaybackActions
import com.veritas.reader.ReaderDocument
import com.veritas.reader.sendPlaybackIntent
import com.veritas.reader.PlaybackStateStore
import com.veritas.reader.RsvpPlaybackSession
import com.veritas.reader.RsvpTransport
import com.veritas.reader.SpeechPositionTiming
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.veritas.reader.ui.VeritasSleekSlider
import kotlinx.coroutines.delay

/**
 * Data structure for a single word token in RSVP mode.
 */
data class RsvpWordToken(
    val word: String,
    val sentenceIndex: Int,
    val orpIndex: Int,
    val delayMultiplier: Float = 1.0f,
    val sourceStart: Int = 0,
    val sourceEnd: Int = word.length
)

/**
 * RSVP (Rapid Serial Visual Presentation) Speed Reader Dialog / Full-screen overlay.
 */
@Composable
fun RsvpSpeedReader(
    document: ReaderDocument,
    initialSentenceIndex: Int,
    onClose: (targetSentenceIndex: Int) -> Unit
) {
    val context = LocalContext.current
    val audioSession = remember(context, document.id) { RsvpPlaybackSession(context, document.id) }
    RsvpSpeedReaderContent(document, initialSentenceIndex, onClose, audioSession)
}

@Composable
internal fun RsvpSpeedReaderContent(document: ReaderDocument, initialSentenceIndex: Int,
    onClose: (Int) -> Unit, audioSession: RsvpTransport) {
    val tokens = remember(document.id, document.chunks) { buildRsvpWordTokens(document.chunks) }

    // Find starting word index matching initialSentenceIndex
    val startingWordIndex = remember(tokens, initialSentenceIndex) {
        val found = tokens.indexOfFirst { it.sentenceIndex >= initialSentenceIndex }
        if (found >= 0) found else 0
    }

    val context = LocalContext.current
    val preferences = remember(context) { DocumentRepository(context).prefs }
    var currentWordIndex by remember(document.id) { mutableIntStateOf(startingWordIndex) }
    var isPlaying by remember(document.id) { mutableStateOf(false) }
    var wordsPerMinute by remember { mutableFloatStateOf(preferences.getFloat("rsvp_wpm", 350f).coerceIn(150f, 850f)) }
    var audioRate by remember { mutableFloatStateOf(preferences.getFloat("rsvp_audio_rate", 1f).coerceIn(.5f, 2f)) }
    var audioNarration by remember { mutableStateOf(document.id != null && preferences.getBoolean("rsvp_audio_mode", false)) }
    var showControls by remember { mutableStateOf(true) }
    var seekRevision by remember { mutableIntStateOf(0) }
    var audioHasStarted by remember { mutableStateOf(false) }
    DisposableEffect(audioSession) {
        audioSession.enter()
        onDispose { audioSession.close() }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) isPlaying = false }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(wordsPerMinute, audioRate, audioNarration) {
        delay(300)
        preferences.edit().putFloat("rsvp_wpm", wordsPerMinute).putFloat("rsvp_audio_rate", audioRate)
            .putBoolean("rsvp_audio_mode", audioNarration).apply()
    }

    // Silent mode alone uses WPM. Audio mode advances from the existing player's source ranges.
    LaunchedEffect(isPlaying, audioNarration, currentWordIndex, wordsPerMinute, tokens.size) {
        if (isPlaying && !audioNarration && currentWordIndex < tokens.size) {
            val token = tokens.getOrNull(currentWordIndex)
            delay(((60_000f / wordsPerMinute) * (token?.delayMultiplier ?: 1f)).toLong().coerceAtLeast(40))
            if (currentWordIndex < tokens.lastIndex) currentWordIndex++ else isPlaying = false
        }
    }
    LaunchedEffect(isPlaying, audioNarration, seekRevision, audioRate) {
        audioHasStarted = false
        if (audioNarration && isPlaying) {
            tokens.getOrNull(currentWordIndex)?.let { audioSession.play(it.sentenceIndex, it.sourceStart, audioRate) }
        } else audioSession.pause()
    }
    val speechPosition = PlaybackStateStore.speechPosition
    val playerPlaying = PlaybackStateStore.isPlaying
    val preparing = PlaybackStateStore.isPreparingFor(document.id)
    val failure = PlaybackStateStore.playbackFailure
    LaunchedEffect(audioNarration, speechPosition, playerPlaying, preparing, failure) {
        if (audioNarration && isPlaying) {
            if (speechPosition != null && audioSession.accepts(speechPosition)) {
                audioHasStarted = true
                val next = rsvpTokenAtSource(tokens, speechPosition.sentenceIndex, speechPosition.sourceStart)
                if (next >= 0) currentWordIndex = next
            }
            if ((audioHasStarted || failure != null) && !playerPlaying && !preparing) isPlaying = false
        }
    }
    val currentToken = tokens.getOrNull(currentWordIndex)
    val activeSentenceIndex = currentToken?.sentenceIndex ?: initialSentenceIndex
    val timingLabel = when (speechPosition?.takeIf { it.documentId == document.id }?.timing) {
        SpeechPositionTiming.WORD_RANGE -> "Audio · word timing"
        SpeechPositionTiming.ESTIMATED_PCM -> "Audio · estimated word timing"
        else -> "Audio · sentence timing"
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
            },
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // TOP BAR
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onClose(activeSentenceIndex) },
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Reader",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "⚡ RSVP Speed Reader",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${currentWordIndex + 1} / ${tokens.size.coerceAtLeast(1)} words",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { currentWordIndex = 0; seekRevision++; isPlaying = true },
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), CircleShape)
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Restart",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // CENTER WORD DISPLAY (Optimal Recognition Point ORP)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Focus Guide Ticks
                Row(
                    modifier = Modifier.width(220.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (currentToken != null) {
                    val word = currentToken.word
                    val orp = currentToken.orpIndex.coerceIn(0, (word.length - 1).coerceAtLeast(0))

                    val prefix = word.substring(0, orp)
                    val focalEnd = (orp + Character.charCount(word.codePointAt(orp))).coerceAtMost(word.length)
                    val focalChar = word.substring(orp, focalEnd)
                    val suffix = word.substring(focalEnd)

                    val styledWord = buildAnnotatedString {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Normal)) {
                            append(prefix)
                        }
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Black
                            )
                        ) {
                            append(focalChar)
                        }
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Normal)) {
                            append(suffix)
                        }
                    }

                    Text(
                        text = styledWord,
                        fontSize = 42.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        lineHeight = 48.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "End of document",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Focus Guide Tick
                Row(
                    modifier = Modifier.width(220.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    )
                }
            }

            // BOTTOM CONTROLS
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Play / Pause & Skip Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { currentWordIndex = (currentWordIndex - 20).coerceAtLeast(0); seekRevision++ }
                            ) {
                                Icon(
                                    Icons.Filled.FastRewind,
                                    contentDescription = "Rewind 20 words",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Surface(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .clickable { if (tokens.isNotEmpty()) isPlaying = !isPlaying },
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    currentWordIndex = (currentWordIndex + 20).coerceAtMost(tokens.lastIndex.coerceAtLeast(0)); seekRevision++
                                }
                            ) {
                                Icon(
                                    Icons.Filled.FastForward,
                                    contentDescription = "Forward 20 words",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // WPM Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.FastForward,
                                contentDescription = "Speed",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            VeritasSleekSlider(
                                value = if (audioNarration) audioRate else wordsPerMinute,
                                onValueChange = { if (audioNarration) audioRate = it else wordsPerMinute = it },
                                valueRange = if (audioNarration) .5f..2f else 150f..850f,
                                steps = if (audioNarration) 0 else 13,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = if (audioNarration) "${String.format(java.util.Locale.ROOT, "%.2f", audioRate)}×" else "${wordsPerMinute.toInt()} WPM",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(68.dp),
                                textAlign = TextAlign.End
                            )
                        }

                        // Audio Narration Mode Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (audioNarration) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = null,
                                    tint = if (audioNarration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (audioNarration) timingLabel else if (document.id == null) "Silent RSVP · save for audio" else "Silent visual RSVP",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Switch(
                                checked = audioNarration,
                                onCheckedChange = { audioNarration = it },
                                enabled = tokens.isNotEmpty() && document.id != null
                            )
                        }
                        if (audioNarration && PlaybackStateStore.activeDocumentId == document.id) Text(
                            if (preparing) "Preparing audio…" else PlaybackStateStore.statusMessage,
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

internal fun buildRsvpWordTokens(chunks: List<String>): List<RsvpWordToken> = buildList {
    chunks.forEachIndexed { sentence, chunk ->
        Regex("\\S+").findAll(chunk).forEach { match ->
            val word = match.value
            val multiplier = when {
                word.endsWith(".") || word.endsWith("!") || word.endsWith("?") -> 2f
                word.endsWith(",") || word.endsWith(";") || word.endsWith(":") -> 1.4f
                word.length >= 10 -> 1.25f
                else -> 1f
            }
            add(RsvpWordToken(word, sentence, calculateOrpIndex(word), multiplier, match.range.first, match.range.last + 1))
        }
    }
}

/**
 * Calculates Optimal Recognition Point (ORP) index for a word.
 * 1-3 chars -> 0 (1st letter)
 * 4-5 chars -> 1 (2nd letter)
 * 6-9 chars -> 2 (3rd letter)
 * 10-13 chars -> 3 (4th letter)
 * 14+ chars -> 4 (5th letter)
 */
private fun calculateOrpIndex(word: String): Int {
    val len = word.codePointCount(0, word.length)
    val point = when {
        len <= 3 -> 0
        len <= 5 -> 1
        len <= 9 -> 2
        len <= 13 -> 3
        else -> 4
    }
    return word.offsetByCodePoints(0, point.coerceAtMost((len - 1).coerceAtLeast(0)))
}

/** Lower bound over source-ordered tokens: long books do not scan every word per callback. */
internal fun rsvpTokenAtSource(tokens: List<RsvpWordToken>, sentence: Int, offset: Int): Int {
    var low = 0
    var high = tokens.size
    while (low < high) {
        val mid = (low + high) ushr 1
        val token = tokens[mid]
        if (token.sentenceIndex < sentence || (token.sentenceIndex == sentence && token.sourceEnd <= offset)) low = mid + 1
        else high = mid
    }
    return low.takeIf { it < tokens.size && tokens[it].sentenceIndex == sentence } ?: -1
}
