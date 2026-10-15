package com.veritas.reader

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

data class VernIconPalette(val id: String, val label: String, val background: Int, val foreground: Int)

object VernCustomization {
    private var watching = false
    private var startedActivities = 0
    private var pendingPalette: ReaderSettings? = null
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    /** Samsung may finish the activity launched through an alias when that alias is disabled.
     * Keep all component changes out of the foreground, including while settings are open. */
    fun queueLauncherPalette(settings: ReaderSettings) { pendingPalette = settings }

    fun watchApplication(application: Application) {
        if (watching) return
        watching = true
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { startedActivities++ }
            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities != 0 || activity.isChangingConfigurations) return
                handler.postDelayed({
                    if (startedActivities == 0) pendingPalette?.let { settings ->
                        runCatching { applyLauncherPalette(application, settings) }
                            .onSuccess { pendingPalette = null }
                            .onFailure { android.util.Log.w("VernIcon", "Launcher palette could not be applied", it) }
                    }
                }, 400L)
            }
            override fun onActivityCreated(activity: Activity, state: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
    val palettes = listOf(
        VernIconPalette("indigo", "Vern indigo", 0xFF4540D8.toInt(), 0xFFFFF6E6.toInt()),
        VernIconPalette("forest", "Forest", 0xFF295548.toInt(), 0xFFFFF6E6.toInt()),
        VernIconPalette("ocean", "Ocean", 0xFF145B7A.toInt(), 0xFFE7F6FF.toInt()),
        VernIconPalette("rose", "Rose", 0xFF873C57.toInt(), 0xFFFFF0E9.toInt()),
        VernIconPalette("amber", "Amber", 0xFF8A4C20.toInt(), 0xFFFFF6E6.toInt()),
        VernIconPalette("midnight", "Midnight", 0xFF202534.toInt(), 0xFFE6DCFF.toInt()),
        VernIconPalette("copper", "Copper", 0xFFC07318.toInt(), 0xFFFFFFFF.toInt()),
        VernIconPalette("gold", "Gold", 0xFFE5A93C.toInt(), 0xFF2C1E00.toInt()),
        VernIconPalette("stellar", "Stellar", 0xFF4338CA.toInt(), 0xFFFFFFFF.toInt()),
        VernIconPalette("lilac", "Lilac", 0xFFA79BFF.toInt(), 0xFF221656.toInt()),
        VernIconPalette("violet", "Violet", 0xFF7D4EBA.toInt(), 0xFFFFFFFF.toInt()),
        VernIconPalette("lavender", "Lavender", 0xFFBD93F9.toInt(), 0xFF282A36.toInt()),
        VernIconPalette("cobalt", "Cobalt", 0xFF4078F2.toInt(), 0xFFFFFFFF.toInt()),
        VernIconPalette("azure", "Azure", 0xFF58A6FF.toInt(), 0xFF0D1117.toInt()),
        VernIconPalette("neon", "Neon cyan", 0xFF00E5FF.toInt(), 0xFF001B20.toInt()),
        VernIconPalette("sky", "Sky", 0xFF90CAF9.toInt(), 0xFF003258.toInt()),
        VernIconPalette("ink", "Ink", 0xFF111111.toInt(), 0xFFFFFFFF.toInt()),
        VernIconPalette("pearl", "Pearl", 0xFFF2F2F2.toInt(), 0xFF090909.toInt()),
        VernIconPalette("paper", "Paper", 0xFFFFFFFF.toInt(), 0xFF000000.toInt()),
        VernIconPalette("onyx", "Onyx", 0xFF000000.toInt(), 0xFFFFFFFF.toInt())
    )
    val paletteIds = palettes.map { it.id } + "theme"

    fun palette(id: String, theme: String = "", systemInDarkTheme: Boolean = false): VernIconPalette {
        val resolved = if (id != "theme") id else when (VeritasThemeCatalog.resolveConcreteThemeId(VeritasThemeCatalog.normalizeThemeId(theme), systemInDarkTheme)) {
            "light" -> "copper"
            "dark" -> "gold"
            "midnight_light" -> "stellar"
            "midnight_dark" -> "lilac"
            "dracula_light" -> "violet"
            "dracula" -> "lavender"
            "github_light" -> "cobalt"
            "github_dark" -> "azure"
            "bw_gradient_light" -> "ink"
            "bw_gradient_dark" -> "pearl"
            "dark_high_contrast" -> "paper"
            "white_high_contrast" -> "onyx"
            "amoled" -> "sky"
            "neon" -> "neon"
            else -> "stellar"
        }
        return palettes.firstOrNull { it.id == resolved } ?: palettes.first()
    }

    /** Exactly one launcher entry. Enable before disabling on pre-33 devices. */
    fun applyLauncherPalette(context: Context, settings: ReaderSettings) {
        val pm = context.packageManager
        val dark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val selected = palette(settings.launcherPalette, settings.themeId, dark).id
        val selectedStatic = settings.reduceMotion || settings.splashAnimation != "bloom"
        val entries = palettes.flatMap { palette -> listOf(palette.id to false, palette.id to true) }
        val changes = entries.mapNotNull { (id, static) ->
            val component = ComponentName(context.packageName, "com.veritas.reader.Launcher${id.replaceFirstChar { it.uppercase() }}${if (static) "Static" else ""}")
            val state = pm.getComponentEnabledSetting(component)
            val enabled = state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && id == "indigo" && !static)
            val desired = id == selected && static == selectedStatic
            if (enabled == desired) null else component to if (desired) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        if (Build.VERSION.SDK_INT >= 33) {
            if (changes.isNotEmpty()) pm.setComponentEnabledSettings(changes.map {
                PackageManager.ComponentEnabledSetting(it.first, it.second, PackageManager.DONT_KILL_APP)
            })
        } else {
            changes.sortedByDescending { it.second == PackageManager.COMPONENT_ENABLED_STATE_ENABLED }.forEach {
                pm.setComponentEnabledSetting(it.first, it.second, PackageManager.DONT_KILL_APP)
            }
        }
    }

    fun startupTheme(context: Context, settings: ReaderSettings): Int {
        val dark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val selected = palette(settings.launcherPalette, settings.themeId, dark).id.replaceFirstChar { it.uppercase() }
        val suffix = if (settings.reduceMotion || settings.splashAnimation != "bloom") "Static" else ""
        return context.resources.getIdentifier("Theme.Vern.Startup$selected$suffix", "style", context.packageName)
            .takeIf { it != 0 } ?: R.style.Theme_Vern_StartupIndigo
    }

    /** Stable, visibly uneven poses; no reshuffling during recomposition or scrolling. */
    fun cardPose(key: String): Triple<Float, Float, Float> {
        // Avalanche nearby keys too, so sequential book/note IDs don't form a pattern.
        var hash = key.hashCode().toUInt()
        hash = (hash xor (hash shr 16)) * 0x7feb352du
        hash = (hash xor (hash shr 15)) * 0x846ca68bu
        hash = hash xor (hash shr 16)
        return Triple(((hash % nine).toInt() - 4) * 1.35f,
            (((hash / 11u) % 7u).toInt() - 3) * 2.4f,
            (((hash / 79u) % 11u).toInt() - 5) * 2.4f)
    }
    private val nine = 9u
}

