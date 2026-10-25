package com.gpo.cronpaw.core.focus

import com.gpo.cronpaw.core.agenda.AgendaItem
import com.gpo.cronpaw.core.agenda.Source
import kotlinx.serialization.Serializable
import kotlin.math.max

/** 番茄 counts down in rounds; 自由 counts up until it is ended by hand. */
@Serializable
enum class FocusMode { Pomodoro, Free }

/** Where a session is. [Waiting]: a break is over and the next round waits for a tap. [Done]: the last round ran out. */
@Serializable
enum class Phase { Focus, Break, Waiting, Done }

/**
 * One focus session on one item. Everything is wall-clock millis, so nothing has to keep
 * running for the timer to be right: the app, the alarm and the notification all derive the
 * same state from it with [advance].
 *
 * A segment is the current focus or break: it started at [segStart] (moved forward by every
 * pause) and lasts [segLen] (0 for 自由, which has no end).
 */
@Serializable
data class FocusSession(
    val itemKey: String?,
    val title: String,
    val color: Int,
    val source: Source?,
    /** The item's own times when the session began (0 for an unscheduled Notion todo). */
    val itemStart: Long,
    val itemEnd: Long,
    val todo: Boolean = false,
    val mode: FocusMode,
    val startedAt: Long,
    val focusMin: Int = 25,
    val breakMin: Int = 5,
    val longBreakMin: Int = 15,
    val rounds: Int = 1,
    /** The last round takes what is left over, so the rounds fill the item. */
    val lastFocusMin: Int = 25,
    val round: Int = 0,
    val phase: Phase = Phase.Focus,
    val segStart: Long = startedAt,
    val segLen: Long = 0,
    val pausedAt: Long? = null,
    val pauses: Int = 0,
    /** Focus time banked by finished rounds (breaks and pauses not counted). */
    val focusedMs: Long = 0,
    /** Last time the user was around (the app in front, a tap on the notification). */
    val lastSeen: Long = startedAt,
    /** When the last round ran out ([Phase.Done]). */
    val endedAt: Long? = null,
    /** Use CronPaw's 专注 mode (do not disturb) during focus. */
    val quiet: Boolean = true,
    /** Settings.Global.BOOT_COUNT when it started: a different one means the phone restarted. */
    val bootCount: Int = -1,
) {
    val paused: Boolean get() = pausedAt != null
    val running: Boolean get() = pausedAt == null && (phase == Phase.Focus || phase == Phase.Break)

    /** Time into the current segment. */
    fun elapsed(now: Long): Long = max(0L, (pausedAt ?: now) - segStart)

    /** Time left in the current segment (0 for 自由). */
    fun remaining(now: Long): Long = if (segLen <= 0) 0 else max(0L, segLen - elapsed(now))

    /** When the current segment ends while it runs; null when paused, waiting, done or 自由. */
    val segEnd: Long? get() = if (pausedAt == null && segLen > 0 && (phase == Phase.Focus || phase == Phase.Break)) segStart + segLen else null

    /** All focus so far, the current round included. */
    fun focused(now: Long): Long = focusedMs + if (phase == Phase.Focus) elapsed(now) else 0

    /** Rounds whose focus is finished. 自由 counts one per 25 minutes. */
    fun harvested(now: Long): Int = when (mode) {
        FocusMode.Free -> (focused(now) / (focusMin * 60_000L)).toInt()
        FocusMode.Pomodoro -> when (phase) {
            Phase.Focus -> round
            Phase.Break, Phase.Waiting -> round + 1
            Phase.Done -> rounds
        }
    }

    /** 0..1 of the current segment; 自由 fills once every [focusMin]. */
    fun progress(now: Long): Float = when {
        mode == FocusMode.Free -> ((focused(now) % (focusMin * 60_000L)).toFloat() / (focusMin * 60_000L))
        phase == Phase.Waiting || phase == Phase.Done -> 1f
        segLen <= 0 -> 0f
        else -> (elapsed(now).toFloat() / segLen).coerceIn(0f, 1f)
    }

    /** The span written back to the item when the session is saved. */
    fun actualEnd(now: Long): Long = when {
        endedAt != null -> endedAt
        phase == Phase.Waiting -> segStart
        else -> pausedAt ?: now
    }

    fun focusLenMin(round: Int): Int = if (round >= rounds - 1) lastFocusMin else focusMin

    fun breakLenMin(afterRound: Int): Int = if ((afterRound + 1) % 4 == 0) longBreakMin else breakMin

    companion object {
        /**
         * Rounds for an item of [minutes]: as many whole 番茄 as fit with their breaks; what is left
         * goes into the last round. Shorter than one round = one round of that length.
         */
        fun plan(minutes: Int, focusMin: Int, breakMin: Int): Pair<Int, Int> {
            if (minutes <= focusMin) return 1 to minutes.coerceAtLeast(5)
            val rounds = max(1, (minutes + breakMin) / (focusMin + breakMin))
            val left = minutes - (rounds * focusMin + (rounds - 1) * breakMin)
            return rounds to (focusMin + left).coerceAtLeast(5)
        }

        /** Minutes a session on [item] should cover when it starts at [now]. */
        fun minutesFor(item: AgendaItem, now: Long, fallback: Int): Int = when {
            item.start <= 0L || item.allDay -> fallback
            now in item.start until item.end -> ((item.end - now) / 60_000L).toInt()
            else -> item.durationMinutes
        }.coerceAtLeast(5)
    }
}

/** Moves [s] past every boundary up to [now]: focus → break → waiting, the last focus → done. */
fun advance(s: FocusSession, now: Long): FocusSession {
    var cur = s
    while (true) {
        val end = cur.segEnd ?: return cur
        if (now < end || cur.mode == FocusMode.Free) return cur
        cur = when (cur.phase) {
            Phase.Focus -> {
                val banked = cur.focusedMs + cur.segLen
                if (cur.round >= cur.rounds - 1) cur.copy(phase = Phase.Done, focusedMs = banked, endedAt = end, segStart = end, segLen = 0)
                else cur.copy(phase = Phase.Break, focusedMs = banked, segStart = end, segLen = cur.breakLenMin(cur.round) * 60_000L)
            }
            Phase.Break -> cur.copy(phase = Phase.Waiting, segStart = end, segLen = 0)
            else -> return cur
        }
    }
}

fun FocusSession.pause(now: Long): FocusSession = if (paused || !running) this else copy(pausedAt = now, pauses = pauses + 1)

fun FocusSession.resume(now: Long): FocusSession {
    val at = pausedAt ?: return this
    return copy(pausedAt = null, segStart = segStart + (now - at))
}

/** +5 分 on the current round (番茄 focus only). */
fun FocusSession.plusFive(): FocusSession =
    if (mode == FocusMode.Pomodoro && phase == Phase.Focus) copy(segLen = segLen + 5 * 60_000L) else this

/** The next round, from a finished break (or skipping the break). */
fun FocusSession.nextRound(now: Long): FocusSession {
    if (mode != FocusMode.Pomodoro || (phase != Phase.Waiting && phase != Phase.Break)) return this
    val next = round + 1
    return copy(phase = Phase.Focus, round = next, segStart = now, segLen = focusLenMin(next) * 60_000L, pausedAt = null)
}
