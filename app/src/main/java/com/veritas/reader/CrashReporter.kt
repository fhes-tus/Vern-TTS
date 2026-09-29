package com.veritas.reader

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local-only crash capture. The app is distributed outside the Play Store, so there is no
 * console to surface crashes — without this, field failures are invisible. An uncaught
 * exception is written to a local file; on next launch the user is offered (never forced)
 * to share the report via any app. Nothing leaves the device without an explicit tap.
 */
object CrashReporter {
    private const val FILE_NAME = "last_crash_report.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val version = runCatching {
                    appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
                }.getOrNull() ?: "?"
                val report = buildString {
                    appendLine("Vern crash report")
                    appendLine("Version: $version")
                    appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})")
                    appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    appendLine(android.util.Log.getStackTraceString(throwable))
                }
                File(appContext.filesDir, FILE_NAME).writeText(report)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Shows a report/dismiss dialog if the previous session crashed. Call from onCreate. */
    fun offerPendingReport(activity: Activity) {
        val file = File(activity.filesDir, FILE_NAME)
        if (!file.exists()) return
        val report = runCatching { file.readText() }.getOrNull().orEmpty()
        if (report.isBlank()) {
            file.delete()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.crash_dialog_title))
            .setMessage(activity.getString(R.string.crash_dialog_message))
            .setPositiveButton(activity.getString(R.string.crash_dialog_send)) { _, _ ->
                file.delete()
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Vern crash report")
                    putExtra(Intent.EXTRA_TEXT, report)
                }
                runCatching {
                    activity.startActivity(
                        Intent.createChooser(send, activity.getString(R.string.crash_dialog_send))
                    )
                }
            }
            .setNegativeButton(activity.getString(R.string.crash_dialog_dismiss)) { _, _ ->
                file.delete()
            }
            .setCancelable(true)
            .setOnCancelListener { file.delete() }
            .show()
    }
}
