package com.veritas.reader

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class WidgetMenuActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            VeritasTheme {
                VernFoldAwarePane(Modifier.fillMaxSize()) {
                WidgetMenuDialog(
                    onDismiss = { finish() },
                    onActionSelected = { action ->
                        val intent = Intent(this@WidgetMenuActivity, MainActivity::class.java).apply {
                            action?.let { putExtra(MainActivity.EXTRA_WIDGET_ACTION, it) }
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        startActivity(intent)
                        finish()
                    }
                )
                }
            }
        }
    }
}

@Composable
fun WidgetMenuDialog(
    onDismiss: () -> Unit,
    onActionSelected: (String?) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(16.dp)
                .clickable(enabled = false, onClick = {}),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_quick_action),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                WidgetMenuItem(
                    icon = Icons.AutoMirrored.Filled.NoteAdd,
                    label = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_text_notes),
                    onClick = { onActionSelected(MainActivity.ACTION_SHOW_NOTES) }
                )

                WidgetMenuItem(
                    icon = Icons.AutoMirrored.Filled.ListAlt,
                    label = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.settings_list),
                    onClick = { onActionSelected(MainActivity.ACTION_NEW_CHECKLIST_NOTE) }
                )

                WidgetMenuItem(
                    icon = Icons.Filled.NotificationsActive,
                    label = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_reminder),
                    onClick = { onActionSelected(MainActivity.ACTION_NEW_REMINDER_NOTE) }
                )

                WidgetMenuItem(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    label = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.ui_library),
                    onClick = { onActionSelected(MainActivity.ACTION_OPEN_LIBRARY) }
                )

                WidgetMenuItem(
                    icon = Icons.Filled.UploadFile,
                    label = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_import_documents),
                    onClick = { onActionSelected(MainActivity.ACTION_IMPORT_DOCUMENTS) }
                )

                WidgetMenuItem(
                    icon = Icons.Filled.AutoStories,
                    label = com.veritas.reader.ui.screens.menuString(com.veritas.reader.R.string.l10n_active_reading),
                    onClick = { onActionSelected(MainActivity.ACTION_ACTIVE_READING) }
                )
            }
        }
    }
}

@Composable
fun WidgetMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
