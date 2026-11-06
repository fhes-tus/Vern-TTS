package com.veritas.reader

import androidx.compose.ui.geometry.Offset

import com.veritas.reader.ui.screens.outlineRevealAnchor

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Universal zoom control pill rendered at the bottom-right of the document viewport.
 */
@Composable
internal fun ActualDocumentZoomPill(
    zoomScale: Float,
    bottomBarVisible: Boolean,
    isLandscape: Boolean,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onZoomReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(end = 12.dp, bottom = if (bottomBarVisible && !isLandscape) 96.dp else 16.dp),
        shape = com.veritas.reader.VeritasPackStyle.compactShape(),
        tonalElevation = 4.dp,
        color = com.veritas.reader.VeritasPackStyle.panelColor(MaterialTheme.colorScheme)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            TextButton(
                onClick = onZoomOut,
                enabled = zoomScale > MIN_CANVAS_ZOOM + 0.01f
            ) { Text("−", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            TextButton(onClick = onZoomReset) {
                Text(
                    "${(zoomScale * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            TextButton(
                onClick = onZoomIn,
                enabled = zoomScale < MAX_CANVAS_ZOOM - 0.01f
            ) { Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}

/**
 * Floating Top App Bar for the Actual Document view.
 */
@Composable
internal fun ActualDocumentTopBar(
    topBarOffset: Float,
    document: SavedDocument,
    isPdf: Boolean,
    isPresentation: Boolean,
    isEpub: Boolean,
    isDocx: Boolean,
    isImage: Boolean,
    pageIndex: Int,
    pageCount: Int,
    zoomScale: Float,
    rotationDegrees: Int,
    isLandscape: Boolean,
    onPageSelected: (Int) -> Unit,
    onClose: () -> Unit,
    onToggleOrientation: () -> Unit,
    topBarVisible: Boolean,
    onToggleFullScreen: () -> Unit,
    onResetZoom: () -> Unit,
    paperToneMode: PaperToneMode,
    onPaperToneModeChange: (PaperToneMode) -> Unit,
    showMenu: Boolean,
    onMenuVisibilityChange: (Boolean) -> Unit,
    onOpenJumpToPageDialog: () -> Unit,
    onOpenExternal: () -> Unit,
    onOpenVoiceStudio: () -> Unit,
    onOpenDocInfo: () -> Unit,
    onOpenOutline: () -> Unit = {},
    onOutlinePositioned: (Offset) -> Unit = {},
    hasOriginal: Boolean,
    originalUri: Uri?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = topBarOffset }
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = VeritasPackStyle.cardShape(),
        color = VeritasPackStyle.headerColor(MaterialTheme.colorScheme),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme),
        tonalElevation = 0.dp,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Back Button
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Center Title & Page Slider / Subtitle
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = if (isPdf) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_pdf) else if (isPresentation) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_ppt) else if (isEpub) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_epub) else if (isDocx) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_docx) else if (isImage) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_img) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_doc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = document.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if ((isPdf || isPresentation || isEpub || isDocx) && pageCount > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_185, com.veritas.reader.ui.screens.localizedUiValue(if (isPresentation) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_fragment_slide) else if (isEpub) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_fragment_chapter) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_fragment_page)), com.veritas.reader.ui.screens.localizedUiValue(pageIndex + 1), com.veritas.reader.ui.screens.localizedUiValue(pageCount)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (zoomScale > 1.05f) {
                            Text(
                                text = "• ${(zoomScale * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        if (rotationDegrees != 0) {
                            Text(
                                text = "• ${rotationDegrees}°",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    SlimPageSlider(
                        pageIndex = pageIndex,
                        pageCount = pageCount,
                        onPageSelected = onPageSelected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                    )
                } else {
                    Text(
                        text = if (isPresentation) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_powerpoint_slide_view) else if (isEpub) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_epub_book_view) else if (isDocx) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_word_document_view) else if (isImage) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_original_image_view) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_original_document_view),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Table of Contents / Outline Button
            IconButton(
                onClick = onOpenOutline,
                modifier = Modifier.outlineRevealAnchor(onOutlinePositioned)
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_table_of_contents),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Paper Tone Switcher
            IconButton(
                onClick = {
                    val nextMode = when (paperToneMode) {
                        PaperToneMode.ACTIVE_THEME -> PaperToneMode.DARK
                        PaperToneMode.DARK -> PaperToneMode.NATURAL_WHITE
                        PaperToneMode.NATURAL_WHITE -> PaperToneMode.WARM_SEPIA
                        PaperToneMode.WARM_SEPIA -> PaperToneMode.ACTIVE_THEME
                    }
                    onPaperToneModeChange(nextMode)
                },
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.InvertColors,
                    contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_paper_tone),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Rotate Page Button
            IconButton(
                onClick = onToggleOrientation,
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isLandscape) Icons.Filled.StayCurrentPortrait else Icons.Filled.StayCurrentLandscape,
                    contentDescription = if (isLandscape) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_switch_to_portrait) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_switch_to_landscape),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }

            // More Options / Tools Menu (3-dots overflow)
            Box {
                IconButton(
                    onClick = { onMenuVisibilityChange(true) },
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_more_options),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
                ActualDocumentOverflowMenu(
                    expanded = showMenu,
                    onDismiss = { onMenuVisibilityChange(false) },
                    document = document,
                    topBarVisible = topBarVisible,
                    onToggleFullScreen = onToggleFullScreen,
                    onResetZoom = onResetZoom,
                    isLandscape = isLandscape,
                    onToggleOrientation = onToggleOrientation,
                    paperToneMode = paperToneMode,
                    onPaperToneModeChange = onPaperToneModeChange,
                    hasMultiplePages = (isPdf || isPresentation || isEpub || isDocx) && pageCount > 1,
                    isPresentation = isPresentation,
                    isEpub = isEpub,
                    pageIndex = pageIndex,
                    pageCount = pageCount,
                    onSelectPage = onPageSelected,
                    onOpenJumpToPageDialog = onOpenJumpToPageDialog,
                    onOpenExternal = onOpenExternal,
                    onOpenVoiceStudio = onOpenVoiceStudio,
                    onOpenDocInfo = onOpenDocInfo,
                    onOpenOutline = onOpenOutline,
                    onClose = onClose,
                    hasOriginal = hasOriginal,
                    originalUri = originalUri
                )
            }
        }
    }
}

