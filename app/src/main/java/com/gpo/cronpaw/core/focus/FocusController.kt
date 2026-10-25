package com.gpo.cronpaw.core.focus

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.gpo.cronpaw.core.agenda.Actions
import com.gpo.cronpaw.core.agenda.AgendaItem
import com.gpo.cronpaw.core.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Every change to a session goes through here: the state is saved, then the alarm, the
 * notification and the 专注 mode are brought in line with it ([sync]).
 */
object FocusController {

    /** What the start sheet chose, waiting for the breath to finish before the clock starts. */
    data class Plan(val item: AgendaItem, val mode: FocusMode, val quiet: Boolean)

    private val _pending = MutableStateFlow<Plan?>(null)
    val pending: StateFlow<Plan?> = _pending.asStateFlow()

    /** Why the app should ask how long the last session ran (上次专注). */
    enum class Interrupted { Restarted, Forgotten, LongBreak }

    private val _recovery = MutableStateFlow<Interrupted?>(null)
    val recovery: StateFlow<Interrupted?> = _recovery.asStateFlow()

    /** 「专注」 was tapped on an item: home shows the start sheet. */
    val asked = MutableStateFlow<AgendaItem?>(null)

    /** The notification (or 接着计时) wants the focus page in front. */
    val openPage = MutableStateFlow(false)

    fun plan(item: AgendaItem, mode: FocusMode, quiet: Boolean) {
        AppSettings.set(FocusSettings.lastMode, mode.name)
        AppSettings.set(FocusSettings.quiet, quiet)
        _pending.value = Plan(item, mode, quiet)
    }

    fun cancelPlan() { _pending.value = null }

    /** 上次专注 swiped away: asked again on the next return. */
    fun dismissRecovery() { _recovery.value = null }

    /** The clock starts now (after the breath, or straight away). */
    fun start(context: Context, plan: Plan, now: Long = System.currentTimeMillis()) {
        val s = preview(context, plan, now)
        _recovery.value = null
        commit(context, s)
        // After the commit: the focus page never sees neither a plan nor a session and closes.
        _pending.value = null
    }

    /** The session [start] would make at [now], not saved: the focus page warms up on it. */
    fun preview(context: Context, plan: Plan, now: Long = System.currentTimeMillis()): FocusSession {
        val item = plan.item
        val focus = FocusSettings.focusMin.value
        val brk = FocusSettings.breakMin.value
        val minutes = FocusSession.minutesFor(item, now, focus)
        val (rounds, last) = FocusSession.plan(minutes, focus, brk)
        return FocusSession(
            itemKey = item.key,
            title = item.title,
            color = item.color,
            source = item.source,
            itemStart = item.start,
            itemEnd = item.end,
            todo = item.todo && !item.done,
            mode = plan.mode,
            startedAt = now,
            focusMin = focus,
            breakMin = brk,
            longBreakMin = FocusSettings.longBreakMin.value,
            rounds = if (plan.mode == FocusMode.Free) 1 else rounds,
            lastFocusMin = last,
            segStart = now,
            segLen = if (plan.mode == FocusMode.Free) 0 else (if (rounds == 1) last else focus) * 60_000L,
            quiet = plan.quiet,
            bootCount = bootCount(context),
        )
    }

    fun pause(context: Context) = update(context) { s, now -> s.pause(now) }
    fun resume(context: Context) = update(context) { s, now -> s.resume(now) }
    fun plusFive(context: Context) = update(context) { s, _ -> s.plusFive() }
    fun nextRound(context: Context) = update(context) { s, now -> s.nextRound(now) }

    /** The user is here: the 3-hour 「忘了按结束」 check counts from now. */
    fun touch(context: Context) {
        val s = FocusStore.value ?: return
        val now = System.currentTimeMillis()
        if (now - s.lastSeen > 25_000L) FocusStore.save(s.copy(lastSeen = now))
    }

