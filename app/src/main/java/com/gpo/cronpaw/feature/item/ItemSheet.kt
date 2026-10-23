package com.gpo.cronpaw.feature.item

import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gpo.cronpaw.core.agenda.Actions
import com.gpo.cronpaw.core.agenda.Agenda
import com.gpo.cronpaw.core.agenda.AgendaItem
import com.gpo.cronpaw.core.agenda.Pins
import com.gpo.cronpaw.core.agenda.Source
import com.gpo.cronpaw.core.notion.NotionRepo
import com.gpo.cronpaw.core.notion.PropValue
import com.gpo.cronpaw.feature.home.TodoDot
import com.gpo.cronpaw.ui.components.rememberToast
import com.gpo.cronpaw.ui.theme.CronSymbols
import com.gpo.cronpaw.ui.theme.pawIsDark
import com.gpo.cronpaw.ui.theme.rememberRichHaptics
import com.gpo.cronpaw.ui.time.Times
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.launch

/**
 * One item. By default only what matters on a phone: when, and (Notion) the status.
 * 「⋯」 lists every property with an eye to show or hide it; the choice is per database.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ItemSheet(item: AgendaItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberRichHaptics()
    val toast = rememberToast()
    val cs = MaterialTheme.colorScheme
    val pins by Pins.keys.collectAsState()
    val dbs by NotionRepo.databases.collectAsState()
    val calendars by Agenda.calendars.collectAsState()
    val items by Agenda.items.collectAsState()
    // Follow the live item (a ticked todo, a reload) while the sheet is open.
    val live = items.firstOrNull { it.key == item.key } ?: item
    val notion = live.source as? Source.Notion
    val db = notion?.let { n -> dbs.firstOrNull { it.id == n.databaseId } }
    var props by remember { mutableStateOf<List<PropValue>>(emptyList()) }
    var showAll by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(live.key, live.done) {
        if (db != null && notion != null && notion.pageId.isNotEmpty()) props = NotionRepo.properties(db, notion.pageId)
    }
    val dark = pawIsDark()

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = cs.surfaceContainerLow) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (live.todo) {
                    TodoDot(live.done, Color(live.color), cs.onSurface, {
                        haptics.tick()
                        scope.launch { if (!Actions.setDone(live, !live.done)) toast("没改成") else if (!live.done) haptics.chargeFull() }
                    }, size = 26.dp)
                } else {
                    Box(Modifier.size(12.dp, 28.dp).clip(RoundedCornerShape(6.dp)).background(Color(live.color)))
                }
                Spacer(Modifier.width(12.dp))
                Text(live.title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))

            // 时间
            Block {
                Label("时间")
                Text(Times.dayLabel(Times.date(live.start)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (live.allDay) "全天" else "${Times.hm(live.start)}  ${Times.hm(live.end)}  ${Times.durationText(live.durationMinutes)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                live.location?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant) }
            }

            // 来源
            when (val src = live.source) {
                is Source.Calendar -> calendars.firstOrNull { it.id == src.calendarId }?.let { cal ->
                    Block {
                        Label("日历")
                        Text(cal.name, style = MaterialTheme.typography.titleSmall)
                        Text(cal.accountName, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                    }
                }
                is Source.Notion -> Unit
            }

            // 状态 + the properties the user chose to show
            if (db != null) {
                val status = props.firstOrNull { it.name == db.statusProp }
                if (status != null && status.type != "checkbox") {
                    Block {
                        Label(status.name)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            status.options.forEach { opt ->
                                val selected = opt.name == status.text
                                val (bg, ink) = NotionRepo.optionColors(opt.color, dark)
                                Text(
                                    opt.name,
                                    Modifier
                                        .clip(CircleShape)
                                        .background(if (selected) Color(bg) else cs.surfaceContainerHighest)
                                        .clickable {
                                            haptics.tick()
                                            scope.launch {
                                                runCatching { NotionRepo.setOption(notion.pageId, status.name, status.type, opt.name) }
                                                    .onSuccess {
                                                        props = NotionRepo.properties(db, notion.pageId)
                                                        Agenda.afterWrite()
                                                    }
                                                    .onFailure { toast("没改成") }
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) Color(ink) else cs.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                props.filter { it.name in db.visibleProps && it.name != db.statusProp && it.name != db.dateProp }.forEach { p ->
                    Block {
                        Label(p.name)
                        Text(p.text.ifEmpty { "空" }, style = MaterialTheme.typography.bodyLarge, color = if (p.text.isEmpty()) cs.onSurfaceVariant else cs.onSurface)
                    }
                }
                AnimatedVisibility(showAll, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Block {
                        Label("显示的属性")
                        props.filter { it.name != db.statusProp && it.name != db.dateProp }.forEach { p ->
                            val visible = p.name in db.visibleProps
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        haptics.tick()
                                        NotionRepo.update(db.id) {
                                            it.copy(visibleProps = if (visible) it.visibleProps - p.name else it.visibleProps + p.name)
                                        }
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (visible) cs.onSurface else cs.onSurfaceVariant)
                                Icon(
                                    if (visible) YoinSymbols.Visibility else YoinSymbols.VisibilityOff,
                                    if (visible) "隐藏" else "显示",
                                    tint = if (visible) cs.primary else cs.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val pinned = live.key in pins
                FilledTonalButton(onClick = { haptics.tick(); Pins.toggle(live.key) }) {
                    Icon(if (pinned) CronSymbols.PinFilled else CronSymbols.Pin, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (pinned) "已置顶" else "置顶")
                }
                Spacer(Modifier.width(8.dp))
                if (notion != null && notion.url.isNotEmpty()) {
                    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(notion.url))) } }) {
                        Text("在 Notion 打开")
                    }
                }
                val cal = live.source as? Source.Calendar
                if (cal != null && cal.eventId > 0) {
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, cal.eventId))
                                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, live.start)
                                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, live.end),
                            )
                        }
                    }) { Text("日历中打开") }
                }
                Spacer(Modifier.weight(1f))
                if (db != null) {
                    IconButton(onClick = { haptics.tick(); showAll = !showAll }) {
                        Icon(YoinSymbols.MoreHorizontal, "属性", tint = if (showAll) cs.primary else cs.onSurfaceVariant)
                    }
                }
                IconButton(onClick = {
                    if (!confirmDelete) { haptics.tick(); confirmDelete = true; return@IconButton }
                    haptics.lowTick()
                    scope.launch {
                        if (Actions.delete(context, live)) { toast("已删除"); onDismiss() } else toast("删不掉")
                    }
                }) {
                    Icon(YoinSymbols.Delete, if (confirmDelete) "再点一次删除" else "删除", tint = if (confirmDelete) cs.error else cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Block(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) { content() }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
}
