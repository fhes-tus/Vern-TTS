package com.veritas.reader.ui.screens

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.veritas.reader.R
import com.veritas.reader.VernDialog
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.ui.OnboardingController
import com.veritas.reader.ui.ReaderUiState
import com.veritas.reader.ui.VeritasMotion
import com.veritas.reader.ui.pressScale
import kotlinx.coroutines.launch

private enum class ChapterTask(
    @StringRes val title: Int,
    @StringRes val description: Int,
    @StringRes val actionLabel: Int,
    val icon: ImageVector
) {
    IMPORT(
        R.string.vern_chapter_import,
        R.string.vern_chapter_import_body,
        R.string.vern_chapter_import_action,
        Icons.Default.Add
    ),
    PACE(
        R.string.vern_chapter_pace,
        R.string.vern_chapter_pace_body,
        R.string.vern_chapter_pace_action,
        Icons.Default.GraphicEq
    ),
    BOOKMARK(
        R.string.vern_chapter_bookmark,
        R.string.vern_chapter_bookmark_body,
        R.string.vern_chapter_bookmark_action,
        Icons.Default.Bookmark
    ),
    NOTE(
        R.string.vern_chapter_note,
        R.string.vern_chapter_note_body,
        R.string.vern_chapter_note_action,
        Icons.Outlined.EditNote
    );

    fun isComplete(state: ReaderUiState): Boolean = when (this) {
        IMPORT -> state.questImportDone
        PACE -> state.questSpeedDone
        BOOKMARK -> state.questBookmarkDone
        NOTE -> state.questNoteDone
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OnboardingQuestChecklist(
    state: ReaderUiState,
    hasReading: Boolean,
    onAddReading: () -> Unit,
    onOpenReading: () -> Unit,
    onWriteNote: () -> Unit,
    onStartTour: () -> Unit,
    onDismissQuests: () -> Unit,
    showSheet: Boolean,
    onShowSheetChange: (Boolean) -> Unit,
    onCardPositioned: (Rect?) -> Unit,
    modifier: Modifier = Modifier
) {
    val tasks = ChapterTask.entries
    val count = tasks.count { it.isComplete(state) }
    val next = tasks.firstOrNull { !it.isComplete(state) }
    val subtitle = next?.title ?: R.string.vern_chapter_finish_title
    val progress by animateFloatAsState(
        count.toFloat() / tasks.size,
        animationSpec = VeritasMotion.effects(),
        label = "First Chapter progress"
    )
    val progressLabel = menuString(R.string.vern_chapter_progress, count, tasks.size)
    val homeVisible = LocalLibraryPageVisible.current
    val fade = VeritasMotion.effectsFast<Float>()
    val interaction = remember { MutableInteractionSource() }
    val reportCardPosition by rememberUpdatedState(onCardPositioned)
    DisposableEffect(homeVisible) {
        onDispose { if (homeVisible) reportCardPosition(null) }
    }

    Surface(
        onClick = { onShowSheetChange(true) },
        enabled = homeVisible && OnboardingController.activeStep == null,
        modifier = modifier.fillMaxWidth().pressScale(interaction).onGloballyPositioned {
            if (homeVisible && it.isAttached) {
                OnboardingController.updateBounds("quest_checklist", it)
                val bounds = it.boundsInRoot()
                reportCardPosition(bounds.takeIf { rect -> rect.height >= it.size.height - 1f })
            }
        },
        interactionSource = interaction,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = VeritasPackStyle.cardShape()
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (next == null) {
                    Icon(Icons.Default.Check, null, Modifier.size(22.dp))
                } else {
                    OnboardingArtwork(
                        R.drawable.vern_setup_read,
                        aspectRatio = 1.45f,
                        modifier = Modifier.width(64.dp),
                        shape = VeritasPackStyle.compactShape()
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(menuString(R.string.vern_chapter_title), style = MaterialTheme.typography.titleMedium)
                    AnimatedContent(
                        subtitle,
                        transitionSpec = { fadeIn(fade).togetherWith(fadeOut(fade)).using(null) },
                        label = "Next First Chapter milestone"
                    ) { title ->
                        Text(menuString(title), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp))
            }
            ChapterProgress(progress, progressLabel)
        }
    }

    if (showSheet && homeVisible && OnboardingController.activeStep == null) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        val reduceMotion = VeritasMotion.scheme.reduceMotion
        var closing by remember { mutableStateOf(false) }

        fun closeAnd(action: () -> Unit) {
            if (closing) return
            closing = true
            scope.launch {
                if (!reduceMotion) sheet.hide()
                onShowSheetChange(false)
                action()
            }
        }

        val content: @Composable () -> Unit = {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        menuString(if (next == null) R.string.vern_chapter_finish_title else R.string.vern_chapter_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f).semantics { heading() }
                    )
                    IconButton(onClick = { closeAnd {} }, enabled = !closing) {
                        Icon(Icons.Default.Close, menuString(R.string.ui_close))
                    }
                }
                if (next == null) {
                    Text(
                        menuString(R.string.vern_chapter_finish_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = { closeAnd(onStartTour) },
                    enabled = !closing,
                    shape = VeritasPackStyle.compactShape(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Explore, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(menuString(R.string.vern_chapter_tour))
                }
                ChapterProgress(progress, progressLabel)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tasks.forEach { task ->
                        val action = when (task) {
                            ChapterTask.IMPORT -> onAddReading
                            ChapterTask.PACE, ChapterTask.BOOKMARK -> onOpenReading
                            ChapterTask.NOTE -> onWriteNote
                        }
                        val needsReading = !hasReading && (task == ChapterTask.PACE || task == ChapterTask.BOOKMARK)
                        val actionLabel = if (needsReading) R.string.vern_chapter_import_action else task.actionLabel
                        val description = if (task == ChapterTask.IMPORT) {
                            when (state.readingInterest) {
                                "PDF & Research" -> R.string.vern_chapter_import_pdf
                                "Books & Novels" -> R.string.vern_chapter_import_book
                                "Web & Articles" -> R.string.vern_chapter_import_web
                                "Study & Learning" -> R.string.vern_chapter_import_study
                                else -> task.description
                            }
                        } else task.description
                        ChapterTaskRow(task, description, actionLabel, task.isComplete(state), task == next, !closing) {
                            closeAnd(action)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = { closeAnd(onDismissQuests) },
                        enabled = !closing,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(menuString(if (next == null) R.string.vern_chapter_close else R.string.vern_chapter_hide))
                    }
                }
            }
        }
        if (reduceMotion) {
            VernDialog(onDismissRequest = { onShowSheetChange(false) }) {
                Surface(
                    shape = VeritasPackStyle.cardShape(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Box(Modifier.padding(top = 12.dp)) { content() }
                }
            }
        } else {
            ModalBottomSheet(
                onDismissRequest = { onShowSheetChange(false) },
                sheetState = sheet,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) { content() }
        }
    }
}

@Composable
private fun ChapterProgress(progress: Float, label: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ChapterTaskRow(
    task: ChapterTask,
    @StringRes description: Int,
    @StringRes actionLabel: Int,
    complete: Boolean,
    next: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val shape = VeritasPackStyle.compactShape()
    val action = if (complete) Modifier else Modifier.clip(shape).clickable(
        enabled = enabled,
        role = Role.Button,
        onClickLabel = menuString(actionLabel),
        onClick = onClick
    )
    Surface(
        modifier = Modifier.fillMaxWidth().then(action),
        shape = shape,
        color = if (next) colors.secondaryContainer else colors.surfaceContainer,
        contentColor = if (next) colors.onSecondaryContainer else colors.onSurface
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (complete) colors.primaryContainer else colors.surface,
                contentColor = if (complete) colors.onPrimaryContainer else colors.primary
            ) {
                Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        if (complete) Icons.Default.Check else task.icon,
                        contentDescription = if (complete) menuString(R.string.vern_chapter_done) else null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(menuString(task.title), style = MaterialTheme.typography.titleSmall)
                if (!complete) {
                    Text(menuString(description), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!complete) Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(17.dp))
        }
    }
}
