package com.gpo.cronpaw.feature.home

import com.gpo.cronpaw.ui.components.seam.seamGraphic
import com.gpo.cronpaw.ui.components.seam.seamText
import com.gpo.cronpaw.ui.components.seam.seamViewport
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gpo.cronpaw.core.agenda.Agenda
import com.gpo.cronpaw.core.agenda.AgendaItem
import com.gpo.cronpaw.core.agenda.itemsByDay
import com.gpo.cronpaw.ui.time.Times
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.max

/** How far 日程 reaches either side of today. */
const val ScheduleBack = 14L
const val ScheduleAhead = 120L

/** A row of 日程: a month title, or a day with its items. */
sealed interface ScheduleRow {
    val key: String
    data class Month(val month: YearMonth) : ScheduleRow { override val key = "m:$month" }
    data class Day(val date: LocalDate, val items: List<AgendaItem>) : ScheduleRow { override val key = "d:$date" }
}

fun scheduleRows(items: List<AgendaItem>, today: LocalDate = LocalDate.now()): List<ScheduleRow> {
    val from = today.minusDays(ScheduleBack)
    val to = today.plusDays(ScheduleAhead)
    val byDay = itemsByDay(items, from, to)
    // Days with something, and today even when empty, so 「今天」 always has a place.
    val days = (byDay.keys + today).filter { !it.isBefore(from) && !it.isAfter(to) }.sorted()
    val out = mutableListOf<ScheduleRow>()
    var month: YearMonth? = null
    for (d in days) {
        val m = YearMonth.from(d)
        if (m != month) { out += ScheduleRow.Month(m); month = m }
        out += ScheduleRow.Day(d, byDay[d].orEmpty().sortedWith(compareBy({ !it.allDay }, { it.start })))
    }
    return out
}

/** The row index of [date], or of the next day that has something. */
fun scheduleIndexOf(rows: List<ScheduleRow>, date: LocalDate): Int {
    val i = rows.indexOfFirst { it is ScheduleRow.Day && !it.date.isBefore(date) }
    if (i < 0) return (rows.size - 1).coerceAtLeast(0)
    // Show the month title above it when it starts the month.
    return if (i > 0 && rows[i - 1] is ScheduleRow.Month) i - 1 else i
}

/**
 * 日程: a list of the days that have something, like Google Calendar's Schedule. The day
 * sits on the left, its items as colour blocks on the right.
 */
@Composable
fun ScheduleView(
    list: LazyListState,
    rows: List<ScheduleRow>,
    onItem: (AgendaItem) -> Unit,
    onToggleDone: (AgendaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        val today = LocalDate.now()
        Agenda.ensure(today.minusDays(ScheduleBack), today.plusDays(ScheduleAhead))
    }
    val cs = MaterialTheme.colorScheme
    val now = remember { System.currentTimeMillis() }
    LazyColumn(modifier.seamViewport(list), state = list, contentPadding = PaddingValues(start = 8.dp, end = 12.dp, bottom = 120.dp)) {
        items(rows, key = { it.key }, contentType = { if (it is ScheduleRow.Month) "month" else "day" }) { row ->
            when (row) {
                is ScheduleRow.Month -> Text(
                    if (row.month.year != LocalDate.now().year) "${row.month.year}年${row.month.monthValue}月" else "${row.month.monthValue}月",
                    Modifier.padding(start = 12.dp, top = 20.dp, bottom = 6.dp).seamText(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                is ScheduleRow.Day -> Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    val today = row.date == LocalDate.now()
                    Column(Modifier.width(56.dp).padding(top = 4.dp).seamText(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(Times.weekday(row.date), style = MaterialTheme.typography.labelSmall, color = if (today) cs.primary else cs.onSurfaceVariant)
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(if (today) cs.primary else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                row.date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (today) cs.onPrimary else cs.onSurface,
                            )
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (row.items.isEmpty()) {
                            Text("今天没有安排", Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                        row.items.forEach { item ->
                            val (bg, ink) = itemColors(item)
                            val past = item.end < now
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .seamGraphic()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(bg)
                                    .clickable { onItem(item) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (item.todo) {
                                    TodoDot(item.done, Color(item.color), ink, { onToggleDone(item) }, Modifier.padding(end = 10.dp), size = 18.dp)
                                }
                                Column(Modifier.weight(1f).seamText()) {
                                    Text(
                                        item.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ink.copy(alpha = if (past || item.done) 0.6f else 1f),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        textDecoration = if (item.done) TextDecoration.LineThrough else null,
                                    )
                                    Text(Times.hmRange(item), style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.7f))
                                }
                                Spacer(Modifier.width(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
