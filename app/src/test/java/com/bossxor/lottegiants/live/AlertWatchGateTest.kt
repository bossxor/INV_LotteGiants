package com.bossxor.lottegiants.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertWatchGateTest {

    @Test
    fun rosterOnlyDuringDayHours() {
        assertTrue(
            AlertWatchGate.shouldWatch(
                nowHour = 10,
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
