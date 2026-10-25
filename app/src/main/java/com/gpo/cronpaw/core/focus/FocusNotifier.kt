package com.gpo.cronpaw.core.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import com.gpo.cronpaw.MainActivity
import com.gpo.cronpaw.R

/**
 * The ongoing notification while a session runs. On Android 16 it asks to be a Live Update
 * (promoted, [Notification.ProgressStyle]): a chip in the status bar with the countdown, and a
 * bar split into rounds and breaks. The numbers tick by themselves (a chronometer), so nothing
 * has to run in the background; it is re-posted at each boundary and once a minute while the
 * screen is on, for the bar.
 */
object FocusNotifier {
    const val LiveId = 4101
    private const val AlertId = 4102
    private const val LiveChannel = "focus_live"
    private const val AlertChannel = "focus_alert"
    const val OpenFocus = "cronpaw.open_focus"

    private fun nm(context: Context) = context.getSystemService(NotificationManager::class.java)

    private fun channels(context: Context) {
        val manager = nm(context)
        if (manager.getNotificationChannel(LiveChannel) == null) {
            manager.createNotificationChannel(NotificationChannel(LiveChannel, "专注计时", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            })
        }
        if (manager.getNotificationChannel(AlertChannel) == null) {
            manager.createNotificationChannel(NotificationChannel(AlertChannel, "专注提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                setShowBadge(false)
            })
        }
    }

