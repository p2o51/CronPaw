package com.gpo.cronpaw.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gpo.cronpaw.BuildConfig
import com.gpo.cronpaw.core.agenda.Agenda
import com.gpo.cronpaw.core.notion.NotionClient
import com.gpo.cronpaw.core.notion.NotionRepo
import com.gpo.cronpaw.core.settings.AppSettings
import com.gpo.cronpaw.navigation.Nav
import com.gpo.cronpaw.ui.components.PawTopBar
import com.gpo.cronpaw.ui.components.rememberToast
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = rememberToast()
    val cs = MaterialTheme.colorScheme

    val token by AppSettings.notionToken.state.collectAsState()
    val dbs by NotionRepo.databases.collectAsState()
    val elevenKey by AppSettings.elevenLabsKey.state.collectAsState()
    val model by AppSettings.scribeModel.state.collectAsState()
    val quick by AppSettings.quickEntries.state.collectAsState()
    val autoSave by AppSettings.autoSaveSeconds.state.collectAsState()
    val themeMode by AppSettings.themeMode.state.collectAsState()
    val dynamic by AppSettings.dynamicColor.state.collectAsState()
    var checking by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(cs.surface)) {
        PawTopBar("设置", onBack = { nav.pop() })
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Section("Notion") {
                Field("Integration token", token, { AppSettings.set(AppSettings.notionToken, it.trim()) }, secret = true, placeholder = "ntn_…")
                Hint("在 notion.so/profile/integrations 建一个内部集成，把要用的数据库分享给它。")
                FilledTonalButton(
                    enabled = token.isNotBlank() && !checking,
                    onClick = {
                        checking = true
                        scope.launch {
                            runCatching { NotionClient.searchDatabases() }
                                .onSuccess { toast("能看到 ${it.size} 个数据库") }
                                .onFailure { toast(it.message ?: "连不上") }
                            checking = false
                        }
                    },
                ) { Text(if (checking) "检查中" else "检查") }
                dbs.forEach { db ->
                    Row2(db.title, if (db.enabled) null else "已隐藏", onClick = { nav.push("db:${db.id}") }, leading = {
                        Box(Modifier.size(16.dp).clip(RoundedCornerShape(5.dp)).background(Color(db.color)))
                    })
                }
                Row2("添加数据库", onClick = { nav.push("db:") })
            }

            Section("语音") {
                Field("ElevenLabs API key", elevenKey, { AppSettings.set(AppSettings.elevenLabsKey, it.trim()) }, secret = true)
                Hint("空着就用系统语音识别。填了就用 Scribe，按用量向 ElevenLabs 付费。")
                if (elevenKey.isNotBlank()) {
                    Field("模型", model, { AppSettings.set(AppSettings.scribeModel, it.trim()) })
                }
            }

            Section("快捷入口") {
                Field("每行一个：标题|分钟", quick, { AppSettings.set(AppSettings.quickEntries, it) }, singleLine = false)
            }

            Section("自动加入") {
                Choices(listOf(0 to "不自动", 3 to "3 秒", 4 to "4 秒", 6 to "6 秒"), autoSave) { AppSettings.set(AppSettings.autoSaveSeconds, it) }
                Hint("说完不碰屏幕，倒计时结束就加入。")
            }

            Section("外观") {
                Choices(listOf(0 to "跟随系统", 1 to "浅色", 2 to "深色"), themeMode) { AppSettings.set(AppSettings.themeMode, it) }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("壁纸取色", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(dynamic, { AppSettings.set(AppSettings.dynamicColor, it) })
                }
            }

            Section("关于") {
                Row2("CronPaw", BuildConfig.VERSION_NAME)
                Row2("源代码", "GitHub", onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/p2o51/CronPaw"))) }
                })
                Row2("重新读取日历", onClick = { Agenda.refresh(); toast("已刷新") })
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
