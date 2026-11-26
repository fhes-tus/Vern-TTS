package com.veritas.reader.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.viewModelScope
import com.veritas.reader.BuildConfig
import com.veritas.reader.isInstalledUpgrade
import com.veritas.reader.releaseNotesForVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection

private const val LAST_RUN_VERSION_NAME = "last_run_version_name"
private const val LAST_RUN_VERSION_CODE = "last_run_version_code"
private const val LEGACY_UNVERSIONED_CHANGELOG = "last_downloaded_changelog"

fun ReaderViewModel.checkForUpdates(isManual: Boolean = false) {
    viewModelScope.launch(Dispatchers.Main.immediate) {
        if (uiState.value.isCheckingForUpdates) return@launch
        _uiState.update {
            it.copy(
                isCheckingForUpdates = true,
                updateStatusMessage = if (isManual) "Checking for updates..." else it.updateStatusMessage
            )
        }
        try {
            val context = getApplication<Application>()
            val (release, localVersion) = withContext(Dispatchers.IO) {
                val connection = java.net.URI("https://api.github.com/repos/fhes-tus/Vern-TTS/releases/latest")
                    .toURL().openConnection() as HttpURLConnection
                val release = try {
                    connection.requestMethod = "GET"
                    connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                    connection.setRequestProperty("User-Agent", "Vern-Android-App")
                    connection.connectTimeout = 8_000
                    connection.readTimeout = 8_000
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
                    } else null
                } finally {
                    connection.disconnect()
                }
                val installed = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                    .getOrNull() ?: BuildConfig.VERSION_NAME
                release to installed
            }
            val tagName = release?.optString("tag_name", "")?.trim().orEmpty()
            if (release != null && ReaderViewModel.isVersionNewer(localVersion, tagName.removePrefix("v").trim())) {
                val releaseUrl = release.optString("html_url", "https://github.com/fhes-tus/Vern-TTS/releases")
                val changelog = release.optString("body", "")
                val apkUrl = ReaderViewModel.selectOptimalApkUrl(release.optJSONArray("assets"))
                _uiState.update {
                    if (it.isDownloadingUpdate) it else it.copy(
                        showUpdateDialog = true,
                        updateVersionName = tagName,
                        updateUrl = releaseUrl,
                        updateApkUrl = apkUrl,
                        updateChangelog = changelog,
                        updateStatusMessage = "Update available: $tagName"
                    )
                }
            } else if (isManual) {
                _uiState.update {
                    it.copy(updateStatusMessage = if (release == null) "Could not check for updates" else "Vern is up to date (v$localVersion)")
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            android.util.Log.e("ReaderViewModel", "Error checking for updates", failure)
            if (isManual) _uiState.update { it.copy(updateStatusMessage = "Network error checking for updates") }
        } finally {
            _uiState.update { it.copy(isCheckingForUpdates = false) }
        }
    }
}

/** The installer receives a separately staged copy. This file belongs only to this attempt. */
internal inline fun <T> withUpdateStagingFile(cacheDir: File, block: (File) -> T): T {
    val file = File.createTempFile("vern-update-", ".apk", cacheDir)
    return try {
        block(file)
    } finally {
        file.delete()
    }
}

@androidx.annotation.MainThread
fun ReaderViewModel.startUpdateDownload(apkUrl: String) {
    val context = getApplication<Application>()
    downloadJob?.cancel()
    val job = viewModelScope.launch(Dispatchers.Main.immediate, start = kotlinx.coroutines.CoroutineStart.LAZY) {
        val owner = coroutineContext[kotlinx.coroutines.Job]
        _uiState.update { it.copy(isDownloadingUpdate = true, updateDownloadProgress = 0f, updateDownloadError = null) }
        try {
            val intent = withContext(Dispatchers.IO) {
                val downloadContext = coroutineContext
                withUpdateStagingFile(context.cacheDir) { file ->
                    var lastProgress = 0L
                    com.veritas.reader.SecureDownloads.download(apkUrl, file,
                        com.veritas.reader.ResourceLimits.BOOK_DOWNLOAD_BYTES,
                        headers = { mapOf("User-Agent" to "Vern-Android-App") },
                        checkActive = { downloadContext.ensureActive() },
                        onProgress = { downloaded, total ->
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - lastProgress > 100 || downloaded == total) {
                                lastProgress = now
                                val progress = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else .5f
                                viewModelScope.launch(Dispatchers.Main.immediate) {
                                    if (downloadJob === owner) _uiState.update { it.copy(updateDownloadProgress = progress) }
                                }
                            }
                        })
                    downloadContext.ensureActive()
                    com.veritas.reader.ApkUpdateSafety.validate(context, file)
                    downloadContext.ensureActive()
                    verifiedUpdateIntent(context, file, apkUrl)
                }
            }
            if (downloadJob !== owner) return@launch
            val requestingPermission = intent.action == Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
            context.startActivity(intent)
            _uiState.update {
                it.copy(
                    showUpdateDialog = requestingPermission,
                    updateDownloadProgress = 1f,
                    updateDownloadError = if (requestingPermission) "Allow installs for Vern, then return and try again." else null
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (downloadJob === owner) {
                android.util.Log.e("ReaderViewModel", "Error preparing APK update", failure)
                _uiState.update { it.copy(updateDownloadError = failure.message ?: "Could not prepare the update.") }
            }
        } finally {
            if (downloadJob === owner) {
                downloadJob = null
                _uiState.update { it.copy(isDownloadingUpdate = false) }
            }
        }
    }
    downloadJob = job
    job.start()
}

@androidx.annotation.MainThread
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

/** Only receives the APK after the app identity, signer and version were validated. */
private fun verifiedUpdateIntent(context: Application, apkFile: File, apkUrl: String): Intent {
    val manager = context.packageManager
    val installed = manager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
    val declaresInstallPermission = installed.requestedPermissions.orEmpty().any { it == android.Manifest.permission.REQUEST_INSTALL_PACKAGES }
    if (!declaresInstallPermission) {
        // Play builds do not request installer permission. Preserve the browser path for
        // sideloaded copies of those builds without launching an unusable settings page.
        return Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (!manager.canRequestPackageInstalls()) {
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val apkUri = com.veritas.reader.SafeFileSharing.uri(context, "${context.packageName}.fileprovider", apkFile, "vern_update.apk")
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(apkUri, "application/vnd.android.package-archive")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
}

internal fun ReaderViewModel.showInstalledReleaseNotes() {
    val app = getApplication<Application>()
    val prefs = app.getSharedPreferences("veritas_reader_library", android.content.Context.MODE_PRIVATE)
    val previousName = prefs.getString(LAST_RUN_VERSION_NAME, "").orEmpty()
    val previousCode = prefs.getInt(LAST_RUN_VERSION_CODE, 0)
    if (prefs.contains(LEGACY_UNVERSIONED_CHANGELOG)) {
        prefs.edit().remove(LEGACY_UNVERSIONED_CHANGELOG).apply()
    }

    val versionName = BuildConfig.VERSION_NAME
    if (!isInstalledUpgrade(previousName, previousCode, versionName, BuildConfig.VERSION_CODE)) {
        if (previousName != versionName || previousCode != BuildConfig.VERSION_CODE) {
            prefs.recordInstalledVersion()
        }
        return
    }

    val notes = runCatching {
        app.assets.open("release-notes.json").bufferedReader(Charsets.UTF_8).use {
            releaseNotesForVersion(it.readText(), versionName)
        }
    }.onFailure { android.util.Log.w("ReaderViewModel", "Could not read the installed release notes", it) }
        .getOrDefault(emptyList())
    _uiState.update {
        it.copy(showReleaseNotesDialog = true, releaseNotes = notes, releaseNotesVersionName = versionName)
    }
}

private fun android.content.SharedPreferences.recordInstalledVersion() {
    edit().putString(LAST_RUN_VERSION_NAME, BuildConfig.VERSION_NAME)
        .putInt(LAST_RUN_VERSION_CODE, BuildConfig.VERSION_CODE)
        .apply()
}

fun ReaderViewModel.dismissReleaseNotes() {
    if (uiState.value.showReleaseNotesDialog) {
        getApplication<Application>().getSharedPreferences("veritas_reader_library", android.content.Context.MODE_PRIVATE)
            .recordInstalledVersion()
    }
    _uiState.update {
        it.copy(
            showReleaseNotesDialog = false,
            releaseNotes = emptyList(),
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
