package com.bossxor.lottegiants.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLeadWindowTest {

    private fun game(
        status: GameStatus,
        date: String = "2026-09-02",
        start: String = "18:30",
    ) = LotteGameInfo(
        gameId = "1",
        gameDate = date,
        startTime = start,
        stadium = "사직",
        isHome = true,
        opponentCode = "SS",
        opponentName = "삼성",
        status = status,
    )

    @Test
    fun clampSnapsToThirtyMinutes() {
        assertEquals(30, clampLiveLeadMinutes(10))
        assertEquals(120, clampLiveLeadMinutes(120))
        assertEquals(90, clampLiveLeadMinutes(100))
        assertEquals(360, clampLiveLeadMinutes(999))
    }

    @Test
    fun labelFormatsHoursAndMinutes() {
        assertEquals("30분 전", liveLeadLabel(30))
        assertEquals("2시간 전", liveLeadLabel(120))
        assertEquals("1시간 30분 전", liveLeadLabel(90))
    }

    @Test
    fun beforeGameOnlyInsideLeadWindow() {
        val start = parseKboStartMillis("2026-09-02", "18:30")!!
        val twoHours = 120
        assertFalse(shouldPostLiveNotification(game(GameStatus.BEFORE), twoHours, start - 3 * 60 * 60_000L))
        assertTrue(shouldPostLiveNotification(game(GameStatus.BEFORE), twoHours, start - 2 * 60 * 60_000L))
        assertTrue(shouldPostLiveNotification(game(GameStatus.BEFORE), twoHours, start - 10 * 60_000L))
        assertTrue(shouldPostLiveNotification(game(GameStatus.LIVE), twoHours, start - 5 * 60 * 60_000L))
        assertTrue(shouldPostLiveNotification(game(GameStatus.ENDED), twoHours, start + 4 * 60 * 60_000L))
    }
}