    fun openIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).putExtra(OpenFocus, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun action(context: Context, label: String, act: String): Notification.Action =
        Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_focus), label, FocusReceiver.pending(context, act)).build()

    fun post(context: Context, s: FocusSession, now: Long = System.currentTimeMillis()) {
        channels(context)
        val b = Notification.Builder(context, LiveChannel)
            .setSmallIcon(R.drawable.ic_focus)
            .setContentTitle(s.title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setColor(s.color)
            .setContentIntent(openIntent(context))
            // Swiped away (Android 14+ lets ongoing ones go): it comes back.
            .setDeleteIntent(FocusReceiver.pending(context, FocusReceiver.Refresh))
            .setVisibility(Notification.VISIBILITY_PUBLIC)

        val label = when {
            s.paused -> "已暂停  ${clock(s.remaining(now).takeIf { s.segLen > 0 } ?: s.focused(now))}"
            s.phase == Phase.Break -> "休息"
            s.phase == Phase.Waiting -> "休息好了"
            s.phase == Phase.Done -> "专注完成  ${s.focusedMs / 60_000L} 分钟"
            s.mode == FocusMode.Free -> "已专注"
            else -> if (s.rounds > 1) "第 ${s.round + 1} / ${s.rounds} 轮" else "专注"
        }
        b.setContentText(label)

        val end = s.segEnd
        when {
            s.paused || s.phase == Phase.Waiting || s.phase == Phase.Done -> b.setShowWhen(false)
            s.mode == FocusMode.Free -> b.setUsesChronometer(true).setWhen(s.segStart - s.focusedMs).setShowWhen(true)
            end != null -> b.setUsesChronometer(true).setChronometerCountDown(true).setWhen(end).setShowWhen(true)
        }

        when {
            s.phase == Phase.Waiting -> b.addAction(action(context, "下一轮", FocusReceiver.Next))
            s.phase == Phase.Done -> Unit
            s.paused -> b.addAction(action(context, "继续", FocusReceiver.Resume))
            else -> b.addAction(action(context, "暂停", FocusReceiver.Pause))
        }
        // 结束 opens the end page: the time is written back there.
        b.addAction(Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_focus), "结束", openIntent(context)).build())

        if (Build.VERSION.SDK_INT >= 36) live(b, s, now) else legacyProgress(b, s, now)
        runCatching { nm(context).notify(LiveId, b.build()) }
    }

    private fun legacyProgress(b: Notification.Builder, s: FocusSession, now: Long) {
        if (s.mode == FocusMode.Free || s.phase == Phase.Done) return
        val total = (0 until s.rounds).sumOf { s.focusLenMin(it) + if (it < s.rounds - 1) s.breakLenMin(it) else 0 }
        b.setProgress(total, doneMinutes(s, now).coerceAtMost(total), false)
    }

    /** Minutes of the whole plan behind us (finished rounds and breaks, plus the current segment). */
    private fun doneMinutes(s: FocusSession, now: Long): Int {
        var m = 0
        for (r in 0 until s.round) m += s.focusLenMin(r) + s.breakLenMin(r)
        return m + when (s.phase) {
            Phase.Focus -> (s.elapsed(now) / 60_000L).toInt()
            Phase.Break -> s.focusLenMin(s.round) + (s.elapsed(now) / 60_000L).toInt()
            Phase.Waiting -> s.focusLenMin(s.round) + s.breakLenMin(s.round)
            Phase.Done -> 0
        }
    }

    @Suppress("NewApi")
    private fun live(b: Notification.Builder, s: FocusSession, now: Long) {
        // setRequestPromotedOngoing is API 36.1; compileSdk is 36, so set its extra directly.
        b.addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
        when {
            s.paused -> b.setShortCriticalText("暂停")
            s.phase == Phase.Waiting -> b.setShortCriticalText("休息好了")
            s.phase == Phase.Done -> b.setShortCriticalText("完成")
        }
        val focus = s.color
        val rest = breakColor(s.color)
        val style = Notification.ProgressStyle().setStyledByProgress(false)
        if (s.mode == FocusMode.Free) {
            val minutes = (s.focused(now) / 60_000L).toInt()
            val blocks = minutes / s.focusMin + 1
            style.setProgressSegments((0 until blocks).map { Notification.ProgressStyle.Segment(s.focusMin).setColor(focus) })
            style.setProgress(minutes)
        } else {
            val segments = mutableListOf<Notification.ProgressStyle.Segment>()
            for (r in 0 until s.rounds) {
                segments += Notification.ProgressStyle.Segment(s.focusLenMin(r)).setColor(focus)
                if (r < s.rounds - 1) segments += Notification.ProgressStyle.Segment(s.breakLenMin(r)).setColor(rest)
            }
            style.setProgressSegments(segments)
            style.setProgress(if (s.phase == Phase.Done) segments.sumOf { it.length } else doneMinutes(s, now))
        }
        b.setStyle(style)
    }

    /** A round ended, a break ended: a sound and a heads-up, gone after a minute. */
    fun alert(context: Context, s: FocusSession) {
        channels(context)
        val text = when (s.phase) {
            Phase.Break -> "休息 ${s.segLen / 60_000L} 分钟"
            Phase.Waiting -> "休息好了，开始下一轮"
            Phase.Done -> "专注完成"
            else -> return
        }
        val n = Notification.Builder(context, AlertChannel)
            .setSmallIcon(R.drawable.ic_focus)
            .setContentTitle(s.title)
            .setContentText(text)
            .setColor(s.color)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000L)
            .setContentIntent(openIntent(context))
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        runCatching { nm(context).notify(AlertId, n) }
    }

    fun cancel(context: Context) {
        runCatching {
            nm(context).cancel(LiveId)
            nm(context).cancel(AlertId)
        }
    }

    private fun clock(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = s % 3600 / 60
        val sec = (s % 60).toString().padStart(2, '0')
        return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$sec" else "$m:$sec"
    }
}

/** The third colour for breaks: the item's hue turned a third of the way round, softer. */
fun breakColor(color: Int): Int {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color, hsv)
    hsv[0] = (hsv[0] + 60f) % 360f
    hsv[1] = (hsv[1] * 0.7f).coerceAtMost(0.6f)
    hsv[2] = hsv[2].coerceAtLeast(0.75f)
    return android.graphics.Color.HSVToColor(hsv)
}
