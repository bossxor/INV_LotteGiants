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
    fun rainDelayIsNotCanceled() {
        val game = LotteGameInfo(
            gameId = "1",
            gameDate = "2026-09-01",
            startTime = "18:30",
            stadium = "사직",
            isHome = true,
            opponentCode = "SS",
            opponentName = "삼성",
            status = GameStatus.LIVE,
            statusText = "5회말",
            isSuspended = true,
            resumeTime = "19:50",
            cancelReason = "우천",
        )
        assertFalse(game.isCanceledGame())
        assertEquals("우천중단 (19:50 예정)", game.suspendLabel)
        assertEquals("우천중단 (19:50 예정)", game.inningLabel)
    }

    @Test
    fun withSuspendFilledRestoresLiveFromCanceledDelay() {
        val game = LotteGameInfo(
            gameId = "1",
            gameDate = "2026-09-01",
            startTime = "18:30",
            stadium = "사직",
            isHome = true,
            opponentCode = "SS",
            opponentName = "삼성",
            status = GameStatus.CANCELED,
            statusText = "우천중단",
        )
        val filled = game.withSuspendFilled("19시 50분 재개 예정")
        assertEquals(GameStatus.LIVE, filled.status)
        assertTrue(filled.isSuspended)
        assertFalse(filled.isCanceledGame())
        assertEquals("19:50", filled.resumeTime)
        assertEquals("우천중단 (19:50 예정)", filled.suspendLabel)
    }

    @Test
    fun suspendDisplayFormatsResumeTime() {
        assertEquals("우천중단 (19:50 예정)", suspendDisplayLabel("우천중단", "19:50"))
        assertEquals("기타중단", suspendDisplayLabel("기타사유 중단", ""))
        assertEquals("우천중단 (19:50 예정)", suspendDisplayLabel("우천으로 경기가 중단되었습니다. 19시 50분 재개 예정"))
        assertFalse(isDelayText("우천취소"))
        assertTrue(isDelayText("우천중단"))
        assertEquals("19:50", parseResumeClock("재개 예정 19:50"))
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
