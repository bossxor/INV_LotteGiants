package com.bossxor.lottegiants.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 탈락·확정 뒤에는 트래직/매직 넘버가 음수가 되어도 같은 알림이 반복되면 안 된다. */
class EliminatedRaceAlertTest {

    private fun pulse(tragic: Int?, magic: Int? = null, rank: Int = 8) =
        RacePulse(rank = rank, slot = "", magic = magic, tragic = tragic, magicLabel = "")

    @Test
    fun negativeTragicDoesNotRepeatAlert() {
        val now = pulse(tragic = -3)
        val prev = parseRacePulse(now.fingerprint())
        assertNull(raceChangeAlert(prev, now))
    }

    @Test
    fun tragicChangingBelowZeroDoesNotAlert() {
        val prev = parseRacePulse(pulse(tragic = -3).fingerprint())
        assertNull(raceChangeAlert(prev, pulse(tragic = -4)))
    }

    @Test
    fun oldStoredNegativeFingerprintDoesNotAlertAgain() {
        val prev = parseRacePulse("8||-1|-3")
        assertNull(raceChangeAlert(prev, pulse(tragic = -3)))
    }

    @Test
    fun firstEliminationStillAlerts() {
        val prev = parseRacePulse(pulse(tragic = 1).fingerprint())
        assertEquals("포스트시즌 탈락", raceChangeAlert(prev, pulse(tragic = 0))?.first)
    }

    @Test
    fun nullMagicStaysNull() {
        assertNull(parseRacePulse(pulse(tragic = 2).fingerprint())?.magic)
    }
}
