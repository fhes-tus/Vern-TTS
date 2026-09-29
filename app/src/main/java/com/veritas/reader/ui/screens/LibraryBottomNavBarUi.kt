package com.veritas.reader.ui.screens


import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.veritas.reader.blendColors
import com.veritas.reader.ui.OnboardingController

@Composable
internal fun LibraryBottomNavBar(
    activeNavTab: VeritasHomeTab,
    showNavLabels: Boolean,
    onNavigateToTab: (VeritasHomeTab) -> Unit,
    modifier: Modifier = Modifier
) {
                val scheme = MaterialTheme.colorScheme
                val isDark = scheme.surface.luminance() < 0.5f
                val showNavLabels = showNavLabels
                val barHeight = if (showNavLabels) 66.dp else 60.dp

                // Translucent floating capsule with theme gradient
                val gradientBrush = if (isDark) {
                    Brush.verticalGradient(
                        colors = listOf(
                            blendColors(scheme.surface, scheme.primary, 0.12f).copy(alpha = 0.94f),
                            blendColors(scheme.surface, Color.Black, 0.20f).copy(alpha = 0.96f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            blendColors(scheme.surface, scheme.primaryContainer, 0.35f).copy(alpha = 0.95f),
                            blendColors(scheme.surface, scheme.primary, 0.08f).copy(alpha = 0.97f)
                        )
                    )
                }

                val borderBrush = if (isDark) {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            scheme.primary.copy(alpha = 0.32f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.65f),
                            scheme.primary.copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.30f)
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 580.dp)
                            .fillMaxWidth()
                            .height(barHeight),
                        shape = RoundedCornerShape(barHeight / 2),
                        color = Color.Transparent,
                        shadowElevation = if (isDark) 10.dp else 8.dp,
                        tonalElevation = 0.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    brush = gradientBrush,
                                    shape = RoundedCornerShape(barHeight / 2)
                                )
                                .border(
                                    width = 1.dp,
                                    brush = borderBrush,
                                    shape = RoundedCornerShape(barHeight / 2)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BottomNavItem(
                                    selected = activeNavTab == VeritasHomeTab.HOME,
                                    onClick = { onNavigateToTab(VeritasHomeTab.HOME) },
                                    icon = { color, size ->
                                        Icon(
                                            imageVector = if (activeNavTab == VeritasHomeTab.HOME) Icons.Filled.Home else Icons.Outlined.Home,
                                            contentDescription = "Home",
                                            tint = color,
                                            modifier = Modifier.size(size)
                                        )
                                    },
                                    label = "Home",
                                    showLabel = showNavLabels
                                )

                                BottomNavItem(
                                    selected = activeNavTab == VeritasHomeTab.LIBRARY,
                                    onClick = { onNavigateToTab(VeritasHomeTab.LIBRARY) },
                                    icon = { color, size ->
                                        Icon(
                                            imageVector = if (activeNavTab == VeritasHomeTab.LIBRARY) Icons.AutoMirrored.Filled.MenuBook else Icons.AutoMirrored.Outlined.MenuBook,
                                            contentDescription = "Library",
                                            tint = color,
                                            modifier = Modifier.size(size)
                                        )
                                    },
                                    label = "Library",
                                    showLabel = showNavLabels
                                )

                                BottomNavItem(
                                    selected = activeNavTab == VeritasHomeTab.NOTES,
                                    onClick = { onNavigateToTab(VeritasHomeTab.NOTES) },
                                    icon = { color, size ->
                                        Icon(
                                            imageVector = if (activeNavTab == VeritasHomeTab.NOTES) Icons.Filled.EditNote else Icons.Outlined.EditNote,
                                            contentDescription = "Notes",
                                            tint = color,
                                            modifier = Modifier.size(size)
                                        )
                                    },
                                    label = "Notes",
                                    showLabel = showNavLabels,
                                    modifier = Modifier.onGloballyPositioned { OnboardingController.updateBounds("notes_tab", it) }
                                )

                                BottomNavItem(
                                    selected = activeNavTab == VeritasHomeTab.STUDY,
                                    onClick = { onNavigateToTab(VeritasHomeTab.STUDY) },
                                    icon = { color, size ->
                                        Icon(
                                            imageVector = if (activeNavTab == VeritasHomeTab.STUDY) Icons.Filled.Layers else Icons.Outlined.Layers,
                                            contentDescription = "Study",
                                            tint = color,
                                            modifier = Modifier.size(size)
                                        )
                                    },
                                    label = "Study",
                                    showLabel = showNavLabels,
                                    modifier = Modifier.onGloballyPositioned { OnboardingController.updateBounds("study_tab", it) }
                                )
                            }
                        }
                    }
                }

}

@Preview(showBackground = true)
@Composable
internal fun LibraryBottomNavBarPreview() {
    MaterialTheme {
        LibraryBottomNavBar(
            activeNavTab = VeritasHomeTab.HOME,
            showNavLabels = true,
            onNavigateToTab = {}
        )
    }
}
