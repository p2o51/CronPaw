package com.gpo.cronpaw.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gpo.cronpaw.core.agenda.Agenda
import com.gpo.cronpaw.core.notion.NotionOAuth
import com.gpo.cronpaw.core.notion.NotionRepo
import com.gpo.cronpaw.core.settings.AppSettings
import com.gpo.cronpaw.navigation.Nav
import com.gpo.cronpaw.ui.components.PawTopBar
import com.gpo.cronpaw.ui.components.rememberToast
import com.gpo.cronpaw.ui.components.seam.seamGraphic
import com.gpo.cronpaw.ui.components.seam.seamViewport
import com.gpo.cronpaw.ui.theme.CronSymbols
import com.gpo.cronpaw.ui.theme.rememberRichHaptics
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.delay

/**
 * Notion, a peer of the phone's calendars in 设置: the account (re-authorise, disconnect, side by
 * side under its name) and the databases on the calendar, with + at the end. Disconnecting
 * asks for a second tap instead of a dialog.
 */
@Composable
fun NotionSettingsScreen(nav: Nav) {
    val cs = MaterialTheme.colorScheme
    val haptics = rememberRichHaptics()
    val toast = rememberToast()
    val uriHandler = LocalUriHandler.current
    val token by AppSettings.notionToken.state.collectAsState()
    val workspace by AppSettings.notionWorkspace.state.collectAsState()
    val oauthStatus by NotionOAuth.status.collectAsState()
    val dbs by NotionRepo.databases.collectAsState()
    var confirming by remember { mutableStateOf(false) }
    LaunchedEffect(confirming) {
        if (confirming) {
            delay(3_000)
            confirming = false
        }
    }
    // Disconnected (here or by a failed refresh): nothing left to show on this page.
    LaunchedEffect(token) { if (token.isBlank()) nav.pop() }

    Column(Modifier.fillMaxSize().background(cs.surface)) {
        PawTopBar("Notion", onBack = { nav.pop() })
        val scroll = rememberScrollState()
        // On a tablet the rows keep a phone's reach, centred: titles and values stay near each other.
        Column(
            Modifier.seamViewport(scroll).verticalScroll(scroll).navigationBarsPadding()
                .fillMaxWidth().wrapContentWidth().widthIn(max = 640.dp).padding(horizontal = 12.dp),
        ) {
            // ── The account ──
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .seamGraphic()
                    .clip(RoundedCornerShape(28.dp))
                    .background(cs.surfaceContainer)
                    .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 16.dp),
            ) {
                Text(
                    workspace.ifBlank { "已连接" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${dbs.size} 个数据库在日历上", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                oauthStatus?.let { Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "重新授权",
                        Modifier
                            .clip(CircleShape)
                            .background(cs.primary)
                            .clickable { haptics.tick(); uriHandler.openUri(NotionOAuth.begin().toString()) }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = cs.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (confirming) "再点一下断开" else "断开",
                        Modifier
                            .clip(CircleShape)
                            .background(if (confirming) cs.errorContainer else Color.Transparent)
                            .clickable {
                                haptics.tick()
                                if (!confirming) {
                                    confirming = true
                                } else {
                                    NotionOAuth.disconnect()
                                    Agenda.refresh()
                                    toast("已断开 Notion")
                                }
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (confirming) cs.onErrorContainer else cs.error,
                    )
                }
            }

            // ── Its databases ──
            GroupTitle("数据库")
            Card {
                dbs.forEach { db ->
                    ListRow(CronSymbols.Database, db.title, if (db.enabled) null else "已停用，不在日历上", onClick = { haptics.tick(); nav.push("db:${db.id}") }) {
                        Box(Modifier.size(14.dp).clip(RoundedCornerShape(5.dp)).background(Color(db.color).copy(alpha = if (db.enabled) 1f else 0.35f)))
                        Spacer(Modifier.width(4.dp))
                        Chevron(false)
                    }
                }
                ListRow(YoinSymbols.Add, "添加数据库", onClick = { haptics.tick(); nav.push("db:") }) {}
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
