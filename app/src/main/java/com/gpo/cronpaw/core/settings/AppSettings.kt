package com.gpo.cronpaw.core.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Observable app settings (SharedPreferences-backed). Read with
 * `AppSettings.themeMode.state.collectAsState()`, write with `AppSettings.set(AppSettings.themeMode, v)`.
 * Feature code declares its own keys in its package with [register] instead of editing this file.
 */
object AppSettings {
    private lateinit var prefs: SharedPreferences

    class Key<T>(val name: String, val default: T) {
        internal val flow = MutableStateFlow(default)
        val state: StateFlow<T> get() = flow.asStateFlow()
        val value: T get() = flow.value
    }

    private val keys = mutableListOf<Key<*>>()
    private fun <T> key(name: String, default: T): Key<T> = Key(name, default).also { keys += it }

    /**
     * Feature-owned settings: declare them in YOUR package —
     * `val MyKey = AppSettings.register("feature_x", true)`. Loaded on first access.
     */
    fun <T> register(name: String, default: T): Key<T> = Key(name, default).also {
        keys += it
        if (::prefs.isInitialized) load(it)
    }

    // ── Appearance ─────────────────────────────────────────────────────────
    /** 0 = follow system, 1 = light, 2 = dark */
    val themeMode = key("theme_mode", 0)
    val dynamicColor = key("dynamic_color", true)
    /** ARGB 主色 seed when dynamic color is off: a warm ember. */
    val seedColor = key("seed_color", 0xFFE0703A.toInt())
    val coverColor = key("cover_color", true)

    // ── Calendar ───────────────────────────────────────────────────────────
    /** The view: 1, 3 or 7 days on the timeline (周 starts on Monday), [ViewMonth] or [ViewSchedule]. */
    val viewDays = key("view_days", 3)
    /** Set once a view is picked in the drawer; until then wide screens open on 周. */
    val viewChosen = key("view_chosen", false)
    const val ViewMonth = -1
    const val ViewSchedule = -2
    /** The last timeline length, for coming back from 月 / 日程. */
    val timelineDays = key("timeline_days", 3)
    /** Height of an hour on the timeline, in dp (pinch to change). */
    val hourHeight = key("hour_height", 60)
    /** Calendar ids the user hid in the drawer, comma separated. */
    val hiddenCalendars = key("hidden_calendars", "")
    /** Default target for new items: "cal:<id>" or "notion:<databaseId>". */
    val defaultTarget = key("default_target", "")
    /** Pinned item keys (AgendaItem.key), newline separated. */
    val pins = key("pins", "")

    // ── Notion (OAuth through the relay Worker) ────────────────────────────
    /** The access token. */
    val notionToken = key("notion_token", "")
    /** Notion rotates it on every refresh. */
    val notionRefreshToken = key("notion_refresh_token", "")
    val notionWorkspace = key("notion_workspace", "")
    /** The state of a sign-in in flight, checked on the callback. */
    val notionOAuthState = key("notion_oauth_state", "")
    /** Configured databases, JSON (core/notion/NotionDatabases). */
    val notionDatabases = key("notion_databases", "[]")

    // ── Voice ──────────────────────────────────────────────────────────────
    /** ElevenLabs API key (BYOK). Empty = system speech recognition. */
    val elevenLabsKey = key("elevenlabs_key", "")
    val scribeModel = key("scribe_model", "scribe_v2")
    /** 快捷入口 on the voice sheet: "title|minutes" lines. */
    val quickEntries = key("quick_entries", "简单专注|25\n简单工作|60\n午饭|45\n运动|30")
    /** Length of an item made by tapping the timeline, in minutes. */
    val defaultMinutes = key("default_minutes", 60)
    /** Seconds before a confirmed item saves itself when the screen is left alone. */
    val autoSaveSeconds = key("auto_save_seconds", 4)

    fun init(context: Context) {
        prefs = context.getSharedPreferences("cronpaw_settings", Context.MODE_PRIVATE)
        keys.forEach { load(it) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> load(k: Key<T>) {
        val v: Any? = when (val d = k.default) {
            is Boolean -> prefs.getBoolean(k.name, d)
            is Int -> prefs.getInt(k.name, d)
            is Long -> prefs.getLong(k.name, d)
            is Float -> prefs.getFloat(k.name, d)
            is String -> prefs.getString(k.name, d)
            else -> d
        }
        k.flow.value = v as T
    }

    fun <T> set(k: Key<T>, value: T) {
        k.flow.value = value
        prefs.edit().apply {
            when (value) {
                is Boolean -> putBoolean(k.name, value)
                is Int -> putInt(k.name, value)
                is Long -> putLong(k.name, value)
                is Float -> putFloat(k.name, value)
                is String -> putString(k.name, value)
            }
        }.apply()
    }
}
