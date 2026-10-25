package com.gpo.cronpaw.core.agenda

import android.content.Context
import android.util.AtomicFile
import com.gpo.cronpaw.core.concurrent.LatestRefresh
import com.gpo.cronpaw.core.concurrent.LatestWriter
import com.gpo.cronpaw.core.notion.NotionDb
import com.gpo.cronpaw.core.notion.NotionClient
import com.gpo.cronpaw.core.notion.NotionRepo
import com.gpo.cronpaw.core.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Everything on the timeline, for a window of days around what is on screen.
 *
 * Cold start draws the cached items of the last session straight away (秒开); the
 * calendar provider (fast, local) and Notion (network) land on top. The provider is
 * observed, so a sync or another app's edit shows without a refresh.
 */
object Agenda {
    private lateinit var app: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cacheSerializer = ListSerializer(AgendaItem.serializer())

    private val _calendars = MutableStateFlow<List<CalendarInfo>>(emptyList())
    val calendars: StateFlow<List<CalendarInfo>> = _calendars.asStateFlow()

    private val _items = MutableStateFlow<List<AgendaItem>>(emptyList())
    val items: StateFlow<List<AgendaItem>> = _items.asStateFlow()

    private val _unscheduled = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unscheduled: StateFlow<Map<String, Int>> = _unscheduled.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** The loaded window, as local dates [from, to]. */
    private var window: Pair<LocalDate, LocalDate>? = null
    private var widgetWindow: Pair<LocalDate, LocalDate>? = null
    private var calItems: List<AgendaItem> = emptyList()
    private var notionItems: List<AgendaItem> = emptyList()
    private var cacheLoad: Job? = null
    private lateinit var cacheWriter: LatestWriter<List<AgendaItem>>
    private data class CalendarRead(val bounds: List<Pair<Long, Long>>, val hidden: Set<Long>)
    private data class NotionRead(val bounds: List<Pair<Long, Long>>, val databases: List<NotionDb>, val connected: Boolean)
    private val calendarRefresh = LatestRefresh<CalendarRead, Unit>(scope)
    private val notionRefresh = LatestRefresh<NotionRead, Unit>(scope)

    /** Optimistic local edits not yet confirmed by a reload: key → replacement (null = removed). */
    private val overlay = mutableMapOf<String, AgendaItem?>()
    private val overlaidAt = mutableMapOf<String, Long>()

