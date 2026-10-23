package com.gpo.cronpaw.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
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
import androidx.glance.unit.ColorProvider
import com.gpo.cronpaw.MainActivity
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
        WidgetSync.cancel(context)
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
        provideContent {
            GlanceTheme {
                val items by Agenda.items.collectAsState()
                Content(items)
            }
        }
    }

    @Composable
    private fun Content(items: List<AgendaItem>) {
        val now = System.currentTimeMillis()
        val today = LocalDate.now()
        val todayLeft = items.filter { !it.done && it.end > now && Times.date(it.start) <= today && Times.date(it.end - 1) >= today }
        val tomorrow = items.filter { !it.done && Times.date(it.start) == today.plusDays(1) }
        val size = LocalSize.current
        val rows = ((size.height.value - 56f) / 40f).toInt().coerceAtLeast(1)
        Column(
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(24.dp)
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    today.dayOfMonth.toString(),
                    style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    Times.weekday(today),
                    modifier = GlanceModifier.padding(bottom = 4.dp),
                    style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant),
                )
                Spacer(GlanceModifier.defaultWeight())
                if (todayLeft.isNotEmpty()) Text(
                    todayLeft.size.toString(),
                    modifier = GlanceModifier.padding(bottom = 4.dp),
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.primary),
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            val list = todayLeft.ifEmpty { tomorrow }
            if (list.isEmpty()) {
                Text("今天没有了", style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurfaceVariant))
            } else {
                if (todayLeft.isEmpty()) Text("明天", style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant))
                list.take(rows).forEach { item -> ItemRow(item) }
            }
        }
    }

    @Composable
    private fun ItemRow(item: AgendaItem) {
        Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.width(4.dp).height(30.dp).cornerRadius(2.dp).background(ColorProvider(Color(item.color)))) {}
            Spacer(GlanceModifier.width(8.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    item.title,
                    maxLines = 1,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface),
                )
                Text(
                    if (item.allDay) "全天" else "${Times.hm(item.start)}  ${Times.hm(item.end)}",
                    maxLines = 1,
                    style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
                )
            }
        }
    }
}
