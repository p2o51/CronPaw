package com.gpo.cronpaw.core.notion

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.gpo.cronpaw.BuildConfig
import com.gpo.cronpaw.MainActivity
import com.gpo.cronpaw.core.agenda.Agenda
import com.gpo.cronpaw.core.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * 连接 Notion: OAuth through the relay Worker (notion-oauth-relay, app "cronpaw"), which
 * keeps the public integration's secret. The browser opens `relay/start`, Notion sends the
 * user back to `cronpaw://notion-oauth` ([NotionOAuthActivity]), and the code is exchanged
 * at `relay/token`. Access tokens are refreshed at `relay/refresh` on a 401.
 */
object NotionOAuth {
    const val Scheme = "cronpaw"
    const val Host = "notion-oauth"
    private val relay = BuildConfig.NOTION_OAUTH_RELAY

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val refreshLock = Mutex()

    /** null = idle; a message after a failed sign-in. */
    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    val connected: Boolean get() = AppSettings.notionToken.value.isNotBlank()

    fun begin(): Uri {
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val state = bytes.joinToString("") { "%02x".format(it) }
        AppSettings.set(AppSettings.notionOAuthState, state)
        _status.value = null
        return Uri.parse("$relay/start").buildUpon().appendQueryParameter("state", state).build()
    }

    fun onCallback(uri: Uri) {
        val expected = AppSettings.notionOAuthState.value
        val state = uri.getQueryParameter("state")
        // Not ours, or already used: ignore it.
        if (expected.isEmpty() || state != expected) return
        AppSettings.set(AppSettings.notionOAuthState, "")
        val error = uri.getQueryParameter("error")
        if (error != null) {
            if (error != "access_denied") _status.value = "连接失败：$error"
            return
        }
        val code = uri.getQueryParameter("code") ?: return
        scope.launch {
            runCatching { post("token", "code", code) }
                .onSuccess { tokens ->
                    save(tokens)
                    AppSettings.set(AppSettings.notionWorkspace, tokens.workspace.orEmpty())
                    _status.value = null
                    Agenda.refresh()
                }
                .onFailure { _status.value = "连接失败：${it.message}" }
        }
    }

    fun disconnect() {
        AppSettings.set(AppSettings.notionToken, "")
        AppSettings.set(AppSettings.notionRefreshToken, "")
        AppSettings.set(AppSettings.notionWorkspace, "")
    }

    /**
     * A new access token after [stale] got a 401, or null when the user must sign in again.
     * One at a time: Notion rotates the refresh token, so a second refresh with the old one
     * would revoke the grant.
     */
    suspend fun refreshAfter(stale: String): String? = refreshLock.withLock {
        val current = AppSettings.notionToken.value
        if (current.isNotEmpty() && current != stale) return@withLock current
        val rt = AppSettings.notionRefreshToken.value
        if (rt.isEmpty()) return@withLock null
        runCatching { post("refresh", "refresh_token", rt) }
            .onSuccess { save(it) }
            .getOrNull()?.access
    }

    private fun save(t: Tokens) {
        AppSettings.set(AppSettings.notionToken, t.access)
        t.refresh?.let { AppSettings.set(AppSettings.notionRefreshToken, it) }
    }

    private class Tokens(val access: String, val refresh: String?, val workspace: String?)

    private suspend fun post(action: String, field: String, value: String): Tokens = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put(field, value) }.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$relay/$action").post(body).build()
        http.newCall(req).execute().use { res ->
            val text = res.body.string()
            val obj = runCatching { NotionClient.json.parseToJsonElement(text).jsonObject }.getOrNull()
            fun str(k: String) = obj?.get(k)?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
            val access = str("access_token")
            if (!res.isSuccessful || access == null) {
                throw NotionException(str("error_description") ?: str("message") ?: str("error") ?: "HTTP ${res.code}", res.code)
            }
            Tokens(access, str("refresh_token"), str("workspace_name"))
        }
    }
}

/** Where Notion's redirect lands: hands the callback over and returns to the app. No UI. */
class NotionOAuthActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.data?.let { NotionOAuth.onCallback(it) }
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}
