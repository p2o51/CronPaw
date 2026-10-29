package com.gpo.cronpaw.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gpo.cronpaw.core.settings.QuickEntries
import com.gpo.cronpaw.ui.components.WideSlider
import com.gpo.cronpaw.ui.theme.YoinMotion
import com.gpo.cronpaw.ui.theme.rememberRichHaptics
import com.gpo.yoin.symbols.YoinSymbols

/** The slider's detents: the lengths people pick, few enough to land on by feel. Others go through …. */
private val Detents = listOf(5, 10, 15, 20, 25, 30, 45, 60, 90, 120)

/**
 * 快捷入口 as things you can see and move: each one a card with its name and a slider for its
 * length (ten detents, 5 minutes to 2 hours), … for anything else, × to drop it, and + at the end. Saved as you go.
 */
@Composable
internal fun QuickEntryEditor() {
    val haptics = rememberRichHaptics()
    val cs = MaterialTheme.colorScheme
    var entries by remember { mutableStateOf(QuickEntries.load()) }
    fun update(list: List<QuickEntries.Entry>) {
        entries = list
        QuickEntries.save(list)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        entries.forEachIndexed { i, entry ->
            QuickEntryCard(
                entry = entry,
                onChange = { update(entries.toMutableList().also { l -> l[i] = it }) },
                onRemove = { haptics.tick(); update(entries.filterIndexed { j, _ -> j != i }) },
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable { haptics.tick(); update(entries + QuickEntries.Entry("", 30)) }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(YoinSymbols.Add, null, Modifier.size(20.dp), tint = cs.primary)
            Spacer(Modifier.width(10.dp))
            Text("添加快捷入口", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = cs.primary)
        }
    }
}

@Composable
private fun QuickEntryCard(entry: QuickEntries.Entry, onChange: (QuickEntries.Entry) -> Unit, onRemove: () -> Unit) {
    val haptics = rememberRichHaptics()
    val cs = MaterialTheme.colorScheme
    // Off the slider's detents: typed in, so the … field opens with it.
    var custom by remember { mutableStateOf(entry.minutes !in Detents) }
    var typed by remember { mutableStateOf(entry.minutes.toString()) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cs.surfaceContainerHigh)
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).padding(vertical = 8.dp)) {
                if (entry.title.isEmpty()) Text("名字，比如 午饭", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                BasicTextField(
                    entry.title,
                    { onChange(entry.copy(title = it.replace("\n", "").replace("|", ""))) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(cs.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(length(entry.minutes), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = cs.primary)
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) { Icon(YoinSymbols.Close, "删掉", Modifier.size(18.dp), tint = cs.onSurfaceVariant) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            WideSlider(
                value = Detents.indexOfFirst { it >= entry.minutes }.let { if (it < 0) Detents.lastIndex else it },
                count = Detents.size,
                onValueChange = {
                    val m = Detents[it]
                    if (m != entry.minutes) {
                        haptics.tick()
                        custom = false
                        typed = m.toString()
                        onChange(entry.copy(minutes = m))
                    }
                },
                description = "${entry.title.ifEmpty { "快捷入口" }}的时长",
                state = length(entry.minutes),
                modifier = Modifier.weight(1f),
                marks = setOf(Detents.indexOf(30), Detents.indexOf(60)),
            )
            Spacer(Modifier.width(6.dp))
            // …: longer than two hours, or a length between the detents.
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (custom) cs.primary else cs.secondaryContainer)
                    .clickable { haptics.tick(); custom = !custom },
                contentAlignment = Alignment.Center,
            ) {
                Text("…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (custom) cs.onPrimary else cs.onSecondaryContainer)
            }
        }
        AnimatedVisibility(
            custom,
            enter = expandVertically(YoinMotion.defaultSpatialSpec()) + fadeIn(YoinMotion.defaultEffectsSpec()),
            exit = shrinkVertically(YoinMotion.fastSpatialSpec()) + fadeOut(YoinMotion.fastEffectsSpec()),
        ) {
            Row(Modifier.padding(top = 8.dp, end = 8.dp)) {
                BasicTextField(
                    typed,
                    { text ->
                        typed = text.filter(Char::isDigit).take(4)
                        typed.toIntOrNull()?.takeIf { it in 1..24 * 60 }?.let { onChange(entry.copy(minutes = it)) }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.titleMedium.copy(color = cs.onSurface, fontWeight = FontWeight.Bold),
                    cursorBrush = SolidColor(cs.primary),
                    modifier = Modifier
                        .alignByBaseline()
                        .width(96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(cs.surfaceContainerHighest)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text("分钟，最长一天", Modifier.alignByBaseline(), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
    }
}

/** 45 分钟, 1 小时, 1 小时 30 分. */
private fun length(minutes: Int): String = when {
    minutes < 60 -> "$minutes 分钟"
    minutes % 60 == 0 -> "${minutes / 60} 小时"
    else -> "${minutes / 60} 小时 ${minutes % 60} 分"
}
