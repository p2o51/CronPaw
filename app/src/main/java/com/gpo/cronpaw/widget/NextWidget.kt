package com.gpo.cronpaw.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
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
import com.gpo.cronpaw.core.agenda.Agenda
import com.gpo.cronpaw.core.agenda.AgendaItem
import com.gpo.cronpaw.ui.time.Times
import java.time.LocalDate

class NextWidget : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextGlance()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetSync.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetSync.disabled(context)
    }
}

/** 下一个: the one thing coming up, in its own colour, and how long until it starts. */
class NextGlance : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Agenda.init(context)
        provideContent {
            GlanceTheme {
                val items by Agenda.items.collectAsState()
                Content(context, next(items))
            }
        }
    }

    @Composable
    private fun Content(context: Context, item: AgendaItem?) {
        val size = LocalSize.current
        val short = size.height < 100.dp
        Box(
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(24.dp),
        ) {
            if (item == null) {
                Column(
                    GlanceModifier.fillMaxSize().padding(16.dp).clickable(openAt(context, LocalDate.now())),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("接下来没有了", style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurfaceVariant))
                }
                return@Box
            }
            Column(
                GlanceModifier
                    .fillMaxSize()
                    .background(itemWash(item))
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 14.dp)
                    .clickable(openAt(context, Times.date(item.start), item)),
                verticalAlignment = if (short) Alignment.CenterVertically else Alignment.Top,
            ) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        whenText(item),
                        modifier = GlanceModifier.defaultWeight(),
                        maxLines = 1,
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurfaceVariant),
                    )
                    if (item.todo) {
                        Spacer(GlanceModifier.width(8.dp))
                        TodoCircle(item, size = 20.dp, tint = itemWash(item))
                    }
                }
                Spacer(GlanceModifier.height(if (short) 2.dp else 6.dp))
                Text(
                    item.title,
                    maxLines = if (short) 1 else 2,
                    style = TextStyle(fontSize = if (short) 15.sp else 17.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
                )
                if (!short) {
                    Spacer(GlanceModifier.defaultWeight())
                    TimeLine(item, 13f)
                }
            }
        }
    }

    companion object {
        /** The one on now, else the next to start; not all-day ones and not ticked-off todos. */
        fun next(items: List<AgendaItem>, now: Long = System.currentTimeMillis()): AgendaItem? =
            items.filter { !it.allDay && !it.done && it.end > now }.minByOrNull { it.start }

        /** 进行中 · 还剩 20 分钟, 40 分钟后, 2 小时后, 明天 9:30, 10月27日 周二. */
        fun whenText(item: AgendaItem, now: Long = System.currentTimeMillis()): String {
            if (item.start <= now) return "进行中 · 还剩 ${span((item.end - now) / 60_000L)}"
            val day = Times.date(item.start)
            val today = LocalDate.now()
            return when (day) {
                today -> "${span((item.start - now) / 60_000L)}后"
                today.plusDays(1) -> "明天"
                else -> "${day.monthValue}月${day.dayOfMonth}日 ${Times.weekday(day)}"
            }
        }

        /** Minutes rounded so the text changes at most every five minutes. */
        private fun span(minutes: Long): String = when {
            minutes < 1 -> "1 分钟"
            minutes < 10 -> "$minutes 分钟"
            minutes < 60 -> "${(minutes + 4) / 5 * 5} 分钟"
            minutes % 60 < 5 || minutes >= 180 -> "${(minutes + 30) / 60} 小时"
            else -> "${minutes / 60} 小时 ${(minutes % 60) / 5 * 5} 分钟"
        }
    }
}
