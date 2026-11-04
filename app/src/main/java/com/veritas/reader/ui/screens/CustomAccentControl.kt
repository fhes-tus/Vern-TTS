package com.veritas.reader.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.veritas.reader.AccentHsv
import com.veritas.reader.normalizeCustomAccent
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustomAccentControl(value: String, themeId: String, onSave: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    var hue by rememberSaveable { mutableFloatStateOf(210f) }
    var saturation by rememberSaveable { mutableFloatStateOf(1f) }
    var brightness by rememberSaveable { mutableFloatStateOf(1f) }
    val hsv = AccentHsv(hue, saturation, brightness).bounded()
    val chosenColour = hsv.toHex()
    fun select(next: AccentHsv) {
        val bounded = next.bounded()
        hue = bounded.hue
        saturation = bounded.saturation
        brightness = bounded.value
    }
    val fallback = "#%06X".format(MaterialTheme.colorScheme.primary.toArgb() and 0xFFFFFF)
    val selected = normalizeCustomAccent(value)
    val swatch = if (selected.isEmpty()) MaterialTheme.colorScheme.primary else Color(0xFF000000L or selected.drop(1).toLong(16))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_custom_accent), style = MaterialTheme.typography.titleMedium)
        Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_choose_any_colour_for_buttons_highlights_and_matching_tints_it_fo),
            style = MaterialTheme.typography.bodyMedium)
        if (themeId.contains("high_contrast")) Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_high_contrast_keeps_its_accessibility_colours_your_custom_accent),
            style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(32.dp).background(swatch, CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            OutlinedButton(onClick = {
                select(AccentHsv.fromHex(selected.ifEmpty { fallback }, hue) ?: AccentHsv(210f, 1f, 1f))
                open = true
            }, modifier = Modifier.testTag("choose-custom-accent")) { Text(if (selected.isEmpty()) androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_choose_colour) else androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_change_colour)) }
        }
        if (selected.isNotEmpty()) TextButton({ onSave("") }, Modifier.testTag("reset-custom-accent"),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_use_theme_colours)) }
    }
    if (open) {
        val selectedColourLabel = androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_selected_colour)
        val selectedColourName = localizedSettingsOption(hsv.colourName())
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_choose_accent_colour)) },
            text = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp)
                            .background(Color(hsv.toArgb()), CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .testTag("custom-accent-preview")
                            .semantics {
                                contentDescription = selectedColourLabel
                                stateDescription = selectedColourName
                            })
                        Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_selected_colour), style = MaterialTheme.typography.labelLarge)
                    }
                    Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_text_adjusts_to_contrast_with_your_colour), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SaturationValueField(hsv, { x, y -> select(AccentHsv(hue, saturation, brightness).atFieldPosition(x, y)) })
                        HueStrip(hsv.hue, { select(AccentHsv(it, saturation, brightness)) })
                        Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_quick_colours), style = MaterialTheme.typography.labelLarge)
                        FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 5,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            accentSwatches.forEach { (label, hex) ->
                                val isSelected = hex == chosenColour
                                val colourLabel = localizedSettingsOption(label)
                                val swatchDescription = androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_swatch_colour, colourLabel)
                                val chooseDescription = androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_choose_swatch, colourLabel)
                                Box(Modifier.size(48.dp)
                                    .testTag("custom-accent-preset-${label.lowercase()}")
                                    .semantics { contentDescription = swatchDescription; this.selected = isSelected }
                                    .clickable(role = Role.Button, onClickLabel = chooseDescription) {
                                        AccentHsv.fromHex(hex, hue)?.let(::select)
                                    }, contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(38.dp)
                                        .border(if (isSelected) 2.dp else 0.dp,
                                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                        .padding(4.dp)) {
                                        Box(Modifier.fillMaxSize().background(Color(0xFF000000L or hex.drop(1).toLong(16)), CircleShape)
                                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ onSave(AccentHsv(hue, saturation, brightness).toHex()); open = false },
                modifier = Modifier.testTag("apply-custom-accent"),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_apply)) } },
            dismissButton = { TextButton({ open = false },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_cancel)) } }
        )
    }
}

private val accentSwatches = listOf(
    "Black" to "#000000", "Grey" to "#808080", "White" to "#FFFFFF", "Red" to "#E53935", "Orange" to "#FB8C00",
    "Yellow" to "#FDD835", "Green" to "#43A047", "Cyan" to "#00ACC1", "Blue" to "#1E88E5", "Purple" to "#8E24AA"
)

