package com.gpo.cronpaw.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.gpo.cronpaw.core.agenda.Actions
import com.gpo.cronpaw.core.agenda.Agenda
import com.gpo.cronpaw.core.agenda.AgendaItem
import com.gpo.cronpaw.ui.time.Times
import java.time.LocalDate

class AgendaWidget : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaGlance()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetSync.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetSync.disabled(context)
    }
}

/**
 * 今天: what is left today (and the first of tomorrow when today is done), drawn from the
 * same cache the app opens with, so it is never older than the app itself.
 */
class AgendaGlance : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Agenda.init(context)
        Agenda.awaitReady()
        provideContent {
            GlanceTheme {
                val items by Agenda.items.collectAsState()
                Content(context, items)
            }
        }
    }

    @Composable
    private fun Content(context: Context, items: List<AgendaItem>) {
        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        val todayLeft = items.filter { !it.done && it.end > now && Times.date(it.start) <= today && Times.date(it.end - 1) >= today }
        val tomorrow = items.filter { !it.done && Times.date(it.start) == today.plusDays(1) }
        val size = LocalSize.current
        val narrow = size.width < 200.dp
        // A narrow widget gives a title two lines rather than cutting it after a few characters.
        val rowHeight = if (narrow) 62f else 46f
        val list = todayLeft.ifEmpty { tomorrow }
        val label = if (todayLeft.isEmpty() && list.isNotEmpty()) 22f else 0f
        val rows = ((size.height.value - 30f - 40f - 12f - label) / rowHeight).toInt().coerceAtLeast(1)
        Column(
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(24.dp)
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp)
                .clickable(openAt(context, today)),
        ) {
            // The day, and how many are left: one line, the date carrying it.
            Row(GlanceModifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    today.dayOfMonth.toString(),
                    style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    Times.weekday(today),
                    maxLines = 1,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurfaceVariant),
                )
                Spacer(GlanceModifier.defaultWeight())
                if (todayLeft.isNotEmpty() && !narrow) Text(
                    "还有 ${todayLeft.size} 项",
                    maxLines = 1,
                    style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant),
                )
            }
            Spacer(GlanceModifier.height(12.dp))
            if (list.isEmpty()) {
                Text("今天没有了", style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurfaceVariant))
            } else {
                if (todayLeft.isEmpty()) {
                    Text("明天", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurfaceVariant))
                    Spacer(GlanceModifier.height(8.dp))
                }
                // Spacing as padding, not spacers: a Glance column takes ten children at most.
                Column(GlanceModifier.fillMaxWidth()) {
                    list.take(rows.coerceAtMost(9)).forEachIndexed { i, item ->
                        ItemRow(context, item, if (narrow) 2 else 1, if (i > 0) 12.dp else 0.dp)
                    }
                }
            }
        }
    }

    @Composable
    private fun ItemRow(context: Context, item: AgendaItem, titleLines: Int, top: Dp) {
        Row(
            GlanceModifier.fillMaxWidth().padding(top = top).clickable(openAt(context, Times.date(item.start), item)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The mark sits in an 18dp column so circles and bars line their titles up.
            Box(GlanceModifier.width(18.dp), contentAlignment = Alignment.Center) {
                if (item.todo) TodoCircle(item) else ColorBar(item, 32.dp)
            }
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    item.title,
                    maxLines = titleLines,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface),
                )
                Spacer(GlanceModifier.height(2.dp))
                TimeLine(item)
            }
        }
    }
}

/** The todo circle on the widget: ticks the item off in its source. */
class ToggleDone : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val key = parameters[Key] ?: return
        Agenda.init(context)
        Agenda.awaitReady()
        val item = Agenda.items.value.firstOrNull { it.key == key } ?: return
        Actions.setDone(item, !item.done)
        WidgetSync.update(context)
    }

    companion object {
        val Key = ActionParameters.Key<String>("key")
    }
}
