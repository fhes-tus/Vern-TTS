package com.veritas.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.desktop.models.DesktopThemeType

@Composable
fun DesktopTopBar(
    documentTitle: String?,
    documentSource: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    showSearch: Boolean,
    onToggleSearch: () -> Unit,
    currentTheme: DesktopThemeType,
    onSelectTheme: (DesktopThemeType) -> Unit,
    onSwitchToFloater: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleSidebar: () -> Unit,
    onToggleStudyStudio: () -> Unit,
    isSidebarOpen: Boolean,
    isStudyStudioOpen: Boolean
) {
    var themeMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Toggle Sidebar + Brand
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleSidebar) {
                    Icon(
                        imageVector = if (isSidebarOpen) Icons.Default.MenuOpen else Icons.Default.Menu,
                        contentDescription = "Toggle Library Sidebar",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "VERITAS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimary,
                        letterSpacing = 1.sp
                    )
                }

                if (documentTitle != null) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Divider(
                        modifier = Modifier.height(20.dp).width(1.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = documentTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 340.dp)
                        )
                        if (documentSource != null) {
                            Text(
                                text = documentSource,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Center: Search Bar if active
            if (showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search in document...", fontSize = 13.sp) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        IconButton(onClick = onToggleSearch) {
                            Icon(Icons.Default.Close, contentDescription = "Close search", modifier = Modifier.size(18.dp))
                        }
                    },
                    modifier = Modifier.width(280.dp).height(46.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.background,
                        unfocusedContainerColor = MaterialTheme.colorScheme.background
                    )
                )
            }

            // Right: Actions (Search, Floater switch, Theme, Study Studio)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!showSearch) {
                    IconButton(onClick = onToggleSearch) {
                        Icon(Icons.Default.Search, contentDescription = "Search document")
                    }
                }

                // Switch to Floater Button
                FilledTonalButton(
                    onClick = onSwitchToFloater,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureInPicture,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Floater Mode", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Theme Dropdown Menu
                Box {
                    IconButton(onClick = { themeMenuExpanded = true }) {
                        Icon(Icons.Default.Palette, contentDescription = "Themes")
                    }
                    DropdownMenu(
                        expanded = themeMenuExpanded,
                        onDismissRequest = { themeMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Slate Dark") },
                            onClick = { onSelectTheme(DesktopThemeType.SLATE_DARK); themeMenuExpanded = false },
                            leadingIcon = { Icon(Icons.Default.DarkMode, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Light Air") },
                            onClick = { onSelectTheme(DesktopThemeType.LIGHT_AIR); themeMenuExpanded = false },
                            leadingIcon = { Icon(Icons.Default.LightMode, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Warm Sepia") },
                            onClick = { onSelectTheme(DesktopThemeType.WARM_SEPIA); themeMenuExpanded = false },
                            leadingIcon = { Icon(Icons.Default.AutoStories, null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Obsidian OLED") },
                            onClick = { onSelectTheme(DesktopThemeType.OBSIDIAN_OLED); themeMenuExpanded = false },
                            leadingIcon = { Icon(Icons.Default.Contrast, null) }
                        )
                    }
                }

                // Toggle Study Studio
                IconButton(onClick = onToggleStudyStudio) {
                    Icon(
                        imageVector = if (isStudyStudioOpen) Icons.Default.AutoFixHigh else Icons.Default.AutoFixNormal,
                        contentDescription = "Toggle Study Studio",
                        tint = if (isStudyStudioOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
