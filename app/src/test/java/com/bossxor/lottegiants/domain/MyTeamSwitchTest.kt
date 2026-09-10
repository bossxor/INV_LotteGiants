package com.bossxor.lottegiants.domain

import com.bossxor.lottegiants.LauncherIcon
import com.bossxor.lottegiants.data.NotificationType
import com.bossxor.lottegiants.data.SnapshotStore
import com.bossxor.lottegiants.data.descriptionFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MyTeamSwitchTest {

    @Test
    fun normalizeUnknownTeamFallsBackToLotte() {
        assertEquals(LOTTE_TEAM_CODE, normalizeTeamCode(""))
        assertEquals(LOTTE_TEAM_CODE, normalizeTeamCode("XX"))
        assertEquals("OB", normalizeTeamCode("ob"))
    }

    @Test
    fun stadiumWatermarkFollowsHomePark() {
        assertEquals("SAJIK", stadiumWatermark("LT"))
        assertEquals("JAMSIL", stadiumWatermark("OB"))
        assertEquals("JAMSIL", stadiumWatermark("LG"))
        assertEquals("GOCHEOK", stadiumWatermark("WO"))
    }

    @Test
    fun weatherFallbackUsesTeamHome() {
        val jamsil = resolveStadiumCoord("", "잠실")
        assertEquals("잠실", jamsil.name)
        assertTrue(jamsil.lat > 37)
        val unknown = resolveStadiumCoord("없는구장", "사직")
        assertEquals("없는구장", unknown.name)
        assertEquals(35.1941, unknown.lat, 0.001)
    }

    @Test
    fun involvesTeamMatchesDoosan() {
        val g = MiniGame(
            gameId = "1",
            homeName = "두산",
            awayName = "KIA",
            homeScore = 0,
            awayScore = 0,
            status = GameStatus.BEFORE,
            statusText = "",
            homeTeamCode = "OB",
            awayTeamCode = "HT",
        )
        assertTrue(g.involvesTeam("OB"))
        assertTrue(g.involvesTeam("HT"))
        assertFalse(g.involvesTeam("LT"))
    }

    @Test
    fun scoreAlertsUseTeamName() {
        assertEquals("두산 역전!", leadChangeTitle(3, 4, 5, 4, "KIA", "두산"))
        assertEquals("두산 득점! · 1타점 · 1:0", formatLotteScoreTitle(null, 1, "1:0", teamName = "두산"))
        assertEquals("두산 홈런! 1점홈런 · 1:0", formatHomerunTitle(null, 1, "1:0", teamName = "두산"))
    }

    @Test
    fun toRaceMiniGameUsesFocusTeam() {
        val game = LotteGameInfo(
            gameId = "g1",
            gameDate = "2026-09-10",
            startTime = "18:30",
            stadium = "잠실",
            isHome = true,
            lotteScore = 4,
            opponentScore = 2,
            opponentName = "KIA",
            opponentCode = "HT",
            focusTeamCode = "OB",
            focusTeamName = "두산",
        )
        val mini = game.toRaceMiniGame()
        assertEquals("두산", mini.homeName)
        assertEquals("OB", mini.homeTeamCode)
        assertEquals("KIA", mini.awayName)
    }

    @Test
    fun notificationDescriptionInsertsTeamName() {
        assertEquals("두산이 득점할 때 알림", NotificationType.SCORE.descriptionFor("두산"))
        assertEquals("내 팀이 득점할 때 알림", NotificationType.SCORE.description)
    }

    @Test
    fun teamSwitchClearsTransientKeys() {
        val keys = SnapshotStore.TEAM_SWITCH_CLEAR_KEYS
        assertTrue(keys.contains("live_snapshot"))
        assertTrue(keys.contains("last_race_fingerprint"))
        assertTrue(keys.contains("preferred_live_game_id"))
        assertEquals(9, keys.size)
    }

    @Test
    fun launcherNormalizeAndResultsDefault() {
        assertEquals("OB", LauncherIcon.normalize("ob"))
        assertEquals("LT", LauncherIcon.normalize("xx"))
        val games = listOf(
            MiniGame(gameId = "a", homeName = "KIA", awayName = "삼성", homeScore = 0, awayScore = 0, status = GameStatus.BEFORE, statusText = "", homeTeamCode = "HT", awayTeamCode = "SS"),
            MiniGame(gameId = "b", homeName = "두산", awayName = "롯데", homeScore = 0, awayScore = 0, status = GameStatus.BEFORE, statusText = "", homeTeamCode = "OB", awayTeamCode = "LT"),
        )
        assertEquals("b", com.bossxor.lottegiants.ui.MainViewModel.sortMyTeamFirst(games, "OB").first().gameId)
    }

    @Test
    fun historyHasTenTeams() {
        KBO_TEAMS.forEach { team ->
            assertTrue(TeamHistory.byCode(team.code).isNotEmpty())
        }
    }
}
