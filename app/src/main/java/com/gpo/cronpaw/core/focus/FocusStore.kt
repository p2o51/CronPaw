package com.gpo.cronpaw.core.focus

import android.content.Context
import android.content.SharedPreferences
import com.gpo.cronpaw.core.notion.NotionClient
import com.gpo.cronpaw.core.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 设置 > 专注. */
object FocusSettings {
    val focusMin = AppSettings.register("focus_min", 25)
    val breakMin = AppSettings.register("focus_break_min", 5)
    val longBreakMin = AppSettings.register("focus_long_break_min", 15)
    /** One breath before a session starts (from Daylist's reading ritual). */
    val breath = AppSettings.register("focus_breath", true)
    /** Keep 专注 mode on through 番茄 breaks too. */
    val quietInBreaks = AppSettings.register("focus_quiet_in_breaks", false)
    /** The start sheet remembers 番茄 or 自由. */
    val lastMode = AppSettings.register("focus_last_mode", FocusMode.Pomodoro.name)
    val quiet = AppSettings.register("focus_quiet", true)
}

/**
 * The one running session, kept in its own preferences file so a receiver woken in a fresh
 * process (an alarm, a notification button, a reboot) reads it without starting the app.
 */
object FocusStore {
    private var prefs: SharedPreferences? = null
    private val _session = MutableStateFlow<FocusSession?>(null)
    val session: StateFlow<FocusSession?> = _session.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("cronpaw_focus", Context.MODE_PRIVATE)
        prefs = p
        _session.value = p.getString("session", null)?.let {
            runCatching { NotionClient.json.decodeFromString(FocusSession.serializer(), it) }.getOrNull()
        }
    }

    val value: FocusSession? get() = _session.value

    fun save(s: FocusSession?) {
        _session.value = s
        prefs?.edit()?.apply {
            if (s == null) remove("session") else putString("session", NotionClient.json.encodeToString(FocusSession.serializer(), s))
        }?.apply()
    }

    /** Small state the zen mode keeps between processes. */
    fun getString(key: String): String? = prefs?.getString(key, null)
    fun putString(key: String, value: String?) {
        prefs?.edit()?.apply { if (value == null) remove(key) else putString(key, value) }?.apply()
    }
    fun getInt(key: String, default: Int): Int = prefs?.getInt(key, default) ?: default
    fun putInt(key: String, value: Int) { prefs?.edit()?.putInt(key, value)?.apply() }
}
