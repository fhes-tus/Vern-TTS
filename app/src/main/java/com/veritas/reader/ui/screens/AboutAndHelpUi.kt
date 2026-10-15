package com.veritas.reader.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veritas.reader.R
import com.veritas.reader.VeritasPackStyle
import com.veritas.reader.ui.ReaderUiState

private fun openUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }
}

private fun openEmail(context: Context, email: String, subject: String = "") {
    runCatching {
        val uriText = "mailto:$email" + if (subject.isNotBlank()) "?subject=${Uri.encode(subject)}" else ""
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse(uriText))
        context.startActivity(intent)
    }
}

val IconGithub: ImageVector
    get() = ImageVector.Builder(
        name = "Github",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 0f)
            curveTo(5.37f, 0f, 0f, 5.37f, 0f, 12f)
            curveTo(0f, 17.31f, 3.435f, 21.795f, 8.205f, 23.385f)
            curveTo(8.805f, 23.495f, 9.025f, 23.125f, 9.025f, 22.805f)
            curveTo(9.025f, 22.515f, 9.015f, 21.755f, 9.01f, 20.735f)
            curveTo(5.675f, 21.46f, 4.97f, 19.125f, 4.97f, 19.125f)
            curveTo(4.425f, 17.735f, 3.645f, 17.365f, 3.645f, 17.365f)
            curveTo(2.555f, 16.62f, 3.73f, 16.635f, 3.73f, 16.635f)
            curveTo(4.935f, 16.72f, 5.57f, 17.87f, 5.57f, 17.87f)
            curveTo(6.64f, 19.705f, 8.38f, 19.175f, 9.065f, 18.87f)
            curveTo(9.175f, 18.095f, 9.485f, 17.565f, 9.825f, 17.265f)
            curveTo(7.16f, 16.965f, 4.355f, 15.935f, 4.355f, 11.335f)
            curveTo(4.355f, 10.025f, 4.825f, 8.955f, 5.595f, 8.115f)
            curveTo(5.47f, 7.815f, 5.06f, 6.595f, 5.715f, 4.955f)
            curveTo(5.715f, 4.955f, 6.72f, 4.635f, 9.01f, 6.185f)
            curveTo(9.965f, 5.92f, 10.99f, 5.788f, 12.01f, 5.783f)
            curveTo(13.03f, 5.788f, 14.055f, 5.92f, 15.01f, 6.185f)
            curveTo(17.3f, 4.635f, 18.305f, 4.955f, 18.305f, 4.955f)
            curveTo(18.96f, 6.595f, 18.55f, 7.815f, 18.425f, 8.115f)
            curveTo(19.2f, 8.955f, 19.665f, 10.025f, 19.665f, 11.335f)
            curveTo(19.665f, 15.945f, 16.855f, 16.96f, 14.18f, 17.255f)
            curveTo(14.61f, 17.625f, 15f, 18.355f, 15f, 19.475f)
            curveTo(15f, 21.075f, 14.985f, 22.365f, 14.985f, 22.805f)
            curveTo(14.985f, 23.13f, 15.2f, 23.505f, 15.81f, 23.385f)
            curveTo(20.57f, 21.79f, 24f, 17.31f, 24f, 12f)
            curveTo(24f, 5.37f, 18.63f, 0f, 12f, 0f)
            close()
        }
    }.build()

val IconX: ImageVector
    get() = ImageVector.Builder(
        name = "X",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(18.244f, 2.25f)
            lineTo(21.552f, 2.25f)
            lineTo(14.325f, 10.51f)
            lineTo(22.827f, 21.75f)
            lineTo(16.17f, 21.75f)
            lineTo(10.956f, 14.933f)
            lineTo(4.99f, 21.75f)
            lineTo(1.68f, 21.75f)
            lineTo(9.41f, 12.915f)
            lineTo(1.254f, 2.25f)
            lineTo(8.08f, 2.25f)
            lineTo(12.793f, 8.481f)
            close()
            moveTo(17.083f, 19.77f)
            lineTo(18.916f, 19.77f)
            lineTo(7.084f, 4.126f)
            lineTo(5.117f, 4.126f)
            close()
        }
    }.build()

val IconTelegram: ImageVector
    get() = ImageVector.Builder(
        name = "Telegram",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(11.944f, 0.002f)
            curveTo(5.347f, 0.002f, 0f, 5.349f, 0f, 11.946f)
            curveTo(0f, 18.543f, 5.347f, 23.89f, 11.944f, 23.89f)
            curveTo(18.541f, 23.89f, 23.888f, 18.543f, 23.888f, 11.946f)
            curveTo(23.888f, 5.349f, 18.541f, 0.002f, 11.944f, 0.002f)
            close()
            moveTo(17.848f, 7.915f)
            lineTo(15.864f, 17.268f)
            curveTo(15.714f, 17.931f, 15.321f, 18.096f, 14.764f, 17.784f)
            lineTo(11.741f, 15.556f)
            lineTo(10.282f, 16.96f)
            curveTo(10.12f, 17.122f, 9.985f, 17.257f, 9.673f, 17.257f)
            lineTo(9.89f, 14.175f)
            lineTo(15.498f, 9.106f)
            curveTo(15.742f, 8.889f, 15.444f, 8.769f, 15.119f, 8.986f)
            lineTo(8.188f, 13.35f)
            lineTo(5.199f, 12.416f)
            curveTo(4.549f, 12.213f, 4.536f, 11.766f, 5.336f, 11.454f)
            lineTo(17.037f, 6.945f)
            curveTo(17.579f, 6.742f, 18.053f, 7.067f, 17.848f, 7.915f)
            close()
        }
    }.build()

