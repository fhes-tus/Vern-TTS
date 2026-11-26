package com.veritas.reader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Evening reminder for an unfinished daily goal and a streak of at least two
 * consecutive days that would end without qualifying activity today.
 */
class StreakReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val repository = DocumentRepository(applicationContext)
        val settings = repository.loadReaderSettings()
        if (!settings.streakReminderEnabled) return Result.success()

        val now = System.currentTimeMillis()
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val snapshot = repository.loadReaderTrackerSnapshot(now)
        val reminder = streakReminderFor(today, snapshot, settings.dailyGoalMinutes) ?: return Result.success()
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
                    .setContentTitle("Your ${reminder.streakDays}-day streak is at risk")
                    .setContentText("Read or listen today to keep it going. ${reminder.remainingMinutes} min left toward your daily goal.")
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

internal data class StreakReminder(val streakDays: Int, val remainingMinutes: Long)

internal fun streakReminderFor(
    today: LocalDate,
    snapshot: ReaderTrackerSnapshot,
    dailyGoalMinutes: Int
): StreakReminder? {
    val todayKey = today.toString()
    if (todayKey in snapshot.activeDateKeys) return null
    val todayMinutes = (snapshot.readingMillisByDate[todayKey] ?: 0L) / 60_000L
    val remainingMinutes = dailyGoalMinutes - todayMinutes
    if (remainingMinutes <= 0L) return null
    val streak = ReaderTrackerMath.currentStreak(snapshot.activeDateKeys, today.minusDays(1).toString())
    return if (streak >= 2) StreakReminder(streak, remainingMinutes) else null
}
