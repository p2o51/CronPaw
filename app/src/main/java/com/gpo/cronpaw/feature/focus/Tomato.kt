package com.gpo.cronpaw.feature.focus

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import hct.Hct
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The colours of a focus page on black, from the item's own colour: a bright one for the
 * water's crest and the buttons, two deep ones for the water (few lit pixels on OLED), a pale
 * one for the sepals and the stem. Breaks turn the hue a third of the way round.
 */
@Immutable
data class FocusTones(val bright: Color, val deep: Color, val deeper: Color, val leaf: Color) {
    companion object {
        fun of(argb: Int, rest: Boolean = false): FocusTones {
            val src = Hct.fromInt(argb)
            val hue = if (rest) (src.hue + 60.0) % 360.0 else src.hue
            val chroma = max(src.chroma, 36.0)
            return FocusTones(
                bright = Color(Hct.from(hue, chroma, 74.0).toInt()),
                deep = Color(Hct.from(hue, chroma * 0.6, 26.0).toInt()),
                deeper = Color(Hct.from(hue, chroma * 0.5, 16.0).toInt()),
                leaf = Color(Hct.from(hue, chroma * 0.7, 86.0).toInt()),
            )
        }
    }
}

/**
 * A tomato that fills with water as the round goes (materialpomo's rising waves, made into a
 * tomato): [level] 0..1, two waves and a bright crest. The sepals sway and close up in a break
 * ([open] → 0); [pop] gives it a springy swell when a round is harvested.
 */
@Composable
fun Tomato(
    level: Float,
    tones: FocusTones,
    running: Boolean,
    open: Float,
    popKey: Int,
    modifier: Modifier = Modifier,
    full: Boolean = false,
) {
    val waves = rememberInfiniteTransition(label = "tomato")
    val t by waves.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart), label = "t")
    val amp by animateFloatAsState(if (running) 1f else 0.08f, spring(dampingRatio = 0.6f, stiffness = 120f), label = "amp")
    val shownLevel by animateFloatAsState(level, spring(dampingRatio = 0.8f, stiffness = 60f), label = "level")
    val sepals by animateFloatAsState(open, spring(dampingRatio = 0.55f, stiffness = 140f), label = "open")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(popKey) {
        if (popKey == 0) return@LaunchedEffect
        pop.snapTo(1.08f)
        pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 260f))
    }
    val spin = remember { Animatable(0f) }
    LaunchedEffect(popKey) {
        if (popKey == 0) return@LaunchedEffect
        spin.snapTo(0f)
        spin.animateTo(180f, spring(dampingRatio = 0.7f, stiffness = 90f))
        spin.snapTo(0f)
    }
    Canvas(modifier) {
        val s = size.minDimension / 226f
        withTransform({
            translate((size.width - 226f * s) / 2f, (size.height - 226f * s) / 2f)
            scale(s, s, pivot = Offset.Zero)
            scale(pop.value, pop.value, pivot = Offset(Cx, 134f))
        }) {
            drawTomato(if (full) 1f else 0.04f + 0.92f * shownLevel, t, amp, sepals, spin.value, tones, full)
        }
    }
}

private const val Cx = 113f
private const val Top = 62f
private const val Bottom = 206f

/** The outline's anchors (a 226 box), mirrored; a closed Catmull-Rom curve through them. */
private val Anchors: List<Offset> = run {
    val half = listOf(Offset(113f, 72f), Offset(156f, 62f), Offset(196f, 82f), Offset(210f, 128f), Offset(190f, 180f), Offset(140f, 204f), Offset(113f, 206f))
    half + half.subList(1, half.size - 1).reversed().map { Offset(226f - it.x, it.y) }
}

private val Body: Path = Path().apply {
    val n = Anchors.size
    moveTo(Anchors[0].x, Anchors[0].y)
    for (i in 0 until n) {
        val p0 = Anchors[(i - 1 + n) % n]
        val p1 = Anchors[i]
        val p2 = Anchors[(i + 1) % n]
        val p3 = Anchors[(i + 2) % n]
        cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y,
        )
    }
    close()
}

