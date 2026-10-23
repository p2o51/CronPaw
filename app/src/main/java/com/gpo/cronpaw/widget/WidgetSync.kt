package com.gpo.cronpaw.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.CalendarContract
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

    suspend fun update(context: Context) {
        val ids = runCatching { GlanceAppWidgetManager(context).getGlanceIds(AgendaGlance::class.java) }.getOrDefault(emptyList())
        if (ids.isNotEmpty()) runCatching { AgendaGlance().updateAll(context) }
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
