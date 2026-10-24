package com.gpo.cronpaw.debug

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.gpo.cronpaw.widget.AgendaWidget
import com.gpo.cronpaw.widget.MonthWidget
import com.gpo.cronpaw.widget.NextWidget
import com.gpo.cronpaw.widget.WidgetSync
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * Every widget hosted at the sizes a launcher gives it, on a wallpaper-ish background, so a
 * review screenshot needs no launcher. Needs `adb shell appwidget grantbind --package com.gpo.cronpaw`.
 */
class WidgetGallery : Activity() {
    private lateinit var host: AppWidgetHost
    private val ids = mutableListOf<Int>()

    private val specs = listOf(
        Triple(AgendaWidget::class.java, "今天 · 2×2", 180 to 200),
        Triple(AgendaWidget::class.java, "今天 · 4×2", 370 to 200),
        Triple(NextWidget::class.java, "下一个 · 2×1", 180 to 80),
        Triple(NextWidget::class.java, "下一个 · 2×2", 180 to 180),
        Triple(MonthWidget::class.java, "本月 · 4×3", 370 to 300),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = AppWidgetHost(this, 7301)
        host.startListening()
        val mgr = AppWidgetManager.getInstance(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(48), dp(16), dp(48))
        }
        val only = intent.getStringExtra("only")
        for ((cls, label, size) in specs) {
            if (only != null && !label.startsWith(only)) continue
            val provider = ComponentName(this, cls)
            val id = host.allocateAppWidgetId().also { ids += it }
            if (!mgr.bindAppWidgetIdIfAllowed(id, provider)) {
                column.addView(caption("$label：没有绑定权限（appwidget grantbind）"))
                continue
            }
            val info = mgr.getAppWidgetInfo(id)
            val view = host.createView(applicationContext, id, info)
            val (w, h) = size
            if (Build.VERSION.SDK_INT >= 31) view.updateAppWidgetSize(Bundle(), listOf(SizeF(w.toFloat(), h.toFloat())))
            column.addView(caption(label))
            column.addView(view, LinearLayout.LayoutParams(dp(w), dp(h)).apply { bottomMargin = dp(20) })
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.rgb(0x5C, 0x6B, 0x58))
            addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
        MainScope().launch { WidgetSync.update(applicationContext) }
    }

    override fun onDestroy() {
        ids.forEach { host.deleteAppWidgetId(it) }
        host.stopListening()
        super.onDestroy()
    }

    private fun caption(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(dp(4), 0, 0, dp(6))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
