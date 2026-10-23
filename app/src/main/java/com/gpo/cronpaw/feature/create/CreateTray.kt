package com.gpo.cronpaw.feature.create

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gpo.cronpaw.ui.theme.RichHaptics
import com.gpo.cronpaw.ui.theme.YoinMotion
import com.gpo.cronpaw.ui.theme.rememberRichHaptics
import com.gpo.cronpaw.ui.time.Times
import kotlin.math.abs

/** What the tray tells the screen while a bar is carried to the timeline. */
interface TrayDrag {
    fun lift(minutes: Int, root: Offset)
    fun move(root: Offset)
    fun drop()
    fun cancel()
    /** A bar tapped instead of carried: the next free quarter hour. */
    fun tap(minutes: Int)
}

private const val MinMinutes = 5
private const val MaxMinutes = 240
private val StepWidth = 7.dp

/**
 * 15 分钟 · 30 分钟 · 1.5 小时 and a long 自定义 bar. Any bar can be dragged onto the
 * timeline. The custom bar also changes length: tap it to highlight, then drag sideways
 * in 5-minute ticks (tap elsewhere to leave); unhighlighted, a sideways drag resizes and
 * an upward drag lifts it — the first direction decides.
 */
@Composable
fun CreateTray(
    custom: Int,
    onCustom: (Int) -> Unit,
    editing: Boolean,
    onEditing: (Boolean) -> Unit,
    carry: TrayDrag,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberRichHaptics()
    val cs = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(28.dp))
            .background(cs.surfaceContainerHigh)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Widths follow the lengths, and the three together span the tray.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FixedBar(15, Modifier.weight(1f), carry, haptics)
            FixedBar(30, Modifier.weight(1.3f), carry, haptics)
            FixedBar(90, Modifier.weight(1.8f), carry, haptics)
        }
        CustomBar(custom, onCustom, editing, onEditing, carry, haptics)
    }
}

@Composable
private fun FixedBar(minutes: Int, modifier: Modifier, carry: TrayDrag, haptics: RichHaptics) {
    val cs = MaterialTheme.colorScheme
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val (value, unit) = Times.duration(minutes)
    Box(
        modifier
            .height(48.dp)
            .onGloballyPositioned { coords = it }
            .clip(RoundedCornerShape(16.dp))
            .background(cs.primaryContainer)
            .pointerInput(minutes) {
                liftGesture(
                    root = { p -> coords?.localToRoot(p) ?: p },
                    onTap = { haptics.tick(); carry.tap(minutes) },
                    onLift = { p -> haptics.liftOff(); carry.lift(minutes, p) },
                    carry = carry,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer)
            Text(unit, Modifier.padding(start = 2.dp, bottom = 1.dp), style = MaterialTheme.typography.labelSmall, color = cs.onPrimaryContainer.copy(alpha = 0.7f))
        }
    }
}

/** Down, then: no movement = tap; any movement past slop = lift and carry until up. */
private suspend fun PointerInputScope.liftGesture(
    root: (Offset) -> Offset,
    onTap: () -> Unit,
    onLift: (Offset) -> Unit,
    carry: TrayDrag,
) = awaitEachGesture {
    val down = awaitFirstDown()
    val moved = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
    if (moved == null) {
        if (currentEvent.changes.none { it.pressed }) onTap()
        return@awaitEachGesture
    }
    onLift(root(moved.position))
    val ended = drag(moved.id) { change ->
        carry.move(root(change.position))
        change.consume()
    }
    if (ended) carry.drop() else carry.cancel()
}

@Composable
private fun CustomBar(
    minutes: Int,
    onMinutes: (Int) -> Unit,
    editing: Boolean,
    onEditing: (Boolean) -> Unit,
    carry: TrayDrag,
    haptics: RichHaptics,
) {
    val cs = MaterialTheme.colorScheme
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val current by rememberUpdatedState(minutes)
    val isEditing by rememberUpdatedState(editing)
    val bg by animateColorAsState(if (editing) cs.primary else cs.secondaryContainer, YoinMotion.defaultEffectsSpec(), label = "bar")
    val ink = if (editing) cs.onPrimary else cs.onSecondaryContainer
    val fill by animateFloatAsState(minutes / MaxMinutes.toFloat(), YoinMotion.fastSpatialSpec(), label = "fill")
    val tickColor = ink.copy(alpha = 0.25f)
    val fillColor = ink.copy(alpha = 0.16f)
    val (value, unit) = Times.duration(minutes)

    Box(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .onGloballyPositioned { coords = it }
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .drawBehind {
                drawRoundRect(fillColor, size = Size(size.width * fill, size.height), cornerRadius = CornerRadius(18.dp.toPx()))
                // An hour mark every 60 minutes: the scale the finger moves along.
                for (h in 1 until MaxMinutes / 60) {
                    val x = size.width * h * 60f / MaxMinutes
                    drawCircle(tickColor, radius = 2.dp.toPx(), center = Offset(x, size.height - 8.dp.toPx()))
                }
            }
            .pointerInput(Unit) {
                val step = StepWidth.toPx()
                fun resize(dx: Float, acc: FloatArray) {
                    acc[0] += dx
                    val steps = (acc[0] / step).toInt()
                    if (steps != 0) {
                        acc[0] -= steps * step
                        val next = (current + steps * 5).coerceIn(MinMinutes, MaxMinutes)
                        if (next != current) {
                            onMinutes(next)
                            if (next % 60 == 0) haptics.lowTick() else haptics.tick()
                        }
                    }
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val acc = floatArrayOf(0f)
                    if (isEditing) {
                        drag(down.id) { change ->
                            resize(change.positionChange().x, acc)
                            change.consume()
                        }
                        return@awaitEachGesture
                    }
                    var horizontal = false
                    val moved = awaitTouchSlopOrCancellation(down.id) { change, over ->
                        horizontal = abs(over.x) > abs(over.y)
                        change.consume()
                    }
                    if (moved == null) {
                        if (currentEvent.changes.none { it.pressed }) {
                            haptics.tick()
                            onEditing(true)
                        }
                        return@awaitEachGesture
                    }
                    if (horizontal) {
                        drag(moved.id) { change ->
                            resize(change.positionChange().x, acc)
                            change.consume()
                        }
                    } else {
                        haptics.liftOff()
                        val root = { p: Offset -> coords?.localToRoot(p) ?: p }
                        carry.lift(current, root(moved.position))
                        val ended = drag(moved.id) { change ->
                            carry.move(root(change.position))
                            change.consume()
                        }
                        if (ended) carry.drop() else carry.cancel()
                    }
                }
            }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ink)
            Text(unit, Modifier.padding(start = 3.dp, top = 4.dp), style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.7f))
            Box(Modifier.weight(1f))
            Text(if (editing) "左右拖动" else "自定义", style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.7f))
        }
    }
}

