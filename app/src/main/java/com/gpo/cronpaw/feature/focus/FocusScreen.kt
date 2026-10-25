package com.gpo.cronpaw.feature.focus

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.gpo.cronpaw.R
import com.gpo.cronpaw.core.agenda.Agenda
import com.gpo.cronpaw.core.agenda.AgendaItem
import com.gpo.cronpaw.core.focus.FocusController
import com.gpo.cronpaw.core.focus.FocusMode
import com.gpo.cronpaw.core.focus.FocusSession
import com.gpo.cronpaw.core.focus.FocusSettings
import com.gpo.cronpaw.core.focus.FocusStore
import com.gpo.cronpaw.core.focus.FocusZen
import com.gpo.cronpaw.core.focus.Phase
import com.gpo.cronpaw.core.focus.advance
import com.gpo.cronpaw.navigation.Nav
import com.gpo.cronpaw.ui.components.LocalToastScope
import com.gpo.cronpaw.ui.components.rememberToast
import com.gpo.cronpaw.ui.theme.rememberRichHaptics
import com.gpo.cronpaw.ui.time.Times
import com.gpo.yoin.symbols.YoinSymbols
import java.time.LocalDate
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The clock: Google Sans Flex at a thin weight, grey rather than white (it is on all the time). */
@OptIn(ExperimentalTextApi::class)
private val ThinFlex = FontFamily(
    androidx.compose.ui.text.font.Font(
        R.font.google_sans_flex_variable,
        weight = FontWeight.ExtraLight,
        variationSettings = FontVariation.Settings(FontVariation.weight(250)),
    ),
)

private val ClockGrey = Color(0xFFA3A3A3)
private val Dim = Color(0xFF6E6E6E)
private val Dimmer = Color(0xFF454545)
private val Faint = Color(0xFF141414)
private val NearWhite = Color(0xFFEDEDED)

/**
 * The focus page: pure black, one tomato filling up, the time under it, three buttons. The
 * 「接下来」 pill at the top opens today's list; the buttons fade after 5 seconds untouched.
 */
@Composable
fun FocusScreen(nav: Nav) {
    val context = LocalContext.current
    val session by FocusStore.session.collectAsState()
    val pending by FocusController.pending.collectAsState()
    val breathOn by FocusSettings.breath.state.collectAsState()
    var leaving by remember { mutableStateOf(false) }
    val motionOn = remember { animationsOn(context) }
    // The breath outlives the plan: the clock starts as the 气 gathers, and the overlay stays
    // until the real tomato underneath has taken over.
    var breathing by remember { mutableStateOf<FocusController.Plan?>(null) }
    var gathering by remember { mutableStateOf(false) }
    // The focus page's first composition holds a frame for 100 ms or more: it happens during the
    // 屏住 on a session that isn't saved yet, hidden under the breath, and the real one replaces it.
    var warm by remember { mutableStateOf<FocusSession?>(null) }
    LaunchedEffect(session != null) { if (session != null) warm = null }
    val origin = remember { FocusHandoff.take() }
    val tomatoAt = remember { mutableStateOf<Rect?>(null) }
    val plan = pending
    if (plan != null && breathing == null && breathOn && motionOn) {
        SideEffect { breathing = plan }
    }

    // Nothing to show (ended elsewhere, from the notification, or never started): back home.
    LaunchedEffect(session == null, pending == null, breathing == null) {
        if (session == null && pending == null && breathing == null && !leaving) { leaving = true; nav.pop() }
    }
    KeepScreenOnBlackBars()
    BackHandler(enabled = pending != null && !gathering) {
        breathing = null
        warm = null
        FocusController.cancelPlan()
    }

    Box(Modifier.fillMaxSize()) {
        val s = session ?: warm
        if (s != null) FocusPage(
            s,
            afterBreath = breathing != null,
            revealed = session != null && (gathering || breathing == null),
            tomatoAt = tomatoAt,
            onLeave = { leaving = true; nav.pop() },
        )
        val b = breathing
        if (b != null) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val geo = remember(maxWidth, maxHeight) { tomatoGeometry(maxWidth.value, maxHeight.value) }
                val fallback = with(density) {
                    val side = geo.size.dp.toPx()
                    Rect(Offset(geo.cx.dp.toPx() - side / 2f, geo.cy.dp.toPx() - side / 2f), Size(side, side))
                }
                BreathOverlay(
                    tones = remember(b) { FocusTones.of(b.item.color) },
                    origin = origin,
                    tomato = { tomatoAt.value },
                    fallback = fallback,
                    onGather = {
                        gathering = true
                        if (FocusController.pending.value == b) FocusController.start(context, b)
                    },
                    onDone = { breathing = null },
                    onHold = { if (FocusStore.value == null && FocusController.pending.value == b) warm = FocusController.preview(context, b) },
                )
            }
        } else if (plan != null && (!breathOn || !motionOn)) {
            LaunchedEffect(plan) { FocusController.start(context, plan) }
        }
    }
}

