package com.gpo.cronpaw.feature.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import java.time.YearMonth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.gpo.cronpaw.core.agenda.Actions
import com.gpo.cronpaw.core.agenda.Agenda
import com.gpo.cronpaw.core.agenda.AgendaItem
import com.gpo.cronpaw.core.settings.AppSettings
import com.gpo.cronpaw.feature.create.CreateSheet
import com.gpo.cronpaw.feature.create.CreateTray
import com.gpo.cronpaw.feature.create.Draft
import com.gpo.cronpaw.feature.create.TrayDrag
import com.gpo.cronpaw.feature.item.ItemSheet
import com.gpo.cronpaw.navigation.Nav
import com.gpo.cronpaw.ui.components.LocalSnackbarHost
import com.gpo.cronpaw.ui.components.LocalToastScope
import com.gpo.cronpaw.ui.components.rememberToast
import com.gpo.cronpaw.ui.theme.CronSymbols
import com.gpo.cronpaw.ui.theme.YoinMotion
import com.gpo.cronpaw.ui.theme.rememberRichHaptics
import com.gpo.cronpaw.ui.time.Times
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/** Asks from other pages (search) for the home timeline. */
object HomeRequests {
    data class Jump(val date: LocalDate, val exactFirst: Boolean = false, val open: AgendaItem? = null)
    val jump = MutableStateFlow<Jump?>(null)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(nav: Nav) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rootScope = LocalToastScope.current ?: scope
    val snackbar = LocalSnackbarHost.current
    val haptics = rememberRichHaptics()
    val toast = rememberToast()
    val density = LocalDensity.current
    val cs = MaterialTheme.colorScheme

    // A tablet or unfolded screen: the drawer stays, and 周 is the first view until one is picked.
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    LaunchedEffect(wide) {
        if (wide && !AppSettings.viewChosen.value && AppSettings.viewDays.value == 3) {
            AppSettings.set(AppSettings.viewDays, 7)
            AppSettings.set(AppSettings.timelineDays, 7)
        }
    }
    val viewDays by AppSettings.viewDays.state.collectAsState()
    val tl = rememberTimelineState(if (viewDays > 0) viewDays else AppSettings.timelineDays.value)
    val monthPager = rememberPagerState(initialPage = MonthMid) { MonthMid * 2 }
    val scheduleList = rememberLazyListState()
    val items by Agenda.items.collectAsState()
    var shown by remember { mutableIntStateOf(viewDays) }
    val scheduleRows = remember(items, viewDays, shown) {
        if (viewDays == AppSettings.ViewSchedule || shown == AppSettings.ViewSchedule) scheduleRows(items) else emptyList()
    }

    // ── Switching views: the old one fades and shrinks a little, the new one springs in ──
    val viewIn = remember { Animatable(1f) }
    LaunchedEffect(viewDays) {
        if (viewDays == 2) { AppSettings.set(AppSettings.viewDays, 3); return@LaunchedEffect } // 2 天 is gone
        // A switch cut short (two taps in a row, the wide-screen default, unfolding) can leave the
        // view faded out; whatever happens, the view that ends up chosen is shown.
        if (shown == viewDays) { viewIn.animateTo(1f); return@LaunchedEffect }
        try {
            viewIn.animateTo(0f, spring(stiffness = Spring.StiffnessHigh))
            // The month pager and the schedule list only exist once they are shown, and scrolling
            // either waits for its first layout: switch first, then scroll, or it waits forever.
            val from = if (shown > 0) tl.first else LocalDate.now()
            if (viewDays > 0 && tl.days != viewDays) tl.switchDays(viewDays)
            shown = viewDays
            when (viewDays) {
                AppSettings.ViewMonth -> monthPager.scrollToPage(monthPage(YearMonth.from(from)))
                AppSettings.ViewSchedule -> scheduleList.scrollToItem(scheduleIndexOf(scheduleRows, from))
            }
            viewIn.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
        } finally {
            if (viewIn.value < 1f) withContext(NonCancellable) { viewIn.snapTo(1f) }
        }
    }
    fun openOnTimeline(date: LocalDate) {
        val days = AppSettings.timelineDays.value.takeIf { it > 0 } ?: 3
        scope.launch {
            tl.show(date, animate = false)
            AppSettings.set(AppSettings.viewDays, days)
        }
    }

