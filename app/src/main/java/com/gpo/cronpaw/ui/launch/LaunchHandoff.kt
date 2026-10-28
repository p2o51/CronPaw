package com.gpo.cronpaw.ui.launch

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.gpo.cronpaw.R
import com.gpo.cronpaw.ui.theme.YoinMotion
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import kotlin.math.pow

/**
 * The splash → home handoff. The splash ends on the icon (drawable/ic_splash_paw: a paw whose pad
 * is a page reading 31). [LaunchHandoffOverlay] repaints that frame, drops the platform splash
 * under it, then
 * - **flies**: the 31 rolls to today, the field fades off the home, the toes let go and the pad
 *   flies along an arc into the header's 今天 button ([launchDateSlot]), turning into it on the
 *   way, so the date you open the app on is the one the button shows; or
 * - **fades**: off a widget, a notification or a link, or when the button doesn't report in time.
 *
 * Springs throughout, read in the draw and layer phases only.
 */
@Stable
class LaunchHandoff {
    enum class Phase { Waiting, Flying, Fading, Done }

    var phase by mutableStateOf(Phase.Waiting)
        private set

    // Window px.
    internal var iconCenter = Offset.Zero
        private set

    /** px per unit of the 108 frame the paw is drawn in (the splash's 1.5x included); 0 = no icon. */
    internal var unit = 0f
        private set

    /** The header's 今天 button: where it is and what it looks like. */
    internal var slot by mutableStateOf<DateSlot?>(null)
    internal var target: DateSlot? = null
        private set

    internal val roll = Animatable(0f)
    internal val field = Animatable(1f)
    internal val flight = Animatable(0f)
    internal val fade = Animatable(1f)

    private var removeSplash: (() -> Unit)? = null

    /** The splash view has left our window, and the window has focus (the system's starting
     *  surface is gone): until both, the platform splash still covers the overlay. */
    private var splashDetached by mutableStateOf(false)
    var windowFocused by mutableStateOf(false)

    /** The splash is done: [fly] when launched from the launcher, else it fades. */
    fun begin(fly: Boolean, iconCenter: Offset, unit: Float, splashView: android.view.View?, remove: () -> Unit) {
        if (phase != Phase.Waiting) {
            remove()
            return
        }
        this.iconCenter = iconCenter
        this.unit = unit
        splashView?.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) = Unit
            override fun onViewDetachedFromWindow(v: android.view.View) {
                Log.d(Tag, "splash view detached at ${System.currentTimeMillis()}")
                splashDetached = true
            }
        })
        if (splashView == null || !splashView.isAttachedToWindow) splashDetached = true
        var removed = false
        removeSplash = {
            if (!removed) {
                removed = true
                remove()
            }
        }
        phase = if (fly && unit > 0f) Phase.Flying else Phase.Fading
    }

    /** No handoff (animations off, or the activity came back): straight to the end. */
    fun skip(remove: () -> Unit) {
        remove()
        removeSplash = null
        phase = Phase.Done
    }

    /** The system showed no splash: stand the slot and reveal down. */
    fun abandonIfWaiting() {
        if (phase == Phase.Waiting) phase = Phase.Done
    }

    private fun finish() {
        removeSplash?.invoke()
        removeSplash = null
        phase = Phase.Done
    }

    internal suspend fun run(effects: FiniteAnimationSpec<Float>, fastEffects: FiniteAnimationSpec<Float>): Unit = coroutineScope {
        // Two frames: the one that drew the repainted splash, and the one after it is on screen.
        // Only then drop the platform splash under it, so no frame of the bare home shows through.
        withFrameNanos { }
        withFrameNanos { }
        removeSplash?.invoke()
        Log.d(Tag, "repainted, phase=$phase, removed at ${System.currentTimeMillis()}")
        // remove() only asks: the platform splash can stay on screen for another half second
        // (seen on API 36). Hold the repainted frame until it has really gone, then a frame more.
        withTimeoutOrNull(UncoverWaitMs) { snapshotFlow { splashDetached && windowFocused }.first { it } }
        withFrameNanos { }
        Log.d(Tag, "uncovered at ${System.currentTimeMillis()} (detached=$splashDetached focused=$windowFocused)")
        if (phase == Phase.Flying) {
            target = withTimeoutOrNull(SlotWaitMs) { snapshotFlow { slot }.filterNotNull().first() }
            if (target == null) phase = Phase.Fading
            Log.d(Tag, "slot=${target?.bounds}")
        }
        // The first frames after launch are the busiest (the agenda loading, the home's first
        // pages): a spring started then skips most of its frames and reads as a cut. Wait, briefly,
        // for two frames in a row on time.
        steadyFrames()
        if (phase == Phase.Fading) {
            fade.animateTo(0f, effects)
            finish()
            return@coroutineScope
        }
        // 31 rolls over to today while the page is still big enough to read it.
        if (LocalDate.now().dayOfMonth != IconDay) launch { roll.animateTo(1f, RollSpring) }
        delay(FlightAfterMs)
        launch { field.animateTo(0f, effects) }
        launch { flight.animateTo(1f, FlightSpring) }
        snapshotFlow { flight.value >= SeatedAt && field.value <= 0.01f }.first { it }
        finish()
        coroutineContext.cancelChildren()
    }

    private suspend fun steadyFrames() {
        val start = System.nanoTime()
        var last = withFrameNanos { it }
        var steady = 0
        while (steady < 2 && System.nanoTime() - start < SteadyWaitMs * 1_000_000L) {
            val now = withFrameNanos { it }
            steady = if (now - last < 25_000_000L) steady + 1 else 0
            last = now
        }
        Log.d(Tag, "steady after ${(System.nanoTime() - start) / 1_000_000} ms")
    }

    /** The pad's centre at flight progress [p]: rises a little first, then turns into the button. */
    internal fun padCenter(p: Float, to: Rect): Offset {
        val c0 = iconCenter + Offset(0f, (PadBox.center.y - 54f) * unit)
        val c1 = to.center
        val q = Offset(lerp(c0.x, c1.x, 0.1f), lerp(c1.y, c0.y, 0.15f))
        val u = 1f - p
        return c0 * (u * u) + q * (2f * u * p) + c1 * (p * p)
    }

    internal companion object {
        /** The date on the icon. */
        const val IconDay = 31
        /** The pad's box in the 108 frame (drawable/ic_paw_mark), and where the date's baseline sits. */
        val PadBox = Rect(38f, 48f, 70f, 74f)
        const val IconBaseline = 67.5f
        const val IconFontSize = 16f

        val RollSpring = spring<Float>(dampingRatio = 0.75f, stiffness = 420f)
        val FlightSpring = spring<Float>(dampingRatio = 0.85f, stiffness = 200f)
        const val FlightAfterMs = 200L
        const val SlotWaitMs = 300L
        const val SteadyWaitMs = 600L
        const val UncoverWaitMs = 900L
        const val Tag = "LaunchHandoff"
        const val SeatedAt = 0.99f
    }
}

