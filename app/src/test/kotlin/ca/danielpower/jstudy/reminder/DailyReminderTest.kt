package ca.danielpower.jstudy.reminder

import ca.danielpower.jstudy.data.Reminder
import ca.danielpower.jstudy.data.levelLabel
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailyReminderTest {
    private val zone = ZoneId.of("America/St_Johns")

    private fun at(text: String): ZonedDateTime = LocalDateTime.parse(text).atZone(zone)

    private fun millis(text: String) = at(text).toInstant().toEpochMilli()

    @Test
    fun laterTodayWhenTheTimeHasNotPassed() {
        assertEquals(millis("2026-10-10T19:30"), DailyReminder.nextTime(19 * 60 + 30, at("2026-10-10T08:00")))
    }

    @Test
    fun tomorrowWhenTheTimeHasPassedOrIsNow() {
        assertEquals(millis("2026-10-11T07:00"), DailyReminder.nextTime(7 * 60, at("2026-10-10T08:00")))
        assertEquals(millis("2026-10-11T08:00"), DailyReminder.nextTime(8 * 60, at("2026-10-10T08:00")))
    }

    @Test
    fun keepsTheWallClockTimeAcrossDaylightSaving() {
        // Clocks spring forward overnight on 2026-03-08 in St. John's.
        assertEquals(millis("2026-03-08T20:00"), DailyReminder.nextTime(20 * 60, at("2026-03-07T21:00")))
        // And fall back overnight on 2026-11-01.
        assertEquals(millis("2026-11-01T20:00"), DailyReminder.nextTime(20 * 60, at("2026-10-31T21:00")))
    }

    @Test
    fun reminderHasATimeOnlyWhenEnabledAndPicked() {
        assertNull(Reminder(enabled = true, minuteOfDay = null).time)
        assertNull(Reminder(enabled = false, minuteOfDay = 600).time)
        assertEquals(600, Reminder(enabled = true, minuteOfDay = 600).time)
    }

    @Test
    fun levelLabels() {
        assertEquals("N5", levelLabel(5))
        assertEquals("N1", levelLabel(1))
        assertEquals("Other", levelLabel(0))
        assertEquals("Other", levelLabel(6))
    }
}
