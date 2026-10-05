package com.veritas.reader.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
    val shape = NavigationSplitShape(split, VeritasPackStyle.navigationCornerRadius())
    val isGlass = VeritasPackStyle.glassChromeEnabled() &&
        isDeviceGlassCapable() && LocalVeritasBackdrop.current != null
    val opaqueGlassFallback = VeritasPackStyle.currentPackId() == "liquid_glass" && !isGlass
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 36.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center) {
        Surface(
            Modifier.widthIn(max = 420.dp).fillMaxWidth()
                .height(VeritasPackStyle.navigationHeight(showNavLabels))
                .veritasGlassBackdrop(shape, isGlass,
                    surfaceOpacity = VeritasPackStyle.chromeOpacity(VeritasPackStyle.currentPackId())),
            shape = shape, color = if (opaqueGlassFallback) scheme.surface else Color.Transparent, tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, VeritasPackStyle.navigationBorderBrush(scheme)),
            shadowElevation = VeritasPackStyle.chromeElevation(VeritasPackStyle.currentPackId())
        ) {
            Row(Modifier.fillMaxSize().then(if (isGlass || opaqueGlassFallback) Modifier else Modifier.background(VeritasPackStyle.navigationBrush(scheme))),
                verticalAlignment = Alignment.CenterVertically) {
                VeritasHomeTab.orderedDestinations.forEach { tab ->
                    if (tab == VeritasHomeTab.NOTES) Spacer(Modifier.width(12.dp * split))
                    val selected = activeNavTab == tab
                    BottomNavItem(selected, { onNavigateToTab(tab) }, icon = { color, size ->
                        val icon = when (tab) {
                            VeritasHomeTab.HOME -> if (selected) Icons.Filled.Home else Icons.Outlined.Home
                            VeritasHomeTab.LIBRARY -> if (selected) Icons.AutoMirrored.Filled.MenuBook else Icons.AutoMirrored.Outlined.MenuBook
                            VeritasHomeTab.STUDY -> if (selected) Icons.Filled.Layers else Icons.Outlined.Layers
                            VeritasHomeTab.NOTES -> if (selected) Icons.Filled.EditNote else Icons.Outlined.EditNote
                        }
                        Icon(icon, null, Modifier.size(size), tint = color)
                    }, label = tab.label, showLabel = showNavLabels,
                        modifier = Modifier.onGloballyPositioned {
                            OnboardingController.updateBounds("${tab.name.lowercase()}_tab", it)
                        })
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun LibraryBottomNavBarPreview() {
    MaterialTheme { LibraryBottomNavBar(VeritasHomeTab.HOME, true, {}) }
}
