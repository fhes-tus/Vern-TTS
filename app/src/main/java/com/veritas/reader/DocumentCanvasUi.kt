package com.veritas.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)

@Composable
internal fun getCanvasColors(paperToneMode: PaperToneMode): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    return when (paperToneMode) {
        PaperToneMode.ACTIVE_THEME -> MaterialTheme.colorScheme.surfaceContainerLow to MaterialTheme.colorScheme.onSurface
        PaperToneMode.DARK -> androidx.compose.ui.graphics.Color(0xFF141414) to androidx.compose.ui.graphics.Color(0xFFE8E8E8)
        PaperToneMode.NATURAL_WHITE -> androidx.compose.ui.graphics.Color(0xFFFFFFFF) to androidx.compose.ui.graphics.Color(0xFF1C1B1F)
        PaperToneMode.WARM_SEPIA -> androidx.compose.ui.graphics.Color(0xFFFBF0D9) to androidx.compose.ui.graphics.Color(0xFF3C2F2F)
    }
}

@Composable
internal fun PresentationSlideCanvas(
    slide: PptxSlideContent,
    slideCount: Int,
    slideImages: List<Bitmap>,
    slideImagesByPath: Map<String, Bitmap> = emptyMap(),
    showNotes: Boolean,
    onToggleNotes: () -> Unit,
    onNextSlide: () -> Unit,
    onPrevSlide: () -> Unit,
    onToggleBars: () -> Unit,
    onSelectText: ((String) -> Unit)? = null,
    isPlaying: Boolean = false,
    activeSentencePage: Int = 0,
    activeSentenceText: String = "",
    paperToneMode: PaperToneMode = PaperToneMode.ACTIVE_THEME,
    rotationDegrees: Int = 0,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (slide.layout != null) {
        PositionedPresentationCanvas(slide, slideCount, slideImagesByPath, showNotes, onToggleNotes, onNextSlide, onPrevSlide, onSelectText, modifier)
        return
    }
    val (cardBg, contentColor) = getCanvasColors(paperToneMode)

    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Card(
        modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight),
        shape = if (isLandscape) RectangleShape else RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        ),
        border = if (isLandscape) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isLandscape) 0.dp else 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 14.dp)
        ) {
            // Slide Top Bar (Badge + Notes Pill + Prev/Next Buttons)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "Slide ${slide.number} of $slideCount",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (slide.notesLines.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.clickable { onToggleNotes() },
                            shape = RoundedCornerShape(8.dp),
                            color = if (showNotes) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = "Notes (${slide.notesLines.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (showNotes) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onPrevSlide,
                        enabled = slide.number > 1,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                            contentDescription = "Previous Slide",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onNextSlide,
                        enabled = slide.number < slideCount,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "Next Slide",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Slide Title
            if (slide.titleLines.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = slide.titleLines.joinToString("\n"),
                        style = if (isLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = contentColor
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.2f)
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                    )
                }
            }

            // Slide Body Content (Scrollable with Selectable text & Speech highlighting)
            androidx.compose.runtime.key(slide.number) {
            androidx.compose.foundation.text.selection.SelectionContainer(modifier = Modifier.weight(1f, fill = false)) {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val totalLines = slide.contentLines.size
                    val totalImages = slideImages.size
                    val imagesAfterLine = remember(totalLines, totalImages) {
                        val map = mutableMapOf<Int, MutableList<Int>>()
                        if (totalLines == 0) {
                            map[-1] = (0 until totalImages).toMutableList()
                        } else if (totalImages > 0) {
                            for (imgIdx in 0 until totalImages) {
                                val target = ((imgIdx + 1) * totalLines / (totalImages + 1)).coerceIn(0, totalLines - 1)
                                map.getOrPut(target) { mutableListOf() }.add(imgIdx)
                            }
                        }
                        map
                    }

                    // Render images before lines if slide has no text
                    imagesAfterLine[-1]?.forEach { imgIdx ->
                        val bmp = slideImages.getOrNull(imgIdx)
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Slide Image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .padding(vertical = 4.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    slide.contentLines.forEachIndexed { lineIdx, line ->
                        val isHighlighted = isPlaying &&
                            slide.number == activeSentencePage &&
                            ActiveSentenceMatcher.matches(line, activeSentenceText)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectText?.invoke(line) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f) else androidx.compose.ui.graphics.Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isHighlighted) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(16.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    )
                                }
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = contentColor,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Inline slide image rendered directly between lines/paragraphs
                        imagesAfterLine[lineIdx]?.forEach { imgIdx ->
                            val bmp = slideImages.getOrNull(imgIdx)
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Slide Image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 220.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .padding(vertical = 4.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }

                    // Speaker Notes Area
                    if (showNotes && slide.notesLines.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.8f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Speaker Notes",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                slide.notesLines.forEach { note ->
                                    Text(
                                        text = note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
    }
}

@Composable
internal fun EpubBookCanvas(
    bookTitle: String,
    chapter: EpubChapter,
    chapterCount: Int,
    onNextChapter: () -> Unit,
    onPrevChapter: () -> Unit,
    onToggleBars: () -> Unit,
    onSelectText: ((String) -> Unit)? = null,
    isPlaying: Boolean = false,
    activeSentencePage: Int = 0,
    activeSentenceText: String = "",
    paperToneMode: PaperToneMode = PaperToneMode.ACTIVE_THEME,
    rotationDegrees: Int = 0,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (cardBg, contentColor) = getCanvasColors(paperToneMode)

    Card(
        modifier = if (isLandscape) modifier.fillMaxSize() else modifier.fillMaxWidth(),
        shape = if (isLandscape) RectangleShape else RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        ),
        border = if (isLandscape) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isLandscape) 0.dp else 6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = if (isLandscape) 14.dp else 22.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 14.dp)
        ) {
            // Book Header (Badge + Prev/Next buttons)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Chapter ${chapter.number} of $chapterCount",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onPrevChapter,
                        enabled = chapter.number > 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                            contentDescription = "Previous Chapter",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onNextChapter,
                        enabled = chapter.number < chapterCount,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "Next Chapter",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Chapter Title & Classic Book Ornament
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = chapter.title,
                    style = if (isLandscape) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Text(
                    text = "— ❦ —",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }

            // Chapter Paragraphs (Scrollable with Selectable text & Speech highlighting)
            androidx.compose.foundation.text.selection.SelectionContainer(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = androidx.compose.runtime.key(bookTitle,chapter.number) { rememberLazyListState() },
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(chapter.paragraphs, key = { index, _ -> "${chapter.number}:$index" }) { paraIdx, para ->
                        val imageIndex = Regex("""^\[\[VERITAS_IMAGE:(\d+)]]$""").matchEntire(para.trim())?.groupValues?.get(1)?.toIntOrNull()
                        if (imageIndex != null) {
                            if (imageIndex < chapter.images.size) OriginalDocumentImage(chapter, imageIndex, "Chapter illustration") { chapter.images[imageIndex] }
                            return@itemsIndexed
                        }
                        val isHighlighted = isPlaying &&
                            chapter.number == activeSentencePage &&
                            ActiveSentenceMatcher.matches(para, activeSentenceText)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectText?.invoke(para) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f) else androidx.compose.ui.graphics.Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isHighlighted) {
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .height(18.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    )
                                }
                                Text(
                                    text = para,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        lineHeight = 25.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                                    ),
                                    color = contentColor,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                    }
                }
            }
        }
    }
}

