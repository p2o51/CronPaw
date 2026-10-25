package com.gpo.cronpaw.feature.focus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.gpo.cronpaw.core.agenda.AgendaItem
import com.gpo.cronpaw.core.focus.FocusController
import com.gpo.cronpaw.core.focus.FocusMode
import com.gpo.cronpaw.core.focus.FocusSession
import com.gpo.cronpaw.core.focus.FocusSettings
import com.gpo.cronpaw.core.focus.FocusStore
import com.gpo.cronpaw.core.focus.FocusZen
import com.gpo.cronpaw.core.focus.breakColor
import com.gpo.cronpaw.ui.components.LocalToastScope
import com.gpo.cronpaw.ui.components.WideSlider
import com.gpo.cronpaw.ui.components.rememberToast
import com.gpo.cronpaw.ui.theme.YoinMotion
import com.gpo.cronpaw.ui.theme.rememberRichHaptics
import com.gpo.cronpaw.ui.time.Times
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 「专注」 on an item: 番茄 or 自由 (remembered), whether to turn on 专注 mode, 开始. The
 * breath and the clock follow on the focus page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartFocusSheet(item: AgendaItem, onStart: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val haptics = rememberRichHaptics()
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lastMode by FocusSettings.lastMode.state.collectAsState()
    var mode by remember { mutableStateOf(runCatching { FocusMode.valueOf(lastMode) }.getOrDefault(FocusMode.Pomodoro)) }
    val quietPref by FocusSettings.quiet.state.collectAsState()
    var quiet by remember { mutableStateOf(quietPref) }
    // Coming back from the access page: look again.
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val access = remember(lifecycle) { FocusZen.hasAccess(context) }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val now = remember { System.currentTimeMillis() }
    val focusMin = FocusSettings.focusMin.value
    val breakMin = FocusSettings.breakMin.value
    val minutes = FocusSession.minutesFor(item, now, focusMin)
    val (rounds, last) = FocusSession.plan(minutes, focusMin, breakMin)

    val density = LocalDensity.current
    var buttonRect by remember { mutableStateOf<Rect?>(null) }
    var sheetRect by remember { mutableStateOf<Rect?>(null) }
    val buttonColor = cs.primary
    val sheetColor = cs.surfaceContainerLow

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = sheetColor) {
        // The sheet has its own window: measure against its view.
        val view = LocalView.current
        Column(Modifier.fillMaxWidth().onGloballyPositioned {
            // The sheet's surface starts above this column, at the drag handle.
            val r = it.screenRect(view)
            sheetRect = r.copy(top = r.top - with(density) { 48.dp.toPx() })
        }.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            Text(item.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2)
            Spacer(Modifier.height(20.dp))
            Label("方式")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainer).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(FocusMode.Pomodoro to "番茄", FocusMode.Free to "自由").forEach { (m, label) ->
                    val on = m == mode
                    val bg by animateColorAsState(if (on) cs.primary else Color.Transparent, YoinMotion.defaultEffectsSpec(), label = "mode")
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(16.dp)).background(bg).clickable { haptics.tick(); mode = m },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (on) cs.onPrimary else cs.onSurface)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            AnimatedContent(mode, transitionSpec = { fadeIn(spring(stiffness = 400f)) togetherWith fadeOut(spring(stiffness = 700f)) }, label = "plan") { m ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainer).padding(horizontal = 16.dp, vertical = 14.dp)) {
                    if (m == FocusMode.Pomodoro) {
                        val total = rounds * focusMin + (rounds - 1) * breakMin + (last - focusMin)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$rounds", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("轮", Modifier.padding(start = 2.dp, end = 14.dp, bottom = 3.dp), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                            Text(Times.durationText(total), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            Text("到 ${Times.hm(now + total * 60_000L)}", Modifier.padding(bottom = 3.dp), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(10.dp))
                        // Rounds and breaks as one bar, in the same colours as the notification.
                        Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            for (r in 0 until rounds) {
                                val len = if (r == rounds - 1) last else focusMin
                                Box(Modifier.weight(len.toFloat()).height(8.dp).clip(CircleShape).background(Color(item.color)))
                                if (r < rounds - 1) {
                                    val b = if ((r + 1) % 4 == 0) FocusSettings.longBreakMin.value else breakMin
                                    Box(Modifier.weight(b.toFloat()).height(8.dp).clip(CircleShape).background(Color(breakColor(item.color)).copy(alpha = 0.7f)))
                                }
                            }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(Times.hm(now), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("开始", Modifier.padding(start = 6.dp, bottom = 3.dp), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                        }
                        Text("一直计到你按结束", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Label("手机")
            val on = quiet && access
            val chipBg by animateColorAsState(if (on) cs.primary else cs.surfaceContainerHighest, YoinMotion.defaultEffectsSpec(), label = "quiet")
            Text(
                "专注模式",
                Modifier.clip(CircleShape).background(chipBg).clickable {
                    haptics.tick()
                    if (!access) runCatching { context.startActivity(FocusZen.accessIntent()) }.also { quiet = true }
                    else quiet = !quiet
                }.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (on) cs.onPrimary else cs.onSurfaceVariant,
            )
            if (!access) Text("要先在系统里允许 CronPaw 管理勿扰", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    haptics.tick()
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notify.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    FocusController.plan(item, mode, quiet && access)
                    if (FocusSettings.breath.value && animationsOn(context)) {
                        // The button becomes the breath: the sheet stays until the breath has
                        // drawn its copy of it underneath, then goes in the same frame.
                        FocusHandoff.arm(buttonRect?.let { FocusHandoff.Origin(it, buttonColor, sheetRect, sheetColor) })
                        onStart()
                        scope.launch {
                            withTimeoutOrNull(600) { FocusHandoff.drawn.first { it } }
                            onDismiss()
                        }
                    } else {
                        onStart()
                        scope.launch {
                            runCatching { sheet.hide() }
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp).onGloballyPositioned { buttonRect = it.screenRect(view) },
                shape = CircleShape,
            ) { Text("开始", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        }
    }
}

/**
 * 上次专注: the session was cut off (restart, force-stop, forgotten, a break left too long).
 * The start is known; the end is picked from three guesses and nudged in 5-minute steps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecoverySheet(s: FocusSession, onOpenFocus: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val haptics = rememberRichHaptics()
    val toast = rememberToast()
    val local = rememberCoroutineScope()
    // The write outlives the sheet: it leaves as soon as the session is cleared.
    val root = LocalToastScope.current ?: local
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val now = remember { System.currentTimeMillis() }
    val planned = remember(s) {
        if (s.mode == FocusMode.Pomodoro) {
            var m = 0
            for (r in 0 until s.rounds) m += s.focusLenMin(r) + if (r < s.rounds - 1) s.breakLenMin(r) else 0
            // Only a guess of its own when the plan would have ended before now.
            (s.startedAt + m * 60_000L).takeIf { it < now - 60_000L }
        } else null
    }
    val choices = listOfNotNull(
        planned?.let { "计划结束" to it },
        "最后一次用" to s.lastSeen.coerceIn(s.startedAt, now),
        "现在" to now,
    )
    var pickedIndex by remember { mutableStateOf(0) }
    val picked = choices[pickedIndex.coerceIn(0, choices.lastIndex)].second
    var nudge by remember { mutableIntStateOf(0) }
    val end = (picked + (nudge * 5) * 60_000L).coerceIn(minOf(s.startedAt + 60_000L, now), now)
    val minutes = ((end - s.startedAt + 30_000L) / 60_000L).toInt()
    val finish: (suspend () -> Unit) -> Unit = { block -> root.launch { block() }; local.launch { runCatching { sheet.hide() }; onDismiss() } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = cs.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
            Label("上次专注")
            Text(s.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Times.hm(s.startedAt), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("开始", Modifier.padding(start = 4.dp, end = 20.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Text(Times.hm(end), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = cs.primary)
                Text("结束", Modifier.padding(start = 4.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(if (minutes < 1) "<1 分钟" else "$minutes 分钟", Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Label("结束在")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEachIndexed { i, (label, _) ->
                    val on = i == pickedIndex
                    val bg by animateColorAsState(if (on) cs.primary else cs.surfaceContainerHighest, YoinMotion.defaultEffectsSpec(), label = "end")
                    Text(
                        label,
                        Modifier.clip(CircleShape).background(bg).clickable { haptics.tick(); pickedIndex = i; nudge = 0 }.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (on) cs.onPrimary else cs.onSurfaceVariant,
                    )
                }
            }
            // Each step is 5 minutes either way; the middle dot is the chosen end itself.
            WideSlider(
                value = nudge + 12,
                count = 25,
                onValueChange = { if (it - 12 != nudge) haptics.tick(); nudge = it - 12 },
                description = "微调结束时间",
                state = Times.hm(end),
                modifier = Modifier.padding(top = 8.dp),
                marks = setOf(12),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    haptics.celebrate()
                    finish { if (!FocusController.finish(context, rewrite = true, markDone = true, endAt = end)) toast("没改成") }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = CircleShape,
            ) { Text("完成", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        haptics.tick()
                        onOpenFocus()
                        FocusController.keepGoing(context)
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text("接着计时", fontWeight = FontWeight.Bold) }
                TextButton(
                    onClick = { haptics.lowTick(); FocusController.discard(context, recovered = true); finish { } },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = cs.onSurfaceVariant),
                ) { Text("放弃") }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The session the recovery sheet is about, if one is waiting to be asked. */
@Composable
fun rememberRecovery(): FocusSession? {
    val reason by FocusController.recovery.collectAsState()
    val s by FocusStore.session.collectAsState()
    return if (reason != null) s else null
}
