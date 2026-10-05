package com.veritas.reader.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.veritas.reader.PaperToneMode
import com.veritas.reader.R
import com.veritas.reader.ReaderSettings
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.LocalVeritasPackId
import com.veritas.reader.VeritasThemeCatalog
import com.veritas.reader.VeritasThemePackCatalog
import com.veritas.reader.blendColors
import com.veritas.reader.themePreviewColors
import com.veritas.reader.ui.VeritasSwitch
import com.veritas.reader.ui.VeritasUiFont
import com.veritas.reader.ui.fontFamily
import com.veritas.reader.veritasColorScheme

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
    val normalized = VeritasThemePackCatalog.normalizePackId(packId)
    val context = LocalContext.current
    val resolved = VeritasThemeCatalog.resolveConcreteThemeId(com.veritas.reader.VeritasThemeState.themeId, isSystemInDarkTheme())
    val packed = com.veritas.reader.veritasPackColorScheme(veritasColorScheme(resolved, context), normalized, resolved)
    val scheme = if (VeritasThemeCatalog.isDark(resolved) && (com.veritas.reader.VeritasThemeState.amoledMode || resolved == "amoled")) com.veritas.reader.veritasAmoledColorScheme(packed) else packed
    androidx.compose.runtime.CompositionLocalProvider(LocalVeritasPackId provides normalized) {
        MaterialTheme(colorScheme = scheme, shapes = com.veritas.reader.veritasPackShapes(normalized),
            typography = com.veritas.reader.veritasPackTypography(com.veritas.reader.ui.veritasTypography(com.veritas.reader.ui.VeritasUiFont.fromId(com.veritas.reader.VeritasThemeState.uiFontId)), normalized)) {
            val shape = VeritasPackStyle.compactShape()
            Row(Modifier.fillMaxWidth().height(34.dp).clip(shape)
                .background(VeritasPackStyle.panelColor(scheme))
                .border(VeritasPackStyle.cardBorder(scheme), shape).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Aa", color = scheme.onSurface, style = MaterialTheme.typography.labelLarge)
                Box(Modifier.weight(1f).height(2.dp).background(scheme.onSurfaceVariant.copy(alpha = .45f), VeritasPackStyle.chipShape()))
                Box(Modifier.width(38.dp).height(16.dp).background(scheme.primary, VeritasPackStyle.chipShape()))
            }
        }
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
    val resolvedTheme = VeritasThemeCatalog.resolveConcreteThemeId(themeId, isSystemInDarkTheme())
    val packed = com.veritas.reader.veritasPackColorScheme(veritasColorScheme(resolvedTheme, context), normalizedPack, resolvedTheme)
    val scheme = if (VeritasThemeCatalog.isDark(resolvedTheme) && (com.veritas.reader.VeritasThemeState.amoledMode || resolvedTheme == "amoled")) com.veritas.reader.veritasAmoledColorScheme(packed) else packed
    // Mirror the hero-card style toggle: vibrant = accent poster gradient with a
    // luminance-picked text colour; subtle = container tones.
    val heroGradient: Brush
    val heroOnColor: Color
    if (vibrantHero) {
        val hsl = FloatArray(3)
        android.graphics.Color.colorToHSV(scheme.primary.toArgb(), hsl)
        val color1 = Color(android.graphics.Color.HSVToColor(floatArrayOf(hsl[0], (hsl[1] * 0.7f).coerceIn(0f, 1f), (hsl[2] * 1.15f).coerceIn(0f, 1f))))
        val color2 = Color(android.graphics.Color.HSVToColor(floatArrayOf((hsl[0] + 15f) % 360f, hsl[1].coerceIn(0f, 1f), (hsl[2] * 0.85f).coerceIn(0f, 1f))))
        heroGradient = Brush.linearGradient(listOf(color1, color2))
        val avgLuminance = (color1.luminance() + color2.luminance()) / 2f
        heroOnColor = if (avgLuminance > 0.40f) Color(0xFF0F172A) else Color.White
    } else {
        heroGradient = Brush.linearGradient(listOf(scheme.primaryContainer, blendColors(scheme.primaryContainer, scheme.surface, 0.5f)))
        heroOnColor = scheme.onPrimaryContainer
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "Live preview",
            fontWeight = MaterialTheme.typography.titleLarge.fontWeight,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium
        )
        androidx.compose.runtime.CompositionLocalProvider(LocalVeritasPackId provides normalizedPack) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = com.veritas.reader.veritasPackShapes(normalizedPack),
            typography = com.veritas.reader.veritasPackTypography(com.veritas.reader.ui.veritasTypography(com.veritas.reader.ui.VeritasUiFont.fromId(com.veritas.reader.VeritasThemeState.uiFontId)), normalizedPack)
        ) {
            val previewCardShape = VeritasPackStyle.cardShape()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(386.dp)
                    .clip(VeritasPackStyle.cardShape())
                    .background(VeritasPackStyle.backgroundBrush(scheme))
                    .border(VeritasPackStyle.cardBorder(scheme), VeritasPackStyle.cardShape())
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
                        Text("Vern", fontWeight = MaterialTheme.typography.titleLarge.fontWeight, color = scheme.onBackground, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Box(modifier = Modifier.size(22.dp).background(scheme.surfaceVariant, CircleShape))
                    }
                    // Hero card — reflects the vibrant/subtle toggle, like Home
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(78.dp)
                            .clip(previewCardShape)
                            .background(heroGradient)
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                "Lorem ipsum dolor",
                                color = heroOnColor,
                                style = MaterialTheme.typography.labelLarge,
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
                                .clip(VeritasPackStyle.compactShape())
                                .background(VeritasPackStyle.panelColor(scheme))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(22.dp).background(scheme.secondaryContainer, RoundedCornerShape(6.dp)))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(title, color = scheme.onSurface, style = MaterialTheme.typography.labelMedium, fontWeight = MaterialTheme.typography.labelMedium.fontWeight, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(sub, color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth().height(48.dp)
                        .background(VeritasPackStyle.playerSurfaceColor(scheme), previewCardShape)
                        .border(VeritasPackStyle.cardBorder(scheme), previewCardShape)
                        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, null, tint = scheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Continue reading", style = MaterialTheme.typography.labelLarge, color = scheme.onSurface)
                    }
                    // Bottom nav — active tab in primary, others muted
                    Row(
                        modifier = Modifier.fillMaxWidth().height(VeritasPackStyle.navigationHeight(normalizedPack, true).coerceAtMost(40.dp))
                            .clip(RoundedCornerShape(VeritasPackStyle.navigationCornerRadius()))
                            .background(VeritasPackStyle.navigationBrush(scheme))
                            .border(1.dp, VeritasPackStyle.navigationBorderBrush(scheme), RoundedCornerShape(VeritasPackStyle.navigationCornerRadius()))
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(Icons.Default.Home, Icons.Default.Book, Icons.Default.Layers, Icons.Default.EditNote).forEachIndexed { index, icon ->
                            Box(Modifier.size(30.dp).background(if (index == 0) scheme.primaryContainer else Color.Transparent, VeritasPackStyle.chipShape()), contentAlignment = Alignment.Center) {
                                Icon(icon, null, tint = if (index == 0) scheme.onPrimaryContainer else scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
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
    val currentFamily = VeritasThemeCatalog.familyForThemeId(selectedThemeId)
    val currentMode = VeritasThemeCatalog.modeForThemeId(selectedThemeId)
    val isSystemDark = isSystemInDarkTheme()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── 1. Appearance Mode (System Default / Light / Dark) ──
        Text(
            text = "Appearance Mode",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val modes = listOf(
                Triple("system", "System", Icons.Filled.BrightnessAuto),
                Triple("light", "Light", Icons.Filled.LightMode),
                Triple("dark", "Dark", Icons.Filled.DarkMode)
            )
            modes.forEach { (modeId, label, icon) ->
                val isSelected = currentMode == modeId && currentFamily != "neon"
                val isNeonActive = currentFamily == "neon"

                if (isSelected) {
                    Button(
                        onClick = {
                            val newId = VeritasThemeCatalog.resolveThemeIdForStorage(currentFamily, modeId)
                            onThemeChange(newId)
                        },
                        shape = VeritasPackStyle.chipShape(),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            val targetFamily = if (isNeonActive) "sage" else currentFamily
                            val newId = VeritasThemeCatalog.resolveThemeIdForStorage(targetFamily, modeId)
                            onThemeChange(newId)
                        },
                        shape = VeritasPackStyle.chipShape(),
                        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // ── 2. Palette Families (5 Sets) ──
        Text(
            text = "Theme Palette Sets",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val coreFamilies = VeritasThemeCatalog.families
        val pairs = coreFamilies.chunked(2)
        pairs.forEach { rowFamilies ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowFamilies.forEach { family ->
                    val isFamilySelected = currentFamily == family.id
                    val previewThemeId = when (currentMode) {
                        "light" -> family.lightThemeId
                        "dark" -> family.darkThemeId
                        else -> if (isSystemDark) family.darkThemeId else family.lightThemeId
                    }
                    val colors = themePreviewColors(previewThemeId)
                    val cardModifier = Modifier.weight(1f)

                    if (isFamilySelected) {
                        Button(
                            onClick = {
                                val newId = VeritasThemeCatalog.resolveThemeIdForStorage(family.id, currentMode)
                                onThemeChange(newId)
                            },
                            shape = VeritasPackStyle.chipShape(),
                            modifier = cardModifier
                        ) {
                            ThemeChoiceContent(
                                label = if (family.isAccent) "Neon" else family.displayName,
                                previewColors = colors,
                                selected = true
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                val newId = VeritasThemeCatalog.resolveThemeIdForStorage(family.id, currentMode)
                                onThemeChange(newId)
                            },
                            shape = VeritasPackStyle.chipShape(),
                            border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme),
                            modifier = cardModifier
                        ) {
                            ThemeChoiceContent(
                                label = if (family.isAccent) "Neon" else family.displayName,
                                previewColors = colors,
                                selected = false
                            )
                        }
                    }
                }
                if (rowFamilies.size == 1) {
                    Spacer(Modifier.weight(1f))
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
    onToggleGlassFloatingControls: () -> Unit = {},
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
        Card(Modifier.fillMaxWidth(), shape = VeritasPackStyle.cardShape()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Glass in other theme packs", style = MaterialTheme.typography.titleMedium)
                    Text("Blurred, glossy navigation, player and floating Add, Note and Import controls. Liquid glass includes this look.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (settings.reduceTransparency) Text("Reduce transparency currently keeps controls opaque.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                VeritasSwitch(checked = settings.glassFloatingControls, onCheckedChange = { onToggleGlassFloatingControls() })
            }
        }
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
