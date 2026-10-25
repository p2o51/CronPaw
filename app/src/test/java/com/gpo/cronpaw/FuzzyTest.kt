package com.gpo.cronpaw

import com.gpo.cronpaw.core.agenda.AgendaItem
import com.gpo.cronpaw.core.agenda.Source
import com.gpo.cronpaw.feature.search.Fuzzy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class FuzzyTest {
    @Test fun prefixSubstringAndScatteredLettersKeepTheirRanking() {
        assertEquals(100, Fuzzy.score("Calendar review", "CAL"))
        assertEquals(80, Fuzzy.score("My calendar", "cal"))
        assertEquals(30, Fuzzy.score("c-a-l", "cal"))
        assertEquals(0, Fuzzy.score("calendar", "cal xyz"))
        assertEquals(200, Fuzzy.score("会议复盘", "会议  会议"))
    }

    @Test fun boundedRankingMatchesTheFullSortIncludingTies() = runBlocking {
        val random = Random(31)
        val items = List(20_000) { i ->
            val title = listOf("Review Project", "项目复盘", "Project planning", "p-r-o", "Review")[i % 5]
            AgendaItem("$i", title, random.nextLong(0, 50), 100, false, 0, Source.Calendar(1, i.toLong()))
        }
        for (query in listOf("pro", "r", "项目", "Review pro", "no match")) {
            val expected = items.map { it to Fuzzy.score(it.title, query) }.filter { it.second > 0 }
                .sortedWith(compareByDescending<Pair<AgendaItem, Int>> { it.second }.thenBy { kotlin.math.abs(it.first.start - 25) })
                .take(60).map { it.first }
            assertEquals(expected, Fuzzy.search(items, query, 25))
        }
    }

    @Test fun emptyQueriesAndZeroLimitHaveNoResults() = runBlocking {
        val item = AgendaItem("1", "hello", 0, 1, false, 0, Source.Calendar(1, 1))
        assertTrue(Fuzzy.search(listOf(item), "  ", 0).isEmpty())
        assertTrue(Fuzzy.search(listOf(item), "hello", 0, 0).isEmpty())
    }
}
