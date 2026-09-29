package com.veritas.reader.ui.screens


import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.veritas.reader.FlashcardProgress
import com.veritas.reader.FlashcardSet
import com.veritas.reader.MathText
import com.veritas.reader.VeritasPackStyle
import kotlinx.coroutines.launch


@Composable
internal fun StudyEmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    onGoToLibrary: () -> Unit,
    primaryActionLabel: String = "Import file",
    onPrimaryAction: () -> Unit = {},
    onImportFile: (() -> Unit)? = null
) {
    val actualPrimaryAction = onImportFile ?: onPrimaryAction
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = actualPrimaryAction) {
                    Text(primaryActionLabel)
                }
                OutlinedButton(onClick = onGoToLibrary) {
                    Text("Go to Library")
                }
            }
        }
    }
}

internal fun renderMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    // Defensive slice: a marker's closing delimiter can, in malformed input, resolve
    // to a position at or before the opening one. An unguarded substring(begin, end)
    // with begin > end throws StringIndexOutOfBounds during the tap/draw pass that
    // builds this AnnotatedString — the crash this guards against.
    fun String.safeSlice(begin: Int, end: Int): String {
        val a = begin.coerceIn(0, length)
        val b = end.coerceIn(a, length)
        return substring(a, b)
    }
    return androidx.compose.ui.text.buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
                        append(text.safeSlice(i + 2, end))
                        pop()
                        i = end + 2
                    } else {
                        append("**")
                        i += 2
                    }
                }
                text.startsWith("*", i) -> {
                    val end = text.indexOf("*", i + 1)
                    if (end != -1) {
                        pushStyle(androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic))
                        append(text.safeSlice(i + 1, end))
                        pop()
                        i = end + 1
                    } else {
                        append("*")
                        i += 1
                    }
                }
                text.startsWith("##", i) -> {
                    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.ExtraBold, fontSize = 15.sp))
                    val end = text.indexOf("\n", i)
                    if (end != -1) {
                        append(text.safeSlice(i + 2, end).trim())
                        pop()
                        i = end
                    } else {
                        append(text.safeSlice(i + 2, text.length).trim())
                        pop()
                        i = text.length
                    }
                }
                text.startsWith("__", i) -> {
                    val end = text.indexOf("__", i + 2)
                    if (end != -1) {
                        pushStyle(androidx.compose.ui.text.SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline))
                        append(text.safeSlice(i + 2, end))
                        pop()
                        i = end + 2
                    } else {
                        append("__")
                        i += 2
                    }
                }
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}

/**
 * A flashcard set as a tile (two per row), coloured like the old spaced-repetition
 * deck card. Shows the set name, a "View cards" button, and recall pills that
 * double as filters (tap "Hard" → only that bucket). Overflow menu renames/deletes.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun FlashcardSetTile(
    set: FlashcardSet,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
    onViewBucket: (String) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showDeleteDeckConfirm by remember { mutableStateOf(false) }
    val counts = set.recallCounts

    if (showDeleteDeckConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteDeckConfirm = false },
            title = { Text("Delete Flashcard Deck?") },
            text = { Text("Are you sure you want to delete \"${set.name}\" and all of its ${set.cards.size} cards? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDeckConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDeckConfirm = false }) { Text("Cancel") }
            }
        )
    }
    // Cover-sized tile matching the library grid (portrait 0.75 aspect). Tap anywhere
    // opens the viewer (no "View cards" button).
    Card(
        modifier = modifier.aspectRatio(0.75f).clickable { onOpen() },
        shape = VeritasPackStyle.cardShape(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.size(34.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Book,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Set options", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; onRename() })
                        DropdownMenuItem(text = { Text("Delete set") }, onClick = { menuOpen = false; showDeleteDeckConfirm = true })
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                set.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "${set.cards.size} card${if (set.cards.size == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            // Recall pills — larger and centered; each filters to its bucket.
            if (counts.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FLASHCARD_RECALL_META.forEach { (key, label, color) ->
                        val n = counts[key] ?: 0
                        if (n > 0) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = color.copy(alpha = 0.16f),
                                modifier = Modifier.clickable { onViewBucket(key) }
                            ) {
                                Text(
                                    "$n $label",
                                    color = color,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Softer, calmer shades (same hues) — the saturated originals were eye-piercing as
// solid button fills. Used for both the viewer buttons and the tile recall pills.
internal val FLASHCARD_RECALL_META: List<Triple<String, String, Color>> = listOf(
    Triple("again", "Again", Color(0xFFE57373)),
    Triple("hard", "Hard", Color(0xFFFFB74D)),
    Triple("good", "Good", Color(0xFF64B5F6)),
    Triple("easy", "Easy", Color(0xFF4DB6AC))
)

/**
 * Full-screen single-card viewer (see design reference): one card at a time, tap
 * to flip front↔back, forward/back through the set, recall buttons that bucket the
 * card (latest-wins), a subtle per-card delete, and "Exit card". Cards are passed
 * pre-filtered when opened from a recall pill.
 */
