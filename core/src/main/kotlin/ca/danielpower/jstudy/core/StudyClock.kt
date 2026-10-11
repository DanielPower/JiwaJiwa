package ca.danielpower.jstudy.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Study days run from the rollover hour (4am by default) to the next day's
 * rollover, in the device's time zone. A day is identified by the epoch day
 * of the calendar date it starts on.
 */
class StudyClock(
    private val zone: ZoneId = ZoneId.systemDefault(),
    val rolloverHour: Int = 4,
) {
    init {
        require(rolloverHour in 0..23)
    }

    fun dayOf(epochMillis: Long): Long {
        val local = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val date = if (local.hour < rolloverHour) local.toLocalDate().minusDays(1) else local.toLocalDate()
        return date.toEpochDay()
    }

    /** The instant study day [day] begins. */
    fun startOf(day: Long): Long =
        LocalDate.ofEpochDay(day).atTime(rolloverHour, 0).atZone(zone).toInstant().toEpochMilli()
}