@Composable
internal fun DocxDocumentCanvas(
    docTitle: String,
    page: DocxPage,
    pageCount: Int,
    onNextPage: () -> Unit,
    onPrevPage: () -> Unit,
    onToggleBars: () -> Unit,
    onSelectText: ((String) -> Unit)? = null,
    isPlaying: Boolean = false,
    activeSentencePage: Int = 0,
    activeSentenceText: String = "",
    paperToneMode: PaperToneMode = PaperToneMode.ACTIVE_THEME,
    rotationDegrees: Int = 0,
    isLandscape: Boolean = false,
    modifier: Modifier = Modifier
) {
    val (cardBg, contentColor) = getCanvasColors(paperToneMode)

    Card(
        modifier = if (isLandscape) modifier.fillMaxSize() else modifier.fillMaxWidth(),
        shape = if (isLandscape) RectangleShape else RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBg
        ),
        border = if (isLandscape) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isLandscape) 0.dp else 6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = if (isLandscape) 14.dp else 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Page ${page.pageNumber} of $pageCount",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onPrevPage,
                        enabled = page.pageNumber > 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                            contentDescription = "Previous Page",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onNextPage,
                        enabled = page.pageNumber < pageCount,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = "Next Page",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Blocks (Scrollable with Selectable text & Speech highlighting)
            androidx.compose.foundation.text.selection.SelectionContainer(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = androidx.compose.runtime.key(docTitle,page.pageNumber) { rememberLazyListState() },
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(page.blocks, key = { index, _ -> "${page.pageNumber}:$index" }) { blockIdx, block ->
                        val isHighlighted = isPlaying &&
                            page.pageNumber == activeSentencePage &&
                            ActiveSentenceMatcher.matches(docxBlockPlainText(block), activeSentenceText)
                        when (block) {
                            is DocxBlock.Heading -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp)
                                        .clickable { onSelectText?.invoke(block.text) },
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(if (block.level == 1) 22.dp else 16.dp)
                                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                                    )
                                    DocxFormattedParagraph(DocxBlock.Paragraph(block.text, block.sourceFormat), contentColor,
                                        MaterialTheme.colorScheme.surface, Modifier.weight(1f),
                                        textStyle = (if (block.level == 1) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall)
                                            .copy(fontWeight = FontWeight.Bold))
                                }
                            }
                            is DocxBlock.Bullet -> {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectText?.invoke(block.text) },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f) else androidx.compose.ui.graphics.Color.Transparent
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = (block.level * 16).dp, top = 2.dp, bottom = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(
                                            text = if (block.level == 0) "•" else "◦",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        DocxFormattedParagraph(DocxBlock.Paragraph(block.text, block.sourceFormat?.copy(indentPoints = 0f)),
                                            contentColor, MaterialTheme.colorScheme.surface, Modifier.weight(1f))
                                    }
                                }
                            }
                            is DocxBlock.Paragraph -> {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectText?.invoke(block.text) },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f) else androidx.compose.ui.graphics.Color.Transparent
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isHighlighted) {
                                            Box(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .height(16.dp)
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            )
                                        }
                                        DocxFormattedParagraph(
                                            block = block,
                                            color = contentColor,
                                            background = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                            is DocxBlock.Table -> {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { onSelectText?.invoke(block.rows.joinToString("\n") { it.joinToString(" | ") }) },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.7f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                ) {
                                    Column(modifier = Modifier.horizontalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        block.rows.forEachIndexed { rowIndex, row ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                row.forEach { cell ->
                                                    Text(
                                                        text = cell,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = if (rowIndex == 0) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (rowIndex == 0) MaterialTheme.colorScheme.primary else contentColor,
                                                        modifier = Modifier.width(160.dp)
                                                    )
                                                }
                                            }
                                            if (rowIndex < block.rows.size - 1) {
                                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                            }
                                        }
                                    }
                                }
                            }
                            is DocxBlock.Image -> OriginalDocumentImage(block, blockIdx, block.description) { block.imageBytes }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CanvasControlButton(
    text: String,
    enabled: Boolean = true,
    prominent: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "canvasControlBounce"
    )
    if (prominent) {
        Button(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            shape = CircleShape,
            contentPadding = ButtonDefaults.ContentPadding,
            modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
        ) {
            Text(text)
        }
    } else {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
        ) {
            Text(text)
        }
    }
}

