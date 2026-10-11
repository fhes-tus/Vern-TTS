package com.veritas.reader.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veritas.reader.VeritasPackStyle
import kotlin.math.ceil

/** Uses recorded reading duration, never app-open duration or invented listening totals. */
@Composable
internal fun HomeTodayReadingCard(
    todayMillis: Long,
    goalMinutes: Int,
    readingDaysThisWeek: Int,
    onOpenInsights: () -> Unit,
    onGoalMinutesChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val goalMillis = goalMinutes.toLong() * 60_000L
    val hasGoal = goalMillis > 0L
    val reachedGoal = hasGoal && todayMillis >= goalMillis
    val fraction by animateFloatAsState(
        if (hasGoal) (todayMillis.toFloat() / goalMillis).coerceIn(0f, 1f) else 0f,
        animationSpec = com.veritas.reader.ui.VeritasMotion.spatial(), label = "homeGoalProgress"
    )
    val timeLabel = if (todayMillis in 1..59_999L) "<1" else "${todayMillis / 60_000L}"
    var showGoalPicker by remember { mutableStateOf(false) }
    Card(modifier.fillMaxWidth(), shape = VeritasPackStyle.cardShape(),
        colors = CardDefaults.cardColors(containerColor = VeritasPackStyle.panelColor(scheme)),
        border = VeritasPackStyle.cardBorder(scheme)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(if (hasGoal) "Today’s reading" else "Your reading today",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                    Text(if (hasGoal) "$timeLabel / $goalMinutes min" else "$timeLabel min",
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                    Text(when {
                        reachedGoal -> "Daily goal reached"
                        hasGoal -> ceil((goalMillis - todayMillis).coerceAtLeast(0L) / 60_000.0).toInt().let {
                            "$it ${if (it == 1) "minute" else "minutes"} to your goal"
                        }
                        readingDaysThisWeek > 0 -> "$readingDaysThisWeek reading ${if (readingDaysThisWeek == 1) "day" else "days"} this week"
                        else -> "Recorded reading time"
                    }, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
                if (hasGoal) Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { fraction }, modifier = Modifier.size(64.dp),
                        color = scheme.primary, trackColor = scheme.surfaceContainerHighest, strokeWidth = 5.dp)
                    androidx.compose.animation.Crossfade(reachedGoal,
                        animationSpec = com.veritas.reader.ui.VeritasMotion.effectsFast(), label = "goalReachedStatus") { reached ->
                        if (reached) Icon(Icons.Filled.Check, "Goal reached", tint = scheme.primary)
                        else Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold, color = scheme.primary)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showGoalPicker = true }, modifier = Modifier.weight(1f)) {
                    Text(if (hasGoal) "Edit goal" else "Set a goal")
                }
                TextButton(onClick = onOpenInsights, modifier = Modifier.weight(1f)) { Text("View insights →") }
            }
        }
    }
    if (showGoalPicker) HomeReadingGoalDialog(goalMinutes, onGoalMinutesChange) { showGoalPicker = false }
}

@Composable
internal fun HomeReadingGoalDialog(goalMinutes: Int, onGoalMinutesChange: (Int) -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily reading goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Choose a target, or track your reading without one.", color = scheme.onSurfaceVariant)
                val options = (listOf(0, 15, 30, 45, 60) + goalMinutes.takeIf { it > 0 }.let { listOfNotNull(it) }).distinct().sorted()
                options.forEach { minutes ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                        onGoalMinutesChange(minutes)
                        onDismiss()
                    }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (minutes == 0) "No goal" else "$minutes minutes a day", Modifier.weight(1f),
                            color = if (minutes == goalMinutes) scheme.primary else scheme.onSurface)
                        if (minutes == goalMinutes) Icon(Icons.Filled.Check, "Selected", tint = scheme.primary)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
