package com.gpo.cronpaw.feature.settings

import com.gpo.cronpaw.ui.components.seam.seamGraphic
import com.gpo.cronpaw.ui.components.seam.seamText
import com.gpo.cronpaw.ui.components.seam.seamViewport
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gpo.cronpaw.BuildConfig
import com.gpo.cronpaw.core.agenda.Agenda
import com.gpo.cronpaw.core.notion.NotionOAuth
import com.gpo.cronpaw.core.notion.NotionRepo
import com.gpo.cronpaw.core.settings.AppSettings
import com.gpo.cronpaw.core.settings.QuickEntries
import com.gpo.cronpaw.core.focus.FocusSettings
import com.gpo.cronpaw.core.focus.FocusZen
import com.gpo.cronpaw.R
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.gpo.cronpaw.navigation.Nav
import com.gpo.cronpaw.ui.components.PawTopBar
import com.gpo.cronpaw.ui.components.rememberToast
import com.gpo.cronpaw.ui.theme.CronSymbols
import com.gpo.cronpaw.ui.theme.YoinMotion
import com.gpo.cronpaw.ui.theme.rememberRichHaptics
import com.gpo.yoin.symbols.YoinSymbols

/**
 * Settings by importance, in groups: where the items come from (the phone's calendars and
 * Notion, as peers), how new items behave, 专注, appearance, about. Tight inside a group, a
 * clear gap and a small title between groups; choices sit inline on their rows. Notion's
 * account and databases have their own page (NotionSettingsScreen).
 */