/** Where the tomato sits, so the breath can gather into it (dp). */
private class TomatoGeo(val cx: Float, val cy: Float, val size: Float)

private fun tomatoGeometry(w: Float, h: Float): TomatoGeo {
    val wide = w > h && (w >= 600f || h < 480f)
    return if (wide) {
        val size = minOf(w * 0.42f, h * 0.72f)
        TomatoGeo(w * 0.28f, h / 2f, size)
    } else {
        val size = minOf(w * 0.74f, h * 0.4f, 420f)
        TomatoGeo(w / 2f, h * 0.36f, size)
    }
}

@Composable
private fun KeepScreenOnBlackBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val lightStatus = controller?.isAppearanceLightStatusBars
        val lightNav = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            lightStatus?.let { controller.isAppearanceLightStatusBars = it }
            lightNav?.let { controller.isAppearanceLightNavigationBars = it }
        }
    }
}

@Composable
private fun FocusPage(s0: FocusSession, afterBreath: Boolean, revealed: Boolean, tomatoAt: MutableState<Rect?>, onLeave: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    // After the breath the tomato is the 气 that just landed; the rest settles in around it.
    val intro = remember { Animatable(if (afterBreath) 0f else 1f) }
    val tomatoIn = remember { Animatable(if (afterBreath) 0f else 1f) }
    LaunchedEffect(revealed) {
        if (!revealed || intro.value >= 1f) return@LaunchedEffect
        launch { delay(300); tomatoIn.animateTo(1f, spring(dampingRatio = 1f, stiffness = 380f)) }
        delay(120)
        intro.animateTo(1f, spring(dampingRatio = 1f, stiffness = 110f))
    }
    val fade = Modifier.graphicsLayer { alpha = intro.value }
    val haptics = rememberRichHaptics()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            // The app is in front: 上次专注's 「最后一次用」 stays within half a minute.
            if (now / 1000L % 30L == 0L) FocusController.touch(context)
            now = System.currentTimeMillis()
            delay(1000L - now % 1000L)
        }
    }
    // The app is in front: move past a boundary here too (the alarm makes the sound).
    val s = advance(s0, now)
    var harvestPop by remember { mutableIntStateOf(0) }
    val harvested = s.harvested(now)
    var lastHarvest by remember { mutableIntStateOf(harvested) }
    LaunchedEffect(harvested) {
        if (harvested > lastHarvest) { harvestPop++; haptics.celebrate() }
        lastHarvest = harvested
    }
    LaunchedEffect(s.phase, s.round) { if (s != s0) FocusController.tick(context, alert = false) }

    var ending by remember { mutableStateOf<Long?>(null) }
    var todayOpen by remember { mutableStateOf(false) }
    val endAt = ending ?: if (s.phase == Phase.Done) s.actualEnd(now) else null
    BackHandler(enabled = ending != null && s.phase != Phase.Done) { ending = null }
    BackHandler(enabled = todayOpen) { todayOpen = false }

    val rest = s.phase == Phase.Break || s.phase == Phase.Waiting
    val tones = remember(s.color, rest) { FocusTones.of(s.color, rest) }
    val focusTones = remember(s.color) { FocusTones.of(s.color) }

    // Idle: after 5 s untouched only the tomato, the time and a dimmer pill stay.
    var touches by remember { mutableIntStateOf(0) }
    var idle by remember { mutableStateOf(false) }
    LaunchedEffect(touches, s.paused, ending) {
        idle = false
        if (s.paused || ending != null) return@LaunchedEffect
        delay(5_000)
        idle = true
    }
    val chrome by animateFloatAsState(if (idle) 0f else 1f, spring(dampingRatio = 1f, stiffness = 60f), label = "chrome")
    // Burn-in: the whole picture wanders a few pixels, once a minute.
    val shiftIndex = (now / 60_000L % Shifts.size).toInt()
    val shift by animateIntOffsetAsState(Shifts[shiftIndex], spring(dampingRatio = 1f, stiffness = 20f), label = "shift")

    val today = rememberToday(s, now)
    val pillRect = remember { mutableStateOf(Rect.Zero) }
    val open = remember { Animatable(0f) }
    LaunchedEffect(todayOpen) { open.animateTo(if (todayOpen) 1f else 0f, spring(dampingRatio = 0.85f, stiffness = 320f)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        touches++
                    }
                }
            },
    ) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val p = open.value.coerceIn(0f, 1f)
                scaleX = 1f - 0.06f * p
                scaleY = 1f - 0.06f * p
                alpha = 1f - 0.75f * p
            },
        ) {
            AnimatedContent(
                targetState = endAt != null,
                transitionSpec = { (fadeIn(spring(stiffness = 200f)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = 260f), initialScale = 0.94f)) togetherWith fadeOut(spring(stiffness = 400f)) },
                label = "end",
            ) { isEnd ->
                if (isEnd && endAt != null) {
                    EndPage(s, endAt, focusTones, onLeave)
                } else {
                    BoxWithConstraints(Modifier.fillMaxSize().offset { shift }) {
                        val wide = maxWidth > maxHeight && (maxWidth >= 600.dp || maxHeight < 480.dp)
                        val geo = tomatoGeometry(maxWidth.value, maxHeight.value)
                        // A tablet on a desk is read from further away: the right half grows as a whole.
                        val k = deskScale(maxWidth.value)
                        val deskClock = (minOf(maxWidth.value * 0.13f, maxHeight.value * 0.26f, 220f).coerceAtLeast(84f) / k).sp
                        val pill: @Composable (Modifier) -> Unit = { m ->
                            NextPill(today.next, now, chrome, m.then(fade).onGloballyPositioned { pillRect.value = it.boundsInRoot() }) { todayOpen = true }
                        }
                        val tomato: @Composable (Modifier) -> Unit = { m ->
                            Tomato(
                                level = s.progress(now),
                                tones = tones,
                                running = s.running,
                                open = if (s.phase == Phase.Break) 0.15f else 1f,
                                popKey = harvestPop,
                                modifier = m
                                    .onGloballyPositioned { tomatoAt.value = tomatoBox(it.screenRect(view)) }
                                    .graphicsLayer { alpha = tomatoIn.value },
                            )
                        }
                        if (wide) {
                            Row(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                    tomato(Modifier.size(geo.size.dp))
                                }
                                Scaled(k) { Column(Modifier.weight(1f).fillMaxHeight().then(fade), verticalArrangement = Arrangement.Center) {
                                    pill(Modifier)
                                    Spacer(Modifier.height(24.dp))
                                    // The desk clock: as large as the right half allows.
                                    Clock(s, now, deskClock)
                                    Title(s, focusTones, chrome)
                                    Spacer(Modifier.height(16.dp))
                                    Harvest(s, now, focusTones, Arrangement.Start)
                                    Spacer(Modifier.height(28.dp))
                                    Controls(s, now, tones, focusTones, chrome, onEnd = { ending = s.actualEnd(System.currentTimeMillis()) }, wide = true)
                                } }
                            }
                        } else {
                            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(Modifier.height(12.dp))
                                pill(Modifier)
                                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        tomato(Modifier.size(geo.size.dp))
                                        Spacer(Modifier.height(8.dp))
                                        Column(fade, horizontalAlignment = Alignment.CenterHorizontally) {
                                            Harvest(s, now, focusTones, Arrangement.Center)
                                            Spacer(Modifier.height(20.dp))
                                            Clock(s, now, 84.sp)
                                            Title(s, focusTones, chrome)
                                        }
                                    }
                                }
                                Box(fade) { Controls(s, now, tones, focusTones, chrome, onEnd = { ending = s.actualEnd(System.currentTimeMillis()) }) }
                                Spacer(Modifier.height(28.dp))
                            }
                        }
                        IconButton(
                            onClick = onLeave,
                            modifier = Modifier.statusBarsPadding().padding(4.dp).graphicsLayer { alpha = chrome * intro.value },
                            enabled = chrome > 0.5f,
                        ) { Icon(YoinSymbols.Back, "回到日程", tint = Dim) }
                    }
                }
            }
        }
        if (open.value > 0.001f || todayOpen) {
            val rect = pillRect.value
            val density = LocalDensity.current
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        clip = true
                        shape = PillToPage(rect, open.value, with(density) { 28.dp.toPx() })
                    }
                    .background(Color.Black)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {},
            ) {
                BoxWithConstraints {
                    Scaled(deskScale(maxWidth.value)) {
                        TodayPage(s, now, today, focusTones, progress = { open.value }, onClose = { todayOpen = false })
                    }
                }
            }
        }
    }
}

