package com.veritas.reader.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.border
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.veritas.reader.*
import com.veritas.reader.R
import com.veritas.reader.ui.OnboardingController
import com.veritas.reader.ui.VeritasMotion

@Composable
internal fun LibraryBottomNavBar(
    activeNavTab: VeritasHomeTab,
    showNavLabels: Boolean,
    onNavigateToTab: (VeritasHomeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectionPosition by animateFloatAsState(activeNavTab.pagerIndex + .5f,
        animationSpec = VeritasMotion.spatialSlow(), label = "navigationSelectionSlide")
    val separation by animateFloatAsState(
        if (activeNavTab == VeritasHomeTab.NOTES) 1f else 0f,
        animationSpec = VeritasMotion.spatialSlow(), label = "notesNavigationSeparation"
    )
    val split = separation.coerceIn(0f, 1f)
    val radius = VeritasPackStyle.navigationCornerRadius()
    val isGlass = VeritasPackStyle.glassChromeEnabled() &&
        isDeviceGlassCapable() && LocalVeritasBackdrop.current != null
    val opaqueGlassFallback = VeritasPackStyle.currentPackId() == "liquid_glass" && !isGlass
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 36.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center) {
        Row(Modifier.widthIn(max = 420.dp).fillMaxWidth()
            .height(VeritasPackStyle.navigationHeight(showNavLabels))) {
            if (split < .001f) {
                NavigationCapsule(RoundedCornerShape(radius), isGlass, opaqueGlassFallback, Modifier.fillMaxSize(),
                    activeNavTab, VeritasHomeTab.orderedDestinations, showNavLabels, selectionPosition) {
                    VeritasHomeTab.orderedDestinations.forEach { tab ->
                        NavigationTab(tab, activeNavTab, showNavLabels, onNavigateToTab)
                    }
                }
            } else {
                // Each rounded glass layer keeps its own lens and edge highlight.
                // Separate outlines leave the page visible through the gap.
                val innerRadius = radius * split
                NavigationCapsule(
                    RoundedCornerShape(topStart = radius, bottomStart = radius, topEnd = innerRadius, bottomEnd = innerRadius),
                    isGlass, opaqueGlassFallback, Modifier.weight(3f).fillMaxHeight().testTag("navigation-main-capsule"),
                    activeNavTab, VeritasHomeTab.orderedDestinations.filterNot { it == VeritasHomeTab.NOTES }, showNavLabels, selectionPosition
                ) {
                    VeritasHomeTab.orderedDestinations.filterNot { it == VeritasHomeTab.NOTES }.forEach { tab ->
                        NavigationTab(tab, activeNavTab, showNavLabels, onNavigateToTab)
                    }
                }
                Spacer(Modifier.width(12.dp * split))
                NavigationCapsule(
                    RoundedCornerShape(topStart = innerRadius, bottomStart = innerRadius, topEnd = radius, bottomEnd = radius),
                    isGlass, opaqueGlassFallback, Modifier.weight(1f).fillMaxHeight().testTag("navigation-notes-capsule"),
                    activeNavTab, listOf(VeritasHomeTab.NOTES), showNavLabels, selectionPosition
                ) { NavigationTab(VeritasHomeTab.NOTES, activeNavTab, showNavLabels, onNavigateToTab) }
            }
        }
    }
}

@Composable
private fun NavigationCapsule(shape: Shape, glass: Boolean, opaqueFallback: Boolean, modifier: Modifier,
    active: VeritasHomeTab, tabs: List<VeritasHomeTab>, labels: Boolean, selectionPosition: Float,
    content: @Composable RowScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val targetIndex = tabs.indexOf(active)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val position = (selectionPosition - tabs.first().pagerIndex) / tabs.size
    val opacity by animateFloatAsState(if (targetIndex >= 0) 1f else 0f,
        animationSpec = VeritasMotion.effects(), label = "navigationSelectionFade")
    Surface(modifier.veritasGlassBackdrop(shape, glass, surfaceOpacity = .36f), shape = shape,
        color = if (opaqueFallback) scheme.surface else Color.Transparent, tonalElevation = 0.dp,
        border = if (glass) null else androidx.compose.foundation.BorderStroke(1.dp, VeritasPackStyle.navigationBorderBrush(scheme)),
        shadowElevation = if (glass) 0.dp else VeritasPackStyle.chromeElevation(VeritasPackStyle.currentPackId())) {
        BoxWithConstraints(Modifier.fillMaxSize().then(if (glass || opaqueFallback) Modifier else Modifier.background(VeritasPackStyle.navigationBrush(scheme)))) {
            val pillWidth = minOf(if (labels) 64.dp else 54.dp, maxWidth / tabs.size)
            Box(Modifier.align(Alignment.CenterStart).width(pillWidth).height(if (labels) 48.dp else 42.dp)
                .testTag("navigation-selection-${tabs.first().name}")
                .graphicsLayer {
                    val distance = maxWidth.toPx() * position - pillWidth.toPx() / 2f
                    translationX = if (rtl) -distance else distance
                    alpha = opacity
                }
                .background(if (glass) androidx.compose.ui.graphics.lerp(scheme.surface, scheme.primary,
                    if (scheme.surface.luminance() < .5f) .28f else .18f)
                    else scheme.primary.copy(alpha = if (scheme.surface.luminance() < .5f) .28f else .18f), VeritasPackStyle.chipShape())
                .border(1.dp, scheme.primary.copy(alpha = .35f), VeritasPackStyle.chipShape()))
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, content = content)
        }
    }
}

@Composable
private fun RowScope.NavigationTab(tab: VeritasHomeTab, active: VeritasHomeTab, labels: Boolean, onNavigate: (VeritasHomeTab) -> Unit) {
    val selected = active == tab
    BottomNavItem(selected, { onNavigate(tab) }, icon = { color, size ->
        val drawable = when (tab) {
            VeritasHomeTab.HOME -> if (selected) R.drawable.vern_nav_home_filled else R.drawable.vern_nav_home
            VeritasHomeTab.LIBRARY -> if (selected) R.drawable.vern_nav_newsstand_filled else R.drawable.vern_nav_newsstand
            VeritasHomeTab.NOTES -> if (selected) R.drawable.vern_nav_note_stack_filled else R.drawable.vern_nav_note_stack
            VeritasHomeTab.STUDY -> null
        }
        if (drawable != null) Icon(painterResource(drawable), null, Modifier.size(size), tint = color)
        else Icon(if (selected) Icons.Filled.Layers else Icons.Outlined.Layers, null, Modifier.size(size), tint = color)
    }, label = tab.label, showLabel = labels, showSelectionPill = false, modifier = Modifier.onGloballyPositioned {
        OnboardingController.updateBounds("${tab.name.lowercase()}_tab", it)
    })
}

@Preview(showBackground = true)
@Composable
internal fun LibraryBottomNavBarPreview() {
    MaterialTheme { LibraryBottomNavBar(VeritasHomeTab.HOME, true, {}) }
}
