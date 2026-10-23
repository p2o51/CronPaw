package com.gpo.cronpaw.core.notion

import com.gpo.cronpaw.core.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class NotionException(message: String, val status: Int = 0) : Exception(message)

/**
 * The Notion REST API with the user's own internal-integration token (设置 › Notion).
 * Databases must be shared with that integration in Notion first.
 */
object NotionClient {
    private const val Base = "https://api.notion.com/v1"
    private const val Version = "2022-06-28"
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val JsonType = "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val hasToken: Boolean get() = AppSettings.notionToken.value.isNotBlank()

    private suspend fun call(method: String, path: String, body: JsonElement? = null): JsonObject = withContext(Dispatchers.IO) {
        val token = AppSettings.notionToken.value.trim()
        if (token.isEmpty()) throw NotionException("no token")
        val req = Request.Builder()
            .url("$Base$path")
            .header("Authorization", "Bearer $token")
            .header("Notion-Version", Version)
            .method(method, body?.toString()?.toRequestBody(JsonType) ?: if (method == "GET") null else "{}".toRequestBody(JsonType))
            .build()
        http.newCall(req).execute().use { res ->
            val text = res.body.string()
            val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            if (!res.isSuccessful || obj == null) {
                val msg = obj?.get("message")?.jsonPrimitive?.contentOrNull ?: "HTTP ${res.code}"
                throw NotionException(msg, res.code)
            }
            obj
        }
    }

    /** Databases the integration can see. */
    suspend fun searchDatabases(): List<DatabaseSummary> {
        val res = call("POST", "/search", buildJsonObject {
            put("filter", buildJsonObject { put("property", "object"); put("value", "database") })
            put("page_size", 100)
        })
        return res["results"]?.jsonArray.orEmpty().map { it.jsonObject }.map { db ->
            DatabaseSummary(
                id = db.str("id"),
                title = plainText(db["title"]).ifBlank { "未命名" },
                icon = db["icon"]?.let { (it as? JsonObject)?.str("emoji") }?.takeIf { it.isNotEmpty() },
            )
        }
    }

    suspend fun schema(databaseId: String): DatabaseSchema {
        val db = call("GET", "/databases/$databaseId")
        val props = db["properties"]?.jsonObject.orEmpty().map { (name, v) ->
            val o = v.jsonObject
            val type = o.str("type")
            val options = when (type) {
                "status", "select", "multi_select" ->
                    o[type]?.jsonObject?.get("options")?.jsonArray.orEmpty().map { opt ->
                        val oo = opt.jsonObject
                        PropOption(oo.str("name"), oo.str("color"))
                    }
                else -> emptyList()
            }
            PropSchema(name, type, options)
        }
        return DatabaseSchema(databaseId, plainText(db["title"]).ifBlank { "未命名" }, props)
    }

    /** Pages whose [dateProp] falls in [fromIso, toIso). */
    suspend fun query(databaseId: String, dateProp: String, fromIso: String, toIso: String): List<JsonObject> {
        val filter = buildJsonObject {
            put("and", buildJsonArray {
                add(buildJsonObject { put("property", dateProp); put("date", buildJsonObject { put("on_or_after", fromIso) }) })
                add(buildJsonObject { put("property", dateProp); put("date", buildJsonObject { put("before", toIso) }) })
            })
        }
        return queryAll(databaseId, filter)
    }

    /** Pages with an empty [dateProp] (未排期), not done. */
    suspend fun unscheduled(databaseId: String, dateProp: String): List<JsonObject> {
        val filter = buildJsonObject { put("property", dateProp); put("date", buildJsonObject { put("is_empty", true) }) }
        return queryAll(databaseId, filter, max = 100)
    }

    private suspend fun queryAll(databaseId: String, filter: JsonObject, max: Int = 300): List<JsonObject> {
        val out = mutableListOf<JsonObject>()
        var cursor: String? = null
        do {
            val res = call("POST", "/databases/$databaseId/query", buildJsonObject {
                put("filter", filter)
                put("page_size", 100)
                cursor?.let { put("start_cursor", it) }
            })
            out += res["results"]?.jsonArray.orEmpty().map { it.jsonObject }
            cursor = res["next_cursor"]?.jsonPrimitive?.contentOrNull
        } while (cursor != null && res["has_more"]?.jsonPrimitive?.contentOrNull == "true" && out.size < max)
        return out
    }

    suspend fun updatePage(pageId: String, properties: JsonObject): JsonObject =
        call("PATCH", "/pages/$pageId", buildJsonObject { put("properties", properties) })

    suspend fun archivePage(pageId: String): JsonObject =
        call("PATCH", "/pages/$pageId", buildJsonObject { put("archived", true) })

    suspend fun createPage(databaseId: String, properties: JsonObject): JsonObject =
        call("POST", "/pages", buildJsonObject {
            put("parent", buildJsonObject { put("database_id", databaseId) })
            put("properties", properties)
        })

    fun plainText(el: JsonElement?): String =
        (el as? JsonArray).orEmpty().joinToString("") { (it as? JsonObject)?.str("plain_text").orEmpty() }

    internal fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull.orEmpty()
}

data class DatabaseSummary(val id: String, val title: String, val icon: String?)

data class PropOption(val name: String, val color: String)

data class PropSchema(val name: String, val type: String, val options: List<PropOption>)

data class DatabaseSchema(val id: String, val title: String, val props: List<PropSchema>) {
    val titleProp: String? get() = props.firstOrNull { it.type == "title" }?.name
    val dateProps: List<PropSchema> get() = props.filter { it.type == "date" }
    val statusProps: List<PropSchema> get() = props.filter { it.type == "status" || it.type == "select" || it.type == "checkbox" }
}
