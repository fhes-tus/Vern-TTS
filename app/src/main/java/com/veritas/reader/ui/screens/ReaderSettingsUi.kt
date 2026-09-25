package com.veritas.reader.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.*
import com.veritas.reader.R
import com.veritas.reader.ui.VeritasSwitch
import com.veritas.reader.ui.VeritasUiFont
import com.veritas.reader.ui.fontFamily

/**
 * Typeface picker. Every row is set in the face it offers — a font list rendered
 * in one typeface tells you nothing about the others.
 */
@Composable
fun VeritasFontPicker(
    selectedFontId: String,
    onUiFontChange: (String) -> Unit
) {
    val selected = VeritasUiFont.fromId(selectedFontId)
    Column(modifier = Modifier.fillMaxWidth()) {
        VeritasUiFont.entries.forEachIndexed { index, font ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                )
            }
            val isSelected = font == selected
            // fontFamily() is null for SYSTEM, and a null fontFamily on Text means
            // "inherit" — which made the System default row render in whichever face
            // was currently active instead of the platform one it actually offers.
            val family = font.fontFamily() ?: FontFamily.Default
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUiFontChange(font.id) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = font.label,
                        fontFamily = family,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        // A pangram-ish specimen: enough letterforms to tell the
                        // faces apart at a glance.
                        text = "The quick brown fox jumps over",
                        fontFamily = family,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = font.note,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                RadioButton(selected = isSelected, onClick = { onUiFontChange(font.id) })
            }
        }
    }
}

@Composable
fun VeritasThemePackPicker(
    selectedPackId: String,
    onThemePackChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        VeritasThemePackCatalog.packOptions.chunked(2).forEach { rowPacks ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowPacks.forEach { (packId, label) ->
                    val selected = VeritasThemePackCatalog.normalizePackId(selectedPackId) == packId
                    val modifier = Modifier.weight(1f)
                    if (selected) {
                        Button(
                            onClick = { onThemePackChange(packId) },
                            shape = VeritasPackStyle.chipShape(),
                            modifier = modifier
                        ) {
                            PackChoiceContent(label = label, packId = packId, selected = true)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onThemePackChange(packId) },
                            shape = VeritasPackStyle.chipShape(),
                            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme),
                            modifier = modifier
                        ) {
                            PackChoiceContent(label = label, packId = packId, selected = false)
                        }
                    }
                }
                if (rowPacks.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun PackPalettePreview(packId: String) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary
    
    val shape = when (VeritasThemePackCatalog.normalizePackId(packId)) {
        "material_you" -> RoundedCornerShape(6.dp)
        "liquid_glass" -> RoundedCornerShape(10.dp)
        "one_ui" -> RoundedCornerShape(3.dp)
        else -> RoundedCornerShape(1.dp)
    }
    
    val alpha = when (VeritasThemePackCatalog.normalizePackId(packId)) {
        "liquid_glass" -> 0.62f
        else -> 1.0f
    }
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(primary.copy(alpha = alpha), shape)
        )
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(secondary.copy(alpha = alpha), shape)
        )
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(tertiary.copy(alpha = alpha), shape)
        )
    }
}

@Composable
fun PackChoiceContent(label: String, packId: String, selected: Boolean) {
    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        PackPalettePreview(packId = packId)
    }
}

/**
 * Live theme preview: a miniature of the actual app UI rendered inside a nested
 * MaterialTheme using the SELECTED theme's real ColorScheme and the pack's shapes/
 * background brush — so it looks exactly like the app will, not an approximation.
 */
