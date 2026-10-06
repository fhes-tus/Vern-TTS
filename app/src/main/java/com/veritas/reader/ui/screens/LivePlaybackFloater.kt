package com.veritas.reader.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.veritas.reader.*

@Composable
internal fun LivePlaybackFloater(document: SavedDocument, onOpen: () -> Unit, onPlayPause: () -> Unit,
    collapsed: Boolean = false, expansion: Float = 1f, onToggleCollapsed: () -> Unit = {}) {
    val context = LocalContext.current
    val playing = PlaybackStateStore.isPlaying
    val count = PlaybackStateStore.chunkCount
    val index = PlaybackStateStore.currentIndex.coerceIn(0, (count - 1).coerceAtLeast(0))
    val status = PlaybackStateStore.statusMessage
    val needsStatus = listOf("Preparing", "Could not", "No readable", "Error", "Failed", "Loading").any { status.startsWith(it, ignoreCase = true) }
    val progress = expansion.coerceIn(0f, 1f)
    val packShape = VeritasPackStyle.cardShape()
    val playerHeight = VeritasPackStyle.playerHeight(VeritasPackStyle.currentPackId())
    val density = androidx.compose.ui.platform.LocalDensity.current
    val isGlassCapable = isDeviceGlassCapable() && LocalVeritasBackdrop.current != null
    val isLiquidGlass = VeritasPackStyle.glassChromeEnabled() && isGlassCapable
    BoxWithConstraints(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).widthIn(max = 728.dp).fillMaxWidth().height(playerHeight)) {
    val fullWidth = maxWidth
    val packCorner = packShape.topStart.toPx(androidx.compose.ui.geometry.Size(
        with(density) { fullWidth.toPx() }, with(density) { playerHeight.toPx() }), density) / density.density
    val collapsedSize = playerHeight
    val corner = (collapsedSize.value / 2f + (packCorner.coerceAtMost(collapsedSize.value / 2f) - collapsedSize.value / 2f) * progress).dp
    val playerShape = androidx.compose.foundation.shape.RoundedCornerShape(corner)
    Surface(Modifier.width(collapsedSize + (fullWidth - collapsedSize) * progress).height(playerHeight)
        .veritasGlassBackdrop(playerShape, isLiquidGlass, blurRadiusDp = 28f,
            surfaceOpacity = VeritasPackStyle.playerOpacity(VeritasPackStyle.currentPackId()))
        .testTag("live_playback_floater"),
        shape = playerShape,
        color = if (isLiquidGlass) androidx.compose.ui.graphics.Color.Transparent
            else if (VeritasPackStyle.currentPackId() == "liquid_glass") MaterialTheme.colorScheme.surface
            else VeritasPackStyle.playerSurfaceColor(MaterialTheme.colorScheme),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme), tonalElevation = 0.dp,
        shadowElevation = VeritasPackStyle.chromeElevation(VeritasPackStyle.currentPackId())) {
        Box(Modifier.fillMaxSize().clipToBounds()) {
        if (progress > 0f) Column(Modifier.requiredWidth(fullWidth).align(Alignment.CenterStart)
            .graphicsLayer { alpha = ((progress - .25f) / .75f).coerceIn(0f, 1f) }
            .then(if (collapsed || progress < 1f) Modifier.clearAndSetSemantics {} else Modifier)) {
            Row(Modifier.fillMaxWidth().height(playerHeight - 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onToggleCollapsed, Modifier.width(32.dp).height(48.dp).testTag("floater_collapse"), enabled = !collapsed) {
                    val chevronTint = if (VeritasPackStyle.currentPackId() == "material_you") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    Icon(Icons.Default.ChevronLeft, "Collapse player", Modifier.size(20.dp), tint = chevronTint)
                }
                Row(Modifier.weight(1f).testTag("floater_open_book").clickable(role = Role.Button, onClickLabel = "Open ${document.title}", onClick = onOpen)
                    .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DocumentArtwork(document, Modifier.size(50.dp).clearAndSetSemantics {}, compact = true)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(document.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (count > 0 && !needsStatus) "${if (playing) "Listening" else "Paused"} · Sentence ${index + 1} of $count" else status,
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton({ if (PlaybackStateStore.activeDocumentId == document.id) sendPlaybackIntent(context, PlaybackActions.ACTION_PREVIOUS) },
                    Modifier.size(40.dp).testTag("floater_previous"), enabled = count > 0 && index > 0) {
                    val isMaterialYou = VeritasPackStyle.currentPackId() == "material_you"
                    val btnBg = if (isMaterialYou) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f)
                    val iconTint = if (isMaterialYou) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    Icon(Icons.Default.SkipPrevious, "Previous sentence", Modifier.background(btnBg, CircleShape).padding(6.dp).size(20.dp), tint = iconTint)
                }
                FilledIconButton(onPlayPause, Modifier.size(if (VeritasPackStyle.currentPackId() == "one_ui") 50.dp else 44.dp).testTag("floater_play_pause"), shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause playback" else "Resume playback")
                }
                IconButton({ if (PlaybackStateStore.activeDocumentId == document.id) sendPlaybackIntent(context, PlaybackActions.ACTION_NEXT) },
                    Modifier.size(40.dp).testTag("floater_next"), enabled = count > 0 && index < count - 1) {
                    val isMaterialYou = VeritasPackStyle.currentPackId() == "material_you"
                    val btnBg = if (isMaterialYou) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f)
                    val iconTint = if (isMaterialYou) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    Icon(Icons.Default.SkipNext, "Next sentence", Modifier.background(btnBg, CircleShape).padding(6.dp).size(20.dp), tint = iconTint)
                }
            }
            if (count > 0) {
                val isMaterialYou = VeritasPackStyle.currentPackId() == "material_you"
                LinearProgressIndicator(
                    progress = { (index + 1f) / count },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = if (isMaterialYou) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
        if (progress < 1f) Box(Modifier.size(collapsedSize).graphicsLayer { alpha = (1f - progress * 2f).coerceIn(0f, 1f) }
            .testTag("floater_expand").clickable(enabled = collapsed, role = Role.Button, onClickLabel = "Expand player", onClick = onToggleCollapsed),
            contentAlignment = Alignment.Center) { PlaybackWave(playing) }
        }
    }
    }
}

@Composable
private fun PlaybackWave(playing: Boolean) {
    val color = if (VeritasPackStyle.currentPackId() == "material_you") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val animateWave = playing && !com.veritas.reader.ui.VeritasMotion.scheme.reduceMotion
    val phase = if (animateWave) {
        val transition = rememberInfiniteTransition(label = "playingWave")
        val value by transition.animateFloat(0f, (Math.PI * 2).toFloat(),
            infiniteRepeatable(tween(900, easing = LinearEasing)), label = "wavePhase")
        value
    } else 0f
    Canvas(Modifier.size(36.dp).clearAndSetSemantics {}) {
        repeat(5) { i ->
            val amount = if (animateWave) .25f + .7f * ((kotlin.math.sin(phase + i * 1.3f) + 1f) / 2f) else .25f + .15f * (2 - kotlin.math.abs(i - 2))
            val x = size.width * (i + 1) / 6f
            drawLine(color, Offset(x, size.height * (1f - amount) / 2), Offset(x, size.height * (1f + amount) / 2),
                strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}
