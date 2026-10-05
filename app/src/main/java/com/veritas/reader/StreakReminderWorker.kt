package com.veritas.reader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Evening streak-protection nudge: fires around 19:00 daily; notifies only when
 * the daily reading goal isn't met yet AND there is a streak (2+ days) worth
 * protecting. Silent otherwise — a retention loop, not a nag machine.
 */
class StreakReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val repository = DocumentRepository(applicationContext)
        val settings = repository.loadReaderSettings()
        if (!settings.streakReminderEnabled) return Result.success()

        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val todayMinutes = (repository.loadTrackerDays()[todayKey]?.usageMillis ?: 0L) / 60_000L
        if (todayMinutes >= settings.dailyGoalMinutes) return Result.success()

        val streak = repository.loadReaderTrackerSnapshot().currentStreak
        if (streak < 2) return Result.success()

        val remaining = (settings.dailyGoalMinutes - todayMinutes).coerceAtLeast(1L)
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reading reminders", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            applicationContext, 71, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching {
            manager.notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_veritas)
                    .setContentTitle("Your $streak-day streak ends at midnight")
                    .setContentText("$remaining more minute${if (remaining == 1L) "" else "s"} of reading keeps it alive.")
                    .setContentIntent(pending)
                    .setAutoCancel(true)
                    .build()
            )
        }
        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "streak_reminder"
        private const val NOTIFICATION_ID = 7103
        private const val WORK_NAME = "veritas_streak_reminder"

        fun schedule(context: Context) {
            // First run at the next 19:00, then every 24h (WorkManager drift is fine).
            val now = Calendar.getInstance()
            val target = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 19)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            val delayMinutes = (target.timeInMillis - now.timeInMillis) / 60_000L
            val request = PeriodicWorkRequestBuilder<StreakReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
