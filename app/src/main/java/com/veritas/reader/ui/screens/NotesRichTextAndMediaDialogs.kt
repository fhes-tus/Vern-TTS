package com.veritas.reader.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import java.io.File

@Composable
internal fun NoteImageViewerDialog(
    path: String,
    onShare: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val bitmap = remember(path) {
        loadNoteBitmap(context, path)
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Full view image",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.8f, 5f)
                                if (scale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                )
            } else {
                Text(
                    "Cannot load image",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Top control bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }

                IconButton(
                    onClick = { onShare(path) },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.White)
                }
            }
        }
    }
}

@Composable
internal fun NoteVideoViewerDialog(
    path: String,
    onOpenExternal: (String) -> Unit,
    onShare: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        val uri = Uri.fromFile(File(path))
                        setVideoURI(uri)
                        val controller = android.widget.MediaController(ctx)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setOnPreparedListener { mp ->
                            mp.isLooping = false
                            start()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Top control bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = { onOpenExternal(path) },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = "Open in player", tint = Color.White)
                    }
                    IconButton(
                        onClick = { onShare(path) },
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .size(40.dp)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
internal fun FormatToolbarButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = description, tint = tint.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
    }
}

/**
 * Renders inline markdown-style markers as real formatting while HIDING the marker
 * characters themselves, so the editor shows styled text instead of raw `**markup**`.
 */
@Composable
internal fun NoteTextBlockItem(
    block: NoteBlock.Text,
    onValueChange: (TextFieldValue) -> Unit,
    onFocus: () -> Unit,
    onBackspaceAtStart: () -> Boolean,
    visualTransformation: VisualTransformation,
    onCardColor: Color,
    isOnlyBlock: Boolean,
    modifier: Modifier = Modifier
) {
    var textValue by remember(block) { mutableStateOf(block.value) }

    LaunchedEffect(block.value.text, block.value.selection) {
        if (textValue.text != block.value.text || textValue.selection != block.value.selection) {
            textValue = block.value
        }
    }

    TextField(
        value = textValue,
        onValueChange = { raw ->
            val processed = VeritasNoteEditing.continueListOnNewline(textValue, raw)
            textValue = processed
            onValueChange(processed)
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = onCardColor),
        placeholder = {
            if (isOnlyBlock && textValue.text.isEmpty()) {
                Text(
                    "Note",
                    style = MaterialTheme.typography.bodyLarge,
                    color = onCardColor.copy(alpha = 0.4f)
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (isOnlyBlock) 200.dp else 24.dp)
            .onFocusChanged { fState ->
                if (fState.isFocused) {
                    onFocus()
                }
            }
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.key == Key.Backspace && keyEvent.type == KeyEventType.KeyDown) {
                    val isAtStart = textValue.selection.start == 0 && textValue.selection.end == 0
                    if (isAtStart) {
                        return@onPreviewKeyEvent onBackspaceAtStart()
                    }
                }
                false
            },
        visualTransformation = visualTransformation,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = onCardColor
        )
    )
}

/**
 * Applies visual styling for inline Markdown markers and headings.
 * Maintains an exact bidirectional offset mapping so the caret and selection stay correct.
 */
class RichTextVisualTransformation(private val baseColor: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return RichTextFormatter.transform(text.text, baseColor)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RichTextVisualTransformation) return false
        return baseColor == other.baseColor
    }

    override fun hashCode(): Int {
        return baseColor.hashCode()
    }
}

object RichTextFormatter {
    private val HEADING_REGEX = Regex("^(#{1,3}) ")
    private val inlineMarkers: List<Pair<Regex, (String) -> SpanStyle>> = listOf(
        Regex("\\*\\*(.*?)\\*\\*") to { _: String -> SpanStyle(fontWeight = FontWeight.Bold) },
        Regex("__(.*?)__") to { _: String -> SpanStyle(textDecoration = TextDecoration.Underline) },
        Regex("~~(.*?)~~") to { _: String -> SpanStyle(textDecoration = TextDecoration.LineThrough) },
        Regex("`(.*?)`") to { _: String -> SpanStyle(fontFamily = FontFamily.Monospace) },
        Regex("\\*(.*?)\\*") to { _: String -> SpanStyle(fontStyle = FontStyle.Italic) }
    )

    private val markerLengths = listOf(2, 2, 2, 1, 1)