/** The 今天 button's bounds in the window, and its colours. */
data class DateSlot(val bounds: Rect, val corner: Float, val container: Color, val content: Color)

val LocalLaunchHandoff = staticCompositionLocalOf<LaunchHandoff?> { null }

/** Log interpolation, so a shrink reads even from big to small. */
private fun logLerp(a: Float, b: Float, t: Float) = a * (b / a).pow(t)

private fun smooth(t: Float): Float = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

/** Over the whole app while the handoff runs. Place it last in the root Box. */
@Composable
fun LaunchHandoffOverlay(handoff: LaunchHandoff) {
    val phase = handoff.phase
    if (phase != LaunchHandoff.Phase.Flying && phase != LaunchHandoff.Phase.Fading) return
    val bg = colorResource(R.color.icon_bg)
    val ink = colorResource(R.color.icon_ink)
    // Toes and pad from the same drawable the icon is built on.
    val mark = ImageVector.vectorResource(R.drawable.ic_paw_mark)
    val parts = remember(mark) {
        (0 until mark.root.size).mapNotNull { mark.root[it] as? VectorPath }
            .map { it.pathData.toPath() to ((it.fill as? SolidColor)?.value ?: Color.Black) }
    }
    val (toes, toeColor) = parts[0]
    val (pad, padColor) = parts[1]
    val style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    val from = remember(style) { measurer.measure(LaunchHandoff.IconDay.toString(), style) }
    val to = remember(style) { measurer.measure(LocalDate.now().dayOfMonth.toString(), style) }
    val effects = YoinMotion.defaultEffectsSpec<Float>()
    val fastEffects = YoinMotion.fastEffectsSpec<Float>()
    LaunchedEffect(handoff) { handoff.run(effects, fastEffects) }
    val origin = remember { mutableStateOf(Offset.Zero) }
    val box = LaunchHandoff.PadBox
    Canvas(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin.value = it.positionInWindow() }
            // While the field covers the home, taps must not reach it.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (handoff.field.value > 0.3f && handoff.fade.value > 0.3f) event.changes.forEach { it.consume() }
                    }
                }
            },
    ) {
        val o = origin.value
        val u = handoff.unit
        val slot = handoff.target
        if (handoff.phase == LaunchHandoff.Phase.Fading || slot == null) {
            val a = handoff.fade.value.coerceIn(0f, 1f)
            drawRect(bg, alpha = a)
            if (u > 0f) {
                val tl = handoff.iconCenter - o - Offset(54f * u, 54f * u)
                withTransform({ translate(tl.x, tl.y); scale(u, u, Offset.Zero) }) {
                    drawPath(toes, toeColor, alpha = a)
                    drawPath(pad, padColor, alpha = a)
                }
                drawDate(from, handoff.iconCenter - o + Offset(0f, (LaunchHandoff.IconBaseline - 54f) * u), LaunchHandoff.IconFontSize * u, ink, a, baseline = true)
            }
            return@Canvas
        }
        val p = handoff.flight.value
        val b = slot.bounds.translate(-o)
        drawRect(bg, alpha = handoff.field.value.coerceIn(0f, 1f))
        // The pad's box now: log-shrunk from the splash's page to the button.
        val w = logLerp(box.width * u, b.width, p.coerceIn(0f, 1f))
        val h = logLerp(box.height * u, b.height, p.coerceIn(0f, 1f))
        val c = handoff.padCenter(p, slot.bounds) - o
        val sx = w / box.width
        val sy = h / box.height
        val m = smooth((p - 0.15f) / 0.6f)
        val fill = lerp(padColor, slot.container, smooth(p / 0.8f))
        // Toes let go first, drifting up a touch as they go.
        val toeAlpha = 1f - smooth(p / 0.3f)
        withTransform({
            translate(c.x - box.center.x * sx, c.y - box.center.y * sy)
            scale(sx, sy, Offset.Zero)
        }) {
            if (toeAlpha > 0f) {
                withTransform({ translate(0f, -4f * (1f - toeAlpha)) }) { drawPath(toes, toeColor, alpha = toeAlpha) }
            }
            // The pad's soft notch fades into the button's plain rounded square.
            if (m < 1f) drawPath(pad, fill, alpha = 1f - m)
        }
        if (m > 0f) {
            val r = lerp(10f * sx, slot.corner, p.coerceIn(0f, 1f))
            drawRoundRect(fill, Offset(c.x - w / 2f, c.y - h / 2f), Size(w, h), CornerRadius(r), alpha = m)
        }
        // The date: from the icon's size and baseline to the button's, centred in it; rolled to today.
        val size0 = LaunchHandoff.IconFontSize * u
        val size = logLerp(size0, style.fontSize.toPx(), p.coerceIn(0f, 1f))
        val inkNow = lerp(ink, slot.content, smooth(p / 0.8f))
        val iconAnchor = c + Offset(0f, (LaunchHandoff.IconBaseline - box.center.y) * sy)
        val k = handoff.roll.value
        clipRect(c.x - w / 2f, c.y - h / 2f, c.x + w / 2f, c.y + h / 2f) {
            val shift = 0.75f * h
            if (k < 1f) drawFlyingDate(from, iconAnchor, c, p, size, inkNow, 1f - k, -shift * k)
            if (k > 0f) drawFlyingDate(to, iconAnchor, c, p, size, inkNow, k, shift * (1f - k))
        }
    }
}