/** How much bigger a wide screen draws its text and buttons (1 on phones, up to 1.5 on a tablet). */
private fun deskScale(widthDp: Float): Float = if (widthDp < 840f) 1f else (widthDp / 900f).coerceIn(1f, 1.5f)

@Composable
private fun RowScope.Scaled(k: Float, content: @Composable RowScope.() -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density * k, d.fontScale)) { content() }
}

@Composable
private fun Scaled(k: Float, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density * k, d.fontScale)) { content() }
}

private val Shifts = listOf(IntOffset(0, 0), IntOffset(3, 2), IntOffset(-2, 4), IntOffset(-4, -1), IntOffset(2, -3), IntOffset(4, 1))

/** The pill's rect grows into the whole page; its round ends become the screen's corners. */
private class PillToPage(val from: Rect, val p: Float, val endCorner: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val t = p.coerceIn(0f, 1.02f)
        val src = if (from == Rect.Zero) Rect(size.width / 2f - 1f, 0f, size.width / 2f + 1f, 40f) else from
        fun l(a: Float, b: Float) = a + (b - a) * t
        val r = Rect(l(src.left, 0f), l(src.top, 0f), l(src.right, size.width), l(src.bottom, size.height))
        val corner = l(src.height / 2f, endCorner).coerceAtLeast(0f)
        return Outline.Rounded(RoundRect(r, CornerRadius(corner)))
    }
}