@Composable
fun SettingsScreen(nav: Nav) {
    val context = LocalContext.current
    val toast = rememberToast()
    val haptics = rememberRichHaptics()
    val cs = MaterialTheme.colorScheme

    val token by AppSettings.notionToken.state.collectAsState()
    val dbs by NotionRepo.databases.collectAsState()
    val elevenKey by AppSettings.elevenLabsKey.state.collectAsState()
    val model by AppSettings.scribeModel.state.collectAsState()
    val quick by AppSettings.quickEntries.state.collectAsState()
    val autoSave by AppSettings.autoSaveSeconds.state.collectAsState()
    val defaultMinutes by AppSettings.defaultMinutes.state.collectAsState()
    val focusMin by FocusSettings.focusMin.state.collectAsState()
    val breakMin by FocusSettings.breakMin.state.collectAsState()
    val longBreakMin by FocusSettings.longBreakMin.state.collectAsState()
    val breath by FocusSettings.breath.state.collectAsState()
    val quietInBreaks by FocusSettings.quietInBreaks.state.collectAsState()
    // Coming back from the system's access page: look again.
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val zenAccess = remember(lifecycle) { FocusZen.hasAccess(context) }
    val themeMode by AppSettings.themeMode.state.collectAsState()
    val dynamic by AppSettings.dynamicColor.state.collectAsState()
    val workspace by AppSettings.notionWorkspace.state.collectAsState()
    val oauthStatus by NotionOAuth.status.collectAsState()
    val uriHandler = LocalUriHandler.current
    val connected = token.isNotBlank()
    var voiceOpen by remember { mutableStateOf(false) }
    var quickOpen by remember { mutableStateOf(false) }
    var calendarsOpen by remember { mutableStateOf(false) }
    val calendars by Agenda.calendars.collectAsState()
    val removedRaw by AppSettings.removedCalendars.state.collectAsState()
    val removed = removedRaw.split(',').mapNotNull { it.toLongOrNull() }.toSet()

    Column(Modifier.fillMaxSize().background(cs.surface)) {
        PawTopBar("设置", onBack = { nav.pop() })
        val scroll = rememberScrollState()
        // Yoin's seam: the page dissolves under the top bar instead of being cut by it.
        Column(Modifier.seamViewport(scroll).verticalScroll(scroll).navigationBarsPadding().padding(horizontal = 12.dp)) {
            // ── 日历: where things come from. The phone's calendars and Notion side by side, each one
            // row; Notion's account and databases live on their own page. ──
            GroupTitle("日历", top = 8.dp)
            Card {
                val off = calendars.count { it.id in removed }
                ListRow(CronSymbols.Calendar, "系统日历", "关掉的不进侧栏、不显示、也不能放进去", onClick = { haptics.tick(); calendarsOpen = !calendarsOpen }) {
                    Text(if (off == 0) "全部" else "关了 $off 个", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    Chevron(calendarsOpen)
                }
                Expand(calendarsOpen) {
                    calendars.groupBy { it.accountName }.forEach { (account, cals) ->
                        Text(
                            account,
                            Modifier.padding(start = 6.dp, top = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        cals.forEach { cal ->
                            val on = cal.id !in removed
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                    .clickable { haptics.tick(); Agenda.setCalendarRemoved(cal.id, on) }
                                    .padding(start = 6.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(Color(cal.color).copy(alpha = if (on) 1f else 0.3f)))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    cal.name,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (on) cs.onSurface else cs.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Switch(checked = on, onCheckedChange = { haptics.tick(); Agenda.setCalendarRemoved(cal.id, !it) })
                            }
                        }
                    }
                }
                ListRow(
                    CronSymbols.Database,
                    "Notion",
                    oauthStatus ?: if (connected) workspace.ifBlank { "已连接" } else "连接后，把数据库放进日历",
                    onClick = {
                        haptics.tick()
                        if (connected) nav.push("notion") else uriHandler.openUri(NotionOAuth.begin().toString())
                    },
                ) {
                    if (connected) {
                        Text("${dbs.size} 个数据库", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        Chevron(false)
                    } else {
                        Text(
                            "连接",
                            Modifier.clip(CircleShape).background(cs.primary).padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = cs.onPrimary,
                        )
                    }
                }
            }

            // ── 新建: how new items behave ──
            GroupTitle("新建")
            Card {
                ListRow(CronSymbols.Calendar, "默认时长", "点时间轴空白处新建 · 分钟") {
                    InlineChoices(listOf(15, 30, 45, 60, 90, 120).map { it to it.toString() }, defaultMinutes) {
                        haptics.tick(); AppSettings.set(AppSettings.defaultMinutes, it)
                    }
                }
                ListRow(YoinSymbols.Send, "自动加入", "确认页不碰屏幕，倒计时后加入") {
                    InlineChoices(listOf(0 to "关", 3 to "3秒", 4 to "4秒", 6 to "6秒"), autoSave) {
                        haptics.tick(); AppSettings.set(AppSettings.autoSaveSeconds, it)
                    }
                }
                ListRow(CronSymbols.Keyboard, "快捷入口", onClick = { haptics.tick(); quickOpen = !quickOpen }) {
                    Text("${QuickEntries.parse(quick).size} 个", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    Chevron(quickOpen)
                }
                Expand(quickOpen) { QuickEntryEditor() }
                ListRow(CronSymbols.Mic, "语音识别", onClick = { haptics.tick(); voiceOpen = !voiceOpen }) {
                    Text(if (elevenKey.isBlank()) "系统" else "Scribe", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    Chevron(voiceOpen)
                }
                Expand(voiceOpen) {
                    Field("ElevenLabs API key", elevenKey, { AppSettings.set(AppSettings.elevenLabsKey, it.trim()) }, secret = true)
                    Hint("空着就用系统语音识别。填了就用 Scribe，按用量向 ElevenLabs 付费。")
                    if (elevenKey.isNotBlank()) Field("模型", model, { AppSettings.set(AppSettings.scribeModel, it.trim()) })
                }
            }

            // ── 专注: the 番茄 rhythm, the breath, CronPaw's own mode ──
            GroupTitle("专注")
            Card {
                val tomato = ImageVector.vectorResource(R.drawable.ic_focus)
                ListRow(tomato, "一轮专注", "分钟") {
                    InlineChoices(listOf(15, 20, 25, 30, 45, 50).map { it to it.toString() }, focusMin) {
                        haptics.tick(); AppSettings.set(FocusSettings.focusMin, it)
                    }
                }
                ListRow(YoinSymbols.Sleep, "休息", "分钟") {
                    InlineChoices(listOf(3, 5, 10).map { it to it.toString() }, breakMin) {
                        haptics.tick(); AppSettings.set(FocusSettings.breakMin, it)
                    }
                }
                ListRow(YoinSymbols.Sleep, "长休息", "每 4 轮一次，分钟") {
                    InlineChoices(listOf(10, 15, 20, 30).map { it to it.toString() }, longBreakMin) {
                        haptics.tick(); AppSettings.set(FocusSettings.longBreakMin, it)
                    }
                }
                ListRow(YoinSymbols.Sparkle, "开始前深呼吸", "约 7 秒，点一下跳过") {
                    Switch(breath, { haptics.tick(); AppSettings.set(FocusSettings.breath, it) })
                }
                ListRow(CronSymbols.Bell, "专注模式", if (zenAccess) "谁能打扰，在系统的模式设置里改" else "要先允许 CronPaw 管理勿扰", onClick = {
                    haptics.tick()
                    val intent = if (zenAccess) FocusZen.settingsIntent(context) ?: FocusZen.accessIntent() else FocusZen.accessIntent()
                    runCatching { context.startActivity(intent) }
                }) {
                    Text(if (zenAccess) "已允许" else "去允许", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    Icon(YoinSymbols.ChevronRight, null, tint = cs.onSurfaceVariant)
                }
                ListRow(YoinSymbols.Sleep, "休息时也开专注模式") {
                    Switch(quietInBreaks, { haptics.tick(); AppSettings.set(FocusSettings.quietInBreaks, it) })
                }
            }

            // ── 外观 ──
            GroupTitle("外观")
            Card {
                ListRow(YoinSymbols.LightMode, "主题") {
                    InlineChoices(listOf(0 to "跟随", 1 to "浅色", 2 to "深色"), themeMode) {
                        haptics.tick(); AppSettings.set(AppSettings.themeMode, it)
                    }
                }
                ListRow(YoinSymbols.Sparkle, "壁纸取色") {
                    Switch(dynamic, { haptics.tick(); AppSettings.set(AppSettings.dynamicColor, it) })
                }
            }

            // ── 关于 ──
            GroupTitle("关于")
            Card {
                ListRow(YoinSymbols.Refresh, "重新读取日历和 Notion", onClick = { haptics.tick(); Agenda.refresh(); toast("已刷新") }) {}
                ListRow(YoinSymbols.Code, "源代码", onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/p2o51/CronPaw"))) }
                }) {
                    Text("GitHub", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    Chevron(false)
                }
                ListRow(YoinSymbols.Info, "版本") {
                    Text(BuildConfig.VERSION_NAME, Modifier.padding(end = 20.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
