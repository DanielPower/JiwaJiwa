package ca.danielpower.jstudy.ui

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ca.danielpower.jstudy.reminder.DailyReminder
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Returns a function that asks for a reminder time, then for permission to
 * post notifications if Android needs it, and passes the chosen minute of
 * the day to [onChosen]. Nothing is asked for until it's called, and
 * [onChosen] isn't called if the time picker or the permission is refused.
 */
@Composable
fun rememberReminderPicker(onChosen: (minuteOfDay: Int) -> Unit): (current: Int?) -> Unit {
    val context = LocalContext.current
    val chosen by rememberUpdatedState(onChosen)
    var pending by remember { mutableStateOf<Int?>(null) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val minute = pending
        pending = null
        if (granted && minute != null) chosen(minute)
    }
    return { current ->
        // The picker opens on the current time when no reminder has been set before.
        val start = current ?: LocalTime.now().let { it.hour * 60 + it.minute }
        TimePickerDialog(
            context,
            { _, hour, minute ->
                val minuteOfDay = hour * 60 + minute
                if (!DailyReminder.canNotify(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pending = minuteOfDay
                    askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    chosen(minuteOfDay)
                }
            },
            start / 60,
            start % 60,
            DateFormat.is24HourFormat(context),
        ).show()
    }
}

/** A minute of the day as a local time, like "7:30 PM" or "19:30". */
fun reminderTime(minuteOfDay: Int): String =
    LocalTime.of(minuteOfDay / 60, minuteOfDay % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