/** Today, split for the pill and the 今天 page. */
private class Today(
    val past: List<AgendaItem>,
    val now: List<AgendaItem>,
    val next: AgendaItem?,
    val later: List<AgendaItem>,
)

@Composable
private fun rememberToday(s: FocusSession, now: Long): Today {
    val items by Agenda.items.collectAsState()
    val minute = now / 60_000L
    return remember(items, minute, s.itemKey) {
        val day = LocalDate.now()
        val from = Times.startOf(day)
        val to = Times.startOf(day.plusDays(1))
        val list = items.filter { !it.allDay && it.start < to && it.end > from }.sortedBy { it.start }
        val past = list.filter { it.end <= now && it.key != s.itemKey }
        val current = list.filter { it.start <= now && it.end > now && it.key != s.itemKey }
        val upcoming = list.filter { it.start > now && it.key != s.itemKey }
        Today(past, current, upcoming.firstOrNull(), upcoming.drop(1))
    }
}

@Composable
private fun NextPill(next: AgendaItem?, now: Long, chrome: Float, modifier: Modifier, onClick: () -> Unit) {
    val alpha = 0.5f + 0.5f * chrome
    Row(
        modifier
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(Faint)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
            .widthIn(max = 320.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (next == null) {
            Text("今天", style = MaterialTheme.typography.labelLarge, color = Dim)
        } else {
            Text("接下来", style = MaterialTheme.typography.labelMedium, color = Dim)
            Spacer(Modifier.width(8.dp))
            Text(Times.hm(next.start), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = ClockGrey)
            Spacer(Modifier.width(6.dp))
            Text(next.title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge, color = ClockGrey, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(2.dp))
        Icon(YoinSymbols.ChevronRight, null, Modifier.size(18.dp), tint = Dim)
    }
}

private fun clock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = total % 3600 / 60
    val sec = (total % 60).toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$sec" else "${m.toString().padStart(2, '0')}:$sec"
}

/** What the clock shows: the countdown for 番茄, time focused for 自由. */
private fun clockText(s: FocusSession, now: Long): String = when {
    s.mode == FocusMode.Free -> clock(s.focused(now))
    s.phase == Phase.Waiting || s.phase == Phase.Done -> "00:00"
    else -> clock(s.remaining(now) + 999)
}

@Composable
private fun Clock(s: FocusSession, now: Long, size: androidx.compose.ui.unit.TextUnit) {
    val color by animateColorAsState(if (s.paused) Dimmer else ClockGrey, spring(stiffness = 200f), label = "clock")
    Text(
        clockText(s, now),
        style = TextStyle(fontFamily = ThinFlex, fontWeight = FontWeight.ExtraLight, fontSize = size, fontFeatureSettings = "tnum", letterSpacing = (-1).sp),
        color = color,
        maxLines = 1,
    )
}

private fun statusLabel(s: FocusSession): String = when {
    s.paused -> "已暂停"
    s.phase == Phase.Break -> "休息"
    s.phase == Phase.Waiting -> "休息好了"
    s.mode == FocusMode.Free -> "已专注"
    s.rounds > 1 -> "第 ${s.round + 1} / ${s.rounds} 轮"
    else -> "剩余"
}

@Composable
private fun Title(s: FocusSession, tones: FocusTones, chrome: Float) {
    Text(s.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tones.bright, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Row(Modifier.padding(top = 4.dp).graphicsLayer { alpha = chrome }, verticalAlignment = Alignment.CenterVertically) {
        Text(statusLabel(s), style = MaterialTheme.typography.labelMedium, color = Dim)
        if (FocusZen.wanted(s) && FocusZen.hasAccess(LocalContext.current)) {
            Spacer(Modifier.width(10.dp))
            Text("专注模式", Modifier.clip(CircleShape).background(Faint).padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Dim)
        }
    }
}

/** One mini tomato per round (自由: per 25 minutes, plus the one growing). */
@Composable
private fun Harvest(s: FocusSession, now: Long, tones: FocusTones, arrangement: Arrangement.Horizontal) {
    val done = s.harvested(now)
    val count = if (s.mode == FocusMode.Free) done + 1 else s.rounds
    if (count <= 1 && s.mode == FocusMode.Pomodoro) return
    Row(horizontalArrangement = arrangement) {
        repeat(count.coerceAtMost(12)) { i ->
            val scale by animateFloatAsState(if (i < done) 1f else 0.82f, spring(dampingRatio = 0.4f, stiffness = 300f), label = "mini")
            MiniTomato(i < done, tones, Modifier.padding(horizontal = 2.dp).size(18.dp).graphicsLayer { scaleX = scale; scaleY = scale })
        }
    }
}

@Composable
private fun Controls(s: FocusSession, now: Long, tones: FocusTones, focusTones: FocusTones, chrome: Float, onEnd: () -> Unit, wide: Boolean = false) {
    val context = LocalContext.current
    val haptics = rememberRichHaptics()
    val enabled = chrome > 0.5f
    Row(Modifier.graphicsLayer { alpha = chrome }, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        val canPlus = s.mode == FocusMode.Pomodoro && s.phase == Phase.Focus
        // 自由 has no +5 分; on a phone its place stays empty so the main button stays centred.
        if (s.mode == FocusMode.Pomodoro || !wide) {
            DarkButton(if (s.mode == FocusMode.Free) "" else "+5 分", enabled = enabled && canPlus, modifier = Modifier.width(88.dp)) {
                haptics.tick(); FocusController.plusFive(context)
            }
        }
        // The one filled button: a circle while running, a pill when it waits for you.
        val waiting = s.phase == Phase.Waiting
        val pill = s.paused || waiting
        val width by animateDpAsState(if (pill) 120.dp else 76.dp, spring(dampingRatio = 0.6f, stiffness = 380f), label = "main")
        Box(
            Modifier
                .size(width, 76.dp)
                .clip(CircleShape)
                .background(focusTones.bright)
                .clickable(enabled = enabled) {
                    haptics.tick()
                    when {
                        waiting -> FocusController.nextRound(context)
                        s.paused -> FocusController.resume(context)
                        else -> FocusController.pause(context)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(pill, transitionSpec = { fadeIn(spring(stiffness = 500f)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f), initialScale = 0.6f) togetherWith fadeOut(spring(stiffness = 700f)) }, label = "icon") { p ->
                Icon(
                    if (p) YoinSymbols.PlayFilled else YoinSymbols.PauseFilled,
                    when { waiting -> "下一轮"; s.paused -> "继续"; else -> "暂停" },
                    Modifier.size(32.dp),
                    tint = Color.Black,
                )
            }
        }
        HoldToEnd(enabled, Modifier.width(88.dp), tones, onEnd)
    }
}

@Composable
private fun DarkButton(label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(56.dp).clip(CircleShape).background(if (label.isEmpty()) Color.Transparent else Faint).clickable(enabled = enabled && label.isNotEmpty(), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = if (enabled) ClockGrey else Dimmer)
    }
}

/** 结束 needs a 0.6 s hold, so a pocket or a sleeve doesn't end the session. */
@Composable
private fun HoldToEnd(enabled: Boolean, modifier: Modifier, tones: FocusTones, onEnd: () -> Unit) {
    val haptics = rememberRichHaptics()
    val scope = rememberCoroutineScope()
    val hold = remember { Animatable(0f) }
    var hint by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(56.dp)
            .clip(CircleShape)
            .background(Faint)
            .drawBehind { drawRect(tones.deep, size = Size(size.width * hold.value, size.height)) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    haptics.charge()
                    val job = scope.launch {
                        hold.animateTo(1f, tween(600, easing = LinearEasing))
                        haptics.chargeFull()
                        onEnd()
                    }
                    val released = tryAwaitRelease()
                    if (hold.value < 1f) {
                        job.cancel()
                        haptics.cancel()
                        if (released) hint = true
                        scope.launch { hold.animateTo(0f, spring(stiffness = 500f)) }
                    } else scope.launch { hold.snapTo(0f) }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(if (hint) "按住" else "结束", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = if (enabled) ClockGrey else Dimmer)
    }
    LaunchedEffect(hint) { if (hint) { delay(1500); hint = false } }
}

// ── 今天 ──────────────────────────────────────────────────────────────────────

/** Read-only, pure black: brightness, size and spacing carry the order (现在 > 接下来 > 之后 > 已过). */
@Composable
private fun TodayPage(s: FocusSession, now: Long, today: Today, tones: FocusTones, progress: () -> Float, onClose: () -> Unit) {
    var showPast by remember { mutableStateOf(false) }
    val day = LocalDate.now()
    // Each group drops in a little after the one above it.
    fun Modifier.group(i: Int) = graphicsLayer {
        val t = ((progress() - 0.25f - i * 0.1f) / 0.55f).coerceIn(0f, 1f)
        alpha = t
        translationY = (1f - t) * -24.dp.toPx()
    }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            // A reading column on a tablet, not a strip across the whole screen.
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 600.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp).group(0), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.offset(x = (-12).dp)) { Icon(YoinSymbols.Back, "回到专注", tint = Dim) }
            Text("今天", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ClockGrey)
            Spacer(Modifier.width(10.dp))
            Text("${day.monthValue}月${day.dayOfMonth}日 ${Times.weekday(day)}", style = MaterialTheme.typography.labelLarge, color = Dim)
        }
        if (today.past.isNotEmpty()) {
            Column(Modifier.group(1)) {
                Text(
                    "已过 ${today.past.size} 项",
                    Modifier.padding(top = 12.dp).clip(RoundedCornerShape(8.dp)).clickable { showPast = !showPast }.padding(vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Dimmer,
                )
                AnimatedVisibility(showPast, enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 380f)) + fadeIn(), exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = 500f)) + fadeOut()) {
                    Column { today.past.forEach { LaterRow(it, Dimmer) } }
                }
            }
        }
        GroupTitle("现在", Modifier.group(2))
        Column(Modifier.group(2)) {
            Row(verticalAlignment = Alignment.Bottom) {
                if (s.itemStart > 0) {
                    Text(Times.hm(s.itemStart), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tones.bright)
                    Spacer(Modifier.width(10.dp))
                }
                Text(s.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = tones.bright, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Faint)) {
                Box(Modifier.fillMaxWidth(sessionProgress(s, now)).height(4.dp).clip(CircleShape).background(tones.bright))
            }
            Row(Modifier.padding(top = 6.dp)) {
                if (s.itemEnd > 0 && s.itemEnd > s.itemStart) Text("到 ${Times.hm(s.itemEnd)}", style = MaterialTheme.typography.labelMedium, color = Dim)
                Spacer(Modifier.weight(1f))
                Text(if (s.mode == FocusMode.Free) "已专注 ${clockText(s, now)}" else "${clockText(s, now)} 剩余", style = MaterialTheme.typography.labelMedium, color = Dim)
            }
            today.now.forEach { LaterRow(it, Dim) }
        }
        val next = today.next
        if (next != null) {
            GroupTitle("接下来", Modifier.group(3))
            Column(Modifier.group(3)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Times.hm(next.start), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = NearWhite)
                    Spacer(Modifier.width(10.dp))
                    Text(next.title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = NearWhite, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    Text(untilText(next.start - now), Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = ClockGrey)
                }
                val meta = listOfNotNull(next.location?.takeIf { it.isNotBlank() }, Times.durationText(next.durationMinutes))
                Text(meta.joinToString("  "), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (today.later.isNotEmpty()) {
            GroupTitle("之后", Modifier.group(4))
            Column(Modifier.group(4)) { today.later.forEach { LaterRow(it, Color(0xFF8C8C8C)) } }
        }
        if (next == null && today.later.isEmpty()) {
            Text("今天没有别的安排了", Modifier.padding(top = 24.dp).group(3), style = MaterialTheme.typography.bodyLarge, color = Dim)
        }
    }
}

