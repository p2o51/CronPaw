package com.gpo.cronpaw.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gpo.cronpaw.core.agenda.Agenda
import com.gpo.cronpaw.core.agenda.AgendaItem
import com.gpo.cronpaw.core.settings.AppSettings
import com.gpo.cronpaw.feature.home.HomeRequests
import com.gpo.cronpaw.navigation.Nav
import com.gpo.cronpaw.ui.theme.CronSymbols
import com.gpo.cronpaw.ui.time.Times
import com.gpo.yoin.symbols.YoinSymbols
import java.time.LocalDate

/** A range to show, in the current view's length. */
data class RangeCandidate(val from: LocalDate, val to: LocalDate, val note: String)

object DateQuery {
    private val Full = Regex("^\\s*(?:(\\d{2}|\\d{4})[./\\-年])?(\\d{1,2})[./\\-月](\\d{1,2})[日号]?\\s*$")

    /**
     * 「10.17」 → the next 10.17; 「23.10.17」 → 2023. Ranges match the view: 3 天 centres the
     * day, and also offers it as the first or last day; 周 is the Monday week. Nothing here
     * changes how many days are shown.
     */
    fun candidates(input: String, days: Int, today: LocalDate = LocalDate.now()): List<RangeCandidate> {
        val m = Full.find(input) ?: return relative(input, days, today)
        val month = m.groupValues[2].toInt()
        val day = m.groupValues[3].toInt()
        val yearRaw = m.groupValues[1]
        val dates = runCatching {
            if (yearRaw.isNotEmpty()) {
                val y = yearRaw.toInt().let { if (it < 100) 2000 + it else it }
                listOf(LocalDate.of(y, month, day))
            } else {
                val thisYear = LocalDate.of(today.year, month, day)
                val next = if (thisYear.isBefore(today)) thisYear.plusYears(1) else thisYear
                listOf(next, next.minusYears(1))
            }
        }.getOrDefault(emptyList())
        return dates.flatMapIndexed { i, d -> ranges(d, days, primaryOnly = i > 0) }
    }

    private fun relative(input: String, days: Int, today: LocalDate): List<RangeCandidate> {
        val p = com.gpo.cronpaw.feature.create.Parse.parse(input, today)
        val d = p.date ?: return emptyList()
        if (p.title.isNotBlank()) return emptyList()
        return ranges(d, days, primaryOnly = false)
    }

    private fun ranges(d: LocalDate, days: Int, primaryOnly: Boolean): List<RangeCandidate> {
        fun r(from: LocalDate, note: String) = RangeCandidate(from, from.plusDays((days - 1).toLong()), note)
        return when (days) {
            1 -> listOf(r(d, ""))
            7 -> listOf(r(Times.monday(d), "周"))
            2 -> if (primaryOnly) listOf(r(d, "")) else listOf(r(d, "从这天"), r(d.minusDays(1), "到这天"))
            else -> {
                val half = (days - 1) / 2
                val centre = r(d.minusDays(half.toLong()), "居中")
                if (primaryOnly) listOf(centre)
                else listOf(centre, r(d, "从这天"), r(d.minusDays((days - 1).toLong()), "到这天"))
            }
        }
    }
}

object Fuzzy {
    /** 0 = no match. Every space-separated part must match; whole substrings rank above scattered letters. */
    fun score(title: String, query: String): Int {
        val t = title.lowercase()
        val parts = query.lowercase().split(' ').filter { it.isNotBlank() }
        if (parts.isEmpty()) return 0
        var total = 0
        for (p in parts) {
            val s = when {
                t.startsWith(p) -> 100
                t.contains(p) -> 80
                subsequence(t, p) -> 30
                else -> return 0
            }
            total += s
        }
        return total
    }

    private fun subsequence(t: String, p: String): Boolean {
        var i = 0
        for (c in t) if (i < p.length && c == p[i]) i++
        return i == p.length
    }
}

@Composable
fun SearchScreen(nav: Nav) {
    val cs = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    val items by Agenda.items.collectAsState()
    val days by AppSettings.viewDays.state.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        val today = LocalDate.now()
        Agenda.ensure(today.minusDays(30), today.plusDays(90))
    }
    val ranges = remember(query, days) { DateQuery.candidates(query, days) }
    val now = remember { System.currentTimeMillis() }
    val results = remember(query, items) {
        if (query.isBlank()) emptyList()
        else items.asSequence()
            .map { it to Fuzzy.score(it.title, query) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<AgendaItem, Int>> { it.second }.thenBy { kotlin.math.abs(it.first.start - now) })
            .map { it.first }
            .take(60)
            .toList()
    }

    Column(Modifier.fillMaxSize().background(cs.surface).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.pop() }) { Icon(YoinSymbols.Back, "返回") }
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f).focusRequester(focus),
                placeholder = { Text("事项或日期，如 10.17") },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = cs.surfaceContainerHigh,
                    unfocusedContainerColor = cs.surfaceContainerHigh,
                ),
            )
            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(YoinSymbols.Close, "清空") }
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
            items(ranges, key = { "r:${it.from}:${it.note}" }) { r ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(cs.secondaryContainer)
                        .clickable {
                            HomeRequests.jump.value = HomeRequests.Jump(r.from, exactFirst = true)
                            nav.pop()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(CronSymbols.Calendar, null, Modifier.size(20.dp), tint = cs.onSecondaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        val y = if (r.from.year != LocalDate.now().year) "${r.from.year}." else ""
                        Text(
                            if (r.from == r.to) "$y${r.from.monthValue}.${r.from.dayOfMonth}"
                            else "$y${r.from.monthValue}.${r.from.dayOfMonth}  ${r.to.monthValue}.${r.to.dayOfMonth}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = cs.onSecondaryContainer,
                        )
                        Text(
                            "${Times.weekday(r.from)}  ${Times.weekday(r.to)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSecondaryContainer.copy(alpha = 0.7f),
                        )
                    }
                    if (r.note.isNotEmpty()) Text(r.note, style = MaterialTheme.typography.labelMedium, color = cs.onSecondaryContainer.copy(alpha = 0.7f))
                }
            }
            items(results, key = { it.key }) { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(cs.surfaceContainer)
                        .clickable {
                            HomeRequests.jump.value = HomeRequests.Jump(Times.date(item.start), open = item)
                            nav.pop()
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp, 30.dp).clip(RoundedCornerShape(4.dp)).background(Color(item.color)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${Times.dayLabel(Times.date(item.start))}  ${Times.hmRange(item)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
            }
            if (query.isNotBlank() && ranges.isEmpty() && results.isEmpty()) {
                item {
                    Text("没有找到", Modifier.fillMaxWidth().padding(32.dp), style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}