@Composable
internal fun FlashcardViewerDialog(
    setName: String,
    cards: List<FlashcardProgress>,
    onRate: (String, String) -> Unit,
    onDeleteCard: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var order by remember(cards) { mutableStateOf(cards) }
    var index by remember { mutableStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val dragOffsetX = remember { Animatable(0f) }
    val dragOffsetY = remember { Animatable(0f) }
    var isSwipingOut by remember { mutableStateOf(false) }
    var showDeleteCardConfirm by remember { mutableStateOf(false) }

    if (order.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val safeIndex = index.coerceIn(0, order.lastIndex)
    val card = order[safeIndex]

    val animatedRotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "flashcardFlip"
    )
    val isShowingBack = animatedRotation > 90f

    fun goTo(next: Int) {
        index = next.coerceIn(0, order.lastIndex)
        flipped = false
    }

    if (showDeleteCardConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteCardConfirm = false },
            title = { Text("Delete Flashcard?") },
            text = { Text("Are you sure you want to delete this card? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteCardConfirm = false
                        val removedId = card.id
                        val remaining = order.filterNot { it.id == removedId }
                        onDeleteCard(removedId)
                        order = remaining
                        if (remaining.isNotEmpty()) index = safeIndex.coerceIn(0, remaining.lastIndex)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteCardConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        setName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismiss) { Text("Exit card") }
                }

                // Card is centered with 3D flip rotation effect & smooth swipe gestures
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.05f)
                            .graphicsLayer {
                                rotationY = animatedRotation
                                cameraDistance = 12f * density
                                translationX = dragOffsetX.value
                                translationY = dragOffsetY.value
                                rotationZ = (dragOffsetX.value / 35f).coerceIn(-12f, 12f)
                                alpha = if (isSwipingOut) (1f - (kotlin.math.abs(dragOffsetX.value) / 1600f)).coerceIn(0.1f, 1f) else 1f
                            }
                            .pointerInput(safeIndex) {
                                detectTapGestures(
                                    onTap = { flipped = !flipped }
                                )
                            }
                            .pointerInput(safeIndex) {
                                var accumulatedX = 0f
                                var accumulatedY = 0f
                                detectDragGestures(
                                    onDragStart = {
                                        accumulatedX = 0f
                                        accumulatedY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        accumulatedX += dragAmount.x
                                        accumulatedY += dragAmount.y
                                        coroutineScope.launch {
                                            dragOffsetX.snapTo(accumulatedX.coerceIn(-500f, 500f))
                                            dragOffsetY.snapTo(accumulatedY.coerceIn(-500f, 80f))
                                        }
                                    },
                                    onDragEnd = {
                                        val threshold = 90f
                                        coroutineScope.launch {
                                            when {
                                                accumulatedY < -threshold && kotlin.math.abs(accumulatedY) > kotlin.math.abs(accumulatedX) -> {
                                                    // Total swipe Up: Fly off top of screen
                                                    isSwipingOut = true
                                                    dragOffsetY.animateTo(-1600f, tween(220, easing = LinearOutSlowInEasing))
                                                    goTo(safeIndex + 1)
                                                    dragOffsetX.snapTo(0f)
                                                    dragOffsetY.snapTo(0f)
                                                    isSwipingOut = false
                                                }
                                                accumulatedX < -threshold -> {
                                                    // Total swipe Left: Fly completely off screen to left
                                                    isSwipingOut = true
                                                    dragOffsetX.animateTo(-1800f, tween(220, easing = LinearOutSlowInEasing))
                                                    if (safeIndex < order.lastIndex) goTo(safeIndex + 1)
                                                    dragOffsetX.snapTo(0f)
                                                    dragOffsetY.snapTo(0f)
                                                    isSwipingOut = false
                                                }
                                                accumulatedX > threshold -> {
                                                    // Total swipe Right: Fly completely off screen to right
                                                    isSwipingOut = true
                                                    dragOffsetX.animateTo(1800f, tween(220, easing = LinearOutSlowInEasing))
                                                    if (safeIndex > 0) goTo(safeIndex - 1)
                                                    dragOffsetX.snapTo(0f)
                                                    dragOffsetY.snapTo(0f)
                                                    isSwipingOut = false
                                                }
                                                else -> {
                                                    dragOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                                    dragOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                                }
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        coroutineScope.launch {
                                            dragOffsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                            dragOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                        }
                                    }
                                )
                            },
                        shape = VeritasPackStyle.cardShape(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isShowingBack)
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        ),
                        border = BorderStroke(
                            1.5.dp,
                            if (isShowingBack) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    if (isShowingBack) rotationY = 180f
                                }
                                .padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isShowingBack)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                ) {
                                    Text(
                                        text = if (isShowingBack) "ANSWER · TAP TO FLIP" else "QUESTION · TAP TO FLIP",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isShowingBack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState()),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = MathText.beautify(if (isShowingBack) card.back else card.front),
                                        style = MaterialTheme.typography.headlineSmall,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Subtle per-card delete with confirmation
                            IconButton(
                                onClick = { showDeleteCardConfirm = true },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete this card",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Flip affordance icon
                            Icon(
                                Icons.Filled.Autorenew,
                                contentDescription = "Tap card to flip",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.align(Alignment.BottomEnd).size(22.dp)
                            )
                        }
                    }
                }

                Text(
                    "Swipe ← / → to move • Swipe ↑ to skip • Tap to flip",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp, bottom = 4.dp)
                )

                Text(
                    "Rate your recall (Spaced Repetition)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)
                )

                // All four recall buttons with SM-2 interval hints
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FLASHCARD_RECALL_META.forEach { (key, label, color) ->
                        val selected = card.recall == key
                        val intervalHint = com.veritas.reader.SpacedRepetitionScheduler.previewNextInterval(card, key)
                        Button(
                            onClick = {
                                onRate(card.id, key)
                                order = order.map { if (it.id == card.id) it.copy(recall = key) else it }
                                coroutineScope.launch {
                                    val targetX = if (key == "again") -1800f else 1800f
                                    isSwipingOut = true
                                    dragOffsetX.animateTo(targetX, tween(200, easing = LinearOutSlowInEasing))
                                    if (safeIndex < order.lastIndex) goTo(safeIndex + 1)
                                    dragOffsetX.snapTo(0f)
                                    dragOffsetY.snapTo(0f)
                                    isSwipingOut = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) color else color.copy(alpha = 0.65f)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(52.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    if (selected) "✓$label" else label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                                Text(
                                    intervalHint,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(onClick = { goTo(safeIndex - 1) }, enabled = safeIndex > 0) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous card")
                    }
                    Text(
                        "${safeIndex + 1} of ${order.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    IconButton(onClick = { goTo(safeIndex + 1) }, enabled = safeIndex < order.lastIndex) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next card")
                    }
                }
            }
        }
    }
}



@Composable
internal fun StudyDailyReviewHeroCard(
    completionPercent: Int,
    cardsToReview: Int,
    onStartReview: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = VeritasPackStyle.surfaceAlpha())
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.Center
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                val animatedProgress by animateFloatAsState(
                    targetValue = (completionPercent.coerceIn(0, 100)) / 100f,
                    animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                    label = "review_gauge"
                )

                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 7.dp.toPx()
                    drawCircle(
                        color = trackColor,
                        style = Stroke(width = strokeWidth)
                    )
                    drawArc(
                        color = primaryColor,
                        startAngle = -90f,
                        sweepAngle = animatedProgress * 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }

                Text(
                    text = "$completionPercent%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Daily Review",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$cardsToReview Cards to review today",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onStartReview,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Start Review",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun StudyAiToolCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = VeritasPackStyle.surfaceAlpha())
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
internal fun StudyActiveDeckItem(
    indexNumber: Int,
    title: String,
    cardCount: Int,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = VeritasPackStyle.surfaceAlpha())
        ),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "$indexNumber)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$cardCount cards",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}