internal const val MIN_CANVAS_ZOOM = 0.75f
internal const val MAX_CANVAS_ZOOM = 5.0f
internal const val CANVAS_ZOOM_STEP = 0.25f

/**
 * Decides whether a rendered line in the original-document view is the one currently being
 * spoken. The two sides do not agree on granularity: the reader speaks sentences, while the
 * canvases render slide bullets, EPUB paragraphs, and DOCX blocks, so a line can hold several
 * sentences or a sentence can span several lines. Containment in either direction covers both,
 * and the length floor stops a short line like "Introduction" from matching every sentence that
 * happens to contain the word.
 */
internal object ActiveSentenceMatcher {
    /** A sentence short enough to be a stray fragment is not worth matching a whole line on. */
    private const val MIN_SENTENCE_LENGTH = 12

    /**
     * When the spoken sentence is the longer side, the rendered line is only a fragment of it,
     * and a one- or two-word fragment matches far too much: a "Introduction" heading would
     * light up for every sentence that happens to contain the word. Require the fragment to
     * carry real content instead.
     */
    private const val MIN_FRAGMENT_WORDS = 4

    fun normalize(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    fun matches(line: String, activeSentence: String): Boolean {
        if (line.isBlank() || activeSentence.isBlank()) return false
        val normalizedLine = normalize(line)
        val normalizedSentence = normalize(activeSentence)
        if (normalizedLine.isBlank() || normalizedSentence.isBlank()) return false
        if (normalizedLine == normalizedSentence) return true

        // The line holds several sentences and one of them is being spoken.
        if (normalizedSentence.length >= MIN_SENTENCE_LENGTH &&
            normalizedLine.contains(normalizedSentence)
        ) return true

        // The sentence wrapped across several rendered lines and this is one of them.
        if (normalizedSentence.contains(normalizedLine) &&
            normalizedLine.split(' ').count { it.isNotBlank() } >= MIN_FRAGMENT_WORDS
        ) return true

        return false
    }
}

/** Flattens a DOCX block to the plain text the extractor would have spoken for it. */
internal fun docxBlockPlainText(block: DocxBlock): String = when (block) {
    is DocxBlock.Heading -> block.text
    is DocxBlock.Paragraph -> block.text
    is DocxBlock.Bullet -> block.text
    is DocxBlock.Table -> block.rows.joinToString(" ") { row -> row.joinToString(" ") }
    is DocxBlock.Image -> ""
}

