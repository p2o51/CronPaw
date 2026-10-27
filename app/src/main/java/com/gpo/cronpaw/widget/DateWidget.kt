package com.gpo.cronpaw.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.gpo.cronpaw.MainActivity
import com.gpo.cronpaw.R
import java.time.LocalDate

class DateWidget : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DateGlance()

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
 * 日期: the app icon with today's date on its pad — what a launcher icon can't do. Redrawn at
 * midnight (WidgetTick). A tap opens the app as the icon would, launch animation included.
 */
class DateGlance : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val fontScale = context.resources.configuration.fontScale
        provideContent { Content(context, LocalDate.now(), fontScale) }
    }

    @Composable
    private fun Content(context: Context, today: LocalDate, fontScale: Float) {
        val size = LocalSize.current
        val side = minOf(size.width, size.height)
        val open = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                GlanceModifier
                    .size(side)
                    .appWidgetBackground()
                    .cornerRadius(side / 2)
                    .background(ColorProvider(R.color.icon_bg))
                    .clickable(actionStartActivity(open)),
                contentAlignment = Alignment.Center,
            ) {
                Image(ImageProvider(R.drawable.widget_date_mark), contentDescription = null, modifier = GlanceModifier.fillMaxSize())
                // The pad's centre sits 7 of the 72 units below the middle; the date as large as the
                // icon's (16 units), in dp whatever the font size setting, so it stays on the pad.
                Text(
                    today.dayOfMonth.toString(),
                    modifier = GlanceModifier.padding(top = side * (14f / 72f)),
                    maxLines = 1,
                    style = TextStyle(
                        fontSize = (side.value * 16f / 72f / fontScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = ColorProvider(R.color.icon_ink),
                    ),
                )
            }
        }
    }
}
