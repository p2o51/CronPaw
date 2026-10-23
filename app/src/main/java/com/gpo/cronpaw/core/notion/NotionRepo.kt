package com.gpo.cronpaw.core.notion

import com.gpo.cronpaw.core.agenda.AgendaItem
import com.gpo.cronpaw.core.agenda.Source
import com.gpo.cronpaw.core.notion.NotionClient.str
import com.gpo.cronpaw.core.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A Notion database the user added (添加数据库): which property is the date, which state
 * counts as done, its colour, and which properties the item sheet shows (the eyes).
 */
@Serializable
data class NotionDb(
    val id: String,
    val title: String,
    val color: Int,
    val titleProp: String,
    val dateProp: String,
    /** status / select / checkbox property that marks done; null = not a todo database. */
    val statusProp: String? = null,
    val statusType: String? = null,
    /** The option that means done (status / select). Checkbox: checked = done. */
    val doneValue: String? = null,
    /** The option a fresh item gets / an un-done item returns to. */
    val todoValue: String? = null,
    /** Properties shown in the item sheet. Time and status are on by default. */
    val visibleProps: List<String> = emptyList(),
    val enabled: Boolean = true,
)

/** A property value as the item sheet shows and edits it. */
data class PropValue(
    val name: String,
    val type: String,
    /** Display text; empty = not set. */
    val text: String,
    /** Option colour name (Notion palette) for select / status. */
    val color: String? = null,
    val options: List<PropOption> = emptyList(),
) {
    val editable: Boolean get() = type == "status" || type == "select" || type == "checkbox"
}

object NotionRepo {
    private val listSerializer = ListSerializer(NotionDb.serializer())

    private val _databases = MutableStateFlow<List<NotionDb>>(emptyList())
    val databases: StateFlow<List<NotionDb>> = _databases.asStateFlow()

    /** Schema per database id, loaded lazily (sheet, setup). */
    private val schemas = mutableMapOf<String, DatabaseSchema>()

    /** Raw pages by page id from the latest queries: the sheet reads properties from here. */
    private val pages = mutableMapOf<String, JsonObject>()

    fun init() {
        _databases.value = runCatching {
            NotionClient.json.decodeFromString(listSerializer, AppSettings.notionDatabases.value)
        }.getOrDefault(emptyList())
    }

    private fun save(list: List<NotionDb>) {
        _databases.value = list
        AppSettings.set(AppSettings.notionDatabases, NotionClient.json.encodeToString(listSerializer, list))
    }

    fun upsert(db: NotionDb) = save(_databases.value.filterNot { it.id == db.id } + db)

    fun remove(id: String) = save(_databases.value.filterNot { it.id == id })

    fun db(id: String): NotionDb? = _databases.value.firstOrNull { it.id == id }

    fun update(id: String, block: (NotionDb) -> NotionDb) {
        val cur = db(id) ?: return
        upsert(block(cur))
    }

    suspend fun schema(id: String): DatabaseSchema = schemas[id] ?: NotionClient.schema(id).also { schemas[id] = it }

    // ── Reading ──────────────────────────────────────────────────────────────

    suspend fun items(from: Long, to: Long): List<AgendaItem> {
        if (!NotionClient.hasToken) return emptyList()
        val zone = ZoneId.systemDefault()
        val fromIso = Instant.ofEpochMilli(from).atZone(zone).toLocalDate().toString()
        val toIso = Instant.ofEpochMilli(to).atZone(zone).toLocalDate().plusDays(1).toString()
        val out = mutableListOf<AgendaItem>()
        for (db in _databases.value.filter { it.enabled }) {
            val result = runCatching { NotionClient.query(db.id, db.dateProp, fromIso, toIso) }.getOrNull() ?: continue
            for (page in result) {
                pages[page.str("id")] = page
                toItem(db, page, zone)?.let { out += it }
            }
        }
        return out
    }

    suspend fun unscheduledCount(db: NotionDb): Int =
        runCatching { NotionClient.unscheduled(db.id, db.dateProp).count { !isDone(db, it) } }.getOrDefault(0)

    /** Pages with no date yet, not done: the 未排期 tray. Start and end are 0. */
    suspend fun unscheduledItems(db: NotionDb): List<AgendaItem> =
        runCatching { NotionClient.unscheduled(db.id, db.dateProp) }.getOrDefault(emptyList())
            .filter { !isDone(db, it) }
            .map { page ->
                val pageId = page.str("id")
                pages[pageId] = page
                val props = page["properties"]?.jsonObject
                AgendaItem(
                    key = "notion:$pageId",
                    title = NotionClient.plainText(props?.get(db.titleProp)?.jsonObject?.get("title")).ifBlank { "（无标题）" },
                    start = 0,
                    end = 0,
                    allDay = false,
                    color = db.color,
                    source = Source.Notion(db.id, pageId, page.str("url")),
                    todo = db.statusProp != null,
                )
            }

