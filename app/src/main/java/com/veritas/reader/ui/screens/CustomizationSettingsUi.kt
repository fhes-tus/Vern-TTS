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
    FullScreenSettingsScaffold("Customization", onDismiss) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp)) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(Modifier.size(76.dp).background(Color(palette.background), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.vern_v_cream), null, Modifier.size(50.dp), colorFilter = ColorFilter.tint(Color(palette.foreground)))
                }
                Column(Modifier.weight(1f)) {
                    Text("Make Vern yours", style = MaterialTheme.typography.titleLarge)
                    Text("Colours, card character and page motion.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text("App icon colours", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
        Text("Choose a palette for your launcher icon. The launcher updates when you leave Vern, so your settings stay open.", style = MaterialTheme.typography.bodyMedium)
        FilterChip(settings.launcherPalette == "theme", { onSave(settings.copy(launcherPalette = "theme")) },
            modifier = Modifier.testTag("icon-follow-theme"), label = { Text("Follow my colour theme") })
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
        Text("Startup animation", style = MaterialTheme.typography.titleMedium)
        CustomizationChoices(settings.splashAnimation, listOf("bloom" to "Bloom", "fade" to "Fade", "rise" to "Rise", "none" to "None")) {
            previewRun++
            onSave(settings.copy(splashAnimation = it))
        }
        StartupMotionPreview(palette, settings.splashAnimation, motionOff, previewRun)
        Text("Shown on a fresh launch from your icon. Returning from Recents does not replay the splash. Changes apply after you leave Vern.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton({ previewRun++ }, enabled = !motionOff && settings.splashAnimation != "none",
            modifier = Modifier.testTag("preview-startup-motion")) { Text("Preview startup motion") }
        if (settings.reduceMotion) Text("Reduce motion is on. Startup stays still.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (deviceAnimationsOff) Text("Device animations are off. Startup stays still.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Funky cards", style = MaterialTheme.typography.titleMedium)
                Text("Books, Notes and dashboard cards drop into varied tilts and offsets. Each card keeps its place.", style = MaterialTheme.typography.bodyMedium)
            }
            Switch(settings.scatteredCards, { onSave(settings.copy(scatteredCards = it)) }, Modifier.testTag("scattered-cards-switch"))
        }
        Row(Modifier.fillMaxWidth().height(92.dp).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf("book-a", "note-b", "book-c").forEachIndexed { i, key ->
                Card(Modifier.weight(1f).fillMaxHeight().vernScatteredCard(key, settings.scatteredCards), shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = if (i == 1) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(if (i == 1) "Notes" else "Book", style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Reading page turns", style = MaterialTheme.typography.titleMedium)
        CustomizationChoices(settings.pageTurnStyle, listOf("slide" to "Slide", "flip" to "Page curl", "hinge" to "Light flip", "none" to "None")) { onSave(settings.copy(pageTurnStyle = it)) }
        Text("Page curl bends text pages with a shaded paper reverse. Light flip uses a simpler spine rotation. Native PDF and original-document viewers keep their own navigation.", style = MaterialTheme.typography.bodySmall)
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
            Text("Vern is ready", Modifier.testTag("startup-preview-ready"), color = Color(palette.foreground), style = MaterialTheme.typography.labelLarge)
        } else Image(painterResource(R.drawable.vern_v_cream), "Startup preview",
            Modifier.size(100.dp).testTag("startup-preview-logo").graphicsLayer {
                alpha = pose.alpha
                scaleX = pose.scale; scaleY = pose.scale
                translationY = pose.liftDp.dp.toPx()
            }, colorFilter = ColorFilter.tint(Color(palette.foreground)))
        Text(if (playing) "Playing ${style.replaceFirstChar { it.uppercase() }}" else if (finished) "Preview finished" else "${style.replaceFirstChar { it.uppercase() }} preview",
            Modifier.align(Alignment.BottomCenter).padding(12.dp), style = MaterialTheme.typography.labelSmall, color = Color(palette.foreground))
    }
}
