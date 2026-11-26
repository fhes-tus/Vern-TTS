package com.veritas.reader.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.veritas.reader.R
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.ui.VeritasMotion
import kotlin.math.roundToInt

@Composable
internal fun FirstChapterPrompt(
    target: Rect?,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visible = target != null && target.width > 0 && target.height > 0
    val opacity by animateFloatAsState(
        if (visible) 1f else 0f,
        VeritasMotion.effects(),
        label = "First Chapter spotlight"
    )
    var lastTarget by remember { mutableStateOf<Rect?>(null) }
    if (visible) SideEffect { lastTarget = target }
    val anchor = target?.takeIf { visible } ?: lastTarget ?: return
    if (!visible && opacity <= 0f) return

    var origin by remember { mutableStateOf(Offset.Zero) }
    val card = anchor.translate(-origin)
    val scrim = MaterialTheme.colorScheme.scrim
    val frame = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val title = menuString(R.string.vern_chapter_title)
    val hint = menuString(R.string.vern_chapter_prompt)
    val open = menuString(R.string.ui_open)
    val close = menuString(R.string.ui_close)
    BackHandler(enabled = visible, onBack = onDismiss)

    BoxWithConstraints(
        modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }
    ) {
        if (visible) {
            Box(
                Modifier.matchParentSize().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = close,
                    onClick = onDismiss
                )
            )
        }
        Canvas(Modifier.matchParentSize()) {
            val highlight = card.inflate(4.dp.toPx())
            val corner = CornerRadius(22.dp.toPx())
            val opening = Path().apply { addRoundRect(RoundRect(highlight, corner)) }
            clipPath(opening, ClipOp.Difference) {
                drawRect(scrim.copy(alpha = .58f * opacity))
            }
            drawRoundRect(
                frame.copy(alpha = opacity),
                topLeft = highlight.topLeft,
                size = highlight.size,
                cornerRadius = corner,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        if (visible) {
            val cardWidth = with(density) { card.width.toDp() }
            Box(
                Modifier.absoluteOffset { IntOffset(card.left.roundToInt(), card.top.roundToInt()) }
                    .size(cardWidth, with(density) { card.height.toDp() })
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClickLabel = open,
                        onClick = onOpen
                    )
                    .semantics { contentDescription = title }
            )

            val hintTop = with(density) { card.bottom.toDp() } + 14.dp
            if (hintTop + 72.dp <= maxHeight) {
                Surface(
                    onClick = onOpen,
                    modifier = Modifier.absoluteOffset { IntOffset(card.left.roundToInt(), hintTop.roundToPx()) }
                        .width(cardWidth).graphicsLayer { alpha = opacity },
                    shape = VeritasPackStyle.compactShape(),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                ) {
                    Text(
                        hint,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