    private fun toItem(db: NotionDb, page: JsonObject, zone: ZoneId): AgendaItem? {
        val props = page["properties"]?.jsonObject ?: return null
        val date = props[db.dateProp]?.jsonObject?.get("date") as? JsonObject ?: return null
        val startRaw = date.str("start").ifEmpty { return null }
        val endRaw = date.str("end")
        val allDay = startRaw.length <= 10
        val start = parseTime(startRaw, zone)
        var end = if (endRaw.isNotEmpty()) parseTime(endRaw, zone) else start
        if (allDay) {
            end = LocalDate.parse((endRaw.ifEmpty { startRaw }).take(10)).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        } else if (end <= start) {
            end = start + 30 * 60_000L
        }
        val title = NotionClient.plainText(props[db.titleProp]?.jsonObject?.get("title")).ifBlank { "（无标题）" }
        val pageId = page.str("id")
        return AgendaItem(
            key = "notion:$pageId",
            title = title,
            start = start,
            end = end,
            allDay = allDay,
            color = db.color,
            source = Source.Notion(db.id, pageId, page.str("url")),
            todo = db.statusProp != null,
            done = isDone(db, page),
        )
    }

    private fun parseTime(raw: String, zone: ZoneId): Long =
        if (raw.length <= 10) LocalDate.parse(raw).atStartOfDay(zone).toInstant().toEpochMilli()
        else OffsetDateTime.parse(raw).toInstant().toEpochMilli()

    private fun isDone(db: NotionDb, page: JsonObject): Boolean {
        val prop = db.statusProp ?: return false
        val v = page["properties"]?.jsonObject?.get(prop)?.jsonObject ?: return false
        return when (db.statusType) {
            "checkbox" -> (v["checkbox"] as? JsonPrimitive)?.booleanOrNull == true
            "status", "select" -> (v[db.statusType] as? JsonObject)?.str("name") == db.doneValue
            else -> false
        }
    }

    /** Every property of a cached page, in schema order, for the sheet and its eye menu. */
    suspend fun properties(db: NotionDb, pageId: String): List<PropValue> {
        val schema = runCatching { schema(db.id) }.getOrNull()
        val props = pages[pageId]?.get("properties")?.jsonObject ?: return emptyList()
        val order = schema?.props?.map { it.name } ?: props.keys.toList()
        return order.mapNotNull { name ->
            val v = props[name]?.jsonObject ?: return@mapNotNull null
            val type = v.str("type")
            if (type == "title") return@mapNotNull null
            val opts = schema?.props?.firstOrNull { it.name == name }?.options.orEmpty()
            when (type) {
                "status", "select" -> {
                    val o = v[type] as? JsonObject
                    PropValue(name, type, o?.str("name").orEmpty(), o?.str("color"), opts)
                }
                "multi_select" -> PropValue(name, type, (v[type] as? JsonArray).orEmpty().joinToString("  ") { (it as JsonObject).str("name") })
                "checkbox" -> PropValue(name, type, if ((v["checkbox"] as? JsonPrimitive)?.booleanOrNull == true) "是" else "否")
                "date" -> PropValue(name, type, (v["date"] as? JsonObject)?.let { d -> listOf(d.str("start"), d.str("end")).filter { it.isNotEmpty() }.joinToString("  ") }.orEmpty())
                "people" -> PropValue(name, type, (v["people"] as? JsonArray).orEmpty().joinToString("  ") { (it as JsonObject).str("name") })
                "rich_text" -> PropValue(name, type, NotionClient.plainText(v["rich_text"]))
                "number" -> PropValue(name, type, (v["number"] as? JsonPrimitive)?.content?.takeIf { it != "null" }.orEmpty())
                "url", "email", "phone_number" -> PropValue(name, type, (v[type] as? JsonPrimitive)?.content?.takeIf { it != "null" }.orEmpty())
                else -> PropValue(name, type, "")
            }
        }
    }

    // ── Writing ──────────────────────────────────────────────────────────────

    suspend fun setOption(pageId: String, prop: String, type: String, option: String?) {
        val body = buildJsonObject {
            put(prop, buildJsonObject {
                when (type) {
                    "checkbox" -> put("checkbox", option == "是")
                    else -> put(type, if (option == null) JsonNull else buildJsonObject { put("name", option) })
                }
            })
        }
        val page = NotionClient.updatePage(pageId, body)
        pages[pageId] = page
    }

