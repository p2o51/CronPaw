package com.gpo.cronpaw.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.gpo.cronpaw.ui.theme.YoinMotion
import kotlin.math.abs
import kotlin.math.roundToInt

// M3 Expressive "medium" slider, as in PiliPaw's player sheets: a 40dp track with 12dp outer
// corners, a 4 x 52dp handle standing in a 6dp gap, 2dp corners where the track meets the gap.
private val TrackHeight = 40.dp
private val TrackOuterCorner = 12.dp
private val TrackInnerCorner = 2.dp
private val HandleWidth = 4.dp
private val HandleHeight = 52.dp
private val HandleGap = 6.dp
private val MarkDot = 4.dp
private val EndInset = 4.dp

/**
 * A stepped wide slider over [count] detents (value 0 until count). M3 handles touch, keys and
 * accessibility; the track and handle are drawn here, and the handle glides between detents on
 * a spring. Only the detents in [marks] get a dot, so a long row of steps stays quiet.
 */
@Composable
fun WideSlider(
    value: Int,
    count: Int,
    onValueChange: (Int) -> Unit,
    description: String,
    state: String,
    modifier: Modifier = Modifier,
    marks: Set<Int> = emptySet(),
) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val dragged by interaction.collectIsDraggedAsState()
    // Narrower while held, like M3's own handle.
    val handleWidth by animateDpAsState(if (pressed || dragged) HandleWidth / 2 else HandleWidth, YoinMotion.fastSpatialSpec(), label = "wideHandle")
    val change by rememberUpdatedState(onValueChange)
    val lastIndex = (count - 1).coerceAtLeast(1)
    val at = value.coerceIn(0, lastIndex)
    val glide = remember { Animatable(at.toFloat()) }
    val hop = YoinMotion.fastSpatialSpec<Float>()
    val travel = YoinMotion.defaultSpatialSpec<Float>()
    LaunchedEffect(at) { glide.animateTo(at.toFloat(), if (abs(at - glide.targetValue) > 1f) travel else hop) }
    val active = cs.primary
    val inactive = cs.secondaryContainer
    val activeMark = cs.onPrimary
    val inactiveMark = cs.onSecondaryContainer
    val handle = cs.primary

    Slider(
        value = at.toFloat(),
        onValueChange = { v -> change(v.roundToInt().coerceIn(0, lastIndex)) },
        modifier = modifier.fillMaxWidth().semantics {
            contentDescription = description
            stateDescription = state
        },
        interactionSource = interaction,
        steps = (count - 2).coerceAtLeast(0),
        // M3 lays out and measures this slot; the handle itself is drawn on the track.
        thumb = { Spacer(Modifier.size(HandleWidth, HandleHeight)) },
        track = {
            Canvas(Modifier.fillMaxWidth().height(HandleHeight)) {
                val mirror = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
                scale(mirror, 1f) {
                    drawWideTrack(
                        fraction = (glide.value / lastIndex).coerceIn(0f, 1f),
                        count = count,
                        marks = marks,
                        active = active,
                        inactive = inactive,
                        activeMark = activeMark,
                        inactiveMark = inactiveMark,
                        handle = handle,
                        handleWidth = handleWidth.toPx(),
                    )
                }
            }
        },
        valueRange = 0f..lastIndex.toFloat(),
    )
}

private fun DrawScope.drawWideTrack(
    fraction: Float,
    count: Int,
    marks: Set<Int>,
    active: Color,
    inactive: Color,
    activeMark: Color,
    inactiveMark: Color,
    handle: Color,
    handleWidth: Float,
) {
    val w = size.width
    val top = (size.height - TrackHeight.toPx()) / 2f
    val bottom = top + TrackHeight.toPx()
    val outer = TrackOuterCorner.toPx()
    val inner = TrackInnerCorner.toPx()
    val handleX = fraction * w
    val gap = HandleGap.toPx() + HandleWidth.toPx() / 2f
    if (handleX - gap > 0.5f) segment(0f, handleX - gap, top, bottom, outer, inner, active)
    if (w - handleX - gap > 0.5f) segment(handleX + gap, w, top, bottom, inner, outer, inactive)
    val half = MarkDot.toPx() / 2f
    val inset = EndInset.toPx()
    for (i in marks) {
        if (count < 2 || i !in 0 until count) continue
        val x = (i * w / (count - 1)).coerceIn(half + inset, w - half - inset)
        if (abs(x - handleX) < gap + half) continue
        drawCircle(if (x < handleX) activeMark else inactiveMark, radius = half, center = Offset(x, size.height / 2f))
    }
    drawRoundRect(handle, topLeft = Offset(handleX - handleWidth / 2f, 0f), size = Size(handleWidth, size.height), cornerRadius = CornerRadius(handleWidth / 2f))
}

private fun DrawScope.segment(left: Float, right: Float, top: Float, bottom: Float, startCorner: Float, endCorner: Float, color: Color) {
    val start = CornerRadius(startCorner)
    val end = CornerRadius(endCorner)
    drawPath(
        Path().apply {
            addRoundRect(RoundRect(left, top, right, bottom, topLeftCornerRadius = start, topRightCornerRadius = end, bottomRightCornerRadius = end, bottomLeftCornerRadius = start))
        },
        color,
    )
}
