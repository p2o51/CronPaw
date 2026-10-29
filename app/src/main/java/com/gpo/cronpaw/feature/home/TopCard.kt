package com.gpo.cronpaw.feature.home

import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gpo.cronpaw.core.agenda.AgendaItem
import com.gpo.cronpaw.core.agenda.Pins
import com.gpo.cronpaw.core.focus.FocusController
import com.gpo.cronpaw.core.focus.FocusMode
import com.gpo.cronpaw.core.focus.FocusSession
import com.gpo.cronpaw.core.focus.FocusSettings
import com.gpo.cronpaw.core.focus.FocusStore
import com.gpo.cronpaw.core.focus.FocusZen
import com.gpo.cronpaw.core.focus.Phase
import com.gpo.cronpaw.core.focus.advance
import com.gpo.cronpaw.core.focus.breakColor
import com.gpo.yoin.symbols.YoinSymbols
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import com.gpo.cronpaw.ui.theme.LocalYoinMotionRole
import com.gpo.cronpaw.ui.theme.YoinMotion
import com.gpo.cronpaw.ui.theme.rememberRichHaptics
import com.gpo.cronpaw.ui.time.Times
import java.time.LocalDate
import kotlin.math.abs
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Cards swiped away stay away until the app process ends (「这次打开」). */
object TopCardSession {
    val dismissed = mutableStateListOf<String>()
}

/** Pinned items first, then what comes next; at most three. */
fun topCandidates(items: List<AgendaItem>, pins: List<String>, now: Long, limit: Int = 3): List<AgendaItem> {
    val today = LocalDate.now()
    fun live(it: AgendaItem) = !it.done && (if (it.allDay) Times.date(it.start) <= today && it.end > now else it.end > now)
    val pinned = pins.mapNotNull { k -> items.firstOrNull { it.key == k } }.filter(::live)
    val next = items.asSequence()
        .filter { !it.allDay && live(it) && it.key !in pins && it.start < now + 36 * 3_600_000L }
        .sortedBy { it.start }
        .take(limit)
        .toList()
    return (pinned + next).filter { it.key !in TopCardSession.dismissed }.take(limit)
}

/** When an item is, as a bold value and a quiet label. */
fun whenLabel(item: AgendaItem, now: Long): Pair<String, String> {
    val today = LocalDate.now()
    return when {
        item.allDay -> "全天" to ""
        item.start <= now -> ((item.end - now) / 60_000L).coerceAtLeast(1).toString() to "分钟后结束"
        item.start - now < 90 * 60_000L -> ((item.start - now) / 60_000L).coerceAtLeast(1).toString() to "分钟后"
        Times.date(item.start) == today -> Times.hm(item.start) to "开始"
        Times.date(item.start) == today.plusDays(1) -> Times.hm(item.start) to "明天"
        else -> Times.hm(item.start) to Times.weekday(Times.date(item.start))
    }
}

