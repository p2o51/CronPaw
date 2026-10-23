package com.gpo.cronpaw.core.debug

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.Immutable
import com.gpo.cronpaw.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The last crash, kept on the device so it can be read on a phone that is not on adb
 * (设置 › 关于 › 上次闪退 shares it). Stack trace and versions only; no account data.
 */
object CrashLog {
    @Immutable
    data class Entry(val at: Long, val text: String)

    private lateinit var prefs: SharedPreferences
    private val _last = MutableStateFlow<Entry?>(null)
    val last: StateFlow<Entry?> = _last.asStateFlow()

    fun install(context: Context) {
        prefs = context.getSharedPreferences("cronpaw_crash", Context.MODE_PRIVATE)
        val text = prefs.getString(KEY_TEXT, null)
        if (text != null) _last.value = Entry(prefs.getLong(KEY_AT, 0), text)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val report = buildString {
                    append("CronPaw ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
                    append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append(", Android ").append(Build.VERSION.RELEASE)
                    append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
                    append("thread ").append(thread.name).append('\n')
                    append(error.stackTraceToString().take(12_000))
                }
                // commit, not apply: the process is about to die.
                prefs.edit().putString(KEY_TEXT, report).putLong(KEY_AT, System.currentTimeMillis()).commit()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
        _last.value = null
    }

    private const val KEY_TEXT = "text"
    private const val KEY_AT = "at"
}