@Composable
private fun SaturationValueField(hsv: AccentHsv, onChange: (Float, Float) -> Unit) {
    val shadeLabel = localizedSettingsOption(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_option_colour_shade))
    val colourState = androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_colour_state, (hsv.saturation * 100).roundToInt(), (hsv.value * 100).roundToInt())
    val moreSaturated = localizedSettingsOption(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_option_more_saturated))
    val lessSaturated = localizedSettingsOption(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_option_less_saturated))
    val brighter = localizedSettingsOption(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_option_brighter))
    val darker = localizedSettingsOption(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_option_darker))
    var focused by remember { mutableStateOf(false) }
    val latestChange by rememberUpdatedState(onChange)
    val hueColour = Color(AccentHsv(hsv.hue, 1f, 1f).toArgb())
    val outline = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val shape = RoundedCornerShape(8.dp)
    Canvas(Modifier.fillMaxWidth().aspectRatio(1.25f)
        .testTag("custom-accent-field")
        .clip(shape)
        .border(if (focused) 2.dp else 1.dp, outline, shape)
        .onFocusChanged { focused = it.isFocused }
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) false else {
                when (event.key) {
                    Key.DirectionLeft -> latestChange(hsv.saturation - .01f, 1f - hsv.value)
                    Key.DirectionRight -> latestChange(hsv.saturation + .01f, 1f - hsv.value)
                    Key.DirectionUp -> latestChange(hsv.saturation, 1f - hsv.value - .01f)
                    Key.DirectionDown -> latestChange(hsv.saturation, 1f - hsv.value + .01f)
                    else -> return@onKeyEvent false
                }
                true
            }
        }
        .focusable()
        .semantics {
            contentDescription = shadeLabel
            stateDescription = colourState
            customActions = listOf(
                CustomAccessibilityAction(moreSaturated) { latestChange(hsv.saturation + .05f, 1f - hsv.value); true },
                CustomAccessibilityAction(lessSaturated) { latestChange(hsv.saturation - .05f, 1f - hsv.value); true },
                CustomAccessibilityAction(brighter) { latestChange(hsv.saturation, 1f - hsv.value - .05f); true },
                CustomAccessibilityAction(darker) { latestChange(hsv.saturation, 1f - hsv.value + .05f); true }
            )
        }
        .colourDrag { point, size ->
            if (size.width > 0 && size.height > 0) latestChange(point.x / size.width, point.y / size.height)
        }) {
        drawRect(hueColour)
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.White.copy(alpha = 0f))))
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0f), Color.Black)))
        val radius = 9.dp.toPx().coerceAtMost(minOf(size.width, size.height) / 2f)
        val centre = Offset((hsv.saturation * size.width).coerceIn(radius, size.width - radius),
            ((1f - hsv.value) * size.height).coerceIn(radius, size.height - radius))
        drawCircle(Color.Black, radius, centre, style = Stroke(3.dp.toPx()))
        drawCircle(Color.White, radius, centre, style = Stroke(2.dp.toPx()))
    }
}

@Composable
private fun HueStrip(hue: Float, onChange: (Float) -> Unit) {
    val hueLabel = localizedSettingsOption(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_option_hue))
    val hueState = androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_hue_degrees, hue.roundToInt())
    var focused by remember { mutableStateOf(false) }
    val latestChange by rememberUpdatedState(onChange)
    val rainbow = remember { Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)) }
    val outline = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.fillMaxWidth().height(48.dp).testTag("custom-accent-hue")
        .border(if (focused) 2.dp else 0.dp, if (focused) outline else Color.Transparent, RoundedCornerShape(8.dp))
        .onFocusChanged { focused = it.isFocused }
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) false else when (event.key) {
                Key.DirectionLeft, Key.DirectionDown -> { latestChange((hue - 5f).coerceIn(0f, 360f)); true }
                Key.DirectionRight, Key.DirectionUp -> { latestChange((hue + 5f).coerceIn(0f, 360f)); true }
                else -> false
            }
        }
        .focusable()
        .semantics {
            contentDescription = hueLabel
            stateDescription = hueState
            progressBarRangeInfo = ProgressBarRangeInfo(hue, 0f..360f)
            setProgress { latestChange(it.coerceIn(0f, 360f)); true }
        }
        .colourDrag { point, size -> if (size.width > 0) latestChange((point.x / size.width * 360f).coerceIn(0f, 360f)) }) {
        val trackHeight = 20.dp.toPx()
        drawRoundRect(rainbow, topLeft = Offset(0f, (size.height - trackHeight) / 2f),
            size = Size(size.width, trackHeight), cornerRadius = CornerRadius(trackHeight / 2f))
        val radius = 9.dp.toPx().coerceAtMost(size.width / 2f)
        val centre = Offset((hue / 360f * size.width).coerceIn(radius, size.width - radius), size.height / 2f)
        drawCircle(Color(AccentHsv(hue, 1f, 1f).toArgb()), radius, centre)
        drawCircle(Color.Black, radius, centre, style = Stroke(3.dp.toPx()))
        drawCircle(Color.White, radius, centre, style = Stroke(2.dp.toPx()))
    }
}

/** Consume field drags so the surrounding dialog only scrolls outside the picker. */
@Composable
private fun Modifier.colourDrag(onPoint: (Offset, IntSize) -> Unit): Modifier {
    val latestPoint by rememberUpdatedState(onPoint)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            latestPoint(down.position, size)
            down.consume()
            do {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                latestPoint(change.position, size)
                change.consume()
            } while (change.pressed)
        }
    }
}
