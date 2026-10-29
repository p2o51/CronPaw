package com.gpo.cronpaw.core.settings

/** 快捷入口: one-tap items on the voice sheet, stored in [AppSettings.quickEntries] as "title|minutes" lines. */
object QuickEntries {
    data class Entry(val title: String, val minutes: Int)

    fun parse(raw: String): List<Entry> = raw.lines().mapNotNull { line ->
        val parts = line.split('|')
        val title = parts.getOrNull(0)?.trim().orEmpty()
        val minutes = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: return@mapNotNull null
        if (minutes <= 0) null else Entry(title, minutes)
    }

    // A blank title is kept while being typed; the voice sheet skips it.
    fun format(entries: List<Entry>): String =
        entries.joinToString("\n") { "${it.title.replace('|', ' ').replace('\n', ' ')}|${it.minutes}" }

    fun load(): List<Entry> = parse(AppSettings.quickEntries.value)

    fun save(entries: List<Entry>) = AppSettings.set(AppSettings.quickEntries, format(entries))
}