@Composable
fun TopCard(
    items: List<AgendaItem>,
    onOpenAll: () -> Unit,
    onOpenFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // While a session runs the card is the timer and stays put.
    val focus by FocusStore.session.collectAsState()
    // Not while 上次专注 asks how it ended: the clock there is not to be trusted.
    val asking by FocusController.recovery.collectAsState()
    focus?.takeIf { asking == null }?.let { FocusCard(it, onOpenFocus, modifier); return }
    val pins by Pins.keys.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = System.currentTimeMillis(); delay(20_000) }
        }
    }
    val cards = topCandidates(items, pins, now)
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(cards.size, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (cards.size > 1) {
                delay(5_000)
                index = (index + 1) % cards.size
            }
        }
    }
    if (cards.isEmpty()) return
    val current = cards[index.coerceIn(0, cards.lastIndex)]
    val haptics = rememberRichHaptics()
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(0f) }
    val cs = MaterialTheme.colorScheme
    val motionRole = LocalYoinMotionRole.current
    val motionScheme = MaterialTheme.motionScheme

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .graphicsLayer {
                translationX = drag.value
                alpha = 1f - (abs(drag.value) / (size.width.coerceAtLeast(1f))).coerceIn(0f, 0.8f)
            }
            .clip(RoundedCornerShape(24.dp))
            .background(cs.surfaceContainerHigh)
            .pointerInput(current.key) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (abs(drag.value) > size.width * 0.32f) {
                                haptics.lowTick()
                                drag.animateTo(if (drag.value > 0) size.width * 1.2f else -size.width * 1.2f)
                                TopCardSession.dismissed += current.key
                                index = 0
                                drag.snapTo(0f)
                            } else {
                                drag.animateTo(0f)
                            }
                        }
                    },
                    onDragCancel = { scope.launch { drag.animateTo(0f) } },
                ) { change, delta ->
                    change.consume()
                    scope.launch { drag.snapTo(drag.value + delta) }
                }
            }
            .clickable(onClick = onOpenAll)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        AnimatedContent(
            targetState = current,
            contentKey = { it.key },
            transitionSpec = {
                (slideInVertically(YoinMotion.defaultSpatialSpec(motionRole, motionScheme)) { it / 2 } + fadeIn(YoinMotion.defaultEffectsSpec(motionRole, motionScheme)) +
                    scaleIn(YoinMotion.defaultSpatialSpec(motionRole, motionScheme), initialScale = 0.92f)) togetherWith
                    (slideOutVertically(YoinMotion.defaultSpatialSpec(motionRole, motionScheme)) { -it / 2 } + fadeOut(YoinMotion.fastEffectsSpec(motionRole, motionScheme)))
            },
            label = "topcard",
        ) { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp, 34.dp).clip(RoundedCornerShape(5.dp)).background(Color(item.color)))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.key in pins) {
                            Icon(YoinSymbols.PinFilled, null, Modifier.size(14.dp), tint = cs.primary)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            item.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(Times.hmRange(item), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
                val (value, label) = whenLabel(item, now)
                Column(horizontalAlignment = Alignment.End) {
                    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
                // In progress: start focusing on it the way it was done last time.
                if (!item.allDay && item.start <= now && item.end > now) {
                    val context = LocalContext.current
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(cs.primary).clickable {
                            haptics.tick()
                            val mode = runCatching { FocusMode.valueOf(FocusSettings.lastMode.value) }.getOrDefault(FocusMode.Pomodoro)
                            FocusController.plan(item, mode, FocusSettings.quiet.value && FocusZen.hasAccess(context))
                            onOpenFocus()
                        },
                        contentAlignment = Alignment.Center,
                    ) { Icon(YoinSymbols.PlayFilled, "专注", Modifier.size(20.dp), tint = cs.onPrimary) }
                }
            }
        }
        if (cards.size > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                cards.indices.forEach { i ->
                    Box(
                        Modifier.size(if (i == index) 10.dp else 4.dp, 3.dp).clip(CircleShape)
                            .background(if (i == index) cs.primary else cs.outlineVariant),
                    )
                }
            }
        }
    }
}