    /** Safe to call again (the widget and its jobs may start the process). */
    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        cacheWriter = LatestWriter(CoroutineScope(SupervisorJob() + Dispatchers.IO)) { snapshot ->
            val bytes = NotionClient.json.encodeToString(cacheSerializer, snapshot).toByteArray()
            val file = cacheFile()
            val stream = file.startWrite()
            try {
                stream.write(bytes)
                file.finishWrite(stream)
            } catch (error: Exception) {
                file.failWrite(stream)
                throw error
            }
        }
        cacheLoad = scope.launch {
            val cached = withContext(Dispatchers.IO) {
                runCatching {
                    cacheFile().openRead().bufferedReader().use {
                        NotionClient.json.decodeFromString(cacheSerializer, it.readText())
                    }
                }.getOrNull()
            }
            if (cached != null) {
                calItems = cached.filter { it.source is Source.Calendar }
                notionItems = cached.filter { it.source is Source.Notion }
                if (overlay.isEmpty()) _items.value = cached else publish()
            }
        }
        scope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            CalendarRepo.changes(app).debounce(300).collect { reloadCalendars(force = true) }
        }
    }

    private fun cacheFile() = AtomicFile(File(app.filesDir, "agenda_cache.json"))

    suspend fun awaitReady() { cacheLoad?.join() }

    val hiddenCalendars: Set<Long>
        get() = AppSettings.hiddenCalendars.value.split(',').mapNotNull { it.toLongOrNull() }.toSet()

    val removedCalendars: Set<Long>
        get() = AppSettings.removedCalendars.value.split(',').mapNotNull { it.toLongOrNull() }.toSet()

    fun setCalendarRemoved(id: Long, removed: Boolean) {
        val set = removedCalendars.toMutableSet().apply { if (removed) add(id) else remove(id) }
        AppSettings.set(AppSettings.removedCalendars, set.joinToString(","))
        scope.launch { reloadCalendars() }
    }

    fun setCalendarHidden(id: Long, hidden: Boolean) {
        val set = hiddenCalendars.toMutableSet().apply { if (hidden) add(id) else remove(id) }
        AppSettings.set(AppSettings.hiddenCalendars, set.joinToString(","))
        scope.launch { reloadCalendars() }
    }

    /** Make sure [from]..[to] (local dates) is loaded, with a margin either side. */
    fun ensure(from: LocalDate, to: LocalDate, force: Boolean = false) {
        scope.launch {
            val cur = window
            if (!force && cur != null && !from.isBefore(cur.first) && !to.isAfter(cur.second)) return@launch
            window = from.minusDays(7) to to.plusDays(14)
            refresh()
        }
    }

    fun refresh(): Job = scope.launch { refreshAndAwait() }

    /** Workers wait for the actual reads, including slow Notion responses. */
    suspend fun refreshAndAwait() = withContext(Dispatchers.Main.immediate) {
        coroutineScope {
            val calendars = async { reloadCalendars() }
            val notion = reloadNotion()
            calendars.await()
            notionRefresh.awaitLatest(notion)
        }
    }

    /** The widget's current month must not replace the date the user is browsing. */
    fun ensureWidgetWindow(from: LocalDate, to: LocalDate) {
        scope.launch {
            val next = from to to
            if (widgetWindow == next) return@launch
            widgetWindow = next
            refresh()
        }
    }

    fun clearWidgetWindow() {
        scope.launch { widgetWindow = null }
    }

    private fun bounds(): List<Pair<Long, Long>> {
        val zone = ZoneId.systemDefault()
        val primary = window ?: (LocalDate.now().minusDays(7) to LocalDate.now().plusDays(14))
        return mergeReadWindows(listOfNotNull(primary, widgetWindow).map { w ->
            w.first.atStartOfDay(zone).toInstant().toEpochMilli() to
                w.second.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        })
    }

    suspend fun reloadCalendars(force: Boolean = false) = withContext(Dispatchers.Main.immediate) {
        if (!::app.isInitialized) return@withContext
        val request = CalendarRead(bounds(), hiddenCalendars + removedCalendars)
        calendarRefresh.awaitLatest(calendarRefresh.request(request, force) {
            awaitReady()
            val cals = CalendarRepo.calendars(app)
            val byId = cals.associateBy { it.id }
            val loaded = request.bounds.flatMap { (from, to) ->
                CalendarRepo.instances(app, from, to, byId, request.hidden)
            }
            currentCoroutineContext().ensureActive()
            _calendars.value = cals
            calItems = loaded
            clearOverlay { it.startsWith("cal:") || it.startsWith("new:cal") }
            publish()
        })
    }

    private fun reloadNotion(force: Boolean = false) = notionRefresh.request(
        NotionRead(bounds(), NotionRepo.databases.value, NotionClient.hasToken), force,
    ) {
        _loading.value = true
        try {
            awaitReady()
            val ranges = bounds()
            notionItems = ranges.flatMap { (from, to) -> NotionRepo.items(from, to) }
            currentCoroutineContext().ensureActive()
            // A write still in flight isn't in the query yet: keep its local copy a little longer.
            val now = System.currentTimeMillis()
            clearOverlay { it.startsWith("new:notion") || it.startsWith("notion:") && now - (overlaidAt[it] ?: 0) > 15_000 }
            publish()
            val counts = mutableMapOf<String, Int>()
            for (db in NotionRepo.databases.value.filter { it.enabled }) counts[db.id] = NotionRepo.unscheduledCount(db)
            _unscheduled.value = counts
        } finally {
            // A cancelled older read must not hide its replacement's loading indicator.
            if (currentCoroutineContext().isActive) _loading.value = false
        }
    }

    private fun clearOverlay(predicate: (String) -> Boolean) {
        val keys = overlay.keys.filter(predicate)
        for (key in keys) {
            overlay.remove(key)
            overlaidAt.remove(key)
        }
    }

    private fun publish() {
        val enabledDbs = NotionRepo.databases.value.filter { it.enabled }.map { it.id }.toSet()
        val notion = notionItems.filter { (it.source as Source.Notion).databaseId in enabledDbs }
        val all = (calItems + notion).associateBy { it.key }.toMutableMap()
        for ((k, v) in overlay) if (v == null) all.remove(k) else all[k] = v
        val snapshot = all.values.sortedWith(compareBy({ it.start }, { -it.durationMinutes }))
        if (snapshot == _items.value) return
        _items.value = snapshot
        cacheWriter.submit(snapshot)
        AgendaEvents.changed()
    }

    /** Shows [item] at once (a new item, a moved one, a ticked todo) before the source confirms. */
    fun applyLocal(item: AgendaItem) {
        overlay[item.key] = item
        overlaidAt[item.key] = System.currentTimeMillis()
        publish()
    }

    fun removeLocal(key: String) {
        overlay[key] = null
        overlaidAt[key] = System.currentTimeMillis()
        publish()
    }

    fun forget(key: String) {
        overlay.remove(key)
        overlaidAt.remove(key)
        publish()
    }

    suspend fun afterWrite() = withContext(Dispatchers.Main) {
        reloadCalendars(force = true)
        reloadNotion(force = true)
        Unit
    }
}

/** A tiny bus so the widget re-renders when the agenda changes. */
object AgendaEvents {
    val version = MutableStateFlow(0)
    fun changed() {
        version.value++
    }
}
