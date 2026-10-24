package com.gpo.cronpaw.feature.home

import com.gpo.cronpaw.ui.components.seam.seamGraphic
import com.gpo.cronpaw.ui.components.seam.seamText
import com.gpo.cronpaw.ui.components.seam.seamViewport
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import com.gpo.cronpaw.core.settings.AppSettings
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.animation.core.animateDpAsState
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
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

/** Long-press an item and carry it to a new time (all-day items stay put). */
interface MoveDrag {
    /** [grabY] is how far below the item's top the finger is, in px. */
    fun start(item: AgendaItem, root: Offset, grabY: Float)
    fun move(root: Offset)
    fun drop()
    fun cancel()
}

val MinHour = 28.dp
val MaxHour = 160.dp
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
    /** A tap on an empty part of a day: the quarter hour under the finger. */
    onSlot: (Long) -> Unit = {},
    mover: MoveDrag? = null,
    movingKey: String? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    state.hourPx = with(density) { state.hourDp.toPx() }
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
        // Each header page reports its natural height; the header follows the finger from
        // one page's height to the next, and springs when the data (not a swipe) changes it.
        val pageHeights = remember(state.days, state.base) { mutableStateMapOf<Int, Int>() }
        val headerHeight = remember { Animatable(0f) }
        LaunchedEffect(pageHeights) {
            snapshotFlow {
                val cur = state.pager.currentPage
                val f = state.pager.currentPageOffsetFraction
                val next = if (f >= 0f) cur + 1 else cur - 1
                val a = pageHeights[cur]
                val b = pageHeights[next] ?: a
                val target = if (a == null || b == null) null else a + (b - a) * kotlin.math.abs(f)
                target to state.pager.isScrollInProgress
            }.collect { (target, swiping) ->
                if (target == null) return@collect
                if (swiping || headerHeight.value == 0f) headerHeight.snapTo(target)
                else launch { headerHeight.animateTo(target, spring(stiffness = Spring.StiffnessMediumLow)) }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (headerHeight.value > 0f) Modifier.height(with(density) { headerHeight.value.toDp() }) else Modifier)
                .clipToBounds(),
        ) {
            Spacer(Modifier.width(Gutter))
            HorizontalPager(
                state.header,
                Modifier.weight(1f).fillMaxHeight(),
                userScrollEnabled = false,
                beyondViewportPageCount = 1,
                verticalAlignment = Alignment.Top,
            ) { page ->
                val first = state.firstDay(page)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(align = Alignment.Top, unbounded = true)
                        .onSizeChanged { pageHeights[page] = it.height },
                ) {
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
                .onGloballyPositioned { state.viewport = it.boundsInRoot() }
                .pinchToZoomHours(state),
        ) {
            // Yoin's seam: what scrolls up under the day header dissolves instead of being cut.
            Row(Modifier.fillMaxWidth().seamViewport(state.scroll).verticalScroll(state.scroll)) {
                HourGutter(state.hourDp)
                HorizontalPager(
                    state.pager,
                    Modifier.weight(1f).height(state.hourDp * 24),
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
                                hourDp = state.hourDp,
                                onItem = onItem,
                                onToggleDone = onToggleDone,
                                onSlot = onSlot,
                                mover = mover,
                                movingKey = movingKey,
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
private fun HourGutter(hourDp: Dp) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.width(Gutter).seamText()) {
        for (h in 0 until 24) {
            Box(Modifier.height(hourDp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
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
    hourDp: Dp,
    onItem: (AgendaItem) -> Unit,
    onToggleDone: (AgendaItem) -> Unit,
    onSlot: (Long) -> Unit,
    mover: MoveDrag?,
    movingKey: String?,
    modifier: Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val today = day == LocalDate.now()
    val band = cs.surfaceContainerLow
    val todayTint = cs.primary.copy(alpha = 0.05f)
    val placed = remember(items, day) { place(items, day) }
    BoxWithConstraints(
        modifier
            .pointerInput(day, hourDp) {
                // Items take their own taps; what reaches here is empty time.
                detectTapGestures { pos ->
                    val minute = ((pos.y / hourDp.toPx()) * 60f).toInt() / 15 * 15
                    onSlot(Times.at(day, minute.coerceIn(0, 24 * 60 - 15)))
                }
            }
            .drawBehind {
            if (today) drawRect(todayTint)
            // Every other hour a quiet band: rhythm without lines.
            val h = size.height / 24f
            for (i in 1 until 24 step 2) drawRect(band, topLeft = Offset(0f, i * h), size = Size(size.width, h))
        },
    ) {
        val w = maxWidth
        for (p in placed) {
            val colW = w / p.cols
            ItemBlock(
                item = p.item,
                modifier = Modifier
                    .offset(x = colW * p.col, y = hourDp * (p.startMin / 60f))
                    .width(colW)
                    .height(hourDp * ((p.endMin - p.startMin) / 60f))
                    .padding(horizontal = 1.5.dp, vertical = 1.dp),
                compact = hourDp * ((p.endMin - p.startMin) / 60f) < 36.dp,
                narrow = colW < 64.dp,
                onClick = { onItem(p.item) },
                onToggleDone = { onToggleDone(p.item) },
                mover = mover?.takeIf { !p.item.key.startsWith("new:") },
                lifted = p.item.key == movingKey,
            )
        }
        if (ghost != null) {
            // Glides from slot to slot instead of jumping a quarter hour at a time.
            val y by animateDpAsState(hourDp * (Times.minuteOfDay(ghost.start) / 60f), YoinMotion.fastSpatialSpec(), label = "ghost")
            GhostBlock(ghost, Modifier.offset(y = y).fillMaxWidth().height(hourDp * (ghost.minutes / 60f)))
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
    val item = ghost.item
    val bg = item?.let { Color(it.color).copy(alpha = 0.9f) } ?: cs.primary.copy(alpha = 0.85f)
    val ink = item?.let { if (Color(it.color).luminance() > 0.5f) Color(0xFF1B1B1F) else Color.White } ?: cs.onPrimary
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)) }
    Box(
        modifier
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value; alpha = pop.value.coerceAtMost(1f) }
            .padding(1.5.dp).clip(RoundedCornerShape(10.dp)).background(bg).padding(6.dp),
    ) {
        Column {
            // Moving: the item's own name and colour, so it can't be mistaken for a new item.
            if (item != null) Text(item.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
            Text(
                "${Times.hm(ghost.start)}  ${Times.durationText(ghost.minutes)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (item != null) FontWeight.Normal else FontWeight.Bold,
                color = ink,
            )
        }
    }
}

@Composable
private fun ItemBlock(
    item: AgendaItem,
    modifier: Modifier,
    compact: Boolean,
    narrow: Boolean,
    onClick: () -> Unit,
    onToggleDone: () -> Unit,
    mover: MoveDrag? = null,
    lifted: Boolean = false,
) {
    val (bg, ink) = itemColors(item)
    val doneAlpha by animateFloatAsState(if (item.done) 0.5f else 1f, YoinMotion.defaultEffectsSpec(), label = "done")
    // While carried, the original stays as a faint footprint.
    val liftAlpha by animateFloatAsState(if (lifted) 0.3f else 1f, YoinMotion.fastEffectsSpec(), label = "lifted")
    val pending = item.key.startsWith("new:")
    // Just added: it springs up from a little smaller, where the sheet left it.
    val pop = remember { androidx.compose.animation.core.Animatable(if (pending) 0.8f else 1f) }
    LaunchedEffect(Unit) {
        if (pop.value < 1f) pop.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow))
    }
    var coords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    val currentMover by rememberUpdatedState(mover)
    Row(
        modifier
            .seamGraphic()
            .onGloballyPositioned { coords = it }
            .pointerInput(item.key) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { pos ->
                        val c = coords ?: return@detectDragGesturesAfterLongPress
                        currentMover?.start(item, c.localToRoot(pos), pos.y)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        coords?.let { currentMover?.move(it.localToRoot(change.position)) }
                    },
                    onDragEnd = { currentMover?.drop() },
                    onDragCancel = { currentMover?.cancel() },
                )
            }
            .graphicsLayer {
                alpha = doneAlpha * liftAlpha * (if (pending) 0.7f else 1f)
                scaleX = pop.value
                scaleY = pop.value
            }
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = if (narrow) 3.dp else 5.dp, vertical = if (compact) 1.dp else 4.dp),
    ) {
        // A week on a phone: the dot would leave no room for the title.
        if (item.todo && !(narrow && compact)) {
            TodoDot(item.done, Color(item.color), ink, onToggleDone, Modifier.padding(end = if (narrow) 2.dp else 4.dp, top = if (compact) 0.dp else 1.dp))
        }
        Column(Modifier.weight(1f).seamText()) {
            Text(
                item.title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = if (narrow) 10.sp else 12.sp,
                    lineHeight = if (narrow) 12.sp else 14.sp,
                ),
                fontWeight = FontWeight.SemiBold,
                color = ink,
                // Narrow columns clip instead of spending two characters on "…".
                maxLines = if (compact) 1 else 8,
                softWrap = !compact || !narrow,
                overflow = if (narrow) TextOverflow.Clip else TextOverflow.Ellipsis,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
            )
            if (!compact && !narrow) Text(
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

/**
 * Two fingers change the hour height (one finger still scrolls and pages). The hour under
 * the fingers stays under them.
 */
private fun Modifier.pinchToZoomHours(state: TimelineState): Modifier = pointerInput(state) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var zooming = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2) {
                zooming = true
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                    val old = state.hourDp
                    val next = (old * zoom).coerceIn(MinHour, MaxHour)
                    val ratio = next / old
                    val focusY = event.calculateCentroid(useCurrent = true).y
                    val oldScroll = state.scroll.value.toFloat()
                    state.hourDp = next
                    state.hourPx = next.toPx()
                    state.scroll.dispatchRawDelta((oldScroll + focusY) * ratio - focusY - oldScroll)
                }
                event.changes.forEach { it.consume() }
            } else if (zooming) {
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (zooming) AppSettings.set(AppSettings.hourHeight, state.hourDp.value.toInt())
    }
}
