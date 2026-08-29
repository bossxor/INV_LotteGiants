package com.bossxor.lottegiants.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanceledGameTest {

    @Test
    fun detectsCancelFromStatusTextWhenStatusIsBefore() {
        val game = LotteGameInfo(
            gameId = "1",
            gameDate = "2026-08-29",
            startTime = "18:30",
            stadium = "사직",
            isHome = true,
            opponentCode = "WO",
            opponentName = "키움",
            status = GameStatus.BEFORE,
            statusText = "우천취소",
        )
        assertTrue(game.isCanceledGame())
        val normalized = game.normalizedIfCanceled()
        assertEquals(GameStatus.CANCELED, normalized.status)
        assertEquals("취소(우천)", normalized.cancelLabel)
    }

    @Test
    fun pregameCandidateIsNotCanceledWhenBefore() {
        val mini = MiniGame(
            gameId = "1",
            homeName = "롯데",
            awayName = "키움",
            homeScore = 0,
            awayScore = 0,
            status = GameStatus.BEFORE,
            statusText = "18:30",
        )
        assertFalse(mini.isCanceledGame())
    }
}
