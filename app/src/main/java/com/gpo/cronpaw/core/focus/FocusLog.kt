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
