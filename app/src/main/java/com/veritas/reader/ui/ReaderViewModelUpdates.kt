package com.veritas.reader.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import com.veritas.reader.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

fun ReaderViewModel.checkForUpdates(isManual: Boolean = false) {
    viewModelScope.launch(Dispatchers.IO) {
        _uiState.update {
            it.copy(
                isCheckingForUpdates = true,
                updateStatusMessage = if (isManual) "Checking for updates..." else it.updateStatusMessage
            )
        }
        try {
            // Delete old update file if it exists in the cache
            runCatching {
                val context = getApplication<Application>()
                val oldFile = File(context.cacheDir, "vern_update.apk")
                if (oldFile.exists()) {
                    oldFile.delete()
                }
                val legacyFile = File(context.cacheDir, "veritas_update.apk")
                if (legacyFile.exists()) {
                    legacyFile.delete()
                }
            }

            val url = URL("https://api.github.com/repos/fhes-tus/Vern-TTS/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("User-Agent", "Vern-Android-App")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val tagName = json.optString("tag_name", "").trim()
                val cleanTagName = tagName.removePrefix("v").trim()
                val localVersion = runCatching {
                    val context = getApplication<Application>()
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull() ?: BuildConfig.VERSION_NAME
                if (ReaderViewModel.isVersionNewer(localVersion, cleanTagName)) {
                    val htmlUrl = json.optString("html_url", "https://github.com/fhes-tus/Vern-TTS/releases")
                    val body = json.optString("body", "")
                    val apkUrl = ReaderViewModel.selectOptimalApkUrl(json.optJSONArray("assets"))

                    runCatching {
                        val context = getApplication<Application>()
                        val prefs = context.getSharedPreferences("veritas_reader_library", android.content.Context.MODE_PRIVATE)
                        prefs.edit().putString("last_downloaded_changelog", body).apply()
                    }

                    _uiState.update {
                        it.copy(
                            isCheckingForUpdates = false,
                            showUpdateDialog = true,
                            updateVersionName = tagName,
                            updateUrl = htmlUrl,
                            updateApkUrl = apkUrl,
                            updateChangelog = body,
                            updateStatusMessage = "Update available: $tagName"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isCheckingForUpdates = false,
                            updateStatusMessage = if (isManual) "Vern is up to date (v$localVersion)" else it.updateStatusMessage
                        )
                    }
                }
            } else {
                _uiState.update {
                    it.copy(
                        isCheckingForUpdates = false,
                        updateStatusMessage = if (isManual) "Could not check for updates" else it.updateStatusMessage
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ReaderViewModel", "Error checking for updates", e)
            _uiState.update {
                it.copy(
                    isCheckingForUpdates = false,
                    updateStatusMessage = if (isManual) "Network error checking for updates" else it.updateStatusMessage
                )
            }
        }
    }
}

fun ReaderViewModel.startUpdateDownload(apkUrl: String) {
    downloadJob?.cancel()
    downloadJob = viewModelScope.launch(Dispatchers.IO) {
        _uiState.update {
            it.copy(
                isDownloadingUpdate = true,
                updateDownloadProgress = 0f,
                updateDownloadError = null
            )
        }
        val context = getApplication<Application>()
        val apkFile = File(context.cacheDir, "vern_update.apk")
        var success = false
        try {
            val jobContext = coroutineContext
            var lastProgress = 0L
            com.veritas.reader.SecureDownloads.download(apkUrl, apkFile,
                com.veritas.reader.ResourceLimits.BOOK_DOWNLOAD_BYTES,
                headers = { mapOf("User-Agent" to "Vern-Android-App") },
                checkActive = { jobContext.ensureActive() },
                onProgress = { downloaded, total ->
                    val now = System.currentTimeMillis()
                    if (now - lastProgress > 100 || downloaded == total) {
                        lastProgress = now
                        _uiState.update { it.copy(updateDownloadProgress = if (total > 0) downloaded.toFloat() / total else .5f) }
                    }
                })
            com.veritas.reader.ApkUpdateSafety.validate(context, apkFile)
            _uiState.update { it.copy(updateDownloadProgress = 1f) }
            success = true
        } catch (e: Exception) {
            if (e is CancellationException) {
                // Cancelled by user
            } else {
                android.util.Log.e("ReaderViewModel", "Error downloading APK update", e)
                _uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        updateDownloadError = e.message ?: "Failed to download update"
                    )
                }
            }
        } finally {
            if (!success) apkFile.delete()
            if (success) {
                _uiState.update {
                    it.copy(
                        isDownloadingUpdate = false,
                        showUpdateDialog = false
                    )
                }
                withContext(Dispatchers.Main) {
                    triggerApkInstallation(apkFile, fallbackUrl = apkUrl)
                }
            }
        }
    }
}

fun ReaderViewModel.cancelUpdateDownload() {
    downloadJob?.cancel()
    downloadJob = null
    _uiState.update {
        it.copy(
            isDownloadingUpdate = false,
            updateDownloadProgress = 0f,
            updateDownloadError = null
        )
    }
}

internal fun ReaderViewModel.triggerApkInstallation(apkFile: File, fallbackUrl: String = "") {
    val context = getApplication<Application>()
    try {
        com.veritas.reader.ApkUpdateSafety.validate(context, apkFile)
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(settingsIntent)
        }
        val authority = "${context.packageName}.fileprovider"
        val apkUri = com.veritas.reader.SafeFileSharing.uri(context, authority, apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        android.util.Log.e("ReaderViewModel", "Error launching APK installation", e)
        val targetUrl = fallbackUrl.ifBlank { uiState.value.updateUrl.ifBlank { "https://github.com/fhes-tus/Vern-TTS/releases/latest" } }
        runCatching {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
        }
    }
}

fun ReaderViewModel.dismissReleaseNotes() {
    _uiState.update {
        it.copy(
            showReleaseNotesDialog = false,
            releaseNotesChangelog = "",
            releaseNotesVersionName = ""
        )
    }
}

fun isInstalledFromGooglePlay(context: android.content.Context): Boolean {
    return runCatching {
        val pm = context.packageManager
        val installer = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            pm.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            pm.getInstallerPackageName(context.packageName)
        }
        installer == "com.android.vending"
    }.getOrDefault(false)
}

fun openGooglePlayStore(context: android.content.Context) {
    val pkg = context.packageName
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(webIntent)
    }
}