    fun transform(raw: String, baseColor: Color = Color.Unspecified): TransformedText {
        val n = raw.length
        if (n == 0) {
            return TransformedText(AnnotatedString(""), OffsetMapping.Identity)
        }

        // Fast-path: if text contains no markdown indicator characters, skip all regex parsing entirely!
        val hasMarkdown = raw.any { it == '*' || it == '_' || it == '~' || it == '`' || it == '#' || it == '>' }
        if (!hasMarkdown) {
            return TransformedText(AnnotatedString(raw), OffsetMapping.Identity)
        }

        val markerStyle = if (baseColor != Color.Unspecified) {
            SpanStyle(color = baseColor.copy(alpha = 0.35f))
        } else {
            SpanStyle(color = Color.Gray.copy(alpha = 0.5f))
        }

        val annotated = buildAnnotatedString {
            append(raw)

            // Line-level headings and quotes
            var lineStart = 0
            while (lineStart < n) {
                val nl = raw.indexOf('\n', lineStart).let { if (it < 0) n else it }
                val line = raw.substring(lineStart, nl)
                val m = HEADING_REGEX.find(line)
                if (m != null) {
                    val prefixLen = m.value.length
                    addStyle(markerStyle, lineStart, lineStart + prefixLen)
                    val level = m.groupValues[1].length
                    val size = when (level) {
                        1 -> 24f
                        2 -> 20f
                        else -> 17f
                    }
                    if (nl > lineStart + prefixLen) {
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = size.sp), lineStart + prefixLen, nl)
                    }
                } else if (line.startsWith("> ")) {
                    addStyle(markerStyle, lineStart, lineStart + 2)
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), lineStart + 2, nl)
                }
                if (nl >= n) break
                lineStart = nl + 1
            }

            // Inline markers
            val consumed = BooleanArray(n)
            inlineMarkers.forEachIndexed { index, (regex, styleFor) ->
                val mlen = markerLengths[index]
                regex.findAll(raw).forEach { match ->
                    val s = match.range.first
                    val e = match.range.last // inclusive
                    if (s < 0 || e >= n) return@forEach
                    var overlaps = false
                    for (i in s..e) if (consumed[i]) { overlaps = true; break }
                    if (overlaps) return@forEach
                    val innerStart = s + mlen
                    val innerEnd = e + 1 - mlen
                    if (innerEnd <= innerStart) return@forEach
                    for (i in s..e) consumed[i] = true

                    addStyle(markerStyle, s, innerStart)
                    addStyle(styleFor(match.groupValues.getOrElse(1) { "" }), innerStart, innerEnd)
                    addStyle(markerStyle, innerEnd, e + 1)
                }
            }
        }

        return TransformedText(annotated, OffsetMapping.Identity)
    }

    /** Removes inline markup and heading prefixes for plain-text sharing. */
    fun stripMarkup(raw: String): String {
        var out = raw
        out = out.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        out = out.replace(Regex("__(.+?)__"), "$1")
        out = out.replace(Regex("~~(.+?)~~"), "$1")
        out = out.replace(Regex("`(.+?)`"), "$1")
        out = out.replace(Regex("\\*(.+?)\\*"), "$1")
        out = out.lineSequence().joinToString("\n") {
            it.replaceFirst(Regex("^#{1,3} "), "").replaceFirst(Regex("^> "), "")
        }
        return out
    }
}

fun loadNoteBitmap(context: Context, pathOrUri: String): Bitmap? {
    if (pathOrUri.isBlank()) return null
    return try {
        if (pathOrUri.startsWith("content://") || pathOrUri.startsWith("file://")) {
            val uri = Uri.parse(pathOrUri)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } else {
            val file = File(pathOrUri)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else {
                val uri = Uri.parse(pathOrUri)
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
internal fun NoteAttachmentMenu(
    onCopy: () -> Unit,
    canMoveUp: Boolean,
    onMoveUp: () -> Unit,
    canMoveDown: Boolean,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                .size(32.dp)
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = "Attachment options",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Copy Path") },
                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                onClick = {
                    expanded = false
                    onCopy()
                }
            )
            if (canMoveUp) {
                DropdownMenuItem(
                    text = { Text("Move Up") },
                    leadingIcon = { Icon(Icons.Outlined.ArrowUpward, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onMoveUp()
                    }
                )
            }
            if (canMoveDown) {
                DropdownMenuItem(
                    text = { Text("Move Down") },
                    leadingIcon = { Icon(Icons.Outlined.ArrowDownward, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onMoveDown()
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    expanded = false
                    onDelete()
                }
            )
        }
    }
}

@Composable
internal fun ExactAlarmPermissionDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.reminder_exact_title)) },
        text = { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.reminder_exact_message)) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    runCatching {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    }
                }
            }) { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.reminder_exact_allow)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.reminder_exact_later))
            }
        }
    )
}

@Composable
internal fun DeleteNoteConfirmDialog(
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.delete_note_title)) },
        text = { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.delete_note_message)) },
        confirmButton = {
            TextButton(onClick = onConfirmDelete) {
                Text(
                    androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.action_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.action_cancel))
            }
        }
    )
}

internal fun openVideoFile(context: Context, path: String) {
    try {
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(context, "Video file not found", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Play video"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Cannot open video: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

internal fun shareMediaFile(context: Context, path: String, mimeType: String) {
    try {
        val file = File(path)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Failed to share: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
