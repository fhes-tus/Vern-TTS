package com.veritas.reader.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

internal object VernActionHintManager {
    private const val PREFS_NAME = "vern_action_page_hints"
    private val sessionDismissed = mutableSetOf<String>()

    fun isDismissed(context: Context, hintKey: String): Boolean {
        if (sessionDismissed.contains(hintKey)) return true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(hintKey, false)
    }

    fun dismissForSession(hintKey: String) {
        sessionDismissed.add(hintKey)
    }

    fun dismissPermanently(context: Context, hintKey: String) {
        sessionDismissed.add(hintKey)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(hintKey, true).apply()
    }
}

data class VernHintGestureItem(
    val icon: ImageVector,
    val title: String,
    val description: String
)

/**
 * Standard gesture tips popup card styled identically to Image 1.
 * Features a rounded surface card, lightbulb tile in primaryContainer,
 * title + close button, clear icon-annotated gesture items, and "Don't show again" / "Got it" actions.
 */
@Composable
internal fun VernGesturePopupCard(
    title: String,
    items: List<VernHintGestureItem>,
    onDismissCurrent: () -> Unit,
    onDismissPermanently: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = scheme.surfaceVariant.copy(alpha = 0.96f),
            contentColor = scheme.onSurfaceVariant
        ),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.40f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Lightbulb in rounded square + Title + Close X
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = scheme.primaryContainer,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lightbulb,
                            contentDescription = null,
                            tint = scheme.onPrimaryContainer,
                            modifier = Modifier
                                .padding(5.dp)
                                .size(18.dp)
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface
                    )
                }

                IconButton(
                    onClick = onDismissCurrent,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = scheme.onSurfaceVariant.copy(alpha = 0.70f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action / Gesture Rows
            items.forEachIndexed { index, item ->
                if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = scheme.onSurface)) {
                                append(item.title)
                            }
                            append(" — ")
                            withStyle(SpanStyle(color = scheme.onSurfaceVariant)) {
                                append(item.description)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Actions: "Don't show again" and "Got it"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onDismissPermanently
                ) {
                    Text(
                        text = "Don't show again",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(
                    onClick = onDismissCurrent
                ) {
                    Text(
                        text = "Got it",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Discovery banner for key app screens (Books, Notes) using the visually appealing Image 1 card styling.
 */
@Composable
internal fun VernActionHintBanner(
    hintKey: String,
    message: String,
    title: String = "Tips & Gestures",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var visible by remember(hintKey) { mutableStateOf(!VernActionHintManager.isDismissed(context, hintKey)) }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        val scheme = MaterialTheme.colorScheme
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag("action-hint-$hintKey"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = scheme.surfaceVariant.copy(alpha = 0.96f),
                contentColor = scheme.onSurfaceVariant
            ),
            border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.40f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = scheme.primaryContainer,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lightbulb,
                                contentDescription = null,
                                tint = scheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(5.dp)
                                    .size(18.dp)
                            )
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = {
                            VernActionHintManager.dismissForSession(hintKey)
                            visible = false
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss",
                            tint = scheme.onSurfaceVariant.copy(alpha = 0.70f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val lines = message.split("\n").filter { it.isNotBlank() }
                lines.forEachIndexed { idx, line ->
                    if (idx > 0) Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            VernActionHintManager.dismissPermanently(context, hintKey)
                            visible = false
                        }
                    ) {
                        Text(
                            text = "Don't show again",
                            style = MaterialTheme.typography.labelMedium,
                            color = scheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = {
                            VernActionHintManager.dismissForSession(hintKey)
                            visible = false
                        }
                    ) {
                        Text(
                            text = "Got it",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = scheme.primary
                        )
                    }
                }
            }
        }
    }
}
