package com.bossxor.lottegiants.live

import com.bossxor.lottegiants.domain.GameStatus
import com.bossxor.lottegiants.domain.LiveSnapshot
import com.bossxor.lottegiants.domain.LotteGameInfo
import com.bossxor.lottegiants.domain.MiniGame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameStillScheduledTest {
    private fun mini(id: String, status: GameStatus, statusText: String = "") = MiniGame(
        gameId = id,
        homeName = "롯데",
        awayName = "KIA",
        homeScore = 0,
        awayScore = 0,
        status = status,
        statusText = statusText,
    )

    private fun game(id: String, status: GameStatus, statusText: String = "") = LotteGameInfo(
        gameId = id,
        gameDate = "2026-11-08",
        startTime = "18:30",
        stadium = "사직",
        isHome = true,
        opponentCode = "HT",
        opponentName = "KIA",
        status = status,
        statusText = statusText,
    )

    @Test
    fun blankIdOrMissingSnapshotStaysScheduled() {
        assertTrue(gameStillScheduledInSnapshot(null, "g1"))
        assertTrue(gameStillScheduledInSnapshot(LiveSnapshot(), ""))
        assertTrue(gameStillScheduledInSnapshot(LiveSnapshot(todayLotteGames = listOf(mini("g1", GameStatus.BEFORE))), "g2"))
    }

    @Test
    fun canceledTodayGameIsNotScheduled() {
        val snap = LiveSnapshot(todayLotteGames = listOf(mini("g1", GameStatus.BEFORE, "우천취소")))
        assertFalse(gameStillScheduledInSnapshot(snap, "g1"))
    }

    @Test
    fun canceledFocusGameIsNotScheduled() {
        val snap = LiveSnapshot(lotteGame = game("g1", GameStatus.CANCELED, "폭염취소"))
        assertFalse(gameStillScheduledInSnapshot(snap, "g1"))
    }
}
