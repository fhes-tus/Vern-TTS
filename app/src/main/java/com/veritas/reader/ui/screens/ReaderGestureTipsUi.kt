package com.veritas.reader.ui.screens

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
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.ui.VeritasMotion

@Composable
internal fun ReaderGestureTipsBanner(
    visible: Boolean,
    onDismissCurrent: () -> Unit,
    onDismissPermanently: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(VeritasMotion.effectsFast()) + expandVertically(VeritasMotion.spatial()),
        exit = fadeOut(VeritasMotion.effectsFast()) + shrinkVertically(VeritasMotion.spatial()),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
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
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(5.dp)
                                    .size(18.dp)
                            )
                        }
                        Text(
                            text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_reader_gestures),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onDismissCurrent,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_dismiss_tip),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                GestureTipRow(
                    icon = Icons.Outlined.ZoomIn,
                    title = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_pinch_in_out),
                    description = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_quickly_resize_reading_text)
                )
                Spacer(modifier = Modifier.height(6.dp))
                GestureTipRow(
                    icon = Icons.Outlined.TouchApp,
                    title = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_double_tap_sentence),
                    description = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_start_voice_reading_from_that_exact_line)
                )
                Spacer(modifier = Modifier.height(6.dp))
                GestureTipRow(
                    icon = Icons.Outlined.TouchApp,
                    title = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_long_press_text),
                    description = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_select_words_for_translation_notes_or_search)
                )
                Spacer(modifier = Modifier.height(6.dp))
                GestureTipRow(
                    icon = Icons.Outlined.Tune,
                    title = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_gesture_slider_seek),
                    description = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_gesture_slider_seek_desc)
                )
                Spacer(modifier = Modifier.height(6.dp))
                GestureTipRow(
                    icon = Icons.Outlined.KeyboardArrowUp,
                    title = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_gesture_slide_up_controls),
                    description = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_gesture_slide_up_controls_desc)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismissPermanently
                    ) {
                        Text(
                            text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_don_t_show_again),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = onDismissCurrent
                    ) {
                        Text(
                            text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_got_it),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureTipRow(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface)
        Text(
            text = " — $description",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
