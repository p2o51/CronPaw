package com.gpo.cronpaw.core.agenda

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * The device's calendars through [CalendarContract]: every Google account signed in on the
 * phone is already here and synced by the system, so there is no OAuth at all.
 */
object CalendarRepo {

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun canWrite(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun calendars(context: Context): List<CalendarInfo> = readProvider { signal ->
        if (!hasPermission(context)) return@readProvider emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        val out = mutableListOf<CalendarInfo>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI, projection,
                "${CalendarContract.Calendars.SYNC_EVENTS} = 1", null,
                "${CalendarContract.Calendars.ACCOUNT_NAME} ASC, ${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
                signal,
            )?.use { c ->
                while (c.moveToNext()) {
                    out += CalendarInfo(
                        id = c.getLong(0),
                        accountName = c.getString(1).orEmpty(),
                        accountType = c.getString(2).orEmpty(),
                        name = c.getString(3).orEmpty(),
                        color = c.getInt(4),
                        writable = c.getInt(5) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                    )
                }
            }
        }
        out
    }

    /** Instances overlapping [from, to) on [calendarIds] (all visible calendars when null). */
    suspend fun instances(
        context: Context,
        from: Long,
        to: Long,
        calendars: Map<Long, CalendarInfo>,
        hidden: Set<Long>,
    ): List<AgendaItem> = readProvider { signal ->
        if (!hasPermission(context)) return@readProvider emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val zone = ZoneId.systemDefault()
        val out = mutableListOf<AgendaItem>()
        runCatching {
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC", signal)?.use { c ->
                while (c.moveToNext()) {
                    signal.throwIfCanceled()
                    val calId = c.getLong(6)
                    if (calId in hidden || calId !in calendars) continue
                    val allDay = c.getInt(4) == 1
                    var begin = c.getLong(1)
                    var end = c.getLong(2)
                    if (allDay) {
                        // All-day instances are stored as UTC midnights: move them onto local days.
                        begin = utcMidnightToLocal(begin, zone)
                        end = utcMidnightToLocal(end, zone)
                    }
                    val eventId = c.getLong(0)
                    out += AgendaItem(
                        key = "cal:$eventId:${c.getLong(1)}",
                        title = c.getString(3).orEmpty().ifBlank { "（无标题）" },
                        start = begin,
                        end = end.coerceAtLeast(begin),
                        allDay = allDay,
                        color = c.getInt(5),
                        source = Source.Calendar(calId, eventId),
                        location = c.getString(7)?.takeIf { it.isNotBlank() },
                    )
                }
            }
        }
        out
    }

    private suspend fun <T> readProvider(read: (CancellationSignal) -> T): T = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            continuation.resumeWith(runCatching { read(signal) })
        }
    }

    private fun utcMidnightToLocal(utc: Long, zone: ZoneId): Long {
        val day = Instant.ofEpochMilli(utc).atOffset(ZoneOffset.UTC).toLocalDate()
        return day.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** Inserts an event and returns its id. */
    suspend fun insert(
        context: Context,
        calendarId: Long,
        title: String,
        start: Long,
        end: Long,
        allDay: Boolean,
        description: String? = null,
        extras: EventExtras = EventExtras(),
    ): Long? = withContext(Dispatchers.IO) {
        if (!canWrite(context)) return@withContext null
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            (extras.notes.ifBlank { null } ?: description)?.let { put(CalendarContract.Events.DESCRIPTION, it) }
            extras.location.ifBlank { null }?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
            extras.colorKey?.let { put(CalendarContract.Events.EVENT_COLOR_KEY, it) }
            val (dtStart, dtEnd) = if (allDay) {
                val zone = ZoneId.systemDefault()
                val day = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
                val last = Instant.ofEpochMilli(end - 1).atZone(zone).toLocalDate()
                put(CalendarContract.Events.ALL_DAY, 1)
                put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
                utcStart(day) to utcStart(last.plusDays(1))
            } else {
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                start to end
            }
            put(CalendarContract.Events.DTSTART, dtStart)
            // A repeating event has a length instead of an end.
            if (extras.rrule != null) {
                put(CalendarContract.Events.RRULE, extras.rrule)
                put(CalendarContract.Events.DURATION, if (allDay) "P${(dtEnd - dtStart) / 86_400_000L}D" else "P${(dtEnd - dtStart) / 1000}S")
            } else {
                put(CalendarContract.Events.DTEND, dtEnd)
            }
            if (extras.reminders.isNotEmpty()) put(CalendarContract.Events.HAS_ALARM, 1)
        }
        runCatching {
            val id = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)?.let { ContentUris.parseId(it) }
            if (id != null) for (m in extras.reminders) {
                context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, id)
                    put(CalendarContract.Reminders.MINUTES, m)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                })
            }
            id
        }.getOrNull()
    }

    /** What a new calendar event can carry beyond title and time. */
    data class EventExtras(
        val location: String = "",
        val notes: String = "",
        /** Minutes before the start. */
        val reminders: List<Int> = emptyList(),
        /** "FREQ=DAILY", "FREQ=WEEKLY;BYDAY=MO,…" etc.; null = once. */
        val rrule: String? = null,
        /** A key from the calendar account's event colours ([eventColors]); null = the calendar's. */
        val colorKey: String? = null,
    )

    /** The event colours the account offers (Google calendars have 11), as key → ARGB. */
    suspend fun eventColors(context: Context, calendarId: Long): List<Pair<String, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val (account, type) = context.contentResolver.query(
                ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId),
                arrayOf(CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE),
                null, null, null,
            )?.use { c -> if (c.moveToFirst()) c.getString(0) to c.getString(1) else null } ?: return@runCatching emptyList()
            val out = mutableListOf<Pair<String, Int>>()
            context.contentResolver.query(
                CalendarContract.Colors.CONTENT_URI,
                arrayOf(CalendarContract.Colors.COLOR_KEY, CalendarContract.Colors.COLOR),
                "${CalendarContract.Colors.ACCOUNT_NAME}=? AND ${CalendarContract.Colors.ACCOUNT_TYPE}=? AND ${CalendarContract.Colors.COLOR_TYPE}=?",
                arrayOf(account, type, CalendarContract.Colors.TYPE_EVENT.toString()),
                null,
            )?.use { c -> while (c.moveToNext()) out += c.getString(0) to c.getInt(1) }
            out
        }.getOrDefault(emptyList())
    }

    private fun utcStart(day: LocalDate): Long = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    suspend fun updateTimes(context: Context, eventId: Long, start: Long, end: Long): Boolean = withContext(Dispatchers.IO) {
        if (!canWrite(context)) return@withContext false
        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, end)
        }
        runCatching {
            context.contentResolver.update(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), values, null, null,
            ) > 0
        }.getOrDefault(false)
    }

    suspend fun setTitle(context: Context, eventId: Long, title: String): Boolean = withContext(Dispatchers.IO) {
        if (!canWrite(context)) return@withContext false
        val values = ContentValues().apply { put(CalendarContract.Events.TITLE, title) }
        runCatching {
            context.contentResolver.update(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), values, null, null,
            ) > 0
        }.getOrDefault(false)
    }

    suspend fun delete(context: Context, eventId: Long): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.delete(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null,
            ) > 0
        }.getOrDefault(false)
    }

    /** Emits once whenever the provider changes (a sync landed, another app edited an event…). */
    fun changes(context: Context): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        runCatching {
            context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        }
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }
}
