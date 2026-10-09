package com.bossxor.lottegiants.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertWatchGateTest {

    @Test fun previousGameConfirmationDoesNotSlowNextLineup() {
        assertEquals(10_000L, AlertWatchGate.lineupIntervalMs("game2", "game1:full"))
        assertEquals(10_000L, AlertWatchGate.lineupIntervalMs("game2", "game2:flag"))
        assertEquals(60_000L, AlertWatchGate.lineupIntervalMs("game2", "game2:full"))
    }

    @Test fun nextDoubleHeaderLineupIsWatchedWhileFirstGameIsLive() {
        val first = com.bossxor.lottegiants.domain.LotteGameInfo("game1", "2026-10-09", "14:00", "사직", true, "LG", "LG",
            status = com.bossxor.lottegiants.domain.GameStatus.LIVE)
        val second = first.copy(gameId = "game2", startTime = "18:00", status = com.bossxor.lottegiants.domain.GameStatus.BEFORE)
        assertEquals("game2", AlertWatchGate.watchGame(com.bossxor.lottegiants.domain.LiveSnapshot(
            lotteGame = first, nextLotteGame = second))?.gameId)
    }

    @Test fun publicationPeakHasShorterRosterInterval() {
        assertEquals(30_000L, AlertWatchGate.rosterIntervalMs(8))
        assertEquals(10_000L, AlertWatchGate.rosterIntervalMs(13))
        assertEquals(10_000L, AlertWatchGate.rosterIntervalMs(18))
        assertEquals(30_000L, AlertWatchGate.rosterIntervalMs(19))
    }

    @Test
    fun rosterWatchStartsInMorning() {
        assertTrue(
            AlertWatchGate.shouldWatch(
                nowHour = 10,
                nowMillis = 1_000L,
                lineupEnabled = false,
                rosterEnabled = true,
                gameStartMillis = null,
            ),
        )
        assertTrue(
            AlertWatchGate.shouldWatch(
                nowHour = 14,
                nowMillis = 1_000L,
                lineupEnabled = false,
                rosterEnabled = true,
                gameStartMillis = null,
            ),
        )
        assertFalse(
            AlertWatchGate.shouldWatch(
                nowHour = 7,
                nowMillis = 1_000L,
                lineupEnabled = false,
                rosterEnabled = true,
                gameStartMillis = null,
            ),
        )
    }

    @Test
    fun pollFasterOnlyInLineupWindow() {
        val start = 10_000_000L
        assertEquals(
            AlertWatchGate.LINEUP_POLL_MS,
            AlertWatchGate.pollIntervalMs(start - 60_000L, start),
        )
        assertEquals(
            AlertWatchGate.ROSTER_POLL_MS,
            AlertWatchGate.pollIntervalMs(start - AlertWatchGate.LINEUP_BEFORE_MS - 1, start),
        )
        assertEquals(
            AlertWatchGate.ROSTER_POLL_MS,
            AlertWatchGate.pollIntervalMs(1_000L, null),
        )
    }

    @Test
    fun lineupOnlyInsideWindow() {
        val start = 10_000_000L
        assertTrue(
            AlertWatchGate.shouldWatch(
                nowHour = 6,
                nowMillis = start - 60_000L,
                lineupEnabled = true,
                rosterEnabled = false,
                gameStartMillis = start,
            ),
        )
        assertFalse(
            AlertWatchGate.shouldWatch(
                nowHour = 6,
                nowMillis = start - AlertWatchGate.LINEUP_BEFORE_MS - 1,
                lineupEnabled = true,
                rosterEnabled = false,
                gameStartMillis = start,
            ),
        )
    }
}
