package com.bossxor.lottegiants.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class StandingStreakTest {

    @Test
    fun formatsNaverAndKboStreaks() {
        assertEquals("3승", formatStandingStreak("3승"))
        assertEquals("1패", formatStandingStreak("1패"))
        assertEquals("3승", formatStandingStreak("3연승"))
        assertEquals("9패", formatStandingStreak("9연패"))
        assertEquals("4승", formatStandingStreak("W4"))
        assertEquals("2패", formatStandingStreak("L2"))
        assertEquals("-", formatStandingStreak(""))
        assertEquals("-", formatStandingStreak("-"))
    }
}
