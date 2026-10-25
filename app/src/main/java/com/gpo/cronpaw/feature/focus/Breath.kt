package com.gpo.cronpaw.feature.focus

import android.content.Context
import android.provider.Settings
import android.view.View
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.toPath
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * What the 开始 button hands to the breath (screen px): the button grows into the 气 and the
 * sheet it sat on slides away under the black. The sheet waits for [drawn] before it goes, so the
 * two never show at once or leave a gap.
 */
object FocusHandoff {
    class Origin(val button: Rect, val buttonColor: Color, val sheet: Rect?, val sheetColor: Color)

    private var origin: Origin? = null
    val drawn = MutableStateFlow(false)

    fun arm(origin: Origin?) {
        this.origin = origin
        drawn.value = false
    }

    fun take(): Origin? = origin.also { origin = null }
}

/** A layout's bounds on the screen, so rects from the sheet's window and the page's line up. */
internal fun LayoutCoordinates.screenRect(view: View): Rect {
    val at = IntArray(2).also { view.getLocationOnScreen(it) }
    return boundsInRoot().translate(at[0].toFloat(), at[1].toFloat())
}

/** The breath and the page transitions are motion; with animations off the clock just starts. */
internal fun animationsOn(context: Context): Boolean =
    runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f) > 0f

/**
 * One slow breath before the clock starts, from Daylist's reading ritual (DESIGN_V7 §2.7), on
 * black: a very dark 页 above, the item's deep colour 气 below, and the soft seam between them is
 * the breath (铺开 0.8s, 吸气 1.6s, 屏住 1s, 呼气 3.4s). The 气 grows out of [origin]'s button,
 * and at the end it draws together into the tomato's body: [onGather] starts the clock so the
 * focus page is already underneath, [tomato] says where its tomato box landed (screen px, null
 * until laid out, then [fallback] in local px), and [onDone] comes once the real tomato shows.
 * A tap skips to the gather.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BreathOverlay(
    tones: FocusTones,
    origin: FocusHandoff.Origin?,
    tomato: () -> Rect?,
    fallback: Rect,
    onGather: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val navBottom = with(density) { WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx() }
    val statusTop = with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx() }
    val measurer = rememberTextMeasurer()
    val latestGather by rememberUpdatedState(onGather)
    val latestDone by rememberUpdatedState(onDone)
    val latestTomato by rememberUpdatedState(tomato)
    val wordStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Medium, letterSpacing = .18.em)
    val inhaleText = remember { measurer.measure("吸气", wordStyle) }
    val exhaleText = remember { measurer.measure("呼气", wordStyle) }
    val skipText = remember { measurer.measure("点一下跳过", TextStyle(fontSize = 12.sp)) }
    val shapes = remember { listOf(MaterialShapes.Cookie12Sided, MaterialShapes.Circle, MaterialShapes.Puffy).map { it.toPath().asComposePath() } }
    val colors = remember(tones) { listOf(tones.bright, tones.leaf, FocusTones.of(tones.bright.toArgb(), rest = true).bright) }
    val state = remember { BreathState() }
    var frame by remember { mutableIntStateOf(0) }
    // Where this overlay sits on the screen: the handed-over rects are screen px.
    var here by remember { mutableStateOf(Offset.Zero) }
    val local = remember(origin, here) {
        origin?.let {
            Start(it.button.translate(-here), it.buttonColor, it.sheet?.translate(-here), it.sheetColor)
        }
    }

    val latestLocal by rememberUpdatedState(local)

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { here = it.screenRect(view).topLeft }
            .semantics { contentDescription = "深呼吸一次，然后开始专注" }
            .clickable(remember { MutableInteractionSource() }, indication = null) { state.skip = true },
    ) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val g = remember(w, h, navBottom) { BreathGeometry(density.density, w, h, navBottom) }
        LaunchedEffect(g) {
            val spread = TargetBasedAnimation(spring(dampingRatio = 0.8f, stiffness = 200f), Float.VectorConverter, 0f, 1f)
            val inhale = TargetBasedAnimation(tween(1600, easing = CubicBezierEasing(.22f, .9f, .3f, 1f)), Float.VectorConverter, 0f, 1f)
            val exhale = TargetBasedAnimation(tween(3400, easing = CubicBezierEasing(.45f, 0f, .4f, 1f)), Float.VectorConverter, 0f, 1f)
            val gather = TargetBasedAnimation(spring(dampingRatio = 0.72f, stiffness = 240f), Float.VectorConverter, 0f, 1f)
            val start = withFrameNanos { it }
            var gatheredAt = -1f
            while (true) {
                val t = (withFrameNanos { it } - start) / 1_000_000f
                if (gatheredAt < 0f && (state.skip || t >= ExhaleEnd)) {
                    gatheredAt = t
                    state.snap()
                    latestGather()
                }
                if (gatheredAt >= 0f) {
                    val e = t - gatheredAt
                    val box = latestTomato()?.translate(-here) ?: fallback
                    state.gather(g, gather.value(e), e, box)
                    frame++
                    if (e >= GatherEnd) break
                } else {
                    state.breathe(g, t, spread.value(t), inhale.value(t - Spread), exhale.value(t - HoldEnd), latestLocal, tones.deep)
                    frame++
                    FocusHandoff.drawn.value = true
                }
            }
            latestDone()
        }
        Canvas(Modifier.fillMaxSize()) {
            if (frame < 0) return@Canvas
            drawRect(Color.Black, alpha = state.stageAlpha)
            // The sheet the button sat on, sinking away under the black.
            val sheet = local?.sheet
            if (sheet != null && state.ghostAlpha > 0f) {
                val r = 28f * g.dp
                drawRoundRect(
                    local.sheetColor,
                    topLeft = Offset(sheet.left, sheet.top + state.ghostShift),
                    size = Size(sheet.width, size.height - sheet.top + r),
                    cornerRadius = CornerRadius(r),
                    alpha = state.ghostAlpha,
                )
            }
            drawPath(state.pagePath, PageGrey, alpha = state.pageAlpha)
            drawPath(state.breathPath, state.breathColor, alpha = state.breathAlpha * (1f - state.bodyAlpha))
            clipPath(state.breathPath) {
                g.decorations.forEachIndexed { i, d ->
                    val size = d.size * state.shapeScale * state.spread
                    val c = Offset(d.x, d.y + (1f - state.spread) * 80f * g.dp)
                    withTransform({
                        translate(c.x - size / 2f, c.y - size / 2f)
                        rotate(d.spin * state.seconds, pivot = Offset(size / 2f, size / 2f))
                        scale(size, size, pivot = Offset.Zero)
                    }) { drawPath(shapes[i], colors[i].copy(alpha = 0.55f * state.shapeAlpha)) }
                }
            }
            // Landing: the 气's round becomes the tomato's own outline, then gives way to it.
            if (state.bodyAlpha > 0f) {
                val box = state.box
                val k = box.width / 226f
                withTransform({
                    translate(box.left, box.top)
                    scale(k, k, pivot = Offset.Zero)
                }) { drawPath(TomatoBody, androidx.compose.ui.graphics.lerp(tones.deep, tones.deeper, state.bodyAlpha), alpha = state.bodyAlpha * state.breathAlpha) }
            }
            val word = if (state.exhaleWord) exhaleText else inhaleText
            if (state.wordAlpha > 0f) {
                drawText(word, color = tones.leaf, topLeft = Offset(state.breathCenter.x - word.size.width / 2f, state.breathCenter.y - word.size.height / 2f), alpha = state.wordAlpha)
            }
            if (state.hintAlpha > 0f) {
                drawText(skipText, color = Color(0xFF8A8A8A), topLeft = Offset((size.width - skipText.size.width) / 2f, statusTop + g.dp * 8f), alpha = state.hintAlpha)
            }
        }
    }
}

/** The handed-over button and sheet, in this overlay's own px. */
private class Start(val button: Rect, val buttonColor: Color, val sheet: Rect?, val sheetColor: Color)

