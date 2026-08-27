package com.bossxor.lottegiants.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MagicNumberTest {

    private fun team(
        id: String,
        rank: Int,
        win: Int,
        lose: Int,
        draw: Int = 0,
        games: Int = win + lose + draw,
    ) = TeamStanding(
        teamId = id,
        teamName = id,
        ranking = rank,
        wra = if (win + lose == 0) 0.0 else win.toDouble() / (win + lose),
        gameCount = games,
        win = win,
        draw = draw,
        lose = lose,
        gameBehind = 0.0,
    )

    @Test
    fun magicNumberIsSeasonPlusOneMinusWinsMinusOppLosses() {
        val a = team("A", 1, win = 80, lose = 50)
        val b = team("B", 2, win = 75, lose = 55)
        assertEquals(144 + 1 - 80 - 55, magicNumber(a, b, 144))
    }

    @Test
    fun remainingUsesSeasonLength() {
        val t = team(LOTTE_TEAM_CODE, 4, win = 70, lose = 50, games = 120)
        assertEquals(24, remainingGames(t, 144))
        assertEquals(0, remainingGames(t.copy(gameCount = 150), 144))
    }

    @Test
    fun fourthPlaceShowsClinchMagicVsSixth() {
        val standings = listOf(
            team("SS", 1, 85, 45),
            team("LG", 2, 80, 50),
            team("KT", 3, 76, 54),
            team(LOTTE_TEAM_CODE, 4, 72, 58),
            team("NC", 5, 70, 60),
            team("KI", 6, 68, 62),
            team("WO", 7, 60, 70),
            team("HH", 8, 55, 75),
            team("SK", 9, 50, 80),
            team("OB", 10, 45, 85),
        )
        val race = lotteRaceSummary(standings)!!
        assertEquals("롯데 4위 · 잔여 14경기", race.headline)
        assertTrue(race.lines.any { it.startsWith("5위 확정 매직") })
        assertEquals("5위 확정 매직 ${144 + 1 - 72 - 62}", race.lines.first { it.startsWith("5위 확정") })
        assertTrue(race.lines.any { it.startsWith("1위와") })
    }

    @Test
    fun firstPlaceShowsPennantMagic() {
        val standings = listOf(
            team(LOTTE_TEAM_CODE, 1, 90, 40),
            team("LG", 2, 80, 50),
            team("SS", 3, 70, 60),
            team("KT", 4, 68, 62),
            team("NC", 5, 65, 65),
            team("KI", 6, 60, 70),
        )
        val race = lotteRaceSummary(standings)!!
        assertEquals("롯데 1위 · 잔여 14경기", race.headline)
        assertEquals("1위 확정 매직 ${144 + 1 - 90 - 50}", race.lines.first { it.startsWith("1위 확정") })
        assertTrue(race.lines.any { it.startsWith("5위") })
    }

    @Test
    fun seventhShowsGapAndElimination() {
        val standings = listOf(
            team("SS", 1, 85, 45),
            team("LG", 2, 80, 50),
            team("KT", 3, 76, 54),
            team("NC", 4, 72, 58),
            team("KI", 5, 70, 60),
            team("WO", 6, 68, 62),
            team(LOTTE_TEAM_CODE, 7, 65, 65),
        )
        val race = lotteRaceSummary(standings)!!
        assertTrue(race.lines.any { it.startsWith("5위까지") })
        assertEquals("탈락까지 ${144 + 1 - 70 - 65}", race.lines.first { it.startsWith("탈락") })
    }

    @Test
    fun clinchedFifthWhenMagicNonPositive() {
        val standings = listOf(
            team(LOTTE_TEAM_CODE, 1, 95, 40),
            team("LG", 2, 80, 55),
            team("SS", 3, 70, 65),
            team("KT", 4, 65, 70),
            team("NC", 5, 60, 75),
            team("KI", 6, 40, 95),
        )
        val race = lotteRaceSummary(standings)!!
        assertTrue(race.lines.contains("5위 이상 확정"))
    }

    @Test
    fun emptyOrMissingLotteReturnsNull() {
        assertEquals(null, lotteRaceSummary(emptyList()))
        assertEquals(null, lotteRaceSummary(listOf(team("LG", 1, 80, 50))))
    }
}
