package com.gpo.cronpaw.feature.settings

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
import com.gpo.cronpaw.navigation.Nav
import com.gpo.cronpaw.ui.components.PawTopBar
import com.gpo.cronpaw.ui.components.rememberToast
import com.gpo.cronpaw.ui.theme.CronSymbols
import com.gpo.cronpaw.ui.theme.YoinMotion
import com.gpo.cronpaw.ui.theme.rememberRichHaptics
import com.gpo.yoin.symbols.YoinSymbols

/**
 * Settings in layers, each a different shape: the Notion connection as a large colour
 * block, a row of round colour buttons for one-tap actions, the databases as square tiles,
 * then one card of rows whose choices sit inline on the row itself.
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
    val themeMode by AppSettings.themeMode.state.collectAsState()
    val dynamic by AppSettings.dynamicColor.state.collectAsState()
    val workspace by AppSettings.notionWorkspace.state.collectAsState()
    val oauthStatus by NotionOAuth.status.collectAsState()
    val uriHandler = LocalUriHandler.current
    val connected = token.isNotBlank()
    var voiceOpen by remember { mutableStateOf(false) }
    var quickOpen by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(cs.surface)) {
        PawTopBar("设置", onBack = { nav.pop() })
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 12.dp)) {
            // ── Notion: where things stand ──
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(if (connected) cs.primaryContainer else cs.tertiaryContainer)
                    .padding(start = 22.dp, end = 16.dp, top = 20.dp, bottom = 20.dp),
            ) {
                val ink = if (connected) cs.onPrimaryContainer else cs.onTertiaryContainer
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
            }

            // ── One tap: round colour buttons ──
            Row(
                Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                RoundButton(YoinSymbols.Launch, if (connected) "重新授权" else "连接", cs.primary, cs.onPrimary) {
                    haptics.tick(); uriHandler.openUri(NotionOAuth.begin().toString())
                }
                RoundButton(YoinSymbols.Add, "添加数据库", cs.secondaryContainer, cs.onSecondaryContainer) {
                    haptics.tick(); nav.push("db:")
                }
                RoundButton(YoinSymbols.Refresh, "重新读取", cs.tertiaryContainer, cs.onTertiaryContainer) {
                    haptics.tick(); Agenda.refresh(); toast("已刷新")
                }
                RoundButton(YoinSymbols.Code, "源代码", cs.surfaceContainerHighest, cs.onSurfaceVariant) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/p2o51/CronPaw"))) }
                }
            }

            // ── Databases: square tiles ──
            if (dbs.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    dbs.forEach { db ->
                        Column(
                            Modifier.width(76.dp).alpha(if (db.enabled) 1f else 0.5f).clickable { haptics.tick(); nav.push("db:${db.id}") },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(Color(db.color).copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(Color(db.color)))
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(db.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // ── Rows, choices inline ──
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(cs.surfaceContainer)
                    .padding(vertical = 6.dp),
            ) {
                ListRow(CronSymbols.Calendar, "新建时长", "点时间轴空白处，默认这么长 · 分钟") {
                    InlineChoices(listOf(15, 30, 45, 60, 90, 120).map { it to it.toString() }, defaultMinutes) {
                        haptics.tick(); AppSettings.set(AppSettings.defaultMinutes, it)
                    }
                }
                ListRow(YoinSymbols.Send, "自动加入", "确认页不碰屏幕，倒计时结束就加入 · 秒") {
                    InlineChoices(listOf(0 to "不", 3 to "3", 4 to "4", 6 to "6"), autoSave) {
                        haptics.tick(); AppSettings.set(AppSettings.autoSaveSeconds, it)
                    }
                }
                ListRow(YoinSymbols.LightMode, "主题") {
                    InlineChoices(listOf(0 to "跟随", 1 to "浅色", 2 to "深色"), themeMode) {
                        haptics.tick(); AppSettings.set(AppSettings.themeMode, it)
                    }
                }
                ListRow(YoinSymbols.Sparkle, "壁纸取色") {
                    Switch(dynamic, { haptics.tick(); AppSettings.set(AppSettings.dynamicColor, it) })
                }
                ListRow(
                    CronSymbols.Mic, "语音",
                    onClick = { haptics.tick(); voiceOpen = !voiceOpen },
                ) {
                    Text(if (elevenKey.isBlank()) "系统识别" else "Scribe", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    Chevron(voiceOpen)
                }
                Expand(voiceOpen) {
                    Field("ElevenLabs API key", elevenKey, { AppSettings.set(AppSettings.elevenLabsKey, it.trim()) }, secret = true)
                    Hint("空着就用系统语音识别。填了就用 Scribe，按用量向 ElevenLabs 付费。")
                    if (elevenKey.isNotBlank()) Field("模型", model, { AppSettings.set(AppSettings.scribeModel, it.trim()) })
                }
                ListRow(
                    CronSymbols.Keyboard, "快捷入口",
                    onClick = { haptics.tick(); quickOpen = !quickOpen },
                ) {
                    Text("${quick.lines().count { it.isNotBlank() }} 个", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    Chevron(quickOpen)
                }
                Expand(quickOpen) {
                    Field("每行一个：标题|分钟", quick, { AppSettings.set(AppSettings.quickEntries, it) }, singleLine = false)
                }
                if (connected) {
                    ListRow(YoinSymbols.Close, "断开 Notion", onClick = { NotionOAuth.disconnect(); Agenda.refresh(); toast("已断开") }) {}
                }
            }

            Text(
                "CronPaw ${BuildConfig.VERSION_NAME}",
                Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 32.dp),
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, bg: Color, ink: Color, onClick: () -> Unit) {
    Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(24.dp), tint = ink)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 18.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
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
        options.forEach { (v, label) ->
            val on = v == selected
            val ink by animateColorAsState(if (on) cs.primary else cs.onSurfaceVariant, YoinMotion.defaultEffectsSpec(), label = "choice")
            Text(
                label,
                Modifier.clip(RoundedCornerShape(10.dp)).clickable { onSelect(v) }.padding(horizontal = 7.dp, vertical = 8.dp),
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