private fun wavePath(level: Float, t: Float, amp: Float, freq: Float, off: Float, closed: Boolean): Path {
    val y = Bottom - (Bottom - Top) * level
    val p = Path()
    var x = 6f
    var first = true
    while (x <= 220f) {
        val yy = y + sin(x / 204f * freq * PI.toFloat() + t + off) * amp
        if (first) { p.moveTo(x, yy); first = false } else p.lineTo(x, yy)
        x += 3f
    }
    if (closed) {
        p.lineTo(220f, Bottom + 10f)
        p.lineTo(6f, Bottom + 10f)
        p.close()
    }
    return p
}

/** Five leaves fanning out from the top; [open] 0 folds them in. */
private fun sepalPath(open: Float, sway: Float): Path {
    val ox = Cx
    val oy = 74f
    val p = Path()
    for (i in 0 until 5) {
        val side = abs(i - 2)
        val base = ((-90f + (i - 2) * (30f + 18f * open) + sway) * PI / 180f).toFloat()
        val len = 36f + 14f * open - (if (side == 2) 6f else 0f)
        val w = 15f
        val tx = ox + len * cos(base)
        val ty = oy + len * sin(base) * 0.45f + side * 9f * open
        val nx = -sin(base) * w
        val ny = cos(base) * w * 0.8f
        p.moveTo(ox, oy)
        p.quadraticTo((ox + tx) / 2 + nx, (oy + ty) / 2 + ny, tx, ty)
        p.quadraticTo((ox + tx) / 2 - nx, (oy + ty) / 2 - ny, ox, oy)
        p.close()
    }
    return p
}

private fun DrawScope.drawTomato(level: Float, t: Float, amp: Float, open: Float, spin: Float, tones: FocusTones, full: Boolean) {
    drawPath(Body, if (full) tones.bright else tones.deeper)
    if (!full) {
        clipPath(Body) {
            val a = 5f * amp
            drawPath(wavePath(level, t * 0.8f, a * 0.8f, 3f, 1.4f, true), tones.deep.copy(alpha = 0.55f))
            drawPath(wavePath(level - 0.015f, t, a, 4f, 0f, true), tones.deep)
            drawPath(wavePath(level - 0.015f, t, a, 4f, 0f, false), tones.bright, style = Stroke(3f, cap = StrokeCap.Round))
        }
        // A faint rim, so an empty tomato still reads as one on black.
        drawPath(Body, tones.bright.copy(alpha = 0.22f), style = Stroke(2f))
    }
    drawRoundRect(tones.leaf, topLeft = Offset(108.5f, 46f), size = Size(9f, 30f), cornerRadius = CornerRadius(4.5f))
    withTransform({ rotate(spin, pivot = Offset(Cx, 74f)) }) {
        drawPath(sepalPath(open, sin(t) * 3f), tones.leaf)
    }
}

/** A small tomato for the harvest row: filled when that round is done. */
@Composable
fun MiniTomato(filled: Boolean, tones: FocusTones, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension / 24f
        val c = Offset(12f * s, 13.5f * s)
        if (filled) drawCircle(tones.bright, 8.5f * s, c)
        else drawCircle(tones.bright.copy(alpha = 0.45f), 8.5f * s, c, style = Stroke(1.6f * s))
        val leaf = if (filled) tones.leaf else tones.leaf.copy(alpha = 0.45f)
        val top = Offset(12f * s, 5.5f * s)
        drawLine(leaf, top, Offset(8f * s, 4f * s), 1.6f * s, StrokeCap.Round)
        drawLine(leaf, top, Offset(16f * s, 4f * s), 1.6f * s, StrokeCap.Round)
        drawLine(leaf, top, Offset(12f * s, 2.5f * s), 1.6f * s, StrokeCap.Round)
    }
}