    suspend fun setDone(db: NotionDb, pageId: String, done: Boolean) {
        val prop = db.statusProp ?: return
        when (db.statusType) {
            "checkbox" -> setOption(pageId, prop, "checkbox", if (done) "是" else "否")
            else -> setOption(pageId, prop, db.statusType ?: "status", if (done) db.doneValue else db.todoValue)
        }
    }

    suspend fun setTimes(db: NotionDb, pageId: String, start: Long, end: Long, allDay: Boolean) {
        val page = NotionClient.updatePage(pageId, buildJsonObject {
            put(db.dateProp, buildJsonObject { put("date", dateJson(start, end, allDay)) })
        })
        pages[pageId] = page
    }

    /**
     * [extras]: property name → (type, values) chosen on the confirm sheet. Values are option
     * names, or page ids for a relation.
     */
    suspend fun create(
        db: NotionDb,
        title: String,
        start: Long,
        end: Long,
        allDay: Boolean,
        extras: Map<String, Pair<String, List<String>>> = emptyMap(),
    ): String {
        val page = NotionClient.createPage(db.id, buildJsonObject {
            put(db.titleProp, buildJsonObject {
                put("title", buildJsonArray { add(buildJsonObject { put("text", buildJsonObject { put("content", title) }) }) })
            })
            put(db.dateProp, buildJsonObject { put("date", dateJson(start, end, allDay)) })
            val status = db.statusProp
            val todo = db.todoValue
            if (status != null && todo != null && db.statusType != "checkbox") {
                put(status, buildJsonObject { put(db.statusType ?: "status", buildJsonObject { put("name", todo) }) })
            }
            for ((prop, choice) in extras) {
                val (type, values) = choice
                if (values.isEmpty()) continue
                put(prop, buildJsonObject {
                    when (type) {
                        "multi_select" -> put(type, buildJsonArray { values.forEach { v -> add(buildJsonObject { put("name", v) }) } })
                        "relation" -> put(type, buildJsonArray { values.forEach { v -> add(buildJsonObject { put("id", v) }) } })
                        else -> put(type, buildJsonObject { put("name", values.first()) })
                    }
                })
            }
        })
        val id = page.str("id")
        pages[id] = page
        return id
    }

    suspend fun setTitle(db: NotionDb, pageId: String, title: String) {
        val page = NotionClient.updatePage(pageId, buildJsonObject {
            put(db.titleProp, buildJsonObject {
                put("title", buildJsonArray { add(buildJsonObject { put("text", buildJsonObject { put("content", title) }) }) })
            })
        })
        pages[pageId] = page
    }

    /** Notion has no delete through the API: the page goes to the trash. */
    suspend fun archive(pageId: String) {
        NotionClient.archivePage(pageId)
        pages.remove(pageId)
    }

    private fun dateJson(start: Long, end: Long, allDay: Boolean) = buildJsonObject {
        val zone = ZoneId.systemDefault()
        if (allDay) {
            val s = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()
            val e = Instant.ofEpochMilli(end - 1).atZone(zone).toLocalDate()
            put("start", s.toString())
            if (e != s) put("end", e.toString())
        } else {
            val f = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            put("start", Instant.ofEpochMilli(start).atZone(zone).toOffsetDateTime().format(f))
            put("end", Instant.ofEpochMilli(end).atZone(zone).toOffsetDateTime().format(f))
        }
    }

    /** Notion's option colour names → a soft container and its ink. */
    fun optionColors(name: String?, dark: Boolean): Pair<Long, Long> = when (name) {
        "red" -> if (dark) 0xFF5C2B29 to 0xFFFFDAD6 else 0xFFFFDAD6 to 0xFF8C1D18
        "orange" -> if (dark) 0xFF5A3511 to 0xFFFFDCC2 else 0xFFFFDCC2 to 0xFF6E3900
        "yellow" -> if (dark) 0xFF4F3F00 to 0xFFFFE08A else 0xFFFFE08A to 0xFF4A3300
        "green" -> if (dark) 0xFF1E4D2E to 0xFFC9F0D8 else 0xFFC9F0D8 to 0xFF0B5129
        "blue" -> if (dark) 0xFF1C3D5E to 0xFFD1E4FF else 0xFFD1E4FF to 0xFF0B3B73
        "purple" -> if (dark) 0xFF3F2A6E to 0xFFE9DDFF else 0xFFE9DDFF to 0xFF3F2A86
        "pink" -> if (dark) 0xFF5C2244 to 0xFFFFD8EC else 0xFFFFD8EC to 0xFF7D2049
        "brown" -> if (dark) 0xFF4A3528 to 0xFFF2DFD1 else 0xFFF2DFD1 to 0xFF52372A
        else -> if (dark) 0xFF3A3640 to 0xFFE6E0E9 else 0xFFE6E0E9 to 0xFF1D1A24
    }
}

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