    private fun update(context: Context, block: (FocusSession, Long) -> FocusSession) {
        val now = System.currentTimeMillis()
        val s = FocusStore.value ?: return
        commit(context, block(advance(s, now), now).copy(lastSeen = now))
    }

    private fun commit(context: Context, s: FocusSession) {
        FocusStore.save(s)
        sync(context, s)
    }

    /** Alarm, notification and mode, for [s] as it is now. */
    fun sync(context: Context, s: FocusSession? = FocusStore.value) {
        val app = context.applicationContext
        if (s == null) {
            cancelAlarms(app)
            FocusNotifier.cancel(app)
            FocusZen.apply(app, false)
            return
        }
        FocusZen.apply(app, FocusZen.wanted(s))
        FocusNotifier.post(app, s)
        schedule(app, s)
    }

    /**
     * An alarm went off (or the app came back): move past any boundary, and make a sound if one
     * was crossed.
     */
    fun tick(context: Context, alert: Boolean) {
        val s = FocusStore.value ?: return
        val next = advance(s, System.currentTimeMillis())
        if (next != s) {
            FocusStore.save(next)
            if (alert && next.phase != s.phase) FocusNotifier.alert(context, next)
        }
        sync(context, next)
    }

    /**
     * Ends the session: the item's time becomes what was used ([rewrite]), a todo can be ticked,
     * and everything is cleared. [endAt] overrides the end (上次专注).
     */
    suspend fun finish(context: Context, rewrite: Boolean, markDone: Boolean, endAt: Long? = null): Boolean {
        val s = FocusStore.value ?: return true
        val now = System.currentTimeMillis()
        val cur = advance(s, now)
        val end = endAt ?: cur.actualEnd(now)
        clear(context)
        var ok = true
        val item = item(cur)
        val rewrites = item != null && rewrite && !item.allDay && end - cur.startedAt >= 60_000L
        if (rewrites) ok = Actions.move(context, item!!, cur.startedAt, end, onlyThis = true)
        val done = item != null && markDone && item.todo
        // The timeline keeps the item's own times unless they were just rewritten.
        if (done) ok = Actions.setDone(if (rewrites) item!!.copy(start = cur.startedAt, end = end) else item!!, true) && ok
        FocusLog.add(context, cur, end, if (rewrites) "saved" else "kept", done, recovered = endAt != null)
        return ok
    }

    /** 放弃: nothing is written to the item, but the session still goes into [FocusLog]. */
    fun discard(context: Context, recovered: Boolean = false, endAt: Long? = null) {
        val s = FocusStore.value
        val now = System.currentTimeMillis()
        clear(context)
        if (s != null) {
            // The end the page showed (when 结束 was held), not when 放弃 was tapped.
            val end = endAt ?: advance(s, now).actualEnd(now)
            FocusLog.add(context, advance(s, end), end, "discarded", markedDone = false, recovered = recovered)
        }
    }

    private fun clear(context: Context) {
        FocusStore.save(null)
        _pending.value = null
        _recovery.value = null
        sync(context, null)
    }

    /** The item as the session knew it, enough to write back. */
    private fun item(s: FocusSession): AgendaItem? {
        val src = s.source ?: return null
        val key = s.itemKey ?: return null
        return AgendaItem(key = key, title = s.title, start = s.itemStart, end = s.itemEnd, allDay = false, color = s.color, source = src, todo = s.todo)
    }

    /** Keeps going after 上次专注's 「接着计时」. */
    fun keepGoing(context: Context) {
        val s = FocusStore.value ?: return
        _recovery.value = null
        commit(context, s.copy(lastSeen = System.currentTimeMillis(), bootCount = bootCount(context)))
    }

