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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.gpo.cronpaw.core.focus.FocusController
import com.gpo.cronpaw.core.focus.FocusStore
import com.gpo.cronpaw.R
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.gpo.cronpaw.core.agenda.Source
import com.gpo.cronpaw.core.notion.NotionRepo
import com.gpo.cronpaw.core.notion.PropValue
import com.gpo.cronpaw.feature.home.TodoDot
import com.gpo.cronpaw.feature.home.itemColors
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
    val focus by FocusStore.session.collectAsState()
    // Follow the live item (a ticked todo, a reload) while the sheet is open.
    val live = items.firstOrNull { it.key == item.key } ?: item
    val notion = live.source as? Source.Notion
    val db = notion?.let { n -> dbs.firstOrNull { it.id == n.databaseId } }
    var props by remember { mutableStateOf<List<PropValue>>(emptyList()) }
    var picked by remember { mutableStateOf<String?>(null) }
    var showAll by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(live.key, live.done) {
        if (db != null && notion != null && notion.pageId.isNotEmpty()) props = NotionRepo.properties(db, notion.pageId)
    }
    val dark = pawIsDark()

    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = cs.surfaceContainerLow) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            // The item in its own colours: title, then when, the start large and the rest quiet.
            val (heroBg, heroInk) = itemColors(live)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(heroBg)
                    .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 18.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(live.title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = heroInk)
                    if (live.todo) {
                        Spacer(Modifier.width(12.dp))
                        TodoDot(live.done, Color(live.color), heroInk, {
                            haptics.tick()
                            scope.launch { if (!Actions.setDone(live, !live.done)) toast("没改成") else if (!live.done) haptics.chargeFull() }
                        }, size = 28.dp)
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text(Times.dayLabel(Times.date(live.start)), style = MaterialTheme.typography.labelLarge, color = heroInk.copy(alpha = 0.75f))
                if (live.allDay) {
                    Text("全天", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = heroInk)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(Times.hm(live.start), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = heroInk)
                        Text(
                            "到 ${Times.hm(live.end)} · ${Times.durationText(live.durationMinutes)}",
                            Modifier.padding(start = 8.dp, bottom = 7.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = heroInk.copy(alpha = 0.75f),
                        )
                    }
                }
                live.location?.let { Text(it, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = heroInk.copy(alpha = 0.75f)) }
                // Where it lives belongs to what it is, so it stays inside the block.
                val origin = when (val src = live.source) {
                    is Source.Calendar -> calendars.firstOrNull { it.id == src.calendarId }?.let { cal ->
                        // The account only when two calendars share the name; otherwise it is noise (and an email).
                        cal.name to (if (calendars.count { it.name == cal.name } > 1) cal.accountName else "")
                    }
                    is Source.Notion -> db?.let { it.title to "" }
                }
                origin?.let { (name, account) ->
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(heroInk.copy(alpha = 0.6f)))
                        Spacer(Modifier.width(8.dp))
                        Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = heroInk)
                        if (account.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Text(account, style = MaterialTheme.typography.labelMedium, color = heroInk.copy(alpha = 0.7f), maxLines = 1)
                        }
                    }
                }
            }
            // What you do with it, right under it: one row of pills in one colour family,
            // ⋯ and delete at the end (delete asks twice).
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // The pills scroll when they don't fit a phone's width; ⋯ and delete stay put.
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // 专注 comes first, filled: the reason to open an item right before doing it.
                    if (!live.allDay) {
                        val running = focus != null
                        PillAction(ImageVector.vectorResource(R.drawable.ic_focus), if (running) "专注中" else "专注", on = true) {
                            haptics.tick()
                            scope.launch {
                                runCatching { sheet.hide() }
                                onDismiss()
                                if (running) FocusController.openPage.value = true else FocusController.asked.value = live
                            }
                        }
                    }
                    val pinned = live.key in pins
                    PillAction(if (pinned) CronSymbols.PinFilled else CronSymbols.Pin, if (pinned) "已置顶" else "置顶", on = pinned) {
                        haptics.tick(); Pins.toggle(live.key)
                    }
                    val cal = live.source as? Source.Calendar
                    if (notion != null && notion.url.isNotEmpty()) {
                        PillAction(YoinSymbols.Launch, "在 Notion 打开", on = false) {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(notion.url))) }
                        }
                    } else if (cal != null && cal.eventId > 0) {
                        PillAction(YoinSymbols.Launch, "在日历打开", on = false) {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, cal.eventId))
                                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, live.start)
                                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, live.end),
                                )
                            }
                        }
                    }
                }
                if (db != null) {
                    IconButton(onClick = { haptics.tick(); showAll = !showAll }) {
                        Icon(YoinSymbols.MoreHorizontal, "显示哪些属性", tint = if (showAll) cs.primary else cs.onSurfaceVariant)
                    }
                }
                val delBg by androidx.compose.animation.animateColorAsState(
                    if (confirmDelete) cs.error else Color.Transparent, com.gpo.cronpaw.ui.theme.YoinMotion.defaultEffectsSpec(), label = "del",
                )
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(delBg).clickable {
                        if (!confirmDelete) { haptics.tick(); confirmDelete = true; return@clickable }
                        haptics.lowTick()
                        scope.launch {
                            if (Actions.delete(context, live)) {
                                // Slide away first, so it doesn't just blink out.
                                runCatching { sheet.hide() }
                                toast("已删除")
                                onDismiss()
                            } else toast("删不掉")
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(YoinSymbols.Delete, if (confirmDelete) "再点一次删除" else "删除", tint = if (confirmDelete) cs.onError else cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))

            // 状态 + the properties the user chose to show
            if (db != null) {
                val status = props.firstOrNull { it.name == db.statusProp }
                if (status != null && status.type != "checkbox") {
                    // Show the choice at once: a tapped chip, or the todo ticked above, before Notion answers.
                    val shown = picked ?: when {
                        db.doneValue == null -> status.text
                        live.done -> db.doneValue
                        status.text == db.doneValue -> db.todoValue ?: status.text
                        else -> status.text
                    }
                    Block {
                        Label(status.name)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            status.options.forEach { opt ->
                                val selected = opt.name == shown
                                val (bg, ink) = NotionRepo.optionColors(opt.color, dark)
                                Text(
                                    opt.name,
                                    Modifier
                                        .clip(CircleShape)
                                        .background(if (selected) Color(bg) else cs.surfaceContainerHighest)
                                        .clickable {
                                            haptics.tick()
                                            val before = live
                                            picked = opt.name
                                            if (db.doneValue != null) Agenda.applyLocal(before.copy(done = opt.name == db.doneValue))
                                            scope.launch {
                                                runCatching { NotionRepo.setOption(notion.pageId, status.name, status.type, opt.name) }
                                                    .onSuccess {
                                                        props = NotionRepo.properties(db, notion.pageId)
                                                        picked = null
                                                        Agenda.afterWrite()
                                                    }
                                                    .onFailure {
                                                        picked = null
                                                        Agenda.applyLocal(before)
                                                        toast("没改成")
                                                    }
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

@Composable
private fun PillAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.height(44.dp).clip(CircleShape).background(if (on) cs.primary else cs.secondaryContainer).clickable(onClick = onClick)
            .padding(start = 14.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (on) cs.onPrimary else cs.onSecondaryContainer
        Icon(icon, null, Modifier.size(18.dp), tint = ink)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
    }
}
