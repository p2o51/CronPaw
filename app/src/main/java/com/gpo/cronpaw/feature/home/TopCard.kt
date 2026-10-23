package com.gpo.cronpaw.feature.home

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
import com.gpo.cronpaw.ui.theme.CronSymbols
import com.gpo.cronpaw.ui.theme.LocalYoinMotionRole
import com.gpo.cronpaw.ui.theme.YoinMotion
import com.gpo.cronpaw.ui.theme.rememberRichHaptics
import com.gpo.cronpaw.ui.time.Times
import java.time.LocalDate
import kotlin.math.abs
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
    modifier: Modifier = Modifier,
) {
    val pins by Pins.keys.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { delay(20_000); now = System.currentTimeMillis() }
    }
    val cards = topCandidates(items, pins, now)
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(cards.size) {
        while (cards.size > 1) {
            delay(5_000)
            index = (index + 1) % cards.size
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
                            Icon(CronSymbols.PinFilled, null, Modifier.size(14.dp), tint = cs.primary)
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
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = cs.surfaceContainerLow) {
        Text("接下来", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
            items(list, key = { it.key }) { item ->
                val pinned = item.key in pins
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (pinned) cs.secondaryContainer else cs.surfaceContainer)
                        .clickable { onItem(item) }
                        .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp, 28.dp).clip(RoundedCornerShape(4.dp)).background(Color(item.color)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${Times.dayLabel(Times.date(item.start))}  ${Times.hmRange(item)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        haptics.tick()
                        Pins.toggle(item.key)
                        TopCardSession.dismissed.remove(item.key)
                    }) {
                        Icon(if (pinned) CronSymbols.PinFilled else CronSymbols.Pin, if (pinned) "取消置顶" else "置顶", tint = if (pinned) cs.primary else cs.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