    /**
     * On every return to the app: catch up, and decide whether the last session needs 上次专注
     * (the phone restarted or the app was force-stopped, 自由 left alone for 3 hours, a break
     * over by 15 minutes).
     */
    fun reconcile(context: Context) {
        val s0 = FocusStore.value ?: return
        val now = System.currentTimeMillis()
        val s = advance(s0, now)
        if (s != s0) FocusStore.save(s)
        val reason = when {
            s.phase == Phase.Done -> null
            s.bootCount != -1 && s.bootCount != bootCount(context) -> Interrupted.Restarted
            s.running && !alarmAlive(context) -> Interrupted.Restarted
            s.mode == FocusMode.Free && !s.paused && now - s.lastSeen > 3 * 3_600_000L -> Interrupted.Forgotten
            s.phase == Phase.Waiting && now - s.segStart > 15 * 60_000L -> Interrupted.LongBreak
            else -> null
        }
        _recovery.value = reason
        if (reason == null) {
            touch(context)
            sync(context)
        }
    }

    // ── alarms ──────────────────────────────────────────────────────────────

    private fun am(context: Context) = context.getSystemService(AlarmManager::class.java)

    private fun schedule(context: Context, s: FocusSession) {
        val now = System.currentTimeMillis()
        val am = am(context)
        val boundary = FocusReceiver.pending(context, FocusReceiver.Boundary)
        val end = s.segEnd
        if (end != null && s.mode == FocusMode.Pomodoro) {
            runCatching {
                if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, boundary)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, boundary)
            }
        } else am.cancel(boundary)
        // The bar in the notification moves once a minute, only while the phone is awake (RTC, not _WAKEUP).
        val refresh = FocusReceiver.pending(context, FocusReceiver.Refresh)
        if (s.running) am.set(AlarmManager.RTC, now + 60_000L, refresh) else am.cancel(refresh)
        // A marker that only a force-stop or a restart removes.
        val alive = FocusReceiver.pending(context, FocusReceiver.Alive)
        am.set(AlarmManager.RTC, now + 365L * 86_400_000L, alive)
    }

    private fun alarmAlive(context: Context): Boolean = PendingIntent.getBroadcast(
        context, FocusReceiver.Alive.hashCode(),
        Intent(context, FocusReceiver::class.java).setAction(FocusReceiver.Alive),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    ) != null

    private fun cancelAlarms(context: Context) {
        val am = am(context)
        listOf(FocusReceiver.Boundary, FocusReceiver.Refresh, FocusReceiver.Alive).forEach { a ->
            val pi = FocusReceiver.pending(context, a)
            am.cancel(pi)
            pi.cancel()
        }
    }

    private fun bootCount(context: Context): Int =
        runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT) }.getOrDefault(-1)
}

/** The notification's buttons, the alarms, a restart and an app update. */
class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        FocusStore.init(context)
        when (intent.action) {
            Boundary -> FocusController.tick(context, alert = true)
            Refresh -> FocusController.tick(context, alert = false)
            Pause -> FocusController.pause(context)
            Resume -> FocusController.resume(context)
            Next -> FocusController.nextRound(context)
            // The phone restarted: no notification and no mode until the user is asked how long it was.
            Intent.ACTION_BOOT_COMPLETED -> if (FocusStore.value != null) FocusZen.apply(context, false)
            // An update removes the app's alarms: put them back.
            Intent.ACTION_MY_PACKAGE_REPLACED -> if (FocusStore.value != null) FocusController.tick(context, alert = false)
        }
    }

    companion object {
        const val Boundary = "com.gpo.cronpaw.focus.BOUNDARY"
        const val Refresh = "com.gpo.cronpaw.focus.REFRESH"
        const val Pause = "com.gpo.cronpaw.focus.PAUSE"
        const val Resume = "com.gpo.cronpaw.focus.RESUME"
        const val Next = "com.gpo.cronpaw.focus.NEXT"
        const val Alive = "com.gpo.cronpaw.focus.ALIVE"

        fun pending(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context, action.hashCode(),
            Intent(context, FocusReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
