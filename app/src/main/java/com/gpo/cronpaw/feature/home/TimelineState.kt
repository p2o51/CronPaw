package com.gpo.cronpaw.feature.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.gpo.cronpaw.ui.time.Times
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlin.math.roundToInt

const val PageMid = 5000
private const val PageCount = PageMid * 2

/** A slot being dragged onto the timeline: the snapped start and the length. */
data class Ghost(val start: Long, val minutes: Int)

/**
 * The timeline's position: which days are on screen (pages of [days] days from [base])
 * and how far down the day it is scrolled. Also the geometry a drag from the create tray
 * needs to find a time under the finger.
 */
@Stable
class TimelineState(
    days: Int,
    base: LocalDate,
    val pager: PagerState,
    val header: PagerState,
    val scroll: ScrollState,
) {
    var days by mutableIntStateOf(days)
        private set
    var base by mutableStateOf(base)
        private set

    fun firstDay(page: Int): LocalDate = base.plusDays(((page - PageMid) * days).toLong())
    val first: LocalDate get() = firstDay(pager.currentPage)
    val last: LocalDate get() = first.plusDays((days - 1).toLong())

    // ── Geometry, in root coordinates (set by the timeline as it lays out) ──
    /** Top-left of the current page's day columns at 0:00, and their width. Moves with the scroll. */
    var columnsOrigin by mutableStateOf(Offset.Zero)
    var columnsWidth by mutableIntStateOf(0)
    var hourPx by mutableStateOf(1f)
    /** The visible part of the timeline (below the header). */
    var viewport by mutableStateOf(Rect.Zero)

    var ghost by mutableStateOf<Ghost?>(null)

    /** The snapped slot under [root] for an item of [minutes], or null off the columns. */
    fun slotAt(root: Offset, minutes: Int): Long? {
        if (!viewport.contains(root) || columnsWidth == 0) return null
        val x = root.x - columnsOrigin.x
        val y = root.y - columnsOrigin.y
        if (x < 0 || x >= columnsWidth) return null
        val col = floor(x / (columnsWidth / days.toFloat())).toInt().coerceIn(0, days - 1)
        // The finger holds the middle of the bar's top edge: the item starts where it is.
        val rawMin = (y / hourPx * 60f)
        val snapped = ((rawMin / 15f).roundToInt() * 15).coerceIn(0, 24 * 60 - minutes.coerceAtMost(24 * 60))
        return Times.at(first.plusDays(col.toLong()), snapped)
    }

    /** Shows [date] with the current view: the same paging when it lines up, else a new base. */
    suspend fun show(date: LocalDate, animate: Boolean = true) {
        val target = if (days == 7) Times.monday(date) else date
        val diff = ChronoUnit.DAYS.between(base, target)
        if (diff % days == 0L && kotlin.math.abs(diff / days) < PageMid) {
            val page = PageMid + (diff / days).toInt()
            if (animate && kotlin.math.abs(page - pager.currentPage) <= 3) pager.animateScrollToPage(page) else pager.scrollToPage(page)
        } else {
            // Keep the page and move the base under it.
            base = target.minusDays(((pager.currentPage - PageMid) * days).toLong())
        }
    }

    /** Shows exactly [from] as the first day (a search range), whatever the alignment. */
    suspend fun showFrom(from: LocalDate) {
        base = from.minusDays(((pager.currentPage - PageMid) * days).toLong())
    }

    fun setDays(n: Int) {
        val keep = first
        days = n
        base = (if (n == 7) Times.monday(keep) else keep).minusDays(((pager.currentPage - PageMid) * n).toLong())
    }

    suspend fun scrollToMinute(minute: Int) {
        scroll.scrollTo(((minute / 60f) * hourPx).toInt().coerceAtLeast(0))
    }
}

@Composable
fun rememberTimelineState(days: Int): TimelineState {
    val pager = rememberPagerState(initialPage = PageMid) { PageCount }
    val header = rememberPagerState(initialPage = PageMid) { PageCount }
    val scroll = rememberScrollState()
    return remember {
        val today = LocalDate.now()
        TimelineState(days, if (days == 7) Times.monday(today) else today, pager, header, scroll)
    }
}
