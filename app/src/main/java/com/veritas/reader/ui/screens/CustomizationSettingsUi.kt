package com.veritas.reader.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.veritas.reader.*
import com.veritas.reader.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CustomizationSettingsDialog(settings: ReaderSettings, onSave: (ReaderSettings) -> Unit, onDismiss: () -> Unit) {
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val context = androidx.compose.ui.platform.LocalContext.current
    val deviceAnimationsOff = remember(context) {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val motionOff = settings.reduceMotion || deviceAnimationsOff
    val palette = VernCustomization.palette(settings.launcherPalette, settings.themeId, systemDark)
    var previewRun by remember { mutableIntStateOf(0) }
    FullScreenSettingsScaffold(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_customization), onDismiss) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(Modifier.size(76.dp).background(Color(palette.background), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.vern_v_cream), null, Modifier.size(50.dp), colorFilter = ColorFilter.tint(Color(palette.foreground)))
                }
                Column(Modifier.weight(1f)) {
                    Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_make_vern_yours), style = MaterialTheme.typography.titleLarge)
                    Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_colours_card_character_and_page_motion), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_app_icon_colours), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
        Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_choose_a_palette_for_your_launcher_icon_the_launcher_updates_when), style = MaterialTheme.typography.bodyMedium)
        FilterChip(settings.launcherPalette == "theme", { onSave(settings.copy(launcherPalette = "theme")) },
            modifier = Modifier.testTag("icon-follow-theme"), label = { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_follow_my_colour_theme)) })
        if (settings.launcherPalette == "theme") Text(
            "${VeritasThemeCatalog.displayName(VeritasThemeCatalog.resolveConcreteThemeId(settings.themeId, systemDark))} · ${palette.label}",
            Modifier.testTag("resolved-icon-theme"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VernCustomization.palettes.forEach { choice ->
                FilterChip(settings.launcherPalette == choice.id, { onSave(settings.copy(launcherPalette = choice.id)) },
                    modifier = Modifier.testTag("icon-palette-${choice.id}"), label = { Text(choice.label) },
                    leadingIcon = {
                        Box(Modifier.size(26.dp).background(Color(choice.background), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                            Image(painterResource(R.drawable.vern_v_cream), null, Modifier.size(18.dp), colorFilter = ColorFilter.tint(Color(choice.foreground)))
                        }
                    })
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_startup_animation), style = MaterialTheme.typography.titleMedium)
        CustomizationChoices(settings.splashAnimation, listOf("bloom" to androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_bloom), "fade" to androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_fade), "rise" to androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_rise), "none" to androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_none))) {
            previewRun++
            onSave(settings.copy(splashAnimation = it))
        }
        StartupMotionPreview(palette, settings.splashAnimation, motionOff, previewRun)
        Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_shown_on_a_fresh_launch_from_your_icon_returning_from_recents_doe),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton({ previewRun++ }, enabled = !motionOff && settings.splashAnimation != "none",
            modifier = Modifier.testTag("preview-startup-motion")) { Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_preview_startup_motion)) }
        if (settings.reduceMotion) Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_reduce_motion_is_on_startup_stays_still), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (deviceAnimationsOff) Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_device_animations_are_off_startup_stays_still), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_funky_cards), style = MaterialTheme.typography.titleMedium)
                Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_books_notes_and_dashboard_cards_drop_into_varied_tilts_and_offset), style = MaterialTheme.typography.bodyMedium)
            }
            Switch(settings.scatteredCards, { onSave(settings.copy(scatteredCards = it)) }, Modifier.testTag("scattered-cards-switch"))
        }
        Row(Modifier.fillMaxWidth().height(92.dp).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf("book-a", "note-b", "book-c").forEachIndexed { i, key ->
                Card(Modifier.weight(1f).fillMaxHeight().vernScatteredCard(key, settings.scatteredCards), shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = if (i == 1) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (i == 1) androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_notes) else androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_book), style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomizationChoices(selected: String, choices: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { (id, label) -> FilterChip(selected == id, { onSelect(id) }, modifier = Modifier.testTag("customization-$id"), label = { Text(label) }) }
    }
}

@Composable
private fun StartupMotionPreview(palette: VernIconPalette, style: String, reduce: Boolean, run: Int) {
    val progress = remember { Animatable(0f) }
    var playing by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    LaunchedEffect(run, style, reduce) {
        playing = false
        finished = false
        progress.snapTo(0f)
        if (run == 0 || reduce || style == "none") return@LaunchedEffect
        playing = true
        progress.animateTo(1f, tween(VernStartupMotion.duration(style), easing = androidx.compose.animation.core.FastOutSlowInEasing))
        playing = false
        finished = true
    }
    val pose = if (!playing) VernStartupMotion.Pose() else if (style == "bloom") VernStartupMotion.bloomPose(progress.value)
        else VernStartupMotion.exitPose(style, progress.value)
    Box(Modifier.fillMaxWidth().height(184.dp).testTag("startup-motion-stage")
        .clip(RoundedCornerShape(26.dp))
        .background(Color(palette.background), RoundedCornerShape(26.dp)), contentAlignment = Alignment.Center) {
        if (finished && style != "bloom") {
            Text(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.ui_vern_is_ready), Modifier.testTag("startup-preview-ready"), color = Color(palette.foreground), style = MaterialTheme.typography.labelLarge)
        } else Image(painterResource(R.drawable.vern_v_cream), localizedSettingsOption(androidx.compose.ui.res.stringResource(com.veritas.reader.R.string.settings_option_startup_preview)),
            Modifier.size(100.dp).testTag("startup-preview-logo").graphicsLayer {
                alpha = pose.alpha
                scaleX = pose.scale; scaleY = pose.scale
                translationY = pose.liftDp.dp.toPx()
            }, colorFilter = ColorFilter.tint(Color(palette.foreground)))
        val styleLabel = when (style) {
            "bloom" -> androidx.compose.ui.res.stringResource(R.string.settings_bloom)
            "fade" -> androidx.compose.ui.res.stringResource(R.string.settings_fade)
            "rise" -> androidx.compose.ui.res.stringResource(R.string.settings_rise)
            else -> androidx.compose.ui.res.stringResource(R.string.ui_none)
        }
        Text(if (playing) androidx.compose.ui.res.stringResource(R.string.settings_playing_style, styleLabel) else if (finished) androidx.compose.ui.res.stringResource(R.string.ui_preview_finished) else androidx.compose.ui.res.stringResource(R.string.settings_style_preview, styleLabel),
            Modifier.align(Alignment.BottomCenter).padding(12.dp), style = MaterialTheme.typography.labelSmall, color = Color(palette.foreground))
    }
}
