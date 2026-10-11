package com.veritas.reader.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Home
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.veritas.reader.*
import com.veritas.reader.ui.OnboardingController
import com.veritas.reader.ui.VeritasMotion

@Composable
internal fun LibraryBottomNavBar(
    activeNavTab: VeritasHomeTab,
    showNavLabels: Boolean,
    onNavigateToTab: (VeritasHomeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
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
                NavigationCapsule(RoundedCornerShape(radius), isGlass, opaqueGlassFallback, Modifier.fillMaxSize()) {
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
                    isGlass, opaqueGlassFallback, Modifier.weight(3f).fillMaxHeight().testTag("navigation-main-capsule")
                ) {
                    VeritasHomeTab.orderedDestinations.filterNot { it == VeritasHomeTab.NOTES }.forEach { tab ->
                        NavigationTab(tab, activeNavTab, showNavLabels, onNavigateToTab)
                    }
                }
                Spacer(Modifier.width(12.dp * split))
                NavigationCapsule(
                    RoundedCornerShape(topStart = innerRadius, bottomStart = innerRadius, topEnd = radius, bottomEnd = radius),
                    isGlass, opaqueGlassFallback, Modifier.weight(1f).fillMaxHeight().testTag("navigation-notes-capsule")
                ) { NavigationTab(VeritasHomeTab.NOTES, activeNavTab, showNavLabels, onNavigateToTab) }
            }
        }
    }
}

@Composable
private fun NavigationCapsule(shape: Shape, glass: Boolean, opaqueFallback: Boolean, modifier: Modifier,
    content: @Composable RowScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier.veritasGlassBackdrop(shape, glass, surfaceOpacity = .36f), shape = shape,
        color = if (opaqueFallback) scheme.surface else Color.Transparent, tonalElevation = 0.dp,
        border = if (glass) null else androidx.compose.foundation.BorderStroke(1.dp, VeritasPackStyle.navigationBorderBrush(scheme)),
        shadowElevation = if (glass) 0.dp else VeritasPackStyle.chromeElevation(VeritasPackStyle.currentPackId())) {
        Row(Modifier.fillMaxSize().then(if (glass || opaqueFallback) Modifier else Modifier.background(VeritasPackStyle.navigationBrush(scheme))),
            verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
private fun RowScope.NavigationTab(tab: VeritasHomeTab, active: VeritasHomeTab, labels: Boolean, onNavigate: (VeritasHomeTab) -> Unit) {
    val selected = active == tab
    BottomNavItem(selected, { onNavigate(tab) }, icon = { color, size ->
        val icon = when (tab) {
            VeritasHomeTab.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
            VeritasHomeTab.LIBRARY -> if (selected) Icons.AutoMirrored.Filled.MenuBook else Icons.AutoMirrored.Outlined.MenuBook
            VeritasHomeTab.STUDY -> if (selected) Icons.Filled.Layers else Icons.Outlined.Layers
            VeritasHomeTab.NOTES -> if (selected) Icons.Filled.EditNote else Icons.Outlined.EditNote
        }
        Icon(icon, null, Modifier.size(size), tint = color)
    }, label = tab.label, showLabel = labels, modifier = Modifier.onGloballyPositioned {
        OnboardingController.updateBounds("${tab.name.lowercase()}_tab", it)
    })
}

@Preview(showBackground = true)
@Composable
internal fun LibraryBottomNavBarPreview() {
    MaterialTheme { LibraryBottomNavBar(VeritasHomeTab.HOME, true, {}) }
}
