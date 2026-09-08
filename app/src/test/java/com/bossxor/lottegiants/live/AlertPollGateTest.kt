package com.bossxor.lottegiants.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AlertPollGateTest {

    @Before
    fun reset() {
        AlertPollGate.resetForTest()
    }

    @Test
    fun firstLineupRunsThenSkipsWithinGap() {
        assertTrue(AlertPollGate.tryBeginLineup(60_000L))
        assertFalse(AlertPollGate.tryBeginLineup(60_000L))
    }

    @Test
    fun rosterAndLineupAreIndependent() {
        assertTrue(AlertPollGate.tryBeginLineup(60_000L))
        assertTrue(AlertPollGate.tryBeginRoster(60_000L))
        assertFalse(AlertPollGate.tryBeginRoster(60_000L))
    }
}
