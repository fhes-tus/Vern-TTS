package com.veritas.reader.ui.screens

import com.veritas.reader.vernScatteredCard

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.veritas.reader.GeneralNote
import com.veritas.reader.ReaderSettings
import com.veritas.reader.ReaderTrackerSnapshot
import com.veritas.reader.SavedDocument
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.blendColors
import com.veritas.reader.formatUpdated
import com.veritas.reader.ui.OnboardingController
import com.veritas.reader.ui.OnboardingStep
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun ReadingStatsDashboardDialog(
    snapshot: ReaderTrackerSnapshot,
    documents: List<SavedDocument>,
    documentReadingTimes: Map<String, Long>,
    onDismiss: () -> Unit,
    readerSettings: ReaderSettings = ReaderSettings(),
    onGoalMinutesChange: (Int) -> Unit = {},
    onOpenDocument: (SavedDocument) -> Unit = {}
) {
    ReadingSessionsDashboardDialog(snapshot, documents, readerSettings, onGoalMinutesChange, onDismiss, onOpenDocument)
}

@Composable
internal fun CompactStat(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.vernScatteredCard("insights-stat-$label", amount = .7f),
        shape = VeritasPackStyle.cardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(value, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun BigStat(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.vernScatteredCard("insights-stat-$label", amount = .7f),
        shape = VeritasPackStyle.cardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

internal fun shareGeneralNote(context: Context, note: GeneralNote) {
    NoteSharing.share(context, note)
}

// Count-up variant of formatTrackerDuration: rolls the minute total up to its
// target on first show / change, so the big stats animate like the streak counter.
@Composable
internal fun animatedTrackerDuration(millis: Long): String {
    val goal = (millis / 60_000L).coerceAtLeast(0L).toInt()
    var target by remember { mutableIntStateOf(0) }
    LaunchedEffect(goal) { target = goal }
    val animated by animateIntAsState(
        targetValue = target,
        animationSpec = if (com.veritas.reader.ui.VeritasMotion.scheme.reduceMotion) androidx.compose.animation.core.snap()
            else tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "trackerDurationCountUp"
    )
    return formatTrackerDuration(animated.toLong() * 60_000L)
}

internal fun formatTrackerDuration(millis: Long): String {
    val minutes = (millis / 60_000L).coerceAtLeast(0L)
    val hours = minutes / 60L
    val remaining = minutes % 60L
    return when {
        hours > 0L && remaining > 0L -> "${hours}h ${remaining}m"
        hours > 0L -> "${hours}h"
        else -> "${remaining}m"
    }
}
