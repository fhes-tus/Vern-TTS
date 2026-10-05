package com.veritas.reader.ui.screens

import android.speech.tts.TextToSpeech
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
import com.veritas.reader.VoiceConfigurator
import com.veritas.reader.sendPlaybackIntent
import com.veritas.reader.ui.VeritasSleekSlider
import kotlinx.coroutines.delay

/**
 * Data structure for a single word token in RSVP mode.
 */
data class RsvpWordToken(
    val word: String,
    val sentenceIndex: Int,
    val orpIndex: Int,
    val delayMultiplier: Float = 1.0f
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
    // 1. Flatten all document chunks into RSVP word tokens
    val tokens = remember(document.id, document.chunks) {
        val list = mutableListOf<RsvpWordToken>()
        document.chunks.forEachIndexed { sentenceIdx, chunk ->
            val words = chunk.split(Regex("\\s+")).filter { it.isNotBlank() }
            words.forEach { rawWord ->
                val cleanWord = rawWord.trim()
                if (cleanWord.isNotBlank()) {
                    val orp = calculateOrpIndex(cleanWord)
                    val multiplier = when {
                        cleanWord.endsWith(".") || cleanWord.endsWith("!") || cleanWord.endsWith("?") -> 2.0f
                        cleanWord.endsWith(",") || cleanWord.endsWith(";") || cleanWord.endsWith(":") -> 1.4f
                        cleanWord.length >= 10 -> 1.25f
                        else -> 1.0f
                    }
                    list.add(RsvpWordToken(cleanWord, sentenceIdx, orp, multiplier))
                }
            }
        }
        list
    }

    // Find starting word index matching initialSentenceIndex
    val startingWordIndex = remember(tokens, initialSentenceIndex) {
        val found = tokens.indexOfFirst { it.sentenceIndex >= initialSentenceIndex }
        if (found >= 0) found else 0
    }

    var currentWordIndex by remember { mutableIntStateOf(startingWordIndex) }
    var isPlaying by remember { mutableStateOf(false) }
    var wordsPerMinute by remember { mutableFloatStateOf(350f) }
    var audioNarration by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val voiceSettings = remember(context) {
        DocumentRepository(context).loadVoiceSettings()
    }

    LaunchedEffect(Unit) {
        sendPlaybackIntent(context, PlaybackActions.ACTION_PAUSE)
    }

    val neuralVoice = com.veritas.reader.VoiceManager.isVeritasEngine(voiceSettings.enginePackage)
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    DisposableEffect(context, voiceSettings.enginePackage) {
        if (neuralVoice) return@DisposableEffect onDispose { }
        var disposed = false
        var tts: TextToSpeech? = null
        val initListener = TextToSpeech.OnInitListener { status ->
            if (!disposed && status == TextToSpeech.SUCCESS) {
                tts?.let { engine ->
                    VoiceConfigurator.apply(engine, voiceSettings)
                    engine.setPitch(voiceSettings.preferredPitch)
                }
                isTtsReady = true
            }
        }
        tts = if (voiceSettings.enginePackage.isBlank()) TextToSpeech(context.applicationContext, initListener)
            else TextToSpeech(context.applicationContext, initListener, voiceSettings.enginePackage)
        ttsEngine = tts
        onDispose {
            disposed = true
            tts?.stop()
            tts?.shutdown()
        }
    }

    // Speed timer loop
    LaunchedEffect(isPlaying, currentWordIndex, wordsPerMinute, tokens.size) {
        if (isPlaying && currentWordIndex < tokens.size) {
            val token = tokens.getOrNull(currentWordIndex)
            val baseDelayMs = (60_000f / wordsPerMinute).toLong()
            val finalDelayMs = (baseDelayMs * (token?.delayMultiplier ?: 1.0f)).toLong().coerceAtLeast(40L)
            delay(finalDelayMs)
            if (currentWordIndex < tokens.lastIndex) {
                currentWordIndex++
            } else {
                isPlaying = false
            }
        }
    }

    val currentToken = tokens.getOrNull(currentWordIndex)
    val activeSentenceIndex = currentToken?.sentenceIndex ?: initialSentenceIndex

    // TTS speech synchronization effect
    var lastSpokenSentenceIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(isPlaying, audioNarration, activeSentenceIndex, isTtsReady) {
        if (audioNarration && isPlaying && isTtsReady && ttsEngine != null) {
            if (activeSentenceIndex != lastSpokenSentenceIndex) {
                lastSpokenSentenceIndex = activeSentenceIndex
                val sentenceText = document.chunks.getOrNull(activeSentenceIndex)
                if (!sentenceText.isNullOrBlank()) {
                    val ttsRate = (wordsPerMinute / 200f).coerceIn(0.5f, 3.0f)
                    ttsEngine?.setSpeechRate(ttsRate)
                    ttsEngine?.setPitch(voiceSettings.preferredPitch)
                    ttsEngine?.speak(com.veritas.reader.ReadingSymbols.prepare(sentenceText, ttsEngine?.voice?.locale?.language == "en").text, TextToSpeech.QUEUE_FLUSH, null, "rsvp_$activeSentenceIndex")
                }
            }
        } else {
            ttsEngine?.stop()
            if (!isPlaying) {
                lastSpokenSentenceIndex = -1
            }
        }
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
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${currentWordIndex + 1} / ${tokens.size.coerceAtLeast(1)} words",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { currentWordIndex = 0; isPlaying = true },
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
                    val focalChar = word.getOrNull(orp)?.toString().orEmpty()
                    val suffix = if (orp + 1 < word.length) word.substring(orp + 1) else ""

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
                                onClick = { currentWordIndex = (currentWordIndex - 20).coerceAtLeast(0) }
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
                                    .clickable { isPlaying = !isPlaying },
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
                                    currentWordIndex = (currentWordIndex + 20).coerceAtMost(tokens.lastIndex.coerceAtLeast(0))
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
                                value = wordsPerMinute,
                                onValueChange = { wordsPerMinute = it },
                                valueRange = 150f..850f,
                                steps = 13,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${wordsPerMinute.toInt()} WPM",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
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
                                    text = if (audioNarration) "Read sentences aloud" else if (neuralVoice) "Silent RSVP · neural audio not available yet" else "Silent visual RSVP",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Switch(
                                checked = audioNarration,
                                onCheckedChange = { audioNarration = it },
                                enabled = !neuralVoice && isTtsReady
                            )
                        }
                    }
                }
            }
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
    val len = word.length
    return when {
        len <= 3 -> 0
        len <= 5 -> 1
        len <= 9 -> 2
        len <= 13 -> 3
        else -> 4
    }
}
