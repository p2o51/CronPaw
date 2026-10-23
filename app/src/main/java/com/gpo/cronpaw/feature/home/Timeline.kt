package com.gpo.cronpaw.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gpo.cronpaw.core.agenda.AgendaItem
import com.gpo.cronpaw.ui.theme.YoinMotion
import com.gpo.cronpaw.ui.time.Times
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.max

val HourHeight = 60.dp
private val Gutter = 40.dp

/**
 * The days on screen: a header pager (day numbers and all-day items) that follows the
 * main pager, and below it one shared vertical scroll of 24 hours with the hour labels.
 */
@Composable
fun Timeline(
    state: TimelineState,
    items: List<AgendaItem>,
    onItem: (AgendaItem) -> Unit,
    onToggleDone: (AgendaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    state.hourPx = with(density) { HourHeight.toPx() }
    // Minute ticks so the now line moves.
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now.longValue = System.currentTimeMillis()
        }
    }
    LaunchedEffect(state.pager) {
        snapshotFlow { state.pager.currentPage to state.pager.currentPageOffsetFraction }.collect { (p, f) ->
            state.header.scrollToPage(p, f)
        }
    }
    LaunchedEffect(Unit) { state.scrollToMinute(Times.minuteOfDay(now.longValue) - 90) }

    val byDay = remember(items) { items.groupByDays() }

    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(Gutter))
            HorizontalPager(state.header, Modifier.weight(1f), userScrollEnabled = false, verticalAlignment = Alignment.Top) { page ->
                val first = state.firstDay(page)
                Row(Modifier.fillMaxWidth()) {
                    for (i in 0 until state.days) {
                        val day = first.plusDays(i.toLong())
                        DayHeader(day, byDay[day].orEmpty().filter { it.allDay }, onItem, Modifier.weight(1f))
                    }
                }
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { state.viewport = it.boundsInRoot() },
        ) {
            Row(Modifier.fillMaxWidth().verticalScroll(state.scroll)) {
                HourGutter()
                HorizontalPager(
                    state.pager,
                    Modifier.weight(1f).height(HourHeight * 24),
                    beyondViewportPageCount = 1,
                    key = { it },
                ) { page ->
                    val first = state.firstDay(page)
                    Row(
                        Modifier
                            .fillMaxSize()
                            .onGloballyPositioned {
                                if (page == state.pager.currentPage) {
                                    state.columnsOrigin = it.positionInRoot()
                                    state.columnsWidth = it.size.width
                                }
                            },
                    ) {
                        for (i in 0 until state.days) {
                            val day = first.plusDays(i.toLong())
                            DayColumn(
                                day = day,
                                items = byDay[day].orEmpty().filter { !it.allDay },
                                now = now.longValue,
                                ghost = state.ghost?.takeIf { Times.date(it.start) == day },
                                onItem = onItem,
                                onToggleDone = onToggleDone,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Items by every local day they touch (a 2-day event shows on both). */
private fun List<AgendaItem>.groupByDays(): Map<LocalDate, List<AgendaItem>> {
    val out = HashMap<LocalDate, MutableList<AgendaItem>>()
    for (item in this) {
        var d = Times.date(item.start)
        val last = Times.date(max(item.start, item.end - 1))
        var guard = 0
        while (!d.isAfter(last) && guard++ < 62) {
            out.getOrPut(d) { mutableListOf() } += item
            d = d.plusDays(1)
        }
    }
    return out
}

@Composable
private fun DayHeader(day: LocalDate, allDay: List<AgendaItem>, onItem: (AgendaItem) -> Unit, modifier: Modifier) {
    val today = day == LocalDate.now()
    val cs = MaterialTheme.colorScheme
    Column(modifier.padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(Times.weekday(day), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(if (today) cs.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (today) cs.onPrimary else cs.onSurface,
            )
        }
        Column(Modifier.padding(top = 4.dp, bottom = 4.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (item in allDay.take(3)) {
                val (bg, ink) = itemColors(item)
                Text(
                    item.title,
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(bg)
                        .clickable { onItem(item) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (allDay.size > 3) {
                Text("+${allDay.size - 3}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HourGutter() {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.width(Gutter)) {
        for (h in 0 until 24) {
            Box(Modifier.height(HourHeight).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                if (h > 0) Text(
                    "$h",
                    Modifier.offset(y = (-7).dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

/** A soft block in the item's own colour, and the ink that reads on it. */
@Composable
fun itemColors(item: AgendaItem): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    val base = Color(item.color)
    val dark = cs.surface.luminance() < 0.5f
    val bg = base.copy(alpha = if (dark) 0.42f else 0.26f).compositeOver(cs.surface)
    val ink = if (dark) Color.White.copy(alpha = 0.94f) else Color(0xFF1B1B1F)
    return bg to ink
}

private data class Placed(val item: AgendaItem, val startMin: Int, val endMin: Int, val col: Int, val cols: Int)

/** Side by side when they overlap: each cluster of overlapping items shares its width. */
private fun place(items: List<AgendaItem>, day: LocalDate): List<Placed> {
    val dayStart = Times.startOf(day)
    val spans = items.map {
        val s = ((it.start - dayStart) / 60_000L).toInt().coerceIn(0, 24 * 60)
        val e = ((it.end - dayStart) / 60_000L).toInt().coerceIn(0, 24 * 60)
        Triple(it, s, max(e, s + 20))
    }.sortedWith(compareBy({ it.second }, { -it.third }))
    val out = mutableListOf<Placed>()
    var cluster = mutableListOf<Triple<AgendaItem, Int, Int>>()
    var clusterEnd = -1
    fun flush() {
        val colEnds = mutableListOf<Int>()
        val assigned = cluster.map { span ->
            var c = colEnds.indexOfFirst { it <= span.second }
            if (c == -1) { colEnds += span.third; c = colEnds.lastIndex } else colEnds[c] = span.third
            span to c
        }
        for ((span, c) in assigned) out += Placed(span.first, span.second, span.third, c, colEnds.size)
        cluster = mutableListOf()
        clusterEnd = -1
    }
    for (span in spans) {
        if (cluster.isNotEmpty() && span.second >= clusterEnd) flush()
        cluster += span
        clusterEnd = max(clusterEnd, span.third)
    }
    if (cluster.isNotEmpty()) flush()
    return out
}

@Composable
private fun DayColumn(
    day: LocalDate,
    items: List<AgendaItem>,
    now: Long,
    ghost: Ghost?,
    onItem: (AgendaItem) -> Unit,
    onToggleDone: (AgendaItem) -> Unit,
    modifier: Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val today = day == LocalDate.now()
    val band = cs.surfaceContainerLow
    val todayTint = cs.primary.copy(alpha = 0.05f)
    val placed = remember(items, day) { place(items, day) }
    BoxWithConstraints(
        modifier.drawBehind {
            if (today) drawRect(todayTint)
            // Every other hour a quiet band: rhythm without lines.
            val h = size.height / 24f
            for (i in 1 until 24 step 2) drawRect(band, topLeft = Offset(0f, i * h), size = Size(size.width, h))
        },
    ) {
        val w = maxWidth
        val hourDp = HourHeight
        for (p in placed) {
            val colW = w / p.cols
            ItemBlock(
                item = p.item,
                modifier = Modifier
                    .offset(x = colW * p.col, y = hourDp * (p.startMin / 60f))
                    .width(colW)
                    .height(hourDp * ((p.endMin - p.startMin) / 60f))
                    .padding(horizontal = 1.5.dp, vertical = 1.dp),
                compact = (p.endMin - p.startMin) < 40,
                onClick = { onItem(p.item) },
                onToggleDone = { onToggleDone(p.item) },
            )
        }
        if (ghost != null) {
            val s = Times.minuteOfDay(ghost.start)
            GhostBlock(ghost, Modifier.offset(y = hourDp * (s / 60f)).fillMaxWidth().height(hourDp * (ghost.minutes / 60f)))
        }
        if (today) {
            val m = Times.minuteOfDay(now)
            Box(Modifier.offset(y = hourDp * (m / 60f) - 1.dp).fillMaxWidth().height(2.dp).background(cs.primary))
            Box(Modifier.offset(x = (-4).dp, y = hourDp * (m / 60f) - 4.dp).size(8.dp).clip(CircleShape).background(cs.primary))
        }
    }
}

@Composable
private fun GhostBlock(ghost: Ghost, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier.padding(1.5.dp).clip(RoundedCornerShape(10.dp)).background(cs.primary.copy(alpha = 0.85f)).padding(6.dp),
    ) {
        Text(
            "${Times.hm(ghost.start)}  ${Times.durationText(ghost.minutes)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = cs.onPrimary,
        )
    }
}

@Composable
private fun ItemBlock(
    item: AgendaItem,
    modifier: Modifier,
    compact: Boolean,
    onClick: () -> Unit,
    onToggleDone: () -> Unit,
) {
    val (bg, ink) = itemColors(item)
    val doneAlpha by animateFloatAsState(if (item.done) 0.5f else 1f, YoinMotion.defaultEffectsSpec(), label = "done")
    val pending = item.key.startsWith("new:")
    Row(
        modifier
            .graphicsLayer { alpha = doneAlpha * (if (pending) 0.7f else 1f) }
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = if (compact) 1.dp else 4.dp),
    ) {
        if (item.todo) {
            TodoDot(item.done, Color(item.color), ink, onToggleDone, Modifier.padding(end = 4.dp, top = if (compact) 0.dp else 1.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 14.sp),
                fontWeight = FontWeight.SemiBold,
                color = ink,
                maxLines = if (compact) 1 else 3,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
            )
            if (!compact) Text(
                Times.hm(item.start),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = ink.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }
    }
}

/** The tap target for a Notion todo: a ring that fills when done. */
@Composable
fun TodoDot(done: Boolean, color: Color, ink: Color, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    val fill by animateColorAsState(if (done) color else Color.Transparent, YoinMotion.defaultEffectsSpec(), label = "todo")
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(ink.copy(alpha = 0.22f))
            .padding(2.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(YoinSymbols.Check, null, Modifier.size(size - 4.dp).alpha(0.95f), tint = Color.White)
    }
}
