package com.veritas.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

val BOOKCLOTH_PALETTE = listOf(
    Color(0xFF1E293B), // Slate Navy
    Color(0xFF78350F), // Warm Terracotta / Amber Leather
    Color(0xFF14532D), // Deep Forest Green
    Color(0xFF831843), // Burgundy Wine
    Color(0xFF18181B), // Midnight Charcoal
    Color(0xFF713F12), // Antique Ochre
    Color(0xFF1E3A8A), // Oxford Deep Blue
    Color(0xFF3F3F46)  // Muted Olive Cloth
)

fun getDeterministicBookColor(documentId: String): Color {
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
fun DesktopCoverView(
    documentId: String,
    title: String,
    sourceLabel: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val baseColor = remember(documentId) { getDeterministicBookColor(documentId) }
    val monogram = remember(title, sourceLabel) { extractMonogram(title, sourceLabel) }
    val shape = RoundedCornerShape(if (compact) 6.dp else 12.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        baseColor.copy(alpha = 0.88f),
                        baseColor,
                        baseColor.copy(alpha = 0.96f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
    ) {
        // Spine Crease (Left edge shadow groove)
        Row(
            modifier = Modifier
                .width(if (compact) 5.dp else 9.dp)
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
                    .background(Color.White.copy(alpha = 0.15f))
            )
        }

        if (compact) {
            // Compact Mode
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = monogram,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )
            }
        } else {
            // Full Editorial Cover Layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 18.dp, top = 16.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Gold Foil Accent Bar
                    Box(
                        modifier = Modifier
                            .width(32.dp)
                            .height(2.5.dp)
                            .background(Color(0xFFFBBF24).copy(alpha = 0.9f))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = sourceLabel.uppercase(),
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = monogram,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}
