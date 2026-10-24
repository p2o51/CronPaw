package com.gpo.cronpaw.core.agenda

import android.content.Context
import com.gpo.cronpaw.core.notion.NotionClient
import com.gpo.cronpaw.core.notion.NotionRepo
import com.gpo.cronpaw.core.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var calItems: List<AgendaItem> = emptyList()
    private var notionItems: List<AgendaItem> = emptyList()
    private var notionJob: Job? = null

    /** Optimistic local edits not yet confirmed by a reload: key → replacement (null = removed). */
    private val overlay = mutableMapOf<String, AgendaItem?>()

    /** Safe to call again (the widget and its jobs may start the process). */
    fun init(context: Context) {
        if (::app.isInitialized) return
        app = context.applicationContext
        val cached = runCatching {
            NotionClient.json.decodeFromString(cacheSerializer, cacheFile().readText())
        }.getOrNull()
        if (cached != null) _items.value = cached
        scope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            CalendarRepo.changes(app).debounce(300).collect { reloadCalendars() }
        }
    }

    private fun cacheFile() = File(app.filesDir, "agenda_cache.json")

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
        val cur = window
        if (!force && cur != null && !from.isBefore(cur.first) && !to.isAfter(cur.second)) return
        window = from.minusDays(7) to to.plusDays(14)
        refresh()
    }

    fun refresh() {
        scope.launch { reloadCalendars() }
        reloadNotion()
    }

    private fun bounds(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val w = window ?: (LocalDate.now().minusDays(7) to LocalDate.now().plusDays(14))
        return w.first.atStartOfDay(zone).toInstant().toEpochMilli() to
            w.second.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    suspend fun reloadCalendars() {
        if (!::app.isInitialized) return
        val cals = CalendarRepo.calendars(app)
        _calendars.value = cals
        val (from, to) = bounds()
        calItems = CalendarRepo.instances(app, from, to, cals.associateBy { it.id }, hiddenCalendars + removedCalendars)
        overlay.keys.removeAll { it.startsWith("cal:") || it.startsWith("new:cal") }
        publish()
    }

    private fun reloadNotion() {
        notionJob?.cancel()
        notionJob = scope.launch {
            _loading.value = true
            val (from, to) = bounds()
            notionItems = NotionRepo.items(from, to)
            overlay.keys.removeAll { it.startsWith("notion:") || it.startsWith("new:notion") }
            publish()
            _loading.value = false
            val counts = mutableMapOf<String, Int>()
            for (db in NotionRepo.databases.value.filter { it.enabled }) counts[db.id] = NotionRepo.unscheduledCount(db)
            _unscheduled.value = counts
        }
    }

    private fun publish() {
        val enabledDbs = NotionRepo.databases.value.filter { it.enabled }.map { it.id }.toSet()
        val notion = notionItems.filter { (it.source as Source.Notion).databaseId in enabledDbs }
        var all = (calItems + notion).associateBy { it.key }.toMutableMap()
        for ((k, v) in overlay) if (v == null) all.remove(k) else all[k] = v
        _items.value = all.values.sortedWith(compareBy({ it.start }, { -it.durationMinutes }))
        val snapshot = _items.value
        scope.launch(Dispatchers.IO) {
            runCatching { cacheFile().writeText(NotionClient.json.encodeToString(cacheSerializer, snapshot)) }
        }
        AgendaEvents.changed()
    }

    /** Shows [item] at once (a new item, a moved one, a ticked todo) before the source confirms. */
    fun applyLocal(item: AgendaItem) {
        overlay[item.key] = item
        publish()
    }

    fun removeLocal(key: String) {
        overlay[key] = null
        publish()
    }

    fun forget(key: String) {
        overlay.remove(key)
        publish()
    }

    suspend fun afterWrite() = withContext(Dispatchers.Main) {
        reloadCalendars()
        reloadNotion()
    }
}

/** A tiny bus so the widget re-renders when the agenda changes. */
object AgendaEvents {
    val version = MutableStateFlow(0)
    fun changed() {
        version.value++
    }
}