@Composable
fun ThemePreviewCard(themePackId: String, themeId: String, vibrantHero: Boolean = false) {
    val context = LocalContext.current
    val normalizedPack = VeritasThemePackCatalog.normalizePackId(themePackId)
    val scheme = veritasColorScheme(themeId, context)
    // Mirror the hero-card style toggle: vibrant = accent poster gradient with a
    // luminance-picked text colour; subtle = container tones.
    val heroGradient = if (vibrantHero) {
        val hsl = FloatArray(3)
        android.graphics.Color.colorToHSV(scheme.primary.toArgb(), hsl)
        Brush.linearGradient(
            listOf(
                Color(android.graphics.Color.HSVToColor(floatArrayOf(hsl[0], (hsl[1] * 0.7f).coerceIn(0f, 1f), (hsl[2] * 1.15f).coerceIn(0f, 1f)))),
                Color(android.graphics.Color.HSVToColor(floatArrayOf((hsl[0] + 15f) % 360f, hsl[1].coerceIn(0f, 1f), (hsl[2] * 0.85f).coerceIn(0f, 1f))))
            )
        )
    } else {
        Brush.linearGradient(listOf(scheme.primaryContainer, blendColors(scheme.primaryContainer, scheme.surface, 0.5f)))
    }
    val heroOnColor = if (vibrantHero) {
        if (scheme.primary.luminance() > 0.35f) Color(0xFF1A1A2E) else Color.White
    } else scheme.onPrimaryContainer
    val cardCorner = when (normalizedPack) {
        "material_you" -> 22.dp
        "liquid_glass" -> 26.dp
        "one_ui" -> 18.dp
        else -> 16.dp
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Live preview",
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium
        )
        MaterialTheme(colorScheme = scheme) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(226.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(VeritasPackStyle.backgroundBrush(scheme))
                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Top bar with app icon + wordmark + settings dot
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.veritas_reader_icon),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Vern", fontWeight = FontWeight.Black, color = scheme.onBackground, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Box(modifier = Modifier.size(22.dp).background(scheme.surfaceVariant, CircleShape))
                    }
                    // Hero card — reflects the vibrant/subtle toggle, like Home
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(78.dp)
                            .clip(RoundedCornerShape(cardCorner))
                            .background(heroGradient)
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                "Lorem ipsum dolor",
                                color = heroOnColor,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "sit amet consectetur",
                                color = heroOnColor.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Box(
                            modifier = Modifier.align(Alignment.BottomEnd).size(30.dp).background(if (vibrantHero) heroOnColor.copy(alpha = 0.25f) else scheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(modifier = Modifier.size(11.dp).background(if (vibrantHero) heroOnColor else scheme.onPrimary, CircleShape))
                        }
                    }
                    // Two content rows on real surface, with sample text
                    val sampleRows = listOf(
                        "Dolor sit amet" to "consectetur elit",
                        "Adipiscing tempor" to "incididunt labore"
                    )
                    sampleRows.forEach { (title, sub) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape((cardCorner.value - 4).coerceAtLeast(6f).dp))
                                .background(scheme.surface.copy(alpha = 0.85f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(22.dp).background(scheme.secondaryContainer, RoundedCornerShape(6.dp)))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(title, color = scheme.onSurface, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(sub, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    // Bottom nav — active tab in primary, others muted
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        repeat(3) { i ->
                            Box(modifier = Modifier.size(if (i == 0) 24.dp else 18.dp).background(if (i == 0) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape))
                        }
                    }
                }
            }
        }
        Text(
            "${VeritasThemePackCatalog.displayName(normalizedPack)} · ${VeritasThemeCatalog.displayName(themeId)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun VeritasThemePicker(
    selectedThemeId: String,
    onThemeChange: (String) -> Unit
) {
    val normalizedSelected = VeritasThemeCatalog.normalizeThemeId(selectedThemeId)

    val col1Themes = listOf(
        "system" to "System Default",
        "light" to "Light",
        "github_light" to "GitHub Light",
        "bw_gradient_light" to "B/W Gradient Light",
        "blue_high_contrast" to "Blue High Contrast",
        "one_dark_pro" to "One Dark Pro"
    )

    val col2Themes = listOf(
        "dark" to "Dark",
        "midnight_dark" to "Midnight Dark",
        "github_dark" to "GitHub Dark",
        "bw_gradient_dark" to "B/W Gradient Dark",
        "dracula" to "Dracula",
        "neon" to "Neon"
    )

    val rowCount = maxOf(col1Themes.size, col2Themes.size)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until rowCount) {
            val item1 = col1Themes.getOrNull(i)
            val item2 = col2Themes.getOrNull(i)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (item1 != null) {
                    val (id1, label1) = item1
                    val selected1 = normalizedSelected == id1
                    val colors1 = themePreviewColors(id1)
                    if (selected1) {
                        Button(
                            onClick = { onThemeChange(id1) },
                            shape = VeritasPackStyle.chipShape(),
                            modifier = Modifier.weight(1f)
                        ) {
                            ThemeChoiceContent(label = label1, previewColors = colors1, selected = true)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onThemeChange(id1) },
                            shape = VeritasPackStyle.chipShape(),
                            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme),
                            modifier = Modifier.weight(1f)
                        ) {
                            ThemeChoiceContent(label = label1, previewColors = colors1, selected = false)
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                if (item2 != null) {
                    val (id2, label2) = item2
                    val selected2 = normalizedSelected == id2
                    val colors2 = themePreviewColors(id2)
                    if (selected2) {
                        Button(
                            onClick = { onThemeChange(id2) },
                            shape = VeritasPackStyle.chipShape(),
                            modifier = Modifier.weight(1f)
                        ) {
                            ThemeChoiceContent(label = label2, previewColors = colors2, selected = true)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onThemeChange(id2) },
                            shape = VeritasPackStyle.chipShape(),
                            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme),
                            modifier = Modifier.weight(1f)
                        ) {
                            ThemeChoiceContent(label = label2, previewColors = colors2, selected = false)
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun ThemeChoiceContent(label: String, previewColors: List<Color>, selected: Boolean) {
    Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            previewColors.forEach { color ->
                Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
            }
        }
    }
}

@Composable
fun ReaderSettingsDialog(
    settings: ReaderSettings,
    onDismiss: () -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onSpacingChange: (Int) -> Unit,
    onThemeChange: (String) -> Unit,
    onThemePackChange: (String) -> Unit,
    onToggleVibrantHero: () -> Unit,
    onToggleAutoPlayQueue: () -> Unit,
    onUiFontChange: (String) -> Unit = {},
    onPaperToneModeChange: (PaperToneMode) -> Unit = {},
    onToggleAmoledMode: () -> Unit = {},
    currentPage: Int = 1,
    totalPages: Int = 1,
    onJumpToPage: ((Int) -> Unit)? = null
) {
    var themePacksExpanded by remember { mutableStateOf(false) }
    var colourThemesExpanded by remember { mutableStateOf(false) }
    var paperToneExpanded by remember { mutableStateOf(false) }
    var typefaceExpanded by remember { mutableStateOf(false) }

    FullScreenSettingsScaffold(title = "Display & theme", onBack = onDismiss) {
        // Theme Packs Accordion
        SettingsHubSectionTitle("Theme packs")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = VeritasPackStyle.cardShape(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { themePacksExpanded = !themePacksExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Theme pack: ${VeritasThemePackCatalog.displayName(settings.themePackId)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "UI styling, shapes, and surface finish",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { themePacksExpanded = !themePacksExpanded }) {
                        Icon(
                            if (themePacksExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                            contentDescription = if (themePacksExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (themePacksExpanded) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    VeritasThemePackPicker(
                        selectedPackId = settings.themePackId,
                        onThemePackChange = onThemePackChange
                    )
                }
            }
        }

        // Colour Themes Accordion
        SettingsHubSectionTitle("Colour themes")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = VeritasPackStyle.cardShape(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { colourThemesExpanded = !colourThemesExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Colour theme: ${VeritasThemeCatalog.displayName(settings.themeId)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Color palette and document canvas tone",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { colourThemesExpanded = !colourThemesExpanded }) {
                        Icon(
                            if (colourThemesExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                            contentDescription = if (colourThemesExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (colourThemesExpanded) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    VeritasThemePicker(
                        selectedThemeId = settings.themeId,
                        onThemeChange = onThemeChange
                    )
                }
            }
        }

        ThemePreviewCard(
            themePackId = settings.themePackId,
            themeId = settings.themeId,
            vibrantHero = settings.vibrantHero
        )

        // Document Paper Tone Accordion
        val currentTone = PaperToneMode.fromString(settings.paperToneMode)
        SettingsHubSectionTitle("Document paper tone")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = VeritasPackStyle.cardShape(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { paperToneExpanded = !paperToneExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            when (currentTone) {
                                PaperToneMode.ACTIVE_THEME -> "Paper tone: Default"
                                PaperToneMode.WARM_SEPIA -> "Paper tone: Sepia"
                                PaperToneMode.NATURAL_WHITE -> "Paper tone: Bone"
                                PaperToneMode.DARK -> "Paper tone: Dark slate"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Authentic paper color and typography for reading canvas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { paperToneExpanded = !paperToneExpanded }) {
                        Icon(
                            if (paperToneExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                            contentDescription = if (paperToneExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (paperToneExpanded) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PaperToneOptionRow(
                            label = "Default",
                            description = "Follows active theme background and contrast",
                            isSelected = currentTone == PaperToneMode.ACTIVE_THEME,
                            chipColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            textColor = MaterialTheme.colorScheme.onSurface,
                            onClick = { onPaperToneModeChange(PaperToneMode.ACTIVE_THEME) }
                        )
                        PaperToneOptionRow(
                            label = "Sepia",
                            description = "Authentic printed book paper (#FBF0D9) with deep ink",
                            isSelected = currentTone == PaperToneMode.WARM_SEPIA,
                            chipColor = Color(0xFFFBF0D9),
                            textColor = Color(0xFF3C2F2F),
                            onClick = { onPaperToneModeChange(PaperToneMode.WARM_SEPIA) }
                        )
                        PaperToneOptionRow(
                            label = "Bone",
                            description = "Clean white document page with crisp black ink",
                            isSelected = currentTone == PaperToneMode.NATURAL_WHITE,
                            chipColor = Color(0xFFFFFFFF),
                            textColor = Color(0xFF1C1B1F),
                            onClick = { onPaperToneModeChange(PaperToneMode.NATURAL_WHITE) }
                        )
                        PaperToneOptionRow(
                            label = "Dark slate",
                            description = "High-contrast dark paper (#141414) for low-light reading",
                            isSelected = currentTone == PaperToneMode.DARK,
                            chipColor = Color(0xFF141414),
                            textColor = Color(0xFFE8E8E8),
                            onClick = { onPaperToneModeChange(PaperToneMode.DARK) }
                        )
                    }
                }
            }
        }

        // Typeface Accordion
        SettingsHubSectionTitle("Typeface")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = VeritasPackStyle.cardShape(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { typefaceExpanded = !typefaceExpanded },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Selected Font: ${settings.uiFontId.replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Reader typography & font family",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { typefaceExpanded = !typefaceExpanded }) {
                        Icon(
                            if (typefaceExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                            contentDescription = if (typefaceExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (typefaceExpanded) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    VeritasFontPicker(
                        selectedFontId = settings.uiFontId,
                        onUiFontChange = onUiFontChange
                    )
                }
            }
        }

        // Text Sizing & Spacing Section
        SettingsHubSectionTitle("Text sizing & spacing")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = VeritasPackStyle.cardShape(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Text size",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${settings.fontSizeSp} sp",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                VeritasRoundSlider(
                    value = settings.fontSizeSp.toFloat(),
                    onValueChange = { onFontSizeChange(it.toInt().coerceIn(10, 28)) },
                    valueRange = 10f..28f,
                    steps = 17
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Line spacing",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${settings.sectionSpacingDp} dp",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                VeritasRoundSlider(
                    value = settings.sectionSpacingDp.toFloat(),
                    onValueChange = { onSpacingChange(it.toInt().coerceIn(6, 24)) },
                    valueRange = 6f..24f,
                    steps = 17
                )
            }
        }

        // Jump to Page Card
        if (totalPages > 1 && onJumpToPage != null) {
            SettingsHubSectionTitle("Jump to page")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = VeritasPackStyle.cardShape(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
            ) {
                var inputPageText by remember(currentPage) { mutableStateOf(currentPage.toString()) }
                var inputError by remember { mutableStateOf(false) }

                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Page number",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Page $currentPage of $totalPages",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputPageText,
                            onValueChange = { newText ->
                                val digitsOnly = newText.filter { it.isDigit() }
                                inputPageText = digitsOnly
                                inputError = digitsOnly.toIntOrNull()?.let { it < 1 || it > totalPages } ?: false
                            },
                            singleLine = true,
                            isError = inputError,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            placeholder = { Text("1 - $totalPages") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Button(
                            onClick = {
                                val target = inputPageText.toIntOrNull()
                                if (target != null && target in 1..totalPages) {
                                    onJumpToPage(target)
                                    onDismiss()
                                } else {
                                    inputError = true
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            enabled = inputPageText.toIntOrNull() != null && !inputError
                        ) {
                            Text("Go")
                        }
                    }
                }
            }
        }

        // Preferences Section
        SettingsHubSectionTitle("Display preferences")
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = VeritasPackStyle.cardShape(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Vibrant hero card", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Bold accent gradient on the home card", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    VeritasSwitch(checked = settings.vibrantHero, onCheckedChange = { onToggleVibrantHero() })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-play queue", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Automatically play next queued book", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    VeritasSwitch(checked = settings.autoPlayQueue, onCheckedChange = { onToggleAutoPlayQueue() })
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AMOLED Mode", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text("Pure black background for dark themes and battery saving", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    VeritasSwitch(checked = settings.amoledMode, onCheckedChange = { onToggleAmoledMode() })
                }
            }
        }
    }
}

@Composable
private fun PaperToneOptionRow(
    label: String,
    description: String,
    isSelected: Boolean,
    chipColor: Color,
    textColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(chipColor)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("Aa", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(selected = isSelected, onClick = onClick)
    }
}
