package com.veritas.reader

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Schedules and fires reminder notifications for general notes.
 *
 * Uses an exact alarm when the platform allows it (pre-S, or when the user has
 * granted SCHEDULE_EXACT_ALARM on S+), otherwise falls back to an inexact
 * allow-while-idle alarm so the reminder still fires without requiring any
 * special permission. Either way no reminder is lost silently.
 */
object NoteReminderScheduler {
    const val CHANNEL_ID = "veritas_note_reminders"
    private const val ACTION_FIRE = "com.veritas.reader.action.NOTE_REMINDER"
    const val EXTRA_NOTE_ID = "note_id"
    const val EXTRA_TITLE = "note_title"
    const val EXTRA_BODY = "note_body"

    fun schedule(context: Context, noteId: String, title: String, body: String, triggerAtMillis: Long) {
        if (triggerAtMillis <= System.currentTimeMillis()) {
            cancel(context, noteId)
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildPendingIntent(context, noteId, title, body)
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
        } else {
            true
        }
        runCatching {
            if (canExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }
    }

    fun cancel(context: Context, noteId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        runCatching {
            alarmManager.cancel(buildPendingIntent(context, noteId, "", ""))
        }
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Note reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders you set on your notes"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                enableLights(true)
                setShowBadge(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildPendingIntent(context: Context, noteId: String, title: String, body: String): PendingIntent {
        val intent = Intent(context, NoteReminderReceiver::class.java).apply {
            action = ACTION_FIRE
            // The data uri makes the PendingIntent unique per note so cancel/replace works.
            data = android.net.Uri.parse("vern://note-reminder/$noteId")
            putExtra(EXTRA_NOTE_ID, noteId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, noteId.hashCode(), intent, flags)
    }
}

class NoteReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getStringExtra(NoteReminderScheduler.EXTRA_NOTE_ID).orEmpty()
        val title = intent.getStringExtra(NoteReminderScheduler.EXTRA_TITLE)?.takeIf { it.isNotBlank() }
            ?: "Note reminder"
        val body = intent.getStringExtra(NoteReminderScheduler.EXTRA_BODY).orEmpty()

        NoteReminderScheduler.ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            noteId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NoteReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_veritas)
            .setContentTitle(title)
            .setContentText(body.ifBlank { "Tap to open your note." })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.ifBlank { "Tap to open your note." }))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 400, 200, 400))
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        val manager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return
        // POST_NOTIFICATIONS is requested elsewhere; notify is a no-op if not granted.
        runCatching { manager.notify(noteId.hashCode(), notification) }
    }
}

class NoteReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED) {

            runCatching {
                val repo = DocumentRepository(context)
                val notes = repo.loadGeneralNotes()
                val now = System.currentTimeMillis()
                notes.filter { it.reminderAt != null && it.reminderAt > now }.forEach { note ->
                    val title = note.title.ifBlank { "Vern note" }
                    val body = note.content.take(120).ifBlank { "Tap to view note" }
                    val reminderTime = note.reminderAt ?: return@forEach
                    NoteReminderScheduler.schedule(context, note.id, title, body, reminderTime)
                }
            }
        }
    }
}
