package com.gpo.cronpaw.feature.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gpo.cronpaw.core.agenda.CalendarRepo
import com.gpo.cronpaw.core.agenda.Target
import com.gpo.cronpaw.core.notion.NotionClient
import com.gpo.cronpaw.core.notion.NotionRepo
import com.gpo.cronpaw.core.notion.PropSchema
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

@Composable
private fun EventFields(target: Target.Calendar, start: Long, event: CalendarRepo.EventExtras, onEvent: (CalendarRepo.EventExtras) -> Unit) {
    val context = LocalContext.current
    val haptics = rememberRichHaptics()
    var colors by remember(target.info.id) { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    LaunchedEffect(target.info.id) { colors = CalendarRepo.eventColors(context, target.info.id) }

    // Used now and then, so folded into one row that says what's set; tap to open.
    var open by remember { mutableStateOf(false) }
    val dow = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).dayOfWeek
    val summary = listOfNotNull(
        event.location.ifBlank { null },
        if (event.notes.isNotBlank()) "有备注" else null,
        event.reminders.takeIf { it.isNotEmpty() }?.let { r -> "提醒 " + r.joinToString("、") { reminderLabel(it) } },
        event.rrule?.let { repeatLabel(it, dow) },
        if (event.colorKey != null) "自选颜色" else null,
    ).joinToString(" · ")
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainer)
            .clickable { haptics.tick(); open = !open }.padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            summary.ifBlank { "地点 · 备注 · 提醒 · 重复 · 颜色" },
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (summary.isBlank()) cs.onSurfaceVariant else cs.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(if (open) YoinSymbols.ChevronUp else YoinSymbols.ChevronDown, if (open) "收起" else "展开", tint = cs.onSurfaceVariant)
    }
    androidx.compose.animation.AnimatedVisibility(
        open,
        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
    ) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Place and notes side by side would be cramped: place is one line, notes can grow.
        PlainField(event.location, { onEvent(event.copy(location = it)) }, "地点", singleLine = true)
        PlainField(event.notes, { onEvent(event.copy(notes = it)) }, "备注", singleLine = false)

        Card {
            ChipRow("提醒") {
                val options = listOf(0 to "准时", 5 to "5 分钟", 10 to "10 分钟", 30 to "30 分钟", 60 to "1 小时", 1440 to "1 天")
                Chip("不提醒", event.reminders.isEmpty()) { haptics.tick(); onEvent(event.copy(reminders = emptyList())) }
                options.forEach { (m, label) ->
                    val on = m in event.reminders
                    Chip(label, on) {
                        haptics.tick()
                        onEvent(event.copy(reminders = if (on) event.reminders - m else (event.reminders + m).sorted()))
                    }
                }
            }
            ChipRow("重复") {
                val options = listOf(
                    null to "不重复",
                    "FREQ=DAILY" to "每天",
                    "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR" to "工作日",
                    "FREQ=WEEKLY;BYDAY=${byDay(dow)}" to "每${weekdayName(dow)}",
                    "FREQ=MONTHLY" to "每月",
                    "FREQ=YEARLY" to "每年",
                )
                options.forEach { (rule, label) ->
                    Chip(label, event.rrule == rule) { haptics.tick(); onEvent(event.copy(rrule = rule)) }
                }
            }
            if (colors.isNotEmpty()) {
                ChipRow("颜色") {
                    ColorDot(Color(target.info.color), event.colorKey == null, "日历色") { haptics.tick(); onEvent(event.copy(colorKey = null)) }
                    colors.forEach { (key, argb) ->
                        ColorDot(Color(argb), event.colorKey == key, null) { haptics.tick(); onEvent(event.copy(colorKey = key)) }
                    }
                }
            }
        }
    }
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
    Card(Modifier.padding(top = 10.dp)) {
        list.forEach { p ->
            val picked = chosen[p.name]?.second.orEmpty()
            val many = p.type == "multi_select" || p.type == "relation"
            ChipRow(p.name) {
                p.options.forEach { opt ->
                    val value = opt.id ?: opt.name
                    val on = value in picked
                    val colors = if (p.type == "relation") null else NotionRepo.optionColors(opt.color, dark)
                    Chip(opt.name, on, colors?.let { Color(it.first) to Color(it.second) }) {
                        haptics.tick()
                        val next = when {
                            on -> picked - value
                            many -> picked + value
                            else -> listOf(value)
                        }
                        onChosen(if (next.isEmpty()) chosen - p.name else chosen + (p.name to (p.type to next)))
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(vertical = 6.dp),
    ) { content() }
}

/** A small label, then its chips in a line that scrolls sideways. */
@Composable
private fun ChipRow(label: String, chips: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            Modifier.width(64.dp).padding(start = 14.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { chips() }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, colors: Pair<Color, Color>? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (bg, ink) = when {
        on && colors != null -> colors
        on -> cs.primary to cs.onPrimary
        else -> cs.surfaceContainerHighest to cs.onSurfaceVariant
    }
    Text(
        label,
        Modifier.clip(CircleShape).background(bg).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
        color = ink,
        maxLines = 1,
    )
}

@Composable
private fun ColorDot(color: Color, on: Boolean, label: String?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.clip(CircleShape).clickable(onClick = onClick).padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(color)
                .then(if (on) Modifier.border(3.dp, cs.onSurface, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Icon(YoinSymbols.Check, null, Modifier.size(16.dp), tint = Color.White)
        }
        if (label != null) {
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
        }
    }
}

@Composable
private fun PlainField(value: String, onValue: (String) -> Unit, placeholder: String, singleLine: Boolean) {
    val cs = MaterialTheme.colorScheme
    TextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        singleLine = singleLine,
        minLines = 1,
        maxLines = if (singleLine) 1 else 4,
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = cs.surfaceContainer,
            unfocusedContainerColor = cs.surfaceContainer,
        ),
    )
}