internal val LocalVernCardEntrance = staticCompositionLocalOf { 0L }

@Composable
fun Modifier.vernScatteredCard(key: String, enabled: Boolean = VeritasThemeState.scatteredCards, amount: Float = 1f): Modifier {
    // High-contrast keeps straight edges and stable alignment.
    val active = enabled && !VeritasThemeState.themeId.contains("high_contrast")
    val pose = if (active) VernCustomization.cardPose(key) else Triple(0f, 0f, 0f)
    val scene = LocalVernCardEntrance.current
    val progress = remember { Animatable(1f) }
    LaunchedEffect(active, scene, VeritasThemeState.reduceMotion) {
        if (!active || VeritasThemeState.reduceMotion || (scene != 0L && SystemClock.uptimeMillis() - scene > 1200L)) {
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            kotlinx.coroutines.delay((key.hashCode().toUInt() % 160u).toLong())
            progress.animateTo(1f, spring(dampingRatio = .72f, stiffness = 65f))
        }
    }
    return graphicsLayer {
        val fall = if (active) progress.value else 1f
        rotationZ = pose.first * amount * fall
        translationX = pose.second * amount * density * fall
        translationY = (pose.third * amount * fall - (1f - fall) * 24f) * density
        scaleX = if (active) 1f - .065f * amount * fall else 1f
        scaleY = scaleX
    }
}
