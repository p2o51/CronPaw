package com.gpo.cronpaw.feature.settings

import com.gpo.cronpaw.ui.time.Times
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.gpo.cronpaw.BuildConfig
import com.gpo.cronpaw.core.agenda.Agenda
import com.gpo.cronpaw.core.notion.NotionOAuth
import com.gpo.cronpaw.core.notion.NotionRepo
import com.gpo.cronpaw.core.settings.AppSettings
import com.gpo.cronpaw.navigation.Nav
import com.gpo.cronpaw.ui.components.PawTopBar
import com.gpo.cronpaw.ui.components.rememberToast

@Composable
fun SettingsScreen(nav: Nav) {
    val context = LocalContext.current
    val toast = rememberToast()
    val cs = MaterialTheme.colorScheme

    val token by AppSettings.notionToken.state.collectAsState()
    val dbs by NotionRepo.databases.collectAsState()
    val elevenKey by AppSettings.elevenLabsKey.state.collectAsState()
    val model by AppSettings.scribeModel.state.collectAsState()
    val quick by AppSettings.quickEntries.state.collectAsState()
    val autoSave by AppSettings.autoSaveSeconds.state.collectAsState()
    val defaultMinutes by AppSettings.defaultMinutes.state.collectAsState()
    val themeMode by AppSettings.themeMode.state.collectAsState()
    val dynamic by AppSettings.dynamicColor.state.collectAsState()
    val workspace by AppSettings.notionWorkspace.state.collectAsState()
    val oauthStatus by NotionOAuth.status.collectAsState()
    val uriHandler = LocalUriHandler.current

    Column(Modifier.fillMaxSize().background(cs.surface)) {
        PawTopBar("设置", onBack = { nav.pop() })
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            // Notion first, as a colour block that says where things stand.
            val connected = token.isNotBlank()
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(if (connected) cs.primaryContainer else cs.tertiaryContainer)
                    .padding(start = 22.dp, end = 16.dp, top = 20.dp, bottom = 16.dp),
            ) {
                val ink = if (connected) cs.onPrimaryContainer else cs.onTertiaryContainer
                Text("Notion", style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.7f))
                Text(
                    if (connected) workspace.ifBlank { "已连接" } else "还没连接",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                )
                Text(
                    if (connected) "${dbs.size} 个数据库在日历上" else "在授权页里勾上要放进日历的数据库。",
                    Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink.copy(alpha = 0.75f),
                )
                oauthStatus?.let { Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = ink) }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { uriHandler.openUri(NotionOAuth.begin().toString()) }) {
                        Text(if (connected) "重新授权" else "连接 Notion", fontWeight = FontWeight.Bold)
                    }
                    if (connected) TextButton(onClick = { NotionOAuth.disconnect(); Agenda.refresh(); toast("已断开") }) {
                        Text("断开", color = ink)
                    }
                }
            }
            Section("数据库") {
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

            // 新建: the two numbers that shape a new item, side by side, each with its choices.
            Section("新建") {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ValueTile("点时间轴新建", Times.duration(defaultMinutes), cs.secondaryContainer, cs.onSecondaryContainer, Modifier.weight(1.2f).fillMaxHeight())
                    ValueTile("自动加入", if (autoSave == 0) "不" to "自动" else "$autoSave" to "秒", cs.surfaceContainerHigh, cs.onSurface, Modifier.weight(1f).fillMaxHeight())
                }
                Hint("点时间轴的空白处，从那里开始新建，默认这么长。")
                Choices(listOf(15 to "15 分钟", 30 to "30 分钟", 45 to "45 分钟", 60 to "1 小时", 90 to "1.5 小时", 120 to "2 小时"), defaultMinutes) {
                    AppSettings.set(AppSettings.defaultMinutes, it)
                }
                Hint("确认页不碰屏幕，倒计时结束就自动加入。")
                Choices(listOf(0 to "不自动", 3 to "3 秒", 4 to "4 秒", 6 to "6 秒"), autoSave) { AppSettings.set(AppSettings.autoSaveSeconds, it) }
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

/** A label over a bold value and a quiet unit, in a colour block. */
@Composable
private fun ValueTile(label: String, value: Pair<String, String>, bg: Color, ink: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.7f))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value.first, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = ink)
            Text(value.second, Modifier.padding(start = 3.dp, bottom = 5.dp), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.7f))
        }
    }
}
