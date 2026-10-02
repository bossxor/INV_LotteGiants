package com.bossxor.lottegiants.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NowBarTextTest {
    private fun game(status: GameStatus, block: LotteGameInfo.() -> LotteGameInfo = { this }) = LotteGameInfo(
        gameId = "g1", gameDate = "2026-10-03", startTime = "14:00", stadium = "광주", isHome = false,
        opponentCode = "HT", opponentName = "KIA", status = status,
    ).block()

    @Test fun pregameProbEvenAndFavored() {
        assertEquals(0.5, NowBarText.pregameFocusProb(0.5, 0.5)!!, 1e-9)
        assertTrue(NowBarText.pregameFocusProb(0.6, 0.5)!! > 0.5)
        assertNull(NowBarText.pregameFocusProb(0.0, 0.5))
    }

    @Test fun before() {
        val g = game(GameStatus.BEFORE) {
            copy(lotteStartingPitcher = "박세웅", opponentStartingPitcher = "네일", lotteRank = 8, opponentRank = 4)
        }
        val c = NowBarText.build(g, null, 0.52)
        assertEquals("롯데 vs KIA", c.title)
        assertEquals("10/03(토) 14:00 · 광주", c.text)
        assertEquals("14:00", c.chip)
        assertEquals("롯데 박세웅 · KIA 네일", c.lines.first { it.first == "선발" }.second)
        assertEquals("롯데 8위 · KIA 4위", c.lines.first { it.first == "순위" }.second)
        assertTrue(c.lines.first { it.first == "승리확률" }.second.contains("52%"))
    }

    @Test fun beforeWithoutProbOrRankDropsRows() {
        val c = NowBarText.build(game(GameStatus.BEFORE), null, null)
        assertTrue(c.lines.none { it.first == "순위" || it.first == "승리확률" })
    }

    @Test fun live() {
        val g = game(GameStatus.LIVE) {
            copy(
                lotteScore = 3, opponentScore = 2, inning = 7, isTopInning = true, out = 2, ball = 2, strike = 1,
                onBase1 = true, onBase3 = true, currentPitcherName = "박세웅", currentPitcherPitchCount = 85,
                currentBatterName = "최형우", currentBatterOrder = 4,
                lotteInningScores = listOf("0", "0", "1", "0", "0", "2", "0"),
                opponentInningScores = listOf("0", "1", "0", "0", "0", "0", "1"),
            )
        }
        val c = NowBarText.build(g, null, null)
        assertEquals("롯데 3 : 2 KIA", c.title)
        assertEquals("3:2", c.chip)
        assertEquals("7회초 · KIA 공격", c.text)
        assertEquals("●●   B ●●○   S ●○", c.lines.first { it.first == "아웃" }.second)
        assertEquals("1루 · 3루", c.lines.first { it.first == "주자" }.second)
        assertEquals(2, c.lineScore.size)
        assertTrue(c.lineScore[0].endsWith("| 3"))
    }

    @Test fun endedShowsNextGame() {
        val g = game(GameStatus.ENDED) { copy(lotteScore = 4, opponentScore = 2, winPitcherName = "박세웅") }
        val next = game(GameStatus.BEFORE) { copy(gameId = "g2", gameDate = "2026-10-04", startTime = "14:00") }
        val c = NowBarText.build(g, next, null)
        assertEquals("종료", c.chipSub)
        assertTrue(c.lines.first { it.first == "다음" }.second.contains("10/04(일) 14:00 · 광주 · KIA전"))
        assertNotNull(c.lines.firstOrNull { it.first == "다음" })
    }

    @Test fun homeGameKeepsAwayOnLeft() {
        val g = game(GameStatus.LIVE) {
            copy(
                isHome = true, lotteScore = 5, opponentScore = 3, inning = 3,
                lotteInningScores = listOf("1", "4", "0"), opponentInningScores = listOf("0", "3", "0"),
            )
        }
        val c = NowBarText.build(g, null, null)
        assertEquals("KIA 3 : 5 롯데", c.title)
        assertEquals("3:5", c.chip)
        assertTrue(c.lineScore[0].startsWith("KIA") && c.lineScore[1].startsWith("LOT"))
        val b = NowBarText.build(game(GameStatus.BEFORE) { copy(isHome = true, lotteStartingPitcher = "박세웅", opponentStartingPitcher = "네일") }, null, 0.6)
        assertEquals("KIA 네일 · 롯데 박세웅", b.lines.first { it.first == "선발" }.second)
        assertTrue(b.lines.first { it.first == "승리확률" }.second.startsWith("KIA 40%"))
    }

    @Test fun shortCode() {
        assertEquals("LOT", NowBarText.shortCode("롯데"))
        assertEquals("KT ", NowBarText.shortCode("KT"))
        assertEquals("SSG", NowBarText.shortCode("SSG"))
    }
}
