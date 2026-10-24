package com.gpo.cronpaw.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.gpo.cronpaw.MainActivity
import com.gpo.cronpaw.core.agenda.AgendaItem
import com.gpo.cronpaw.ui.time.Times
import java.time.LocalDate
import androidx.glance.color.ColorProvider as DayNight

/** What the widgets share: opening the app at a day or an item, the todo circle, the time line. */
object WidgetOpen {
    const val Date = "cronpaw.open_date"
    const val Item = "cronpaw.open_item"

    /** The app at [date], with [item]'s sheet open when given. */
    fun intent(context: Context, date: LocalDate, item: AgendaItem? = null): Intent =
        Intent(context, MainActivity::class.java)
            // A distinct data per target, so the system keeps one pending intent each.
            .setData(Uri.parse("cronpaw://open/${date.toEpochDay()}/${item?.key.orEmpty()}"))
            .putExtra(Date, date.toEpochDay())
            .apply { item?.let { putExtra(Item, it.key) } }
}

fun openAt(context: Context, date: LocalDate, item: AgendaItem? = null): Action =
    actionStartActivity(WidgetOpen.intent(context, date, item))

/** The item's colour as a quiet wash, a little stronger at night (as on the timeline). */
fun itemWash(item: AgendaItem): ColorProvider =
    Color(item.color).let { DayNight(day = it.copy(alpha = 0.26f), night = it.copy(alpha = 0.42f)) }

/** A todo's circle, ticked off in its source on tap without opening the app. */
@Composable
fun TodoCircle(item: AgendaItem, size: Dp = 18.dp, tint: ColorProvider? = null) {
    val ring = 2.dp
    Box(
        GlanceModifier.size(size).cornerRadius(size / 2).background(ColorProvider(Color(item.color)))
            .clickable(actionRunCallback<ToggleDone>(actionParametersOf(ToggleDone.Key to item.key))),
        contentAlignment = Alignment.Center,
    ) {
        // Hollow: the widget's own background, under the same wash as around it when on one.
        val inner = GlanceModifier.size(size - ring * 2).cornerRadius(size / 2 - ring)
        Box(inner.background(GlanceTheme.colors.widgetBackground)) {
            if (tint != null) Box(inner.background(tint)) {}
        }
    }
}

/** 11:00 then 11:30 quieter: the start is what you read, the end is there if you look. */
@Composable
fun TimeLine(item: AgendaItem, fontSize: Float = 12f) {
    if (item.allDay) {
        Text("全天", maxLines = 1, style = TextStyle(fontSize = fontSize.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface))
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(Times.hm(item.start), maxLines = 1, style = TextStyle(fontSize = fontSize.sp, fontWeight = FontWeight.Medium, color = GlanceTheme.colors.onSurface))
        Spacer(GlanceModifier.width(6.dp))
        Text(Times.hm(item.end), maxLines = 1, style = TextStyle(fontSize = fontSize.sp, color = GlanceTheme.colors.onSurfaceVariant))
    }
}

/** The colour bar of a plain event, the height of its two lines. */
@Composable
fun ColorBar(item: AgendaItem, height: Dp) {
    Box(GlanceModifier.width(4.dp).height(height).cornerRadius(2.dp).background(ColorProvider(Color(item.color)))) {}
}