@Composable
private fun SocialIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(52.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = CircleShape
            )
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                CircleShape
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun AboutOptionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val cardBorder = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = VeritasPackStyle.compactShape()
                )
                .then(Modifier.border(cardBorder, VeritasPackStyle.compactShape())),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun OpenSourceLicensesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
        title = { Text("Open Source Licenses", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Vern is built using the following open source libraries and tools:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                val licenses = listOf(
                    "Jetpack Compose" to "Apache License 2.0",
                    "Kotlin Standard Library" to "Apache License 2.0",
                    "AndroidX Core & Lifecycle" to "Apache License 2.0",
                    "Material Components for Android" to "Apache License 2.0",
                    "KotlinX Coroutines" to "Apache License 2.0",
                    "Google Gson" to "Apache License 2.0"
                )
                licenses.forEach { (lib, lic) ->
                    Column {
                        Text(lib, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(lic, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    )
}

@Composable
fun AboutDialog(uiState: ReaderUiState? = null, onCheckForUpdates: () -> Unit = {}, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val palette = com.veritas.reader.VernCustomization.palette(com.veritas.reader.VeritasThemeState.launcherPalette, com.veritas.reader.VeritasThemeState.themeId, androidx.compose.foundation.isSystemInDarkTheme())
    var showLicensesDialog by remember { mutableStateOf(false) }
    val version = remember(context) {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).let { "Version ${it.versionName} · Build ${it.longVersionCode}" } }
            .getOrDefault("Version ${com.veritas.reader.BuildConfig.VERSION_NAME}")
    }
    FullScreenSettingsScaffold(title = "About", onBack = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer)) {
                Box(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(scheme.primaryContainer, scheme.surfaceContainer, scheme.tertiaryContainer)))) {
                    // A quiet book-spine motif gives the hero depth without competing with the words.
                    androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                        val accent = scheme.onPrimaryContainer.copy(alpha = .055f)
                        repeat(3) { i ->
                            drawRoundRect(accent, topLeft = androidx.compose.ui.geometry.Offset(size.width * .72f + i * 22.dp.toPx(), -25.dp.toPx()),
                                size = androidx.compose.ui.geometry.Size(42.dp.toPx(), size.height + 60.dp.toPx()),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()))
                        }
                    }
                    Column(Modifier.fillMaxWidth().padding(26.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            Box(Modifier.size(80.dp).background(androidx.compose.ui.graphics.Color(palette.background), androidx.compose.foundation.shape.RoundedCornerShape(26.dp)), contentAlignment = Alignment.Center) {
                                Image(painterResource(R.drawable.vern_v_cream), "Vern logo", Modifier.size(52.dp),
                                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(androidx.compose.ui.graphics.Color(palette.foreground)), contentScale = ContentScale.Fit)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("Vern", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = scheme.onSurface)
                                Text("A little space for big ideas.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                            }
                        }
                        Text("Read. Listen.\nMake it yours.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                        Text("Your books, your notes, your pace. A quieter place to read, listen and follow your curiosity.",
                            style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                        androidx.compose.material3.Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                            color = scheme.surface.copy(alpha = .8f), contentColor = scheme.onSurfaceVariant) {
                            Text(version, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Always growing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                    Text("Keep your reading space up to date.", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    androidx.compose.material3.FilledTonalButton(onCheckForUpdates, enabled = uiState?.isCheckingForUpdates != true, modifier = Modifier.fillMaxWidth()) {
                        if (uiState?.isCheckingForUpdates == true) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Sync, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (uiState?.isCheckingForUpdates == true) "Checking for updates…" else "Check for updates")
                    }
                    uiState?.updateStatusMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Made better together", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow)) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                        AboutOptionRow("Contact the developer", "A question, an idea, or just hello", Icons.Outlined.Mail,
                            { openEmail(context, "myreader.veritas@gmail.com", "Vern Feedback & Support") })
                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = .5f))
                        AboutOptionRow("Support Vern", "Help this little reading space grow", Icons.Outlined.FavoriteBorder,
                            { openEmail(context, "myreader.veritas@gmail.com", "Donate to Vern") })
                        HorizontalDivider(color = scheme.outlineVariant.copy(alpha = .5f))
                        AboutOptionRow("Report an issue", "Tell us what could work better", Icons.Outlined.BugReport,
                            { openUrl(context, "https://github.com/fhes-tus/Veritas-Reader/issues") })
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Find Vern online", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = scheme.onSurface)
                Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf(Triple(IconGithub, "GitHub", "https://github.com/fhes-tus/Vern-TTS"),
                            Triple(IconX, "X", "https://x.com/_1st2us"),
                            Triple(IconTelegram, "Telegram", "https://t.me/myreader_veritas"),
                            Triple(Icons.Outlined.Language, "Website", "https://fhes-tus.github.io/Vern-TTS/")).forEach { (icon, label, url) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                SocialIconButton(icon, label, { openUrl(context, url) })
                                Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            AboutOptionRow("Open source licenses", "Built with care, and a little help", Icons.Outlined.Info, { showLicensesDialog = true })
            Text("Your library stays on your device. Online features are optional.",
                Modifier.fillMaxWidth().padding(horizontal = 16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
        }
    }
    if (showLicensesDialog) OpenSourceLicensesDialog({ showLicensesDialog = false })
}
