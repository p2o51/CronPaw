package com.gpo.cronpaw.core.agenda

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Expand only the requested days. A long-running event still appears after its first 62 days. */
internal fun itemsByDay(
    items: List<AgendaItem>,
    from: LocalDate,
    to: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): Map<LocalDate, List<AgendaItem>> {
    if (to < from) return emptyMap()
    val out = HashMap<LocalDate, MutableList<AgendaItem>>()
    for (item in items) {
        var day = maxOf(Instant.ofEpochMilli(item.start).atZone(zone).toLocalDate(), from)
        val end = if (item.end > item.start) item.end - 1 else item.start
        val last = minOf(Instant.ofEpochMilli(end).atZone(zone).toLocalDate(), to)
        while (day <= last) {
            out.getOrPut(day) { mutableListOf() } += item
            day = day.plusDays(1)
        }
    }
    return out
}
