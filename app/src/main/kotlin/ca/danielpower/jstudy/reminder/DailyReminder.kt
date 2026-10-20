package ca.danielpower.jstudy.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import ca.danielpower.jstudy.MainActivity
import ca.danielpower.jstudy.R
import ca.danielpower.jstudy.data.Reminder
import ca.danielpower.jstudy.data.Repository
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.concurrent.thread

/**
 * A once-a-day notification when cards are waiting. It uses an inexact
 * alarm, so it can arrive a few minutes late, but it needs no special
 * permission and doesn't wake the phone more than Android allows.
 */
object DailyReminder {
    private const val CHANNEL = "daily_reminder"
    private const val NOTIFICATION_ID = 1

    /** Set or cancel the alarm to match [reminder]. Safe to call often. */
    fun schedule(context: Context, reminder: Reminder) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = alarmIntent(context)
        alarms.cancel(pending)
        val minuteOfDay = reminder.time ?: return
        alarms.set(AlarmManager.RTC_WAKEUP, nextTime(minuteOfDay), pending)
    }

    /** The next time the clock reads [minuteOfDay], today or tomorrow. */
    fun nextTime(minuteOfDay: Int, now: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())): Long {
        val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        var next = now.with(time)
        if (!next.isAfter(now)) next = ZonedDateTime.of(LocalDate.from(now).plusDays(1), time, now.zone)
        return next.toInstant().toEpochMilli()
    }

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Whether a reminder would show: allowed, and the app's notifications not turned off in the phone's settings. */
    fun notificationsOn(context: Context): Boolean =
        canNotify(context) && context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    /** Check the queue and post the reminder if anything is due, then set tomorrow's alarm. */
    internal fun fire(context: Context) {
        val repo = Repository.get(context)
        val reminder = repo.reminder()
        schedule(context, reminder)
        if (reminder.time == null || !canNotify(context)) return
        val summary = repo.summary(System.currentTimeMillis())
        val kana = summary.kana?.due ?: 0
        if (!summary.anythingDue && kana == 0) return
        val due = summary.newCards + summary.learning + summary.reviews
        val text = buildString {
            when {
                kana > 0 && due > 0 -> append("Kana practice and ${if (due == 1) "1 card" else "$due cards"} are waiting")
                kana > 0 -> append("Kana practice is waiting")
                else -> append(if (due == 1) "1 card is waiting" else "$due cards are waiting")
            }
            // Only while today doesn't count yet: reading or kana practice keep a streak as well as reviews.
            if (summary.streak > 0 && !summary.activeToday) {
                append(if (summary.streak == 1) " · keep your 1-day streak" else " · keep your ${summary.streak}-day streak")
            }
        }
        post(context, text)
    }

    @SuppressLint("MissingPermission") // fire() checks canNotify first.
    private fun post(context: Context, text: String) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Time to study")
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        notifications.notify(NOTIFICATION_ID, notification)
    }
}

/** The daily alarm. Checking the queue reads the database, so it runs off the main thread. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        thread {
            try {
                DailyReminder.fire(context.applicationContext)
            } finally {
                result.finish()
            }
        }
    }
}

/** Alarms don't survive a reboot or an app update, so set it again after either. */
class ReminderRescheduler : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        thread {
            try {
                DailyReminder.schedule(context.applicationContext, Repository.get(context).reminder())
            } finally {
                result.finish()
            }
        }
    }
}
