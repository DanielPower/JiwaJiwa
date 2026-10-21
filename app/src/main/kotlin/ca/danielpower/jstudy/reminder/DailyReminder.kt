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
import ca.danielpower.jstudy.core.StepKind
import ca.danielpower.jstudy.core.TodayPlan
import ca.danielpower.jstudy.data.ChapterEntry
import ca.danielpower.jstudy.data.Reminder
import ca.danielpower.jstudy.data.Repository
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.concurrent.thread

/**
 * A once-a-day notification while something's left in the day's plan. It
 * uses an inexact alarm, so it can arrive a few minutes late, but it needs
 * no special permission and doesn't wake the phone more than Android allows.
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

    /** Post the reminder if anything's left in the day's plan, then set tomorrow's alarm. */
    internal fun fire(context: Context) {
        val repo = Repository.get(context)
        val reminder = repo.settled { repo.reminder() }
        schedule(context, reminder)
        if (reminder.time == null || !canNotify(context)) return
        val now = System.currentTimeMillis()
        val text = repo.settled { text(repo.todayPlan(now), repo.summary(now).streak) } ?: return
        post(context, text)
    }

    /**
     * What's left of the day's plan, as Home shows it: "Today: kana,
     * Arrival 2 and 6 reviews", or one step on its own. Null once there's
     * nothing to do now; today's new kana's late check alone isn't worth a
     * notification. The streak is mentioned while today doesn't count yet.
     */
    internal fun text(plan: TodayPlan<ChapterEntry>, streak: Int): String? {
        val left = plan.left
        if (left.isEmpty()) return null
        fun reviews(n: Int) = if (n == 1) "1 review" else "$n reviews"
        val only = left.singleOrNull()
        val text = if (only != null) {
            when (only.kind) {
                StepKind.KANA -> "Kana practice is waiting"
                StepKind.READ -> "${only.read?.item?.label ?: "A chapter"} is ready to read"
                StepKind.REVIEW -> "${reviews(only.count)} left today"
            }
        } else {
            val names = left.map { step ->
                when (step.kind) {
                    StepKind.KANA -> "kana"
                    StepKind.READ -> step.read?.item?.label ?: "a chapter"
                    StepKind.REVIEW -> reviews(step.count)
                }
            }
            "Today: " + names.dropLast(1).joinToString(", ") + " and " + names.last()
        }
        if (streak == 0 || plan.soFar.any) return text
        return text + if (streak == 1) " · keep your 1-day streak" else " · keep your $streak-day streak"
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
                val repo = Repository.get(context)
                DailyReminder.schedule(context.applicationContext, repo.settled { repo.reminder() })
            } finally {
                result.finish()
            }
        }
    }
}
