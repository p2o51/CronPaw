package com.gpo.cronpaw.feature.home

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
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

    val viewDays by AppSettings.viewDays.state.collectAsState()
    val tl = rememberTimelineState(viewDays)
    LaunchedEffect(viewDays) { if (tl.days != viewDays) tl.switchDays(viewDays) }
    val items by Agenda.items.collectAsState()

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
        snapshotFlow { tl.first to tl.last }.collect { (a, b) -> Agenda.ensure(a, b) }
    }

    // ── Sheets ──
    var selected by remember { mutableStateOf<AgendaItem?>(null) }
    var showUpcoming by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<Draft?>(null) }

    LaunchedEffect(Unit) {
        HomeRequests.jump.collect { j ->
            if (j == null) return@collect
            HomeRequests.jump.value = null
            if (j.exactFirst) tl.showFrom(j.date) else tl.show(j.date)
            j.open?.let { item ->
                if (!item.allDay) tl.scrollToMinute(Times.minuteOfDay(item.start) - 60)
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

    fun updateGhost(minutes: Int, root: Offset) {
        val slot = tl.slotAt(root, minutes)
        if (slot != tl.ghost?.start) {
            if (slot != null) haptics.tick()
            tl.ghost = slot?.let { Ghost(it, minutes) }
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
                updateGhost(c.first, c.second)
            }
            delay(16)
        }
    }

    val drawer = rememberDrawerState(DrawerValue.Closed)
    BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = drawer.isOpen,
        drawerContent = {
            CalendarDrawer(
                viewDays = viewDays,
                onViewDays = { AppSettings.set(AppSettings.viewDays, it) },
                onAddDatabase = { scope.launch { drawer.close() }; nav.push("db:") },
                onSettings = { scope.launch { drawer.close() }; nav.push("settings") },
            )
        },
    ) {
        Box(Modifier.fillMaxSize().background(cs.surface)) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Header(
                    first = tl.first,
                    last = tl.last,
                    onMenu = { scope.launch { drawer.open() } },
                    onToday = {
                        haptics.tick()
                        scope.launch {
                            tl.show(LocalDate.now())
                            tl.scrollToMinute(Times.minuteOfDay(System.currentTimeMillis()) - 90)
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
                Timeline(
                    state = tl,
                    items = items,
                    onItem = { haptics.tick(); selected = it },
                    onToggleDone = { item ->
                        haptics.tick()
                        scope.launch {
                            val ok = Actions.setDone(item, !item.done)
                            if (!ok) toast("没改成") else if (!item.done) haptics.chargeFull()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
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

            // The bar under the finger, until it is over the timeline (then the ghost shows).
            val c = carried
            if (c != null && tl.ghost == null) {
                val (value, unit) = Times.duration(c.first)
                Box(
                    Modifier
                        .offset { IntOffset((c.second.x - 60.dp.toPx()).roundToInt(), (c.second.y - 28.dp.toPx()).roundToInt()) }
                        .size(120.dp, 44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(cs.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$value$unit", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = cs.onPrimary)
                }
            }
        }
    }

    selected?.let { ItemSheet(it, onDismiss = { selected = null }) }
    if (showUpcoming) {
        AllUpcomingSheet(items, onItem = { showUpcoming = false; selected = it }, onDismiss = { showUpcoming = false })
    }
    draft?.let { d ->
        CreateSheet(
            draft = d,
            onSaved = { created, _ ->
                draft = null
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
            onDismiss = { draft = null },
        )
    }
}

@Composable
private fun Header(first: LocalDate, last: LocalDate, onMenu: () -> Unit, onToday: () -> Unit, onSearch: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val today = LocalDate.now()
    Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMenu) { Icon(CronSymbols.Menu, "日历和数据库") }
        Text(
            if (first.year != today.year) "${first.year % 100}年${first.monthValue}月" else "${first.monthValue}月",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(10.dp))
        // First and last day on screen, stacked.
        if (first != last) {
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
