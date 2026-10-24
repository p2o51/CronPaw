package com.gpo.cronpaw.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gpo.cronpaw.core.agenda.Agenda
import com.gpo.cronpaw.core.agenda.AgendaEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Keeps 今天 current — the thing Notion Calendar's widget gets wrong. Three triggers:
 * the app's own agenda changing, the calendar provider changing (even with the app
 * closed: [CalendarChangeJob]), and a 30-minute tick for the clock and Notion.
 */
object WidgetSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private const val Periodic = "agenda_widget_refresh"

    @OptIn(FlowPreview::class)
    fun init(context: Context) {
        val app = context.applicationContext
        scope.launch {
            AgendaEvents.version.debounce(600).collect { update(app) }
        }
    }

    /** Every CronPaw widget, by its Glance class. */
    private val widgets: List<Pair<Class<out GlanceAppWidget>, () -> GlanceAppWidget>> = listOf(
        AgendaGlance::class.java to { AgendaGlance() },
        NextGlance::class.java to { NextGlance() },
        MonthGlance::class.java to { MonthGlance() },
    )

    private suspend fun placed(context: Context, type: Class<out GlanceAppWidget>): Boolean =
        runCatching { GlanceAppWidgetManager(context).getGlanceIds(type) }.getOrDefault(emptyList()).isNotEmpty()

    suspend fun update(context: Context) {
        var any = false
        for ((type, make) in widgets) {
            if (!placed(context, type)) continue
            any = true
            runCatching { make().updateAll(context) }
        }
        if (any) WidgetTick.arm(context, countdown = placed(context, NextGlance::class.java))
    }

    /** One kind of widget is gone; stop the background refresh once none is left. */
    fun disabled(context: Context) {
        val app = context.applicationContext
        scope.launch { if (widgets.none { placed(app, it.first) }) cancel(app) }
    }

    fun schedule(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            Periodic,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES).build(),
        )
        CalendarChangeJob.schedule(context)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(Periodic)
    }
}

class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Agenda.init(applicationContext)
        Agenda.refresh()
        delay(4_000) // Notion's answer, usually
        WidgetSync.update(applicationContext)
        return Result.success()
    }
}

/**
 * A content-trigger job on the calendar provider: runs shortly after any calendar change
 * (a sync, another app's edit) and re-arms itself, since such jobs fire once.
 */
class CalendarChangeJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            Agenda.init(applicationContext)
            Agenda.reloadCalendars()
            WidgetSync.update(applicationContext)
            schedule(applicationContext)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val Id = 4107

        fun schedule(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            val job = JobInfo.Builder(Id, ComponentName(context, CalendarChangeJob::class.java))
                .addTriggerContentUri(JobInfo.TriggerContentUri(CalendarContract.CONTENT_URI, JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS))
                .setTriggerContentUpdateDelay(1_000)
                .setTriggerContentMaxDelay(10_000)
                .build()
            runCatching { js.schedule(job) }
        }
    }
}

/**
 * An alarm at the next moment the widget changes: an item starting or ending, or midnight.
 * Re-armed on every widget update, so it always points at the next one.
 */
class WidgetTick : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            runCatching {
                Agenda.init(context)
                WidgetSync.update(context)
            }
            pending.finish()
        }
    }

    companion object {
        /** [countdown]: a 下一个 widget is placed, whose 「40 分钟后」 moves on in five-minute steps. */
        fun arm(context: Context, countdown: Boolean = false) {
            val now = System.currentTimeMillis()
            val midnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            var next = Agenda.items.value.asSequence()
                .filter { !it.allDay }
                .flatMap { sequenceOf(it.start, it.end) }
                .filter { it > now + 1_000 }
                .minOrNull()
                ?.coerceAtMost(midnight) ?: midnight
            // Only while the next one is close (or on now): beyond three hours the text moves by the hour.
            NextGlance.next(Agenda.items.value, now)?.takeIf { countdown }?.let { item ->
                val left = (if (item.start > now) item.start else item.end) - now
                if (left < 3 * 3_600_000L) next = minOf(next, now + if (left < 10 * 60_000L) 60_000L else 5 * 60_000L)
            }
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val intent = PendingIntent.getBroadcast(
                context, 0, Intent(context, WidgetTick::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            // A one-minute window needs no exact-alarm permission and is close enough for a list.
            runCatching { am.setWindow(AlarmManager.RTC, next, 60_000, intent) }
        }
    }
}
