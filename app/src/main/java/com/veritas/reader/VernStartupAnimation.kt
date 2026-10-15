package com.veritas.reader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.veritas.reader.ui.LocalVeritasMotion
import kotlinx.coroutines.delay

/** One short entrance on a fresh launch; never replay on rotation or foreground resume. */
@Composable
internal fun VernStartupAnimation(enabled: Boolean) {
    val reduceMotion = LocalVeritasMotion.current.reduceMotion || VeritasThemeState.splashAnimation == "none"
    var finished by rememberSaveable { mutableStateOf(!enabled || reduceMotion) }
    val entrance = remember { Animatable(0f) }
    val opacity = remember { Animatable(1f) }
    LaunchedEffect(enabled, reduceMotion) {
        if (!enabled || reduceMotion) { finished = true; return@LaunchedEffect }
        if (finished) return@LaunchedEffect
        entrance.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        delay(180)
        opacity.animateTo(0f, tween(180))
        finished = true
    }
    if (!finished) Box(Modifier.fillMaxSize().testTag("vern-startup-animation")
        .graphicsLayer { alpha = opacity.value }
        .background(Color(VernCustomization.palette(VeritasThemeState.launcherPalette, VeritasThemeState.themeId).background))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.vern_v_cream), "Vern is starting",
            Modifier.size(132.dp).graphicsLayer {
                val scale = if (VeritasThemeState.splashAnimation == "bloom") .84f + entrance.value * .16f else 1f
                scaleX = scale; scaleY = scale
                alpha = entrance.value
                translationY = if (VeritasThemeState.splashAnimation == "rise") (1f - entrance.value) * 18.dp.toPx() else 0f
            }, colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color(VernCustomization.palette(VeritasThemeState.launcherPalette, VeritasThemeState.themeId).foreground)))
    }
}
