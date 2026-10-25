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
 * Settings by importance, in groups: Notion and its databases, how new items behave,
 * appearance, about. Tight inside a group, a clear gap and a small title between groups;
 * choices sit inline on their rows. Disconnecting comes last, on its own.
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
            // ── Notion: where things stand ──
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(cs.primaryContainer)
                    .padding(start = 22.dp, end = 16.dp, top = 20.dp, bottom = 20.dp),
            ) {
                val ink = cs.onPrimaryContainer
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(CronSymbols.Database, null, Modifier.size(18.dp), tint = ink.copy(alpha = 0.7f))
                    Spacer(Modifier.width(6.dp))
                    Text("Notion", style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.7f))
                }
                Text(
                    if (connected) workspace.ifBlank { "已连接" } else "还没连接",
                    Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (connected) "${dbs.size} 个数据库在日历上" else "连接后，在授权页里勾上要放进日历的数据库。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ink.copy(alpha = 0.75f),
                )
                oauthStatus?.let { Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = ink) }
                Spacer(Modifier.height(14.dp))
                Text(
                    if (connected) "重新授权" else "连接 Notion",
                    Modifier
                        .clip(CircleShape)
                        .background(if (connected) ink.copy(alpha = 0.12f) else ink)
                        .clickable { haptics.tick(); uriHandler.openUri(NotionOAuth.begin().toString()) }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (connected) ink else cs.primaryContainer,
                )
            }

            // Its databases right under it, as tiles, with + at the end — only once connected.
            if (connected) Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                dbs.forEach { db ->
                    Tile(Modifier.alpha(if (db.enabled) 1f else 0.5f), db.title, onClick = { haptics.tick(); nav.push("db:${db.id}") }) {
                        Box(
                            Modifier.fillMaxSize().background(Color(db.color).copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center,
                        ) { Box(Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(Color(db.color))) }
                    }
                }
                Tile(Modifier, "添加", onClick = { haptics.tick(); nav.push("db:") }) {
                    Box(Modifier.fillMaxSize().background(cs.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                        Icon(YoinSymbols.Add, null, tint = cs.onSurfaceVariant)
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
                    Text("${quick.lines().count { it.isNotBlank() }} 个", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    Chevron(quickOpen)
                }
                Expand(quickOpen) {
                    Field("每行一个：标题|分钟", quick, { AppSettings.set(AppSettings.quickEntries, it) }, singleLine = false)
                }
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

            // ── 日历: which system calendars CronPaw uses at all ──
            GroupTitle("日历")
            Card {
                val off = calendars.count { it.id in removed }
                ListRow(CronSymbols.Calendar, "用哪些日历", "关掉的不进侧栏、不显示、也不能放进去", onClick = { haptics.tick(); calendarsOpen = !calendarsOpen }) {
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

            // Disconnecting is rare and drastic: last, on its own, quiet red.
            if (connected) {
                Text(
                    "断开 Notion",
                    Modifier
                        .padding(top = 24.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(CircleShape)
                        .clickable { NotionOAuth.disconnect(); Agenda.refresh(); toast("已断开") }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = cs.error,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** Groups are 24dp apart under a small title; rows inside a group touch. */
@Composable
private fun GroupTitle(text: String) {
    Text(
        text,
        Modifier.padding(start = 20.dp, top = 32.dp, bottom = 8.dp).seamText(),
        style = MaterialTheme.typography.labelMedium,
        letterSpacing = androidx.compose.ui.unit.TextUnit(1f, androidx.compose.ui.unit.TextUnitType.Sp),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().seamGraphic().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(vertical = 4.dp),
    ) { content() }
}

@Composable
private fun Tile(modifier: Modifier, label: String, onClick: () -> Unit, face: @Composable () -> Unit) {
    Column(modifier.width(72.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(60.dp).clip(RoundedCornerShape(18.dp))) { face() }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Icon, title (and a quiet line under it), then whatever the row holds on the right. */
@Composable
private fun ListRow(
    icon: ImageVector,
    title: String,
    hint: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 18.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).seamText()) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (hint != null) Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

/** Plain words in a line, the chosen one coloured — like a player's speed row. */
@Composable
private fun <T> InlineChoices(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        options.forEachIndexed { i, (v, label) ->
            val on = v == selected
            val ink by animateColorAsState(if (on) cs.primary else cs.onSurfaceVariant, YoinMotion.defaultEffectsSpec(), label = "choice")
            Text(
                label,
                Modifier.clip(RoundedCornerShape(10.dp)).clickable { onSelect(v) }
                    .padding(start = 7.dp, end = if (i == options.lastIndex) 0.dp else 7.dp, top = 8.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                color = ink,
            )
        }
    }
}

@Composable
private fun Chevron(open: Boolean) {
    val turn by animateFloatAsState(if (open) 90f else 0f, YoinMotion.defaultSpatialSpec(), label = "chevron")
    Icon(YoinSymbols.ChevronRight, null, Modifier.size(20.dp).graphicsLayer { rotationZ = turn }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Expand(open: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        open,
        enter = expandVertically(YoinMotion.defaultSpatialSpec()) + fadeIn(YoinMotion.defaultEffectsSpec()),
        exit = shrinkVertically(YoinMotion.fastSpatialSpec()) + fadeOut(YoinMotion.fastEffectsSpec()),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}
