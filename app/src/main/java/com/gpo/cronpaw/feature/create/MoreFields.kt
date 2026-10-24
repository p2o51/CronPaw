package com.gpo.cronpaw.feature.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.gpo.cronpaw.core.agenda.CalendarRepo
import com.gpo.cronpaw.core.agenda.Target
import com.gpo.cronpaw.core.notion.NotionClient
import com.gpo.cronpaw.core.notion.NotionRepo
import com.gpo.cronpaw.core.notion.PropSchema
import com.gpo.cronpaw.ui.theme.CronSymbols
import com.gpo.cronpaw.ui.theme.pawIsDark
import com.gpo.cronpaw.ui.theme.rememberRichHaptics
import com.gpo.yoin.symbols.YoinSymbols
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/** Notion property choices: property → (type, chosen option names or page ids). */
typealias NotionExtras = Map<String, Pair<String, List<String>>>

/**
 * Everything past title and time, for where the item is going: a calendar event's place,
 * notes, reminders, repeat and colour; a Notion page's select, multi-select and relation
 * properties (项目 and the like).
 */
@Composable
fun MoreFields(
    target: Target?,
    start: Long,
    notion: NotionExtras,
    onNotion: (NotionExtras) -> Unit,
    event: CalendarRepo.EventExtras,
    onEvent: (CalendarRepo.EventExtras) -> Unit,
) {
    when (target) {
        is Target.Calendar -> EventFields(target, start, event, onEvent)
        is Target.Notion -> NotionFields(target, notion, onNotion)
        null -> Unit
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventFields(target: Target.Calendar, start: Long, event: CalendarRepo.EventExtras, onEvent: (CalendarRepo.EventExtras) -> Unit) {
    val context = LocalContext.current
    val haptics = rememberRichHaptics()
    var colors by remember(target.info.id) { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    LaunchedEffect(target.info.id) { colors = CalendarRepo.eventColors(context, target.info.id) }
    val dow = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).dayOfWeek

    // One list, most used first, every row the same shape: icon, what it is, what's set.
    // Choices open a menu from their row instead of spilling chips across the sheet.
    Card {
        PickRow(
            icon = { Icon(CronSymbols.Bell, null) },
            label = "提醒",
            value = if (event.reminders.isEmpty()) "不提醒" else event.reminders.joinToString("、") { reminderLabel(it) + if (it == 0) "" else "前" },
            set = event.reminders.isNotEmpty(),
        ) { close ->
            Item("不提醒", event.reminders.isEmpty()) { haptics.tick(); onEvent(event.copy(reminders = emptyList())); close() }
            listOf(0, 5, 10, 30, 60, 1440).forEach { m ->
                val on = m in event.reminders
                // Several reminders are allowed, so the menu stays open while ticking them.
                Item(if (m == 0) "准时" else reminderLabel(m) + "前", on) {
                    haptics.tick()
                    onEvent(event.copy(reminders = if (on) event.reminders - m else (event.reminders + m).sorted()))
                }
            }
        }
        FieldRow(CronSymbols.Place, event.location, { onEvent(event.copy(location = it)) }, "地点", singleLine = true)
        PickRow(
            icon = { Icon(YoinSymbols.Repeat, null) },
            label = "重复",
            value = event.rrule?.let { repeatLabel(it, dow) } ?: "不重复",
            set = event.rrule != null,
        ) { close ->
            listOf(
                null to "不重复",
                "FREQ=DAILY" to "每天",
                "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR" to "工作日",
                "FREQ=WEEKLY;BYDAY=${byDay(dow)}" to "每${weekdayName(dow)}",
                "FREQ=MONTHLY" to "每月",
                "FREQ=YEARLY" to "每年",
            ).forEach { (rule, label) ->
                Item(label, event.rrule == rule) { haptics.tick(); onEvent(event.copy(rrule = rule)); close() }
            }
        }
        if (colors.isNotEmpty()) {
            val current = event.colorKey?.let { k -> colors.firstOrNull { it.first == k }?.second } ?: target.info.color
            PickRow(
                icon = { Box(Modifier.size(18.dp).clip(CircleShape).background(Color(current))) },
                label = "颜色",
                value = if (event.colorKey == null) "跟日历" else "自选",
                set = event.colorKey != null,
            ) { close ->
                FlowRow(
                    Modifier.width(232.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ColorDot(Color(target.info.color), event.colorKey == null) { haptics.tick(); onEvent(event.copy(colorKey = null)); close() }
                    colors.forEach { (key, argb) ->
                        ColorDot(Color(argb), event.colorKey == key) { haptics.tick(); onEvent(event.copy(colorKey = key)); close() }
                    }
                }
                Text(
                    "第一个是日历本身的颜色",
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FieldRow(YoinSymbols.Note, event.notes, { onEvent(event.copy(notes = it)) }, "备注", singleLine = false)
    }
}

private fun reminderLabel(m: Int) = when {
    m == 0 -> "准时"
    m % 1440 == 0 -> "${m / 1440}天"
    m % 60 == 0 -> "${m / 60}小时"
    else -> "${m}分钟"
}

private fun repeatLabel(rule: String, dow: DayOfWeek) = when {
    rule == "FREQ=DAILY" -> "每天"
    rule.endsWith("MO,TU,WE,TH,FR") -> "工作日"
    rule.startsWith("FREQ=WEEKLY") -> "每${weekdayName(dow)}"
    rule == "FREQ=MONTHLY" -> "每月"
    rule == "FREQ=YEARLY" -> "每年"
    else -> "重复"
}

private fun byDay(d: DayOfWeek) = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")[d.value - 1]
private fun weekdayName(d: DayOfWeek) = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[d.value - 1]

@Composable
private fun NotionFields(target: Target.Notion, chosen: NotionExtras, onChosen: (NotionExtras) -> Unit) {
    val db = target.db
    val haptics = rememberRichHaptics()
    val dark = pawIsDark()
    var props by remember(db.id) { mutableStateOf<List<PropSchema>?>(null) }
    LaunchedEffect(db.id) {
        val schema = runCatching { NotionRepo.schema(db.id) }.getOrNull()
        props = schema?.props.orEmpty()
            .filter { it.name != db.statusProp && it.name != db.titleProp && it.name != db.dateProp }
            .filter { (it.type in setOf("select", "multi_select", "status") && it.options.isNotEmpty()) || (it.type == "relation" && it.relatedDb != null) }
            // Properties shown in the item sheet come first: they are the ones that matter.
            .sortedBy { if (it.name in db.visibleProps) 0 else 1 }
            .map { p ->
                if (p.type != "relation") p
                else p.copy(options = runCatching { NotionClient.recentPages(p.relatedDb!!) }.getOrDefault(emptyList()))
            }
            .filter { it.options.isNotEmpty() }
    }
    val list = props ?: return
    if (list.isEmpty()) return
    Card {
        list.forEach { p ->
            val picked = chosen[p.name]?.second.orEmpty()
            val many = p.type == "multi_select" || p.type == "relation"
            val names = p.options.filter { (it.id ?: it.name) in picked }.map { it.name }
            PickRow(
                icon = { Icon(if (p.type == "relation") CronSymbols.Database else YoinSymbols.Filter, null) },
                label = p.name,
                value = names.joinToString("、").ifBlank { "未选" },
                set = names.isNotEmpty(),
            ) { close ->
                p.options.forEach { opt ->
                    val value = opt.id ?: opt.name
                    val on = value in picked
                    val dot = if (p.type == "relation") null else NotionRepo.optionColors(opt.color, dark).first
                    Item(opt.name, on, dot?.let { Color(it) }) {
                        haptics.tick()
                        val next = when {
                            on -> picked - value
                            many -> picked + value
                            else -> listOf(value)
                        }
                        onChosen(if (next.isEmpty()) chosen - p.name else chosen + (p.name to (p.type to next)))
                        if (!many) close()
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(vertical = 4.dp),
    ) { content() }
}

/** Icon, label, what's chosen on the right; tapping opens [menu] under the row. */
@Composable
private fun PickRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    set: Boolean,
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val haptics = rememberRichHaptics()
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { haptics.tick(); open = true }.padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalContentColor provides cs.onSurfaceVariant) { icon() }
            }
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(12.dp))
            Text(
                value,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (set) FontWeight.Bold else FontWeight.Normal,
                color = if (set) cs.primary else cs.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(YoinSymbols.UnfoldMore, null, Modifier.padding(start = 4.dp).size(18.dp), tint = cs.onSurfaceVariant)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            offset = DpOffset(16.dp, 0.dp),
            modifier = Modifier.heightIn(max = 360.dp),
        ) { menu { open = false } }
    }
}

/** A menu line: an optional colour dot, the name, a check when it's on. */
@Composable
private fun Item(label: String, on: Boolean, dot: Color? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    DropdownMenuItem(
        text = { Text(label, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, color = if (on) cs.primary else cs.onSurface) },
        onClick = onClick,
        leadingIcon = dot?.let { c -> { Box(Modifier.size(12.dp).clip(CircleShape).background(c)) } },
        trailingIcon = { if (on) Icon(YoinSymbols.Check, null, tint = cs.primary) else Spacer(Modifier.size(24.dp)) },
    )
}

@Composable
private fun ColorDot(color: Color, on: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick)
            .then(if (on) Modifier.border(2.dp, cs.onSurface, CircleShape) else Modifier)
            .padding(4.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Icon(YoinSymbols.Check, null, Modifier.size(16.dp), tint = Color.White)
    }
}

/** A row you type into: same icon column as the pick rows, the placeholder is the label. */
@Composable
private fun FieldRow(icon: ImageVector, value: String, onValue: (String) -> Unit, placeholder: String, singleLine: Boolean) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(24.dp), tint = cs.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        BasicTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.weight(1f).padding(vertical = 14.dp),
            singleLine = singleLine,
            maxLines = if (singleLine) 1 else 4,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
            cursorBrush = SolidColor(cs.primary),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                    inner()
                }
            },
        )
    }
}
