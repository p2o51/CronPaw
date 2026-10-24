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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.gpo.cronpaw.core.agenda.Agenda
import com.gpo.cronpaw.core.agenda.AgendaItem
import com.gpo.cronpaw.ui.time.Times
import java.time.LocalDate
import java.time.YearMonth

class MonthWidget : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthGlance()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetSync.schedule(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetSync.disabled(context)
    }
}

/** 本月: the month at a glance, a dot per item in its colour, today marked; a day opens it. */
class MonthGlance : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Agenda.init(context)
        val month = YearMonth.now()
        Agenda.ensure(Times.monday(month.atDay(1)), month.atEndOfMonth())
        provideContent {
            GlanceTheme {
                val items by Agenda.items.collectAsState()
                Content(context, month, items)
            }
        }
    }

    @Composable
    private fun Content(context: Context, month: YearMonth, items: List<AgendaItem>) {
        val today = LocalDate.now()
        val first = Times.monday(month.atDay(1))
        val weeks = ((month.atEndOfMonth().toEpochDay() - first.toEpochDay()) / 7 + 1).toInt()
        val byDay = items.filter { !it.done }.groupBy { Times.date(it.start) }
        Column(
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(24.dp)
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
        ) {
            Text(
                "${month.monthValue}月",
                modifier = GlanceModifier.padding(start = 6.dp).clickable(openAt(context, today)),
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
            )
            Spacer(GlanceModifier.height(8.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                    Text(
                        it,
                        modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant, textAlign = TextAlign.Center),
                    )
                }
            }
            Spacer(GlanceModifier.height(4.dp))
            for (w in 0 until weeks) {
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    for (d in 0 until 7) {
                        val day = first.plusDays((w * 7 + d).toLong())
                        Box(GlanceModifier.defaultWeight().fillMaxHeight()) {
                            if (YearMonth.from(day) == month) Day(context, day, day == today, byDay[day].orEmpty())
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Day(context: Context, day: LocalDate, isToday: Boolean, items: List<AgendaItem>) {
        Column(
            GlanceModifier.fillMaxSize().clickable(openAt(context, day)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                GlanceModifier.size(24.dp).cornerRadius(12.dp)
                    .background(if (isToday) GlanceTheme.colors.primary else ColorProvider(Color.Transparent)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    day.dayOfMonth.toString(),
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                        color = if (isToday) GlanceTheme.colors.onPrimary else GlanceTheme.colors.onSurface,
                    ),
                )
            }
            // Up to three dots, one per item colour, so a busy day reads busy without numbers.
            Spacer(GlanceModifier.height(2.dp))
            Row(GlanceModifier.height(4.dp)) {
                items.map { it.color }.distinct().take(3).forEachIndexed { i, c ->
                    if (i > 0) Spacer(GlanceModifier.width(2.dp))
                    Box(GlanceModifier.size(4.dp).cornerRadius(2.dp).background(ColorProvider(Color(c)))) {}
                }
            }
        }
    }
}
