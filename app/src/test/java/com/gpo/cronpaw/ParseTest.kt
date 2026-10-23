package com.gpo.cronpaw

import com.gpo.cronpaw.feature.create.Parse
import com.gpo.cronpaw.feature.search.DateQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ParseTest {
    private val today = LocalDate.of(2026, 10, 23) // a Friday

    @Test fun tomorrowAfternoonWithLength() {
        val p = Parse.parse("明天下午三点半 和小王吃饭 一小时", today)
        assertEquals(today.plusDays(1), p.date)
        assertEquals(15 * 60 + 30, p.minute)
        assertEquals(60, p.minutes)
        assertEquals("和小王吃饭", p.title)
    }

    @Test fun clockAndMinutes() {
        val p = Parse.parse("开会 14:00 45分钟", today)
        assertEquals(14 * 60, p.minute)
        assertEquals(45, p.minutes)
        assertEquals("开会", p.title)
        assertNull(p.date)
    }

    @Test fun nextWeekday() {
        assertEquals(LocalDate.of(2026, 10, 27), Parse.parse("下周二 复盘", today).date)
        assertEquals(LocalDate.of(2026, 10, 24), Parse.parse("周六 爬山", today).date)
    }

    @Test fun dateNextOccurrence() {
        assertEquals(LocalDate.of(2027, 10, 17), Parse.parse("10.17 生日", today).date)
        assertEquals(LocalDate.of(2023, 10, 17), Parse.parse("23.10.17", today).date)
    }

    @Test fun halfHour() {
        assertEquals(90, Parse.parse("一个半小时 跑步", today).minutes)
        assertEquals(30, Parse.parse("半小时 冥想", today).minutes)
    }

    @Test fun rangesFollowTheView() {
        val three = DateQuery.candidates("10.17", 3, today)
        assertEquals(LocalDate.of(2027, 10, 16), three.first().from)
        assertEquals(LocalDate.of(2027, 10, 18), three.first().to)
        val week = DateQuery.candidates("10.17", 7, today)
        assertEquals(LocalDate.of(2027, 10, 11), week.first().from) // Monday
        val y = DateQuery.candidates("23.10.17", 1, today)
        assertEquals(LocalDate.of(2023, 10, 17), y.single().from)
    }
}
