package com.gpo.cronpaw.feature.create

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * What a spoken or typed line says about when: 「明天下午三点半 和小王吃饭 一小时」 →
 * date tomorrow, 15:30, 60 minutes, title 「和小王吃饭」. Whatever is not recognised stays
 * in the title; nothing here guesses beyond the words.
 */
data class Parsed(
    val title: String,
    val date: LocalDate? = null,
    /** Minute of day. */
    val minute: Int? = null,
    val minutes: Int? = null,
)

object Parse {
    private val Cn = mapOf('零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)

    /** 「十二」「二十五」「3」 → Int. */
    fun number(s: String): Int? {
        s.toIntOrNull()?.let { return it }
        if (s.isEmpty()) return null
        if (s == "十") return 10
        val ten = s.indexOf('十')
        if (ten >= 0) {
            val hi = if (ten == 0) 1 else Cn[s[0]] ?: return null
            val lo = if (ten == s.lastIndex) 0 else Cn[s[ten + 1]] ?: return null
            return hi * 10 + lo
        }
        if (s.length == 1) return Cn[s[0]]
        return null
    }

    private const val Num = "(\\d{1,2}|[零〇一二两三四五六七八九十]{1,3})"
    private val Weekday = mapOf('一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5, '六' to 6, '日' to 7, '天' to 7)

    fun parse(input: String, today: LocalDate = LocalDate.now()): Parsed {
        var text = " ${input.trim()} "
        var date: LocalDate? = null
        var minute: Int? = null
        var minutes: Int? = null

        fun take(regex: Regex, block: (MatchResult) -> Boolean) {
            val m = regex.find(text) ?: return
            if (block(m)) text = text.removeRange(m.range).let { " $it " }
        }

        // ── Dates ──
        take(Regex("大后天")) { date = today.plusDays(3); true }
        take(Regex("后天")) { if (date == null) date = today.plusDays(2); true }
        take(Regex("明天|明日|明早|明晚")) { m ->
            if (date == null) date = today.plusDays(1)
            if (m.value == "明晚") text = text.replaceRange(m.range.last + 1, m.range.last + 1, "晚上")
            if (m.value == "明早") text = text.replaceRange(m.range.last + 1, m.range.last + 1, "早上")
            true
        }
        take(Regex("今天|今日|今晚")) { m ->
            if (date == null) date = today
            if (m.value == "今晚") text = text.replaceRange(m.range.last + 1, m.range.last + 1, "晚上")
            true
        }
        take(Regex("(下下|下个?|这个?|本)?(周|星期|礼拜)([一二三四五六日天])")) { m ->
            val dow = DayOfWeek.of(Weekday.getValue(m.groupValues[3][0]))
            val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).with(TemporalAdjusters.nextOrSame(dow))
            date = when (m.groupValues[1]) {
                "下下" -> thisWeek.plusWeeks(2)
                "下", "下个" -> thisWeek.plusWeeks(1)
                "这", "这个", "本" -> thisWeek
                else -> if (thisWeek.isBefore(today)) thisWeek.plusWeeks(1) else thisWeek
            }
            true
        }
        take(Regex("(?:(\\d{2,4})[年./-])?(\\d{1,2})(?:月|[./-])(\\d{1,2})[日号]?")) { m ->
            val month = m.groupValues[2].toInt()
            val day = m.groupValues[3].toInt()
            val yearRaw = m.groupValues[1]
            runCatching {
                date = if (yearRaw.isNotEmpty()) {
                    val y = yearRaw.toInt().let { if (it < 100) 2000 + it else it }
                    LocalDate.of(y, month, day)
                } else {
                    LocalDate.of(today.year, month, day).let { if (it.isBefore(today)) it.plusYears(1) else it }
                }
            }.isSuccess
        }

        // ── Durations (before times, so 「一小时」 is not read as 一点) ──
        take(Regex("$Num?(个)?半(个)?(小时|钟头)")) { m ->
            val h = number(m.groupValues[1]) ?: 0
            minutes = h * 60 + 30; true
        }
        take(Regex("(\\d+(?:\\.\\d+)?)\\s*(h|小时|个小时|hr)", RegexOption.IGNORE_CASE)) { m ->
            minutes = ((m.groupValues[1].toFloatOrNull() ?: return@take false) * 60).toInt(); true
        }
        take(Regex("$Num(个)?(小时|钟头)")) { m ->
            minutes = (number(m.groupValues[1]) ?: return@take false) * 60; true
        }
        take(Regex("(\\d{1,3}|[一二两三四五六七八九十]{1,3})\\s*(分钟|min|mins|m)(?![a-z])", RegexOption.IGNORE_CASE)) { m ->
            minutes = number(m.groupValues[1]) ?: return@take false; true
        }

        // ── Times ──
        val period = Regex("(凌晨|早上|早晨|上午|中午|下午|傍晚|晚上|夜里|今晚)").find(text)?.value
        take(Regex("(\\d{1,2})[:：](\\d{2})")) { m ->
            minute = adjust(m.groupValues[1].toInt(), period) * 60 + m.groupValues[2].toInt(); true
        }
        if (minute == null) take(Regex("$Num\\s*[点时](半|一刻|三刻|$Num\\s*分?)?")) { m ->
            val h = number(m.groupValues[1]) ?: return@take false
            if (h > 24) return@take false
            val tail = m.groupValues[2]
            val min = when {
                tail == "半" -> 30
                tail == "一刻" -> 15
                tail == "三刻" -> 45
                tail.isNotEmpty() -> number(m.groupValues[3]) ?: 0
                else -> 0
            }
            minute = adjust(h, period) * 60 + min.coerceIn(0, 59); true
        }
        if (minute != null && period != null) text = text.replaceFirst(period, " ")
        if (minute == null && period != null) {
            // Only a part of the day: a sensible default hour.
            minute = when (period) {
                "凌晨" -> 6 * 60; "早上", "早晨" -> 8 * 60; "上午" -> 10 * 60; "中午" -> 12 * 60
                "下午" -> 15 * 60; "傍晚" -> 18 * 60; else -> 20 * 60
            }
            text = text.replaceFirst(period, " ")
        }

        val title = text.replace(Regex("^[\\s，。,.的在到从]+|[\\s，。,.]+$"), "").replace(Regex("\\s{2,}"), " ")
        return Parsed(title, date, minute, minutes)
    }

    private fun adjust(h: Int, period: String?): Int = when (period) {
        "下午", "傍晚", "晚上", "夜里", "今晚" -> if (h in 1..11) h + 12 else h
        "中午" -> if (h in 1..2) h + 12 else h
        else -> h
    }.coerceIn(0, 23)
}
