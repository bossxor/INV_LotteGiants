package com.bossxor.lottegiants.data

import com.bossxor.lottegiants.domain.*
import org.junit.Assert.*
import org.junit.Test

class RelayMergeStateTest {
    private val base = LotteGameInfo("game", "2026-10-09", "14:00", "사직", true, "LG", "LG", status = GameStatus.LIVE)
    private val relay = TextRelayData(inn = 7, homeOrAway = "1",
        homeLineup = LineupDto(batter = listOf(
            LineupBatterDto("한동희", "68525", 4, 1), LineupBatterDto("박승욱", "62802", 4, 2),
            LineupBatterDto("전민재", "68205", 5, 1))),
        currentGameState = GameStateDto(batter = "68205", base1 = "4", homeScore = "5", out = "2"),
        textRelays = listOf(TextRelayDto(inn = 7, homeOrAway = "1", textOptions = listOf(
            TextOptionDto(402, "1루주자 한동희 : 대주자 박승욱 (으)로 교체", currentGameState =
                GameStateDto(batter = "68525", base1 = "4", homeScore = "5", out = "1")))))
    )
    @Test fun currentStateBeatsSameScoreHistoricalText() {
        val result = mergeRelayUnsafe(base, relay)
        assertEquals("전민재", result.currentBatterName)
        assertEquals(2, result.out)
        assertEquals("박승욱", result.runnerOn1Name)
        assertEquals(4, result.runnerOn1Order)
    }
    @Test fun scoreCorrectionIsNotOverwrittenByOldText() {
        val result = mergeRelayUnsafe(base, relay.copy(currentGameState = relay.currentGameState!!.copy(homeScore = "4")))
        assertEquals(4, result.lotteScore)
    }
    @Test fun oldSubstitutionDoesNotPutScoredRunnerBackOnBase() {
        val result = mergeRelayUnsafe(base, relay.copy(currentGameState = relay.currentGameState!!.copy(base1 = "0")))
        assertFalse(result.onBase1)
        assertEquals("", result.runnerOn1Name)
    }
}
