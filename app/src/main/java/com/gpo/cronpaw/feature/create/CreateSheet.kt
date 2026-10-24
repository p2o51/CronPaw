package com.gpo.cronpaw.feature.create

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.gpo.cronpaw.core.agenda.CalendarRepo
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.gpo.cronpaw.core.agenda.Actions
import com.gpo.cronpaw.core.agenda.Target
import com.gpo.cronpaw.core.settings.AppSettings
import com.gpo.cronpaw.ui.components.rememberToast
import com.gpo.cronpaw.ui.theme.CronSymbols
import com.gpo.cronpaw.ui.theme.YoinMotion
import com.gpo.cronpaw.ui.theme.rememberRichHaptics
import com.gpo.cronpaw.ui.time.Times
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A new item on its way: where the drop (or now) put it, and how it was asked for. */
data class Draft(
    val start: Long,
    val minutes: Int,
    val text: String = "",
    /** Typed (long-press +) instead of spoken. */
    val typed: Boolean = false,
    /** Set when 「编辑」 reopens something already saved. */
    val editing: Actions.Created? = null,
)

private enum class Stage { Voice, Confirm }

/**
 * Voice → confirm. The voice stage listens until the stop button; the confirm stage
 * saves itself after a short countdown unless the screen is touched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSheet(
    draft: Draft,
    onSaved: (Actions.Created, auto: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberRichHaptics()
    val toast = rememberToast()
    val cs = MaterialTheme.colorScheme
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val targets = remember { Actions.targets() }
    var target by remember {
        mutableStateOf(draft.editing?.target ?: targets.firstOrNull())
    }
    var stage by remember { mutableStateOf(if (draft.typed || draft.editing != null) Stage.Confirm else Stage.Voice) }
    var text by remember { mutableStateOf(draft.text) }
    var start by remember { mutableLongStateOf(draft.start) }
    var minutes by remember { mutableIntStateOf(draft.minutes) }
    var touched by remember { mutableStateOf(draft.typed || draft.editing != null) }
    var saving by remember { mutableStateOf(false) }
    var allDay by remember { mutableStateOf(draft.editing?.allDay ?: false) }
    // Beyond title and time; reset when the target changes, since they belong to it.
    var notionExtras by remember(target?.id) { mutableStateOf<NotionExtras>(emptyMap()) }
    var eventExtras by remember(target?.id) { mutableStateOf(CalendarRepo.EventExtras()) }

    val voice = remember { Voice.create(context, scope) }
    val heard by voice.text.collectAsState()
    val level by voice.level.collectAsState()
    val voiceError by voice.error.collectAsState()
    var listening by remember { mutableStateOf(false) }
    var transcribing by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { voice.cancel() } }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { voice.start(); listening = true } else { stage = Stage.Confirm; touched = true }
    }
    LaunchedEffect(Unit) {
        if (stage == Stage.Voice) {
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                voice.start(); listening = true
            } else {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // What the words say wins over where the bar was dropped.
    val parsed = remember(text) { Parse.parse(text) }
    val effStart = run {
        val day = parsed.date ?: Times.date(start)
        val minute = parsed.minute ?: Times.minuteOfDay(start)
        Times.at(day, minute)
    }
    val effMinutes = parsed.minutes ?: minutes
    val title = parsed.title.ifBlank { text.trim() }

    // After a save: the button turns into a tick, then the sheet slides away before it closes.
    var saved by remember { mutableStateOf(false) }
    suspend fun settle() {
        saved = true
        delay(260)
        runCatching { sheet.hide() }
    }

    fun save(auto: Boolean) {
        val t = target ?: run { toast("没有可写的日历"); return }
        if (title.isBlank() || saving || saved) return
        saving = true
        scope.launch {
            val begin = if (allDay) Times.startOf(Times.date(effStart)) else effStart
            val end = if (allDay) Times.startOf(Times.date(effStart).plusDays(1)) else effStart + effMinutes * 60_000L
            val editing = draft.editing
            if (editing != null) {
                if (Actions.edit(context, editing, title, begin, end)) {
                    haptics.chargeFull()
                    settle()
                    onDismiss()
                } else toast("没改成")
            } else {
                val created = Actions.create(context, t, title, begin, end, allDay, extras = notionExtras, eventExtras = eventExtras)
                if (created != null) {
                    haptics.chargeFull()
                    settle()
                    onSaved(created, auto)
                } else toast("没加上")
            }
            saving = false
        }
    }

    fun finishVoice(withText: String? = null) {
        scope.launch {
            listening = false
            if (withText != null) {
                voice.cancel()
                text = withText
            } else {
                transcribing = true
                val result = voice.stop()
                transcribing = false
                text = result
            }
            stage = Stage.Confirm
            // Nothing heard: the user has to type, so no countdown.
            touched = text.isBlank()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = cs.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                // With the keyboard up the sheet scrolls instead of squashing the 加入 button.
                .verticalScroll(rememberScrollState())
                .pointerInput(Unit) {
                    // Any touch stops the countdown.
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            if (stage == Stage.Confirm) touched = true
                        }
                    }
                }
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            // Where it goes: Notion databases and writable calendars.
            // While speaking, where it goes sits on top; on the confirm page it moves down to its own group.
            if (draft.editing == null && stage == Stage.Voice) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    targets.forEach { t ->
                        TargetChip(t, selected = t.id == target?.id) { haptics.tick(); target = t }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            AnimatedContent(stage, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "stage") { s ->
                when (s) {
                    Stage.Voice -> VoiceStage(
                        heard = heard,
                        level = level,
                        error = voiceError,
                        listening = listening,
                        transcribing = transcribing,
                        start = start,
                        minutes = minutes,
                        onQuick = { qt, qm -> haptics.tick(); minutes = qm; finishVoice(qt) },
                        onStop = { haptics.tick(); finishVoice() },
                        onType = { voice.cancel(); listening = false; stage = Stage.Confirm; touched = true },
                    )
                    Stage.Confirm -> ConfirmStage(
                        text = text,
                        onText = { text = it },
                        title = title,
                        parsed = parsed,
                        start = effStart,
                        minutes = effMinutes,
                        focus = draft.typed || text.isBlank(),
                        onStart = { start = it; if (parsed.date != null || parsed.minute != null) text = parsed.title },
                        onMinutes = { minutes = it; if (parsed.minutes != null) text = parsed.title },
                        countdown = !touched && AppSettings.autoSaveSeconds.value > 0 && title.isNotBlank(),
                        saving = saving,
                        saved = saved,
                        editing = draft.editing != null,
                        allDay = allDay,
                        onAllDay = { allDay = it },
                        onSave = { save(auto = it) },
                        more = {
                            if (draft.editing == null) {
                                Text("放到哪", Modifier.padding(start = 4.dp, bottom = 6.dp), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    targets.forEach { t ->
                                        TargetChip(t, selected = t.id == target?.id) { haptics.tick(); target = t }
                                    }
                                }
                            }
                            if (draft.editing == null) MoreFields(
                                target = target,
                                start = effStart,
                                notion = notionExtras,
                                onNotion = { notionExtras = it },
                                event = eventExtras,
                                onEvent = { eventExtras = it },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetChip(t: Target, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (selected) cs.secondaryContainer else cs.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(t.color)))
        Spacer(Modifier.width(6.dp))
        if (t is Target.Notion) {
            Icon(CronSymbols.Database, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant)
            Spacer(Modifier.width(3.dp))
        }
        Text(
            t.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) cs.onSecondaryContainer else cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun VoiceStage(
    heard: String,
    level: Float,
    error: String?,
    listening: Boolean,
    transcribing: Boolean,
    start: Long,
    minutes: Int,
    onQuick: (String, Int) -> Unit,
    onStop: () -> Unit,
    onType: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val quick = remember {
        AppSettings.quickEntries.value.lines().mapNotNull { line ->
            val parts = line.split('|')
            val t = parts.getOrNull(0)?.trim().orEmpty()
            val m = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return@mapNotNull null
            if (t.isEmpty()) null else t to m
        }
    }
    val pulse by animateFloatAsState(1f + level * 0.35f, label = "pulse")
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (quick.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                quick.forEach { (t, m) ->
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(cs.tertiaryContainer)
                            .clickable { onQuick(t, m) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(t, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = cs.onTertiaryContainer)
                        Spacer(Modifier.width(6.dp))
                        Text(Times.durationText(m), style = MaterialTheme.typography.labelSmall, color = cs.onTertiaryContainer.copy(alpha = 0.7f))
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "${Times.dayLabel(Times.date(start))}  ${Times.hm(start)}  ${Times.durationText(minutes)}",
            style = MaterialTheme.typography.labelLarge,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                error != null -> error
                transcribing -> "识别中"
                heard.isNotBlank() -> heard
                listening -> "说吧"
                else -> ""
            },
            Modifier.fillMaxWidth().heightIn(min = 64.dp),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = if (heard.isBlank()) cs.onSurfaceVariant else cs.onSurface,
        )
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onType, Modifier.size(56.dp)) {
                Icon(CronSymbols.Keyboard, "打字", tint = cs.onSurfaceVariant)
            }
            Spacer(Modifier.width(24.dp))
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(88.dp).scale(pulse).clip(CircleShape).background(cs.primary.copy(alpha = 0.18f)))
                Box(
                    Modifier.size(76.dp).clip(CircleShape).background(cs.primary).clickable(enabled = !transcribing, onClick = onStop),
                    contentAlignment = Alignment.Center,
                ) {
                    if (transcribing) CircularProgressIndicator(Modifier.size(28.dp), color = cs.onPrimary, strokeWidth = 3.dp)
                    else Icon(CronSymbols.Stop, "结束", Modifier.size(32.dp), tint = cs.onPrimary)
                }
            }
            Spacer(Modifier.width(24.dp))
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Icon(CronSymbols.Mic, null, tint = if (listening) cs.primary else cs.outline)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ConfirmStage(
    text: String,
    onText: (String) -> Unit,
    title: String,
    parsed: Parsed,
    start: Long,
    minutes: Int,
    focus: Boolean,
    onStart: (Long) -> Unit,
    onMinutes: (Int) -> Unit,
    countdown: Boolean,
    saving: Boolean,
    saved: Boolean,
    editing: Boolean,
    allDay: Boolean,
    onAllDay: (Boolean) -> Unit,
    onSave: (auto: Boolean) -> Unit,
    more: @Composable () -> Unit = {},
) {
    // Which time the dialog is setting: the start, or the end (which sets the length).
    var picking by remember { mutableStateOf<String?>(null) }
    picking?.let { which ->
        val end = start + minutes * 60_000L
        TimeDialog(
            title = if (which == "start") "开始" else "结束",
            minuteOfDay = Times.minuteOfDay(if (which == "start") start else end),
            onPick = { m ->
                picking = null
                if (which == "start") onStart(Times.at(Times.date(start), m))
                else {
                    var d = m - Times.minuteOfDay(start)
                    if (d <= 0) d += 24 * 60
                    onMinutes(d)
                }
            },
            onDismiss = { picking = null },
        )
    }
    val cs = MaterialTheme.colorScheme
    val haptics = rememberRichHaptics()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focus) { if (focus) runCatching { focusRequester.requestFocus() } }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(countdown) {
        if (countdown) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(AppSettings.autoSaveSeconds.value * 1000, easing = LinearEasing))
            onSave(true)
        } else {
            progress.snapTo(0f)
        }
    }
    Column(Modifier.fillMaxWidth()) {
        // Title, with 加入 right beside it so the keyboard never hides it.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextField(
                value = text,
                onValueChange = onText,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                placeholder = { Text("明天下午三点 和小王吃饭", maxLines = 1) },
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = cs.surfaceContainer,
                    unfocusedContainerColor = cs.surfaceContainer,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(false) }),
            )
            Box(
                Modifier
                    .size(width = 72.dp, height = 56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (title.isNotBlank()) cs.primary else cs.surfaceContainerHighest)
                    .clickable(enabled = !saving && !saved && title.isNotBlank()) { onSave(false) },
                contentAlignment = Alignment.Center,
            ) {
                val ink = if (title.isNotBlank()) cs.onPrimary else cs.onSurfaceVariant
                // The countdown fills the button from the left.
                Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(progress.value).background(ink.copy(alpha = 0.2f)))
                when {
                    saved -> Icon(YoinSymbols.Check, null, Modifier.size(26.dp), tint = ink)
                    saving -> CircularProgressIndicator(Modifier.size(22.dp), color = ink, strokeWidth = 3.dp)
                    else -> Text(if (editing) "保存" else "加入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
                }
            }
        }
        // What the words were read as, piece by piece, as they are typed.
        val heardWhen = parsed.date != null || parsed.minute != null || parsed.minutes != null
        val pieces = listOfNotNull(
            parsed.date?.let { "日期" to Times.dayLabel(it) },
            parsed.minute?.let { "开始" to Times.hm(start) },
            parsed.minutes?.let { m -> Times.duration(m).let { (v, u) -> "时长" to "$v$u" } },
            title.takeIf { heardWhen && it.isNotBlank() }?.let { "标题" to it },
        )
        val count = pieces.size
        LaunchedEffect(count) { if (count > 0) haptics.lowTick() }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).animateContentSize(YoinMotion.defaultSpatialSpec()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf("日期", "开始", "时长", "标题").forEach { label ->
                val piece = pieces.firstOrNull { it.first == label }
                AnimatedVisibility(
                    visible = piece != null,
                    enter = fadeIn(YoinMotion.defaultEffectsSpec()) + scaleIn(YoinMotion.defaultSpatialSpec(), initialScale = 0.7f),
                    exit = fadeOut(YoinMotion.fastEffectsSpec()) + scaleOut(YoinMotion.fastSpatialSpec(), targetScale = 0.7f),
                ) {
                    // Keeps the last value while it leaves.
                    val shown = remember { mutableStateOf("") }.apply { piece?.let { value = it.second } }.value
                    Row(
                        Modifier.padding(top = 8.dp).clip(RoundedCornerShape(12.dp)).background(if (label == "标题") cs.surfaceContainerHighest else cs.primaryContainer)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        val ink = if (label == "标题") cs.onSurface else cs.onPrimaryContainer
                        Text(label, style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.7f))
                        Spacer(Modifier.width(4.dp))
                        Text(shown, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = ink, maxLines = 1)
                    }
                }
            }
        }

        // ── When: the day with 全天 next to it, then start | end, then the length ──
        Spacer(Modifier.height(24.dp))
        val day = Times.date(start)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.clip(CircleShape).background(cs.surfaceContainer),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { haptics.tick(); onStart(start - 86_400_000L) }) { Icon(YoinSymbols.ChevronLeft, "前一天") }
                AnimatedContent(Times.dayLabel(day), transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "day") { label ->
                    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = { haptics.tick(); onStart(start + 86_400_000L) }) { Icon(YoinSymbols.ChevronRight, "后一天") }
            }
            Text(
                "全天",
                Modifier
                    .clip(CircleShape)
                    .background(if (allDay) cs.primary else cs.surfaceContainer)
                    .clickable { haptics.tick(); onAllDay(!allDay) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (allDay) cs.onPrimary else cs.onSurfaceVariant,
            )
        }
        AnimatedVisibility(
            visible = !allDay,
            enter = fadeIn(YoinMotion.defaultEffectsSpec()) + expandVertically(YoinMotion.defaultSpatialSpec()),
            exit = fadeOut(YoinMotion.fastEffectsSpec()) + shrinkVertically(YoinMotion.fastSpatialSpec()),
        ) {
          Column {
            // One colour family: the start in the full block, the end a shade lighter. Tap either to pick a time.
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val end = start + minutes * 60_000L
                TimeBlock("开始", Times.hm(start), null, cs.primaryContainer, cs.onPrimaryContainer, Modifier.weight(1f)) {
                    haptics.tick(); picking = "start"
                }
                TimeBlock(
                    "结束", Times.hm(end), "共 ${Times.durationText(minutes)}",
                    cs.primaryContainer.copy(alpha = 0.45f), cs.onSurface, Modifier.weight(1f),
                ) { haptics.tick(); picking = "end" }
            }
            // The usual lengths, equal widths so all six fit on a phone.
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(cs.surfaceContainer).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(15 to "15分", 30 to "30分", 45 to "45分", 60 to "1时", 90 to "1.5时", 120 to "2时").forEach { (m, label) ->
                    val on = m == minutes
                    val bg by animateColorAsState(if (on) cs.primary else Color.Transparent, YoinMotion.defaultEffectsSpec(), label = "len")
                    Box(
                        Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp)).background(bg).clickable { haptics.tick(); onMinutes(m) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, color = if (on) cs.onPrimary else cs.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
            val defaultMinutes = AppSettings.defaultMinutes.state.collectAsState().value
            if (minutes != defaultMinutes) {
                Text(
                    "把 ${Times.durationText(minutes)} 设为默认",
                    Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp)
                        .clip(CircleShape)
                        .clickable { haptics.tick(); AppSettings.set(AppSettings.defaultMinutes, minutes) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = cs.primary,
                )
            }
          }
        }

        // ── Where it goes, and what that place asks for ──
        Spacer(Modifier.height(24.dp))
        more()
    }
}

/** One end of the time: a label, the time large, an optional quiet note. The whole block opens the picker. */
@Composable
private fun TimeBlock(label: String, value: String, note: String?, bg: Color, ink: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(22.dp)).background(bg).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.7f))
            if (note != null) {
                Spacer(Modifier.width(6.dp))
                Text(note, style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.7f), maxLines = 1)
            }
        }
        AnimatedContent(value, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "value") { v ->
            Text(v, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = ink)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(title: String, minuteOfDay: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = minuteOfDay / 60, initialMinute = minuteOfDay % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onPick(state.hour * 60 + state.minute) }) { Text("好") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
