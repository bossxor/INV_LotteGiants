package com.bossxor.lottegiants.domain

import org.junit.Assert.*
import org.junit.Test

class LivePlayDetailsTest {
    private val roster = listOf("전준우", "윤동희", "고승민", "레이예스")
    private fun line(seq: Int, text: String, score: Int, title: String = "4번타자 전준우") =
        RelayText(seq, text, 1, 5, true, batterTitle = title, awayScore = score, homeScore = 0)

    @Test fun walkWithFirstEmptyDoesNotMoveOtherRunners() {
        assertEquals(NamedBases("고승민", "윤동희", "전준우"),
            inferBasesAfterAdvance(NamedBases(null, "윤동희", "전준우"), "고승민", "볼넷"))
    }
    @Test fun walkForcesOnlyContiguousBases() {
        assertEquals(NamedBases("고승민", "윤동희", "전준우"),
            inferBasesAfterAdvance(NamedBases("윤동희", null, "전준우"), "고승민", "볼넷"))
    }
    @Test fun scoredRunnerIsNotTheBatter() {
        assertNull(pickPlayerName("3루주자 윤동희 : 홈인", "4번타자 전준우", roster))
    }
    @Test fun explicitAdvanceUsesDestinationAndRemovesHomeIn() {
        val before = NamedBases("고승민", "윤동희", "레이예스")
        assertEquals(NamedBases(null, "고승민", "윤동희"), advanceNamedRunners(before, listOf(
            line(1, "3루주자 레이예스 : 홈인", 1),
            line(2, "2루주자 윤동희 : 3루까지 진루", 1),
            line(3, "1루주자 고승민 : 2루까지 진루", 1)), roster))
    }
    @Test fun wildPitchDoesNotReusePreviousRbiHit() {
        val texts = listOf(line(100, "전준우 : 좌전 적시타", 1),
            line(101, "3루주자 레이예스 : 홈인", 1),
            line(105, "폭투", 1, "5번타자 고승민"),
            line(106, "3루주자 윤동희 : 홈인", 2, "5번타자 고승민"))
        val p = scoringPlays(texts, 102, 1, 2, false, roster).single()
        assertEquals("폭투", p.how)
        assertNull(p.who)
        assertEquals(0, p.rbi)
        assertEquals(listOf("윤동희"), p.scorers)
        assertFalse(formatLotteScoreTitle(p.who, p.runs, "2:0", p.how, rbi = p.rbi).contains("타점"))
    }
    @Test fun hitAndHomeInsAreCombinedAndNextBatterIsSeparate() {
        val texts = listOf(line(10, "전준우 : 좌전 2루타", 0),
            line(11, "3루주자 레이예스 : 홈인", 1),
            line(12, "2루주자 윤동희 : 홈인", 2))
        val p = scoringPlays(texts, 9, 0, 2, false, roster).single()
        assertEquals("전준우", p.who)
        assertEquals("좌전 2루타", p.how)
        assertEquals(2, p.runs)
        assertEquals(2, p.rbi)
        assertEquals(listOf("레이예스", "윤동희"), p.scorers)
        assertTrue(scoringDetailBody(p, "고승민", NamedBases(null, "전준우", null), "5회초")
            .contains("타석 고승민"))
    }
    @Test fun sacrificeFlySurvivesNextBatterTitleOnHomeIn() {
        val p = scoringPlays(listOf(line(10, "전준우 : 중견수 희생플라이", 0),
            line(11, "3루주자 윤동희 : 홈인", 1, "5번타자 고승민")), 9, 0, 1, false, roster).single()
        assertEquals("전준우", p.who)
        assertEquals(1, p.rbi)
    }
    @Test fun scoreOnlyDoesNotInventRbiOrOldHitter() {
        val p = scoringPlays(listOf(line(1, "전준우 : 좌전 적시타", 1)), 10, 1, 2, false, roster).single()
        assertNull(p.who)
        assertNull(p.rbi)
    }
    @Test fun opposingHalfDoesNotContaminatePlay() {
        val wrong = line(14, "고승민 : 홈런", 1).copy(isTopInning = false, homeScore = 1)
        val p = scoringPlays(listOf(line(10, "전준우 : 중전 안타", 0),
            line(11, "3루주자 윤동희 : 홈인", 1), wrong), 9, 0, 1, false, roster).single()
        assertEquals("전준우", p.who)
    }
    @Test fun errorsDoNotCreateRbi() {
        assertNull(creditedRbi("좌전 안타", 2, listOf(line(1, "송구 실책", 2))))
        assertEquals(0, creditedRbi("실책", 1, emptyList()))
    }
    @Test fun homerunIncludesHitterAmongScorers() {
        val p = scoringPlays(listOf(line(10, "전준우 : 솔로홈런", 1)), 9, 0, 1, false, roster).single()
        assertEquals(listOf("전준우"), p.scorers)
    }
}
