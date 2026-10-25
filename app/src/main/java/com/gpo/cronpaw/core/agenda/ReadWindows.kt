package com.gpo.cronpaw.core.agenda

/** Merge overlapping windows, never fill a months/years-long gap just for a widget. */
internal fun mergeReadWindows(windows: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
    val merged = mutableListOf<Pair<Long, Long>>()
    for ((from, to) in windows.filter { it.first < it.second }.sortedBy { it.first }) {
        val last = merged.lastOrNull()
        if (last != null && from <= last.second) merged[merged.lastIndex] = last.first to maxOf(last.second, to)
        else merged += from to to
    }
    return merged
}
