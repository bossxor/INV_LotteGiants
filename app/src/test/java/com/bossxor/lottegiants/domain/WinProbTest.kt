package com.bossxor.lottegiants.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WinProbTest {

    @Test
    fun normalizePercentIntegers() {
        assertEquals(0.45, WinProb.normalizeWinRatePercent(45.0)!!, 0.001)
        assertEquals(0.99, WinProb.normalizeWinRatePercent(99.0)!!, 0.001)
    }

    @Test
    fun normalizeFractions() {
        assertEquals(0.45, WinProb.normalizeWinRatePercent(0.45)!!, 0.001)
        assertEquals(0.5, WinProb.normalizeWinRatePercent(0.5)!!, 0.001)
    }

    @Test
    fun pairFixesOneAndNinetyNineAtZeroZero() {
        val pair = WinProb.normalizeWinRatePair(1.0, 99.0)!!
        assertEquals(0.01, pair.first, 0.001)
        assertEquals(0.99, pair.second, 0.001)
    }

    @Test
    fun focusProbFromPairWhenLotteIsHome() {
        val p = WinProb.focusWinProb(55.0, 45.0, lotteIsHome = true)!!
        assertEquals(0.55, p, 0.001)
    }

    @Test
    fun clampKeepsParsedFavoriteAtZeroZero() {
        val game = LotteGameInfo(
            gameId = "g1",
            gameDate = "2026-09-02",
            startTime = "18:30",
            stadium = "사직",
            isHome = true,
            opponentCode = "OB",
            opponentName = "두산",
            lotteScore = 0,
            opponentScore = 0,
            status = GameStatus.LIVE,
            inning = 1,
            isTopInning = true,
        )
        assertEquals(0.62, WinProb.clampForDisplay(game, 0.62), 0.001)
    }

    @Test
    fun displayPercentsSumToHundred() {
        val (away, home) = WinProb.displayPercents(0.42, 0.58)
        assertEquals(42, away)
        assertEquals(58, home)
    }

    @Test
    fun singleSidedOneMeansOnePercent() {
        assertEquals(0.01, WinProb.focusWinProb(1.0, null, lotteIsHome = true)!!, 0.001)
    }

    @Test
    fun clampRejectsExtremeApiInEarlyInning() {
        val game = LotteGameInfo(
            gameId = "g2",
            gameDate = "2026-09-02",
            startTime = "18:30",
            stadium = "사직",
            isHome = true,
            opponentCode = "SS",
            opponentName = "삼성",
            lotteScore = 3,
            opponentScore = 1,
            status = GameStatus.LIVE,
            inning = 3,
            isTopInning = true,
        )
        val clamped = WinProb.clampForDisplay(game, 0.01)
        assertTrue(clamped in 0.55..0.90)
    }

    @Test
    fun pickSeriesPrefersLongerNaver() {
        val naver = listOf(
            WinProbPoint(0, "1회", 0.52),
            WinProbPoint(1, "2회", 0.48),
        )
        val ruta = listOf(WinProbPoint(0, "현재", 0.51))
        val picked = WinProb.pickSeries(naver, ruta, emptyList())
        assertEquals(2, picked.size)
    }
}
