package com.gpo.cronpaw.core.focus

import android.content.Context
import com.gpo.cronpaw.core.agenda.Source
import com.gpo.cronpaw.core.notion.NotionClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Every ended session, kept on the phone only (one JSON line each in focus_log.jsonl), so a
 * time overview can be built later from real history. Nothing reads it yet; nothing leaves the
 * phone. Written however the session ended, including 不记 and sessions under a minute.
 */
object FocusLog {
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Serializable
    data class Entry(
        val itemKey: String?,
        val title: String,
        val source: Source?,
        val todo: Boolean,
        val mode: FocusMode,
        val startedAt: Long,
        val endedAt: Long,
        /** Focus time only: breaks and pauses left out. */
        val focusedMs: Long,
        val roundsPlanned: Int,
        val roundsDone: Int,
        val pauses: Int,
        /** saved: the item's time was rewritten; discarded: 不记; kept: ended without rewriting. */
        val outcome: String,
        val markedDone: Boolean,
        /** Ended from 上次专注, after the app was gone. */
        val recovered: Boolean,
    )

    /**
     * The line a Notion page gets, e.g. 「🍅 10月25日 16:02–16:47，专注 45 分钟，2 轮」. Steady
     * wording, so it can be read back if the local log is ever lost.
     */
    fun line(s: FocusSession, endedAt: Long): String {
        val zone = ZoneId.systemDefault()
        val day = DateTimeFormatter.ofPattern("M月d日")
        val hm = DateTimeFormatter.ofPattern("HH:mm")
        val a = Instant.ofEpochMilli(s.startedAt).atZone(zone)
        val b = Instant.ofEpochMilli(endedAt).atZone(zone)
        val minutes = ((s.focused(endedAt).coerceAtMost(endedAt - s.startedAt) + 30_000L) / 60_000L).coerceAtLeast(1L)
        val mark = if (s.mode == FocusMode.Pomodoro) "🍅" else "⏱"
        val rounds = if (s.mode == FocusMode.Pomodoro) "，${s.harvested(endedAt)} 轮" else ""
        return "$mark ${a.format(day)} ${a.format(hm)}–${b.format(hm)}，专注 $minutes 分钟$rounds"
    }

    fun add(context: Context, s: FocusSession, endedAt: Long, outcome: String, markedDone: Boolean, recovered: Boolean) {
        val e = Entry(
            itemKey = s.itemKey,
            title = s.title,
            source = s.source,
            todo = s.todo,
            mode = s.mode,
            startedAt = s.startedAt,
            endedAt = endedAt,
            focusedMs = s.focused(endedAt).coerceAtMost(endedAt - s.startedAt).coerceAtLeast(0L),
            roundsPlanned = s.rounds,
            roundsDone = s.harvested(endedAt),
            pauses = s.pauses,
            outcome = outcome,
            markedDone = markedDone,
            recovered = recovered,
        )
        val line = NotionClient.json.encodeToString(Entry.serializer(), e)
        val file = File(context.applicationContext.filesDir, "focus_log.jsonl")
        io.launch { runCatching { file.appendText(line + "\n") } }
    }
}
