package com.veritas.reader.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.veritas.reader.*
import com.veritas.reader.R
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
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
fun AboutDialog(
    uiState: ReaderUiState? = null,
    onCheckForUpdates: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showLicensesDialog by remember { mutableStateOf(false) }

    FullScreenSettingsScaffold(title = "About", onBack = onDismiss, scrollable = false) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header: App Logo (max 2dp border) + Title + Version
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .border(
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.veritas_reader_icon),
                        contentDescription = "Vern Logo",
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Fit
                    )
                }

                val appVersionText = remember(context) {
                    try {
                        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        val vName = pInfo.versionName ?: com.veritas.reader.BuildConfig.VERSION_NAME
                        val vCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            pInfo.longVersionCode
                        } else {
                            @Suppress("DEPRECATION")
                            pInfo.versionCode.toLong()
                        }
                        "Version $vName (Build $vCode)"
                    } catch (_: Exception) {
                        "Version ${com.veritas.reader.BuildConfig.VERSION_NAME}"
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Vern TTS",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = appVersionText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    OutlinedButton(
                        onClick = onCheckForUpdates,
                        enabled = uiState?.isCheckingForUpdates != true,
                        shape = VeritasPackStyle.chipShape(),
                        border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme),
                        modifier = Modifier.height(36.dp)
                    ) {
                        if (uiState?.isCheckingForUpdates == true) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Checking...", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = uiState?.updateStatusMessage ?: "Check for updates",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            // Action Buttons (Donate & Contact)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Button(
                    onClick = { openEmail(context, "myreader.veritas@gmail.com", "Donate to Vern") },
                    shape = VeritasPackStyle.chipShape(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            VeritasPackStyle.chipShape()
                        )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Donate", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { openEmail(context, "myreader.veritas@gmail.com", "Vern Feedback & Support") },
                    shape = VeritasPackStyle.chipShape(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            VeritasPackStyle.chipShape()
                        )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Mail,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Contact", fontWeight = FontWeight.Bold)
                }
            }

            // Monochrome Social & Web App Icons Row (GitHub, X, Telegram, Website)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SocialIconButton(
                    icon = IconGithub,
                    label = "GitHub",
                    onClick = { openUrl(context, "https://github.com/fhes-tus/Vern-TTS") }
                )
                SocialIconButton(
                    icon = IconX,
                    label = "X",
                    onClick = { openUrl(context, "https://x.com/_1st2us") }
                )
                SocialIconButton(
                    icon = IconTelegram,
                    label = "Telegram",
                    onClick = { openUrl(context, "https://t.me/myreader_veritas") }
                )
                SocialIconButton(
                    icon = Icons.Outlined.Language,
                    label = "Website",
                    onClick = { openUrl(context, "https://fhes-tus.github.io/Vern-TTS/") }
                )
            }

            // About Veritas Reader Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = VeritasPackStyle.cardShape(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = VeritasPackStyle.surfaceAlpha())
                ),
                border = VeritasPackStyle.cardBorder(MaterialTheme.colorScheme)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "About Vern",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Vern (root word Vernehmen — German: to hear/perceive) is a privacy-focused reading engine and document studio for Android. Combining intelligent text-to-speech narration, custom voice controls, smart study cards, and reading insights, Vern brings you an effortless learning experience with zero tracking.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )
                }
            }

            // Tight Option Cards List (Submit Issue & Open Source Licenses)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AboutOptionRow(
                    title = "Submit issue or feedback",
                    subtitle = "Help us improve this application on GitHub",
                    icon = Icons.Outlined.BugReport,
                    onClick = { openUrl(context, "https://github.com/fhes-tus/Veritas-Reader/issues") }
                )
                AboutOptionRow(
                    title = "Open source licenses",
                    subtitle = "View all the libraries used to build Vern",
                    icon = Icons.Outlined.Info,
                    onClick = { showLicensesDialog = true }
                )
            }
        }
    }

    if (showLicensesDialog) {
        OpenSourceLicensesDialog(onDismiss = { showLicensesDialog = false })
    }
}