/**
 * Overflow dropdown menu for Document Tools.
 */
@Composable
internal fun ActualDocumentOverflowMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    document: SavedDocument,
    topBarVisible: Boolean,
    onToggleFullScreen: () -> Unit,
    onResetZoom: () -> Unit,
    isLandscape: Boolean,
    onToggleOrientation: () -> Unit,
    paperToneMode: PaperToneMode,
    onPaperToneModeChange: (PaperToneMode) -> Unit,
    hasMultiplePages: Boolean,
    isPresentation: Boolean,
    isEpub: Boolean,
    pageIndex: Int,
    pageCount: Int,
    onSelectPage: (Int) -> Unit,
    onOpenJumpToPageDialog: () -> Unit,
    onOpenExternal: () -> Unit,
    onOpenVoiceStudio: () -> Unit,
    onOpenDocInfo: () -> Unit,
    onOpenOutline: () -> Unit = {},
    onClose: () -> Unit,
    hasOriginal: Boolean,
    originalUri: Uri?
) {
    val context = LocalContext.current

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .width(290.dp)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Text(
            text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_document_tools),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Text(
            text = document.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

        // --- VIEW & DISPLAY ---
        Text(
            text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_display),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        DropdownMenuItem(
            text = {
                Column {
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_fit_to_screen), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_reset_zoom_to_100), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            leadingIcon = {
                Icon(Icons.Filled.FitScreen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            onClick = {
                onResetZoom()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = {
                Column {
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_rotate_view_90), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(if (isLandscape) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_switch_to_portrait) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_switch_to_landscape), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            leadingIcon = {
                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            onClick = {
                onToggleOrientation()
                onDismiss()
            }
        )
        DropdownMenuItem(
            text = {
                Column {
                    Text(
                        when (paperToneMode) {
                            PaperToneMode.ACTIVE_THEME -> com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_default)
                            PaperToneMode.DARK -> com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_dark_slate)
                            PaperToneMode.NATURAL_WHITE -> com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_bone)
                            PaperToneMode.WARM_SEPIA -> com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_sepia)
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        when (paperToneMode) {
                            PaperToneMode.ACTIVE_THEME -> com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_tap_for_dark_slate)
                            PaperToneMode.DARK -> com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_tap_for_bone)
                            PaperToneMode.NATURAL_WHITE -> com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_tap_for_sepia)
                            PaperToneMode.WARM_SEPIA -> com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_tap_for_default)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            leadingIcon = {
                Icon(Icons.Filled.InvertColors, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            onClick = {
                val nextMode = when (paperToneMode) {
                    PaperToneMode.ACTIVE_THEME -> PaperToneMode.DARK
                    PaperToneMode.DARK -> PaperToneMode.NATURAL_WHITE
                    PaperToneMode.NATURAL_WHITE -> PaperToneMode.WARM_SEPIA
                    PaperToneMode.WARM_SEPIA -> PaperToneMode.ACTIVE_THEME
                }
                onPaperToneModeChange(nextMode)
                onDismiss()
            }
        )

        // --- PAGE NAVIGATION ---
        if (hasMultiplePages) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            Text(
                text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_navigation),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_table_of_contents_outline), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_chapters_headings_sections), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                onClick = {
                    onOpenOutline()
                    onDismiss()
                }
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_186, com.veritas.reader.ui.screens.localizedUiValue(if (isPresentation) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_fragment_slide) else if (isEpub) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_fragment_chapter) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_fragment_page))), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_000, com.veritas.reader.ui.screens.localizedUiValue(pageCount)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                leadingIcon = {
                    Icon(Icons.Filled.Numbers, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                onClick = {
                    onOpenJumpToPageDialog()
                    onDismiss()
                }
            )
            DropdownMenuItem(
                text = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_187, com.veritas.reader.ui.screens.localizedUiValue(if (isPresentation) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_fragment_slide) else if (isEpub) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_fragment_chapter) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_fragment_page))), color = MaterialTheme.colorScheme.onSurface) },
                leadingIcon = {
                    Icon(Icons.Filled.FirstPage, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                enabled = pageIndex > 0,
                onClick = {
                    onSelectPage(0)
                    onDismiss()
                }
            )
            DropdownMenuItem(
                text = { Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_dynamic_188, com.veritas.reader.ui.screens.localizedUiValue(if (isPresentation) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_fragment_slide) else if (isEpub) com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_fragment_chapter) else com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_fragment_page)), com.veritas.reader.ui.screens.localizedUiValue(pageCount)), color = MaterialTheme.colorScheme.onSurface) },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                },
                enabled = pageIndex < pageCount - 1,
                onClick = {
                    onSelectPage(pageCount - 1)
                    onDismiss()
                }
            )
        }

        // --- STUDY & AUDIO ---
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        Text(
            text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_reading_audio),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        DropdownMenuItem(
            text = {
                Column {
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_switch_to_text_reader), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_flowing_text_notes_speed_reader), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            leadingIcon = {
                Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            onClick = {
                onDismiss()
                onClose()
            }
        )
        DropdownMenuItem(
            text = {
                Column {
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_voice_studio), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_narrators_speed_audio_tuning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            leadingIcon = {
                Icon(Icons.Filled.Mic, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            onClick = {
                onDismiss()
                onOpenVoiceStudio()
            }
        )

        // --- FILE & SHARE ---
        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        DropdownMenuItem(
            text = {
                Column {
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_share_original_file), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_send_to_other_apps), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            leadingIcon = {
                Icon(Icons.Filled.Share, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            enabled = hasOriginal,
            onClick = {
                onDismiss()
                val repo = DocumentRepository(context)
                val shareUri = repo.getShareableUri(document) ?: originalUri
                shareUri?.let { uri ->
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        val mime = repo.getShareMimeType(document)
                        type = mime
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, document.title)
                        clipData = android.content.ClipData.newUri(context.contentResolver, document.title, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
                }
            }
        )
        DropdownMenuItem(
            text = {
                Column {
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_open_in_external_app), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_use_system_pdf_or_photo_viewer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            leadingIcon = {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            enabled = hasOriginal,
            onClick = {
                onDismiss()
                onOpenExternal()
            }
        )
        DropdownMenuItem(
            text = {
                Text(com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_document_information), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            },
            leadingIcon = {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            onClick = {
                onOpenDocInfo()
                onDismiss()
            }
        )
    }
}

/**
 * Floating bottom player bar wrapping DocPlayerPanel.
 */
@Composable
internal fun ActualDocumentBottomBar(
    bottomBarOffset: Float,
    documentId: String? = null,
    isPlaying: Boolean,
    statusMessage: String,
    rate: Float,
    pitch: Float,
    fontSizeSp: Int,
    sectionSpacingDp: Int = 10,
    queueCount: Int,
    canGoPrevious: Boolean,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRateChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onSectionSpacingChange: (Int) -> Unit = {},
    onOpenVoiceStudio: () -> Unit,
    voices: List<TtsVoiceOption>,
    voiceSettings: VoiceSettings,
    onVoiceSelected: (TtsVoiceOption) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = bottomBarOffset }
    ) {
        DocPlayerPanel(
            documentId = documentId,
            isPlaying = isPlaying,
            statusMessage = statusMessage,
            rate = rate,
            pitch = pitch,
            fontSizeSp = fontSizeSp,
            sectionSpacingDp = sectionSpacingDp,
            queueCount = queueCount,
            canGoPrevious = canGoPrevious,
            canGoNext = canGoNext,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onRateChange = onRateChange,
            onPitchChange = onPitchChange,
            onFontSizeChange = onFontSizeChange,
            onSectionSpacingChange = onSectionSpacingChange,
            onOpenVoiceStudio = onOpenVoiceStudio,
            voices = voices,
            voiceSettings = voiceSettings,
            onVoiceSelected = onVoiceSelected,
            onToggleTextMode = onClose
        )
    }
}
