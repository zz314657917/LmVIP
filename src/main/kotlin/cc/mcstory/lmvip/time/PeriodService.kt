package cc.mcstory.lmvip.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

class PeriodService(
    val zoneId: ZoneId,
    private val weekStart: DayOfWeek,
) {
    private val dayFormatter = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(zoneId)
    private val monthFormatter = DateTimeFormatter.ofPattern("yyyyMM").withZone(zoneId)

    fun nowMillis(): Long = System.currentTimeMillis()

    fun dayKey(epochMillis: Long = nowMillis()): String {
        return dayFormatter.format(Instant.ofEpochMilli(epochMillis))
    }

    fun monthKey(epochMillis: Long = nowMillis()): String {
        return monthFormatter.format(Instant.ofEpochMilli(epochMillis))
    }

    fun weekKey(epochMillis: Long = nowMillis()): String {
        val date = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
        val fields = WeekFields.of(weekStart, 4)
        val year = date.get(fields.weekBasedYear())
        val week = date.get(fields.weekOfWeekBasedYear())
        return "%04d-W%02d".format(year, week)
    }
}
