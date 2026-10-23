package com.gpo.cronpaw.core.agenda

import android.content.Context
import com.gpo.cronpaw.core.notion.NotionRepo
import com.gpo.cronpaw.core.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where a new item goes: a system calendar or a Notion database. */
sealed interface Target {
    data class Calendar(val info: CalendarInfo) : Target
    data class Notion(val db: com.gpo.cronpaw.core.notion.NotionDb) : Target

    val label: String
        get() = when (this) {
            is Calendar -> info.name
            is Notion -> db.title
        }
    val color: Int
        get() = when (this) {
            is Calendar -> info.color
            is Notion -> db.color
        }
    val id: String
        get() = when (this) {
            is Calendar -> "cal:${info.id}"
            is Notion -> "notion:${db.id}"
        }
}

/**
 * Every write, optimistic: the timeline changes first ([Agenda.applyLocal]), the source is
 * written, and a failure puts the old state back. Returns false on failure.
 */
object Actions {

    suspend fun setDone(item: AgendaItem, done: Boolean): Boolean {
        val src = item.source as? Source.Notion ?: return false
        val db = NotionRepo.db(src.databaseId) ?: return false
        Agenda.applyLocal(item.copy(done = done))
        return runCatching { NotionRepo.setDone(db, src.pageId, done) }
            .onFailure { Agenda.applyLocal(item) }
            .isSuccess
    }

    suspend fun move(context: Context, item: AgendaItem, start: Long, end: Long): Boolean {
        Agenda.applyLocal(item.copy(start = start, end = end))
        val ok = when (val src = item.source) {
            is Source.Calendar -> CalendarRepo.updateTimes(context, src.eventId, start, end)
            is Source.Notion -> {
                val db = NotionRepo.db(src.databaseId)
                db != null && runCatching { NotionRepo.setTimes(db, src.pageId, start, end, item.allDay) }.isSuccess
            }
        }
        if (!ok) Agenda.applyLocal(item)
        return ok
    }

    /** What [create] made, so 「编辑」 on the snackbar can change it afterwards. */
    data class Created(val target: Target, val ref: String, val title: String, val start: Long, val end: Long, val allDay: Boolean)

    suspend fun create(context: Context, target: Target, title: String, start: Long, end: Long, allDay: Boolean = false): Created? {
        val tempKey = "new:${if (target is Target.Notion) "notion" else "cal"}:${System.nanoTime()}"
        val temp = AgendaItem(
            key = tempKey, title = title, start = start, end = end, allDay = allDay, color = target.color,
            source = when (target) {
                is Target.Calendar -> Source.Calendar(target.info.id, -1)
                is Target.Notion -> Source.Notion(target.db.id, "", "")
            },
            todo = target is Target.Notion && target.db.statusProp != null,
        )
        Agenda.applyLocal(temp)
        AppSettings.set(AppSettings.defaultTarget, target.id)
        val ref: String? = when (target) {
            is Target.Calendar -> CalendarRepo.insert(context, target.info.id, title, start, end, allDay)?.toString()
            is Target.Notion -> runCatching { NotionRepo.create(target.db, title, start, end, allDay) }.getOrNull()
        }
        if (ref == null) Agenda.removeLocal(tempKey)
        Agenda.afterWrite()
        return ref?.let { Created(target, it, title, start, end, allDay) }
    }

    /** Changes something [create] just made (the 「编辑」 after an auto-save). */
    suspend fun edit(context: Context, created: Created, title: String, start: Long, end: Long): Boolean {
        val ok = when (val t = created.target) {
            is Target.Calendar -> {
                val id = created.ref.toLong()
                (title == created.title || CalendarRepo.setTitle(context, id, title)) &&
                    ((start == created.start && end == created.end) || CalendarRepo.updateTimes(context, id, start, end))
            }
            is Target.Notion -> runCatching {
                if (title != created.title) NotionRepo.setTitle(t.db, created.ref, title)
                if (start != created.start || end != created.end) NotionRepo.setTimes(t.db, created.ref, start, end, created.allDay)
            }.isSuccess
        }
        Agenda.afterWrite()
        return ok
    }

    suspend fun delete(context: Context, item: AgendaItem): Boolean {
        Agenda.removeLocal(item.key)
        val ok = when (val src = item.source) {
            is Source.Calendar -> CalendarRepo.delete(context, src.eventId)
            is Source.Notion -> runCatching { NotionRepo.archive(src.pageId) }.isSuccess
        }
        if (!ok) Agenda.applyLocal(item)
        return ok
    }

    /** Every place a new item can go, the last used first. */
    fun targets(): List<Target> {
        val cals = Agenda.calendars.value.filter { it.writable && it.id !in Agenda.hiddenCalendars }.map { Target.Calendar(it) }
        val dbs = NotionRepo.databases.value.filter { it.enabled }.map { Target.Notion(it) }
        val all = dbs + cals
        val last = AppSettings.defaultTarget.value
        return all.sortedBy { if (it.id == last) 0 else 1 }
    }
}

/** 置顶: pinned items join 「下一个」 in the top card (at most three in all). */
object Pins {
    private val _keys = MutableStateFlow<List<String>>(emptyList())
    val keys: StateFlow<List<String>> = _keys.asStateFlow()

    fun init() {
        _keys.value = AppSettings.pins.value.split('\n').filter { it.isNotBlank() }
    }

    fun isPinned(key: String) = key in _keys.value

    fun toggle(key: String) {
        val cur = _keys.value
        _keys.value = if (key in cur) cur - key else cur + key
        AppSettings.set(AppSettings.pins, _keys.value.joinToString("\n"))
    }
}
