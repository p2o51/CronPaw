package com.gpo.cronpaw.feature.create

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
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

    fun save(auto: Boolean) {
        val t = target ?: run { toast("没有可写的日历"); return }
        if (title.isBlank() || saving) return
        saving = true
        scope.launch {
            val begin = if (allDay) Times.startOf(Times.date(effStart)) else effStart
            val end = if (allDay) Times.startOf(Times.date(effStart).plusDays(1)) else effStart + effMinutes * 60_000L
            val editing = draft.editing
            if (editing != null) {
                if (Actions.edit(context, editing, title, begin, end)) {
                    haptics.chargeFull()
                    onDismiss()
                } else toast("没改成")
            } else {
                val created = Actions.create(context, t, title, begin, end, allDay)
                if (created != null) {
                    haptics.chargeFull()
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
            if (draft.editing == null) {
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
                        editing = draft.editing != null,
                        allDay = allDay,
                        onAllDay = { allDay = it },
                        onSave = { save(auto = it) },
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
    editing: Boolean,
    allDay: Boolean,
    onAllDay: (Boolean) -> Unit,
    onSave: (auto: Boolean) -> Unit,
) {
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
        TextField(
            value = text,
            onValueChange = onText,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            placeholder = { Text("明天下午三点 和小王吃饭 一小时") },
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
        Spacer(Modifier.height(12.dp))
        val day = Times.date(start)
        Stepper("日期", Times.dayLabel(day), onMinus = { haptics.tick(); onStart(start - 86_400_000L) }, onPlus = { haptics.tick(); onStart(start + 86_400_000L) })
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(cs.surfaceContainer)
                .padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("全天", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Switch(allDay, { haptics.tick(); onAllDay(it) })
        }
        if (!allDay) {
            Stepper("开始", Times.hm(start), onMinus = { haptics.tick(); onStart(start - 15 * 60_000L) }, onPlus = { haptics.tick(); onStart(start + 15 * 60_000L) })
            Stepper("时长", Times.durationText(minutes), onMinus = { haptics.tick(); onMinutes((minutes - 15).coerceAtLeast(5)) }, onPlus = { haptics.tick(); onMinutes(minutes + 15) })
            // The usual lengths one tap away; 「默认」 keeps the choice for the next tap on the timeline.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val defaultMinutes = AppSettings.defaultMinutes.state.collectAsState().value
                listOf(15, 30, 45, 60, 90, 120).forEach { m ->
                    val on = m == minutes
                    val (v, u) = Times.duration(m)
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(if (on) cs.primary else cs.surfaceContainerHighest)
                            .clickable { haptics.tick(); onMinutes(m) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(v, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = if (on) cs.onPrimary else cs.onSurface)
                        Text(u, Modifier.padding(start = 1.dp), style = MaterialTheme.typography.labelSmall, color = if (on) cs.onPrimary.copy(alpha = 0.7f) else cs.onSurfaceVariant)
                    }
                }
                if (minutes != defaultMinutes) {
                    Text(
                        "设为默认",
                        Modifier
                            .clip(CircleShape)
                            .clickable { haptics.tick(); AppSettings.set(AppSettings.defaultMinutes, minutes) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.primary,
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(CircleShape)
                .background(cs.primary)
                .clickable(enabled = !saving && title.isNotBlank()) { onSave(false) },
            contentAlignment = Alignment.Center,
        ) {
            // The countdown fills the button from the left.
            Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(progress.value).background(cs.onPrimary.copy(alpha = 0.18f)))
            if (saving) CircularProgressIndicator(Modifier.size(24.dp), color = cs.onPrimary, strokeWidth = 3.dp)
            else Text(if (editing) "保存" else "加入", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = cs.onPrimary)
        }
    }
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surfaceContainer)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.width(48.dp), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        Text(value, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onMinus) { Icon(YoinSymbols.ChevronLeft, "减") }
        IconButton(onClick = onPlus) { Icon(YoinSymbols.ChevronRight, "加") }
    }
}

