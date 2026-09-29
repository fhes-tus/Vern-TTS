package com.veritas.desktop.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.desktop.models.DesktopDocument
import kotlinx.coroutines.delay

data class RsvpToken(
    val word: String,
    val sentenceIndex: Int,
    val orpIndex: Int,
    val delayMultiplier: Float = 1.0f
)

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

@Composable
fun DesktopRsvpReader(
    document: DesktopDocument,
    initialSentenceIndex: Int,
    onClose: (targetSentenceIndex: Int) -> Unit
) {
    val tokens = remember(document.id, document.chunks) {
        val list = mutableListOf<RsvpToken>()
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
                    list.add(RsvpToken(cleanWord, sentenceIdx, orp, multiplier))
                }
            }
        }
        list
    }

    val startingWordIndex = remember(tokens, initialSentenceIndex) {
        val found = tokens.indexOfFirst { it.sentenceIndex >= initialSentenceIndex }
        if (found >= 0) found else 0
    }

    var currentWordIndex by remember { mutableStateOf(startingWordIndex) }
    var isPlaying by remember { mutableStateOf(true) }
    var wordsPerMinute by remember { mutableStateOf(350f) }
    var showControls by remember { mutableStateOf(true) }

    // RSVP loop
    LaunchedEffect(isPlaying, currentWordIndex, wordsPerMinute, tokens.size) {
        if (isPlaying && currentWordIndex < tokens.size) {
            val token = tokens.getOrNull(currentWordIndex)
            val baseDelayMs = (60_000f / wordsPerMinute).toLong()
            val finalDelayMs = (baseDelayMs * (token?.delayMultiplier ?: 1.0f)).toLong().coerceAtLeast(35L)
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
            // Top Bar
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onClose(activeSentenceIndex) },
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Return to Reader")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "⚡ RSVP Speed Reader",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${currentWordIndex + 1} / ${tokens.size.coerceAtLeast(1)} words • Sentence ${activeSentenceIndex + 1}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = { currentWordIndex = 0; isPlaying = true },
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Restart")
                    }
                }
            }

            // Center: ORP Focal Display
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Focus Guide Tick
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                )

                Spacer(modifier = Modifier.height(20.dp))

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
                        fontSize = 54.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        lineHeight = 60.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "Finished reading.",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom Focus Guide Tick
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(18.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                )
            }

            // Bottom Floating Controls
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 32.dp, vertical = 24.dp)
            ) {
                Surface(
                    modifier = Modifier.widthIn(max = 620.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                    tonalElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Transport Buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { currentWordIndex = (currentWordIndex - 25).coerceAtLeast(0) }
                            ) {
                                Icon(Icons.Default.FastRewind, contentDescription = "Rewind 25 words", modifier = Modifier.size(28.dp))
                            }

                            FilledIconButton(
                                onClick = { isPlaying = !isPlaying },
                                modifier = Modifier.size(56.dp),
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    currentWordIndex = (currentWordIndex + 25).coerceAtMost(tokens.lastIndex.coerceAtLeast(0))
                                }
                            ) {
                                Icon(Icons.Default.FastForward, contentDescription = "Forward 25 words", modifier = Modifier.size(28.dp))
                            }
                        }

                        // WPM Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Speed:", style = MaterialTheme.typography.labelMedium)
                            Slider(
                                value = wordsPerMinute,
                                onValueChange = { wordsPerMinute = it },
                                valueRange = 150f..850f,
                                steps = 14,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.width(88.dp)
                            ) {
                                Text(
                                    text = "${wordsPerMinute.toInt()} WPM",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