/** How far through the whole session (all rounds) it is, for the 今天 bar. */
private fun sessionProgress(s: FocusSession, now: Long): Float {
    if (s.mode == FocusMode.Free) return s.progress(now).coerceIn(0.02f, 1f)
    val total = (0 until s.rounds).sumOf { s.focusLenMin(it) }.coerceAtLeast(1)
    return (s.focused(now) / 60_000f / total).coerceIn(0.02f, 1f)
}

private fun untilText(ms: Long): String {
    val m = max(1L, ms / 60_000L)
    return if (m < 60) "$m 分钟后" else if (m % 60 == 0L || m >= 180) "${(m + 30) / 60} 小时后" else "${m / 60} 小时 ${m % 60} 分后"
}

@Composable
private fun GroupTitle(text: String, modifier: Modifier) {
    Text(text, modifier.padding(top = 24.dp, bottom = 8.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Dimmer)
}

@Composable
private fun LaterRow(item: AgendaItem, ink: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(Times.hm(item.start), Modifier.width(52.dp), style = MaterialTheme.typography.bodyLarge, color = ink)
        val dot = Color(item.color).copy(alpha = 0.6f)
        if (item.todo) Box(Modifier.size(10.dp).clip(CircleShape).drawBehind { drawCircle(dot, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx())) })
        else Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(10.dp))
        Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ── 结束 ──────────────────────────────────────────────────────────────────────

