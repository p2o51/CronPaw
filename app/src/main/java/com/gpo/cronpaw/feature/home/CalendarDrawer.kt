package com.gpo.cronpaw.feature.home

import androidx.compose.foundation.lazy.rememberLazyListState
import com.gpo.cronpaw.ui.components.seam.seamGraphic
import com.gpo.cronpaw.ui.components.seam.seamText
import com.gpo.cronpaw.ui.components.seam.seamViewport
import androidx.compose.material3.IconButton
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gpo.cronpaw.core.agenda.Agenda
import com.gpo.cronpaw.core.notion.NotionRepo
import com.gpo.cronpaw.core.settings.AppSettings
import com.gpo.cronpaw.ui.theme.YoinMotion
import com.gpo.cronpaw.ui.theme.rememberRichHaptics
import com.gpo.yoin.symbols.YoinSymbols

/**
 * View length, every system calendar by account, and the Notion databases — each a
 * colour block that is filled when shown. Counts are 未排期 pages.
 */
@Composable
fun CalendarDrawer(
    permanent: Boolean,
    viewDays: Int,
    onViewDays: (Int) -> Unit,
    onAddDatabase: () -> Unit,
    onSettings: () -> Unit,
    onUnscheduled: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptics = rememberRichHaptics()
    val calendars by Agenda.calendars.collectAsState()
    val hiddenRaw by AppSettings.hiddenCalendars.state.collectAsState()
    val hidden = hiddenRaw.split(',').mapNotNull { it.toLongOrNull() }.toSet()
    val removedRaw by AppSettings.removedCalendars.state.collectAsState()
    val removed = removedRaw.split(',').mapNotNull { it.toLongOrNull() }.toSet()
    val dbs by NotionRepo.databases.collectAsState()
    val unscheduled by Agenda.unscheduled.collectAsState()

    val sheet: @Composable (@Composable () -> Unit) -> Unit = { content ->
        if (permanent) PermanentDrawerSheet(Modifier.width(300.dp), drawerContainerColor = cs.surfaceContainerLow) { content() }
        else ModalDrawerSheet(drawerContainerColor = cs.surfaceContainerLow) { content() }
    }
    sheet {
        val list = rememberLazyListState()
        LazyColumn(Modifier.statusBarsPadding().navigationBarsPadding().seamViewport(list), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
            item {
                // The name, and 设置 across from it: always in reach, not at the end of a long list.
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("CronPaw", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { haptics.tick(); onSettings() }) { Icon(YoinSymbols.Settings, "设置") }
                }
                Row(
                    Modifier.fillMaxWidth().seamGraphic().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainer).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(1 to "1天", 3 to "3天", 7 to "周", AppSettings.ViewMonth to "月", AppSettings.ViewSchedule to "日程").forEach { (n, label) ->
                        val on = n == viewDays
                        val bg by animateColorAsState(if (on) cs.primary else Color.Transparent, YoinMotion.defaultEffectsSpec(), label = "days")
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(16.dp)).background(bg)
                                .clickable { haptics.tick(); onViewDays(n) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (on) cs.onPrimary else cs.onSurface,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            val byAccount = calendars.filter { it.id !in removed }.groupBy { it.accountName }
            byAccount.forEach { (account, cals) ->
                item(key = "acc:$account") {
                    SectionLabel(account)
                }
                items(cals, key = { "cal:${it.id}" }) { cal ->
                    val shown = cal.id !in hidden
                    ToggleRow(
                        color = Color(cal.color),
                        shown = shown,
                        title = cal.name,
                        trailing = null,
                        onTrailing = {},
                        onClick = { haptics.tick(); Agenda.setCalendarHidden(cal.id, shown) },
                    )
                }
            }

            item(key = "notion") { SectionLabel("Notion") }
            items(dbs, key = { "db:${it.id}" }) { db ->
                ToggleRow(
                    color = Color(db.color),
                    shown = db.enabled,
                    title = db.title,
                    trailing = unscheduled[db.id]?.takeIf { it > 0 }?.toString(),
                    onTrailing = { haptics.tick(); onUnscheduled(db.id) },
                    onClick = {
                        haptics.tick()
                        NotionRepo.update(db.id) { it.copy(enabled = !it.enabled) }
                        Agenda.refresh()
                    },
                )
            }
            item(key = "add") {
                ActionRow(YoinSymbols.Add, "添加数据库", onAddDatabase)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp).seamText(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ToggleRow(color: Color, shown: Boolean, title: String, trailing: String?, onTrailing: () -> Unit, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (shown) color else color.copy(alpha = 0.18f), YoinMotion.defaultEffectsSpec(), label = "cal")
    Row(
        Modifier
            .fillMaxWidth()
            .seamText()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(fill), contentAlignment = Alignment.Center) {
            if (shown) Icon(YoinSymbols.Check, null, Modifier.size(14.dp), tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (shown) cs.onSurface else cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null) {
            Text(
                trailing,
                // 未排期: tap the count to lay those pages out in a tray.
                Modifier.clip(CircleShape).background(cs.secondaryContainer).clickable(onClick = onTrailing).padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().seamText().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
