package com.gpo.cronpaw.ui.time

import com.gpo.cronpaw.core.agenda.AgendaItem
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Local-time helpers. Everything is the device zone, read fresh (the user may travel). */
object Times {
    val zone: ZoneId get() = ZoneId.systemDefault()

    fun date(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun dateTime(ms: Long): LocalDateTime = Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()
    fun startOf(day: LocalDate): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()
    fun at(day: LocalDate, minuteOfDay: Int): Long = startOf(day) + minuteOfDay * 60_000L
    fun minuteOfDay(ms: Long): Int = dateTime(ms).let { it.hour * 60 + it.minute }

    fun monday(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** 9:05 */
    fun hm(ms: Long): String = dateTime(ms).let { "${it.hour}:${it.minute.toString().padStart(2, '0')}" }

    fun hmRange(item: AgendaItem): String = if (item.allDay) "全天" else "${hm(item.start)}  ${hm(item.end)}"

    private val Weekdays = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    fun weekday(day: LocalDate): String = Weekdays[day.dayOfWeek.value - 1]

    /** 10月17日 周五, with the year only when it is not this year. */
    fun dayLabel(day: LocalDate): String {
        val y = if (day.year != LocalDate.now().year) "${day.year}年" else ""
        return "$y${day.monthValue}月${day.dayOfMonth}日  ${weekday(day)}"
    }

    /** A duration as a bold value and a quiet unit: 45 / 分钟, 1.5 / 小时. */
    fun duration(minutes: Int): Pair<String, String> = when {
        minutes < 60 -> minutes.toString() to "分钟"
        minutes % 60 == 0 -> (minutes / 60).toString() to "小时"
        minutes % 30 == 0 -> (minutes / 60f).toString().removeSuffix("0") to "小时"
        else -> "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}" to "小时"
    }

    fun durationText(minutes: Int): String = duration(minutes).let { (v, u) -> "$v$u" }
}
