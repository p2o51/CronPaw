package com.gpo.cronpaw.feature.search

import com.gpo.cronpaw.core.agenda.AgendaItem
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object Fuzzy {
    /** 0 = no match. Every space-separated part must match; whole substrings rank above scattered letters. */
    fun score(title: String, query: String): Int {
        return scoreParts(title.lowercase(), query.lowercase().split(' ').filter { it.isNotBlank() })
    }

    private fun scoreParts(t: String, parts: List<String>): Int {
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

    /** Score once per item and retain only the best 60, preserving tie order. */
    suspend fun search(items: List<AgendaItem>, query: String, now: Long, limit: Int = 60): List<AgendaItem> {
        if (query.isBlank() || limit <= 0) return emptyList()
        val parts = query.lowercase().split(' ').filter { it.isNotBlank() }
        data class Match(val item: AgendaItem, val score: Int, val order: Int)
        val bestFirst = compareByDescending<Match> { it.score }
            .thenBy { kotlin.math.abs(it.item.start - now) }.thenBy { it.order }
        val best = java.util.PriorityQueue(limit, bestFirst.reversed())
        for ((index, item) in items.withIndex()) {
            if (index % 64 == 0) currentCoroutineContext().ensureActive()
            val score = scoreParts(item.title.lowercase(), parts)
            if (score == 0) continue
            val match = Match(item, score, index)
            if (best.size < limit) best.add(match)
            else if (bestFirst.compare(match, best.peek()) < 0) {
                best.poll()
                best.add(match)
            }
        }
        return best.sortedWith(bestFirst).map { it.item }
    }

    private fun subsequence(t: String, p: String): Boolean {
        var i = 0
        for (c in t) if (i < p.length && c == p[i]) i++
        return i == p.length
    }
}

