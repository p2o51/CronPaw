package com.gpo.cronpaw.core.agenda

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DayIndexTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    private val today = LocalDate.of(2026, 10, 25)
    private fun at(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun item(start: Long, end: Long) = AgendaItem("test", "test", start, end, true, 0, Source.Calendar(1, 1))

    @Test fun longEventsAreClippedToTheVisibleWeekRatherThanTheirFirst62Days() {
        val item = item(at(today.minusYears(30)), at(today.plusYears(30)))
        val index = itemsByDay(listOf(item), today, today.plusDays(6), zone)
        assertEquals(7, index.size)
        assertEquals(listOf(item), index[today.plusDays(6)])
    }

    @Test fun endAtMidnightDoesNotLeakIntoTheFollowingDay() {
        val item = item(at(today), at(today.plusDays(2)))
        val index = itemsByDay(listOf(item), today, today.plusDays(2), zone)
        assertEquals(setOf(today, today.plusDays(1)), index.keys)
    }

    @Test fun zeroDurationItemStillHasADay() {
        val index = itemsByDay(listOf(item(at(today), at(today))), today, today, zone)
        assertEquals(1, index[today]?.size)
    }

    @Test fun outOfRangeAndInvertedWindowsStayEmpty() {
        val items = listOf(item(at(today.minusDays(2)), at(today)))
        assertTrue(itemsByDay(items, today, today.plusDays(6), zone).isEmpty())
        assertTrue(itemsByDay(items, today.plusDays(1), today, zone).isEmpty())
    }

    @Test fun localDaysAcrossDaylightSavingAreNotAssumedToBe24Hours() {
        val berlin = ZoneId.of("Europe/Berlin")
        val begin = today.atStartOfDay(berlin).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(berlin).toInstant().toEpochMilli()
        assertEquals(25 * 3_600_000L, end - begin)
        assertEquals(setOf(today), itemsByDay(listOf(item(begin, end)), today, today.plusDays(1), berlin).keys)
    }

    @Test fun nearbyWindowsMergeWithoutQueryingTheGapToADistantMonth() {
        assertEquals(listOf(0L to 20L, 1000L to 1020L),
            mergeReadWindows(listOf(1000L to 1020L, 10L to 20L, 0L to 10L, 4L to 6L)))
    }

    @Test fun invalidReadWindowsAreIgnored() {
        assertEquals(emptyList<Pair<Long, Long>>(), mergeReadWindows(listOf(2L to 1L, 5L to 5L)))
    }
}
