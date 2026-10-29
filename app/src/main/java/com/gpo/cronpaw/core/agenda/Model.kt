package com.gpo.cronpaw.core.agenda

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** One system calendar (CalendarContract.Calendars), grouped by account in the drawer. */
@Immutable
data class CalendarInfo(
    val id: Long,
    val accountName: String,
    val accountType: String,
    val name: String,
    val color: Int,
    val writable: Boolean,
)

/** Where an [AgendaItem] lives. */
@Serializable
sealed interface Source {
    @Serializable
    data class Calendar(val calendarId: Long, val eventId: Long) : Source

    @Serializable
    data class Notion(val databaseId: String, val pageId: String, val url: String) : Source
}

/**
 * Anything on the timeline: a system calendar instance or a Notion page with a date.
 * Times are epoch millis in the device zone; an all-day item spans whole local days
 * ([end] is the start of the day after its last day).
 */
@Immutable
@Serializable
data class AgendaItem(
    val key: String,
    val title: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val source: Source,
    /** A Notion item whose database has a done state: drawn with a circle. */
    val todo: Boolean = false,
    val done: Boolean = false,
    val location: String? = null,
    /** A video-call link from the location or the notes (core/agenda/Meetings). */
    val meeting: String? = null,
) {
    val isNotion: Boolean get() = source is Source.Notion
    val durationMinutes: Int get() = ((end - start) / 60_000L).toInt()
}