/** The running session: its label, the item, the clock; the card fills with the item's colour as it goes. */
@Composable
private fun FocusCard(s: FocusSession, onOpen: () -> Unit, modifier: Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { now = System.currentTimeMillis(); delay(1000L - now % 1000L) }
        }
    }
    val cur = advance(s, now)
    val cs = MaterialTheme.colorScheme
    val fill by animateFloatAsState(cur.progress(now), spring(dampingRatio = 1f, stiffness = 50f), label = "fill")
    val color = Color(if (cur.phase == Phase.Break || cur.phase == Phase.Waiting) breakColor(cur.color) else cur.color)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(cs.surfaceContainerHigh)
            .drawBehind { drawRect(color.copy(alpha = 0.28f), size = Size(size.width * fill, size.height)) }
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp, 34.dp).clip(RoundedCornerShape(5.dp)).background(Color(cur.color)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val label = when {
                cur.paused -> "已暂停"
                cur.phase == Phase.Break -> "休息"
                cur.phase == Phase.Waiting -> "休息好了"
                cur.phase == Phase.Done -> "专注完成"
                cur.mode == FocusMode.Free -> "专注"
                cur.rounds > 1 -> "专注 第 ${cur.round + 1} 轮"
                else -> "专注"
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Text(cur.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val ms = if (cur.mode == FocusMode.Free) cur.focused(now) else cur.remaining(now) + 999
        val sec = ms / 1000
        val text = if (sec >= 3600) "${sec / 3600}:${(sec % 3600 / 60).toString().padStart(2, '0')}:${(sec % 60).toString().padStart(2, '0')}"
        else "${(sec / 60).toString().padStart(2, '0')}:${(sec % 60).toString().padStart(2, '0')}"
        Column(horizontalAlignment = Alignment.End) {
            Text(text, style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold)
            Text(if (cur.mode == FocusMode.Free) "已专注" else "剩余", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}

/** Everything that could be on the card, with a pin each. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllUpcomingSheet(items: List<AgendaItem>, onItem: (AgendaItem) -> Unit, onDismiss: () -> Unit) {
    val pins by Pins.keys.collectAsState()
    val now = remember { System.currentTimeMillis() }
    val list = remember(items) {
        items.filter { !it.done && it.end > now && it.start < now + 7 * 86_400_000L }.sortedBy { it.start }
    }
    val haptics = rememberRichHaptics()
    val cs = MaterialTheme.colorScheme
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()
    // Slide down, then open the item.
    val open: (AgendaItem) -> Unit = { item -> scope.launch { runCatching { sheet.hide() }; onItem(item) } }
    val byDay = remember(list) { list.drop(1).groupBy { Times.date(it.start) } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = cs.surfaceContainerLow) {
        Text("接下来", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
            // The very next one, large and in its own colours.
            list.firstOrNull()?.let { next ->
                item(key = "next:${next.key}") {
                    val (bg, ink) = itemColors(next)
                    val pinned = next.key in pins
                    Column(
                        Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(RoundedCornerShape(28.dp)).background(bg)
                            .clickable { open(next) }.padding(start = 20.dp, top = 16.dp, bottom = 8.dp, end = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text(Times.dayLabel(Times.date(next.start)), style = MaterialTheme.typography.labelLarge, color = ink.copy(alpha = 0.75f))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(if (next.allDay) "全天" else Times.hm(next.start), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = ink)
                                    if (!next.allDay) Text(
                                        "到 ${Times.hm(next.end)}",
                                        Modifier.padding(start = 8.dp, bottom = 7.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = ink.copy(alpha = 0.75f),
                                    )
                                }
                            }
                            PinButton(pinned, ink) { haptics.tick(); Pins.toggle(next.key); TopCardSession.dismissed.remove(next.key) }
                        }
                        Text(next.title, Modifier.padding(end = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            byDay.forEach { (day, dayItems) ->
                item(key = "day:$day") {
                    Text(Times.dayLabel(day), Modifier.padding(start = 12.dp, top = 14.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                items(dayItems, key = { it.key }) { item ->
                    val pinned = item.key in pins
                    // Time on the left, bold start over a quiet end; the item as a colour block.
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(60.dp).padding(start = 12.dp)) {
                            Text(if (item.allDay) "全天" else Times.hm(item.start), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            if (!item.allDay) Text(Times.hm(item.end), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                        }
                        val (bg, ink) = itemColors(item)
                        Row(
                            Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(if (pinned) bg else cs.surfaceContainer)
                                .clickable { open(item) }.padding(start = 14.dp, top = 4.dp, bottom = 4.dp, end = 0.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!pinned) {
                                Box(Modifier.size(6.dp, 20.dp).clip(RoundedCornerShape(3.dp)).background(Color(item.color)))
                                Spacer(Modifier.width(10.dp))
                            }
                            Text(
                                item.title,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = if (pinned) ink else cs.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            PinButton(pinned, if (pinned) ink else cs.onSurfaceVariant) {
                                haptics.tick()
                                Pins.toggle(item.key)
                                TopCardSession.dismissed.remove(item.key)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinButton(pinned: Boolean, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(if (pinned) YoinSymbols.PinFilled else YoinSymbols.Pin, if (pinned) "取消置顶" else "置顶", tint = tint)
    }

}