    // ── Calendar permission ──
    var hasCalendar by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED)
    }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        hasCalendar = r[Manifest.permission.READ_CALENDAR] == true
        if (hasCalendar) Agenda.refresh()
    }
    LaunchedEffect(Unit) {
        if (!hasCalendar) calendarPermission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }

    // Load what is on screen (and a margin) whenever the page moves.
    LaunchedEffect(tl) {
        snapshotFlow { Triple(tl.first, tl.last, shown > 0) }.collect { (a, b, onTimeline) -> if (onTimeline) Agenda.ensure(a, b) }
    }

    val currentScheduleRows by rememberUpdatedState(scheduleRows)

    // ── Sheets ──
    var selected by remember { mutableStateOf<AgendaItem?>(null) }
    var showUpcoming by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<Draft?>(null) }

    LaunchedEffect(Unit) {
        HomeRequests.jump.collect { j ->
            if (j == null) return@collect
            HomeRequests.jump.value = null
            when {
                shown == AppSettings.ViewMonth -> monthPager.animateScrollToPage(monthPage(YearMonth.from(j.date)))
                shown == AppSettings.ViewSchedule -> scheduleList.animateScrollToItem(scheduleIndexOf(currentScheduleRows, j.date))
                j.exactFirst -> tl.showFrom(j.date)
                else -> tl.show(j.date)
            }
            j.open?.let { item ->
                if (shown > 0 && !item.allDay) tl.scrollToMinute(Times.minuteOfDay(item.start) - 60)
                selected = item
            }
        }
    }

    // ── Create: tray and the bar being carried ──
    var trayOpen by remember { mutableStateOf(false) }
    var customMinutes by remember { mutableIntStateOf(45) }
    var customEditing by remember { mutableStateOf(false) }
    var carried by remember { mutableStateOf<Pair<Int, Offset>?>(null) }
    BackHandler(trayOpen) { trayOpen = false; customEditing = false }

    fun nextQuarter(): Long {
        val now = System.currentTimeMillis()
        val q = 15 * 60_000L
        return (now / q + 1) * q
    }

    fun updateGhost(minutes: Int, root: Offset, item: AgendaItem? = null) {
        val slot = tl.slotAt(root, minutes)
        if (slot != tl.ghost?.start) {
            if (slot != null) haptics.tick()
            tl.ghost = slot?.let { Ghost(it, minutes, item) }
        }
    }

    val carry = remember {
        object : TrayDrag {
            override fun lift(minutes: Int, root: Offset) {
                customEditing = false
                carried = minutes to root
                updateGhost(minutes, root)
            }
            override fun move(root: Offset) {
                val c = carried ?: return
                carried = c.first to root
                updateGhost(c.first, root)
            }
            override fun drop() {
                val g = tl.ghost
                carried = null
                tl.ghost = null
                if (g != null) {
                    haptics.chargeFull()
                    trayOpen = false
                    draft = Draft(g.start, g.minutes)
                }
            }
            override fun cancel() {
                carried = null
                tl.ghost = null
            }
            override fun tap(minutes: Int) {
                trayOpen = false
                draft = Draft(nextQuarter(), minutes)
            }
        }
    }
    // Long-press an item to move it: the same carry, the item's top following the finger.
    var moving by remember { mutableStateOf<AgendaItem?>(null) }
    // 未排期: the database whose dateless pages are out in the tray, and the ones placed since.
    var trayDb by remember { mutableStateOf<String?>(null) }
    var scheduled by remember { mutableStateOf(emptySet<String>()) }
    BackHandler(trayDb != null && carried == null) { trayDb = null }
    val mover = remember {
        object : MoveDrag {
            var grabY = 0f
            override fun start(item: AgendaItem, root: Offset, grabY: Float) {
                if (item.allDay) return
                this.grabY = grabY
                haptics.liftOff()
                moving = item
                val at = root.copy(y = root.y - grabY)
                // A dateless page has no length yet: the default one.
                val minutes = if (item.end > item.start) item.durationMinutes.coerceAtLeast(15) else AppSettings.defaultMinutes.value
                carried = minutes to at
                updateGhost(carried!!.first, at, item)
            }
            override fun move(root: Offset) {
                val c = carried ?: return
                if (moving == null) return
                val at = root.copy(y = root.y - grabY)
                carried = c.first to at
                updateGhost(c.first, at, moving)
            }
            override fun drop() {
                val item = moving ?: return
                val g = tl.ghost
                carried = null
                moving = null
                tl.ghost = null
                if (g != null && g.start != item.start) {
                    haptics.chargeFull()
                    val unscheduled = item.end <= item.start
                    if (unscheduled) scheduled = scheduled + item.key
                    scope.launch {
                        val ok = Actions.move(context, item, g.start, g.start + g.minutes * 60_000L)
                        if (!ok) {
                            if (unscheduled) { scheduled = scheduled - item.key; Agenda.forget(item.key) }
                            toast("没挪成")
                        } else if (unscheduled) Agenda.afterWrite()
                    }
                }
            }
            override fun cancel() {
                carried = null
                moving = null
                tl.ghost = null
            }
        }
    }

    // Near the top or bottom edge while carrying: scroll the day.
    LaunchedEffect(carried != null) {
        while (isActive && carried != null) {
            val c = carried ?: break
            val edge = with(density) { 72.dp.toPx() }
            val v = tl.viewport
            val dy = when {
                c.second.y > v.bottom - edge -> 14f
                c.second.y < v.top + edge * 0.6f -> -14f
                else -> 0f
            }
            if (dy != 0f) {
                tl.scroll.scrollBy(dy)
                updateGhost(c.first, c.second, moving)
            }
            delay(16)
        }
    }

    val drawer = rememberDrawerState(DrawerValue.Closed)
    val sideOpen by AppSettings.sidebarOpen.state.collectAsState()
    BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }

    val drawerContent: @Composable () -> Unit = {
            CalendarDrawer(
                permanent = wide,
                viewDays = viewDays,
                onViewDays = {
                    AppSettings.set(AppSettings.viewChosen, true)
                    AppSettings.set(AppSettings.viewDays, it)
                    if (it > 0) AppSettings.set(AppSettings.timelineDays, it)
                },
                onAddDatabase = { scope.launch { drawer.close() }; nav.push("db:") },
                onSettings = { scope.launch { drawer.close() }; nav.push("settings") },
                onUnscheduled = { id ->
                    scope.launch { drawer.close() }
                    trayOpen = false
                    // Placing needs the timeline.
                    if (viewDays <= 0) AppSettings.set(AppSettings.viewDays, AppSettings.timelineDays.value)
                    scheduled = emptySet()
                    trayDb = id
                },
            )
    }
    // Where the body sits in the window: the carried bar is placed in window terms, and a
    // permanent drawer pushes the body right.
    var bodyOrigin by remember { mutableStateOf(Offset.Zero) }
    val body: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize().background(cs.surface).onGloballyPositioned { bodyOrigin = it.positionInRoot() }) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                // What the header names: the days, the month, or the day at the top of 日程.
                val (first, last) = when {
                    shown == AppSettings.ViewMonth -> monthOf(monthPager.currentPage).let { it.atDay(1) to it.atEndOfMonth() }
                    shown == AppSettings.ViewSchedule -> {
                        val row = scheduleRows.getOrNull(scheduleList.firstVisibleItemIndex)
                        val d = when (row) {
                            is ScheduleRow.Day -> row.date
                            is ScheduleRow.Month -> row.month.atDay(1)
                            null -> LocalDate.now()
                        }
                        d to d
                    }
                    else -> tl.first to tl.last
                }
                Header(
                    first = first,
                    last = last,
                    showRange = shown > 1,
                    onMenu = {
                        haptics.tick()
                        if (wide) AppSettings.set(AppSettings.sidebarOpen, !sideOpen) else scope.launch { drawer.open() }
                    },
                    onToday = {
                        haptics.tick()
                        scope.launch {
                            val today = LocalDate.now()
                            when (shown) {
                                AppSettings.ViewMonth -> monthPager.animateScrollToPage(MonthMid)
                                AppSettings.ViewSchedule -> scheduleList.animateScrollToItem(scheduleIndexOf(scheduleRows, today))
                                else -> {
                                    tl.show(today)
                                    tl.scrollToMinute(Times.minuteOfDay(System.currentTimeMillis()) - 90)
                                }
                            }
                        }
                    },
                    onSearch = { nav.push("search") },
                )
                Box(Modifier.animateContentSize(YoinMotion.defaultSpatialSpec())) {
                    TopCard(items, onOpenAll = { showUpcoming = true }, modifier = Modifier.padding(bottom = 8.dp))
                }
                if (!hasCalendar) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(20.dp)).background(cs.tertiaryContainer).padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("看不到系统日历", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = cs.onTertiaryContainer)
                        FilledTonalButton(onClick = {
                            calendarPermission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                        }) { Text("允许") }
                    }
                }
                val onItem: (AgendaItem) -> Unit = { haptics.tick(); selected = it }
                val onToggleDone: (AgendaItem) -> Unit = { item ->
                    haptics.tick()
                    scope.launch {
                        val ok = Actions.setDone(item, !item.done)
                        if (!ok) toast("没改成") else if (!item.done) haptics.chargeFull()
                    }
                }
                val viewModifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        alpha = viewIn.value
                        val s = 0.94f + 0.06f * viewIn.value
                        scaleX = s
                        scaleY = s
                    }
                when (shown) {
                    AppSettings.ViewMonth -> MonthView(monthPager, items, onDay = { haptics.tick(); openOnTimeline(it) }, modifier = viewModifier)
                    AppSettings.ViewSchedule -> ScheduleView(scheduleList, scheduleRows, onItem, onToggleDone, modifier = viewModifier)
                    else -> Timeline(
                        state = tl,
                        items = items,
                        onItem = onItem,
                        onToggleDone = onToggleDone,
                        // Tap on empty time: a new item from there, the default length, marked on the timeline.
                        mover = mover,
                        movingKey = moving?.key,
                        onSlot = { start ->
                            if (carried != null || draft != null) return@Timeline
                            if (trayOpen) { trayOpen = false; customEditing = false; return@Timeline }
                            haptics.tick()
                            val minutes = AppSettings.defaultMinutes.value
                            tl.ghost = Ghost(start, minutes)
                            draft = Draft(start, minutes, typed = true)
                        },
                        modifier = viewModifier,
                    )
                }
            }

            // Tapping outside the tray closes it (and leaves the custom bar's edit state).
            if (trayOpen && carried == null) {
                Box(
                    Modifier.fillMaxSize().clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (customEditing) customEditing = false else trayOpen = false
                    },
                )
            }

            val trayAlpha by animateFloatAsState(if (carried != null) 0f else 1f, YoinMotion.fastEffectsSpec(), label = "tray")
            AnimatedVisibility(
                visible = trayOpen,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 92.dp, start = 16.dp),
                enter = fadeIn(YoinMotion.defaultEffectsSpec()) + scaleIn(YoinMotion.defaultSpatialSpec(), initialScale = 0.85f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)),
                exit = fadeOut(YoinMotion.fastEffectsSpec()) + scaleOut(YoinMotion.fastSpatialSpec(), targetScale = 0.85f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)),
            ) {
                CreateTray(
                    custom = customMinutes,
                    onCustom = { customMinutes = it },
                    editing = customEditing,
                    onEditing = { customEditing = it },
                    carry = carry,
                    modifier = Modifier.widthIn(max = 380.dp).graphicsLayer { alpha = trayAlpha },
                )
            }

            val unscheduledAlpha by animateFloatAsState(if (moving != null) 0.25f else 1f, YoinMotion.fastEffectsSpec(), label = "unscheduled")
            AnimatedVisibility(
                visible = trayDb != null,
                modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 16.dp, end = 96.dp, bottom = 16.dp),
                enter = fadeIn(YoinMotion.defaultEffectsSpec()) + scaleIn(YoinMotion.defaultSpatialSpec(), initialScale = 0.85f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f)),
                exit = fadeOut(YoinMotion.fastEffectsSpec()) + scaleOut(YoinMotion.fastSpatialSpec(), targetScale = 0.85f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 1f)),
            ) {
                // Kept while it animates out.
                val id = remember { mutableStateOf("") }.apply { trayDb?.let { value = it } }.value
                UnscheduledTray(
                    databaseId = id,
                    removed = scheduled,
                    mover = mover,
                    onClose = { trayDb = null },
                    modifier = Modifier.widthIn(max = 380.dp).graphicsLayer { alpha = unscheduledAlpha },
                )
            }

            // The + : tap for the tray, hold to type.
            val fabRotation by animateFloatAsState(if (trayOpen) 45f else 0f, YoinMotion.defaultSpatialSpec(), label = "fab")
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 16.dp)
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(cs.primaryContainer)
                    .combinedClickable(
                        onClick = { haptics.tick(); trayOpen = !trayOpen; customEditing = false },
                        onLongClick = {
                            haptics.lowTick()
                            trayOpen = false
                            draft = Draft(nextQuarter(), 30, typed = true)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(YoinSymbols.Add, "新建", Modifier.size(28.dp).graphicsLayer { rotationZ = fabRotation }, tint = cs.onPrimaryContainer)
            }

            // The bar under the finger: it pops up out of the tray when lifted and tilts a
            // little, then hands over to the ghost once it is over the timeline.
            val c = carried
            val lift = remember { Animatable(0f) }
            LaunchedEffect(c != null) {
                if (c != null) {
                    lift.snapTo(0f)
                    lift.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
                } else lift.snapTo(0f)
            }
            if (c != null && tl.ghost == null) {
                val (value, unit) = Times.duration(c.first)
                Box(
                    Modifier
                        .offset { IntOffset((c.second.x - bodyOrigin.x - 60.dp.toPx()).roundToInt(), (c.second.y - bodyOrigin.y - 28.dp.toPx()).roundToInt()) }
                        .size(120.dp, 44.dp)
                        .graphicsLayer {
                            val v = lift.value
                            scaleX = 0.7f + 0.3f * v
                            scaleY = 0.7f + 0.3f * v
                            rotationZ = -4f * v
                            shadowElevation = 12.dp.toPx() * v
                            shape = RoundedCornerShape(14.dp)
                            clip = true
                        }
                        // A moved item keeps its own colour, so it never reads as a new one.
                        .background(moving?.let { Color(it.color) } ?: cs.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        moving?.title ?: "$value$unit",
                        Modifier.padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = moving?.let { if (Color(it.color).luminance() > 0.5f) Color(0xFF1B1B1F) else Color.White } ?: cs.onPrimary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    // Wide screens keep the drawer beside the days; ☰ slides it away and back.
    if (wide) Row(Modifier.fillMaxSize().background(cs.surface)) {
        AnimatedVisibility(
            sideOpen,
            enter = expandHorizontally(YoinMotion.defaultSpatialSpec(), expandFrom = Alignment.Start) + fadeIn(YoinMotion.defaultEffectsSpec()),
            exit = shrinkHorizontally(YoinMotion.defaultSpatialSpec(), shrinkTowards = Alignment.Start) + fadeOut(YoinMotion.fastEffectsSpec()),
        ) { drawerContent() }
        Box(Modifier.weight(1f).fillMaxHeight()) { body() }
    }
    else ModalNavigationDrawer(drawerContent, drawerState = drawer, gesturesEnabled = drawer.isOpen) { body() }

    selected?.let { ItemSheet(it, onDismiss = { selected = null }) }
    if (showUpcoming) {
        AllUpcomingSheet(items, onItem = { showUpcoming = false; selected = it }, onDismiss = { showUpcoming = false })
    }
    draft?.let { d ->
        CreateSheet(
            draft = d,
            onSaved = { created, _ ->
                draft = null
                tl.ghost = null
                rootScope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val r = snackbar.showSnackbar("已加入  ${created.title}", actionLabel = "编辑", duration = SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) {
                        draft = Draft(
                            start = created.start,
                            minutes = ((created.end - created.start) / 60_000L).toInt(),
                            text = created.title,
                            editing = created,
                        )
                    }
                }
            },
            onDismiss = { draft = null; tl.ghost = null },
        )
    }
}

@Composable
private fun Header(first: LocalDate, last: LocalDate, showRange: Boolean, onMenu: (() -> Unit)?, onToday: () -> Unit, onSearch: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onMenu != null) IconButton(onClick = onMenu) { Icon(CronSymbols.Menu, "日历和数据库") }
        else Spacer(Modifier.width(12.dp))
        Text(
            if (first.year != today.year) "${first.year % 100}年${first.monthValue}月" else "${first.monthValue}月",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(10.dp))
        // First and last day on screen, stacked.
        if (showRange && first != last) {
            Column(
                Modifier.clip(RoundedCornerShape(10.dp)).background(cs.surfaceContainerHigh).padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(first.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    (if (last.monthValue != first.monthValue) "${last.monthValue}/" else "") + last.dayOfMonth,
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearch) { Icon(YoinSymbols.Search, "搜索") }
        // Today's date; tap → back to today.
        val onToday0 = today in first..last
        Box(
            Modifier
                .padding(end = 8.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (onToday0) cs.surfaceContainerHigh else cs.primary)
                .clickable(onClick = onToday),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                today.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (onToday0) cs.onSurface else cs.onPrimary,
            )
        }
    }
}
