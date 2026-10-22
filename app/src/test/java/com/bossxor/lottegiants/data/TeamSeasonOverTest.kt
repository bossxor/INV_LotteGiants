package com.bossxor.lottegiants.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 시즌이 끝난 팀에는 「등말소 변화 없음」 안내를 보내지 않는다. */
class TeamSeasonOverTest {
    private fun g(date: String, away: String, home: String, state: Int, sr: Int = 0) = KboOfficialGame(
        gameDate = date, gameId = "$date$away${home}0", seasonId = 2026, startTime = "18:30",
        awayId = away, homeId = home, gameState = state, srId = sr,
    )

    @Test fun eliminatedTeamIsOverDuringPostseason() {
        val range = listOf(g("20261004", "LT", "KT", 3), g("20261025", "HT", "SS", 1, 5))
        assertTrue(teamSeasonOver(range, "LT", "2026-10-22"))
        val ps = range + g("20261020", "LG", "SS", 3, 3)
        assertFalse(teamSeasonOver(ps, "SS", "2026-10-22"))
    }

    @Test fun offDayInSeasonIsNotOver() {
        val range = listOf(g("20260901", "LT", "KT", 3), g("20260903", "LT", "NC", 1))
        assertFalse(teamSeasonOver(range, "LT", "2026-09-02"))
    }

    @Test fun beforeOpeningEvenWithScheduleIsOverUntilOpeningDay() {
        val range = listOf(g("20270315", "LT", "KT", 1, 1), g("20270328", "LT", "SS", 1))
        assertTrue(teamSeasonOver(range, "LT", "2027-03-20"))
        assertFalse(teamSeasonOver(range, "LT", "2027-03-28"))
    }

    @Test fun offSeasonWithNoScheduleIsOver() {
        assertTrue(teamSeasonOver(emptyList(), "LT", "2026-12-01"))
    }
}
