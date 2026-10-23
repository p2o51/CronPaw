package com.gpo.cronpaw.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpo.cronpaw.core.agenda.Agenda
import com.gpo.cronpaw.core.agenda.AgendaItem
import com.gpo.cronpaw.ui.time.Times
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.max

const val MonthMid = 1200

/** The month on [page] of a month pager whose middle page is this month. */
fun monthOf(page: Int): YearMonth = YearMonth.now().plusMonths((page - MonthMid).toLong())

fun monthPage(month: YearMonth): Int = MonthMid + ChronoUnit.MONTHS.between(YearMonth.now(), month).toInt()

/** Monday on or before the 1st: the grid's first cell. */
private fun gridStart(month: YearMonth): LocalDate = Times.monday(month.atDay(1))

/**
 * 月: six weeks from the Monday before the 1st. Each day shows as many items as fit,
 * then a count. Tapping a day opens it on the timeline.
 */
@Composable
fun MonthView(
    pager: PagerState,
    items: List<AgendaItem>,
    onDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { page ->
            val start = gridStart(monthOf(page))
            Agenda.ensure(start, start.plusDays(41))
        }
    }
    val byDay = remember(items) {
        val out = HashMap<LocalDate, MutableList<AgendaItem>>()
        for (item in items) {
            var d = Times.date(item.start)
            val last = Times.date(max(item.start, item.end - 1))
            var guard = 0
            while (!d.isAfter(last) && guard++ < 62) {
                out.getOrPut(d) { mutableListOf() } += item
                d = d.plusDays(1)
            }
        }
        out.mapValues { (_, v) -> v.sortedWith(compareBy({ !it.allDay }, { it.start })) }
    }
    val cs = MaterialTheme.colorScheme
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            val month = monthOf(page)
            val start = gridStart(month)
            Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (week in 0 until 6) {
                    Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (dow in 0 until 7) {
                            val day = start.plusDays((week * 7 + dow).toLong())
                            DayCell(day, inMonth = YearMonth.from(day) == month, items = byDay[day].orEmpty(), onClick = { onDay(day) }, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, inMonth: Boolean, items: List<AgendaItem>, onClick: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val today = day == LocalDate.now()
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (inMonth) cs.surfaceContainerLow else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(2.dp)
            .alpha(if (inMonth) 1f else 0.45f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(if (today) cs.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (today) FontWeight.Bold else FontWeight.Medium,
                color = if (today) cs.onPrimary else cs.onSurface,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val fit = ((maxHeight.value - 2f) / 16f).toInt().coerceAtLeast(0)
            val shown = if (items.size > fit) (fit - 1).coerceAtLeast(0) else items.size
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(top = 2.dp)) {
                items.take(shown).forEach { item ->
                    val (bg, ink) = itemColors(item)
                    Text(
                        item.title,
                        Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (item.allDay) bg else Color.Transparent)
                            .padding(horizontal = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 13.sp),
                        color = if (item.allDay) ink else Color(item.color),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
                if (items.size > shown && fit > 0) {
                    Text("+${items.size - shown}", Modifier.padding(horizontal = 2.dp), style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = cs.onSurfaceVariant)
                }
            }
        }
    }
}
