package com.bossxor.lottegiants.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** 누적 점수 차이를 "이번 득점"으로 쓰지 않는다. */
class ScoreTitleRunsTest {
    @Test
    fun catchUpDeltaDoesNotClaimSixRuns() {
        assertEquals("KIA 득점! · 6:2", formatLotteScoreTitle(null, 6, "6:2", null, "KIA", null))
    }

    @Test
    fun singleRunStillShown() {
        assertEquals("KIA 득점! · 1득점 · 3:2", formatLotteScoreTitle(null, 1, "3:2", null, "KIA", null))
    }

    @Test
    fun knownPlayKeepsCountAndRbi() {
        assertEquals("KIA 득점! 김도영 우중간 2루타 · 2타점 · 4:2",
            formatLotteScoreTitle("김도영", 2, "4:2", "우중간 2루타", "KIA", 2))
        assertEquals("KIA 득점! 김도영 적시타 · 2득점 · 4:2",
            formatLotteScoreTitle("김도영", 2, "4:2", "적시타", "KIA", null))
    }

    @Test
    fun twoRunsWithoutDetailStillShown() {
        assertEquals("KIA 득점! · 2득점 · 6:3", formatLotteScoreTitle(null, 2, "6:3", null, "KIA", null))
        assertEquals("실점! LG · 3점 · 5:2", formatConcedeTitle(null, "LG", 3, "5:2", null))
    }

    @Test
    fun impossibleConcedeCountOmitted() {
        assertEquals("실점! LG · 7:2", formatConcedeTitle(null, "LG", 5, "7:2", null))
    }

    @Test
    fun homerunShowsHowManyRunsWithDirection() {
        assertEquals("KIA 홈런! 김도영 좌중간 만루 홈런 · 7:0", formatHomerunTitle("김도영", 4, "7:0", "좌중간 홈런", "KIA"))
        assertEquals("KIA 홈런! 나성범 중견 솔로 홈런 · 1:0", formatHomerunTitle("나성범", 1, "1:0", "중견 홈런", "KIA"))
        assertEquals("KIA 홈런! 2점 홈런 · 3:0", formatHomerunTitle(null, 2, "3:0", null, "KIA"))
    }

    @Test
    fun homerunRunsCountRunnersOnHomerLine() {
        // 2026-10-20 준PO3 김도영: 홈런 줄엔 주자 셋, 홈인 줄은 한 줄만 먼저 온 상태
        val hr = RelayText(109, "김도영 : 좌중간 뒤 홈런", 0, 2, true, base1Code = "2", base2Code = "1", base3Code = "9")
        val play = ScoringPlay(109, "김도영", "좌중간 홈런", 2, null, emptyList(), 2, true, 3, 5)
        assertEquals(4, homerunRuns(play, listOf(hr)))
        val solo = RelayText(52, "나성범 : 중견수 뒤 홈런", 0, 2, true, base1Code = "0", base2Code = "0", base3Code = "0")
        assertEquals(1, homerunRuns(play.copy(seqno = 52, runs = 1), listOf(solo)))
    }

    @Test
    fun homerunNeverSaysMoreThanFour() {
        assertEquals("KIA 홈런! 홈런 · 6:2", formatHomerunTitle(null, 6, "6:2", null, "KIA"))
    }
}
