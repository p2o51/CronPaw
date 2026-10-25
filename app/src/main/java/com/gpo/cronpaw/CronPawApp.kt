package com.gpo.cronpaw

import android.app.Application
import com.gpo.cronpaw.core.agenda.Agenda
import com.gpo.cronpaw.core.agenda.Pins
import com.gpo.cronpaw.core.notion.NotionRepo
import com.gpo.cronpaw.core.settings.AppSettings
import com.gpo.cronpaw.widget.WidgetSync

class CronPawApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.gpo.cronpaw.core.debug.CrashLog.install(this)
        AppSettings.init(this)
        NotionRepo.init()
        Pins.init()
        Agenda.init(this)
        WidgetSync.init(this)
    }
}