@Composable
private fun EndPage(s: FocusSession, endAt: Long, tones: FocusTones, onLeave: () -> Unit) {
    val context = LocalContext.current
    // The write outlives this page, which leaves as soon as the session is cleared.
    val scope = LocalToastScope.current ?: rememberCoroutineScope()
    val toast = rememberToast()
    val haptics = rememberRichHaptics()
    var leaving by remember { mutableStateOf(false) }
    val minutes = ((endAt - s.startedAt + 30_000L) / 60_000L).toInt()
    // Two ways out only. 完成: the item takes the time actually used and a todo is ticked.
    // 放弃: nothing is written to the item.
    val complete: () -> Unit = {
        if (!leaving) {
            leaving = true
            haptics.celebrate()
            onLeave()
            scope.launch {
                if (!FocusController.finish(context, rewrite = minutes >= 1, markDone = true, endAt = endAt)) toast("没改成")
            }
        }
    }
    val giveUp: () -> Unit = {
        if (!leaving) {
            leaving = true
            haptics.lowTick()
            onLeave()
            FocusController.discard(context, endAt = endAt)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        val wide = maxWidth > maxHeight && (maxWidth >= 600.dp || maxHeight < 480.dp)
        val endScale = deskScale(maxWidth.value)
        val tomatoSize = if (wide) minOf(maxHeight * 0.6f, 320.dp) else minOf(maxWidth * 0.5f, 240.dp)
        val tomato: @Composable () -> Unit = {
            Box(contentAlignment = Alignment.Center) {
                Tomato(1f, tones, running = false, open = 1f, popKey = 1, modifier = Modifier.size(tomatoSize), full = true)
                Icon(YoinSymbols.Check, null, Modifier.size(tomatoSize * 0.28f).offset(y = tomatoSize * 0.08f), tint = Color.Black)
            }
        }
        val details: @Composable () -> Unit = {
            Column(Modifier.widthIn(max = 420.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (minutes < 1) "<1" else "$minutes", style = TextStyle(fontFamily = ThinFlex, fontWeight = FontWeight.ExtraLight, fontSize = 72.sp, fontFeatureSettings = "tnum"), color = ClockGrey)
                    Text("分钟", Modifier.padding(start = 6.dp, bottom = 14.dp), style = MaterialTheme.typography.titleMedium, color = Dim)
                }
                Text(s.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = tones.bright, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                val now = endAt
                Harvest(s, now, tones, Arrangement.Start)
                Spacer(Modifier.height(24.dp))
                if (s.source != null && minutes >= 1) {
                    // Was → becomes: what 完成 will write.
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (s.itemStart > 0) "${Times.hm(s.itemStart)}–${Times.hm(s.itemEnd)}" else "未排期", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Dim)
                        Text("→", Modifier.padding(horizontal = 10.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Dim)
                        Text("${Times.hm(s.startedAt)}–${Times.hm(endAt)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = NearWhite)
                    }
                }
                Spacer(Modifier.height(28.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EndButton("完成", filled = true, tones, Modifier.weight(1f)) { complete() }
                    EndButton("放弃", filled = false, tones, Modifier.weight(1f)) { giveUp() }
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { tomato() }
                // Qualified so the outer Row doesn't pull in RowScope.Scaled.
                Box(Modifier.weight(1f)) { com.gpo.cronpaw.feature.focus.Scaled(endScale) { details() } }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(48.dp))
                tomato()
                Spacer(Modifier.height(24.dp))
                details()
            }
        }
    }
}

@Composable
private fun EndButton(label: String, filled: Boolean, tones: FocusTones, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(56.dp).clip(CircleShape).background(if (filled) tones.bright else Faint).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = if (filled) Color.Black else ClockGrey)
    }
}
