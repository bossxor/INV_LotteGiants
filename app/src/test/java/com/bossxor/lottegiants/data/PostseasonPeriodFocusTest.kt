package com.bossxor.lottegiants.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 포스트시즌 기간에는 쉬는 날도 내 팀이 아니라 가을야구 경기를 따라간다. */
class PostseasonPeriodFocusTest {
    private fun g(date: String, away: String, home: String, label: String, state: Int, sr: Int = 3) = KboOfficialGame(
        gameDate = date, gameId = "$date$away$home", seasonId = 2026, startTime = "18:30",
        awayId = away, homeId = home, gameState = state, gameScNm = label, srId = sr,
    )

    @Test fun offDayPicksNextPostseasonHomeTeam() {
        val range = listOf(
            g("20261004", "LT", "KT", "정규경기", 3, 0),
            g("20261020", "HT", "LG", "준PO3", 3),
            g("20261022", "LG", "SS", "PO1", 1, 4),
        )
        assertEquals("SS", postseasonPeriodFocus(range, "2026-10-21", emptyList(), "LT"))
    }

    @Test fun afterLastGameKeepsMostRecentPostseasonGame() {
        val range = listOf(g("20261020", "HT", "LG", "준PO3", 3))
        assertEquals("LG", postseasonPeriodFocus(range, "2026-10-21", emptyList(), "LT"))
    }

    @Test fun afterKoreanSeriesEndsFallsBackToMyTeam() {
        val range = listOf(g("20261101", "KT", "LG", "KS7", 3, 7))
        assertNull(postseasonPeriodFocus(range, "2026-11-02", emptyList(), "LT"))
    }

    @Test fun afterWildCardEndsKeepsPostseasonFocus() {
        val range = listOf(g("20261006", "HT", "LG", "WC2", 3, 5))
        assertEquals("LG", postseasonPeriodFocus(range, "2026-10-07", emptyList(), "LT"))
    }

    @Test fun canceledKoreanSeriesGameIsNotTheEnd() {
        val range = listOf(g("20261101", "KT", "LG", "KS2", 3, 7), g("20261103", "LG", "KT", "KS3", 4, 7))
        assertEquals("LG", postseasonPeriodFocus(range, "2026-11-03", emptyList(), "LT"))
    }

    @Test fun koreanSeriesBreakDayKeepsNextGame() {
        val range = listOf(g("20261101", "KT", "LG", "KS2", 3, 7), g("20261103", "LG", "KT", "KS3", 1, 7))
        assertEquals("KT", postseasonPeriodFocus(range, "2026-11-02", emptyList(), "LT"))
    }

    @Test fun regularSeasonStillRunningKeepsMyTeam() {
        val range = listOf(
            g("20260930", "LT", "KT", "정규경기", 1, 0),
            g("20261006", "HT", "LG", "WC1", 1, 5),
        )
        assertNull(postseasonPeriodFocus(range, "2026-09-29", emptyList(), "LT"))
    }

    @Test fun myRegularGameTodayKeepsMyTeam() {
        val today = listOf(g("20261021", "LT", "KT", "정규경기", 1, 0))
        assertNull(postseasonPeriodFocus(listOf(g("20261020", "HT", "LG", "준PO3", 3)), "2026-10-21", today, "LT"))
    }
}