private val PageGrey = Color(0xFF161616)

private const val Spread = 800f
private const val InhaleEnd = Spread + 1600f
private const val HoldEnd = InhaleEnd + 1000f
private const val ExhaleEnd = HoldEnd + 3400f
private const val GatherEnd = 900f

private fun TargetBasedAnimation<Float, *>.value(ms: Float): Float = getValueFromNanos((ms.coerceAtLeast(0f) * 1_000_000f).toLong())
private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun clamp01(x: Float) = x.coerceIn(0f, 1f)

private class BreathGeometry(val dp: Float, val w: Float, val h: Float, navBottom: Float) {
    val margin = 16f * dp
    val top = 64f * dp
    val bottom = h - navBottom - 24f * dp
    val inner = w - 2f * margin
    val gap = 3f * dp
    val radius = minOf(.3f * inner, 120f * dp)
    val rest = top + (bottom - top) * .42f
    val inhaled = top + 76f * dp
    val exhaled = bottom - 64f * dp

    class Deco(val x: Float, val y: Float, val size: Float, val spin: Float)

    val decorations = listOf(
        Deco(margin + .2f * inner, bottom - .16f * inner, .62f * minOf(inner, 420f * dp), 7f),
        Deco(w - margin - .18f * inner, bottom - .06f * inner, .3f * minOf(inner, 420f * dp), 0f),
        Deco(w - margin - .32f * inner, bottom - .4f * inner, .2f * minOf(inner, 420f * dp), -11f),
    )
}

