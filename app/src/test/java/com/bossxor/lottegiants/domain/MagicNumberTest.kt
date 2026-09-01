package com.bossxor.lottegiants.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun mini(
        opp: String,
        date: String,
        home: Boolean,
        status: GameStatus = GameStatus.BEFORE,
        oppName: String = opp,
    ) = MiniGame(
        gameId = "$date-$opp",
        homeName = if (home) "롯데" else oppName,
        awayName = if (home) oppName else "롯데",
        homeScore = 0,
        awayScore = 0,
        status = status,
        statusText = "",
        gameDate = date,
        homeTeamCode = if (home) LOTTE_TEAM_CODE else opp,
        awayTeamCode = if (home) opp else LOTTE_TEAM_CODE,
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
    fun fourthPlaceShowsWildcardMagicVsSixth() {
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
        assertTrue(race.headline.contains("4위"))
        assertTrue(race.headline.contains("와일드카드"))
        assertEquals(
            "와일드카드 매직넘버 ${144 + 1 - 72 - 62} (롯데 승+6위 패)",
            race.lines.first { it.startsWith("와일드카드 매직넘버") },
        )
        assertTrue(race.lines.any { it.startsWith("준플레이오프까지") })
    }

    @Test
    fun firstPlaceShowsKoreanSeriesMagic() {
        val standings = listOf(
            team(LOTTE_TEAM_CODE, 1, 90, 40),
            team("LG", 2, 80, 50),
            team("SS", 3, 70, 60),
            team("KT", 4, 68, 62),
            team("NC", 5, 65, 65),
            team("KI", 6, 60, 70),
        )
        val race = lotteRaceSummary(standings)!!
        assertTrue(race.headline.contains("한국시리즈 직행"))
        assertEquals(
            "한국시리즈 직행 매직넘버 ${144 + 1 - 90 - 50} (롯데 승+2위 패)",
            race.lines.first { it.startsWith("한국시리즈") },
        )
    }

    @Test
    fun seventhShowsWildcardGapAndTragic() {
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
        assertTrue(race.headline.contains("포스트시즌 밖"))
        assertTrue(race.lines.any { it.startsWith("와일드카드까지") })
        assertEquals(
            "와일드카드 트래직넘버 ${144 + 1 - 70 - 65} (5위 승+롯데 패)",
            race.lines.first { it.startsWith("와일드카드 트래직") },
        )
    }

    @Test
    fun remainingOpponentsGroupsUnplayed() {
        val games = listOf(
            mini("OB", "2026-09-01", home = true),
            mini("OB", "2026-09-02", home = true),
            mini("HH", "2026-09-10", home = false, oppName = "한화"),
            mini("OB", "2026-08-01", home = true, status = GameStatus.ENDED),
            mini("NC", "2026-09-05", home = true, status = GameStatus.CANCELED),
        )
        val list = remainingOpponentsFrom(games, "2026-08-28")
        assertEquals(2, list.size)
        assertEquals("두산", list.first { it.code == "OB" }.name)
        assertEquals(2, list.first { it.code == "OB" }.games)
        assertEquals(1, list.first { it.code == "HH" }.games)
        assertTrue(formatRemainingOpponents(list).contains("두산 2"))
    }

    @Test
    fun raceChangeAlertOnMagicDropAndClinch() {
        val a = RacePulse(5, "와일드카드", magic = 12, tragic = null, magicLabel = "와일드카드 매직넘버")
        val b = a.copy(magic = 11)
        val drop = raceChangeAlert(a, b)!!
        assertTrue(drop.first.contains("11"))
        assertTrue(drop.second.contains("12 → 11"))
        val clinch = raceChangeAlert(b, b.copy(magic = 0))!!
        assertTrue(clinch.first.contains("확정"))
        assertNull(raceChangeAlert(null, a))
        assertNull(raceChangeAlert(a, a))
    }

    @Test
    fun raceChangeAlertIncludesGameResultReason() {
        val prev = RacePulse(5, "와일드카드", magic = 12, tragic = null, magicLabel = "와일드카드 매직넘버")
        val now = prev.copy(magic = 11)
        val standings = listOf(
            team(LOTTE_TEAM_CODE, 5, 72, 58),
            team("KT", 6, 68, 62),
        )
        val games = listOf(
            mini("OB", "2026-08-30", home = true, status = GameStatus.ENDED, oppName = "두산")
                .copy(homeScore = 5, awayScore = 3),
            MiniGame(
                gameId = "2026-08-30-KT",
                homeName = "KT",
                awayName = "키움",
                homeScore = 2,
                awayScore = 4,
                status = GameStatus.ENDED,
                statusText = "",
                gameDate = "2026-08-30",
                homeTeamCode = "KT",
                awayTeamCode = "WO",
            ),
        )
        val alert = raceChangeAlert(prev, now, standings, games)!!
        assertTrue(alert.second.contains("롯데, 두산 5:3 승"))
        assertTrue(alert.second.contains("KT, 키움 2:4 패"))
    }

    @Test
    fun widgetRaceLineJoinsRankRemainingOnly() {
        assertEquals("6위 · 잔여 23", widgetRaceLine(6, 23, "박세웅"))
        assertEquals("4위 · 잔여 0", widgetRaceLine(4, 0, ""))
        assertEquals("6위 · 잔여 23", widgetRaceLine(6, 23, "", "D-2"))
        assertEquals("잔여 31", widgetFooterLine(31))
        assertEquals("", widgetFooterLine(0))
    }

    @Test
    fun emptyOrMissingLotteReturnsNull() {
        assertEquals(null, lotteRaceSummary(emptyList()))
        assertEquals(null, lotteRaceSummary(listOf(team("LG", 1, 80, 50))))
    }

    @Test
    fun homeAwayAndUpcomingAndLastFive() {
        val standings = listOf(
            team("SS", 1, 85, 45),
            team("LG", 2, 80, 50),
            team("KT", 3, 76, 54),
            team("NC", 4, 72, 58),
            team("KI", 5, 70, 60),
            team("WO", 6, 68, 62),
            team(LOTTE_TEAM_CODE, 7, 65, 65),
        )
        val games = listOf(
            mini("OB", "2026-08-26", home = true, status = GameStatus.ENDED).copy(homeScore = 4, awayScore = 1),
            mini("OB", "2026-08-27", home = true, status = GameStatus.ENDED).copy(homeScore = 1, awayScore = 3),
            mini("OB", "2026-09-01", home = true, oppName = "두산").copy(startTime = "18:30"),
            mini("OB", "2026-09-02", home = true, oppName = "두산").copy(startTime = "18:30"),
            mini("OB", "2026-09-03", home = true, oppName = "두산").copy(startTime = "18:30"),
            mini("HH", "2026-09-10", home = false, oppName = "한화").copy(startTime = "18:30"),
        )
        val race = lotteRaceSummary(standings, seasonGames = games, todayIso = "2026-08-28")!!
        assertTrue(race.lines.any { it == "잔여 홈 3 · 원정 1" })
        assertTrue(race.lines.any { it.contains("두산") && it.contains("3연전") })
        assertTrue(race.lines.any { it.startsWith("최근 2경기") && it.contains("승") && it.contains("패") })
        assertEquals("9/1 홈 두산 18:30", race.upcoming.first())
        assertTrue(race.upcoming.any { it.contains("원정 한화") })
        val vs = formatVsRaceOpponent(remainingOpponentsFrom(games, "2026-08-28"), standings)
        assertTrue(vs.contains("잔여 맞대결 없음"))
    }

    @Test
    fun selfClinchWhenMagicFitsRemaining() {
        val lotte = team(LOTTE_TEAM_CODE, 4, 72, 58)
        val sixth = team("KI", 6, 68, 62)
        assertEquals("자력: 잔여 14경기 중 11승이면 상대 결과 무관", formatSelfClinchLine(lotte, sixth, 144))
    }

    @Test
    fun countdownUsesHoursThenDday() {
        val start = parseKboStartMillis("2026-08-28", "18:30")!!
        val twoHoursBefore = start - 2 * 60 * 60 * 1000L - 14 * 60 * 1000L
        assertEquals("2시간 14분 후", gameCountdownLabel("2026-08-28", "18:30", twoHoursBefore))
        val threeDaysBefore = start - 3 * 24 * 60 * 60 * 1000L
        assertEquals("D-3", gameCountdownLabel("2026-08-28", "18:30", threeDaysBefore))
        assertEquals("임박", gameCountdownLabel("2026-08-28", "18:30", start))
    }
}