/** [layout] measured at its own size, drawn at [px]; centred on x, anchored at its baseline or centre. */
private fun DrawScope.drawDate(layout: androidx.compose.ui.text.TextLayoutResult, at: Offset, px: Float, color: Color, alpha: Float, baseline: Boolean) {
    val k = px / layout.layoutInput.style.fontSize.toPx()
    val left = at.x - layout.size.width * k / 2f
    val top = if (baseline) at.y - layout.firstBaseline * k else at.y - layout.size.height * k / 2f
    withTransform({ translate(left, top); scale(k, k, Offset.Zero) }) {
        drawText(layout, color = color, alpha = alpha)
    }
}

/** The date in flight: its anchor moves from the icon's baseline to the button's centre. */
private fun DrawScope.drawFlyingDate(
    layout: androidx.compose.ui.text.TextLayoutResult,
    iconAnchor: Offset,
    center: Offset,
    p: Float,
    px: Float,
    color: Color,
    alpha: Float,
    dy: Float,
) {
    val k = px / layout.layoutInput.style.fontSize.toPx()
    val left = center.x - layout.size.width * k / 2f
    val top0 = iconAnchor.y - layout.firstBaseline * k
    val top1 = center.y - layout.size.height * k / 2f
    val top = lerp(top0, top1, p.coerceIn(0f, 1f)) + dy
    withTransform({ translate(left, top); scale(k, k, Offset.Zero) }) {
        drawText(layout, color = color, alpha = alpha.coerceIn(0f, 1f))
    }
}

/**
 * The header's 今天 button: reports where it is and how it looks as the flight's landing, and
 * stays hidden under the arriving pad until the overlay lets go.
 */
fun Modifier.launchDateSlot(container: Color, content: Color): Modifier = composed {
    val handoff = LocalLaunchHandoff.current
    if (handoff == null || handoff.phase == LaunchHandoff.Phase.Done) return@composed this
    val corner = with(androidx.compose.ui.platform.LocalDensity.current) { 12.dp.toPx() }
    this
        .onGloballyPositioned { handoff.slot = DateSlot(it.boundsInWindow(), corner, container, content) }
        .graphicsLayer { if (handoff.phase == LaunchHandoff.Phase.Flying) alpha = 0f }
}

/** The home under the field: settles up from 0.96 as the field lifts off it. */
fun Modifier.launchReveal(): Modifier = composed {
    val handoff = LocalLaunchHandoff.current
    if (handoff == null || handoff.phase != LaunchHandoff.Phase.Flying && handoff.phase != LaunchHandoff.Phase.Waiting) return@composed this
    this.graphicsLayer {
        if (handoff.phase == LaunchHandoff.Phase.Flying || handoff.phase == LaunchHandoff.Phase.Waiting) {
            val k = 1f - handoff.field.value
            val s = 0.96f + 0.04f * k
            scaleX = s
            scaleY = s
            translationY = (1f - k) * 24.dp.toPx()
        }
    }
}