/** The seam leans and bows like two soft bodies pressing on each other (Daylist's RitualPhysics). */
private class Follower {
    var x = 0f
    private var v = 0f
    fun step(target: Float, dt: Float) {
        v += (6.5f * 6.5f * (target - x) - 2f * .42f * 6.5f * v) * dt
        x += v * dt
    }
}

private class BreathState {
    var skip = false
    var stageAlpha = 0f
    var ghostAlpha = 0f
    var ghostShift = 0f
    var breathColor = Color.Unspecified
    var bodyAlpha = 0f
    var box = Rect.Zero
    var pageAlpha = 0f
    var breathAlpha = 0f
    var wordAlpha = 0f
    var exhaleWord = false
    var hintAlpha = 0f
    var shapeScale = 1f
    var shapeAlpha = 1f
    var spread = 0f
    var seconds = 0f
    var breathCenter = Offset.Zero
    val pagePath = Path()
    val breathPath = Path()

    private val tilt = Follower()
    private val bulge = Follower()
    private var seam = 0f
    private var prevSeam = Float.NaN
    private var prevT = 0f
    private var snapSeam = 0f
    private var snapTilt = 0f
    private var snapBulge = 0f

    fun breathe(g: BreathGeometry, t: Float, spreadV: Float, eIn: Float, eOut: Float, from: Start?, deep: Color) {
        seconds = t / 1000f
        val dt = ((t - prevT) / 1000f).coerceIn(1f / 240f, 1f / 30f)
        prevT = t
        spread = spreadV
        seam = when {
            t < Spread && from != null -> g.rest
            t < Spread -> lerp(g.bottom - 40f * g.dp, g.rest, spreadV)
            t < InhaleEnd -> lerp(g.rest, g.inhaled, eIn)
            t < HoldEnd -> g.inhaled
            else -> lerp(g.inhaled, g.exhaled, eOut)
        }
        pageAlpha = clamp01(t / 450f)
        if (from != null) {
            // The sheet's scrim deepens to black; the sheet sinks; the button is the 气 already.
            stageAlpha = lerp(.32f, 1f, clamp01(t / 380f))
            ghostAlpha = 1f - clamp01(t / 220f)
            ghostShift = 72f * g.dp * clamp01(t / 320f)
            breathAlpha = 1f
            breathColor = androidx.compose.ui.graphics.lerp(from.buttonColor, deep, clamp01(t / 520f))
        } else {
            stageAlpha = clamp01(t / 250f)
            breathAlpha = clamp01(t / 250f)
            breathColor = deep
        }
        when {
            t < InhaleEnd -> { exhaleWord = false; wordAlpha = clamp01((t - 400f) / 360f) * (1f - clamp01((t - InhaleEnd + 360f) / 300f)) }
            t < HoldEnd -> { exhaleWord = true; wordAlpha = clamp01((t - InhaleEnd - 380f) / 420f) }
            else -> { exhaleWord = true; wordAlpha = 1f - clamp01((t - HoldEnd - 2720f) / 476f) }
        }
        shapeScale = when {
            t < InhaleEnd -> 1f + .1f * eIn
            t < HoldEnd -> 1.1f
            else -> 1.1f - .2f * eOut
        }
        hintAlpha = clamp01((t - 1100f) / 500f) * (1f - clamp01((t - ExhaleEnd + 500f) / 400f))
        if (prevSeam.isNaN() || t < Spread) prevSeam = seam
        val velocity = (seam - prevSeam) / dt / g.dp
        prevSeam = seam
        if (t >= Spread) {
            val topSqueeze = (1f - ((seam - g.top) / g.dp - 76f) / 280f).coerceIn(0f, 1f)
            val bottomSqueeze = (1f - ((g.bottom - seam) / g.dp - 64f) / 280f).coerceIn(0f, 1f)
            val sway = 3f * sin(2f * PI.toFloat() * ((t - Spread) / 1000f) / 4.2f)
            tilt.step((26f * topSqueeze.pow(1.3f) - 18f * bottomSqueeze.pow(1.3f) - .05f * velocity + sway).coerceIn(-30f, 30f), dt)
            bulge.step((-.03f * velocity).coerceIn(-10f, 10f), dt)
        }
        val at = seamAt(g, seam, tilt.x, bulge.x)
        if (from != null && t < Spread) {
            // 铺开 from the button: its pill widens and rises into the 气's block.
            val b = from.button
            val p = spreadV
            blob(
                breathPath,
                lerp(b.left, g.margin, p),
                lerp(b.right, g.w - g.margin, p),
                { x -> lerp(b.top, at(x) + g.gap / 2f, p) },
                { lerp(b.bottom, g.bottom, p) },
                lerp(b.height / 2f, g.radius, p),
            )
        } else {
            blob(breathPath, g.margin, g.w - g.margin, { x -> at(x) + g.gap / 2f }, { g.bottom }, g.radius)
        }
        blob(pagePath, g.margin, g.w - g.margin, { g.top }, { x -> at(x) - g.gap / 2f }, g.radius)
        val mid = g.w / 2f
        breathCenter = Offset(mid, (at(mid) + g.gap / 2f + g.bottom) / 2f)
    }

