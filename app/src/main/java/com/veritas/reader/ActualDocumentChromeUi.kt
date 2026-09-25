package com.veritas.reader

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
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Palette
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
import androidx.compose.ui.res.painterResource
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
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
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
                    fontWeight = FontWeight.Bold,
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
        color = MaterialTheme.colorScheme.surface.copy(alpha = VeritasPackStyle.surfaceAlpha()),
        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme),
        tonalElevation = 4.dp,
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
                    contentDescription = "Back",
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
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = if (isPdf) "PDF" else if (isPresentation) "PPT" else if (isEpub) "EPUB" else if (isDocx) "DOCX" else if (isImage) "IMG" else "DOC",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = document.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if ((isPdf || isPresentation || isEpub || isDocx) && pageCount > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${if (isPresentation) "Slide" else if (isEpub) "Chapter" else "Page"} ${pageIndex + 1} of $pageCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (zoomScale > 1.05f) {
                            Text(
                                text = "• ${(zoomScale * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (rotationDegrees != 0) {
                            Text(
                                text = "• ${rotationDegrees}°",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold
                            )
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
                        text = if (isPresentation) "PowerPoint Slide View" else if (isEpub) "EPUB Book View" else if (isDocx) "Word Document View" else if (isImage) "Original Image View" else "Original Document View",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Table of Contents / Outline Button
            IconButton(
                onClick = onOpenOutline,
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = "Table of Contents",
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
                    contentDescription = "Paper Tone",
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
                    contentDescription = if (isLandscape) "Switch to portrait" else "Switch to landscape",
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
                        contentDescription = "More Options",
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
            text = "Document Tools",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
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
            text = "Display",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        DropdownMenuItem(
            text = {
                Column {
                    Text("Fit to Screen", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Reset zoom to 100%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("Rotate View (90°)", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(if (isLandscape) "Switch to portrait" else "Switch to landscape", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            PaperToneMode.ACTIVE_THEME -> "Default"
                            PaperToneMode.DARK -> "Dark slate"
                            PaperToneMode.NATURAL_WHITE -> "Bone"
                            PaperToneMode.WARM_SEPIA -> "Sepia"
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        when (paperToneMode) {
                            PaperToneMode.ACTIVE_THEME -> "Tap for Dark slate"
                            PaperToneMode.DARK -> "Tap for Bone"
                            PaperToneMode.NATURAL_WHITE -> "Tap for Sepia"
                            PaperToneMode.WARM_SEPIA -> "Tap for Default"
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
                text = "Navigation",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Table of Contents / Outline", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Chapters, headings & sections", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text("Jump to ${if (isPresentation) "Slide" else if (isEpub) "Chapter" else "Page"}...", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Go to 1–$pageCount", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                text = { Text("First ${if (isPresentation) "Slide" else if (isEpub) "Chapter" else "Page"} (1)", color = MaterialTheme.colorScheme.onSurface) },
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
                text = { Text("Last ${if (isPresentation) "Slide" else if (isEpub) "Chapter" else "Page"} ($pageCount)", color = MaterialTheme.colorScheme.onSurface) },
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
            text = "Reading & Audio",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        DropdownMenuItem(
            text = {
                Column {
                    Text("Switch to Text Reader", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Flowing text, notes & speed reader", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("Voice Studio", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Narrators, speed & audio tuning", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("Share Original File", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Send to other apps", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("Open in External App", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Use system PDF or photo viewer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("Document Information", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
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
