package com.veritas.reader.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/**
 * Curated bookbinding palette of rich, library-grade cloth & leather tones.
 * Each document deterministically receives a stable, handsome cover color.
 */
private val BOOKCLOTH_PALETTE = listOf(
    Color(0xFF2C394B), // Slate Navy
    Color(0xFF4A3428), // Warm Leather / Terracotta
    Color(0xFF243B30), // British Racing Green / Forest
    Color(0xFF452434), // Burgundy / Wine
    Color(0xFF2B2B36), // Midnight Charcoal
    Color(0xFF4E3B20), // Antique Amber / Ochre
    Color(0xFF283648), // Deep Oxford Blue
    Color(0xFF383832)  // Muted Olive Cloth
)

private fun stableBookColor(documentId: String): Color {
    var hash = 0
    for (ch in documentId) hash = hash * 31 + ch.code
    val index = abs(hash) % BOOKCLOTH_PALETTE.size
    return BOOKCLOTH_PALETTE[index]
}

private fun extractMonogram(title: String, sourceLabel: String): String {
    val words = title.trim().split(Regex("[\\s_\\-\\.]+")).filter { it.isNotEmpty() }
    return when {
        words.size >= 2 -> "${words[0].first().uppercase()}${words[1].first().uppercase()}"
        words.isNotEmpty() -> words[0].take(2).uppercase()
        else -> sourceLabel.take(2).uppercase()
    }
}

@Composable
fun VeritasCoverPlaceholder(
    documentId: String,
    title: String,
    sourceLabel: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(start = 14.dp, top = 14.dp, end = 12.dp, bottom = 12.dp)
) {
    val base = remember(documentId) { stableBookColor(documentId) }
    val monogram = remember(title, sourceLabel) { extractMonogram(title, sourceLabel) }
    val shape = RoundedCornerShape(if (compact) 6.dp else 10.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        base.copy(alpha = 0.85f),
                        base,
                        base.copy(alpha = 0.95f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
    ) {
        // Spine crease groove on the left edge
        Row(
            modifier = Modifier
                .width(if (compact) 4.dp else 8.dp)
                .fillMaxHeight()
        ) {
            Box(
                modifier = Modifier
                    .width(if (compact) 2.dp else 4.dp)
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.35f))
            )
            Box(
                modifier = Modifier
                    .width(if (compact) 1.dp else 2.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.12f))
            )
        }

        if (compact) {
            // Elegant compact book icon + monogram
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = monogram,
                    color = Color.White.copy(alpha = 0.95f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.8.sp
                )
            }
        } else {
            // Editorial full cover layout with serif typography and foil accent
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Serif,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Subtle gold/amber foil dividing rule
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                )

                Box(modifier = Modifier.weight(1f))

                // Format tag badge
                Text(
                    text = sourceLabel.uppercase(),
                    color = Color.White.copy(alpha = 0.70f),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.sp,
                    maxLines = 1
                )
            }
        }
    }
}