    fun snap() {
        snapSeam = seam
        snapTilt = tilt.x
        snapBulge = bulge.x
    }

    /** 气 draws together onto the tomato's body in [tomato] (its 226 box); 页 fades into the black. */
    fun gather(g: BreathGeometry, v: Float, e: Float, tomato: Rect) {
        val at = seamAt(g, snapSeam, snapTilt, snapBulge)
        val k = tomato.width / 226f
        val body = Rect(
            tomato.left + TomatoBodyBounds.left * k,
            tomato.top + TomatoBodyBounds.top * k,
            tomato.left + TomatoBodyBounds.right * k,
            tomato.top + TomatoBodyBounds.bottom * k,
        )
        box = tomato
        blob(
            breathPath,
            lerp(g.margin, body.left, v),
            lerp(g.w - g.margin, body.right, v),
            { x -> lerp(at(x) + g.gap / 2f, body.top, v) },
            { lerp(g.bottom, body.bottom, v) },
            lerp(g.radius, body.height / 2f, v),
        )
        stageAlpha = 1f - clamp01(e / 320f)
        ghostAlpha = 0f
        pageAlpha = 1f - clamp01(e / 300f)
        bodyAlpha = clamp01((clamp01(v) - .62f) / .3f)
        breathAlpha = 1f - clamp01((e - 520f) / 360f)
        shapeAlpha = 1f - clamp01(e / 250f)
        wordAlpha = minOf(wordAlpha, 1f - clamp01(e / 150f))
        hintAlpha = 0f
    }

    private fun seamAt(g: BreathGeometry, seam: Float, tiltDp: Float, bulgeDp: Float): (Float) -> Float = { x ->
        val u = (x - g.margin) / g.inner
        seam + tiltDp * g.dp * (u - .5f) - bulgeDp * g.dp * 4f * u * (1f - u)
    }
}

/** Straight sides, curved top and bottom, superellipse corners (Daylist's blob, without the shear). */
private fun blob(path: Path, x0: Float, x1: Float, top: (Float) -> Float, bottom: (Float) -> Float, radius: Float) {
    path.reset()
    val w = x1 - x0
    if (w < 2f) return
    val rl = maxOf(0f, minOf(radius, w / 2f, (bottom(x0) - top(x0)) / 2f - .5f))
    val rr = maxOf(0f, minOf(radius, w / 2f, (bottom(x1) - top(x1)) / 2f - .5f))
    var first = true
    fun pt(x: Float, y: Float) { if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y) }
    fun corner(cx: Float, cy: Float, r: Float, a0: Float, a1: Float) {
        for (i in 0..10) {
            val a = a0 + (a1 - a0) * i / 10f
            val c = cos(a)
            val s = sin(a)
            pt(cx + r * sign(c) * abs(c).pow(0.8f), cy + r * sign(s) * abs(s).pow(0.8f))
        }
    }
    val pi = PI.toFloat()
    corner(x0 + rl, top(x0 + rl) + rl, rl, pi, 1.5f * pi)
    for (i in 1 until 18) { val x = lerp(x0 + rl, x1 - rr, i / 18f); pt(x, top(x)) }
    corner(x1 - rr, top(x1 - rr) + rr, rr, 1.5f * pi, 2f * pi)
    corner(x1 - rr, bottom(x1 - rr) - rr, rr, 0f, .5f * pi)
    for (i in 1 until 18) { val x = lerp(x1 - rr, x0 + rl, i / 18f); pt(x, bottom(x)) }
    corner(x0 + rl, bottom(x0 + rl) - rl, rl, .5f * pi, pi)
    path.close()
}
