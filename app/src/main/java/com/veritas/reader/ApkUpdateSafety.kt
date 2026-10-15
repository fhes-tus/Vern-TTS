package com.veritas.reader

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import java.security.MessageDigest

internal object ApkUpdateSafety {
    fun validate(context: Context, file: File) {
        val manager = context.packageManager
        val candidate = requireNotNull(manager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)) {
            "Downloaded file is not a valid Android application."
        }
        val installed = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        require(candidate.packageName == context.packageName) { "Downloaded update belongs to another application." }
        require(candidate.longVersionCode > installed.longVersionCode) { "Downloaded update is not a newer version." }
        val signing = requireNotNull(candidate.signingInfo) { "Downloaded update has no verified signer." }
        val current = requireNotNull(installed.signingInfo) { "Installed signing identity is unavailable." }
        val installedSigners = current.apkContentsSigners.map { it.toByteArray() }
        val updateSigners = signing.apkContentsSigners.map { it.toByteArray() }
        // Multiple signers must match exactly; a single signer may have a
        // platform-verified rotation lineage containing our current signer.
        val matches = if (current.hasMultipleSigners() || signing.hasMultipleSigners()) {
            installedSigners.size == updateSigners.size && installedSigners.all { known -> updateSigners.any { MessageDigest.isEqual(known, it) } }
        } else {
            val lineage = signing.signingCertificateHistory.map { it.toByteArray() }
            installedSigners.all { known -> lineage.any { MessageDigest.isEqual(known, it) } }
        }
        require(matches) { "This APK uses a different signing key. Keep Vern installed and use your existing update channel. Export a backup before any uninstall." }
    }
}
